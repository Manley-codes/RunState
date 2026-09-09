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

    /**
     * Restores a fresh machine to RUNNING for a run storage already holds as running.
     *
     * This is not a shortcut around [beginCountdown] and [startRun]. Those two exist to
     * make a run official — the countdown is a screen the runner watches, and the start
     * is the moment a row is first written. Replaying them during recovery would act
     * out a countdown nobody saw and re-declare a run official that already is one.
     * Recovery is not starting a run; it is a new process catching up to a run that
     * never stopped, so it moves straight to the state storage reports.
     *
     * Only a machine that owns nothing may be restored. NO_SESSION is what a machine
     * built at process start looks like, and it is the only situation where adopting a
     * stored run cannot overwrite a live one this machine is already tracking.
     */
    internal fun restoreRunning() {
        restoreTo(RunSessionState.RUNNING)
    }

    /**
     * Restores a fresh machine to PAUSED for a run storage already holds as paused.
     *
     * Deliberately a direct move rather than a restore-to-RUNNING followed by
     * [pauseRun]. A synthetic pause would look identical in memory and be a lie in
     * every other respect: the run did not pause at recovery time, and any code that
     * later trusted the machine's history would be reading an event this app invented.
     * The stored run's own transition rows are the only record of when it really paused.
     */
    internal fun restorePaused() {
        restoreTo(RunSessionState.PAUSED)
    }

    /**
     * The shared guard behind both recovery entrances.
     *
     * Two named entrances rather than one public `restore(state)` on purpose. A generic
     * restore would accept any [RunSessionState] the caller happened to hold, which
     * reopens exactly what this class exists to prevent: NO_SESSION, COUNTDOWN and
     * COMPLETED would all become representable restore targets, and the compiler could
     * not object. Two entrances make the only two legal destinations the only two things
     * that can be asked for.
     *
     * It is also why nothing here mentions the storage enum. The machine describes the
     * session's stages and knows nothing about rows or columns; translating a stored
     * state into which entrance to call belongs to recovery, not to the rules.
     */
    private fun restoreTo(recoveredState: RunSessionState) {

        // A rejected restoration must leave the machine exactly as it was, so the check
        // happens before the assignment and nothing is written on the failing path.
        check(state == RunSessionState.NO_SESSION) {
            "A run can only be restored into a machine that owns no session, but this " +
                "machine is $state."
        }

        state = recoveredState
    }
}