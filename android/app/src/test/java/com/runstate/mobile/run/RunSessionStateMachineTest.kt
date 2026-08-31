package com.runstate.mobile.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Checks the run-state rules without needing an Android phone or emulator.
 */
class RunSessionStateMachineTest {

    /**
     * Proves that the first approved transition works correctly.
     */
    @Test
    fun `begin countdown moves from no session to countdown`() {

        // Arrange: create a fresh state machine in its default state.
        val machine = RunSessionStateMachine()

        // Confirm that every new machine begins with no active run.
        assertEquals(RunSessionState.NO_SESSION, machine.state)

        // Act: request the first approved state change.
        machine.beginCountdown()

        // Assert: verify that the method produced the expected result.
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves that the same countdown cannot be started twice.
     */
    @Test
    fun `begin countdown cannot run twice`() {

        // Arrange: create a machine and legally move it into COUNTDOWN.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()

        // Act and Assert: the second request must produce an IllegalStateException.
        assertThrows(IllegalStateException::class.java) {
            machine.beginCountdown()
        }

        // Confirm that the rejected request did not corrupt or change the state.
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves that the run may enter RUNNING after the countdown.
     *
     * This checks only the transition rule. It does not save a Room record yet.
     */
    @Test
    fun `start run moves from countdown to running`() {

        // Arrange: create a machine and move through the required countdown stage.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()

        // Act: begin the official run.
        machine.startRun()

        // Assert: verify that the state is now RUNNING.
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves that a run cannot skip directly from NO_SESSION to RUNNING.
     */
    @Test
    fun `start run cannot skip countdown`() {

        // Arrange: create a fresh machine that is still in NO_SESSION.
        val machine = RunSessionStateMachine()

        // Act and Assert: starting a run without countdown must be rejected.
        assertThrows(IllegalStateException::class.java) {
            machine.startRun()
        }

        // Confirm that the rejected request left the original state unchanged.
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves that an active run may move into PAUSED.
     */
    @Test
    fun `pause run moves from running to paused`() {
        // Arrange: reach RUNNING through the required legal path.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()

        // Act: pause the active run.
        machine.pauseRun()

        // Assert: confirm that the state changed to PAUSED.
        assertEquals(RunSessionState.PAUSED, machine.state)
    }

    /**
     * Proves that the visual countdown cannot be paused.
     */
    @Test
    fun `pause run cannot happen during countdown`() {
        // Arrange: begin the countdown without starting the official run.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()

        // Act and Assert: pausing during countdown must be rejected.
        assertThrows(IllegalStateException::class.java) {
            machine.pauseRun()
        }

        // The rejected request must leave the countdown unchanged.
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves that pausing is rejected when no run session exists.
     */
    @Test
    fun `pause run cannot happen when no session exists`() {
        // Arrange: a new machine begins in NO_SESSION.
        val machine = RunSessionStateMachine()

        // Act and Assert: pausing must throw an IllegalStateException.
        assertThrows(IllegalStateException::class.java) {
            machine.pauseRun()
        }

        // The rejected request must not change the original state.
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves that an already paused run cannot be paused again.
     */
    @Test
    fun `pause run cannot happen twice`() {
        // Arrange: legally reach PAUSED.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()
        machine.pauseRun()

        // Act and Assert: a second pause request must be rejected.
        assertThrows(IllegalStateException::class.java) {
            machine.pauseRun()
        }

        // The rejected request must leave the run paused.
        assertEquals(RunSessionState.PAUSED, machine.state)
    }

    /**
     * Proves that a paused run may return to RUNNING.
     */
    @Test
    fun `resume run moves from paused to running`() {
        // Arrange: legally reach PAUSED.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()
        machine.pauseRun()

        // Act: resume the same run.
        machine.resumeRun()

        // Assert: the session is active again.
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves that resuming is rejected when no run session exists.
     */
    @Test
    fun `resume run cannot happen when no session exists`() {
        // Arrange: a new machine begins in NO_SESSION.
        val machine = RunSessionStateMachine()

        // Act and Assert: there is no run available to resume.
        assertThrows(IllegalStateException::class.java) {
            machine.resumeRun()
        }

        // The rejected request must preserve the original state.
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves that the visual countdown cannot be resumed.
     */
    @Test
    fun `resume run cannot happen during countdown`() {
        // Arrange: begin the countdown without starting the official run.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()

        // Act and Assert: there is no paused run to resume.
        assertThrows(IllegalStateException::class.java) {
            machine.resumeRun()
        }

        // The rejected request must leave the countdown unchanged.
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves that an active run cannot be resumed again.
     */
    @Test
    fun `resume run cannot happen while already running`() {
        // Arrange: legally reach RUNNING.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()

        // Act and Assert: an active run is not available to resume.
        assertThrows(IllegalStateException::class.java) {
            machine.resumeRun()
        }

        // The rejected request must leave the run active.
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves that a paused run may enter COMPLETED.
     */
    @Test
    fun `complete run moves from paused to completed`() {
        // Arrange: legally reach PAUSED.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()
        machine.pauseRun()

        // Act: finish the paused run.
        machine.completeRun()

        // Assert: the run lifecycle has ended.
        assertEquals(RunSessionState.COMPLETED, machine.state)
    }

    /**
     * Proves that completion is rejected when no run session exists.
     */
    @Test
    fun `complete run cannot happen when no session exists`() {
        // Arrange: a new machine begins in NO_SESSION.
        val machine = RunSessionStateMachine()

        // Act and Assert: there is no run available to complete.
        assertThrows(IllegalStateException::class.java) {
            machine.completeRun()
        }

        // The rejected request must preserve the original state.
        assertEquals(RunSessionState.NO_SESSION, machine.state)
    }

    /**
     * Proves that the visual countdown cannot be completed.
     */
    @Test
    fun `complete run cannot happen during countdown`() {
        // Arrange: begin the countdown without starting the official run.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()

        // Act and Assert: there is no official run to complete.
        assertThrows(IllegalStateException::class.java) {
            machine.completeRun()
        }

        // The rejected request must leave the countdown unchanged.
        assertEquals(RunSessionState.COUNTDOWN, machine.state)
    }

    /**
     * Proves that an active run must be paused before completion.
     */
    @Test
    fun `complete run cannot happen while running`() {
        // Arrange: legally reach RUNNING.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()

        // Act and Assert: completion must wait for the paused state.
        assertThrows(IllegalStateException::class.java) {
            machine.completeRun()
        }

        // The rejected request must leave the run active.
        assertEquals(RunSessionState.RUNNING, machine.state)
    }

    /**
     * Proves that a completed run cannot be completed again.
     */
    @Test
    fun `complete run cannot happen twice`() {
        // Arrange: legally complete the run.
        val machine = RunSessionStateMachine()
        machine.beginCountdown()
        machine.startRun()
        machine.pauseRun()
        machine.completeRun()

        // Act and Assert: a second completion request must be rejected.
        assertThrows(IllegalStateException::class.java) {
            machine.completeRun()
        }

        // The rejected request must leave the run completed.
        assertEquals(RunSessionState.COMPLETED, machine.state)
    }
}