package com.runstate.mobile.run

import com.runstate.mobile.data.local.FakeRunDao
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks the one gate a run has to pass through, without a phone or a database.
 *
 * The parts underneath are already proved elsewhere: recovery's zero/one/many decision in
 * `ActiveRunRecoveryTest`, the save-before-RUNNING boundary in `RunSessionStarterTest`,
 * and the durable lifecycle in `ActiveRunSessionTest`. What only shows up here is the
 * ordering between them — that recovery runs before anything may start, that exactly one
 * owner is ever held, that a finished run is retired rather than reset, and that an official
 * start belongs to the application rather than to whoever asked for it.
 *
 * [FakeRunDao] is used because these tests need to force discovery and inserts to fail and
 * to park them mid-call, which is about the coordinator's sequencing rather than SQLite.
 *
 * ## The application scope used here
 *
 * Every coordinator gets its own `SupervisorJob` scope on `Dispatchers.Unconfined`. That is
 * the test stand-in for the process scope, chosen for determinism rather than speed: an
 * attempt starts running on the requesting thread until its first real suspension, and
 * resumes on whichever thread releases it — which in these tests is always the one test
 * thread. Nothing depends on a delay or a background thread. Crucially the scope's job is
 * not the caller's, so cancelling a caller cannot reach the attempt unless the coordinator
 * wrongly ran it in the caller.
 */
class RunSessionCoordinatorTest {

    private companion object {
        const val OFFICIAL_START = 1_756_000_000_000L

        /** A plausible first run: pause at one minute, end at two. */
        const val FIRST_PAUSE = OFFICIAL_START + 60_000L
        const val FIRST_FINISH = OFFICIAL_START + 120_000L

        /** The next run starts well after the first one ended. */
        const val SECOND_START = OFFICIAL_START + 600_000L

        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val SECOND_RUN_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val THIRD_RUN_ID = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"

        const val START_ZONE = "America/Chicago"
    }

    /** A wall clock a test can move forward, so a retry can prove it did not re-read it. */
    private class SettableClock(var nowMillis: Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    }

    /**
     * One coordinator plus every source it was built from.
     *
     * The identities are handed out in order, and [identitiesIssued] counts them. That
     * count is how the tests tell "reused the prepared row" apart from "prepared a new one
     * that happened to look similar".
     */
    private class Fixture(val dao: FakeRunDao = FakeRunDao()) {
        val clock = SettableClock(OFFICIAL_START)
        private val identities = ArrayDeque(listOf(RUN_ID, SECOND_RUN_ID, THIRD_RUN_ID))
        var identitiesIssued = 0
            private set

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        val coordinator = RunSessionCoordinator(
            runDao = dao,
            preparedRunFactory = PreparedRunFactory(
                clock = clock,
                zoneIdSupplier = { ZoneId.of(START_ZONE) },
                uuidSupplier = {
                    identitiesIssued++
                    UUID.fromString(identities.removeFirst())
                }
            ),
            applicationScope = scope
        )

        /** Requests the official start and waits for its owner. */
        suspend fun startRun(): ActiveRunSession = coordinator.requestStart().await()

        /** The coordinator's current admission, read without recovery. */
        fun admission(): RunAdmission = runBlocking { coordinator.journeySnapshot() }.admission
    }

    /** The row a real countdown hands over: RUNNING, checkpoint at the official start. */
    private fun preparedRun(
        runId: String = RUN_ID,
        officialStart: Long = OFFICIAL_START
    ): RunEntity = RunEntity(
        runId = runId,
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = officialStart,
        startTimezoneId = START_ZONE,
        lastCheckpointEpochMillis = officialStart
    )

    /**
     * The exact throwable an attempt failed with.
     *
     * Read through a completion handler rather than by catching `await()`, because
     * coroutine stack-trace recovery may rethrow a copy from `await()`; the handler receives
     * the original, which is what "unchanged" has to be checked against.
     */
    private fun failureOf(attempt: Deferred<*>): Throwable? {
        var cause: Throwable? = null
        attempt.invokeOnCompletion { cause = it }
        return cause
    }

    // ---------------------------------------------------------------------------------
    // Initialization and snapshots
    // ---------------------------------------------------------------------------------

