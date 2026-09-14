package com.runstate.mobile.ui

import com.runstate.mobile.run.InitializationStatus
import com.runstate.mobile.run.RunActionKind
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunJourneySnapshot
import com.runstate.mobile.run.RunSessionState
import com.runstate.mobile.run.StartAttemptPhase

/**
 * The things the run screen can be showing.
 *
 * An enum rather than a sealed hierarchy, and that is the point rather than a shortcut.
 * These are the app's answers translated into something a screen can draw, and the
 * translation is only honest if nothing survives it: no [com.runstate.mobile.run.ActiveRunSession],
 * no UUID, no exception, no stored row, no candidate count. An enum makes that structural.
 * A sealed interface would leave a constructor parameter one convenient moment away, and
 * the first thing anyone would reach for is the session — which is exactly the object the
 * UI must not hold.
 *
 * ## Why the session in particular must not be kept
 *
 * `ActiveRunSession.state` is a plain property read. It is not Compose-observable and
 * nothing recomposes when it changes, so a screen holding the session would render
 * whatever that property said at the last recomposition and then silently go stale.
 * Reading it once during mapping and keeping only the answer means the screen is showing a
 * value it actually captured, and the way to get a newer one is to ask the coordinator
 * again — which is the truthful thing to do anyway.
 *
 * ## Why Active rather than Recovered
 *
 * A run adopted at startup and a run started in this process are the same run to the
 * person looking at the phone. Naming the states for where the run came from would push
 * that distinction into the UI, where it changes nothing and can only be got wrong. The
 * screen says what is true now.
 */
internal enum class RunUiState {

    /** Recovery is underway. Nothing is known yet, so nothing may be offered. */
    Initializing,

    /** Nothing is live and nothing is blocking; a countdown may begin. */
    ReadyToStart,

    /** A countdown is underway. No run is official and nothing has been stored. */
    Countdown,

    /** The official start is being written. Nothing may be cancelled or claimed yet. */
    StartingRun,

    /** The official start could not be saved; the same run may be retried or abandoned. */
    StartFailed,

    /** A run is live, whether it was started here or adopted at startup. */
    ActiveRunning,

    /** The same run, paused. */
    ActivePaused,

    /** The run's completion is durably stored. */
    Saved,

    /** Recovery could not read storage, so the screen offers a retry and nothing else. */
    InitializationFailed,

    /** Storage holds more than one unfinished run, so starting is withheld. */
    StorageInconsistent
}

/**
 * What the screen draws: one state, plus whether a lifecycle write is pending or last failed.
 *
 * A bare [RunUiState] cannot carry those two facts, and they are independent of the state:
 * a pending pause is still visibly a running run, and a refused pause is still one too. They
 * are plain booleans on purpose, so this model inherits the enum's guarantee — nothing about
 * the run itself, and nothing about *why* a write failed, can travel through it.
 *
 * The countdown digit is deliberately absent. It is composition-local timing, not an answer
 * from the coordinator, and restarts from 3 whenever the screen does.
 *
 * @property actionInProgress a lifecycle write is underway; controls must be disabled.
 * @property actionFailed the last lifecycle write was refused; the runner may try again.
 */
internal data class RunUiModel(
    val state: RunUiState,
    val actionInProgress: Boolean = false,
    val actionFailed: Boolean = false
) {
    init {

        // Only a live run has lifecycle controls to be busy or to have failed.
        require(
            (!actionInProgress && !actionFailed) ||
                state == RunUiState.ActiveRunning ||
                state == RunUiState.ActivePaused
        ) {
            "Only an active run can have a pending or failed action, not $state."
        }

        // A pending retry is shown as busy; the older failure is not shown beside it.
        require(!(actionInProgress && actionFailed)) {
            "A run action cannot be both pending and failed."
        }
    }
}

/**
 * Translates one coordinator snapshot into the model to draw.
 *
 * Pure and deliberately not a composable: no Compose types, no coroutines, no clock, no
 * state of its own. The same snapshot always produces the same model, which is what lets
 * the whole mapping be tested on the JVM instead of on a phone.
 *
 * ## Conservative while a write is pending
 *
 * A reserved action shows the state that was visible *before* it began — Pause pending is
 * still Running, Resume and Complete pending are still Paused — with controls busy. The
 * session may already have advanced in memory, but the coordinator has not yet applied the
 * write's result, and the screen must not claim something storage has not confirmed. Saved,
 * in particular, appears only once completion has succeeded and its reservation is gone.
 *
 * Combinations the coordinator cannot produce are raised rather than resolved. Choosing
 * the nearest ordinary screen would turn an internal defect into a Start button offered
 * over a database the app has already refused to interpret, which is a worse outcome than
 * a crash a test can catch.
 */
