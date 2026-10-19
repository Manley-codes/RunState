package com.runstate.mobile.reflection

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.SongPreparationClaimEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.TransitionHistoryCoverage
import com.runstate.mobile.weather.RunWeatherSnapshot
import com.runstate.mobile.music.SongReflectionPlan
import com.runstate.mobile.music.selectSongReflectionPlan
import com.runstate.mobile.music.reflectionSongCatalog
import com.runstate.mobile.music.canonicalSpotifyTrackUri
import java.util.Locale
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.charset.CodingErrorAction
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

internal const val LOCAL_REFLECTION_ENDPOINT =
    "http://10.0.2.2:8787/api/reflections/candidates"
internal const val PHONE_REFLECTION_ENDPOINT =
    "http://127.0.0.1:8787/api/reflections/candidates"

/** The transport request. Every value comes from one completed Room row or its history. */
internal data class ReflectionApiRequest(
    val requestId: String,
    val runId: String,
    val evidence: CompletedRunEvidence
)

internal data class CompletedRunEvidence(
    val officialStartEpochMillis: Long,
    val finishEpochMillis: Long,
    val startTimezoneId: String,
    val activeDurationMillis: Long?,
    val metrics: ReflectionMetricsEvidence,
    val preRunEnergy: String = "UNKNOWN",
    val musicContext: ReflectionMusicContext = ReflectionMusicContext(),
    val weatherContext: RunWeatherSnapshot? = null,
    val songPassage: SongReflectionPlan? = null,
    /** Local transport binding; removed from evidence before sending any completed facts. */
    val songPreparationReference: SongPreparationReference? = null,
    /** Frozen local shortlist; only a capable, same-destination server receives it. */
    val songCandidates: List<ReflectionSongCandidate>? = null,
    val selectedSongId: String? = null,
    val songSelectionMethod: String? = null,
    val songSnippetLookupAllowed: Boolean = false,
    /** Local validated playback coordinates; never sent as run facts or model context. */
    val songPlaybackReceipt: ReflectionSongSnippet? = null
)

internal data class ReflectionSongCandidate(
    val candidateId: String,
    val musicContext: ReflectionMusicContext,
    val songPassage: SongReflectionPlan? = null,
    val preparationReference: SongPreparationReference? = null,
    /** Playback URI stays on the phone; only opted-in album/duration can reach the timing lookup. */
    val recording: ReflectionSongRecording? = null
)

internal data class ReflectionSongRecording(val albumName: String?, val durationMillis: Long,
    val spotifyTrackUri: String)

/** Numeric, source-derived timing only. No lyric text or remotely supplied playback URI. */
internal data class ReflectionSongSnippet(
    val cueId: String, val source: String, val sourceId: String,
    val trackTitle: String, val artistName: String, val albumName: String?,
    val trackDurationMillis: Long, val startPositionMillis: Long, val durationMillis: Long,
    val selectionMethod: String, val version: Int
)

internal data class SongPreparationReference(val preparationId: String, val destinationFingerprint: String) {
    override fun toString() = "SongPreparationReference[reference=redacted]"
}

internal data class ReflectionMetricsEvidence(
    val distanceMeters: Double?,
    val source: String?,
    val coverage: String?,
    val displayUnit: String?
)

internal data class ReflectionMusicContext(
    val state: String = "UNKNOWN",
    val trackTitle: String? = null,
    val artistName: String? = null,
    val artistDescription: String? = null,
    val provenance: String? = null,
    val songCue: ReflectionSongCue? = null
)

/** Independent editorial interpretation, separate from the partial listening report. */
internal data class ReflectionSongCue(
    val cueId: String,
    val source: String,
    val theme: String,
    val connectionNote: String
)

/** All four candidates are validated before the no-selection branch may be displayed. */
internal data class ReflectionCandidates(
    val spent: String,
    val feelingGood: String,
    val poweredUp: String,
    val noSelection: String,
    val isTestData: Boolean = true,
    val songCueId: String? = null,
    val selectedSongId: String? = null,
    val songSelectionMethod: String? = null,
    val songSnippet: ReflectionSongSnippet? = null
)

/** A narrow seam that keeps HTTP out of the process-owned coordinator's tests. */
internal fun interface ReflectionGateway {
    suspend fun request(request: ReflectionApiRequest): ReflectionCandidates
}

/**
 * Re-creates backend evidence from durable facts, never from formatted screen strings.
 *
 * Active duration is derived only when the row certifies that its stored transition
 * history is complete. Older rows keep it unknown instead of turning absent history into
 * a claim that the runner never paused.
 */
