package com.runstate.mobile

import android.app.Application
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.buildRunStateDatabase

/**
 * The process-scoped home of the one run database.
 *
 * Android creates exactly one Application instance per app process and keeps it for the
 * life of that process, which makes it the natural place to answer "how long does the
 * database live". Everything below is about that single question; no run logic, no
 * recovery and no startup work belongs here.
 *
 * ## Why one instance matters, and what it does not settle
 *
 * Room is expensive to open and caches per instance, so building a second one over the
 * same file wastes work and gives observers two separate invalidation trackers — a
 * change written through one instance does not notify queries observing through the
 * other. That is the real cost of duplicate instances, and it is a correctness problem
 * for anything watching the database rather than a durability problem.
 *
 * What duplicate instances do **not** do is weaken SQLite. Transactions stay atomic and
 * the conditional updates in [com.runstate.mobile.data.local.RunDao] keep working across
 * connections and across processes: a losing write still matches no row and still fails
 * rather than corrupting a stored run. Storage defends itself. The thing storage cannot
 * defend is the app's in-memory picture of a run.
 *
 * Which is why one database instance is necessary but nowhere near sufficient. It does
 * not establish one active-run owner. Two `ActiveRunSession` objects over the same run,
 * even sharing this exact database, hold two unrelated mutexes and two independent state
 * machines, and would drift apart while every individual write remained perfectly legal.
 * Single ownership — and running recovery before a new start can create a second owner —
 * is separate wiring that does not exist yet.
 *
 * ## Two constraints on future work
 *
 * **The foreground service must stay in this process.** Giving it `android:process` would
 * put it in a second process with its own Application instance, its own lazy database and
 * its own in-memory run owner. The stored run would survive that — see above — but the
 * app would then have two owners of one live run with no way to coordinate them, which is
 * precisely the split the session design exists to prevent. Any future service, receiver
 * or provider added for an active run stays in the main process.
 *
 * **[onCreate] is deliberately not overridden.** This class is instantiated during
 * instrumented tests too, so anything done at startup is done before every emulator test,
 * on the main thread, whether that test needs it or not. Opening a database there would
 * also mean a query on the main thread. Startup therefore stays empty and the cost is
 * deferred to [database]'s first real use.
 */
class RunStateApplication : Application() {

    /**
     * The one database this process uses, opened the first time something asks for it.
     *
     * Kotlin's default `lazy` is `LazyThreadSafetyMode.SYNCHRONIZED`, and that default is
     * load-bearing here rather than incidental. The first touch can easily come from more
     * than one thread — a background coroutine and the UI reaching for it together — and
     * `NONE` would let both run the initializer and hand out two different Room instances
     * over the same file, which is the duplicate-instance problem described above. It must
     * not be changed to `NONE`.
     *
     * Module-internal because nothing outside this app has any business holding it, and
     * because the eventual owner of this property is a repository or startup coordinator
     * inside the module rather than an external caller.
     */
    internal val database: RunStateDatabase by lazy { buildRunStateDatabase(this) }
}
