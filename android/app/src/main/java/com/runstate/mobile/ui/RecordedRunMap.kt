package com.runstate.mobile.ui

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.runstate.mobile.location.GpsMapCoordinate
import com.runstate.mobile.location.GpsMapEvidence
import com.runstate.mobile.location.GpsMapMarker
import com.runstate.mobile.location.GpsMapMarkerState
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.log.Logger
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.IOException

internal const val RECORDED_RUN_MAP_TAG = "recordedRunMap"
internal const val RECORDED_RUN_MAP_STATUS_TAG = "recordedRunMapStatus"
internal const val RECORDED_RUN_MAP_RECENTER_TAG = "recordedRunMapRecenter"
internal const val RECORDED_RUN_MAP_BACKGROUND_TAG = "recordedRunMapBackground"

/** Original feature-level palette over one vector source; no hosted style or location engine. */
internal object RecordedMapConfig {
    const val TILE_JSON_URL = "https://tiles.openfreemap.org/planet"
    const val GLYPHS_URL = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
    const val ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"
    const val USER_AGENT = "RunState/1.0 (Android; com.runstate.mobile)"
    const val INITIAL_ZOOM = 16.0
    const val PREFETCH_ZOOM_DELTA = 0
    val styleJson = """
        {
          "version": 8,
          "glyphs": "$GLYPHS_URL",
          "sources": {
            "streets": {
              "type": "vector", "url": "$TILE_JSON_URL",
              "attribution": "OpenFreeMap · © OpenMapTiles · © OpenStreetMap contributors"
            }
          },
          "layers": [
            {"id":"ground","type":"background","paint":{"background-color":"#dce7d5"}},
            {"id":"landuse","type":"fill","source":"streets","source-layer":"landuse",
             "filter":["==",["geometry-type"],"Polygon"],
             "paint":{"fill-color":["match",["get","class"],
               "residential","#dce7d5",["commercial","industrial"],"#d4ddcf",
               ["cemetery","recreation_ground"],"#c8dabe","#d8e4d0"]}},
            {"id":"landcover","type":"fill","source":"streets","source-layer":"landcover",
             "filter":["==",["geometry-type"],"Polygon"],
             "paint":{"fill-color":["match",["get","class"],"wood","#bcd1b2",
               "grass","#cedec2","wetland","#bed9ca","ice","#eef4f3",
               "sand","#ded7c4","rock","#ced2c9","#d6e3cc"]}},
            {"id":"parks","type":"fill","source":"streets","source-layer":"park",
             "filter":["==",["geometry-type"],"Polygon"],
             "paint":{"fill-color":"#c2d7b6","fill-opacity":0.65}},
            {"id":"water","type":"fill","source":"streets","source-layer":"water",
             "filter":["all",["==",["geometry-type"],"Polygon"],["!=",["get","brunnel"],"tunnel"]],
             "paint":{"fill-color":"#afd1df"}},
            {"id":"waterways","type":"line","source":"streets","source-layer":"waterway",
             "filter":["all",["==",["geometry-type"],"LineString"],["!=",["get","brunnel"],"tunnel"]],
             "layout":{"line-cap":"round","line-join":"round"},
             "paint":{"line-color":"#8eb9cc","line-width":["interpolate",["linear"],["zoom"],10,0.7,16,2.2,19,5]}},
            {"id":"buildings","type":"fill","source":"streets","source-layer":"building","minzoom":13,
             "filter":["==",["geometry-type"],"Polygon"],
             "paint":{"fill-color":"#c7c0af","fill-outline-color":"#b4b29f"}},
            {"id":"road-edges","type":"line","source":"streets","source-layer":"transportation",
             "filter":["all",["==",["geometry-type"],"LineString"],
               ["match",["get","class"],["motorway","trunk","primary","secondary","tertiary","minor","service"],true,false]],
             "layout":{"line-cap":"round","line-join":"round"},
             "paint":{"line-color":"#a8bec0","line-width":["interpolate",["exponential",1.3],["zoom"],
               6,0.7,12,2,16,["match",["get","class"],["motorway","trunk","primary"],10,7],19,25]}},
            {"id":"roads","type":"line","source":"streets","source-layer":"transportation",
             "filter":["all",["==",["geometry-type"],"LineString"],
               ["match",["get","class"],["motorway","trunk","primary","secondary","tertiary","minor","service"],true,false]],
             "layout":{"line-cap":"round","line-join":"round"},
             "paint":{"line-color":"#f5f6ed","line-width":["interpolate",["exponential",1.3],["zoom"],
               6,0.3,12,1,16,["match",["get","class"],["motorway","trunk","primary"],8,5],19,22],
               "line-opacity":["case",["==",["get","brunnel"],"tunnel"],0.55,1]}},
            {"id":"paths","type":"line","source":"streets","source-layer":"transportation","minzoom":12,
             "filter":["all",["==",["geometry-type"],"LineString"],
               ["match",["get","class"],["path","pedestrian","track"],true,false]],
             "paint":{"line-color":"#749da3","line-dasharray":[2,2],
               "line-width":["interpolate",["linear"],["zoom"],12,0.8,16,1.8,19,3]}},
            {"id":"rail","type":"line","source":"streets","source-layer":"transportation","minzoom":12,
             "filter":["all",["==",["geometry-type"],"LineString"],["==",["get","class"],"rail"]],
             "paint":{"line-color":"#9ba79a","line-width":1,"line-dasharray":[3,2]}},
            {"id":"road-labels","type":"symbol","source":"streets","source-layer":"transportation_name","minzoom":13,
             "filter":["==",["geometry-type"],"LineString"],
             "layout":{"symbol-placement":"line","text-field":["coalesce",["get","name:en"],["get","name_en"],["get","name"]],
               "text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],13,10,18,13],"symbol-spacing":250},
             "paint":{"text-color":"#526250","text-halo-color":"#f0f4e9","text-halo-width":1.4}},
            {"id":"place-labels","type":"symbol","source":"streets","source-layer":"place","minzoom":5,"maxzoom":17,
             "filter":["match",["get","class"],["city","town","village","suburb","neighbourhood"],true,false],
             "layout":{"text-field":["coalesce",["get","name:en"],["get","name_en"],["get","name"]],
               "text-font":["Noto Sans Regular"],"text-size":13,"text-max-width":9},
             "paint":{"text-color":"#425642","text-halo-color":"#eef4e8","text-halo-width":1.5}}
          ]
        }
    """.trimIndent()

