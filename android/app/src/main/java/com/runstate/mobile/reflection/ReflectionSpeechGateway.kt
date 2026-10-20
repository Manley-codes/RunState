package com.runstate.mobile.reflection

import com.google.gson.Gson
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume

/** A missing/older voice service is optional; the exact text remains locally speakable. */
internal fun interface ReflectionSpeechGateway {
    suspend fun synthesize(text: String): ByteArray?
}

internal const val MAX_REFLECTION_AUDIO_BYTES = 2 * 1024 * 1024

/**
 * The two server voices. Each has its own path and capability, so adding the run voice never
 * changes how the reflection voice is requested.
 */
internal enum class SpeechVoice(val path: String, val capabilityHeader: String, val maxTextLength: Int) {
    /** The accepted custom voice that reads the selected reflection. */
    REFLECTION("/api/reflections/speech", "X-RunState-Reflection-Voice", 4_000),
    /** The separately chosen voice for the countdown, saved facts, energy question and announcements. */
    RUN("/api/run-voice/speech", "X-RunState-Run-Voice", 500)
}

/** Provider credentials never leave the server. Only the exact line being spoken is uploaded. */
internal class ReflectionSpeechHttpClient private constructor(
    private val endpoint: URL,
    private val accessCode: String?,
    private val voice: SpeechVoice,
    private val connectionFactory: (URL) -> HttpURLConnection
) : ReflectionSpeechGateway {
    companion object {
        fun hosted(config: ReflectionConnectionConfig, voice: SpeechVoice = SpeechVoice.REFLECTION,
            connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
        ) = ReflectionSpeechHttpClient(URL(config.endpoint), config.accessCode, voice, connectionFactory)

        /** Called only after the composition root admits the debug local bridge. */
        fun local(endpoint: String, voice: SpeechVoice = SpeechVoice.REFLECTION,
            connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
        ): ReflectionSpeechHttpClient {
            val url = URL(endpoint)
            require(url.protocol == "http" && url.host in setOf("127.0.0.1", "10.0.2.2") &&
                url.port == 8787 && url.path == "/api/reflections/candidates" &&
                url.query == null && url.ref == null && url.userInfo == null)
            return ReflectionSpeechHttpClient(url, null, voice, connectionFactory)
        }
    }

    override suspend fun synthesize(text: String): ByteArray? {
        if (text.isBlank() || text.length > voice.maxTextLength) return null
        val active = AtomicReference<HttpURLConnection?>()
        return withTimeoutOrNull(20_000L) {
            coroutineScope {
                suspendCancellableCoroutine { continuation ->
                    val worker = launch(Dispatchers.IO) {
                        val result = fetch(text, active)
                        if (continuation.isActive) continuation.resume(result)
                    }
                    continuation.invokeOnCancellation {
                        worker.cancel()
                        // Disconnect immediately on Stop/close/deadline, including a blocked body read.
                        try { active.getAndSet(null)?.disconnect() } catch (_: RuntimeException) { }
                    }
                }
            }
        }
    }

    private suspend fun fetch(text: String, active: AtomicReference<HttpURLConnection?>): ByteArray? {
        val deadlineNanos = System.nanoTime() + 20_000_000_000L
        fun remaining(maximum: Int): Int {
            val millis = (deadlineNanos - System.nanoTime()) / 1_000_000L
            if (millis <= 0) throw IOException("Voice request deadline reached.")
            return minOf(maximum.toLong(), millis).toInt().coerceAtLeast(1)
        }
        try {
            suspend fun connect(path: String): HttpURLConnection = connectionFactory(URL(endpoint, path)).also {
                active.set(it)
                require(accessCode == null || it is HttpsURLConnection)
                it.instanceFollowRedirects = false
                it.connectTimeout = remaining(2_000)
                it.readTimeout = remaining(15_000)
                coroutineContext.ensureActive()
            }
            val health = connect("/health")
            health.requestMethod = "GET"
            health.readTimeout = remaining(2_000)
            val supported = health.responseCode == HttpURLConnection.HTTP_OK &&
                health.getHeaderField(voice.capabilityHeader) == "1"
            health.disconnect()
            active.compareAndSet(health, null)
            coroutineContext.ensureActive()
            if (!supported) return null
            val body = Gson().toJson(mapOf("text" to text)).toByteArray(StandardCharsets.UTF_8)
            val speech = connect(voice.path)
            speech.requestMethod = "POST"
            speech.doOutput = true
            speech.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            speech.setRequestProperty("Accept", "audio/mpeg")
            accessCode?.let { speech.setRequestProperty("Authorization", "Bearer $it") }
            speech.setFixedLengthStreamingMode(body.size)
            speech.outputStream.use { it.write(body) }
            speech.readTimeout = remaining(15_000)
            if (speech.responseCode != HttpURLConnection.HTTP_OK ||
                speech.contentType?.substringBefore(';')?.trim()?.lowercase() != "audio/mpeg" ||
                speech.contentLengthLong > MAX_REFLECTION_AUDIO_BYTES) return null
            val output = ByteArrayOutputStream()
            speech.inputStream.use { input ->
                val buffer = ByteArray(8_192)
                while (true) {
                    coroutineContext.ensureActive()
                    speech.readTimeout = remaining(15_000)
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size() + count > MAX_REFLECTION_AUDIO_BYTES) return null
                    output.write(buffer, 0, count)
                }
            }
            coroutineContext.ensureActive()
            return output.toByteArray().takeIf { it.isNotEmpty() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never retain URL/headers/error bodies or transport exceptions that can contain a token.
            return null
        } finally {
            try { active.getAndSet(null)?.disconnect() } catch (_: RuntimeException) { }
        }
    }
}
