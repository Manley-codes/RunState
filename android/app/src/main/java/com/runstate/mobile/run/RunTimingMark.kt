package com.runstate.mobile.run

import java.util.UUID

/**
 * A monotonic reading from one application process. Readings from another process
 * are deliberately not comparable, even if the phone has not restarted.
 */
data class RunTimingMark(val elapsedRealtimeMillis: Long, val sessionId: String) {
    init {
        require(elapsedRealtimeMillis >= 0L) { "Elapsed realtime cannot be negative." }
        require(runCatching { UUID.fromString(sessionId).toString() == sessionId }.getOrDefault(false)) {
            "A timing session id must be canonical lowercase UUID text."
        }
    }
}

/** One wall-clock reading paired with the available monotonic evidence. */
internal data class RunTimeReading(val epochMillis: Long, val timingMark: RunTimingMark?)
