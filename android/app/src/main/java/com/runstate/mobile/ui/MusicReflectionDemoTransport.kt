package com.runstate.mobile.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.runstate.mobile.R
import com.runstate.mobile.music.AndroidSpotifyPassageSession
import com.runstate.mobile.music.PassagePlayback
import com.runstate.mobile.music.SongPassageReason
import com.runstate.mobile.music.SongPassageSession
import com.runstate.mobile.music.SongPassageStage
import com.runstate.mobile.music.SongPassageStatus
import com.runstate.mobile.music.SpotifyAppRemoteConnection
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.music.SpotifyRemoteConnectionStatus
import com.runstate.mobile.music.SpotifySongPassageController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface MusicReflectionDemoTransport {
    val player: StateFlow<SpotifyLivePlayerState>
    /** User-triggered only. Returns after playback and the transport lease have finished cleanup. */
    suspend fun playOpening(): SongPassageStatus
    /** Signals interruption; callers await playOpening's job before starting another audio owner. */
    fun stop()
    fun close()
}

/** Owns only the explicitly selected demo passage, never a run or a persistent setting. */
internal class AndroidMusicReflectionDemoTransport(context: Context) : MusicReflectionDemoTransport {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val spotify = SpotifyAppRemoteConnection.get(appContext)
    private val preset = SpotifyLivePlayerState(
        connected = true, canConnect = false,
        trackUri = MusicReflectionDemoData.trackUri,
        title = "Good as Hell", artist = "Lizzo", album = "Cuz I Love You (Super Deluxe)",
        artwork = BitmapFactory.decodeResource(appContext.resources, R.drawable.demo_lizzo_cover),
        durationMillis = MusicReflectionDemoData.passage().observedDurationMillis ?: 0L,
        paused = true, canSkipPrevious = true, canSkipNext = true, canTogglePlayback = true
    )
    private val mutablePlayer = MutableStateFlow(preset)
    override val player: StateFlow<SpotifyLivePlayerState> = mutablePlayer.asStateFlow()
    private var operation: Deferred<SongPassageStatus>? = null
    private var controller: SpotifySongPassageController? = null
    private var closed = false

    override suspend fun playOpening(): SongPassageStatus = withContext(Dispatchers.Main.immediate) {
        if (closed || operation != null) return@withContext SongPassageStatus.UNAVAILABLE
        coroutineScope {
            val next = async(start = CoroutineStart.LAZY) { playOwnedOpening() }
            operation = next
            next.start()
            try {
                next.await()
            } catch (cancelled: CancellationException) {
                // A local Stop finishes normally; cancellation of the owning screen propagates.
                currentCoroutineContext().ensureActive()
                SongPassageStatus.INTERRUPTED
            } finally {
                withContext(NonCancellable) {
                    next.cancelAndJoin()
                    if (operation === next) operation = null
                }
            }
        }
    }

