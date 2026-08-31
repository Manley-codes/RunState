package com.runstate.mobile.run

/**
 * A closed list of stages in RunState's run journey.
 *
 * "Closed list" means the app must use one of these known choices.
 * It cannot quietly invent another session stage elsewhere.
 */
enum class RunSessionState {
    NO_SESSION, // The runner is preparing; nothing has been saved.
    COUNTDOWN,  // A visual transition only; there is still no saved run.
    RUNNING,    // The official run now exists and must be saved.
    PAUSED,     // The same run exists, but active run time is not increasing.
    COMPLETED   // The run has ended and must remain durably saved.
}

/**
 * Controls how the run journey is allowed to change state.
 *
 * Other code may ask this class for its current state, but state changes
 * must pass through the controlled functions defined inside this class.
 */
class RunSessionStateMachine {

    // `var` means this value may change. Every new machine starts with no session.
    var state: RunSessionState = RunSessionState.NO_SESSION

        // Other code can read `state`, but only this class can change it.
        private set

    /**
     * Moves the journey into its visual countdown stage.
     */
    fun beginCountdown() {

        // `check` stops an illegal move and provides a useful error message.
        check(state == RunSessionState.NO_SESSION) {
            "Countdown can only begin when no run session exists."
        }

        // The safety check passed, so this is now a legal state change.
        state = RunSessionState.COUNTDOWN
    }

    /**
     * Moves the journey from the visual countdown into the official Running state.
     *
     * This state machine validates only the legal order. Future production code
     * must create the Room-backed session before showing RUNNING on the screen.
     */
    fun startRun() {

        // A real run may begin only after the countdown stage.
        check(state == RunSessionState.COUNTDOWN) {
            "A run can only start after the countdown."
        }

        // The transition is legal, so the rule state may now become RUNNING.
        state = RunSessionState.RUNNING
    }

    /**
     * Pauses the active run without ending its session.
     */
    fun pauseRun() {
        check(state == RunSessionState.RUNNING) {
            "A run can only be paused while it is running."
        }

        state = RunSessionState.PAUSED
    }

    /**
     * Resumes the paused run without creating a new session.
     */
    fun resumeRun() {
        check(state == RunSessionState.PAUSED) {
            "A run can only be resumed while it is paused."
        }

        state = RunSessionState.RUNNING
    }

    /**
     * Completes the paused run after the runner confirms the hold-to-end action.
     */
    fun completeRun() {
        check(state == RunSessionState.PAUSED) {
            "A run can only be completed while it is paused."
        }

        state = RunSessionState.COMPLETED
    }
}