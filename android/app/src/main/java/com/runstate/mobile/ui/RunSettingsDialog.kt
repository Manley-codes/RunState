package com.runstate.mobile.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.settings.RunAppearance
import com.runstate.mobile.reflection.HostedReflectionAccess
import com.runstate.mobile.reflection.ReflectionTier
import kotlinx.coroutines.launch
import com.runstate.mobile.settings.RunMusicPlayerLayout

internal const val SETTINGS_DIALOG_TAG = "runSettingsDialog"
internal const val SETTINGS_LOCATION_TAG = "runSettingsLocation"
internal const val SETTINGS_MUSIC_OBSERVATION_TAG = "runSettingsMusicObservation"
internal const val SETTINGS_MUSIC_ACCESS_TAG = "runSettingsMusicAccess"
internal const val SETTINGS_MUSIC_SHARING_TAG = "runSettingsMusicSharing"
internal const val SETTINGS_REFLECTION_SETUP_TAG = "runSettingsReflectionSetup"
internal const val SETTINGS_DEMO_MODE_TAG = "runSettingsDemoMode"
internal const val SETTINGS_DONE_TAG = "runSettingsDone"
internal const val SETTINGS_RETRY_PREFERENCES_TAG = "runSettingsRetryPreferences"
internal const val SETTINGS_WEATHER_TAG = "runSettingsWeather"
internal const val SETTINGS_SONG_SNIPPETS_TAG = "runSettingsSongSnippets"
internal const val SETTINGS_SPOTIFY_CONNECT_TAG = "runSettingsSpotifyConnect"
internal const val SETTINGS_SPOTIFY_DISCONNECT_TAG = "runSettingsSpotifyDisconnect"
internal const val SETTINGS_DISTANCE_ANNOUNCEMENTS_TAG = "runSettingsDistanceAnnouncements"
internal const val SETTINGS_APPEARANCE_TAG = "runSettingsAppearance"
internal const val SETTINGS_RECORDING_SOURCES_TAG = "runSettingsRecordingSources"
internal const val SETTINGS_MUSIC_REFLECTION_DEMO_TAG = "runSettingsMusicReflectionDemo"
internal const val SETTINGS_PRIVACY_TAG = "runSettingsPrivacy"
internal const val SETTINGS_PRIVACY_DIALOG_TAG = "runSettingsPrivacyDialog"
internal const val SETTINGS_PRIVACY_BACK_TAG = "runSettingsPrivacyBack"
internal fun appearanceOptionTag(appearance: RunAppearance): String =
    "runSettingsAppearance${appearance.name}"
internal fun musicPlayerOptionTag(layout: RunMusicPlayerLayout): String =
    "runSettingsMusicPlayer${layout.name}"

private val SettingsInk = Color(0xFF293326)
private val SettingsCream = Color(0xFFF9FAF5)

