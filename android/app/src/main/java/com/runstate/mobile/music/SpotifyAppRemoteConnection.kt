package com.runstate.mobile.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.runstate.mobile.BuildConfig
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Capabilities
import com.spotify.protocol.types.PlayerState
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.abs

internal enum class SpotifyRemoteConnectionStatus { DISCONNECTED, CONNECTING, READY, NEEDS_AUTH, ERROR }
internal data class SpotifyRemoteConnectionState(
    val status: SpotifyRemoteConnectionStatus,
    val configured: Boolean,
    val canPlayOnDemand: Boolean = false,
    /** Safe diagnostic category only: never an exception message, URI, credential or account data. */
    val failureCategory: String? = null
)

/** Process-local SDK connection. The persisted flag is permission to reconnect, never an account token. */
internal class SpotifyAppRemoteConnection private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val preferences = appContext.getSharedPreferences("spotify_remote_connection", Context.MODE_PRIVATE)
    private val configured = BuildConfig.SPOTIFY_CLIENT_ID.matches(Regex("[A-Fa-f0-9]{32}"))
    private val mutableState = MutableStateFlow(SpotifyRemoteConnectionState(
        if (configured && !hasGrant()) SpotifyRemoteConnectionStatus.NEEDS_AUTH else SpotifyRemoteConnectionStatus.DISCONNECTED,
        configured))
    val state: StateFlow<SpotifyRemoteConnectionState> = mutableState.asStateFlow()
    /** A preflight diagnostic, not proof that Spotify will accept the next connection. */
    val hasRememberedAuthorization: Boolean get() = hasGrant()
    private var generation = 0L
    private var remote: SpotifyAppRemote? = null
    private var playerSubscription: Subscription<PlayerState>? = null
    private var capabilitySubscription: Subscription<Capabilities>? = null
    private var player: PlayerState? = null
    private var playerReceivedAt = 0L
    private var capabilitiesKnown = false
    private var onDemand = false
    private var background = false
    private var interactiveConnecting = false
    private var refreshPending = false
    private var lastRefreshAt = 0L
    private val sessions = mutableSetOf<RemotePassageSession>()

    private fun hasGrant() = configured && preferences.getString("authorized_client", null) == BuildConfig.SPOTIFY_CLIENT_ID
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == main.looper) action() else main.post(action)
    }

    /**
     * Explicit Connect Spotify. On Android 14+ the App Remote kit's own approval screen is started
     * by Spotify from the background and blocked, so the connection hangs. Spotify's login library
     * opens the same approval from RunState while it is on screen; once approved, App Remote
     * connects without any approval UI. Returns null when Spotify is not configured or unavailable.
     */
    fun authorizationIntent(activity: Activity): Intent? {
        check(Looper.myLooper() == main.looper)
        if (!configured) return null
        background = false
        clearConnection(SpotifyRemoteConnectionStatus.CONNECTING)
        // Keep this attempt alive while Spotify's approval screen puts RunState in the background.
        interactiveConnecting = true
        return try {
            val request = AuthorizationRequest.Builder(BuildConfig.SPOTIFY_CLIENT_ID,
                AuthorizationResponse.Type.CODE, BuildConfig.SPOTIFY_REDIRECT_URI)
                .setScopes(arrayOf("app-remote-control")).build()
            AuthorizationClient.createLoginActivityIntent(activity, request)
        } catch (_: RuntimeException) {
            authorizationFailed("AuthorizationUnavailable")
            null
        }
    }

    /** The returned code is never used or stored: App Remote relies on the Spotify app's own session. */
    fun onAuthorizationResult(resultCode: Int, data: Intent?) = onMain {
        val response = try { AuthorizationClient.getResponse(resultCode, data) } catch (_: RuntimeException) { null }
        val errorCode = response?.error
        when (response?.type) {
            AuthorizationResponse.Type.CODE, AuthorizationResponse.Type.TOKEN -> {
                preferences.edit().putString("authorized_client", BuildConfig.SPOTIFY_CLIENT_ID).apply()
                clearConnection(SpotifyRemoteConnectionStatus.DISCONNECTED)
                background = false
                connect(appContext, interactive = false)
            }
            AuthorizationResponse.Type.CANCELLED -> authorizationFailed("AuthorizationCancelled")
            AuthorizationResponse.Type.ERROR -> authorizationFailed(spotifyAuthorizationErrorCategory(errorCode))
            else -> authorizationFailed("AuthorizationIncomplete")
        }
    }

    private fun authorizationFailed(category: String) {
        preferences.edit().remove("authorized_client").apply()
        recordDebugFailure(category)
        clearConnection(SpotifyRemoteConnectionStatus.NEEDS_AUTH, category)
    }
    fun connectSilently() = onMain {
        if (!background && hasGrant()) connect(appContext, interactive = false)
    }
    fun onForeground() = onMain { background = false; if (hasGrant()) connect(appContext, interactive = false) }
    fun onBackground() = onMain { background = true; disconnectIfIdle() }

    /** Explicit disconnect also removes automatic reconnection permission. */
    fun disconnect() = onMain {
        preferences.edit().remove("authorized_client").apply()
        sessions.toList().forEach { it.cancelPendingTrackLoad() }
        background = true
        if (sessions.isEmpty()) clearConnection(SpotifyRemoteConnectionStatus.DISCONNECTED) else disconnectIfIdle()
    }

    private fun disconnectIfIdle() {
        // Spotify authorization temporarily backgrounds this Activity. Let that explicit
        // attempt finish and remember its result before releasing the idle connection.
        if (background && sessions.isEmpty() && !interactiveConnecting)
            clearConnection(SpotifyRemoteConnectionStatus.DISCONNECTED, mutableState.value.failureCategory)
    }

    private fun clearConnection(status: SpotifyRemoteConnectionStatus, failureCategory: String? = null) {
        generation++
        interactiveConnecting = false
        playerSubscription?.cancel(); playerSubscription = null
        capabilitySubscription?.cancel(); capabilitySubscription = null
        val old = remote
        remote = null
        player = null
        capabilitiesKnown = false
        onDemand = false
        refreshPending = false
        if (old != null) try { SpotifyAppRemote.disconnect(old) } catch (_: RuntimeException) { }
        mutableState.value = SpotifyRemoteConnectionState(status, configured, failureCategory = failureCategory)
    }

    private fun connect(context: Context, interactive: Boolean) {
        if (!configured || !interactive && !hasGrant()) return
        if (remote?.isConnected == true) { refreshPlayer(); return }
        if (mutableState.value.status == SpotifyRemoteConnectionStatus.CONNECTING) return
        clearConnection(SpotifyRemoteConnectionStatus.CONNECTING)
        interactiveConnecting = interactive
        val attempt = generation
        val params = ConnectionParams.Builder(BuildConfig.SPOTIFY_CLIENT_ID)
            .setRedirectUri(BuildConfig.SPOTIFY_REDIRECT_URI).showAuthView(interactive).build()
        val timeout = Runnable {
            if (generation == attempt && mutableState.value.status == SpotifyRemoteConnectionStatus.CONNECTING) {
                val category = when {
                    remote == null -> "ConnectionTimeout"
                    player == null && !capabilitiesKnown -> "ReadinessTimeout"
                    player == null -> "PlayerStateTimeout"
                    else -> "CapabilitiesTimeout"
                }
                recordDebugFailure(category)
                clearConnection(SpotifyRemoteConnectionStatus.ERROR, category)
            }
        }
        main.postDelayed(timeout, 20_000L)
        try {
            SpotifyAppRemote.setDebugMode(false)
            SpotifyAppRemote.connect(context, params, object : Connector.ConnectionListener {
                override fun onConnected(connected: SpotifyAppRemote) = onMain {
                    if (generation != attempt) { SpotifyAppRemote.disconnect(connected); return@onMain }
                    interactiveConnecting = false
                    remote = connected
                    preferences.edit().putString("authorized_client", BuildConfig.SPOTIFY_CLIENT_ID).apply()
                    try {
                        playerSubscription = connected.playerApi.subscribeToPlayerState().also { subscription ->
                            subscription.setEventCallback { next -> onMain {
                                if (generation == attempt && remote === connected) acceptPlayer(next)
                            } }
                            subscription.setErrorCallback { failure -> onMain { failed(attempt, failure) } }
                        }
                        capabilitySubscription = connected.userApi.subscribeToCapabilities().also { subscription ->
                            subscription.setEventCallback { next -> onMain {
                                if (generation == attempt && remote === connected) acceptCapabilities(next)
                            } }
                            subscription.setErrorCallback { failure -> onMain { failed(attempt, failure) } }
                        }
                        connected.userApi.capabilities.setResultCallback { next -> onMain {
                            if (generation == attempt && remote === connected) acceptCapabilities(next)
                        } }.setErrorCallback { failure -> onMain { failed(attempt, failure) } }
                        refreshPlayer(force = true)
                    } catch (failure: RuntimeException) { failed(attempt, failure) }
                    disconnectIfIdle()
                }
                override fun onFailure(failure: Throwable) = onMain { failed(attempt, failure) }
            })
        } catch (failure: RuntimeException) { failed(attempt, failure) }
    }

    private fun failed(attempt: Long, failure: Throwable) {
        if (generation != attempt) return
        val auth = failure is UserNotAuthorizedException || failure is AuthenticationFailedException || failure is NotLoggedInException
        if (auth) preferences.edit().remove("authorized_client").apply()
        val category = spotifyRemoteFailureCategory(failure)
        recordDebugFailure(category)
        clearConnection(if (auth) SpotifyRemoteConnectionStatus.NEEDS_AUTH else SpotifyRemoteConnectionStatus.ERROR,
            category)
    }

    private fun recordDebugFailure(category: String) {
        if (!BuildConfig.DEBUG) return
        val bounded = category.takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")) } ?: "UnknownFailure"
        // Intentionally omit Throwable/message/cause, provider payloads, account and recording identifiers.
        preferences.edit().putString("debug_last_failure_category", bounded).apply()
        Log.i("RunStateSpotify", "Connection failure category: $bounded")
    }

    private fun acceptCapabilities(next: Capabilities) { capabilitiesKnown = true; onDemand = next.canPlayOnDemand; publishReady() }
    private fun acceptPlayer(next: PlayerState) {
        player = next
        playerReceivedAt = SystemClock.elapsedRealtime()
        sessions.toList().forEach { it.observe(next) }
        publishReady()
    }
    private fun publishReady() {
        if (remote?.isConnected == true && player != null && capabilitiesKnown)
            mutableState.value = SpotifyRemoteConnectionState(SpotifyRemoteConnectionStatus.READY, configured, onDemand)
    }
    private fun refreshPlayer(force: Boolean = false) {
        val connected = remote?.takeIf { it.isConnected } ?: return
        val now = SystemClock.elapsedRealtime()
        if (refreshPending || !force && now - lastRefreshAt < 250L) return
        val attempt = generation
        val issued = now
        lastRefreshAt = now
        refreshPending = true
        try {
            connected.playerApi.playerState.setResultCallback { next -> onMain {
                if (generation == attempt && remote === connected && lastRefreshAt == issued) {
                    refreshPending = false
                    // A reply to an older query must not overwrite a newer subscription event.
                    if (issued >= playerReceivedAt) acceptPlayer(next)
                }
            } }.setErrorCallback { failure -> onMain {
                if (generation == attempt && lastRefreshAt == issued) { refreshPending = false; failed(attempt, failure) }
            } }
        } catch (failure: RuntimeException) { refreshPending = false; failed(attempt, failure) }
        main.postDelayed({ if (generation == attempt && lastRefreshAt == issued) refreshPending = false }, 3_000L)
    }

    /** A lease freezes one transport/connection for the entire passage, including restoration. */
    fun newPassageSession(): SongPassageSession? {
        check(Looper.myLooper() == main.looper)
        if (state.value.status != SpotifyRemoteConnectionStatus.READY || !onDemand || remote?.isConnected != true ||
            sessions.isNotEmpty()) return null
        return RemotePassageSession(generation, requireNotNull(remote)).also { sessions += it }
    }

    private inner class RemotePassageSession(private val epoch: Long, private val connected: SpotifyAppRemote) : SongPassageSession {
        private val native = AndroidSpotifyPassageSession(appContext)
        private var selected: SongPassageSnapshot? = null
        private var selectedAt = 0L
        private val metadata = mutableMapOf<String, Pair<String?, String?>>()
        private val admittedDurations = mutableMapOf<String, Long>()
        private var nativeSessionKey: Any? = null
        private var released = false
        private var pending: SpotifyPendingTrackLoad? = null
        private fun currentConnection() = !released && epoch == generation && remote === connected && connected.isConnected

        override fun expectTrack(request: SongPassageRequest) {
            canonicalSpotifyTrackUri(request.spotifyMediaId)?.let { uri ->
                // This is metadata previously observed for this canonical recording, not a
                // title-only guess. Keep SDK/native artist formatting from changing identity.
                metadata[uri] = request.trackTitle to request.artistName
                request.observedDurationMillis?.let { admittedDurations[uri] = it }
            }
        }

        override suspend fun prepareForPlayback() {
            val until = SystemClock.elapsedRealtime() + 3_000L
            refreshPlayer(force = true)
            while (currentConnection() && SystemClock.elapsedRealtime() < until) {
                if (player != null && SystemClock.elapsedRealtime() - playerReceivedAt <= 1_000L) return
                refreshPlayer()
                delay(100L)
            }
            check(false) { "Spotify playback state is unavailable." }
        }

        override fun snapshot(): SongPassageSnapshot? {
            // A disconnected SDK cannot authorize further play/seek/load. Its already-owned
            // audio may still be running, so expose only a verified native pause boundary.
            if (!currentConnection()) return nativeCleanupSnapshot()
            refreshPlayer()
            val current = player ?: return null
            val track = current.track ?: return null
            val uri = canonicalSpotifyTrackUri(track.uri) ?: return null
            val age = (SystemClock.elapsedRealtime() - playerReceivedAt).coerceAtLeast(0L)
            if (age > 5_000L) return null
            val nativeState = try { native.snapshot()?.takeIf { it.mediaId == uri } } catch (_: RuntimeException) { null }
            if (nativeSessionKey == null && nativeState != null) nativeSessionKey = nativeState.sessionKey
            val stableMetadata = metadata.getOrPut(uri) { (nativeState?.title ?: track.name) to
                (nativeState?.artist ?: track.artists?.mapNotNull { it.name }?.joinToString(", ")?.takeIf { it.isNotBlank() }
                    ?: track.artist?.name) }
            val moving = !current.isPaused && current.playbackSpeed == 1f
            val duration = nativeState?.durationMillis?.takeIf { it > 0 } ?: track.duration
            admittedDurations.putIfAbsent(uri, duration)
            val awaitingPauseBarrier = current.isPaused && pending?.let {
                it.target == uri && (!it.loadAcknowledged || !it.pauseAcknowledged)
            } == true
            return SongPassageSnapshot(epoch, "com.spotify.music", uri,
                stableMetadata.first, stableMetadata.second,
                duration,
                if (current.playbackPosition < 0L) -1L else current.playbackPosition + if (moving) age else 0L,
                if (awaitingPauseBarrier) PassagePlayback.OTHER else if (current.isPaused) PassagePlayback.PAUSED
                else if (moving) PassagePlayback.PLAYING else PassagePlayback.OTHER,
                canPause = true, canPlay = onDemand, canSeek = current.playbackRestrictions?.canSeek == true,
                positionUpdateAgeMillis = age, canPrepareTrack = false, canPlayTrack = onDemand).also {
                selected = it; selectedAt = SystemClock.elapsedRealtime()
            }
        }

        private fun nativeCleanupSnapshot(): SongPassageSnapshot? {
            if (released || nativeSessionKey == null) return null
            val observed = try { native.snapshot() } catch (_: RuntimeException) { null } ?: return null
            val uri = canonicalSpotifyTrackUri(observed.mediaId) ?: return null
            val identity = metadata[uri] ?: return null
            val duration = admittedDurations[uri] ?: return null
            if (observed.sessionKey != nativeSessionKey || duration !in 1_000..86_400_000L ||
                observed.durationMillis !in 1_000..86_400_000L ||
                abs(observed.durationMillis - duration) > 1L) return null
            return observed.copy(sessionKey = epoch, title = identity.first, artist = identity.second,
                canPlay = false, canSeek = false, canPrepareTrack = false, canPlayTrack = false).also {
                selected = it; selectedAt = SystemClock.elapsedRealtime()
            }
        }

        private fun pauseNativeCleanup(): Boolean {
            val expected = selected ?: return false
            val expectedAt = selectedAt
            val actual = nativeCleanupSnapshot() ?: return false
            if (!mayPauseNativeSpotifyCleanup(expected, actual, SystemClock.elapsedRealtime() - expectedAt)) return false
            native.pause()
            return true
        }

        private fun owned() {
            check(currentConnection())
            val expected = checkNotNull(selected)
            check(player?.track?.uri == expected.mediaId)
        }
        private fun command(action: () -> com.spotify.protocol.client.CallResult<com.spotify.protocol.types.Empty>) {
            owned()
            action().setResultCallback { onMain { if (currentConnection()) refreshPlayer(force = true) } }
                .setErrorCallback { onMain { if (currentConnection()) refreshPlayer(force = true) } }
        }
        override fun pause() {
            if (!currentConnection()) { pauseNativeCleanup(); return }
            val load = pending?.takeIf { player?.track?.uri == it.target }
            if (load != null) load.pauseSent = true
            owned()
            connected.playerApi.pause().setResultCallback { onMain {
                if (currentConnection()) {
                    if (pending === load) load?.pauseAcknowledged = true
                    player?.let(::observe)
                    refreshPlayer(force = true)
                }
            } }.setErrorCallback { onMain { if (pending === load) load?.pauseSent = false } }
        }
        override fun seekTo(positionMillis: Long) = command { connected.playerApi.seekTo(positionMillis) }
        override fun play() = command { connected.playerApi.resume() }
        override fun playFromTrackUri(uri: String): Boolean {
            owned()
            if (!onDemand || canonicalSpotifyTrackUri(uri) == null) return false
            val old = checkNotNull(selected)
            val load = SpotifyPendingTrackLoad(uri, old.mediaId, old.positionMillis, SystemClock.elapsedRealtime())
            pending = load
            connected.playerApi.play(uri).setResultCallback { onMain {
                if (currentConnection() && pending === load) {
                    load.loadAcknowledged = true
                    player?.let(::observe)
                    refreshPlayer(force = true)
                }
            } }.setErrorCallback { onMain {
                if (currentConnection() && pending === load) {
                    load.loadAcknowledged = true
                    if (player?.track?.uri == load.previous && player?.isPaused == true) pending = null
                    else player?.let(::observe)
                }
            } }
            return true
        }
        override fun cancelPendingTrackLoad() {
            pending?.let { it.cancelled = true }
            if (currentConnection()) player?.let(::observe) else containDisconnectedLoad()
        }

        private fun containDisconnectedLoad() {
            val load = pending ?: return
            val actual = nativeCleanupSnapshot() ?: return
            if (load.pauseSent && actual.playback == PassagePlayback.PAUSED) load.pauseAcknowledged = true
            when (load.observe(actual.mediaId, actual.playback == PassagePlayback.PAUSED,
                actual.positionMillis, SystemClock.elapsedRealtime())) {
                SpotifyLoadAction.RELEASE -> pending = null
                SpotifyLoadAction.PAUSE_TARGET -> {
                    val sent = pauseNativeCleanup()
                    // The native command is only a request. Keep observing rather than claim
                    // SDK acknowledgement or restore success after the connection was lost.
                    load.pauseSent = sent
                    if (sent) load.pauseAcknowledged = false
                }
                SpotifyLoadAction.WAIT -> Unit
            }
        }
        fun observe(next: PlayerState) {
            val load = pending ?: return
            if (!currentConnection()) { pending = null; return }
            when (load.observe(next.track?.uri, next.isPaused, next.playbackPosition, SystemClock.elapsedRealtime())) {
                SpotifyLoadAction.RELEASE -> pending = null
                SpotifyLoadAction.PAUSE_TARGET -> {
                    // A late SDK play result still belongs to this cancelled load. Never pause another track.
                    load.pauseSent = true
                    load.pauseAcknowledged = false
                    connected.playerApi.pause().setResultCallback { onMain {
                        if (currentConnection() && pending === load) {
                            load.pauseAcknowledged = true
                            player?.let(::observe)
                            refreshPlayer(force = true)
                        }
                    } }.setErrorCallback { onMain { if (pending === load) load.pauseSent = false } }
                }
                SpotifyLoadAction.WAIT -> Unit
            }
        }
        override suspend fun release() = withContext(NonCancellable) {
            if (released) return@withContext
            pending?.let { it.cancelled = true }
            val until = SystemClock.elapsedRealtime() + 10_000L
            while (pending != null && SystemClock.elapsedRealtime() < until) {
                if (currentConnection()) { player?.let(::observe); refreshPlayer(force = true) }
                else containDisconnectedLoad()
                delay(100L)
            }
            pending = null
            released = true
            sessions -= this@RemotePassageSession
            disconnectIfIdle()
        }
    }

    companion object {
        @Volatile private var instance: SpotifyAppRemoteConnection? = null
        fun get(context: Context): SpotifyAppRemoteConnection = instance ?: synchronized(this) {
            instance ?: SpotifyAppRemoteConnection(context).also { instance = it }
        }
    }
}

