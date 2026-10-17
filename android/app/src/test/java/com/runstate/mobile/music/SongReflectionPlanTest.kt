package com.runstate.mobile.music

import com.google.gson.Gson
import com.runstate.mobile.data.local.*
import com.runstate.mobile.reflection.CompletedRunEvidence
import com.runstate.mobile.reflection.reflectionRequestFromSavedRun
import org.junit.Assert.*
import org.junit.Test

class SongReflectionPlanTest {
    private val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val requestId = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"
    private val run = RunEntity(runId = runId, state = StoredRunState.COMPLETED,
        officialStartEpochMillis = 1_000L, startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 61_000L, finishEpochMillis = 61_000L,
        musicObservationRequested = true, musicReflectionSharingAllowed = true)

    private fun observation(sequence: Int = 1) = RunMusicObservationEntity(
        runId, sequence, "test-$sequence", 10_000L, 10_000_000L, "com.spotify.music",
        "Sunday Best", "Surfaces", MusicPlaybackState.PLAYING, MusicObservationKind.SESSION_SNAPSHOT)

    @Test fun requiresBothRunSpecificObservationAndSharingChoices() {
        assertNotNull(selectSongReflectionPlan(run, 61_000, listOf(observation())))
        assertNull(selectSongReflectionPlan(run.copy(musicObservationRequested = false,
            musicReflectionSharingAllowed = false), 61_000,
            listOf(observation())))
        assertNull(selectSongReflectionPlan(run.copy(musicReflectionSharingAllowed = false), 61_000,
            listOf(observation())))
    }

    @Test fun excludesForeignRunTimeProviderAndNonPlayingReports() {
        val rejected = listOf(
            observation().copy(runId = requestId),
            observation().copy(observedAtEpochMillis = 999L),
            observation().copy(observedAtEpochMillis = 61_001L),
            observation().copy(sourcePackage = "another.player"),
            observation().copy(title = null),
            observation().copy(artist = null)
        ) + MusicPlaybackState.entries.filter { it != MusicPlaybackState.PLAYING }.map {
            observation().copy(playbackState = it)
        }
        for (report in rejected) assertNull(selectSongReflectionPlan(run, 61_000, listOf(report)))
        assertNull(selectSongReflectionPlan(run, 61_000, emptyList()))
    }

    @Test fun normalizesCaseAndSpacesWithoutConfusingRemixesOrDifferentArtists() {
        assertNotNull(selectSongReflectionPlan(run, 61_000,
            listOf(observation().copy(title = " sunday  BEST ", artist = " SURFACES "))))
        for (report in listOf(observation().copy(title = "Sunday Best (Live)"),
            observation().copy(title = "Sunday Best - Remix"), observation().copy(artist = "Other artist"))) {
            assertNull(selectSongReflectionPlan(run, 61_000, listOf(report)))
        }
    }

    @Test fun canChooseAnActuallyObservedKnownSongRatherThanInventMeaningForTheLatestSong() {
        val known = observation()
        val latest = observation(2).copy(title = "Uncatalogued song", artist = "Another artist")
        val selected = selectSongReflectionPlan(run, 61_000, listOf(latest, known))
        assertEquals("Sunday Best", selected?.title)
        val evidence = reflectionRequestFromSavedRun(requestId, run, emptyList(), listOf(known, latest)).evidence
        assertEquals(selected?.cue, evidence.musicContext.songCue)
        assertEquals("MEDIA_SESSION_PARTIAL", evidence.musicContext.provenance)
        assertEquals(selected?.plan(), evidence.songPassage)
    }

    @Test fun savedEvidenceRetainsTheExactPairingWithoutDependingOnAChangedCatalog() {
        val evidence = reflectionRequestFromSavedRun(requestId, run, emptyList(), listOf(observation())).evidence
        val restored = Gson().fromJson(Gson().toJson(evidence), CompletedRunEvidence::class.java)
        assertEquals(evidence, restored)
        assertEquals(restored.musicContext.songCue?.cueId, restored.songPassage?.cueId)
        assertEquals("sunday_best_surfaces_v2", restored.songPassage?.cueId)
        assertEquals(15_500L, restored.songPassage?.startPositionMillis)
        assertEquals(10_000L, restored.songPassage?.durationMillis)
        for (song in reflectionSongCatalog) {
            assertTrue(song.cue.theme.length in 1..120)
            assertTrue(song.cue.connectionNote.length in 1..400)
            assertTrue(song.durationMillis in 1_000..10_000)
        }
    }
}
