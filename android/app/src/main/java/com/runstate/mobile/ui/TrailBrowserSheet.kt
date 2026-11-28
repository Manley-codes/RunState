package com.runstate.mobile.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.trails.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.round

internal const val TRAIL_SEARCH_TAG = "trailSearch"
internal const val TRAIL_FILTER_TAG = "trailFilters"
internal const val TRAIL_RESULTS_TAG = "trailResults"
internal const val TRAIL_DIRECTIONS_TAG = "trailDirections"
private val BrowserInk = androidx.compose.ui.graphics.Color(0xFF293D38)

/** Discovery owns no run state. Selecting a park or opening directions cannot start a run. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrailBrowserSheet(
    initialTrailId: String?,
    location: TrailCoordinate?,
    onRequestLocation: (() -> Unit)?,
    enabled: Boolean,
    distanceUnit: DistanceUnit,
    onDismiss: () -> Unit,
    allowNetwork: Boolean = true,
    catalogOverride: List<TrailPlace>? = null,
    discoverOverride: (suspend (TrailCoordinate) -> TrailDiscoveryResult)? = null,
    citiesOverride: ((String) -> List<TrailCity>)? = null,
    onDirectionsOverride: ((TrailPlace) -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { TrailRepository(context.applicationContext) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var catalog by remember { mutableStateOf(catalogOverride.orEmpty()) }
    var catalogReady by remember { mutableStateOf(catalogOverride != null) }
    var query by rememberSaveable { mutableStateOf("") }
    var chosenLatitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var chosenLongitude by rememberSaveable { mutableStateOf<Double?>(null) }
    var chosenCity by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf(initialTrailId) }
    var selectedSnapshot by remember { mutableStateOf<TrailPlace?>(null) }
    var discovery by remember { mutableStateOf<TrailDiscoveryResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var cities by remember { mutableStateOf(emptyList<TrailCity>()) }
    var filters by remember { mutableStateOf(TrailFilters()) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }
    var creditsOpen by rememberSaveable { mutableStateOf(false) }
    val coarseLocation = location?.takeIf { it.valid }?.let {
        TrailCoordinate(round(it.latitude * 100) / 100, round(it.longitude * 100) / 100)
    }
    // Use one location per browsing visit. GPS drift/app switching must not replace results
    // while the runner reads a trail; Near me is the deliberate way to refresh the area.
    var nearbyLocation by remember { mutableStateOf(coarseLocation) }
    LaunchedEffect(coarseLocation) {
        if (nearbyLocation == null && coarseLocation != null) nearbyLocation = coarseLocation
    }
    val center = if (chosenLatitude != null && chosenLongitude != null)
        TrailCoordinate(chosenLatitude!!, chosenLongitude!!) else nearbyLocation
    val nearbyCatalog = remember(catalog, center) {
        if (center == null) catalog else catalog.filter { trailDistanceMeters(center, it.center) <= 40_000 }
    }
    LaunchedEffect(repository, catalogOverride) {
        catalog = catalogOverride ?: withContext(Dispatchers.IO) { repository.bundled() }
        catalogReady = true
    }
    LaunchedEffect(center, retry, catalogReady, allowNetwork, foreground) {
        discovery = null
        if (!foreground || center == null || !catalogReady || (!allowNetwork && discoverOverride == null)) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            discovery = discoverOverride?.invoke(center) ?: repository.nearby(center)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            discovery = TrailDiscoveryResult(nearbyCatalog, networkUnavailable = true)
        } finally { loading = false }
    }
    LaunchedEffect(query) {
        cities = emptyList()
        if (query.trim().length >= 2) {
            delay(250)
            cities = withContext(Dispatchers.IO) {
                citiesOverride?.invoke(query) ?: repository.cities(query)
            }.take(5)
        }
    }
    val areaTrails = remember(discovery, nearbyCatalog) {
        (nearbyCatalog + discovery?.trails.orEmpty()).distinctBy { it.id }
    }
    // Typing also searches the bundled U.S. collection; a selected city controls nearby discovery.
    val results = remember(areaTrails, catalog, query, filters) {
        searchTrails(if (query.isBlank()) areaTrails else areaTrails + catalog, query, filters)
    }
    val selected = selectedId?.let { id ->
        selectedSnapshot?.takeIf { it.id == id } ?: (catalog + areaTrails).firstOrNull { it.id == id }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(query, center, filters) { listState.scrollToItem(0) }
    val flingBoundary = remember {
        object : NestedScrollConnection {
            override suspend fun onPostFling(consumed: Velocity, available: Velocity) =
                if (available.y < 0f) Velocity(0f, available.y) else Velocity.Zero
        }
    }
    BackHandler(selectedId != null && !filtersOpen && !creditsOpen) { selectedId = null }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = androidx.compose.ui.graphics.Color(0xFFF5F9FA),
        modifier = Modifier.testTag(if (selectedId == null) START_TRAILS_LIST_TAG else START_TRAIL_SHEET_TAG)
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.96f).padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectedId != null) TextButton(onClick = { selectedId = null }, modifier = Modifier.testTag("trailBack")) {
                    Text("‹ Back", color = BrowserInk)
                }
                Text("Trails", fontSize = 27.sp, fontWeight = FontWeight.Bold,
                    color = BrowserInk, modifier = Modifier.weight(1f))
                TextButton(onClick = { creditsOpen = true }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Trail and photo credits" }) { Text("ⓘ", fontSize = 22.sp, color = BrowserInk) }
                TextButton(onClick = onDismiss) { Text("Close", color = BrowserInk) }
            }
            if (selectedId != null) {
                if (selected == null) {
                    Text(if (catalogReady) "This trail is no longer in the saved results. Search for it again." else "Loading trail…",
                        color = BrowserInk, modifier = Modifier.padding(vertical = 24.dp))
                } else key(selected.id) {
                    Column(Modifier.weight(1f).nestedScroll(flingBoundary).verticalScroll(rememberScrollState())
                        .padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        TrailPhotoHero(selected, Modifier.fillMaxWidth(), allowNetwork = allowNetwork)
                        Text(selected.name, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, color = BrowserInk)
                        TrailFacts(selected, distanceUnit)
                        Button(onClick = {
                            if (onDirectionsOverride != null) onDirectionsOverride(selected)
                            else openTrailLink(context, trailNavigationUri(selected))
                        }, enabled = enabled, colors = ButtonDefaults.buttonColors(containerColor = BrowserInk),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(TRAIL_DIRECTIONS_TAG)) {
                            Text("Directions")
                        }
                        if (selected.trailhead == null) Text("Choose an entrance in your maps app.",
                            color = BrowserInk.copy(alpha = .75f), fontSize = 13.sp)
                        if (selected.website.startsWith("https://")) TextButton(
                            onClick = { openTrailLink(context, selected.website) }, enabled = enabled,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("trailParkInformation")) {
                            Text("Park information ↗", color = BrowserInk)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(query, { query = it.take(100) }, singleLine = true, enabled = enabled,
                        placeholder = { Text("Search trails or city") }, shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).testTag(TRAIL_SEARCH_TAG))
                    FilledTonalButton(onClick = { filtersOpen = true }, enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 12.dp), shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.heightIn(min = 54.dp).testTag(TRAIL_FILTER_TAG)) {
                        Text(if (filters.count == 0) "Filters" else "Filters · ${filters.count}")
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(chosenCity ?: if (center != null) "Near you · within ${if (distanceUnit == DistanceUnit.MILES) "25 mi" else "40 km"}" else "Popular U.S. trails",
                        color = BrowserInk, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f).testTag("trailArea"))
                    TextButton(onClick = {
                        chosenLatitude = null; chosenLongitude = null; chosenCity = null; query = ""
                        if (coarseLocation == null) onRequestLocation?.invoke() else {
                            nearbyLocation = coarseLocation
                            retry++
                        }
                    }, enabled = enabled && (coarseLocation != null || onRequestLocation != null),
                        modifier = Modifier.testTag("trailNearby")) { Text("Near me", color = BrowserInk) }
                }
                if (center == null) Text("Choose a city or use your location.", color = BrowserInk.copy(alpha = .75f), fontSize = 13.sp)
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("trailLoading"), color = BrowserInk)
                LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()
                    .nestedScroll(flingBoundary).testTag(TRAIL_RESULTS_TAG),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (cities.isNotEmpty()) item(key = "cities") {
                        Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(18.dp))) {
                            cities.forEach { city ->
                                TextButton(onClick = {
                                    chosenLatitude = city.coordinate.latitude; chosenLongitude = city.coordinate.longitude
                                    chosenCity = city.label; query = ""
                                }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .testTag("trailCity:${city.label}")) { Text("Search near ${city.label}", color = BrowserInk) }
                            }
                        }
                    }
                    if (discovery?.networkUnavailable == true) item(key = "offline") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Showing saved trails. Nearby search is unavailable.", fontSize = 13.sp,
                                color = BrowserInk, modifier = Modifier.weight(1f))
                            TextButton(onClick = { retry++ }, enabled = enabled) { Text("Retry", color = BrowserInk) }
                        }
                    }
                    if (results.isEmpty() && catalogReady && !loading) item(key = "empty") {
                        Column(Modifier.padding(vertical = 20.dp)) {
                            Text("No matching trails", fontWeight = FontWeight.Bold, color = BrowserInk)
                            Text(if (filters.count > 0) "Try fewer filters. Unlisted trail details are excluded by specific filters."
                                else "Try another name or a nearby city. Trail coverage varies by area.", color = BrowserInk)
                            if (filters.count > 0) TextButton(onClick = { filters = TrailFilters() }) { Text("Clear filters") }
                        }
                    }
                    items(results, key = { it.id }) { trail ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                            .background(androidx.compose.ui.graphics.Color.White)
                            .clickable(enabled = enabled) { selectedSnapshot = trail; selectedId = trail.id }
                            .testTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + trail.id)) {
                            TrailPhotoHero(trail, Modifier.fillMaxWidth(), allowNetwork = allowNetwork)
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(trail.name, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, color = BrowserInk)
                                TrailFacts(trail, distanceUnit)
                            }
                        }
                    }
                    if (discovery?.moreAvailable == true) item(key = "more") {
                        Text("Showing a selection near this area. Search a nearby city to explore further.",
                            fontSize = 13.sp, color = BrowserInk.copy(alpha = .75f))
                    }
                }
            }
        }
    }
    if (filtersOpen) TrailFilterDialog(filters, distanceUnit, onChange = { filters = it }, onDismiss = { filtersOpen = false })
    if (creditsOpen) TrailCreditsDialog(if (selected != null) listOf(selected) else results, onDismiss = { creditsOpen = false })
}

@Composable
private fun TrailFacts(trail: TrailPlace, unit: DistanceUnit) {
    val location = listOf(trail.city, trail.state).filter { it.isNotBlank() }.joinToString(", ")
    val details = buildList {
        trail.difficulty?.let { add(it.label) }
        if (trail.surfaces.isNotEmpty()) add(trail.surfaces.joinToString(" / ") { it.label })
        trail.lengthMeters?.takeIf { it > 0 && it.isFinite() }?.let {
            add(String.format(Locale.US, "%.1f %s", it / if (unit == DistanceUnit.MILES) 1609.344 else 1000.0,
                if (unit == DistanceUnit.MILES) "mi" else "km"))
        }
        trail.elevationGainMeters?.takeIf { it >= 0 && it.isFinite() }?.let {
            add("${(if (unit == DistanceUnit.MILES) it / .3048 else it).toInt()} ${if (unit == DistanceUnit.MILES) "ft" else "m"} gain")
        }
        if (trail.isSegment) add("Trail segment")
    }.joinToString(" · ")
    if (details.isNotEmpty()) Text(details, color = BrowserInk, fontSize = 14.sp)
    if (location.isNotEmpty()) Text(location, color = BrowserInk.copy(alpha = .75f), fontSize = 14.sp)
}

@Composable
private fun TrailFilterDialog(filters: TrailFilters, unit: DistanceUnit, onChange: (TrailFilters) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filter trails") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Difficulty", fontWeight = FontWeight.Bold)
                FilterChoice("Any difficulty", filters.difficulty == null) { onChange(filters.copy(difficulty = null)) }
                TrailDifficulty.entries.forEach { value -> FilterChoice(value.label, filters.difficulty == value) { onChange(filters.copy(difficulty = value)) } }
                HorizontalDivider()
                Text("Surface", fontWeight = FontWeight.Bold)
                FilterChoice("Any surface", filters.surface == null) { onChange(filters.copy(surface = null)) }
                TrailSurface.entries.forEach { value -> FilterChoice(value.label, filters.surface == value) { onChange(filters.copy(surface = value)) } }
                HorizontalDivider()
                Text("Elevation gain", fontWeight = FontWeight.Bold)
                TrailElevation.entries.forEach { value ->
                    val label = if (unit == DistanceUnit.KILOMETERS) when (value) {
                        TrailElevation.ANY -> value.label
                        TrailElevation.LOW -> "Under 91 m"
                        TrailElevation.MEDIUM -> "91–305 m"
                        TrailElevation.HIGH -> "Over 305 m"
                    } else value.label
                    FilterChoice(label, filters.elevation == value) { onChange(filters.copy(elevation = value)) }
                }
                Text("Specific filters show only trails with those details listed.", fontSize = 12.sp)
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Show trails") } },
        dismissButton = { TextButton(onClick = { onChange(TrailFilters()) }) { Text("Reset") } })
}

@Composable
private fun FilterChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun TrailCreditsDialog(trails: List<TrailPlace>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Trail & photo credits") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Nearby trail paths: USGS Trails, public-domain federal data. Coverage and trail details vary. Check park information for current access.")
            TextButton(onClick = { openTrailLink(context, "https://www.usgs.gov/national-digital-trails") }) { Text("USGS National Digital Trails") }
            Text("City locations: U.S. Census Bureau, 2025 Gazetteer. Search areas stay in a private seven-day cache on this phone.")
            Text("Maps: OpenFreeMap / OpenMapTiles / © OpenStreetMap contributors.")
            TextButton(onClick = { openTrailLink(context, "https://www.openstreetmap.org/copyright") }) { Text("Map attribution") }
            trails.distinctBy { it.photo?.sourceUrl ?: it.id }.forEach { trail ->
                if (trail.photo != null || trail.sourceUrl.isNotBlank()) {
                    HorizontalDivider()
                    Text(trail.name, fontWeight = FontWeight.SemiBold)
                    trail.photo?.let { photo ->
                        Text("${photo.credit} · ${photo.license}")
                        if (photo.sourceUrl.startsWith("https://")) TextButton(onClick = { openTrailLink(context, photo.sourceUrl) }) { Text("Original photo") }
                        if (photo.licenseUrl.startsWith("https://")) TextButton(onClick = { openTrailLink(context, photo.licenseUrl) }) { Text("Photo license") }
                    }
                    if (trail.sourceUrl.startsWith("https://")) TextButton(onClick = { openTrailLink(context, trail.sourceUrl) }) { Text(trail.sourceName.ifBlank { "Trail information source" }) }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

private fun openTrailLink(context: android.content.Context, uri: String) {
    if (!uri.startsWith("https://") && !uri.startsWith("geo:")) return
    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
    catch (_: ActivityNotFoundException) { Toast.makeText(context, "No app is available to open this link.", Toast.LENGTH_LONG).show() }
    catch (_: SecurityException) { Toast.makeText(context, "This link couldn't open.", Toast.LENGTH_LONG).show() }
}