internal fun reflectionRequestFromSavedRun(
    requestId: String,
    run: RunEntity,
    transitions: List<RunTransitionEntity>,
    musicObservations: List<RunMusicObservationEntity> = emptyList(),
    weather: RunWeatherSnapshot? = null,
    songPreparation: SongPreparationClaimEntity? = null,
    songPreparations: List<SongPreparationClaimEntity> = listOfNotNull(songPreparation)
): ReflectionApiRequest {
    require(UUID.fromString(requestId).toString() == requestId) {
        "requestId must be canonical lowercase UUID text."
    }
    require(run.state == StoredRunState.COMPLETED) {
        "Reflection evidence requires a completed run."
    }
    val finishEpochMillis = requireNotNull(run.finishEpochMillis) {
        "Reflection evidence requires a durable finish time."
    }
    require(weather == null || weather.targetEpochMillis == run.officialStartEpochMillis) {
        "Weather must describe the completed run's start time."
    }

    // A GPS service gap can leave a RUNNING row and complete transition history behind.
    // Match History's conservative timing boundary instead of counting that gap as active.
    val activeTimingKnown = run.transitionHistoryComplete == true &&
        (run.metricSource != MetricSource.GPS || run.telemetryCoverage == TelemetryCoverage.COMPLETE)
    val activeDurationMillis = if (activeTimingKnown) {
        require(transitions.all { it.runId == run.runId }) {
            "Reflection history must belong to the completed run."
        }
        RunTimeline.activeMillis(
            officialStartEpochMillis = run.officialStartEpochMillis,
            finishEpochMillis = finishEpochMillis,
            currentState = run.state,
            transitions = transitions,
            nowEpochMillis = finishEpochMillis,
            historyCoverage = TransitionHistoryCoverage.COMPLETE
        )
    } else {
        null
    }

    val metrics = if (
        run.metricSource == null &&
        run.telemetryCoverage == null &&
        run.displayDistanceUnit == null &&
        run.finalDistanceMeters == null
    ) {
        ReflectionMetricsEvidence(null, null, null, null)
    } else {
        ReflectionMetricsEvidence(
            distanceMeters = run.finalDistanceMeters,
            source = requireNotNull(run.metricSource).name,
            coverage = requireNotNull(run.telemetryCoverage).name,
            displayUnit = requireNotNull(run.displayDistanceUnit).name
        )
    }

    // Preserve the original pinned identity for an older server. A capable server can
    // instead compare the separately frozen, explicitly shared multi-song shortlist.
    val sharedMusicAllowed = run.musicObservationRequested && run.musicReflectionSharingAllowed
    val pinnedObservation = songPreparation?.takeIf { it.runId == run.runId && sharedMusicAllowed }?.let { claim ->
        musicObservations.singleOrNull { observation ->
            observation.runId == run.runId && observation.sequenceNumber == claim.observationSequenceNumber &&
                observation.sourcePackage == "com.spotify.music" && observation.playbackState == MusicPlaybackState.PLAYING &&
                observation.observedAtEpochMillis in run.officialStartEpochMillis..finishEpochMillis &&
                observation.title?.trim() == claim.trackTitle && observation.artist?.trim() == claim.artistName
        }
    }
    val usableObservations = if (songPreparation == null) musicObservations else listOfNotNull(
        pinnedObservation?.copy(title = songPreparation.trackTitle, artist = songPreparation.artistName))
    val song = selectSongReflectionPlan(run, finishEpochMillis, usableObservations)
    val musicContext = if (song == null) {
        reflectionMusicContextFromSavedRun(run, finishEpochMillis, usableObservations)
    } else ReflectionMusicContext(
        state = "MUSIC", trackTitle = song.title, artistName = song.artist,
        provenance = "MEDIA_SESSION_PARTIAL", songCue = song.cue
    )
    return ReflectionApiRequest(
        requestId = requestId,
        runId = run.runId,
        evidence = CompletedRunEvidence(
            officialStartEpochMillis = run.officialStartEpochMillis,
            finishEpochMillis = finishEpochMillis,
            startTimezoneId = run.startTimezoneId,
            activeDurationMillis = activeDurationMillis,
            metrics = metrics,
            preRunEnergy = run.preRunEnergy?.name ?: "UNKNOWN",
            musicContext = musicContext,
            weatherContext = weather,
            songPassage = song?.plan(),
            songPreparationReference = songPreparation?.takeIf { pinnedObservation != null }?.let {
                SongPreparationReference(it.preparationId, it.destinationFingerprint)
            },
            songCandidates = reflectionSongCandidates(run, finishEpochMillis, musicObservations, songPreparations),
            songSnippetLookupAllowed = sharedMusicAllowed && run.songSnippetLookupAllowed
        )
    )
}

