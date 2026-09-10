package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import com.runstate.mobile.data.local.RunEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * What happened the last time this process tried to recover.
 *
 * Deliberately a history of the attempt rather than a description of the present. It
 * answers "has this coordinator earned the right to admit a run yet", which is a
 * different question from "what may the runner do right now" — that one is [RunAdmission].
 * Keeping them apart is what lets a failed attempt be retried without any of the
 * admission answers having to carry a retry concept they have no use for.
 */
internal sealed interface InitializationStatus {

    /** Recovery has not run, or a previous attempt was cancelled before it decided. */
    data object NotAttempted : InitializationStatus

    /**
     * Recovery was attempted and could not finish.
     *
     * @property cause the exact throwable storage raised, unwrapped and untranslated,
     *   because the caller deciding whether to retry or surface an error needs to know
     *   what actually went wrong.
     */
    data class Failed(val cause: Throwable) : InitializationStatus

    /**
     * Recovery ran and its result was fully applied.
     *
     * Completed describes the attempt, not the outcome. Finding nothing, adopting one
     * run and refusing an inconsistent database all complete: each is a decision
     * recovery genuinely reached, and what it decided is then reported by [RunAdmission].
     */
    data object Completed : InitializationStatus
}

/**
 * What the runner is allowed to do right now.
 *
 * Sealed, so every caller has to name each case. A screen that forgot to handle
 * [BlockedByInconsistentStorage] would otherwise be a screen that quietly offers a
 * start button over a database the app has already refused to interpret.
 */
internal sealed interface RunAdmission {

    /** Recovery has not successfully run, so nothing may be admitted yet. */
    data object NotInitialized : RunAdmission

    /** No run is live and none is blocking, so a countdown may begin. */
    data object ReadyForCountdown : RunAdmission

    /** A countdown is underway; the run is not official and nothing is stored yet. */
    data object CountdownInProgress : RunAdmission

    /**
     * A run is live and this coordinator holds its one owner.
     *
     * @property session the single owner for that run. It is handed out rather than
     *   copied or re-wrapped, because a second owner over one run is exactly what this
     *   coordinator exists to prevent.
     */
    data class RunInProgress(
        val session: ActiveRunSession
    ) : RunAdmission

    /**
     * Storage holds more than one unfinished run, so nothing may start.
     *
     * @property activeRuns every candidate, in the order discovery returned them.
     *   Complete rows rather than UUIDs, and evidence ordering rather than a ranking —
     *   being first here does not make a row the one to keep.
     */
    data class BlockedByInconsistentStorage(
        val activeRuns: List<RunEntity>
    ) : RunAdmission
}

