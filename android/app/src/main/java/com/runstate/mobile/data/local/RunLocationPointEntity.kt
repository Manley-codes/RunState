package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * A platform location observation and the decision made when it first reached storage.
 * Rejected observations remain visible for later diagnosis but never add distance.
 * A cumulative value on each accepted point makes recovery independent of process memory.
 */
@Entity(
    tableName = "run_location_points",
    primaryKeys = ["run_id", "sequence_number"],
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["run_id"]),
        Index(value = ["run_id", "observation_key"], unique = true)
    ]
)
data class RunLocationPointEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Int,
    @ColumnInfo(name = "observation_key") val observationKey: String,
    @ColumnInfo(name = "observed_at_epoch_millis") val observedAtEpochMillis: Long,
    /** Since-boot time is only comparable within a single boot. */
    @ColumnInfo(name = "elapsed_realtime_nanos") val elapsedRealtimeNanos: Long?,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "horizontal_accuracy_meters") val horizontalAccuracyMeters: Float?,
    @ColumnInfo(name = "altitude_meters") val altitudeMeters: Double?,
    @ColumnInfo(name = "speed_meters_per_second") val speedMetersPerSecond: Float?,
    @ColumnInfo(name = "bearing_degrees") val bearingDegrees: Float?,
    @ColumnInfo(name = "accepted_for_distance") val acceptedForDistance: Boolean,
    @ColumnInfo(name = "rejection_reason") val rejectionReason: String?,
    @ColumnInfo(name = "segment_distance_meters") val segmentDistanceMeters: Double?,
    @ColumnInfo(name = "cumulative_distance_meters") val cumulativeDistanceMeters: Double?
) {
    init {
        require(sequenceNumber >= 1) { "Location sequence must begin at 1." }
        validateLocationFields(
            observationKey, elapsedRealtimeNanos, latitude, longitude,
            horizontalAccuracyMeters, altitudeMeters, speedMetersPerSecond, bearingDegrees
        )
        if (acceptedForDistance) {
            require(rejectionReason == null) { "An accepted point cannot have a rejection reason." }
            require(segmentDistanceMeters == null || (segmentDistanceMeters.isFinite() && segmentDistanceMeters >= 0.0)) {
                "An accepted segment distance must be finite and non-negative."
            }
            require(cumulativeDistanceMeters == null || (cumulativeDistanceMeters.isFinite() && cumulativeDistanceMeters >= 0.0)) {
                "An accepted cumulative distance must be finite and non-negative."
            }
            require(segmentDistanceMeters == null || cumulativeDistanceMeters != null) {
                "A measured segment needs a cumulative distance."
            }
        } else {
            require(!rejectionReason.isNullOrBlank()) { "A rejected point needs a reason." }
            require(segmentDistanceMeters == null && cumulativeDistanceMeters == null) {
                "A rejected point cannot contribute distance."
            }
        }
    }
}

/** Decision and raw facts supplied by the foreground location collector. */
data class RunLocationObservation(
    val runId: String,
    /** Stable for retries of one platform observation, and unique within the run. */
    val observationKey: String,
    val observedAtEpochMillis: Long,
    val elapsedRealtimeNanos: Long?,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float?,
    val altitudeMeters: Double?,
    val speedMetersPerSecond: Float?,
    val bearingDegrees: Float?,
    val acceptedForDistance: Boolean,
    val rejectionReason: String? = null,
    /** Null for an anchor without a measured segment; non-negative for a measured segment. */
    val segmentDistanceMeters: Double? = null
) {
    init {
        require(observedAtEpochMillis >= 0L) { "Observation time cannot be negative." }
        validateLocationFields(
            observationKey, elapsedRealtimeNanos, latitude, longitude,
            horizontalAccuracyMeters, altitudeMeters, speedMetersPerSecond, bearingDegrees
        )
        if (acceptedForDistance) {
            require(rejectionReason == null) { "An accepted point cannot have a rejection reason." }
            require(segmentDistanceMeters == null || (segmentDistanceMeters.isFinite() && segmentDistanceMeters >= 0.0)) {
                "An accepted segment distance must be finite and non-negative."
            }
        } else {
            require(!rejectionReason.isNullOrBlank()) { "A rejected point needs a reason." }
            require(segmentDistanceMeters == null) { "A rejected point cannot add distance." }
        }
    }
}

private fun validateLocationFields(
    key: String,
    elapsedRealtimeNanos: Long?,
    latitude: Double,
    longitude: Double,
    accuracy: Float?,
    altitude: Double?,
    speed: Float?,
    bearing: Float?
) {
    require(key.isNotBlank()) { "An observation needs a stable key." }
    require(elapsedRealtimeNanos == null || elapsedRealtimeNanos >= 0L) {
        "Since-boot observation time cannot be negative."
    }
    require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude is invalid." }
    require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude is invalid." }
    require(accuracy == null || (accuracy.isFinite() && accuracy >= 0f)) { "Accuracy is invalid." }
    require(altitude == null || altitude.isFinite()) { "Altitude is invalid." }
    require(speed == null || (speed.isFinite() && speed >= 0f)) { "Speed is invalid." }
    require(bearing == null || (bearing.isFinite() && bearing >= 0f && bearing < 360f)) {
        "Bearing is invalid."
    }
}
