package com.runstate.mobile.run

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class RunCountdownOwnerTest {
    @Test fun cancellationInvalidatesPermissionBeforeAnImmediatelyResumedWaiterCanStart() = runBlocking {
        val owner = RunCountdownOwner()
        val token = owner.reserve()
        var starts = 0
        val work = async(Dispatchers.Unconfined) {
            owner.execute(token, { CompletableDeferred<Unit>().await() }, {}) { starts++ }
        }
        assertEquals(RunCountdownPhase.INTRO, owner.state.value.phase)
        owner.cancel(token)
        assertFalse(work.await())
        assertEquals(0, starts)
        owner.retire(token)
    }

    @Test fun cancelBeforeServiceArrivalHoldsAdmissionUntilCleanupWithoutSpeakingOrStarting() = runBlocking {
        val owner = RunCountdownOwner()
        val token = owner.reserve()
        assertTrue(owner.cancel(token))
        assertEquals(RunCountdownPhase.CANCELLING, owner.state.value.phase)
        assertFalse(owner.execute(token, { error("Cancelled intro") }, { error("Cancelled count") }) {
            error("Cancelled start")
        })
        assertTrue(owner.state.value.active)
        owner.retire(token)
        val next = owner.reserve()
        owner.retire(token) // A retired service cannot clear the new explicit start.
        assertTrue(owner.owns(next))
        owner.cancel(next)
        owner.retire(next)
    }

    @Test fun tenIsVisibleImmediatelyAndHeldThroughIntroBeforeTenTicksAndOneOfficialStart() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val intro = CompletableDeferred<Unit>()
        val timedSeconds = mutableListOf<Long>()
        val owner = RunCountdownOwner(waitMillis = {
            timedSeconds.add(it)
            ticks.receive()
        })
        val token = owner.reserve()
        assertEquals(RunCountdownPhase.PREPARING, owner.state.value.phase)
        assertEquals(10, owner.state.value.digit)
        val spoken = mutableListOf<Int>()
        var starts = 0
        val work = async(start = CoroutineStart.UNDISPATCHED) {
            owner.execute(token, { intro.await() }, spoken::add) {
                assertFalse(owner.state.value.startedEarly)
                starts++
            }
        }
        repeat(5) { yield() }
        assertEquals(RunCountdownPhase.INTRO, owner.state.value.phase)
        assertEquals(10, owner.state.value.digit)
        assertTrue(spoken.isEmpty())
        assertTrue(timedSeconds.isEmpty())
        assertEquals(0, starts)
        intro.complete(Unit)
        repeat(5) { yield() }
        assertEquals(RunCountdownPhase.COUNTING, owner.state.value.phase)
        assertEquals(10, owner.state.value.digit)
        repeat(9) { ticks.send(Unit); repeat(5) { yield() } }
        assertEquals(1, owner.state.value.digit)
        assertEquals(0, starts)
        ticks.send(Unit)
        assertTrue(work.await())
        assertEquals(List(10) { 1_000L }, timedSeconds)
        assertEquals(listOf(10, 3, 2, 1), spoken)
        assertEquals(1, starts)
        assertFalse(owner.state.value.active)
        assertFalse(owner.startNow(token))
    }

    @Test fun startNowDuringIntroductionStopsWaitingAndCommitsOnlyOnce() = runBlocking {
        val intro = CompletableDeferred<Unit>()
        val owner = RunCountdownOwner()
        val token = owner.reserve()
        var starts = 0
        val work = async {
            owner.execute(token, { intro.await() }, { error("No remaining counts") }) {
                assertTrue(owner.state.value.startedEarly)
                starts++
            }
        }
        repeat(3) { yield() }
        assertTrue(owner.startNow(token))
        assertFalse(owner.startNow(token))
        assertTrue(work.await())
        intro.complete(Unit)
        assertEquals(1, starts)
        assertFalse(owner.execute(token, {}, {}) { starts++ })
        assertEquals(1, starts)
    }

    @Test fun cancelDuringIntroductionCannotBeRevivedByLateSpeechOrStartNow() = runBlocking {
        val intro = CompletableDeferred<Unit>()
        val owner = RunCountdownOwner()
        val token = owner.reserve()
        var starts = 0
        val work = async { owner.execute(token, { intro.await() }, {}) { starts++ } }
        repeat(3) { yield() }
        assertTrue(owner.cancel(token))
        assertFalse(work.await())
        owner.retire(token)
        intro.complete(Unit)
        assertFalse(owner.startNow(token))
        assertEquals(0, starts)
    }

    @Test fun cancelWinsDuringFinalSecondAndStaleCommandsCannotAffectNextCountdown() = runBlocking {
        val tick = Channel<Unit>(Channel.UNLIMITED)
        val owner = RunCountdownOwner(seconds = 1, waitMillis = { tick.receive() })
        val first = owner.reserve()
        var starts = 0
        val work = async { owner.execute(first, {}, {}) { starts++ } }
        repeat(5) { yield() }
        assertEquals(1, owner.state.value.digit)
        assertTrue(owner.cancel(first))
        assertFalse(work.await())
        owner.retire(first)
        val next = owner.reserve()
        assertFalse(owner.startNow(first))
        assertFalse(owner.cancel(first))
        assertTrue(owner.owns(next))
        assertEquals(0, starts)
        owner.cancel(next)
        owner.retire(next)
    }

    @Test fun recreatedObserverAttachesToSameCounterWithoutAnotherWorkerOrStart() = runBlocking {
        val ticks = Channel<Unit>(Channel.UNLIMITED)
        val owner = RunCountdownOwner(seconds = 2, waitMillis = { ticks.receive() })
        val token = owner.reserve()
        var starts = 0
        val work = async { owner.execute(token, {}, {}) { starts++ } }
        repeat(5) { yield() }
        val firstObservedState = owner.state.value
        assertEquals(2, firstObservedState.digit)
        assertFalse(owner.execute(token, { error("Must not restart intro") }, {}) { starts++ })
        ticks.send(Unit)
        repeat(5) { yield() }
        assertEquals(1, owner.state.value.digit)
        assertEquals(token, owner.state.value.token)
        ticks.send(Unit)
        assertTrue(work.await())
        assertEquals(1, starts)
    }

    @Test fun missingIntroCompletionHasBoundedFallbackAndNoNetworkDependency() = runBlocking {
        val owner = RunCountdownOwner(seconds = 1, stepMillis = 1, introTimeoutMillis = 20)
        var starts = 0
        val token = owner.reserve()
        assertTrue(owner.execute(token, { CompletableDeferred<Unit>().await() }, {}) { starts++ })
        assertEquals(1, starts)
    }

    @Test fun onceDurableStartIsClaimedNeitherCancelNorStartNowCanStartAnotherRun() = runBlocking {
        val owner = RunCountdownOwner(seconds = 1, stepMillis = 1)
        val token = owner.reserve()
        val writing = CompletableDeferred<Unit>()
        var starts = 0
        val work = async {
            owner.execute(token, {}, {}) { starts++; writing.await() }
        }
        while (owner.state.value.phase != RunCountdownPhase.STARTING) yield()
        assertFalse(owner.cancel(token))
        assertFalse(owner.startNow(token))
        writing.complete(Unit)
        assertTrue(work.await())
        assertEquals(1, starts)
    }

    @Test fun cancelledServiceScopeRetiresCountdownWithoutStartingAndNewProcessHasNoPermission() = runBlocking {
        val owner = RunCountdownOwner()
        val token = owner.reserve()
        var starts = 0
        val work = launch { owner.execute(token, { CompletableDeferred<Unit>().await() }, {}) { starts++ } }
        repeat(3) { yield() }
        work.cancel()
        work.join()
        assertFalse(owner.state.value.active)
        assertEquals(0, starts)
        val restartedProcess = RunCountdownOwner()
        assertFalse(restartedProcess.execute(token, {}, {}) { starts++ })
        assertEquals(0, starts)
    }

    @Test fun failedDurableStartRetiresOwnerAndDoesNotRetryAutomatically() = runBlocking {
        val owner = RunCountdownOwner(seconds = 1, stepMillis = 1)
        val token = owner.reserve()
        var starts = 0
        try {
            owner.execute(token, {}, {}) { starts++; throw IllegalStateException("Synthetic storage failure") }
            fail("Failure must reach the service")
        } catch (_: IllegalStateException) { }
        assertEquals(1, starts)
        assertFalse(owner.state.value.active)
        assertFalse(owner.execute(token, {}, {}) { starts++ })
        assertEquals(1, starts)
    }
}
