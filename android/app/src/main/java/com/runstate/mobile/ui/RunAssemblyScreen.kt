package com.runstate.mobile.ui

import android.animation.ValueAnimator
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.runstate.mobile.R
import com.runstate.mobile.location.GpsSignalStatus
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.settings.RunMusicPlayerLayout
import kotlinx.coroutines.currentCoroutineContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val ACTIVE_RUN_SPLIT_TAG = "activeRunSplit"
internal const val ACTIVE_RUN_MUSIC_TAG = "activeRunMusic"
internal const val ACTIVE_RUN_FINISH_TAG = "activeRunFinish"

private val AssemblyNumberFont = FontFamily(Font(R.font.anton_regular))
private val AssemblyEase = CubicBezierEasing(.22f, .85f, .16f, 1f)
private val AssemblyPull = CubicBezierEasing(.48f, .04f, .85f, .6f)
private val AssemblySettle = CubicBezierEasing(.18f, .75f, .28f, 1f)
private val AssemblyArcEase = CubicBezierEasing(.42f, 0f, .58f, 1f)
// Match the accepted browser's blendHex interpolation, including its rounded sRGB channels.
// Compose Color.lerp uses Oklab, which subtly changes these carefully reviewed transitions.
private fun assemblyRgbBlend(from: Color, to: Color, amount: Float): Color {
    fun channel(a: Float, b: Float) = (mix(a, b, amount) * 255f + .5f).toInt().coerceIn(0, 255)
    return Color(channel(from.red, to.red), channel(from.green, to.green), channel(from.blue, to.blue))
}
private val AssemblyDayBarColors = List(76) { index ->
    val position = (index - 12 + 76) % 76
    val stops = listOf(0 to Color(0xFF699E78), 24 to Color(0xFFA080C2),
        50 to Color(0xFFC6A044), 76 to Color(0xFF699E78))
    val upper = stops.indexOfFirst { it.first > position }
    val start = stops[upper - 1]
    val end = stops[upper]
    val blend = (.5 - .5 * cos(PI * (position - start.first) / (end.first - start.first))).toFloat()
    assemblyRgbBlend(start.second, end.second, blend)
}

private data class AssemblyColors(val day: Boolean) {
    val ink = if (day) Color(0xFF344F50) else Color(0xFFF5F5F7)
    val canvas = if (day) Color(0xFFF5FCFF) else Color(0xFF101113)
    val brand = if (day) Color(0xFF23687B) else ink
    val muted = if (day) Color(0xFF566E7A) else Color(0xFFA9ACB8)
    val label = if (day) Color(0xFF537078) else Color(0xFFA7AAB7)
    val line = if (day) Color(0xFFBED4DD) else Color(0xFF35363C)
    val grid = if (day) Color(0xFF4E7E8F) else Color(0xFFB8BACC)
    val ring = if (day) Color(0xFF5E8E9F) else Color(0xFFB8B9C4)
    val panel = if (day) Color.Transparent else Color(0xFF1B1D20)
    val musicPanel = if (day) Color.Transparent else Color(0xFFBFC2C2)
    val musicInk = if (day) Color(0xFF325E56) else Color(0xFF191D1E)
    val accent = if (day) Color(0xFF239B78) else Color(0xFF795BFA)
    val pause = if (day) Color(0xFFEAC956) else Color(0xFF7353EF)
    val gps = if (day) Color(0xFF397B6B) else muted
    val map = if (day) Color(0xFF297795) else muted
    val cancel = if (day) Color(0xFF695C83) else Color(0xFF9DA1AE)
    val warning = if (day) Color(0xFF914831) else Color(0xFFFFBD9F)
}

private data class AssemblyBox(val x: Float, val y: Float, val w: Float, val h: Float)
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun fraction(value: Float) = value.coerceIn(0f, 1f)
private fun progress(time: Float, delay: Float, duration: Float, reduced: Boolean): Float =
    if (reduced) 1f else AssemblyEase.transform(fraction((time - delay) / duration))
private fun mixBox(a: AssemblyBox, b: AssemblyBox, t: Float) = AssemblyBox(
    mix(a.x, b.x, t), mix(a.y, b.y, t), mix(a.w, b.w, t), mix(a.h, b.h, t))
private fun landingScale(beat: Float, reduced: Boolean): Float = when {
    reduced -> 1f
    beat < .24f -> mix(1.48f, 1.025f, AssemblyPull.transform(fraction(beat / .24f)))
    else -> mix(1.025f, 1f, AssemblySettle.transform(fraction((beat - .24f) / .06f)))
}
private fun beatVisibility(beat: Float, reduced: Boolean) =
    if (reduced) 1f else 1f - AssemblyEase.transform(fraction((beat - .8f) / .08f))
private fun articulation(beat: Float, reduced: Boolean) =
    if (reduced) 0f else sin(PI * fraction((beat - .28f) / .49f)).toFloat()

