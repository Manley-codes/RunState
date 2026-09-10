package com.runstate.mobile.run

import com.runstate.mobile.data.local.RunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.StoredRunState

/**
 * What storage turned out to be holding when recovery looked.
 *
 * Three outcomes, kept distinct on purpose. [RunDao.findActiveRuns] already refuses to
 * collapse "nothing unfinished" and "several unfinished, here is one" into one shape,
 * and this type carries that same honesty up to the caller: an empty database, a single
 * adoptable run and a stored state that should be impossible are three different
 * situations, and no caller should be able to confuse them by accident.
 *
 * Sealed, so a `when` over the result has to name every case. Adding a fourth outcome
 * later becomes a compile error at each place that decides what to do about one, rather
 * than a silently missed branch.
 */
sealed interface ActiveRunRecoveryResult {

    /**
     * Storage holds no unfinished run, so there is nothing to adopt.
     *
     * This is the ordinary case — the app was closed normally after the last run ended,
     * or it has never recorded one. The state machine is left in NO_SESSION, which is
     * already the correct description of a phone with no run in progress.
     */
    object NothingToRecover : ActiveRunRecoveryResult

    /**
     * Exactly one unfinished run was found, and it is now owned again.
     *
     * @property runAtRecovery the stored row exactly as discovery read it. It is named
     *   for *when* it was read rather than for where it came from, because that is the
     *   property that matters to anyone holding it: it is a snapshot taken at recovery
     *   time, and the first pause or resume through [session] makes it stale. Calling it
     *   `storedRun` would invite reading it as the run's current stored state, which it
     *   stops being almost immediately. When the live row is needed, it must be read
     *   from the DAO again.
     * @property session the owner bound to that run's UUID, sharing the same state
     *   machine and DAO recovery was given, so the recovered run has exactly one
     *   in-memory state and one route to storage.
     */
    data class Recovered(
        val runAtRecovery: RunEntity,
        val session: ActiveRunSession
    ) : ActiveRunRecoveryResult

    /**
     * Storage holds more than one unfinished run, which should never happen.
     *
     * @property activeRuns every candidate discovery returned, in the order it returned
     *   them. Complete entities rather than a list of UUIDs, because this result exists
     *   to be looked at: whoever investigates needs each row's state, official start,
     *   timezone and checkpoint to work out how two runs came to be live at once, and a
     *   bare UUID list would force a second round of queries to learn anything at all.
     *
     *   The discovery ordering is preserved exactly. It is a stable, total ordering, so
     *   two reports of the same bad data describe it the same way — but it is evidence
     *   ordering, not a ranking, and being first in this list means nothing.
     */
    data class InconsistentActiveRuns(
        val activeRuns: List<RunEntity>
    ) : ActiveRunRecoveryResult
}

/**
 * Decides what to do about a run the phone was still in the middle of.
 *
 * The pieces this joins already exist and each refuses to guess on its own.
 * [RunDao.findActiveRuns] reports every unfinished row and explicitly does not choose
 * among them; [RunSessionStateMachine] can be restored directly into RUNNING or PAUSED
 * but has no idea what storage holds; [ActiveRunSession] will own a run in either of
 * those states but only if something already read that state out of storage. This class
 * is the something. It reads, it decides, and it hands back one of three answers.
 *
 * ## It reads and restores; it never writes
 *
 * Recovery performs no database writes at all — no repair, no completion of a stale
 * row, no deduplication, no checkpoint touch-up. The run on disk is the record of what
 * actually happened, and the only legitimate way to change it is a real lifecycle
 * action the runner takes afterwards through the returned owner. Everything this class
 * changes is in memory, and only the state machine it was handed.
 *
 * ## Where the missing lock now lives
 *
 * Two limitations recorded here were about ordering, and [RunSessionCoordinator] now
 * answers both. The NO_SESSION precondition below is still only a check — it reports what
 * the machine held at the instant it was asked and reserves nothing — and [recover] still
 * suspends at the query, so a countdown could in principle interleave with it. What
 * changed is that the coordinator holds one lock across recovery, admission and start,
 * so a start arriving mid-recovery waits rather than interleaving. That lock is
 * deliberately not moved in here: recovery is one decision, and a mutex inside it could
 * only protect this call rather than the sequence the ordering problem actually spans.
 *
 * ## Known limitations, deliberately left open
 *
 * - **No Android entry point calls initialization yet.** A production database builder
 *   now exists and the coordinator now sequences recovery against starting, but no
 *   Activity, service or bootstrap invokes it, so recovery still runs in tests and
 *   nowhere else. Making it happen when the app launches is separate work.
 * - **This is not process death.** Restoring from a stored row proves the decision
 *   logic and, in the instrumented test, proves a run survives a closed and reopened
 *   database file. Neither one is the Android system killing the app and rebuilding it,
 *   and no test here claims to be.
 */