    /**
     * Proves an empty database admits a countdown and holds no run.
     */
    @Test
    fun `initialization with no active rows is ready for a countdown`() {

        // Arrange: nothing unfinished on disk.
        val fixture = Fixture()

        // Act
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: recovery reached a decision, and the decision was "nothing to adopt".
        assertEquals(
            RunJourneySnapshot(
                InitializationStatus.Completed,
                RunAdmission.ReadyForCountdown
            ),
            snapshot
        )
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())
        assertEquals(1, fixture.dao.discoveryQueryCalls)
    }

    /**
     * Proves a single unfinished run is adopted and reported as still in progress, in the
     * very snapshot initialization returns.
     */
    @Test
    fun `initialization with one active row reports the recovered run in progress`() {

        // Arrange: one run the phone was still in the middle of.
        val fixture = Fixture()
        runBlocking { fixture.dao.insert(preparedRun()) }

        // Act
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: the returned pair already reflects the adoption it just applied.
        assertEquals(InitializationStatus.Completed, snapshot.initializationStatus)
        val inProgress = snapshot.admission as RunAdmission.RunInProgress

        // Assert: the owner is bound to the stored run, and it is live.
        assertEquals(RUN_ID, inProgress.session.runId)
        assertEquals(RunSessionState.RUNNING, inProgress.session.state)

        // Assert: the same owner every time, never a fresh one per question.
        assertSame(
            inProgress.session,
            (fixture.admission() as RunAdmission.RunInProgress).session
        )
    }

    /**
     * Proves several unfinished runs block admission instead of being resolved.
     *
     * The candidates come back whole and in discovery order. That order is evidence
     * ordering rather than a ranking, which is exactly why nothing here adopts the first.
     */
    @Test
    fun `initialization with multiple active rows is blocked by inconsistent storage`() {

        // Arrange: two live runs at once, inserted in the wrong order on purpose.
        val fixture = Fixture()
        val earlierRun = preparedRun()
        val laterRun = preparedRun(SECOND_RUN_ID, SECOND_START)
        runBlocking {
            fixture.dao.insert(laterRun)
            fixture.dao.insert(earlierRun)
        }

        // Act
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: reaching a refusal is still reaching a decision.
        assertEquals(InitializationStatus.Completed, snapshot.initializationStatus)

        // Assert: every candidate, in discovery order, with nothing adopted.
        val blocked = snapshot.admission as RunAdmission.BlockedByInconsistentStorage
        assertEquals(listOf(earlierRun, laterRun), blocked.activeRuns)
    }

    /**
     * Proves an unreadable database is reported as a failure, not as an empty one.
     */
    @Test
    fun `a discovery failure fails initialization and admits nothing`() {

        // Arrange: a live run on disk that discovery cannot reach.
        val fixture = Fixture()
        runBlocking { fixture.dao.insert(preparedRun()) }
        val failure = IllegalStateException("disk unavailable")
        fixture.dao.failDiscoveryWith = failure

        // Act
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: the exact cause is carried, unwrapped, beside an admission of nothing.
        val failed = snapshot.initializationStatus as InitializationStatus.Failed
        assertSame(failure, failed.cause)
        assertEquals(RunAdmission.NotInitialized, snapshot.admission)
        assertEquals(RunAdmission.NotInitialized, fixture.admission())
    }

    /**
     * Proves a failed attempt is genuinely retried rather than remembered as a verdict.
     *
     * The call count is the assertion. A retry that returned the previous answer without
     * asking storage again would look identical from the outside.
     */
    @Test
    fun `initialization retries after a failure and can then complete`() {

        // Arrange: discovery fails once.
        val fixture = Fixture()
        fixture.dao.failDiscoveryWith = IllegalStateException("disk unavailable")
        assertTrue(
            runBlocking { fixture.coordinator.initialize() }.initializationStatus
                is InitializationStatus.Failed
        )
        assertEquals(1, fixture.dao.discoveryQueryCalls)

        // Act: the database becomes readable and the caller tries again.
        fixture.dao.failDiscoveryWith = null
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: a real second query, and a real completion this time.
        assertEquals(
            RunJourneySnapshot(
                InitializationStatus.Completed,
                RunAdmission.ReadyForCountdown
            ),
            snapshot
        )
        assertEquals(2, fixture.dao.discoveryQueryCalls)
    }

    /**
     * Proves a completed attempt is not repeated.
     *
     * Asking storage a second time could return a different database than the one this
     * process built its whole in-memory picture from.
     */
    @Test
    fun `initialization after success is idempotent and does not query again`() {

        // Arrange: one successful initialization.
        val fixture = Fixture()
        val first = runBlocking { fixture.coordinator.initialize() }
        assertEquals(InitializationStatus.Completed, first.initializationStatus)
        assertEquals(1, fixture.dao.discoveryQueryCalls)

        // Act
        val second = runBlocking { fixture.coordinator.initialize() }

        // Assert: same answer, no second query.
        assertEquals(first, second)
        assertEquals(1, fixture.dao.discoveryQueryCalls)
    }

    /**
     * Proves a snapshot requested mid-recovery cannot see recovery half-applied.
     *
     * Recovery is parked inside discovery, holding the lock, and a snapshot is requested
     * UNDISPATCHED so it runs until it can go no further. If the two values were read
     * outside the lock, or at two moments, the snapshot could return `NotAttempted`
     * beside a live run, or `Completed` beside no run. Instead it waits, then returns the
     * one pair recovery left behind.
     */
    @Test
    fun `a snapshot during initialization waits and is internally consistent`() {

        // Arrange: one live run on disk, and a discovery that parks.
        val fixture = Fixture()
        runBlocking { fixture.dao.insert(preparedRun()) }
        val discoveryReached = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        fixture.dao.duringDiscovery = {
            discoveryReached.complete(Unit)
            releaseDiscovery.await()
        }

        runBlocking {

            // Act: recovery parks holding the lock.
            val initialization = CompletableDeferred<RunJourneySnapshot>()
            launch { initialization.complete(fixture.coordinator.initialize()) }
            discoveryReached.await()

            // Act: a read arrives mid-recovery.
            var observed: RunJourneySnapshot? = null
            val reader = launch(start = CoroutineStart.UNDISPATCHED) {
                observed = fixture.coordinator.journeySnapshot()
            }

            // Assert: the read cannot get past the lock while recovery is unapplied.
            assertFalse(reader.isCompleted)
            assertNull(observed)

            // Act: recovery finishes.
            releaseDiscovery.complete(Unit)
            reader.join()

            // Assert: both callers saw the same adopted owner, with Completed beside it.
            val fromInitialize = initialization.await()
            val fromReader = checkNotNull(observed)
            assertEquals(InitializationStatus.Completed, fromReader.initializationStatus)
            assertSame(
                (fromInitialize.admission as RunAdmission.RunInProgress).session,
                (fromReader.admission as RunAdmission.RunInProgress).session
            )
        }
    }

    /**
     * Proves reading a snapshot never initializes, retries or writes.
     *
     * The row on disk is the discriminator: had the read triggered recovery, it would have
     * been adopted and reported as a run in progress, and the query count would have moved.
     */
    @Test
    fun `journey snapshot performs no recovery and no storage write`() {

        // Arrange: a live run on disk, and a coordinator nobody has initialized.
        val fixture = Fixture()
        runBlocking { fixture.dao.insert(preparedRun()) }
        val storedBefore = fixture.dao.inserted.toList()

        // Act
        val beforeInitialization = runBlocking { fixture.coordinator.journeySnapshot() }

        // Assert: honestly uninitialized, with storage neither read nor written.
        assertEquals(
            RunJourneySnapshot(InitializationStatus.NotAttempted, RunAdmission.NotInitialized),
            beforeInitialization
        )
        assertEquals(0, fixture.dao.discoveryQueryCalls)
        assertEquals(storedBefore, fixture.dao.inserted)

        // Act: a failed initialization, then a read.
        fixture.dao.failDiscoveryWith = IllegalStateException("disk unavailable")
        runBlocking { fixture.coordinator.initialize() }
        fixture.dao.failDiscoveryWith = null
        val afterFailure = runBlocking { fixture.coordinator.journeySnapshot() }

        // Assert: a read after failure is not a retry.
        assertTrue(afterFailure.initializationStatus is InitializationStatus.Failed)
        assertEquals(RunAdmission.NotInitialized, afterFailure.admission)
        assertEquals(1, fixture.dao.discoveryQueryCalls)
        assertEquals(storedBefore, fixture.dao.inserted)
        assertTrue(fixture.dao.transitions.isEmpty())
    }

    // ---------------------------------------------------------------------------------
    // Admission refusals
    // ---------------------------------------------------------------------------------

    /**
     * Proves nothing can start before recovery has established what storage holds.
     *
     * This is the whole reason the coordinator exists: starting first could create a
     * second live run beside one already on disk.
     */
    @Test
    fun `start before initialization is refused and writes nothing`() {

        // Arrange: a coordinator nobody has initialized.
        val fixture = Fixture()

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestStart() }
        }

        // Assert: storage was never touched, nothing was prepared, and no countdown was
        // opened either.
        assertTrue(fixture.dao.inserted.isEmpty())
        assertEquals(0, fixture.dao.discoveryQueryCalls)
        assertEquals(0, fixture.identitiesIssued)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.beginCountdown() }
        }
    }

    /**
     * Proves a start request outside a countdown is refused without preparing a run.
     */
    @Test
    fun `start without a countdown is refused and prepares nothing`() {

        // Arrange: initialized and ready, but no countdown.
        val fixture = Fixture()
        runBlocking { fixture.coordinator.initialize() }

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestStart() }
        }

        // Assert: no identity was consumed, nothing stored, still ready.
        assertEquals(0, fixture.identitiesIssued)
        assertTrue(fixture.dao.inserted.isEmpty())
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())
    }

    /**
     * Proves a second run cannot begin while the first is still live.
     */
    @Test
    fun `a second start while a run is held is refused and stores no second row`() {

        // Arrange: one run genuinely started through the coordinator.
        val fixture = Fixture()
        val session = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Act and Assert: a second start is refused while that run is RUNNING.
        fixture.clock.nowMillis = SECOND_START
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestStart() }
        }

        // Act and Assert: pausing changes nothing about admission.
        runBlocking { session.pause(FIRST_PAUSE) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestStart() }
        }

        // Assert: still exactly one stored run, one identity used, and the same owner.
        assertEquals(1, fixture.dao.inserted.size)
        assertEquals(RUN_ID, fixture.dao.inserted.single().runId)
        assertEquals(1, fixture.identitiesIssued)
        assertSame(session, (fixture.admission() as RunAdmission.RunInProgress).session)
        assertEquals(RunSessionState.PAUSED, session.state)
    }

    /**
     * Proves a blocked database refuses a start without touching the evidence.
     */
    @Test
    fun `start while blocked by inconsistent storage is refused and changes nothing`() {

        // Arrange: two live runs, so the coordinator refuses to interpret storage.
        val fixture = Fixture()
        val earlierRun = preparedRun()
        val laterRun = preparedRun(SECOND_RUN_ID, SECOND_START)
        runBlocking {
            fixture.dao.insert(earlierRun)
            fixture.dao.insert(laterRun)
        }
        runBlocking { fixture.coordinator.initialize() }
        val storedBefore = fixture.dao.inserted.toList()

        // Act and Assert: neither a countdown nor a start is admitted.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.beginCountdown() }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestStart() }
        }

        // Assert: no third row was written, nothing prepared, candidates untouched.
        assertEquals(storedBefore, fixture.dao.inserted)
        assertEquals(2, fixture.dao.inserted.size)
        assertEquals(0, fixture.identitiesIssued)

        // Assert: the evidence is still reported whole, in the same order.
        val blocked = fixture.admission() as RunAdmission.BlockedByInconsistentStorage
        assertEquals(listOf(earlierRun, laterRun), blocked.activeRuns)
    }

    // ---------------------------------------------------------------------------------
    // Official start
    // ---------------------------------------------------------------------------------

    /**
     * Proves one successful start stores exactly the prepared row and publishes the exact
     * owner the attempt returned.
     */
    @Test
    fun `a successful start stores one prepared row and publishes the exact owner`() {

        // Arrange: a countdown underway.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }

        // Act
        val session = runBlocking { fixture.startRun() }

        // Assert: exactly the row the factory built from the fixed sources.
        assertEquals(listOf(preparedRun()), fixture.dao.inserted)
        assertEquals(1, fixture.identitiesIssued)

        // Assert: the coordinator holds the very object the caller received.
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertEquals(InitializationStatus.Completed, snapshot.initializationStatus)
        assertSame(session, (snapshot.admission as RunAdmission.RunInProgress).session)
        assertEquals(RunSessionState.RUNNING, session.state)
    }

    /**
     * Proves overlapping requests share one attempt rather than racing two inserts.
     *
     * The first insert is parked, so the first attempt is provably still underway when the
     * second request arrives. Two requests launched from separate coroutines must receive
     * the identical [Deferred] — not an equal one — and only one identity may be consumed.
     */
    @Test
    fun `overlapping start requests receive the same attempt`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val insertReached = CompletableDeferred<Unit>()
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = {
            insertReached.complete(Unit)
            releaseInsert.await()
        }

        runBlocking {

            // Act: the first request, whose insert parks.
            val firstRequest = CompletableDeferred<Deferred<ActiveRunSession>>()
            launch(start = CoroutineStart.UNDISPATCHED) {
                firstRequest.complete(fixture.coordinator.requestStart())
            }
            insertReached.await()

            // Act: a second request from another coroutine while the insert is parked.
            val secondRequest = CompletableDeferred<Deferred<ActiveRunSession>>()
            launch(start = CoroutineStart.UNDISPATCHED) {
                secondRequest.complete(fixture.coordinator.requestStart())
            }

            // Assert: one attempt object, still running, one identity, nothing stored yet.
            val first = firstRequest.await()
            assertSame(first, secondRequest.await())
            assertTrue(first.isActive)
            assertEquals(1, fixture.identitiesIssued)
            assertTrue(fixture.dao.inserted.isEmpty())

            // Assert: while it runs, the journey reports the countdown boundary.
            assertEquals(RunAdmission.CountdownInProgress, fixture.admission())

            // Act: let the insert finish.
            releaseInsert.complete(Unit)
            val session = first.await()

            // Assert: one row, one owner.
            assertEquals(listOf(preparedRun()), fixture.dao.inserted)
            assertSame(session, (fixture.admission() as RunAdmission.RunInProgress).session)
        }
    }

    /**
     * Proves the attempt belongs to the application, not to the coroutine awaiting it.
     *
     * The caller is cancelled while the insert is parked. If the coordinator had run the
     * insert in the caller's own scope, that cancellation would have cancelled the attempt
     * too: it would no longer be active, no row would ever be written and no owner would be
     * published. Instead the attempt is still active after the caller is gone, and once
     * released it stores the run and the coordinator owns it — with nobody awaiting it.
     */
    @Test
    fun `cancelling the awaiting caller does not cancel the start`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val insertReached = CompletableDeferred<Unit>()
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = {
            insertReached.complete(Unit)
            releaseInsert.await()
        }

        runBlocking {

            // Act: a caller requests the start and waits on it; the insert parks.
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.startRun()
            }
            insertReached.await()
            val attempt = fixture.coordinator.requestStart()

            // Act: the screen goes away.
            caller.cancel()
            caller.join()

            // Assert: the caller is gone, but the attempt is not.
            assertTrue(caller.isCancelled)
            assertTrue("The start was cancelled with its caller.", attempt.isActive)
            assertTrue(fixture.dao.inserted.isEmpty())

            // Act: storage finishes, with nobody waiting.
            releaseInsert.complete(Unit)

            // Assert: the attempt completed normally, stored the run and published it.
            assertTrue(attempt.isCompleted)
            assertFalse(attempt.isCancelled)
            assertNull(failureOf(attempt))
            assertEquals(listOf(preparedRun()), fixture.dao.inserted)
            val owner = (fixture.admission() as RunAdmission.RunInProgress).session
            assertSame(attempt.await(), owner)
            assertEquals(RunSessionState.RUNNING, owner.state)
        }
    }

    /**
     * Proves a failed insert leaves the runner in the countdown with no owner.
     */
    @Test
    fun `an insert failure leaves the countdown with no owner`() {

        // Arrange: a countdown, and storage that refuses the insert.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val failure = IllegalStateException("disk full")
        fixture.dao.failWith = failure

        // Act
        val attempt = runBlocking { fixture.coordinator.requestStart() }

        // Assert: the attempt failed with exactly storage's exception. (`isCancelled` is
        // not checked: kotlinx reports true for any exceptional completion, failure
        // included. The identity of the cause is the precise check.)
        assertTrue(attempt.isCompleted)
        assertSame(failure, failureOf(attempt))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { attempt.await() }
        }

        // Assert: nothing stored, no owner, still a countdown — whose start is reported failed.
        assertTrue(fixture.dao.inserted.isEmpty())
        assertEquals(
            RunJourneySnapshot(
                InitializationStatus.Completed,
                RunAdmission.CountdownInProgress,
                StartAttemptPhase.Failed
            ),
            runBlocking { fixture.coordinator.journeySnapshot() }
        )

        // Assert: the reservation was released, so the countdown can be cancelled again.
        runBlocking { fixture.coordinator.cancelCountdown() }
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())
    }

    /**
     * Proves a retry after failure is the same run, not a second one.
     *
     * Between the attempts the clock moves on and the identity source has another UUID
     * ready. A retry that asked the factory again would pick up both, so the stored row
     * matching the *first* moment and identity is what proves the prepared row was reused.
     */
    @Test
    fun `a retry after failure reuses the exact prepared run`() {

        // Arrange: a countdown whose first insert fails.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        fixture.dao.failWith = IllegalStateException("disk full")
        val failed = runBlocking { fixture.coordinator.requestStart() }
        assertTrue(failureOf(failed) is IllegalStateException)

        // Arrange: storage recovers, and time passes before the runner retries.
        fixture.dao.failWith = null
        fixture.clock.nowMillis = SECOND_START

        // Act
        val retry = runBlocking { fixture.coordinator.requestStart() }
        val session = runBlocking { retry.await() }

        // Assert: a new attempt object, but the same run — first UUID, first start.
        assertFalse(retry === failed)
        assertEquals(RUN_ID, session.runId)
        assertEquals(listOf(preparedRun()), fixture.dao.inserted)
        assertEquals(1, fixture.identitiesIssued)
        assertSame(session, (fixture.admission() as RunAdmission.RunInProgress).session)
    }

    /**
     * Proves cancelling after a failed start discards that run's prepared identity.
     *
     * The next cycle receiving the next UUID and the new clock reading is the evidence. Had
     * the old row survived the cancel, the later run would have been stored under the
     * abandoned identity and the stale start time.
     */
    @Test
    fun `cancel after a failed start clears the prepared run`() {

        // Arrange: a failed start in the first countdown.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        fixture.dao.failWith = IllegalStateException("disk full")
        runBlocking { fixture.coordinator.requestStart() }

        // Act: the runner backs out, storage recovers, and later starts again.
        runBlocking { fixture.coordinator.cancelCountdown() }
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())
        fixture.dao.failWith = null
        fixture.clock.nowMillis = SECOND_START
        val session = runBlocking {
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Assert: a new identity and start time, and only that one run stored.
        assertEquals(SECOND_RUN_ID, session.runId)
        assertEquals(listOf(preparedRun(SECOND_RUN_ID, SECOND_START)), fixture.dao.inserted)
        assertEquals(2, fixture.identitiesIssued)
    }

    /**
     * Proves a countdown cannot be cancelled, or replaced, while its start is underway.
     *
     * With the insert parked the machine is still COUNTDOWN and no owner is held — exactly
     * the conditions under which a cancel used to be allowed. Only the reservation refuses
     * it. Had the cancel gone through, the parked insert would have landed afterwards over a
     * machine the coordinator had already discarded.
     */
    @Test
    fun `cancel and a new countdown are refused while a start is underway`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val insertReached = CompletableDeferred<Unit>()
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = {
            insertReached.complete(Unit)
            releaseInsert.await()
        }

        runBlocking {
            val attempt = fixture.coordinator.requestStart()
            insertReached.await()

            // Act and Assert: neither exit from the cycle is admitted.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.cancelCountdown() }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.beginCountdown() }
            }

            // Assert: the attempt was untouched by either refusal.
            assertTrue(attempt.isActive)
            assertSame(attempt, fixture.coordinator.requestStart())
            assertEquals(RunAdmission.CountdownInProgress, fixture.admission())

            // Act: the insert lands.
            releaseInsert.complete(Unit)
            val session = attempt.await()

            // Assert: the owner is live over the cycle the coordinator still holds, so the
            // run can be taken through to completion and the next cycle begun normally.
            assertSame(session, (fixture.admission() as RunAdmission.RunInProgress).session)
            session.pause(FIRST_PAUSE)
            session.complete(FIRST_FINISH)
            assertEquals(RunAdmission.RunCompleted(session), fixture.admission())
            assertEquals(1, fixture.dao.inserted.size)
        }
    }

    /**
     * Proves a successful start clears the prepared intention.
     *
     * After the run completes, the next cycle must receive a new identity. Had the used row
     * been kept, the next start would try to insert the finished run's UUID again.
     */
    @Test
    fun `a successful start clears the prepared run for the next cycle`() {

        // Arrange: one full run, taken all the way to completion.
        val fixture = Fixture()
        val firstSession = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
            fixture.startRun().also {
                it.pause(FIRST_PAUSE)
                it.complete(FIRST_FINISH)
            }
        }

        // Act: the next run.
        fixture.clock.nowMillis = SECOND_START
        val secondSession = runBlocking {
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Assert: a fresh identity and start, and both runs stored once each.
        assertEquals(SECOND_RUN_ID, secondSession.runId)
        assertEquals(RUN_ID, firstSession.runId)
        assertEquals(2, fixture.identitiesIssued)
        assertEquals(
            preparedRun(SECOND_RUN_ID, SECOND_START),
            fixture.dao.inserted.last()
        )
        assertEquals(listOf(RUN_ID, SECOND_RUN_ID), fixture.dao.inserted.map { it.runId })
    }

    /**
     * Proves cancellation of the attempt itself is not treated as an ordinary failure.
     *
     * Only the application scope can cancel an attempt, and production never does. If it
     * happens anyway, whether the insert landed is unknown, so the coordinator must not
     * release the reservation as if storage had refused — that would let Cancel discard a
     * cycle, or a retry race, over a run that might already be stored. It stays refused.
     */
    @Test
    fun `a cancelled attempt is not recorded as a failure and admits nothing further`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val insertReached = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = {
            insertReached.complete(Unit)
            CompletableDeferred<Unit>().await()
        }

        runBlocking {
            val attempt = fixture.coordinator.requestStart()
            insertReached.await()

            // Act: the application scope itself is cancelled mid-insert.
            fixture.scope.cancel()
            attempt.join()

            // Assert: cancelled, not failed with an ordinary exception.
            assertTrue(failureOf(attempt) is CancellationException)

            // Assert: no retry, no cancel and no new countdown are admitted.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.requestStart() }
            }
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.cancelCountdown() }
            }
            assertEquals(RunAdmission.CountdownInProgress, fixture.admission())
            assertEquals(1, fixture.identitiesIssued)
            assertTrue(fixture.dao.inserted.isEmpty())
        }
    }

    // ---------------------------------------------------------------------------------
    // Cycles and countdown cancellation
    // ---------------------------------------------------------------------------------

    /**
     * Proves a finished run is retired at the next countdown so another run can begin.
     *
     * The retired machine is discarded rather than reset, which is why the first session
     * keeps reporting COMPLETED forever afterwards: it still points at that machine, and
     * "this run ended" is not a reversible claim.
     */
    @Test
    fun `a completed run is retired and the next countdown starts a new run`() {

        // Arrange: one full run, taken all the way to completion.
        val fixture = Fixture()
        val firstSession = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()

            // Assert: a countdown is underway and no run is official yet.
            assertEquals(
                RunAdmission.CountdownInProgress,
                fixture.coordinator.journeySnapshot().admission
            )

            val session = fixture.startRun()
            session.pause(FIRST_PAUSE)
            session.complete(FIRST_FINISH)
            session
        }

        // Assert: a completed cycle is reported as such, holding the finished owner.
        assertEquals(RunSessionState.COMPLETED, firstSession.state)
        assertEquals(RunAdmission.RunCompleted(firstSession), fixture.admission())

        // Act: the next countdown retires the finished cycle, and a new run begins.
        fixture.clock.nowMillis = SECOND_START
        val secondSession = runBlocking {
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Assert: two distinct runs, and the finished one stayed finished.
        assertEquals(SECOND_RUN_ID, secondSession.runId)
        assertEquals(RunSessionState.RUNNING, secondSession.state)
        assertEquals(RunSessionState.COMPLETED, firstSession.state)

        // Assert: the coordinator now owns the second run, not the first.
        assertSame(
            secondSession,
            (fixture.admission() as RunAdmission.RunInProgress).session
        )

        // Assert: both runs are stored, each written once.
        assertEquals(2, fixture.dao.inserted.size)
        assertEquals(listOf(RUN_ID, SECOND_RUN_ID), fixture.dao.inserted.map { it.runId })
    }

    /**
     * Proves a countdown and start arriving mid-recovery wait rather than interleaving.
     *
     * This is the ordering guarantee the coordinator was written for, and it is asserted
     * as ordering rather than as a final state. Recovery is parked inside the DAO query
     * while it holds the coordinator lock; the competing job is then launched UNDISPATCHED
     * so it runs on this thread until it can go no further — which, with the lock held, is
     * the lock itself. The assertions taken at that moment are what prove the competing
     * start had not crossed into storage, something a check of the end state could not
     * distinguish from a start that ran first and simply lost a later race.
     *
     * Nothing here depends on a delay. CompletableDeferred does the coordinating, and the
     * mutex — not the scheduler — is what makes initialization finish first.
     */
    @Test
    fun `recovery completes before a competing countdown and start reach storage`() {

        // Arrange: a DAO whose discovery parks until this test releases it.
        val fixture = Fixture()
        val discoveryReached = CompletableDeferred<Unit>()
        val releaseDiscovery = CompletableDeferred<Unit>()
        fixture.dao.duringDiscovery = {
            discoveryReached.complete(Unit)
            releaseDiscovery.await()
        }

        // Arrange: a record of which job actually finished first.
        val completionOrder = mutableListOf<String>()

        runBlocking {

            // Act: initialization runs until it is parked inside discovery, holding the
            // coordinator lock.
            val initialization = launch {
                fixture.coordinator.initialize()
                completionOrder += "initialization"
            }
            discoveryReached.await()

            // Act: a countdown and start arrive while recovery is still in flight.
            val competingStart = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.coordinator.beginCountdown()
                fixture.startRun()
                completionOrder += "start"
            }

            // Assert: with recovery parked, the competing job is stuck at the lock. It has
            // not finished, and — the assertion that matters — it has not inserted a run.
            assertFalse(
                "The competing start must not complete while recovery is parked.",
                competingStart.isCompleted
            )
            assertTrue(
                "No run may reach storage before recovery finishes: ${fixture.dao.inserted}",
                fixture.dao.inserted.isEmpty()
            )
            assertTrue(completionOrder.isEmpty())

            // Act: let recovery finish.
            releaseDiscovery.complete(Unit)
            initialization.join()
            competingStart.join()
        }

        // Assert: initialization finished first, and only then did the run start.
        assertEquals(listOf("initialization", "start"), completionOrder)

        // Assert: the run that was waiting did eventually start, exactly once.
        assertEquals(1, fixture.dao.inserted.size)
        assertEquals(RUN_ID, fixture.dao.inserted.single().runId)
        assertEquals(
            RUN_ID,
            (fixture.admission() as RunAdmission.RunInProgress).session.runId
        )
        assertEquals(1, fixture.dao.discoveryQueryCalls)
    }

    /**
     * Proves a later initialize call cannot disturb a run that is already underway.
     *
     * A caller that cannot easily tell whether startup already ran should be able to ask
     * again safely. Re-querying here would be worse than useless: discovery would find the
     * live run and recovery would try to adopt a run this coordinator already owns.
     */
    @Test
    fun `initialization during a live run stays completed without querying again`() {

        // Arrange: an initialized coordinator with one run underway.
        val fixture = Fixture()
        val session = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }
        assertEquals(1, fixture.dao.discoveryQueryCalls)

        // Act
        val snapshot = runBlocking { fixture.coordinator.initialize() }

        // Assert: still completed, and storage was not asked a second time.
        assertEquals(InitializationStatus.Completed, snapshot.initializationStatus)
        assertEquals(1, fixture.dao.discoveryQueryCalls)

        // Assert: the same live run, still owned by the same object.
        assertSame(session, (snapshot.admission as RunAdmission.RunInProgress).session)
        assertEquals(RunSessionState.RUNNING, session.state)
        assertEquals(1, fixture.dao.inserted.size)
    }

    /**
     * Proves a countdown can be backed out of, at no cost and with nothing stored.
     *
     * This is the exit that entering COUNTDOWN requires. The machine has no legal move
     * from COUNTDOWN back to NO_SESSION, so without cancellation a runner who tapped Start
     * by accident could only start a run they did not want or kill the app.
     *
     * The storage assertions are the other half. A countdown never wrote anything, so
     * cancelling it has nothing to undo — which is precisely why it can be offered as a
     * plain Cancel rather than as a discard with consequences.
     */
    @Test
    fun `cancelling a countdown returns to ready and writes nothing`() {

        // Arrange: an initialized coordinator with a countdown underway.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        assertEquals(RunAdmission.CountdownInProgress, fixture.admission())
        val discoveriesBeforeCancel = fixture.dao.discoveryQueryCalls

        // Act
        runBlocking { fixture.coordinator.cancelCountdown() }

        // Assert: back to the state a countdown began from.
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())

        // Assert: nothing was written, and nothing was read either.
        assertTrue("Cancelling wrote a run: ${fixture.dao.inserted}", fixture.dao.inserted.isEmpty())
        assertTrue(
            "Cancelling wrote a transition: ${fixture.dao.transitions}",
            fixture.dao.transitions.isEmpty()
        )
        assertEquals(0, runBlocking { fixture.dao.countRuns() })
        assertEquals(discoveriesBeforeCancel, fixture.dao.discoveryQueryCalls)

        // Assert: the fresh cycle is genuinely usable, not merely reported as ready.
        val session = runBlocking {
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }
        assertEquals(RunSessionState.RUNNING, session.state)
        assertEquals(1, fixture.dao.inserted.size)
    }

    /**
     * Proves cancelling with no countdown underway fails instead of quietly succeeding.
     *
     * Silently doing nothing would make a stray cancel indistinguishable from a real one,
     * and would leave "cancel" available as a way to discard a cycle that might be holding
     * something.
     */
    @Test
    fun `cancelling without a countdown is refused`() {

        // Arrange: initialized, ready, but no countdown.
        val fixture = Fixture()
        runBlocking { fixture.coordinator.initialize() }

        // Act and Assert
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.cancelCountdown() }
        }

        // Assert: nothing changed, and a countdown is still available.
        assertEquals(RunAdmission.ReadyForCountdown, fixture.admission())
        assertTrue(fixture.dao.inserted.isEmpty())

        // Act and Assert: refused before initialization for the same reason.
        val uninitialized = Fixture()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { uninitialized.coordinator.cancelCountdown() }
        }
    }

    /**
     * Proves cancelling cannot be used to drop a run this coordinator owns.
     *
     * A live run is not a countdown, and cancellation must never become a quiet way to let
     * go of an owner while its run is still stored as unfinished.
     */
    @Test
    fun `cancelling while a run is held is refused`() {

        // Arrange: one run genuinely underway.
        val fixture = Fixture()
        val session = runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Act and Assert: refused while RUNNING.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.cancelCountdown() }
        }

        // Act and Assert: still refused while PAUSED.
        runBlocking { session.pause(FIRST_PAUSE) }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.cancelCountdown() }
        }

        // Assert: the run is untouched and still owned by the same object.
        assertEquals(RunSessionState.PAUSED, session.state)
        assertSame(session, (fixture.admission() as RunAdmission.RunInProgress).session)
        assertEquals(1, fixture.dao.inserted.size)
    }

    // ---------------------------------------------------------------------------------
    // Start attempt reporting
    // ---------------------------------------------------------------------------------

    /**
     * Proves a start still writing is reported as in progress, and stops being so once its
     * owner is published.
     */
    @Test
    fun `a reserved start reports in progress`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = { releaseInsert.await() }

        runBlocking {

            // Act
            val attempt = fixture.coordinator.requestStart()

            // Assert: still a countdown to admission, but visibly a start underway.
            val during = fixture.coordinator.journeySnapshot()
            assertEquals(RunAdmission.CountdownInProgress, during.admission)
            assertEquals(StartAttemptPhase.InProgress, during.startAttemptPhase)

            // Act: the insert lands.
            releaseInsert.complete(Unit)
            attempt.await()

            // Assert: the phase is gone once the owner is published.
            val after = fixture.coordinator.journeySnapshot()
            assertEquals(StartAttemptPhase.None, after.startAttemptPhase)
            assertTrue(after.admission is RunAdmission.RunInProgress)
        }
    }

    /**
     * Proves an ordinary insert failure is reported as a failed start beside the countdown.
     */
    @Test
    fun `an ordinary start failure reports failed`() {

        // Arrange: a countdown, and storage that refuses the insert.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        fixture.dao.failWith = IllegalStateException("disk full")

        // Act
        runBlocking { fixture.coordinator.requestStart() }

        // Assert
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertEquals(StartAttemptPhase.Failed, snapshot.startAttemptPhase)
        assertEquals(RunAdmission.CountdownInProgress, snapshot.admission)
    }

    /**
     * Proves a cancelled attempt is never relabelled as a failed one.
     *
     * The insert may have landed, so the reservation stays and the start is still reported
     * as in progress rather than as something the runner could retry.
     */
    @Test
    fun `a cancelled start is not reported as failed`() {

        // Arrange: a countdown, and an insert that never returns.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        fixture.dao.duringInsert = { CompletableDeferred<Unit>().await() }

        runBlocking {
            val attempt = fixture.coordinator.requestStart()

            // Act: the application scope itself is cancelled mid-insert.
            fixture.scope.cancel()
            attempt.join()

            // Assert
            assertEquals(
                StartAttemptPhase.InProgress,
                fixture.coordinator.journeySnapshot().startAttemptPhase
            )
        }
    }

    /**
     * Proves a failure is held by the coordinator, not by whoever was waiting.
     *
     * The waiting caller is cancelled while the insert is parked — the screen went away — and
     * only then does storage refuse. A failure recorded by the caller would be lost with it.
     */
    @Test
    fun `start failure remains reportable after the awaiting caller is cancelled`() {

        // Arrange: a countdown, and an insert that parks.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        val insertReached = CompletableDeferred<Unit>()
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = {
            insertReached.complete(Unit)
            releaseInsert.await()
        }

        runBlocking {

            // Act: a caller waits on the start, then goes away.
            val caller = launch(start = CoroutineStart.UNDISPATCHED) { fixture.startRun() }
            insertReached.await()
            caller.cancel()
            caller.join()

            // Act: storage refuses, with nobody waiting.
            fixture.dao.failWith = IllegalStateException("disk full")
            releaseInsert.complete(Unit)

            // Assert: the failure is still there for whoever asks next.
            val snapshot = fixture.coordinator.journeySnapshot()
            assertEquals(StartAttemptPhase.Failed, snapshot.startAttemptPhase)
            assertEquals(RunAdmission.CountdownInProgress, snapshot.admission)
            assertTrue(fixture.dao.inserted.isEmpty())
        }
    }

    /**
     * Proves a retry clears the failure as it begins, and stores the first prepared run.
     *
     * The retry's insert is parked so the cleared phase can be seen while it runs. Between
     * attempts the clock moves and a new identity is waiting, so the stored row matching the
     * first moment, zone and identity is what proves reuse.
     */
    @Test
    fun `a retry clears the failure and reuses the exact prepared identity and timestamps`() {

        // Arrange: a first attempt that fails.
        val fixture = Fixture()
        runBlocking {
            fixture.coordinator.initialize()
            fixture.coordinator.beginCountdown()
        }
        fixture.dao.failWith = IllegalStateException("disk full")
        runBlocking { fixture.coordinator.requestStart() }
        assertEquals(
            StartAttemptPhase.Failed,
            runBlocking { fixture.coordinator.journeySnapshot() }.startAttemptPhase
        )

        // Arrange: storage recovers, time passes, and the retry's insert parks.
        fixture.dao.failWith = null
        fixture.clock.nowMillis = SECOND_START
        val releaseInsert = CompletableDeferred<Unit>()
        fixture.dao.duringInsert = { releaseInsert.await() }

        runBlocking {

            // Act
            val retry = fixture.coordinator.requestStart()

            // Assert: the failure is cleared the moment the genuine retry begins.
            assertEquals(
                StartAttemptPhase.InProgress,
                fixture.coordinator.journeySnapshot().startAttemptPhase
            )

            releaseInsert.complete(Unit)
            retry.await()

            // Assert: the first run's exact row — UUID, start, zone and checkpoint.
            assertEquals(listOf(preparedRun()), fixture.dao.inserted)
            assertEquals(1, fixture.identitiesIssued)
            assertEquals(
                StartAttemptPhase.None,
                fixture.coordinator.journeySnapshot().startAttemptPhase
            )
        }
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle actions
    // ---------------------------------------------------------------------------------

    /** Initializes, counts down and starts, returning the owner the coordinator holds. */
    private fun startedRun(fixture: Fixture): ActiveRunSession = runBlocking {
        fixture.coordinator.initialize()
        fixture.coordinator.beginCountdown()
        fixture.startRun()
    }

    /**
     * Proves Pause, Resume and Complete through the coordinator reach a reported completion.
     */
    @Test
    fun `successful completion through the coordinator reports run completed`() {

        // Arrange
        val fixture = Fixture()
        val session = startedRun(fixture)

        // Act
        runBlocking {
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        // Assert: completed, with nothing reserved and no failure left over.
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertEquals(RunAdmission.RunCompleted(session), snapshot.admission)
        assertNull(snapshot.reservedAction)
        assertNull(snapshot.lastActionFailed)
        assertEquals(StoredRunState.COMPLETED, fixture.dao.inserted.single().state)
    }

    /**
     * Proves the next countdown retires a completed cycle into a fresh machine.
     */
    @Test
    fun `beginning another countdown retires the completed cycle`() {

        // Arrange: a run completed through the coordinator.
        val fixture = Fixture()
        val first = startedRun(fixture)
        runBlocking {
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        // Act
        runBlocking { fixture.coordinator.beginCountdown() }

        // Assert: a plain countdown, with the finished owner released and still finished.
        assertEquals(
            RunJourneySnapshot(InitializationStatus.Completed, RunAdmission.CountdownInProgress),
            runBlocking { fixture.coordinator.journeySnapshot() }
        )
        assertEquals(RunSessionState.COMPLETED, first.state)

        // Assert: the fresh machine takes a new run with a new identity.
        fixture.clock.nowMillis = SECOND_START
        val second = runBlocking { fixture.startRun() }
        assertEquals(SECOND_RUN_ID, second.runId)
        assertEquals(RunSessionState.RUNNING, second.state)
    }

    /**
     * Proves a repeated request for the action already writing is that same write.
     */
    @Test
    fun `duplicate same-kind lifecycle actions return the exact same deferred`() {

        // Arrange: a live run whose pause write parks.
        val fixture = Fixture()
        startedRun(fixture)
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.dao.duringStateChange = { releaseWrite.await() }

        runBlocking {

            // Act
            val first = fixture.coordinator.requestAction(RunActionKind.PAUSE)
            val second = fixture.coordinator.requestAction(RunActionKind.PAUSE)

            // Assert: one write object, still underway, and reported as reserved.
            assertSame(first, second)
            assertTrue(first.isActive)
            assertEquals(
                RunActionKind.PAUSE,
                fixture.coordinator.journeySnapshot().reservedAction
            )

            // Act
            releaseWrite.complete(Unit)
            first.await()

            // Assert: exactly one pause recorded.
            assertEquals(
                listOf(RunTransitionType.PAUSE),
                fixture.dao.transitions.map { it.transitionType }
            )
        }
    }

    /**
     * Proves a different action is refused while another is still writing.
     */
    @Test
    fun `a different lifecycle action is refused while one is reserved`() {

        // Arrange: a paused run whose resume write parks.
        val fixture = Fixture()
        startedRun(fixture)
        runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE).await() }
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.dao.duringStateChange = { releaseWrite.await() }

        runBlocking {
            val resume = fixture.coordinator.requestAction(RunActionKind.RESUME)

            // Act and Assert: Complete is legal from PAUSED, but not over a pending resume.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.requestAction(RunActionKind.COMPLETE) }
            }

            // Act and Assert: nor may the next countdown retire the cycle underneath it.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.beginCountdown() }
            }

            // Assert: the resume was untouched and completes normally.
            assertTrue(resume.isActive)
            releaseWrite.complete(Unit)
            resume.await()
            assertNull(fixture.coordinator.journeySnapshot().reservedAction)
            assertEquals(2, fixture.dao.transitions.size)
        }
    }

    /**
     * Proves actions are refused when they are not legal for the run's current state, or
     * when there is no run to act on.
     */
    @Test
    fun `illegal lifecycle actions are refused and launch nothing`() {

        // Arrange and Act and Assert: nothing initialized.
        val uninitialized = Fixture()
        assertThrows(IllegalStateException::class.java) {
            runBlocking { uninitialized.coordinator.requestAction(RunActionKind.PAUSE) }
        }

        // Arrange: initialized, but no run.
        val fixture = Fixture()
        runBlocking { fixture.coordinator.initialize() }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE) }
        }

        // Arrange: a running run.
        runBlocking {
            fixture.coordinator.beginCountdown()
            fixture.startRun()
        }

        // Act and Assert: neither resume nor complete is legal from RUNNING.
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestAction(RunActionKind.RESUME) }
        }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { fixture.coordinator.requestAction(RunActionKind.COMPLETE) }
        }

        // Assert: nothing was written, reserved or marked failed.
        assertTrue(fixture.dao.transitions.isEmpty())
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertNull(snapshot.reservedAction)
        assertNull(snapshot.lastActionFailed)
    }

    /**
     * Proves an action belongs to the application, not to the coroutine awaiting it.
     */
    @Test
    fun `cancelling a caller awaiting an action does not cancel the action`() {

        // Arrange: a live run whose pause write parks.
        val fixture = Fixture()
        val session = startedRun(fixture)
        val writeReached = CompletableDeferred<Unit>()
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.dao.duringStateChange = {
            writeReached.complete(Unit)
            releaseWrite.await()
        }

        runBlocking {

            // Act: a caller waits on the pause; the write parks; the caller goes away.
            val caller = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            }
            writeReached.await()
            val action = fixture.coordinator.requestAction(RunActionKind.PAUSE)
            caller.cancel()
            caller.join()

            // Assert: the caller is gone, but the write is not.
            assertTrue(caller.isCancelled)
            assertTrue("The action was cancelled with its caller.", action.isActive)

            // Act: storage finishes, with nobody waiting.
            releaseWrite.complete(Unit)

            // Assert: completed normally, durably paused, reservation released.
            assertTrue(action.isCompleted)
            assertNull(failureOf(action))
            assertEquals(RunSessionState.PAUSED, session.state)
            assertEquals(StoredRunState.PAUSED, fixture.dao.inserted.single().state)
            assertNull(fixture.coordinator.journeySnapshot().reservedAction)
        }
    }

    /**
     * Proves a refused write is retained for reporting beside the run's surviving state.
     */
    @Test
    fun `an ordinary action failure is retained in the snapshot`() {

        // Arrange: a live run whose pause storage refuses.
        val fixture = Fixture()
        val session = startedRun(fixture)
        val failure = IllegalStateException("disk full")
        fixture.dao.failStateChangeWith = failure

        // Act
        val action = runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE) }

        // Assert: failed with storage's exact exception.
        assertTrue(action.isCompleted)
        assertSame(failure, failureOf(action))

        // Assert: still running, nothing reserved, and the failed action is named.
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertEquals(RunAdmission.RunInProgress(session), snapshot.admission)
        assertEquals(RunSessionState.RUNNING, session.state)
        assertNull(snapshot.reservedAction)
        assertEquals(RunActionKind.PAUSE, snapshot.lastActionFailed)
        assertTrue(fixture.dao.transitions.isEmpty())
    }

    /**
     * Proves a successful retry of the refused action clears the failure.
     */
    @Test
    fun `a successful retry clears the action failure`() {

        // Arrange: one refused pause.
        val fixture = Fixture()
        val session = startedRun(fixture)
        fixture.dao.failStateChangeWith = IllegalStateException("disk full")
        runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE) }

        // Act: storage recovers and the runner tries again.
        fixture.dao.failStateChangeWith = null
        runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE).await() }

        // Assert
        val snapshot = runBlocking { fixture.coordinator.journeySnapshot() }
        assertNull(snapshot.lastActionFailed)
        assertNull(snapshot.reservedAction)
        assertEquals(RunSessionState.PAUSED, session.state)
        assertEquals(1, fixture.dao.transitions.size)
    }

    /**
     * Proves every lifecycle timestamp is read from the one clock the start came from.
     *
     * The clock is moved to a distinct value before each request, so a coordinator reading any
     * other source — or reading this one at the wrong moment — would store a different number.
     */
    @Test
    fun `lifecycle timestamps come from the shared clock`() {

        // Arrange
        val fixture = Fixture()
        startedRun(fixture)
        val resumeAt = FIRST_PAUSE + 30_000L
        val secondPauseAt = FIRST_PAUSE + 45_000L

        // Act
        runBlocking {
            fixture.clock.nowMillis = FIRST_PAUSE
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.clock.nowMillis = resumeAt
            fixture.coordinator.requestAction(RunActionKind.RESUME).await()
            fixture.clock.nowMillis = secondPauseAt
            fixture.coordinator.requestAction(RunActionKind.PAUSE).await()
            fixture.clock.nowMillis = FIRST_FINISH
            fixture.coordinator.requestAction(RunActionKind.COMPLETE).await()
        }

        // Assert
        assertEquals(
            listOf(FIRST_PAUSE, resumeAt, secondPauseAt),
            fixture.dao.transitions.map { it.occurredAtEpochMillis }
        )
        val stored = fixture.dao.inserted.single()
        assertEquals(OFFICIAL_START, stored.officialStartEpochMillis)
        assertEquals(FIRST_FINISH, stored.finishEpochMillis)
        assertEquals(FIRST_FINISH, stored.lastCheckpointEpochMillis)
    }

    /**
     * Proves a completion still writing is reported as a reserved action over a run in
     * progress, never as a completed run.
     */
    @Test
    fun `a pending completion is not reported as completed`() {

        // Arrange: a paused run whose completion write parks.
        val fixture = Fixture()
        val session = startedRun(fixture)
        runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE).await() }
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.dao.duringCompletion = { releaseWrite.await() }

        runBlocking {

            // Act
            val complete = fixture.coordinator.requestAction(RunActionKind.COMPLETE)

            // Assert: reserved, and still merely in progress.
            val during = fixture.coordinator.journeySnapshot()
            assertEquals(RunAdmission.RunInProgress(session), during.admission)
            assertEquals(RunActionKind.COMPLETE, during.reservedAction)

            // Act
            releaseWrite.complete(Unit)
            complete.await()

            // Assert: completed only once the reservation is released.
            val after = fixture.coordinator.journeySnapshot()
            assertEquals(RunAdmission.RunCompleted(session), after.admission)
            assertNull(after.reservedAction)
        }
    }

    /**
     * Proves a cancelled action is neither a failure nor released, and blocks further actions.
     */
    @Test
    fun `a cancelled action is not recorded as a failure and admits no further action`() {

        // Arrange: a live run whose pause write never returns.
        val fixture = Fixture()
        startedRun(fixture)
        fixture.dao.duringStateChange = { CompletableDeferred<Unit>().await() }

        runBlocking {
            val action = fixture.coordinator.requestAction(RunActionKind.PAUSE)

            // Act: the application scope itself is cancelled mid-write.
            fixture.scope.cancel()
            action.join()

            // Assert: cancelled, not an ordinary failure, and still reserved.
            assertTrue(failureOf(action) is CancellationException)
            val snapshot = fixture.coordinator.journeySnapshot()
            assertEquals(RunActionKind.PAUSE, snapshot.reservedAction)
            assertNull(snapshot.lastActionFailed)

            // Assert: neither a repeat nor a different action is admitted.
            assertThrows(IllegalStateException::class.java) {
                runBlocking { fixture.coordinator.requestAction(RunActionKind.PAUSE) }
            }
            assertTrue(fixture.dao.transitions.isEmpty())
        }
    }

    /**
     * Proves waiting for reserved work waits without starting or retrying anything.
     */
    @Test
    fun `awaiting reserved work waits for the write and starts nothing`() {

        // Arrange: nothing reserved returns immediately.
        val fixture = Fixture()
        val session = startedRun(fixture)
        runBlocking { fixture.coordinator.awaitReservedWork() }

        // Arrange: a pause write that parks.
        val releaseWrite = CompletableDeferred<Unit>()
        fixture.dao.duringStateChange = { releaseWrite.await() }

        runBlocking {
            fixture.coordinator.requestAction(RunActionKind.PAUSE)

            // Act: a waiter arrives mid-write.
            val waiter = launch(start = CoroutineStart.UNDISPATCHED) {
                fixture.coordinator.awaitReservedWork()
            }

            // Assert: it waits.
            assertFalse(waiter.isCompleted)

            // Act
            releaseWrite.complete(Unit)
            waiter.join()

            // Assert: exactly the one requested pause happened.
            assertEquals(RunSessionState.PAUSED, session.state)
            assertEquals(1, fixture.dao.transitions.size)
        }
    }
}
