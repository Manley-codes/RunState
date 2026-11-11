package com.runstate.mobile.reflection

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RunVoiceCuesTest {
    @get:Rule val folder = TemporaryFolder()
    private fun files() = FixedRunVoiceFiles(folder.root.resolve("fixed-run-voice"))
    private suspend fun cache(key: String = "hosted-test", fetch: suspend (String) -> ByteArray?): RunVoiceCueCache =
        RunVoiceCueCache(files()).apply { select(key, ReflectionSpeechGateway(fetch), clearPrevious = false) }

    @Test fun preparedLinesMatchTheSpokenCountdownStepsAndTheEnergyQuestion() {
        assertEquals(listOf("Starting in ten.", "Three.", "Two.", "One.", "Let’s go, runner."), COUNTDOWN_VOICE_LINES)
        assertEquals(COUNTDOWN_VOICE_LINES + "How’s your energy now?", RUN_VOICE_PREPARED_LINES)
        assertEquals(ENERGY_QUESTION_TEXT, RUN_VOICE_PREPARED_LINES.last())
        assertTrue(RUN_VOICE_PREPARED_LINES.all { it.length <= SpeechVoice.RUN.maxTextLength })
    }

    @Test fun prefetchesEachLineOnceAndRetriesOnlyWhatIsMissing() = runBlocking {
        val requested = mutableListOf<String>()
        var failOn: String? = "Two."
        val cache = cache { text ->
            requested += text
            if (text == failOn) null else text.toByteArray()
        }

        cache.prefetch()
        assertEquals(listOf(COUNTDOWN_INTRO_TEXT, "Three.", "Two."), requested)
        assertFalse(cache.hasAll(COUNTDOWN_VOICE_LINES))
        assertArrayEquals("Three.".toByteArray(), cache.audioFor("Three."))
        assertNull(cache.audioFor("Two."))

        failOn = null
        requested.clear()
        cache.prefetch()
        assertEquals(listOf("Two.", "One.", COUNTDOWN_FINISH_TEXT, ENERGY_QUESTION_TEXT), requested)
        assertTrue(cache.hasAll(RUN_VOICE_PREPARED_LINES))

        requested.clear()
        cache.prefetch()
        assertTrue(requested.isEmpty())
    }

    @Test fun emptyOversizedOrFailedAudioIsNeverKept() = runBlocking {
        for (result in listOf<() -> ByteArray?>({ ByteArray(0) }, { ByteArray(MAX_REFLECTION_AUDIO_BYTES + 1) },
            { throw IllegalStateException("private transport detail") })) {
            val cache = cache { result() }
            cache.prefetch()
            assertNull(cache.audioFor(COUNTDOWN_INTRO_TEXT))
            assertFalse(cache.hasAll(COUNTDOWN_VOICE_LINES))
        }
    }

    @Test fun freshProcessRestoresExactlyTheSixRecordingsWithoutNetwork() = runBlocking {
        val first = cache { it.toByteArray() }
        first.prefetch()
        val reopened = cache { error("A cache hit must not request a recording") }
        reopened.prefetch()
        for (line in RUN_VOICE_PREPARED_LINES) assertArrayEquals(line.toByteArray(), reopened.audioFor(line))
        assertNull(reopened.audioFor("Mile two in 18 minutes."))
        files().write("hosted-test", "A personal reflection", byteArrayOf(1)) { true }
        assertNull(files().read("hosted-test", "A personal reflection"))
        assertEquals(6, folder.root.resolve("fixed-run-voice").listFiles()!!.size)
    }

    @Test fun damagedRecordingIsRefetchedAloneAndOtherRunnersCannotReadIt() = runBlocking {
        cache { it.toByteArray() }.prefetch()
        val file = folder.root.resolve("fixed-run-voice/cue-1")
        file.writeBytes(file.readBytes().apply { this[lastIndex] = (last() + 1).toByte() })
        val requested = mutableListOf<String>()
        val repaired = cache { requested += it; it.toByteArray() }
        repaired.prefetch()
        assertEquals(listOf(COUNTDOWN_VOICE_LINES[1]), requested)
        assertNull(files().read("different-access-code", COUNTDOWN_VOICE_LINES[1]))

        repaired.select(null, null, clearPrevious = true)
        assertFalse(repaired.hasAll(COUNTDOWN_VOICE_LINES))
        assertTrue(folder.root.resolve("fixed-run-voice").listFiles()!!.isEmpty())
    }

    @Test fun freeRunRestoresExistingCuesWithoutAskingForMissingOnes() = runBlocking {
        cache { it.toByteArray() }.prefetch(listOf(COUNTDOWN_INTRO_TEXT))
        val calls = mutableListOf<String>()
        val free = cache { calls += it; null }
        free.prefetch(fetchMissing = false)
        assertTrue(calls.isEmpty())
        assertArrayEquals(COUNTDOWN_INTRO_TEXT.toByteArray(), free.audioFor(COUNTDOWN_INTRO_TEXT))
        assertNull(free.audioFor(ENERGY_QUESTION_TEXT))
    }

    @Test fun unavailableDiskStillAllowsMemoryPlaybackAndConcurrentPrefetchesShareTheAudio() = runBlocking {
        folder.root.resolve("fixed-run-voice").writeText("directory unavailable")
        val requests = mutableListOf<String>()
        val firstLineStarted = CompletableDeferred<Unit>()
        val reply = CompletableDeferred<Unit>()
        val cues = cache {
            requests += it
            if (it == COUNTDOWN_INTRO_TEXT) { firstLineStarted.complete(Unit); reply.await() }
            it.toByteArray()
        }
        val first = launch { cues.prefetch() }
        firstLineStarted.await()
        val second = launch { cues.prefetch() }
        reply.complete(Unit)
        first.join()
        second.join()
        assertEquals(RUN_VOICE_PREPARED_LINES, requests)
        assertTrue(cues.hasAll(RUN_VOICE_PREPARED_LINES))
    }

    @Test fun leavingStartCancelsFurtherFetchAndADisconnectedResponseCannotRestoreCues() = runBlocking {
        val requested = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<ByteArray>()
        val original = cache { requested += it; started.complete(Unit); response.await() }
        val waiting = launch { original.prefetch() }
        started.await()
        original.select(null, null, clearPrevious = true)
        response.complete(byteArrayOf(42))
        waiting.join()
        assertEquals(listOf(COUNTDOWN_INTRO_TEXT), requested)
        assertNull(original.audioFor(COUNTDOWN_INTRO_TEXT))
        assertNull(files().read("hosted-test", COUNTDOWN_INTRO_TEXT))

        val pending = CompletableDeferred<Unit>()
        val stopOnLeave = cache { requested += it; pending.complete(Unit); CompletableDeferred<ByteArray>().await() }
        val startScreen = launch { stopOnLeave.prefetch() }
        pending.await()
        startScreen.cancelAndJoin()
        assertEquals(listOf(COUNTDOWN_INTRO_TEXT, COUNTDOWN_INTRO_TEXT), requested)
        assertNull(files().read("hosted-test", COUNTDOWN_INTRO_TEXT))
    }

    private class FakeVoice : RehearsableVoice {
        val rehearsed = mutableListOf<String>()
        var closed = false
        override fun warmUp(text: String) { rehearsed += text }
        override fun close() { closed = true }
    }

    @Test fun preparedVoiceRehearsesTheOpeningOnceAndBelongsToTheCountdownOnceTaken() {
        val created = mutableListOf<FakeVoice>()
        val prepared = PreparedCountdownVoice { FakeVoice().also { created += it } }

        prepared.prepare()
        prepared.prepare()
        assertEquals(1, created.size)
        assertEquals(listOf(COUNTDOWN_INTRO_TEXT), created.single().rehearsed)

        assertSame(created.single(), prepared.take())
        assertNull(prepared.take())
        prepared.release()
        assertFalse(created.single().closed) // The countdown closes the voice it took.

        prepared.prepare()
        assertEquals(2, created.size)
        prepared.release()
        assertTrue(created.last().closed)
        assertNull(prepared.take())
    }

    @Test fun aMissingOrFailingVoiceNeverBlocksTheCountdown() {
        val unavailable = PreparedCountdownVoice<FakeVoice> { throw IllegalStateException("no speech engine") }
        unavailable.prepare()
        assertNull(unavailable.take())
        unavailable.release()

        var closed = false
        val rehearsalFails = PreparedCountdownVoice<RehearsableVoice> {
            object : RehearsableVoice {
                override fun warmUp(text: String) { throw IllegalStateException("engine busy") }
                override fun close() { closed = true }
            }
        }
        rehearsalFails.prepare()
        assertNotNull(rehearsalFails.take())
        assertFalse(closed)
    }
}