/** The parent persists preferences and owns Android permission requests; this is presentation only. */
@Composable
internal fun RunSettingsDialog(
    gpsReady: Boolean,
    gpsMessage: String?,
    musicObservationEnabled: Boolean,
    musicAccessGranted: Boolean,
    musicReflectionSharingEnabled: Boolean,
    musicReflectionSharingAvailable: Boolean,
    reflectionConnectionLabel: String?,
    demoMode: Boolean,
    onDismiss: () -> Unit,
    onRequestLocationSetup: () -> Unit,
    onSetMusicObservation: (Boolean) -> Unit,
    onRequestMusicAccess: () -> Unit,
    onSetMusicReflectionSharing: (Boolean) -> Unit,
    onSetDemoMode: (Boolean) -> Unit,
    onOpenReflectionSetup: () -> Unit,
    settingsLoading: Boolean = false,
    settingsError: String? = null,
    actionsEnabled: Boolean = true,
    preferencesAvailable: Boolean = true,
    onRetryPreferences: () -> Unit = {},
    weatherEnabled: Boolean = false,
    onSetWeather: (Boolean) -> Unit = {},
    songSnippetsEnabled: Boolean = false,
    onSetSongSnippets: (Boolean) -> Unit = {},
    distanceAnnouncements: Boolean = true,
    onSetDistanceAnnouncements: (Boolean) -> Unit = {},
    spotifyConnectionLabel: String? = null,
    spotifyConnectEnabled: Boolean = false,
    spotifyDisconnectEnabled: Boolean = false,
    onConnectSpotify: () -> Unit = {},
    onDisconnectSpotify: () -> Unit = {},
    appearance: RunAppearance = RunAppearance.NIGHT,
    onSetAppearance: (RunAppearance) -> Unit = {},
    musicPlayerLayout: RunMusicPlayerLayout = RunMusicPlayerLayout.HEADPHONES,
    onSetMusicPlayerLayout: (RunMusicPlayerLayout) -> Unit = {},
    onOpenRecordingSources: (() -> Unit)? = null,
    onOpenMusicReflectionDemo: (() -> Unit)? = null,
    ownerToolsEnabled: Boolean = com.runstate.mobile.BuildConfig.OWNER_TOOLS_ENABLED,
    hostedAccess: HostedReflectionAccess? = null,
    cloudReflectionsEnabled: Boolean? = null,
    onSetCloudReflections: (Boolean) -> Unit = {}
) {
    val controlsEnabled = actionsEnabled && !settingsLoading
    val preferenceControlsEnabled = controlsEnabled && preferencesAvailable
    var privacyVisible by rememberSaveable { mutableStateOf(false) }
    // Keep the settings position while the read-only summary replaces this dialog.
    val settingsScrollState = rememberScrollState()
    if (privacyVisible) {
        RunPrivacyDialog(onBack = { privacyVisible = false })
        return
    }
    AlertDialog(
        onDismissRequest = { if (controlsEnabled) onDismiss() },
        modifier = Modifier.testTag(SETTINGS_DIALOG_TAG),
        containerColor = SettingsCream,
        title = { Text("Run settings", color = SettingsInk) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp)
                    .verticalScroll(settingsScrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SettingsDescription("Regular runs use GPS. Your optional choices are remembered for future runs.")
                if (settingsLoading) {
                    SettingsDescription(if (preferencesAvailable) "Saving your preferences…"
                        else "Loading preferences…")
                }
                if (settingsError != null) {
                    Text(
                        settingsError,
                        color = Color(0xFF8B392D),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                    )
                }
                if (!preferencesAvailable && !settingsLoading) {
                    SettingsDescription("Your saved choices could not be loaded. Retry before changing music or demo settings.")
                    TextButton(
                        onClick = onRetryPreferences,
                        enabled = actionsEnabled,
                        modifier = Modifier.heightIn(min = 48.dp)
                            .testTag(SETTINGS_RETRY_PREFERENCES_TAG)
                    ) { Text("Retry preferences", color = SettingsInk) }
                }

                SettingsSectionTitle("RUN APPEARANCE")
                Row(
                    Modifier.fillMaxWidth().selectableGroup().testTag(SETTINGS_APPEARANCE_TAG),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(RunAppearance.NIGHT, RunAppearance.DAY, RunAppearance.AUTO).forEach { choice ->
                        val selected = appearance == choice
                        val label = when (choice) {
                            RunAppearance.AUTO -> "Auto"
                            RunAppearance.DAY -> "Light"
                            RunAppearance.NIGHT -> "Dark"
                        }
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .selectable(selected = selected, enabled = preferenceControlsEnabled,
                                    role = Role.RadioButton, onClick = { onSetAppearance(choice) })
                                .testTag(appearanceOptionTag(choice))
                                .semantics { contentDescription = "$label run appearance" },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) SettingsInk else Color.Transparent,
                            border = BorderStroke(1.dp, SettingsInk.copy(alpha = 0.2f))
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = (if (selected) SettingsCream else SettingsInk)
                                        .copy(alpha = if (preferenceControlsEnabled) 1f else 0.5f))
                            }
                        }
                    }
                }
                SettingsDescription("Auto uses Light from 7 AM to 7 PM. Your run keeps its starting look.")
                SettingsDivider()
                SettingsSectionTitle("LOCATION")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (gpsReady) "GPS ready" else "Location setup needed",
                        color = SettingsInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    SettingsDescription("Start uses precise location to measure your run. " +
                        "If permission or phone location is off, we ask before starting.")
                    if (gpsMessage != null) SettingsDescription(gpsMessage)
                    if (!gpsReady) {
                        TextButton(
                            onClick = onRequestLocationSetup,
                            enabled = controlsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_LOCATION_TAG)
                        ) { Text("Set up location", color = SettingsInk) }
                    }
                }

                SettingsDivider()
                SettingsSectionTitle("RUN UPDATES")
                SettingsPreference(
                    title = "Every mile",
                    description = "Brief distance, average pace and active-time updates during GPS runs. " +
                        "Music returns after each update. Turn off for a quiet run.",
                    checked = distanceAnnouncements,
                    enabled = preferenceControlsEnabled,
                    onCheckedChange = onSetDistanceAnnouncements,
                    tag = SETTINGS_DISTANCE_ANNOUNCEMENTS_TAG
                )
                SettingsDivider()
                SettingsSectionTitle("MUSIC")
                Text("Player artwork", color = SettingsInk, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RunMusicPlayerLayout.entries.forEach { choice ->
                        val selected = musicPlayerLayout == choice
                        val label = if (choice == RunMusicPlayerLayout.HEADPHONES) "Headphones" else "Album cover"
                        Surface(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .selectable(selected, enabled = preferenceControlsEnabled,
                                    role = Role.RadioButton, onClick = { onSetMusicPlayerLayout(choice) })
                                .testTag(musicPlayerOptionTag(choice))
                                .semantics { contentDescription = "$label music player" },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) SettingsInk else Color.Transparent,
                            border = BorderStroke(1.dp, SettingsInk.copy(alpha = .2f))
                        ) {
                            androidx.compose.foundation.layout.Box(
                                Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    color = (if (selected) SettingsCream else SettingsInk)
                                        .copy(alpha = if (preferenceControlsEnabled) 1f else .5f))
                            }
                        }
                    }
                }
                SettingsPreference(
                    title = "Observe music",
                    description = "Save available song title, artist, album, recording details and observed playback time during your run. " +
                        "These observations stay on your phone unless you separately choose music in reflections. " +
                        "No audio is recorded.",
                    checked = musicObservationEnabled,
                    enabled = preferenceControlsEnabled,
                    onCheckedChange = onSetMusicObservation,
                    tag = SETTINGS_MUSIC_OBSERVATION_TAG
                )
                if (!musicAccessGranted) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SettingsDescription("Android grants broad notification access. " +
                            "RunState uses media sessions only during runs with observation enabled. " +
                            "Granting access does not turn observation on by itself.")
                        TextButton(
                            onClick = onRequestMusicAccess,
                            enabled = preferenceControlsEnabled && musicObservationEnabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_MUSIC_ACCESS_TAG)
                        ) { Text("Allow music access", color = SettingsInk) }
                    }
                } else {
                    SettingsDescription("Music access granted. Available song details will be captured " +
                        "only when Observe music is on.")
                }
                SettingsPreference(
                    title = "Music in reflection",
                    description = "Optional: compare up to 12 Spotify songs from this run for your reflection. " +
                        "A song needs one minute of observed playback to qualify. " +
                        "Titles and artists go to RunState’s reflection service, Musixmatch and Anthropic. " +
                        "Preparation starts while you run and may use AI credits even if you discard the run. " +
                        "No phone audio, notification text or listening outside this run is sent.",
                    checked = musicReflectionSharingEnabled,
                    enabled = preferenceControlsEnabled &&
                        (musicReflectionSharingEnabled || musicObservationEnabled),
                    onCheckedChange = onSetMusicReflectionSharing,
                    tag = SETTINGS_MUSIC_SHARING_TAG
                )
                when {
                    !musicObservationEnabled -> SettingsDescription("Turn on Observe music to include music in future reflections.")
                    !musicAccessGranted -> SettingsDescription("Music access is needed before any song can be observed or shared.")
                    !musicReflectionSharingAvailable -> SettingsDescription("A reflection connection is needed to use observed music in reflections.")
                }
                SettingsPreference(
                    title = "Find song snippets",
                    description = "For future runs, send the chosen song's title, artist, album and duration " +
                        "through RunState to LRCLIB to find a short vocal hook. Timing is community supplied " +
                        "and may be imperfect. If timing is unavailable, use a short opening of the same song. " +
                        "No lyrics are saved to your run. " +
                        "After your reflection, RunState may briefly switch Spotify to that recording; " +
                        "restoring the previous song is best effort and the queue may change.",
                    checked = songSnippetsEnabled,
                    enabled = preferenceControlsEnabled && (songSnippetsEnabled || musicReflectionSharingEnabled),
                    onCheckedChange = onSetSongSnippets,
                    tag = SETTINGS_SONG_SNIPPETS_TAG
                )
                if (spotifyConnectionLabel != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(spotifyConnectionLabel, color = SettingsInk, fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        SettingsDescription("Connect Spotify for the controls inside your run, and so RunState can load the selected song, " +
                            "play its short ending and stop it automatically. Spotify Premium is needed " +
                            "to choose a specific song. Connecting does not turn music sharing on.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = onConnectSpotify,
                                enabled = controlsEnabled && spotifyConnectEnabled,
                                modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_SPOTIFY_CONNECT_TAG)
                            ) { Text("Connect Spotify", color = SettingsInk) }
                            if (spotifyDisconnectEnabled) {
                                TextButton(onClick = onDisconnectSpotify, enabled = controlsEnabled,
                                    modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_SPOTIFY_DISCONNECT_TAG)
                                ) { Text("Disconnect", color = SettingsInk) }
                            }
                        }
                    }
                }

                SettingsDivider()
                SettingsSectionTitle("WEATHER")
                SettingsPreference(
                    title = "Weather near my run",
                    description = "Send one approximate start location (rounded to about 1 km) to Open-Meteo " +
                        "to save an hourly weather estimate for your run's start time. The provider also sees your IP address. " +
                        "Saved weather facts can be included in reflections; your route is not sent. " +
                        "Turning this on applies to future GPS runs.",
                    checked = weatherEnabled,
                    enabled = preferenceControlsEnabled,
                    onCheckedChange = onSetWeather,
                    tag = SETTINGS_WEATHER_TAG
                )
                SettingsDivider()
                SettingsSectionTitle("REFLECTION CONNECTION")
                if (reflectionConnectionLabel != null) SettingsDescription(reflectionConnectionLabel)
                SettingsDescription("The saved service address is a setup preference, not a connection test.")
                TextButton(
                    onClick = onOpenReflectionSetup,
                    enabled = controlsEnabled,
                    modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_REFLECTION_SETUP_TAG)
                ) { Text("Reflection setup", color = SettingsInk) }
                if (cloudReflectionsEnabled != null) {
                    CloudReflectionSettings(hostedAccess, cloudReflectionsEnabled, preferenceControlsEnabled,
                        onSetCloudReflections)
                }

                SettingsDivider()
                TextButton(
                    onClick = { privacyVisible = true },
                    modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_PRIVACY_TAG)
                ) { Text("Privacy & data", color = SettingsInk) }

                if (com.runstate.mobile.BuildConfig.OWNER_TOOLS_ENABLED && ownerToolsEnabled) {
                    SettingsDivider()
                    SettingsSectionTitle("DEMO & TESTING")
                    if (onOpenMusicReflectionDemo != null) {
                        TextButton(
                            onClick = onOpenMusicReflectionDemo,
                            enabled = controlsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_MUSIC_REFLECTION_DEMO_TAG)
                        ) { Text("Music Reflection Demo", color = SettingsInk) }
                    }
                    if (onOpenRecordingSources != null) {
                        TextButton(
                            onClick = onOpenRecordingSources,
                            enabled = controlsEnabled,
                            modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_RECORDING_SOURCES_TAG)
                        ) { Text("Recording sources", color = SettingsInk) }
                    }
                    SettingsPreference(
                        title = "Sample demo",
                        description = "Temporary simulated distance, even while standing still. " +
                            "No GPS is recorded. Saving or discarding returns Start to GPS. " +
                            "This choice is not remembered after closing the app.",
                        checked = demoMode,
                        enabled = preferenceControlsEnabled,
                        onCheckedChange = onSetDemoMode,
                        tag = SETTINGS_DEMO_MODE_TAG
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = controlsEnabled,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_DONE_TAG)
            ) { Text("Done", color = SettingsInk) }
        }
    )
}