    fun styleJson(loadStreetTiles: Boolean): String = if (loadStreetTiles) styleJson else """
        {"version":8,"sources":{},"layers":[
          {"id":"ground","type":"background","paint":{"background-color":"#dce7d5"}}
        ]}
    """.trimIndent()
}

/** Only the provider's TileJSON, dated vector tiles and one font; no route-bearing URLs. */
internal fun recordedMapRequest(request: Request): Request {
    val url = request.url
    if (request.method != "GET" || url.scheme != "https" || url.host != "tiles.openfreemap.org" ||
        url.port != 443 || url.query != null || url.fragment != null ||
        url.username.isNotEmpty() || url.password.isNotEmpty() || !allowedMapPath(url.encodedPath)) {
        throw IOException("Map tile request is not supported.")
    }
    return request.newBuilder().header("User-Agent", RecordedMapConfig.USER_AGENT).build()
}

private fun allowedMapPath(path: String): Boolean {
    if (path == "/planet") return true
    val tile = Regex("/planet/[0-9]{8}_[0-9]{6}_pt/([0-9]{1,2})/([0-9]+)/([0-9]+)\\.pbf").matchEntire(path)
    if (tile != null) {
        val zoom = tile.groupValues[1].toIntOrNull() ?: return false
        val x = tile.groupValues[2].toLongOrNull() ?: return false
        val y = tile.groupValues[3].toLongOrNull() ?: return false
        return zoom in 0..14 && x in 0 until (1L shl zoom) && y in 0 until (1L shl zoom)
    }
    val glyph = Regex("/fonts/Noto%20Sans%20Regular/([0-9]+)-([0-9]+)\\.pbf").matchEntire(path) ?: return false
    val first = glyph.groupValues[1].toIntOrNull() ?: return false
    val last = glyph.groupValues[2].toIntOrNull() ?: return false
    return first in 0..65280 && first % 256 == 0 && last == first + 255
}

