package com.runstate.mobile.music

import android.graphics.Bitmap

/** Ephemeral Spotify observations for the in-run player; never persisted as run/sample data. */
internal data class SpotifyLivePlayerState(
    val connected: Boolean = false,
    val connecting: Boolean = false,
    val canConnect: Boolean = true,
    val trackUri: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: Bitmap? = null,
    val durationMillis: Long = 0L,
    val positionMillis: Long = 0L,
    val positionReceivedAtElapsedRealtime: Long = 0L,
    val paused: Boolean = true,
    val playbackSpeed: Float = 0f,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canTogglePlayback: Boolean = false,
    val busy: Boolean = false,
    val unavailableMessage: String? = null
) {
    /** A display estimate only. Spotify remains the authority for position and playback. */
    fun positionAt(elapsedRealtime: Long): Long {
        if (durationMillis <= 0L || positionMillis < 0L) return 0L
        val base = positionMillis.coerceIn(0L, durationMillis)
        if (!connected || paused || !playbackSpeed.isFinite() || playbackSpeed <= 0f) return base
        val age = (elapsedRealtime - positionReceivedAtElapsedRealtime).coerceAtLeast(0L)
        val advance = (age.toDouble() * playbackSpeed).coerceIn(0.0, (durationMillis - base).toDouble()).toLong()
        return base + advance
    }
}

/** Main-thread gate: late callbacks must not unlock another command or an exclusive passage. */
internal class SpotifyManualCommandGate {
    private var serial = 0L
    private var pending: Long? = null
    private val audioOwners = mutableSetOf<Any>()
    val blocked: Boolean get() = audioOwners.isNotEmpty()
    val busy: Boolean get() = pending != null

    fun acquireAudio(owner: Any): Boolean {
        if (busy) return false
        audioOwners += owner
        return true
    }

    fun releaseAudio(owner: Any) { audioOwners -= owner }

    fun begin(allowed: Boolean, passageOwned: Boolean): Long? {
        if (!allowed || blocked || passageOwned || busy) return null
        return (++serial).also { pending = it }
    }

    fun owns(token: Long): Boolean = pending == token

    fun finish(token: Long): Boolean {
        if (!owns(token)) return false
        pending = null
        return true
    }

    /** Used only after disconnecting the SDK, including its bounded command timeout. */
    fun invalidateConnection() {
        serial++
        pending = null
    }
}