/** Offline information only: reading it never requests access or changes a preference. */
@Composable
private fun RunPrivacyDialog(onBack: () -> Unit) {
    AlertDialog(
        onDismissRequest = onBack,
        modifier = Modifier.testTag(SETTINGS_PRIVACY_DIALOG_TAG),
        containerColor = SettingsCream,
        title = { Text("Privacy & data", color = SettingsInk) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                PrivacySection("On this phone",
                    "RunState saves your runs, GPS routes, Energy and Effort answers, notes, shoe details " +
                        "and reflections on this phone. GPS recording can continue with the screen off " +
                        "during an active run. There is currently no cloud history sync.")
                PrivacySection("Reflections & voice",
                    "When reflections are connected, run facts and permitted optional context go through " +
                        "RunState's service to Anthropic. Precise routes, notes and shoe details are not " +
                        "included in reflection requests. Cloud speech sends the spoken words to ElevenLabs. " +
                        "Your phone's fallback speech engine may also use the internet.")
                PrivacySection("Music choices",
                    "Observe music, music in reflections and song snippets are separate choices, off by " +
                        "default. Shared song details go through RunState to Musixmatch and Anthropic; " +
                        "snippet lookup also uses LRCLIB. Preparation can begin during your run.\n\n" +
                        "Spotify connection and Android notification access do not turn music sharing on. " +
                        "RunState does not record microphone audio or copy Spotify music. Music and weather " +
                        "settings apply to future runs; turning them off does not recall data already sent.")
                PrivacySection("Maps & weather",
                    "Optional weather sends an approximate start location to Open-Meteo. Opening the live " +
                        "map requests the viewed area from OpenFreeMap. Both services can see your IP address. " +
                        "The static illustration and History route drawing do not request live map tiles.")
                PrivacySection("Deleting a run",
                    "In Log History, open a saved run, choose its three-dot menu, then Delete run. " +
                        "This permanently removes that run and its related data from this phone. " +
                        "It does not delete information previously sent to RunState's service or other providers.")
                PrivacySection("Backups & recovery",
                    "Run history is not included in Android backup or phone-to-phone transfer. There is " +
                        "currently no export or restore option. Uninstalling RunState or clearing its " +
                        "storage can erase your history.")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 48.dp).testTag(SETTINGS_PRIVACY_BACK_TAG)
            ) { Text("Back to settings", color = SettingsInk) }
        }
    )
}