internal fun recordedMapStatus(evidence: GpsMapEvidence?): String = when {
    evidence?.runId == "foreground-gps-preview" -> when {
        evidence.evidenceUnavailable -> "GPS preview is unavailable. Run setup still works."
        evidence.marker?.state == GpsMapMarkerState.CURRENT -> "Current GPS location · run not started"
        evidence.marker != null -> "Last known location · waiting for a fresh GPS fix"
        else -> "Finding your GPS location · run not started"
    }
    evidence == null -> "Waiting for a trustworthy GPS location."
    !evidence.isGpsRun -> "Sample runs do not record a GPS location."
    evidence.evidenceUnavailable -> "Recorded location is unavailable. Your run controls still work."
    evidence.marker?.state == GpsMapMarkerState.CURRENT -> "Current recorded location"
    evidence.marker?.state == GpsMapMarkerState.PAUSED -> "Paused · last recorded location"
    evidence.marker != null -> "Last recorded location · current position is unknown"
    evidence.sections.any { it.isNotEmpty() } -> "Recorded route · current position is unknown"
    else -> "Waiting for a trustworthy GPS location."
}

internal fun recordedMapAnchor(evidence: GpsMapEvidence?): GpsMapCoordinate? =
    if (evidence == null || !evidence.isGpsRun || evidence.evidenceUnavailable) null
    else evidence.marker?.coordinate ?: evidence.sections.lastOrNull { it.isNotEmpty() }?.last()

/** A complete replacement of the visible local overlays, including an explicitly empty owner. */
internal data class RecordedMapOverlay(
    val ownerId: String?,
    val sections: List<List<GpsMapCoordinate>> = emptyList(),
    val marker: GpsMapMarker? = null
)

internal fun recordedMapOverlay(evidence: GpsMapEvidence?): RecordedMapOverlay =
    if (evidence == null || !evidence.isGpsRun || evidence.evidenceUnavailable) {
        RecordedMapOverlay(evidence?.runId)
    } else RecordedMapOverlay(evidence.runId, evidence.sections, evidence.marker)

/**
 * Optional presentation of local recording evidence. The caller must explicitly enable the SDK;
 * previews and normal UI tests never create a native renderer or request remote map tiles.
 */
@Composable
internal fun RecordedRunMap(
    evidence: GpsMapEvidence,
    modifier: Modifier = Modifier,
    enableNativeMap: Boolean = false,
    loadStreetTiles: Boolean = true
) {
    Column(modifier.testTag(RECORDED_RUN_MAP_TAG)) {
        Text(
            recordedMapStatus(evidence), color = CompletionStrongInk, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(RECORDED_RUN_MAP_STATUS_TAG)
        )
        RecordedRunMapBackground(
            evidence, Modifier.weight(1f).fillMaxWidth(), enableNativeMap, loadStreetTiles,
            showControls = true
        )
        RecordedMapAttribution(Modifier.align(Alignment.End))
    }
}

/**
 * Retain one renderer while the optional map is open. It is created only after a real local
 * anchor arrives, then retained across owner/empty-evidence changes within that view. Each
 * update replaces every route/marker source; the last camera area is not a last-known marker.
 * The parent must show [RecordedMapAttribution] beside any visible map and expose status/retry
 * when [showControls] is false. Increment [retryKey] for an explicit retry, not for a run change.
 */
@Composable
internal fun RecordedRunMapBackground(
    evidence: GpsMapEvidence?,
    modifier: Modifier = Modifier,
    enableNativeMap: Boolean = false,
    loadStreetTiles: Boolean = true,
    showControls: Boolean = false,
    retryKey: Int = 0,
    controlsPadding: PaddingValues = PaddingValues(0.dp),
    onStatusChange: (String?) -> Unit = {}
) {
    var firstAnchoredEvidence by remember { mutableStateOf<GpsMapEvidence?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    if (firstAnchoredEvidence == null && recordedMapAnchor(evidence) != null) {
        SideEffect { firstAnchoredEvidence = evidence }
    }
    val initialEvidence = evidence?.takeIf { recordedMapAnchor(it) != null } ?: firstAnchoredEvidence
    Box(modifier.testTag(RECORDED_RUN_MAP_BACKGROUND_TAG)) {
        if (!enableNativeMap || initialEvidence == null) {
            val latestStatusChange by rememberUpdatedState(onStatusChange)
            LaunchedEffect(Unit) { latestStatusChange(null) }
            Box(
                Modifier.fillMaxSize().background(Color(0xFFEEF1ED)),
                contentAlignment = Alignment.Center
            ) {
                if (showControls) Text(
                    if (initialEvidence == null) recordedMapStatus(evidence) else "Map preview",
                    color = CompletionMutedInk, fontSize = 14.sp, modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            key(retry, retryKey, loadStreetTiles) {
                NativeRecordedMap(
                    evidence, initialEvidence, Modifier.fillMaxSize(), loadStreetTiles,
                    showControls, controlsPadding, onStatusChange
                ) { retry++ }
            }
        }
    }
}

@Composable
internal fun RecordedMapAttribution(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { runCatching { uriHandler.openUri("https://openfreemap.org/") } }) {
                Text("OpenFreeMap", fontSize = 10.sp, color = CompletionMutedInk)
            }
            TextButton(onClick = { runCatching { uriHandler.openUri("https://openmaptiles.org/") } }) {
                Text("© OpenMapTiles", fontSize = 10.sp, color = CompletionMutedInk)
            }
        }
        TextButton(onClick = { runCatching { uriHandler.openUri(RecordedMapConfig.ATTRIBUTION_URL) } }) {
            Text("© OpenStreetMap contributors", fontSize = 10.sp, color = CompletionMutedInk)
        }
    }
}

