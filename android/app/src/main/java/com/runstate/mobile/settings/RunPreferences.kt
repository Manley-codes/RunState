package com.runstate.mobile.settings

import android.content.Context
import android.content.SharedPreferences

/** Choices made by the runner, separate from Android permission grants and individual runs. */
internal data class RunPreferences(
    val observeMusic: Boolean = false,
    val shareMusicInReflection: Boolean = false,
    val musicSetupComplete: Boolean = false,
    val captureWeather: Boolean = false,
    val findSongSnippets: Boolean = false,
    val distanceAnnouncements: Boolean = true
) {
    init {
        require(!shareMusicInReflection || observeMusic) {
            "Music sharing requires music observation."
        }
        require(!findSongSnippets || shareMusicInReflection) {
            "Song timing lookup requires music reflection sharing."
        }
    }
}

/** Call on an I/O dispatcher. A failed read or commit must not be presented as saved consent. */
internal interface RunPreferencesStore {
    fun load(): RunPreferences
    fun save(preferences: RunPreferences)
}

/** Small storage boundary keeps defaults, validation and failure behavior testable without Android. */
internal interface RunPreferenceValues {
    fun readBoolean(key: String): Boolean?
    fun commit(values: Map<String, Boolean>): Boolean
}

internal class StoredRunPreferences(
    private val values: RunPreferenceValues
) : RunPreferencesStore {
    override fun load(): RunPreferences = storageOperation {
        val observeMusic = values.readBoolean(OBSERVE_MUSIC) ?: false
        RunPreferences(
            observeMusic = observeMusic,
            // An inconsistent old/corrupted combination never grants sharing by itself.
            shareMusicInReflection = observeMusic &&
                (values.readBoolean(SHARE_MUSIC_IN_REFLECTION) ?: false),
            musicSetupComplete = values.readBoolean(MUSIC_SETUP_COMPLETE) ?: false,
            captureWeather = values.readBoolean(CAPTURE_WEATHER) ?: false,
            findSongSnippets = observeMusic && (values.readBoolean(SHARE_MUSIC_IN_REFLECTION) ?: false) &&
                (values.readBoolean(FIND_SONG_SNIPPETS) ?: false),
            distanceAnnouncements = values.readBoolean(DISTANCE_ANNOUNCEMENTS) ?: true
        )
    }

    override fun save(preferences: RunPreferences) = storageOperation {
        check(values.commit(mapOf(
            OBSERVE_MUSIC to preferences.observeMusic,
            SHARE_MUSIC_IN_REFLECTION to preferences.shareMusicInReflection,
            MUSIC_SETUP_COMPLETE to preferences.musicSetupComplete,
            CAPTURE_WEATHER to preferences.captureWeather,
            FIND_SONG_SNIPPETS to preferences.findSongSnippets,
            DISTANCE_ANNOUNCEMENTS to preferences.distanceAnnouncements
        )))
    }

    private inline fun <T> storageOperation(operation: () -> T): T = try {
        operation()
    } catch (failure: Exception) {
        throw IllegalStateException("Run preferences could not be read or saved.")
    }

    private companion object {
        const val OBSERVE_MUSIC = "observe_music"
        const val SHARE_MUSIC_IN_REFLECTION = "share_music_in_reflection"
        const val MUSIC_SETUP_COMPLETE = "music_setup_complete"
        const val CAPTURE_WEATHER = "capture_weather"
        const val FIND_SONG_SNIPPETS = "find_song_snippets"
        const val DISTANCE_ANNOUNCEMENTS = "distance_announcements"
    }
}

/** Contains nonsecret booleans only. Sample/demo mode intentionally has no persistent preference. */
internal class AndroidRunPreferencesStore(
    context: Context,
    /** Override only in isolated tests; production always uses the default file. */
    preferencesName: String = "run_preferences"
) : RunPreferencesStore by
    StoredRunPreferences(AndroidRunPreferenceValues(
        context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    ))

private class AndroidRunPreferenceValues(
    private val preferences: SharedPreferences
) : RunPreferenceValues {
    override fun readBoolean(key: String): Boolean? =
        if (preferences.contains(key)) preferences.getBoolean(key, false) else null

    override fun commit(values: Map<String, Boolean>): Boolean {
        val editor = preferences.edit()
        values.forEach { (key, value) -> editor.putBoolean(key, value) }
        return editor.commit()
    }
}