/** A report of PLAYING is evidence of presence, not listening duration or an effect on pace. */
internal fun reflectionSongCandidates(
    run: RunEntity,
    finishEpochMillis: Long,
    observations: List<RunMusicObservationEntity>,
    preparations: List<SongPreparationClaimEntity> = emptyList()
): List<ReflectionSongCandidate> {
    if (!run.musicObservationRequested || !run.musicReflectionSharingAllowed) return emptyList()
    fun key(value: String) = value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    fun valid(value: String?) = value != null && value.trim().isNotEmpty() && value.trim().length <= 200 &&
        value.codePoints().noneMatch { Character.isISOControl(it) || it in 0xd800..0xdfff }
    return observations.asSequence().filter {
        it.runId == run.runId && it.sourcePackage == "com.spotify.music" &&
            it.playbackState == MusicPlaybackState.PLAYING && it.sequenceNumber > 0 &&
            it.observedAtEpochMillis in run.officialStartEpochMillis..finishEpochMillis && valid(it.title) && valid(it.artist)
    }.sortedBy { it.sequenceNumber }.toList()
        .groupBy { key(requireNotNull(it.title)) to key(requireNotNull(it.artist)) }
        .values.take(12).map { rows ->
            val row = rows.first()
            val title = requireNotNull(row.title).trim()
            val artist = requireNotNull(row.artist).trim()
            val knownSong = reflectionSongCatalog.singleOrNull { key(it.title) == key(title) && key(it.artist) == key(artist) }
            val preparation = preparations.firstOrNull {
                it.runId == run.runId && key(it.trackTitle) == key(title) && key(it.artistName) == key(artist)
            }
            // A later callback may fill missing metadata. Conflicting recording IDs or
            // versions remain ambiguous; do not silently load an arbitrary recording.
            val recordings = rows.mapNotNull { observed ->
                val uri = canonicalSpotifyTrackUri(observed.spotifyTrackUri) ?: return@mapNotNull null
                val album = observed.albumName?.trim()?.takeIf(::valid)
                val duration = observed.trackDurationMillis?.takeIf { it in 1_000..3_600_000 } ?: return@mapNotNull null
                ReflectionSongRecording(album, duration, uri)
            }
            val knownUris = rows.mapNotNull { canonicalSpotifyTrackUri(it.spotifyTrackUri) }.distinct()
            val knownAlbums = rows.mapNotNull { it.albumName?.trim()?.takeIf(::valid)?.let(::key) }.distinct()
            val knownDurations = rows.mapNotNull { it.trackDurationMillis?.takeIf { duration -> duration in 1_000..3_600_000 } }
            val recording = recordings.lastOrNull()?.takeIf { last ->
                knownUris.size <= 1 && knownAlbums.size <= 1 &&
                    knownDurations.all { kotlin.math.abs(it - last.durationMillis) <= 1L } && recordings.all {
                it.spotifyTrackUri == last.spotifyTrackUri && (it.albumName == null || last.albumName == null ||
                    key(it.albumName) == key(last.albumName)) &&
                    kotlin.math.abs(it.durationMillis - last.durationMillis) <= 1L
            } }
            // Accepted timings are verified for specific recordings. With new lookup enabled,
            // other complete editions must earn their own source timing instead of inheriting it.
            val curated = knownSong?.takeIf { !run.songSnippetLookupAllowed || recording == null ||
                recording.spotifyTrackUri in it.verifiedSpotifyTrackUris }
            val plan = curated?.plan()?.let { plan -> if (recording == null) plan else plan.copy(
                spotifyTrackUri = recording.spotifyTrackUri, observedDurationMillis = recording.durationMillis,
                allowTrackSwitch = run.songSnippetLookupAllowed && recording.spotifyTrackUri in curated.verifiedSpotifyTrackUris) }
            ReflectionSongCandidate(row.sequenceNumber.toString(),
                ReflectionMusicContext("MUSIC", curated?.title ?: title, curated?.artist ?: artist,
                    provenance = "MEDIA_SESSION_PARTIAL", songCue = curated?.cue),
                plan, preparation?.let { SongPreparationReference(it.preparationId, it.destinationFingerprint) }, recording)
        }.toList()
}

/** Freeze the actual winner with the exact response; unused branch text is still discarded. */
internal fun CompletedRunEvidence.withSongSelection(result: ReflectionCandidates): CompletedRunEvidence {
    val id = result.selectedSongId ?: return copy(selectedSongId = null, songSelectionMethod = null,
        musicContext = if (musicContext.songCue?.source == "LRCLIB") musicContext.copy(songCue = null) else musicContext,
        songPassage = if (musicContext.songCue?.source == "LRCLIB" || songPlaybackReceipt != null) null else songPassage,
        songPlaybackReceipt = null)
    val selected = songCandidates.orEmpty().single { it.candidateId == id }
    require(result.songSelectionMethod in setOf("CONTEXT_MATCH", "AVAILABLE_FALLBACK", "OBSERVED_FALLBACK"))
    val snippet = result.songSnippet
    if (snippet != null) validateSongSnippet(snippet, selected, songSnippetLookupAllowed, allowFallbacks = true)
    require(result.songCueId == null || result.songCueId == (snippet?.cueId ?: selected.musicContext.songCue?.cueId))
    val context = if (snippet == null) selected.musicContext else if (result.songCueId == null)
        selected.musicContext.copy(songCue = null) else selected.musicContext.copy(songCue = ReflectionSongCue(
        snippet.cueId, "LRCLIB", if (snippet.selectionMethod == "TIMED_LINE_WINDOW")
            "A short vocal line window identified from published timings; not a verified hook."
        else "A short repeated vocal hook identified from published line timings.",
        "Community timing; a musical response to the completed run, not a claim of perfect phrase alignment."))
    val curated = selected.songPassage?.takeIf { snippet?.version == 2 && it.allowTrackSwitch }
    val plan = if (snippet == null) selected.songPassage else if (curated != null)
        curated.copy(playbackReceiptId = snippet.cueId) else SongReflectionPlan(snippet.cueId,
        snippet.trackTitle, snippet.artistName, snippet.startPositionMillis, snippet.durationMillis,
        if (result.songCueId == null) 0 else 1,
        selected.recording!!.spotifyTrackUri, selected.recording.durationMillis, true,
        snippet.source, snippet.sourceId, snippet.version, snippet.cueId.takeIf { snippet.version == 2 })
    return copy(musicContext = context, songPassage = plan,
        songPreparationReference = selected.preparationReference, selectedSongId = id,
        songSelectionMethod = result.songSelectionMethod, songPlaybackReceipt = snippet?.takeIf { it.version == 2 })
}