internal fun runUiModelFor(snapshot: RunJourneySnapshot): RunUiModel {
    val admission = snapshot.admission

    // Start progress belongs only to a countdown; action progress only to a live run.
    if (admission !is RunAdmission.CountdownInProgress) {
        check(snapshot.startAttemptPhase == StartAttemptPhase.None) {
            "A start is ${snapshot.startAttemptPhase}, but admission is $admission."
        }
    }
    if (admission !is RunAdmission.RunInProgress) {
        check(snapshot.reservedAction == null && snapshot.lastActionFailed == null) {
            "Action reporting (${snapshot.reservedAction}, ${snapshot.lastActionFailed}) " +
                "cannot accompany $admission."
        }
    }

    return when (snapshot.initializationStatus) {

        is InitializationStatus.NotAttempted -> when (admission) {
            is RunAdmission.NotInitialized -> RunUiModel(RunUiState.Initializing)
            else -> error("Recovery has not been attempted, but admission is $admission.")
        }

        is InitializationStatus.Failed -> when (admission) {

            // The retry screen. The cause is deliberately left behind here: the runner is
            // told RunState could not get ready, not what SQLite said about it.
            is RunAdmission.NotInitialized -> RunUiModel(RunUiState.InitializationFailed)
            else -> error("Recovery failed, but admission is $admission.")
        }

        is InitializationStatus.Completed -> when (admission) {
            is RunAdmission.ReadyForCountdown -> RunUiModel(RunUiState.ReadyToStart)

            is RunAdmission.CountdownInProgress -> when (snapshot.startAttemptPhase) {
                StartAttemptPhase.None -> RunUiModel(RunUiState.Countdown)
                StartAttemptPhase.InProgress -> RunUiModel(RunUiState.StartingRun)
                StartAttemptPhase.Failed -> RunUiModel(RunUiState.StartFailed)
            }

            is RunAdmission.RunInProgress ->
                activeRunModel(admission.session.state, snapshot)

            is RunAdmission.RunCompleted -> {
                check(admission.session.state == RunSessionState.COMPLETED) {
                    "A completed run cannot be ${admission.session.state}."
                }
                RunUiModel(RunUiState.Saved)
            }

            // Only the fact that starting is withheld crosses over. How many candidates there
            // are, and which, is diagnostic detail the runner has no use for and no way to act
            // on, so it stays with the coordinator.
            is RunAdmission.BlockedByInconsistentStorage ->
                RunUiModel(RunUiState.StorageInconsistent)

            is RunAdmission.NotInitialized -> error(
                "Recovery completed, but admission is still $admission."
            )
        }
    }
}

/**
 * The model for a live run: the pre-action state while a write is reserved, otherwise the
 * session's own state, flagged when the last write was refused.
 */
private fun activeRunModel(
    sessionState: RunSessionState,
    snapshot: RunJourneySnapshot
): RunUiModel {
    val reserved = snapshot.reservedAction

    if (reserved != null) {

        // Mid-write, the session is either still where the action began or already where
        // it is going. Anything else means the machine moved outside the coordinator.
        val (visible, allowed) = when (reserved) {
            RunActionKind.PAUSE ->
                RunUiState.ActiveRunning to setOf(RunSessionState.RUNNING, RunSessionState.PAUSED)

            RunActionKind.RESUME ->
                RunUiState.ActivePaused to setOf(RunSessionState.PAUSED, RunSessionState.RUNNING)

            RunActionKind.COMPLETE ->
                RunUiState.ActivePaused to setOf(RunSessionState.PAUSED, RunSessionState.COMPLETED)
        }
        check(sessionState in allowed) {
            "A reserved $reserved cannot accompany a $sessionState run."
        }

        return RunUiModel(visible, actionInProgress = true)
    }

    val failed = snapshot.lastActionFailed != null

    return when (sessionState) {
        RunSessionState.RUNNING -> RunUiModel(RunUiState.ActiveRunning, actionFailed = failed)

        RunSessionState.PAUSED -> RunUiModel(RunUiState.ActivePaused, actionFailed = failed)

        // The owner's own constructor refuses the first two, and the coordinator reports a
        // finished, unreserved run as RunCompleted, so reaching any of these means the
        // machine moved outside the coordinator's lock rather than that the screen needs a
        // new state to show.
        RunSessionState.NO_SESSION,
        RunSessionState.COUNTDOWN,
        RunSessionState.COMPLETED -> error("A run in progress cannot be $sessionState.")
    }
}
