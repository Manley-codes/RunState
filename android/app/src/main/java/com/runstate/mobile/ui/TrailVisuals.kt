package com.runstate.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.runstate.mobile.R
import com.runstate.mobile.trails.TrailCoordinate
import com.runstate.mobile.trails.TrailPlace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.snapshotter.MapSnapshotter
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.max

private val TrailMapColor = Color(0xFFE6EEE1)

/** Full real photo with a separate inset map. Portraits use a bounded frame, never a crop. */
@Composable
internal fun TrailPhotoHero(
    trail: TrailPlace,
    modifier: Modifier = Modifier,
    allowNetwork: Boolean = true
) {
    val resource = trailPhotoResource(trail.photo?.resourceName)
    val remoteAllowed = allowNetwork && !LocalInspectionMode.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var remotePhoto by remember(trail.photo?.url, remoteAllowed) { mutableStateOf<Bitmap?>(null) }
    var photoAttempted by remember(trail.photo?.url, remoteAllowed) { mutableStateOf(false) }
    LaunchedEffect(trail.photo?.url, remoteAllowed, lifecycle) {
        if (resource == null && remoteAllowed) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!photoAttempted) {
                    remotePhoto = TrailPhotoImages.load(trail.photo?.url)
                    photoAttempted = true
                }
                awaitCancellation()
            }
        }
    }
    val painter = resource?.let { painterResource(it) }
    val aspect = when {
        painter != null && painter.intrinsicSize.width > 0 && painter.intrinsicSize.height > 0 ->
            painter.intrinsicSize.width / painter.intrinsicSize.height
        remotePhoto != null -> remotePhoto!!.width.toFloat() / remotePhoto!!.height
        else -> 1.6f
    }
    Box(modifier.fillMaxWidth().aspectRatio(aspect.coerceIn(1.1f, 2.2f)).clip(RoundedCornerShape(22.dp))
        .background(TrailMapColor).testTag("trailPhotoHero:${trail.id}")) {
        when {
            painter != null -> Image(painter, "${trail.name} photograph", Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit)
            remotePhoto != null -> Image(remotePhoto!!.asImageBitmap(), "${trail.name} photograph",
                Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else -> {
                TrailSnapshotImage(trail, Modifier.fillMaxSize(), remoteAllowed, large = true)
                Surface(Modifier.align(Alignment.TopStart).padding(12.dp),
                    shape = RoundedCornerShape(20.dp), color = Color.White.copy(alpha = 0.93f)) {
                    Text("Trail map", Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium, color = Color(0xFF344833))
                }
            }
        }
        if (painter != null || remotePhoto != null) {
            TrailMapThumbnail(trail, Modifier.align(Alignment.BottomEnd).padding(12.dp).size(96.dp),
                allowNetwork = remoteAllowed)
        } else {
            TrailMapCreditButton(Modifier.align(Alignment.BottomEnd))
        }
    }
}

@Composable
internal fun TrailMapThumbnail(
    trail: TrailPlace,
    modifier: Modifier = Modifier,
    allowNetwork: Boolean = true
) {
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.size(96.dp).clip(shape).background(TrailMapColor).border(2.dp, Color.White, shape)
        .testTag("trailMapThumbnail:${trail.id}")) {
        TrailSnapshotImage(trail, Modifier.fillMaxSize(), allowNetwork && !LocalInspectionMode.current,
            large = false)
        TrailMapCreditButton(Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
private fun TrailSnapshotImage(trail: TrailPlace, modifier: Modifier, allowNetwork: Boolean, large: Boolean) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val key = remember(trail.id, trail.center, trail.routeLines, large) {
        TrailSnapshotKey(trail.id, trail.center, trailSnapshotLines(trail.routeLines), large)
    }
    var attempted by remember(key, allowNetwork) { mutableStateOf(false) }
    var snapshot by remember(key, allowNetwork) { mutableStateOf(TrailMapImages.cached(key)) }
    LaunchedEffect(key, allowNetwork, lifecycle) {
        if (allowNetwork && snapshot == null) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                if (!attempted) {
                    // A dismissed sheet/backgrounded app cancels native work. An actual error is
                    // not retried continuously; another visit can make one fresh bounded attempt.
                    snapshot = TrailMapImages.load(context, key)
                    attempted = true
                }
                awaitCancellation()
            }
        }
    }
    Box(modifier.background(TrailMapColor), contentAlignment = Alignment.Center) {
        if (snapshot != null) {
            Image(snapshot!!.asImageBitmap(), "Map of ${trail.name}", Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit)
        } else {
            Text(if (allowNetwork && !attempted) "Loading map" else "Map unavailable",
                Modifier.padding(8.dp), color = Color(0xFF52654F), fontSize = if (large) 14.sp else 10.sp)
        }
    }
}

