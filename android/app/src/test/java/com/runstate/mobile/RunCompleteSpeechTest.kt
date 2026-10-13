package com.runstate.mobile

import com.runstate.mobile.reflection.RunReflectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunCompleteSpeechTest {
    private val runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val otherRunId = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"

    @Test
    fun newlyCompletedRunSpeaksItsExactReadyTextOnlyOnce() {
        val exact = "  First line.\nSecond line with a tab\tand emoji 🌤  "
        val ready = RunReflectionState.Ready(runId, exact)

        assertEquals(exact, readyCompletionSpeechText(runId, runId, null, ready))
        assertNull(readyCompletionSpeechText(runId, runId, runId, ready))
        assertNull(readyCompletionSpeechText(runId, null, null, ready))
    }

    @Test
    fun pendingFailedAndDifferentRunNeverSpeak() {
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Preparing(runId)))
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Failed(runId)))
        assertNull(readyCompletionSpeechText(runId, runId, null,
            RunReflectionState.Ready(otherRunId, "Other run")))
        assertNull(readyCompletionSpeechText(otherRunId, runId, null,
            RunReflectionState.Ready(runId, "Previous run")))
    }
}
