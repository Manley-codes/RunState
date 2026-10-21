package com.runstate.mobile.music

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.data.local.MusicObservationKind
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.RunMusicObservation
import com.runstate.mobile.data.local.StoredRunState
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Android grants notification-listener access to discover active media sessions. This
 * implementation does not inspect notifications, control playback, or collect while no
 * single opted-in RUNNING run exists. A callback is only evidence of what another app
 * reported at that instant; it cannot prove a whole song or complete soundtrack.
 */
internal class RunMusicNotificationListener : NotificationListenerService() {
    private val app: RunStateApplication get() = application as RunStateApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val callbackHandler = Handler(Looper.getMainLooper())
    private val component by lazy { ComponentName(this, RunMusicNotificationListener::class.java) }
    private val sessionManager by lazy { getSystemService(MediaSessionManager::class.java) }

    private var runObserver: Job? = null
    private var runId: String? = null
    private var captureJob: Job? = null
    private var captureScope: CoroutineScope? = null
    private var sessionsChanged: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val playbackIntervals = SongPlaybackIntervalTracker()
    private val controllers = MusicSessionRegistry<WatchedController>(
        onObservation = { ownerRunId, watched, kind -> capture(ownerRunId, watched, kind) },
        onControllerFailure = {
            playbackIntervals.reset()
            Log.w(TAG, "A media controller did not provide a usable observation.")
        },
        onAccessLost = { stopCapture() },
        onContinuityLost = { playbackIntervals.reset() }
    )

