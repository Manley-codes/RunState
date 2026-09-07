package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one object that owns a live run once it has officially started.
 *
 * Two halves already existed and did not know about each other. [RunDao] durably moves
 * a stored run through pause, resume and completion, and [RunSessionStateMachine]
 * independently enforces the same legal order in memory. This joins them, so one
 * runner tap becomes one decision rather than two that can disagree.
 *
 * ## Room stays the source of truth
 *
 * Every lifecycle operation writes to Room first and advances the machine only after
 * that write has succeeded. The in-memory state is therefore a follower, never an
 * authority: if storage refuses a transition, memory never claims it happened. This is
 * the same ordering [RunSessionStarter] uses to make a run official, continued for the
 * rest of the run's life.
 *
 * ## One UUID, no cached row
 *
 * The owner holds [runId] and nothing else about the run. It is immutable, so it cannot
 * be repointed at another run, and no lifecycle method accepts a run id — a caller may
 * supply a timestamp but never a target. Deliberately absent is any cached run entity:
 * a held snapshot would go stale the moment Room updated the row, and the app would
 * then have two versions of one run with no rule for which is right. When the current
 * row is needed, it is read from Room.
 *
 * ## What the mutex does and does not cover
 *
 * Each lifecycle operation is compound: read the in-memory state, write to Room, then
 * change the in-memory state. Those steps have to be indivisible, because the state
 * read at the start must still be true at the end. [sessionLock] is held across the
 * whole sequence, so a second caller entering this owner waits and then reads the state
 * the first caller left behind rather than a stale one.
 *
 * The lock protects calls made through THIS owner instance. Production wiring must give
 * the live run exactly one owner; two owners over one run would hold two unrelated
 * mutexes and coordinate nothing between them. If that ever happened anyway, Room's
 * conditional writes remain the durable defense — the losing write matches no row and
 * the operation fails rather than corrupting the stored run — but the in-memory halves
 * would already have diverged, which is why single ownership is the real rule.
 *
 * Whoever constructs [RunSessionStarter] still holds the state machine this owner was
 * handed, and can mutate it directly, outside this lock. Restructuring that so the
 * machine is unreachable except through the owner is deferred.
 *
 * ## Known gap, deliberately left open
 *
 * Cancellation between the committed Room write and the synchronous machine call leaves
 * memory temporarily behind storage: the pause is durable, but this object still says
 * RUNNING. `NonCancellable`, an automatic retry or a compensating write would each hide
 * that rather than resolve it, and a compensating write would additionally undo a fact
 * the phone has already durably recorded. Recovery — reading the stored run back and
 * restoring memory from it — is the next slice, and it is the correct answer.
 *
 * ## Not yet a UI model
 *
 * [state] is a plain read of the machine's current value. It is not Compose-observable,
 * it recomposes nothing, and it is read outside the lock, so a read taken while another
 * coroutine is mid-operation can be the value from just before that operation finished.
 * Giving the interface an observable, consistently published state belongs to UI
 * wiring, not here.
 *
 * ## One thing the timeline already implies
 *
 * A run may only be completed from PAUSED. So the final running interval ended at the
 * final pause, not at the finish passed to [complete] — the hold-to-end gesture happens
 * while the run is already paused. Anything later computing active duration must sum
 * the running intervals from the transition history and must not treat the finish as
 * the end of a running interval.
 */
class ActiveRunSession internal constructor(

    /**
     * The permanent UUID of the run this owner is bound to, for its whole life.
     *
     * Publicly readable so callers can identify the run, and immutable so nothing can
     * retarget the owner at a different one.
     */
    val runId: String,

    private val stateMachine: RunSessionStateMachine,
    private val runDao: RunDao
) {

    init {

        // An owner only exists for a run that is already official and live. Accepting a
        // machine in any other state would let this object be built for a run that was
        // never saved, or adopt one from PAUSED — and adopting is recovery, which has
        // to read storage rather than trust whatever it was handed.
        check(stateMachine.state == RunSessionState.RUNNING) {
            "An active run session can only own a run that is already RUNNING."
        }
    }

    /** Serializes the read-write-advance sequence of every lifecycle operation. */
    private val sessionLock = Mutex()

    /** The session stage this owner currently believes the run is in. */
    val state: RunSessionState
        get() = stateMachine.state

    /**
     * Durably pauses this run, then stops in-memory active time.
     *
     * A storage failure propagates unchanged and leaves the session RUNNING, because a
     * pause storage never recorded did not happen.
     *
     * @throws IllegalStateException if this session is not RUNNING, or if Room refuses
     *   the pause. Nothing in memory changes in either case.
     */
    suspend fun pause(pausedAtEpochMillis: Long) {
        sessionLock.withLock {
            check(stateMachine.state == RunSessionState.RUNNING) {
                "A run can only be paused while it is running, but this session is " +
                    "${stateMachine.state}."
            }

            // Durable first: if this throws, the session honestly stays RUNNING.
            runDao.pauseRun(runId, pausedAtEpochMillis)

            // The pause is on disk, so memory may now agree with it.
            stateMachine.pauseRun()
        }
    }

    /**
     * Durably resumes this run, then restarts in-memory active time.
     *
     * @throws IllegalStateException if this session is not PAUSED, or if Room refuses
     *   the resume. Nothing in memory changes in either case.
     */
    suspend fun resume(resumedAtEpochMillis: Long) {
        sessionLock.withLock {
            check(stateMachine.state == RunSessionState.PAUSED) {
                "A run can only be resumed while it is paused, but this session is " +
                    "${stateMachine.state}."
            }

            runDao.resumeRun(runId, resumedAtEpochMillis)

            stateMachine.resumeRun()
        }
    }

    /**
     * Durably ends this run, then marks the session completed.
     *
     * [finishEpochMillis] is when the runner confirmed the end. It is later than the
     * final pause that stopped active time; both are stored, and they are not the same
     * instant.
     *
     * @throws IllegalStateException if this session is not PAUSED, or if Room refuses
     *   the completion. Nothing in memory changes in either case.
     */
    suspend fun complete(finishEpochMillis: Long) {
        sessionLock.withLock {
            check(stateMachine.state == RunSessionState.PAUSED) {
                "A run can only be completed while it is paused, but this session is " +
                    "${stateMachine.state}."
            }

            runDao.completeRun(runId, finishEpochMillis)

            stateMachine.completeRun()
        }
    }
}
