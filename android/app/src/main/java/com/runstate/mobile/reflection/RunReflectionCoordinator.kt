package com.runstate.mobile.reflection

import com.google.gson.GsonBuilder
import com.runstate.mobile.data.local.*
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface RunReflectionState {
    data object Idle : RunReflectionState
    data class Preparing(val runId: String, val energy: ReflectionBranch? = null) : RunReflectionState
    data class Ready(val runId: String, val text: String, val energy: ReflectionBranch? = null,
        val isTestData: Boolean = true) : RunReflectionState
    data class Failed(val runId: String, val energy: ReflectionBranch? = null) : RunReflectionState
}

/** Storage seam for deterministic coordinator tests; Room implements the durable rules. */
internal interface ReflectionStore {
    suspend fun find(runId: String): RunReflectionEntity?
    suspend fun all(): List<RunReflectionEntity>
    suspend fun ensure(record: RunReflectionEntity): RunReflectionEntity
    suspend fun energy(runId: String, branch: ReflectionBranch, now: Long): RunReflectionEntity
    suspend fun defaultSelection(runId: String, now: Long): RunReflectionEntity
    suspend fun candidates(runId: String, requestId: String, json: String, isTestData: Boolean): Boolean
    suspend fun ready(runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long): Boolean
    suspend fun fail(runId: String, requestId: String): Boolean
    suspend fun retry(runId: String, requestId: String): RunReflectionEntity?
}

internal class RoomReflectionStore(private val dao: ReflectionDao) : ReflectionStore {
    override suspend fun find(runId: String) = dao.findByRunId(runId)
    override suspend fun all() = dao.all()
    override suspend fun ensure(record: RunReflectionEntity) = dao.ensurePending(record)
    override suspend fun energy(runId: String, branch: ReflectionBranch, now: Long) = dao.recordEnergy(runId, branch, now)
    override suspend fun defaultSelection(runId: String, now: Long) = dao.selectNoSelectionIfDue(runId, now)
    override suspend fun candidates(runId: String, requestId: String, json: String, isTestData: Boolean) = dao.saveCandidates(runId, requestId, json, isTestData)
    override suspend fun ready(runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long) = dao.finalizeReady(runId, requestId, branch, text, now)
    override suspend fun fail(runId: String, requestId: String) = dao.markFailed(runId, requestId)
    override suspend fun retry(runId: String, requestId: String) = dao.retry(runId, requestId)
}

/**
 * Room owns selection and exact final text. The process owns only running work.
 * Network waits never hold the mutation mutex, so Energy can save during generation.
 */
