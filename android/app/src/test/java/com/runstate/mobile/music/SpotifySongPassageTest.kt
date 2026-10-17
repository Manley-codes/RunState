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
        var bufferPlayCalls = emptySet<Int>()
        private var playCalls = 0
        private var pendingPlay = false
        val commands = mutableListOf<String>()
        override fun snapshot(): SongPassageSnapshot {
            if (denied) throw SecurityException("No session access")
            val advance = if (current.playback == PassagePlayback.PLAYING) clock.time - changedAt else 0
            return current.copy(positionMillis = current.positionMillis + advance)
        }
        fun replace(snapshot: SongPassageSnapshot) { current = snapshot; changedAt = clock.time }
        override fun pause() {
            commands += "pause"
            pendingPlay = false
            replace(snapshot().copy(playback = PassagePlayback.PAUSED, canPlay = true, canPause = false))
        }
        override fun seekTo(positionMillis: Long) {
            commands += "seek:$positionMillis"
            replace(snapshot().copy(positionMillis = positionMillis))
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
                replace(before.copy(playback = PassagePlayback.PLAYING, canPlay = false, canPause = true,
                    positionMillis = if (playAtWrongPosition) 120_000 else before.positionMillis))
            }
        }
        fun completePendingPlay() {
            if (pendingPlay) {
                pendingPlay = false
                replace(snapshot().copy(playback = PassagePlayback.PLAYING, canPlay = false, canPause = true))
            }
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

    @Test fun pendingBufferingDoesNotOverrideChangedRecordingSessionOrManualSeek() = runBlocking {
        for (change in listOf<(SongPassageSnapshot) -> SongPassageSnapshot>(
            { it.copy(mediaId = "next-track", title = "Next title") },
            { it.copy(sessionKey = "replacement-session") },
            { it.copy(positionMillis = 90_000) }
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
        // A position change outside our trajectory may be a user action, so do not fight it.
        assertEquals(listOf("pause", "seek:5000", "play"), session.commands)
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
}
