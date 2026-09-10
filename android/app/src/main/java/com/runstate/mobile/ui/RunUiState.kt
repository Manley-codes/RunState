package com.runstate.mobile.ui

import com.runstate.mobile.run.InitializationStatus
import com.runstate.mobile.run.RunAdmission
import com.runstate.mobile.run.RunSessionState

/**
 * The seven things the run screen can be showing.
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

    /** A run is live, whether it was started here or adopted at startup. */
    ActiveRunning,

    /** The same run, paused. */
    ActivePaused,

    /** Recovery could not read storage, so the screen offers a retry and nothing else. */
    InitializationFailed,

    /** Storage holds more than one unfinished run, so starting is withheld. */
    StorageInconsistent
}

/**
 * Translates the coordinator's two answers into the one thing to draw.
 *
 * Pure and deliberately not a composable: no Compose types, no coroutines, no clock, no
 * state of its own. The same pair always produces the same screen, which is what lets the
 * whole mapping be tested on the JVM instead of on a phone.
 *
 * Both answers are needed, and neither substitutes for the other.
 * [InitializationStatus] is the history of the attempt and [RunAdmission] is the present
 * situation, so `NotInitialized` alone cannot tell "still working on it" apart from "it
 * failed" — the difference between those two screens is a spinner and a retry button.
 *
 * Combinations the coordinator cannot produce are raised rather than resolved. Choosing
 * the nearest ordinary screen would turn an internal defect into a Start button offered
 * over a database the app has already refused to interpret, which is a worse outcome than
 * a crash a test can catch.
 */
internal fun runUiStateFor(
    initializationStatus: InitializationStatus,
    admission: RunAdmission
): RunUiState = when (initializationStatus) {

    is InitializationStatus.NotAttempted -> when (admission) {
        is RunAdmission.NotInitialized -> RunUiState.Initializing
        else -> error("Recovery has not been attempted, but admission is $admission.")
    }

    is InitializationStatus.Failed -> when (admission) {

        // The retry screen. The cause is deliberately left behind here: the runner is
        // told RunState could not get ready, not what SQLite said about it.
        is RunAdmission.NotInitialized -> RunUiState.InitializationFailed
        else -> error("Recovery failed, but admission is $admission.")
    }

    is InitializationStatus.Completed -> when (admission) {
        is RunAdmission.ReadyForCountdown -> RunUiState.ReadyToStart

        is RunAdmission.CountdownInProgress -> RunUiState.Countdown

        is RunAdmission.RunInProgress -> when (admission.session.state) {
            RunSessionState.RUNNING -> RunUiState.ActiveRunning

            RunSessionState.PAUSED -> RunUiState.ActivePaused

            // The owner's own constructor refuses these three, so reaching one means the
            // machine moved outside the coordinator's lock rather than that the screen
            // needs a new state to show.
            RunSessionState.NO_SESSION,
            RunSessionState.COUNTDOWN,
            RunSessionState.COMPLETED -> error(
                "A run in progress cannot be ${admission.session.state}."
            )
        }

        // Only the fact that starting is withheld crosses over. How many candidates there
        // are, and which, is diagnostic detail the runner has no use for and no way to act
        // on, so it stays with the coordinator.
        is RunAdmission.BlockedByInconsistentStorage -> RunUiState.StorageInconsistent

        is RunAdmission.NotInitialized -> error(
            "Recovery completed, but admission is still $admission."
        )
    }
}
