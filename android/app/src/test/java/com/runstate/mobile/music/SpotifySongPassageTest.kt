package com.runstate.mobile.music

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        private var playingReportedAt = 0L
        var bufferPlayCalls = emptySet<Int>()
        private var playCalls = 0
        private var pendingPlay = false
        val commands = mutableListOf<String>()
        override fun snapshot(): SongPassageSnapshot {
            if (denied) throw SecurityException("No session access")
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
            replace(snapshot().copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false))
        }
        override fun seekTo(positionMillis: Long) {
            commands += "seek:$positionMillis"
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
            }
        }
        fun bufferCurrentPlay() {
            pendingPlay = true
            replace(snapshot().copy(playback = PassagePlayback.OTHER, canPause = false, canPlay = false))
        }
    }

    private val request = SongPassageRequest("Test title", "Test artist", 5_000)

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
}
