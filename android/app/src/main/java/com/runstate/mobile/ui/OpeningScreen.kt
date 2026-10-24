package com.runstate.mobile.ui

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag as semanticTestTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.runstate.mobile.R
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val OPENING_VISIBLE_MILLIS = 4_800L
internal const val OPENING_SCREEN_TAG = "openingScreen"
internal const val OPENING_FIRST_FRAME_TAG = "openingFirstFrame"
private const val OPENING_GAP_DP = 6f
private val OpeningFont = FontFamily(Font(R.font.barlow_condensed_medium, FontWeight.Medium))
private val OpeningArrival = CubicBezierEasing(.19f, 1f, .22f, 1f)

private data class OpeningPhoto(val id: String, val widthDp: Float, val verticalPosition: Float = .5f)

// Manley's saved browser arrangement, with only the three declined photos removed and
// the two explicitly approved free replacements in the same positions. Width travels
// with each photograph, just as it did in the browser's arrange-photos tool.
private val OpeningRows = listOf(
    listOf(OpeningPhoto("pick-23", 114f), OpeningPhoto("city-stride", 114f),
        OpeningPhoto("track-partners", 126f, .76f), OpeningPhoto("motion-city", 114f),
        OpeningPhoto("pick-11", 114f), OpeningPhoto("motion-green", 114f)),
    listOf(OpeningPhoto("pick-06", 114f), OpeningPhoto("pick-03", 240f),
        OpeningPhoto("forest-run", 114f), OpeningPhoto("pick-14", 240f),
        OpeningPhoto("pick-10", 240f), OpeningPhoto("orange-bottle", 115f)),
    listOf(OpeningPhoto("headphone-runner", 114f, .7f), OpeningPhoto("pick-13", 114f),
        OpeningPhoto("pick-19", 136f), OpeningPhoto("race-celebration", 244f),
        OpeningPhoto("pick-21", 124f)),
    listOf(OpeningPhoto("pick-22", 114f), OpeningPhoto("pick-07", 114f),
        OpeningPhoto("purple-sneakers", 230f, 1f), OpeningPhoto("pick-12", 114f),
        OpeningPhoto("pick-04", 114f), OpeningPhoto("pick-24", 114f)),
    listOf(OpeningPhoto("pick-01", 244f), OpeningPhoto("pick-02", 242f),
        OpeningPhoto("pick-25", 114f), OpeningPhoto("blue-shoes", 208f)),
    listOf(OpeningPhoto("pick-15", 240f), OpeningPhoto("pick-20", 114f),
        OpeningPhoto("hydration", 190f), OpeningPhoto("run-friends", 238f),
        OpeningPhoto("pick-09", 114f), OpeningPhoto("motion-street", 114f))
)
private val OpeningSpeeds = floatArrayOf(51.75f, 56.0625f, 53.1875f, 57.5f, 52.46875f, 56.0625f)
private val OpeningPhases = floatArrayOf(5f, 2f, 2f, 2f, 3f, 3f)
private const val OPENING_START_STEP_SECONDS = 8.0

private fun nextOpeningStartSeconds(context: Context): Double {
    // Presentation only: a cold launch must not reset the wall to its original photos.
    val preferences = context.applicationContext.getSharedPreferences("opening_appearance", Context.MODE_PRIVATE)
    val previous = preferences.getLong("entrance", 0L).coerceAtLeast(0L)
    val entrance = if (previous < 1_000_000L) previous + 1L else 1L
    preferences.edit().putLong("entrance", entrance).apply()
    return entrance * OPENING_START_STEP_SECONDS
}

/**
 * A brief, offline opening. The journey owner decides whether entry is eligible; this
 * component never reads a run, launches setup, or changes navigation by itself.
 * Visible time and motion stop together when the Activity is no longer RESUMED.
 */
