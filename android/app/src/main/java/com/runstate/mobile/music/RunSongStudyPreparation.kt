package com.runstate.mobile.music

import com.runstate.mobile.data.local.*
import com.runstate.mobile.reflection.SongStudyPreparationGateway
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.TransitionHistoryCoverage
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Each study request receives one identity only; run evidence stays inside this store. */
internal interface SongPreparationStore {
    suspend fun activeRuns(): List<RunEntity>
    suspend fun transitions(runId: String): List<RunTransitionEntity>
    suspend fun observations(runId: String): List<RunMusicObservationEntity>
    suspend fun preparation(runId: String): SongPreparationClaimEntity?
    suspend fun preparations(runId: String): List<SongPreparationClaimEntity> = listOfNotNull(preparation(runId))
    suspend fun claim(runId: String, preparationId: String, observationSequenceNumber: Int,
        destinationFingerprint: String, nowEpochMillis: Long): SongPreparationClaimEntity?
    suspend fun finish(runId: String, preparationId: String, ready: Boolean)
}

internal class RoomSongPreparationStore(private val runs: RunDao, private val music: MusicObservationDao) :
    SongPreparationStore {
    override suspend fun activeRuns() = runs.findActiveRuns()
    override suspend fun transitions(runId: String) = runs.transitionsFor(runId)
    override suspend fun observations(runId: String) = music.observationsFor(runId)
    override suspend fun preparation(runId: String) = music.preparationFor(runId)
    override suspend fun preparations(runId: String) = music.preparationsFor(runId)
    override suspend fun claim(runId: String, preparationId: String, observationSequenceNumber: Int,
        destinationFingerprint: String, nowEpochMillis: Long) = music.claimPreparation(
            runId, preparationId, observationSequenceNumber, destinationFingerprint, nowEpochMillis)
    override suspend fun finish(runId: String, preparationId: String, ready: Boolean) =
        music.finishPreparation(runId, preparationId, ready)
}

/** Snapshot identity prevents a connection change from sending this run to another destination. */
internal class SongPreparationDestination(val fingerprint: String, val gateway: SongStudyPreparationGateway) {
    override fun toString() = "SongPreparationDestination[connection=redacted]"
}

/**
 * Starts only after durable playback receipts establish an eligible song. The local timer
 * rechecks stored evidence without inferring listening from elapsed run time or polling Spotify.
 * Distinct eligible songs are prepared sequentially. A persisted identity is never retried
 * automatically, including after process death, cancellation or provider failure. Old servers
 * retain one-song behavior.
 */
