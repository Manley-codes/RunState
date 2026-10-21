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
        runId, sequence, "test-$sequence", 1_000L, 1_000_000_000L, "com.spotify.music",
        "Sunday Best", "Surfaces", MusicPlaybackState.PLAYING, MusicObservationKind.SESSION_SNAPSHOT)

    /** Explicit durable playback receipts; a metadata callback alone no longer qualifies. */
    private fun qualified(vararg rows: RunMusicObservationEntity) = rows.flatMap { row ->
        listOf(row) + (1..12).map { step ->
            row.copy(sequenceNumber = row.sequenceNumber * 100 + step,
                observationKey = "${row.observationKey}-$step",
                observedAtEpochMillis = row.observedAtEpochMillis + step * 5_000L,
                elapsedRealtimeNanos = (row.observedAtEpochMillis + step * 5_000L) * 1_000_000L,
                eventKind = MusicObservationKind.PLAYBACK_SAMPLE,
                playbackIntervalMillis = if (row.sourcePackage == "com.spotify.music" &&
                    row.playbackState == MusicPlaybackState.PLAYING) 5_000L else null)
        }
    }

    @Test fun requiresBothRunSpecificObservationAndSharingChoices() {
        assertNotNull(selectSongReflectionPlan(run, 61_000, qualified(observation())))
        assertNull(selectSongReflectionPlan(run.copy(musicObservationRequested = false,
            musicReflectionSharingAllowed = false), 61_000,
            qualified(observation())))
        assertNull(selectSongReflectionPlan(run.copy(musicReflectionSharingAllowed = false), 61_000,
            qualified(observation())))
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
        for (report in rejected) assertNull(selectSongReflectionPlan(run, 61_000, qualified(report)))
        assertNull(selectSongReflectionPlan(run, 61_000, emptyList()))
    }

    @Test fun normalizesCaseAndSpacesWithoutConfusingRemixesOrDifferentArtists() {
        assertNotNull(selectSongReflectionPlan(run, 61_000,
            qualified(observation().copy(title = " sunday  BEST ", artist = " SURFACES "))))
        for (report in listOf(observation().copy(title = "Sunday Best (Live)"),
            observation().copy(title = "Sunday Best - Remix"), observation().copy(artist = "Other artist"))) {
            assertNull(selectSongReflectionPlan(run, 61_000, qualified(report)))
        }
    }

    @Test fun canChooseAnActuallyObservedKnownSongRatherThanInventMeaningForTheLatestSong() {
        val known = observation()
        val latest = observation(2).copy(title = "Uncatalogued song", artist = "Another artist")
        val selected = selectSongReflectionPlan(run, 61_000, qualified(latest, known))
        assertEquals("Sunday Best", selected?.title)
        val evidence = reflectionRequestFromSavedRun(requestId, run, emptyList(), qualified(known, latest)).evidence
        assertEquals(selected?.cue, evidence.musicContext.songCue)
        assertEquals("MEDIA_SESSION_PARTIAL", evidence.musicContext.provenance)
        assertEquals(selected?.plan(), evidence.songPassage)
    }

    @Test fun savedEvidenceRetainsTheExactPairingWithoutDependingOnAChangedCatalog() {
        val evidence = reflectionRequestFromSavedRun(requestId, run, emptyList(), qualified(observation())).evidence
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

    @Test fun acceptedPassagesFreezeTheirIndividualEntrancesAndStudyWriterRequirement() {
        val accepted = listOf(
            Triple("Lovely Day" to "Bill Withers", "lovely_day_bill_withers_v1", 58_030L to 5_870L),
            Triple("Stronger" to "Kanye West", "stronger_kanye_west_v1", 0L to 10_000L),
            Triple("Good as Hell" to "Lizzo", "good_as_hell_lizzo_v1", 48_910L to 4_640L),
            Triple("God's Plan" to "Drake", "gods_plan_drake_v1", 37_300L to 7_300L)
        )
        for ((identity, cueId, timing) in accepted) {
            val report = observation().copy(title = identity.first, artist = identity.second)
            val evidence = reflectionRequestFromSavedRun(requestId, run, emptyList(), qualified(report)).evidence
            val restored = Gson().fromJson(Gson().toJson(evidence), CompletedRunEvidence::class.java)
            val plan = requireNotNull(restored.songPassage)
            assertEquals(cueId, restored.musicContext.songCue?.cueId)
            assertEquals(cueId, plan.cueId)
            assertEquals(identity.first, plan.trackTitle)
            assertEquals(identity.second, plan.artistName)
            assertEquals(timing.first, plan.startPositionMillis)
            assertEquals(timing.second, plan.durationMillis)
            assertEquals(1, plan.songStudyVersion)
        }
        assertEquals(reflectionSongCatalog.size, reflectionSongCatalog.map { it.cue.cueId }.distinct().size)
    }

    @Test fun acceptedOriginalsDoNotSilentlyMatchLiveRemixOrAdditionalArtistCredits() {
        for (song in reflectionSongCatalog.filter { it.songStudyVersion == 1 }) {
            val rejected = listOf(
                observation().copy(title = "${song.title} (Live)", artist = song.artist),
                observation().copy(title = "${song.title} - Remix", artist = song.artist),
                observation().copy(title = song.title, artist = "${song.artist}, Another Artist")
            )
            for (report in rejected) assertNull(selectSongReflectionPlan(run, 61_000, qualified(report)))
            assertNull(selectSongReflectionPlan(run.copy(musicReflectionSharingAllowed = false),
                61_000, qualified(observation().copy(title = song.title, artist = song.artist))))
        }
    }

    @Test fun sixtySecondsAreRequiredAndOldCallbacksDoNotInventPlaybackTime() {
        val complete = qualified(observation())
        val fiftyNine = complete.dropLast(1) + complete.last().copy(
            observedAtEpochMillis = 60_000L, elapsedRealtimeNanos = 60_000_000_000L,
            playbackIntervalMillis = 4_000L)
        assertNull(selectSongReflectionPlan(run, 61_000, fiftyNine))
        assertNotNull(selectSongReflectionPlan(run, 61_000, complete))
        assertNull(selectSongReflectionPlan(run, 61_000, listOf(observation())))
        assertNull(selectSongReflectionPlan(run, 61_000,
            complete.map { it.copy(playbackIntervalMillis = null) }))
        assertNull(selectSongReflectionPlan(run, 61_000,
            complete.map { it.copy(playbackIntervalMillis = null, elapsedRealtimeNanos = null) }))
        // Re-reading the same stored receipt cannot turn 30 seconds into a minute.
        val thirty = complete.take(7)
        assertNull(selectSongReflectionPlan(run, 61_000, thirty + thirty))
    }

    @Test fun separateListensAccumulateOnlyTheirCreditedIntervals() {
        val receipts = qualified(observation())
        val first = receipts.take(7)
        val later = receipts.drop(7).map {
            it.copy(observedAtEpochMillis = it.observedAtEpochMillis + 60_000L,
                elapsedRealtimeNanos = requireNotNull(it.elapsedRealtimeNanos) + 60_000_000_000L)
        }
        assertTrue(observedSongPlaybackEligible(runId, 1_000L, 121_000L, first).isEmpty())
        assertEquals(13, observedSongPlaybackEligible(runId, 1_000L, 121_000L, first + later).size)
    }

    @Test fun playbackTrackerDoesNotCountPausesSkippedTracksOrUnobservedGaps() {
        val tracker = SongPlaybackIntervalTracker()
        fun tick(millis: Long, title: String = "One", state: MusicPlaybackState = MusicPlaybackState.PLAYING) =
            tracker.observe(runId, "session", title, "Artist", null, state, millis * 1_000_000L)
        assertEquals(0L, tick(1_000))
        assertEquals(5_000L, tick(6_000))
        assertEquals(0L, tick(8_000, title = "Skipped"))
        assertEquals(0L, tick(9_000, title = "One"))
        assertEquals(5_000L, tick(14_000))
        assertEquals(0L, tick(16_000, state = MusicPlaybackState.PAUSED))
        assertEquals(0L, tick(25_000))
        assertEquals(5_000L, tick(30_000))
        assertEquals(0L, tick(40_000))
        assertEquals(5_000L, tick(45_000))
        assertEquals(0L, tick(46_000, state = MusicPlaybackState.BUFFERING))
        assertEquals(0L, tick(50_000))
    }

    @Test fun callbackSamplesShareOneClockAndResetAcrossCaptureOrRecordingChanges() {
        val tracker = SongPlaybackIntervalTracker()
        fun tick(millis: Long, session: String = "session", owner: String = runId,
            uri: String? = null, reset: Boolean = false) = tracker.observe(
            owner, session, "One", "Artist", uri, MusicPlaybackState.PLAYING, millis * 1_000_000L, reset)
        assertEquals(0L, tick(1_000))
        assertEquals(2_000L, tick(3_000)) // Callback between periodic samples.
        assertEquals(3_000L, tick(6_000))
        assertEquals(0L, tick(6_000)) // Duplicate.
        assertEquals(0L, tick(7_000, session = "replacement"))
        assertEquals(0L, tick(8_000, session = "replacement", owner = requestId))
        assertEquals(0L, tick(9_000, session = "replacement", owner = requestId,
            uri = "spotify:track:0bRXwKfigvpKZUurwqAlEh"))
        assertEquals(0L, tick(10_000, reset = true))
        assertEquals(0L, tick(9_000)) // Backward monotonic time cannot earn credit.
        tracker.reset()
        assertEquals(0L, tick(14_000))
        assertEquals(5_000L, tick(19_000))
    }

    @Test fun queuedPauseAndBriefTrackChangeBreakContinuityEvenWhenLiveStateAlreadyReturned() {
        val tracker = SongPlaybackIntervalTracker()
        fun live(millis: Long) = tracker.observe(runId, "session", "One", "Artist", null,
            MusicPlaybackState.PLAYING, millis * 1_000_000L)
        assertEquals(0L, live(1_000))
        assertEquals(5_000L, live(6_000))
        tracker.onReportedPlaybackState(MusicPlaybackState.PAUSED)
        assertEquals(0L, live(10_000)) // Current controller has already resumed.
        tracker.onReportedPlaybackState(MusicPlaybackState.PLAYING)
        assertEquals(5_000L, live(15_000)) // Position callbacks do not reset healthy playback.
        tracker.onReportedRecording("Briefly skipped song", "Artist", null)
        assertEquals(0L, live(20_000)) // A -> B -> A before the callback queue drained.
        tracker.onReportedRecording("One", "Artist", null)
        assertEquals(5_000L, live(25_000))
    }
}
