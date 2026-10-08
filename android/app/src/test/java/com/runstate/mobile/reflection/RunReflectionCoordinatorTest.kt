package com.runstate.mobile.reflection

import com.runstate.mobile.data.local.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class RunReflectionCoordinatorTest {
    private companion object {
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val SECOND_RUN_ID = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"
        const val FIRST_REQUEST_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val SECOND_REQUEST_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"
        const val START = 1_780_000_000_000L
        const val PAUSE = START + 60_000L
        const val FINISH = START + 90_000L
        const val FAKE_TEXT = "[FAKE] No-selection candidate."
    }

    private class ReadingRunDao : FakeRunDao() {
        var runReads = 0
        override suspend fun findById(runId: String): RunEntity? {
            runReads++
            return super.findById(runId)
        }
    }

    @Test fun repeatSavedObserverCallsOnceAndWaitsForQuietWindow() {
        val dao = completedDao()
        var providerCalls = 0
        var requestedDelay = -1L
        val release = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            gateway = ReflectionGateway { providerCalls++; candidates() },
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 6_000L,
            elapsedRealtimeMillis = { 100L },
            quietDelay = { requestedDelay = it; release.await() }
        )

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(1, providerCalls)
        assertEquals(1, dao.runReads)
        assertEquals(6_000L, requestedDelay)
        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)

        release.complete(Unit)
        assertEquals(RunReflectionState.Ready(RUN_ID, FAKE_TEXT), coordinator.state.value)
        assertEquals(1, providerCalls)
    }

    @Test fun retryRereadsRoomUsesNewRequestIdAndNeverSavesAgain() {
        val dao = completedDao()
        val ids = ArrayDeque(listOf(
            UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)
        ))
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            gateway = ReflectionGateway { request ->
                requests += request
                if (requests.size == 1) error("forced failure")
                candidates()
            },
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L
        )

        coordinator.onSavedVisible(RUN_ID)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        assertEquals(1, runBlocking { dao.countRuns() })

        coordinator.retry(RUN_ID)

        assertEquals(2, requests.size)
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(2, dao.runReads)
        assertEquals(1, runBlocking { dao.countRuns() })
        assertEquals(RunReflectionState.Ready(RUN_ID, FAKE_TEXT), coordinator.state.value)
    }

    @Test fun waitsForDebugBridgePermissionWithoutReadingRoomOrCallingProvider() {
        val dao = completedDao()
        var providerCalls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            gateway = ReflectionGateway { providerCalls++; candidates() },
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 0L
        )

        coordinator.onSavedVisible(RUN_ID, bridgeReady = false)

        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)
        assertEquals(0, dao.runReads)
        assertEquals(0, providerCalls)

        coordinator.onBridgeUnavailable(RUN_ID)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)

        coordinator.startWhenBridgeReady(RUN_ID)
        assertEquals(1, dao.runReads)
        assertEquals(1, providerCalls)
        assertEquals(RunReflectionState.Ready(RUN_ID, FAKE_TEXT), coordinator.state.value)
        assertEquals(1, runBlocking { dao.countRuns() })
    }

    @Test fun lateSuccessForFirstRunCannotReplaceSecondRun() {
        val first = CompletableDeferred<ReflectionCandidates>()
        val second = CompletableDeferred<ReflectionCandidates>()
        val coordinator = outOfOrderCoordinator(first, second)

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(SECOND_RUN_ID)

        second.complete(candidates("[FAKE] Second run."))
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )

        first.complete(candidates("[FAKE] Late first run."))
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )
    }

    @Test fun lateFailureForFirstRunCannotReplaceSecondRun() {
        val first = CompletableDeferred<ReflectionCandidates>()
        val second = CompletableDeferred<ReflectionCandidates>()
        val coordinator = outOfOrderCoordinator(first, second)

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(SECOND_RUN_ID)

        second.complete(candidates("[FAKE] Second run."))
        first.completeExceptionally(IllegalStateException("late first-run failure"))

        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )
    }

    private fun outOfOrderCoordinator(
        first: CompletableDeferred<ReflectionCandidates>,
        second: CompletableDeferred<ReflectionCandidates>
    ): RunReflectionCoordinator {
        val ids = ArrayDeque(listOf(
            UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)
        ))
        return RunReflectionCoordinator(
            runDao = completedDao(RUN_ID, SECOND_RUN_ID),
            gateway = ReflectionGateway { request ->
                if (request.runId == RUN_ID) first.await() else second.await()
            },
            applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L
        )
    }

    private fun completedDao(vararg runIds: String) = ReadingRunDao().apply {
        (if (runIds.isEmpty()) arrayOf(RUN_ID) else runIds).forEach { runId ->
            inserted += RunEntity(
                runId = runId,
                state = StoredRunState.COMPLETED,
                officialStartEpochMillis = START,
                startTimezoneId = "America/Chicago",
                lastCheckpointEpochMillis = FINISH,
                finishEpochMillis = FINISH,
                finalDistanceMeters = 180.0,
                metricSource = MetricSource.FIXTURE,
                telemetryCoverage = TelemetryCoverage.COMPLETE,
                displayDistanceUnit = DistanceUnit.MILES,
                transitionHistoryComplete = true
            )
            transitions += RunTransitionEntity(runId, 1, RunTransitionType.PAUSE, PAUSE)
        }
    }

    private fun candidates(noSelection: String = FAKE_TEXT) = ReflectionCandidates(
        spent = "[FAKE] Spent.",
        feelingGood = "[FAKE] Good.",
        poweredUp = "[FAKE] Up.",
        noSelection = noSelection
    )
}
