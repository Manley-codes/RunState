package com.runstate.mobile.reflection

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.GsonBuilder
import com.runstate.mobile.data.local.*
import com.runstate.mobile.weather.RunWeatherSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReflectionApiClientTest {
    private companion object {
        const val REQUEST_ID = "c4e1b8a2-7d35-4f61-8b0c-2a9e6d4f13b7"
        const val RUN_ID = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
        const val START = 1_780_000_000_000L
        const val PAUSE = START + 65_432L
        const val FINISH = START + 90_123L
    }

    @Test fun mapsRawSavedFactsAndUnknownUncapturedInputs() {
        val request = reflectionRequestFromSavedRun(
            REQUEST_ID,
            completedRun(),
            listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
        )

        assertEquals(REQUEST_ID, request.requestId)
        assertEquals(RUN_ID, request.runId)
        with(request.evidence) {
            assertEquals(START, officialStartEpochMillis)
            assertEquals(FINISH, finishEpochMillis)
            assertEquals("America/Chicago", startTimezoneId)
            assertEquals(65_432L, activeDurationMillis)
            assertEquals(180.25, metrics.distanceMeters!!, 0.0)
            assertEquals("FIXTURE", metrics.source)
            assertEquals("COMPLETE", metrics.coverage)
            assertEquals("MILES", metrics.displayUnit)
            assertEquals("UNKNOWN", preRunEnergy)
            assertEquals("UNKNOWN", musicContext.state)
            assertNull(musicContext.trackTitle)
        }
    }

    @Test fun incompleteHistoryAndAbsentMetricsStayUnknown() {
        val run = completedRun().copy(
            finalDistanceMeters = null,
            metricSource = null,
            telemetryCoverage = null,
            displayDistanceUnit = null,
            transitionHistoryComplete = null
        )
        val evidence = reflectionRequestFromSavedRun(REQUEST_ID, run, emptyList()).evidence

        assertNull(evidence.activeDurationMillis)
        assertEquals(
            ReflectionMetricsEvidence(null, null, null, null),
            evidence.metrics
        )
    }

    @Test fun incompleteGpsCoverageDoesNotInventActiveTimeFromCompleteTransitions() {
        for (coverage in listOf(TelemetryCoverage.PARTIAL, TelemetryCoverage.UNAVAILABLE)) {
            val run = completedRun().copy(
                trackingMode = RunTrackingMode.GPS,
                metricSource = MetricSource.GPS,
                telemetryCoverage = coverage,
                finalDistanceMeters = if (coverage == TelemetryCoverage.PARTIAL) 180.25 else null
            )
            val evidence = reflectionRequestFromSavedRun(
                REQUEST_ID, run,
                listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
            ).evidence

            assertNull(evidence.activeDurationMillis)
            assertEquals(START, evidence.officialStartEpochMillis)
            assertEquals(FINISH, evidence.finishEpochMillis)
            assertEquals("GPS", evidence.metrics.source)
            assertEquals(coverage.name, evidence.metrics.coverage)
            assertEquals(run.finalDistanceMeters, evidence.metrics.distanceMeters)
        }
    }

    @Test fun completeGpsCoveragePreservesKnownActiveTiming() {
        val run = completedRun().copy(
            trackingMode = RunTrackingMode.GPS,
            metricSource = MetricSource.GPS
        )
        val evidence = reflectionRequestFromSavedRun(
            REQUEST_ID, run,
            listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
        ).evidence

        assertEquals(65_432L, evidence.activeDurationMillis)
        assertEquals(180.25, evidence.metrics.distanceMeters!!, 0.0)
        assertEquals("COMPLETE", evidence.metrics.coverage)
    }

    @Test fun chosenPreRunEnergyComesFromTheSavedRun() {
        val evidence = reflectionRequestFromSavedRun(
            REQUEST_ID,
            completedRun().copy(preRunEnergy = PreRunEnergy.MODERATE),
            listOf(RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE))
        ).evidence

        assertEquals("MODERATE", evidence.preRunEnergy)
    }

    @Test fun savedWeatherMustBelongToTheOfficialStartRatherThanAnotherTimeInTheHour() {
        val weather = weatherSnapshot()
        val request = reflectionRequestFromSavedRun(
            REQUEST_ID, completedRun(), completedTransitions(), weather = weather
        )
        assertEquals(weather, request.evidence.weatherContext)
        assertThrows(IllegalArgumentException::class.java) {
            reflectionRequestFromSavedRun(
                REQUEST_ID, completedRun(), completedTransitions(),
                weather = weather.copy(targetEpochMillis = START + 1)
            )
        }
    }

    @Test fun localCaptureDoesNotAuthorizeCloudMusicSharing() {
        val observed = listOf(musicObservation())
        listOf(
            completedRun(), // Legacy and untouched new runs both default to no sharing.
            completedRun().copy(musicObservationRequested = true)
        ).forEach { run ->
            assertEquals(
                ReflectionMusicContext(),
                reflectionRequestFromSavedRun(REQUEST_ID, run, completedTransitions(), observed).evidence.musicContext
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            completedRun().copy(musicReflectionSharingAllowed = true)
        }
    }

    @Test fun approvedMusicUsesOnlyTheLatestUsableSameRunSpotifyReport() {
        val latest = musicObservation(sequence = 3).copy(
            title = "  Latest observed title  ", artist = "  Reported artist  "
        )
        val observations = listOf(
            latest,
            musicObservation(sequence = 4).copy(playbackState = MusicPlaybackState.PAUSED),
            musicObservation(sequence = 5).copy(title = null),
            musicObservation(sequence = 6).copy(sourcePackage = "other.player"),
            musicObservation(sequence = 7).copy(observedAtEpochMillis = FINISH + 1),
            musicObservation(sequence = 1).copy(title = "Earlier title")
        )
        val context = reflectionRequestFromSavedRun(
            REQUEST_ID, sharingRun(), completedTransitions(), observations
        ).evidence.musicContext

        assertEquals(ReflectionMusicContext(
            state = "MUSIC", trackTitle = latest.title, artistName = latest.artist,
            provenance = "MEDIA_SESSION_PARTIAL"
        ), context)
        assertNull(context.artistDescription)
    }

    @Test fun missingOrIneligibleObservedMusicStaysUnknownRatherThanNoMusic() {
        val wrongRun = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"
        val ineligible = listOf(
            musicObservation().copy(runId = wrongRun),
            musicObservation().copy(sourcePackage = "other.player"),
            musicObservation().copy(title = null),
            musicObservation().copy(title = "  \t"),
            musicObservation().copy(artist = null),
            musicObservation().copy(artist = "  \t"),
            musicObservation().copy(observedAtEpochMillis = START - 1),
            musicObservation().copy(observedAtEpochMillis = FINISH + 1)
        ) + MusicPlaybackState.entries.filter { it != MusicPlaybackState.PLAYING }.map { state ->
            musicObservation().copy(playbackState = state)
        }
        (listOf(emptyList<RunMusicObservationEntity>()) + ineligible.map { listOf(it) }).forEach { observations ->
            assertEquals(
                ReflectionMusicContext(),
                reflectionRequestFromSavedRun(
                    REQUEST_ID, sharingRun(), completedTransitions(), observations
                ).evidence.musicContext
            )
        }
    }

    @Test fun observedMusicPayloadContainsOnlyApprovedTitleArtistAndProvenance() {
        val request = reflectionRequestFromSavedRun(
            REQUEST_ID, sharingRun(), completedTransitions(), listOf(musicObservation())
        )
        val gson = GsonBuilder().serializeNulls().create()
        val json = gson.toJsonTree(request).asJsonObject
        val music = json.getAsJsonObject("evidence").getAsJsonObject("musicContext")
        assertEquals(setOf("state", "trackTitle", "artistName", "artistDescription", "provenance", "songCue"), music.keySet())
        assertTrue(music.get("songCue").isJsonNull)
        assertEquals("Reported title", music.get("trackTitle").asString)
        assertEquals("Reported artist", music.get("artistName").asString)
        assertEquals("MEDIA_SESSION_PARTIAL", music.get("provenance").asString)
        assertTrue(music.get("artistDescription").isJsonNull)
        val serialized = gson.toJson(request)
        listOf("sourcePackage", "observedAtEpochMillis", "elapsedRealtimeNanos", "eventKind",
            "observationKey", "sequenceNumber", "com.spotify.music").forEach { privateField ->
            assertFalse("Raw music field leaked: $privateField", serialized.contains(privateField))
        }
        val controlled = request.copy(evidence = request.evidence.copy(musicContext = ReflectionMusicContext(
            state = "MUSIC", trackTitle = "Controlled title", artistName = "Controlled artist",
            provenance = "CONTROLLED"
        )))
        assertEquals("CONTROLLED", gson.toJsonTree(controlled).asJsonObject
            .getAsJsonObject("evidence").getAsJsonObject("musicContext").get("provenance").asString)
    }

    @Test fun responsePreservesExactTextAndRejectsUntrustworthyAnswers() {
        val exact = "  [FAKE] Exact no-selection candidate.  "
        val parsed = parseReflectionResponse(response(noSelection = exact), REQUEST_ID, RUN_ID)
        assertEquals(exact, parsed.noSelection)
        assertTrue(parsed.isTestData)

        // The mode comes from the server field, never from wording inside a candidate.
        val live = parseReflectionResponse(
            response(generationMode = "ANTHROPIC", noSelection = exact), REQUEST_ID, RUN_ID
        )
        assertEquals(exact, live.noSelection)
        assertFalse(live.isTestData)

        val invalid = listOf(
            response(requestId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
            response(runId = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d"),
            response(includeNoSelection = false),
            response(noSelection = "   "),
            response(includeExtra = true),
            response(includeGenerationMode = false),
            response(generationMode = "MOCK"),
            response().replace("\"generationMode\":\"FAKE\"", "\"generationMode\":7"),
            response().replace("\"spent\":\"[FAKE] Spent.\"", "\"spent\":7"),
            response().replace("\"requestId\":", "\"requestId\":\"duplicate\",\"requestId\":"),
            response().replace("\"spent\":", "\"spent\":\"duplicate\",\"spent\":"),
            response() + "{}",
            "/* comment */" + response()
        )
        invalid.forEach {
            assertThrows(ReflectionBridgeException::class.java) {
                parseReflectionResponse(it, REQUEST_ID, RUN_ID)
            }
        }
    }

    @Test fun debugBridgeAcceptsOnlyEmulatorOrAdbReverseLoopback() {
        LocalReflectionHttpClient(LOCAL_REFLECTION_ENDPOINT)
        LocalReflectionHttpClient(PHONE_REFLECTION_ENDPOINT)
        listOf(
            "http://10.0.0.2:8787/api/reflections/candidates",
            "http://127.0.0.1:8788/api/reflections/candidates",
            "http://127.0.0.1:8787/api/reflections/candidates?leak=true"
        ).forEach { endpoint ->
            assertThrows(IllegalArgumentException::class.java) {
                LocalReflectionHttpClient(endpoint)
            }
        }
    }

    @Test fun hostedSettingsAcceptOnlyExactHttpsDnsEndpointAndOpaqueCode() {
        val valid = "https://reflection.example.com/api/reflections/candidates"
        ReflectionConnectionConfig(valid, "A".repeat(43))
        ReflectionConnectionConfig(
            "https://reflection.example.com:443/api/reflections/candidates", "a_-".repeat(20)
        )
        listOf(
            "http://reflection.example.com/api/reflections/candidates",
            "https://127.0.0.1/api/reflections/candidates",
            "https://localhost/api/reflections/candidates",
            "https://user@reflection.example.com/api/reflections/candidates",
            "https://reflection.example.com:8443/api/reflections/candidates",
            "https://reflection.example.com/api/reflections/candidates?debug=1",
            "https://reflection.example.com/api/reflections/candidates#fragment",
            "https://reflection.example.com/api/reflections/%63andidates",
            "https://reflection.example.com/other"
        ).forEach { address ->
            assertThrows(IllegalArgumentException::class.java) {
                ReflectionConnectionConfig(address, "A".repeat(43))
            }
        }
        listOf("short", "A".repeat(129), "sk-ant-" + "A".repeat(43), "A".repeat(42) + "+")
            .forEach { code ->
                assertThrows(IllegalArgumentException::class.java) {
                    ReflectionConnectionConfig(valid, code)
                }
            }
    }

    @Test fun hostedSettingsNeverPrintAddressOrAccessCode() {
        val address = "https://reflection.example.com/api/reflections/candidates"
        val code = "S".repeat(43)
        val printed = ReflectionConnectionConfig(address, code).toString()
        assertFalse(printed.contains(address))
        assertFalse(printed.contains(code))
    }

    @Test fun hostedClientSendsBearerOverHttpsWithExistingRequestContract() = runBlocking {
        val code = "K".repeat(43)
        val config = ReflectionConnectionConfig(
            "https://reflection.example.com/api/reflections/candidates", code
        )
        val fake = FakeHttpsConnection(URL(config.endpoint), response(generationMode = "ANTHROPIC"))
        val result = HostedReflectionHttpClient(config, { fake }).request(
            reflectionRequestFromSavedRun(REQUEST_ID, completedRun(), completedTransitions())
        )
        assertEquals("Bearer $code", fake.getRequestProperty("Authorization"))
        assertEquals("POST", fake.requestMethod)
        assertEquals(3_000, fake.connectTimeout)
        assertEquals(100_000, fake.readTimeout)
        assertFalse(fake.instanceFollowRedirects)
        assertTrue(fake.sent.toString(StandardCharsets.UTF_8.name()).contains(REQUEST_ID))
        val body = JsonParser.parseString(fake.sent.toString(StandardCharsets.UTF_8.name())).asJsonObject
        assertEquals(setOf("officialStartEpochMillis", "finishEpochMillis", "startTimezoneId",
            "activeDurationMillis", "metrics", "preRunEnergy", "musicContext"),
            body.getAsJsonObject("evidence").keySet())
        assertFalse(body.getAsJsonObject("evidence").has("weatherContext"))
        assertEquals("[FAKE] None.", result.noSelection)
        assertFalse(result.isTestData)
        assertTrue(fake.wasDisconnected)
    }

    @Test fun hostedClientSendsExactKnownWeatherWithoutLookupCoordinates() = runBlocking {
        val config = ReflectionConnectionConfig(
            "https://reflection.example.com/api/reflections/candidates", "W".repeat(43)
        )
        val fake = FakeHttpsConnection(URL(config.endpoint), response(generationMode = "ANTHROPIC"))
        val snapshot = weatherSnapshot()
        HostedReflectionHttpClient(config, { fake }).request(reflectionRequestFromSavedRun(
            REQUEST_ID, completedRun(), completedTransitions(), weather = snapshot
        ))
        val serialized = fake.sent.toString(StandardCharsets.UTF_8.name())
        val weather = JsonParser.parseString(serialized).asJsonObject.getAsJsonObject("evidence")
            .getAsJsonObject("weatherContext")
        assertEquals(setOf("source", "resolution", "targetEpochMillis", "validFromEpochMillis",
            "validToEpochMillis", "fetchedAtEpochMillis", "temperatureCelsius",
            "apparentTemperatureCelsius", "relativeHumidityPercent", "windSpeedKmh", "weatherCode"),
            weather.keySet())
        assertEquals(snapshot, RunWeatherSnapshot.fromJson(weather.toString()))
        assertEquals(START, weather.get("targetEpochMillis").asLong)
        assertEquals(FINISH, weather.get("fetchedAtEpochMillis").asLong)
        assertEquals(48.5, weather.get("relativeHumidityPercent").asDouble, 0.0)
        assertFalse(serialized.contains("latitude"))
        assertFalse(serialized.contains("longitude"))
        assertTrue(fake.wasDisconnected)
    }

    private fun weatherSnapshot(): RunWeatherSnapshot {
        val hour = START / 3_600_000L * 3_600_000L
        return RunWeatherSnapshot(targetEpochMillis = START, validFromEpochMillis = hour,
            validToEpochMillis = hour + 3_600_000L, fetchedAtEpochMillis = FINISH,
            temperatureCelsius = 21.5, apparentTemperatureCelsius = 20.0,
            relativeHumidityPercent = 48.5, windSpeedKmh = 8.0, weatherCode = 0)
    }

    @Test fun hostedClientRejectsRedirectHttpDowngradeAndOversizeResponse() = runBlocking {
        val config = ReflectionConnectionConfig(
            "https://reflection.example.com/api/reflections/candidates", "Q".repeat(43)
        )
        val request = reflectionRequestFromSavedRun(REQUEST_ID, completedRun(), completedTransitions())
        val redirected = FakeHttpsConnection(URL(config.endpoint), "", 302)
        assertThrows(ReflectionBridgeException::class.java) {
            runBlocking { HostedReflectionHttpClient(config, { redirected }).request(request) }
        }
        assertFalse(redirected.instanceFollowRedirects)
        assertTrue(redirected.wasDisconnected)

        val http = object : HttpURLConnection(URL("http://reflection.example.com")) {
            override fun connect() = Unit
            override fun disconnect() = Unit
            override fun usingProxy() = false
        }
        assertThrows(ReflectionBridgeException::class.java) {
            runBlocking { HostedReflectionHttpClient(config, { http }).request(request) }
        }
        val huge = FakeHttpsConnection(URL(config.endpoint), "x".repeat(65_537))
        assertThrows(ReflectionBridgeException::class.java) {
            runBlocking { HostedReflectionHttpClient(config, { huge }).request(request) }
        }
        assertTrue(huge.wasDisconnected)

        val malformed = FakeHttpsConnection(URL(config.endpoint), "{bad json")
        val invalid = assertThrows(ReflectionBridgeException::class.java) {
            runBlocking { HostedReflectionHttpClient(config, { malformed }).request(request) }
        }
        assertEquals("The hosted reflection response was invalid.", invalid.message)
        // Coroutine stack recovery can add a copy of our sanitized exception as its cause.
        assertTrue(generateSequence(invalid as Throwable) { it.cause }.all {
            it.message == "The hosted reflection response was invalid."
        })

        val unavailable = assertThrows(ReflectionBridgeException::class.java) {
            runBlocking {
                HostedReflectionHttpClient(config, { throw IllegalStateException(config.accessCode) })
                    .request(request)
            }
        }
        assertEquals("The hosted reflection service is unavailable.", unavailable.message)
        assertTrue(generateSequence(unavailable as Throwable) { it.cause }.all {
            it.message == "The hosted reflection service is unavailable."
        })
    }

    @Test fun hostedOldServerOmitsSongCueAndNeverReceivesLocalPassageInstructions() = runBlocking {
        val request = knownSongRequest()
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{\"status\":\"ok\"}")
        val post = FakeHttpsConnection(URL(config.endpoint), response(generationMode = "ANTHROPIC"))
        val result = HostedReflectionHttpClient(config, { if (it.path == "/health") health else post }).request(request)
        val sent = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject.getAsJsonObject("evidence")
        assertNull(result.songCueId)
        assertFalse(sent.has("songPassage"))
        assertFalse(sent.getAsJsonObject("musicContext").has("songCue"))
        assertNull(health.getRequestProperty("Authorization"))
        assertTrue(health.wasDisconnected)
    }

    @Test fun capableServerAcknowledgesExactSongAndReceivesOnlyEditorialCueFields() = runBlocking {
        val request = knownSongRequest()
        val cue = requireNotNull(request.evidence.musicContext.songCue)
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        val reply = JsonParser.parseString(response(generationMode = "ANTHROPIC")).asJsonObject.apply {
            addProperty("songCueId", cue.cueId)
        }.toString()
        val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{}", cueCapability = "1")
        val post = FakeHttpsConnection(URL(config.endpoint), reply)
        val result = HostedReflectionHttpClient(config, { if (it.path == "/health") health else post }).request(request)
        val sent = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject.getAsJsonObject("evidence")
        assertEquals(cue.cueId, result.songCueId)
        assertFalse(sent.has("songPassage"))
        assertEquals(setOf("cueId", "source", "theme", "connectionNote"),
            sent.getAsJsonObject("musicContext").getAsJsonObject("songCue").keySet())
        assertFalse(post.sent.toString("UTF-8").contains("startPositionMillis"))
        assertFalse(post.sent.toString("UTF-8").contains("spotify:track"))
    }

    @Test fun songAcknowledgementCanBeAbsentForFallbackButNeverForeignOrUnrequested() {
        val reply = JsonParser.parseString(response()).asJsonObject
        assertNull(parseReflectionResponse(reply.toString(), REQUEST_ID, RUN_ID, "expected").songCueId)
        reply.addProperty("songCueId", "foreign")
        assertThrows(ReflectionBridgeException::class.java) {
            parseReflectionResponse(reply.toString(), REQUEST_ID, RUN_ID, "expected")
        }
        assertThrows(ReflectionBridgeException::class.java) {
            parseReflectionResponse(reply.toString(), REQUEST_ID, RUN_ID)
        }
    }

    @Test fun legacyCueCapabilityCannotAuthorizeTheNewStudyWriterPairings() = runBlocking {
        val request = acceptedSongRequest()
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{}", cueCapability = "1")
        val post = FakeHttpsConnection(URL(config.endpoint), response(generationMode = "ANTHROPIC"))
        val result = HostedReflectionHttpClient(config, { if (it.path == "/health") health else post }).request(request)
        val sent = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject.getAsJsonObject("evidence")
        assertFalse(sent.getAsJsonObject("musicContext").has("songCue"))
        assertFalse(sent.has("songPassage"))
        assertNull(result.songCueId)
    }

    @Test fun studyWriterCapabilitySendsFrozenCueAndAllowsHonestUnpairedSourceFallback() = runBlocking {
        val request = acceptedSongRequest()
        val cueId = requireNotNull(request.evidence.musicContext.songCue).cueId
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        for (acknowledge in listOf(false, true)) {
            val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{}",
                cueCapability = "1", studyCapability = "1")
            val body = JsonParser.parseString(response(generationMode = "ANTHROPIC")).asJsonObject.apply {
                if (acknowledge) addProperty("songCueId", cueId)
            }.toString()
            val post = FakeHttpsConnection(URL(config.endpoint), body,
                studyCapability = if (acknowledge) "1" else null)
            val result = HostedReflectionHttpClient(config, { if (it.path == "/health") health else post }).request(request)
            val sent = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject.getAsJsonObject("evidence")
            assertEquals(cueId, sent.getAsJsonObject("musicContext").getAsJsonObject("songCue").get("cueId").asString)
            assertEquals(if (acknowledge) cueId else null, result.songCueId)
            assertFalse(sent.has("songPassage"))
            assertFalse(post.sent.toString("UTF-8").contains("songStudyVersion"))
            assertNull(health.getRequestProperty("Authorization"))
            assertEquals(100_000, post.readTimeout)
        }
    }

    @Test fun retryCanDiscoverStudyWriterAfterAnOlderServerDeploysWithoutChangingEvidence() = runBlocking {
        val request = acceptedSongRequest()
        val cueId = requireNotNull(request.evidence.musicContext.songCue).cueId
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        var studyVersion: String? = null
        val posts = mutableListOf<FakeHttpsConnection>()
        val client = HostedReflectionHttpClient(config, { url ->
            if (url.path == "/health") FakeHttpsConnection(url, "{}", cueCapability = "1",
                studyCapability = studyVersion)
            else FakeHttpsConnection(url, JsonParser.parseString(response(generationMode = "ANTHROPIC"))
                .asJsonObject.apply { if (studyVersion != null) addProperty("songCueId", cueId) }.toString(),
                studyCapability = studyVersion)
                .also { posts += it }
        })
        assertNull(client.request(request).songCueId)
        studyVersion = "1"
        assertEquals(cueId, client.request(request).songCueId)
        val music = posts.map { JsonParser.parseString(it.sent.toString("UTF-8")).asJsonObject
            .getAsJsonObject("evidence").getAsJsonObject("musicContext") }
        assertFalse(music[0].has("songCue"))
        assertEquals(cueId, music[1].getAsJsonObject("songCue").get("cueId").asString)
    }

    @Test fun cachedCapabilityCannotAuthorizeAnOlderServerThatOnlyEchoesTheCue() = runBlocking {
        val request = acceptedSongRequest()
        val cueId = requireNotNull(request.evidence.musicContext.songCue).cueId
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates",
            "K".repeat(43))
        val body = JsonParser.parseString(response(generationMode = "ANTHROPIC")).asJsonObject.apply {
            addProperty("songCueId", cueId)
        }.toString()
        var healthCalls = 0
        var postCalls = 0
        val client = HostedReflectionHttpClient(config, { url ->
            if (url.path == "/health") {
                healthCalls++
                FakeHttpsConnection(url, "{}", cueCapability = "1", studyCapability = "1")
            } else FakeHttpsConnection(url, body,
                studyCapability = if (++postCalls == 1) "1" else null)
        })
        assertEquals(cueId, client.request(request).songCueId)
        val afterRollback = client.request(request)
        assertEquals(1, healthCalls)
        assertNull(afterRollback.songCueId)
        assertEquals("[FAKE] None.", afterRollback.noSelection)
    }

    private fun acceptedSongRequest() = reflectionRequestFromSavedRun(REQUEST_ID, sharingRun(),
        completedTransitions(), listOf(musicObservation().copy(title = "Lovely Day", artist = "Bill Withers")))

    private fun knownSongRequest() = reflectionRequestFromSavedRun(REQUEST_ID, sharingRun(),
        completedTransitions(), listOf(musicObservation().copy(title = "Sunday Best", artist = "Surfaces")))

    private class FakeHttpsConnection(url: URL, private val body: String, private val status: Int = 200,
        private val cueCapability: String? = null,
        private val studyCapability: String? = null,
        private val preparationCapability: String? = null,
        private val responseContentType: String? = null) :
        HttpsURLConnection(url) {
        val sent = ByteArrayOutputStream()
        var wasDisconnected = false
        override fun connect() = Unit
        override fun disconnect() { wasDisconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite(): String = "TLS_AES_128_GCM_SHA256"
        override fun getLocalCertificates(): Array<Certificate> = emptyArray()
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getOutputStream() = sent
        override fun getInputStream(): InputStream =
            ByteArrayInputStream(body.toByteArray(StandardCharsets.UTF_8))
        override fun getResponseCode(): Int = status
        override fun getHeaderField(name: String?): String? = when {
            name.equals("X-RunState-Song-Cue", ignoreCase = true) -> cueCapability
            name.equals("X-RunState-Song-Study", ignoreCase = true) -> studyCapability
            name.equals("X-RunState-Song-Preparation", ignoreCase = true) -> preparationCapability
            name.equals("Content-Type", ignoreCase = true) -> responseContentType
            else -> null
        }
    }

    @Test fun earlyPreparationSendsOneIdentityOnlyAndChecksActualVersionedResponse() = runBlocking {
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates", "a".repeat(43))
        val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{}", preparationCapability = "1")
        val post = FakeHttpsConnection(URL("https://reflection.example.com/api/reflections/song-study"),
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"READY\",\"songStudyVersion\":1}",
            responseContentType = "application/json")
        val client = HostedSongStudyPreparationClient(config) { if (it.path == "/health") health else post }
        assertTrue(client.supports())
        assertTrue(client.prepare(REQUEST_ID, "Good as Hell", "Lizzo"))
        val body = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject
        assertEquals(setOf("preparationId", "trackTitle", "artistName"), body.keySet())
        assertEquals("Good as Hell", body["trackTitle"].asString)
        assertEquals("Lizzo", body["artistName"].asString)
        assertEquals("Bearer ${config.accessCode}", post.getRequestProperty("Authorization"))
        assertNull(health.getRequestProperty("Authorization"))
        assertTrue(post.wasDisconnected && health.wasDisconnected)
        assertFalse(post.instanceFollowRedirects)
    }

    @Test fun earlyPreparationRejectsUnmatchedDuplicateExtraOrUnavailableResponses() = runBlocking {
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates", "a".repeat(43))
        val bodies = listOf(
            "{\"preparationId\":\"$RUN_ID\",\"state\":\"READY\",\"songStudyVersion\":1}",
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"UNAVAILABLE\",\"songStudyVersion\":1}",
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"READY\",\"songStudyVersion\":2}",
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"READY\",\"state\":\"READY\",\"songStudyVersion\":1}",
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"READY\",\"songStudyVersion\":1,\"extra\":true}",
            "{\"preparationId\":\"$REQUEST_ID\",\"state\":\"READY\",\"songStudyVersion\":1.0}",
            "not JSON")
        for (body in bodies) {
            val post = FakeHttpsConnection(URL(config.endpoint), body, responseContentType = "application/json")
            assertFalse(HostedSongStudyPreparationClient(config) { post }.prepare(REQUEST_ID, "Title", "Artist"))
            assertTrue(post.wasDisconnected)
        }
        val unsupported = FakeHttpsConnection(URL(config.endpoint), "{}")
        assertFalse(HostedSongStudyPreparationClient(config) { unsupported }.supports())
    }

    @Test fun finalPreparationReferenceIsLocalBoundAndOnlyVersionedTopLevelWireMetadata() = runBlocking {
        val config = ReflectionConnectionConfig("https://reflection.example.com/api/reflections/candidates", "a".repeat(43))
        val original = acceptedSongRequest()
        val reference = SongPreparationReference(REQUEST_ID, reflectionDestinationFingerprint(config))
        val request = original.copy(evidence = original.evidence.copy(songPreparationReference = reference))
        for (supported in listOf(false, true)) {
            val health = FakeHttpsConnection(URL("https://reflection.example.com/health"), "{}", cueCapability = "1",
                studyCapability = "1", preparationCapability = if (supported) "1" else null)
            val post = FakeHttpsConnection(URL(config.endpoint), response(generationMode = "ANTHROPIC"))
            HostedReflectionHttpClient(config, { if (it.path == "/health") health else post }).request(request)
            val body = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject
            assertEquals(supported, body.has("songPreparationId"))
            if (supported) assertEquals(REQUEST_ID, body["songPreparationId"].asString)
            assertFalse(body.getAsJsonObject("evidence").has("songPreparationReference"))
            assertFalse(post.sent.toString("UTF-8").contains(reference.destinationFingerprint))
        }
        val other = ReflectionConnectionConfig(config.endpoint, "b".repeat(43))
        val post = FakeHttpsConnection(URL(other.endpoint), response(generationMode = "ANTHROPIC"))
        HostedReflectionHttpClient(other, { post }).request(request)
        val body = JsonParser.parseString(post.sent.toString("UTF-8")).asJsonObject
        assertFalse(body.has("songPreparationId"))
        val music = body.getAsJsonObject("evidence").getAsJsonObject("musicContext")
        assertEquals("UNKNOWN", music["state"].asString)
        assertFalse(post.sent.toString("UTF-8").contains("Lovely Day"))
        // Clearing a hosted destination can select the debug bridge. It is a different
        // destination too, and must never receive the previously shared song identity.
        val local = FakeHttpsConnection(URL(LOCAL_REFLECTION_ENDPOINT), response())
        LocalReflectionHttpClient(connectionFactory = { local }).request(request)
        val localBody = JsonParser.parseString(local.sent.toString("UTF-8")).asJsonObject
        assertEquals("UNKNOWN", localBody.getAsJsonObject("evidence").getAsJsonObject("musicContext")["state"].asString)
        assertFalse(localBody.has("songPreparationId"))
        assertFalse(local.sent.toString("UTF-8").contains("Lovely Day"))
    }

    @Test fun earlyClaimPinsItsSongAndCannotLeakANewerSongAfterFailureOrChangedObservation() {
        val first = musicObservation().copy(title = "Lovely Day", artist = "Bill Withers")
        val latest = musicObservation(2).copy(title = "Stronger", artist = "Kanye West")
        val claim = SongPreparationClaimEntity(RUN_ID, REQUEST_ID, first.title!!, first.artist!!,
            first.sequenceNumber, "a".repeat(64), START + 31_000L)
        val request = reflectionRequestFromSavedRun(REQUEST_ID, sharingRun(), completedTransitions(),
            listOf(first, latest), songPreparation = claim)
        assertEquals("Lovely Day", request.evidence.musicContext.trackTitle)
        assertEquals(REQUEST_ID, request.evidence.songPreparationReference?.preparationId)
        val missing = reflectionRequestFromSavedRun(REQUEST_ID, sharingRun(), completedTransitions(),
            listOf(latest), songPreparation = claim)
        assertEquals("UNKNOWN", missing.evidence.musicContext.state)
        assertNull(missing.evidence.songPreparationReference)
        val legacy = reflectionRequestFromSavedRun(REQUEST_ID, sharingRun(), completedTransitions(), listOf(first, latest))
        assertEquals("Stronger", legacy.evidence.musicContext.trackTitle)
        val revoked = reflectionRequestFromSavedRun(REQUEST_ID, completedRun(), completedTransitions(),
            listOf(first, latest), songPreparation = claim)
        assertEquals("UNKNOWN", revoked.evidence.musicContext.state)
    }

    private fun response(
        requestId: String = REQUEST_ID,
        runId: String = RUN_ID,
        noSelection: String = "[FAKE] None.",
        includeNoSelection: Boolean = true,
        includeExtra: Boolean = false,
        includeGenerationMode: Boolean = true,
        generationMode: String = "FAKE"
    ): String {
        val candidates = JsonObject().apply {
            addProperty("spent", "[FAKE] Spent.")
            addProperty("feeling_good", "[FAKE] Good.")
            addProperty("powered_up", "[FAKE] Up.")
            if (includeNoSelection) addProperty("no_selection", noSelection)
        }
        return JsonObject().apply {
            addProperty("requestId", requestId)
            addProperty("runId", runId)
            if (includeGenerationMode) addProperty("generationMode", generationMode)
            if (includeExtra) addProperty("extra", true)
            add("candidates", candidates)
        }.toString()
    }

    private fun completedRun() = RunEntity(
        runId = RUN_ID,
        state = StoredRunState.COMPLETED,
        officialStartEpochMillis = START,
        startTimezoneId = "America/Chicago",
        lastCheckpointEpochMillis = FINISH,
        finishEpochMillis = FINISH,
        finalDistanceMeters = 180.25,
        metricSource = MetricSource.FIXTURE,
        telemetryCoverage = TelemetryCoverage.COMPLETE,
        displayDistanceUnit = DistanceUnit.MILES,
        transitionHistoryComplete = true
    )

    private fun sharingRun() = completedRun().copy(
        musicObservationRequested = true, musicReflectionSharingAllowed = true
    )

    private fun completedTransitions() = listOf(
        RunTransitionEntity(RUN_ID, 1, RunTransitionType.PAUSE, PAUSE)
    )

    private fun musicObservation(sequence: Int = 1) = RunMusicObservationEntity(
        runId = RUN_ID,
        sequenceNumber = sequence,
        observationKey = "fictional-observation-$sequence",
        observedAtEpochMillis = START + 1_000L * sequence,
        elapsedRealtimeNanos = 1_000_000L * sequence,
        sourcePackage = "com.spotify.music",
        title = "Reported title",
        artist = "Reported artist",
        playbackState = MusicPlaybackState.PLAYING,
        eventKind = MusicObservationKind.SESSION_SNAPSHOT
    )
}
