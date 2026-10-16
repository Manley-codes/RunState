package com.runstate.mobile

import android.app.Application
import android.os.Build
import com.runstate.mobile.data.local.RunStateDatabase
import com.runstate.mobile.data.local.buildRunStateDatabase
import com.runstate.mobile.location.RunTrackingServiceState
import com.runstate.mobile.reflection.LocalReflectionHttpClient
import com.runstate.mobile.reflection.AndroidReflectionConnectionStore
import com.runstate.mobile.reflection.HostedReflectionHttpClient
import com.runstate.mobile.reflection.ReflectionConnectionConfig
import com.runstate.mobile.reflection.ReflectionGateway
import com.runstate.mobile.reflection.ReflectionBridgeException
import com.runstate.mobile.reflection.LOCAL_REFLECTION_ENDPOINT
import com.runstate.mobile.reflection.PHONE_REFLECTION_ENDPOINT
import com.runstate.mobile.reflection.RoomReflectionStore
import com.runstate.mobile.reflection.RunReflectionCoordinator
import com.runstate.mobile.reflection.reflectionBridgeEnabledForBuild
import com.runstate.mobile.run.PreparedRunFactory
import com.runstate.mobile.run.RunSessionCoordinator
import com.runstate.mobile.settings.AndroidRunPreferencesStore
import com.runstate.mobile.settings.RunPreferences
import java.time.Clock
import android.os.SystemClock
import com.runstate.mobile.run.RunTimingMark
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Configuration is distinct from proof that a server is currently reachable. */
internal sealed interface ReflectionConnectionState {
    data object Loading : ReflectionConnectionState
    data object Local : ReflectionConnectionState
    data object NotConfigured : ReflectionConnectionState
    data object Unavailable : ReflectionConnectionState
    data class Hosted(val config: ReflectionConnectionConfig) : ReflectionConnectionState
}

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
 * `MainActivity` drives the visible journey through the coordinator. A same-process
 * foreground service now owns GPS run execution after the visible countdown.
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
 * therefore stays empty: both properties below are lazy, and nothing in this class calls
 * [RunSessionCoordinator.initialize]. Recovery is invoked deliberately by `MainActivity`,
 * from a composition coroutine rather than from application startup, so it never runs on
 * the main thread and never runs for a test that had no use for it.
 */
class RunStateApplication : Application() {

    /** Temporary demo drafts may survive rotation, but never inherit another process's mode. */
    internal val runSettingsSessionId: String = UUID.randomUUID().toString()

    private val mutableTrackingServiceState = MutableStateFlow<RunTrackingServiceState>(
        RunTrackingServiceState.Idle
    )

    internal val trackingServiceState: StateFlow<RunTrackingServiceState> =
        mutableTrackingServiceState.asStateFlow()

    internal fun publishTrackingServiceState(state: RunTrackingServiceState) {
        mutableTrackingServiceState.value = state
    }

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
     * The one wall clock run timestamps are read from in this process.
     *
     * Shared rather than created per use so every recorded moment of a run — its official
     * start now, and its pause, resume and completion once those are wired — comes from the
     * same source, and a test double can replace all of them at once. UTC because only the
     * instant is ever read; the zone a run began in is a separate fact, captured by
     * [PreparedRunFactory] from the phone's current setting.
     */
    internal val clock: Clock = Clock.systemUTC()

    // An elapsed-time reading is comparable only within this uninterrupted process.
    private val timingSessionId = UUID.randomUUID().toString()

    /**
     * The scope for work that must outlive any screen.
     *
     * An official start runs here rather than in an Activity's scope, so rotating the phone
     * or leaving the screen cancels only the screen's waiting, never the insert. It is never
     * cancelled: it lives exactly as long as this process does.
     *
     * - `SupervisorJob` so one failed start is reported through its own result instead of
     *   cancelling the scope — and with it every later start in the process.
     * - `Dispatchers.Main.immediate` because the coordinator's results are consumed by the
     *   UI, and Room's suspending DAO methods already move their own I/O off the main
     *   thread. `immediate` skips a pointless re-dispatch when the caller is already there.
     *
     * Lazy, like the properties around it, so application startup does not touch the main
     * dispatcher for a test that never asks for a coordinator.
     */
    internal val applicationScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    private val runPreferencesStore by lazy { AndroidRunPreferencesStore(this) }
    private val runPreferencesLock = Mutex()
    private var runPreferencesLoadStarted = false
    private var pendingRunPreferencesSaves = 0
    private val mutableRunPreferences = MutableStateFlow<RunPreferences?>(null)
    internal val runPreferences: StateFlow<RunPreferences?> = mutableRunPreferences.asStateFlow()
    private val mutableRunPreferencesSaving = MutableStateFlow(false)
    internal val runPreferencesSaving: StateFlow<Boolean> = mutableRunPreferencesSaving.asStateFlow()
    private val mutableRunPreferencesError = MutableStateFlow<String?>(null)
    internal val runPreferencesError: StateFlow<String?> = mutableRunPreferencesError.asStateFlow()

