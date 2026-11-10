package com.spotify.android.appremote.api

/** Test-only access to the SDK's package-private constructor; never opens Spotify or a socket. */
internal class TestSpotifyAppRemote(playerApi: PlayerApi, userApi: UserApi, imagesApi: ImagesApi? = null) :
    SpotifyAppRemote(null, playerApi, imagesApi, userApi, null, null, null) {
    var connectedForTest = true
    override fun isConnected(): Boolean = connectedForTest
}