    private suspend fun playOwnedOpening(): SongPassageStatus = coroutineScope {
        val owner = Any()
        var acquired = false
        var session: SongPassageSession? = null
        var confirmedPlaying = false
        var monitor: kotlinx.coroutines.Job? = null
        mutablePlayer.value = preset.copy(busy = true)
        try {
            // Only an already remembered authorization can reconnect. This opens no login UI.
            spotify.connectSilently()
            val deadline = SystemClock.elapsedRealtime() + 20_000L
            while (spotify.state.value.status == SpotifyRemoteConnectionStatus.CONNECTING &&
                SystemClock.elapsedRealtime() < deadline) delay(50)
            if (com.runstate.mobile.BuildConfig.DEBUG) android.util.Log.d("RunStateDemo",
                "Spotify status=${spotify.state.value.status} onDemand=${spotify.state.value.canPlayOnDemand}")
            currentCoroutineContext().ensureActive()
            if (closed) return@coroutineScope SongPassageStatus.INTERRUPTED
            acquired = spotify.tryAcquireAudioOwnership(owner)
            if (!acquired) return@coroutineScope SongPassageStatus.UNAVAILABLE

            val remoteReady = spotify.state.value.let {
                it.status == SpotifyRemoteConnectionStatus.READY && it.canPlayOnDemand
            }
            session = if (remoteReady) {
                // A busy remote lease must never be bypassed via the native transport.
                spotify.newPassageSession() ?: return@coroutineScope SongPassageStatus.UNAVAILABLE
            } else {
                // Native fallback can control only an already-current, exact Lizzo recording.
                AndroidSpotifyPassageSession(appContext).takeIf { candidate ->
                    candidate.snapshot()?.let { snapshot ->
                        snapshot.sourcePackage == "com.spotify.music" &&
                            snapshot.mediaId == MusicReflectionDemoData.trackUri
                    } == true
                } ?: return@coroutineScope SongPassageStatus.UNAVAILABLE
            }
            val owned = requireNotNull(session)
            owned.prepareForPlayback()
            currentCoroutineContext().ensureActive()
            val initial = owned.snapshot() ?: return@coroutineScope SongPassageStatus.UNAVAILABLE
            val duration = if (initial.mediaId == MusicReflectionDemoData.trackUri)
                initial.durationMillis else requireNotNull(MusicReflectionDemoData.passage().observedDurationMillis)
            val requested = MusicReflectionDemoData.passage(duration).copy(allowTrackSwitch = remoteReady)
            owned.expectTrack(requested)
            val activeController = SpotifySongPassageController(
                owned, quiescePausedBeforeEntry = true,
                diagnosticObserver = { event ->
                    if (com.runstate.mobile.BuildConfig.DEBUG) android.util.Log.d("RunStateDemo",
                        "Passage ${event.stage} ${event.reason}")
                    // Merely opening the display or loading a track never claims audible playback.
                    if (event.reason == SongPassageReason.ENTERED)
                        confirmedPlaying = event.stage == SongPassageStage.PLAYING
                }
            )
            controller = activeController
            monitor = launch {
                while (isActive) {
                    if (confirmedPlaying) {
                        val observed = try { owned.snapshot() } catch (_: RuntimeException) { null }
                        if (observed?.mediaId == MusicReflectionDemoData.trackUri &&
                            observed.sourcePackage == "com.spotify.music") {
                            val live = spotify.livePlayer.value.takeIf {
                                it.trackUri == MusicReflectionDemoData.trackUri
                            }
                            val playing = observed.playback == PassagePlayback.PLAYING
                            mutablePlayer.value = preset.copy(
                                artwork = live?.artwork ?: preset.artwork,
                                durationMillis = observed.durationMillis,
                                positionMillis = observed.positionMillis,
                                positionReceivedAtElapsedRealtime = SystemClock.elapsedRealtime(),
                                paused = !playing, playbackSpeed = if (playing) 1f else 0f
                            )
                        } else mutablePlayer.value = pausedPlayer()
                    } else if (!mutablePlayer.value.paused) mutablePlayer.value = pausedPlayer()
                    delay(50)
                }
            }
            activeController.play(requested).status
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: RuntimeException) {
            SongPassageStatus.FAILED
        } finally {
            // A subsequent facts/reflection speaker cannot race a cancelled seek/load callback.
            withContext(NonCancellable) {
                monitor?.cancelAndJoin()
                try { session?.release() } catch (_: RuntimeException) { }
                controller = null
                if (acquired) spotify.releaseAudioOwnership(owner)
                mutablePlayer.value = pausedPlayer()
            }
        }
    }

    private fun pausedPlayer(): SpotifyLivePlayerState = mutablePlayer.value.copy(
        paused = true, playbackSpeed = 0f, busy = false,
        positionReceivedAtElapsedRealtime = SystemClock.elapsedRealtime()
    )

    override fun stop() = onMain {
        controller?.stop()
        operation?.cancel()
        mutablePlayer.value = pausedPlayer()
    }

    override fun close() = onMain {
        closed = true
        stop()
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post(action)
    }
}
