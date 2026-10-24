package com.runstate.mobile.run

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

internal enum class RunCountdownPhase { IDLE, PREPARING, INTRO, COUNTING, CANCELLING, STARTING }

/** Process-only permission from an explicit Start tap. It is never saved or restored after death. */
internal data class RunCountdownState(
    val token: Long = 0L,
    val phase: RunCountdownPhase = RunCountdownPhase.IDLE,
    val digit: Int? = null,
    val startedEarly: Boolean = false
) {
    val active: Boolean get() = phase != RunCountdownPhase.IDLE
    val canControl: Boolean get() = phase in setOf(RunCountdownPhase.PREPARING, RunCountdownPhase.INTRO, RunCountdownPhase.COUNTING)
}

/**
 * One main-thread countdown, independently of Activity visibility. The already running foreground
 * service supplies speech and the durable start; observing this state never starts a timer or run.
 * A token rejects late commands/callbacks from a cancelled countdown or a retired service.
 */
internal class RunCountdownOwner(
    private val seconds: Int = 10,
    private val stepMillis: Long = 1_000L,
    private val introTimeoutMillis: Long = 5_000L,
    private val waitMillis: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutableState = MutableStateFlow(RunCountdownState())
    val state: StateFlow<RunCountdownState> = mutableState.asStateFlow()
    private var generation = 0L
    private var command = CompletableDeferred<Unit>()
    private var claimedToken: Long? = null

    init { require(seconds > 0 && stepMillis > 0L && introTimeoutMillis > 0L) }

    fun reserve(): Long {
        check(!state.value.active) { "A countdown already owns this start." }
        val token = ++generation
        command = CompletableDeferred()
        claimedToken = null
        // Show the opening number immediately; its first timed second starts after the intro.
        mutableState.value = RunCountdownState(token, RunCountdownPhase.PREPARING, seconds)
        return token
    }

    fun owns(token: Long): Boolean = state.value.active && state.value.token == token

    fun startNow(token: Long = state.value.token): Boolean {
        if (!owns(token) || !state.value.canControl) return false
        return command.complete(Unit)
    }

    fun cancel(token: Long = state.value.token): Boolean {
        if (!owns(token) || !state.value.canControl) return false
        // Keep this admission reserved until the service has also cancelled the coordinator.
        // Otherwise a rapid Cancel/Start could let old cleanup cancel the next countdown.
        mutableState.value = RunCountdownState(token, RunCountdownPhase.CANCELLING)
        command.complete(Unit)
        return true
    }

    /** Service failure/destruction also retires an in-flight start display, never its durable row. */
    fun retire(token: Long) {
        if (state.value.token != token) return
        mutableState.value = RunCountdownState(token)
        command.complete(Unit)
    }

    suspend fun execute(
        token: Long,
        intro: suspend () -> Unit,
        cue: (Int) -> Unit,
        start: suspend () -> Unit
    ): Boolean = coroutineScope {
        if (!owns(token) || state.value.phase == RunCountdownPhase.CANCELLING || claimedToken != null) return@coroutineScope false
        claimedToken = token
        val signal = command
        try {
            if (!signal.isCompleted) {
                mutableState.value = RunCountdownState(token, RunCountdownPhase.INTRO, seconds)
                val introduction = async { withTimeoutOrNull(introTimeoutMillis) { intro() } }
                try { select<Unit> { introduction.onAwait { }; signal.onAwait { } } }
                finally { introduction.cancel() }
            }
            if (!owns(token) || state.value.phase == RunCountdownPhase.CANCELLING) return@coroutineScope false
            if (!signal.isCompleted) {
                for (step in seconds downTo 1) {
                    if (!owns(token) || state.value.phase == RunCountdownPhase.CANCELLING) return@coroutineScope false
                    mutableState.value = RunCountdownState(token, RunCountdownPhase.COUNTING, step)
                    if (step == seconds || step <= 3) cue(step)
                    val tick = async { waitMillis(stepMillis) }
                    try { select<Unit> { tick.onAwait { }; signal.onAwait { } } }
                    finally { tick.cancel() }
                    if (signal.isCompleted) break
                }
            }
            if (!owns(token) || state.value.phase == RunCountdownPhase.CANCELLING) return@coroutineScope false
            // There is no suspension between this claim and calling the durable start boundary.
            mutableState.value = RunCountdownState(token, RunCountdownPhase.STARTING, startedEarly = signal.isCompleted)
            start()
            true
        } finally {
            if (state.value.token == token && state.value.phase != RunCountdownPhase.CANCELLING) retire(token)
        }
    }
}
