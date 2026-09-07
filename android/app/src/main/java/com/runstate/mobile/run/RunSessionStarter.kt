package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The boundary a run must cross to become official.
 *
 * RUNNING means "this run exists". Storage is what makes that true, so the row is
 * written first and the state machine is advanced only once the write has succeeded.
 * Ordering it the other way would let the screen show a live run that nothing on disk
 * backs: if the app died at that moment, the run would be gone after the runner had
 * already watched it start.
 *
 * The state machine itself only enforces legal transition order. It has no idea
 * storage exists, and this class does not change that — it wraps the machine rather
 * than modifying it. The machine also stays synchronous and unaware of coroutines;
 * all waiting happens here.
 */
class RunSessionStarter(
    private val stateMachine: RunSessionStateMachine,
    private val runDao: RunDao
) {

    /**
     * Serializes starts so two callers cannot both pass the COUNTDOWN check.
     *
     * The check, the insert and the transition are one indivisible operation. Without
     * this, a double-tapped start button could let both callers read COUNTDOWN before
     * either had written anything, and the same run would be saved twice under two
     * UUIDs. Holding the lock across the whole operation means the second caller reads
     * the state the first one left behind.
     *
     * The lock covers calls made through THIS starter instance. Production wiring must
     * therefore give the active-session owner one shared starter: separate instances
     * would hold separate mutexes and protect nothing from each other.
     */
    private val startLock = Mutex()

    /**
     * Saves the prepared run, moves the session into RUNNING, and hands back the one
     * object that owns the run from then on.
     *
     * [preparedRun] is inserted exactly as given. Its UUID and official start are
     * chosen before this call and are never regenerated here, so a retry after a
     * storage failure re-attempts the same run rather than creating a second one.
     *
     * A storage failure is allowed to propagate. Nothing is caught and nothing is
     * rolled back, because the state machine has not moved yet: the session is still
     * in COUNTDOWN, which is the honest description of a run that never got saved.
     * No owner is returned in that case — an owner exists only for a run that is both
     * stored and running.
     *
     * Known gap, deliberately left open: cancellation between the durable insert and
     * the in-memory transition leaves a saved active row behind that this object no
     * longer knows about. `NonCancellable` is not used here, because papering over it
     * would hide the case rather than resolve it. The real answer is the foreground
     * service owning the session and recovery adopting an already-saved active run at
     * startup; both are later work.
     *
     * @return the [ActiveRunSession] that owns this run for the rest of its life. It is
     *   bound to [preparedRun]'s UUID, and it is given this starter's own state machine
     *   and DAO rather than new ones, so the run has exactly one in-memory state and
     *   one route to storage. The caller that built this starter still holds that same
     *   machine and can mutate it directly; making the owner the only route to it is
     *   deferred.
     * @throws IllegalStateException if the session is not in COUNTDOWN — including
     *   when a concurrent caller already started the run — or if the prepared row does
     *   not describe the initial RUNNING moment. Nothing is inserted in either case.
     */
    suspend fun start(preparedRun: RunEntity): ActiveRunSession =
        startLock.withLock {

            // Countdown is the only stage a run may officially begin from. Checking
            // inside the lock is what makes a losing concurrent caller see RUNNING here
            // rather than a stale COUNTDOWN, so it is refused before touching storage.
            check(stateMachine.state == RunSessionState.COUNTDOWN) {
                "A run can only be saved and started from the countdown."
            }

            // The first row a run ever gets is its RUNNING row. A PAUSED or COMPLETED
            // row here would mean a run that was saved as already over.
            check(preparedRun.state == StoredRunState.RUNNING) {
                "A run must be saved in its initial RUNNING state."
            }

            // Nothing past the official start has been confirmed durable yet, so the
            // first checkpoint is the start itself. A later checkpoint would claim
            // recovery could trust time the run has not actually recorded.
            check(preparedRun.lastCheckpointEpochMillis == preparedRun.officialStartEpochMillis) {
                "A run's initial checkpoint must equal its official start."
            }

            // Durable first: if this throws, the session stays in COUNTDOWN.
            runDao.insert(preparedRun)

            // The run is on disk, so it may now officially be running.
            stateMachine.startRun()

            // Built last, and still inside the lock, so an owner can never be handed
            // out for a run that is not both stored and running. It receives the same
            // machine and the same DAO this starter used, because a second machine
            // would give one run two disagreeing in-memory states.
            ActiveRunSession(
                runId = preparedRun.runId,
                stateMachine = stateMachine,
                runDao = runDao
            )
        }
}
