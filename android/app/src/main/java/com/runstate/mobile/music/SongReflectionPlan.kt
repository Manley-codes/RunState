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
    val durationMillis: Long,
    /** Frozen locally so an older server cannot authorize a new pairing with its legacy writer. */
    val songStudyVersion: Int = 0,
    val spotifyTrackUri: String? = null,
    val observedDurationMillis: Long? = null,
    val allowTrackSwitch: Boolean = false,
    val timingSource: String = "RUNSTATE_CURATED",
    val timingSourceId: String? = null,
    val timingVersion: Int = 0,
    /** A validated v2 playback receipt, distinct from acknowledgement of creative song analysis. */
    val playbackReceiptId: String? = null
) {
    fun passage() = SongPassageRequest(trackTitle, artistName, startPositionMillis, durationMillis,
        spotifyMediaId = spotifyTrackUri, allowTrackSwitch = allowTrackSwitch,
        observedDurationMillis = observedDurationMillis)
}

internal data class CuratedReflectionSong(
    val cue: ReflectionSongCue,
    val title: String,
    val artist: String,
    val startPositionMillis: Long,
    val durationMillis: Long = 10_000,
    val songStudyVersion: Int = 0,
    /** IDs documented by the accepted auditions; unknown editions use source lookup before loading. */
    val verifiedSpotifyTrackUris: Set<String> = emptySet()
) {
    fun plan() = SongReflectionPlan(cue.cueId, title, artist, startPositionMillis, durationMillis,
        songStudyVersion)
}

/**
 * A deliberately small editorial catalog, based on the accepted listening examples.
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
    ),
    CuratedReflectionSong(
        cue = ReflectionSongCue(
            cueId = "lovely_day_bill_withers_v1", source = "RUNSTATE_CURATED",
            theme = "A compact sequence of the recognizable repeated-title chorus.",
            connectionNote = "A warm affirmative musical response to one concrete part of this completed run. " +
                "Keep reported feelings conditional on each branch, without claiming perfect weather " +
                "or a perfect whole day."
        ),
        title = "Lovely Day", artist = "Bill Withers", startPositionMillis = 58_030,
        durationMillis = 5_870, songStudyVersion = 1,
        verifiedSpotifyTrackUris = setOf("spotify:track:0bRXwKfigvpKZUurwqAlEh")
    ),
    CuratedReflectionSong(
        cue = ReflectionSongCue(
            cueId = "stronger_kanye_west_v1", source = "RUNSTATE_CURATED",
            theme = "The already auditioned forceful electronic opening, brief and recognizable.",
            connectionNote = "A concise self-assured finish to this completed run. Let attitude and cadence " +
                "carry the connection without names, quoted lyrics or claimed training gains."
        ),
        title = "Stronger", artist = "Kanye West", startPositionMillis = 0,
        durationMillis = 10_000, songStudyVersion = 1,
        verifiedSpotifyTrackUris = setOf("spotify:track:4fzsfWzRhPawzqhX8Qt9F3")
    ),
    CuratedReflectionSong(
        cue = ReflectionSongCue(
            cueId = "good_as_hell_lizzo_v1", source = "RUNSTATE_CURATED",
            theme = "A short recognizable title response near the end of a chorus, followed by an annotated gap.",
            connectionNote = "A bright playful musical response to this completed run. Combine attitude with " +
                "one light source-grounded song detail. Keep Energy conditional, without inferred " +
                "training gains or a relationship story."
        ),
        title = "Good as Hell", artist = "Lizzo", startPositionMillis = 48_910,
        durationMillis = 4_640, songStudyVersion = 1,
        verifiedSpotifyTrackUris = setOf("spotify:track:6KgBpzTuTRPebChN0VTyzV")
    ),
    CuratedReflectionSong(
        cue = ReflectionSongCue(
            cueId = "gods_plan_drake_v1", source = "RUNSTATE_CURATED",
            theme = "A short recognizable title hook and its following statement.",
            connectionNote = "Let the record answer a relaxed, self-assured finish to this completed run. " +
                "A connection through attitude and delivery can work without explaining the song " +
                "or claiming why this runner ran."
        ),
        title = "God's Plan", artist = "Drake", startPositionMillis = 37_300,
        durationMillis = 7_300, songStudyVersion = 1
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
