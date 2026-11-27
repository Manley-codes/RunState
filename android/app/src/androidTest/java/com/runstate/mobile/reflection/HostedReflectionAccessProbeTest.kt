package com.runstate.mobile.reflection

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.runstate.mobile.RunStateApplication
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.settings.AndroidRunPreferencesStore
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.Instant
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly gated checks. Credentials and saved evidence stay on-device and are never reported. */
@RunWith(AndroidJUnit4::class)
class HostedReflectionAccessProbeTest {
    /**
     * PAID, opt-in repair verification. Reads the latest failed saved Sample, retries its exact
     * evidence once and checks both hosted voices silently. No Activity, Room writes or playback.
     * The normal History retry can subsequently retrieve the cached answer through its own writer.
     */
    @Test fun latestFailedSampleCanGenerateAndFetchBothVoicesWithoutPlayback() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyHostedCloudRepair") == "yes")
        val context = instrumentation.targetContext
        val report = Bundle()
        var stage = "PREFLIGHT"
        fun advance(next: String) {
            stage = next
            instrumentation.sendStatus(2, Bundle().apply { putString("runstate_repair_stage", next) })
        }
        try {
            withTimeout(150_000L) {
                val preferences = AndroidRunPreferencesStore(context).load()
                check(preferences.cloudReflectionsEnabled)
                val config = requireNotNull(AndroidReflectionConnectionStore(context).load())
                val endpoint = URL(config.endpoint)
                check(endpoint.protocol == "https" &&
                    endpoint.host.equals("runstate-reflections.onrender.com", ignoreCase = true) &&
                    endpoint.port in setOf(-1, 443))
                val db = (context.applicationContext as RunStateApplication).database
                check(db.runDao().findActiveRuns().isEmpty())
                val samples = db.runDao().observeCompletedRuns().first()
                    .filter { it.activityFinalized && it.trackingMode == RunTrackingMode.FIXTURE }
                    .sortedByDescending { it.finishEpochMillis }
                val run = requireNotNull(samples.firstOrNull())
                val stored = requireNotNull(db.reflectionDao().findByRunId(run.runId))
                check(stored.status == ReflectionStatus.FAILED && stored.text == null && stored.candidatesJson == null)
                val evidence = GsonBuilder().create().fromJson(stored.evidenceJson, CompletedRunEvidence::class.java)
                check(evidence.finishEpochMillis == run.finishEpochMillis)
                val access = requireNotNull(HostedReflectionAccess(config).refresh())
                check(access.tier == ReflectionTier.OWNER && access.allowsNewRun)

                val opened = mutableMapOf<String, Int>()
                val factory: (URL) -> java.net.HttpURLConnection = { url ->
                    check(url.protocol == "https" && url.host.equals("runstate-reflections.onrender.com", true))
                    val label = when (url.path) {
                        "/health" -> "health"
                        "/api/reflections/candidates" -> "written"
                        "/api/reflections/speech" -> "reflection_voice"
                        "/api/run-voice/speech" -> "run_voice"
                        else -> error("Unexpected repair endpoint")
                    }
                    synchronized(opened) {
                        opened[label] = (opened[label] ?: 0) + 1
                        check(label == "health" || opened[label] == 1) // Never retry a paid endpoint here.
                    }
                    RepairObservedConnection(url.openConnection() as HttpsURLConnection, label) {
                        instrumentation.sendStatus(2, it)
                    }
                }
                advance("WRITTEN")
                val candidates = HostedReflectionHttpClient(config, connectionFactory = factory)
                    .request(ReflectionApiRequest(stored.requestId, stored.runId, evidence))
                check(!candidates.isTestData)
                val text = when (stored.selectedBranch ?: ReflectionBranch.NO_SELECTION) {
                    ReflectionBranch.SPENT -> candidates.spent
                    ReflectionBranch.FEELING_GOOD -> candidates.feelingGood
                    ReflectionBranch.POWERED_UP -> candidates.poweredUp
                    ReflectionBranch.NO_SELECTION -> candidates.noSelection
                }
                check(text.isNotBlank())
                report.putBoolean("runstate_repair_written_ready", true)
                report.putBoolean("runstate_repair_music_selected", candidates.selectedSongId != null)
                report.putBoolean("runstate_repair_snippet_available", candidates.songSnippet != null)
                advance("RUN_VOICE")
                val runAudio = ReflectionSpeechHttpClient.hosted(config, SpeechVoice.RUN, factory)
                    .synthesize("RunState voice connection restored.")
                check(runAudio != null && runAudio.isNotEmpty())
                report.putBoolean("runstate_repair_run_voice_ready", true)
                advance("REFLECTION_VOICE")
                val reflectionAudio = ReflectionSpeechHttpClient.hosted(config, SpeechVoice.REFLECTION, factory)
                    .synthesize(text)
                check(reflectionAudio != null && reflectionAudio.isNotEmpty())
                report.putBoolean("runstate_repair_reflection_voice_ready", true)
                check(db.reflectionDao().findByRunId(stored.runId) == stored)
                check(db.runDao().findById(run.runId) == run)
                check(AndroidRunPreferencesStore(context).load() == preferences)
                report.putBoolean("runstate_repair_saved_data_unchanged", true)
                advance("COMPLETE")
            }
        } catch (failure: Exception) {
            report.putString("runstate_repair_failure", when (failure) {
                is kotlinx.coroutines.TimeoutCancellationException, is SocketTimeoutException -> "TIMEOUT"
                is ReflectionBridgeException -> reflectionFailureKind(failure).name
                is java.io.IOException -> "NETWORK"
                else -> "UNAVAILABLE_OR_INVALID"
            })
            throw AssertionError("Hosted cloud repair verification failed at $stage; see sanitized HTTP status fields.")
        } finally {
            report.putString("runstate_repair_stage", stage)
            instrumentation.sendStatus(2, report)
        }
    }

    @Test fun savedConnectionCanReadHostedAccessWithoutGeneratingAnything() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("diagnoseHostedAccessOnly") == "yes")
        val report = Bundle()
        var stage = "CONNECTION"
        try {
            val config = requireNotNull(AndroidReflectionConnectionStore(instrumentation.targetContext).load())
            report.putBoolean("runstate_access_saved_connection", true)
            report.putBoolean("runstate_access_endpoint_matches_expected_render",
                URL(config.endpoint).host.equals("runstate-reflections.onrender.com", ignoreCase = true))
            stage = "HEALTH"
            val health = get(config, "/health", authenticated = false)
            report.putInt("runstate_access_health_http", health.code)
            report.putString("runstate_access_capability", when (health.capability) {
                "1" -> "SUPPORTED"
                null -> "ABSENT"
                else -> "UNKNOWN"
            })
            check(health.code == 200)
            check(JsonParser.parseString(health.body).asJsonObject["status"]?.asString == "ok")

            stage = "ACCESS"
            val response = get(config, "/api/reflections/access", authenticated = true)
            report.putInt("runstate_access_http", response.code)
            val body = JsonParser.parseString(response.body).asJsonObject
            if (response.code != 200) {
                // Only known server codes are reportable. Never print a response or exception message.
                val code = body.getAsJsonObject("error")?.get("code")?.asString
                report.putString("runstate_access_error", code?.takeIf {
                    it in setOf("unauthorized", "reflection_access_unavailable", "not_found",
                        "method_not_allowed", "invalid_request")
                } ?: "UNRECOGNIZED")
                error("Access unavailable")
            }
            check(response.json)
            val tier = body["tier"]?.asString
            val status = body["status"]?.asString
            check(tier in setOf("owner", "tracking", "trial", "pilot"))
            check(status in setOf("owner", "tracking", "available", "active", "expired"))
            report.putString("runstate_access_tier", tier)
            report.putString("runstate_access_status", status)
            body["remaining"]?.asString?.let { value ->
                val remaining = requireNotNull(value.toIntOrNull())
                check(remaining >= 0)
                report.putInt("runstate_access_remaining", remaining)
            }
            body["expiresAt"]?.asString?.let { value ->
                report.putBoolean("runstate_access_expired", !Instant.parse(value).isAfter(Instant.now()))
            }
            stage = "PRODUCTION_ACCESS"
            // Exercise the exact app parser/cache path as well, without trial activation or admission.
            val production = HostedReflectionAccess(config).refresh()
            report.putBoolean("runstate_access_production_available", production != null)
            production?.let {
                report.putString("runstate_access_production_tier", it.tier.name)
                report.putString("runstate_access_production_status", it.status)
                report.putBoolean("runstate_access_production_allows_new_run", it.allowsNewRun)
                report.putBoolean("runstate_access_production_active", it.active)
                it.remaining?.let { remaining -> report.putInt("runstate_access_production_remaining", remaining) }
            }
            check(production != null)
            stage = "COMPLETE"
        } catch (failure: Exception) {
            report.putString("runstate_access_failure", when (failure) {
                is SocketTimeoutException -> "TIMEOUT"
                is UnknownHostException -> "DNS"
                is SSLException -> "TLS"
                is java.io.IOException -> "NETWORK"
                else -> "UNAVAILABLE_OR_INVALID"
            })
            throw AssertionError("GET-only hosted access diagnosis failed at $stage; see safe status fields.")
        } finally {
            report.putString("runstate_access_stage", stage)
            instrumentation.sendStatus(2, report)
        }
    }

    private data class Response(val code: Int, val body: String, val json: Boolean, val capability: String?)

    /** Delegate the real transport while reporting only fixed endpoint labels and safe error codes. */
    private class RepairObservedConnection(
        private val delegate: HttpsURLConnection,
        private val label: String,
        private val report: (Bundle) -> Unit
    ) : HttpsURLConnection(delegate.url) {
        private var reported = false
        override fun getResponseCode(): Int = delegate.responseCode.also { code ->
            if (!reported) {
                reported = true
                val result = Bundle().apply {
                    putString("runstate_repair_endpoint", label)
                    putInt("runstate_repair_http", code)
                }
                if (code !in 200..299) {
                    val safe = runCatching {
                        val body = delegate.errorStream?.use { stream ->
                            val output = ByteArrayOutputStream()
                            val buffer = ByteArray(1_024)
                            while (output.size() < 8_192) {
                                val count = stream.read(buffer, 0, minOf(buffer.size, 8_192 - output.size()))
                                if (count < 0) break
                                output.write(buffer, 0, count)
                            }
                            output.toString("UTF-8")
                        }
                        val value = body?.let { JsonParser.parseString(it).asJsonObject }
                        value?.getAsJsonObject("error")?.get("code")?.asString
                    }.getOrNull()
                    result.putString("runstate_repair_error", safe?.takeIf { it in setOf(
                        "unauthorized", "spend_control_unavailable", "spend_limit_reached", "provider_unavailable",
                        "provider_failure", "provider_timeout", "reflection_pending", "speech_pending",
                        "reflection_storage_unavailable", "speech_storage_unavailable", "server_busy",
                        "server_capacity", "invalid_request", "internal_error"
                    ) } ?: "UNRECOGNIZED")
                }
                report(result)
            }
        }
        override fun connect() = delegate.connect()
        override fun disconnect() = delegate.disconnect()
        override fun usingProxy() = delegate.usingProxy()
        override fun setRequestMethod(method: String) { delegate.requestMethod = method }
        override fun setConnectTimeout(timeout: Int) { delegate.connectTimeout = timeout }
        override fun setReadTimeout(timeout: Int) { delegate.readTimeout = timeout }
        override fun setUseCaches(use: Boolean) { delegate.useCaches = use }
        override fun setInstanceFollowRedirects(follow: Boolean) { delegate.instanceFollowRedirects = follow }
        override fun setDoOutput(output: Boolean) { delegate.doOutput = output }
        override fun setRequestProperty(key: String, value: String) = delegate.setRequestProperty(key, value)
        override fun setFixedLengthStreamingMode(length: Int) = delegate.setFixedLengthStreamingMode(length)
        override fun setFixedLengthStreamingMode(length: Long) = delegate.setFixedLengthStreamingMode(length)
        override fun getOutputStream() = delegate.outputStream
        override fun getInputStream() = delegate.inputStream
        override fun getErrorStream() = delegate.errorStream
        override fun getHeaderField(name: String?) = delegate.getHeaderField(name)
        override fun getContentType() = delegate.contentType
        override fun getContentLengthLong() = delegate.contentLengthLong
        override fun getCipherSuite() = delegate.cipherSuite
        override fun getLocalCertificates() = delegate.localCertificates
        override fun getServerCertificates() = delegate.serverCertificates
    }

    private fun get(config: ReflectionConnectionConfig, path: String, authenticated: Boolean): Response {
        check((path == "/health" && !authenticated) || (path == "/api/reflections/access" && authenticated))
        val connection = URL(URL(config.endpoint), path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "GET"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 3_000
            connection.readTimeout = 4_000
            connection.useCaches = false
            connection.doOutput = false
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) connection.setRequestProperty("Authorization", "Bearer ${config.accessCode}")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val output = ByteArrayOutputStream()
            stream?.use {
                val buffer = ByteArray(1_024)
                while (true) {
                    val size = it.read(buffer)
                    if (size < 0) break
                    check(output.size() + size <= 8_192)
                    output.write(buffer, 0, size)
                }
            }
            return Response(code, output.toString("UTF-8"),
                connection.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true,
                connection.getHeaderField("X-RunState-Reflection-Access"))
        } finally {
            connection.disconnect()
        }
    }
}
