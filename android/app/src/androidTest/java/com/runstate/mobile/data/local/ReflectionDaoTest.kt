package com.runstate.mobile.data.local

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReflectionDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val completed = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = 1_756_000_000_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_756_000_060_000L,
        finishEpochMillis = 1_756_000_060_000L
    )
    private val pending = RunReflectionEntity(
        runId = completed.runId,
        requestId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba",
        evidenceJson = "{\"source\":\"saved-run\"}",
        selectionDeadlineEpochMillis = 1_756_000_066_000L
    )
    private val retryId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6"

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = buildRunStateDatabase(context, DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private suspend fun createPending(): ReflectionDao {
        database.runDao().insert(completed)
        return database.reflectionDao().also { it.ensurePending(pending) }
    }

    @Test
    fun receiptPendingWindowSurvivesReopenAndStartsExactlyOnce() = runBlocking {
        database.runDao().insert(completed)
        var dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionWindowStarted = false))
        assertNull(dao.selectNoSelectionIfDue(completed.runId, Long.MAX_VALUE).selectedBranch)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        dao = database.reflectionDao()
        assertFalse(requireNotNull(dao.findByRunId(completed.runId)).selectionWindowStarted)

        val newDeadline = pending.selectionDeadlineEpochMillis + 12_000L
        val started = dao.beginSelectionWindow(completed.runId, newDeadline)
        assertTrue(started.selectionWindowStarted)
        assertEquals(newDeadline, started.selectionDeadlineEpochMillis)
        assertEquals(started, dao.beginSelectionWindow(completed.runId, newDeadline + 100_000L))
        assertNull(dao.selectNoSelectionIfDue(completed.runId, newDeadline - 1L).selectedBranch)
        assertEquals(ReflectionBranch.NO_SELECTION,
            dao.selectNoSelectionIfDue(completed.runId, newDeadline).selectedBranch)
    }

    @Test
    fun receiptAcceptsEarlyEnergyButCannotFinalizeUntilDeliveryEnds() = runBlocking {
        database.runDao().insert(completed)
        val dao = database.reflectionDao()
        dao.ensurePending(pending.copy(selectionWindowStarted = false))
        val afterPlaceholderDeadline = pending.selectionDeadlineEpochMillis + 20_000L
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, afterPlaceholderDeadline)
        assertEquals(ReflectionBranch.SPENT, chosen.selectedBranch)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{}", true))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId,
            ReflectionBranch.SPENT, "Exact prepared reply.", afterPlaceholderDeadline))
        dao.beginSelectionWindow(completed.runId, afterPlaceholderDeadline + 6_000L)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId,
            ReflectionBranch.SPENT, "Exact prepared reply.", afterPlaceholderDeadline))
    }

    @Test
    fun effortHoldAllowsFirstEnergyAfterDeadlineWithoutReplacingAnExistingSelection() = runBlocking {
        val dao = createPending()
        val afterDeadline = pending.selectionDeadlineEpochMillis + 20_000L
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.SPENT,
            afterDeadline, selectionHeld = true)
        assertEquals(ReflectionBranch.SPENT, chosen.selectedBranch)
        val later = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP,
            afterDeadline + 100L, selectionHeld = true)
        assertEquals(ReflectionBranch.SPENT, later.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, later.postRunEnergy)
    }

    @Test
    fun readyTextSurvivesReopenExactlyAndUnusedCandidatesAreDiscarded() = runBlocking {
        val dao = createPending()
        val text = "  Exact reply.\nSecond line\twith Unicode — 🌤  "
        dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, pending.selectionDeadlineEpochMillis - 1)
        assertTrue(dao.saveCandidates(completed.runId, pending.requestId, "{\"spent\":\"candidate\"}", false))
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, text, pending.selectionDeadlineEpochMillis))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)

        val restored = requireNotNull(database.reflectionDao().findByRunId(completed.runId))
        assertEquals(ReflectionStatus.READY, restored.status)
        assertEquals(text, restored.text)
        assertEquals(ReflectionBranch.SPENT, restored.selectedBranch)
        assertEquals(pending.evidenceJson, restored.evidenceJson)
        assertEquals(pending.selectionDeadlineEpochMillis, restored.finalizedAtEpochMillis)
        assertFalse(restored.isTestData)
        assertNull(restored.candidatesJson)
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun duplicateCreationCannotReplaceEvidenceDeadlineOrRequestIdentity() = runBlocking {
        val dao = createPending()
        assertEquals(pending, dao.ensurePending(pending.copy(
            requestId = retryId,
            evidenceJson = "{\"changed\":true}",
            selectionDeadlineEpochMillis = pending.selectionDeadlineEpochMillis + 60_000
        )))
        assertEquals(listOf(pending), dao.all())
    }

    @Test
    fun firstEnergyFreezesBranchAndLaterEnergyOnlyUpdatesFeedbackEvenAfterReady() = runBlocking {
        val dao = createPending()
        val deadline = pending.selectionDeadlineEpochMillis
        dao.recordEnergy(completed.runId, ReflectionBranch.SPENT, deadline - 100)
        val second = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP, deadline - 50)
        assertEquals(ReflectionBranch.SPENT, second.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, second.postRunEnergy)
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, "Stored reply", deadline)
        val late = dao.recordEnergy(completed.runId, ReflectionBranch.FEELING_GOOD, deadline + 1)
        assertEquals(ReflectionBranch.SPENT, late.selectedBranch)
        assertEquals(ReflectionBranch.FEELING_GOOD, late.postRunEnergy)
        assertEquals("Stored reply", late.text)
        assertEquals(deadline, late.finalizedAtEpochMillis)
        assertFalse(dao.markFailed(completed.runId, pending.requestId))
        assertFalse(dao.saveCandidates(completed.runId, pending.requestId, "replacement", false))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.SPENT, "replacement", deadline + 5))
        assertNull(dao.retry(completed.runId, retryId))
    }

    @Test
    fun quietWindowExpiresAtDeadlineAndLateEnergyDoesNotChangeNoSelection() = runBlocking {
        val dao = createPending()
        val deadline = pending.selectionDeadlineEpochMillis
        assertNull(dao.selectNoSelectionIfDue(completed.runId, deadline - 1).selectedBranch)
        val chosen = dao.recordEnergy(completed.runId, ReflectionBranch.POWERED_UP, deadline)
        assertEquals(ReflectionBranch.NO_SELECTION, chosen.selectedBranch)
        assertEquals(ReflectionBranch.POWERED_UP, chosen.postRunEnergy)
        assertEquals(chosen, dao.selectNoSelectionIfDue(completed.runId, deadline + 1))
    }

    @Test
    fun failureCanStillAcceptSelectionAndRetryPreservesEvidenceAndRejectsOldCallbacks() = runBlocking {
        val dao = createPending()
        assertTrue(dao.markFailed(completed.runId, pending.requestId))
        dao.recordEnergy(completed.runId, ReflectionBranch.FEELING_GOOD, pending.selectionDeadlineEpochMillis - 1)
        val retried = requireNotNull(dao.retry(completed.runId, retryId))
        assertEquals(ReflectionStatus.PENDING, retried.status)
        assertEquals(ReflectionBranch.FEELING_GOOD, retried.selectedBranch)
        assertEquals(pending.evidenceJson, retried.evidenceJson)
        assertEquals(pending.selectionDeadlineEpochMillis, retried.selectionDeadlineEpochMillis)
        assertFalse(dao.saveCandidates(completed.runId, pending.requestId, "old", true))
        assertFalse(dao.markFailed(completed.runId, pending.requestId))
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.FEELING_GOOD, "old", pending.selectionDeadlineEpochMillis))
        assertTrue(dao.saveCandidates(completed.runId, retryId, "new", true))
        assertFalse(dao.saveCandidates(completed.runId, retryId, "duplicate", true))
        assertFalse(dao.finalizeReady(completed.runId, retryId, ReflectionBranch.SPENT, "wrong branch", pending.selectionDeadlineEpochMillis))
        assertTrue(dao.finalizeReady(completed.runId, retryId, ReflectionBranch.FEELING_GOOD, "new reply", pending.selectionDeadlineEpochMillis))
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun failedUnselectedRecordCanExpireAndRetryRequiresNewIdentity() = runBlocking {
        val dao = createPending()
        dao.markFailed(completed.runId, pending.requestId)
        assertEquals(ReflectionBranch.NO_SELECTION,
            dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis).selectedBranch)
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.retry(completed.runId, pending.requestId) }
        }
        assertEquals(ReflectionBranch.NO_SELECTION, dao.retry(completed.runId, retryId)?.selectedBranch)
    }

    @Test
    fun finalizationRequiresAcceptedCandidatesAndFrozenSelection() = runBlocking {
        val dao = createPending()
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
        dao.selectNoSelectionIfDue(completed.runId, pending.selectionDeadlineEpochMillis)
        assertFalse(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
        dao.saveCandidates(completed.runId, pending.requestId, "{}", true)
        assertTrue(dao.finalizeReady(completed.runId, pending.requestId, ReflectionBranch.NO_SELECTION, "reply", pending.selectionDeadlineEpochMillis))
    }

    @Test
    fun missingOrUnfinishedParentsCannotReceiveReflectionsAndDeletesCascade(): Unit = runBlocking {
        val dao = database.reflectionDao()
        assertThrows(IllegalStateException::class.java) { runBlocking { dao.ensurePending(pending) } }
        database.runDao().insert(completed.copy(state = StoredRunState.RUNNING, finishEpochMillis = null))
        assertThrows(IllegalStateException::class.java) { runBlocking { dao.ensurePending(pending) } }
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs")
        database.runDao().insert(completed)
        dao.ensurePending(pending)
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs WHERE run_id = ?", arrayOf(completed.runId))
        assertNull(dao.findByRunId(completed.runId))
        assertTrue(dao.observeAll().first().isEmpty())
        assertThrows<SQLiteConstraintException>(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO run_reflections (run_id, request_id, evidence_json, selection_deadline_epoch_millis, status) VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(pending.runId, pending.requestId, pending.evidenceJson, pending.selectionDeadlineEpochMillis, "PENDING")
            )
        }
    }

    @Test
    fun completedHistoryExcludesActiveRunsAndHasStableNewestFirstOrder() = runBlocking {
        val earlier = completed.copy(runId = "03689ffc-65d9-4907-ae77-7a17b3ad77a6", officialStartEpochMillis = completed.officialStartEpochMillis - 1)
        val tied = completed.copy(runId = "8c405ab0-4a80-4be9-9bfb-19f2517ec2ba")
        val active = completed.copy(runId = "fd87fa03-dfbf-47ec-b721-4d41314e52f3", state = StoredRunState.RUNNING, finishEpochMillis = null)
        listOf(earlier, tied, active, completed).forEach { database.runDao().insert(it) }
        assertEquals(listOf(completed, tied, earlier), database.runDao().observeCompletedRuns().first())
    }

    private companion object {
        const val DATABASE_NAME = "runstate-reflection-test.db"
    }
}
