package com.runstate.mobile.music

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

private const val MAX_TRACK_MILLIS = 24 * 60 * 60 * 1_000L

/** Spotify refined one observed duration by 1 ms at play; all larger changes remain distinct. */
private fun sameReportedDuration(first: Long, next: Long): Boolean =
    first in 1_000..MAX_TRACK_MILLIS && next in 1_000..MAX_TRACK_MILLIS && abs(first - next) <= 1L

/** Only canonical track identities actually observed on the device may select another recording. */
internal fun canonicalSpotifyTrackUri(value: String?): String? = value?.takeIf {
    it.matches(Regex("spotify:track:[A-Za-z0-9]{22}"))
}

/** Match the listener's canonical ID/URI fallback, retaining old IDs only for current-song use. */
internal fun spotifySessionMediaId(mediaId: String?, mediaUri: String?): String? =
    canonicalSpotifyTrackUri(mediaId) ?: canonicalSpotifyTrackUri(mediaUri) ?: mediaId

/** A short passage, with optional explicit permission to load its observed exact recording. */
internal data class SongPassageRequest(
    val trackTitle: String,
    val artistName: String,
    val startPositionMillis: Long,
    val durationMillis: Long = 10_000,
    val spotifyMediaId: String? = null,
    val allowTrackSwitch: Boolean = false,
    val observedDurationMillis: Long? = null,
    /**
     * The runner's music was playing until RunState paused it for the spoken reflection. A
     * paused original then resumes after the passage, as it would have without the pause.
     */
    val resumePausedOriginal: Boolean = false
) {
    init {
        require(trackTitle.isNotBlank() && artistName.isNotBlank())
        require(startPositionMillis >= 0 && durationMillis in 1_000..10_000)
        require(spotifyMediaId == null || spotifyMediaId.isNotBlank())
        require(observedDurationMillis == null || observedDurationMillis in 1_000..MAX_TRACK_MILLIS)
        require(!allowTrackSwitch || canonicalSpotifyTrackUri(spotifyMediaId) != null && observedDurationMillis != null)
    }
}

internal enum class SongPassageStatus { FINISHED, UNAVAILABLE, INTERRUPTED, FAILED }
internal data class SongPassageResult(val status: SongPassageStatus, val playbackRestored: Boolean = false)
internal enum class PassagePlayback { PLAYING, PAUSED, OTHER }

/** Optional test diagnostics contain enums and bounded timing differences only, never media identity. */
internal enum class SongPassageStage {
    ADMISSION, INITIAL_PAUSE, TRACK_LOAD, ENTRANCE_SEEK, ENTRANCE_PLAY, PLAYING, ENDING_PAUSE,
    RESTORE_TRACK, RESTORE_SEEK, RESTORE_PLAY, CLEANUP
}
internal enum class SongPassageReason {
    ENTERED, FINISHED, OWNER_BUSY, SESSION_MISSING, REQUEST_MISMATCH, INVALID_DURATION,
    INVALID_POSITION, PLAYBACK_CHANGED, POSITION_DRIFT, CAPABILITY_CHANGED, CONFIRM_TIMEOUT,
    STOPPED, CANCELLED, TRANSPORT_ERROR, SOURCE_CHANGED, SESSION_CHANGED, MEDIA_ID_CHANGED,
    TITLE_CHANGED, ARTIST_CHANGED, DURATION_CHANGED, CLEANUP_PAUSE_SENT, CLEANUP_NOT_REQUIRED,
    TRACK_LOAD_UNSUPPORTED, TRACK_LOAD_TIMEOUT
}
internal data class SongPassageDiagnostic(
    val stage: SongPassageStage,
    val reason: SongPassageReason,
    val playback: PassagePlayback? = null,
    val stageElapsedMillis: Long = 0L,
    val positionErrorMillis: Long? = null,
    val positionUpdateAgeMillis: Long? = null,
    /** Bounded change from the admitted duration; no song identity is included. */
    val durationDeltaMillis: Long? = null
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
    val positionUpdateAgeMillis: Long? = null,
    val canPrepareTrack: Boolean = false,
    val canPlayTrack: Boolean = false
)

