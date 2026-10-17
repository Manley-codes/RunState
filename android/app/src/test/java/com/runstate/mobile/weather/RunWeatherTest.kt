package com.runstate.mobile.weather

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunWeatherEntity
import com.runstate.mobile.data.local.RunWeatherStatus
import com.runstate.mobile.data.local.RunWeatherStore
import com.runstate.mobile.data.local.StoredRunState
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class RunWeatherTest {
    private val id = "0f6a2c1e-9d43-4b7a-9c21-7b5e8a4d1f30"
    private val start = 1_780_000_000_000L
    private val from = start / WEATHER_HOUR_MILLIS * WEATHER_HOUR_MILLIS
    private fun snapshot(target: Long = start) = RunWeatherSnapshot(targetEpochMillis = target,
        validFromEpochMillis = target / WEATHER_HOUR_MILLIS * WEATHER_HOUR_MILLIS,
        validToEpochMillis = target / WEATHER_HOUR_MILLIS * WEATHER_HOUR_MILLIS + WEATHER_HOUR_MILLIS,
        fetchedAtEpochMillis = target + 10_000L, temperatureCelsius = 20.0,
        apparentTemperatureCelsius = 19.0, relativeHumidityPercent = 55.0, windSpeedKmh = 5.0, weatherCode = 1)

    private fun run() = RunEntity(id, StoredRunState.RUNNING, start, "America/Chicago", start + 10_000L,
        trackingMode = RunTrackingMode.GPS, startElapsedRealtimeMillis = 100_000L, timingSessionId = id)

    private fun point() = RunLocationPointEntity(id, 1, "first", start + 10_000L,
        110_000_000_000L, 1.23456, 2.34567, 3f, null, null, null, true, null, null, null)

    private class MemoryStore : RunWeatherStore {
        val rows = mutableMapOf<String, RunWeatherEntity>()
        var claimFailure = false
        override suspend fun find(runId: String) = rows[runId]
        override suspend fun claim(runId: String, allowed: Boolean): Boolean {
            if (claimFailure) error("Storage unavailable")
            if (rows.containsKey(runId)) {
                if (rows[runId]?.status == RunWeatherStatus.PENDING) finish(runId, null)
                return false
            }
            rows[runId] = RunWeatherEntity(runId,
                if (allowed) RunWeatherStatus.PENDING else RunWeatherStatus.DISABLED)
            return allowed
        }
        override suspend fun finish(runId: String, snapshotJson: String?) {
            if (rows[runId]?.status != RunWeatherStatus.PENDING) return
            rows[runId] = RunWeatherEntity(runId,
                if (snapshotJson == null) RunWeatherStatus.UNAVAILABLE else RunWeatherStatus.READY, snapshotJson)
        }
    }

    @Test fun snapshotRoundTripHasExactlyElevenFieldsAndRejectsUntrustedJson() {
        val json = snapshot().toJson()
        assertEquals(snapshot(), RunWeatherSnapshot.fromJson(json))
        assertEquals(11, strictWeatherJson(json).size())
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("status"))
        val invalid = listOf(
            json.replace("\"source\":\"OPEN_METEO\"", "\"source\":\"OTHER\""),
            json.replace("\"weatherCode\":1", "\"weatherCode\":4"),
            json.replace("\"weatherCode\":1", "\"weatherCode\":1.5"),
            json.replace("\"weatherCode\":1", "\"weatherCode\":\"1\""),
            json.replace("\"temperatureCelsius\":20.0", "\"temperatureCelsius\":null"),
            json.replace("\"temperatureCelsius\":20.0", "\"temperatureCelsius\":NaN"),
            json.replace("\"relativeHumidityPercent\":55.0", "\"relativeHumidityPercent\":101"),
            json.replace("\"fetchedAtEpochMillis\":${start + 10_000L}", "\"fetchedAtEpochMillis\":${start - 1L}"),
            json.dropLast(1) + ",\"latitude\":1}",
            json.dropLast(1) + ",\"weatherCode\":1}",
            json.replace("\"source\":\"OPEN_METEO\",", ""),
            "$json {}"
        )
        invalid.forEach { candidate ->
            assertThrows(Exception::class.java) { RunWeatherSnapshot.fromJson(candidate) }
        }
        assertNull(RunWeatherEntity(id, RunWeatherStatus.READY, "{}").readySnapshot())
    }

    private fun response(timeSeconds: Long = from / 1_000L): String = """
        {"utc_offset_seconds":0,"hourly_units":{"time":"unixtime","temperature_2m":"°C",
        "apparent_temperature":"°C","relative_humidity_2m":"%","wind_speed_10m":"km/h","weather_code":"wmo code"},
        "hourly":{"time":[$timeSeconds],"temperature_2m":[20],"apparent_temperature":[19],
        "relative_humidity_2m":[55],"wind_speed_10m":[5],"weather_code":[1]}}
    """.trimIndent()

    @Test fun clientRequestsTheSavedUtcHourWithRoundedCoordinatesAndVerifiesUnits() = runBlocking {
        var requested: String? = null
        val clock = Clock.fixed(Instant.ofEpochMilli(start + 10_000L), ZoneOffset.UTC)
        val client = OpenMeteoWeatherClient(WeatherHttpTransport { requested = it; response() }, clock)
        assertEquals(snapshot(), client.fetch(start, 1.23456, 2.34567))
        val url = requireNotNull(requested).toHttpUrl()
        assertEquals("https", url.scheme)
        assertEquals("api.open-meteo.com", url.host)
        assertEquals("1.23", url.queryParameter("latitude"))
        assertEquals("2.35", url.queryParameter("longitude"))
        assertEquals("GMT", url.queryParameter("timezone"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals(url.queryParameter("start_hour"), url.queryParameter("end_hour"))
        assertEquals(Instant.ofEpochMilli(from).toString().take(16), url.queryParameter("start_hour"))
        val invalid = listOf(response(from / 1_000L + 3_600L), response().replace("km/h", "mph"),
            response().replace("[20]", "[20,21]"), response().replace("[20]", "[null]"),
            response().replace("\"weather_code\":[1]", "\"weather_code\":[100]"),
            response().dropLast(1) + ",\"hourly\":{}}", " ".repeat(WEATHER_RESPONSE_LIMIT + 1))
        for (json in invalid) {
            assertNull(OpenMeteoWeatherClient(WeatherHttpTransport { json }, clock).fetch(start, 0.0, 0.0))
        }
    }

    @Test fun clientRejectsBadRequestsAndUnavailableTransportWithoutExposingErrors() = runBlocking {
        var calls = 0
        val client = OpenMeteoWeatherClient(WeatherHttpTransport { calls++; error("Private request URL") })
        assertNull(client.fetch(start, Double.NaN, 0.0))
        assertNull(client.fetch(start, 91.0, 0.0))
        assertEquals(0, calls)
        assertNull(client.fetch(start, 0.0, 0.0))
        assertEquals(1, calls)
    }

    @Test fun captureIsOncePerRunAndKeepsDisabledConsentAcrossRecreation() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = MemoryStore()
            var calls = 0
            val gateway = RunWeatherGateway { target, latitude, longitude ->
                calls++
                assertEquals(start, target)
                assertEquals(1.23, latitude, 0.0)
                assertEquals(2.35, longitude, 0.0)
                snapshot()
            }
            val capture = RunWeatherCapture(scope, store, gateway, Dispatchers.Unconfined)
            assertTrue(capture.onPoint(run(), point(), false))
            assertTrue(capture.onPoint(run(), point(), true))
            RunWeatherCapture(scope, store, gateway, Dispatchers.Unconfined).onPoint(run(), point(), true)
            assertEquals(0, calls)
            assertEquals(RunWeatherStatus.DISABLED, store.rows[id]?.status)

            val enabledStore = MemoryStore()
            val enabled = RunWeatherCapture(scope, enabledStore, gateway, Dispatchers.Unconfined)
            enabled.onPoint(run(), point(), true)
            enabled.onPoint(run(), point(), true)
            RunWeatherCapture(scope, enabledStore, gateway, Dispatchers.Unconfined).onPoint(run(), point(), true)
            assertEquals(1, calls)
            assertEquals(snapshot(), enabledStore.rows[id]?.readySnapshot())
        } finally { scope.cancel() }
    }

    @Test fun captureRejectsOldRunsFixtureDataPoorFixesAndInconsistentClocks() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = MemoryStore()
            var calls = 0
            val capture = RunWeatherCapture(scope, store, RunWeatherGateway { _, _, _ -> calls++; snapshot() },
                Dispatchers.Unconfined)
            assertFalse(capture.onPoint(run().copy(state = StoredRunState.COMPLETED, finishEpochMillis = start + 10_000L), point(), true))
            assertFalse(capture.onPoint(run().copy(trackingMode = RunTrackingMode.FIXTURE), point(), true))
            assertFalse(capture.onPoint(run(), point().copy(horizontalAccuracyMeters = 26f), true))
            assertFalse(capture.onPoint(run(), point().copy(acceptedForDistance = false, rejectionReason = "REJECTED"), true))
            assertFalse(capture.onPoint(run(), point().copy(observedAtEpochMillis = start - 1L), true))
            assertFalse(capture.onPoint(run(), point().copy(observedAtEpochMillis = start + 13_001L), true))
            assertTrue(store.rows.isEmpty())
            assertEquals(0, calls)
            assertTrue(capture.onPoint(run(), point(), true))
            assertEquals(1, calls)
            assertTrue(capture.onPoint(run(), point().copy(horizontalAccuracyMeters = 26f), true))
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun expiredWindowIsTerminalWithoutLookupAndNeverCreatesHistoricalWeather() {
        for (allowed in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val store = MemoryStore()
                var calls = 0
                val capture = RunWeatherCapture(scope, store,
                    RunWeatherGateway { _, _, _ -> calls++; snapshot() }, Dispatchers.Unconfined)
                val late = point().copy(observedAtEpochMillis = start + 121_000L,
                    elapsedRealtimeNanos = 221_000_000_000L)
                assertFalse(capture.onPoint(run().copy(state = StoredRunState.COMPLETED,
                    finishEpochMillis = start + 10_000L), late, allowed))
                assertFalse(capture.onPoint(run().copy(trackingMode = RunTrackingMode.FIXTURE), late, allowed))
                assertFalse(capture.onPoint(run(), late.copy(runId = "9b3d7a5c-2e41-4f68-8a90-1c2d3e4f5a6b"), allowed))
                assertTrue(store.rows.isEmpty())
                assertTrue(capture.onPoint(run(), late, allowed))
                assertEquals(if (allowed) RunWeatherStatus.UNAVAILABLE else RunWeatherStatus.DISABLED,
                    store.rows[id]?.status)
                assertTrue(capture.onPoint(run(), late, true))
                assertTrue(capture.onPoint(run(), point(), true))
                assertEquals(0, calls)
                assertNull(store.rows[id]?.snapshotJson)
            } finally { scope.cancel() }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = MemoryStore()
            var calls = 0
            val capture = RunWeatherCapture(scope, store,
                RunWeatherGateway { _, _, _ -> calls++; snapshot() }, Dispatchers.Unconfined)
            assertTrue(capture.onPoint(run().copy(startElapsedRealtimeMillis = null, timingSessionId = null), point(), true))
            assertTrue(capture.onPoint(run(), point(), true))
            assertTrue(store.rows.isEmpty())
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }

    @Test fun pendingCaptureDoesNotBlockCompletionAndCancellationClosesItsClaim() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val store = MemoryStore()
        val gate = CompletableDeferred<RunWeatherSnapshot?>()
        RunWeatherCapture(scope, store, RunWeatherGateway { _, _, _ -> gate.await() }, Dispatchers.Unconfined)
            .onPoint(run(), point(), true)
        assertEquals(RunWeatherStatus.PENDING, store.rows[id]?.status)
        assertNull(store.rows[id]?.readySnapshot())
        // Returning to the caller while HTTP is pending lets the ordinary save path proceed.
        scope.cancel()
        assertEquals(RunWeatherStatus.UNAVAILABLE, store.rows[id]?.status)
        assertNull(store.rows[id]?.snapshotJson)
    }

    @Test fun failureOrMismatchedResultClosesTheClaimAndNeverRetries() {
        for (mismatch in listOf(false, true)) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val store = MemoryStore()
                var calls = 0
                val gateway = RunWeatherGateway { _, _, _ -> calls++; if (mismatch) snapshot(start + 1L) else error("failed") }
                val capture = RunWeatherCapture(scope, store, gateway, Dispatchers.Unconfined)
                capture.onPoint(run(), point(), true)
                capture.onPoint(run(), point(), true)
                assertEquals(RunWeatherStatus.UNAVAILABLE, store.rows[id]?.status)
                assertEquals(1, calls)
            } finally { scope.cancel() }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = MemoryStore().also { it.claimFailure = true }
            var calls = 0
            RunWeatherCapture(scope, store, RunWeatherGateway { _, _, _ -> calls++; snapshot() }, Dispatchers.Unconfined)
                .onPoint(run(), point(), true)
            assertEquals(0, calls)
        } finally { scope.cancel() }
    }
}
