package com.runstate.mobile.reflection

import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.cert.Certificate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class ReflectionSpeechGatewayTest {
    private val config = ReflectionConnectionConfig(
        "https://reflection.example.com/api/reflections/candidates", "K".repeat(43))
    private val mp3 = byteArrayOf(0x49, 0x44, 0x33, 1, 2, 3)

    @Test fun sendsOneExactSelectedTextAndBearerOnlyToSameOriginSpeech() = runBlocking {
        val health = FakeHttps(URL("https://reflection.example.com/health"), capability = "1")
        val speech = FakeHttps(URL("https://reflection.example.com/api/reflections/speech"), mp3)
        val urls = mutableListOf<URL>()
        val text = "You finished feeling good.\nKeep that good feeling—it's yours."
        val result = ReflectionSpeechHttpClient.hosted(config) { url ->
            urls += url
            if (url.path == "/health") health else speech
        }.synthesize(text)
        assertArrayEquals(mp3, result)
        assertEquals(listOf("/health", "/api/reflections/speech"), urls.map { it.path })
        assertTrue(urls.all { it.protocol == "https" && it.host == "reflection.example.com" })
        val body = JsonParser.parseString(speech.sent.toString("UTF-8")).asJsonObject
        assertEquals(setOf("text"), body.keySet())
        assertEquals(text, body["text"].asString)
        assertNull(health.getRequestProperty("Authorization"))
        assertEquals("Bearer ${config.accessCode}", speech.getRequestProperty("Authorization"))
        assertEquals("POST", speech.requestMethod)
        assertEquals("audio/mpeg", speech.getRequestProperty("Accept"))
        assertFalse(health.instanceFollowRedirects)
        assertFalse(speech.instanceFollowRedirects)
        assertTrue(health.disconnected && speech.disconnected)
    }

    @Test fun runVoiceUsesItsOwnPathCapabilityAndShorterLimit() = runBlocking {
        val health = FakeHttps(URL("https://reflection.example.com/health"), capability = "1",
            capabilityName = "X-RunState-Run-Voice")
        val speech = FakeHttps(URL("https://reflection.example.com/api/run-voice/speech"), mp3)
        val urls = mutableListOf<URL>()
        val result = ReflectionSpeechHttpClient.hosted(config, SpeechVoice.RUN) { url ->
            urls += url
            if (url.path == "/health") health else speech
        }.synthesize("Starting in ten.")
        assertArrayEquals(mp3, result)
        assertEquals(listOf("/health", "/api/run-voice/speech"), urls.map { it.path })
        assertEquals("Bearer ${config.accessCode}", speech.getRequestProperty("Authorization"))
        val body = JsonParser.parseString(speech.sent.toString("UTF-8")).asJsonObject
        assertEquals(setOf("text"), body.keySet())
        assertEquals("Starting in ten.", body["text"].asString)

        // A server offering only the reflection voice never receives run lines.
        var calls = 0
        val reflectionOnly = FakeHttps(URL(config.endpoint), capability = "1")
        assertNull(ReflectionSpeechHttpClient.hosted(config, SpeechVoice.RUN) { calls++; reflectionOnly }
            .synthesize("One."))
        assertEquals(1, calls)
        assertEquals(0, reflectionOnly.sent.size())

        var opened = 0
        assertNull(ReflectionSpeechHttpClient.hosted(config, SpeechVoice.RUN) { opened++; error("Unexpected") }
            .synthesize("x".repeat(501)))
        assertEquals(0, opened)
    }

    @Test fun oldUnconfiguredRedirectedAndUnavailableHealthNeverUploadsText() = runBlocking {
        for ((status, capability) in listOf(200 to null, 200 to "0", 302 to "1", 503 to "1")) {
            var calls = 0
            val connection = FakeHttps(URL(config.endpoint), status = status, capability = capability)
            assertNull(ReflectionSpeechHttpClient.hosted(config) { calls++; connection }.synthesize("Exact"))
            assertEquals(1, calls)
            assertEquals(0, connection.sent.size())
            assertTrue(connection.disconnected)
        }
    }

    @Test fun rejectsEmptyWrongTypeRedirectErrorAndOversizedAudioWithoutReadingErrorBody() = runBlocking {
        val cases = listOf(
            FakeHttps(URL(config.endpoint), byteArrayOf()),
            FakeHttps(URL(config.endpoint), mp3, type = "application/json"),
            FakeHttps(URL(config.endpoint), mp3, status = 302),
            FakeHttps(URL(config.endpoint), mp3, status = 401),
            FakeHttps(URL(config.endpoint), mp3, declaredLength = MAX_REFLECTION_AUDIO_BYTES + 1L),
            FakeHttps(URL(config.endpoint), ByteArray(MAX_REFLECTION_AUDIO_BYTES + 1), declaredLength = -1)
        )
        for (speech in cases) {
            val health = FakeHttps(URL(config.endpoint), capability = "1")
            assertNull(ReflectionSpeechHttpClient.hosted(config) {
                if (it.path == "/health") health else speech
            }.synthesize("Exact"))
            assertTrue(speech.disconnected)
            if (speech.status != 200 || speech.type != "audio/mpeg" ||
                speech.declaredLength > MAX_REFLECTION_AUDIO_BYTES) assertEquals(0, speech.bodyReads)
        }
    }

    @Test fun blankAndOversizedTextNeverOpensAnyConnection() = runBlocking {
        var calls = 0
        val client = ReflectionSpeechHttpClient.hosted(config) { calls++; error("Unexpected") }
        for (text in listOf("", "  ", "x".repeat(4_001))) assertNull(client.synthesize(text))
        assertEquals(0, calls)
    }

    @Test fun transportFailureReturnsNoProviderExceptionOrSecret() = runBlocking {
        assertNull(ReflectionSpeechHttpClient.hosted(config) {
            throw IllegalStateException("secret-bearing transport text")
        }.synthesize("Exact"))
    }

    @Test fun hostedClientRejectsHttpConnectionAndNeverSendsBearer() = runBlocking {
        val http = object : HttpURLConnection(URL("http://reflection.example.com/health")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
        }
        assertNull(ReflectionSpeechHttpClient.hosted(config) { http }.synthesize("Exact"))
        assertNull(http.getRequestProperty("Authorization"))
    }

    @Test fun localEndpointAdmissionIsRestrictedToExistingDebugBridgeHosts() {
        for (url in listOf("http://evil.example:8787/api/reflections/candidates",
            "http://127.0.0.1:8788/api/reflections/candidates",
            "http://127.0.0.1:8787/api/reflections/candidates?token=x")) {
            assertThrows(IllegalArgumentException::class.java) { ReflectionSpeechHttpClient.local(url) }
        }
    }

    @Test fun cancelDisconnectsBlockedAudioReadAndNeverPublishesLateBytes() = runBlocking {
        val entered = CountDownLatch(1)
        val disconnected = CountDownLatch(1)
        val health = FakeHttps(URL(config.endpoint), capability = "1")
        val speech = object : FakeHttps(URL(config.endpoint), mp3) {
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
            override fun getInputStream(): InputStream = object : InputStream() {
                override fun read(): Int {
                    entered.countDown()
                    assertTrue(disconnected.await(5, TimeUnit.SECONDS))
                    return -1
                }
            }
        }
        var published = false
        val attempt = launch {
            ReflectionSpeechHttpClient.hosted(config) {
                if (it.path == "/health") health else speech
            }.synthesize("Exact")
            published = true
        }
        withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        attempt.cancelAndJoin()
        assertTrue(speech.disconnected)
        assertFalse(published)
    }

    private open class FakeHttps(url: URL, private val body: ByteArray = byteArrayOf(),
        val status: Int = 200, private val capability: String? = null,
        val type: String = "audio/mpeg", val declaredLength: Long = body.size.toLong(),
        private val capabilityName: String = "X-RunState-Reflection-Voice"
    ) : HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var disconnected = false
        var bodyReads = 0
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate> = emptyArray()
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = sent
        override fun getInputStream(): InputStream { bodyReads++; return ByteArrayInputStream(body) }
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getContentLengthLong() = declaredLength
        override fun getHeaderField(name: String?): String? =
            if (name.equals(capabilityName, true)) capability else null
    }
}
