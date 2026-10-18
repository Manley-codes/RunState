package com.runstate.mobile.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** A passage of the currently selected Spotify recording; this never selects another song. */
internal data class SongPassageRequest(
    val trackTitle: String,
    val artistName: String,
    val startPositionMillis: Long,
    val durationMillis: Long = 10_000,
    val spotifyMediaId: String? = null
) {
    init {
        require(trackTitle.isNotBlank() && artistName.isNotBlank())
        require(startPositionMillis >= 0 && durationMillis in 1_000..10_000)
        require(spotifyMediaId == null || spotifyMediaId.isNotBlank())
    }
}

internal enum class SongPassageStatus { FINISHED, UNAVAILABLE, INTERRUPTED, FAILED }
internal data class SongPassageResult(val status: SongPassageStatus, val playbackRestored: Boolean = false)
internal enum class PassagePlayback { PLAYING, PAUSED, OTHER }

/** Optional test diagnostics contain enums and bounded timing differences only, never media identity. */
internal enum class SongPassageStage {
    ADMISSION, INITIAL_PAUSE, ENTRANCE_SEEK, ENTRANCE_PLAY, PLAYING, ENDING_PAUSE,
    RESTORE_SEEK, RESTORE_PLAY, CLEANUP
}
internal enum class SongPassageReason {
    ENTERED, FINISHED, OWNER_BUSY, SESSION_MISSING, REQUEST_MISMATCH, INVALID_DURATION,
    INVALID_POSITION, PLAYBACK_CHANGED, POSITION_DRIFT, CAPABILITY_CHANGED, CONFIRM_TIMEOUT,
    STOPPED, CANCELLED, TRANSPORT_ERROR, SOURCE_CHANGED, SESSION_CHANGED, MEDIA_ID_CHANGED,
    TITLE_CHANGED, ARTIST_CHANGED, DURATION_CHANGED, CLEANUP_PAUSE_SENT, CLEANUP_NOT_REQUIRED
}
internal data class SongPassageDiagnostic(
    val stage: SongPassageStage,
    val reason: SongPassageReason,
    val playback: PassagePlayback? = null,
    val stageElapsedMillis: Long = 0L,
    val positionErrorMillis: Long? = null,
    val positionUpdateAgeMillis: Long? = null
)

/** Position is the player's reported position, extrapolated only while it reports playing. */
internal data class SongPassageSnapshot(
    val sessionKey: Any,
    val sourcePackage: String,
    val mediaId: String?,
    val title: String?,
    val artist: String?,
    val durationMillis: Long,
    val positionMillis: Long,
    val playback: PassagePlayback,
    val canPause: Boolean,
    val canPlay: Boolean,
    val canSeek: Boolean,
    /** Relative age of the player's position report; null means it supplied no usable clock. */
    val positionUpdateAgeMillis: Long? = null
)

internal interface SongPassageSession {
    /** Null means missing/ambiguous/unreadable access, never a fabricated current song. */
    fun snapshot(): SongPassageSnapshot?
    fun pause()
    fun seekTo(positionMillis: Long)
    fun play()
}

internal interface PassageClock {
    fun nowMillis(): Long
    suspend fun waitMillis(millis: Long)
}

private object RealPassageClock : PassageClock {
    override fun nowMillis() = System.nanoTime() / 1_000_000
    override suspend fun waitMillis(millis: Long) = delay(millis)
}

/**
 * One foreground owner, called from the UI's coroutine. Stop/cancellation never resumes music.
 * Only an uninterrupted completed passage restores the prior position and playing/paused state.
 * No position, title, URI, or provider exception is logged. Timings are software bounds, not a
 * guarantee of sample-accurate audible playback from another app.
 */