/** Tiny visible credit mark, with an accessible 48 dp target and complete linked attribution. */
@Composable
private fun TrailMapCreditButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .semantics { contentDescription = "Map credits" },
        contentPadding = PaddingValues(4.dp)) {
        Surface(color = Color.White.copy(alpha = 0.93f), shape = RoundedCornerShape(5.dp)) {
            Text("ⓘ", Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color(0xFF344833), fontSize = 12.sp)
        }
    }
    if (open) {
        val uriHandler = LocalUriHandler.current
        AlertDialog(onDismissRequest = { open = false }, title = { Text("Map credits") }, text = {
            Column {
                listOf("OpenFreeMap" to "https://openfreemap.org/",
                    "OpenMapTiles" to "https://openmaptiles.org/",
                    "© OpenStreetMap contributors" to RecordedMapConfig.ATTRIBUTION_URL).forEach { (label, url) ->
                    TextButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(label) }
                }
            }
        }, confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
    }
}

private fun trailPhotoResource(name: String?): Int? = when (name) {
    "trail_memorial_photo" -> R.drawable.trail_memorial_photo
    "trail_hermann_photo" -> R.drawable.trail_hermann_photo
    "trail_buffalo_bayou_photo" -> R.drawable.trail_buffalo_bayou_photo
    "trail_terry_hershey_photo" -> R.drawable.trail_terry_hershey_photo
    else -> null
}

private class TrailBitmapCache<K : Any>(bytes: Int) : LruCache<K, Bitmap>(bytes) {
    override fun sizeOf(key: K, value: Bitmap): Int = value.allocationByteCount
    // Compose can still display an evicted bitmap. Never recycle it while a view owns it.
}

internal fun allowedTrailPhotoUrl(url: String): Boolean = try {
    val uri = URI(url)
    uri.scheme == "https" && uri.host in setOf("upload.wikimedia.org", "thumb.wikimedia.org") &&
        uri.port in listOf(-1, 443) && uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
        uri.path.startsWith("/wikipedia/commons/") && !uri.path.contains("..") &&
        uri.path.substringAfterLast('.').lowercase() in setOf("jpg", "jpeg", "png", "webp")
} catch (_: Exception) { false }

private object TrailPhotoImages {
    private const val MAX_BYTES = 5 * 1024 * 1024
    private val cache = TrailBitmapCache<String>(12 * 1024 * 1024)
    private val slots = Semaphore(2)
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS).callTimeout(12, TimeUnit.SECONDS).build()

    suspend fun load(url: String?): Bitmap? {
        if (url == null || !allowedTrailPhotoUrl(url)) return null
        cache.get(url)?.let { return it }
        return slots.withPermit {
            cache.get(url)?.let { return@withPermit it }
            suspendCancellableCoroutine<Bitmap?> { continuation ->
                val call = client.newCall(Request.Builder().url(url)
                    .header("User-Agent", RecordedMapConfig.USER_AGENT).build())
                // Keep cancellation attached until the body is read, not merely until headers arrive.
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : okhttp3.Callback {
                    override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                    override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                        // OkHttp dispatches this callback on its worker thread. Neither network reads
                        // nor bitmap decoding occupy the UI thread.
                        val bitmap = try {
                            response.use { readPhoto(it) { continuation.isActive } }
                        } catch (_: Exception) { null }
                        if (continuation.isActive) {
                            if (bitmap != null) cache.put(url, bitmap)
                            continuation.resume(bitmap)
                        }
                    }
                })
            }
        }
    }

    private fun readPhoto(response: okhttp3.Response, active: () -> Boolean): Bitmap? {
        val body = response.body ?: return null
        if (!active() || !response.isSuccessful || body.contentLength() > MAX_BYTES) return null
        val output = ByteArrayOutputStream()
        body.byteStream().use { input ->
            val chunk = ByteArray(8192)
            while (active()) {
                val count = input.read(chunk)
                if (count < 0) break
                if (output.size() + count > MAX_BYTES) return null
                output.write(chunk, 0, count)
            }
        }
        return if (active()) decodeBoundedTrailPhoto(output.toByteArray()) else null
    }
}

private fun decodeBoundedTrailPhoto(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth !in 1..16000 || bounds.outHeight !in 1..16000) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / sample > 1200) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample })
}

