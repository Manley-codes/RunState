package com.runstate.mobile.reflection

import com.google.gson.GsonBuilder
import com.runstate.mobile.data.local.*
import com.runstate.mobile.weather.RunWeatherSnapshot
import com.runstate.mobile.music.SongPassageRequest
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
        val isTestData: Boolean = true, val passage: SongPassageRequest? = null) : RunReflectionState
    data class Failed(val runId: String, val energy: ReflectionBranch? = null) : RunReflectionState
}

/** Storage seam for deterministic coordinator tests; Room implements the durable rules. */
internal interface ReflectionStore {
    suspend fun find(runId: String): RunReflectionEntity?
    suspend fun all(): List<RunReflectionEntity>
    suspend fun ensure(record: RunReflectionEntity): RunReflectionEntity
    suspend fun energy(runId: String, branch: ReflectionBranch, now: Long, selectionHeld: Boolean): RunReflectionEntity
    suspend fun defaultSelection(runId: String, now: Long): RunReflectionEntity
    suspend fun beginSelectionWindow(runId: String, deadline: Long): RunReflectionEntity
    suspend fun candidates(runId: String, requestId: String, json: String, isTestData: Boolean): Boolean
    suspend fun ready(runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long): Boolean
    suspend fun fail(runId: String, requestId: String): Boolean
    suspend fun retry(runId: String, requestId: String): RunReflectionEntity?
    suspend fun musicObservations(runId: String): List<RunMusicObservationEntity> = emptyList()
    suspend fun weatherSnapshot(runId: String): RunWeatherSnapshot? = null
}

