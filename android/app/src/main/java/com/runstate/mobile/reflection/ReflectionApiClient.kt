package com.runstate.mobile.reflection

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.MusicPlaybackState
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.TransitionHistoryCoverage
import com.runstate.mobile.weather.RunWeatherSnapshot
import com.runstate.mobile.music.SongReflectionPlan
import com.runstate.mobile.music.selectSongReflectionPlan
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
    val songPassage: SongReflectionPlan? = null
)

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
    val songCueId: String? = null
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
    weather: RunWeatherSnapshot? = null
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

    val song = selectSongReflectionPlan(run, finishEpochMillis, musicObservations)
    val musicContext = if (song == null) {
        reflectionMusicContextFromSavedRun(run, finishEpochMillis, musicObservations)
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
            songPassage = song?.plan()
        )
    )
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
    private val gson: Gson = GsonBuilder().serializeNulls().create()
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
        postReflectionRequest(endpointUrl, request, gson, { it.openConnection() as HttpURLConnection }, null)
}

/** Hosted mode never accepts a local HTTP connection, follows a redirect, or drops credentials. */
internal class HostedReflectionHttpClient(
    private val config: ReflectionConnectionConfig,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
    private val gson: Gson = GsonBuilder().serializeNulls().create()
) : ReflectionGateway {
    private var songCueSupported: Boolean? = null

    override suspend fun request(request: ReflectionApiRequest): ReflectionCandidates = withContext(Dispatchers.IO) {
        val sendCue = request.evidence.musicContext.songCue != null && supportsSongCue()
        postReflectionRequest(URL(config.endpoint), request, gson, connectionFactory, config.accessCode, sendCue)
    }

    /** A cheap, credential-free capability check keeps the currently deployed strict server usable. */
    private fun supportsSongCue(): Boolean {
        songCueSupported?.let { return it }
        var connection: HttpURLConnection? = null
        val supported = try {
            connection = connectionFactory(URL(URL(config.endpoint), "/health"))
            if (connection !is HttpsURLConnection) false else {
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 2_000
                connection.readTimeout = 2_000
                val response = connection.responseCode
                val ready = response == HttpURLConnection.HTTP_OK &&
                    connection.getHeaderField("X-RunState-Song-Cue") == "1"
                try { connection.inputStream?.close() } catch (_: Exception) { }
                ready
            }
        } catch (_: Exception) { false }
        finally { try { connection?.disconnect() } catch (_: RuntimeException) { } }
        songCueSupported = supported
        return supported
    }
}

private suspend fun postReflectionRequest(
    endpoint: URL,
    request: ReflectionApiRequest,
    gson: Gson,
    connectionFactory: (URL) -> HttpURLConnection,
    accessCode: String?,
    includeSongCue: Boolean = true
): ReflectionCandidates = withContext(Dispatchers.IO) {
    val hosted = accessCode != null
    var connection: HttpURLConnection? = null
    try {
        // Older hosted servers reject unknown fields, including explicit null. Preserve their
        // existing wire shape whenever weather was not captured for this evidence snapshot.
        val json = gson.toJsonTree(request).asJsonObject
        val evidence = json.getAsJsonObject("evidence")
        evidence.remove("songPassage") // Local timing/transport instructions never go to the model.
        if (request.evidence.weatherContext == null) evidence.remove("weatherContext")
        val expectedCueId = request.evidence.musicContext.songCue?.cueId?.takeIf { includeSongCue }
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
        if (hosted) {
            try {
                parseReflectionResponse(responseBody, request.requestId, request.runId, expectedCueId)
            } catch (invalid: ReflectionBridgeException) {
                throw ReflectionBridgeException("The hosted reflection response was invalid.")
            }
        } else {
            parseReflectionResponse(responseBody, request.requestId, request.runId, expectedCueId)
        }
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
// Anthropic's backend request can legitimately run for up to 45 seconds.
private const val READ_TIMEOUT_MILLIS = 60_000
private const val MAX_RESPONSE_BYTES = 64 * 1_024

/** Rejects any response that cannot be tied exactly to the request and four branches. */
internal fun parseReflectionResponse(
    rawJson: String,
    expectedRequestId: String,
    expectedRunId: String,
    expectedCueId: String? = null
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
                        require(expectedCueId != null && songCueId == expectedCueId) {
                            "The song pairing does not match the request."
                        }
                    }
                    else -> throw ReflectionBridgeException("The local reflection response was invalid.")
                }
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT &&
                seen.containsAll(setOf("requestId", "runId", "generationMode", "candidates")) &&
                seen.size == if (songCueId == null) 4 else 5) {
                "The local reflection response was invalid."
            }
            require(songCueId == expectedCueId) { "The reflection song pairing was not acknowledged." }
            require(requestId == expectedRequestId && runId == expectedRunId) {
                "Reflection response identifiers do not match the request."
            }
            require(generationMode == "FAKE" || generationMode == "ANTHROPIC") {
                "The local reflection generation mode was invalid."
            }
            return requireNotNull(candidates).copy(isTestData = generationMode == "FAKE", songCueId = songCueId)
        }
    } catch (known: ReflectionBridgeException) {
        throw known
    } catch (invalid: Exception) {
        throw ReflectionBridgeException("The local reflection response was invalid.", invalid)
    }
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
