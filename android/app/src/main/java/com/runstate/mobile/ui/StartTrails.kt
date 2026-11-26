package com.runstate.mobile.ui

import androidx.annotation.DrawableRes
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.snapshotFlow
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val START_TRAILS_BACKGROUND_TAG = "startTrailsBackground"
internal const val START_TRAILS_GALLERY_TAG = "startTrailsGallery"
internal const val START_TRAIL_PHOTO_TAG_PREFIX = "startTrailPhoto:"
internal const val START_TRAIL_BAND_TAG_PREFIX = "startTrailBand:"
internal const val START_TRAILS_BROWSE_TAG = "startTrailsBrowse"
internal const val START_TRAIL_SHEET_TAG = "startTrailSheet"
internal const val START_TRAIL_USE_TAG = "startTrailUse"
internal const val START_TRAILS_LIST_TAG = "startTrailsList"
internal const val START_TRAIL_LIST_ITEM_TAG_PREFIX = "startTrailListItem:"
private val TrailInk = Color(0xFF293D38)

/** A small, bundled place collection. Area maps are not recorded routes or navigation. */
internal data class StartTrail(
    val id: String,
    val name: String,
    val description: String,
    @param:DrawableRes val photo: Int,
    @param:DrawableRes val map: Int,
    val website: String,
    val photoCredit: String,
    val photoSource: String,
    val photoLicense: String,
    val licenseUrl: String
) { val planLabel: String get() = name }

internal val HoustonStartTrails = listOf(
    StartTrail("memorial", "Memorial Park", "Seymour Lieberman Trail",
        R.drawable.trail_memorial_photo, R.drawable.trail_memorial_map,
        "https://www.memorialparkconservancy.org/visit/things_to_do/trails/",
        "Beverly Moseley / USDA NRCS Texas", "https://commons.wikimedia.org/wiki/File:Joggers_along_the_Seymour_Lieberman_Exer-Trail_at_Memorial_Park_in_Houston.NRCS_photo_by_Beverly_Moseley._(25116673655).jpg",
        "CC BY 2.0", "https://creativecommons.org/licenses/by/2.0/"),
    StartTrail("hermann", "Hermann Park", "Marvin Taylor Trail",
        R.drawable.trail_hermann_photo, R.drawable.trail_hermann_map,
        "https://hermannpark.org/place/marvin-taylor-trail/",
        "WhisperToMe", "https://commons.wikimedia.org/wiki/File:MarvinTaylorTrailHoustonTX.JPG",
        "Public domain", "https://commons.wikimedia.org/wiki/File:MarvinTaylorTrailHoustonTX.JPG#Licensing"),
    StartTrail("buffalo_bayou", "Buffalo Bayou Park", "Trails along the bayou",
        R.drawable.trail_buffalo_bayou_photo, R.drawable.trail_buffalo_bayou_map,
        "https://buffalobayou.org/location/buffalo-bayou-park/",
        "Michael Barera", "https://commons.wikimedia.org/wiki/File:Houston_May_2022_13_(Buffalo_Bayou_Park_and_skyline).jpg",
        "CC BY-SA 4.0", "https://creativecommons.org/licenses/by-sa/4.0/"),
    StartTrail("terry_hershey", "Terry Hershey Park", "Wooded trails along Buffalo Bayou",
        R.drawable.trail_terry_hershey_photo, R.drawable.trail_terry_hershey_map,
        "https://cp4.harriscountytx.gov/Explore/Parks/Precinct-4-Parks/terry-hershey-park",
        "Scott Ehardt", "https://commons.wikimedia.org/wiki/File:Terry_Hershey_Park_trail.jpg",
        "Public domain", "https://commons.wikimedia.org/wiki/File:Terry_Hershey_Park_trail.jpg")
)

