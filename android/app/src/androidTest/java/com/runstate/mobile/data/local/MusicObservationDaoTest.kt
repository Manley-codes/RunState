package com.runstate.mobile.data.local

import android.content.Context
import android.media.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.util.UUID
import com.runstate.mobile.music.spotifyObservationRecording
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicObservationDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: RunStateDatabase
    private val run = RunEntity(
        runId = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30",
        state = StoredRunState.RUNNING,
        officialStartEpochMillis = 1_756_000_000_000L,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = 1_756_000_000_000L,
        transitionHistoryComplete = true,
        musicObservationRequested = true
    )

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = buildRunStateDatabase(context, DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun observation(key: String, offsetMillis: Long) = RunMusicObservation(
        runId = run.runId,
        observationKey = key,
        observedAtEpochMillis = run.officialStartEpochMillis + offsetMillis,
        elapsedRealtimeNanos = offsetMillis * 1_000_000,
        sourcePackage = "com.example.player",
        title = "Observed title",
        artist = "Observed artist",
        playbackState = MusicPlaybackState.PLAYING,
        eventKind = MusicObservationKind.SESSION_SNAPSHOT
    )

    @Test
    fun optedInObservationIsOrderedRetrySafeAndSurvivesReopen() = runBlocking {
        database.runDao().insert(run)
        val first = database.musicObservationDao().recordObservation(observation("event-1", 1_000))
        val second = database.musicObservationDao().recordObservation(observation("event-2", 2_000))
        assertEquals(1, first.sequenceNumber)
        assertEquals(2, second.sequenceNumber)

        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 3_000)
        // A retry of an already-committed event remains safe after the run pauses.
        assertEquals(first, database.musicObservationDao().recordObservation(observation("event-1", 9_000)))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)

        assertEquals(listOf(first, second), database.musicObservationDao().observationsFor(run.runId))
    }

    @Test
    fun spotifyRecordingDetailsSurviveReopenAndDuplicateCallbacksKeepTheFirstSnapshot() = runBlocking {
        database.runDao().insert(run.copy(musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true))
        val source = observation("spotify-recording", 1_000).copy(
            sourcePackage = "com.spotify.music",
            spotifyTrackUri = "spotify:track:0bRXwKfigvpKZUurwqAlEh",
            albumName = "Menagerie",
            trackDurationMillis = 254_000L
        )
        val saved = database.musicObservationDao().recordObservation(source)
        assertEquals(saved, database.musicObservationDao().recordObservation(source.copy(
            spotifyTrackUri = "spotify:track:4fzsfWzRhPawzqhX8Qt9F3",
            albumName = "Different record", trackDurationMillis = 300_000L)))
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        assertEquals(saved, database.musicObservationDao().observationsFor(run.runId).single())
        assertTrue(requireNotNull(database.runDao().findById(run.runId)).songSnippetLookupAllowed)
    }

    @Test
    fun snippetLookupDefaultsOffAndCannotBypassMusicSharing() {
        assertEquals(false, run.songSnippetLookupAllowed)
        assertThrows(IllegalArgumentException::class.java) { run.copy(songSnippetLookupAllowed = true) }
        assertTrue(run.copy(musicReflectionSharingAllowed = true, songSnippetLookupAllowed = true)
            .songSnippetLookupAllowed)
    }

    @Test
    fun recordingFieldsRejectOtherAppsInvalidIdentitiesAndUnboundedMetadata() {
        val base = observation("recording-validation", 1_000).copy(sourcePackage = "com.spotify.music")
        listOf("spotify:episode:0bRXwKfigvpKZUurwqAlEh", "spotify:track:short",
            " spotify:track:0bRXwKfigvpKZUurwqAlEh", "https://open.spotify.com/track/0bRXwKfigvpKZUurwqAlEh")
            .forEach { uri -> assertThrows(IllegalArgumentException::class.java) { base.copy(spotifyTrackUri = uri) } }
        listOf("", " album", "album\nname", "a".repeat(201)).forEach { album ->
            assertThrows(IllegalArgumentException::class.java) { base.copy(albumName = album) }
        }
        listOf(0L, 999L, 86_400_001L).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { base.copy(trackDurationMillis = duration) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourcePackage = "com.example.player", albumName = "Observed album")
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourcePackage = "com.example.player", trackDurationMillis = 180_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(sourcePackage = "com.example.player", spotifyTrackUri = "spotify:track:0bRXwKfigvpKZUurwqAlEh")
        }
    }

    @Test
    fun spotifyMetadataCaptureUsesCanonicalMediaIdentityAndDropsUnavailableFields() {
        val primary = "spotify:track:0bRXwKfigvpKZUurwqAlEh"
        val alternate = "spotify:track:4fzsfWzRhPawzqhX8Qt9F3"
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, primary)
            .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, alternate)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "  Menagerie  ")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 254_000L).build()
        val captured = requireNotNull(spotifyObservationRecording("com.spotify.music", metadata))
        assertEquals(primary, captured.trackUri)
        assertEquals("Menagerie", captured.albumName)
        assertEquals(254_000L, captured.durationMillis)
        assertNull(spotifyObservationRecording("com.example.player", metadata))
        assertNull(spotifyObservationRecording("com.spotify.music", null))

        val partial = MediaMetadata.Builder(metadata)
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "untrusted-id")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "bad\nmetadata")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.MAX_VALUE).build()
        val fallback = requireNotNull(spotifyObservationRecording("com.spotify.music", partial))
        assertEquals(alternate, fallback.trackUri)
        assertNull(fallback.albumName)
        assertNull(fallback.durationMillis)
        val invalid = requireNotNull(spotifyObservationRecording("com.spotify.music",
            MediaMetadata.Builder(partial).putString(MediaMetadata.METADATA_KEY_MEDIA_URI,
                "https://open.spotify.com/track/0bRXwKfigvpKZUurwqAlEh").build()))
        assertNull(invalid.trackUri)
    }

    @Test
    fun noOptInOrPausedParentCannotAcquireNewEvidence() = runBlocking {
        database.runDao().insert(run.copy(musicObservationRequested = false))
        assertThrows(IllegalStateException::class.java) {
            runBlocking { database.musicObservationDao().recordObservation(observation("off", 1_000)) }
        }
        assertTrue(database.musicObservationDao().observationsFor(run.runId).isEmpty())

        // A distinct opted-in run may start; after its durable pause, callbacks are rejected.
        val optedIn = run.copy(runId = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7")
        database.runDao().insert(optedIn)
        database.runDao().pauseRun(optedIn.runId, optedIn.officialStartEpochMillis + 2_000)
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                database.musicObservationDao().recordObservation(
                    observation("paused", 3_000).copy(runId = optedIn.runId)
                )
            }
        }
        assertTrue(database.musicObservationDao().observationsFor(optedIn.runId).isEmpty())
    }

    @Test
    fun preparationClaimIsDurableAndCannotPayAgainAfterReopenOrSettleTwice() = runBlocking {
        val sharing = run.copy(musicReflectionSharingAllowed = true)
        database.runDao().insert(sharing)
        val dao = database.musicObservationDao()
        val observed = dao.recordObservation(observation("song", 1_000).copy(sourcePackage = "com.spotify.music"))
        val preparationId = UUID.randomUUID().toString()
        val claimed = requireNotNull(dao.claimPreparation(run.runId, preparationId,
            observed.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 30_000))
        assertEquals(SongPreparationClaimStatus.CLAIMED, claimed.status)
        assertEquals("Observed title", claimed.trackTitle)
        assertEquals(observed.sequenceNumber, claimed.observationSequenceNumber)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        val reopened = database.musicObservationDao()
        assertEquals(claimed, reopened.preparationFor(run.runId))
        assertNull(reopened.claimPreparation(run.runId, UUID.randomUUID().toString(),
            observed.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 60_000))
        reopened.finishPreparation(run.runId, UUID.randomUUID().toString(), true)
        assertEquals(SongPreparationClaimStatus.CLAIMED, reopened.preparationFor(run.runId)?.status)
        reopened.finishPreparation(run.runId, preparationId, true)
        reopened.finishPreparation(run.runId, preparationId, false)
        assertEquals(claimed.copy(status = SongPreparationClaimStatus.READY), reopened.preparationFor(run.runId))
    }

    @Test
    fun preparationCountsOnlyActiveTimeAndRequiresCurrentlyRunningWithBothChoices() = runBlocking {
        val dao = database.musicObservationDao()
        val sharing = run.copy(musicReflectionSharingAllowed = true)
        database.runDao().insert(sharing)
        val observed = dao.recordObservation(observation("song", 1_000).copy(sourcePackage = "com.spotify.music"))
        suspend fun claim(offset: Long) = dao.claimPreparation(run.runId, UUID.randomUUID().toString(),
            observed.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + offset)
        assertNull(claim(29_999))
        database.runDao().pauseRun(run.runId, run.officialStartEpochMillis + 20_000)
        assertNull(claim(99_000))
        database.runDao().resumeRun(run.runId, run.officialStartEpochMillis + 100_000)
        assertNull(claim(109_999))
        assertEquals(SongPreparationClaimStatus.CLAIMED, claim(110_000)?.status)

        for (unsupported in listOf(
            sharing.copy(runId = UUID.randomUUID().toString(), musicReflectionSharingAllowed = false),
            sharing.copy(runId = UUID.randomUUID().toString(), musicObservationRequested = false,
                musicReflectionSharingAllowed = false),
            sharing.copy(runId = UUID.randomUUID().toString(), transitionHistoryComplete = null),
            sharing.copy(runId = UUID.randomUUID().toString(), state = StoredRunState.COMPLETED,
                finishEpochMillis = run.officialStartEpochMillis + 60_000)
        )) {
            database.runDao().insert(unsupported)
            assertNull(dao.claimPreparation(unsupported.runId, UUID.randomUUID().toString(),
                1, FINGERPRINT, run.officialStartEpochMillis + 60_000))
            assertNull(dao.preparationFor(unsupported.runId))
        }
    }

    @Test
    fun preparationUsesFirstEligibleDurableSpotifyObservationAndConcurrentClaimsHaveOneWinner() = runBlocking {
        database.runDao().insert(run.copy(musicReflectionSharingAllowed = true))
        val dao = database.musicObservationDao()
        dao.recordObservation(observation("other-player", 1_000))
        dao.recordObservation(observation("missing-artist", 2_000).copy(sourcePackage = "com.spotify.music", artist = " "))
        dao.recordObservation(observation("too-long", 3_000).copy(sourcePackage = "com.spotify.music", title = "x".repeat(201)))
        dao.recordObservation(observation("paused-song", 4_000).copy(sourcePackage = "com.spotify.music",
            playbackState = MusicPlaybackState.PAUSED))
        val first = dao.recordObservation(observation("first-eligible", 5_000).copy(sourcePackage = "com.spotify.music",
            title = "  First title  ", artist = "  First artist  "))
        val later = dao.recordObservation(observation("later", 6_000).copy(sourcePackage = "com.spotify.music"))
        assertNull(dao.claimPreparation(run.runId, UUID.randomUUID().toString(), later.sequenceNumber,
            FINGERPRINT, run.officialStartEpochMillis + 30_000))
        val outcomes = (1..2).map {
            async(Dispatchers.IO) { dao.claimPreparation(run.runId, UUID.randomUUID().toString(),
                first.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 30_000) }
        }.awaitAll()
        val claim = outcomes.single { it != null }
        assertEquals(1, outcomes.count { it != null })
        assertEquals("First title", claim?.trackTitle)
        assertEquals("First artist", claim?.artistName)
    }

    @Test
    fun multipleClaimsDeduplicateIdentityKeepVersionsAndBindTheFirstDestination() = runBlocking {
        database.runDao().insert(run.copy(musicReflectionSharingAllowed = true))
        val dao = database.musicObservationDao()
        val first = dao.recordObservation(observation("first", 1_000).copy(sourcePackage = "com.spotify.music"))
        val firstClaim = requireNotNull(dao.claimPreparation(run.runId, UUID.randomUUID().toString(),
            first.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 30_000))
        dao.finishPreparation(run.runId, firstClaim.preparationId, false)
        val duplicate = dao.recordObservation(observation("duplicate", 2_000).copy(sourcePackage = "com.spotify.music",
            title = "  OBSERVED   title ", artist = " observed ARTIST "))
        assertNull(dao.claimPreparation(run.runId, UUID.randomUUID().toString(), duplicate.sequenceNumber,
            FINGERPRINT, run.officialStartEpochMillis + 31_000))
        val live = dao.recordObservation(observation("live-version", 3_000).copy(sourcePackage = "com.spotify.music",
            title = "Observed title (Live)"))
        assertNull(dao.claimPreparation(run.runId, UUID.randomUUID().toString(), live.sequenceNumber,
            "b".repeat(64), run.officialStartEpochMillis + 32_000))
        val liveClaim = requireNotNull(dao.claimPreparation(run.runId, UUID.randomUUID().toString(),
            live.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 32_000))
        dao.finishPreparation(run.runId, liveClaim.preparationId, true)
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        assertEquals(listOf(firstClaim.copy(status = SongPreparationClaimStatus.UNAVAILABLE),
            liveClaim.copy(status = SongPreparationClaimStatus.READY)), database.musicObservationDao().preparationsFor(run.runId))
        assertEquals(firstClaim.preparationId, database.musicObservationDao().preparationFor(run.runId)?.preparationId)
    }

    @Test
    fun twelveClaimsAreADurableBudgetAcrossReopen() = runBlocking {
        database.runDao().insert(run.copy(musicReflectionSharingAllowed = true))
        val dao = database.musicObservationDao()
        val observations = (1..13).map { index -> dao.recordObservation(observation("song-$index", index * 1_000L)
            .copy(sourcePackage = "com.spotify.music", title = "Title $index")) }
        observations.take(12).forEach { observed ->
            assertEquals(observed.sequenceNumber, dao.claimPreparation(run.runId, UUID.randomUUID().toString(),
                observed.sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 30_000)?.observationSequenceNumber)
        }
        database.close()
        database = buildRunStateDatabase(context, DATABASE_NAME)
        assertNull(database.musicObservationDao().claimPreparation(run.runId, UUID.randomUUID().toString(),
            observations.last().sequenceNumber, FINGERPRINT, run.officialStartEpochMillis + 40_000))
        assertEquals(12, database.musicObservationDao().preparationsFor(run.runId).size)
    }

    private companion object {
        const val DATABASE_NAME = "runstate-music-observation-test.db"
        val FINGERPRINT = "a".repeat(64)
    }
}
