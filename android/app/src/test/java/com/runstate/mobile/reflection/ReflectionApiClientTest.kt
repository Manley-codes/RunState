package com.runstate.mobile.reflection

import com.google.gson.JsonObject
import com.runstate.mobile.data.local.*
import org.junit.Assert.*
import org.junit.Test

class ReflectionApiClientTest {
    private companion object {
        const val REQUEST_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val START = 1_780_000_000_000L
        const val PAUSE = START + 65_432L
        const val FINISH = START + 90_123L
    }

    @Test fun mapsRawSavedFactsAndUnknownUncapturedInputs() {
        val request = reflectionRequestFromSavedRun(
            REQUEST_ID,
            completedRun(),
            listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
        )

        assertEquals(REQUEST_ID, request.requestId)
        assertEquals(RUN_ID, request.runId)
        with(request.evidence) {
            assertEquals(START, officialStartEpochMillis)
            assertEquals(FINISH, finishEpochMillis)
            assertEquals("America/Chicago", startTimezoneId)
            assertEquals(65_432L, activeDurationMillis)
            assertEquals(180.25, metrics.distanceMeters!!, 0.0)
            assertEquals("FIXTURE", metrics.source)
            assertEquals("COMPLETE", metrics.coverage)
            assertEquals("MILES", metrics.displayUnit)
            assertEquals("UNKNOWN", preRunEnergy)
            assertEquals("UNKNOWN", musicContext.state)
            assertNull(musicContext.trackTitle)
        }
    }

    @Test fun incompleteHistoryAndAbsentMetricsStayUnknown() {
        val run = completedRun().copy(
            finalDistanceMeters = null,
            metricSource = null,
            telemetryCoverage = null,
            displayDistanceUnit = null,
            transitionHistoryComplete = null
        )
        val evidence = reflectionRequestFromSavedRun(REQUEST_ID, run, emptyList()).evidence

        assertNull(evidence.activeDurationMillis)
        assertEquals(
            ReflectionMetricsEvidence(null, null, null, null),
            evidence.metrics
        )
    }

    @Test fun responsePreservesExactTextAndRejectsUntrustworthyAnswers() {
        val exact = "  [FAKE] Exact no-selection candidate.  "
        val parsed = parseReflectionResponse(response(noSelection = exact), REQUEST_ID, RUN_ID)
        assertEquals(exact, parsed.noSelection)

        val invalid = listOf(
            response(requestId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
            response(runId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
            response(includeNoSelection = false),
            response(noSelection = "   "),
            response(includeExtra = true)
        )
        invalid.forEach {
            assertThrows(ReflectionBridgeException::class.java) {
                parseReflectionResponse(it, REQUEST_ID, RUN_ID)
            }
        }
    }

    private fun response(
        requestId: String = REQUEST_ID,
        runId: String = RUN_ID,
        noSelection: String = "[FAKE] None.",
        includeNoSelection: Boolean = true,
        includeExtra: Boolean = false
    ): String {
        val candidates = JsonObject().apply {
            addProperty("spent", "[FAKE] Spent.")
            addProperty("feeling_good", "[FAKE] Good.")
            addProperty("powered_up", "[FAKE] Up.")
            if (includeNoSelection) addProperty("no_selection", noSelection)
        }
        return JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("runId", runId)
            if (includeExtra) addProperty("extra", true)
            add("candidates", candidates)
        }.toString()
    }

    private fun completedRun() = RunEntity(
        runId = RUN_ID,
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = FINISH,
        finishEpochMillis = FINISH,
        finalDistanceMeters = 180.25,
        metricSource = MetricSource.FIXTURE,
        telemetryCoverage = TelemetryCoverage.COMPLETE,
        displayDistanceUnit = DistanceUnit.MILES,
        transitionHistoryComplete = true
    )
}
