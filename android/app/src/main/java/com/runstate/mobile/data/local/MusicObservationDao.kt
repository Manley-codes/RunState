package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.runstate.mobile.music.observedSongPlaybackEligible

/** Run-owned, ordered evidence from Android media sessions. No row means unknown, not silence. */
@Dao
abstract class MusicObservationDao {
    @Query("SELECT * FROM run_song_preparations WHERE run_id = :runId ORDER BY observation_sequence_number, claimed_at_epoch_millis, preparation_id LIMIT 1")
    abstract suspend fun preparationFor(runId: String): SongPreparationClaimEntity?

    @Query("SELECT * FROM run_song_preparations WHERE run_id = :runId ORDER BY observation_sequence_number, claimed_at_epoch_millis, preparation_id")
    abstract suspend fun preparationsFor(runId: String): List<SongPreparationClaimEntity>

    /** Claim is committed before HTTP. Reopen/retry never returns an existing claim as new work. */
    @Transaction
    open suspend fun claimPreparation(
        runId: String,
        preparationId: String,
        observationSequenceNumber: Int,
        destinationFingerprint: String,
        nowEpochMillis: Long
    ): SongPreparationClaimEntity? {
        val previous = preparationsFor(runId)
        if (previous.size >= MAX_SONG_PREPARATIONS_PER_RUN ||
            previous.any { it.destinationFingerprint != destinationFingerprint }) return null
        val run = findRun(runId) ?: return null
        if (run.state != StoredRunState.RUNNING || !run.musicObservationRequested ||
            !run.musicReflectionSharingAllowed || run.transitionHistoryComplete != true ||
            nowEpochMillis < run.officialStartEpochMillis) return null
        val claimedIdentities = previous.mapNotNull { songPreparationIdentity(it.trackTitle, it.artistName) }.toSet()
        val first = observedSongPlaybackEligible(
            runId, run.officialStartEpochMillis, nowEpochMillis, observationsFor(runId)
        ).firstOrNull {
            songPreparationIdentity(it.title, it.artist)?.let { identity -> identity !in claimedIdentities } == true
        } ?: return null
        if (first.sequenceNumber != observationSequenceNumber) return null
        val row = SongPreparationClaimEntity(
            runId, preparationId, requireNotNull(first.title).trim(), requireNotNull(first.artist).trim(),
            first.sequenceNumber, destinationFingerprint, nowEpochMillis
        )
        return if (insertPreparation(row) == -1L) null else row
    }

    /** A late response can settle its own claim only; it cannot create or replace another attempt. */
    open suspend fun finishPreparation(runId: String, preparationId: String, ready: Boolean) {
        finishClaimedPreparation(runId, preparationId,
            if (ready) SongPreparationClaimStatus.READY else SongPreparationClaimStatus.UNAVAILABLE)
    }

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

        // The observer may race a durable Pause/Resume. Credit can belong only to the
        // currently active interval, never to a paused interval or an earlier owner.
        val interval = observation.playbackIntervalMillis?.let { measured ->
            val latest = transitionsForPreparation(observation.runId).lastOrNull()
            if (run.transitionHistoryComplete != true || latest?.transitionType == RunTransitionType.PAUSE) 0L
            else {
                val activeStart = maxOf(run.officialStartEpochMillis,
                    latest?.occurredAtEpochMillis ?: run.officialStartEpochMillis)
                minOf(measured, (observation.observedAtEpochMillis - activeStart).coerceAtLeast(0L))
            }
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
            eventKind = observation.eventKind,
            spotifyTrackUri = observation.spotifyTrackUri,
            albumName = observation.albumName,
            trackDurationMillis = observation.trackDurationMillis,
            playbackIntervalMillis = interval
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

    @Query("SELECT * FROM run_transitions WHERE run_id = :runId ORDER BY sequence_number ASC")
    protected abstract suspend fun transitionsForPreparation(runId: String): List<RunTransitionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPreparation(row: SongPreparationClaimEntity): Long

    @Query("""
        UPDATE run_song_preparations SET status = :status
        WHERE run_id = :runId AND preparation_id = :preparationId AND status = 'CLAIMED'
    """)
    protected abstract suspend fun finishClaimedPreparation(
        runId: String,
        preparationId: String,
        status: SongPreparationClaimStatus
    ): Int
}
