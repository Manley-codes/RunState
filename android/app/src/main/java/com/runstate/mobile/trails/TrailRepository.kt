package com.runstate.mobile.trails

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.round

/** Public geographic data only. No run history, account token or precise GPS track is sent. */
internal class TrailRepository(context: Context) {
    private val app = context.applicationContext
    private val cacheDirectory = File(app.cacheDir, "trail-discovery-v1")
    private val catalog by lazy {
        runCatching { app.assets.open("trails/curated_trails.json").bufferedReader().use { TrailDataParser.curated(it.readText()) } }
            .getOrDefault(emptyList())
    }
    private val cityIndex by lazy {
        runCatching { app.assets.open("trails/us_cities.tsv").bufferedReader().use { TrailDataParser.cities(it.readText()) } }
            .getOrDefault(emptyList())
    }

    fun bundled(): List<TrailPlace> = catalog

    fun cities(query: String): List<TrailCity> = TrailDataParser.searchCities(cityIndex, query)

    suspend fun nearby(center: TrailCoordinate, radiusKm: Double = 40.0): TrailDiscoveryResult = withContext(Dispatchers.IO) {
        require(center.valid && radiusKm.isFinite() && radiusKm in 1.0..100.0)
        currentCoroutineContext().ensureActive()
        // Approximate to about one kilometre before making an external request or writing cache keys.
        val coarse = TrailCoordinate(round(center.latitude * 100) / 100, round(center.longitude * 100) / 100)
        val radius = round(radiusKm).coerceIn(1.0, 100.0)
        val key = String.format(Locale.US, "%.2f_%.2f_%.0f.json", coarse.latitude, coarse.longitude, radius)
        val cached = readCache(key)
        if (cached != null) {
            return@withContext cached.copy(trails = TrailDataParser.nearby(catalog + cached.trails, center, radiusKm), fromCache = true)
        }
        val fetched = mutableListOf<TrailPlace>()
        var moreAvailable = false
        try {
            for (page in 0 until MAX_PAGES) {
                currentCoroutineContext().ensureActive()
                val body = download(queryUrl(coarse, radius, page * PAGE_SIZE))
                currentCoroutineContext().ensureActive()
                val parsed = TrailDataParser.usgs(body)
                fetched += parsed.trails
                moreAvailable = parsed.moreAvailable
                if (!moreAvailable) break
            }
            currentCoroutineContext().ensureActive()
            val result = TrailDiscoveryResult(TrailDataParser.combineSegments(fetched), moreAvailable = moreAvailable)
            writeCache(key, result)
            result.copy(trails = TrailDataParser.nearby(catalog + result.trails, center, radiusKm))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // A partial second-page failure keeps useful first-page data, but never claims full coverage.
            TrailDiscoveryResult(
                trails = TrailDataParser.nearby(catalog + TrailDataParser.combineSegments(fetched), center, radiusKm),
                networkUnavailable = true,
                moreAvailable = fetched.isNotEmpty()
            )
        }
    }

    private fun queryUrl(center: TrailCoordinate, radius: Double, offset: Int): String = USGS_QUERY.toHttpUrl().newBuilder()
        .addQueryParameter("f", "geojson")
        .addQueryParameter("where", "trailtype = 'Terra Trail' AND name IS NOT NULL")
        .addQueryParameter("geometry", String.format(Locale.US, "%.2f,%.2f", center.longitude, center.latitude))
        .addQueryParameter("geometryType", "esriGeometryPoint")
        .addQueryParameter("inSR", "4326")
        .addQueryParameter("spatialRel", "esriSpatialRelIntersects")
        .addQueryParameter("distance", (radius + 1.0).toString())
        .addQueryParameter("units", "esriSRUnit_Kilometer")
        .addQueryParameter("outFields", "objectid,permanentidentifier,name,namealternate,trailtype,trailsurface,hikerpedestrian,sourceoriginator")
        .addQueryParameter("returnGeometry", "true")
        .addQueryParameter("outSR", "4326")
        .addQueryParameter("orderByFields", "objectid")
        .addQueryParameter("resultOffset", offset.toString())
        .addQueryParameter("resultRecordCount", PAGE_SIZE.toString())
        .build().toString()

