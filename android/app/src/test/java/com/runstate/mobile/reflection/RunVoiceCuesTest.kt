package com.runstate.mobile.reflection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

class RunVoiceCuesTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test fun preparedLinesMatchTheSpokenCountdownStepsAndTheEnergyQuestion() {
        assertEquals(listOf("Starting in ten.", "Three.", "Two.", "One.", "Let’s go, runner."), COUNTDOWN_VOICE_LINES)
        assertEquals(COUNTDOWN_VOICE_LINES + "How’s your energy now?", RUN_VOICE_PREPARED_LINES)
        assertEquals(ENERGY_QUESTION_TEXT, RUN_VOICE_PREPARED_LINES.last())
        assertTrue(RUN_VOICE_PREPARED_LINES.all { it.length <= SpeechVoice.RUN.maxTextLength })
    }

    @Test fun prefetchesEachLineOnceAndRetriesOnlyWhatIsMissing() {
        val requested = mutableListOf<String>()
        var failOn: String? = "Two."
        val cache = RunVoiceCueCache(scope, ReflectionSpeechGateway { text ->
            requested += text
            if (text == failOn) null else text.toByteArray()
        })

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

    @Test fun emptyOversizedOrFailedAudioIsNeverKept() {
        for (result in listOf<() -> ByteArray?>({ ByteArray(0) }, { ByteArray(MAX_REFLECTION_AUDIO_BYTES + 1) },
            { throw IllegalStateException("private transport detail") })) {
            val cache = RunVoiceCueCache(scope, ReflectionSpeechGateway { result() })
            cache.prefetch()
            assertNull(cache.audioFor(COUNTDOWN_INTRO_TEXT))
            assertFalse(cache.hasAll(COUNTDOWN_VOICE_LINES))
        }
    }
}
