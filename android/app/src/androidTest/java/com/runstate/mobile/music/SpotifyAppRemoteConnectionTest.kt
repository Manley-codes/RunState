package com.runstate.mobile.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.PlayerApi
import com.spotify.android.appremote.api.SpotifyAppRemote
import com.spotify.android.appremote.api.TestSpotifyAppRemote
import com.spotify.android.appremote.api.UserApi
import com.spotify.android.appremote.api.error.AuthenticationFailedException
import com.spotify.android.appremote.api.error.NotLoggedInException
import com.spotify.android.appremote.api.error.UserNotAuthorizedException
import com.spotify.protocol.client.CallResult
import com.spotify.protocol.client.RemoteClient
import com.spotify.protocol.client.ResultUtils
import com.spotify.protocol.client.Subscription
import com.spotify.protocol.types.Album
import com.spotify.protocol.types.Artist
import com.spotify.protocol.types.Capabilities
import com.spotify.protocol.types.Empty
import com.spotify.protocol.types.ImageUri
import com.spotify.protocol.types.PlayerOptions
import com.spotify.protocol.types.PlayerRestrictions
import com.spotify.protocol.types.PlayerState
import com.spotify.protocol.types.Track
import com.spotify.protocol.types.Types
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production connection owner with controlled SDK callbacks, never real Spotify. */
@RunWith(AndroidJUnit4::class)
class SpotifyAppRemoteConnectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var preferences: SharedPreferences
    private lateinit var connector: ControlledConnector
    private lateinit var connection: SpotifyAppRemoteConnection

    @Before fun setUp() = onMain {
        // A dedicated injected test file; never RunState's actual Spotify settings or grant.
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        check(preferences.edit().clear().putString("authorized_client", CLIENT).commit())
        connector = ControlledConnector()
        connection = SpotifyAppRemoteConnection(context, connector, preferences, CLIENT)
    }

    @After fun tearDown() = onMain {
        if (::connection.isInitialized) connection.disconnect()
        if (::preferences.isInitialized) preferences.edit().clear().commit()
        context.deleteSharedPreferences(PREFERENCES)
    }

    @Test fun notLoggedInThenReturningFromSpotifyRetriesWithoutNewAuthorization() = onMain {
        connection.onForeground()
        connector.attempts.single().listener.onFailure(NotLoggedInException("Test session unavailable", null))
        assertTrue("A missing Spotify login is not revoked app consent", connection.hasRememberedAuthorization)
        assertEquals(SpotifyRemoteConnectionStatus.ERROR, connection.state.value.status)
        connection.onBackground()
        connection.onForeground()
        assertEquals(2, connector.attempts.size)
        assertFalse(connector.attempts.last().params.shouldShowAuthView())
        connector.succeed(1, FakeRemote().remote)
        assertTrue(connection.livePlayer.value.connected)
    }

    @Test fun playerOpenCanRetryTransientFailureWithoutStartingPlayback() = onMain {
        connection.onForeground()
        connector.attempts.single().listener.onFailure(IllegalStateException("Test service unavailable"))
        connection.connectSilently()
        assertEquals(2, connector.attempts.size)
        val fake = FakeRemote()
        connector.succeed(1, fake.remote)
        assertTrue(connection.livePlayer.value.connected)
        assertEquals("Test song", connection.livePlayer.value.title)
        assertTrue(connection.livePlayer.value.paused)
        assertEquals(0, fake.transportCalls)
    }

    @Test fun foregroundAndPlayerOpenShareOnePendingConnection() = onMain {
        connection.onForeground()
        repeat(4) { connection.connectSilently(); connection.onForeground() }
        assertEquals(1, connector.attempts.size)
        assertEquals(SpotifyRemoteConnectionStatus.CONNECTING, connection.state.value.status)
        assertTrue(connector.attempts.none { it.params.shouldShowAuthView() })
    }

    @Test fun noGrantAndExplicitDisconnectNeverSilentlyAuthorize() = onMain {
        preferences.edit().remove("authorized_client").commit()
        connection.onForeground()
        connection.connectSilently()
        assertTrue(connector.attempts.isEmpty())
        preferences.edit().putString("authorized_client", CLIENT).commit()
        connection.onForeground()
        assertEquals(1, connector.attempts.size)
        connection.disconnect()
        connection.onForeground()
        connection.connectSilently()
        assertFalse(connection.hasRememberedAuthorization)
        assertEquals(1, connector.attempts.size)
    }

    @Test fun genuineAuthorizationFailuresRequireExplicitPermissionAgain() = onMain {
        listOf(UserNotAuthorizedException("Test revoked grant", null),
            AuthenticationFailedException("Test client authentication", null)).forEach { failure ->
            preferences.edit().putString("authorized_client", CLIENT).commit()
            connection.onForeground()
            val attempt = connector.attempts.last()
            attempt.listener.onFailure(failure)
            assertFalse(connection.hasRememberedAuthorization)
            assertEquals(SpotifyRemoteConnectionStatus.NEEDS_AUTH, connection.state.value.status)
            val count = connector.attempts.size
            connection.onForeground()
            connection.connectSilently()
            assertEquals(count, connector.attempts.size)
        }
    }

    @Test fun backgroundCancelsAttemptAndOldCallbacksCannotOverwriteNewOne() = onMain {
        connection.onForeground()
        val old = connector.attempts.single()
        connection.onBackground()
        connection.connectSilently()
        assertEquals(1, connector.attempts.size)
        connection.onForeground()
        assertEquals(2, connector.attempts.size)
        old.listener.onFailure(UserNotAuthorizedException("Obsolete failure", null))
        assertTrue(connection.hasRememberedAuthorization)
        assertEquals(SpotifyRemoteConnectionStatus.CONNECTING, connection.state.value.status)
        val late = FakeRemote()
        old.listener.onConnected(late.remote)
        assertFalse(late.remote.connectedForTest)
        assertEquals(listOf(late.remote), connector.disconnected)
        val current = FakeRemote()
        connector.succeed(1, current.remote)
        assertTrue(connection.livePlayer.value.connected)
    }

    @Test fun healthyConnectionIsReusedAndDeadReadyHandleReconnects() = onMain {
        val fake = FakeRemote()
        connection.onForeground()
        connector.succeed(0, fake.remote)
        assertEquals(SpotifyRemoteConnectionStatus.READY, connection.state.value.status)
        connection.connectSilently()
        connection.onForeground()
        assertEquals(1, connector.attempts.size)
        assertEquals(0, fake.transportCalls)
        fake.remote.connectedForTest = false
        connection.connectSilently()
        assertEquals(2, connector.attempts.size)
        assertFalse(connection.livePlayer.value.connected)
        assertNull(connection.livePlayer.value.title)
    }

    @Test fun explicitConnectWithRememberedGrantDoesNotOpenApprovalAgain() = onMain {
        assertNull(connection.connectFromUserAction(Activity()))
        assertEquals(1, connector.attempts.size)
        assertFalse(connector.attempts.single().params.shouldShowAuthView())
        assertNull(connection.connectFromUserAction(Activity()))
        assertEquals(1, connector.attempts.size)
    }

    @Test fun lateAuthorizationResultCannotUndoExplicitDisconnect() = withActivity { activity ->
        preferences.edit().remove("authorized_client").commit()
        assertNotNull(connection.connectFromUserAction(activity))
        assertTrue(preferences.getBoolean("authorization_pending", false))
        connection.onBackground()
        connection.disconnect()
        assertFalse(preferences.getBoolean("authorization_pending", false))
        connection.onAuthorizationResult(Activity.RESULT_OK, successfulAuthorizationResult())
        assertFalse("An obsolete approval must not undo Disconnect", connection.hasRememberedAuthorization)
        assertTrue(connector.attempts.isEmpty())
        connection.onForeground()
        connection.connectSilently()
        assertTrue(connector.attempts.isEmpty())
    }

    @Test fun backgroundAuthorizationResultWaitsForForegroundBeforeConnecting() = withActivity { activity ->
        preferences.edit().remove("authorized_client").commit()
        // Build the real SDK login intent, but never launch its screen or contact Spotify.
        assertNotNull(connection.connectFromUserAction(activity))
        connection.onBackground()
        connection.onAuthorizationResult(Activity.RESULT_OK, successfulAuthorizationResult())
        assertTrue(connection.hasRememberedAuthorization)
        assertFalse(preferences.getBoolean("authorization_pending", false))
        assertFalse(preferences.all.containsValue("test-only-code"))
        assertTrue("An approval result is not a foreground lifecycle event", connector.attempts.isEmpty())
        connection.connectSilently()
        assertTrue(connector.attempts.isEmpty())
        connection.onForeground()
        assertEquals(1, connector.attempts.size)
        val fake = FakeRemote()
        connector.succeed(0, fake.remote)
        assertTrue(connection.livePlayer.value.connected)
        // A duplicate delivered result cannot clear the now-ready connection or start another.
        connection.onAuthorizationResult(Activity.RESULT_OK, successfulAuthorizationResult())
        assertEquals(1, connector.attempts.size)
        assertTrue(connection.livePlayer.value.connected)
        assertEquals(0, fake.transportCalls)
    }

    @Test fun restoredAuthorizationResultSurvivesOwnerRecreationButWaitsForForeground() = withActivity { activity ->
        preferences.edit().remove("authorized_client").commit()
        assertNotNull(connection.connectFromUserAction(activity))
        assertTrue(preferences.getBoolean("authorization_pending", false))
        connection.onBackground()
        assertTrue(connector.attempts.isEmpty())

        // A recreated process receives its ActivityResult with a new connection owner. Only the
        // pending explicit-approval marker survives; no response code or token is persisted.
        connection = SpotifyAppRemoteConnection(context, connector, preferences, CLIENT)
        connection.onBackground()
        connection.onAuthorizationResult(Activity.RESULT_OK, successfulAuthorizationResult())
        assertTrue(connection.hasRememberedAuthorization)
        assertFalse(preferences.getBoolean("authorization_pending", false))
        assertFalse(preferences.all.containsValue("test-only-code"))
        assertTrue(connector.attempts.isEmpty())
        connection.onForeground()
        assertEquals(1, connector.attempts.size)
        assertFalse(connector.attempts.single().params.shouldShowAuthView())
        val fake = FakeRemote()
        connector.succeed(0, fake.remote)
        assertTrue(connection.livePlayer.value.connected)
        assertEquals(0, fake.transportCalls)
    }

    @Test fun retryDoesNotReplaceRemoteWhileManualCommandIsUnsettled() = onMain {
        val fake = FakeRemote()
        connection.onForeground()
        connector.succeed(0, fake.remote)
        connection.next()
        assertEquals(1, fake.transportCalls)
        assertTrue(connection.livePlayer.value.busy)
        fake.remote.connectedForTest = false
        connection.connectSilently()
        connection.onForeground()
        assertEquals("Retry must wait for the old playback command", 1, connector.attempts.size)
        assertTrue(connection.livePlayer.value.busy)
        fake.command.deliverResult(ResultUtils.createSuccessfulResult(Empty()))
        connection.connectSilently()
        assertEquals(2, connector.attempts.size)
        assertEquals("Recovery must never replay Next", 1, fake.transportCalls)
    }

    @Test fun retryWaitsForPassageOwnershipToRelease() = onMain {
        val fake = FakeRemote()
        connection.onForeground()
        connector.succeed(0, fake.remote)
        val passage = requireNotNull(connection.newPassageSession())
        fake.remote.connectedForTest = false
        connection.connectSilently()
        connection.onForeground()
        assertEquals(1, connector.attempts.size)
        runBlocking { passage.release() }
        connection.connectSilently()
        assertEquals(2, connector.attempts.size)
        assertEquals(0, fake.transportCalls)
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { try { action() } catch (caught: Throwable) { failure = caught } }
        failure?.let { throw it }
    }

    private fun withActivity(action: (Activity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var activity: Activity
            scenario.onActivity { activity = it }
            onMain { action(activity) }
        }
    }

    private fun successfulAuthorizationResult(): Intent {
        val response = AuthorizationResponse.Builder().setType(AuthorizationResponse.Type.CODE)
            .setCode("test-only-code").build()
        // The pinned Spotify auth SDK 5.0.0's LoginActivity uses this nested response bundle.
        return Intent().putExtra("EXTRA_AUTH_RESPONSE", Bundle().apply { putParcelable("response", response) })
    }

    private class ControlledConnector : Connector {
        data class Attempt(val params: ConnectionParams, val listener: Connector.ConnectionListener)
        val attempts = mutableListOf<Attempt>()
        val disconnected = mutableListOf<SpotifyAppRemote>()
        override fun connect(context: Context, params: ConnectionParams, listener: Connector.ConnectionListener) {
            attempts += Attempt(params, listener)
        }
        override fun disconnect(remote: SpotifyAppRemote) {
            disconnected += remote
            (remote as TestSpotifyAppRemote).connectedForTest = false
        }
        fun succeed(index: Int, remote: SpotifyAppRemote) = attempts[index].listener.onConnected(remote)
    }

    private class FakeRemote {
        var transportCalls = 0
        val command = CallResult<Empty>(Types.RequestId.from(3))
        private val track = Track(Artist("Test artist", ""), emptyList(), Album("Test album", ""),
            180_000L, "Test song", "spotify:track:0123456789ABCDEFGHIJKL", ImageUri(""), false, false)
        private val player = PlayerState(track, true, 0f, 30_000L, PlayerOptions.DEFAULT,
            PlayerRestrictions(true, true, true, true, true, true))
        private val client = proxy(RemoteClient::class.java) { name ->
            check(name == "unsubscribe") { "Unexpected SDK client call: $name" }; null
        }
        private val playerSubscription = Subscription<PlayerState>(Types.RequestId.from(1), client).apply {
            deliverResult(ResultUtils.createSuccessfulResult(player))
        }
        private val capabilitiesSubscription = Subscription<Capabilities>(Types.RequestId.from(2), client).apply {
            deliverResult(ResultUtils.createSuccessfulResult(Capabilities(true)))
        }
        private val playerApi = proxy(PlayerApi::class.java) { name ->
            when (name) {
                "getPlayerState" -> completed(player)
                "subscribeToPlayerState" -> playerSubscription
                "skipNext" -> { transportCalls++; command }
                else -> error("Unexpected playback call: $name")
            }
        }
        private val userApi = proxy(UserApi::class.java) { name ->
            when (name) {
                "getCapabilities" -> completed(Capabilities(true))
                "subscribeToCapabilities" -> capabilitiesSubscription
                else -> error("Unexpected user call: $name")
            }
        }
        val remote = TestSpotifyAppRemote(playerApi, userApi)

        private fun <T> completed(value: T) = CallResult<T>(Types.RequestId.NONE).apply {
            deliverResult(ResultUtils.createSuccessfulResult(value))
        }
        private fun <T : Any> proxy(type: Class<T>, invoke: (String) -> Any?): T = requireNotNull(type.cast(
            Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { instance, method, args ->
                when (method.name) {
                    "toString" -> "Controlled${type.simpleName}"
                    "hashCode" -> System.identityHashCode(instance)
                    "equals" -> instance === args?.firstOrNull()
                    else -> invoke(method.name)
                }
            }))
    }

    private companion object {
        const val CLIENT = "11111111111111111111111111111111"
        const val PREFERENCES = "spotify-connection-test-only"
    }
}
