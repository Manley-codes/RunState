package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState

/**
 * Whether the stored pause/resume rows tell the whole story of a run.
 *
 * Version-1 rows can legitimately reach today's database with no transition rows even
 * when their state is PAUSED or COMPLETED. Treating that absence as "never paused" would
 * turn a migration gap into invented active time, so callers have to certify completeness
 * before [RunTimeline] will calculate it.
 */
internal enum class TransitionHistoryCoverage {
    COMPLETE,
    INCOMPLETE
}

/**
 * Pure calculations over one run's durable lifecycle timeline.
 *
 * This object reads no clock and keeps no previous value. A caller supplies the current
 * epoch time, which makes every calculation reproducible in a JVM test. It also means this
 * object cannot guarantee that values from separate calls never decrease if the wall clock
 * moves backwards; a later presentation or foreground-service owner must keep that floor.
 */
internal object RunTimeline {

    /**
     * Returns start-to-end elapsed time, including pauses.
     *
     * A durable finish before the start is malformed and rejected. A live `now` before the
     * start can happen during a wall-clock correction, so it contributes zero rather than a
     * negative duration. A legacy COMPLETED row with no stored finish returns null: using
     * `now` for a run that already ended would make its elapsed time grow forever.
     */
    fun elapsedMillis(
        officialStartEpochMillis: Long,
        finishEpochMillis: Long?,
        currentState: StoredRunState,
        nowEpochMillis: Long
    ): Long? {
        val endEpochMillis = when (currentState) {
            StoredRunState.COMPLETED -> finishEpochMillis ?: return null

            StoredRunState.RUNNING,
            StoredRunState.PAUSED -> {
                require(finishEpochMillis == null) {
                    "A live $currentState timeline cannot have a finish time."
                }
                nowEpochMillis
            }
        }

        if (currentState == StoredRunState.COMPLETED) {
            require(endEpochMillis >= officialStartEpochMillis) {
                "A run's finish cannot precede its official start: " +
                    "$endEpochMillis is before $officialStartEpochMillis."
            }
            return differenceExact(
                later = endEpochMillis,
                earlier = officialStartEpochMillis,
                description = "elapsed run duration"
            )
        }

        if (endEpochMillis <= officialStartEpochMillis) return 0L

        return differenceExact(
            later = endEpochMillis,
            earlier = officialStartEpochMillis,
            description = "elapsed run duration"
        )
    }