/**
 * A single presentation from Tall frame to the live instrument. Its clocks only draw pixels:
 * the run owner still supplies every countdown digit, start, metric, pause and spoken cue.
 */
@Composable
internal fun RunAssemblyScreen(
    state: RunUiState,
    countdownDigit: Int?,
    metrics: RunMetricsDisplay?,
    day: Boolean,
    actionsEnabled: Boolean,
    actionFailed: Boolean,
    isGpsRun: Boolean,
    gpsServicePresent: Boolean,
    gpsSignalStatus: GpsSignalStatus,
    gpsMessage: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    onStartNow: () -> Unit,
    onResumeGpsTracking: () -> Unit,
    onOpenRunMap: () -> Unit,
    onOpenSpotify: () -> Unit,
    modifier: Modifier = Modifier,
    countdownTickKey: Long = 0L,
    countdownCadenceActive: Boolean = true,
    musicPlayer: SpotifyLivePlayerState = SpotifyLivePlayerState(),
    musicPlayerLayout: RunMusicPlayerLayout = RunMusicPlayerLayout.HEADPHONES,
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onToggleMusicPlayback: () -> Unit = {},
    onConnectMusic: () -> Unit = {},
    onOpenMusicTrack: (String?) -> Unit = {}
) {
    val counting = state == RunUiState.Countdown
    val starting = state == RunUiState.StartingRun
    val active = state == RunUiState.ActiveRunning || state == RunUiState.ActivePaused
    val paused = state == RunUiState.ActivePaused
    var musicOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(active) { if (!active) musicOpen = false }
    BackHandler(active && musicOpen) { musicOpen = false }
    val colors = remember(day) { AssemblyColors(day) }
    val motion = rememberAssemblyMotion()
    val reduced = !motion.systemEnabled
    val beat = remember { mutableFloatStateOf(.3f) }
    val clock = remember { mutableFloatStateOf(if (active) 1.19f else 0f) }
    val lastDigit = remember { mutableIntStateOf(countdownDigit ?: COUNTDOWN_SECONDS) }
    var receivedOwnerDigit by remember { mutableStateOf(countdownDigit != null) }
    SideEffect {
        if (countdownDigit != null) receivedOwnerDigit = true
        if (countdownDigit != null && countdownDigit > 0) lastDigit.intValue = countdownDigit
    }
    val visibleDigit = when {
        !counting -> null
        countdownDigit != null -> countdownDigit.takeIf { it > 0 }
        !receivedOwnerDigit -> COUNTDOWN_SECONDS
        else -> null
    }

    // The intro holds a settled ten. The owner publishes a separate cadence edge after the
    // joined "Starting in ten" phrase; no display-side timer can advance that speech contract.
    LaunchedEffect(countdownDigit, countdownTickKey, counting, countdownCadenceActive,
        motion.foreground, reduced) {
        beat.floatValue = .3f
        if (counting && countdownCadenceActive && motion.foreground && !reduced) {
            val start = withFrameNanos { it }
            do {
                val now = withFrameNanos { it }
                beat.floatValue = ((now - start) / 1_000_000_000f).coerceAtMost(1f)
            } while (beat.floatValue < 1f)
        }
    }
    LaunchedEffect(active, paused, motion.foreground, reduced) {
        if (!active) { clock.floatValue = 0f; return@LaunchedEffect }
        if (reduced) { clock.floatValue = 1.19f; return@LaunchedEffect }
        if (paused) {
            // An early pause must reveal the complete instrument before ambient motion stops.
            clock.floatValue = max(clock.floatValue, 1.19f)
            return@LaunchedEffect
        }
        if (!motion.foreground) return@LaunchedEffect
        val durationScale = currentCoroutineContext()[MotionDurationScale]?.scaleFactor ?: 1f
        if (durationScale == 0f) { clock.floatValue = 1.19f; return@LaunchedEffect }
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            // Foreground re-entry resumes decoration rather than simulating missed frames.
            clock.floatValue += min((now - previous) / 1_000_000_000f, .1f)
            previous = now
        }
    }
    val gpsLabel = when {
        !isGpsRun -> "Sample run"
        !active -> "GPS at start"
        !gpsServicePresent -> "GPS stopped"
        paused -> "GPS paused"
        gpsSignalStatus == GpsSignalStatus.RECORDING -> "GPS recording"
        gpsSignalStatus == GpsSignalStatus.WEAK -> "GPS weak"
        gpsSignalStatus == GpsSignalStatus.STALE -> "GPS unavailable"
        else -> "Finding GPS"
    }
    val showNotice = active && (actionFailed || !isGpsRun || !gpsServicePresent ||
        gpsSignalStatus != GpsSignalStatus.RECORDING || metrics?.sourceLabel?.contains("partial") == true)

    BoxWithConstraints(modifier.fillMaxSize().background(colors.canvas)
        .testTag(if (active) ACTIVE_RUN_SCREEN_TAG else COUNTDOWN_SCREEN_TAG)) {
    // Notices and the paused footer reduce the scroll viewport, not the artwork's anchor.
    val surfaceHeight = maxHeight.value
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val unit = maxWidth.value / 393f
            val extra = max(0f, LocalDensity.current.fontScale - 1.25f) * 70f
            val shift = extra * .7f
            val heroY = 214f + shift
            val paceY = 546f + shift
            val stackedPace = LocalDensity.current.fontScale > 1.3f || maxWidth.value < 340f
            val paceH = if (active && !stackedPace) 84f else 104f + extra
            val bottomY = paceY + paceH + 12f
            val bottomH = 92f + extra
            val designHeight = bottomY + bottomH + if (active) 76f else 20f
            val contentHeight = max(surfaceHeight, designHeight * unit)
            val topInset = max(0f, (contentHeight - designHeight * unit) / 2f)
            val scroll = rememberScrollState()
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Box(Modifier.fillMaxWidth().height(contentHeight.dp)) {
                    AssemblyAtmosphere(day, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxWidth().height((designHeight * unit).dp).offset(y = topInset.dp)) {
                        AssemblyGeometry(
                            active, counting, visibleDigit ?: lastDigit.intValue, colors, beat, clock, reduced,
                            heroY, paceY, paceH, bottomY, bottomH,
                            Modifier.fillMaxSize()
                        )
                        AssemblyHeader(colors, if (starting) "Starting" else if (counting) "Ready to run"
                            else if (paused) "Paused" else "Running", gpsLabel, isGpsRun && active,
                            onOpenRunMap, unit)
                        if (counting || starting) {
                            val numberSize = if ((visibleDigit ?: lastDigit.intValue) >= 10) 118f else 142f
                            val fixedNumeral = with(LocalDensity.current) { (numberSize * unit).dp.toSp() }
                            Box(Modifier.offset(x = (42 * unit).dp, y = (153 * unit).dp)
                                .size((309 * unit).dp),
                                contentAlignment = Alignment.Center) {
                                if (visibleDigit != null) Text(visibleDigit.toString(), color = colors.ink,
                                    fontFamily = AssemblyNumberFont, fontSize = fixedNumeral,
                                    lineHeight = fixedNumeral,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                                    modifier = Modifier.testTag(COUNTDOWN_DIGIT_TAG).graphicsLayer {
                                        val scale = landingScale(beat.floatValue, reduced)
                                        scaleX = scale; scaleY = scale
                                        alpha = if (starting) .55f else beatVisibility(beat.floatValue, reduced)
                                    })
                            }
                            // Only the central Tall frame starts early; the six side symbols
                            // are decorative and must not accidentally skip the countdown.
                            Box(Modifier.offset(x = (141 * unit).dp, y = (146 * unit).dp)
                                .size((112 * unit).dp, (324 * unit).dp)
                                .testTag(COUNTDOWN_START_NOW_TAG)
                                .semantics { contentDescription = "Start now" }
                                .clickable(enabled = counting && actionsEnabled && countdownDigit != null && countdownDigit > 0,
                                    role = Role.Button, onClick = onStartNow))
                            Text(if (starting) "STARTING YOUR RUN" else "STARTING IN",
                                fontSize = (10 * unit).sp, letterSpacing = (3.1f * unit).sp,
                                color = if (day) Color(0xFF326F7E) else Color(0xFF898C98),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().offset(y = (516 * unit).dp))
                            if (counting) {
                                TextButton(onClick = onCancel, enabled = actionsEnabled,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = (653 * unit).dp).heightIn(min = 48.dp)) {
                                    Text("Cancel", color = colors.cancel, fontSize = 13.sp)
                                }
                                Text("Tap the number to start now", color = colors.cancel.copy(alpha = .8f),
                                    fontSize = 11.sp, textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().offset(y = (706 * unit).dp))
                            }
                        }
                        if (active) {
                            Box(Modifier.offset(x = (25 * unit).dp, y = (103 * unit).dp)
                                .size((78 * unit).dp, (89 * unit + extra * unit).dp)
                                .reveal(clock, .765f, reduced), contentAlignment = Alignment.TopCenter) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size((67 * unit).dp).clip(CircleShape)
                                        .background(colors.pause)
                                        .clickable(enabled = actionsEnabled, role = Role.Button,
                                            onClick = if (paused) onResume else onPause)
                                        .semantics { contentDescription = if (paused) "Resume run" else "Pause run" },
                                        contentAlignment = Alignment.Center) {
                                        AssemblyControlIcon(paused, if (day) colors.ink else Color.White,
                                            Modifier.size((29 * unit).dp))
                                    }
                                    Text(if (paused) "Resume" else "Pause", color = colors.ink,
                                        fontSize = (11 * unit).sp, maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.requiredWidth((100 * unit).dp).padding(top = 6.dp))
                                }
                            }
                            Column(Modifier.offset(x = (130 * unit).dp, y = (108 * unit).dp)
                                .size((238 * unit).dp, ((86 + extra) * unit).dp)
                                .reveal(clock, .71f, reduced)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AssemblyClockIcon(colors.muted, Modifier.size((25 * unit).dp))
                                    Spacer(Modifier.width((9 * unit).dp))
                                    AssemblyFittedReading(metrics?.active ?: "—", colors.ink, 43f * unit,
                                        CompletionNumberFont, Modifier.weight(1f), readingTag = ACTIVE_RUN_ACTIVE_TAG)
                                }
                                Text("ACTIVE TIME", color = colors.label, fontSize = (10 * unit).sp,
                                    letterSpacing = (1.7f * unit).sp,
                                    modifier = Modifier.padding(start = (34 * unit).dp, top = 8.dp))
                            }
                            if (musicOpen) {
                                ActiveRunMusicPlayer(
                                    player = musicPlayer, layout = musicPlayerLayout,
                                    reducedMotion = reduced, foreground = motion.foreground,
                                    onClose = { musicOpen = false }, onOpenSpotify = onOpenSpotify,
                                    onConnect = onConnectMusic, onOpenTrack = onOpenMusicTrack,
                                    onPrevious = onPreviousTrack, onNext = onNextTrack,
                                    onTogglePlayback = onToggleMusicPlayback,
                                    modifier = Modifier.offset(x = (42 * unit).dp, y = (heroY * unit).dp)
                                        .size((309 * unit).dp).reveal(clock, .3f, reduced)
                                )
                            } else Column(Modifier.offset(x = (62 * unit).dp, y = ((heroY + 40) * unit).dp)
                                .size((269 * unit).dp, (229 * unit).dp)
                                .testTag(ACTIVE_RUN_CIRCLE_TAG).reveal(clock, .3f, reduced),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center) {
                                val distance = metrics?.distance ?: "—"
                                AssemblyFittedReading(distance.substringBefore(' '), colors.ink, 103f * unit,
                                    FontFamily.SansSerif, Modifier.fillMaxWidth(),
                                    FontWeight.SemiBold, centered = true, readingTag = ACTIVE_RUN_DISTANCE_TAG,
                                    description = "Distance, $distance")
                                Text(distance.substringAfter(' ', ""), color = colors.ink,
                                    fontSize = (25 * unit).sp, modifier = Modifier.padding(top = 2.dp))
                                Text("DISTANCE", color = colors.label, fontSize = (10 * unit).sp,
                                    letterSpacing = (3.3f * unit).sp,
                                    modifier = Modifier.padding(top = (18 * unit).dp))
                            }
                            Row(Modifier.offset(x = (22 * unit).dp, y = (paceY * unit).dp)
                                .size((349 * unit).dp, (paceH * unit).dp)
                                .padding(horizontal = (21 * unit).dp)
                                .reveal(clock, .38f, reduced).testTag(ACTIVE_RUN_METRICS_TAG),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (stackedPace) Column(Modifier.weight(1f)) {
                                    Text("Average pace", color = colors.label, fontSize = (15 * unit).sp,
                                        fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(3.dp))
                                    AssemblyFittedReading(metrics?.averagePace ?: "—", colors.ink, 49f * unit,
                                        CompletionNumberFont, Modifier.fillMaxWidth(), readingTag = ACTIVE_RUN_PACE_TAG)
                                } else {
                                    Text("Average pace", color = colors.label, fontSize = (15 * unit).sp,
                                        fontWeight = FontWeight.Medium, modifier = Modifier.width((110 * unit).dp))
                                    Spacer(Modifier.width((12 * unit).dp))
                                    AssemblyFittedReading(metrics?.averagePace ?: "—", colors.ink, 49f * unit,
                                        CompletionNumberFont, Modifier.weight(1f), readingTag = ACTIVE_RUN_PACE_TAG)
                                }
                            }
                            Column(Modifier.offset(x = (43 * unit).dp, y = (bottomY * unit).dp)
                                .size((153 * unit).dp, (bottomH * unit).dp)
                                .reveal(clock, .49f, reduced), verticalArrangement = Arrangement.Center) {
                                AssemblyFittedReading(metrics?.currentMilePace ?: "—", colors.ink, 35f * unit,
                                    CompletionNumberFont, Modifier.fillMaxWidth(), readingTag = ACTIVE_RUN_SPLIT_TAG)
                                Text("This mile", color = colors.label, fontSize = (12 * unit).sp,
                                    modifier = Modifier.padding(top = 7.dp))
                            }
                            Row(Modifier.offset(x = (196 * unit).dp, y = (bottomY * unit).dp)
                                .size((175 * unit).dp, (bottomH * unit).dp)
                                .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                                .reveal(clock, .435f, reduced)
                                .testTag(ACTIVE_RUN_MUSIC_TAG)
                                .clickable(role = Role.Button, onClick = { musicOpen = !musicOpen })
                                .semantics { contentDescription = if (musicOpen) "Show distance" else "Open music player" }
                                .padding(horizontal = (13 * unit).dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy((9 * unit).dp)) {
                                Image(painterResource(R.drawable.start_music_orb), contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size((53 * unit).dp).clip(CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text("Music", color = colors.musicInk, fontSize = (14 * unit).sp,
                                        fontWeight = FontWeight.Medium)
                                    Text("Spotify", color = colors.musicInk.copy(alpha = .72f),
                                        fontSize = (10 * unit).sp, modifier = Modifier.padding(top = 5.dp))
                                }
                            }
                            if (!paused) {
                                TextButton(onClick = onPause, enabled = actionsEnabled,
                                    modifier = Modifier.align(Alignment.TopCenter)
                                        .offset(y = ((bottomY + bottomH + 22f) * unit).dp)
                                        .heightIn(min = 48.dp).testTag(ACTIVE_RUN_FINISH_TAG)
                                        .reveal(clock, .765f, reduced)) {
                                    Text("Finish run", color = colors.ink, fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Recording failures must remain visible even when a short viewport scrolls the art.
        if (showNotice) Box(Modifier.fillMaxWidth().heightIn(max = 176.dp)
            .verticalScroll(rememberScrollState())) {
            AssemblyNotices(colors, metrics, actionFailed, isGpsRun,
                gpsServicePresent, gpsSignalStatus, gpsMessage, paused, actionsEnabled,
                onResumeGpsTracking)
        }
        if (paused) {
            Column(Modifier.fillMaxWidth().background(colors.canvas).padding(horizontal = 22.dp, vertical = 8.dp)) {
                Text("Active time and distance are paused.", color = colors.muted,
                    fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
                HoldToStopButton(onHoldComplete = onStop, enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth(), label = "Hold to finish run")
            }
        }
    }
    }
}

private fun Modifier.reveal(clock: State<Float>, delay: Float, reduced: Boolean) = graphicsLayer {
    alpha = progress(clock.value, delay, .19f, reduced)
    translationY = (1f - alpha) * 7.dp.toPx()
}

@Composable
private fun AssemblyHeader(colors: AssemblyColors, status: String, gps: String, mapEnabled: Boolean,
    onOpenMap: () -> Unit, unit: Float) {
    Row(Modifier.offset(x = (24 * unit).dp, y = (20 * unit).dp)
        .width((345 * unit).dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("RUNSTATE", color = colors.brand, fontSize = with(LocalDensity.current) { (21 * unit).dp.toSp() },
                letterSpacing = (4.5f * unit).sp, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = (12 * unit).dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(colors.accent))
                Text(status, color = colors.muted, fontSize = (10 * unit).sp)
                Box(Modifier.width(1.dp).height(12.dp).background(colors.line))
                Text(gps, color = colors.gps, fontSize = (10 * unit).sp)
            }
        }
        if (mapEnabled) Column(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
            .testTag(ACTIVE_RUN_MAP_BUTTON_TAG).clickable(role = Role.Button, onClick = onOpenMap)
            .semantics { contentDescription = "View live map" }, horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.size((23 * unit).dp)) {
                val r = size.minDimension * .31f
                drawCircle(colors.map, r, style = Stroke(1.4.dp.toPx()))
                drawLine(colors.map, Offset(center.x, 0f), Offset(center.x, center.y - r * .55f), 1.4.dp.toPx())
                drawLine(colors.map, Offset(center.x, size.height), Offset(center.x, center.y + r * .55f), 1.4.dp.toPx())
                drawLine(colors.map, Offset(0f, center.y), Offset(center.x - r * .55f, center.y), 1.4.dp.toPx())
                drawLine(colors.map, Offset(size.width, center.y), Offset(center.x + r * .55f, center.y), 1.4.dp.toPx())
            }
            Text("Map", color = colors.map, fontSize = (10 * unit).sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun AssemblyFittedReading(value: String, color: Color, maxSize: Float, family: FontFamily,
    modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Medium, centered: Boolean = false,
    readingTag: String? = null, description: String? = null) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val pixelWidth = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(1)
        val size = remember(value, pixelWidth, maxSize, family, density) {
            var candidate = maxSize
            while (candidate > 15f) {
                val textSize = with(density) { candidate.dp.toSp() }
                if (!measurer.measure(value, TextStyle(fontSize = textSize, fontFamily = family,
                        fontWeight = weight), maxLines = 1,
                        constraints = Constraints(maxWidth = pixelWidth)).hasVisualOverflow) break
                candidate -= 1f
            }
            with(density) { candidate.dp.toSp() }
        }
        Text(value, color = color, fontSize = size, lineHeight = size * 1.08f, fontFamily = family,
            fontWeight = weight, maxLines = 1, textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.fillMaxWidth().then(if (readingTag != null) Modifier.testTag(readingTag) else Modifier)
                .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier))
    }
}

@Composable
private fun AssemblyControlIcon(play: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        if (play) drawPath(Path().apply {
            moveTo(size.width * .32f, size.height * .18f)
            lineTo(size.width * .82f, size.height * .5f)
            lineTo(size.width * .32f, size.height * .82f); close()
        }, color) else {
            drawRoundRect(color, Offset(size.width * .24f, size.height * .18f),
                Size(size.width * .15f, size.height * .64f), CornerRadius(1.2.dp.toPx()))
            drawRoundRect(color, Offset(size.width * .61f, size.height * .18f),
                Size(size.width * .15f, size.height * .64f), CornerRadius(1.2.dp.toPx()))
        }
    }
}

@Composable
private fun AssemblyClockIcon(color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(center.x, size.height * .56f)
        drawCircle(color, size.width * .33f, c, style = Stroke(1.5.dp.toPx()))
        drawLine(color, Offset(size.width * .4f, size.height * .06f),
            Offset(size.width * .6f, size.height * .06f), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(center.x, size.height * .08f),
            Offset(center.x, size.height * .2f), 1.5.dp.toPx())
        drawLine(color, c, Offset(c.x, c.y - size.height * .21f), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun AssemblyNotices(colors: AssemblyColors, metrics: RunMetricsDisplay?, failed: Boolean,
    gps: Boolean, service: Boolean, signal: GpsSignalStatus, message: String?, paused: Boolean,
    enabled: Boolean, onRestart: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (failed) Text(ACTION_FAILED_MESSAGE, color = colors.warning, fontSize = 13.sp)
        if (!gps) Text("Sample run · simulated distance", color = colors.muted, fontSize = 12.sp)
        if (gps && !service) {
            Text(message ?: "GPS is not recording. Distance may be incomplete.", color = colors.warning, fontSize = 13.sp)
            Button(onClick = onRestart, enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = colors.ink, contentColor = colors.canvas)) {
                Text("Restart GPS tracking")
            }
        } else if (gps && !paused && signal != GpsSignalStatus.RECORDING) {
            Text(message ?: if (signal == GpsSignalStatus.ACQUIRING)
                "Waiting for usable GPS movement. Distance will appear once it is recorded."
                else "GPS signal is limited. Distance may be incomplete.", color = colors.muted, fontSize = 12.sp)
        }
        if (metrics?.sourceLabel?.contains("partial") == true)
            Text("Partial GPS coverage · pace unavailable", color = colors.muted, fontSize = 12.sp)
    }
}

@Composable
private fun AssemblyGeometry(active: Boolean, counting: Boolean, digit: Int, colors: AssemblyColors,
    beat: State<Float>, clock: State<Float>, reduced: Boolean, heroY: Float, paceY: Float,
    paceH: Float, bottomY: Float, bottomH: Float, modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 393f
        scale(u, u, Offset.Zero) {
            val time = clock.value
            val b = if (counting) beat.value else .3f
            drawLine(colors.line, Offset(24f, 86f), Offset(369f, 86f), 1f)
            val step = ((COUNTDOWN_SECONDS - digit).coerceAtLeast(0) / 2).coerceAtMost(4)
            val withinGridPair = (COUNTDOWN_SECONDS - digit).coerceAtLeast(0) % 2 + b
            val gridTravel = if (reduced) 1f else AssemblyEase.transform(fraction(withinGridPair / .42f))
            fun source(index: Int, stepAt: Int): AssemblyBox {
                val left = index < 3
                val row = ((index % 3) + if (left) stepAt % 3 else 3 - stepAt % 3) % 3
                return AssemblyBox(if (left) 22f else 265f, 146f + row * 112f, 106f, 100f)
            }
            // Two source squares become the two halves of one unbroken average-pace panel.
            val finals = listOf(AssemblyBox(116f,103f,252f,89f),
                AssemblyBox(22f,paceY,174.5f,paceH), AssemblyBox(22f,bottomY,174f,bottomH),
                AssemblyBox(25f,103f,78f,89f), AssemblyBox(196f,bottomY,175f,bottomH),
                AssemblyBox(196.5f,paceY,174.5f,paceH))
            val orders = listOf(4,0,2,5,1,3)
            repeat(6) { index ->
                val start = mixBox(source(index, max(0,step-1)), source(index,step), gridTravel)
                val p = if (active) progress(time,.12f + orders[index]*.055f,.45f,reduced) else 0f
                val r = mixBox(start,finals[index],p)
                val landing = if (reduced) .6f else 1f-AssemblyEase.transform(fraction(b/.32f))
                val tone = fraction(.16f + landing*.68f + articulation(b,reduced)*.2f)
                val alpha = mix(if(colors.day) mix(.26f,.52f,tone) else mix(.13f,.46f,tone),0f,p)
                drawRect(colors.grid.copy(alpha=alpha),Offset(r.x,r.y),Size(r.w,r.h),style=Stroke(1f))
                if (!active) drawEnergyMark(index,Offset(r.x+r.w/2,r.y+r.h/2),b,reduced,colors)
            }
            if (active) {
                val pp=progress(time,.12f,.45f,reduced)
                val bp=progress(time,.23f,.45f,reduced)
                drawRoundRect(colors.panel.copy(alpha=if(colors.day)0f else pp),Offset(22f,paceY),
                    Size(349f,paceH),CornerRadius(10f))
                drawRoundRect(colors.panel.copy(alpha=if(colors.day)0f else bp),Offset(22f,bottomY),
                    Size(349f,bottomH),CornerRadius(10f))
                val musicPath=Path().apply {
                    moveTo(196f,bottomY);lineTo(361f,bottomY);quadraticTo(371f,bottomY,371f,bottomY+10)
                    lineTo(371f,bottomY+bottomH-10);quadraticTo(371f,bottomY+bottomH,361f,bottomY+bottomH)
                    lineTo(196f,bottomY+bottomH);close()
                }
                drawPath(musicPath,colors.musicPanel.copy(alpha=if(colors.day)0f else bp))
                if(colors.day) {
                    drawRoundRect(colors.grid.copy(alpha=.3f*pp),Offset(22f,paceY),Size(349f,paceH),CornerRadius(10f),style=Stroke(1f))
                    drawRoundRect(colors.grid.copy(alpha=.3f*bp),Offset(22f,bottomY),Size(349f,bottomH),CornerRadius(10f),style=Stroke(1f))
                    drawLine(colors.grid.copy(alpha=.3f*bp),Offset(196f,bottomY),Offset(196f,bottomY+bottomH),1f)
                }
                drawLine(colors.line.copy(alpha=progress(time,.71f,.19f,reduced)),Offset(116f,108f),Offset(116f,180f),1f)
            }
            val frame = if(active)progress(time,0f,.51f,reduced) else 0f
            val frameBox=mixBox(AssemblyBox(141f,146f,112f,324f),AssemblyBox(42f,heroY,309f,309f),frame)
            val frameTone=if(reduced).6f else fraction(.28f+(1-AssemblyEase.transform(fraction(b/.32f)))*.6f+articulation(b,reduced)*.12f)
            val frameAlpha=(if(colors.day)mix(.4f,.52f,frameTone) else mix(.32f,.46f,frameTone))*(1-frame)
            drawRoundRect(colors.grid.copy(alpha=frameAlpha),Offset(frameBox.x,frameBox.y),Size(frameBox.w,frameBox.h),CornerRadius(mix(1f,155f,frame)),style=Stroke(1f))
            if(active) drawAssemblyRing(Offset(196.5f,heroY+154.5f),time,reduced,colors)
            drawLine(colors.line.copy(alpha=if(active).6f else .2f),Offset(22f,bottomY+bottomH+18f),Offset(371f,bottomY+bottomH+18f),1f)
        }
    }
}

