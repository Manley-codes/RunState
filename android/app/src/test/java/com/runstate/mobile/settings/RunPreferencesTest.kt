package com.runstate.mobile.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunPreferencesTest {
    @Test
    fun appearanceDefaultsToAutoForExistingPreferencesWithoutWriting() {
        val values = FakeValues(mutableMapOf("observe_music" to true))
        assertEquals(RunAppearance.AUTO, StoredRunPreferences(values).load().appearance)
        values.savedStrings["run_appearance"] = "unknown-old-mode"
        assertEquals(RunAppearance.AUTO, StoredRunPreferences(values).load().appearance)
        assertTrue(StoredRunPreferences(values).load().observeMusic)
        assertEquals(0, values.commits)
    }

    @Test
    fun appearanceSurvivesReopeningAndPreservesExistingConsentChoicesInOneCommit() {
        val values = FakeValues()
        val before = RunPreferences(observeMusic = true, shareMusicInReflection = true,
            musicSetupComplete = true, captureWeather = true, findSongSnippets = true,
            distanceAnnouncements = false)
        StoredRunPreferences(values).save(before)
        val booleansBefore = values.saved.toMap()
        RunAppearance.entries.forEachIndexed { index, appearance ->
            StoredRunPreferences(values).save(before.copy(appearance = appearance))
            assertEquals(before.copy(appearance = appearance), StoredRunPreferences(values).load())
            assertEquals(booleansBefore, values.saved)
            assertEquals(index + 2, values.commits)
        }
    }

    @Test
    fun failedAppearanceSaveDoesNotPartiallyChangePreviouslySavedChoices() {
        val values = FakeValues()
        val store = StoredRunPreferences(values)
        val before = RunPreferences(observeMusic = true, appearance = RunAppearance.NIGHT)
        store.save(before)
        values.acceptCommit = false
        assertThrows(IllegalStateException::class.java) {
            store.save(before.copy(observeMusic = false, appearance = RunAppearance.DAY))
        }
        assertEquals(before, StoredRunPreferences(values).load())
        assertEquals(2, values.commits)
    }

    @Test
    fun appearanceAutomaticHoursAndExplicitOverridesArePredictable() {
        for (hour in 0..23) {
            assertEquals(hour in 7..18, RunAppearance.AUTO.isDay(hour))
            assertTrue(RunAppearance.DAY.isDay(hour))
            assertFalse(RunAppearance.NIGHT.isDay(hour))
        }
        assertThrows(IllegalArgumentException::class.java) { RunAppearance.AUTO.isDay(-1) }
        assertThrows(IllegalArgumentException::class.java) { RunAppearance.AUTO.isDay(24) }
    }

    @Test
    fun mileAnnouncementsCanBeTurnedOffWithoutChangingMusicConsent() {
        val values = FakeValues()
        assertTrue(StoredRunPreferences(values).load().distanceAnnouncements)
        StoredRunPreferences(values).save(RunPreferences(distanceAnnouncements = false))
        val reopened = StoredRunPreferences(values).load()
        assertFalse(reopened.distanceAnnouncements)
        assertFalse(reopened.observeMusic)
        assertFalse(reopened.shareMusicInReflection)
    }
    @Test
    fun songSnippetLookupNeedsItsOwnChoiceAndSharing() {
        val values = FakeValues(mutableMapOf("observe_music" to true, "share_music_in_reflection" to true))
        assertFalse(StoredRunPreferences(values).load().findSongSnippets)
        val enabled = RunPreferences(observeMusic = true, shareMusicInReflection = true, findSongSnippets = true)
        StoredRunPreferences(values).save(enabled)
        assertEquals(enabled, StoredRunPreferences(values).load())
        values.saved["share_music_in_reflection"] = false
        assertFalse(StoredRunPreferences(values).load().findSongSnippets)
        assertThrows(IllegalArgumentException::class.java) { RunPreferences(findSongSnippets = true) }
    }

    @Test
    fun weatherRequiresItsOwnSavedChoiceAndDoesNotEnableMusicSharing() {
        val values = FakeValues(mutableMapOf("music_setup_complete" to true))
        assertFalse(StoredRunPreferences(values).load().captureWeather)
        StoredRunPreferences(values).save(RunPreferences(musicSetupComplete = true, captureWeather = true))
        val reopened = StoredRunPreferences(values).load()
        assertTrue(reopened.captureWeather)
        assertFalse(reopened.observeMusic)
        assertFalse(reopened.shareMusicInReflection)
    }

    @Test
    fun unsetPreferencesDoNotInferMusicConsentOrCompletedSetup() {
        val values = FakeValues()

        assertEquals(RunPreferences(), StoredRunPreferences(values).load())
        assertEquals(0, values.commits)
        assertTrue(values.saved.isEmpty())
    }

    @Test
    fun choicesSurviveANewStoreAndCanBeExplicitlyTurnedOff() {
        val values = FakeValues()
        val enabled = RunPreferences(
            observeMusic = true,
            shareMusicInReflection = true,
            musicSetupComplete = true,
            captureWeather = true
        )
        StoredRunPreferences(values).save(enabled)
        assertEquals(enabled, StoredRunPreferences(values).load())

        val disabled = RunPreferences(musicSetupComplete = true)
        StoredRunPreferences(values).save(disabled)

        assertEquals(disabled, StoredRunPreferences(values).load())
        assertEquals(2, values.commits)
        assertEquals(setOf("observe_music", "share_music_in_reflection", "music_setup_complete", "capture_weather", "find_song_snippets", "distance_announcements"),
            values.saved.keys)
    }

    @Test
    fun observationCanBeRememberedWithoutCloudSharing() {
        val values = FakeValues()
        val localOnly = RunPreferences(observeMusic = true, musicSetupComplete = true)

        StoredRunPreferences(values).save(localOnly)

        assertEquals(localOnly, StoredRunPreferences(values).load())
        assertFalse(values.saved.getValue("share_music_in_reflection"))
    }

    @Test
    fun missingIndividualChoicesStayOffAndDoNotMarkSetupComplete() {
        val values = FakeValues(mutableMapOf("observe_music" to true))

        assertEquals(RunPreferences(observeMusic = true), StoredRunPreferences(values).load())
        assertEquals(0, values.commits)
    }

    @Test
    fun storedSharingWithoutObservationIsNormalizedOffWithoutWriting() {
        val values = FakeValues(mutableMapOf(
            "observe_music" to false,
            "share_music_in_reflection" to true,
            "music_setup_complete" to true
        ))

        assertEquals(RunPreferences(musicSetupComplete = true),
            StoredRunPreferences(values).load())
        assertEquals(0, values.commits)
    }

    @Test
    fun newChoicesCannotEnableSharingWithoutObservation() {
        assertThrows(IllegalArgumentException::class.java) {
            RunPreferences(shareMusicInReflection = true)
        }
    }

    @Test
    fun rejectedCommitIsReportedAsFailureAndDoesNotRetry() {
        val values = FakeValues().apply { acceptCommit = false }

        val failure = assertThrows(IllegalStateException::class.java) {
            StoredRunPreferences(values).save(RunPreferences(observeMusic = true))
        }

        assertEquals("Run preferences could not be read or saved.", failure.message)
        assertEquals(1, values.commits)
    }

    @Test
    fun unreadablePreferencesFailExplicitlyInsteadOfClaimingDefaultsWereLoaded() {
        val values = FakeValues().apply { readFailure = ClassCastException("private storage detail") }

        val failure = assertThrows(IllegalStateException::class.java) {
            StoredRunPreferences(values).load()
        }

        assertEquals("Run preferences could not be read or saved.", failure.message)
        assertEquals(null, failure.cause)
        assertEquals(0, values.commits)
    }

    @Test
    fun storageExceptionsDoNotLeakTheirDetailsOrProduceSuccess() {
        val values = FakeValues().apply { writeFailure = SecurityException("private storage detail") }

        val failure = assertThrows(IllegalStateException::class.java) {
            StoredRunPreferences(values).save(RunPreferences(musicSetupComplete = true))
        }

        assertEquals("Run preferences could not be read or saved.", failure.message)
        assertEquals(null, failure.cause)
        assertEquals(1, values.commits)
    }

    private class FakeValues(
        val saved: MutableMap<String, Boolean> = mutableMapOf(),
        val savedStrings: MutableMap<String, String> = mutableMapOf()
    ) : RunPreferenceValues {
        var acceptCommit = true
        var readFailure: Exception? = null
        var writeFailure: Exception? = null
        var commits = 0

        override fun readBoolean(key: String): Boolean? {
            readFailure?.let { throw it }
            return saved[key]
        }

        override fun readString(key: String): String? {
            readFailure?.let { throw it }
            return savedStrings[key]
        }

        override fun commit(values: Map<String, Boolean>, strings: Map<String, String>): Boolean {
            commits += 1
            writeFailure?.let { throw it }
            if (acceptCommit) {
                saved.putAll(values)
                savedStrings.putAll(strings)
            }
            return acceptCommit
        }
    }
}
