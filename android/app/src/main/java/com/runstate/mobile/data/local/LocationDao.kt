package com.runstate.mobile.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/** Durable, retry-safe observation stream for runs that explicitly use GPS. */
@Dao
abstract class LocationDao {
    /**
     * A retry returns the first stored decision without adding sequence or distance.
     * A fresh observation and its cumulative distance are committed in one transaction.
     * The run's current stored state, not a screen's state, decides whether it can count.
     */
    @Transaction
    open suspend fun recordObservation(
        observation: RunLocationObservation,
        currentTimingSessionId: String? = null
    ): RunLocationPointEntity {
        findByObservationKey(observation.runId, observation.observationKey)?.let { return it }

        val run = requireNotNull(findRun(observation.runId)) {
            "No run is stored under ${observation.runId}."
        }
        check(run.trackingMode == RunTrackingMode.GPS) {
            "Run ${observation.runId} is not a GPS run."
        }
        check(run.state != StoredRunState.COMPLETED) {
            "A completed run cannot receive new location observations."
        }

        val previous = latestAccepted(observation.runId)
        val previousMeasuredDistance = latestMeasured(observation.runId)?.cumulativeDistanceMeters
        // GPS wall time can differ slightly from the phone's clock. Compare source time
        // with the saved lifecycle clock only when this collector belongs to that same
        // process. Recovered/legacy observations retain the conservative wall fallback;
        // elapsed realtime from a prior process or boot must never gain new authority.
        val latestTransition = latestTransition(observation.runId)
        val checkpointMono = if (currentTimingSessionId != null &&
            currentTimingSessionId == run.timingSessionId &&
            observation.elapsedRealtimeNanos != null &&
            (previous == null || previous.elapsedRealtimeNanos != null)
        ) {
            if (latestTransition == null) {
                run.startElapsedRealtimeMillis.takeIf {
                    run.lastCheckpointEpochMillis == run.officialStartEpochMillis
                }
            } else latestTransition.elapsedRealtimeMillis.takeIf {
                latestTransition.transitionType == RunTransitionType.RESUME &&
                    latestTransition.timingSessionId == currentTimingSessionId &&
                    latestTransition.occurredAtEpochMillis == run.lastCheckpointEpochMillis
            }
        } else null
        val beforeCheckpoint = if (checkpointMono != null) {
            compareNanosToMillis(checkNotNull(observation.elapsedRealtimeNanos), checkpointMono) < 0
        } else observation.observedAtEpochMillis < run.lastCheckpointEpochMillis
        val outOfOrder = if (checkpointMono != null) {
            previous?.elapsedRealtimeNanos?.let {
                checkNotNull(observation.elapsedRealtimeNanos) <= it
            } == true
        } else observation.observedAtEpochMillis < previousOrStart(previous, run)
        val previousBeforeCheckpoint = previous?.let {
            if (checkpointMono != null) {
                compareNanosToMillis(checkNotNull(it.elapsedRealtimeNanos), checkpointMono) <= 0
            } else it.observedAtEpochMillis <= run.lastCheckpointEpochMillis
        } == true
        val rejection = when {
            run.state != StoredRunState.RUNNING -> "NOT_RUNNING"
            beforeCheckpoint -> "BEFORE_CHECKPOINT"
            outOfOrder -> "OUT_OF_ORDER"
            // A rapidly paused/resumed run can skip the PAUSED Flow emission in the
            // service. Durable storage still refuses a segment reaching back across
            // that checkpoint; the next fix must become a new anchor.
            observation.acceptedForDistance && observation.segmentDistanceMeters != null &&
                previousBeforeCheckpoint ->
                "CROSSES_CHECKPOINT"
            observation.acceptedForDistance -> null
            else -> observation.rejectionReason
        }
        val accepted = rejection == null
        val segment = if (accepted) observation.segmentDistanceMeters else null
        check(!accepted || previous != null || segment == null) {
            "The first accepted GPS point is an anchor, not a measured segment."
        }
        val cumulative = if (accepted) {
            if (segment == null) previousMeasuredDistance else {
                val total = (previousMeasuredDistance ?: 0.0) + segment
                require(total.isFinite() && total >= 0.0) { "Cumulative GPS distance is invalid." }
                total
            }
        } else {
            null
        }

        val point = RunLocationPointEntity(
            runId = observation.runId,
            sequenceNumber = (highestSequenceNumber(observation.runId) ?: 0) + 1,
            observationKey = observation.observationKey,
            observedAtEpochMillis = observation.observedAtEpochMillis,
            elapsedRealtimeNanos = observation.elapsedRealtimeNanos,
            latitude = observation.latitude,
            longitude = observation.longitude,
            horizontalAccuracyMeters = observation.horizontalAccuracyMeters,
            altitudeMeters = observation.altitudeMeters,
            speedMetersPerSecond = observation.speedMetersPerSecond,
            bearingDegrees = observation.bearingDegrees,
            acceptedForDistance = accepted,
            rejectionReason = rejection,
            segmentDistanceMeters = segment,
            cumulativeDistanceMeters = cumulative
        )
        insert(point)
        return point
    }

