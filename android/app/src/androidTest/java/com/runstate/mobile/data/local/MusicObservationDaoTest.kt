package com.runstate.mobile.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicObservationDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val run = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = 1_756_000_000_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_756_000_000_000L,
        transitionHistoryComplete = true,
        musicObservationRequested = true
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

    private fun observation(key: String, offsetMillis: Long) = RunMusicObservation(
        runId = run.runId,
        observationKey = key,
        observedAtEpochMillis = run.officialStartEpochMillis + offsetMillis,
        elapsedRealtimeNanos = offsetMillis * 1_000_000,
        sourcePackage = "com.example.player",
        title = "Observed title",
        artist = "Observed artist",
        playbackState = MusicPlaybackState.PLAYING,
        eventKind = MusicObservationKind.SESSION_SNAPSHOT
    )

    @Test
    fun optedInObservationIsOrderedRetrySafeAndSurvivesReopen() = runBlocking {
        database.runDao().insert(run)
        val first = database.musicObservationDao().recordObservation(observation("event-1", 1_000))
        val second = database.musicObservationDao().recordObservation(observation("event-2", 2_000))
        assertEquals(1, first.sequenceNumber)
        assertEquals(2, second.sequenceNumber)

        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 3_000)
        // A retry of an already-committed event remains safe after the run pauses.
        assertEquals(first, database.musicObservationDao().recordObservation(observation("event-1", 9_000)))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)

        assertEquals(listOf(first, second), database.musicObservationDao().observationsFor(run.runId))
    }

    @Test
    fun noOptInOrPausedParentCannotAcquireNewEvidence() = runBlocking {
        database.runDao().insert(run.copy(musicObservationRequested = false))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.musicObservationDao().recordObservation(observation("off", 1_000)) }
        }
        assertTrue(database.musicObservationDao().observationsFor(run.runId).isEmpty())

        // A distinct opted-in run may start; after its durable pause, callbacks are rejected.
        val optedIn = run.copy(runId = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7")
        database.runDao().insert(optedIn)
        database.runDao().pauseRun(optedIn.runId, optedIn.officialStartEpochMillis + 2_000)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.musicObservationDao().recordObservation(
                    observation("paused", 3_000).copy(runId = optedIn.runId)
                )
            }
        }
        assertTrue(database.musicObservationDao().observationsFor(optedIn.runId).isEmpty())
    }

    private companion object {
        const val DATABASE_NAME = "runstate-music-observation-test.db"
    }
}