internal class RunSongStudyPreparation(
    private val scope: CoroutineScope,
    private val store: SongPreparationStore,
    private val allowed: () -> Boolean,
    private val destination: () -> SongPreparationDestination?,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val waitMillis: suspend (Long) -> Unit = { delay(it) },
    private val preparationId: () -> UUID = UUID::randomUUID
) {
    private class Owner(val runId: String, val destination: SongPreparationDestination)
    private val admission = Mutex()
    private var owner: Owner? = null
    private var work: Job? = null
    // A capability miss/transport failure before claim must not cause repeated background probes.
    private val unsupportedRuns = mutableSetOf<String>()

    fun onObservationSaved(runId: String) = scope.launch {
        try {
            admission.withLock {
                if (owner?.runId == runId && work?.isActive == true) return@withLock
                if (!allowed() || runId in unsupportedRuns) return@withLock
                val selected = destination() ?: return@withLock
                val run = store.activeRuns().singleOrNull()?.takeIf { eligible(it, runId) } ?: return@withLock
                val previous = store.preparations(runId)
                if (previous.size >= MAX_SONG_PREPARATIONS_PER_RUN ||
                    previous.any { it.destinationFingerprint != selected.fingerprint }) return@withLock
                cancel()
                val next = Owner(run.runId, selected)
                owner = next
                work = scope.launch { prepare(next) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional admission/storage failure must never escape the app scope or affect GPS.
            unsupportedRuns.add(runId)
        }
    }

    /** Pause, revocation, navigation to another run and connection changes invalidate this owner. */
    fun cancel() {
        owner = null
        work?.cancel()
        work = null
    }

    private fun eligible(run: RunEntity, runId: String) = run.runId == runId &&
        run.state == StoredRunState.RUNNING && run.musicObservationRequested &&
        run.musicReflectionSharingAllowed && run.transitionHistoryComplete == true

    private suspend fun stillEligible(current: Owner): RunEntity? {
        if (owner !== current || !allowed() || destination()?.fingerprint != current.destination.fingerprint) return null
        return store.activeRuns().singleOrNull()?.takeIf { eligible(it, current.runId) }
    }

    private suspend fun prepare(current: Owner) {
        var capabilitiesRead = false
        var multiple = false
        try {
            while (true) {
                val run = stillEligible(current) ?: return
                val previous = store.preparations(run.runId)
                if (previous.size >= MAX_SONG_PREPARATIONS_PER_RUN ||
                    previous.any { it.destinationFingerprint != current.destination.fingerprint }) return
                val now = nowMillis()
                val active = RunTimeline.activeMillis(run.officialStartEpochMillis, null,
                    StoredRunState.RUNNING, store.transitions(run.runId), now,
                    TransitionHistoryCoverage.COMPLETE) ?: return
                val claimedIdentities = previous.mapNotNull {
                    songPreparationIdentity(it.trackTitle, it.artistName)
                }.toSet()
                val observation = if (active >= MINIMUM_ACTIVE_MILLIS) observedSongPlaybackEligible(
                    run.runId, run.officialStartEpochMillis, now, store.observations(run.runId))
                    .asSequence().filter { row ->
                        row.runId == run.runId && row.sourcePackage == "com.spotify.music" &&
                            row.playbackState == MusicPlaybackState.PLAYING &&
                            row.observedAtEpochMillis in run.officialStartEpochMillis..now &&
                            songPreparationIdentity(row.title, row.artist)?.let { it !in claimedIdentities } == true
                    }.minByOrNull { it.sequenceNumber } else null
                if (observation == null) {
                    waitMillis(1_000L)
                    continue
                }
                if (!capabilitiesRead) {
                    if (stillEligible(current) == null) return
                    if (withTimeoutOrNull(5_000L) { current.destination.gateway.supports() } != true) {
                        unsupportedRuns.add(run.runId)
                        return
                    }
                    if (stillEligible(current) == null) return
                    multiple = withTimeoutOrNull(5_000L) { current.destination.gateway.supportsMultiple() } == true
                    capabilitiesRead = true
                    if (stillEligible(current) == null) return
                }
                if (previous.isNotEmpty() && !multiple) {
                    unsupportedRuns.add(run.runId)
                    return
                }
                if (stillEligible(current) == null) return
                val claimed = store.claim(run.runId, preparationId().toString(), observation.sequenceNumber,
                    current.destination.fingerprint, nowMillis()) ?: return
                var ready = false
                try {
                    if (stillEligible(current) == null) return
                    ready = withTimeoutOrNull(65_000L) {
                        current.destination.gateway.prepare(claimed.preparationId, claimed.trackTitle, claimed.artistName)
                    } == true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // An unavailable song does not prevent trying the next distinct observation.
                } finally {
                    withContext(NonCancellable) {
                        try { store.finish(claimed.runId, claimed.preparationId, ready) } catch (_: Exception) { }
                    }
                }
                if (!multiple) {
                    unsupportedRuns.add(run.runId)
                    return
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            unsupportedRuns.add(current.runId)
            // No song title, destination, access code or provider diagnostics enter logs.
        }
    }

    companion object {
        const val MINIMUM_ACTIVE_MILLIS = MINIMUM_SONG_PLAYBACK_MILLIS
    }
}