private fun DrawScope.drawEnergyMark(index: Int, center: Offset, beat: Float, reduced: Boolean, colors: AssemblyColors) {
    val kind=listOf(0,1,2,2,0,1)[index]
    val a=articulation(beat,reduced)
    val landing=if(reduced)0f else 1-AssemblyEase.transform(fraction(beat/.3f))
    val alpha=if(reduced).85f else beatVisibility(beat,reduced)*fraction((if(colors.day).76f else .67f)+landing*.3f+a*.1f)
    val base=if(!colors.day)Color(0xFFECEFF2) else when(kind) {
        0->Color(0xFF7E679E);1->Color(0xFF438271);else->Color(0xFFB18832)
    }
    val color=base.copy(alpha=alpha)
    scale(landingScale(beat,reduced)*(1+a*.07f),pivot=center) {
        when(kind) {
            0->rotate(a*(if(index<3)-13 else 13),center) {
                val moon=Path.combine(PathOperation.Difference,
                    Path().apply { addOval(Rect(center,10.08f)) },
                    Path().apply { addOval(Rect(center+Offset(5.52f,-3.84f),9.36f)) })
                drawPath(moon,color)
            }
            1->{val gap=if(reduced)5.28f else mix(3.1f,7f,a)
                for(line in -1..1)drawLine(color,center+Offset(-7.2f,.24f+line*gap),center+Offset(7.2f,.24f+line*gap),2.667f,StrokeCap.Round)}
            else->{drawCircle(color,4.8f,center)
                rotate(a*(if(index<3)35 else -35),center) {
                    repeat(8){i->val angle=i*PI/4;val d=Offset(cos(angle).toFloat(),sin(angle).toFloat())
                        drawLine(color,center+d*7.92f,center+d*11.52f,2.4f,StrokeCap.Round)}
                }}
        }
    }
}