@Composable
private fun PrivacySection(title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = SettingsInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(description, color = SettingsInk.copy(alpha = 0.88f), fontSize = 14.sp,
            lineHeight = 21.sp)
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(title, color = SettingsInk.copy(alpha = 0.88f), fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
private fun SettingsDescription(text: String) {
    Text(text, color = SettingsInk.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 20.sp)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = SettingsInk.copy(alpha = 0.12f))
}

@Composable
private fun CloudReflectionSettings(access: HostedReflectionAccess?, enabled: Boolean,
    controlsEnabled: Boolean, onSetCloudReflections: (Boolean) -> Unit) {
    val info = access?.state?.collectAsState()?.value
    val scope = rememberCoroutineScope()
    var starting by remember(access) { mutableStateOf(false) }
    var failed by remember(access) { mutableStateOf(false) }
    LaunchedEffect(access) { access?.refresh() }
    when {
        info?.tier == ReflectionTier.TRACKING -> SettingsDescription("Tracking only")
        info?.tier == ReflectionTier.TRIAL && info.status == "available" -> {
            SettingsDescription("Try two personalized reflections over seven days. No automatic charge.")
            TextButton(onClick = {
                starting = true
                scope.launch {
                    try {
                        failed = access?.startTrial() != true
                        if (!failed && !enabled) onSetCloudReflections(true)
                    } finally { starting = false }
                }
            }, enabled = controlsEnabled && !starting) { Text("Start free preview", color = SettingsInk) }
            if (failed) SettingsDescription("Couldn’t start the preview. Try again.")
        }
        info?.status == "expired" -> SettingsDescription("Your reflection preview has ended.")
        else -> {
            SettingsPreference("Cloud reflections", "Turn off for a tracking-only run.", enabled,
                controlsEnabled, onSetCloudReflections, "runSettingsCloudReflections")
            info?.remaining?.let { count ->
                val period = if (info.tier == ReflectionTier.TRIAL) "in your preview" else "this month"
                SettingsDescription("$count ${if (count == 1) "reflection" else "reflections"} left $period.")
            }
        }
    }
}

@Composable
private fun SettingsPreference(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, modifier = Modifier.weight(1f), color = SettingsInk,
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                modifier = Modifier.testTag(tag).semantics { contentDescription = title },
                colors = SwitchDefaults.colors(checkedThumbColor = SettingsCream,
                    checkedTrackColor = SettingsInk)
            )
        }
        SettingsDescription(description)
    }
}
