package com.runstate.mobile.reflection

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.runstate.mobile.data.local.*
import com.runstate.mobile.weather.RunWeatherSnapshot
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
        var musicReads = 0
        var observedMusic: List<RunMusicObservationEntity> = emptyList()
        var musicReadFailure: Exception? = null
        var weatherReads = 0
        var savedWeather: RunWeatherSnapshot? = null
        var weatherReadFailure: Exception? = null
        var preparedSong: SongPreparationClaimEntity? = null
        var failFinalizationOnce = false
        override suspend fun songPreparation(runId: String) = preparedSong

        override suspend fun find(runId: String) = rows[runId]
        override suspend fun all() = rows.values.toList()
        override suspend fun musicObservations(runId: String): List<RunMusicObservationEntity> {
            musicReads++
            musicReadFailure?.let { throw it }
            return observedMusic
        }
        override suspend fun weatherSnapshot(runId: String): RunWeatherSnapshot? {
            weatherReads++
            weatherReadFailure?.let { throw it }
            return savedWeather
        }

        override suspend fun ensure(record: RunReflectionEntity): RunReflectionEntity =
            rows[record.runId] ?: record.also {
                rows[record.runId] = it
                creations++
            }

        override suspend fun energy(
            runId: String, branch: ReflectionBranch, now: Long, selectionHeld: Boolean
        ): RunReflectionEntity {
            val old = rows.getValue(runId)
            return old.copy(
                selectedBranch = old.selectedBranch
                    ?: if (!old.selectionWindowStarted || selectionHeld ||
                        now < old.selectionDeadlineEpochMillis) branch
                    else ReflectionBranch.NO_SELECTION,
                postRunEnergy = branch
            ).also { rows[runId] = it }
        }

        override suspend fun defaultSelection(runId: String, now: Long): RunReflectionEntity {
            val old = rows.getValue(runId)
            if (!old.selectionWindowStarted || old.selectedBranch != null ||
                now < old.selectionDeadlineEpochMillis) return old
            return old.copy(selectedBranch = ReflectionBranch.NO_SELECTION)
                .also { rows[runId] = it }
        }

        override suspend fun beginSelectionWindow(runId: String, deadline: Long): RunReflectionEntity {
            val old = rows.getValue(runId)
            if (old.selectionWindowStarted) return old
            return old.copy(
                selectionWindowStarted = true,
                selectionDeadlineEpochMillis = deadline
            ).also { rows[runId] = it }
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

        override suspend fun selectedCandidates(runId: String, requestId: String, json: String,
            isTestData: Boolean, selectedEvidenceJson: String?): Boolean {
            if (!candidates(runId, requestId, json, isTestData)) return false
            selectedEvidenceJson?.let { rows[runId] = rows.getValue(runId).copy(evidenceJson = it) }
            return true
        }

        override suspend fun ready(
            runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long
        ): Boolean {
            if (failFinalizationOnce) {
                failFinalizationOnce = false
                throw IllegalStateException("Synthetic local finalization failure")
            }
            val old = rows[runId] ?: return false
            if (old.status != ReflectionStatus.PENDING || !old.selectionWindowStarted || old.requestId != requestId ||
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

    @Test fun receiptHoldsEarlyEnergyAndReadyCandidatesUntilItFinishes() {
        val store = MemoryReflectionStore()
        var now = 100L
        var timers = 0
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { now },
            quietDelay = { timers++; CompletableDeferred<Unit>().await() }
        )

        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        assertNull(coordinator.voicePreparation.value)
        now = 9_000L // The unused placeholder deadline has passed while receipt plays.
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.SPENT)
        val waiting = store.rows.getValue(RUN_ID)
        assertFalse(waiting.selectionWindowStarted)
        assertEquals(ReflectionBranch.SPENT, waiting.selectedBranch)
        assertNotNull(waiting.candidatesJson)
        assertEquals(RunReflectionState.Preparing(RUN_ID, ReflectionBranch.SPENT), coordinator.state.value)
        assertEquals(0, timers)
        assertEquals(ReflectionVoicePreparation(RUN_ID, FIRST_REQUEST_ID, SPENT_TEXT),
            coordinator.voicePreparation.value)
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.POWERED_UP)
        assertEquals("Later feedback must not change the locked voice branch", SPENT_TEXT,
            coordinator.voicePreparation.value?.text)

        coordinator.finishReceipt(RUN_ID)
        val ready = store.rows.getValue(RUN_ID)
        assertTrue(ready.selectionWindowStarted)
        assertEquals(15_000L, ready.selectionDeadlineEpochMillis)
        assertEquals(RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.POWERED_UP), coordinator.state.value)
        now = 12_000L
        coordinator.finishReceipt(RUN_ID)
        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        assertEquals(ready, store.rows.getValue(RUN_ID))
    }

    @Test fun allEnergyBranchesUseTheAlreadyStartedDelayedFamilyWithoutAnotherRequest() {
        val family = candidates()
        val chosenTexts = mapOf(
            ReflectionBranch.SPENT to family.spent,
            ReflectionBranch.FEELING_GOOD to family.feelingGood,
            ReflectionBranch.POWERED_UP to family.poweredUp
        )
        chosenTexts.forEach { (branch, exactText) ->
            val store = MemoryReflectionStore()
            val response = CompletableDeferred<ReflectionCandidates>()
            var calls = 0
            var now = 100L
            val coordinator = RunReflectionCoordinator(
                runDao = completedDao(), store = store,
                gateway = ReflectionGateway { calls++; response.await() },
                applicationScope = scope(),
                requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
                nowMillis = { now },
                quietDelay = { CompletableDeferred<Unit>().await() }
            )

            coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
            assertEquals("$branch starts generation before Energy input", 1, calls)
            assertNull(store.rows.getValue(RUN_ID).postRunEnergy)
            assertNull(store.rows.getValue(RUN_ID).candidatesJson)

            now = 9_000L // The receipt is still playing while generation is delayed.
            coordinator.selectEnergy(RUN_ID, branch)
            assertEquals("$branch must not start another request", 1, calls)
            assertEquals(branch, store.rows.getValue(RUN_ID).selectedBranch)
            assertEquals(RunReflectionState.Preparing(RUN_ID, branch), coordinator.state.value)
            assertNull(coordinator.voicePreparation.value)

            response.complete(family)
            val waitingForReceipt = store.rows.getValue(RUN_ID)
            assertNotNull(waitingForReceipt.candidatesJson)
            assertNull(waitingForReceipt.text)
            assertEquals(ReflectionStatus.PENDING, waitingForReceipt.status)
            assertEquals(RunReflectionState.Preparing(RUN_ID, branch), coordinator.state.value)
            assertEquals(ReflectionVoicePreparation(RUN_ID, FIRST_REQUEST_ID, exactText),
                coordinator.voicePreparation.value)

            coordinator.finishReceipt(RUN_ID)
            val ready = store.rows.getValue(RUN_ID)
            assertEquals(RunReflectionState.Ready(RUN_ID, exactText, branch), coordinator.state.value)
            assertEquals(exactText, ready.text)
            assertEquals(branch, ready.selectedBranch)
            assertNull(ready.candidatesJson)
            assertEquals(1, calls)

            now = 12_000L
            coordinator.finishReceipt(RUN_ID)
            coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
            assertEquals("$branch must remain finalized exactly once", ready, store.rows.getValue(RUN_ID))
            assertEquals(1, calls)
        }
    }

    @Test fun explicitNotSureSelectsUnknownAndSurvivesRecoveryWithoutAnotherRequest() {
        val store = MemoryReflectionStore()
        var calls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { calls++; candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) }, nowMillis = { 100L },
            quietDelay = { CompletableDeferred<Unit>().await() })
        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.NO_SELECTION)
        assertEquals(ReflectionBranch.NO_SELECTION, store.rows.getValue(RUN_ID).postRunEnergy)
        assertEquals(ReflectionBranch.NO_SELECTION, store.rows.getValue(RUN_ID).selectedBranch)
        assertEquals(RunReflectionState.Preparing(RUN_ID, ReflectionBranch.NO_SELECTION), coordinator.state.value)
        coordinator.finishReceipt(RUN_ID)
        val expected = RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT, ReflectionBranch.NO_SELECTION)
        assertEquals(expected, coordinator.state.value)
        val exact = store.rows.getValue(RUN_ID)
        val reopened = RunReflectionCoordinator(runDao = completedDao(), store = store,
            gateway = ReflectionGateway { error("Recovery must not regenerate a saved reflection") },
            applicationScope = scope(), nowMillis = { 30_000L })
        reopened.onSavedVisible(RUN_ID)
        assertEquals(expected, reopened.state.value)
        assertEquals(exact, store.rows.getValue(RUN_ID))
        assertEquals(1, calls)
    }

    @Test fun noEnergyGetsAFullQuietWindowAfterReceiptFinishes() {
        val store = MemoryReflectionStore()
        var now = 100L
        var requestedDelay = -1L
        val release = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { now }, quietDelay = { requestedDelay = it; release.await() }
        )

        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        assertEquals(-1L, requestedDelay)
        now = 9_000L
        coordinator.finishReceipt(RUN_ID)
        assertEquals(6_000L, requestedDelay)
        assertEquals(15_000L, store.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        assertNull(store.rows.getValue(RUN_ID).selectedBranch)
        assertNull(coordinator.voicePreparation.value)
        now = 15_000L
        release.complete(Unit)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
        assertNull("Timeout must not fabricate an explicit Not sure answer", store.rows.getValue(RUN_ID).postRunEnergy)
        assertEquals(NO_SELECTION_TEXT, coordinator.voicePreparation.value?.text)
    }

    @Test fun receiptFinishedBeforeSavedVisibilityCannotLeaveSelectionWaiting() {
        val store = MemoryReflectionStore()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        coordinator.finishReceipt(RUN_ID)
        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        assertTrue(store.rows.getValue(RUN_ID).selectionWindowStarted)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun delayedRowCreationUsesReceiptCompletionTimeRatherThanRestartingTheWindow() {
        val store = MemoryReflectionStore()
        var now = 100L
        var requestedDelay = -1L
        val release = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { now }, quietDelay = { requestedDelay = it; release.await() }
        )
        coordinator.finishReceipt(RUN_ID)
        now = 2_000L // Saved/Room initialization completes well after voice delivery ended.
        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        assertEquals(6_100L, store.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        assertEquals(4_100L, requestedDelay)
        now = 6_100L
        release.complete(Unit)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun retryRepairsFailedReceiptWindowWriteFromCachedFamilyWithoutProviderCall() {
        val backing = MemoryReflectionStore()
        var failNextWindowWrite = true
        val oneShotFailure = object : ReflectionStore by backing {
            override suspend fun beginSelectionWindow(runId: String, deadline: Long): RunReflectionEntity {
                if (failNextWindowWrite) {
                    failNextWindowWrite = false
                    error("temporary receipt-window storage failure")
                }
                return backing.beginSelectionWindow(runId, deadline)
            }
        }
        var now = 100L
        var requests = 0
        var requestIds = 0
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = oneShotFailure,
            gateway = ReflectionGateway { requests++; candidates() }, applicationScope = scope(),
            requestIdSupplier = { requestIds++; UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { now }, quietDelay = { CompletableDeferred<Unit>().await() }
        )
        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.SPENT)
        now = 9_000L
        coordinator.finishReceipt(RUN_ID)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        assertEquals(ReflectionStatus.PENDING, backing.rows.getValue(RUN_ID).status)
        assertFalse(backing.rows.getValue(RUN_ID).selectionWindowStarted)
        assertNotNull(backing.rows.getValue(RUN_ID).candidatesJson)

        now = 10_000L
        coordinator.retry(RUN_ID)
        assertEquals(RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.SPENT), coordinator.state.value)
        assertEquals(15_000L, backing.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        assertEquals(1, requests)
        assertEquals(1, requestIds)
    }

    @Test fun interruptedReceiptRecoversCachedFamilyLocallyWithOneNewWindow() {
        val store = MemoryReflectionStore(listOf(RunReflectionEntity(
            runId = RUN_ID, requestId = FIRST_REQUEST_ID, evidenceJson = "{}",
            selectionDeadlineEpochMillis = 100L, selectionWindowStarted = false,
            candidatesJson = gson.toJson(candidates())
        )))
        var calls = 0
        var now = 9_000L
        val release = CompletableDeferred<Unit>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { calls++; candidates() }, applicationScope = scope(),
            nowMillis = { now }, quietDelay = { release.await() }
        )
        runBlocking { coordinator.initialize() }
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(0, calls)
        assertTrue(store.rows.getValue(RUN_ID).selectionWindowStarted)
        assertEquals(15_000L, store.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        now = 12_000L
        runBlocking { coordinator.initialize() }
        coordinator.finishReceipt(RUN_ID)
        assertEquals(15_000L, store.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        now = 15_000L
        release.complete(Unit)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
        assertEquals(0, calls)
    }

    @Test fun energyDuringEffortHoldCanSelectEvenAfterQuietDeadline() {
        val store = MemoryReflectionStore()
        var now = 100L
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { now }, quietDelay = { CompletableDeferred<Unit>().await() }
        )
        coordinator.onSavedVisible(RUN_ID)
        coordinator.setEffortSheetOpen(RUN_ID, true)
        now = 9_000L
        runBlocking { coordinator.saveEnergy(RUN_ID, ReflectionBranch.SPENT) }
        assertEquals(RunReflectionState.Ready(RUN_ID, SPENT_TEXT, ReflectionBranch.SPENT), coordinator.state.value)
    }

    @Test fun awaitedEnergySaveReportsStorageFailureToItsCaller() {
        val backing = MemoryReflectionStore()
        val failing = object : ReflectionStore by backing {
            override suspend fun energy(
                runId: String, branch: ReflectionBranch, now: Long, selectionHeld: Boolean
            ): RunReflectionEntity = error("feedback storage unavailable")
        }
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = failing,
            gateway = ReflectionGateway { candidates() }, applicationScope = scope(),
            requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            nowMillis = { 100L }, quietDelay = { CompletableDeferred<Unit>().await() }
        )
        coordinator.onSavedVisible(RUN_ID)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { coordinator.saveEnergy(RUN_ID, ReflectionBranch.SPENT) }
        }
        assertNull(backing.rows.getValue(RUN_ID).postRunEnergy)
        assertEquals(RunReflectionState.Preparing(RUN_ID), coordinator.state.value)
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

        coordinator.onSavedVisible(RUN_ID, awaitingReceipt = true)
        coordinator.setEffortSheetOpen(RUN_ID, true)
        coordinator.finishReceipt(RUN_ID)
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

    @Test fun musicSnapshotNeverLoadsLocallyObservedMetadataWithoutBothSavedOptIns() {
        listOf(false to false, true to false).forEach { (capture, sharing) ->
            val dao = completedDao().apply {
                inserted[0] = inserted[0].copy(
                    musicObservationRequested = capture, musicReflectionSharingAllowed = sharing
                )
            }
            val store = MemoryReflectionStore().apply { observedMusic = qualifiedMusic(listOf(sampleMusicObservation())) }
            val requests = mutableListOf<ReflectionApiRequest>()
            val coordinator = RunReflectionCoordinator(
                runDao = dao, store = store,
                gateway = ReflectionGateway { request -> requests += request; candidates() },
                applicationScope = scope(), quietWindowMillis = 0L, nowMillis = { 100L }
            )

            coordinator.onSavedVisible(RUN_ID)
            assertEquals(0, store.musicReads)
            assertEquals(ReflectionMusicContext(), requests.single().evidence.musicContext)
            assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
        }
    }

    @Test fun approvedMusicSnapshotRemainsFrozenAcrossLaterObservationsRetryAndReopen() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(
                musicObservationRequested = true, musicReflectionSharingAllowed = true
            )
        }
        val store = MemoryReflectionStore().apply { observedMusic = qualifiedMusic(listOf(sampleMusicObservation())) }
        val ids = ArrayDeque(listOf(UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)))
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao, store = store,
            gateway = ReflectionGateway { request ->
                requests += request
                if (requests.size == 1) error("forced first-attempt failure")
                candidates()
            },
            applicationScope = scope(), requestIdSupplier = { ids.removeFirst() },
            quietWindowMillis = 0L, nowMillis = { 100L }
        )

        coordinator.onSavedVisible(RUN_ID)
        val original = store.rows.getValue(RUN_ID).evidenceJson
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        assertEquals("First observed title", requests.single().evidence.musicContext.trackTitle)
        store.observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(sequenceNumber = 2,
            observationKey = "later-title", title = "Later observed title")))
        coordinator.retry(RUN_ID)
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(2, requests.size)
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(requests[0].evidence, requests[1].evidence)
        assertEquals(original, store.rows.getValue(RUN_ID).evidenceJson)
        assertEquals(1, store.musicReads)
        assertEquals(1, dao.runReads)
        assertEquals(1, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun unavailableOptionalMusicReadStillGeneratesFromSavedRunWithUnknownMusic() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(
                musicObservationRequested = true, musicReflectionSharingAllowed = true
            )
        }
        val store = MemoryReflectionStore().apply { musicReadFailure = IllegalStateException("unavailable music") }
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = dao, store = store,
            gateway = ReflectionGateway { request -> requests += request; candidates() },
            applicationScope = scope(), quietWindowMillis = 0L, nowMillis = { 100L }
        )

        coordinator.onSavedVisible(RUN_ID)
        assertEquals(1, store.musicReads)
        assertEquals(ReflectionMusicContext(), requests.single().evidence.musicContext)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun skippedSongProducesOrdinaryReflectionAndOneMinuteEnablesMusicWithoutChangingRunFacts() {
        for (listenedMillis in listOf(59_000L, 60_000L)) {
            val dao = completedDao().apply {
                inserted[0] = inserted[0].copy(musicObservationRequested = true, musicReflectionSharingAllowed = true)
            }
            val originalRun = dao.inserted.single()
            val store = MemoryReflectionStore().apply {
                observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(
                    title = "Lovely Day", artist = "Bill Withers")), listenedMillis)
            }
            val requests = mutableListOf<ReflectionApiRequest>()
            val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
                requests += request
                candidates().copy(isTestData = false, songCueId = request.evidence.musicContext.songCue?.cueId)
            }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })

            coordinator.onSavedVisible(RUN_ID)

            val evidence = requests.single().evidence
            val ready = coordinator.state.value as RunReflectionState.Ready
            assertEquals(NO_SELECTION_TEXT, ready.text)
            assertEquals(originalRun, dao.inserted.single())
            assertEquals(60_000L, evidence.activeDurationMillis)
            assertEquals(180.0, evidence.metrics.distanceMeters!!, 0.0)
            if (listenedMillis < 60_000L) {
                assertEquals(ReflectionMusicContext(), evidence.musicContext)
                assertTrue(evidence.songCandidates.orEmpty().isEmpty())
                assertNull(ready.passage)
            } else {
                assertEquals("Lovely Day", evidence.musicContext.trackTitle)
                assertEquals("1", evidence.songCandidates!!.single().candidateId)
                assertNotNull(ready.passage)
            }
            assertFalse(store.rows.getValue(RUN_ID).evidenceJson.contains("playbackIntervalMillis"))
        }
    }

    @Test fun weatherSnapshotRemainsFrozenAcrossRetryLaterWeatherAndProcessReopen() {
        val dao = completedDao()
        val firstWeather = sampleWeather()
        val store = MemoryReflectionStore().apply { savedWeather = firstWeather }
        val ids = ArrayDeque(listOf(UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)))
        val requests = mutableListOf<ReflectionApiRequest>()
        val gateway = ReflectionGateway { request ->
            requests += request
            if (requests.size == 1) error("forced first-attempt failure")
            candidates()
        }
        val coordinator = RunReflectionCoordinator(
            runDao = dao, store = store, gateway = gateway, applicationScope = scope(),
            requestIdSupplier = { ids.removeFirst() }, quietWindowMillis = 0L, nowMillis = { 100L }
        )
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        val originalEvidence = store.rows.getValue(RUN_ID).evidenceJson
        assertEquals(firstWeather, requests.single().evidence.weatherContext)

        store.savedWeather = firstWeather.copy(temperatureCelsius = 35.0, fetchedAtEpochMillis = FINISH + 1_000L)
        coordinator.retry(RUN_ID)
        val ready = store.rows.getValue(RUN_ID)
        val reopened = RunReflectionCoordinator(
            runDao = dao, store = store, gateway = gateway, applicationScope = scope(),
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        reopened.onSavedVisible(RUN_ID)

        assertEquals(2, requests.size)
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(firstWeather, requests[1].evidence.weatherContext)
        assertEquals(originalEvidence, store.rows.getValue(RUN_ID).evidenceJson)
        assertEquals(ready, store.rows.getValue(RUN_ID))
        assertEquals(1, store.weatherReads)
        assertEquals(1, dao.runReads)
        assertEquals(1, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), reopened.state.value)
    }

    @Test fun lateWeatherCannotReplaceTheUnknownEvidenceFrozenBeforeTheServiceWasReady() {
        val dao = completedDao()
        val store = MemoryReflectionStore()
        val requests = mutableListOf<ReflectionApiRequest>()
        val first = RunReflectionCoordinator(
            runDao = dao, store = store, gateway = ReflectionGateway { error("bridge not ready") },
            applicationScope = scope(), requestIdSupplier = { UUID.fromString(FIRST_REQUEST_ID) },
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        first.onSavedVisible(RUN_ID, bridgeReady = false)
        val originalEvidence = store.rows.getValue(RUN_ID).evidenceJson
        assertNull(gson.fromJson(originalEvidence, CompletedRunEvidence::class.java).weatherContext)
        store.savedWeather = sampleWeather()

        val reopened = RunReflectionCoordinator(
            runDao = dao, store = store,
            gateway = ReflectionGateway { request -> requests += request; candidates() },
            applicationScope = scope(), requestIdSupplier = { UUID.fromString(SECOND_REQUEST_ID) },
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        reopened.onSavedVisible(RUN_ID)
        assertTrue(requests.isEmpty()) // Recovery waits for explicit Retry rather than billing again.
        reopened.retry(RUN_ID)

        assertNull(requests.single().evidence.weatherContext)
        assertEquals(originalEvidence, store.rows.getValue(RUN_ID).evidenceJson)
        assertEquals(1, store.weatherReads)
        assertEquals(1, dao.runReads)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), reopened.state.value)
    }

    @Test fun unavailableOptionalWeatherReadStillGeneratesTheSavedRunWithoutWeather() {
        val store = MemoryReflectionStore().apply { weatherReadFailure = IllegalStateException("weather unavailable") }
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { request -> requests += request; candidates() },
            applicationScope = scope(), quietWindowMillis = 0L, nowMillis = { 100L }
        )
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(1, store.weatherReads)
        assertNull(requests.single().evidence.weatherContext)
        assertEquals(1, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun preWeatherSavedEvidenceRetriesUnchangedWithoutReadingNewWeather() {
        val dao = completedDao()
        val oldEvidence = JsonParser.parseString(gson.toJson(reflectionRequestFromSavedRun(
            FIRST_REQUEST_ID, dao.inserted.single(),
            listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
        ).evidence)).asJsonObject.apply { remove("weatherContext") }.toString()
        val store = MemoryReflectionStore(listOf(RunReflectionEntity(
            runId = RUN_ID, requestId = FIRST_REQUEST_ID, evidenceJson = oldEvidence,
            selectionDeadlineEpochMillis = 100L, selectedBranch = ReflectionBranch.NO_SELECTION,
            status = ReflectionStatus.FAILED
        ))).apply { savedWeather = sampleWeather() }
        val requests = mutableListOf<ReflectionApiRequest>()
        val reopened = RunReflectionCoordinator(
            runDao = dao, store = store,
            gateway = ReflectionGateway { request -> requests += request; candidates() },
            applicationScope = scope(), requestIdSupplier = { UUID.fromString(SECOND_REQUEST_ID) },
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        reopened.onSavedVisible(RUN_ID)
        assertTrue(requests.isEmpty())
        reopened.retry(RUN_ID)

        assertNull(requests.single().evidence.weatherContext)
        assertEquals(oldEvidence, store.rows.getValue(RUN_ID).evidenceJson)
        assertEquals(0, store.weatherReads)
        assertEquals(0, dao.runReads)
        assertEquals(0, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), reopened.state.value)
    }

    @Test fun weatherForAnotherStartIsOmittedWithoutBlockingTheRunReflection() {
        val store = MemoryReflectionStore().apply {
            savedWeather = sampleWeather().copy(targetEpochMillis = START + 1L)
        }
        val requests = mutableListOf<ReflectionApiRequest>()
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { request -> requests += request; candidates() },
            applicationScope = scope(), quietWindowMillis = 0L, nowMillis = { 100L }
        )
        coordinator.onSavedVisible(RUN_ID)

        assertEquals(1, store.weatherReads)
        assertNull(requests.single().evidence.weatherContext)
        assertEquals(1, store.creations)
        assertEquals(RunReflectionState.Ready(RUN_ID, NO_SELECTION_TEXT), coordinator.state.value)
    }

    @Test fun cancelledWeatherReadDoesNotCreateARequestOrPublishAWeatherFailure() {
        val store = MemoryReflectionStore().apply { weatherReadFailure = CancellationException("cancelled read") }
        var calls = 0
        val coordinator = RunReflectionCoordinator(
            runDao = completedDao(), store = store,
            gateway = ReflectionGateway { calls++; candidates() }, applicationScope = scope(),
            quietWindowMillis = 0L, nowMillis = { 100L }
        )
        val work = coordinator.onSavedVisible(RUN_ID)
        assertTrue(work.isCancelled)
        assertEquals(1, store.weatherReads)
        assertEquals(0, calls)
        assertEquals(0, store.creations)
        assertTrue(store.rows.isEmpty())
        assertEquals(RunReflectionState.Idle, coordinator.state.value)
    }

    private fun sampleWeather(): RunWeatherSnapshot {
        val hour = START / 3_600_000L * 3_600_000L
        return RunWeatherSnapshot(targetEpochMillis = START, validFromEpochMillis = hour,
            validToEpochMillis = hour + 3_600_000L, fetchedAtEpochMillis = FINISH,
            temperatureCelsius = 21.5, apparentTemperatureCelsius = 20.0,
            relativeHumidityPercent = 48.5, windSpeedKmh = 8.0, weatherCode = 0)
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
                selectionDeadlineEpochMillis = 6_100L,
                selectionWindowStarted = false
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
        assertTrue(store.rows.getValue(RUN_ID).selectionWindowStarted)
        assertEquals(6_100L, store.rows.getValue(RUN_ID).selectionDeadlineEpochMillis)
        assertEquals(RunReflectionState.Failed(RUN_ID), coordinator.state.value)
        assertEquals(0, calls)
        assertEquals(0, dao.runReads)
    }

    @Test fun acceptedSongRetryUsesFrozenEvidenceAndAcknowledgedClipThenRestartKeepsOnlyExactText() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Stronger", artist = "Kanye West")))
            val first = observedMusic.first()
            preparedSong = SongPreparationClaimEntity(RUN_ID, FIRST_REQUEST_ID, first.title!!, first.artist!!,
                first.sequenceNumber, "a".repeat(64), START + 60_000L)
        }
        val requests = mutableListOf<ReflectionApiRequest>()
        val ids = ArrayDeque(listOf(UUID.fromString(FIRST_REQUEST_ID), UUID.fromString(SECOND_REQUEST_ID)))
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            requests += request
            if (requests.size == 1) throw IllegalStateException("Fictional unavailable provider")
            candidates().copy(isTestData = false, songCueId = request.evidence.musicContext.songCue?.cueId)
        }, scope(), requestIdSupplier = { ids.removeFirst() }, nowMillis = { 100L })

        coordinator.onSavedVisible(RUN_ID)
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        coordinator.selectEnergy(RUN_ID, ReflectionBranch.SPENT)
        store.observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Lovely Day", artist = "Bill Withers")))
        coordinator.retry(RUN_ID)

        assertEquals(2, requests.size)
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        assertEquals(requests[0].evidence, requests[1].evidence)
        assertEquals("Stronger", requests[1].evidence.musicContext.trackTitle)
        assertEquals(SongPreparationReference(FIRST_REQUEST_ID, "a".repeat(64)),
            requests[1].evidence.songPreparationReference)
        assertEquals(1, requests[1].evidence.songPassage?.songStudyVersion)
        assertEquals(1, store.musicReads)
        val ready = coordinator.state.value as RunReflectionState.Ready
        assertEquals(SPENT_TEXT, ready.text)
        assertEquals(requests[1].evidence.songPassage?.passage(), ready.passage)

        var restartCalls = 0
        val restarted = RunReflectionCoordinator(dao, store, ReflectionGateway {
            restartCalls++; error("A saved reflection must not regenerate")
        }, scope(), nowMillis = { 100L })
        restarted.onSavedVisible(RUN_ID)
        val restored = restarted.state.value as RunReflectionState.Ready
        assertEquals(0, restartCalls)
        assertEquals(SPENT_TEXT, restored.text)
        assertNull(restored.passage)
    }

    @Test fun chosenNonFirstSongIsSavedWithReplyAndDoesNotReplayAfterReopening() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true, musicReflectionSharingAllowed = true,
                lastCheckpointEpochMillis = START + 180_000L, finishEpochMillis = START + 180_000L)
            transitions.clear()
            transitions += RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, START + 180_000L)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Stronger", artist = "Kanye West"),
                sampleMusicObservation().copy(sequenceNumber = 2, observationKey = "second-song",
                    observedAtEpochMillis = START + 65_000L, title = "Lovely Day", artist = "Bill Withers")))
        }
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            assertEquals(2, request.evidence.songCandidates!!.size)
            candidates().copy(isTestData = false, selectedSongId = "2", songSelectionMethod = "CONTEXT_MATCH",
                songCueId = "lovely_day_bill_withers_v1")
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        val ready = coordinator.state.value as RunReflectionState.Ready
        assertEquals("Lovely Day", ready.passage?.trackTitle)
        val saved = store.rows.getValue(RUN_ID)
        val evidence = com.google.gson.Gson().fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertEquals("2", evidence.selectedSongId)
        assertEquals("Lovely Day", evidence.musicContext.trackTitle)
        assertNull(saved.candidatesJson)
        val reopened = RunReflectionCoordinator(dao, store, ReflectionGateway { error("No regeneration") },
            scope(), nowMillis = { 100L })
        reopened.onSavedVisible(RUN_ID)
        assertNull((reopened.state.value as RunReflectionState.Ready).passage)
        assertEquals(saved.text, (reopened.state.value as RunReflectionState.Ready).text)
    }

    @Test fun sourcedSnippetKeepsItsObservedRecordingAndTimingButDoesNotReplayAfterReopening() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true)
        }
        val uri = "spotify:track:" + "a".repeat(22)
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Example Song", artist = "Example Artist",
                spotifyTrackUri = uri, albumName = "Example Album", trackDurationMillis = 180_000L)))
        }
        val snippet = ReflectionSongSnippet("lrclib_v1_123_32000_36000", "LRCLIB", "123",
            "Example Song", "Example Artist", "Example Album", 180_000L, 32_000L, 4_000L, "REPEATED_HOOK", 1)
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            candidates().copy(isTestData = false, selectedSongId = request.evidence.songCandidates!!.single().candidateId,
                songSelectionMethod = "CONTEXT_MATCH", songCueId = snippet.cueId, songSnippet = snippet)
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        val passage = (coordinator.state.value as RunReflectionState.Ready).passage!!
        assertEquals(uri, passage.spotifyMediaId)
        assertEquals(32_000L, passage.startPositionMillis)
        assertEquals(4_000L, passage.durationMillis)
        assertTrue(passage.allowTrackSwitch)
        val saved = store.rows.getValue(RUN_ID)
        val evidence = gson.fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertEquals("LRCLIB", evidence.songPassage!!.timingSource)
        assertEquals("123", evidence.songPassage!!.timingSourceId)
        assertEquals(1, evidence.songPassage!!.timingVersion)
        assertNull(saved.candidatesJson)
        assertNull(pairedPassageFor(saved))
        assertNull(pairedPassageFor(saved, "wrong-cue"))
        val reopened = RunReflectionCoordinator(dao, store, ReflectionGateway { error("No regeneration") },
            scope(), nowMillis = { 100L })
        reopened.onSavedVisible(RUN_ID)
        assertNull((reopened.state.value as RunReflectionState.Ready).passage)
        assertEquals(saved.text, (reopened.state.value as RunReflectionState.Ready).text)
    }

    @Test fun observedOpeningReceiptPlaysWithoutAnalysisAcknowledgementAndNeverReplaysOnReopen() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true)
        }
        val uri = "spotify:track:" + "a".repeat(22)
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Example Song", artist = "Example Artist",
                spotifyTrackUri = uri, albumName = null, trackDurationMillis = 180_000L)))
        }
        val snippet = ReflectionSongSnippet("recording_v2_opening_0_8000", "RUNSTATE_RECORDING", "opening",
            "Example Song", "Example Artist", null, 180_000, 0, 8_000, "RECORDING_OPENING_FALLBACK", 2)
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            candidates().copy(isTestData = false, selectedSongId = request.evidence.songCandidates!!.single().candidateId,
                songSelectionMethod = "OBSERVED_FALLBACK", songSnippet = snippet)
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        val passage = (coordinator.state.value as RunReflectionState.Ready).passage!!
        assertEquals(uri, passage.spotifyMediaId); assertEquals(8_000L, passage.durationMillis)
        val saved = store.rows.getValue(RUN_ID)
        val evidence = gson.fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertNull(evidence.musicContext.songCue); assertEquals(snippet, evidence.songPlaybackReceipt)
        assertNull(pairedPassageFor(saved)); assertNull(pairedPassageFor(saved, "wrong-cue"))
        assertNotNull(pairedPassageFor(saved, snippet.cueId))
        val damaged = evidence.copy(songPassage = evidence.songPassage!!.copy(startPositionMillis = 50_000))
        assertNull(pairedPassageFor(saved.copy(evidenceJson = gson.toJson(damaged)), snippet.cueId))
        val reopened = RunReflectionCoordinator(dao, store, ReflectionGateway { error("No regeneration") },
            scope(), nowMillis = { 100L })
        reopened.onSavedVisible(RUN_ID)
        assertNull((reopened.state.value as RunReflectionState.Ready).passage)
        assertEquals(saved.text, (reopened.state.value as RunReflectionState.Ready).text)
    }

    @Test fun v2RetryClearsFailedDeliveryReceiptWhenServerReturnsNoWinner() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Example Song", artist = "Example Artist",
                spotifyTrackUri = "spotify:track:" + "a".repeat(22), albumName = null, trackDurationMillis = 180_000L)))
            failFinalizationOnce = true
        }
        val snippet = ReflectionSongSnippet("recording_v2_opening_0_8000", "RUNSTATE_RECORDING", "opening",
            "Example Song", "Example Artist", null, 180_000, 0, 8_000, "RECORDING_OPENING_FALLBACK", 2)
        var calls = 0
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            if (++calls == 1) candidates().copy(isTestData = false,
                selectedSongId = request.evidence.songCandidates!!.single().candidateId,
                songSelectionMethod = "OBSERVED_FALLBACK", songSnippet = snippet)
            else candidates().copy(isTestData = false)
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        coordinator.retry(RUN_ID)
        val saved = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionStatus.READY, saved.status)
        val evidence = gson.fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertNull(evidence.songPlaybackReceipt); assertNull(evidence.songPassage); assertNull(evidence.selectedSongId)
        assertNull((coordinator.state.value as RunReflectionState.Ready).passage); assertEquals(2, calls)
    }

    @Test fun failedLocalFinalizationThenFallbackCannotKeepASourcedSnippet() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Example Song", artist = "Example Artist",
                spotifyTrackUri = "spotify:track:" + "a".repeat(22), albumName = "Example Album", trackDurationMillis = 180_000L)))
            failFinalizationOnce = true
        }
        val snippet = ReflectionSongSnippet("lrclib_v1_123_32000_36000", "LRCLIB", "123",
            "Example Song", "Example Artist", "Example Album", 180_000L, 32_000L, 4_000L, "REPEATED_HOOK", 1)
        var calls = 0
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway { request ->
            if (++calls == 1) candidates().copy(isTestData = false,
                selectedSongId = request.evidence.songCandidates!!.single().candidateId,
                songSelectionMethod = "CONTEXT_MATCH", songCueId = snippet.cueId, songSnippet = snippet)
            else candidates().copy(isTestData = false)
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        coordinator.retry(RUN_ID)
        val saved = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionStatus.READY, saved.status)
        val evidence = gson.fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertNull(evidence.selectedSongId)
        assertNull(evidence.musicContext.songCue)
        assertNull(evidence.songPassage)
        assertNull((coordinator.state.value as RunReflectionState.Ready).passage)
        assertEquals(2, calls)
    }

    @Test fun retryFallbackClearsPreviousSelectionAfterLocalFinalizationFailure() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true, musicReflectionSharingAllowed = true)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Lovely Day", artist = "Bill Withers")))
            failFinalizationOnce = true
        }
        var calls = 0
        val coordinator = RunReflectionCoordinator(dao, store, ReflectionGateway {
            if (++calls == 1) candidates().copy(isTestData = false, selectedSongId = "1",
                songSelectionMethod = "CONTEXT_MATCH", songCueId = "lovely_day_bill_withers_v1")
            else candidates().copy(isTestData = false)
        }, scope(), quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        assertEquals(ReflectionStatus.FAILED, store.rows.getValue(RUN_ID).status)
        coordinator.retry(RUN_ID)
        val saved = store.rows.getValue(RUN_ID)
        assertEquals(ReflectionStatus.READY, saved.status)
        val evidence = com.google.gson.Gson().fromJson(saved.evidenceJson, CompletedRunEvidence::class.java)
        assertNull(evidence.selectedSongId)
        assertNull(evidence.songSelectionMethod)
        assertNull((coordinator.state.value as RunReflectionState.Ready).passage)
        assertEquals(2, calls)
    }

    @Test fun ordinarySourceFallbackRemainsReadableWithoutLaunchingFrozenSongPassage() {
        val dao = completedDao().apply {
            inserted[0] = inserted[0].copy(musicObservationRequested = true,
                musicReflectionSharingAllowed = true)
        }
        val store = MemoryReflectionStore().apply {
            observedMusic = qualifiedMusic(listOf(sampleMusicObservation().copy(title = "Good as Hell", artist = "Lizzo")))
        }
        val coordinator = RunReflectionCoordinator(dao, store,
            ReflectionGateway { candidates().copy(isTestData = false) }, scope(),
            quietWindowMillis = 0L, nowMillis = { 100L })
        coordinator.onSavedVisible(RUN_ID)
        val ready = coordinator.state.value as RunReflectionState.Ready
        assertEquals(NO_SELECTION_TEXT, ready.text)
        assertNull(ready.passage)
    }

    @Test fun lateSuccessForFirstRunCannotReplaceSecondVisibleRun() {
        val first = CompletableDeferred<ReflectionCandidates>()
        val second = CompletableDeferred<ReflectionCandidates>()
        val store = MemoryReflectionStore()
        val coordinator = outOfOrderCoordinator(first, second, store)

        coordinator.onSavedVisible(RUN_ID)
        coordinator.onSavedVisible(SECOND_RUN_ID)
        second.complete(candidates("[FAKE] Second run."))
        val secondPreparation = coordinator.voicePreparation.value
        assertEquals(SECOND_RUN_ID, secondPreparation?.runId)
        assertEquals(
            RunReflectionState.Ready(SECOND_RUN_ID, "[FAKE] Second run."),
            coordinator.state.value
        )

        first.complete(candidates("[FAKE] Late first run."))
        assertEquals(secondPreparation, coordinator.voicePreparation.value)
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

    private fun sampleMusicObservation() = RunMusicObservationEntity(
        runId = RUN_ID, sequenceNumber = 1, observationKey = "fictional-first-report",
        observedAtEpochMillis = START, elapsedRealtimeNanos = 1_000_000_000L,
        sourcePackage = "com.spotify.music", title = "First observed title", artist = "Reported artist",
        playbackState = MusicPlaybackState.PLAYING, eventKind = MusicObservationKind.SESSION_SNAPSHOT
    )

    /** Each song contributes twelve separate five-second receipts, never one inferred whole play. */
    private fun qualifiedMusic(
        rows: List<RunMusicObservationEntity>,
        playbackMillis: Long = 60_000L
    ): List<RunMusicObservationEntity> {
        var sequence = (rows.maxOfOrNull { it.sequenceNumber } ?: 0) + 1_000
        var nextStart = START
        val samples = rows.groupBy { requireNotNull(songPreparationIdentity(it.title, it.artist)) }
            .values.flatMap { reports ->
                val first = reports.first()
                val anchor = maxOf(nextStart, reports.maxOf { it.observedAtEpochMillis })
                nextStart = anchor + playbackMillis + 5_000L
                val result = mutableListOf<RunMusicObservationEntity>()
                var credited = 0L
                while (credited < playbackMillis) {
                    val interval = minOf(5_000L, playbackMillis - credited)
                    credited += interval
                    val time = anchor + credited
                    result += first.copy(sequenceNumber = sequence++,
                        observationKey = "${first.observationKey}-sample-$credited",
                        observedAtEpochMillis = time,
                        elapsedRealtimeNanos = (time - START + 1_000L) * 1_000_000L,
                        eventKind = MusicObservationKind.PLAYBACK_SAMPLE, playbackIntervalMillis = interval)
                }
                result
            }
        return rows + samples
    }
}
