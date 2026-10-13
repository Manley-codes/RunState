package com.runstate.mobile.reflection

import com.google.gson.GsonBuilder
import com.runstate.mobile.data.local.*
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.After
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
        const val NO_SELECTION_TEXT = "[FAKE] No-selection candidate."
        const val SPENT_TEXT = "[FAKE] Spent."
        const val POWERED_UP_TEXT = "[FAKE] Up."
    }

    private val scopes = mutableListOf<CoroutineScope>()
    private val gson = GsonBuilder().serializeNulls().create()

    @After fun cancelWork() {
        scopes.forEach { it.cancel() }
    }

    private fun scope() =
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { scopes += it }

    private class ReadingRunDao : FakeRunDao() {
        var runReads = 0
        override suspend fun findById(runId: String): RunEntity? {
            runReads++
            return super.findById(runId)
        }
    }

    /**
     * This fake reproduces the state changes at the coordinator/Room seam. The Room
     * instrumented tests separately prove that the same rules are transactional.
     */
    private class MemoryReflectionStore(
        initial: List<RunReflectionEntity> = emptyList()
    ) : ReflectionStore {
        val rows = initial.associateByTo(mutableMapOf()) { it.runId }
        var creations = 0

        override suspend fun find(runId: String) = rows[runId]
        override suspend fun all() = rows.values.toList()

        override suspend fun ensure(record: RunReflectionEntity): RunReflectionEntity =
            rows[record.runId] ?: record.also {
                rows[record.runId] = it
                creations++
            }

        override suspend fun energy(
            runId: String, branch: ReflectionBranch, now: Long
        ): RunReflectionEntity {
            require(branch != ReflectionBranch.NO_SELECTION)
            val old = rows.getValue(runId)
            return old.copy(
                selectedBranch = old.selectedBranch
                    ?: if (now < old.selectionDeadlineEpochMillis) branch
                    else ReflectionBranch.NO_SELECTION,
                postRunEnergy = branch
            ).also { rows[runId] = it }
        }

        override suspend fun defaultSelection(runId: String, now: Long): RunReflectionEntity {
            val old = rows.getValue(runId)
            if (old.selectedBranch != null || now < old.selectionDeadlineEpochMillis) return old
            return old.copy(selectedBranch = ReflectionBranch.NO_SELECTION)
                .also { rows[runId] = it }
        }

        override suspend fun candidates(
            runId: String, requestId: String, json: String, isTestData: Boolean
        ): Boolean {
            val old = rows[runId] ?: return false
            if (old.status != ReflectionStatus.PENDING || old.requestId != requestId ||
                old.candidatesJson != null) return false
            rows[runId] = old.copy(candidatesJson = json, isTestData = isTestData)
            return true
        }

        override suspend fun ready(
            runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long
        ): Boolean {
            val old = rows[runId] ?: return false
            if (old.status != ReflectionStatus.PENDING || old.requestId != requestId ||
                old.selectedBranch != branch || old.candidatesJson == null) return false
            rows[runId] = old.copy(
                status = ReflectionStatus.READY,
                text = text,
                finalizedAtEpochMillis = now,
                candidatesJson = null
            )
            return true
        }

        override suspend fun fail(runId: String, requestId: String): Boolean {
            val old = rows[runId] ?: return false
            if (old.status != ReflectionStatus.PENDING || old.requestId != requestId) return false
            rows[runId] = old.copy(status = ReflectionStatus.FAILED, candidatesJson = null)
            return true
        }

        override suspend fun retry(runId: String, requestId: String): RunReflectionEntity? {
            val old = rows[runId] ?: return null
            if (old.status != ReflectionStatus.FAILED) return null
            require(old.requestId != requestId)
            return old.copy(
                status = ReflectionStatus.PENDING,
                requestId = requestId,
                candidatesJson = null
            ).also { rows[runId] = it }
        }
    }

    @Test fun repeatedSavedVisibilityMakesOneRequestAndDefaultsAfterQuietWindow() {
        val dao = completedDao()
        val store = MemoryReflectionStore()
        var calls = 0
        var requestedDelay = -1L
        var now = 100L
        val release = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            store = store,
            gateway = ReflectionGateway { calls++; candidates() },
            applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 6_000L,
            nowMillis = { now },
            quietDelay = { requestedDelay = it; release.await() }
        )

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(1, calls)
        assertEquals(1, dao.runReads)
        assertEquals(1, store.creations)
        assertEquals(6_000L, requestedDelay)
        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)

        now = 6_100L
        release.complete(Unit)
        assertEquals(
            RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT),
            coordinator.state.value
        )
        assertEquals(ReflectionBranch.NO_SELECTION, store.rows.getValue(RUN_ID).selectedBranch)
        assertNull(store.rows.getValue(RUN_ID).candidatesJson)
    }

    @Test fun openEffortSheetHoldsNoSelectionUntilTheRunnerLeavesIt() {
        val store = MemoryReflectionStore()
        var now = 100L
        val releaseTimer = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(),
            store = store,
            gateway = ReflectionGateway { candidates() },
            applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 6_000L,
            nowMillis = { now },
            quietDelay = { releaseTimer.await() }
        )

        coordinator.onSavedVisible(RUN_ID)
        coordinator.setEffortSheetOpen(RUN_ID, true)
        now = 6_100L
        releaseTimer.complete(Unit)
        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)
        assertNull(store.rows.getValue(RUN_ID).selectedBranch)

        coordinator.setEffortSheetOpen(RUN_ID, false)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
        assertEquals(ReflectionBranch.NO_SELECTION, store.rows.getValue(RUN_ID).selectedBranch)
    }

    @Test fun onTimeEnergyChoosesOneExactCandidateAndLateAnswerOnlyUpdatesFeedback() {
        val store = MemoryReflectionStore()
        var now = 100L
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(),
            store = store,
            gateway = ReflectionGateway { candidates() },
            applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 6_000L,
            nowMillis = { now },
            quietDelay = { CompletableDeferred<Unit>().await() }
        )

        coordinator.onSavedVisible(RUN_ID)
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.SPENT)

        assertEquals(
            RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.SPENT),
            coordinator.state.value
        )
        val firstReady = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionBranch.SPENT, firstReady.selectedBranch)
        assertNull(firstReady.candidatesJson)

        now = 8_000L
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.POWERED_UP)

        assertEquals(
            RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.POWERED_UP),
            coordinator.state.value
        )
        val saved = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionBranch.SPENT, saved.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, saved.postRunEnergy)
        assertEquals(SPENT_TEXT, saved.text)
        assertEquals(firstReady.finalizedAtEpochMillis, saved.finalizedAtEpochMillis)
    }

    @Test fun firstEnergyAfterQuietDeadlineKeepsNoSelectionReply() {
        val store = MemoryReflectionStore()
        var now = 100L
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(),
            store = store,
            gateway = ReflectionGateway { candidates() },
            applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 6_000L,
            nowMillis = { now },
            quietDelay = { CompletableDeferred<Unit>().await() }
        )

        coordinator.onSavedVisible(RUN_ID)
        now = 6_100L
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.FEELING_GOOD)

        val saved = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionBranch.NO_SELECTION, saved.selectedBranch)
        assertEquals(ReflectionBranch.FEELING_GOOD, saved.postRunEnergy)
        assertEquals(NO_SELECTION_TEXT, saved.text)
        assertEquals(
            RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT, ReflectionBranch.FEELING_GOOD),
            coordinator.state.value
        )
    }

    @Test fun retryUsesNewRequestIdAndOriginalSavedEvidenceWithoutAddingRun() {
        val dao = completedDao()
        val store = MemoryReflectionStore()
        val ids = ArrayDeque(listOf(
            UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)
        ))
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            store = store,
            gateway = ReflectionGateway { request ->
                requests += request
                if (requests.size == 1) error("forced failure")
                candidates()
            },
            applicationScope = scope(),
            requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L,
            nowMillis = { 100L }
        )

        coordinator.onSavedVisible(RUN_ID)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        val originalEvidence = store.rows.getValue(RUN_ID).evidenceJson

        coordinator.retry(RUN_ID)

        assertEquals(2, requests.size)
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(requests[0].evidence, requests[1].evidence)
        assertEquals(originalEvidence, store.rows.getValue(RUN_ID).evidenceJson)
        assertEquals(1, dao.runReads)
        assertEquals(1, runBlocking { dao.countRuns() })
        assertEquals(1, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun bridgeCanWaitForPermissionThenRetryTheSameSavedRun() {
        val dao = completedDao()
        val store = MemoryReflectionStore()
        val ids = ArrayDeque(listOf(
            UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)
        ))
        var calls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            store = store,
            gateway = ReflectionGateway { calls++; candidates() },
            applicationScope = scope(),
            requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L,
            nowMillis = { 100L }
        )

        coordinator.onSavedVisible(RUN_ID, bridgeReady = false)
        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)
        assertEquals(1, dao.runReads)
        assertEquals(0, calls)

        coordinator.onBridgeUnavailable(RUN_ID)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        coordinator.startWhenBridgeReady(RUN_ID)

        assertEquals(1, calls)
        assertEquals(1, dao.runReads)
        assertEquals(1, runBlocking { dao.countRuns() })
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun processRestartFinalizesCachedCandidateWithoutAnotherNetworkCall() {
        val dao = completedDao()
        val store = MemoryReflectionStore(listOf(
            RunReflectionEntity(
                runId = RUN_ID,
                requestId = FIRST_REQUEST_ID,
                evidenceJson = "{}",
                selectionDeadlineEpochMillis = 6_100L,
                selectedBranch = ReflectionBranch.SPENT,
                postRunEnergy = ReflectionBranch.SPENT,
                candidatesJson = gson.toJson(candidates())
            )
        ))
        var calls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            store = store,
            gateway = ReflectionGateway { calls++; candidates() },
            applicationScope = scope(),
            nowMillis = { 100L }
        )

        runBlocking { coordinator.initialize() }
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(0, calls)
        assertEquals(0, dao.runReads)
        assertEquals(0, store.creations)
        assertEquals(SPENT_TEXT, store.rows.getValue(RUN_ID).text)
        assertNull(store.rows.getValue(RUN_ID).candidatesJson)
        assertEquals(
            RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.SPENT),
            coordinator.state.value
        )
    }

    @Test fun processRestartLeavesUnresolvedRequestFailedUntilExplicitRetry() {
        val dao = completedDao()
        val store = MemoryReflectionStore(listOf(
            RunReflectionEntity(
                runId = RUN_ID,
                requestId = FIRST_REQUEST_ID,
                evidenceJson = "{}",
                selectionDeadlineEpochMillis = 6_100L
            )
        ))
        var calls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = dao,
            store = store,
            gateway = ReflectionGateway { calls++; candidates() },
            applicationScope = scope(),
            nowMillis = { 100L }
        )

        runBlocking { coordinator.initialize() }
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        assertEquals(0, calls)
        assertEquals(0, dao.runReads)
    }

    @Test fun lateSuccessForFirstRunCannotReplaceSecondVisibleRun() {
        val first = CompletableDeferred<ReflectionCandidates>()
        val second = CompletableDeferred<ReflectionCandidates>()
        val store = MemoryReflectionStore()
        val coordinator = outOfOrderCoordinator(first, second, store)

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(SECOND_RUN_ID)
        second.complete(candidates("[FAKE] Second run."))
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )

        first.complete(candidates("[FAKE] Late first run."))
        assertEquals("[FAKE] Late first run.", store.rows.getValue(RUN_ID).text)
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )
    }

    @Test fun lateFailureForFirstRunCannotReplaceSecondVisibleRun() {
        val first = CompletableDeferred<ReflectionCandidates>()
        val second = CompletableDeferred<ReflectionCandidates>()
        val store = MemoryReflectionStore()
        val coordinator = outOfOrderCoordinator(first, second, store)

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(SECOND_RUN_ID)
        second.complete(candidates("[FAKE] Second run."))
        first.completeExceptionally(IllegalStateException("late first-run failure"))

        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )
    }

    private fun outOfOrderCoordinator(
        first: CompletableDeferred<ReflectionCandidates>,
        second: CompletableDeferred<ReflectionCandidates>,
        store: MemoryReflectionStore
    ): RunReflectionCoordinator {
        val ids = ArrayDeque(listOf(
            UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)
        ))
        return RunReflectionCoordinator(
            runDao = completedDao(RUN_ID, SECOND_RUN_ID),
            store = store,
            gateway = ReflectionGateway { request ->
                if (request.runId == RUN_ID) first.await() else second.await()
            },
            applicationScope = scope(),
            requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L,
            nowMillis = { 100L }
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

    private fun candidates(noSelection: String = NO_SELECTION_TEXT) = ReflectionCandidates(
        spent = SPENT_TEXT,
        feelingGood = "[FAKE] Good.",
        poweredUp = POWERED_UP_TEXT,
        noSelection = noSelection
    )
}
