package com.runstate.mobile.reflection

import com.runstate.mobile.data.local.RunDao
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Process-owned presentation state. Compose sees only this, never transport evidence. */
internal sealed interface RunReflectionState {
    data object Idle : RunReflectionState
    data class Preparing(val runId: String) : RunReflectionState
    data class Ready(val runId: String, val text: String) : RunReflectionState
    data class Failed(val runId: String) : RunReflectionState
}

/**
 * Owns the one reflection attempt for a saved-run appearance outside Activity/Compose.
 *
 * Rotation can call [onSavedVisible] again, but the process-held attempt record makes that
 * a no-op. A retry starts a new request ID and re-reads the same completed Room row; it has
 * no write dependency and therefore cannot save a second run.
 */
internal class RunReflectionCoordinator(
    private val runDao: RunDao,
    private val gateway: ReflectionGateway,
    private val applicationScope: CoroutineScope,
    private val requestIdSupplier: () -> UUID = UUID::randomUUID,
    private val quietWindowMillis: Long = 6_000L,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val quietDelay: suspend (Long) -> Unit = { delay(it) }
) {
    private data class AttemptRecord(
        val firstVisibleAtMillis: Long,
        var inFlight: Boolean,
        var state: RunReflectionState
    )

    private val attemptsLock = Any()
    private val attempts = mutableMapOf<String, AttemptRecord>()
    private var activeRunId: String? = null
    private val mutableState = MutableStateFlow<RunReflectionState>(RunReflectionState.Idle)

    val state: StateFlow<RunReflectionState> = mutableState.asStateFlow()

    fun onSavedVisible(runId: String, bridgeReady: Boolean = true) {
        val shouldStart = synchronized(attemptsLock) {
            activeRunId = runId
            val existing = attempts[runId]
            if (existing != null) {
                mutableState.value = existing.state
                false
            } else {
                val preparing = RunReflectionState.Preparing(runId)
                attempts[runId] = AttemptRecord(
                    firstVisibleAtMillis = elapsedRealtimeMillis(),
                    inFlight = bridgeReady,
                    state = preparing
                )
                mutableState.value = preparing
                bridgeReady
            }
        }
        if (shouldStart) launchAttempt(runId)
    }

    /** Continues a first attempt after Android grants the debug bridge permission. */
    fun startWhenBridgeReady(runId: String) {
        val shouldStart = synchronized(attemptsLock) {
            val record = attempts[runId] ?: return@synchronized false
            if (
                activeRunId != runId ||
                record.inFlight ||
                record.state is RunReflectionState.Ready
            ) {
                false
            } else {
                record.inFlight = true
                true
            }
        }
        if (shouldStart) launchAttempt(runId)
    }

    /** Converts a denied debug-only platform prerequisite into the ordinary retry state. */
    fun onBridgeUnavailable(runId: String) {
        synchronized(attemptsLock) {
            val record = attempts[runId] ?: return
            if (activeRunId != runId || record.inFlight) return
            val failed = RunReflectionState.Failed(runId)
            record.state = failed
            mutableState.value = failed
        }
    }

    fun retry(runId: String) {
        val shouldStart = synchronized(attemptsLock) {
            val record = attempts[runId] ?: return@synchronized false
            if (
                activeRunId != runId ||
                record.inFlight ||
                record.state !is RunReflectionState.Failed
            ) {
                false
            } else {
                record.inFlight = true
                true
            }
        }
        if (shouldStart) launchAttempt(runId)
    }

    private fun launchAttempt(runId: String) {
        recordState(runId, RunReflectionState.Preparing(runId))
        applicationScope.launch {
            try {
                val savedRun = requireNotNull(runDao.findById(runId)) {
                    "The completed run is no longer stored."
                }
                val transitions = runDao.transitionsFor(runId)
                val request = reflectionRequestFromSavedRun(
                    requestId = requestIdSupplier().toString(),
                    run = savedRun,
                    transitions = transitions
                )
                val candidates = gateway.request(request)

                val firstVisibleAt = synchronized(attemptsLock) {
                    attempts.getValue(runId).firstVisibleAtMillis
                }
                val revealAt = firstVisibleAt + quietWindowMillis
                val remaining = (revealAt - elapsedRealtimeMillis()).coerceAtLeast(0L)
                if (remaining > 0L) quietDelay(remaining)

                recordState(runId, RunReflectionState.Ready(runId, candidates.noSelection))
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                recordState(runId, RunReflectionState.Failed(runId))
            } finally {
                synchronized(attemptsLock) {
                    attempts[runId]?.inFlight = false
                }
            }
        }
    }

    /** Stores every attempt outcome, but only the currently visible run may publish it. */
    private fun recordState(runId: String, state: RunReflectionState) {
        synchronized(attemptsLock) {
            val record = attempts[runId] ?: return
            record.state = state
            if (activeRunId == runId) {
                mutableState.value = state
            }
        }
    }
}
