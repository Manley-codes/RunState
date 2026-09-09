package com.runstate.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the process gets one database instance, not one per caller.
 *
 * This is a regression test with a specific mistake in mind. `by lazy` and a plain
 * expression body read almost identically at a glance:
 *
 * ```
 * internal val database: RunStateDatabase by lazy { buildRunStateDatabase(this) }
 * internal val database: RunStateDatabase get() = buildRunStateDatabase(this)
 * ```
 *
 * The second compiles, type-checks, and builds a brand-new Room instance on every single
 * access. Nothing would visibly break — each instance opens the same file, and SQLite
 * keeps every write atomic across them — so the damage would be quiet: duplicated open
 * cost, per-instance caches, and separate invalidation trackers, so a write through one
 * instance would never notify a query observing through another. [assertSame] is what
 * makes that substitution fail, because equality would not: two Room instances over one
 * file are not `equals`, but nothing else about them looks wrong either.
 *
 * The test deliberately does nothing else. It does not query the database, close it,
 * delete the file or clear application data — the instance it touches is the real one
 * this test process is using, and disturbing it would affect whatever else runs in the
 * same process. Identity is the whole assertion.
 */
@RunWith(AndroidJUnit4::class)
class RunStateApplicationTest {

    /**
     * Proves two reads of the database property return the very same object.
     */
    @Test
    fun theDatabasePropertyReturnsOneInstancePerProcess() {

        // Arrange: the real Application instance Android built for this test process.
        val application = ApplicationProvider.getApplicationContext<RunStateApplication>()

        // Act: ask twice, the way two unrelated callers eventually will.
        val first = application.database
        val second = application.database

        // Assert: one instance, not two equal-looking ones.
        assertSame(first, second)
    }
}