private fun DrawScope.drawAssemblyRing(center: Offset, time: Float, reduced: Boolean, colors: AssemblyColors) {
    val turn=360*progress(time,.45f,.62f,reduced)
    val morph=if(reduced)1f else AssemblyArcEase.transform(fraction((time-.91f)/.28f))
    rotate(turn,center) {
        repeat(14){index->
            drawArc(colors.ring.copy(alpha=.82f*progress(time,.45f,.18f,reduced)*(1-morph)),
                index*360f/14-90f-9.34f,18.68f,false,
                center-Offset(146f,146f),Size(292f,292f),style=Stroke(4.5f))
        }
        val phase=if(reduced)0.0 else 2*PI*max(0f,time-1.19f)/5.464
        repeat(76){index->
            val angle=index/76.0*PI*2-PI/2
            val amplitude=(.5+.5*sin(angle*3-phase)).toFloat()
            val texture=(.7*(.5+.5*sin(index*5.31))+.3*(.5+.5*sin(angle*11+phase))).toFloat()
            val inner=mix(146f,134f,morph)
            val length=mix(2f,8f+16*amplitude+7*texture,morph)
            val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
            val color=if(!colors.day)assemblyRgbBlend(Color(0xFF8996A2),Color(0xFFF3F6F8),.3f+.7f*amplitude)
            else AssemblyDayBarColors[index]
            drawLine(color.copy(alpha=morph*(.9f+.08f*amplitude)),center+direction*inner,
                center+direction*(inner+length),3.2f,StrokeCap.Round)
        }
    }
}

