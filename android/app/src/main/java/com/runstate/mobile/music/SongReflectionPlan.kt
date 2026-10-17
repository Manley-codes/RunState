package com.runstate.mobile.music

import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.reflection.ReflectionSongCue
import java.util.Locale

/** Frozen with the reflection evidence; it is local playback data, never a backend field. */
internal data class SongReflectionPlan(
    val cueId: String,
    val trackTitle: String,
    val artistName: String,
    val startPositionMillis: Long,
    val durationMillis: Long
) {
    fun passage() = SongPassageRequest(trackTitle, artistName, startPositionMillis, durationMillis)
}

internal data class CuratedReflectionSong(
    val cue: ReflectionSongCue,
    val title: String,
    val artist: String,
    val startPositionMillis: Long,
    val durationMillis: Long = 10_000
) {
    fun plan() = SongReflectionPlan(cue.cueId, title, artist, startPositionMillis, durationMillis)
}

/**
 * A deliberately small editorial catalog, based on the earlier liked listening example.
 * It contains no lyrics, downloaded recordings, inferred artist persona or invented run facts.
 * Passage entrances are editorial audition choices, not automatic chorus detection.
 * New songs need a checked description and passage before they can enter this catalog.
 */
internal val reflectionSongCatalog = listOf(
    CuratedReflectionSong(
        cue = ReflectionSongCue(
            cueId = "sunday_best_surfaces_v2",
            source = "RUNSTATE_CURATED",
            theme = "Everyday optimism: noticing something good in showing up and finishing",
            connectionNote = "A small good thing to take from finishing leads into the early upbeat vocal hook. " +
                "Feeling Good can acknowledge that reported feeling; Spent recognizes finishing without " +
                "claiming good feelings. With unknown Energy, the positive note is completing the run, " +
                "not an invented mood. This is editorial interpretation, not lyric text, artist persona " +
                "or evidence that music changed the run."
        ),
        // Manley confirmed this early vocal-hook entrance on the original Spotify recording.
        title = "Sunday Best", artist = "Surfaces", startPositionMillis = 15_500
    )
)

/** Only genuine, opted-in reports from this completed run can select a paired song. */
internal fun selectSongReflectionPlan(
    run: RunEntity,
    finishEpochMillis: Long,
    observations: List<RunMusicObservationEntity>,
    catalog: List<CuratedReflectionSong> = reflectionSongCatalog
): CuratedReflectionSong? {
    if (!run.musicObservationRequested || !run.musicReflectionSharingAllowed) return null
    return observations.asSequence()
        .filter { it.runId == run.runId && it.sourcePackage == "com.spotify.music" &&
            it.playbackState == MusicPlaybackState.PLAYING &&
            it.observedAtEpochMillis in run.officialStartEpochMillis..finishEpochMillis }
        .sortedByDescending { it.sequenceNumber }
        .mapNotNull { observation -> catalog.singleOrNull {
            normalizedSongText(it.title) == normalizedSongText(observation.title) &&
                normalizedSongText(it.artist) == normalizedSongText(observation.artist)
        } }
        .firstOrNull()
}

/** Keep version suffixes: a live/remix/edit title must not silently match the original. */
private fun normalizedSongText(value: String?) = value?.trim()?.lowercase(Locale.ROOT)
    ?.replace(Regex("\\s+"), " ")
