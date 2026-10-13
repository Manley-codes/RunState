package com.runstate.mobile.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One platform location fix. The elapsed-realtime value is valid only within the current boot.
 * The epoch value is retained for later durable storage, not used to judge fix freshness.
 */
internal data class GpsSample(
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val horizontalAccuracyMeters: Double?,
    val observedEpochMillis: Long,
    val elapsedRealtimeMillis: Long
)

/** Conservative starting thresholds; outdoor phone testing must tune these values. */
internal data class GpsDistanceConfig(
    val maxHorizontalAccuracyMeters: Double = 25.0,
    val maxSampleAgeMillis: Long = 10_000L,
    val maxPointIntervalMillis: Long = 15_000L,
    val maxPlausibleSpeedMetersPerSecond: Double = 9.0
) {
    init {
        require(maxHorizontalAccuracyMeters.isFinite() && maxHorizontalAccuracyMeters > 0.0)
        require(maxSampleAgeMillis > 0L)
        require(maxPointIntervalMillis > 0L)
        require(
            maxPlausibleSpeedMetersPerSecond.isFinite() &&
                maxPlausibleSpeedMetersPerSecond > 0.0
        )
    }
}

internal enum class GpsSampleStatus {
    ACCEPTED_ANCHOR,
    ACCEPTED_SEGMENT,
    IGNORED_WITHIN_ACCURACY,
    REJECTED_NOT_RUNNING,
    REJECTED_INVALID,
    REJECTED_INACCURATE,
    REJECTED_STALE,
    REJECTED_OUT_OF_ORDER,
    REJECTED_IMPLAUSIBLE_JUMP
}

internal data class GpsDecision(
    val status: GpsSampleStatus,
    /** Whether this point may be retained as part of the route, including an anchor. */
    val acceptedForRoute: Boolean,
    /** Non-null only when this fix added a trustworthy segment. */
    val distanceAddedMeters: Double?,
    /** Null means no trustworthy segment has been observed, not a zero-distance run. */
    val totalDistanceMeters: Double?,
    val hadUnobservedGap: Boolean
)

/**
 * Accepts only fresh, accurate, plausible Running fixes and totals observed segments.
 *
 * The caller must invoke [pause] when the run pauses and [trackingInterrupted] whenever location
 * collection becomes uncertain (including service recovery). Neither action bridges the next fix
 * to an old point. This class has no Android dependency and does not persist anything by itself.
 */
