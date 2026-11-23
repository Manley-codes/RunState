package com.runstate.mobile.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R

internal const val START_TRAILS_BACKGROUND_TAG = "startTrailsBackground"
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
    // Keep boundaries and hit targets stationary while each photo/map scene drifts inside them.
    val drift = if (moving) {
        val transition = rememberInfiniteTransition(label = "trail scenery")
        transition.animateFloat(-1f, 1f,
            infiniteRepeatable(tween(28_000, easing = LinearEasing), RepeatMode.Reverse),
            label = "slow horizontal drift")
    } else remember { mutableStateOf(0f) }
    val driftPx = with(LocalDensity.current) { 7.dp.toPx() }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF6F8F6))
        .testTag(START_TRAILS_BACKGROUND_TAG).clipToBounds()) {
        val available = (maxHeight - 64.dp).coerceAtLeast(200.dp)
        val upper = upperContentBottom.coerceIn(0.dp, available * .46f)
        val galleryBottom = if (controlsTop > upper + 160.dp) controlsTop.coerceAtMost(available) else available
        val bandHeight = ((galleryBottom - upper - 12.dp) / 4).coerceIn(40.dp, 104.dp)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            HoustonStartTrails.forEachIndexed { index, trail ->
                val height = when (index) {
                    0 -> upper + bandHeight
                    3 -> (available - upper - bandHeight * 3 - 12.dp).coerceAtLeast(bandHeight)
                    else -> bandHeight
                }
                Box(Modifier.fillMaxWidth().height(height).clipToBounds()
                    .clickable(enabled = enabled, role = Role.Button, onClick = { onExplore(trail) })
                    .testTag(START_TRAIL_BAND_TAG_PREFIX + trail.id)
                    .semantics { contentDescription = "Explore ${trail.name}" }) {
                    Image(painterResource(trail.map), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().graphicsLayer {
                            scaleX = 1.07f; scaleY = 1.07f
                            translationX = drift.value * driftPx * if (index % 2 == 0) 1f else -1f
                        })
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = .35f)))
                    Row(Modifier.align(if (index == 3) Alignment.TopStart else Alignment.BottomStart)
                        .fillMaxWidth().height(bandHeight),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(bandHeight.coerceAtMost(104.dp)).clipToBounds()) {
                            Image(painterResource(trail.photo), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = 1.08f; scaleY = 1.08f
                                    translationX = drift.value * driftPx * .35f
                                })
                            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = .16f)))))
                        }
                        Text(trail.name, color = TrailInk, fontSize = 12.sp, lineHeight = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("startTrailLabel:" + trail.id)
                                .padding(start = 8.dp, end = 12.dp)
                                .background(Color.White.copy(alpha = .84f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 7.dp, vertical = 4.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StartTrailsSheet(
    trail: StartTrail?,
    enabled: Boolean,
    onExplore: (StartTrail) -> Unit,
    onUse: (StartTrail) -> Unit,
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
                Button(onClick = { onUse(trail) }, enabled = enabled,
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