internal class RoomReflectionStore(
    private val dao: ReflectionDao,
    private val musicDao: MusicObservationDao? = null,
    private val weatherDao: WeatherDao? = null
) : ReflectionStore {
    override suspend fun find(runId: String) = dao.findByRunId(runId)
    override suspend fun all() = dao.all()
    override suspend fun ensure(record: RunReflectionEntity) = dao.ensurePending(record)
    override suspend fun energy(runId: String, branch: ReflectionBranch, now: Long, selectionHeld: Boolean) = dao.recordEnergy(runId, branch, now, selectionHeld)
    override suspend fun defaultSelection(runId: String, now: Long) = dao.selectNoSelectionIfDue(runId, now)
    override suspend fun beginSelectionWindow(runId: String, deadline: Long) = dao.beginSelectionWindow(runId, deadline)
    override suspend fun candidates(runId: String, requestId: String, json: String, isTestData: Boolean) = dao.saveCandidates(runId, requestId, json, isTestData)
    override suspend fun ready(runId: String, requestId: String, branch: ReflectionBranch, text: String, now: Long) = dao.finalizeReady(runId, requestId, branch, text, now)
    override suspend fun fail(runId: String, requestId: String) = dao.markFailed(runId, requestId)
    override suspend fun retry(runId: String, requestId: String) = dao.retry(runId, requestId)
    override suspend fun musicObservations(runId: String) = musicDao?.observationsFor(runId).orEmpty()
    override suspend fun weatherSnapshot(runId: String) = weatherDao?.find(runId)?.readySnapshot()
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
    private val effortSheetHolds = mutableSetOf<String>()
    // A very fast or unavailable voice engine can finish before Saved creates its row.
    private val finishedReceipts = mutableMapOf<String, Long>()
    // Completed Room rows intentionally discard the four unused candidates. Keep the server's
    // pairing acknowledgement only for this process's one automatic arrival playback attempt.
    private val completedSongCues = mutableMapOf<String, Pair<String, String>>()
    private var activeRunId: String? = null
    private val mutableState = MutableStateFlow<RunReflectionState>(RunReflectionState.Idle)
    val state: StateFlow<RunReflectionState> = mutableState.asStateFlow()

    /** Recover local replies without silently starting another potentially paid request. */
    suspend fun initialize() = initialization.withLock {
        if (!initialized) {
            store.all().forEach { record ->
                mutex.withLock {
                    // A process restart consumes any interrupted receipt. Recover selection
                    // locally without another audible receipt or provider request.
                    val recovered = if (!record.selectionWindowStarted) {
                        store.beginSelectionWindow(record.runId, nowMillis() + quietWindowMillis)
                    } else record
                    if (recovered.status == ReflectionStatus.PENDING) {
                        if (recovered.candidatesJson == null) store.fail(recovered.runId, recovered.requestId)
                        else finalizeIfPossible(recovered.runId)
                    }
                }
            }
            initialized = true
        }
    }

    fun onSavedVisible(
        runId: String,
        bridgeReady: Boolean = true,
        awaitingReceipt: Boolean = false
    ): Job {
        activeRunId = runId
        return applicationScope.launch {
            try {
                initialize()
                mutex.withLock {
                    var record = store.find(runId)
                    if (record == null) {
                        val run = requireNotNull(runDao.findById(runId))
                        // Read opted-in metadata only for this first durable evidence snapshot.
                        // Later observations, retry and History cannot rewrite what was shared.
                        val music = if (run.state == StoredRunState.COMPLETED &&
                            run.musicObservationRequested && run.musicReflectionSharingAllowed) {
                            try { store.musicObservations(runId) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { emptyList() }
                        } else emptyList()
                        val weather = try { store.weatherSnapshot(runId)?.takeIf {
                            it.targetEpochMillis == run.officialStartEpochMillis
                        } }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { null }
                        val request = reflectionRequestFromSavedRun(
                            requestIdSupplier().toString(), run, runDao.transitionsFor(runId), music, weather)
                        val receiptFinishedAt = finishedReceipts[runId]
                        record = store.ensure(RunReflectionEntity(
                            runId = runId, requestId = request.requestId,
                            evidenceJson = gson.toJson(request.evidence),
                            selectionDeadlineEpochMillis = (receiptFinishedAt ?: nowMillis()) + quietWindowMillis,
                            selectionWindowStarted = !awaitingReceipt || receiptFinishedAt != null))
                    }
                    finalizeIfPossible(runId)
                    publish(requireNotNull(store.find(runId)))
                    if (record.status == ReflectionStatus.PENDING) {
                        if (record.selectionWindowStarted) {
                            scheduleSelection(runId, record.selectionDeadlineEpochMillis)
                        }
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

    /** Callers that show feedback-save failures can await the real durable write. */
    suspend fun saveEnergy(runId: String, branch: ReflectionBranch) {
        initialize()
        mutex.withLock {
            store.energy(runId, branch, nowMillis(), selectionHeld = runId in effortSheetHolds)
            finalizeIfPossible(runId)
            publish(requireNotNull(store.find(runId)))
        }
    }

    fun selectEnergy(runId: String, branch: ReflectionBranch) = applicationScope.launch {
        try {
            saveEnergy(runId, branch)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    /** Start the quiet window once after receipt completion, failure or interruption. */
    fun finishReceipt(runId: String): Job {
        val finishedAt = nowMillis()
        return applicationScope.launch {
            try {
                initialize()
                mutex.withLock {
                    // Keep the actual receipt completion time even if Room is delayed or
                    // a transient write fails. A later local retry uses this same deadline.
                    val originalFinishedAt = finishedReceipts.getOrPut(runId) { finishedAt }
                    if (store.find(runId) == null) return@withLock
                    val started = store.beginSelectionWindow(runId, originalFinishedAt + quietWindowMillis)
                    finalizeIfPossible(runId)
                    store.find(runId)?.let(::publish)
                    if (started.status == ReflectionStatus.PENDING) {
                        scheduleSelection(runId, started.selectionDeadlineEpochMillis)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publishFailure(runId) }
        }
    }

    /** Keep the quiet no-Energy path pending only while the optional Effort sheet is open. */
    fun setEffortSheetOpen(runId: String, open: Boolean) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                if (open) {
                    // The sheet can open before onSavedVisible has created the pending row.
                    effortSheetHolds.add(runId)
                } else {
                    effortSheetHolds.remove(runId)
                    finalizeIfPossible(runId)
                    store.find(runId)?.let(::publish)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { publishFailure(runId) }
    }

    fun retry(runId: String) = applicationScope.launch {
        try {
            initialize()
            mutex.withLock {
                val record = store.find(runId) ?: return@withLock
                if (!record.selectionWindowStarted) {
                    finishedReceipts[runId]?.let { finishedAt ->
                        store.beginSelectionWindow(runId, finishedAt + quietWindowMillis)
                    }
                }
                // A failed receipt-window write may leave a PENDING cached family.
                // Repair that local transition before considering another provider call.
                finalizeIfPossible(runId)
                val repaired = store.find(runId) ?: return@withLock
                publish(repaired)
                if (repaired.status == ReflectionStatus.READY || repaired.candidatesJson != null) {
                    return@withLock
                }
                if (runId in requests) return@withLock
                if (repaired.status == ReflectionStatus.FAILED) {
                    store.retry(runId, requestIdSupplier().toString()) ?: return@withLock
                }
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
        if (record.selectionWindowStarted) {
            scheduleSelection(runId, record.selectionDeadlineEpochMillis)
        }
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
        val beforeSelection = store.find(runId) ?: return
        if (!beforeSelection.selectionWindowStarted) return
        val record = if (runId in effortSheetHolds && beforeSelection.selectedBranch == null) {
            beforeSelection
        } else {
            store.defaultSelection(runId, nowMillis())
        }
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
        if (store.ready(runId, record.requestId, branch, text, nowMillis())) {
            candidates.songCueId?.let { completedSongCues[runId] = record.requestId to it }
        }
    }

    private fun publish(record: RunReflectionEntity) {
        if (activeRunId != record.runId) return
        mutableState.value = when (record.status) {
            ReflectionStatus.PENDING -> RunReflectionState.Preparing(record.runId, record.postRunEnergy)
            ReflectionStatus.READY -> RunReflectionState.Ready(record.runId, requireNotNull(record.text),
                record.postRunEnergy, record.isTestData, pairedPassageFor(record,
                    completedSongCues[record.runId]?.takeIf { it.first == record.requestId }?.second))
            ReflectionStatus.FAILED -> RunReflectionState.Failed(record.runId, record.postRunEnergy)
        }
    }

    private fun publishFailure(runId: String) {
        if (activeRunId == runId) mutableState.value = RunReflectionState.Failed(runId)
    }
}

/** Only a server-acknowledged cue paired with this frozen evidence can launch recorded music. */
internal fun pairedPassageFor(record: RunReflectionEntity, acknowledgedCueId: String? = null): SongPassageRequest? {
    if (record.status != ReflectionStatus.READY || record.isTestData) return null
    return try {
        val gson = GsonBuilder().create()
        val evidence = gson.fromJson(record.evidenceJson, CompletedRunEvidence::class.java)
        val plan = evidence.songPassage ?: return null
        val music = evidence.musicContext
        val cue = music.songCue ?: return null
        if (acknowledgedCueId != cue.cueId || cue.cueId != plan.cueId ||
            music.state != "MUSIC" || music.provenance != "MEDIA_SESSION_PARTIAL" ||
            music.trackTitle != plan.trackTitle || music.artistName != plan.artistName ||
            cue.source != "RUNSTATE_CURATED") return null
        plan.passage()
    } catch (_: Exception) {
        // Old or damaged optional metadata never prevents reading the exact saved reflection.
        null
    }
}
