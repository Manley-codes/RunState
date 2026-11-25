package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunEntity

sealed interface RunGoal {

    data class Distance(val meters: Double) : RunGoal {
        init {
            require(meters.isFinite() && meters > 0.0) {
                "Distance must be a positive number."
            }
        }
    }

    data class Time(val activeMillis: Long) : RunGoal {
        init {
            require(activeMillis > 0L) {
                "Time must be greater than zero."
            }
        }
    }
}

/** A saved run has at most one explicit goal; absent values mean no goal was chosen. */
val RunEntity.runGoal: RunGoal?
    get() = runGoalFromValues(goalDistanceMeters, goalActiveMillis)

/** Shared decoding boundary for durable values and the foreground-service request. */
internal fun runGoalFromValues(distanceMeters: Double?, activeMillis: Long?): RunGoal? {
    require(distanceMeters == null || activeMillis == null) { "Choose either a distance or a time goal." }
    return when {
        distanceMeters != null -> RunGoal.Distance(distanceMeters)
        activeMillis != null -> RunGoal.Time(activeMillis)
        else -> null
    }
}
