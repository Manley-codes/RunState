package com.runstate.mobile.music

import com.runstate.mobile.data.local.MusicObservationKind
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.songPreparationIdentity

internal const val MINIMUM_SONG_PLAYBACK_MILLIS = 60_000L
internal const val SONG_PLAYBACK_SAMPLE_MILLIS = 5_000L
internal const val MAX_OBSERVED_PLAYBACK_INTERVAL_MILLIS = 7_500L

/**
 * Credits only short intervals whose two ends report the same playing Spotify recording.
 * Callbacks and periodic samples share one anchor, so neither can count the other's time again.
 * The listener resets this in-memory anchor on capture loss; persisted interval receipts can
 * still accumulate across later listens without filling the unobserved gap.
 */
internal class SongPlaybackIntervalTracker {
    private data class Anchor(
        val runId: String,
        val sessionToken: Any,
        val identity: Pair<String, String>,
        val trackUri: String?,
        val nanos: Long
    )

    private var anchor: Anchor? = null

    fun reset() { anchor = null }

    /** A queued callback still identifies an interruption even if the live state has moved on. */
    fun onReportedPlaybackState(state: MusicPlaybackState) {
        if (state != MusicPlaybackState.PLAYING) reset()
    }

    fun onReportedRecording(title: String?, artist: String?, spotifyTrackUri: String?) {
        val previous = anchor ?: return
        val identity = songPreparationIdentity(title?.trim(), artist?.trim())
        if (identity != previous.identity || canonicalSpotifyTrackUri(spotifyTrackUri) != previous.trackUri) reset()
    }

    fun observe(
        runId: String,
        sessionToken: Any,
        title: String?,
        artist: String?,
        spotifyTrackUri: String?,
        playbackState: MusicPlaybackState,
        elapsedRealtimeNanos: Long,
        resetContinuity: Boolean = false
    ): Long {
        val previous = anchor.takeUnless { resetContinuity }
        val identity = songPreparationIdentity(title?.trim(), artist?.trim())
        if (playbackState != MusicPlaybackState.PLAYING || identity == null || elapsedRealtimeNanos < 0) {
            reset()
            return 0L
        }
        val current = Anchor(runId, sessionToken, identity,
            canonicalSpotifyTrackUri(spotifyTrackUri), elapsedRealtimeNanos)
        anchor = current
        if (previous == null || previous.runId != current.runId ||
            previous.sessionToken != current.sessionToken || previous.identity != current.identity ||
            previous.trackUri != current.trackUri || current.nanos <= previous.nanos) return 0L
        val delta = current.nanos - previous.nanos
        if (delta > MAX_OBSERVED_PLAYBACK_INTERVAL_MILLIS * 1_000_000L) return 0L
        return delta / 1_000_000L
    }
}

/**
 * The one eligibility rule for preparation, its transactional claim, and final reflection.
 * Old callback-only rows have no interval receipt and cannot establish listening duration.
 * Keep the first observation of a qualifying identity available for existing durable claims.
 */
internal fun observedSongPlaybackEligible(
    runId: String,
    startEpochMillis: Long,
    endEpochMillis: Long,
    observations: List<RunMusicObservationEntity>
): List<RunMusicObservationEntity> {
    if (endEpochMillis < startEpochMillis) return emptyList()
    val rows = observations.asSequence().filter {
        it.runId == runId && it.sourcePackage == "com.spotify.music" && it.sequenceNumber > 0 &&
            it.playbackState == MusicPlaybackState.PLAYING &&
            it.observedAtEpochMillis in startEpochMillis..endEpochMillis &&
            songPreparationIdentity(it.title?.trim(), it.artist?.trim()) != null
    }.sortedBy { it.sequenceNumber }.distinctBy { it.observationKey }.distinctBy { it.sequenceNumber }.toList()
    val totals = mutableMapOf<Pair<String, String>, Long>()
    for (row in rows) {
        val interval = row.playbackIntervalMillis ?: continue
        if (interval !in 1L..MAX_OBSERVED_PLAYBACK_INTERVAL_MILLIS ||
            row.eventKind == MusicObservationKind.SESSION_SNAPSHOT ||
            (row.elapsedRealtimeNanos ?: -1L) < interval * 1_000_000L) continue
        val identity = requireNotNull(songPreparationIdentity(row.title?.trim(), row.artist?.trim()))
        // A receipt cannot claim any time before this run officially started.
        val credited = minOf(interval, row.observedAtEpochMillis - startEpochMillis)
        totals[identity] = minOf(MINIMUM_SONG_PLAYBACK_MILLIS, totals.getOrDefault(identity, 0L) + credited)
    }
    return rows.filter {
        totals.getOrDefault(requireNotNull(songPreparationIdentity(it.title?.trim(), it.artist?.trim())), 0L) >=
            MINIMUM_SONG_PLAYBACK_MILLIS
    }
}
