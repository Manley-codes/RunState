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
        val claims = mutableListOf<SongPreparationClaimEntity>()
        val claim: SongPreparationClaimEntity? get() = claims.firstOrNull()
        var preparationReadFails = false
        var onClaim: () -> Unit = {}
        override suspend fun activeRuns() = runs
        override suspend fun transitions(runId: String) = timeline
        override suspend fun observations(runId: String) = music
        override suspend fun preparation(runId: String): SongPreparationClaimEntity? {
            if (preparationReadFails) error("Fictional unavailable local store.")
            return claim
        }
        override suspend fun preparations(runId: String): List<SongPreparationClaimEntity> {
            if (preparationReadFails) error("Fictional unavailable local store.")
            return claims.toList()
        }
        override suspend fun claim(runId: String, preparationId: String, observationSequenceNumber: Int,
            destinationFingerprint: String, nowEpochMillis: Long): SongPreparationClaimEntity? {
            if (runs.singleOrNull()?.state != StoredRunState.RUNNING) return null
            val observation = music.single { it.sequenceNumber == observationSequenceNumber }
            if (claims.any { songPreparationIdentity(it.trackTitle, it.artistName) ==
                songPreparationIdentity(observation.title, observation.artist) }) return null
            val row = SongPreparationClaimEntity(runId, preparationId, requireNotNull(observation.title).trim(),
                requireNotNull(observation.artist).trim(), observationSequenceNumber, destinationFingerprint,
                nowEpochMillis)
            claims += row
            onClaim()
            return row
        }
        override suspend fun finish(runId: String, preparationId: String, ready: Boolean) {
            val index = claims.indexOfFirst { it.preparationId == preparationId }
            claims[index] = claims[index].copy(status = if (ready) SongPreparationClaimStatus.READY
                else SongPreparationClaimStatus.UNAVAILABLE)
        }
    }

    private class Gateway : SongStudyPreparationGateway {
        var supported = true
        var multiple = false
        var multipleCapabilityReply: CompletableDeferred<Boolean>? = null
        var capabilityReads = 0
        var capabilityReply: CompletableDeferred<Boolean>? = null
        var preparationReply: CompletableDeferred<Boolean>? = null
        var ready = true
        val prepared = mutableListOf<Triple<String, String, String>>()
        override suspend fun supports(): Boolean {
            capabilityReads++
            return capabilityReply?.await() ?: supported
        }
        override suspend fun supportsMultiple(): Boolean = multipleCapabilityReply?.await() ?: multiple
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
            UUID::randomUUID)
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

    @Test fun distinctSongsPrepareSeriallyWhileDuplicateCallbacksAndVersionSuffixesStayHonest() {
        val pending = CompletableDeferred<Boolean>()
        val fixture = Harness(gateway = Gateway().apply { multiple = true; preparationReply = pending })
        try {
            val first = fixture.store.music.single()
            fixture.store.music += listOf(
                first.copy(sequenceNumber = 2, title = "  FIRST   title ", artist = " first ARTIST "),
                first.copy(sequenceNumber = 3, title = "First title (Live)"),
                first.copy(sequenceNumber = 4, title = "Second title", artist = "Second artist")
            )
            fixture.observe()
            assertEquals(1, fixture.gateway.prepared.size)
            assertEquals(1, fixture.store.claims.size)
            fixture.gateway.preparationReply = null
            pending.complete(true)
            assertEquals(listOf("First title", "First title (Live)", "Second title"),
                fixture.gateway.prepared.map { it.second })
            assertEquals(3, fixture.store.claims.map { it.preparationId }.toSet().size)
            assertTrue(fixture.store.claims.all { it.status == SongPreparationClaimStatus.READY })
        } finally { fixture.close() }
    }

    @Test fun laterObservationIsPreparedAndTwelveDistinctAttemptsAreTheRunBudget() {
        val fixture = Harness(gateway = Gateway().apply { multiple = true; ready = false })
        try {
            fixture.observe()
            val first = fixture.store.music.single()
            fixture.store.music += (2..15).map { index -> first.copy(sequenceNumber = index, title = "Title $index") }
            fixture.observe()
            fixture.advance(1_000L)
            assertEquals(12, fixture.gateway.prepared.size)
            assertEquals(12, fixture.store.claims.size)
            fixture.observe()
            assertEquals(12, fixture.gateway.prepared.size)
            assertTrue(fixture.store.claims.all { it.status == SongPreparationClaimStatus.UNAVAILABLE })
            val restarted = Harness(fixture.store, Gateway().apply { multiple = true })
            try {
                restarted.observe()
                assertEquals(0, restarted.gateway.capabilityReads)
                assertTrue(restarted.gateway.prepared.isEmpty())
            } finally { restarted.close() }
        } finally { fixture.close() }
    }

    @Test fun failedAndInterruptedIdentitiesAreNeverRetriedButResumeCanPrepareANewSong() {
        val pending = CompletableDeferred<Boolean>()
        val fixture = Harness(gateway = Gateway().apply { multiple = true; preparationReply = pending })
        try {
            fixture.observe()
            fixture.controller.cancel()
            assertEquals(SongPreparationClaimStatus.UNAVAILABLE, fixture.store.claim?.status)
            val second = fixture.store.music.single().copy(sequenceNumber = 2, title = "Second title")
            fixture.store.music += second
            fixture.gateway.preparationReply = null
            fixture.gateway.ready = false
            fixture.observe()
            assertEquals(listOf("First title", "Second title"), fixture.gateway.prepared.map { it.second })
            fixture.controller.cancel()
            val restarted = Harness(fixture.store, Gateway().apply { multiple = true })
            try {
                restarted.observe()
                assertTrue(restarted.gateway.prepared.isEmpty())
                restarted.store.music += second.copy(sequenceNumber = 3, title = "Third title")
                restarted.advance(1_000L)
                assertEquals("Third title", restarted.gateway.prepared.single().second)
            } finally { restarted.close() }
        } finally { fixture.close() }
    }

    @Test fun unsupportedMultiCapabilityKeepsLegacyOneSongBehaviorAcrossProcessRecreation() {
        val fixture = Harness()
        try {
            fixture.observe()
            fixture.store.music += fixture.store.music.single().copy(sequenceNumber = 2, title = "Second title")
            val restarted = Harness(fixture.store)
            try {
                restarted.observe()
                assertTrue(restarted.gateway.prepared.isEmpty())
                assertEquals(1, fixture.store.claims.size)
            } finally { restarted.close() }
        } finally { fixture.close() }
    }

    @Test fun revocationDuringMultiCapabilityCheckPreventsAnyIdentityDispatch() {
        val pending = CompletableDeferred<Boolean>()
        val fixture = Harness(gateway = Gateway().apply { multipleCapabilityReply = pending })
        try {
            fixture.observe()
            fixture.allowed = false
            pending.complete(true)
            assertTrue(fixture.store.claims.isEmpty())
            assertTrue(fixture.gateway.prepared.isEmpty())
        } finally { fixture.close() }
    }

    @Test fun excludedObservationsCannotUsePreparationBudget() {
        val fixture = Harness(gateway = Gateway().apply { multiple = true })
        try {
            val first = fixture.store.music.single()
            fixture.store.music = listOf(
                first.copy(runId = UUID.randomUUID().toString()),
                first.copy(sequenceNumber = 2, sourcePackage = "com.other.player"),
                first.copy(sequenceNumber = 3, playbackState = MusicPlaybackState.PAUSED),
                first.copy(sequenceNumber = 4, title = " "),
                first.copy(sequenceNumber = 5, observedAtEpochMillis = 999L),
                first.copy(sequenceNumber = 6, observedAtEpochMillis = 999_999L),
                first.copy(sequenceNumber = 7, title = "x".repeat(201)),
                first.copy(sequenceNumber = 8, artist = "Bad\nArtist")
            )
            fixture.observe()
            assertEquals(0, fixture.gateway.capabilityReads)
            assertTrue(fixture.store.claims.isEmpty())
        } finally { fixture.close() }
    }
}