    /** Explicit setup: tests and Application startup never read a runner's saved choices. */
    internal fun initializeRunPreferences() {
        synchronized(this) {
            if (runPreferencesLoadStarted || mutableRunPreferences.value != null) return
            runPreferencesLoadStarted = true
        }
        applicationScope.launch {
            try {
                runPreferencesLock.withLock {
                    // A save accepted before initialization may already have supplied the state.
                    if (mutableRunPreferences.value != null) return@withLock
                    mutableRunPreferencesError.value = null
                    try {
                        mutableRunPreferences.value = withContext(Dispatchers.IO) {
                            runPreferencesStore.load()
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        mutableRunPreferencesError.value =
                            "Run preferences could not be loaded. Try again."
                    }
                }
            } finally {
                // Failure remains retryable; successful initialization is guarded by its value.
                synchronized(this@RunStateApplication) { runPreferencesLoadStarted = false }
            }
        }
    }

    /** Rotation cancels only the caller's wait; an accepted preference save finishes in the app. */
    internal suspend fun saveRunPreferences(next: RunPreferences) {
        applicationScope.async {
            synchronized(this@RunStateApplication) {
                pendingRunPreferencesSaves += 1
                mutableRunPreferencesSaving.value = true
            }
            try {
                runPreferencesLock.withLock {
                    try {
                        withContext(Dispatchers.IO) { runPreferencesStore.save(next) }
                        // Publish only after commit, preserving the previous confirmed choice on error.
                        mutableRunPreferences.value = next
                        mutableRunPreferencesError.value = null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        val message = "Your run preferences could not be saved. Please try again."
                        mutableRunPreferencesError.value = message
                        throw IllegalStateException(message)
                    }
                }
            } finally {
                synchronized(this@RunStateApplication) {
                    pendingRunPreferencesSaves -= 1
                    mutableRunPreferencesSaving.value = pendingRunPreferencesSaves > 0
                }
            }
        }.await()
    }

    /**
     * The one admission gate for runs in this process.
     *
     * Built over [database]'s DAO rather than a database or DAO of its own, so the
     * coordinator, recovery and every run it admits all reach storage the same way.
     * Touching this constructs the Room instance and obtains its DAO; Room opens the file
     * on the first real query. The property is lazy so neither object exists until a caller
     * needs the admission boundary — which is now `MainActivity`, on its first composition,
     * rather than anything in this class during startup.
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
        RunSessionCoordinator(
            runDao = database.runDao(),
            locationDao = database.locationDao(),

            // The phone's zone is read when each run is prepared, not captured here, so a
            // run records the zone it actually began in.
            preparedRunFactory = PreparedRunFactory(
                clock = clock,
                zoneIdSupplier = ZoneId::systemDefault,
                uuidSupplier = UUID::randomUUID,
                timingMarkSupplier = { RunTimingMark(SystemClock.elapsedRealtime(), timingSessionId) }
            ),
            applicationScope = applicationScope
        )
    }

    private val connectionStore by lazy { AndroidReflectionConnectionStore(this) }
    private val connectionLock = Mutex()
    private var connectionLoadStarted = false
    private val mutableReflectionConnection = MutableStateFlow<ReflectionConnectionState>(
        ReflectionConnectionState.Loading
    )
    internal val reflectionConnection: StateFlow<ReflectionConnectionState> =
        mutableReflectionConnection.asStateFlow()
    private val mutableConnectionSaving = MutableStateFlow(false)
    internal val reflectionConnectionSaving: StateFlow<Boolean> = mutableConnectionSaving.asStateFlow()

    /** Explicitly invoked by the Activity; tests and Application startup do not open the Keystore. */
    internal fun initializeReflectionConnection() {
        synchronized(this) {
            if (connectionLoadStarted) return
            connectionLoadStarted = true
        }
        applicationScope.launch {
            connectionLock.withLock {
                mutableReflectionConnection.value = try {
                    connectionStore.load()?.let { ReflectionConnectionState.Hosted(it) }
                        ?: defaultReflectionConnection()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Corrupt credentials never silently change the request's destination.
                    ReflectionConnectionState.Unavailable
                }
            }
        }
    }

    internal suspend fun saveReflectionConnection(endpoint: String, accessCode: String) {
        val config = ReflectionConnectionConfig(endpoint.trim(), accessCode.trim())
        changeReflectionConnection {
            connectionStore.save(config)
            mutableReflectionConnection.value = ReflectionConnectionState.Hosted(config)
        }
    }

    internal suspend fun clearReflectionConnection() = changeReflectionConnection {
        connectionStore.clear()
        mutableReflectionConnection.value = defaultReflectionConnection()
    }

    /** Rotation cancels the caller's wait, never a connection write already accepted by the app. */
    private suspend fun changeReflectionConnection(change: suspend () -> Unit) {
        applicationScope.async {
            connectionLock.withLock {
                mutableConnectionSaving.value = true
                try { change() } finally { mutableConnectionSaving.value = false }
            }
        }.await()
    }

    private fun defaultReflectionConnection(): ReflectionConnectionState =
        if (reflectionBridgeEnabledForBuild()) ReflectionConnectionState.Local
        else ReflectionConnectionState.NotConfigured

    private var reflectionOwner: RunReflectionCoordinator? = null

    /** Local feedback works offline; network admission separately requires a selected connection. */
    internal val runReflectionCoordinator: RunReflectionCoordinator
        get() {
            return synchronized(this) {
                reflectionOwner ?: RunReflectionCoordinator(
                    runDao = database.runDao(),
                    store = RoomReflectionStore(database.reflectionDao(), database.musicObservationDao()),
                    gateway = ReflectionGateway { request ->
                        when (val connection = mutableReflectionConnection.value) {
                            is ReflectionConnectionState.Hosted ->
                                HostedReflectionHttpClient(connection.config).request(request)
                            ReflectionConnectionState.Local -> {
                                val emulator = Build.PRODUCT.startsWith("sdk_") ||
                                    Build.DEVICE.startsWith("emu") || Build.HARDWARE == "ranchu"
                                LocalReflectionHttpClient(
                                    if (emulator) LOCAL_REFLECTION_ENDPOINT else PHONE_REFLECTION_ENDPOINT
                                ).request(request)
                            }
                            else -> throw ReflectionBridgeException("Reflection connection needs setup.")
                        }
                    },
                    applicationScope = applicationScope
                ).also { reflectionOwner = it }
            }
        }
}