internal interface SongPassageSession {
    /** The observed exact URI can stabilize provider-specific title/artist presentation. */
    fun expectTrack(request: SongPassageRequest) = Unit
    /** Refresh optional asynchronous transports before the controller takes its baseline. */
    suspend fun prepareForPlayback() = Unit
    /** Null means missing/ambiguous/unreadable access, never a fabricated current song. */
    fun snapshot(): SongPassageSnapshot?
    fun pause()
    fun seekTo(positionMillis: Long)
    fun play()
    /** True means a supported command was dispatched, not that the requested song is loaded. */
    fun prepareFromTrackUri(uri: String): Boolean = false
    fun playFromTrackUri(uri: String): Boolean = false
    /** Best-effort cancellation of our pending selection; a foreign player can ignore this request. */
    fun cancelPendingTrackLoad() { pause() }
    /** Release a transport lease only after bounded containment of an outstanding load. */
    suspend fun release() = Unit
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
 * A completed passage restores the prior recording, position and playing/paused state. A failed
 * entrance seek can recover that state only while the selected recording is still owned/paused.
 * No position, title, URI, or provider exception is logged. Timings are software bounds, not a
 * guarantee of sample-accurate audible playback from another app.
 */
internal class SpotifySongPassageController(
    private val session: SongPassageSession,
    private val clock: PassageClock = RealPassageClock,
    private val onEntranceReady: () -> Unit = {},
    private val quiescePausedBeforeEntry: Boolean = false,
    private val diagnosticObserver: (SongPassageDiagnostic) -> Unit = {}
) {
    private class Owner(var initial: SongPassageSnapshot) {
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
        var lastDurationDelta: Long? = null
        var failureReason: SongPassageReason? = null
        var switchedTrack = false
        var restoreAttempted = false
        var ownedPausedPosition = initial.positionMillis
        var loadingFrom: SongPassageSnapshot? = null
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
        val sameRequestedTrack = matches(request, initial)
        val maySwitch = !sameRequestedTrack && request.allowTrackSwitch &&
            canonicalSpotifyTrackUri(request.spotifyMediaId) != null &&
            canonicalSpotifyTrackUri(initial.mediaId) != null && request.spotifyMediaId != initial.mediaId &&
            initial.sourcePackage == SPOTIFY_PACKAGE && (initial.canPrepareTrack || initial.canPlayTrack)
        val targetDuration = if (sameRequestedTrack) initial.durationMillis else request.observedDurationMillis ?: 0L
        if ((!sameRequestedTrack && !maySwitch) || initial.durationMillis !in 1_000..MAX_TRACK_MILLIS ||
            targetDuration !in 1_000..MAX_TRACK_MILLIS ||
            (sameRequestedTrack && request.observedDurationMillis?.let { !sameReportedDuration(it, initial.durationMillis) } == true) ||
            initial.positionMillis !in 0..initial.durationMillis ||
            initial.playback == PassagePlayback.OTHER || !initial.canSeek ||
            (initial.playback == PassagePlayback.PLAYING && !initial.canPause) ||
            request.startPositionMillis > targetDuration - request.durationMillis - 2_000) {
            val reason = when {
                !sameRequestedTrack && !maySwitch -> if (request.allowTrackSwitch &&
                    !initial.canPrepareTrack && !initial.canPlayTrack) SongPassageReason.TRACK_LOAD_UNSUPPORTED
                    else SongPassageReason.REQUEST_MISMATCH
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
        // Restoration target: the prior song and position, playing again if it was paused only for us.
        val restoreTarget = if (request.resumePausedOriginal && initial.playback == PassagePlayback.PAUSED)
            initial.copy(playback = PassagePlayback.PLAYING) else initial
        val current = Owner(initial).also { it.stageStartedAt = clock.nowMillis() }
        owner = current
        var completed = false
        try {
            if (initial.playback == PassagePlayback.PLAYING || quiescePausedBeforeEntry) {
                enter(current, SongPassageStage.INITIAL_PAUSE)
                if (!checked(current).canPause && initial.playback == PassagePlayback.PLAYING)
                    throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
                current.changedPlayback = true
                session.pause()
                await(current) { it.playback == PassagePlayback.PAUSED }
            }
            if (maySwitch) {
                val beforeLoad = checked(current)
                if (beforeLoad.playback != PassagePlayback.PAUSED ||
                    abs(beforeLoad.positionMillis - initial.positionMillis) > POSITION_TOLERANCE)
                    throw PassageInterrupted(SongPassageReason.POSITION_DRIFT)
                val expected = beforeLoad.copy(mediaId = request.spotifyMediaId, title = request.trackTitle,
                    artist = request.artistName, durationMillis = targetDuration, positionMillis = 0L)
                loadTrack(current, beforeLoad, expected, SongPassageStage.TRACK_LOAD)
                current.switchedTrack = true
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
            // The speech platform retains focus while this exact recording is loaded/paused
            // and sought. Release only at the deliberate entrance, not before our baseline.
            onEntranceReady()
            coroutineContext.ensureActive()
            if (current.stopped || owner !== current) throw PassageInterrupted(SongPassageReason.STOPPED)
            current.expectedPosition = request.startPositionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            current.pendingPlay = true
            enter(current, SongPassageStage.ENTRANCE_PLAY)
            session.play()
            val playing = await(current) { it.playback == PassagePlayback.PLAYING }
            if (!isConfirmedPlay(current, playing)) throw PassageInterrupted(playbackFailure(playing))
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
            restoreOriginal(current, restoreTarget)
            completed = true
            report(current, SongPassageReason.FINISHED)
            return SongPassageResult(SongPassageStatus.FINISHED, playbackRestored = true)
        } catch (cancelled: CancellationException) {
            report(current, SongPassageReason.CANCELLED)
            throw cancelled
        } catch (interrupted: PassageInterrupted) {
            current.failureReason = interrupted.reason
            report(current, interrupted.reason)
            completed = recoverPausedSelection(current, restoreTarget, interrupted.reason)
            return SongPassageResult(SongPassageStatus.INTERRUPTED, playbackRestored = completed)
        } catch (_: RuntimeException) {
            current.failureReason = SongPassageReason.TRANSPORT_ERROR
            report(current, SongPassageReason.TRANSPORT_ERROR)
            completed = recoverPausedSelection(current, restoreTarget, SongPassageReason.TRANSPORT_ERROR)
            return SongPassageResult(SongPassageStatus.FAILED, playbackRestored = completed)
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
                        val durationOnlyContainment = snapshot != null &&
                            current.failureReason == SongPassageReason.DURATION_CHANGED &&
                            !current.initial.mediaId.isNullOrBlank() &&
                            sameIdentity(current.initial, snapshot) &&
                            snapshot.durationMillis in 1_000..MAX_TRACK_MILLIS &&
                            (current.pendingPlay || snapshot.playback == PassagePlayback.PLAYING ||
                                snapshot.playback == PassagePlayback.OTHER) &&
                            isOnExpectedTrajectory(current, snapshot)
                        val pendingSelectionContainment = snapshot != null && current.loadingFrom?.let { before ->
                            sameRecording(before, snapshot) && snapshot.playback != PassagePlayback.PLAYING &&
                                (snapshot.playback == PassagePlayback.OTHER ||
                                    abs(snapshot.positionMillis - before.positionMillis) <= POSITION_TOLERANCE)
                        } == true
                        val pendingTargetContainment = snapshot != null && current.loadingFrom != null &&
                            snapshot.sourcePackage == SPOTIFY_PACKAGE && snapshot.sessionKey == current.initial.sessionKey &&
                            canonicalSpotifyTrackUri(snapshot.mediaId) != null && snapshot.mediaId == current.initial.mediaId &&
                            (snapshot.positionMillis in 0..((clock.nowMillis() - current.expectedAt)
                                .coerceAtLeast(0L) + POSITION_TOLERANCE) ||
                                snapshot.playback == PassagePlayback.OTHER && snapshot.positionMillis < 0L)
                        if (snapshot != null && (pendingSelectionContainment || pendingTargetContainment || durationOnlyContainment ||
                            (sameRecording(current.initial, snapshot) &&
                            (isOwnedPlaying(current, snapshot) ||
                                (current.pendingPlay && current.loadingFrom == null &&
                                    snapshot.positionMillis in 0..snapshot.durationMillis) ||
                                (snapshot.playback == PassagePlayback.OTHER &&
                                    isOnExpectedTrajectory(current, snapshot)))))) {
                            // The pause action may not be advertised until pending play completes.
                            // This command only cancels our outstanding play; it never resumes music.
                            // A duration-only failure still aborts the clip. It may cancel our own
                            // trajectory on the otherwise identical recording, never restore it.
                            if (pendingSelectionContainment || pendingTargetContainment) session.cancelPendingTrackLoad()
                            else session.pause()
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

    /** Loading is a request, not proof. Only the exact requested recording can acquire ownership. */
    private suspend fun loadTrack(current: Owner, before: SongPassageSnapshot,
        expected: SongPassageSnapshot, stage: SongPassageStage) {
        coroutineContext.ensureActive()
        if (current.stopped || owner !== current) throw PassageInterrupted(SongPassageReason.STOPPED)
        val uri = canonicalSpotifyTrackUri(expected.mediaId)
            ?: throw PassageInterrupted(SongPassageReason.TRACK_LOAD_UNSUPPORTED)
        if (!before.canPrepareTrack && !before.canPlayTrack)
            throw PassageInterrupted(SongPassageReason.TRACK_LOAD_UNSUPPORTED)
        enter(current, stage)
        current.initial = expected
        current.changedPlayback = true
        // Even prepare implementations can misbehave; cleanup may pause only this exact target.
        current.pendingPlay = true
        current.expectsPlaying = true
        current.expectedPosition = 0L
        current.expectedAt = clock.nowMillis()
        current.loadingFrom = before
        val sent = if (before.canPrepareTrack) session.prepareFromTrackUri(uri) else session.playFromTrackUri(uri)
        if (!sent) throw PassageInterrupted(SongPassageReason.TRACK_LOAD_UNSUPPORTED)
        val until = clock.nowMillis() + TRACK_LOAD_TIMEOUT_MILLIS
        do {
            coroutineContext.ensureActive()
            if (current.stopped || owner !== current) throw PassageInterrupted(SongPassageReason.STOPPED)
            val snapshot = session.snapshot()
            if (snapshot != null) {
                sampleDiagnostic(current, snapshot)
                if (snapshot.sourcePackage != SPOTIFY_PACKAGE || snapshot.sessionKey != before.sessionKey)
                    throw PassageInterrupted(identityFailure(before, snapshot))
                if (snapshot.mediaId == expected.mediaId) {
                    // Loading metadata can arrive in pieces. It still cannot authorize a seek
                    // or a passage until title, artist, duration and position are all confirmed.
                    if (snapshot.title.isNullOrBlank() || snapshot.artist.isNullOrBlank() ||
                        snapshot.durationMillis == 0L || snapshot.positionMillis < 0L) {
                        clock.waitMillis(POLL_MILLIS)
                        continue
                    }
                    if (!expected.title?.trim().equals(snapshot.title?.trim(), ignoreCase = true) ||
                        !expected.artist?.trim().equals(snapshot.artist?.trim(), ignoreCase = true))
                        throw PassageInterrupted(identityFailure(expected, snapshot))
                    // Keep the observed duration as the fixed baseline; tolerance cannot accumulate.
                    current.initial = expected.copy(title = snapshot.title, artist = snapshot.artist)
                    if (!sameReportedDuration(expected.durationMillis, snapshot.durationMillis))
                        throw PassageInterrupted(SongPassageReason.DURATION_CHANGED)
                    if (snapshot.positionMillis in 0..snapshot.durationMillis) {
                        // A distant seek while metadata arrives belongs to the user. Matching
                        // the recording alone does not authorize replacing their position.
                        if (!isAtLoadedEntrance(current, snapshot))
                            throw PassageInterrupted(SongPassageReason.POSITION_DRIFT)
                        val paused = if (snapshot.playback == PassagePlayback.PAUSED) snapshot else {
                            // playFrom... can begin audibly before metadata arrives. Contain only the
                            // verified requested recording, then seek to the deliberate passage.
                            session.pause()
                            await(current) { it.playback == PassagePlayback.PAUSED }
                        }
                        if (!isAtLoadedEntrance(current, paused))
                            throw PassageInterrupted(SongPassageReason.POSITION_DRIFT)
                        current.pendingPlay = false
                        current.expectsPlaying = false
                        current.expectedPosition = paused.positionMillis
                        current.ownedPausedPosition = paused.positionMillis
                        current.loadingFrom = null
                        return
                    }
                } else if (!sameRecording(before, snapshot)) {
                    throw PassageInterrupted(identityFailure(before, snapshot))
                } else if (snapshot.playback == PassagePlayback.PLAYING ||
                    snapshot.playback == PassagePlayback.PAUSED &&
                    abs(snapshot.positionMillis - before.positionMillis) > POSITION_TOLERANCE) {
                    // A user resumed or sought the previous recording while loading. They win.
                    throw PassageInterrupted(playbackFailure(snapshot))
                }
            }
            clock.waitMillis(POLL_MILLIS)
        } while (clock.nowMillis() < until)
        throw PassageInterrupted(SongPassageReason.TRACK_LOAD_TIMEOUT)
    }

    private suspend fun restoreOriginal(current: Owner, original: SongPassageSnapshot) {
        current.restoreAttempted = true
        if (current.switchedTrack) {
            val before = checked(current)
            if (before.playback != PassagePlayback.PAUSED)
                throw PassageInterrupted(SongPassageReason.PLAYBACK_CHANGED)
            loadTrack(current, before, original, SongPassageStage.RESTORE_TRACK)
        }
        val paused = checked(current)
        if (paused.playback != PassagePlayback.PAUSED || !paused.canSeek)
            throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
        enter(current, SongPassageStage.RESTORE_SEEK, original.positionMillis)
        session.seekTo(original.positionMillis)
        val restored = await(current) { it.playback == PassagePlayback.PAUSED &&
            abs(it.positionMillis - original.positionMillis) <= POSITION_TOLERANCE }
        if (original.playback == PassagePlayback.PLAYING) {
            if (!restored.canPlay) throw PassageInterrupted(SongPassageReason.CAPABILITY_CHANGED)
            checked(current)
            current.expectedPosition = original.positionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            current.pendingPlay = true
            enter(current, SongPassageStage.RESTORE_PLAY)
            session.play()
            val resumed = await(current) { it.playback == PassagePlayback.PLAYING }
            if (!isConfirmedPlay(current, resumed)) throw PassageInterrupted(playbackFailure(resumed))
            current.pendingPlay = false
            current.expectedPosition = resumed.positionMillis
            current.expectedAt = clock.nowMillis()
        }
    }

    /** A failed entrance seek may recover the original song only while our target remains paused. */
    private suspend fun recoverPausedSelection(current: Owner, original: SongPassageSnapshot,
        reason: SongPassageReason): Boolean {
        if (!current.switchedTrack || current.restoreAttempted || current.stopped || current.expectsPlaying ||
            current.stage !in setOf(SongPassageStage.TRACK_LOAD, SongPassageStage.ENTRANCE_SEEK) ||
            reason !in setOf(SongPassageReason.TRANSPORT_ERROR, SongPassageReason.CONFIRM_TIMEOUT,
                SongPassageReason.CAPABILITY_CHANGED)) return false
        return try {
            val snapshot = checked(current)
            val nearOwnedPosition = abs(snapshot.positionMillis - current.ownedPausedPosition) <= POSITION_TOLERANCE ||
                current.diagnosticTarget?.let { abs(snapshot.positionMillis - it) <= POSITION_TOLERANCE } == true
            if (snapshot.playback != PassagePlayback.PAUSED || !nearOwnedPosition) return false
            restoreOriginal(current, original)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            false
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

    private fun isAtLoadedEntrance(current: Owner, snapshot: SongPassageSnapshot): Boolean =
        snapshot.positionMillis in 0..((clock.nowMillis() - current.expectedAt).coerceAtLeast(0L) +
            POSITION_TOLERANCE)

    private fun isConfirmedPlay(current: Owner, snapshot: SongPassageSnapshot): Boolean {
        // A transport command does not mean audio started immediately. The first PLAYING
        // report may describe any progress from the requested seek through the bounded command
        // wait. Admit that interval once, then follow the confirmed position with the ordinary
        // drift guard. Keep the requested target here so seek tolerance cannot accumulate.
        val commandElapsed = (clock.nowMillis() - current.expectedAt)
            .coerceIn(0L, CONFIRM_TIMEOUT_MILLIS)
        return snapshot.playback == PassagePlayback.PLAYING &&
            snapshot.positionMillis in 0..snapshot.durationMillis &&
            snapshot.positionMillis >= current.expectedPosition - POSITION_TOLERANCE &&
            snapshot.positionMillis <= current.expectedPosition + commandElapsed + POSITION_TOLERANCE
    }

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
        sameIdentity(first, next) && sameReportedDuration(first.durationMillis, next.durationMillis)

    private fun sameIdentity(first: SongPassageSnapshot, next: SongPassageSnapshot): Boolean =
        next.sourcePackage == SPOTIFY_PACKAGE && first.sessionKey == next.sessionKey &&
            first.mediaId == next.mediaId && first.title == next.title && first.artist == next.artist

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
        current.lastDurationDelta = snapshot.durationMillis.coerceIn(0L, MAX_TRACK_MILLIS) -
            current.initial.durationMillis
    }

    private fun report(current: Owner?, reason: SongPassageReason) {
        val event = SongPassageDiagnostic(current?.stage ?: SongPassageStage.ADMISSION, reason,
            current?.lastPlayback,
            current?.let { (clock.nowMillis() - it.stageStartedAt).coerceIn(0L, 120_000L) } ?: 0L,
            current?.lastPositionError, current?.lastPositionAge, current?.lastDurationDelta)
        try { diagnosticObserver(event) } catch (_: RuntimeException) { /* Diagnostics never own playback. */ }
    }

    private class PassageInterrupted(val reason: SongPassageReason) : RuntimeException()
    private companion object {
        const val SPOTIFY_PACKAGE = "com.spotify.music"
        const val POSITION_TOLERANCE = 1_500L
        const val CONFIRM_TIMEOUT_MILLIS = 3_000L
        const val TRACK_LOAD_TIMEOUT_MILLIS = 5_000L
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
            mediaId = spotifySessionMediaId(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)),
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
            positionUpdateAgeMillis = reportAge,
            canPrepareTrack = state.actions and (PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID or
                PlaybackState.ACTION_PREPARE_FROM_URI) != 0L,
            canPlayTrack = state.actions and (PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackState.ACTION_PLAY_FROM_URI) != 0L
        ).also { selected = it }
    }

    private fun transport(allowUnconfirmedDuration: Boolean = false) = checkNotNull(controller()).also { controller ->
        val expected = checkNotNull(selected)
        val metadata = checkNotNull(controller.metadata)
        check(controller.sessionToken == expected.sessionKey &&
            spotifySessionMediaId(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI)) == expected.mediaId &&
            metadata.getString(MediaMetadata.METADATA_KEY_TITLE) == expected.title &&
            metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) == expected.artist &&
            (sameReportedDuration(expected.durationMillis, metadata.getLong(MediaMetadata.METADATA_KEY_DURATION)) ||
                allowUnconfirmedDuration && canonicalSpotifyTrackUri(expected.mediaId) != null))
    }.transportControls
    override fun pause() { transport().pause() }
    override fun seekTo(positionMillis: Long) { transport().seekTo(positionMillis) }
    override fun play() { transport().play() }
    override fun cancelPendingTrackLoad() { transport(allowUnconfirmedDuration = true).pause() }
    override fun prepareFromTrackUri(uri: String): Boolean {
        require(canonicalSpotifyTrackUri(uri) != null)
        val transport = transport()
        val actions = checkNotNull(controller()?.playbackState).actions
        return when {
            actions and PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID != 0L -> {
                transport.prepareFromMediaId(uri, null); true
            }
            actions and PlaybackState.ACTION_PREPARE_FROM_URI != 0L -> {
                transport.prepareFromUri(Uri.parse(uri), null); true
            }
            else -> false
        }
    }
    override fun playFromTrackUri(uri: String): Boolean {
        require(canonicalSpotifyTrackUri(uri) != null)
        val transport = transport()
        val actions = checkNotNull(controller()?.playbackState).actions
        return when {
            actions and PlaybackState.ACTION_PLAY_FROM_MEDIA_ID != 0L -> {
                transport.playFromMediaId(uri, null); true
            }
            actions and PlaybackState.ACTION_PLAY_FROM_URI != 0L -> {
                transport.playFromUri(Uri.parse(uri), null); true
            }
            else -> false
        }
    }
}
