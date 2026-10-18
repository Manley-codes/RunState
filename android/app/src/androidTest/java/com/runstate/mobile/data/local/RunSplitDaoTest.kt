package com.runstate.mobile.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.runstate.mobile.run.RunSplit
import com.runstate.mobile.run.RunSplitCalculator
import com.runstate.mobile.run.RunTimingMark
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room transactions prove retry, immutability and owned-row deletion without phone history. */
@RunWith(AndroidJUnit4::class)
class RunSplitDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val start = 1_756_000_000_000L

    @Before
    fun open() {
        database = Room.inMemoryDatabaseBuilder(context, RunStateDatabase::class.java).build()
    }

    @After
    fun close() = database.close()

    private fun running(id: String, mode: RunTrackingMode = RunTrackingMode.GPS) = RunEntity(
        runId = id, state = StoredRunState.RUNNING,
        officialStartEpochMillis = start, startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = start, transitionHistoryComplete = true,
        trackingMode = mode
    )

    private suspend fun complete(
        id: String = UUID.randomUUID().toString(),
        mode: RunTrackingMode = RunTrackingMode.GPS,
        coverage: TelemetryCoverage = TelemetryCoverage.COMPLETE,
        unit: DistanceUnit = DistanceUnit.KILOMETERS
    ): String {
        database.runDao().insert(running(id, mode))
        database.runDao().pauseRun(id, start + 400_000L)
        database.runDao().completeRun(id, start + 410_000L, FinalRunMetrics(
            distanceMeters = if (coverage == TelemetryCoverage.UNAVAILABLE) null else 2_500.0,
            source = if (mode == RunTrackingMode.GPS) MetricSource.GPS else MetricSource.FIXTURE,
            coverage = coverage, displayUnit = unit
        ))
        return id
    }

    private fun splits(): List<RunSplit> = listOf(
        RunSplit(1, 0.0, 1_000.0, 0, 160_000, start, start + 160_000, false),
        RunSplit(2, 1_000.0, 2_000.0, 160_000, 320_000, start + 160_000, start + 320_000, false),
        RunSplit(3, 2_000.0, 2_500.0, 320_000, 400_000, start + 320_000, start + 400_000, true)
    )

    @Test
    fun completionCreatesPendingOnlyForNewGpsAndLeavesOldAndSampleRunsWithoutSnapshots() = runBlocking {
        val gps = complete()
        val sample = complete(mode = RunTrackingMode.FIXTURE)
        val inherited = UUID.randomUUID().toString()
        database.runDao().insert(running(inherited).copy(
            state = StoredRunState.COMPLETED, finishEpochMillis = start + 410_000,
            lastCheckpointEpochMillis = start + 410_000, finalDistanceMeters = 2_500.0,
            metricSource = MetricSource.GPS, telemetryCoverage = TelemetryCoverage.COMPLETE,
            displayDistanceUnit = DistanceUnit.KILOMETERS
        ))
        val dao = database.splitDao()
        assertEquals(RunSplitSnapshotEntity(gps, RunSplitStatus.PENDING,
            RunSplitCalculator.DERIVATION_VERSION, DistanceUnit.KILOMETERS), dao.snapshotFor(gps))
        assertNull(dao.snapshotFor(sample))
        assertNull(dao.snapshotFor(inherited))
        assertEquals(listOf(gps), dao.pendingRunIds())
        assertTrue(dao.rowsFor(gps).isEmpty())
        assertEquals(listOf(gps), dao.observeAllSnapshots().first().map { it.runId })
        assertFalse(dao.publishIfPending(inherited, splits()))
    }

    @Test
    fun invalidPublicationRollsBackAndCanRetryWithCompleteRows() = runBlocking {
        val id = complete()
        val dao = database.splitDao()
        val invalidRows = listOf(
            emptyList(),
            splits().mapIndexed { index, split -> if (index == 0) split.copy(index = 2) else split },
            splits().mapIndexed { index, split -> if (index == 1) split.copy(startDistanceMeters = 1_001.0) else split },
            splits().mapIndexed { index, split -> if (index == 2) split.copy(endDistanceMeters = 2_400.0) else split },
            splits().mapIndexed { index, split -> if (index == 0) split.copy(endDistanceMeters = Double.NaN) else split },
            splits().mapIndexed { index, split -> if (index == 0) split.copy(isPartial = true) else split },
            splits().mapIndexed { index, split -> if (index == 1) split.copy(startActiveMillis = 160_001) else split },
            splits().mapIndexed { index, split -> if (index == 2) split.copy(endEpochMillis = start + 500_000) else split }
        )
        invalidRows.forEach { invalid ->
            var rejected = false
            try { dao.publishIfPending(id, invalid) } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue("An invalid split set must fail before publishing any rows.", rejected)
            assertEquals(RunSplitStatus.PENDING, dao.snapshotFor(id)?.status)
            assertTrue(dao.rowsFor(id).isEmpty())
        }
        val originalRun = database.runDao().findById(id)
        assertTrue(dao.publishIfPending(id, splits()))
        assertEquals(RunSplitStatus.READY, dao.snapshotFor(id)?.status)
        val rows = dao.rowsFor(id)
        assertEquals(listOf(1, 2, 3), rows.map { it.splitNumber })
        assertEquals(listOf(false, false, true), rows.map { it.isPartial })
        assertEquals(0.16, rows.last().paceSecondsPerMeter, 0.0)
        assertEquals(2_500.0, rows.last().endDistanceMeters, 0.0)
        assertEquals(originalRun, database.runDao().findById(id))
        assertFalse(dao.publishIfPending(id, null))
        assertFalse(dao.publishIfPending(id, splits()))
        assertEquals(rows, dao.rowsFor(id))
        assertTrue(dao.pendingRunIds().isEmpty())
    }

    @Test
    fun unavailableIsSettledOnceAndCannotGainInventedRows() = runBlocking {
        val id = complete(coverage = TelemetryCoverage.UNAVAILABLE)
        val dao = database.splitDao()
        val parent = database.runDao().findById(id)
        assertTrue(dao.publishIfPending(id, null))
        assertEquals(RunSplitStatus.UNAVAILABLE, dao.snapshotFor(id)?.status)
        assertFalse(dao.publishIfPending(id, splits()))
        assertTrue(dao.rowsFor(id).isEmpty())
        assertEquals(parent, database.runDao().findById(id))
    }

    @Test
    fun aMidInsertStorageFailureRollsBackAllRowsAndLeavesTheRunSavedForRetry() = runBlocking {
        val id = complete()
        val parent = database.runDao().findById(id)
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_second_split BEFORE INSERT ON run_splits
            WHEN NEW.split_number = 2 BEGIN
                SELECT RAISE(ABORT, 'Deliberate isolated split write failure');
            END
        """.trimIndent())
        var failed = false
        try { database.splitDao().publishIfPending(id, splits()) }
        catch (_: Exception) { failed = true }
        assertTrue("The test trigger must interrupt publication after the first row.", failed)
        assertEquals(RunSplitStatus.PENDING, database.splitDao().snapshotFor(id)?.status)
        assertTrue(database.splitDao().rowsFor(id).isEmpty())
        assertEquals(parent, database.runDao().findById(id))
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_second_split")
        assertTrue(database.splitDao().publishIfPending(id, splits()))
        assertEquals(3, database.splitDao().rowsFor(id).size)
    }

    @Test
    fun partialGpsAndWrongUnitCannotPublishPreciseSplits() = runBlocking {
        val partial = complete(coverage = TelemetryCoverage.PARTIAL)
        val miles = complete(unit = DistanceUnit.MILES)
        for (id in listOf(partial, miles)) {
            var rejected = false
            try { database.splitDao().publishIfPending(id, splits()) }
            catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(RunSplitStatus.PENDING, database.splitDao().snapshotFor(id)?.status)
            assertTrue(database.splitDao().rowsFor(id).isEmpty())
        }
    }

    @Test
    fun simultaneousPublishersSettleOnlyOneCompleteSnapshot() = runBlocking {
        val id = complete()
        val results = listOf(
            async(Dispatchers.IO) { database.splitDao().publishIfPending(id, splits()) },
            async(Dispatchers.IO) { database.splitDao().publishIfPending(id, splits()) }
        ).awaitAll()
        assertEquals(1, results.count { it })
        assertEquals(3, database.splitDao().rowsFor(id).size)
        assertEquals(RunSplitStatus.READY, database.splitDao().snapshotFor(id)?.status)
    }

    @Test
    fun actualCalculatorOutputPublishesAndFreezesWithoutChangingTheSavedRunOrItsEvidence() = runBlocking {
        val id = UUID.randomUUID().toString()
        val session = UUID.randomUUID().toString()
        val mono = 5_000_000L
        database.runDao().insert(running(id).copy(
            startElapsedRealtimeMillis = mono, timingSessionId = session
        ))
        suspend fun recordInterval(from: Long, to: Long, distance: Double) {
            var offset = from
            var previous: Long? = null
            while (true) {
                val segment = previous?.let { distance * (offset - it).toDouble() / (to - from) }
                val point = database.locationDao().recordObservation(RunLocationObservation(
                    runId = id, observationKey = "calculated-$offset",
                    observedAtEpochMillis = start + offset,
                    elapsedRealtimeNanos = (mono + offset) * 1_000_000L,
                    latitude = 29.7, longitude = -95.4,
                    horizontalAccuracyMeters = 3f, altitudeMeters = null,
                    speedMetersPerSecond = null, bearingDegrees = null,
                    acceptedForDistance = true, segmentDistanceMeters = segment
                ))
                assertTrue("The isolated route must remain accepted by the real location DAO.", point.acceptedForDistance)
                if (offset == to) break
                previous = offset
                offset = minOf(to, offset + 10_000L)
            }
        }
        // First acquisition is one second after each start; no segment bridges the real pause.
        recordInterval(1_000L, 500_000L, 1_000.0)
        database.runDao().pauseRun(id, start + 500_000L, RunTimingMark(mono + 500_000L, session))
        database.runDao().resumeRun(id, start + 560_000L, RunTimingMark(mono + 560_000L, session))
        recordInterval(561_000L, 810_000L, 500.0)
        database.runDao().pauseRun(id, start + 810_000L, RunTimingMark(mono + 810_000L, session))
        val distance = requireNotNull(database.locationDao().latestMeasured(id)?.cumulativeDistanceMeters)
        database.runDao().completeRun(id, start + 820_000L, FinalRunMetrics(
            distance, MetricSource.GPS, TelemetryCoverage.COMPLETE, DistanceUnit.KILOMETERS
        ))
        val parent = requireNotNull(database.runDao().findById(id))
        val transitions = database.runDao().transitionsFor(id)
        val points = database.locationDao().pointsFor(id)
        val calculated = requireNotNull(RunSplitCalculator.calculate(parent, transitions, points))
        assertEquals(2, calculated.size)
        assertEquals(listOf(false, true), calculated.map { it.isPartial })
        assertEquals(750_000L, calculated.sumOf { it.durationMillis })
        assertTrue(database.splitDao().publishIfPending(id, calculated))
        val saved = database.splitDao().rowsFor(id)
        assertEquals(calculated, saved.map { row -> RunSplit(
            row.splitNumber, row.startDistanceMeters, row.endDistanceMeters,
            row.startActiveMillis, row.endActiveMillis, row.startEpochMillis, row.endEpochMillis,
            row.isPartial
        ) })
        calculated.zip(saved).forEach { (derived, row) ->
            assertEquals(derived.paceSecondsPerMeter, row.paceSecondsPerMeter, 0.0)
        }
        assertEquals(RunSplitStatus.READY, database.splitDao().snapshotFor(id)?.status)
        assertFalse(database.splitDao().publishIfPending(id, null))
        assertEquals(saved, database.splitDao().rowsFor(id))
        assertEquals(parent, database.runDao().findById(id))
        assertEquals(transitions, database.runDao().transitionsFor(id))
        assertEquals(points, database.locationDao().pointsFor(id))
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun deletingOneParentCascadesItsSnapshotAndRowsOnly() = runBlocking {
        val removed = complete()
        val retained = complete()
        assertTrue(database.splitDao().publishIfPending(removed, splits()))
        assertTrue(database.splitDao().publishIfPending(retained, splits()))
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs WHERE run_id = ?", arrayOf(removed))
        assertNull(database.splitDao().snapshotFor(removed))
        assertTrue(database.splitDao().rowsFor(removed).isEmpty())
        assertEquals(3, database.splitDao().rowsFor(retained).size)
        assertEquals(RunSplitStatus.READY, database.splitDao().snapshotFor(retained)?.status)
        assertEquals(1, database.runDao().countRuns())
    }

    @Test
    fun pendingAndReadySnapshotsSurviveDatabaseReopen() = runBlocking {
        val name = "runstate-splits-${UUID.randomUUID()}.db"
        var fileDatabase: RunStateDatabase? = null
        try {
            fileDatabase = buildRunStateDatabase(context, name)
            val id = UUID.randomUUID().toString()
            fileDatabase.runDao().insert(running(id))
            fileDatabase.runDao().pauseRun(id, start + 400_000)
            fileDatabase.runDao().completeRun(id, start + 410_000, FinalRunMetrics(
                2_500.0, MetricSource.GPS, TelemetryCoverage.COMPLETE, DistanceUnit.KILOMETERS))
            fileDatabase.close()
            fileDatabase = buildRunStateDatabase(context, name)
            assertEquals(listOf(id), fileDatabase.splitDao().pendingRunIds())
            assertTrue(fileDatabase.splitDao().publishIfPending(id, splits()))
            val before = fileDatabase.splitDao().rowsFor(id)
            fileDatabase.close()
            fileDatabase = buildRunStateDatabase(context, name)
            assertEquals(RunSplitStatus.READY, fileDatabase.splitDao().snapshotFor(id)?.status)
            assertEquals(before, fileDatabase.splitDao().rowsFor(id))
        } finally {
            fileDatabase?.close()
            context.deleteDatabase(name)
        }
    }
}