    private suspend fun download(url: String): String = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", "RunState/1.0 TrailDiscovery").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        if (!it.isSuccessful) throw IOException("Trail service unavailable")
                        val body = it.body ?: throw IOException("No trail data")
                        if (body.contentLength() > MAX_BYTES) throw IOException("Trail response too large")
                        val bytes = ByteArrayOutputStream()
                        body.byteStream().use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (!continuation.isActive) throw IOException("Cancelled")
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (bytes.size() + count > MAX_BYTES) throw IOException("Trail response too large")
                                bytes.write(buffer, 0, count)
                            }
                        }
                        bytes.toString(Charsets.UTF_8.name())
                    }
                    if (continuation.isActive) continuation.resume(text)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private fun readCache(key: String): TrailDiscoveryResult? = runCatching {
        val file = File(cacheDirectory, key)
        val age = System.currentTimeMillis() - file.lastModified()
        if (!file.isFile) return@runCatching null
        if (age !in 0..CACHE_MILLIS || file.length() > MAX_BYTES) {
            file.delete()
            return@runCatching null
        }
        val root = JsonParser.parseString(file.readText()).asJsonObject
        if (root.get("version")?.asInt != 1) return@runCatching null
        val trails = root.get("trails")?.takeIf { it.isJsonArray } ?: return@runCatching null
        TrailDiscoveryResult(TrailDataParser.curated(trails.toString()), moreAvailable = root.boolean("moreAvailable"))
    }.getOrNull()

    private fun writeCache(key: String, result: TrailDiscoveryResult) {
        runCatching {
            if (!cacheDirectory.isDirectory && !cacheDirectory.mkdirs()) return
            val root = JsonObject().apply {
                addProperty("version", 1)
                addProperty("moreAvailable", result.moreAvailable)
                add("trails", Gson().toJsonTree(result.trails))
            }.toString()
            if (root.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return
            File(cacheDirectory, key).writeText(root)
            // Private disposable cache, limited to seven days and sixteen nearby areas.
            val now = System.currentTimeMillis()
            cacheDirectory.listFiles()?.filter { it.isFile && it.name.endsWith(".json") }
                ?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, file ->
                    if (index >= 16 || now - file.lastModified() > CACHE_MILLIS) file.delete()
                }
        }
    }

    companion object {
        internal const val USGS_QUERY = "https://partnerships.nationalmap.gov/arcgis/rest/services/USGSTrails/MapServer/0/query"
        private const val PAGE_SIZE = 200
        private const val MAX_PAGES = 2
        private const val MAX_BYTES = 4 * 1024 * 1024
        private const val CACHE_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    }
}

internal data class TrailSourcePage(val trails: List<TrailPlace>, val moreAvailable: Boolean)

/** Strict source parsing is kept independent of Android so malformed/partial data can be tested. */
internal object TrailDataParser {
    private const val MAX_FEATURES = 400
    private const val MAX_NODES = 30_000
    private const val MAX_LINE_NODES = 8_000

    fun curated(json: String): List<TrailPlace> {
        val root = JsonParser.parseString(json)
        val rows = if (root.isJsonArray) root.asJsonArray else root.objectOrNull()?.get("trails")?.arrayOrNull() ?: return emptyList()
        return rows.take(2_000).mapNotNull { item ->
            val value = item.objectOrNull() ?: return@mapNotNull null
            val center = coordinate(value.get("center")) ?: return@mapNotNull null
            val id = value.text("id", 180)
            val name = value.text("name", 160)
            if (id.isBlank() || name.isBlank()) return@mapNotNull null
            val photo = value.get("photo")?.objectOrNull()?.let { p ->
                TrailPhoto(
                    resourceName = p.text("resourceName", 100).takeIf { it.matches(Regex("[a-z0-9_]+")) },
                    url = https(p.text("url", 2_048)).ifBlank { null },
                    credit = p.text("credit", 500), sourceUrl = https(p.text("sourceUrl", 2_048)),
                    license = p.text("license", 100), licenseUrl = https(p.text("licenseUrl", 2_048))
                )
            }
            TrailPlace(
                id = id, name = name, center = center, subtitle = value.text("subtitle", 800),
                city = value.text("city", 100), state = value.text("state", 100),
                trailhead = coordinate(value.get("trailhead")), website = https(value.text("website", 2_048)), photo = photo,
                routeLines = objectLines(value.get("routeLines")),
                lengthMeters = value.number("lengthMeters")?.takeIf { it > 0 && it <= 20_000_000 },
                elevationGainMeters = value.number("elevationGainMeters")?.takeIf { it >= 0 && it <= 100_000 },
                difficulty = TrailDifficulty.entries.find { it.name.equals(value.text("difficulty"), true) },
                surfaces = value.get("surfaces")?.arrayOrNull()?.mapNotNull { entry ->
                    TrailSurface.entries.find { it.name.equals(entry.stringOrNull(), true) }
                }?.toSet() ?: emptySet(),
                sourceName = value.text("sourceName", 300), sourceUrl = https(value.text("sourceUrl", 2_048)),
                isSegment = value.boolean("isSegment")
            )
        }.distinctBy { it.id }
    }