/**
 * The one gate a run has to pass through to begin, for the life of the process.
 *
 * Every piece it coordinates already refuses to guess on its own, and each was built
 * without knowing about the others. [ActiveRunRecovery] decides what a stored active row
 * means but cannot stop a countdown starting beside it. [RunSessionStarter] serializes
 * starts through its own mutex but has no idea whether recovery has run. The state
 * machine enforces legal order for whichever machine it happens to be. What was missing
 * is the thing that puts them in order — recovery first, then admission, then at most one
 * live owner — and that is all this class is.
 *
 * ## Admission, not execution
 *
 * This owns two questions only: may a run begin, and which single [ActiveRunSession] is
 * the live one. It does not own the run while it happens. The timer, GPS capture, the
 * notification, screen-off behavior and keeping the process alive belong to the
 * foreground service that does not exist yet, and that service will *consume* this
 * coordinator rather than replace it: it will ask admission, hold the owner it is given,
 * and run the lifecycle through that owner exactly as any other caller would. Pause,
 * resume and complete are deliberately not routed through here — they stay on
 * [ActiveRunSession], which already serializes them and already writes durably first.
 *
 * ## One lock, held across the decision and the act
 *
 * Recovery's own limitation was that its NO_SESSION precondition was a check rather than
 * a reservation, and [ActiveRunRecovery.recover] suspends at the query, so a countdown
 * could interleave. The coordinator mutex closes that: the check and everything that acts
 * on it happen inside one held lock, so a start arriving mid-recovery waits and then sees
 * the state recovery left behind rather than a stale one.
 *
 * [RunSessionStarter] keeps its own separate start mutex. Two different locks, always
 * taken coordinator-first and never the same mutex twice, so there is one acquisition
 * order and no way to build a cycle. The starter's lock is not redundant — it remains the
 * defense for any caller holding a starter directly.
 *
 * ## What this does not yet establish
 *
 * **Nothing in the app calls it.** No Activity, no service and no bootstrap uses this
 * coordinator, so the production journey is not wired and no runner is protected by it
 * yet. It enforces the boundary for callers that go through it, and that is a real but
 * bounded claim.
 *
 * **It is not the only way to build these pieces.** [RunSessionStarter] and
 * [ActiveRunRecovery] have internal constructors, which stops code outside this module
 * from assembling a second unmanaged owner but not code inside it — the tests do exactly
 * that on purpose. Single ownership in production depends on the upcoming UI and
 * bootstrap slice using this coordinator exclusively.
 *
 * **The starter's cancellation gap survives here.** If a coroutine is cancelled after
 * [RunSessionStarter] has durably inserted the run but before the new owner is published
 * back to this coordinator, storage holds an active row this process does not hold an
 * owner for. The next process's [initialize] will find and adopt it, but this process
 * cannot rediscover it on its own. That is left honestly open: `NonCancellable`, a
 * compensating delete or a second speculative recovery pass would each hide the case
 * rather than resolve it, and a compensating write would erase a run the phone has
 * already durably recorded.
 */
internal class RunSessionCoordinator(private val runDao: RunDao) {

    /**
     * Serializes every admission decision and every act taken on one.
     *
     * Held across the whole of [initialize], [admission], [beginCountdown] and [start],
     * because in each of them the state read at the start has to still be true at the
     * end. A lock released between the check and the act would leave exactly the gap
     * recovery already documented.
     */
    private val coordinatorLock = Mutex()

    /**
     * The machine for the current run cycle.
     *
     * A `var` because a machine is per-cycle, not per-process: a completed one is retired
     * and replaced when the next countdown begins. It is never reset, and it is never
     * reused after COMPLETED.
     */
    private var stateMachine = RunSessionStateMachine()

    /** The starter bound to [stateMachine]. Replaced with it, so the two never disagree. */
    private var starter = RunSessionStarter(stateMachine, runDao)

    /** The one live owner, or null when no run is live in this cycle. */
    private var activeSession: ActiveRunSession? = null

    /** Candidates from a refused multi-row database. Non-empty means nothing may start. */
    private var inconsistentActiveRuns: List<RunEntity> = emptyList()

    /** What the last recovery attempt did. Never `Completed` before its result is applied. */
    private var initializationStatus: InitializationStatus = InitializationStatus.NotAttempted

