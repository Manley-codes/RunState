package com.runstate.mobile.run

/**
 * Deterministic distance for the Phase 3 fixture journey.
 *
 * This is intentionally not a production `RunDistanceSource`. Real GPS distance will be
 * accumulated from accepted location observations rather than inferred from active time.
 * The fixture has one smaller job: give identical active timelines identical controlled
 * distances while the UI and persistence journey are being built.
 */
internal class FixtureDistanceCalculator(
    private val metersPerSecond: Double = 3.0
) {
    init {
        require(metersPerSecond.isFinite() && metersPerSecond > 0.0) {
            "Fixture speed must be finite and positive: $metersPerSecond."
        }
    }

    /** Returns the controlled distance at [activeDurationMillis]. */
    fun distanceMetersAt(activeDurationMillis: Long): Double {
        require(activeDurationMillis >= 0L) {
            "Active duration cannot be negative: $activeDurationMillis."
        }

        val distance = metersPerSecond * (activeDurationMillis / 1_000.0)
        require(distance.isFinite() && distance >= 0.0) {
            "Fixture distance exceeds the supported range: $distance."
        }
        return distance
    }
}
