package com.runstate.mobile.reflection

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.TransitionHistoryCoverage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal const val LOCAL_REFLECTION_ENDPOINT =
    "http://10.0.2.2:8787/api/reflections/candidates"

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
    val musicContext: ReflectionMusicContext = ReflectionMusicContext()
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
    val artistDescription: String? = null
)

/** All four candidates are validated before the no-selection branch may be displayed. */
internal data class ReflectionCandidates(
    val spent: String,
    val feelingGood: String,
    val poweredUp: String,
    val noSelection: String
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
    transitions: List<RunTransitionEntity>
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

    val activeDurationMillis = if (run.transitionHistoryComplete == true) {
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

    return ReflectionApiRequest(
        requestId = requestId,
        runId = run.runId,
        evidence = CompletedRunEvidence(
            officialStartEpochMillis = run.officialStartEpochMillis,
            finishEpochMillis = finishEpochMillis,
            startTimezoneId = run.startTimezoneId,
            activeDurationMillis = activeDurationMillis,
            metrics = metrics
        )
    )
}

/** A small debug bridge to the loopback-bound backend through the emulator host alias. */
internal class LocalReflectionHttpClient(
    endpoint: String = LOCAL_REFLECTION_ENDPOINT,
    private val gson: Gson = GsonBuilder().serializeNulls().create()
) : ReflectionGateway {

    private val endpointUrl = URL(endpoint).also { url ->
        require(url.protocol == "http") { "The local reflection endpoint must use HTTP." }
        require(url.host == "10.0.2.2" && url.port == 8787) {
            "The Android debug bridge may only use the emulator host alias on port 8787."
        }
        require(url.path == "/api/reflections/candidates" && url.query == null) {
            "The local reflection endpoint path is invalid."
        }
    }

    override suspend fun request(request: ReflectionApiRequest): ReflectionCandidates =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(request).toByteArray(StandardCharsets.UTF_8)
            val connection = endpointUrl.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
                connection.readTimeout = READ_TIMEOUT_MILLIS
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setRequestProperty("Accept", "application/json")
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    connection.errorStream?.use(::readBoundedUtf8)
                    throw ReflectionBridgeException(
                        "The local reflection service rejected the request (HTTP $responseCode)."
                    )
                }

                val responseBody = connection.inputStream.use(::readBoundedUtf8)
                parseReflectionResponse(responseBody, request.requestId, request.runId)
            } catch (known: ReflectionBridgeException) {
                throw known
            } catch (unavailable: Exception) {
                throw ReflectionBridgeException("The local reflection service is unavailable.", unavailable)
            } finally {
                connection.disconnect()
            }
        }

    private fun readBoundedUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) {
                throw ReflectionBridgeException("The local reflection response was too large.")
            }
            output.write(buffer, 0, read)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 3_000
        const val READ_TIMEOUT_MILLIS = 15_000
        const val MAX_RESPONSE_BYTES = 64 * 1_024
    }
}

/** Rejects any response that cannot be tied exactly to the request and four branches. */
internal fun parseReflectionResponse(
    rawJson: String,
    expectedRequestId: String,
    expectedRunId: String
): ReflectionCandidates {
    try {
        val root = JsonParser.parseString(rawJson).requireObject("response")
        root.requireExactKeys(setOf("requestId", "runId", "candidates"), "response")

        val requestId = root.requireString("requestId")
        val runId = root.requireString("runId")
        require(requestId == expectedRequestId && runId == expectedRunId) {
            "Reflection response identifiers do not match the request."
        }

        val candidates = root.get("candidates").requireObject("candidates")
        candidates.requireExactKeys(
            setOf("spent", "feeling_good", "powered_up", "no_selection"),
            "candidates"
        )

        return ReflectionCandidates(
            spent = candidates.requireNonBlankString("spent"),
            feelingGood = candidates.requireNonBlankString("feeling_good"),
            poweredUp = candidates.requireNonBlankString("powered_up"),
            noSelection = candidates.requireNonBlankString("no_selection")
        )
    } catch (known: ReflectionBridgeException) {
        throw known
    } catch (invalid: Exception) {
        throw ReflectionBridgeException("The local reflection response was invalid.", invalid)
    }
}

private fun JsonElement?.requireObject(label: String): JsonObject {
    if (this == null || !isJsonObject) {
        throw ReflectionBridgeException("The local reflection response was invalid.")
    }
    return asJsonObject
}

private fun JsonObject.requireExactKeys(expected: Set<String>, label: String) {
    if (keySet() != expected) {
        throw ReflectionBridgeException("The local reflection $label was invalid.")
    }
}

private fun JsonObject.requireString(name: String): String {
    val value = get(name)
    if (value == null || !value.isJsonPrimitive || !value.asJsonPrimitive.isString) {
        throw ReflectionBridgeException("The local reflection response was invalid.")
    }
    return value.asString
}

private fun JsonObject.requireNonBlankString(name: String): String =
    requireString(name).also { value ->
        if (value.isBlank()) {
            throw ReflectionBridgeException("The local reflection response was invalid.")
        }
    }

internal class ReflectionBridgeException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)
