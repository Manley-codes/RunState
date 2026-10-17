package com.runstate.mobile.weather

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.StringReader

internal const val WEATHER_HOUR_MILLIS = 3_600_000L
internal const val WEATHER_RESPONSE_LIMIT = 16_384
private val weatherCodes = setOf(0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57,
    61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)

/** Modeled conditions for one UTC hour near the start, never a route or phone sensor reading. */
data class RunWeatherSnapshot(
    val source: String = "OPEN_METEO",
    val resolution: String = "HOURLY",
    val targetEpochMillis: Long,
    val validFromEpochMillis: Long,
    val validToEpochMillis: Long,
    val fetchedAtEpochMillis: Long,
    val temperatureCelsius: Double,
    val apparentTemperatureCelsius: Double,
    val relativeHumidityPercent: Double,
    val windSpeedKmh: Double,
    val weatherCode: Int
) {
    init {
        require(source == "OPEN_METEO" && resolution == "HOURLY") { "Unsupported weather source." }
        require(targetEpochMillis >= 0 && fetchedAtEpochMillis >= targetEpochMillis) { "Invalid weather time." }
        require(validFromEpochMillis == targetEpochMillis / WEATHER_HOUR_MILLIS * WEATHER_HOUR_MILLIS &&
            validFromEpochMillis <= Long.MAX_VALUE - WEATHER_HOUR_MILLIS &&
            validToEpochMillis == validFromEpochMillis + WEATHER_HOUR_MILLIS &&
            targetEpochMillis in validFromEpochMillis until validToEpochMillis) { "Invalid weather interval." }
        require(temperatureCelsius.isFinite() && temperatureCelsius in -100.0..70.0 &&
            apparentTemperatureCelsius.isFinite() && apparentTemperatureCelsius in -150.0..100.0 &&
            relativeHumidityPercent.isFinite() && relativeHumidityPercent in 0.0..100.0 &&
            windSpeedKmh.isFinite() && windSpeedKmh in 0.0..500.0 && weatherCode in weatherCodes) {
            "Invalid weather values."
        }
    }

    fun toJson(): String = Gson().toJson(this)

    companion object {
        private val fields = setOf("source", "resolution", "targetEpochMillis", "validFromEpochMillis",
            "validToEpochMillis", "fetchedAtEpochMillis", "temperatureCelsius",
            "apparentTemperatureCelsius", "relativeHumidityPercent", "windSpeedKmh", "weatherCode")

        /** Validate every read too: Gson reflection alone would bypass this class's constructor. */
        fun fromJson(json: String): RunWeatherSnapshot {
            val value = strictWeatherJson(json)
            require(value.keySet() == fields) { "Invalid weather fields." }
            fun text(name: String): String {
                val item = value.get(name)
                require(item is JsonPrimitive && item.isString) { "Invalid weather field type." }
                return item.asString
            }
            return RunWeatherSnapshot(text("source"), text("resolution"),
                weatherLong(value.get("targetEpochMillis")), weatherLong(value.get("validFromEpochMillis")),
                weatherLong(value.get("validToEpochMillis")), weatherLong(value.get("fetchedAtEpochMillis")),
                weatherDouble(value.get("temperatureCelsius")), weatherDouble(value.get("apparentTemperatureCelsius")),
                weatherDouble(value.get("relativeHumidityPercent")), weatherDouble(value.get("windSpeedKmh")),
                Math.toIntExact(weatherLong(value.get("weatherCode"))))
        }
    }
}

/** Bounded strict parsing rejects duplicates, extra documents and deeply nested provider input. */
internal fun strictWeatherJson(json: String): JsonObject {
    require(json.length <= WEATHER_RESPONSE_LIMIT) { "Weather response too large." }
    JsonReader(StringReader(json)).use { reader ->
        reader.strictness = Strictness.STRICT
        fun read(depth: Int): JsonElement {
            require(depth <= 8) { "Weather response too deep." }
            return when (reader.peek()) {
                JsonToken.BEGIN_OBJECT -> JsonObject().also { obj ->
                    reader.beginObject()
                    val names = mutableSetOf<String>()
                    while (reader.hasNext()) {
                        val name = reader.nextName()
                        require(names.add(name)) { "Duplicate weather field." }
                        obj.add(name, read(depth + 1))
                    }
                    reader.endObject()
                }
                JsonToken.BEGIN_ARRAY -> JsonArray().also { array ->
                    reader.beginArray()
                    while (reader.hasNext()) {
                        require(array.size() < 48) { "Weather array too large." }
                        array.add(read(depth + 1))
                    }
                    reader.endArray()
                }
                JsonToken.STRING -> JsonPrimitive(reader.nextString())
                JsonToken.NUMBER -> {
                    val number = reader.nextString()
                    require(number.toDouble().isFinite()) { "Invalid weather number." }
                    JsonPrimitive(java.math.BigDecimal(number))
                }
                JsonToken.BOOLEAN -> JsonPrimitive(reader.nextBoolean())
                JsonToken.NULL -> { reader.nextNull(); JsonNull.INSTANCE }
                else -> throw IllegalArgumentException("Invalid weather JSON.")
            }
        }
        val value = read(0)
        require(reader.peek() == JsonToken.END_DOCUMENT && value is JsonObject) { "Invalid weather document." }
        return value
    }
}

internal fun weatherLong(value: JsonElement?): Long {
    require(value is JsonPrimitive && value.isNumber &&
        value.asString.matches(Regex("-?(0|[1-9][0-9]*)"))) { "Invalid weather integer." }
    return value.asString.toLong()
}

internal fun weatherDouble(value: JsonElement?): Double {
    require(value is JsonPrimitive && value.isNumber) { "Invalid weather number type." }
    return value.asDouble.also { require(it.isFinite()) { "Invalid weather number." } }
}
