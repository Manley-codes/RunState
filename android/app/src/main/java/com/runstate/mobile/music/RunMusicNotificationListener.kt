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
    private var sessionsChanged: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val controllers = MusicSessionRegistry<WatchedController>(
        onObservation = { ownerRunId, watched, kind -> capture(ownerRunId, watched.controller, kind) },
        onControllerFailure = { Log.w(TAG, "A media controller did not provide a usable observation.") },
        onAccessLost = { stopCapture() }
    )

    private inner class WatchedController(val controller: MediaController) : MusicSessionHandle {
        override val token: Any get() = controller.sessionToken
        override val isOwnApp: Boolean get() = controller.packageName == packageName
        private var callback: MediaController.Callback? = null

        override fun registerCallback(onEvent: (MusicObservationKind) -> Unit) {
            val next = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    onEvent(MusicObservationKind.METADATA_CHANGED)
                }

                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    onEvent(MusicObservationKind.PLAYBACK_CHANGED)
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

    private fun capture(ownerRunId: String, controller: MediaController, kind: MusicObservationKind) {
        if (runId != ownerRunId) return
        val currentScope = captureScope ?: return
        // The registry handles unreadable snapshots per controller and stops on permission loss.
        val metadata = controller.metadata
        val observation = RunMusicObservation(
            runId = ownerRunId,
            observationKey = UUID.randomUUID().toString(),
            observedAtEpochMillis = System.currentTimeMillis(),
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(),
            sourcePackage = controller.packageName,
            title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.takeIf(String::isNotBlank),
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf(String::isNotBlank),
            playbackState = playbackStateOf(controller.playbackState),
            eventKind = kind
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
    }
}
