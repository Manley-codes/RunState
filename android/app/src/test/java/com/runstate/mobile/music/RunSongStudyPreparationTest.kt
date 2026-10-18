package com.runstate.mobile.music

import com.runstate.mobile.data.local.*
import com.runstate.mobile.reflection.SongStudyPreparationGateway
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RunSongStudyPreparationTest {
    private class Store : SongPreparationStore {
        val runId = "175d6171-19cb-4968-bd32-2abb075d95a4"
        var runs = listOf(RunEntity(runId, StoredRunState.RUNNING, 1_000L, "America/Chicago", 1_000L,
            transitionHistoryComplete = true, musicObservationRequested = true,
            musicReflectionSharingAllowed = true))
        var timeline = emptyList<RunTransitionEntity>()
        var music = listOf(RunMusicObservationEntity(runId, 1, "first-observation", 1_100L, null,
            "com.spotify.music", "First title", "First artist", MusicPlaybackState.PLAYING,
            MusicObservationKind.SESSION_SNAPSHOT))
        var claim: SongPreparationClaimEntity? = null
        var preparationReadFails = false
        var onClaim: () -> Unit = {}
        override suspend fun activeRuns() = runs
        override suspend fun transitions(runId: String) = timeline
        override suspend fun observations(runId: String) = music
        override suspend fun preparation(runId: String): SongPreparationClaimEntity? {
            if (preparationReadFails) error("Fictional unavailable local store.")
            return claim
        }
        override suspend fun claim(runId: String, preparationId: String, observationSequenceNumber: Int,
            destinationFingerprint: String, nowEpochMillis: Long): SongPreparationClaimEntity? {
            if (claim != null || runs.singleOrNull()?.state != StoredRunState.RUNNING) return null
            val observation = music.single { it.sequenceNumber == observationSequenceNumber }
            val row = SongPreparationClaimEntity(runId, preparationId, requireNotNull(observation.title).trim(),
                requireNotNull(observation.artist).trim(), observationSequenceNumber, destinationFingerprint,
                nowEpochMillis)
            claim = row
            onClaim()
            return row
        }
        override suspend fun finish(runId: String, preparationId: String, ready: Boolean) {
            claim = requireNotNull(claim).copy(status = if (ready) SongPreparationClaimStatus.READY
                else SongPreparationClaimStatus.UNAVAILABLE)
        }
    }

    private class Gateway : SongStudyPreparationGateway {
        var supported = true
        var capabilityReads = 0
        var capabilityReply: CompletableDeferred<Boolean>? = null
        var preparationReply: CompletableDeferred<Boolean>? = null
        var ready = true
        val prepared = mutableListOf<Triple<String, String, String>>()
        override suspend fun supports(): Boolean {
            capabilityReads++
            return capabilityReply?.await() ?: supported
        }
        override suspend fun prepare(preparationId: String, trackTitle: String, artistName: String): Boolean {
            prepared += Triple(preparationId, trackTitle, artistName)
            return preparationReply?.await() ?: ready
        }
    }

    private class Harness(val store: Store = Store(), val gateway: Gateway = Gateway(), activeMillis: Long = 30_000L) {
        val uncaught = mutableListOf<Throwable>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined +
            CoroutineExceptionHandler { _, failure -> uncaught += failure })
        var allowed = true
        var fingerprint = "a".repeat(64)
        var now = 1_000L + activeMillis
        val waits = ArrayDeque<CompletableDeferred<Unit>>()
        val controller = RunSongStudyPreparation(scope, store, { allowed },
            { SongPreparationDestination(fingerprint, gateway) }, { now },
            {
                val next = CompletableDeferred<Unit>()
                waits.addLast(next)
                next.await()
            },
            { UUID.fromString("a8ddf13c-316b-44c4-bfa0-740012266a82") })
        fun observe() = runBlocking { controller.onObservationSaved(store.runId).join() }
        fun advance(millis: Long) {
            now += millis
            waits.removeFirst().complete(Unit)
        }
        fun close() = scope.cancel()
    }

    @Test fun oneInitialCallbackStillPreparesAfterThirtyActiveSecondsAndPinsOnlyTheFirstSong() {
        val fixture = Harness(activeMillis = 0L)
        try {
            fixture.observe()
            assertEquals(0, fixture.gateway.capabilityReads)
            fixture.advance(29_999L)
            assertEquals(0, fixture.gateway.capabilityReads)
            fixture.advance(1L)
            val claim = requireNotNull(fixture.store.claim)
            assertEquals(SongPreparationClaimStatus.READY, claim.status)
            assertEquals(Triple(claim.preparationId, "First title", "First artist"), fixture.gateway.prepared.single())
            fixture.store.music = fixture.store.music + fixture.store.music.first().copy(
                sequenceNumber = 2, title = "Second title", artist = "Second artist")
            fixture.observe()
            assertEquals(1, fixture.gateway.prepared.size)
            assertEquals("First title", fixture.store.claim?.trackTitle)
        } finally { fixture.close() }
    }

    @Test fun unsupportedServerDoesNotClaimOrSendASongAndDoesNotRepeatBackgroundProbes() {
        val fixture = Harness(gateway = Gateway().apply { supported = false })
        try {
            fixture.observe()
            fixture.observe()
            assertNull(fixture.store.claim)
            assertTrue(fixture.gateway.prepared.isEmpty())
            assertEquals(1, fixture.gateway.capabilityReads)
        } finally { fixture.close() }
    }

    @Test fun frozenAndCurrentChoicesAndSingleRunningOwnershipAreRequiredBeforeAnyNetworkWork() {
        val fixture = Harness()
        try {
            fixture.allowed = false
            fixture.observe()
            fixture.allowed = true
            fixture.store.runs = fixture.store.runs.map { it.copy(musicReflectionSharingAllowed = false) }
            fixture.observe()
            fixture.store.runs = fixture.store.runs.map { it.copy(musicReflectionSharingAllowed = true) }
                .let { it + it.single().copy(runId = "5041fd6f-7fd0-44ed-b293-9da2d36af445") }
            fixture.observe()
            assertEquals(0, fixture.gateway.capabilityReads)
            assertTrue(fixture.gateway.prepared.isEmpty())
            assertNull(fixture.store.claim)
        } finally { fixture.close() }
    }

    @Test fun failedClaimedAttemptNeverRepeatsAfterAnotherCallbackOrProcessRecreation() {
        val original = Harness(gateway = Gateway().apply { ready = false })
        try {
            original.observe()
            original.observe()
            assertEquals(SongPreparationClaimStatus.UNAVAILABLE, original.store.claim?.status)
            assertEquals(1, original.gateway.prepared.size)
            val restarted = Harness(original.store)
            try {
                restarted.observe()
                assertEquals(0, restarted.gateway.capabilityReads)
                assertTrue(restarted.gateway.prepared.isEmpty())
            } finally { restarted.close() }
        } finally { original.close() }
    }

    @Test fun pausedTimeDoesNotCountTowardTheThirtySecondThreshold() {
        val fixture = Harness(activeMillis = 10_000L)
        try {
            fixture.observe()
            fixture.controller.cancel()
            fixture.store.timeline = listOf(RunTransitionEntity(fixture.store.runId, 1,
                RunTransitionType.PAUSE, 11_000L), RunTransitionEntity(fixture.store.runId, 2,
                RunTransitionType.RESUME, 71_000L))
            fixture.store.runs = fixture.store.runs.map { it.copy(lastCheckpointEpochMillis = 71_000L) }
            fixture.now = 71_000L
            fixture.waits.clear()
            fixture.observe()
            fixture.advance(19_999L)
            assertEquals(0, fixture.gateway.capabilityReads)
            fixture.advance(1L)
            assertEquals(1, fixture.gateway.prepared.size)
        } finally { fixture.close() }
    }

    @Test fun revocationDuringCapabilityCheckCannotCreateAClaimOrSendTitleAndArtist() {
        val response = CompletableDeferred<Boolean>()
        val fixture = Harness(gateway = Gateway().apply { capabilityReply = response })
        try {
            fixture.observe()
            fixture.allowed = false
            response.complete(true)
            assertNull(fixture.store.claim)
            assertTrue(fixture.gateway.prepared.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun destinationReplacementCancelsTheClaimedAttemptWithoutSendingAnotherSong() {
        val response = CompletableDeferred<Boolean>()
        val fixture = Harness(gateway = Gateway().apply { preparationReply = response })
        try {
            fixture.observe()
            val originalClaim = requireNotNull(fixture.store.claim)
            fixture.fingerprint = "b".repeat(64)
            fixture.controller.cancel()
            fixture.store.music = fixture.store.music.map { it.copy(title = "Another title") }
            fixture.observe()
            response.complete(true)
            assertEquals(1, fixture.gateway.prepared.size)
            assertEquals(originalClaim.preparationId, fixture.store.claim?.preparationId)
            assertEquals("First title", fixture.store.claim?.trackTitle)
            assertEquals("a".repeat(64), fixture.store.claim?.destinationFingerprint)
            assertEquals(SongPreparationClaimStatus.UNAVAILABLE, fixture.store.claim?.status)
        } finally { fixture.close() }
    }

    @Test fun consentRevokedImmediatelyAfterDurableClaimPreventsTheProviderDispatch() {
        val fixture = Harness()
        try {
            fixture.store.onClaim = { fixture.allowed = false }
            fixture.observe()
            assertNotNull(fixture.store.claim)
            assertEquals(SongPreparationClaimStatus.UNAVAILABLE, fixture.store.claim?.status)
            assertTrue(fixture.gateway.prepared.isEmpty())
            fixture.allowed = true
            fixture.observe()
            assertTrue(fixture.gateway.prepared.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun optionalAdmissionStoreFailureCannotCrashTheApplicationScopeOrStartNetworkWork() {
        val fixture = Harness()
        try {
            fixture.store.preparationReadFails = true
            fixture.observe()
            assertTrue(fixture.uncaught.isEmpty())
            assertNull(fixture.store.claim)
            assertEquals(0, fixture.gateway.capabilityReads)
            assertTrue(fixture.gateway.prepared.isEmpty())
        } finally { fixture.close() }
    }
}
