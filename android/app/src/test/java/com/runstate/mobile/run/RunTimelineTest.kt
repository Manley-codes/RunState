package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Proves duration and pace are derived only from complete, internally valid evidence. */
class RunTimelineTest {

    private companion object {
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val OTHER_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val START = 1_756_000_000_000L
    }

    private fun transition(
        sequence: Int,
        type: RunTransitionType,
        offsetMillis: Long,
        runId: String = RUN_ID
    ) = RunTransitionEntity(
        runId = runId,
        sequenceNumber = sequence,
        transitionType = type,
        occurredAtEpochMillis = START + offsetMillis
    )

    private fun activeMillis(
        state: StoredRunState,
        transitions: List<RunTransitionEntity> = emptyList(),
        finishOffsetMillis: Long? = null,
        nowOffsetMillis: Long = 10_000L,
        coverage: TransitionHistoryCoverage = TransitionHistoryCoverage.COMPLETE
    ) = RunTimeline.activeMillis(
        officialStartEpochMillis = START,
        finishEpochMillis = finishOffsetMillis?.let(START::plus),
        currentState = state,
        transitions = transitions,
        nowEpochMillis = START + nowOffsetMillis,
        historyCoverage = coverage
    )

    @Test
    fun `elapsed running duration uses now`() {
        assertEquals(
            10_000L,
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = null,
                currentState = StoredRunState.RUNNING,
                nowEpochMillis = START + 10_000L
            )
        )
    }

    @Test
    fun `elapsed completed duration uses finish instead of now`() {
        assertEquals(
            8_000L,
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = START + 8_000L,
                currentState = StoredRunState.COMPLETED,
                nowEpochMillis = START + 20_000L
            )
        )
    }

    @Test
    fun `live now before official start produces zero`() {
        assertEquals(
            0L,
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = null,
                currentState = StoredRunState.RUNNING,
                nowEpochMillis = START - 1L
            )
        )
    }

    @Test
    fun `legacy completed run without a finish has unavailable elapsed time`() {
        assertNull(
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = null,
                currentState = StoredRunState.COMPLETED,
                nowEpochMillis = START + 20_000L
            )
        )
    }

    @Test
    fun `finish before official start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = START - 1L,
                currentState = StoredRunState.COMPLETED,
                nowEpochMillis = START
            )
        }
    }

    @Test
    fun `live elapsed timeline refuses a finish`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = START,
                finishEpochMillis = START + 5_000L,
                currentState = StoredRunState.PAUSED,
                nowEpochMillis = START + 10_000L
            )
        }
    }

    @Test
    fun `running without transitions uses start through now`() {
        assertEquals(10_000L, activeMillis(StoredRunState.RUNNING))
    }

    @Test
    fun `paused history closes active time at pause`() {
        assertEquals(
            4_000L,
            activeMillis(
                state = StoredRunState.PAUSED,
                transitions = listOf(transition(1, RunTransitionType.PAUSE, 4_000L)),
                nowOffsetMillis = 20_000L
            )
        )
    }

    @Test
    fun `multiple pause and resume cycles sum only running intervals`() {
        val transitions = listOf(
            transition(1, RunTransitionType.PAUSE, 3_000L),
            transition(2, RunTransitionType.RESUME, 5_000L),
            transition(3, RunTransitionType.PAUSE, 9_000L),
            transition(4, RunTransitionType.RESUME, 12_000L)
        )

        assertEquals(
            10_000L,
            activeMillis(
                state = StoredRunState.RUNNING,
                transitions = transitions,
                nowOffsetMillis = 15_000L
            )
        )
    }

    @Test
    fun `completed history ends active time at final pause rather than finish`() {
        val transitions = listOf(
            transition(1, RunTransitionType.PAUSE, 3_000L),
            transition(2, RunTransitionType.RESUME, 5_000L),
            transition(3, RunTransitionType.PAUSE, 9_000L)
        )

        assertEquals(
            7_000L,
            activeMillis(
                state = StoredRunState.COMPLETED,
                transitions = transitions,
                finishOffsetMillis = 20_000L,
                nowOffsetMillis = 30_000L
            )
        )
    }

    @Test
    fun `equal millisecond pause and resume is a valid zero length interval`() {
        val transitions = listOf(
            transition(1, RunTransitionType.PAUSE, 3_000L),
            transition(2, RunTransitionType.RESUME, 3_000L),
            transition(3, RunTransitionType.PAUSE, 3_000L)
        )

        assertEquals(
            3_000L,
            activeMillis(
                state = StoredRunState.PAUSED,
                transitions = transitions,
                nowOffsetMillis = 8_000L
            )
        )
    }

    @Test
    fun `incomplete running history is unavailable`() {
        assertNull(
            activeMillis(
                state = StoredRunState.RUNNING,
                coverage = TransitionHistoryCoverage.INCOMPLETE
            )
        )
    }

    @Test
    fun `incomplete paused history with no events is unavailable rather than zero`() {
        assertNull(
            activeMillis(
                state = StoredRunState.PAUSED,
                coverage = TransitionHistoryCoverage.INCOMPLETE
            )
        )
    }

    @Test
    fun `incomplete legacy completed history is unavailable`() {
        assertNull(
            activeMillis(
                state = StoredRunState.COMPLETED,
                coverage = TransitionHistoryCoverage.INCOMPLETE
            )
        )
    }

    @Test
    fun `sequence must begin at one`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.PAUSED,
                transitions = listOf(transition(2, RunTransitionType.PAUSE, 1_000L))
            )
        }
    }

    @Test
    fun `sequence gaps duplicates and supplied order are rejected`() {
        val malformedLists = listOf(
            listOf(
                transition(1, RunTransitionType.PAUSE, 1_000L),
                transition(3, RunTransitionType.RESUME, 2_000L)
            ),
            listOf(
                transition(1, RunTransitionType.PAUSE, 1_000L),
                transition(1, RunTransitionType.RESUME, 2_000L)
            ),
            listOf(
                transition(2, RunTransitionType.PAUSE, 1_000L),
                transition(1, RunTransitionType.RESUME, 2_000L)
            )
        )

        malformedLists.forEach { transitions ->
            assertThrows(IllegalArgumentException::class.java) {
                activeMillis(StoredRunState.RUNNING, transitions)
            }
        }
    }

    @Test
    fun `resume cannot be the first event`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.RUNNING,
                transitions = listOf(transition(1, RunTransitionType.RESUME, 1_000L))
            )
        }
    }

    @Test
    fun `pause and resume must alternate`() {
        val repeatedPause = listOf(
            transition(1, RunTransitionType.PAUSE, 1_000L),
            transition(2, RunTransitionType.PAUSE, 2_000L)
        )
        val repeatedResume = listOf(
            transition(1, RunTransitionType.PAUSE, 1_000L),
            transition(2, RunTransitionType.RESUME, 2_000L),
            transition(3, RunTransitionType.RESUME, 3_000L)
        )

        listOf(repeatedPause, repeatedResume).forEach { transitions ->
            assertThrows(IllegalArgumentException::class.java) {
                activeMillis(StoredRunState.RUNNING, transitions)
            }
        }
    }

    @Test
    fun `transition before official start is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.PAUSED,
                transitions = listOf(transition(1, RunTransitionType.PAUSE, -1L))
            )
        }
    }

    @Test
    fun `transition epoch time cannot move backwards`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.RUNNING,
                transitions = listOf(
                    transition(1, RunTransitionType.PAUSE, 2_000L),
                    transition(2, RunTransitionType.RESUME, 1_000L)
                )
            )
        }
    }

    @Test
    fun `transition after finish is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.COMPLETED,
                transitions = listOf(transition(1, RunTransitionType.PAUSE, 5_000L)),
                finishOffsetMillis = 4_000L
            )
        }
    }

    @Test
    fun `current state must agree with the final transition`() {
        val malformedCases = listOf(
            StoredRunState.RUNNING to listOf(transition(1, RunTransitionType.PAUSE, 1_000L)),
            StoredRunState.PAUSED to emptyList(),
            StoredRunState.COMPLETED to listOf(
                transition(1, RunTransitionType.PAUSE, 1_000L),
                transition(2, RunTransitionType.RESUME, 2_000L)
            )
        )

        malformedCases.forEach { (state, transitions) ->
            assertThrows(IllegalArgumentException::class.java) {
                activeMillis(
                    state = state,
                    transitions = transitions,
                    finishOffsetMillis = if (state == StoredRunState.COMPLETED) 5_000L else null
                )
            }
        }
    }

    @Test
    fun `complete completed history requires a finish`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.COMPLETED,
                transitions = listOf(transition(1, RunTransitionType.PAUSE, 1_000L))
            )
        }
    }

    @Test
    fun `non completed history refuses a finish`() {
        listOf(
            StoredRunState.RUNNING to emptyList(),
            StoredRunState.PAUSED to listOf(transition(1, RunTransitionType.PAUSE, 1_000L))
        ).forEach { (state, transitions) ->
            assertThrows(IllegalArgumentException::class.java) {
                activeMillis(
                    state = state,
                    transitions = transitions,
                    finishOffsetMillis = 5_000L
                )
            }
        }
    }

    @Test
    fun `running clock behind last resume adds no negative interval`() {
        val transitions = listOf(
            transition(1, RunTransitionType.PAUSE, 2_000L),
            transition(2, RunTransitionType.RESUME, 5_000L)
        )

        assertEquals(
            2_000L,
            activeMillis(
                state = StoredRunState.RUNNING,
                transitions = transitions,
                nowOffsetMillis = 4_000L
            )
        )
    }

    @Test
    fun `transitions from multiple runs are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            activeMillis(
                state = StoredRunState.RUNNING,
                transitions = listOf(
                    transition(1, RunTransitionType.PAUSE, 1_000L),
                    transition(2, RunTransitionType.RESUME, 2_000L, OTHER_RUN_ID)
                )
            )
        }
    }

    @Test
    fun `average pace is seconds per meter`() {
        assertEquals(
            2.0,
            requireNotNull(RunTimeline.averagePaceSecondsPerMeter(10_000L, 5.0)),
            0.0
        )
    }

    @Test
    fun `pace is unavailable when duration or distance is unavailable`() {
        assertNull(RunTimeline.averagePaceSecondsPerMeter(null, 5.0))
        assertNull(RunTimeline.averagePaceSecondsPerMeter(10_000L, null))
    }

    @Test
    fun `pace is unavailable for zero duration or zero distance`() {
        assertNull(RunTimeline.averagePaceSecondsPerMeter(0L, 5.0))
        assertNull(RunTimeline.averagePaceSecondsPerMeter(10_000L, 0.0))
    }

    @Test
    fun `pace rejects negative and non finite inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.averagePaceSecondsPerMeter(-1L, 5.0)
        }
        listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            invalidDistance ->
            assertThrows(IllegalArgumentException::class.java) {
                RunTimeline.averagePaceSecondsPerMeter(10_000L, invalidDistance)
            }
        }
    }

    @Test
    fun `pace rejects an infinite calculated result`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.averagePaceSecondsPerMeter(Long.MAX_VALUE, Double.MIN_VALUE)
        }
    }

    @Test
    fun `elapsed duration overflow is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = Long.MIN_VALUE,
                finishEpochMillis = Long.MAX_VALUE,
                currentState = StoredRunState.COMPLETED,
                nowEpochMillis = 0L
            )
        }
    }

    @Test
    fun `active duration overflow is rejected`() {
        val extremePause = RunTransitionEntity(
            runId = RUN_ID,
            sequenceNumber = 1,
            transitionType = RunTransitionType.PAUSE,
            occurredAtEpochMillis = Long.MAX_VALUE
        )

        assertThrows(IllegalArgumentException::class.java) {
            RunTimeline.activeMillis(
                officialStartEpochMillis = Long.MIN_VALUE,
                finishEpochMillis = Long.MAX_VALUE,
                currentState = StoredRunState.COMPLETED,
                transitions = listOf(extremePause),
                nowEpochMillis = 0L,
                historyCoverage = TransitionHistoryCoverage.COMPLETE
            )
        }
    }
}
