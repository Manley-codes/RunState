package com.runstate.mobile.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCpuWakeLeaseTest {
    @Test
    fun `start renewal pause and restart never stack wake locks`() {
        val events = mutableListOf<String>()
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = { FakeWakeLock("lock${locks.size + 1}", events).also { locks.add(it) } },
            timeoutMillis = 120_000L
        )

        lease.renew() // A stopped or paused run must not restart itself.
        lease.start()
        lease.start()
        assertEquals(1, locks.size)
        assertTrue(locks[0].isHeld)

        lease.renew()
        assertEquals(2, locks.size)
        assertEquals(
            listOf("lock1.acquire:120000", "lock2.acquire:120000", "lock1.release"),
            events
        )
        assertFalse(locks[0].isHeld)
        assertTrue(locks[1].isHeld)

        lease.stop() // Pause, provider loss, failure, completion, and destroy use this path.
        lease.stop()
        lease.renew()
        assertFalse(locks[1].isHeld)
        assertEquals(2, locks.size)
        assertEquals(1, events.count { it == "lock2.release" })

        lease.start() // Explicit resume takes a fresh timed lock.
        assertEquals(3, locks.size)
        assertTrue(locks[2].isHeld)
        lease.stop()
        assertTrue(locks.none { it.isHeld })
    }

    @Test
    fun `failed renewal cleans replacement and lets stop release previous lock`() {
        val events = mutableListOf<String>()
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = {
                FakeWakeLock("lock${locks.size + 1}", events).also {
                    if (locks.isNotEmpty()) it.failAfterAcquire = true
                    locks.add(it)
                }
            },
            timeoutMillis = 120_000L
        )
        lease.start()

        try {
            lease.renew()
            throw AssertionError("Expected the simulated platform acquisition to fail")
        } catch (_: IllegalStateException) {
            // The service catches this and stops GPS, which calls lease.stop().
        }
        assertTrue(locks[0].isHeld)
        assertFalse(locks[1].isHeld)
        lease.stop()
        assertTrue(locks.none { it.isHeld })
    }

    @Test
    fun `expired timeout can be stopped and replaced safely`() {
        val locks = mutableListOf<FakeWakeLock>()
        val lease = RunCpuWakeLease(
            newLock = {
                FakeWakeLock("lock${locks.size + 1}", mutableListOf()).also { locks.add(it) }
            },
            timeoutMillis = 120_000L
        )
        lease.start()
        locks[0].held = false // Android's hard timeout released it before service cleanup.
        lease.stop()
        lease.start()
        assertEquals(2, locks.size)
        assertTrue(locks[1].isHeld)
        lease.stop()
    }

    private class FakeWakeLock(
        private val name: String,
        private val events: MutableList<String>
    ) : TimedCpuWakeLock {
        var held = false
        var failAfterAcquire = false
        override val isHeld: Boolean get() = held

        override fun acquire(timeoutMillis: Long) {
            events.add("$name.acquire:$timeoutMillis")
            held = true
            if (failAfterAcquire) throw IllegalStateException("Simulated platform failure")
        }

        override fun release() {
            events.add("$name.release")
            held = false
        }
    }
}
