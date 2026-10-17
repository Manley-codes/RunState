package com.runstate.mobile.weather

import com.google.gson.JsonArray
import java.io.IOException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.round
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal const val WEATHER_TIMEOUT_MILLIS = 3_000L

internal fun interface RunWeatherGateway {
    suspend fun fetch(targetEpochMillis: Long, latitude: Double, longitude: Double): RunWeatherSnapshot?
}

/** Tests inject this seam; production can only contact the fixed HTTPS provider below. */
internal fun interface WeatherHttpTransport {
    suspend fun get(url: String): String?
}

internal class OpenMeteoWeatherClient(
    private val transport: WeatherHttpTransport = OpenMeteoHttpTransport(),
    private val clock: Clock = Clock.systemUTC()
) : RunWeatherGateway {
    override suspend fun fetch(targetEpochMillis: Long, latitude: Double, longitude: Double): RunWeatherSnapshot? =
        withTimeoutOrNull(WEATHER_TIMEOUT_MILLIS) {
            try {
                require(targetEpochMillis >= 0 && latitude.isFinite() && latitude in -90.0..90.0 &&
                    longitude.isFinite() && longitude in -180.0..180.0)
                val from = targetEpochMillis / WEATHER_HOUR_MILLIS * WEATHER_HOUR_MILLIS
                val hour = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
                    .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(from))
                val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
                    .addQueryParameter("latitude", (round(latitude * 100.0) / 100.0).toString())
                    .addQueryParameter("longitude", (round(longitude * 100.0) / 100.0).toString())
                    .addQueryParameter("hourly", "temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code")
                    .addQueryParameter("temperature_unit", "celsius")
                    .addQueryParameter("wind_speed_unit", "kmh")
                    .addQueryParameter("timezone", "GMT")
                    .addQueryParameter("timeformat", "unixtime")
                    .addQueryParameter("start_hour", hour).addQueryParameter("end_hour", hour).build()
                parseResponse(transport.get(url.toString()) ?: return@withTimeoutOrNull null,
                    targetEpochMillis, from)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Exception messages may contain request URLs. Never log or expose them.
                null
            }
        }

    private fun parseResponse(json: String, target: Long, from: Long): RunWeatherSnapshot {
        val root = strictWeatherJson(json)
        require(weatherLong(root.get("utc_offset_seconds")) == 0L)
        val units = root.getAsJsonObject("hourly_units")
        require(units.get("time")?.asString == "unixtime" && units.get("temperature_2m")?.asString == "°C" &&
            units.get("apparent_temperature")?.asString == "°C" &&
            units.get("relative_humidity_2m")?.asString == "%" && units.get("wind_speed_10m")?.asString == "km/h" &&
            units.get("weather_code")?.asString == "wmo code")
        val hourly = root.getAsJsonObject("hourly")
        fun single(name: String) = hourly.get(name).let {
            require(it is JsonArray && it.size() == 1) { "Invalid weather sample count." }
            it.get(0)
        }
        require(weatherLong(single("time")) == from / 1_000L) { "Weather time does not match run." }
        return RunWeatherSnapshot(targetEpochMillis = target, validFromEpochMillis = from,
            validToEpochMillis = Math.addExact(from, WEATHER_HOUR_MILLIS), fetchedAtEpochMillis = clock.millis(),
            temperatureCelsius = weatherDouble(single("temperature_2m")),
            apparentTemperatureCelsius = weatherDouble(single("apparent_temperature")),
            relativeHumidityPercent = weatherDouble(single("relative_humidity_2m")),
            windSpeedKmh = weatherDouble(single("wind_speed_10m")),
            weatherCode = Math.toIntExact(weatherLong(single("weather_code"))))
    }
}

private class OpenMeteoHttpTransport : WeatherHttpTransport {
    private val client = OkHttpClient.Builder()
        .callTimeout(WEATHER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .connectTimeout(WEATHER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .readTimeout(WEATHER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .writeTimeout(WEATHER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    override suspend fun get(url: String): String? = suspendCancellableCoroutine { continuation ->
        val address = url.toHttpUrl()
        require(address.scheme == "https" && address.host == "api.open-meteo.com" &&
            address.encodedPath == "/v1/forecast" && address.port == 443)
        val call = client.newCall(Request.Builder().url(address).header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }
            override fun onResponse(call: Call, response: Response) {
                val value = try {
                    response.use {
                        if (it.code != 200) return@use null
                        val body = it.body ?: return@use null
                        if (body.contentLength() > WEATHER_RESPONSE_LIMIT) return@use null
                        val bytes = body.byteStream().use { stream ->
                            val buffer = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(1_024)
                            while (true) {
                                val count = stream.read(chunk)
                                if (count < 0) break
                                require(buffer.size() + count <= WEATHER_RESPONSE_LIMIT)
                                buffer.write(chunk, 0, count)
                            }
                            buffer.toByteArray()
                        }
                        StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
                    }
                } catch (_: Exception) { null }
                if (continuation.isActive) continuation.resume(value)
            }
        })
    }
}