internal fun validateSongSnippet(snippet: ReflectionSongSnippet, candidate: ReflectionSongCandidate, allowed: Boolean,
                                allowFallbacks: Boolean = false) {
    val recording = requireNotNull(candidate.recording)
    require(allowed && (snippet.version == 1 || snippet.version == 2 && allowFallbacks))
    require(candidate.musicContext.songCue == null || snippet.version == 2)
    val opening = snippet.selectionMethod == "RECORDING_OPENING_FALLBACK"
    if (opening) require(snippet.version == 2 && snippet.source == "RUNSTATE_RECORDING" && snippet.sourceId == "opening")
    else require(snippet.source == "LRCLIB" && snippet.sourceId.matches(Regex("[1-9][0-9]{0,18}")))
    require(snippet.trackTitle == candidate.musicContext.trackTitle && snippet.artistName == candidate.musicContext.artistName &&
        snippet.albumName == recording.albumName && snippet.trackDurationMillis == recording.durationMillis)
    require(canonicalSpotifyTrackUri(recording.spotifyTrackUri) != null && recording.durationMillis in 1_000..3_600_000)
    require(snippet.startPositionMillis >= 0 && snippet.durationMillis in 2_000..10_000 &&
        snippet.startPositionMillis <= recording.durationMillis - snippet.durationMillis)
    require(snippet.version == 1 || snippet.startPositionMillis <= recording.durationMillis - snippet.durationMillis - 2_000)
    require(snippet.selectionMethod in if (snippet.version == 1) setOf("TITLE_HOOK", "REPEATED_HOOK")
        else setOf("TITLE_HOOK", "REPEATED_HOOK", "TIMED_LINE_WINDOW", "RECORDING_OPENING_FALLBACK"))
    if (opening) require(snippet.startPositionMillis == 0L && snippet.durationMillis <= 8_000)
    val prefix = if (opening) "recording_v2_" else "lrclib_v${snippet.version}_"
    require(snippet.cueId == "${prefix}${snippet.sourceId}_${snippet.startPositionMillis}_${snippet.startPositionMillis + snippet.durationMillis}")
}

/** Sharing is separate from local capture; one reported title never proves a whole song play. */
private fun reflectionMusicContextFromSavedRun(
    run: RunEntity,
    finishEpochMillis: Long,
    observations: List<RunMusicObservationEntity>
): ReflectionMusicContext {
    if (!run.musicObservationRequested || !run.musicReflectionSharingAllowed) return ReflectionMusicContext()
    val latest = observations.asSequence().filter { observation ->
        observation.runId == run.runId &&
            observation.sourcePackage == "com.spotify.music" &&
            observation.playbackState == MusicPlaybackState.PLAYING &&
            !observation.title.isNullOrBlank() && !observation.artist.isNullOrBlank() &&
            observation.observedAtEpochMillis in run.officialStartEpochMillis..finishEpochMillis
    }.maxByOrNull { it.sequenceNumber } ?: return ReflectionMusicContext()

    return ReflectionMusicContext(
        state = "MUSIC",
        trackTitle = latest.title,
        artistName = latest.artist,
        provenance = "MEDIA_SESSION_PARTIAL"
    )
}