@Composable
internal fun StartTrailsBackground(
    upperContentBottom: Dp,
    controlsTop: Dp,
    moving: Boolean,
    enabled: Boolean,
    onExplore: (StartTrail) -> Unit,
    modifier: Modifier = Modifier
) {
    val accessibility = LocalContext.current.getSystemService(AccessibilityManager::class.java)
    var touchExploration by remember(accessibility) {
        mutableStateOf(accessibility?.isTouchExplorationEnabled == true)
    }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    val cycleSize = HoustonStartTrails.size
    val centerCycle = cycleSize * 3
    val flow = rememberLazyListState(initialFirstVisibleItemIndex = centerCycle)
    var initialized by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var resumeAfter by remember { mutableLongStateOf(0L) }
    // Lazy-list placement is integer-pixel based. Retain the fraction in a drawing layer so
    // this intentionally slow motion still advances every frame, including across pauses.
    var fractionalScrollPx by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF6F8F6))
        .testTag(START_TRAILS_BACKGROUND_TAG).clipToBounds()) {
        val available = (maxHeight - 64.dp).coerceAtLeast(200.dp)
        val upper = upperContentBottom.coerceIn(0.dp, available * .46f)
        val galleryBottom = if (controlsTop > upper) controlsTop.coerceAtMost(available) else available
        val density = LocalDensity.current
        val upperPx = with(density) { upper.toPx() }
        val bottomPx = with(density) { galleryBottom.toPx() }
        val pitchPx = with(density) { 116.dp.toPx() }
        val insetPx = with(density) { 20.dp.toPx() }
        val photoPx = with(density) { 72.dp.toPx() }
        val speedPx = with(density) { 4.dp.toPx() }
        val itemCount = ceil(available.value / 116f).toInt() + cycleSize * 6
        // Keep a finite buffer on both sides; recenter only at rest, preserving pixel alignment.
        LaunchedEffect(upperPx, controlsTop) {
            if (!initialized && controlsTop > 0.dp) {
                val before = ceil(upperPx / pitchPx).toInt()
                flow.scrollToItem(centerCycle + Math.floorMod(-before, cycleSize),
                    (before * pitchPx - upperPx).roundToInt())
                initialized = true
            }
        }
        LaunchedEffect(flow) {
            snapshotFlow { Triple(flow.isScrollInProgress || touching,
                flow.firstVisibleItemIndex, flow.firstVisibleItemScrollOffset) }
                .collect { (busy, index, offset) ->
                if (!busy && initialized &&
                    index !in (centerCycle - cycleSize) until (centerCycle + cycleSize)) {
                    flow.requestScrollToItem(centerCycle + index % cycleSize, offset)
                }
            }
        }
        LaunchedEffect(moving, enabled, touchExploration, initialized) {
            if (!moving || !enabled || touchExploration || !initialized) return@LaunchedEffect
            var previous = 0L
            while (isActive) {
                val now = withInfiniteAnimationFrameNanos { it }
                val seconds = if (previous == 0L) 0f else ((now - previous) / 1_000_000_000f).coerceAtMost(.05f)
                previous = now
                if (!touching && !flow.isScrollInProgress && SystemClock.uptimeMillis() >= resumeAfter) {
                    val next = fractionalScrollPx + speedPx * seconds
                    val wholePixels = next.toInt()
                    val consumed = if (wholePixels > 0) flow.scrollBy(wholePixels.toFloat()) else 0f
                    fractionalScrollPx = (next - consumed).coerceIn(0f, 1f)
                }
            }
        }
        // Only clear photos can open details. Repeated/faded rows are decorative, and the finite
        // Houston trails sheet remains the accessible route to all four places.
        val selectable by remember(flow, upperPx, bottomPx, insetPx, photoPx) {
            derivedStateOf {
                flow.layoutInfo.visibleItemsInfo
                    .filter { it.offset - fractionalScrollPx + insetPx >= upperPx &&
                        it.offset - fractionalScrollPx + insetPx + photoPx <= bottomPx }
                    .groupBy { it.index % cycleSize }
                    .values.map { copies -> copies.minBy { abs(it.offset + 56f * density.density - (upperPx + bottomPx) / 2) }.index }
                    .toSet()
            }
        }
        LazyColumn(state = flow, userScrollEnabled = enabled && !touchExploration,
            modifier = Modifier.fillMaxWidth().height(available + 1.dp)
                .graphicsLayer { translationY = -fractionalScrollPx }
                .then(if (touchExploration) Modifier.clearAndSetSemantics { } else Modifier)
                .pointerInput(enabled) {
                    if (enabled) awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        touching = true
                        try {
                            do { val event = awaitPointerEvent(PointerEventPass.Initial) }
                            while (event.changes.any { it.pressed })
                        } finally {
                            touching = false
                            resumeAfter = SystemClock.uptimeMillis() + 12_000L
                        }
                    }
                },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(itemCount, key = { it }) { index ->
                val trail = HoustonStartTrails[index % cycleSize]
                val clear = index in selectable && !touchExploration
                Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))
                    .then(if (clear) Modifier
                        .clickable(enabled = enabled, role = Role.Button, onClick = { onExplore(trail) })
                        .testTag(START_TRAIL_BAND_TAG_PREFIX + trail.id)
                        .semantics { contentDescription = "Explore ${trail.name}" }
                    else Modifier.clearAndSetSemantics { })) {
                    StartTrailMap(trail, Modifier.fillMaxSize())
                    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(72.dp)
                            .then(if (clear) Modifier.testTag(START_TRAIL_PHOTO_TAG_PREFIX + trail.id) else Modifier)
                            .clip(RoundedCornerShape(10.dp))) {
                            Image(painterResource(trail.photo), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = 1.08f; scaleY = 1.08f
                                })
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .16f)))))
                        }
                        Text(trail.name, color = TrailInk, fontSize = 12.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.then(if (clear) Modifier.testTag("startTrailLabel:" + trail.id) else Modifier)
                                .padding(start = 8.dp, end = 12.dp)
                                .background(Color.White.copy(alpha = .84f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
        }
        // One screen-anchored fade softens the same stream behind controls without a hard edge.
        val topFade = (upper.value / available.value).coerceIn(0f, 1f)
        val bottomFade = (galleryBottom.value / available.value).coerceIn(topFade, 1f)
        Box(Modifier.fillMaxWidth().height(available).background(Brush.verticalGradient(
            0f to Color(0xFFF6F8F6).copy(alpha = .88f),
            (topFade - .04f).coerceAtLeast(0f) to Color(0xFFF6F8F6).copy(alpha = .88f),
            topFade to Color.Transparent,
            (bottomFade - .03f).coerceAtLeast(topFade) to Color.Transparent,
            (bottomFade + .07f).coerceAtMost(1f) to Color(0xFFF6F8F6).copy(alpha = .88f),
            1f to Color(0xFFF6F8F6).copy(alpha = .92f)
        )))
        Box(Modifier.offset(y = upper).fillMaxWidth().height(galleryBottom - upper)
            .testTag(START_TRAILS_GALLERY_TAG))
    }
}

