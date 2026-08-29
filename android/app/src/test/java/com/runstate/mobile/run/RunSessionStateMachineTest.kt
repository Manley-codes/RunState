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
}