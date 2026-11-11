package com.runstate.mobile.reflection

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.net.URL
import java.time.Instant
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal enum class ReflectionTier { OWNER, TRACKING, TRIAL, PILOT }
internal enum class WrittenAccess { GENERATE_OR_REPLAY, REPLAY_ONLY }

internal data class ReflectionAccessInfo(val tier: ReflectionTier, val status: String,
    val remaining: Int? = null, val expiresAt: Instant? = null) {
    val active: Boolean get() = status == "active" && expiresAt?.isAfter(Instant.now()) != false
    val allowsNewRun: Boolean get() = tier == ReflectionTier.OWNER || (active && (remaining ?: 0) > 0)
}

/** One instance belongs to one saved server and credential. Only the server grants an allowance. */
internal class HostedReflectionAccess(
    private val config: ReflectionConnectionConfig,
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection }
) {
    private val lock = Mutex()
    private val info = MutableStateFlow<ReflectionAccessInfo?>(null)
    val state = info.asStateFlow()
    private var supportsAccess: Boolean? = null
    private var lastCheckNanos = 0L

    suspend fun refresh(): ReflectionAccessInfo? = lock.withLock { loadLocked(probe = true) }

    suspend fun startTrial(): Boolean = lock.withLock {
        val current = loadLocked()
        if (current?.tier != ReflectionTier.TRIAL || current.status != "available") return@withLock false
        val started = exchange("/api/reflections/trial", "{}")
        remember(started)
        started?.status == "active"
    }

    suspend fun current(): ReflectionAccessInfo? = lock.withLock { currentLocked() }

    private suspend fun currentLocked(): ReflectionAccessInfo? {
        val cached = info.value
        return if (cached == null || (cached.status == "active" && !cached.active) ||
            System.nanoTime() - lastCheckNanos >= 300_000_000_000L) loadLocked(probe = true)
        else cached
    }

    private fun remember(next: ReflectionAccessInfo?) {
        lastCheckNanos = System.nanoTime()
        info.value = next
    }

    /** Repeating admission for the same run after a phone restart is safe on the server. */
    suspend fun admit(runId: String): Boolean = lock.withLock {
        val current = currentLocked()
        if (current == null || !validRunId(runId)) return@withLock false
        if (current.tier == ReflectionTier.OWNER) return@withLock true
        if (!current.active) return@withLock false
        val granted = exchange("/api/reflections/run-access", Gson().toJson(mapOf("runId" to runId)))
        if (granted == null) loadLocked() else remember(granted)
        granted?.active == true
    }

    suspend fun writtenAccess(runId: String): WrittenAccess? = when {
        admit(runId) -> WrittenAccess.GENERATE_OR_REPLAY
        hasWrittenReplay(runId) -> WrittenAccess.REPLAY_ONLY
        else -> null
    }

    /** Probe with an opaque run ID before sharing saved run details for an otherwise ineligible retry. */
    private suspend fun hasWrittenReplay(runId: String): Boolean = lock.withLock {
        if (!validRunId(runId) || currentLocked() == null || supportsAccess != true) return@withLock false
        val saved = exchange("/api/reflections/run-access",
            Gson().toJson(mapOf("runId" to runId, "replayOnly" to true))) ?: return@withLock false
        remember(saved)
        true
    }

    private suspend fun loadLocked(probe: Boolean = false): ReflectionAccessInfo? {
        if (probe || supportsAccess == null) {
            supportsAccess = request("/health", null, authenticated = false) {
                when (it.getHeaderField("X-RunState-Reflection-Access")) {
                    "1" -> true
                    null -> {
                        val body = it.inputStream.use { stream -> readBoundedUtf8(stream, true) }
                        require(JsonParser.parseString(body).asJsonObject.get("status")?.asString == "ok")
                        false
                    }
                    else -> error("Unknown reflection access protocol.")
                }
            }
        }
        // A definite successful health response without the header is a compatible older server.
        val next = when (supportsAccess) {
            true -> exchange("/api/reflections/access", null)
            false -> ReflectionAccessInfo(ReflectionTier.OWNER, "owner")
            null -> null
        }
        remember(next)
        return next
    }

    private suspend fun exchange(path: String, json: String?): ReflectionAccessInfo? =
        request(path, json, authenticated = true) { connection ->
            require(connection.contentType?.substringBefore(';')?.trim()?.equals("application/json", true) == true)
            val body = connection.inputStream.use { readBoundedUtf8(it, true) }
            val value = JsonParser.parseString(body).asJsonObject
            val tier = ReflectionTier.valueOf(value.get("tier").asString.uppercase(java.util.Locale.ROOT))
            val status = value.get("status").asString
            val remaining = value.get("remaining")?.asString?.toInt()
            require(remaining == null || remaining >= 0)
            require(when (tier) {
                ReflectionTier.OWNER -> status == "owner"
                ReflectionTier.TRACKING -> status == "tracking" && remaining == 0
                ReflectionTier.TRIAL -> status in setOf("available", "active", "expired") && remaining != null
                ReflectionTier.PILOT -> status == "active" && remaining != null
            })
            ReflectionAccessInfo(tier, status, remaining, value.get("expiresAt")?.asString?.let(Instant::parse))
        }

    private suspend fun <T> request(path: String, json: String?, authenticated: Boolean,
        read: (HttpsURLConnection) -> T): T? = withContext(Dispatchers.IO) {
        var connection: HttpsURLConnection? = null
        try {
            val http = open(URL(URL(config.endpoint), path))
            connection = http
            http.instanceFollowRedirects = false
            http.connectTimeout = 3_000
            http.readTimeout = 4_000
            http.useCaches = false
            http.setRequestProperty("Accept", "application/json")
            if (authenticated) http.setRequestProperty("Authorization", "Bearer ${config.accessCode}")
            if (json != null) {
                http.requestMethod = "POST"
                http.doOutput = true
                http.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                val bytes = json.toByteArray(Charsets.UTF_8)
                http.setFixedLengthStreamingMode(bytes.size)
                http.outputStream.use { it.write(bytes) }
            }
            if (http.responseCode == 200) read(http) else null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        finally { connection?.disconnect() }
    }

    private fun validRunId(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}