class ActiveRunRecovery internal constructor(
    private val stateMachine: RunSessionStateMachine,
    private val runDao: RunDao
) {

    /**
     * Looks for an unfinished run and adopts it when there is exactly one.
     *
     * A failure reading storage, or a stored row too malformed to construct, propagates
     * unchanged. Neither is caught and neither is turned into [NothingToRecover]: a
     * database that could not be read is not a database that is empty, and reporting it
     * as empty would silently abandon a run the runner may still be on.
     *
     * @return [ActiveRunRecoveryResult.NothingToRecover] when storage holds no
     *   unfinished run, [ActiveRunRecoveryResult.Recovered] when it holds exactly one,
     *   or [ActiveRunRecoveryResult.InconsistentActiveRuns] when it holds more.
     * @throws IllegalStateException if this recovery's state machine is not NO_SESSION,
     *   in which case storage is never queried at all.
     */
    suspend fun recover(): ActiveRunRecoveryResult {

        // Asked before the query, so a machine that already owns a run costs nothing to
        // refuse. Restoring into a live machine would overwrite a run in progress with
        // whatever happened to be on disk, which is worse than not recovering at all.
        check(stateMachine.state == RunSessionState.NO_SESSION) {
            "Recovery can only restore into a machine that owns no session, but this " +
                "machine is ${stateMachine.state}."
        }

        // Exactly one query. Asking twice could see two different answers and decide on
        // a state that was never true at any single moment.
        val activeRuns = runDao.findActiveRuns()

        if (activeRuns.isEmpty()) {

            // Nothing unfinished, so the machine stays exactly as it was found.
            return ActiveRunRecoveryResult.NothingToRecover
        }

        if (activeRuns.size > 1) {

            // Refuse rather than resolve. The oldest is not more correct than the
            // newest — two live runs mean the app already lost track of which one the
            // runner is on, and adopting either would turn an inconsistency the app can
            // still report into a wrong answer it silently commits to. The machine and
            // the database are both left untouched, so the evidence survives for
            // whatever repair path is eventually written.
            //
            // Copied, so the reported candidates cannot change afterwards through a
            // reference the caller does not know it shares.
            return ActiveRunRecoveryResult.InconsistentActiveRuns(activeRuns.toList())
        }

        val runAtRecovery = activeRuns.single()

        // Straight to the stored state. There is no countdown to replay, because the
        // run became official long ago, and no synthetic pause to invent, because the
        // row's own state already says where it is.
        when (runAtRecovery.state) {
            StoredRunState.RUNNING -> stateMachine.restoreRunning()

            StoredRunState.PAUSED -> stateMachine.restorePaused()

            // Unreachable through the real DAO and through the faithful fake: discovery
            // asks for RUNNING or PAUSED and cannot match a COMPLETED row. It is named
            // anyway because an exhaustive `when` is what makes a future fourth stored
            // state a compile error here instead of a missed case.
            //
            // If it ever did happen, the query contract would be broken, and the run in
            // hand would be finished history being offered for adoption. Returning
            // NothingToRecover would hide that behind the most ordinary-looking result
            // there is, so it fails loudly instead. There is deliberately no test for
            // this branch: reaching it would mean building a fake that contradicts the
            // real DAO, and a fake that lies about the query is worse than an untested
            // guard against the query lying.
            StoredRunState.COMPLETED -> error(
                "Discovery returned COMPLETED run ${runAtRecovery.runId}, which breaks " +
                    "the active-run query contract."
            )
        }

        // Built after the restoration, so the owner's own constructor guard sees the
        // recovered state. It gets this recovery's machine and DAO rather than new
        // ones, for the same reason the start boundary does: one run, one in-memory
        // state, one route to storage. It is bound to the UUID alone — the entity above
        // goes to the caller as a snapshot and is not cached in here.
        val session = ActiveRunSession(
            runId = runAtRecovery.runId,
            stateMachine = stateMachine,
            runDao = runDao
        )

        return ActiveRunRecoveryResult.Recovered(
            runAtRecovery = runAtRecovery,
            session = session
        )
    }
}