    fun usgs(json: String): TrailSourcePage {
        require(json.length <= 4 * 1024 * 1024) { "Trail response too large" }
        val root = JsonParser.parseString(json).objectOrNull() ?: throw IOException("Invalid trail response")
        if (root.has("error") || root.text("type") != "FeatureCollection") throw IOException("Invalid trail response")
        val features = root.get("features")?.arrayOrNull() ?: throw IOException("Missing trail features")
        var nodes = 0
        var limited = features.size() > MAX_FEATURES
        val places = features.take(MAX_FEATURES).mapNotNull { feature ->
            val value = feature.objectOrNull() ?: return@mapNotNull null
            val properties = value.get("properties")?.objectOrNull() ?: return@mapNotNull null
            if (properties.text("trailtype") != "Terra Trail") return@mapNotNull null
            if (properties.text("hikerpedestrian").lowercase(Locale.ROOT) in setOf("no", "not allowed", "prohibited", "closed", "n", "0")) return@mapNotNull null
            val name = properties.text("name", 160).ifBlank { properties.text("namealternate", 160) }
            if (name.isBlank() || normalized(name) in setOf("unnamed", "unnamed trail", "unknown", "no name", "trail", "n a", "null")) return@mapNotNull null
            val id = properties.text("permanentidentifier", 180).ifBlank { properties.text("objectid", 40) }
            if (id.isBlank()) return@mapNotNull null
            val geometry = value.get("geometry")?.objectOrNull() ?: return@mapNotNull null
            val raw = geometry.get("coordinates")?.arrayOrNull() ?: return@mapNotNull null
            val rawLines = when (geometry.text("type")) {
                "LineString" -> listOf(raw)
                "MultiLineString" -> raw.mapNotNull { it.arrayOrNull() }
                else -> return@mapNotNull null
            }
            if (rawLines.any { it.size() > MAX_LINE_NODES } || nodes + rawLines.sumOf { it.size() } > MAX_NODES) {
                limited = true
                return@mapNotNull null
            }
            nodes += rawLines.sumOf { it.size() }
            val lines = rawLines.flatMap { splitCoordinates(it, ::geoCoordinate) }
            if (lines.isEmpty()) return@mapNotNull null
            // Source features are segments. Their measured geometry is not an advertised loop length.
            TrailPlace(
                id = "usgs:$id", name = name, center = lines.first().first(), routeLines = lines,
                surfaces = if (properties.text("trailsurface").equals("Paved", true)) setOf(TrailSurface.PAVED) else emptySet(),
                sourceName = properties.text("sourceoriginator", 300).ifBlank { "USGS National Digital Trails" },
                sourceUrl = "https://www.usgs.gov/national-digital-trails", isSegment = true
            )
        }
        return TrailSourcePage(places, limited || root.boolean("exceededTransferLimit") ||
            (root.get("properties")?.objectOrNull()?.boolean("exceededTransferLimit") == true))
    }

    fun cities(tsv: String): List<TrailCity> = tsv.lineSequence().filterNot { it.startsWith("#") }.take(50_000).mapNotNull { line ->
        val parts = line.split('\t')
        if (parts.size != 4 || parts[0].isBlank() || !parts[1].matches(Regex("[A-Z]{2}"))) return@mapNotNull null
        val point = TrailCoordinate(parts[2].toDoubleOrNull() ?: return@mapNotNull null, parts[3].toDoubleOrNull() ?: return@mapNotNull null)
        if (!point.valid) null else TrailCity(parts[0].take(100), parts[1], point)
    }.distinctBy { Triple(it.name, it.state, it.coordinate) }.toList()

