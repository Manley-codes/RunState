package com.runstate.mobile.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val run = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = 1_756_000_000_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_756_000_000_000L,
        transitionHistoryComplete = true,
        trackingMode = RunTrackingMode.GPS
    )

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

    private fun point(
        key: String,
        offsetMillis: Long,
        segmentMeters: Double? = null
    ) = RunLocationObservation(
        runId = run.runId,
        observationKey = key,
        observedAtEpochMillis = run.officialStartEpochMillis + offsetMillis,
        elapsedRealtimeNanos = offsetMillis * 1_000_000,
        latitude = 29.7604,
        longitude = -95.3698,
        horizontalAccuracyMeters = 4f,
        altitudeMeters = null,
        speedMetersPerSecond = null,
        bearingDegrees = null,
        acceptedForDistance = true,
        segmentDistanceMeters = segmentMeters
    )

    @Test
    fun acceptedDistanceAndDuplicateDecisionSurviveReopen() = runBlocking {
        database.runDao().insert(run)
        val dao = database.locationDao()
        val anchor = dao.recordObservation(point("fix-1", 1_000))
        val measured = dao.recordObservation(point("fix-2", 6_000, 25.5))
        assertEquals(1, anchor.sequenceNumber)
        assertEquals(2, measured.sequenceNumber)
        assertNull(anchor.cumulativeDistanceMeters)
        assertEquals(25.5, measured.cumulativeDistanceMeters!!, 0.0)

        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 7_000)
        assertEquals(measured, dao.recordObservation(point("fix-2", 6_000, 999.0)))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)

        val restored = database.locationDao()
        assertEquals(listOf(anchor, measured), restored.pointsFor(run.runId))
        assertEquals(measured, restored.latestAccepted(run.runId))
        assertEquals(measured, restored.latestMeasured(run.runId))
        assertEquals(2, restored.acceptedCount(run.runId))
    }

    @Test
    fun pausedAndOutOfOrderObservationsStayAuditableButAddNoDistance() = runBlocking {
        database.runDao().insert(run)
        val dao = database.locationDao()
        dao.recordObservation(point("fix-1", 5_000))
        val stale = dao.recordObservation(point("late-fix", 4_000, 25.0))
        assertEquals("OUT_OF_ORDER", stale.rejectionReason)
        assertEquals(false, stale.acceptedForDistance)
        assertNull(stale.cumulativeDistanceMeters)

        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 6_000)
        val paused = dao.recordObservation(point("paused-fix", 7_000, 25.0))
        assertEquals("NOT_RUNNING", paused.rejectionReason)
        assertEquals(false, paused.acceptedForDistance)
        assertNull(paused.segmentDistanceMeters)
        assertEquals(1, dao.acceptedCount(run.runId))
        assertNull(dao.latestAccepted(run.runId)!!.cumulativeDistanceMeters)
        assertNull(dao.latestMeasured(run.runId))
    }

    @Test
    fun resumedRunRequiresFreshAnchorBeforeAnotherMeasuredSegment() = runBlocking {
        database.runDao().insert(run)
        val dao = database.locationDao()
        dao.recordObservation(point("before-pause-anchor", 1_000))
        val beforePause = dao.recordObservation(point("before-pause-segment", 3_000, 25.0))

        // The service could miss a pause/resume callback even though both transitions
        // reached Room. Storage must not join those two separate running intervals.
        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 4_000)
        database.runDao().resumeRun(run.runId, run.officialStartEpochMillis + 5_000)
        val crossingPause = dao.recordObservation(point("crossing-pause", 6_000, 100.0))

        assertEquals(false, crossingPause.acceptedForDistance)
        assertEquals("CROSSES_CHECKPOINT", crossingPause.rejectionReason)
        assertNull(crossingPause.segmentDistanceMeters)
        assertEquals(beforePause, dao.latestMeasured(run.runId))

        val resumedAnchor = dao.recordObservation(point("resumed-anchor", 7_000))
        val resumedSegment = dao.recordObservation(point("resumed-segment", 8_000, 30.0))
        assertEquals(true, resumedAnchor.acceptedForDistance)
        assertNull(resumedAnchor.segmentDistanceMeters)
        assertEquals(55.0, resumedSegment.cumulativeDistanceMeters!!, 0.0)
        assertEquals(4, dao.acceptedCount(run.runId))
    }

    @Test
    fun fixtureAndLegacyRunsCannotAcquireGpsObservations() = runBlocking {
        for (mode in listOf(RunTrackingMode.FIXTURE, null)) {
            database.runDao().insert(run.copy(
                runId = if (mode == null) "6d052916-3476-483b-a5fd-bc759f47cd87" else run.runId,
                trackingMode = mode
            ))
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.locationDao().recordObservation(point("fixture", 1_000)) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.locationDao().recordObservation(
                    point("legacy", 1_000).copy(runId = "6d052916-3476-483b-a5fd-bc759f47cd87")
                )
            }
        }
        assertTrue(database.locationDao().pointsFor(run.runId).isEmpty())
    }

    @Test
    fun invalidCumulativeValueRollsBackAndCompletionRefusesNewSamples() = runBlocking {
        database.runDao().insert(run)
        val dao = database.locationDao()
        dao.recordObservation(point("anchor", 1_000))
        dao.recordObservation(point("huge", 2_000, Double.MAX_VALUE))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { dao.recordObservation(point("overflow", 3_000, Double.MAX_VALUE)) }
        }
        assertEquals(2, dao.pointsFor(run.runId).size)

        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 4_000)
        database.runDao().completeRun(
            run.runId,
            run.officialStartEpochMillis + 5_000,
            FinalRunMetrics(1.0, MetricSource.GPS, TelemetryCoverage.PARTIAL, DistanceUnit.MILES)
        )
        assertThrows(IllegalStateException::class.java) {
            runBlocking { dao.recordObservation(point("after-finish", 6_000)) }
        }
        assertEquals(2, dao.pointsFor(run.runId).size)
    }

    @Test
    fun deletingParentCascadesToLocationPoints() = runBlocking {
        database.runDao().insert(run)
        database.locationDao().recordObservation(point("anchor", 1_000))
        database.openHelper.writableDatabase.execSQL("DELETE FROM runs")
        assertTrue(database.locationDao().pointsFor(run.runId).isEmpty())
    }

    @Test
    fun firstPointCannotInventDistanceWithoutAnAnchor() = runBlocking {
        database.runDao().insert(run)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.locationDao().recordObservation(point("unanchored", 1_000, 30.0)) }
        }
        assertTrue(database.locationDao().pointsFor(run.runId).isEmpty())
        assertNull(database.locationDao().latestMeasured(run.runId))
    }

    private companion object {
        const val DATABASE_NAME = "runstate-location-dao-test.db"
    }
}