/** Invalid evidence breaks a line; filtering it out would invent a connector across the gap. */
internal fun trailSnapshotLines(lines: List<List<TrailCoordinate>>): List<List<TrailCoordinate>> {
    if (lines.sumOf { it.size.toLong() } > 20_000L) return emptyList()
    return buildList {
        for (line in lines) {
            var segment = mutableListOf<TrailCoordinate>()
            for (point in line) {
                if (point.valid && point.latitude in -85.0..85.0) segment.add(point)
                else {
                    if (segment.size >= 2) add(segment.toList())
                    segment = mutableListOf()
                }
            }
            if (segment.size >= 2) add(segment.toList())
        }
    }
}

internal fun trailSnapshotStyle(lines: List<List<TrailCoordinate>>): String {
    val style = JsonParser.parseString(RecordedMapConfig.styleJson).asJsonObject
    val features = JsonArray()
    trailSnapshotLines(lines).forEach { segment ->
        val coordinates = JsonArray().apply { segment.forEach { point ->
            add(JsonArray().apply { add(point.longitude); add(point.latitude) })
        } }
        features.add(JsonObject().apply {
            addProperty("type", "Feature")
            add("properties", JsonObject())
            add("geometry", JsonObject().apply {
                addProperty("type", "LineString"); add("coordinates", coordinates)
            })
        })
    }
    style.getAsJsonObject("sources").add("trail-discovery", JsonObject().apply {
        addProperty("type", "geojson")
        add("data", JsonObject().apply { addProperty("type", "FeatureCollection"); add("features", features) })
    })
    listOf("trail-outline" to ("#FFFFFF" to 7), "trail-line" to ("#C96A37" to 4)).forEach { (id, paint) ->
        style.getAsJsonArray("layers").add(JsonObject().apply {
            addProperty("id", id); addProperty("type", "line"); addProperty("source", "trail-discovery")
            add("layout", JsonObject().apply { addProperty("line-cap", "round"); addProperty("line-join", "round") })
            add("paint", JsonObject().apply { addProperty("line-color", paint.first); addProperty("line-width", paint.second) })
        })
    }
    return style.toString()
}

private data class TrailSnapshotKey(
    val id: String,
    val center: TrailCoordinate,
    val lines: List<List<TrailCoordinate>>,
    val large: Boolean
)

private object TrailMapImages {
    private val cache = TrailBitmapCache<TrailSnapshotKey>(8 * 1024 * 1024)
    private val slots = Semaphore(2)
    fun cached(key: TrailSnapshotKey): Bitmap? = cache.get(key)

    suspend fun load(context: Context, key: TrailSnapshotKey): Bitmap? = slots.withPermit {
        cache.get(key)?.let { return@withPermit it }
        val styleJson = withContext(Dispatchers.Default) { trailSnapshotStyle(key.lines) }
        withContext(Dispatchers.Main.immediate) {
            if (!key.center.valid) return@withContext null
            var snapshotter: MapSnapshotter? = null
            try {
                initializeRecordedMapNetwork(context)
                val options = MapSnapshotter.Options(if (key.large) 640 else 288, if (key.large) 400 else 288)
                    .withPixelRatio(1f).withLogo(false).withAttribution(false)
                    .withStyleBuilder(Style.Builder().fromJson(styleJson))
                    .withCameraPosition(CameraPosition.Builder()
                        .target(LatLng(key.center.latitude, key.center.longitude)).zoom(14.5).build())
                val points = key.lines.flatten()
                if (points.size >= 2) {
                    val north = points.maxOf { it.latitude }
                    val south = points.minOf { it.latitude }
                    val east = points.maxOf { it.longitude }
                    val west = points.minOf { it.longitude }
                    val latitudePad = max((north - south) * 0.03, 0.00005)
                    val longitudePad = max((east - west) * 0.03, 0.00005)
                    options.withRegion(LatLngBounds.from(
                        (north + latitudePad).coerceAtMost(85.0),
                        (east + longitudePad).coerceAtMost(180.0),
                        (south - latitudePad).coerceAtLeast(-85.0),
                        (west - longitudePad).coerceAtLeast(-180.0)))
                        .withPadding(24, 24, 24, 24)
                }
                val renderer = MapSnapshotter(context, options)
                snapshotter = renderer
                withTimeoutOrNull(12_000) {
                    suspendCancellableCoroutine<Bitmap?> { continuation ->
                        renderer.start({ snapshot ->
                            if (continuation.isActive) continuation.resume(snapshot.bitmap)
                        }, { if (continuation.isActive) continuation.resume(null) })
                    }
                }?.also { cache.put(key, it) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            finally {
                // All native APIs including cancellation remain on the creating main thread.
                runCatching { snapshotter?.cancel() }
                snapshotter = null
            }
        }
    }
}
