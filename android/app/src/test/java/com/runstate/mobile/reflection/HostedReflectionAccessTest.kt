package com.runstate.mobile.reflection

import com.google.gson.JsonParser
import com.runstate.mobile.reflection.ReflectionApiClientTest.FakeHttpsConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HostedReflectionAccessTest {
    private val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates", "a".repeat(43))
    private val runId = "b4412c84-cf52-4856-93ab-65dc635c94bc"
    private fun trial(status: String, remaining: Int) =
        """{"tier":"trial","status":"$status","remaining":$remaining}"""

    @Test fun checkingAnAvailablePreviewNeverStartsItOrSendsRunDetails() = runBlocking {
        val calls = mutableListOf<FakeHttpsConnection>()
        val access = HostedReflectionAccess(config) { url ->
            FakeHttpsConnection(url, trial("available", 2), accessCapability = "1",
                responseContentType = "application/json").also(calls::add)
        }
        assertEquals("available", access.current()?.status)
        assertFalse(access.admit(runId))
        assertEquals(listOf("/health", "/api/reflections/access"), calls.map { it.url.path })
        assertNull(calls.first().getRequestProperty("Authorization"))
        assertEquals("Bearer ${config.accessCode}", calls.last().getRequestProperty("Authorization"))
        assertTrue(calls.all { it.sent.size() == 0 && !it.instanceFollowRedirects })
    }

    @Test fun explicitStartAndRunAdmissionUseOneStableRunIdEvenForTheLastSlot() = runBlocking {
        val calls = mutableListOf<FakeHttpsConnection>()
        val access = HostedReflectionAccess(config) { url ->
            val body = when (url.path) {
                "/api/reflections/access" -> trial("available", 2)
                "/api/reflections/trial" -> trial("active", 2)
                else -> trial("active", 0)
            }
            FakeHttpsConnection(url, body, accessCapability = "1",
                responseContentType = "application/json").also(calls::add)
        }
        assertTrue(access.startTrial())
        assertTrue(access.admit(runId))
        assertTrue(access.admit(runId))
        assertEquals(listOf("/api/reflections/trial", "/api/reflections/run-access", "/api/reflections/run-access"),
            calls.filter { it.requestMethod == "POST" }.map { it.url.path })
        calls.takeLast(2).forEach { assertEquals(runId, JsonParser.parseString(it.sent.toString("UTF-8")).asJsonObject["runId"].asString) }
        assertEquals(0, access.state.value?.remaining)
    }

    @Test fun failedHealthCannotEnableOldServerCompatibilityOrTransmitCredentials() = runBlocking {
        for (status in listOf(302, 401, 503)) {
            val calls = mutableListOf<FakeHttpsConnection>()
            val access = HostedReflectionAccess(config) { url -> FakeHttpsConnection(url, "{}", status).also(calls::add) }
            assertNull(access.current())
            assertFalse(access.admit(runId))
            assertTrue(calls.all { it.url.path == "/health" && it.getRequestProperty("Authorization") == null })
        }
    }

    @Test fun expiredPreviewChecksOnlyRunIdAndUsesReplayOnlyForAnExistingAnswer() = runBlocking {
        for (saved in listOf(true, false)) {
            val calls = mutableListOf<FakeHttpsConnection>()
            val access = HostedReflectionAccess(config) { url ->
                val code = if (url.path.endsWith("run-access") && !saved) 403 else 200
                FakeHttpsConnection(url, trial("expired", 0), status = code, accessCapability = "1",
                    responseContentType = "application/json").also(calls::add)
            }
            assertEquals(if (saved) WrittenAccess.REPLAY_ONLY else null, access.writtenAccess(runId))
            assertEquals(listOf("/health", "/api/reflections/access", "/api/reflections/run-access"),
                calls.map { it.url.path })
            val probe = JsonParser.parseString(calls.last().sent.toString("UTF-8")).asJsonObject
            assertEquals(setOf("runId", "replayOnly"), probe.keySet())
            assertEquals(runId, probe["runId"].asString)
            assertTrue(probe["replayOnly"].asBoolean)
        }
    }

    @Test fun refusedAdmissionRefreshesStatusAndCannotSendAFreshRunAsARetry() = runBlocking {
        var reads = 0
        val posts = mutableListOf<FakeHttpsConnection>()
        val access = HostedReflectionAccess(config) { url ->
            when (url.path) {
                "/health" -> FakeHttpsConnection(url, "{}", accessCapability = "1")
                "/api/reflections/access" -> FakeHttpsConnection(url,
                    if (++reads == 1) trial("active", 1) else trial("expired", 0),
                    responseContentType = "application/json")
                else -> FakeHttpsConnection(url, "{}", status = 403).also(posts::add)
            }
        }
        assertNull(access.writtenAccess(runId))
        assertEquals("expired", access.state.value?.status)
        assertEquals(2, posts.size)
        val bodies = posts.map { JsonParser.parseString(it.sent.toString("UTF-8")).asJsonObject }
        assertEquals(setOf("runId"), bodies.first().keySet())
        assertEquals(setOf("runId", "replayOnly"), bodies.last().keySet())
    }

    @Test fun unknownVersionAndSuccessfulNonHealthPageCannotGrantOwnerAccess() = runBlocking {
        for (header in listOf(null, "2")) {
            val calls = mutableListOf<FakeHttpsConnection>()
            val access = HostedReflectionAccess(config) { url ->
                FakeHttpsConnection(url, "<html>Sign in</html>", accessCapability = header).also(calls::add)
            }
            assertNull(access.current())
            assertTrue(calls.all { it.getRequestProperty("Authorization") == null && it.sent.size() == 0 })
        }
    }

    @Test fun healthyOlderServerKeepsOwnerAccessWithoutPostingAnAdmission() = runBlocking {
        val access = HostedReflectionAccess(config) { url ->
            assertEquals("/health", url.path)
            FakeHttpsConnection(url, """{"status":"ok"}""")
        }
        assertEquals(ReflectionTier.OWNER, access.current()?.tier)
        assertTrue(access.admit(runId))
        assertFalse(access.startTrial())
    }

    @Test fun authorizedPreparationCarriesRunIdOnlyOnItsPaidPath() = runBlocking {
        val access = HostedReflectionAccess(config) { url -> FakeHttpsConnection(url,
            trial("active", 1), accessCapability = "1", responseContentType = "application/json") }
        val calls = mutableListOf<FakeHttpsConnection>()
        val client = HostedSongStudyPreparationClient(config, access) { url -> FakeHttpsConnection(url,
            """{"status":"ready"}""", preparationCapability = "1",
            responseContentType = "application/json").also(calls::add) }
        assertTrue(client.supports())
        assertTrue(client.admit(runId))
        client.prepare("16ba9def-ab85-4368-9929-a40a3a0ccb75", "Title", "Artist")
        assertNull(calls.first().getRequestProperty("X-RunState-Run-Id"))
        assertEquals(runId, calls.last().getRequestProperty("X-RunState-Run-Id"))
    }

    @Test fun changingToTrackingEndsSongPreparationForAnAlreadyAdmittedRun() = runBlocking {
        var body = trial("active", 1)
        val access = HostedReflectionAccess(config) { url -> FakeHttpsConnection(url,
            body, accessCapability = "1", responseContentType = "application/json") }
        val client = HostedSongStudyPreparationClient(config, access)
        assertTrue(client.admit(runId))
        body = """{"tier":"tracking","status":"tracking","remaining":0}"""
        access.refresh()
        assertFalse(client.admit(runId))
    }
}