    fun searchCities(cities: List<TrailCity>, query: String): List<TrailCity> {
        val needle = normalized(query.take(120))
        if (needle.length < 2) return emptyList()
        val tokens = needle.split(' ')
        val requestedState = tokens.lastOrNull()?.takeIf { token ->
            tokens.size > 1 && token.length == 2 && cities.any { it.state.equals(token, ignoreCase = true) }
        }
        val nameTokens = if (requestedState != null) tokens.dropLast(1) else tokens
        return cities.asSequence().filter { city ->
            val label = normalized(city.label)
            (requestedState == null || city.state.equals(requestedState, ignoreCase = true)) &&
                nameTokens.all { label.contains(it) }
        }.sortedWith(compareBy<TrailCity> { if (normalized(it.name) == needle) 0 else if (normalized(it.name).startsWith(needle)) 1 else 2 }
            .thenBy { it.name }.thenBy { it.state }).take(30).toList()
    }

    fun nearby(trails: List<TrailPlace>, center: TrailCoordinate, radiusKm: Double): List<TrailPlace> = trails.distinctBy { it.id }
        .map { it to distance(it, center) }.filter { it.second <= radiusKm * 1_000 }
        .sortedWith(compareBy<Pair<TrailPlace, Double>> { it.second }.thenBy { it.first.name }).map { it.first }

    fun combineSegments(trails: List<TrailPlace>): List<TrailPlace> {
        val result = mutableListOf<TrailPlace>()
        for (trail in trails.distinctBy { it.id }) {
            val index = result.indexOfFirst { previous ->
                normalized(previous.name) == normalized(trail.name) && previous.sourceName == trail.sourceName &&
                    previous.routeLines.any { left -> trail.routeLines.any { right ->
                        listOf(left.first(), left.last()).any { a -> listOf(right.first(), right.last()).any { b -> trailDistanceMeters(a, b) <= 100 } }
                    } }
            }
            if (index < 0) result += trail else result[index] = result[index].copy(
                routeLines = result[index].routeLines + trail.routeLines,
                surfaces = if (result[index].surfaces == trail.surfaces) trail.surfaces else emptySet()
            )
        }
        return result
    }

    private fun distance(trail: TrailPlace, center: TrailCoordinate): Double =
        trail.routeLines.asSequence().flatten().map { trailDistanceMeters(it, center) }.minOrNull()
            ?: trailDistanceMeters(trail.center, center)

    private fun coordinate(element: JsonElement?): TrailCoordinate? {
        val obj = element?.objectOrNull() ?: return null
        val result = TrailCoordinate(obj.number("latitude") ?: return null, obj.number("longitude") ?: return null)
        return result.takeIf { it.valid }
    }

    private fun geoCoordinate(element: JsonElement): TrailCoordinate? {
        val array = element.arrayOrNull() ?: return null
        if (array.size() < 2) return null
        val result = TrailCoordinate(array[1].finiteNumber() ?: return null, array[0].finiteNumber() ?: return null)
        return result.takeIf { it.valid }
    }

    private fun objectLines(element: JsonElement?): List<List<TrailCoordinate>> = element?.arrayOrNull()?.take(400)?.flatMap { line ->
        val array = line.arrayOrNull()
        if (array == null || array.size() > MAX_LINE_NODES) emptyList() else splitCoordinates(array, ::coordinate)
    } ?: emptyList()

    private fun splitCoordinates(array: JsonArray, parse: (JsonElement) -> TrailCoordinate?): List<List<TrailCoordinate>> {
        val result = mutableListOf<List<TrailCoordinate>>()
        var current = mutableListOf<TrailCoordinate>()
        for (element in array) {
            val point = parse(element)
            if (point == null) {
                if (current.size >= 2) result += current
                current = mutableListOf()
            } else if (current.lastOrNull() != point) current += point
        }
        if (current.size >= 2) result += current
        return result
    }

    private fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun https(value: String): String = runCatching {
        value.takeIf { it.isNotBlank() && it.toHttpUrl().scheme == "https" && it.toHttpUrl().username.isBlank() && it.toHttpUrl().password.isBlank() } ?: ""
    }.getOrDefault("")
}

private fun JsonElement.objectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null
private fun JsonElement.arrayOrNull(): JsonArray? = if (isJsonArray) asJsonArray else null
private fun JsonElement.stringOrNull(): String? = if (isJsonPrimitive) runCatching { asString }.getOrNull() else null
private fun JsonElement.finiteNumber(): Double? = if (isJsonPrimitive && asJsonPrimitive.isNumber) runCatching { asDouble.takeIf { it.isFinite() } }.getOrNull() else null
private fun JsonObject.number(key: String): Double? = get(key)?.finiteNumber()
private fun JsonObject.text(key: String, max: Int = 180): String = get(key)?.stringOrNull()?.trim()?.take(max).orEmpty()
private fun JsonObject.boolean(key: String): Boolean = get(key)?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } == true