internal enum class SpotifyLoadAction { WAIT, PAUSE_TARGET, RELEASE }

/** Spotify's OAuth error code, bounded to a safe diagnostic word. It never contains a code or token. */
internal fun spotifyAuthorizationErrorCategory(error: String?): String {
    val cleaned = error?.replace(Regex("[^A-Za-z0-9]+"), "_")?.trim('_')?.take(60).orEmpty()
    return if (cleaned.isEmpty()) "AuthorizationError" else "AuthorizationError_$cleaned"
}

internal fun spotifyRemoteFailureCategory(failure: Throwable): String = failure.javaClass.simpleName
    .takeIf { it.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")) } ?: "UnknownFailure"

/** Native access after SDK loss is containment only, never authority to continue or restore playback. */
internal fun mayPauseNativeSpotifyCleanup(expected: SongPassageSnapshot, actual: SongPassageSnapshot,
    elapsedMillis: Long): Boolean = expected.sessionKey == actual.sessionKey &&
    expected.sourcePackage == "com.spotify.music" && actual.sourcePackage == "com.spotify.music" &&
    canonicalSpotifyTrackUri(expected.mediaId) != null && expected.mediaId == actual.mediaId &&
    expected.title == actual.title && expected.artist == actual.artist &&
    expected.durationMillis in 1_000..86_400_000L && actual.durationMillis in 1_000..86_400_000L &&
    abs(expected.durationMillis - actual.durationMillis) <= 1L &&
    expected.playback == PassagePlayback.PLAYING && actual.playback == PassagePlayback.PLAYING &&
    expected.positionMillis in 0..expected.durationMillis && actual.positionMillis in 0..actual.durationMillis &&
    elapsedMillis in 0..3_000L && abs(actual.positionMillis - expected.positionMillis - elapsedMillis) <= 1_500L

/** No SDK/Android dependency: late-load containment must yield to any observed user intervention. */
internal class SpotifyPendingTrackLoad(val target: String, val previous: String?, val previousPosition: Long,
    val started: Long) {
    var cancelled = false
    var pauseSent = false
    var pauseAcknowledged = false
    var loadAcknowledged = false

    fun observe(uri: String?, paused: Boolean, position: Long, now: Long): SpotifyLoadAction {
        if (uri == target) {
            if (paused && loadAcknowledged && pauseAcknowledged) return SpotifyLoadAction.RELEASE
            if (!cancelled) return SpotifyLoadAction.WAIT
            if (position >= 0L && position > (now - started).coerceAtLeast(0L) + 1_500L) return SpotifyLoadAction.RELEASE
            // A delayed play may follow an earlier acknowledged pause. Re-contain that
            // exact owned entrance once, then wait for the new pause acknowledgement.
            return if (!pauseSent || pauseAcknowledged && !paused) SpotifyLoadAction.PAUSE_TARGET else SpotifyLoadAction.WAIT
        }
        return if (uri == previous && paused && abs(position - previousPosition) <= 1_500L) SpotifyLoadAction.WAIT
        else SpotifyLoadAction.RELEASE
    }
}