/** A small debug bridge to the loopback-bound backend through emulator routing or adb reverse. */
internal class LocalReflectionHttpClient(
    endpoint: String = LOCAL_REFLECTION_ENDPOINT,
    private val gson: Gson = GsonBuilder().serializeNulls().create(),
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : ReflectionGateway {

    private val endpointUrl = URL(endpoint).also { url ->
        require(url.protocol == "http") { "The local reflection endpoint must use HTTP." }
        require(url.host in setOf("10.0.2.2", "127.0.0.1") && url.port == 8787) {
            "The Android debug bridge may only use the local development hosts on port 8787."
        }
        require(
            url.path == "/api/reflections/candidates" &&
                url.query == null && url.ref == null && url.userInfo == null
        ) {
            "The local reflection endpoint path is invalid."
        }
    }

    override suspend fun request(request: ReflectionApiRequest): ReflectionCandidates =
        postReflectionRequest(endpointUrl, request, gson, connectionFactory, null,
            includeSongCue = request.evidence.songPreparationReference == null,
            includeSharedMusic = request.evidence.songPreparationReference == null)
}

/** Hosted mode never accepts a local HTTP connection, follows a redirect, or drops credentials. */
internal class HostedReflectionHttpClient(
    private val config: ReflectionConnectionConfig,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val gson: Gson = GsonBuilder().serializeNulls().create(),
    private val capabilityNowNanos: () -> Long = System::nanoTime
) : ReflectionGateway {
    private data class SongCapabilities(val editorialCue: Boolean, val studyVersion: Int,
        val preparation: Boolean, val selection: Boolean, val snippets: Boolean, val snippetFallbacks: Boolean)
    private var songCapabilities: SongCapabilities? = null
    private var nextSnippetCapabilityProbeNanos: Long = 0L

    override suspend fun request(request: ReflectionApiRequest): ReflectionCandidates = withContext(Dispatchers.IO) {
        // A phone installed before a backend deploy must eventually discover the new route.
        // Keep negative capability probes at least thirty seconds apart, without retry loops.
        if (request.evidence.songSnippetLookupAllowed && songCapabilities?.snippetFallbacks == false &&
            capabilityNowNanos() >= nextSnippetCapabilityProbeNanos) songCapabilities = null
        val reference = request.evidence.songPreparationReference
        val sameDestination = reference == null ||
            reference.destinationFingerprint == reflectionDestinationFingerprint(config)
        val sendCue = sameDestination && request.evidence.musicContext.songCue?.source == "RUNSTATE_CURATED" &&
            supportsSongCue(request.evidence.songPassage?.songStudyVersion ?: 0)
        val sendPreparation = sameDestination && reference != null &&
            (songCapabilities ?: readSongCapabilities())?.preparation == true
        val candidatesSameDestination = request.evidence.songCandidates.orEmpty().all {
            it.preparationReference == null || it.preparationReference.destinationFingerprint == reflectionDestinationFingerprint(config)
        }
        val sendSelection = sameDestination && candidatesSameDestination &&
            !request.evidence.songCandidates.isNullOrEmpty() &&
            (songCapabilities ?: readSongCapabilities())?.selection == true
        postReflectionRequest(URL(config.endpoint), request, gson, connectionFactory, config.accessCode,
            sendCue, sendPreparation, sameDestination, sendSelection,
            sendSelection && request.evidence.songSnippetLookupAllowed && songCapabilities?.snippets == true,
            songCapabilities?.snippetFallbacks == true)
    }

    /** A cheap, credential-free capability check keeps the currently deployed strict server usable. */
    private fun supportsSongCue(requiredStudyVersion: Int): Boolean {
        fun SongCapabilities.supports() = editorialCue && studyVersion >= requiredStudyVersion
        songCapabilities?.let { return it.supports() }
        return readSongCapabilities()?.supports() == true
    }

    private fun readSongCapabilities(): SongCapabilities? {
        nextSnippetCapabilityProbeNanos = capabilityNowNanos() + 30_000_000_000L
        var connection: HttpURLConnection? = null
        val supported = try {
            connection = connectionFactory(URL(URL(config.endpoint), "/health"))
            if (connection !is HttpsURLConnection) null else {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                val response = connection.responseCode
                val ready = if (response == HttpURLConnection.HTTP_OK) SongCapabilities(
                    editorialCue = connection.getHeaderField("X-RunState-Song-Cue") == "1",
                    studyVersion = connection.getHeaderField("X-RunState-Song-Study")?.toIntOrNull()
                        ?.takeIf { it >= 0 } ?: 0,
                    preparation = connection.getHeaderField("X-RunState-Song-Preparation") == "1",
                    selection = connection.getHeaderField("X-RunState-Song-Selection") == "1",
                    snippets = connection.getHeaderField("X-RunState-Song-Snippets") == "1",
                    snippetFallbacks = connection.getHeaderField("X-RunState-Song-Snippet-Fallbacks") == "1"
                ) else null
                try { connection.inputStream?.close() } catch (_: Exception) { }
                ready
            }
        } catch (_: Exception) { null }
        finally { try { connection?.disconnect() } catch (_: RuntimeException) { } }
        // Do not pin an older/deploying server's missing study capability for this process.
        // A later explicit retry can discover the deployment without changing frozen evidence.
        if (supported?.studyVersion?.let { it >= 1 } == true) songCapabilities = supported
        return supported
    }
}

private suspend fun postReflectionRequest(
    endpoint: URL,
    request: ReflectionApiRequest,
    gson: Gson,
    connectionFactory: (URL) -> HttpURLConnection,
    accessCode: String?,
    includeSongCue: Boolean = true,
    includeSongPreparation: Boolean = false,
    includeSharedMusic: Boolean = true,
    includeSongSelection: Boolean = false,
    includeSongSnippets: Boolean = false,
    includeSongSnippetFallbacks: Boolean = false
): ReflectionCandidates = withContext(Dispatchers.IO) {
    val hosted = accessCode != null
    var connection: HttpURLConnection? = null
    try {
        // Older hosted servers reject unknown fields, including explicit null. Preserve their
        // existing wire shape whenever weather was not captured for this evidence snapshot.
        val json = gson.toJsonTree(request).asJsonObject
        val evidence = json.getAsJsonObject("evidence")
        evidence.remove("songPassage") // Local timing/transport instructions never go to the model.
        evidence.remove("songPreparationReference")
        evidence.remove("songCandidates")
        evidence.remove("selectedSongId")
        evidence.remove("songSelectionMethod")
        evidence.remove("songSnippetLookupAllowed")
        evidence.remove("songPlaybackReceipt")
        val sentSongs = request.evidence.songCandidates.orEmpty().takeIf { includeSharedMusic && includeSongSelection }.orEmpty()
        if (sentSongs.isNotEmpty()) {
            json.add("songCandidates", gson.toJsonTree(sentSongs.map { candidate ->
                linkedMapOf<String, Any>("candidateId" to candidate.candidateId,
                    "musicContext" to candidate.musicContext).apply {
                    candidate.preparationReference?.let { put("songPreparationId", it.preparationId) }
                    candidate.recording?.takeIf { includeSongSnippets }?.let {
                        put("recording", linkedMapOf<String, Any>("durationMillis" to it.durationMillis).apply {
                            it.albumName?.let { album -> put("albumName", album) }
                        })
                    }
                }
            }))
        }
        val snippetsAllowed = includeSongSnippets && sentSongs.isNotEmpty() && request.evidence.songSnippetLookupAllowed
        if (snippetsAllowed) json.addProperty("allowSongSnippetLookup", true)
        val fallbacksAllowed = snippetsAllowed && includeSongSnippetFallbacks
        if (fallbacksAllowed) json.addProperty("allowSongSnippetFallbacks", true)
        if (!includeSharedMusic) evidence.add("musicContext", gson.toJsonTree(ReflectionMusicContext()))
        request.evidence.songPreparationReference?.takeIf { includeSongPreparation }?.let {
            require(UUID.fromString(it.preparationId).toString() == it.preparationId)
            json.addProperty("songPreparationId", it.preparationId)
        }
        if (request.evidence.weatherContext == null) evidence.remove("weatherContext")
        val expectedCueId = request.evidence.musicContext.songCue?.cueId?.takeIf { includeSongCue && includeSharedMusic }
        if (expectedCueId == null) evidence.getAsJsonObject("musicContext").remove("songCue")
        val body = gson.toJson(json).toByteArray(StandardCharsets.UTF_8)
        connection = connectionFactory(endpoint)
        if (hosted && connection !is HttpsURLConnection) {
            throw ReflectionBridgeException("The hosted reflection connection requires HTTPS.")
        }
        connection.requestMethod = "POST"
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.instanceFollowRedirects = false
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        if (accessCode != null) connection.setRequestProperty("Authorization", "Bearer $accessCode")
        connection.setFixedLengthStreamingMode(body.size)
        connection.outputStream.use { it.write(body) }

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (!hosted) connection.errorStream?.use { readBoundedUtf8(it, false) }
            // Never deserialize or display remote error bodies; they can echo credentials.
            throw ReflectionBridgeException(
                if (hosted) "The hosted reflection service rejected the request (HTTP $responseCode)."
                else "The local reflection service rejected the request (HTTP $responseCode)."
            )
        }
        val responseBody = connection.inputStream.use { readBoundedUtf8(it, hosted) }
        val parsed = if (hosted) {
            try {
                parseReflectionResponse(responseBody, request.requestId, request.runId, expectedCueId, sentSongs, snippetsAllowed, fallbacksAllowed)
            } catch (invalid: ReflectionBridgeException) {
                throw ReflectionBridgeException("The hosted reflection response was invalid.")
            }
        } else {
            parseReflectionResponse(responseBody, request.requestId, request.runId, expectedCueId, sentSongs, snippetsAllowed, fallbacksAllowed)
        }
        val requiredStudyVersion = if (parsed.selectedSongId != null) sentSongs.single {
            it.candidateId == parsed.selectedSongId }.songPassage?.songStudyVersion ?: 0
            else request.evidence.songPassage?.songStudyVersion ?: 0
        val deliveredStudyVersion = connection.getHeaderField("X-RunState-Song-Study")
            ?.toIntOrNull() ?: 0
        // Bind a new cue to this delivery, not just an earlier health check. A rolling
        // deployment or rollback may otherwise leave an older server echoing a new cue ID.
        if (parsed.songSnippet != null && (connection.getHeaderField("X-RunState-Song-Snippets") != "1" ||
            parsed.songSnippet.version == 1 && deliveredStudyVersion < 1 ||
            parsed.songSnippet.version == 2 && connection.getHeaderField("X-RunState-Song-Snippet-Fallbacks") != "1")) {
            parsed.copy(songCueId = null, songSnippet = null)
        } else if (parsed.songCueId != null && requiredStudyVersion > deliveredStudyVersion) {
            parsed.copy(songCueId = null, songSnippet = null)
        } else parsed
    } catch (known: ReflectionBridgeException) {
        throw known
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (unavailable: Exception) {
        if (hosted) throw ReflectionBridgeException("The hosted reflection service is unavailable.")
        throw ReflectionBridgeException("The local reflection service is unavailable.", unavailable)
    } finally {
        try { connection?.disconnect() } catch (_: RuntimeException) {
            // Cleanup must not replace a safe error or expose headers through a transport exception.
        }
    }
}

private fun readBoundedUtf8(input: InputStream, hosted: Boolean): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4_096)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        if (total > MAX_RESPONSE_BYTES) {
            throw ReflectionBridgeException(
                if (hosted) "The hosted reflection response was too large."
                else "The local reflection response was too large."
            )
        }
        output.write(buffer, 0, read)
    }
    return StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(output.toByteArray()))
        .toString()
}