    /**
     * Runs recovery once, and records what it found.
     *
     * Safe to call repeatedly. After a completed attempt this returns immediately without
     * touching Room, so a caller that cannot easily tell whether startup already ran may
     * simply call it — re-querying would risk adopting a second answer over the first.
     * After a failure it genuinely retries, because a failure is usually the database
     * being briefly unavailable rather than a permanent verdict.
     *
     * @return [InitializationStatus.Completed] when recovery reached a decision — including
     *   deciding the database is inconsistent — or [InitializationStatus.Failed] carrying
     *   the exact cause when it could not.
     * @throws CancellationException if the caller is cancelled, which leaves the attempt
     *   retryable rather than recording it as a failure.
     */
    suspend fun initialize(): InitializationStatus = coordinatorLock.withLock {

        // An attempt that already reached a decision is not repeated. Querying again
        // could return a different database than the one this process built its whole
        // in-memory picture from.
        if (initializationStatus == InitializationStatus.Completed) {
            return@withLock InitializationStatus.Completed
        }

        // A retry starts from a clean attempt rather than from the previous verdict, so
        // a `Failed` never lingers beside a recovery that is currently in flight.
        initializationStatus = InitializationStatus.NotAttempted

        val result = try {

            // Built here, from this coordinator's own machine and DAO, and not retained.
            // Recovery is a decision made once per attempt, not a collaborator with a
            // lifetime; keeping one would mean holding a second reference to the machine
            // that a retired cycle would then leave pointing at the wrong one.
            ActiveRunRecovery(stateMachine, runDao).recover()
        } catch (cancellation: CancellationException) {

            // Cancellation is not a verdict about the database. The status stays
            // NotAttempted, so the next caller performs a real attempt, and the
            // cancellation propagates rather than being absorbed into a result.
            throw cancellation
        } catch (failure: Exception) {

            // Only Exception. A JVM Error means the runtime itself is in trouble, and
            // catching one to relabel it as a recoverable initialization failure would
            // claim this coordinator can carry on from something it cannot.
            val failed = InitializationStatus.Failed(failure)
            initializationStatus = failed
            activeSession = null
            inconsistentActiveRuns = emptyList()

            // Reported, not thrown, and never dressed up as a completed attempt: the
            // caller has to be able to tell an empty database from an unreadable one.
            return@withLock failed
        }

        when (result) {
            is ActiveRunRecoveryResult.NothingToRecover -> {
                activeSession = null
                inconsistentActiveRuns = emptyList()
            }

            is ActiveRunRecoveryResult.Recovered -> {

                // The exact owner recovery built, over the machine it already restored.
                // Rebuilding one here would be a second owner over the same run.
                activeSession = result.session
                inconsistentActiveRuns = emptyList()
            }

            is ActiveRunRecoveryResult.InconsistentActiveRuns -> {

                // Evidence kept whole and in order; no candidate adopted. The database
                // is left exactly as found for whatever repair path is written later.
                activeSession = null
                inconsistentActiveRuns = result.activeRuns
            }
        }

        // Written last, on purpose. Completed is the promise that the result above has
        // already been applied, so it must never be observable before that is true.
        initializationStatus = InitializationStatus.Completed
        InitializationStatus.Completed
    }

    /**
     * Reports what the runner may do, as of the moment the lock was held.
     *
     * One conservative case is worth naming. [ActiveRunSession.state] is read here but is
     * protected by the session's own mutex rather than this one, so a completion running
     * concurrently can be observed mid-flight and answered [RunAdmission.RunInProgress]
     * when it is about to become [RunAdmission.ReadyForCountdown]. That direction is the
     * safe one and it is momentary. The reverse cannot happen: the session writes the
     * completion to Room before advancing its machine, so COMPLETED is never visible here
     * until the run is durably finished.
     */
    suspend fun admission(): RunAdmission = coordinatorLock.withLock {
        when (initializationStatus) {

            // Both mean the same thing to a caller: recovery has not established what
            // storage holds, so admitting a run could start a second live one.
            is InitializationStatus.NotAttempted,
            is InitializationStatus.Failed -> RunAdmission.NotInitialized

            is InitializationStatus.Completed -> admissionAfterInitialization()
        }
    }

    /**
     * The admission answer once recovery has decided, in priority order.
     *
     * Combinations this coordinator cannot legitimately produce are raised as internal
     * errors rather than mapped to whichever ordinary answer looks closest. A blocked
     * database reported as [RunAdmission.ReadyForCountdown] would be a defect that
     * presents as a working start button.
     */
    private fun admissionAfterInitialization(): RunAdmission {

        if (inconsistentActiveRuns.isNotEmpty()) {

            // Refusing and adopting are mutually exclusive; holding both would mean the
            // coordinator had adopted one of the rows it also says it cannot interpret.
            check(activeSession == null) {
                "Inconsistent storage is retained alongside an active session."
            }

            return RunAdmission.BlockedByInconsistentStorage(inconsistentActiveRuns)
        }

        val session = activeSession
        if (session != null) {
            return when (session.state) {

                RunSessionState.RUNNING,
                RunSessionState.PAUSED -> RunAdmission.RunInProgress(session)

                // The cycle is over but not yet retired. Retirement is deliberately not
                // done here — reporting is not the place to mutate — so the completed
                // machine is swapped out when the next countdown actually begins.
                RunSessionState.COMPLETED -> RunAdmission.ReadyForCountdown

                // An owner cannot exist for either of these: its own constructor refuses
                // them, so seeing one means the machine was mutated outside this lock.
                RunSessionState.NO_SESSION,
                RunSessionState.COUNTDOWN -> error(
                    "A retained session cannot be in ${session.state}."
                )
            }
        }

        return when (stateMachine.state) {
            RunSessionState.NO_SESSION -> RunAdmission.ReadyForCountdown

            RunSessionState.COUNTDOWN -> RunAdmission.CountdownInProgress

            // No session held, yet the machine claims a run exists. Nothing this class
            // does can produce that, so it is reported rather than smoothed over.
            RunSessionState.RUNNING,
            RunSessionState.PAUSED,
            RunSessionState.COMPLETED -> error(
                "No session is retained, but the machine is ${stateMachine.state}."
            )
        }
    }

