package com.runstate.mobile.music

import android.content.ComponentName
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
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
import kotlinx.coroutines.flow.collect
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
    private var watchingSessions = false
    private val controllers = mutableMapOf<MediaSession.Token, WatchedController>()

    private data class WatchedController(
        val controller: MediaController,
        val callback: MediaController.Callback
    )

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { active ->
        if (runId != null) {
            try {
                reconcileControllers(active.orEmpty())
            } catch (_: RuntimeException) {
                Log.w(TAG, "Media-session observation stopped after a controller error.")
                stopCapture()
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
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
        runObserver?.cancel()
        runObserver = null
        stopCapture()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        runObserver?.cancel()
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    private fun startCapture(nextRunId: String) {
        if (runId == nextRunId) return
        stopCapture()
        runId = nextRunId
        captureJob = SupervisorJob(scope.coroutineContext[Job])
        captureScope = CoroutineScope(captureJob!! + Dispatchers.Main.immediate)
        try {
            sessionManager.addOnActiveSessionsChangedListener(
                sessionsChanged,
                component,
                callbackHandler
            )
            watchingSessions = true
            reconcileControllers(sessionManager.getActiveSessions(component))
        } catch (_: SecurityException) {
            // A revoked or unavailable grant leaves an honest gap, never a fake song.
            stopCapture()
        } catch (_: RuntimeException) {
            stopCapture()
        }
    }

    private fun stopCapture() {
        runId = null
        captureJob?.cancel()
        captureJob = null
        captureScope = null
        controllers.values.forEach { watched ->
            try {
                watched.controller.unregisterCallback(watched.callback)
            } catch (_: RuntimeException) {
                // The controller may already have died; no session state is inferred.
            }
        }
        controllers.clear()
        if (watchingSessions) {
            try {
                sessionManager.removeOnActiveSessionsChangedListener(sessionsChanged)
            } catch (_: RuntimeException) {
                // The listener may already have been disconnected by Android.
            }
            watchingSessions = false
        }
    }

    private fun reconcileControllers(active: List<MediaController>) {
        val byToken = active
            .filter { it.packageName != packageName }
            .associateBy { it.sessionToken }
        controllers.keys.filter { it !in byToken }.forEach { token ->
            controllers.remove(token)?.let { watched ->
                try {
                    watched.controller.unregisterCallback(watched.callback)
                } catch (_: RuntimeException) {
                    // The removed controller may already have been destroyed.
                }
            }
        }
        byToken.forEach { (token, controller) ->
            if (token !in controllers) {
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        capture(controller, MusicObservationKind.METADATA_CHANGED)
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        capture(controller, MusicObservationKind.PLAYBACK_CHANGED)
                    }
                }
                controller.registerCallback(callback, callbackHandler)
                controllers[token] = WatchedController(controller, callback)
                capture(controller, MusicObservationKind.SESSION_SNAPSHOT)
            }
        }
    }

    private fun capture(controller: MediaController, kind: MusicObservationKind) {
        val currentRunId = runId ?: return
        val currentScope = captureScope ?: return
        val observation = try {
            val metadata = controller.metadata
            RunMusicObservation(
                runId = currentRunId,
                observationKey = UUID.randomUUID().toString(),
                observedAtEpochMillis = System.currentTimeMillis(),
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
                sourcePackage = controller.packageName,
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank),
                playbackState = playbackStateOf(controller.playbackState),
                eventKind = kind
            )
        } catch (_: RuntimeException) {
            Log.w(TAG, "A media controller did not provide a readable snapshot.")
            return
        }
        currentScope.launch {
            try {
                app.database.musicObservationDao().recordObservation(observation)
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
    }
}