    /**
     * Sums only the intervals in which the run was Running.
     *
     * [TransitionHistoryCoverage.INCOMPLETE] returns null before interpreting the events.
     * That is the honest result for migrated rows whose current state survived but whose
     * earlier pause/resume history was never stored.
     *
     * A complete history is deliberately strict: sequence numbers are contiguous, event
     * types alternate from PAUSE, epoch time never moves backwards, and the final event has
     * to agree with [currentState]. A malformed complete history is a defect, not a duration
     * to approximate.
     */
    fun activeMillis(
        officialStartEpochMillis: Long,
        finishEpochMillis: Long?,
        currentState: StoredRunState,
        transitions: List<RunTransitionEntity>,
        nowEpochMillis: Long,
        historyCoverage: TransitionHistoryCoverage
    ): Long? {
        if (historyCoverage == TransitionHistoryCoverage.INCOMPLETE) return null

        when (currentState) {
            StoredRunState.COMPLETED -> require(finishEpochMillis != null) {
                "A complete COMPLETED timeline needs a finish time."
            }

            StoredRunState.RUNNING,
            StoredRunState.PAUSED -> require(finishEpochMillis == null) {
                "A live $currentState timeline cannot have a finish time."
            }
        }

        var expectedSequence = 1
        var expectedType = RunTransitionType.PAUSE
        var previousEpochMillis = officialStartEpochMillis
        var openRunningIntervalStart: Long? = officialStartEpochMillis
        var activeTotal = 0L
        var transitionRunId: String? = null

        transitions.forEach { transition ->
            require(transition.sequenceNumber == expectedSequence) {
                "Run transition sequence must be contiguous from 1: expected " +
                    "$expectedSequence but found ${transition.sequenceNumber}."
            }
            require(transition.transitionType == expectedType) {
                "Run transitions must alternate from PAUSE: expected $expectedType at " +
                    "sequence $expectedSequence but found ${transition.transitionType}."
            }
            require(transition.occurredAtEpochMillis >= previousEpochMillis) {
                "Run transition time moved backwards at sequence $expectedSequence: " +
                    "${transition.occurredAtEpochMillis} is before $previousEpochMillis."
            }
            if (finishEpochMillis != null) {
                require(transition.occurredAtEpochMillis <= finishEpochMillis) {
                    "Run transition $expectedSequence occurs after the run finished."
                }
            }

            val firstRunId = transitionRunId
            if (firstRunId == null) {
                transitionRunId = transition.runId
            } else {
                require(transition.runId == firstRunId) {
                    "One timeline cannot contain transitions from multiple runs."
                }
            }

            when (transition.transitionType) {
                RunTransitionType.PAUSE -> {
                    val intervalStart = checkNotNull(openRunningIntervalStart) {
                        "A PAUSE cannot close a run that is not Running."
                    }
                    activeTotal = addExact(
                        activeTotal,
                        differenceExact(
                            later = transition.occurredAtEpochMillis,
                            earlier = intervalStart,
                            description = "active run interval"
                        )
                    )
                    openRunningIntervalStart = null
                    expectedType = RunTransitionType.RESUME
                }

                RunTransitionType.RESUME -> {
                    check(openRunningIntervalStart == null) {
                        "A RESUME cannot open a run that is already Running."
                    }
                    openRunningIntervalStart = transition.occurredAtEpochMillis
                    expectedType = RunTransitionType.PAUSE
                }
            }

            previousEpochMillis = transition.occurredAtEpochMillis
            expectedSequence += 1
        }

        when (currentState) {
            StoredRunState.RUNNING -> {
                val intervalStart = requireNotNull(openRunningIntervalStart) {
                    "A RUNNING timeline must have no transitions or end with RESUME."
                }

                // A live wall clock can move behind the last durable Resume. It adds
                // nothing until it catches up rather than creating a negative interval.
                val effectiveNow = maxOf(nowEpochMillis, intervalStart)
                activeTotal = addExact(
                    activeTotal,
                    differenceExact(
                        later = effectiveNow,
                        earlier = intervalStart,
                        description = "open active run interval"
                    )
                )
            }

            StoredRunState.PAUSED -> require(openRunningIntervalStart == null) {
                "A PAUSED timeline must end with PAUSE."
            }

            StoredRunState.COMPLETED -> {
                require(openRunningIntervalStart == null) {
                    "A COMPLETED timeline must end with PAUSE."
                }
                require(finishEpochMillis!! >= previousEpochMillis) {
                    "A run cannot finish before its last durable transition."
                }
            }
        }

        return activeTotal
    }

    /**
     * Returns average seconds per meter, or null when pace is not measurable.
     *
     * Formatting as minutes per mile or kilometer belongs to the later presentation
     * layer; this keeps the stored-unit-independent calculation in one place.
     */
    fun averagePaceSecondsPerMeter(
        activeDurationMillis: Long?,
        distanceMeters: Double?
    ): Double? {
        if (activeDurationMillis == null || distanceMeters == null) return null

        require(activeDurationMillis >= 0L) {
            "Active duration cannot be negative: $activeDurationMillis."
        }
        require(distanceMeters.isFinite() && distanceMeters >= 0.0) {
            "Distance must be a finite, non-negative value: $distanceMeters."
        }

        if (activeDurationMillis == 0L || distanceMeters == 0.0) return null

        val pace = (activeDurationMillis / 1_000.0) / distanceMeters
        require(pace.isFinite() && pace > 0.0) {
            "Calculated pace must be finite and positive: $pace."
        }
        return pace
    }

    /** Subtracts two ordered epoch values without allowing Long wraparound. */
    private fun differenceExact(later: Long, earlier: Long, description: String): Long =
        try {
            Math.subtractExact(later, earlier)
        } catch (overflow: ArithmeticException) {
            throw IllegalArgumentException("$description exceeds the supported range.", overflow)
        }

    /** Adds non-overlapping interval durations without allowing Long wraparound. */
    private fun addExact(total: Long, interval: Long): Long =
        try {
            Math.addExact(total, interval)
        } catch (overflow: ArithmeticException) {
            throw IllegalArgumentException("Active run duration exceeds the supported range.", overflow)
        }
}
