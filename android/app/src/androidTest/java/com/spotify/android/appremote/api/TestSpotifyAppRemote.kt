package com.spotify.android.appremote.api

/** Test-only access to the SDK's package-private constructor; never opens Spotify or a socket. */
internal class TestSpotifyAppRemote(playerApi: PlayerApi, userApi: UserApi) :
    SpotifyAppRemote(null, playerApi, null, userApi, null, null, null) {
    var connectedForTest = true
    override fun isConnected(): Boolean = connectedForTest
}