internal class SpotifySongPassageController(
    private val session: SongPassageSession,
    private val clock: PassageClock = RealPassageClock,
    private val diagnosticObserver: (SongPassageDiagnostic) -> Unit = {}
) {
    private class Owner(val initial: SongPassageSnapshot) {
        @Volatile var stopped = false
        var changedPlayback = false
        var expectedPosition = initial.positionMillis
        var expectedAt = 0L
        var expectsPlaying = false
        var pendingPlay = false
        var stage = SongPassageStage.ADMISSION
        var stageStartedAt = 0L
        var diagnosticTarget: Long? = null
        var lastPlayback: PassagePlayback? = null
        var lastPositionError: Long? = null
        var lastPositionAge: Long? = null
    }
    private var owner: Owner? = null

    fun stop() { owner?.stopped = true }

    suspend fun play(request: SongPassageRequest): SongPassageResult {
        if (owner != null) {
            report(null, SongPassageReason.OWNER_BUSY)
            return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        }
        val initial = try { session.snapshot() } catch (_: RuntimeException) {
            report(null, SongPassageReason.TRANSPORT_ERROR)
            return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        } ?: run {
            report(null, SongPassageReason.SESSION_MISSING)
            return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        }
        if (!matches(request, initial) || initial.durationMillis !in 1_000..MAX_TRACK_MILLIS ||
            initial.positionMillis !in 0..initial.durationMillis ||
            initial.playback == PassagePlayback.OTHER || !initial.canSeek ||
            (initial.playback == PassagePlayback.PLAYING && !initial.canPause) ||
            request.startPositionMillis > initial.durationMillis - request.durationMillis - 2_000) {
            val reason = when {
                !matches(request, initial) -> SongPassageReason.REQUEST_MISMATCH
                initial.durationMillis !in 1_000..MAX_TRACK_MILLIS -> SongPassageReason.INVALID_DURATION
                initial.positionMillis !in 0..initial.durationMillis -> SongPassageReason.INVALID_POSITION
                initial.playback == PassagePlayback.OTHER -> SongPassageReason.PLAYBACK_CHANGED
                !initial.canSeek || (initial.playback == PassagePlayback.PLAYING && !initial.canPause) ->
                    SongPassageReason.CAPABILITY_CHANGED
                else -> SongPassageReason.INVALID_DURATION
            }
            report(null, reason)
            return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        }
        val current = Owner(initial).also { it.stageStartedAt = clock.nowMillis() }
        owner = current
        var completed = false
        try {
            if (initial.playback == PassagePlayback.PLAYING) {
                enter(current, SongPassageStage.INITIAL_PAUSE)
                if (!checked(current).canPause) throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
                current.changedPlayback = true
                session.pause()
                await(current) { it.playback == PassagePlayback.PAUSED }
            }
            var paused = checked(current)
            if (paused.playback != PassagePlayback.PAUSED || !paused.canSeek || !paused.canPlay) {
                throw PassageInterrupted(if (paused.playback != PassagePlayback.PAUSED)
                    SongPassageReason.PLAYBACK_CHANGED else SongPassageReason.CAPABILITY_CHANGED)
            }
            enter(current, SongPassageStage.ENTRANCE_SEEK, request.startPositionMillis)
            current.changedPlayback = true
            session.seekTo(request.startPositionMillis)
            paused = await(current) { it.playback == PassagePlayback.PAUSED &&
                abs(it.positionMillis - request.startPositionMillis) <= POSITION_TOLERANCE }
            if (!paused.canPlay) throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
            checked(current)
            current.expectedPosition = request.startPositionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            current.pendingPlay = true
            enter(current, SongPassageStage.ENTRANCE_PLAY)
            session.play()
            val playing = await(current) { it.playback == PassagePlayback.PLAYING }
            if (!isOwnedPlaying(current, playing)) throw PassageInterrupted(playbackFailure(playing))
            current.pendingPlay = false
            current.expectedPosition = playing.positionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            enter(current, SongPassageStage.PLAYING)
            // Admission already bounds start + duration below the validated (at most 24 h)
            // track length, so this addition cannot overflow. A late seek/PLAYING report must
            // consume the selected passage, not move its editorial ending further into the song.
            val endPositionMillis = request.startPositionMillis + request.durationMillis
            val finishAt = current.expectedAt + request.durationMillis
            while (clock.nowMillis() < finishAt) {
                val snapshot = checked(current)
                if (!isOwnedPlaying(current, snapshot)) throw PassageInterrupted(playbackFailure(snapshot))
                val positionRemaining = endPositionMillis - snapshot.positionMillis
                if (positionRemaining <= 0L) break
                // The existing wall-clock cap still wins if an early player report does not
                // reach the endpoint. Reports/transport latency cannot promise sample accuracy.
                clock.waitMillis(minOf(POLL_MILLIS, positionRemaining,
                    finishAt - clock.nowMillis()).coerceAtLeast(1))
            }
            val finalPlaying = checked(current)
            if (!isOwnedPlaying(current, finalPlaying)) throw PassageInterrupted(playbackFailure(finalPlaying))
            enter(current, SongPassageStage.ENDING_PAUSE)
            // We confirmed our own playback. A missing advertised action must not suppress the
            // bounded pause attempt; the following same-recording PAUSED confirmation is decisive.
            session.pause()
            val ended = await(current) { it.playback == PassagePlayback.PAUSED }
            current.expectsPlaying = false
            current.expectedPosition = ended.positionMillis
            val beforeRestore = checked(current)
            if (beforeRestore.playback != PassagePlayback.PAUSED || !beforeRestore.canSeek ||
                abs(beforeRestore.positionMillis - current.expectedPosition) > POSITION_TOLERANCE) {
                throw PassageInterrupted(when {
                    beforeRestore.playback != PassagePlayback.PAUSED -> SongPassageReason.PLAYBACK_CHANGED
                    !beforeRestore.canSeek -> SongPassageReason.CAPABILITY_CHANGED
                    else -> SongPassageReason.POSITION_DRIFT
                })
            }
            enter(current, SongPassageStage.RESTORE_SEEK, initial.positionMillis)
            session.seekTo(initial.positionMillis)
            val restored = await(current) { it.playback == PassagePlayback.PAUSED &&
                abs(it.positionMillis - initial.positionMillis) <= POSITION_TOLERANCE }
            if (initial.playback == PassagePlayback.PLAYING) {
                if (!restored.canPlay) throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
                checked(current)
                current.expectedPosition = initial.positionMillis
                current.expectedAt = clock.nowMillis()
                current.expectsPlaying = true
                current.pendingPlay = true
                enter(current, SongPassageStage.RESTORE_PLAY)
                session.play()
                val resumed = await(current) { it.playback == PassagePlayback.PLAYING }
                if (!isOwnedPlaying(current, resumed)) throw PassageInterrupted(playbackFailure(resumed))
                current.pendingPlay = false
            }
            completed = true
            report(current, SongPassageReason.FINISHED)
            return SongPassageResult(SongPassageStatus.FINISHED, playbackRestored = true)
        } catch (cancelled: CancellationException) {
            report(current, SongPassageReason.CANCELLED)
            throw cancelled
        } catch (interrupted: PassageInterrupted) {
            report(current, interrupted.reason)
            return SongPassageResult(SongPassageStatus.INTERRUPTED)
        } catch (_: RuntimeException) {
            report(current, SongPassageReason.TRANSPORT_ERROR)
            return SongPassageResult(SongPassageStatus.FAILED)
        } finally {
            // Leaving cannot resume music. Cancel our unconfirmed play on the same valid recording,
            // including a wrong reported entrance. Once confirmed, preserve a user seek by requiring
            // our trajectory; that also covers buffering that may otherwise play after we leave.
            if (!completed && current.changedPlayback && current.expectsPlaying) {
                withContext(NonCancellable) {
                    try {
                        enter(current, SongPassageStage.CLEANUP)
                        val snapshot = session.snapshot()
                        snapshot?.let { sampleDiagnostic(current, it) }
                        if (snapshot != null && sameRecording(current.initial, snapshot) &&
                            (isOwnedPlaying(current, snapshot) ||
                                (current.pendingPlay && snapshot.positionMillis in 0..snapshot.durationMillis) ||
                                (snapshot.playback == PassagePlayback.OTHER &&
                                    isOnExpectedTrajectory(current, snapshot)))) {
                            // The pause action may not be advertised until pending play completes.
                            // This command only cancels our outstanding play; it never resumes music.
                            session.pause()
                            report(current, SongPassageReason.CLEANUP_PAUSE_SENT)
                        } else {
                            report(current, when {
                                snapshot == null -> SongPassageReason.SESSION_MISSING
                                !sameRecording(current.initial, snapshot) -> identityFailure(current.initial, snapshot)
                                snapshot.positionMillis !in 0..snapshot.durationMillis -> SongPassageReason.INVALID_POSITION
                                snapshot.playback != PassagePlayback.PLAYING -> SongPassageReason.PLAYBACK_CHANGED
                                else -> SongPassageReason.POSITION_DRIFT
                            })
                        }
                    } catch (_: RuntimeException) { report(current, SongPassageReason.TRANSPORT_ERROR) }
                }
            } else {
                report(current, SongPassageReason.CLEANUP_NOT_REQUIRED)
            }
            if (owner === current) owner = null
        }
    }

    private suspend fun checked(current: Owner): SongPassageSnapshot {
        coroutineContext.ensureActive()
        if (current.stopped || owner !== current) throw PassageInterrupted(SongPassageReason.STOPPED)
        val snapshot = session.snapshot() ?: throw PassageInterrupted(SongPassageReason.SESSION_MISSING)
        sampleDiagnostic(current, snapshot)
        if (!sameRecording(current.initial, snapshot) || snapshot.positionMillis !in 0..snapshot.durationMillis) {
            throw PassageInterrupted(if (!sameRecording(current.initial, snapshot))
                identityFailure(current.initial, snapshot) else SongPassageReason.INVALID_POSITION)
        }
        return snapshot
    }

    private suspend fun await(current: Owner, predicate: (SongPassageSnapshot) -> Boolean): SongPassageSnapshot {
        val until = clock.nowMillis() + CONFIRM_TIMEOUT_MILLIS
        do {
            val snapshot = checked(current)
            if (predicate(snapshot)) return snapshot
            clock.waitMillis(POLL_MILLIS)
        } while (clock.nowMillis() < until)
        throw PassageInterrupted(SongPassageReason.CONFIRM_TIMEOUT)
    }

    private fun isOwnedPlaying(current: Owner, snapshot: SongPassageSnapshot): Boolean =
        snapshot.playback == PassagePlayback.PLAYING && isOnExpectedTrajectory(current, snapshot)

    private fun isOnExpectedTrajectory(current: Owner, snapshot: SongPassageSnapshot): Boolean =
        snapshot.positionMillis in 0..snapshot.durationMillis &&
            abs(snapshot.positionMillis - (current.expectedPosition + clock.nowMillis() - current.expectedAt)) <=
            POSITION_TOLERANCE

    private fun matches(request: SongPassageRequest, snapshot: SongPassageSnapshot): Boolean =
        snapshot.sourcePackage == SPOTIFY_PACKAGE &&
            request.trackTitle.trim().equals(snapshot.title?.trim(), ignoreCase = true) &&
            request.artistName.trim().equals(snapshot.artist?.trim(), ignoreCase = true) &&
            (request.spotifyMediaId == null || request.spotifyMediaId == snapshot.mediaId)

    private fun sameRecording(first: SongPassageSnapshot, next: SongPassageSnapshot): Boolean =
        next.sourcePackage == SPOTIFY_PACKAGE && first.sessionKey == next.sessionKey &&
            first.mediaId == next.mediaId && first.title == next.title && first.artist == next.artist &&
            first.durationMillis == next.durationMillis

    private fun playbackFailure(snapshot: SongPassageSnapshot) = when {
        snapshot.playback != PassagePlayback.PLAYING -> SongPassageReason.PLAYBACK_CHANGED
        snapshot.positionMillis !in 0..snapshot.durationMillis -> SongPassageReason.INVALID_POSITION
        else -> SongPassageReason.POSITION_DRIFT
    }

    private fun identityFailure(first: SongPassageSnapshot, next: SongPassageSnapshot) = when {
        next.sourcePackage != SPOTIFY_PACKAGE -> SongPassageReason.SOURCE_CHANGED
        first.sessionKey != next.sessionKey -> SongPassageReason.SESSION_CHANGED
        first.mediaId != next.mediaId -> SongPassageReason.MEDIA_ID_CHANGED
        first.title != next.title -> SongPassageReason.TITLE_CHANGED
        first.artist != next.artist -> SongPassageReason.ARTIST_CHANGED
        else -> SongPassageReason.DURATION_CHANGED
    }

    private fun enter(current: Owner, stage: SongPassageStage, target: Long? = null) {
        current.stage = stage
        current.stageStartedAt = clock.nowMillis()
        current.diagnosticTarget = target
        current.lastPositionError = null
        report(current, SongPassageReason.ENTERED)
    }

    private fun sampleDiagnostic(current: Owner, snapshot: SongPassageSnapshot) {
        current.lastPlayback = snapshot.playback
        val target = if (current.expectsPlaying) current.expectedPosition + clock.nowMillis() - current.expectedAt
            else current.diagnosticTarget
        current.lastPositionError = target?.let {
            (snapshot.positionMillis - it).coerceIn(-MAX_TRACK_MILLIS, MAX_TRACK_MILLIS)
        }
        current.lastPositionAge = snapshot.positionUpdateAgeMillis?.coerceIn(-MAX_TRACK_MILLIS, MAX_TRACK_MILLIS)
    }

    private fun report(current: Owner?, reason: SongPassageReason) {
        val event = SongPassageDiagnostic(current?.stage ?: SongPassageStage.ADMISSION, reason,
            current?.lastPlayback,
            current?.let { (clock.nowMillis() - it.stageStartedAt).coerceIn(0L, 120_000L) } ?: 0L,
            current?.lastPositionError, current?.lastPositionAge)
        try { diagnosticObserver(event) } catch (_: RuntimeException) { /* Diagnostics never own playback. */ }
    }

    private class PassageInterrupted(val reason: SongPassageReason) : RuntimeException()
    private companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val MAX_TRACK_MILLIS = 24 * 60 * 60 * 1_000L
        const val POSITION_TOLERANCE = 1_500L
        const val CONFIRM_TIMEOUT_MILLIS = 3_000L
        const val POLL_MILLIS = 100L
    }
}