private enum class MapLoadState { LOADING, READY, UNAVAILABLE }

@Composable
private fun NativeRecordedMap(
    evidence: GpsMapEvidence?,
    initialEvidence: GpsMapEvidence,
    modifier: Modifier,
    loadStreetTiles: Boolean,
    showControls: Boolean,
    controlsPadding: PaddingValues,
    onStatusChange: (String?) -> Unit,
    onRetry: () -> Unit
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember { mutableStateOf(MapLoadState.LOADING) }
    var following by remember { mutableStateOf(true) }
    var slow by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(!hasInternetConnection(context)) }
    val controller = remember {
        try {
            RecordedMapController(context, initialEvidence, loadStreetTiles, showControls,
                { state = it }, { following = it })
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> controller?.syncLifecycle(lifecycle.currentState) }
        lifecycle.addObserver(observer)
        controller?.syncLifecycle(lifecycle.currentState)
        onDispose {
            lifecycle.removeObserver(observer)
            controller?.destroy()
        }
    }
    LaunchedEffect(controller) {
        // Tile availability cannot be inferred from a loaded inline style. This is only a
        // presentation timeout: it never retries requests or changes the GPS recorder.
        delay(15_000L)
        if (state == MapLoadState.LOADING) slow = true
    }
    LaunchedEffect(context, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                offline = !hasInternetConnection(context)
                delay(3_000L)
            }
        }
    }
    val message = when {
        controller == null -> "Map could not open. Your run controls still work."
        !loadStreetTiles -> "Street map disabled for this check."
        offline -> "Offline · only cached streets may be visible. GPS recording is separate."
        state == MapLoadState.UNAVAILABLE -> "Some streets could not load. Your recorded route is kept."
        state == MapLoadState.LOADING && slow -> "Streets are taking longer to load. GPS recording is separate."
        state == MapLoadState.LOADING -> "Loading streets…"
        else -> null
    }
    val latestStatusChange by rememberUpdatedState(onStatusChange)
    LaunchedEffect(message) { latestStatusChange(message) }
    Box(modifier.background(Color(0xFFEEF1ED))) {
        if (controller != null) {
            AndroidView(
                factory = { controller.view },
                update = {
                    controller.update(evidence)
                    controller.setInteractionEnabled(showControls)
                },
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = if (evidence?.runId == "foreground-gps-preview")
                        "Location preview. ${recordedMapStatus(evidence)}" else
                        "Street map with recorded GPS sections. " +
                        "Unrecorded intervals are not joined. ${recordedMapStatus(evidence)}"
                }
            )
        }
        Box(Modifier.fillMaxSize().padding(controlsPadding)) {
        if (showControls && message != null) {
            Column(
                Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Color(0xF2F7F8F5)).padding(12.dp)
            ) {
                Text(message, fontSize = 12.sp, color = CompletionStrongInk)
                if (controller == null || state == MapLoadState.UNAVAILABLE || slow || offline) {
                    TextButton(onClick = onRetry) { Text("Retry map") }
                }
            }
        }
        if (showControls && controller != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { controller.recenter() },
                    modifier = Modifier.testTag(RECORDED_RUN_MAP_RECENTER_TAG)
                ) { Text(if (following) "Centered" else "Recenter") }
            }
        }
        }
    }
}