    private fun previousOrStart(previous: RunLocationPointEntity?, run: RunEntity): Long =
        previous?.observedAtEpochMillis ?: run.officialStartEpochMillis

    /** Preserve sub-millisecond ordering without multiplying a possibly large saved clock. */
    private fun compareNanosToMillis(nanos: Long, millis: Long): Int {
        val wholeMillis = (nanos / 1_000_000L).compareTo(millis)
        return if (wholeMillis != 0) wholeMillis else (nanos % 1_000_000L).compareTo(0L)
    }

    /** Every observation, including rejected ones, in its durable arrival order. */
    @Query("SELECT * FROM run_location_points WHERE run_id = :runId ORDER BY sequence_number ASC")
    abstract suspend fun pointsFor(runId: String): List<RunLocationPointEntity>

    /** Append-only live evidence: a UI tick reads new observations instead of the whole route. */
    @Query("SELECT * FROM run_location_points WHERE run_id = :runId AND sequence_number > :afterSequence ORDER BY sequence_number ASC")
    abstract suspend fun pointsAfter(runId: String, afterSequence: Int): List<RunLocationPointEntity>

    /** The final accepted cumulative value is the distance confirmed by durable points. */
    @Query("SELECT * FROM run_location_points WHERE run_id = :runId AND accepted_for_distance = 1 ORDER BY sequence_number DESC LIMIT 1")
    abstract suspend fun latestAccepted(runId: String): RunLocationPointEntity?

    /** Null means no accepted segment has measured distance yet. */
    @Query("SELECT * FROM run_location_points WHERE run_id = :runId AND accepted_for_distance = 1 AND cumulative_distance_meters IS NOT NULL ORDER BY sequence_number DESC LIMIT 1")
    abstract suspend fun latestMeasured(runId: String): RunLocationPointEntity?

    @Query("SELECT COUNT(*) FROM run_location_points WHERE run_id = :runId AND accepted_for_distance = 1")
    abstract suspend fun acceptedCount(runId: String): Int

    @Query("SELECT * FROM run_location_points WHERE run_id = :runId AND observation_key = :observationKey LIMIT 1")
    protected abstract suspend fun findByObservationKey(runId: String, observationKey: String): RunLocationPointEntity?

    @Query("SELECT * FROM runs WHERE run_id = :runId")
    protected abstract suspend fun findRun(runId: String): RunEntity?

    @Query("SELECT * FROM run_transitions WHERE run_id = :runId ORDER BY sequence_number DESC LIMIT 1")
    protected abstract suspend fun latestTransition(runId: String): RunTransitionEntity?

    @Query("SELECT MAX(sequence_number) FROM run_location_points WHERE run_id = :runId")
    protected abstract suspend fun highestSequenceNumber(runId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(point: RunLocationPointEntity)
}
