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
    val canSeek: Boolean
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
    private val clock: PassageClock = RealPassageClock
) {
    private class Owner(val initial: SongPassageSnapshot) {
        @Volatile var stopped = false
        var changedPlayback = false
        var expectedPosition = initial.positionMillis
        var expectedAt = 0L
        var expectsPlaying = false
        var pendingPlay = false
    }
    private var owner: Owner? = null

    fun stop() { owner?.stopped = true }

    suspend fun play(request: SongPassageRequest): SongPassageResult {
        if (owner != null) return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        val initial = try { session.snapshot() } catch (_: RuntimeException) { null }
            ?: return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        if (!matches(request, initial) || initial.durationMillis !in 1_000..MAX_TRACK_MILLIS ||
            initial.positionMillis !in 0..initial.durationMillis ||
            initial.playback == PassagePlayback.OTHER || !initial.canSeek ||
            (initial.playback == PassagePlayback.PLAYING && !initial.canPause) ||
            request.startPositionMillis > initial.durationMillis - request.durationMillis - 2_000) {
            return SongPassageResult(SongPassageStatus.UNAVAILABLE)
        }
        val current = Owner(initial)
        owner = current
        var completed = false
        try {
            if (initial.playback == PassagePlayback.PLAYING) {
                if (!checked(current).canPause) throw PassageInterrupted()
                current.changedPlayback = true
                session.pause()
                await(current) { it.playback == PassagePlayback.PAUSED }
            }
            var paused = checked(current)
            if (paused.playback != PassagePlayback.PAUSED || !paused.canSeek || !paused.canPlay) {
                throw PassageInterrupted()
            }
            current.changedPlayback = true
            session.seekTo(request.startPositionMillis)
            paused = await(current) { it.playback == PassagePlayback.PAUSED &&
                abs(it.positionMillis - request.startPositionMillis) <= POSITION_TOLERANCE }
            if (!paused.canPlay) throw PassageInterrupted()
            checked(current)
            current.expectedPosition = request.startPositionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            current.pendingPlay = true
            session.play()
            val playing = await(current) { it.playback == PassagePlayback.PLAYING }
            if (!isOwnedPlaying(current, playing)) throw PassageInterrupted()
            current.pendingPlay = false
            current.expectedPosition = playing.positionMillis
            current.expectedAt = clock.nowMillis()
            current.expectsPlaying = true
            val finishAt = current.expectedAt + request.durationMillis
            while (clock.nowMillis() < finishAt) {
                val snapshot = checked(current)
                if (!isOwnedPlaying(current, snapshot)) throw PassageInterrupted()
                clock.waitMillis(minOf(POLL_MILLIS, finishAt - clock.nowMillis()).coerceAtLeast(1))
            }
            val finalPlaying = checked(current)
            if (!isOwnedPlaying(current, finalPlaying) || !finalPlaying.canPause) throw PassageInterrupted()
            session.pause()
            val ended = await(current) { it.playback == PassagePlayback.PAUSED }
            current.expectsPlaying = false
            current.expectedPosition = ended.positionMillis
            val beforeRestore = checked(current)
            if (beforeRestore.playback != PassagePlayback.PAUSED || !beforeRestore.canSeek ||
                abs(beforeRestore.positionMillis - current.expectedPosition) > POSITION_TOLERANCE) {
                throw PassageInterrupted()
            }
            session.seekTo(initial.positionMillis)
            val restored = await(current) { it.playback == PassagePlayback.PAUSED &&
                abs(it.positionMillis - initial.positionMillis) <= POSITION_TOLERANCE }
            if (initial.playback == PassagePlayback.PLAYING) {
                if (!restored.canPlay) throw PassageInterrupted()
                checked(current)
                current.expectedPosition = initial.positionMillis
                current.expectedAt = clock.nowMillis()
                current.expectsPlaying = true
                current.pendingPlay = true
                session.play()
                val resumed = await(current) { it.playback == PassagePlayback.PLAYING }
                if (!isOwnedPlaying(current, resumed)) throw PassageInterrupted()
                current.pendingPlay = false
            }
            completed = true
            return SongPassageResult(SongPassageStatus.FINISHED, playbackRestored = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: PassageInterrupted) {
            return SongPassageResult(SongPassageStatus.INTERRUPTED)
        } catch (_: RuntimeException) {
            return SongPassageResult(SongPassageStatus.FAILED)
        } finally {
            // Leaving the screen/focus loss cannot start music again. Do not touch a changed song
            // or a user seek. Also cancel a sent play still paused/buffering at our position:
            // waiting for PLAYING before cancelling would let delayed playback escape ownership.
            if (!completed && current.changedPlayback && current.expectsPlaying) {
                withContext(NonCancellable) {
                    try {
                        val snapshot = session.snapshot()
                        if (snapshot != null && sameRecording(current.initial, snapshot) &&
                            ((isOwnedPlaying(current, snapshot) && snapshot.canPause) ||
                                (current.pendingPlay && snapshot.positionMillis in 0..snapshot.durationMillis &&
                                    abs(snapshot.positionMillis - current.expectedPosition) <= POSITION_TOLERANCE))) {
                            // The pause action may not be advertised until pending play completes.
                            // This command only cancels our outstanding play; it never resumes music.
                            session.pause()
                        }
                    } catch (_: RuntimeException) { /* Spotify may already have disconnected. */ }
                }
            }
            if (owner === current) owner = null
        }
    }

    private suspend fun checked(current: Owner): SongPassageSnapshot {
        coroutineContext.ensureActive()
        if (current.stopped || owner !== current) throw PassageInterrupted()
        val snapshot = session.snapshot() ?: throw PassageInterrupted()
        if (!sameRecording(current.initial, snapshot) || snapshot.positionMillis !in 0..snapshot.durationMillis) {
            throw PassageInterrupted()
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
        throw PassageInterrupted()
    }

    private fun isOwnedPlaying(current: Owner, snapshot: SongPassageSnapshot): Boolean =
        snapshot.playback == PassagePlayback.PLAYING && snapshot.positionMillis in 0..snapshot.durationMillis &&
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

    private class PassageInterrupted : RuntimeException()
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
            canSeek = state.actions and PlaybackState.ACTION_SEEK_TO != 0L
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