private fun hasInternetConnection(context: Context): Boolean = try {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
} catch (_: RuntimeException) {
    // No permission or temporarily unavailable service is not proof of being offline.
    true
}

/** Owns exactly one MapView, its callbacks and its balanced native lifecycle. */
private class RecordedMapController(
    context: Context,
    initialEvidence: GpsMapEvidence,
    loadStreetTiles: Boolean,
    initiallyInteractive: Boolean,
    private val onLoad: (MapLoadState) -> Unit,
    private val onFollowing: (Boolean) -> Unit
) {
    private val application = context.applicationContext
    private var destroyed = false
    private var started = false
    private var resumed = false
    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var latest: GpsMapEvidence? = initialEvidence
    private var rendered: RecordedMapOverlay? = null
    private var following = true
    private var interactive = initiallyInteractive
    private var lastCentered: GpsMapCoordinate? = null
    val view: MapView

    private val memoryCallback = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        override fun onLowMemory() { if (!destroyed) view.onLowMemory() }
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) onLowMemory()
        }
    }

    init {
        initializeMapNetwork(application)
        val anchor = checkNotNull(recordedMapAnchor(initialEvidence))
        view = MapView(context, MapLibreMapOptions.createFromAttributes(context)
            .textureMode(true)
            .camera(CameraPosition.Builder().target(anchor.latLng()).zoom(RecordedMapConfig.INITIAL_ZOOM).build())
            .attributionEnabled(false) // Always-visible Compose attribution sits directly below the map.
            .logoEnabled(false).tiltGesturesEnabled(false).rotateGesturesEnabled(false)
            .maxZoomPreference(19.0).actionJournalEnabled(false)
            .apply { prefetchZoomDelta = RecordedMapConfig.PREFETCH_ZOOM_DELTA })
        view.onCreate(null)
        view.addOnDidFinishLoadingMapListener { if (!destroyed) onLoad(MapLoadState.READY) }
        view.addOnDidFailLoadingMapListener { _ -> if (!destroyed) onLoad(MapLoadState.UNAVAILABLE) }
        application.registerComponentCallbacks(memoryCallback)
        view.getMapAsync { readyMap ->
            if (!destroyed) safely {
                map = readyMap
                applyGestureMode(readyMap)
                readyMap.setPrefetchZoomDelta(RecordedMapConfig.PREFETCH_ZOOM_DELTA)
                readyMap.addOnCameraMoveStartedListener { reason ->
                    if (!destroyed && reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        following = false
                        onFollowing(false)
                    }
                }
                readyMap.setStyle(Style.Builder().fromJson(RecordedMapConfig.styleJson(loadStreetTiles))) { readyStyle ->
                    if (!destroyed) safely {
                        style = readyStyle
                        readyStyle.addSource(GeoJsonSource(ROUTE_SOURCE, emptyFeatures()))
                        readyStyle.addSource(GeoJsonSource(ANCHOR_SOURCE, emptyFeatures()))
                        readyStyle.addSource(GeoJsonSource(MARKER_SOURCE, emptyFeatures()))
                        readyStyle.addLayer(LineLayer("recorded-route", ROUTE_SOURCE).withProperties(
                            lineColor("#2c6875"), lineWidth(4.5f),
                            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
                        ))
                        readyStyle.addLayer(CircleLayer("recorded-anchors", ANCHOR_SOURCE).withProperties(
                            circleColor("#2c6875"), circleRadius(3f),
                            circleStrokeColor("#ffffff"), circleStrokeWidth(1f)
                        ))
                        readyStyle.addLayer(CircleLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
                            circleRadius(8f), circleStrokeWidth(3f), circleStrokeColor("#ffffff")
                        ))
                        update(latest)
                    }
                }
            }
        }
    }

    fun update(evidence: GpsMapEvidence?) {
        if (destroyed) return
        if (evidence?.runId != latest?.runId) {
            // A preview becoming a run (or leaving the run) changes ownership, not the
            // native renderer. Never carry a previous owner's marker or camera-follow state.
            following = true
            lastCentered = null
            onFollowing(true)
        }
        latest = evidence
        val loadedStyle = style ?: return
        val overlay = recordedMapOverlay(evidence)
        if (rendered == overlay) return
        safely {
            // One feature per section: the renderer cannot invent a connector across a pause/gap.
            val routes = overlay.sections.filter { it.size > 1 }.map { section ->
                Feature.fromGeometry(LineString.fromLngLats(section.map { it.point() }))
            }
            val anchors = overlay.sections.filter { it.size == 1 }.map {
                Feature.fromGeometry(it.single().point())
            }
            loadedStyle.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(routes))
            loadedStyle.getSourceAs<GeoJsonSource>(ANCHOR_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(anchors))
            val markers = overlay.marker?.let { listOf(Feature.fromGeometry(it.coordinate.point())) } ?: emptyList()
            loadedStyle.getSourceAs<GeoJsonSource>(MARKER_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(markers))
            loadedStyle.getLayerAs<CircleLayer>(MARKER_LAYER)?.setProperties(circleColor(
                if (overlay.marker?.state == GpsMapMarkerState.CURRENT) "#16749a" else "#787c75"
            ))
            rendered = overlay
            if (following) center(force = false)
        }
    }

    fun recenter() {
        if (destroyed) return
        following = true
        onFollowing(true)
        safely { center(force = true) }
    }

    fun setInteractionEnabled(enabled: Boolean) {
        if (destroyed || interactive == enabled) return
        interactive = enabled
        safely { map?.let(::applyGestureMode) }
        // The Start/Active backdrop follows GPS. Exploring the expanded map must not leave
        // that backdrop pointed at another area or allow a swipe on the layout to pan it.
        if (!enabled) recenter()
    }

    private fun applyGestureMode(readyMap: MapLibreMap) {
        readyMap.uiSettings.apply {
            setAllGesturesEnabled(interactive)
            isRotateGesturesEnabled = false
            isTiltGesturesEnabled = false
        }
    }

    private fun center(force: Boolean) {
        val coordinate = recordedMapAnchor(latest) ?: return
        if (!force && coordinate == lastCentered) return
        val readyMap = map ?: return
        val zoom = if (force) RecordedMapConfig.INITIAL_ZOOM else readyMap.cameraPosition.zoom
        readyMap.moveCamera(CameraUpdateFactory.newLatLngZoom(coordinate.latLng(), zoom))
        lastCentered = coordinate
    }

    fun syncLifecycle(state: Lifecycle.State) {
        if (destroyed) return
        if (state == Lifecycle.State.DESTROYED) { destroy(); return }
        if (resumed && !state.isAtLeast(Lifecycle.State.RESUMED)) { view.onPause(); resumed = false }
        if (started && !state.isAtLeast(Lifecycle.State.STARTED)) { view.onStop(); started = false }
        if (!started && state.isAtLeast(Lifecycle.State.STARTED)) { view.onStart(); started = true }
        if (!resumed && state.isAtLeast(Lifecycle.State.RESUMED)) { view.onResume(); resumed = true }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true // Late native callbacks cannot publish after the dialog/run changes.
        application.unregisterComponentCallbacks(memoryCallback)
        if (resumed) { view.onPause(); resumed = false }
        if (started) { view.onStop(); started = false }
        view.onDestroy()
        style = null
        map = null
    }

    private inline fun safely(action: () -> Unit) {
        try { action() } catch (_: RuntimeException) { if (!destroyed) onLoad(MapLoadState.UNAVAILABLE) }
    }

    companion object {
        private const val ROUTE_SOURCE = "runstate-route"
        private const val ANCHOR_SOURCE = "runstate-anchors"
        private const val MARKER_SOURCE = "runstate-marker"
        private const val MARKER_LAYER = "runstate-position"
        private var networkInitialized = false

        private fun initializeMapNetwork(context: Context) {
            if (networkInitialized) return
            Logger.setVerbosity(Logger.NONE)
            MapLibre.getInstance(context)
            HttpRequestUtil.setLogEnabled(false)
            HttpRequestUtil.setPrintRequestUrlOnFailure(false)
            // Preserve normal cache headers and identify the app. No area download or prefetch.
            HttpRequestUtil.setOkHttpClient(OkHttpClient.Builder()
                .followRedirects(false).followSslRedirects(false).addInterceptor { chain ->
                chain.proceed(recordedMapRequest(chain.request()))
            }.build())
            networkInitialized = true
        }
    }
}

private fun emptyFeatures() = FeatureCollection.fromFeatures(emptyList())
private fun GpsMapCoordinate.point() = Point.fromLngLat(longitude, latitude)
private fun GpsMapCoordinate.latLng() = LatLng(latitude, longitude)