/** Uses the user's existing system grant. No Spotify SDK/key, notification text, or audio capture. */
internal class AndroidSpotifyPassageSession(context: Context) : SongPassageSession {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(appContext, RunMusicNotificationListener::class.java)
    private var selected: SongPassageSnapshot? = null

    private fun controller() = manager.getActiveSessions(listener)
        .filter { it.packageName == "com.spotify.music" }.singleOrNull()

    override fun snapshot(): SongPassageSnapshot? {
        val controller = controller() ?: return null
        val metadata = controller.metadata ?: return null
        val state = controller.playbackState ?: return null
        val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)
        val playing = state.state == PlaybackState.STATE_PLAYING
        val reportAge = if (state.lastPositionUpdateTime > 0)
            SystemClock.elapsedRealtime() - state.lastPositionUpdateTime else null
        val advance = if (playing && state.lastPositionUpdateTime > 0 && state.playbackSpeed == 1f) {
            (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0)
        } else 0L
        return SongPassageSnapshot(
            sessionKey = controller.sessionToken, sourcePackage = controller.packageName,
            mediaId = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST),
            durationMillis = duration, positionMillis = if (state.position < 0) -1 else state.position + advance,
            playback = when {
                playing && state.playbackSpeed == 1f -> PassagePlayback.PLAYING
                state.state == PlaybackState.STATE_PAUSED -> PassagePlayback.PAUSED
                else -> PassagePlayback.OTHER
            },
            canPause = state.actions and PlaybackState.ACTION_PAUSE != 0L,
            canPlay = state.actions and PlaybackState.ACTION_PLAY != 0L,
            canSeek = state.actions and PlaybackState.ACTION_SEEK_TO != 0L,
            positionUpdateAgeMillis = reportAge
        ).also { selected = it }
    }

    private fun transport() = checkNotNull(controller()).also { controller ->
        val expected = checkNotNull(selected)
        val metadata = checkNotNull(controller.metadata)
        check(controller.sessionToken == expected.sessionKey &&
            metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) == expected.mediaId &&
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE) == expected.title &&
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) == expected.artist &&
            metadata.getLong(MediaMetadata.METADATA_KEY_DURATION) == expected.durationMillis)
    }.transportControls
    override fun pause() { transport().pause() }
    override fun seekTo(positionMillis: Long) { transport().seekTo(positionMillis) }
    override fun play() { transport().play() }
}