    /**
     * Opens the countdown for the next run, retiring a finished cycle if one is held.
     *
     * @throws IllegalStateException if recovery has not completed, if storage is blocked,
     *   if a run is still live, or if a countdown is already underway. Nothing changes in
     *   any of those cases.
     */
    suspend fun beginCountdown() = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A countdown cannot begin before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A countdown cannot begin while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        val session = activeSession
        if (session != null) {

            // A live run is not something a new countdown may talk over.
            check(session.state == RunSessionState.COMPLETED) {
                "A countdown cannot begin while a run is ${session.state}."
            }

            retireCompletedCycle()
        }

        // Left to the machine's own guard rather than pre-checked here, so a second
        // countdown is refused by the same rule that governs every other caller.
        stateMachine.beginCountdown()
    }

    /**
     * Replaces the finished cycle with a fresh one.
     *
     * Retirement happens at this boundary and nowhere else. Doing it inside [start] would
     * mean a coordinator that had answered [RunAdmission.ReadyForCountdown] was still
     * holding a completed owner with no countdown underway, and doing it inside
     * [admission] would make a report mutate what it reports on. Here it is tied to the
     * one moment a new cycle genuinely begins.
     *
     * The old machine is discarded, never reset. A COMPLETED machine has no legal way
     * back to NO_SESSION and must not be given one: a reset would make "this run ended"
     * a reversible claim, and every rule the machine enforces depends on it not being.
     * The completed [ActiveRunSession] handed to earlier callers keeps pointing at that
     * retired machine and correctly keeps reporting COMPLETED forever.
     */
    private fun retireCompletedCycle() {
        activeSession = null
        stateMachine = RunSessionStateMachine()

        // Rebuilt with the new machine. A starter left pointing at the retired one would
        // start the next run into a machine nothing else is watching.
        starter = RunSessionStarter(stateMachine, runDao)
    }

    /**
     * Makes the prepared run official and retains its one owner.
     *
     * The insert happens through [RunSessionStarter] while this coordinator's lock is
     * still held, so the durable write and the publication of the owner are one
     * indivisible step from any other caller's point of view.
     *
     * @return the exact [ActiveRunSession] the starter produced. It is not re-wrapped and
     *   not rebuilt; the object the caller receives is the object this coordinator holds.
     * @throws IllegalStateException if recovery has not completed, if storage is blocked,
     *   if an owner is already held, or if no countdown is underway. A storage failure
     *   inside the starter propagates unchanged and leaves the countdown intact.
     */
    suspend fun start(preparedRun: RunEntity): ActiveRunSession = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A run cannot start before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A run cannot start while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        // Checked before the countdown state, because "a run is already live" is the more
        // specific reason and the more useful one to report.
        check(activeSession == null) {
            "A run cannot start while this coordinator already owns one."
        }

        check(stateMachine.state == RunSessionState.COUNTDOWN) {
            "A run can only start from a countdown, but this coordinator is " +
                "${stateMachine.state}."
        }

        // The starter keeps its own lock and its own checks. This is the second holder of
        // an uncontended lock rather than a duplicated guard: taken in one order, always
        // coordinator-first.
        val session = starter.start(preparedRun)

        // Retained inside the same lock the insert happened under, so no other caller can
        // observe a stored running run that this coordinator does not yet own.
        activeSession = session
        session
    }
}
