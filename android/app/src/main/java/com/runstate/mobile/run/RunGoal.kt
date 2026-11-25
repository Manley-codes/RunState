package com.runstate.mobile.run

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