@Composable
internal fun OpeningScreen(
    onFinished: () -> Unit,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier,
    motionEnabledOverride: Boolean? = null,
    photoLoader: suspend (AssetManager) -> Map<String, ImageBitmap> = ::loadOpeningPhotos
) {
    val context = LocalContext.current
    val assets = context.assets
    val currentOnFinished by rememberUpdatedState(onFinished)
    var finished by rememberSaveable { mutableStateOf(false) }
    var visibleNanos by rememberSaveable { mutableLongStateOf(0L) }
    // Allocate once per eligible opening; rotation and recomposition keep this same phase.
    val startingSeconds = rememberSaveable { nextOpeningStartSeconds(context) }
    val systemMotionEnabled = rememberOpeningMotionEnabled(context)
    val motionEnabled = motionEnabledOverride ?: systemMotionEnabled
    val images by produceState<Map<String, ImageBitmap>?>(null, assets, photoLoader) {
        // Decoding happens away from Compose's first frame. Bundled photos never need a
        // connection; a damaged/missing asset cannot prevent leaving this screen.
        value = null
        value = withTimeoutOrNull(1_500L) {
            photoLoader(assets)
        } ?: emptyMap()
    }
    val finish = {
        if (!finished) {
            finished = true
            currentOnFinished()
        }
    }
    val currentFinish by rememberUpdatedState(finish)
    val photosReady = images != null
    val allPhotosDecoded = images?.size == OpeningRows.sumOf { it.size }

    OpeningSystemBars()
    BackHandler(enabled = !finished, onBack = finish)
    LaunchedEffect(lifecycle, photosReady) {
        if (!photosReady) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (images.isNullOrEmpty()) {
                currentFinish()
                return@repeatOnLifecycle
            }
            // A new frame anchor excludes time spent in another app or with the screen
            // locked. Both a skip and the timer share one once-only completion flag.
            var previousFrame: Long? = null
            while (isActive && !finished) {
                withFrameNanos { frame ->
                    previousFrame?.let { previous -> visibleNanos += (frame - previous).coerceAtLeast(0L) }
                    previousFrame = frame
                    if (visibleNanos >= OPENING_VISIBLE_MILLIS * 1_000_000L) currentFinish()
                }
            }
        }
    }

    val elapsedSeconds = visibleNanos / 1_000_000_000f
    val arrival = if (!motionEnabled) 1f else
        OpeningArrival.transform(((elapsedSeconds - .6f) / .85f).coerceIn(0f, 1f))
    val photoFilter = remember {
        val saturation = ColorMatrix().apply { setToSaturation(1.08f) }
        // Match the approved preview's mild contrast adjustment, not a recoloring tint.
        val contrast = ColorMatrix(floatArrayOf(
            1.02f, 0f, 0f, 0f, -2.55f,
            0f, 1.02f, 0f, 0f, -2.55f,
            0f, 0f, 1.02f, 0f, -2.55f,
            0f, 0f, 0f, 1f, 0f
        ))
        contrast.timesAssign(saturation)
        ColorFilter.colorMatrix(contrast)
    }
    BoxWithConstraints(
        modifier.fillMaxSize().background(Color(0xFF17201B))
            .testTag(OPENING_SCREEN_TAG)
            .semantics { contentDescription = "RunState opening screen" }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = "Skip opening screen",
                onClick = finish
            ),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        // The wordmark is artwork; keep it inside narrow windows and large-font devices.
        // The full-screen accessible action provides the readable app name independently.
        val wordmarkScale = min(1f, maxWidth.value / 390f)
        if (images.isNullOrEmpty()) {
            // This bundled still is a native capture of the approved photo wall, including
            // its shading. It draws synchronously so decoding cannot expose a blank frame.
            Box(Modifier.fillMaxSize().testTag(
                if (photosReady) "openingPhotosUnavailable" else "openingPhotosLoading"
            )) {
                Image(
                    painter = painterResource(R.drawable.opening_first_frame),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag(OPENING_FIRST_FRAME_TAG)
                )
            }
        } else {
            Canvas(Modifier.fillMaxSize().clearAndSetSemantics {
                // Readiness is exposed only as a test tag; decorative photos are not announced.
                semanticTestTag = when {
                    allPhotosDecoded -> "openingPhotosReady"
                    photosReady -> "openingPhotosUnavailable"
                    else -> "openingPhotosLoading"
                }
            }) {
                val gap = OPENING_GAP_DP.dp.toPx()
                val overhang = 36.dp.toPx()
                val rowHeight = (size.height + overhang * 2 - gap * 5) / 6
                val seconds = if (motionEnabled) elapsedSeconds else 0f
                OpeningRows.forEachIndexed { index, photos ->
                    val width = photos.sumOf { (it.widthDp + OPENING_GAP_DP).toDouble() }.toFloat().dp.toPx()
                    val travel = ((OpeningSpeeds[index].dp.toPx() *
                        (startingSeconds + seconds + OpeningPhases[index])) % width).toFloat()
                    val offset = if (index % 2 == 0) -travel else travel - width
                    val top = -overhang + index * (rowHeight + gap)
                    clipRect(0f, max(0f, top), size.width, min(size.height, top + rowHeight)) {
                        // More than two copies are only needed in an unusually wide window.
                        repeat(ceil(size.width / width).toInt() + 2) { copy ->
                            var left = -20.dp.toPx() + offset + copy * width
                            photos.forEach { photo ->
                                val tileWidth = photo.widthDp.dp.toPx()
                                if (left + tileWidth > 0 && left < size.width) {
                                    images?.get(photo.id)?.let { bitmap ->
                                        drawOpeningPhoto(bitmap, left, top, tileWidth, rowHeight,
                                            photo.verticalPosition, photoFilter)
                                    }
                                }
                                left += tileWidth + gap
                            }
                        }
                    }
                }
                val shade = Color(0xFF0A130F)
                drawRect(Brush.verticalGradient(
                    0f to shade.copy(alpha = .48f), .16f to shade.copy(alpha = .09f),
                    .29f to shade.copy(alpha = .14f), .49f to shade.copy(alpha = .65f),
                    .56f to shade.copy(alpha = .51f), .73f to shade.copy(alpha = .12f),
                    1f to shade.copy(alpha = .48f)
                ))
            }
        }
        Text(
            text = buildAnnotatedString {
                // Approved October 22: a medium purple R and light orange n echo the collage's
                // purple sneakers and orange bottle; an olive a (the app's olive, brightened to
                // read on the dark band) completes the spaced accents. Other letters keep cream/sage.
                withStyle(SpanStyle(color = Color(0xFF8F6FE3))) { append("R") }
                withStyle(SpanStyle(color = Color(0xFFFFFDF6))) { append("u") }
                withStyle(SpanStyle(color = Color(0xFFF9A869))) { append("n") }
                withStyle(SpanStyle(color = Color(0xFFE2EDC9))) { append("St") }
                withStyle(SpanStyle(color = Color(0xFF7F9C54))) { append("a") }
                withStyle(SpanStyle(color = Color(0xFFE2EDC9))) { append("te") }
            },
            fontFamily = OpeningFont,
            fontWeight = FontWeight.Medium,
            fontSize = (98f * wordmarkScale / density.fontScale).sp,
            letterSpacing = (-3.8f * wordmarkScale / density.fontScale).sp,
            maxLines = 1,
            style = androidx.compose.ui.text.TextStyle(
                textGeometricTransform = TextGeometricTransform(skewX = -.08749f),
                shadow = Shadow(Color(0x40050E09), Offset(0f, with(density) { 4.dp.toPx() }),
                    with(density) { 28.dp.toPx() })
            ),
            modifier = Modifier.clearAndSetSemantics {}.graphicsLayer {
                alpha = arrival
                translationY = (-8f + 14f * (1f - arrival)).dp.toPx()
                scaleX = .96f + .04f * arrival
                scaleY = scaleX
            }
        )
        Text(
            text = "Tap to skip",
            color = Color(0xFFFFFDF6).copy(alpha = .78f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = .2.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
                .then(if (maxHeight >= 240.dp) Modifier.navigationBarsPadding() else Modifier)
                .padding(bottom = if (maxHeight >= 240.dp) 18.dp else 8.dp)
        )
    }
}