internal class GpsDistancePolicy(
    private val config: GpsDistanceConfig = GpsDistanceConfig()
) {
    private var anchor: GpsSample? = null
    private var lastSeenElapsedRealtimeMillis: Long? = null
    private var measuredDistanceMeters: Double? = null
    private var firstFixAfterPause = false

    var hadUnobservedGap: Boolean = false
        private set

    val distanceMeters: Double?
        get() = measuredDistanceMeters

    fun pause() {
        anchor = null
        firstFixAfterPause = true
    }

    fun trackingInterrupted() {
        anchor = null
        firstFixAfterPause = false
        hadUnobservedGap = true
    }

    fun observe(
        sample: GpsSample,
        nowElapsedRealtimeMillis: Long,
        isRunning: Boolean
    ): GpsDecision {
        require(nowElapsedRealtimeMillis >= 0L) { "Current elapsed realtime cannot be negative." }

        if (!isRunning) {
            pause()
            return decision(GpsSampleStatus.REJECTED_NOT_RUNNING)
        }

        if (
            !sample.latitudeDegrees.isFinite() || sample.latitudeDegrees !in -90.0..90.0 ||
            !sample.longitudeDegrees.isFinite() || sample.longitudeDegrees !in -180.0..180.0 ||
            sample.observedEpochMillis <= 0L || sample.elapsedRealtimeMillis < 0L ||
            sample.elapsedRealtimeMillis > nowElapsedRealtimeMillis
        ) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_INVALID)
        }

        val previousSeen = lastSeenElapsedRealtimeMillis
        if (previousSeen != null && sample.elapsedRealtimeMillis <= previousSeen) {
            // Duplicate and delayed delivery add no distance, but do not spoil a good current anchor.
            return decision(GpsSampleStatus.REJECTED_OUT_OF_ORDER)
        }

        if (nowElapsedRealtimeMillis - sample.elapsedRealtimeMillis > config.maxSampleAgeMillis) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_STALE)
        }

        if (
            previousSeen != null &&
            sample.elapsedRealtimeMillis - previousSeen > config.maxPointIntervalMillis &&
            !firstFixAfterPause
        ) {
            // This point can begin a new measured section, but not close the unknown interval.
            trackingInterrupted()
        }
        lastSeenElapsedRealtimeMillis = sample.elapsedRealtimeMillis

        val accuracy = sample.horizontalAccuracyMeters
        if (
            accuracy == null || !accuracy.isFinite() || accuracy <= 0.0 ||
            accuracy > config.maxHorizontalAccuracyMeters
        ) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_INACCURATE)
        }

        val previousAnchor = anchor
        if (previousAnchor == null) {
            anchor = sample
            firstFixAfterPause = false
            return decision(GpsSampleStatus.ACCEPTED_ANCHOR, acceptedForRoute = true)
        }

        val segmentMeters = greatCircleMeters(previousAnchor, sample)
        val durationSeconds =
            (sample.elapsedRealtimeMillis - previousAnchor.elapsedRealtimeMillis) / 1_000.0
        if (!segmentMeters.isFinite() || durationSeconds <= 0.0) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_INVALID)
        }

        // Small movement cannot be distinguished from the reported uncertainty. Retain the
        // earlier anchor so genuine movement can eventually exceed the uncertainty envelope.
        val uncertaintyMeters =
            previousAnchor.horizontalAccuracyMeters!! + accuracy
        if (segmentMeters <= uncertaintyMeters) {
            return decision(GpsSampleStatus.IGNORED_WITHIN_ACCURACY)
        }

        if (segmentMeters / durationSeconds > config.maxPlausibleSpeedMetersPerSecond) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_IMPLAUSIBLE_JUMP)
        }

        val newDistance = (measuredDistanceMeters ?: 0.0) + segmentMeters
        if (!newDistance.isFinite()) {
            trackingInterrupted()
            return decision(GpsSampleStatus.REJECTED_INVALID)
        }
        measuredDistanceMeters = newDistance
        anchor = sample
        return decision(
            GpsSampleStatus.ACCEPTED_SEGMENT,
            acceptedForRoute = true,
            distanceAddedMeters = segmentMeters
        )
    }

    private fun decision(
        status: GpsSampleStatus,
        acceptedForRoute: Boolean = false,
        distanceAddedMeters: Double? = null
    ): GpsDecision = GpsDecision(
        status = status,
        acceptedForRoute = acceptedForRoute,
        distanceAddedMeters = distanceAddedMeters,
        totalDistanceMeters = measuredDistanceMeters,
        hadUnobservedGap = hadUnobservedGap
    )
}

private fun greatCircleMeters(from: GpsSample, to: GpsSample): Double {
    val fromLatitude = Math.toRadians(from.latitudeDegrees)
    val toLatitude = Math.toRadians(to.latitudeDegrees)
    val latitudeDifference = toLatitude - fromLatitude
    val longitudeDifference = Math.toRadians(to.longitudeDegrees - from.longitudeDegrees)
    val halfChord =
        sin(latitudeDifference / 2.0).let { it * it } +
            cos(fromLatitude) * cos(toLatitude) *
            sin(longitudeDifference / 2.0).let { it * it }
    val boundedChord = halfChord.coerceIn(0.0, 1.0)
    return 6_371_000.0 * 2.0 * atan2(sqrt(boundedChord), sqrt(max(0.0, 1.0 - boundedChord)))
}