@Composable
private fun AssemblyAtmosphere(day: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        if(day) {
            drawRect(Brush.linearGradient(listOf(Color(0xFFE3F5FF),Color(0xFFF4FCFF),Color.White,Color(0xFFEDFAFF)),
                start=Offset.Zero,end=Offset(size.width*.8f,size.height)))
            val u=size.width/393f
            fun branch(origin: Offset,angle: Float) {
                rotate(angle,origin) {
                    val stem=Path().apply {moveTo(origin.x,origin.y);cubicTo(origin.x+38*u,origin.y+19*u,
                        origin.x+115*u,origin.y+32*u,origin.x+182*u,origin.y+90*u)}
                    drawPath(stem,Color(0xFF387B68).copy(alpha=.045f),style=Stroke(3.5f*u,cap=StrokeCap.Round))
                    repeat(7){i->val root=origin+Offset((24+i*20)*u,(10+i*8)*u)
                        rotate(if(i%2==0)-45f else 40f,root){
                            val length=(if(i%2==0)54f else 45f)*u;val width=length*.38f
                            val leaf=Path().apply{moveTo(root.x,root.y)
                                cubicTo(root.x+length*.28f,root.y-width,root.x+length*.82f,root.y-width*.55f,root.x+length,root.y)
                                cubicTo(root.x+length*.7f,root.y+width*.8f,root.x+length*.25f,root.y+width*.7f,root.x,root.y);close()}
                            drawPath(leaf,Color(0xFF387B68).copy(alpha=.055f))
                        }}
                }
            }
            branch(Offset(size.width+25*u,20*u),115f)
            branch(Offset(-45*u,size.height-20*u),-36f)
        } else {
            val points=listOf(23 to 109,96 to 125,281 to 104,369 to 129,15 to 205,110 to 231,
                291 to 191,378 to 261,42 to 302,327 to 315,19 to 373,109 to 418,287 to 393,
                374 to 450,31 to 495,91 to 530,301 to 509,361 to 539,14 to 599,381 to 620,
                23 to 676,374 to 709,77 to 759,144 to 731,244 to 766,322 to 751,44 to 782,361 to 783)
            points.forEachIndexed{i,(x,y)->drawCircle(Color.White.copy(alpha=.3f+(i%6)*.045f),
                (.65f+(i%4)*.12f)*size.width/393f,Offset(x/393f*size.width,y/794f*size.height))}
        }
    }
}

private data class AssemblyMotion(val foreground: Boolean, val systemEnabled: Boolean)

@Composable
private fun rememberAssemblyMotion(): AssemblyMotion {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var systemEnabled by remember { mutableStateOf(ValueAnimator.areAnimatorsEnabled()) }
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(context,lifecycle) {
        val observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {systemEnabled=ValueAnimator.areAnimatorsEnabled()}
        }
        val lifecycleObserver=LifecycleEventObserver{_,_->foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)}
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        lifecycle.addObserver(lifecycleObserver)
        onDispose {context.contentResolver.unregisterContentObserver(observer);lifecycle.removeObserver(lifecycleObserver)}
    }
    return AssemblyMotion(foreground,systemEnabled)
}