private fun DrawScope.drawOpeningPhoto(
    image: ImageBitmap,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    verticalPosition: Float,
    filter: ColorFilter
) {
    val scale = max(width / image.width, height / image.height)
    val sourceWidth = (width / scale).roundToInt().coerceIn(1, image.width)
    val sourceHeight = (height / scale).roundToInt().coerceIn(1, image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - sourceWidth) / 2,
            ((image.height - sourceHeight) * verticalPosition).roundToInt()),
        srcSize = IntSize(sourceWidth, sourceHeight),
        dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
        dstSize = IntSize(width.roundToInt().coerceAtLeast(1), height.roundToInt().coerceAtLeast(1)),
        colorFilter = filter,
        filterQuality = FilterQuality.Medium
    )
}

/** The loader seam lets first-frame tests hold decoding without changing visible timing. */
internal suspend fun loadOpeningPhotos(assets: AssetManager): Map<String, ImageBitmap> =
    withContext(Dispatchers.IO) {
        OpeningRows.flatten().mapNotNull { photo ->
            ensureActive()
            decodeOpeningPhoto(assets, photo.id)?.let { photo.id to it }
        }.toMap()
    }

private fun decodeOpeningPhoto(assets: AssetManager, id: String): ImageBitmap? = try {
    val path = "opening/$id.webp"
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    // The collage's small tiles do not need full source-resolution bitmap allocations.
    while (max(bounds.outWidth, bounds.outHeight) / sample > 640) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
} catch (_: IOException) {
    null
}

@Composable
private fun rememberOpeningMotionEnabled(context: Context): Boolean {
    var enabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { enabled = ValueAnimator.areAnimatorsEnabled() }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
private fun OpeningSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (previousStatus != null) controller?.isAppearanceLightStatusBars = previousStatus
            if (previousNavigation != null) controller?.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}
