package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Run-owned, ordered evidence from Android media sessions. No row means unknown, not silence. */
@Dao
abstract class MusicObservationDao {
    /** A retry preserves the first event; a new event requires an opted-in RUNNING parent. */
    @Transaction
    open suspend fun recordObservation(observation: RunMusicObservation): RunMusicObservationEntity {
        findByObservationKey(observation.runId, observation.observationKey)?.let { return it }

        val run = requireNotNull(findRun(observation.runId)) {
            "No run is stored under ${observation.runId}."
        }
        check(run.musicObservationRequested) {
            "Run ${observation.runId} did not opt in to music observation."
        }
        check(run.state == StoredRunState.RUNNING) {
            "Music observations belong only to a RUNNING run."
        }

        val row = RunMusicObservationEntity(
            runId = observation.runId,
            sequenceNumber = (highestSequenceNumber(observation.runId) ?: 0) + 1,
            observationKey = observation.observationKey,
            observedAtEpochMillis = observation.observedAtEpochMillis,
            elapsedRealtimeNanos = observation.elapsedRealtimeNanos,
            sourcePackage = observation.sourcePackage,
            title = observation.title,
            artist = observation.artist,
            playbackState = observation.playbackState,
            eventKind = observation.eventKind
        )
        insert(row)
        return row
    }

    @Query("SELECT * FROM run_music_observations WHERE run_id = :runId ORDER BY sequence_number ASC")
    abstract suspend fun observationsFor(runId: String): List<RunMusicObservationEntity>

    @Query("SELECT * FROM run_music_observations WHERE run_id = :runId AND observation_key = :key LIMIT 1")
    protected abstract suspend fun findByObservationKey(runId: String, key: String): RunMusicObservationEntity?

    @Query("SELECT * FROM runs WHERE run_id = :runId")
    protected abstract suspend fun findRun(runId: String): RunEntity?

    @Query("SELECT MAX(sequence_number) FROM run_music_observations WHERE run_id = :runId")
    protected abstract suspend fun highestSequenceNumber(runId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(row: RunMusicObservationEntity)
}