internal class RunReflectionCoordinator(
    private val runDao: RunDao,
    private val store: ReflectionStore,
    private val gateway: ReflectionGateway,
    private val applicationScope: CoroutineScope,
    private val requestIdSupplier: () -> UUID = UUID::randomUUID,
    private val quietWindowMillis: Long = 6_000L,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val quietDelay: suspend (Long) -> Unit = { delay(it) }
) {
    private val gson = GsonBuilder().serializeNulls().create()
    private val mutex = Mutex()
    private val initialization = Mutex()
    private var initialized = false
    private val requests = mutableSetOf<String>()
    private val timers = mutableSetOf<String>()
    private var activeRunId: String? = null
    private val mutableState = MutableStateFlow<RunReflectionState>(RunReflectionState.Idle)
    val state: StateFlow<RunReflectionState> = mutableState.asStateFlow()

    /** Recover local replies without silently starting another potentially paid request. */
    suspend fun initialize() = initialization.withLock {
        if (!initialized) {
            store.all().filter { it.status == ReflectionStatus.PENDING }.forEach { record ->
                if (record.candidatesJson == null) store.fail(record.runId, record.requestId)
                else mutex.withLock { finalizeIfPossible(record.runId) }
            }
            initialized = true
        }
    }

    fun onSavedVisible(runId: String, bridgeReady: Boolean = true) {
        activeRunId = runId
        applicationScope.launch {
            try {
                initialize()
                mutex.withLock {
                    var record = store.find(runId)
                    if (record == null) {
                        val run = requireNotNull(runDao.findById(runId))
                        val request = reflectionRequestFromSavedRun(
                            requestIdSupplier().toString(), run, runDao.transitionsFor(runId))
                        record = store.ensure(RunReflectionEntity(
                            runId = runId, requestId = request.requestId,
                            evidenceJson = gson.toJson(request.evidence),
                            selectionDeadlineEpochMillis = nowMillis() + quietWindowMillis))
                    }
                    finalizeIfPossible(runId)
                    publish(requireNotNull(store.find(runId)))
                    if (record.status == ReflectionStatus.PENDING) {
                        scheduleSelection(runId, record.selectionDeadlineEpochMillis)
                        if (bridgeReady && record.candidatesJson == null) startRequest(runId)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishFailure(runId) }
        }
    }

    fun startWhenBridgeReady(runId: String) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                val record = store.find(runId) ?: return@withLock
                if (record.status == ReflectionStatus.FAILED) store.retry(runId, requestIdSupplier().toString())
                startRequest(runId)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    fun onBridgeUnavailable(runId: String) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                store.find(runId)?.let { store.fail(runId, it.requestId) }
                store.find(runId)?.let(::publish)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    fun selectEnergy(runId: String, branch: ReflectionBranch) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                store.energy(runId, branch, nowMillis())
                finalizeIfPossible(runId)
                publish(requireNotNull(store.find(runId)))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    fun retry(runId: String) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                if (runId in requests) return@withLock
                store.retry(runId, requestIdSupplier().toString()) ?: return@withLock
                startRequest(runId)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    private fun scheduleSelection(runId: String, deadline: Long) {
        if (!timers.add(runId)) return
        applicationScope.launch {
            try {
                quietDelay((deadline - nowMillis()).coerceAtLeast(0))
                mutex.withLock {
                    finalizeIfPossible(runId)
                    store.find(runId)?.let(::publish)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishFailure(runId) }
            finally { timers.remove(runId) }
        }
    }

    /** Called under mutex; request ownership is reserved before launching network work. */
    private suspend fun startRequest(runId: String) {
        val record = store.find(runId) ?: return
        if (record.status != ReflectionStatus.PENDING || record.candidatesJson != null || !requests.add(runId)) return
        publish(record)
        scheduleSelection(runId, record.selectionDeadlineEpochMillis)
        applicationScope.launch {
            try {
                val evidence = gson.fromJson(record.evidenceJson, CompletedRunEvidence::class.java)
                val candidates = gateway.request(ReflectionApiRequest(record.requestId, runId, evidence))
                mutex.withLock {
                    store.candidates(runId, record.requestId, gson.toJson(candidates), candidates.isTestData)
                    finalizeIfPossible(runId)
                    store.find(runId)?.let(::publish)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                mutex.withLock {
                    store.fail(runId, record.requestId)
                    store.find(runId)?.let(::publish)
                }
            } finally { requests.remove(runId) }
        }
    }

    private suspend fun finalizeIfPossible(runId: String) {
        val record = store.defaultSelection(runId, nowMillis())
        if (record.status != ReflectionStatus.PENDING) return
        val branch = record.selectedBranch
        if (branch == null) {
            scheduleSelection(runId, record.selectionDeadlineEpochMillis)
            return
        }
        val json = record.candidatesJson ?: return
        val candidates = gson.fromJson(json, ReflectionCandidates::class.java)
        val text = when (branch) {
            ReflectionBranch.SPENT -> candidates.spent
            ReflectionBranch.FEELING_GOOD -> candidates.feelingGood
            ReflectionBranch.POWERED_UP -> candidates.poweredUp
            ReflectionBranch.NO_SELECTION -> candidates.noSelection
        }
        store.ready(runId, record.requestId, branch, text, nowMillis())
    }

    private fun publish(record: RunReflectionEntity) {
        if (activeRunId != record.runId) return
        mutableState.value = when (record.status) {
            ReflectionStatus.PENDING -> RunReflectionState.Preparing(record.runId, record.postRunEnergy)
            ReflectionStatus.READY -> RunReflectionState.Ready(record.runId, requireNotNull(record.text), record.postRunEnergy, record.isTestData)
            ReflectionStatus.FAILED -> RunReflectionState.Failed(record.runId, record.postRunEnergy)
        }
    }

    private fun publishFailure(runId: String) {
        if (activeRunId == runId) mutableState.value = RunReflectionState.Failed(runId)
    }
}
