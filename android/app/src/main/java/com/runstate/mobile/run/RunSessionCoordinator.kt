package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import com.runstate.mobile.data.local.RunEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
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
     * This cycle's run has durably finished, and nothing new has begun yet.
     *
     * Distinct from [ReadyForCountdown] because the runner is owed a truthful "saved"
     * before being offered another start. The next countdown still retires this cycle,
     * exactly as it did when a completed owner was reported as ready.
     *
     * @property session the completed owner, still pointing at its finished machine.
     */
    data class RunCompleted(
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
 * Both coordinator answers, read at one moment.
 *
 * The screen needs both values — [InitializationStatus] separates a spinner from a retry
 * button, [RunAdmission] says what may happen next — and they are only meaningful together
 * if nothing changed between reading them. Asking for them in two separate calls released
 * the coordinator lock in between, so a recovery or an action could land in the gap and
 * produce a pair no single moment ever held. Producing them together inside one lock
 * acquisition makes that pairing impossible rather than unlikely.
 *
 * The same reasoning covers the three progress fields. Whether a start or a lifecycle action
 * is still writing, or last failed, is only meaningful beside the admission it belongs to,
 * so they are read in the same lock acquisition rather than asked for separately.
 *
 * @property startAttemptPhase whether this cycle's official start is writing or last failed.
 * @property reservedAction the lifecycle action whose durable write is underway, if any.
 * @property lastActionFailed the most recent lifecycle action that storage refused, kept
 *   until a later action succeeds.
 */
internal data class RunJourneySnapshot(
    val initializationStatus: InitializationStatus,
    val admission: RunAdmission,
    val startAttemptPhase: StartAttemptPhase = StartAttemptPhase.None,
    val reservedAction: RunActionKind? = null,
    val lastActionFailed: RunActionKind? = null
)

/**
 * Where this cycle's official start stands, separately from what the runner may do.
 *
 * Kept apart from [RunAdmission] for the same reason [InitializationStatus] is: while a start
 * is writing, or after it failed, the runner is still in the countdown as far as admission is
 * concerned. What differs is what the screen may honestly say about it.
 */
internal enum class StartAttemptPhase {

    /** No start is underway and the last one did not fail. */
    None,

    /** The start's insert is running in the application scope. */
    InProgress,

    /** The last start attempt failed with an ordinary exception; the prepared row is kept. */
    Failed
}

/** The three durable lifecycle actions a live run can be asked to take. */
internal enum class RunActionKind {
    PAUSE,
    RESUME,
    COMPLETE
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
 * and run the lifecycle through this coordinator exactly as any other caller would.
 *
 * Pause, resume and complete are requested here — see [requestAction] — but still
 * *performed* by [ActiveRunSession], which serializes them and writes durably first. What
 * the coordinator adds is ownership of the write by the process rather than the screen, one
 * reservation at a time, and honest reporting of a write still in flight or refused.
 *
 * ## One lock, held across the decision and the act
 *
 * Recovery's own limitation was that its NO_SESSION precondition was a check rather than
 * a reservation, and [ActiveRunRecovery.recover] suspends at the query, so a countdown
 * could interleave. The coordinator mutex closes that: the check and everything that acts
 * on it happen inside one held lock, so a start arriving mid-recovery waits and then sees
 * the state recovery left behind rather than a stale one.
 *
 * The official start is the one deliberate exception, and it is replaced by a reservation
 * rather than left open — see [requestStart]. The Room insert runs outside this lock so
 * that a second request can still get in and receive the attempt already underway.
 *
 * [RunSessionStarter] keeps its own separate start mutex. It is never taken while this
 * coordinator's lock is held, so there is no acquisition order to get wrong and no way to
 * build a cycle. The starter's lock is not redundant — it remains the defense for any
 * caller holding a starter directly.
 *
 * ## The official start belongs to the process, not to a screen
 *
 * A screen asks for the start and may wait for it, but it does not own the work. The
 * attempt runs in the application-owned [applicationScope], so a rotation, a back gesture
 * or a destroyed Activity cancels only the screen's *waiting*, never the insert itself.
 * That is what stops a run from being half-made — durably stored, but abandoned before
 * anyone took ownership of it — merely because the UI that asked went away.
 *
 * ## Who calls it, and what that does not yet cover
 *
 * `MainActivity` initializes this coordinator and drives the whole visible fixture journey
 * through it: countdown, official start, pause, resume, hold-to-stop completion and the next
 * countdown. What is not yet wired is a foreground service holding a live run, so a run
 * survives only as long as this process does, plus whatever recovery rebuilds from storage.
 *
 * **It is not the only way to build these pieces.** [RunSessionStarter] and
 * [ActiveRunRecovery] have internal constructors, which stops code outside this module
 * from assembling a second unmanaged owner but not code inside it — the tests do exactly
 * that on purpose. Single ownership in production depends on the UI and bootstrap using
 * this coordinator exclusively.
 *
 * **One cancellation gap is still left honestly open.** [applicationScope] is never
 * cancelled in production, so a start or lifecycle action always gets to publish its result.
 * If that scope ever were cancelled mid-write, the work could not reacquire the lock to
 * release its reservation, and `NonCancellable` is deliberately not used to force it. The
 * coordinator then refuses every further start and cancel — or every further lifecycle
 * action — in this process rather than guess whether the write landed; the next process's
 * [initialize] finds the truth in storage. A compensating delete or a speculative recovery pass would each hide the case
 * rather than resolve it, and a compensating write could erase a run the phone has already
 * durably recorded.
 *
 * @property runDao the one route to storage for recovery and every run admitted.
 * @property preparedRunFactory the only place a run's UUID, official start and zone are
 *   decided, and the clock lifecycle timestamps are read from. Injected so no coordinator
 *   method reaches for a random UUID or a system clock.
 * @property applicationScope the process-lifetime scope every durable write runs in. It must
 *   outlive every screen, and it should use a `SupervisorJob` so one failed start does not
 *   cancel the scope for every later one.
 */
internal class RunSessionCoordinator(
    private val runDao: RunDao,
    private val preparedRunFactory: PreparedRunFactory,
    private val applicationScope: CoroutineScope
) {

    /**
     * Serializes every admission decision and every act taken on one.
     *
     * Held across the whole of [initialize], [journeySnapshot], [beginCountdown] and
     * [cancelCountdown], because in each of them the state read at the start has to still
     * be true at the end. [requestStart] and [requestAction] hold it for their decision and
     * again for their result, but not across the write between them; [startReserved] and
     * [reservedAction] cover that gap.
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
     * The row this cycle's official start will insert, once one has been requested.
     *
     * Created on the first [requestStart] and then kept, so a failed insert is retried with
     * the exact same UUID, start, zone and checkpoint. Cleared when the start succeeds, or
     * when the cycle is discarded — at which point the runner's intention to start *that*
     * run is gone and the next cycle deserves a new identity.
     */
    private var preparedRun: RunEntity? = null

    /** The official start attempt currently underway, or null when none is. */
    private var pendingStart: Deferred<ActiveRunSession>? = null

    /**
     * True from the moment a start is admitted until its result has been applied.
     *
     * This is what stands in for the lock while the insert runs without it. While it is
     * set, the current machine and starter are spoken for: nothing may cancel the countdown,
     * begin another one, or read the machine as if its state were settled — it may already
     * be RUNNING with the owner not yet published.
     */
    private var startReserved = false

    /**
     * True when this cycle's last start attempt failed with an ordinary exception.
     *
     * Held here rather than by whoever awaited the attempt, so a screen that was rotated or
     * destroyed while the insert ran still learns it failed. Cleared when a genuine retry
     * begins, when the start succeeds, and when the cycle is discarded.
     */
    private var startFailed = false

    /** The lifecycle action write currently underway, or null when none is. */
    private var pendingAction: Deferred<Unit>? = null

    /**
     * Which lifecycle action is writing, from admission until its result is applied.
     *
     * The action counterpart of [startReserved]: while set, the session may already have
     * advanced in memory with its write not yet reported here, so reporting uses the state
     * that was visible before the action began.
     */
    private var reservedAction: RunActionKind? = null

    /** The last lifecycle action storage refused, kept until a later action succeeds. */
    private var lastActionFailed: RunActionKind? = null

    /**
     * Runs recovery once, records what it found, and reports the result.
     *
     * Safe to call repeatedly. After a completed attempt this returns immediately without
     * touching Room, so a caller that cannot easily tell whether startup already ran may
     * simply call it — re-querying would risk adopting a second answer over the first.
     * After a failure it genuinely retries, because a failure is usually the database
     * being briefly unavailable rather than a permanent verdict.
     *
     * @return a [RunJourneySnapshot] taken inside the same lock acquisition that applied
     *   recovery, so the admission it reports is the one this attempt produced. Its status
     *   is [InitializationStatus.Completed] when recovery reached a decision — including
     *   deciding the database is inconsistent — or [InitializationStatus.Failed] carrying
     *   the exact cause when it could not.
     * @throws CancellationException if the caller is cancelled, which leaves the attempt
     *   retryable rather than recording it as a failure.
     */
    suspend fun initialize(): RunJourneySnapshot = coordinatorLock.withLock {

        // An attempt that already reached a decision is not repeated. Querying again
        // could return a different database than the one this process built its whole
        // in-memory picture from.
        if (initializationStatus == InitializationStatus.Completed) {
            return@withLock snapshotUnderLock()
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
            return@withLock snapshotUnderLock()
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
        snapshotUnderLock()
    }

    /**
     * Reports both coordinator answers, as of the moment the lock was held.
     *
     * A pure read: no recovery is attempted and nothing is written, whatever the current
     * status. A coordinator that has not initialized reports that honestly rather than
     * initializing as a side effect of being asked.
     *
     * One conservative case is worth naming. [ActiveRunSession.state] is read here but is
     * protected by the session's own mutex rather than this one. For actions requested
     * through [requestAction] that no longer matters: while one is reserved, the admission
     * stays [RunAdmission.RunInProgress] and [RunJourneySnapshot.reservedAction] tells the
     * reader which pre-action state to show, even if the session has already advanced. Only
     * a caller driving the session directly, outside this coordinator, can still be observed
     * mid-flight — and even then COMPLETED is never visible before Room has it, because the
     * session writes first.
     */
    suspend fun journeySnapshot(): RunJourneySnapshot = coordinatorLock.withLock {
        snapshotUnderLock()
    }

    /** Both answers from the current fields. Only ever called with [coordinatorLock] held. */
    private fun snapshotUnderLock(): RunJourneySnapshot {
        val admission = when (initializationStatus) {

            // Both mean the same thing to a caller: recovery has not established what
            // storage holds, so admitting a run could start a second live one.
            is InitializationStatus.NotAttempted,
            is InitializationStatus.Failed -> RunAdmission.NotInitialized

            is InitializationStatus.Completed -> admissionAfterInitialization()
        }

        val startAttemptPhase = when {
            startReserved -> StartAttemptPhase.InProgress
            startFailed -> StartAttemptPhase.Failed
            else -> StartAttemptPhase.None
        }

        return RunJourneySnapshot(
            initializationStatus = initializationStatus,
            admission = admission,
            startAttemptPhase = startAttemptPhase,
            reservedAction = reservedAction,
            lastActionFailed = lastActionFailed
        )
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

        if (startReserved) {

            // The insert is running outside the lock. The machine may still be COUNTDOWN,
            // or may already be RUNNING with its owner not yet published back here, and
            // reading it now would report that second moment as an impossible state. Until
            // the result is applied, the conservative answer is the boundary the start
            // began from: no run is owned yet, and nothing new may be admitted.
            return RunAdmission.CountdownInProgress
        }

        val session = activeSession
        if (session != null) {

            if (reservedAction != null) {

                // The write is still running outside the lock. The session may already say
                // PAUSED or even COMPLETED, but nothing is reported as having happened until
                // its result is applied here, so the run is still simply in progress.
                return RunAdmission.RunInProgress(session)
            }

            return when (session.state) {

                RunSessionState.RUNNING,
                RunSessionState.PAUSED -> RunAdmission.RunInProgress(session)

                // The cycle is over but not yet retired. Retirement is deliberately not
                // done here — reporting is not the place to mutate — so the completed
                // machine is swapped out when the next countdown actually begins.
                RunSessionState.COMPLETED -> RunAdmission.RunCompleted(session)

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
     *   if an official start or lifecycle action is underway, if a run is still live, or if
     *   a countdown is already underway. Nothing changes in any of those cases.
     */
    suspend fun beginCountdown() = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A countdown cannot begin before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A countdown cannot begin while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        // Named explicitly rather than left to the machine, which may be mid-change while
        // the insert runs and would refuse for a misleading reason.
        check(!startReserved) {
            "A countdown cannot begin while an official start is underway."
        }

        // A completion still writing may already show COMPLETED in memory. Retiring the
        // cycle then would discard the owner before its result had been applied.
        check(reservedAction == null) {
            "A countdown cannot begin while a $reservedAction is still being saved."
        }

        val session = activeSession
        if (session != null) {

            // A live run is not something a new countdown may talk over.
            check(session.state == RunSessionState.COMPLETED) {
                "A countdown cannot begin while a run is ${session.state}."
            }

            beginFreshCycle()
        }

        // Left to the machine's own guard rather than pre-checked here, so a second
        // countdown is refused by the same rule that governs every other caller.
        stateMachine.beginCountdown()
    }

    /**
     * Abandons a countdown, returning the coordinator to a fresh NO_SESSION cycle.
     *
     * The exit that entering COUNTDOWN requires. A countdown is the one stage the runner
     * can be in with nothing durable behind it — no row, no UUID, no owner — so backing
     * out of it has to be possible and has to cost nothing. Without this, tapping Start
     * would strand the coordinator: COUNTDOWN has no legal transition back to NO_SESSION,
     * so the only ways forward would be starting a run the runner no longer wants or
     * killing the process.
     *
     * Nothing is read from or written to the database. There is nothing to undo, because
     * a countdown never stored anything in the first place — which is exactly why this
     * can be offered as a plain cancel rather than as a discard with consequences.
     *
     * A call outside COUNTDOWN fails rather than quietly doing nothing. Silently
     * succeeding would let a stray cancel look identical to a real one and, worse, would
     * make "cancel" a way to discard a machine that might be holding something.
     *
     * A cancel after a *failed* start is still a plain cancel: the insert never landed, so
     * the retained prepared row is discarded along with the cycle. A cancel while a start
     * is still underway is refused — see below.
     *
     * @throws IllegalStateException if recovery has not completed, if storage is blocked,
     *   if an official start is underway, if a session is held, or if no countdown is
     *   underway. Nothing changes in any of those cases.
     */
    suspend fun cancelCountdown() = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A countdown cannot be cancelled before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A countdown cannot be cancelled while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        // The start attempt captured this cycle's starter and machine and is using them
        // outside the lock right now. Replacing them would let that insert land and publish
        // an owner over a machine this coordinator had already thrown away — a stored,
        // running run with nothing tracking it. The insert may well succeed, so the cancel
        // is refused rather than raced; the caller learns the outcome from the start.
        check(!startReserved) {
            "A countdown cannot be cancelled while an official start is underway."
        }

        // A live or finished run is not a countdown, and cancelling must never become a
        // way to drop an owner this coordinator is responsible for.
        check(activeSession == null) {
            "A countdown cannot be cancelled while this coordinator owns a run."
        }

        check(stateMachine.state == RunSessionState.COUNTDOWN) {
            "A countdown can only be cancelled while one is underway, but this " +
                "coordinator is ${stateMachine.state}."
        }

        beginFreshCycle()
    }

    /**
     * Discards the current cycle and installs a fresh machine and starter.
     *
     * One helper for both exits from a cycle — a completed run being retired before the
     * next countdown, and a countdown being cancelled — because the replacement itself is
     * the same operation and a second copy of it could drift. What differs is when it is
     * allowed, and that stays with each caller, where the rules belong.
     *
     * Cycle replacement happens only at those two boundaries. Doing it inside
     * [requestStart] would mean a coordinator that had answered
     * [RunAdmission.ReadyForCountdown] was still holding a completed owner with no
     * countdown underway, and doing it inside [journeySnapshot] would make a report mutate
     * what it reports on.
     *
     * The prepared row goes with the cycle. It described the run *this* cycle was about to
     * make official; a later cycle is a new decision to run and gets a new identity.
     *
     * The old machine is discarded, never reset. Neither COMPLETED nor COUNTDOWN has a
     * legal transition back to NO_SESSION and neither may be given one: a reset would
     * make "this run ended" a reversible claim, and every rule the machine enforces
     * depends on it not being. A completed [ActiveRunSession] handed to earlier callers
     * keeps pointing at its retired machine and correctly keeps reporting COMPLETED
     * forever.
     */
    private fun beginFreshCycle() {
        preparedRun = null
        startFailed = false
        lastActionFailed = null
        activeSession = null
        stateMachine = RunSessionStateMachine()

        // Rebuilt with the new machine. A starter left pointing at the retired one would
        // start the next run into a machine nothing else is watching.
        starter = RunSessionStarter(stateMachine, runDao)
    }

    /**
     * Asks for the countdown to become one official, durably stored run.
     *
     * The work happens in [applicationScope], not in the caller. The caller receives the
     * attempt as a [Deferred] it may await, and cancelling that caller cancels only its
     * waiting: the insert carries on, and its owner is still published here. That matters
     * because a screen can disappear at any moment, and a run that reached storage must
     * never be left without the one object responsible for it.
     *
     * ## Three steps, and why the lock is released in the middle
     *
     * 1. **Decide, under the lock.** Validate, create or reuse the prepared row, set
     *    [startReserved], capture this cycle's starter, launch the attempt and retain it.
     * 2. **Insert, outside the lock.** [RunSessionStarter.start] writes the row and moves
     *    the captured machine to RUNNING.
     * 3. **Apply the result, under the lock again.** Publish the owner on success, or keep
     *    the prepared row on failure; either way release the reservation.
     *
     * Holding the lock across step 2 would be simpler and wrong: a second request would
     * wait at the lock for the whole insert and could never see — or reuse — the attempt
     * already running. The reservation keeps the one guarantee the lock used to provide:
     * nobody replaces the machine and starter the attempt captured while it is using them.
     *
     * ## Repeated requests
     *
     * - While an attempt is active, every request returns that exact [Deferred]. No second
     *   row is prepared and no second insert begins.
     * - After a failed attempt, the next explicit request starts a new attempt with the
     *   *same* prepared row — same UUID, start, zone and checkpoint — so retrying can only
     *   ever produce one stored run.
     * - After a successful one, the prepared row is cleared and an owner exists, so a
     *   further request is refused as a start over a live run.
     *
     * @return the attempt, owned by [applicationScope]. It completes with the exact
     *   [ActiveRunSession] the starter produced and this coordinator retains, or fails with
     *   the starter's exception unchanged, leaving the countdown intact with no owner.
     * @throws IllegalStateException if recovery has not completed, if storage is blocked,
     *   if an owner is already held, if no countdown is underway, or if an earlier attempt
     *   was interrupted without reporting its result. Nothing is prepared or launched in
     *   any of those cases.
     */
    suspend fun requestStart(): Deferred<ActiveRunSession> = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A run cannot start before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A run cannot start while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        if (startReserved) {
            val attempt = checkNotNull(pendingStart) {
                "A start is reserved, but no attempt is retained."
            }

            // Only a normal success or an ordinary failure release the reservation, and
            // both do so before the attempt completes. A reserved attempt that has already
            // completed was therefore cancelled or died with an Error before it could say
            // whether the insert landed, and handing it out would pass that uncertainty to
            // a caller as if it were a result.
            check(attempt.isActive) {
                "An earlier start was interrupted before reporting whether the run was " +
                    "stored; this process cannot safely start another."
            }

            // The same attempt, not a second one: a double tap is one run.
            return@withLock attempt
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

        // Reused after a failure, created only the first time. This is the whole of retry
        // identity: the UUID and official start are decided once per cycle.
        val row = preparedRun ?: preparedRunFactory.create().also { preparedRun = it }

        // Reserved before anything is launched, so there is no moment at which an attempt
        // exists and the cycle is not yet protected. A previous failure is cleared here,
        // because this is the genuine retry it was waiting for.
        startReserved = true
        startFailed = false

        // Read now, under the lock that protects the field. The attempt runs later on
        // another coroutine without the lock, so it is handed the starter rather than
        // reading the field itself.
        val cycleStarter = starter

        val attempt = applicationScope.async {
            runStartAttempt(cycleStarter, row)
        }
        pendingStart = attempt
        attempt
    }

    /**
     * Steps 2 and 3 of [requestStart]: the insert, then the result applied under the lock.
     *
     * Runs as the body of the application-scope attempt, never in the requesting caller.
     */
    private suspend fun runStartAttempt(
        cycleStarter: RunSessionStarter,
        row: RunEntity
    ): ActiveRunSession {
        val session = try {

            // Outside the coordinator lock on purpose; the reservation guards the cycle.
            cycleStarter.start(row)
        } catch (cancellation: CancellationException) {

            // Not a verdict about storage. The insert may or may not have landed, so
            // nothing is recorded and the reservation is not released as if it had failed.
            // See the class notes on the gap this leaves if the application scope is ever
            // cancelled.
            throw cancellation
        } catch (failure: Exception) {
            coordinatorLock.withLock {

                // The prepared row is deliberately kept. The machine never left COUNTDOWN,
                // because the starter advances it only after a successful insert, so the
                // runner is still in the countdown and a retry is the same run.
                pendingStart = null
                startReserved = false

                // Recorded here, not by the caller, so it outlives whoever was waiting.
                startFailed = true
            }

            // Rethrown unchanged, so whoever awaits sees exactly what storage raised.
            throw failure
        }

        coordinatorLock.withLock {

            // The exact owner the starter built, over the machine this cycle still holds.
            activeSession = session

            // The intention has become a run. Keeping the row would let a later cycle
            // mistake it for a start still waiting to happen.
            preparedRun = null
            pendingStart = null
            startReserved = false
            startFailed = false
        }

        return session
    }

    /**
     * Asks the live run to pause, resume or complete, as work the process owns.
     *
     * The same three-step shape as [requestStart], for the same reason: a screen may ask and
     * wait, but a rotation or a destroyed Activity must cancel only its waiting, never a
     * write that may already have reached Room.
     *
     * 1. **Decide, under the lock.** Validate, capture the timestamp from the shared clock,
     *    capture the session, reserve the action, launch and retain the work.
     * 2. **Write, outside the lock.** [ActiveRunSession] writes Room first and advances its
     *    machine only after that succeeds.
     * 3. **Apply the result, under the lock again.** Release the reservation; on success
     *    clear any earlier failure, on ordinary failure record this one.
     *
     * ## One reservation at a time
     *
     * - While an action is reserved, a request for the *same* action returns that exact
     *   [Deferred]: a double tap on Pause is one pause.
     * - A *different* action is refused while one is reserved. Resume cannot be admitted
     *   against a pause whose outcome is still unknown.
     * - The reservation is checked before legality on purpose. Mid-write, the session may
     *   already say PAUSED, and a duplicate pause must still receive the attempt underway
     *   rather than be refused as a pause of a paused run.
     *
     * @return the write, owned by [applicationScope]. It completes normally once storage and
     *   memory agree, or fails with the session's exception unchanged.
     * @throws IllegalStateException if recovery has not completed, if storage is blocked, if
     *   no live run is held, if the action is not legal from the run's current state, if a
     *   different action is reserved, or if an earlier action was interrupted without
     *   reporting its result. Nothing is launched in any of those cases.
     */
    suspend fun requestAction(kind: RunActionKind): Deferred<Unit> = coordinatorLock.withLock {
        check(initializationStatus == InitializationStatus.Completed) {
            "A run action cannot be taken before recovery has completed."
        }

        check(inconsistentActiveRuns.isEmpty()) {
            "A run action cannot be taken while storage holds " +
                "${inconsistentActiveRuns.size} unfinished runs."
        }

        val session = checkNotNull(activeSession) {
            "A run action needs a live run, but this coordinator holds none."
        }

        val reserved = reservedAction
        if (reserved != null) {
            val attempt = checkNotNull(pendingAction) {
                "A $reserved is reserved, but no attempt is retained."
            }

            // As with a start: only a released reservation means the write reported its
            // result. A reserved attempt that has already completed was cancelled before it
            // could say whether the write landed, and nothing may be stacked on that.
            check(attempt.isActive) {
                "An earlier $reserved was interrupted before reporting whether it was " +
                    "saved; this process cannot safely take another run action."
            }

            check(reserved == kind) {
                "A $kind cannot begin while a $reserved is still being saved."
            }

            return@withLock attempt
        }

        val requiredState = when (kind) {
            RunActionKind.PAUSE -> RunSessionState.RUNNING
            RunActionKind.RESUME,
            RunActionKind.COMPLETE -> RunSessionState.PAUSED
        }
        check(session.state == requiredState) {
            "A $kind needs a $requiredState run, but this run is ${session.state}."
        }

        // Read once, here, from the same clock the official start came from. A retry after
        // a failure is a new request and reads it again: the runner pressed at a new moment.
        val occurredAt = preparedRunFactory.nowEpochMillis()

        // Reserved before launch, for the same reason as a start.
        reservedAction = kind

        val attempt = applicationScope.async {
            runLifecycleAction(session, kind, occurredAt)
        }
        pendingAction = attempt
        attempt
    }

    /**
     * Steps 2 and 3 of [requestAction]: the write, then the result applied under the lock.
     *
     * Runs as the body of the application-scope work, never in the requesting caller.
     */
    private suspend fun runLifecycleAction(
        session: ActiveRunSession,
        kind: RunActionKind,
        occurredAt: Long
    ) {
        try {
            when (kind) {
                RunActionKind.PAUSE -> session.pause(occurredAt)
                RunActionKind.RESUME -> session.resume(occurredAt)
                RunActionKind.COMPLETE -> session.complete(occurredAt)
            }
        } catch (cancellation: CancellationException) {

            // Not a verdict about storage, so not recorded as a failure, and the
            // reservation is deliberately left in place. See the class notes.
            throw cancellation
        } catch (failure: Exception) {
            coordinatorLock.withLock {

                // The session left its machine where it was, because it advances only after
                // a successful write, so the reported state is still the surviving one.
                pendingAction = null
                reservedAction = null
                lastActionFailed = kind
            }

            throw failure
        }

        coordinatorLock.withLock {
            pendingAction = null
            reservedAction = null
            lastActionFailed = null
        }
    }

    /**
     * Waits for whichever durable write is currently reserved, without starting anything.
     *
     * For a screen that arrives while a start or action is already underway — after a
     * rotation, say — and needs to know when to ask for a fresh snapshot. It never retries,
     * never throws the work's own failure, and returns at once when nothing is reserved; the
     * outcome is read from [journeySnapshot] afterwards, like every other outcome.
     */
    suspend fun awaitReservedWork() {
        val work = coordinatorLock.withLock { pendingStart ?: pendingAction }
        work?.join()
    }
}