private const val CONNECT_TIMEOUT_MILLIS = 3_000
// The source lookup and two sequential study/writer calls share the backend's 90-second budget.
private const val READ_TIMEOUT_MILLIS = 100_000
private const val MAX_RESPONSE_BYTES = 64 * 1_024

/** Early work contains one song identity, never run facts or a listening-history list. */
internal interface SongStudyPreparationGateway {
    suspend fun supports(): Boolean
    suspend fun supportsMultiple(): Boolean = false
    suspend fun prepare(preparationId: String, trackTitle: String, artistName: String): Boolean
}

internal class HostedSongStudyPreparationClient(
    private val config: ReflectionConnectionConfig,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) : SongStudyPreparationGateway {
    private var multipleSupported = false
    override suspend fun supports(): Boolean = exchange(null)
    override suspend fun supportsMultiple(): Boolean = multipleSupported

    override suspend fun prepare(preparationId: String, trackTitle: String, artistName: String): Boolean {
        require(UUID.fromString(preparationId).toString() == preparationId)
        require(listOf(trackTitle, artistName).all { value ->
            value.isNotBlank() && value.length <= 200 && value.none(Char::isISOControl)
        })
        return exchange(Triple(preparationId, trackTitle, artistName))
    }

    private suspend fun exchange(song: Triple<String, String, String>?): Boolean {
        val active = AtomicReference<HttpURLConnection?>()
        val budget = if (song == null) 4_000L else 65_000L
        return withTimeoutOrNull(budget) {
            coroutineScope {
                suspendCancellableCoroutine<Boolean> { continuation ->
                    val worker = launch(Dispatchers.IO) {
                        val result = fetch(song, active)
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation {
                        worker.cancel()
                        try { active.getAndSet(null)?.disconnect() } catch (_: RuntimeException) { }
                    }
                }
            }
        } ?: false
    }

    private suspend fun fetch(song: Triple<String, String, String>?,
        active: AtomicReference<HttpURLConnection?>): Boolean {
        try {
            val path = if (song == null) "/health" else "/api/reflections/song-study"
            val connection = connectionFactory(URL(URL(config.endpoint), path))
            active.set(connection)
            if (connection !is HttpsURLConnection) return false
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 2_000
            connection.readTimeout = if (song == null) 2_000 else 60_000
            coroutineContext.ensureActive()
            if (song == null) {
                connection.requestMethod = "GET"
                val supported = connection.responseCode == HttpURLConnection.HTTP_OK &&
                    connection.getHeaderField("X-RunState-Song-Preparation") == "1"
                multipleSupported = supported && connection.getHeaderField("X-RunState-Song-Selection") == "1"
                return supported
            }
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer ${config.accessCode}")
            val body = Gson().toJson(mapOf("preparationId" to song.first,
                "trackTitle" to song.second, "artistName" to song.third)).toByteArray(StandardCharsets.UTF_8)
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK ||
                connection.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json") return false
            val response = connection.inputStream.use { readBoundedUtf8(it, true) }
            if (response.length > 4_096) return false
            JsonReader(StringReader(response)).use { reader ->
                reader.strictness = Strictness.STRICT
                reader.beginObject()
                val seen = mutableSetOf<String>()
                var id: String? = null
                var state: String? = null
                var version: Int? = null
                while (reader.hasNext()) when (val name = reader.nextName()) {
                    "preparationId" -> { requireUnique(seen, name); id = reader.requireString() }
                    "state" -> { requireUnique(seen, name); state = reader.requireString() }
                    "songStudyVersion" -> {
                        requireUnique(seen, name)
                        if (reader.peek() != JsonToken.NUMBER) return false
                        val raw = reader.nextString()
                        if (raw != "1") return false
                        version = 1
                    }
                    else -> return false
                }
                reader.endObject()
                return reader.peek() == JsonToken.END_DOCUMENT && seen.size == 3 &&
                    id == song.first && version == 1 && state == "READY"
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return false }
        finally { try { active.getAndSet(null)?.disconnect() } catch (_: RuntimeException) { } }
    }
}

/** Rejects any response that cannot be tied exactly to the request and four branches. */
internal fun parseReflectionResponse(
    rawJson: String,
    expectedRequestId: String,
    expectedRunId: String,
    expectedCueId: String? = null,
    expectedSongs: List<ReflectionSongCandidate> = emptyList(),
    allowSongSnippetLookup: Boolean = false,
    allowSongSnippetFallbacks: Boolean = false
): ReflectionCandidates {
    try {
        JsonReader(StringReader(rawJson)).use { reader ->
            reader.strictness = Strictness.STRICT
            reader.beginObject()
            val seen = mutableSetOf<String>()
            var requestId: String? = null
            var runId: String? = null
            var generationMode: String? = null
            var songCueId: String? = null
            var selectedSongId: String? = null
            var selectionMethod: String? = null
            var songSnippet: ReflectionSongSnippet? = null
            var candidates: ReflectionCandidates? = null
            while (reader.hasNext()) {
                when (val name = reader.nextName()) {
                    "requestId" -> {
                        requireUnique(seen, name)
                        requestId = reader.requireString()
                    }
                    "runId" -> {
                        requireUnique(seen, name)
                        runId = reader.requireString()
                    }
                    "generationMode" -> {
                        requireUnique(seen, name)
                        generationMode = reader.requireString()
                    }
                    "candidates" -> {
                        requireUnique(seen, name)
                        candidates = reader.readCandidates()
                    }
                    "songCueId" -> {
                        requireUnique(seen, name)
                        songCueId = reader.requireString()
                    }
                    "selectedSongId" -> { requireUnique(seen, name); selectedSongId = reader.requireString() }
                    "songSelectionMethod" -> { requireUnique(seen, name); selectionMethod = reader.requireString() }
                    "songSnippet" -> { requireUnique(seen, name); songSnippet = reader.readSongSnippet() }
                    else -> throw ReflectionBridgeException("The local reflection response was invalid.")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT &&
                seen.containsAll(setOf("requestId", "runId", "generationMode", "candidates")) &&
                seen.size == 4 + (if (songCueId == null) 0 else 1) + (if (selectedSongId == null) 0 else 2) +
                    (if (songSnippet == null) 0 else 1)) {
                "The local reflection response was invalid."
            }
            // A missing acknowledgement is an ordinary reflection fallback. It must remain
            // readable, but the coordinator will not attach this request's optional song clip.
            require(requestId == expectedRequestId && runId == expectedRunId) {
                "Reflection response identifiers do not match the request."
            }
            require(generationMode == "FAKE" || generationMode == "ANTHROPIC") {
                "The local reflection generation mode was invalid."
            }
            val winner = selectedSongId?.let { id -> expectedSongs.singleOrNull { it.candidateId == id }
                ?: throw ReflectionBridgeException("The selected song was not observed in this run.") }
            require((selectedSongId == null && selectionMethod == null) ||
                selectionMethod in setOf("CONTEXT_MATCH", "AVAILABLE_FALLBACK") ||
                selectionMethod == "OBSERVED_FALLBACK" && allowSongSnippetFallbacks && songSnippet?.version == 2 && songCueId == null)
            require(expectedSongs.isEmpty() || songCueId == null || selectedSongId != null)
            if (songSnippet != null) {
                require(generationMode == "ANTHROPIC" && (songCueId == songSnippet.cueId ||
                    songSnippet.version == 2 && songCueId == null))
                require(songSnippet.selectionMethod != "RECORDING_OPENING_FALLBACK" || songCueId == null)
                require(winner?.musicContext?.songCue == null || songCueId == null)
                validateSongSnippet(songSnippet, requireNotNull(winner), allowSongSnippetLookup, allowSongSnippetFallbacks)
            }
            require(songSnippet != null || songCueId == null || songCueId == (winner?.musicContext?.songCue?.cueId ?: expectedCueId).takeIf {
                winner == null || winner.musicContext.songCue != null
            }) { "The song pairing does not match the selected song." }
            return requireNotNull(candidates).copy(isTestData = generationMode == "FAKE", songCueId = songCueId,
                selectedSongId = selectedSongId, songSelectionMethod = selectionMethod, songSnippet = songSnippet)
        }
    } catch (known: ReflectionBridgeException) {
        throw known
    } catch (invalid: Exception) {
        throw ReflectionBridgeException("The local reflection response was invalid.", invalid)
    }
}

private fun JsonReader.readSongSnippet(): ReflectionSongSnippet {
    val strings = setOf("cueId", "source", "sourceId", "trackTitle", "artistName", "albumName", "selectionMethod")
    val numbers = setOf("trackDurationMillis", "startPositionMillis", "durationMillis", "version")
    val seen = mutableSetOf<String>()
    val values = mutableMapOf<String, String>()
    beginObject()
    while (hasNext()) {
        val name = nextName()
        requireUnique(seen, name)
        val value = when (name) {
            in strings -> requireString().also { require(it.isNotBlank() && it.length <= 200 && it.none(Char::isISOControl)) }
            in numbers -> {
                require(peek() == JsonToken.NUMBER)
                nextString().also { require(it.matches(Regex("0|[1-9][0-9]{0,15}"))) }
            }
            else -> error("Unexpected snippet field.")
        }
        values[name] = value
    }
    endObject()
    require(seen == strings + numbers || seen == (strings - "albumName") + numbers)
    fun text(name: String) = values.getValue(name)
    require(text("version") in setOf("1", "2"))
    return ReflectionSongSnippet(text("cueId"), text("source"), text("sourceId"), text("trackTitle"),
        text("artistName"), values["albumName"], text("trackDurationMillis").toLong(),
        text("startPositionMillis").toLong(), text("durationMillis").toLong(), text("selectionMethod"), text("version").toInt())
}

private fun requireUnique(seen: MutableSet<String>, name: String) {
    if (!seen.add(name)) {
        throw ReflectionBridgeException("The local reflection response contains duplicate keys.")
    }
}

private fun JsonReader.readCandidates(): ReflectionCandidates {
    beginObject()
    val values = mutableMapOf<String, String>()
    val expected = setOf("spent", "feeling_good", "powered_up", "no_selection")
    while (hasNext()) {
        val name = nextName()
        if (name !in expected || name in values) {
            throw ReflectionBridgeException("The local reflection candidates were invalid.")
        }
        val value = requireString()
        if (value.isBlank()) {
            throw ReflectionBridgeException("The local reflection candidates were invalid.")
        }
        values[name] = value
    }
    endObject()
    require(values.keys == expected) { "The local reflection candidates were invalid." }
    return ReflectionCandidates(
        spent = values.getValue("spent"),
        feelingGood = values.getValue("feeling_good"),
        poweredUp = values.getValue("powered_up"),
        noSelection = values.getValue("no_selection")
    )
}

private fun JsonReader.requireString(): String {
    if (peek() != JsonToken.STRING) {
        throw ReflectionBridgeException("The local reflection response was invalid.")
    }
    return nextString()
}

internal class ReflectionBridgeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