@Composable
private fun StartTrailMap(trail: StartTrail, modifier: Modifier) {
    Box(modifier.clipToBounds()) {
        Image(painterResource(trail.map), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.07f; scaleY = 1.07f
            })
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .35f)))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTrailsSheet(
    trail: StartTrail?,
    enabled: Boolean,
    onExplore: (StartTrail) -> Unit,
    onUse: ((StartTrail) -> Unit)? = null,
    onOpenMap: (() -> Unit)? = null,
    mapNeedsLocation: Boolean = false,
    mapStatus: String? = null,
    onDismiss: () -> Unit
) {
    val uri = LocalUriHandler.current
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFFF8FAF8),
        modifier = Modifier.testTag(if (trail == null) START_TRAILS_LIST_TAG else START_TRAIL_SHEET_TAG)) {
        Column(Modifier.fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(trail?.name ?: "Houston trails", fontSize = 24.sp, lineHeight = 29.sp,
                    fontWeight = FontWeight.SemiBold, color = TrailInk, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close", color = TrailInk) }
            }
            key(trail?.id) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onOpenMap?.invoke() }, enabled = enabled && onOpenMap != null,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag(if (mapNeedsLocation) START_MAP_LOCATION_ACCESS_TAG else "startExpandMap")
                        .semantics {
                            contentDescription = if (mapNeedsLocation) "Enable location" else "Open live map"
                            val status = if (mapNeedsLocation) "Location access needed" else mapStatus
                            if (status != null) stateDescription = status
                        }) {
                    Text(if (mapNeedsLocation) "Enable location" else "Open map", color = TrailInk)
                }
            if (trail == null) {
                HoustonStartTrails.forEach { place ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Color.White).clickable(enabled = enabled) { onExplore(place) }
                        .testTag(START_TRAIL_LIST_ITEM_TAG_PREFIX + place.id).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Image(painterResource(place.photo), null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(place.name, color = TrailInk, fontWeight = FontWeight.SemiBold)
                            Text(place.description, color = TrailInk.copy(alpha = .75f), fontSize = 12.sp)
                        }
                    }
                }
                TextButton(onClick = { uri.openUri("https://www.visithoustontexas.com/blog/post/healthy-in-houston/") }) {
                    Text("More places around Houston ↗", color = TrailInk)
                }
            } else {
                Text(trail.description, color = TrailInk)
                Image(painterResource(trail.photo), null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(12.dp)))
                Image(painterResource(trail.map), "Area map of ${trail.name}", contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(12.dp)))
                Text("Area overview · not a guided route", color = TrailInk, fontSize = 12.sp)
                if (onUse != null) Button(onClick = { onUse(trail) }, enabled = enabled,
                    colors = ButtonDefaults.buttonColors(containerColor = TrailInk),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(START_TRAIL_USE_TAG)) {
                    Text("Use trail name in plan")
                }
                TextButton(onClick = { uri.openUri(trail.website) }) { Text("Park information ↗", color = TrailInk) }
                Text("Photo: ${trail.photoCredit} · ${trail.photoLicense}. Cropped to fit.",
                    color = TrailInk.copy(alpha = .8f), fontSize = 11.sp, lineHeight = 15.sp)
                Row {
                    TextButton(onClick = { uri.openUri(trail.photoSource) }) { Text("Photo source", color = TrailInk) }
                    TextButton(onClick = { uri.openUri(trail.licenseUrl) }) { Text("Photo license", color = TrailInk) }
                }
            }
            TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }) {
                Text("Map data © OpenStreetMap contributors", color = TrailInk, fontSize = 11.sp)
            }
            }
            }
        }
    }
}

@Composable
internal fun StartTrailMapCredit(modifier: Modifier = Modifier) {
    val uri = LocalUriHandler.current
    TextButton(onClick = { uri.openUri("https://www.openstreetmap.org/copyright") }, modifier = modifier) {
        Text("© OpenStreetMap", color = TrailInk, fontSize = 10.sp,
            modifier = Modifier.background(Color.White.copy(alpha = .86f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