    private inner class WatchedController(val controller: MediaController) : MusicSessionHandle {
        override val token: Any get() = controller.sessionToken
        override val isOwnApp: Boolean get() = controller.packageName == packageName
        private var callback: MediaController.Callback? = null
        var metadataCallbackActive = false
            private set
        var callbackMetadata: MediaMetadata? = null
            private set
        var playbackCallbackActive = false
            private set
        var callbackPlaybackState: PlaybackState? = null
            private set

        override fun registerCallback(onEvent: (MusicObservationKind) -> Unit, onSessionEnded: () -> Unit) {
            val next = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    metadataCallbackActive = true
                    callbackMetadata = metadata
                    try { onEvent(MusicObservationKind.METADATA_CHANGED) }
                    finally { metadataCallbackActive = false; callbackMetadata = null }
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    playbackCallbackActive = true
                    callbackPlaybackState = state
                    try { onEvent(MusicObservationKind.PLAYBACK_CHANGED) }
                    finally { playbackCallbackActive = false; callbackPlaybackState = null }
                }

                override fun onSessionDestroyed() {
                    onSessionEnded()
                }
            }
            callback = next
            controller.registerCallback(next, callbackHandler)
        }

        override fun unregisterCallback() {
            val previous = callback ?: return
            callback = null
            controller.unregisterCallback(previous)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        app.musicListenerConnectionChanged(true)
        if (runObserver?.isActive == true) return
        runObserver = scope.launch {
            app.database.runDao().observeActiveRuns().collect { activeRuns ->
                // Never guess which run owns music when durable storage is ambiguous.
                val eligible = activeRuns.singleOrNull()?.takeIf {
                    it.state == StoredRunState.RUNNING && it.musicObservationRequested
                }
                if (eligible == null) stopCapture() else startCapture(eligible.runId)
            }
        }
    }

    override fun onListenerDisconnected() {
        app.musicListenerConnectionChanged(false)
        runObserver?.cancel()
        runObserver = null
        stopCapture()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        app.musicListenerConnectionChanged(false)
        runObserver?.cancel()
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture(nextRunId: String) {
        if (runId == nextRunId) return
        stopCapture()
        runId = nextRunId
        val ownerJob = SupervisorJob(scope.coroutineContext[Job])
        captureJob = ownerJob
        captureScope = CoroutineScope(ownerJob + Dispatchers.Main.immediate)
        controllers.start(nextRunId)
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { active ->
            // Session-list callbacks can also arrive after pause/reconnect, even for the same run.
            if (captureJob === ownerJob && runId == nextRunId) {
                controllers.reconcile(active.orEmpty().map { WatchedController(it) })
            }
        }
        sessionsChanged = listener
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                listener,
                component,
                callbackHandler
            )
            controllers.reconcile(sessionManager.getActiveSessions(component).map { WatchedController(it) })
            if (captureJob === ownerJob) captureScope?.launch {
                // Only Spotify needs listening-time evidence for reflection selection. Other
                // apps remain callback-only, and this capture owner never controls playback.
                while (isActive && captureJob === ownerJob && runId == nextRunId) {
                    delay(SONG_PLAYBACK_SAMPLE_MILLIS)
                    if (captureJob !== ownerJob || runId != nextRunId) break
                    controllers.sample { it.controller.packageName == SPOTIFY_PACKAGE }
                }
            }
        } catch (cancelled: CancellationException) {
            stopCapture()
            throw cancelled
        } catch (_: SecurityException) {
            // A revoked or unavailable grant leaves an honest gap, never a fake song.
            stopCapture()
        } catch (_: RuntimeException) {
            stopCapture()
        }
    }

    private fun stopCapture() {
        app.cancelSongStudyPreparation()
        playbackIntervals.reset()
        runId = null
        captureJob?.cancel()
        captureJob = null
        captureScope = null
        controllers.stop()
        val listener = sessionsChanged
        sessionsChanged = null
        if (listener != null) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(listener)
            } catch (_: RuntimeException) {
                // The listener may already have been disconnected by Android.
            }
        }
    }

    private fun capture(ownerRunId: String, watched: WatchedController, kind: MusicObservationKind) {
        if (runId != ownerRunId) return
        val currentScope = captureScope ?: return
        val controller = watched.controller
        // The registry handles unreadable snapshots per controller and stops on permission loss.
        val metadata = controller.metadata
        val sourcePackage = controller.packageName
        val recording = spotifyObservationRecording(sourcePackage, metadata)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank)
        val playbackState = playbackStateOf(controller.playbackState)
        val elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        val intervalMillis = if (sourcePackage == SPOTIFY_PACKAGE) {
            // Android may deliver a Pause/Resume or A/B/A callback batch after the live
            // controller already reports the latest state. Preserve those gap boundaries.
            if (watched.playbackCallbackActive) {
                playbackIntervals.onReportedPlaybackState(playbackStateOf(watched.callbackPlaybackState))
            }
            if (watched.metadataCallbackActive) {
                val reported = watched.callbackMetadata
                playbackIntervals.onReportedRecording(
                    reported?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    reported?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    spotifyObservationRecording(sourcePackage, reported)?.trackUri)
            }
            playbackIntervals.observe(
                runId = ownerRunId,
                sessionToken = controller.sessionToken,
                title = title,
                artist = artist,
                spotifyTrackUri = recording?.trackUri,
                playbackState = playbackState,
                elapsedRealtimeNanos = elapsedRealtimeNanos,
                resetContinuity = kind == MusicObservationKind.SESSION_SNAPSHOT
            )
        } else null
        val observation = RunMusicObservation(
            runId = ownerRunId,
            observationKey = UUID.randomUUID().toString(),
            observedAtEpochMillis = System.currentTimeMillis(),
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            sourcePackage = sourcePackage,
            title = title,
            artist = artist,
            playbackState = playbackState,
            eventKind = kind,
            spotifyTrackUri = recording?.trackUri,
            albumName = recording?.albumName,
            trackDurationMillis = recording?.durationMillis,
            playbackIntervalMillis = intervalMillis
        )
        currentScope.launch {
            try {
                app.database.musicObservationDao().recordObservation(observation)
                app.onMusicObservationSaved(ownerRunId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Music capture never blocks the run. Do not log track metadata or lyrics.
                Log.w(TAG, "A media-session observation was not saved.")
            }
        }
    }

    private fun playbackStateOf(state: PlaybackState?): MusicPlaybackState = when (state?.state) {
        PlaybackState.STATE_PLAYING -> MusicPlaybackState.PLAYING
        PlaybackState.STATE_PAUSED -> MusicPlaybackState.PAUSED
        PlaybackState.STATE_STOPPED -> MusicPlaybackState.STOPPED
        PlaybackState.STATE_BUFFERING, PlaybackState.STATE_CONNECTING -> MusicPlaybackState.BUFFERING
        null, PlaybackState.STATE_NONE -> MusicPlaybackState.UNKNOWN
        else -> MusicPlaybackState.OTHER
    }

    private companion object {
        const val TAG = "RunMusicObservation"
        const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}

/** Optional recording evidence is Spotify-only and never repaired from a title/search guess. */
internal data class SpotifyObservationRecording(
    val trackUri: String?,
    val albumName: String?,
    val durationMillis: Long?
)

internal fun spotifyObservationRecording(
    sourcePackage: String,
    metadata: MediaMetadata?
): SpotifyObservationRecording? {
    if (sourcePackage != "com.spotify.music" || metadata == null) return null
    val uri = canonicalSpotifyTrackUri(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID))
        ?: canonicalSpotifyTrackUri(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_URI))
    val album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM)?.trim()?.takeIf {
        it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl)
    }
    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf {
        it in 1_000L..86_400_000L
    }
    return SpotifyObservationRecording(uri, album, duration)
}
