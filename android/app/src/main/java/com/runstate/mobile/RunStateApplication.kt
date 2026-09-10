package com.runstate.mobile

import android.app.Application
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.buildRunStateDatabase
import com.runstate.mobile.run.RunSessionCoordinator

/**
 * The composition root: one database and one run coordinator, for the process.
 *
 * Android creates exactly one Application instance per app process and keeps it for the
 * life of that process, which makes it the natural place to answer "how long does this
 * live". That is the only question it answers. It holds the two process-scoped objects
 * and wires them together; it does not run recovery, decide admission, or do any run
 * logic itself. [RunSessionCoordinator] owns all of that, and it does nothing at all
 * until something explicitly calls it.
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
 * That is what [runSessionCoordinator] is for: one gate, holding one owner, running
 * recovery before any start is admitted. It is held here rather than built per caller for
 * the same reason the database is — a second coordinator would be a second gate, and two
 * gates guard nothing.
 *
 * What that does *not* mean is that the app's journey is wired. Nothing here calls the
 * coordinator, and no Activity or service uses it yet.
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
 * on the main thread, whether that test needs it or not. Running recovery there would mean
 * a database query on the main thread before any test that never wanted one. Startup
 * therefore stays empty: both properties below are lazy, and
 * [RunSessionCoordinator.initialize] is never called automatically. Deciding when
 * recovery runs belongs to the startup slice that will call it deliberately.
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

    /**
     * The one admission gate for runs in this process.
     *
     * Built over [database]'s DAO rather than a database or DAO of its own, so the
     * coordinator, recovery and every run it admits all reach storage the same way.
     * Touching this constructs the Room instance and obtains its DAO; Room opens the file
     * on the first real query. The property is lazy so neither object exists until a caller
     * needs the admission boundary, and nothing calls it during startup.
     *
     * The default synchronized `lazy` matters here for the same reason it does above, and
     * more sharply: two coordinators would be two independent gates, each with its own
     * lock and its own idea of which run is live, and the whole point of the class is
     * that there is exactly one.
     *
     * Constructing it runs no recovery and touches no rows. [RunSessionCoordinator]
     * starts in `NotAttempted` and does nothing until something calls
     * [RunSessionCoordinator.initialize].
     */
    internal val runSessionCoordinator: RunSessionCoordinator by lazy {
        RunSessionCoordinator(database.runDao())
    }
}
