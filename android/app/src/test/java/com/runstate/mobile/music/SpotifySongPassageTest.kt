package com.runstate.mobile.music

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class SpotifySongPassageTest {
    private class Clock : PassageClock {
        var time = 0L
        var onWait: suspend () -> Unit = {}
        override fun nowMillis() = time
        override suspend fun waitMillis(millis: Long) { time += millis; onWait() }
    }

    private class Session(private val clock: Clock) : SongPassageSession {
        var current = SongPassageSnapshot(
            sessionKey = "first-session", sourcePackage = "com.spotify.music", mediaId = "test-track",
            title = "Test title", artist = "Test artist", durationMillis = 180_000,
            positionMillis = 60_000, playback = PassagePlayback.PLAYING,
            canPause = true, canPlay = false, canSeek = true
        )
        var changedAt = clock.time
        var denied = false
        var ignorePlay = false
        var playAtWrongPosition = false
        var omitPauseAdvertisement = false
        var ignorePause = false
        var seekOffsetMillis = 0L
        var playingReportDelayMillis = 0L
        val tracks = mutableMapOf<String, SongPassageSnapshot>()
        var ignoreTrackLoad = false
        var trackLoadDelayMillis = 0L
        var ignoreEntranceSeek = false
        var ignorePendingLoadCancellation = false
        private var pendingTrack: SongPassageSnapshot? = null
        private var trackLoadsAt = 0L
        private var playingReportedAt = 0L
        var bufferPlayCalls = emptySet<Int>()
        private var playCalls = 0
        private var pendingPlay = false
        val commands = mutableListOf<String>()
        override fun snapshot(): SongPassageSnapshot {
            if (denied) throw SecurityException("No session access")
            pendingTrack?.takeIf { clock.time >= trackLoadsAt }?.let {
                pendingTrack = null
                replace(it)
            }
            val advance = if (current.playback == PassagePlayback.PLAYING) clock.time - changedAt else 0
            return current.copy(positionMillis = current.positionMillis + advance,
                playback = if (current.playback == PassagePlayback.PLAYING && clock.time < playingReportedAt)
                    PassagePlayback.OTHER else current.playback)
        }
        fun replace(snapshot: SongPassageSnapshot) { current = snapshot; changedAt = clock.time }
        override fun pause() {
            commands += "pause"
            if (ignorePause) return
            pendingPlay = false
            pendingTrack = null
            replace(snapshot().copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false))
        }
        override fun seekTo(positionMillis: Long) {
            commands += "seek:$positionMillis"
            if (ignoreEntranceSeek && commands.count { it.startsWith("seek:") } == 1) return
            // Apply the simulated seek error only to the passage entrance, never restoration.
            replace(snapshot().copy(positionMillis = positionMillis +
                if (commands.count { it.startsWith("seek:") } == 1) seekOffsetMillis else 0L))
        }
        override fun play() {
            commands += "play"
            playCalls++
            if (playCalls in bufferPlayCalls) {
                pendingPlay = true
                replace(snapshot().copy(playback = PassagePlayback.OTHER, canPause = false, canPlay = false))
                return
            }
            if (!ignorePlay) {
                val before = snapshot()
                replace(before.copy(playback = PassagePlayback.PLAYING, canPlay = false,
                    canPause = !omitPauseAdvertisement,
                    positionMillis = if (playAtWrongPosition) 120_000 else before.positionMillis))
                playingReportedAt = clock.time + playingReportDelayMillis
            }
        }
        fun completePendingPlay() {
            if (pendingPlay) {
                pendingPlay = false
                replace(snapshot().copy(playback = PassagePlayback.PLAYING, canPlay = false, canPause = true))
                playingReportedAt = clock.time + playingReportDelayMillis
            }
        }
        fun bufferCurrentPlay() {
            pendingPlay = true
            replace(snapshot().copy(playback = PassagePlayback.OTHER, canPause = false, canPlay = false))
        }
        override fun prepareFromTrackUri(uri: String): Boolean = loadTrack(uri, true)
        override fun playFromTrackUri(uri: String): Boolean = loadTrack(uri, false)
        override fun cancelPendingTrackLoad() {
            val uncooperativePending = pendingTrack
            pause()
            if (ignorePendingLoadCancellation) pendingTrack = uncooperativePending
        }
        private fun loadTrack(uri: String, prepare: Boolean): Boolean {
            commands += if (prepare) "prepareTrack:$uri" else "playTrack:$uri"
            val target = tracks[uri] ?: return false
            if (ignoreTrackLoad) return true
            val loaded = target.copy(positionMillis = 0L,
                playback = if (prepare) PassagePlayback.PAUSED else PassagePlayback.PLAYING,
                canPlay = prepare, canPause = !prepare)
            if (trackLoadDelayMillis > 0L) {
                pendingTrack = loaded
                trackLoadsAt = clock.time + trackLoadDelayMillis
            } else replace(loaded)
            return true
        }
    }

    private val request = SongPassageRequest("Test title", "Test artist", 5_000)
    private val originalUri = "spotify:track:" + "A".repeat(22)
    private val selectedUri = "spotify:track:" + "B".repeat(22)
    private val selectedRequest = SongPassageRequest("Chosen title", "Chosen artist", 45_000,
        durationMillis = 4_000, spotifyMediaId = selectedUri, allowTrackSwitch = true,
        observedDurationMillis = 210_000)

    private fun Session.enableTrackSelection(prepare: Boolean = true, originallyPlaying: Boolean = true) {
        current = current.copy(mediaId = originalUri, canPrepareTrack = prepare, canPlayTrack = true,
            playback = if (originallyPlaying) PassagePlayback.PLAYING else PassagePlayback.PAUSED,
            canPause = originallyPlaying, canPlay = !originallyPlaying)
        tracks[originalUri] = current
        tracks[selectedUri] = current.copy(mediaId = selectedUri, title = "Chosen title",
            artist = "Chosen artist", durationMillis = 210_000)
    }

    @Test fun chosenObservedTrackLoadsSilentlyAndRestoresPriorTrackPositionAndPlayingState() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection() }
        val result = SpotifySongPassageController(session, clock).play(selectedRequest)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri", "seek:45000", "play", "pause",
            "prepareTrack:$originalUri", "seek:60000", "play"), session.commands)
        assertEquals(originalUri, session.snapshot().mediaId)
        assertEquals(60_000L, session.snapshot().positionMillis)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
        assertEquals(4_000L, clock.time)
    }

    @Test fun chosenTrackRestoresAnOriginallyPausedSongWithoutResumingIt() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(originallyPlaying = false) }
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true),
            SpotifySongPassageController(session, clock).play(selectedRequest))
        assertEquals(listOf("prepareTrack:$selectedUri", "seek:45000", "play", "pause",
            "prepareTrack:$originalUri", "seek:60000"), session.commands)
        assertEquals(originalUri, session.snapshot().mediaId)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun advertisedPlayFromFallbackImmediatelyContainsLoadedPlaybackBeforeSeeking() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(prepare = false) }
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true),
            SpotifySongPassageController(session, clock).play(selectedRequest))
        assertEquals(listOf("pause", "playTrack:$selectedUri", "pause", "seek:45000", "play", "pause",
            "playTrack:$originalUri", "pause", "seek:60000", "play"), session.commands)
        assertEquals(originalUri, session.snapshot().mediaId)
    }

    @Test fun missingLoadingCapabilityOrUnrestorablePriorIdentityNeverTouchesPlayback() = runBlocking {
        for (missingCapability in listOf(true, false)) {
            val clock = Clock()
            val session = Session(clock).apply {
                enableTrackSelection()
                current = if (missingCapability) current.copy(canPrepareTrack = false, canPlayTrack = false)
                    else current.copy(mediaId = "not-an-exact-spotify-track")
            }
            assertEquals(SongPassageStatus.UNAVAILABLE,
                SpotifySongPassageController(session, clock).play(selectedRequest).status)
            assertTrue(session.commands.isEmpty())
        }
    }

    @Test fun currentSongPathDoesNotImplicitlyGainPermissionToSwitchTracks() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection() }
        assertEquals(SongPassageStatus.UNAVAILABLE, SpotifySongPassageController(session, clock)
            .play(selectedRequest.copy(allowTrackSwitch = false)).status)
        assertTrue(session.commands.isEmpty())
    }

    @Test fun canonicalTrackIdentityRejectsUrlsAlbumsEpisodesWhitespaceAndMalformedIds() {
        assertEquals(selectedUri, canonicalSpotifyTrackUri(selectedUri))
        for (value in listOf(null, " $selectedUri", "$selectedUri ", "spotify:track:" + "B".repeat(21),
            "spotify:album:" + "B".repeat(22), "spotify:episode:" + "B".repeat(22),
            "https://open.spotify.com/track/" + "B".repeat(22), "spotify:track:" + "_".repeat(22)))
            assertNull(canonicalSpotifyTrackUri(value))
        assertEquals(selectedUri, spotifySessionMediaId(null, selectedUri))
        assertEquals(selectedUri, spotifySessionMediaId("legacy-provider-id", selectedUri))
        assertEquals(originalUri, spotifySessionMediaId(originalUri, selectedUri))
        assertEquals("legacy-provider-id", spotifySessionMediaId("legacy-provider-id", null))
    }

    @Test fun wrongLoadedMetadataOrDurationCannotBecomeTheRequestedPassage() = runBlocking {
        for (mismatch in listOf("title", "artist", "duration", "session")) {
            val clock = Clock()
            val session = Session(clock).apply {
                enableTrackSelection(prepare = false)
                tracks[selectedUri] = requireNotNull(tracks[selectedUri]).let {
                    when (mismatch) {
                        "title" -> it.copy(title = "Different recording")
                        "artist" -> it.copy(artist = "Different artist")
                        "duration" -> it.copy(durationMillis = 210_002)
                        else -> it.copy(sessionKey = "replacement-session")
                    }
                }
            }
            val result = SpotifySongPassageController(session, clock).play(selectedRequest)
            assertEquals(mismatch, SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertFalse(session.commands.any { it.startsWith("seek:") })
            if (mismatch != "session") assertEquals(mismatch, PassagePlayback.PAUSED, session.snapshot().playback)
        }
    }

    @Test fun ignoredLoadHasBoundedTimeoutAndCancelsOnlyItsOwnPendingSelection() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(); ignoreTrackLoad = true }
        val result = SpotifySongPassageController(session, clock).play(selectedRequest)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(5_000L, clock.time)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri", "pause"), session.commands)
        assertEquals(originalUri, session.snapshot().mediaId)
    }

    @Test fun cooperativePlayerHonorsPendingLoadPauseWhenCoroutineIsCancelled() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(prepare = false); trackLoadDelayMillis = 1_000L }
        clock.onWait = { throw CancellationException("Leave screen") }
        try {
            SpotifySongPassageController(session, clock).play(selectedRequest)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(listOf("pause", "playTrack:$selectedUri", "pause"), session.commands)
        clock.time = 5_000L
        assertEquals(originalUri, session.snapshot().mediaId)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun ignoredCancellationRequestNeverClaimsThatTheForeignPlayerStoppedOrRestored() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            enableTrackSelection(prepare = false)
            trackLoadDelayMillis = 1_000L
            ignorePendingLoadCancellation = true
        }
        val controller = SpotifySongPassageController(session, clock)
        clock.onWait = { controller.stop() }
        assertEquals(SongPassageResult(SongPassageStatus.INTERRUPTED, false), controller.play(selectedRequest))
        assertEquals(listOf("pause", "playTrack:$selectedUri", "pause"), session.commands)
        clock.time = 5_000L
        // Android sends a request, not a remote cancellation guarantee. An uncooperative
        // provider may still start later; the result must never call this completed/restored.
        assertEquals(selectedUri, session.snapshot().mediaId)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun incompleteLoadingMetadataWaitsForFullIdentityBeforeAnySeekOrPassage() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            enableTrackSelection()
            tracks[selectedUri] = requireNotNull(tracks[selectedUri]).copy(title = null, durationMillis = 0L)
        }
        clock.onWait = {
            if (clock.time == 200L) session.replace(session.current.copy(title = "Chosen title", durationMillis = 210_000L))
        }
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true),
            SpotifySongPassageController(session, clock).play(selectedRequest))
        assertEquals(4_200L, clock.time)
        assertEquals(originalUri, session.snapshot().mediaId)
    }

    @Test fun distantUserSeekOnThePendingTargetIsNotOverriddenByCleanup() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            enableTrackSelection(prepare = false)
            tracks[selectedUri] = requireNotNull(tracks[selectedUri]).copy(title = "Metadata mismatch")
        }
        val controller = SpotifySongPassageController(session, clock) { event ->
            if (event.reason == SongPassageReason.TITLE_CHANGED)
                session.replace(session.current.copy(positionMillis = 120_000))
        }
        assertEquals(SongPassageStatus.INTERRUPTED, controller.play(selectedRequest).status)
        assertEquals(listOf("pause", "playTrack:$selectedUri"), session.commands)
        assertEquals(120_000L, session.snapshot().positionMillis)
    }

    @Test fun userSeekWhileSelectedRecordingMetadataLoadsWinsOverPassageAdmission() = runBlocking {
        for (prepare in listOf(true, false)) {
            val clock = Clock()
            val session = Session(clock).apply {
                enableTrackSelection(prepare = prepare)
                tracks[selectedUri] = requireNotNull(tracks[selectedUri]).copy(title = null)
            }
            var commandsAtUserSeek = 0
            clock.onWait = {
                if (session.current.mediaId == selectedUri && session.current.title == null) {
                    session.replace(session.current.copy(title = "Chosen title", positionMillis = 120_000))
                    commandsAtUserSeek = session.commands.size
                }
            }
            assertEquals(SongPassageResult(SongPassageStatus.INTERRUPTED, false),
                SpotifySongPassageController(session, clock).play(selectedRequest))
            assertTrue(commandsAtUserSeek > 0)
            assertEquals(commandsAtUserSeek, session.commands.size)
            assertFalse(session.commands.any { it.startsWith("seek:") })
            assertEquals(120_000L, session.snapshot().positionMillis)
        }
    }

    @Test fun userSeekWhilePriorRecordingReloadsWinsOverAutomaticRestoration() = runBlocking {
        for (prepare in listOf(true, false)) {
            val clock = Clock()
            val session = Session(clock).apply {
                enableTrackSelection(prepare = prepare)
                tracks[originalUri] = requireNotNull(tracks[originalUri]).copy(title = null)
            }
            var commandsAtUserSeek = 0
            clock.onWait = {
                if (session.current.mediaId == originalUri && session.current.title == null) {
                    session.replace(session.current.copy(title = "Test title", positionMillis = 120_000))
                    commandsAtUserSeek = session.commands.size
                }
            }
            assertEquals(SongPassageResult(SongPassageStatus.INTERRUPTED, false),
                SpotifySongPassageController(session, clock).play(selectedRequest))
            assertTrue(commandsAtUserSeek > 0)
            assertEquals(commandsAtUserSeek, session.commands.size)
            assertFalse(session.commands.contains("seek:60000"))
            assertEquals(originalUri, session.snapshot().mediaId)
            assertEquals(120_000L, session.snapshot().positionMillis)
        }
    }

    @Test fun userTrackChangeDuringLoadingIsNotPausedOrReplacedByCleanup() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(); ignoreTrackLoad = true }
        clock.onWait = { session.replace(session.current.copy(mediaId = "spotify:track:" + "C".repeat(22),
            title = "User choice", playback = PassagePlayback.PLAYING)) }
        assertEquals(SongPassageStatus.INTERRUPTED, SpotifySongPassageController(session, clock).play(selectedRequest).status)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri"), session.commands)
        assertEquals("User choice", session.snapshot().title)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun ignoredEntranceSeekRecoversPriorTrackOnlyWhileOurChosenTrackRemainsPaused() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(); ignoreEntranceSeek = true }
        val result = SpotifySongPassageController(session, clock).play(selectedRequest)
        assertEquals(SongPassageResult(SongPassageStatus.INTERRUPTED, true), result)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri", "seek:45000",
            "prepareTrack:$originalUri", "seek:60000", "play"), session.commands)
        assertEquals(originalUri, session.snapshot().mediaId)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun userSeekDuringFailedEntrancePreventsAutomaticRestoration() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection(); ignoreEntranceSeek = true }
        clock.onWait = { session.replace(session.current.copy(positionMillis = 120_000)) }
        val result = SpotifySongPassageController(session, clock).play(selectedRequest)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri", "seek:45000"), session.commands)
        assertEquals(selectedUri, session.snapshot().mediaId)
        assertEquals(120_000L, session.snapshot().positionMillis)
    }

    @Test fun newUserSongDuringSelectedPassageWinsOverAutomaticRestoration() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { enableTrackSelection() }
        clock.onWait = { session.replace(session.current.copy(mediaId = "spotify:track:" + "C".repeat(22),
            title = "User choice", positionMillis = 0)) }
        val result = SpotifySongPassageController(session, clock).play(selectedRequest)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("pause", "prepareTrack:$selectedUri", "seek:45000", "play"), session.commands)
        assertEquals("User choice", session.snapshot().title)
    }

    @Test fun completedPassageRestoresOriginalPlayingPositionAndUsesBoundedCommands() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(listOf("pause", "seek:5000", "play", "pause", "seek:60000", "play"), session.commands)
        assertEquals(10_000L, clock.time)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun originallyPausedSongIsRestoredWithoutAnUnrequestedResume() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.FINISHED, result.status)
        assertEquals(listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun advancedPlayingConfirmationConsumesPassageInsteadOfExtendingItsEnding() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            playingReportDelayMillis = 1_000L
        }
        var positionAtEnding: Long? = null
        val result = SpotifySongPassageController(session, clock) { event ->
            if (event.stage == SongPassageStage.ENDING_PAUSE && event.reason == SongPassageReason.ENTERED)
                positionAtEnding = session.snapshot().positionMillis
        }.play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(15_000L, positionAtEnding)
        assertEquals(10_000L, clock.time)
        assertEquals(listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
        assertEquals(60_000L, session.snapshot().positionMillis)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun acceptedLateEntranceStillStopsAtRequestedSongEndpoint() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { seekOffsetMillis = 1_000L }
        var positionAtEnding: Long? = null
        val result = SpotifySongPassageController(session, clock) { event ->
            if (event.stage == SongPassageStage.ENDING_PAUSE && event.reason == SongPassageReason.ENTERED)
                positionAtEnding = session.snapshot().positionMillis
        }.play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(15_000L, positionAtEnding)
        assertEquals(9_000L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause", "seek:60000", "play"), session.commands)
        assertEquals(60_000L, session.snapshot().positionMillis)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun delayedPlaybackStartUsesFirstPlayingReportWithoutExtendingTheSongEndpoint() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false,
                positionUpdateAgeMillis = 226_207L)
            bufferPlayCalls = setOf(1)
            playingReportDelayMillis = 700L
        }
        clock.onWait = {
            if (clock.time == 1_900L) {
                session.current = session.current.copy(durationMillis = 180_001L,
                    positionUpdateAgeMillis = 0L)
                session.completePendingPlay()
            }
        }
        var confirmedAt: Long? = null
        var positionAtEnding: Long? = null
        val result = SpotifySongPassageController(session, clock) { event ->
            if (event.stage == SongPassageStage.PLAYING && event.reason == SongPassageReason.ENTERED)
                confirmedAt = clock.time
            if (event.stage == SongPassageStage.ENDING_PAUSE && event.reason == SongPassageReason.ENTERED)
                positionAtEnding = session.snapshot().positionMillis
        }.play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(2_600L, confirmedAt)
        assertEquals(15_000L, positionAtEnding)
        assertEquals(11_900L, clock.time)
        assertEquals(listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun delayedRestoreResumeIsConfirmedWithoutAssumingImmediatePlayback() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(2) }
        clock.onWait = { if (clock.time == 12_500L) session.completePendingPlay() }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(12_500L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause", "seek:60000", "play"), session.commands)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun firstPlayingReportOutsideTheBoundedEntranceIntervalIsCancelled() = runBlocking {
        // After 2600 ms, a valid first report is bounded by target−1500 through target+2600+1500.
        for (offsetAtStart in listOf(-2_201L, 3_401L)) {
            val clock = Clock()
            val session = Session(clock).apply {
                current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
                bufferPlayCalls = setOf(1)
                playingReportDelayMillis = 700L
            }
            clock.onWait = {
                if (clock.time == 1_900L) {
                    session.completePendingPlay()
                    session.replace(session.current.copy(positionMillis = 5_000L + offsetAtStart))
                }
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(2_600L, clock.time)
            assertEquals(listOf("seek:5000", "play", "pause"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        }
    }

    @Test fun delayedFirstConfirmationDoesNotRelaxSubsequentUserSeekDetection() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            bufferPlayCalls = setOf(1)
            playingReportDelayMillis = 700L
        }
        clock.onWait = {
            if (clock.time == 1_900L) session.completePendingPlay()
            if (clock.time == 2_700L)
                session.replace(session.snapshot().copy(positionMillis = session.snapshot().positionMillis + 1_501L))
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(2_700L, clock.time)
        assertEquals(listOf("seek:5000", "play"), session.commands)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun acceptedEarlyEntranceCannotExtendExistingWallDurationCap() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { seekOffsetMillis = -1_000L }
        var positionAtEnding: Long? = null
        val result = SpotifySongPassageController(session, clock) { event ->
            if (event.stage == SongPassageStage.ENDING_PAUSE && event.reason == SongPassageReason.ENTERED)
                positionAtEnding = session.snapshot().positionMillis
        }.play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(14_000L, positionAtEnding)
        assertEquals(10_000L, clock.time)
        assertEquals(60_000L, session.snapshot().positionMillis)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun missingCapabilityOrWrongRecordingNeverTouchesPlayback() = runBlocking {
        val variants = listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(canSeek = false) }, { it.copy(canPause = false) },
            { it.copy(sourcePackage = "other.player") }, { it.copy(title = "Different title") },
            { it.copy(artist = "Different artist") }, { it.copy(durationMillis = 0) },
            { it.copy(durationMillis = Long.MAX_VALUE) },
            { it.copy(positionMillis = -1) }, { it.copy(playback = PassagePlayback.OTHER) }
        )
        for (variant in variants) {
            val clock = Clock()
            val session = Session(clock).apply { current = variant(current) }
            assertEquals(SongPassageStatus.UNAVAILABLE, SpotifySongPassageController(session, clock).play(request).status)
            assertTrue(session.commands.isEmpty())
        }
    }

    @Test fun explicitRecordingIdMustMatchAndPassageCannotCrossTheSongEnd() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val controller = SpotifySongPassageController(session, clock)
        assertEquals(SongPassageStatus.UNAVAILABLE, controller.play(request.copy(spotifyMediaId = "other-id")).status)
        assertEquals(SongPassageStatus.UNAVAILABLE, controller.play(request.copy(startPositionMillis = 175_000)).status)
        assertEquals(SongPassageStatus.UNAVAILABLE,
            controller.play(request.copy(startPositionMillis = Long.MAX_VALUE)).status)
        assertTrue(session.commands.isEmpty())
    }

    @Test fun navigationOrExplicitStopPausesOwnedPassageAndNeverRestoresOrResumes() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val controller = SpotifySongPassageController(session, clock)
        clock.onWait = { if (clock.time >= 500) controller.stop() }
        val result = controller.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun changedSongOrSessionIsNeverPausedOrRestoredByOldOwner() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(mediaId = "next-track", title = "Next title") },
            { it.copy(sessionKey = "replacement-session") }
        )) {
            val clock = Clock()
            val session = Session(clock)
            clock.onWait = { if (clock.time == 500L) session.replace(change(session.snapshot())) }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
            assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
        }
    }

    @Test fun manualPauseOrSeekWinsOverAutomaticRestoration() = runBlocking {
        for (pause in listOf(true, false)) {
            val clock = Clock()
            val session = Session(clock)
            clock.onWait = {
                if (clock.time == 500L) session.replace(session.snapshot().let {
                    if (pause) it.copy(playback = PassagePlayback.PAUSED)
                    else it.copy(positionMillis = 90_000)
                })
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
        }
    }

    @Test fun permissionLossFailsWithoutTryingToResume() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        clock.onWait = { if (clock.time == 500L) session.denied = true }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.FAILED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
    }

    @Test fun ignoredCommandTimesOutRatherThanWaitingForever() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { ignorePlay = true }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertEquals(3_000L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
    }

    @Test fun bufferingPlayTimeoutCancelsQueuedPlaybackBeforeItCanStart() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(1) }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertEquals(3_000L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        session.completePendingPlay()
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(5_000L, session.snapshot().positionMillis)
    }

    @Test fun navigationDuringBufferingCancelsQueuedPlayWithoutRestoringOrResuming() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(1) }
        val controller = SpotifySongPassageController(session, clock)
        clock.onWait = { if (clock.time == 500L) controller.stop() }
        val result = controller.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        session.completePendingPlay()
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun cancellationDuringBufferingCancelsQueuedPlay() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(1) }
        clock.onWait = { throw CancellationException("Screen left while buffering") }
        try {
            SpotifySongPassageController(session, clock).play(request)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
            session.completePendingPlay()
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        }
    }

    @Test fun bufferingRestoreResumeTimeoutIsCancelledInsteadOfPlayingLater() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(2) }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(13_000L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause", "seek:60000", "play", "pause"),
            session.commands)
        session.completePendingPlay()
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun pendingBufferingDoesNotOverrideChangedRecordingOrSession() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(mediaId = "next-track", title = "Next title") },
            { it.copy(sessionKey = "replacement-session") }
        )) {
            val clock = Clock()
            val session = Session(clock).apply { bufferPlayCalls = setOf(1) }
            clock.onWait = { if (clock.time == 500L) session.replace(change(session.snapshot())) }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
        }
    }

    @Test fun playingTheWrongPositionCannotBeReportedAsSuccess() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { playAtWrongPosition = true }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        // The play command was never confirmed at our entrance. Cancel it without restoring/resuming.
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun originallyPausedWrongEntranceIsCancelledWithoutRestoreOrResume() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            playAtWrongPosition = true
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(listOf("seek:5000", "play", "pause"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(120_000L, session.snapshot().positionMillis)
    }

    @Test fun unconfirmedBufferingAtWrongPositionCannotStartAfterCancellation() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { bufferPlayCalls = setOf(1) }
        clock.onWait = {
            if (clock.time == 500L) session.replace(session.snapshot().copy(positionMillis = 90_000L))
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        session.completePendingPlay()
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(90_000L, session.snapshot().positionMillis)
    }

    @Test fun confirmedPlaybackBufferingIsCancelledOnInterruptionOrExplicitStop() = runBlocking {
        for (explicitStop in listOf(false, true)) {
            val clock = Clock()
            val session = Session(clock)
            val controller = SpotifySongPassageController(session, clock)
            clock.onWait = {
                if (clock.time == 500L) {
                    session.bufferCurrentPlay()
                    if (explicitStop) controller.stop()
                }
            }
            val result = controller.play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
            session.completePendingPlay()
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
            assertEquals(5_500L, session.snapshot().positionMillis)
        }
    }

    @Test fun confirmedBufferingOutsideOurTrajectoryOrOnChangedRecordingIsNotTouched() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(positionMillis = 90_000L) },
            { it.copy(mediaId = "new-recording", title = "New title") }
        )) {
            val clock = Clock()
            val session = Session(clock)
            clock.onWait = {
                if (clock.time == 500L) {
                    session.bufferCurrentPlay()
                    session.replace(change(session.snapshot()))
                }
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
        }
    }

    @Test fun confirmedPlaybackStillPausesAtEndOrStopWithoutAdvertisedPauseAction() = runBlocking {
        for (explicitStop in listOf(false, true)) {
            val clock = Clock()
            val session = Session(clock).apply {
                current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
                omitPauseAdvertisement = true
            }
            val controller = SpotifySongPassageController(session, clock)
            clock.onWait = { if (explicitStop && clock.time == 500L) controller.stop() }
            val result = controller.play(request)
            assertEquals(if (explicitStop) SongPassageStatus.INTERRUPTED else SongPassageStatus.FINISHED,
                result.status)
            assertEquals(if (explicitStop) listOf("seek:5000", "play", "pause")
                else listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
            assertEquals(!explicitStop, result.playbackRestored)
        }
    }

    @Test fun ignoredEndingPauseCannotReportSuccessOrRestoreAndResume() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            omitPauseAdvertisement = true
            ignorePause = true
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertFalse(result.playbackRestored)
        assertEquals(13_000L, clock.time)
        assertEquals(listOf("seek:5000", "play", "pause", "pause"), session.commands)
        assertEquals(PassagePlayback.PLAYING, session.snapshot().playback)
    }

    @Test fun cancellationPropagatesButStopsOnlyTheOwnedPassage() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        clock.onWait = { throw CancellationException("Screen left") }
        try {
            SpotifySongPassageController(session, clock).play(request)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        }
    }

    @Test fun secondConcurrentRequestCannotAcquireTheFirstRequestsPlayer() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val controller = SpotifySongPassageController(session, clock)
        var attempted = false
        clock.onWait = {
            if (!attempted) {
                attempted = true
                assertEquals(SongPassageStatus.UNAVAILABLE, controller.play(request).status)
            }
        }
        assertEquals(SongPassageStatus.FINISHED, controller.play(request).status)
        assertEquals(2, session.commands.count { it == "play" })
    }

    @Test fun invalidRequestBoundsAreRejectedBeforeAnyControllerUse() {
        for (invalid in listOf(
            { request.copy(trackTitle = " ") }, { request.copy(artistName = "") },
            { request.copy(startPositionMillis = -1) }, { request.copy(durationMillis = 10_001) }
        )) {
            try { invalid(); fail("Invalid request was accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun diagnosticsIdentifyEarlyPlaybackDriftAndUnconfirmedPlayCancellation() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { playAtWrongPosition = true }
        session.current = session.current.copy(positionUpdateAgeMillis = 1_700L)
        val events = mutableListOf<SongPassageDiagnostic>()
        val result = SpotifySongPassageController(session, clock) { events += it }.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        val failure = events.first { it.reason == SongPassageReason.POSITION_DRIFT }
        assertEquals(SongPassageStage.ENTRANCE_PLAY, failure.stage)
        assertEquals(PassagePlayback.PLAYING, failure.playback)
        assertEquals(115_000L, failure.positionErrorMillis)
        assertEquals(1_700L, failure.positionUpdateAgeMillis)
        assertEquals(0L, failure.stageElapsedMillis)
        assertTrue(events.any { it.stage == SongPassageStage.CLEANUP &&
            it.reason == SongPassageReason.CLEANUP_PAUSE_SENT && it.positionErrorMillis == 115_000L })
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
    }

    @Test fun diagnosticsDistinguishMetadataChangeWithoutIncludingIdentity() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val events = mutableListOf<SongPassageDiagnostic>()
        clock.onWait = {
            if (clock.time == 500L) session.replace(session.snapshot().copy(
                durationMillis = 180_002L, positionUpdateAgeMillis = -20L))
        }
        val result = SpotifySongPassageController(session, clock) { events += it }.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        val failure = events.first { it.reason == SongPassageReason.DURATION_CHANGED }
        assertEquals(SongPassageStage.PLAYING, failure.stage)
        assertEquals(500L, failure.stageElapsedMillis)
        assertEquals(-20L, failure.positionUpdateAgeMillis)
        assertEquals(2L, failure.durationDeltaMillis)
        assertTrue(events.none { it.toString().contains("Test title") || it.toString().contains("test-track") })
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun durationDiagnosticClampsAnInvalidReportWithoutOverflowOrContinuingPlayback() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val events = mutableListOf<SongPassageDiagnostic>()
        clock.onWait = {
            if (clock.time == 500L) session.replace(session.snapshot().copy(durationMillis = Long.MAX_VALUE))
        }
        val result = SpotifySongPassageController(session, clock) { events += it }.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        val failure = events.first { it.reason == SongPassageReason.DURATION_CHANGED }
        assertEquals(86_400_000L - 180_000L, failure.durationDeltaMillis)
        assertTrue(events.none { it.toString().contains("Test title") || it.toString().contains("test-track") })
    }

    @Test fun singleMillisecondDurationRefinementPreservesPassageAndPausedRestoration() = runBlocking {
        for (delta in listOf(-1L, 1L)) {
            val clock = Clock()
            val session = Session(clock).apply {
                current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            }
            clock.onWait = {
                if (clock.time == 500L) session.current = session.current.copy(durationMillis = 180_000L + delta)
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
            assertEquals(listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
            assertEquals(60_000L, session.snapshot().positionMillis)
            assertEquals(180_000L + delta, session.snapshot().durationMillis)
            assertEquals(10_000L, clock.time)
        }
    }

    @Test fun singleMillisecondRefinementBeforePlayingConfirmationKeepsTheOriginalEndpoint() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply {
            current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            playingReportDelayMillis = 1_000L
        }
        clock.onWait = {
            if (clock.time == 500L) session.current = session.current.copy(durationMillis = 180_001L)
        }
        val result = SpotifySongPassageController(session, clock).play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(listOf("seek:5000", "play", "pause", "seek:60000"), session.commands)
        assertEquals(10_000L, clock.time)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        assertEquals(60_000L, session.snapshot().positionMillis)
    }

    @Test fun twoMillisecondChangesStillAbortAndCancelOwnedPlaybackWithoutRestoration() = runBlocking {
        for (delta in listOf(-2L, 2L)) {
            val clock = Clock()
            val session = Session(clock).apply {
                current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
            }
            clock.onWait = {
                if (clock.time == 500L) session.current = session.current.copy(durationMillis = 180_000L + delta)
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(listOf("seek:5000", "play", "pause"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
        }
    }

    @Test fun durationRefinementAllowanceNeverAccumulatesBeyondTheAdmittedDuration() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val events = mutableListOf<SongPassageDiagnostic>()
        clock.onWait = {
            if (clock.time == 500L) session.current = session.current.copy(durationMillis = 180_001L)
            if (clock.time == 600L) session.current = session.current.copy(durationMillis = 180_002L)
        }
        val result = SpotifySongPassageController(session, clock) { events += it }.play(request)
        assertEquals(SongPassageStatus.INTERRUPTED, result.status)
        assertEquals(2L, events.first { it.reason == SongPassageReason.DURATION_CHANGED }.durationDeltaMillis)
        assertEquals(600L, clock.time)
        assertEquals(listOf("pause", "seek:5000", "play", "pause"), session.commands)
    }

    @Test fun aOneMillisecondDifferenceNeverAllowsChangedRecordingIdentityOrInvalidMetadata() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(mediaId = "next-track", durationMillis = 180_001L) },
            { it.copy(sessionKey = "next-session", durationMillis = 180_001L) },
            { it.copy(title = "Next title", durationMillis = 180_001L) },
            { it.copy(artist = "Another artist", durationMillis = 180_001L) },
            { it.copy(sourcePackage = "another.player", durationMillis = 180_001L) },
            { it.copy(durationMillis = 0L) }, { it.copy(durationMillis = 999L) },
            { it.copy(durationMillis = Long.MIN_VALUE) }
        )) {
            val clock = Clock()
            val session = Session(clock)
            clock.onWait = { if (clock.time == 500L) session.replace(change(session.snapshot())) }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
        }
    }

    @Test fun durationChangeDuringPendingPlayCancelsQueuedStartWithoutRestoringOrResuming() = runBlocking {
        for (buffering in listOf(true, false)) {
            val clock = Clock()
            val session = Session(clock).apply {
                current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false)
                if (buffering) bufferPlayCalls = setOf(1) else ignorePlay = true
            }
            clock.onWait = {
                if (clock.time == 500L) session.replace(session.snapshot().copy(durationMillis = 180_073L))
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(listOf("seek:5000", "play", "pause"), session.commands)
            session.completePendingPlay()
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
            assertEquals(5_000L, session.snapshot().positionMillis)
        }
    }

    @Test fun durationOnlyFailureNeverOverridesNewIdentityUserPauseOrDistantSeek() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(mediaId = "next-track") }, { it.copy(sessionKey = "next-session") },
            { it.copy(sourcePackage = "another.player") }, { it.copy(title = "Next title") },
            { it.copy(artist = "Another artist") }, { it.copy(positionMillis = 90_000L) },
            { it.copy(playback = PassagePlayback.PAUSED) }, { it.copy(mediaId = null) }
        )) {
            val clock = Clock()
            val session = Session(clock)
            clock.onWait = {
                if (clock.time == 500L) session.replace(change(session.snapshot()).copy(durationMillis = 180_073L))
            }
            val result = SpotifySongPassageController(session, clock).play(request)
            assertEquals(SongPassageStatus.INTERRUPTED, result.status)
            assertFalse(result.playbackRestored)
            assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
        }
    }

    @Test fun speechFocusHandoffOccursOnlyAfterPausedBaselineAndConfirmedEntranceSeek() = runBlocking {
        val clock = Clock()
        val session = Session(clock).apply { current = current.copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false) }
        var handoffs = 0
        val controller = SpotifySongPassageController(session, clock, onEntranceReady = {
            handoffs++
            assertEquals(listOf("pause", "seek:5000"), session.commands)
            assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
            assertEquals(5_000L, session.snapshot().positionMillis)
        }, quiescePausedBeforeEntry = true)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), controller.play(request))
        assertEquals(1, handoffs)
        assertEquals(PassagePlayback.PAUSED, session.snapshot().playback)
    }

    @Test fun officialConnectionFailureDiagnosticContainsOnlyBoundedClassName() {
        val failure = IllegalStateException("Do not expose a token, URI, account or provider message.",
            IllegalArgumentException("Private nested details."))
        assertEquals("IllegalStateException", spotifyRemoteFailureCategory(failure))
        assertEquals("UnknownFailure", spotifyRemoteFailureCategory(object : RuntimeException("Private details.") {}))
        assertEquals(null, SpotifyRemoteConnectionState(SpotifyRemoteConnectionStatus.DISCONNECTED, true).failureCategory)
    }

    @Test fun sdkDisconnectNativeCleanupPausesOnlyTheExactOwnedPlaybackTrajectory() {
        val owned = Session(Clock()).current.copy(mediaId = selectedUri, playback = PassagePlayback.PLAYING)
        assertTrue(mayPauseNativeSpotifyCleanup(owned, owned.copy(positionMillis = owned.positionMillis + 100), 100))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(mediaId = originalUri), 0))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(sessionKey = "replacement"), 0))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(positionMillis = 120_000), 100))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(playback = PassagePlayback.PAUSED), 0))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(durationMillis = owned.durationMillis + 2), 0))
        assertFalse(mayPauseNativeSpotifyCleanup(owned, owned.copy(positionMillis = owned.positionMillis + 4_000), 4_000))
    }

    @Test fun officialLateLoadWaitsForBothCommandAcknowledgementsAndConfirmedPause() {
        val pending = SpotifyPendingTrackLoad(selectedUri, originalUri, 60_000L, 100L).apply { cancelled = true }
        assertEquals(SpotifyLoadAction.WAIT, pending.observe(originalUri, true, 60_000L, 200L))
        assertEquals(SpotifyLoadAction.PAUSE_TARGET, pending.observe(selectedUri, false, 200L, 300L))
        pending.pauseSent = true
        pending.pauseAcknowledged = true
        assertEquals("A pause cannot discharge a still-pending load command.", SpotifyLoadAction.WAIT,
            pending.observe(selectedUri, true, 300L, 400L))
        pending.loadAcknowledged = true
        assertEquals("Late playback after an acknowledged pause needs another targeted pause.", SpotifyLoadAction.PAUSE_TARGET,
            pending.observe(selectedUri, false, 300L, 400L))
        assertEquals(SpotifyLoadAction.RELEASE, pending.observe(selectedUri, true, 300L, 400L))
    }

    @Test fun officialLateLoadNeverPausesAnotherTrackOrOverridesUserSeekOrResume() {
        fun pending() = SpotifyPendingTrackLoad(selectedUri, originalUri, 60_000L, 100L).apply { cancelled = true }
        assertEquals(SpotifyLoadAction.RELEASE, pending().observe("spotify:track:" + "C".repeat(22), false, 0, 500))
        assertEquals(SpotifyLoadAction.RELEASE, pending().observe(originalUri, false, 60_100, 500))
        assertEquals(SpotifyLoadAction.RELEASE, pending().observe(originalUri, true, 75_000, 500))
        assertEquals(SpotifyLoadAction.RELEASE, pending().observe(selectedUri, true, 90_000, 500))
        assertEquals("The exact target may be buffering without a reported position.", SpotifyLoadAction.PAUSE_TARGET,
            pending().observe(selectedUri, false, -1, 500))
    }

    @Test fun officialOrdinaryLoadCannotReleaseBeforeItsPauseBarrierCompletes() {
        val pending = SpotifyPendingTrackLoad(selectedUri, originalUri, 60_000L, 100L)
        assertEquals(SpotifyLoadAction.WAIT, pending.observe(selectedUri, false, 100L, 200L))
        assertEquals(SpotifyLoadAction.WAIT, pending.observe(selectedUri, true, 100L, 200L))
        pending.loadAcknowledged = true
        assertEquals(SpotifyLoadAction.WAIT, pending.observe(selectedUri, true, 100L, 200L))
        pending.pauseAcknowledged = true
        assertEquals(SpotifyLoadAction.RELEASE, pending.observe(selectedUri, true, 100L, 200L))
    }

    @Test fun throwingDiagnosticObserverCannotChangePlaybackOrRestoration() = runBlocking {
        val clock = Clock()
        val session = Session(clock)
        val result = SpotifySongPassageController(session, clock) {
            throw IllegalStateException("Observer unavailable")
        }.play(request)
        assertEquals(SongPassageResult(SongPassageStatus.FINISHED, true), result)
        assertEquals(listOf("pause", "seek:5000", "play", "pause", "seek:60000", "play"), session.commands)
        assertEquals(10_000L, clock.time)
    }

    @Test fun spotifyAuthorizationErrorCategoryIsBoundedAndSafe() {
        assertEquals("AuthorizationError", spotifyAuthorizationErrorCategory(null))
        assertEquals("AuthorizationError", spotifyAuthorizationErrorCategory("  "))
        assertEquals("AuthorizationError_access_denied", spotifyAuthorizationErrorCategory("access_denied"))
        assertEquals("AuthorizationError_User_not_registered_in_the_Developer_Dashboard",
            spotifyAuthorizationErrorCategory("User not registered in the Developer Dashboard"))
        val long = spotifyAuthorizationErrorCategory("x".repeat(200))
        assertTrue(long.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,79}")))
    }
}
