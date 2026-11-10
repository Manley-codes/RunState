package com.runstate.mobile.ui

import android.os.SystemClock
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.R
import com.runstate.mobile.music.SpotifyLivePlayerState
import com.runstate.mobile.settings.RunMusicPlayerLayout
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal const val ACTIVE_RUN_PLAYER_TAG = "activeRunPlayer"
internal const val ACTIVE_RUN_PLAYER_ART_TAG = "activeRunPlayerArtwork"
internal const val ACTIVE_RUN_PLAYER_CLOSE_TAG = "activeRunPlayerClose"
internal const val ACTIVE_RUN_PLAYER_PREVIOUS_TAG = "activeRunPlayerPrevious"
internal const val ACTIVE_RUN_PLAYER_NEXT_TAG = "activeRunPlayerNext"
internal const val ACTIVE_RUN_PLAYER_PLAY_TAG = "activeRunPlayerPlayPause"
internal const val ACTIVE_RUN_PLAYER_PROGRESS_TAG = "activeRunPlayerProgress"
internal const val ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG = "activeRunPlayerAccessible"

/**
 * Two temporary visual treatments of the same live Spotify transport. All state comes from
 * App Remote; opening this surface never starts music, changes a run, or grants sharing.
 * The album crop/overlay remains a design experiment, not a Spotify approval claim.
 */
@Composable
internal fun ActiveRunMusicPlayer(
    player: SpotifyLivePlayerState,
    layout: RunMusicPlayerLayout,
    reducedMotion: Boolean,
    onClose: () -> Unit,
    onOpenSpotify: () -> Unit,
    onConnect: () -> Unit,
    onOpenTrack: (String?) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onTogglePlayback: () -> Unit,
    foreground: Boolean = true,
    motionSeconds: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    var elapsedRealtime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(player, reducedMotion, foreground) {
        elapsedRealtime = SystemClock.elapsedRealtime()
        while (player.connected && !player.paused && foreground) {
            delay(if (reducedMotion) 1_000 else 100)
            elapsedRealtime = SystemClock.elapsedRealtime()
        }
    }
    val position = player.positionAt(elapsedRealtime)
    val hasTrack = (player.connected || player.connecting) && !player.title.isNullOrBlank()
    var slowReconnect by remember(player.connecting, foreground) { mutableStateOf(false) }
    LaunchedEffect(player.connecting, foreground) {
        if (player.connecting && foreground) {
            delay(1_500L)
            slowReconnect = true
        }
    }
    val statusText = when {
        player.connecting -> if (slowReconnect) "Connecting to Spotify…" else null
        !player.unavailableMessage.isNullOrBlank() -> player.unavailableMessage
        hasTrack -> null
        player.connected -> "Nothing playing"
        else -> "Spotify not connected"
    }
    val fullTitle = player.title.orEmpty()
    val fullArtist = player.artist.orEmpty()
    val openTrack = { onOpenTrack(player.trackUri) }
    BoxWithConstraints(modifier.testTag(ACTIVE_RUN_PLAYER_TAG)) {
        val unit = maxWidth.value / 309f
        val roomyText = LocalDensity.current.fontScale > 1.3f || unit < .9f
        Box(Modifier.offset((30.5f * unit).dp, (30.5f * unit).dp)
            .size((248f * unit).dp).clip(CircleShape).background(Color(0xFF17211D))) {
            MusicArtwork(player, layout, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                0f to Color(0x1F0C130F), .28f to Color.Transparent,
                .43f to Color(0x1F0D1411), .67f to Color(0x9E0D1411),
                1f to Color(0xFF151D19))))
            // Draw broad targets beneath the header, metadata and playback control. Their
            // real Spotify actions remain gated by the provider's current capabilities.
            if (!roomyText && hasTrack) {
                MusicArrowButton(false, player.canSkipPrevious && !player.busy,
                    reducedMotion, motionSeconds, onPrevious,
                    Modifier.offset(y = (15f * unit).dp).size((88f * unit).dp, (218f * unit).dp))
                MusicArrowButton(true, player.canSkipNext && !player.busy,
                    reducedMotion, motionSeconds, onNext,
                    Modifier.offset((160f * unit).dp, (15f * unit).dp)
                        .size((88f * unit).dp, (218f * unit).dp))
            }
        }
        if (!roomyText) {
        // The visible icon keeps Spotify's official proportions; the surrounding control is
        // at least 48 dp. The exact official path is bundled in spotify_icon_white.xml.
        Box(Modifier.offset((116f * unit - 24f).dp, (80f * unit - 24f).dp)
            .size(48.dp).clip(CircleShape).clickable(enabled = !player.connecting, role = Role.Button, onClick = openTrack)
            .semantics { contentDescription = "Open in Spotify" }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.spotify_icon_white), null, Modifier.size(24.dp))
        }
        Box(Modifier.offset((193f * unit - 24f).dp, (80f * unit - 24f).dp)
            .size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClose)
            .testTag(ACTIVE_RUN_PLAYER_CLOSE_TAG)
            .semantics { contentDescription = "Back to distance" }, contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(16.dp)) {
                drawLine(Color.White, Offset(3.dp.toPx(), 3.dp.toPx()),
                    Offset(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(size.width - 3.dp.toPx(), 3.dp.toPx()),
                    Offset(3.dp.toPx(), size.height - 3.dp.toPx()), 1.5.dp.toPx(), StrokeCap.Round)
            }
        }
        if (hasTrack) {
            // Keep the visible title at138 in the reviewed layout. The48dp link starts a
            // little earlier; larger text rises to preserve the progress line beneath it.
            val visibleMetadataTop = (138f - max(0f, LocalDensity.current.fontScale - 1f) * 40f) * unit
            val metadataTargetTop = min(visibleMetadataTop, 181f * unit - 48f - unit)
            val textShadow = with(LocalDensity.current) {
                Shadow(Color.Black.copy(alpha = .9f), Offset(0f, unit.dp.toPx()), (5f * unit).dp.toPx())
            }
            Column(Modifier.offset((98.5f * unit).dp, metadataTargetTop.dp)
                .width((112f * unit).dp).heightIn(min = 48.dp)
                .clickable(enabled = player.connected, role = Role.Button, onClick = openTrack)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$fullTitle, $fullArtist. Open in Spotify"
                }.padding(top = (visibleMetadataTop - metadataTargetTop).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(fullTitle, color = Color.White, fontSize = (16f * unit).sp,
                    lineHeight = (21f * unit).sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(shadow = textShadow))
                Text(fullArtist, color = Color(0xFFE0E3E6), fontSize = (12f * unit).sp,
                    lineHeight = (16f * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    style = androidx.compose.ui.text.TextStyle(shadow = textShadow),
                    modifier = Modifier.padding(top = unit.dp))
            }
            MusicProgress(position, player.durationMillis, !player.paused, reducedMotion,
                Modifier.offset((123.5f * unit).dp, (181f * unit).dp)
                    .size((62f * unit).dp, (12f * unit).dp))
            MusicPlayButton(player, onTogglePlayback,
                Modifier.offset((127f * unit).dp, (211f * unit).dp).size((55f * unit).dp),
                circular = true)
        } else {
            Column(Modifier.offset((64f * unit).dp, (109f * unit).dp).width((181f * unit).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                if (statusText != null) Text(statusText, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (!player.connecting && !player.connected && player.canConnect) {
                    TextButton(onClick = onConnect, enabled = !player.busy,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Connect Spotify", color = Color(0xFFDFE8D2))
                    }
                }
                if (!player.connecting) TextButton(onClick = onOpenSpotify,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open Spotify", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (hasTrack && statusText != null) {
            Text(statusText, color = Color.White, fontSize = (10f * unit).sp,
                lineHeight = (11f * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset((70f * unit).dp, max(106f * unit, 82f * unit + 24f).dp)
                    .width((170f * unit).dp).semantics { liveRegion = LiveRegionMode.Polite })
        }
        }
        // A fixed-size circular layout cannot honestly fit arbitrary accessibility text.
        // Preserve the selected artwork while giving large text the full, scrollable details.
        if (roomyText) {
            AlertDialog(onDismissRequest = onClose,
                modifier = Modifier.testTag(ACTIVE_RUN_PLAYER_ACCESSIBLE_TAG),
                containerColor = Color(0xFF171C1B), titleContentColor = Color.White,
                title = { Text("Music") },
                text = {
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        MusicArtwork(player, layout, Modifier.size(160.dp).clip(CircleShape)
                            .align(Alignment.CenterHorizontally))
                        if (hasTrack) {
                            Text(fullTitle, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(fullArtist, color = Color(0xFFE0E3E6))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                MusicSkipButton(false, player.canSkipPrevious && !player.busy,
                                    onPrevious, Modifier.size(56.dp))
                                MusicPlayButton(player, onTogglePlayback, Modifier.size(56.dp))
                                MusicSkipButton(true, player.canSkipNext && !player.busy,
                                    onNext, Modifier.size(56.dp))
                            }
                            MusicProgress(position, player.durationMillis, !player.paused, reducedMotion,
                                Modifier.fillMaxWidth().height(16.dp))
                        }
                        statusText?.let { Text(it, color = Color(0xFFE0E3E6)) }
                        if (!player.connecting && !player.connected && player.canConnect) TextButton(onClick = onConnect,
                            enabled = !player.busy) {
                            Text("Connect Spotify", color = Color(0xFFDFE8D2))
                        }
                        if (!player.connecting) TextButton(onClick = if (hasTrack) openTrack else onOpenSpotify) {
                            Image(painterResource(R.drawable.spotify_icon_white), null, Modifier.size(24.dp))
                            Text("Open Spotify", color = Color.White, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                }, confirmButton = { TextButton(onClick = onClose) { Text("Back to distance", color = Color.White) } })
        }
    }
}

@Composable
private fun MusicArtwork(player: SpotifyLivePlayerState, layout: RunMusicPlayerLayout, modifier: Modifier) {
    val showHeadphones = layout == RunMusicPlayerLayout.HEADPHONES ||
        (player.artwork == null && (player.connecting || player.loadingArtwork))
    Box(modifier.background(Color(0xFF17211D)).testTag(ACTIVE_RUN_PLAYER_ART_TAG)
        .semantics { contentDescription = when {
            showHeadphones -> "RunState headphones artwork"
            player.artwork != null -> "Current song album artwork"
            else -> "Album artwork unavailable"
        } }) {
        if (showHeadphones) {
            Image(painterResource(R.drawable.start_music_orb), null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.16f; scaleY = 1.16f; translationY = -12.dp.toPx()
                })
        } else player.artwork?.let { bitmap ->
            Image(remember(bitmap) { bitmap.asImageBitmap() }, null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MusicPlayButton(player: SpotifyLivePlayerState, onClick: () -> Unit, modifier: Modifier,
                            circular: Boolean = false) {
    val enabled = player.connected && player.canTogglePlayback && !player.busy
    Box(modifier.clip(if (circular || player.paused) CircleShape else RoundedCornerShape(16.dp))
        .background(Color(0xFFDCE7CF).copy(alpha = if (!enabled) .5f else if (circular) .82f else 1f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag(ACTIVE_RUN_PLAYER_PLAY_TAG)
        .semantics { contentDescription = if (player.paused) "Play music" else "Pause music" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(24.dp)) {
            val color = Color(0xFF253A2C)
            if (player.paused) drawPath(Path().apply {
                moveTo(size.width * .3f, size.height * .15f)
                lineTo(size.width * .83f, size.height * .5f)
                lineTo(size.width * .3f, size.height * .85f); close()
            }, color) else {
                drawRoundRect(color, Offset(size.width * .25f, size.height * .2f),
                    Size(size.width * .15f, size.height * .6f), CornerRadius(1.dp.toPx()))
                drawRoundRect(color, Offset(size.width * .6f, size.height * .2f),
                    Size(size.width * .15f, size.height * .6f), CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Composable
private fun MusicArrowButton(
    next: Boolean,
    enabled: Boolean,
    reducedMotion: Boolean,
    motionSeconds: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val (chevron, trail) = remember(next) { musicChevronPaths(next) }
    Canvas(modifier.clickable(interactionSource = interaction, indication = null,
        enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag(if (next) ACTIVE_RUN_PLAYER_NEXT_TAG else ACTIVE_RUN_PLAYER_PREVIOUS_TAG)
        .semantics { contentDescription = if (next) "Next song" else "Previous song" }) {
        // Read the existing ring clock only while drawing: no second animation timer or
        // frame-rate recomposition. Pausing/background/reduced motion are owned by the ring.
        val phase = if (reducedMotion) 0f else (motionSeconds() / 5.464f) % 1f
        val pulse = if (reducedMotion) .85f else musicArrowPulse(phase)
        val availability = if (enabled) 1f else .35f
        val unit = size.width / 88f
        withTransform({ scale(unit, unit, pivot = Offset.Zero) }) {
            if (enabled && (pressed || focused)) {
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = if (pressed) .20f else .10f), Color.Transparent),
                    center = Offset(if (next) 88f else 0f, 109f), radius = 95f), size = Size(88f, 218f))
            }
            drawPath(chevron, Color(0xFFF0F4EA).copy(alpha = .5f * pulse * availability))
            drawPath(trail, Brush.verticalGradient(
                0f to Color.Transparent, .3f to Color.White.copy(alpha = .24f * pulse * availability),
                .7f to Color.White.copy(alpha = .24f * pulse * availability), 1f to Color.Transparent,
                startY = 22f, endY = 194f),
                style = Stroke(.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (!reducedMotion && enabled) {
                val strength = when {
                    phase < .18f -> musicArrowEase.transform(phase / .18f) * .64f
                    phase < .62f -> .64f
                    phase < .84f -> (1f - musicArrowEase.transform((phase - .62f) / .22f)) * .64f
                    else -> 0f
                }
                if (strength > 0f) {
                    val lightY = 218f * (1.35f - 1.7f * musicArrowEase.transform((phase / .84f).coerceIn(0f, 1f)))
                    fun light(alpha: Float) = Brush.verticalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = strength * pulse * alpha), Color.Transparent),
                        startY = lightY - 65f, endY = lightY + 65f)
                    drawPath(chevron, light(.10f), style = Stroke(8f, join = StrokeJoin.Round))
                    drawPath(chevron, light(.18f), style = Stroke(4f, join = StrokeJoin.Round))
                    drawPath(chevron, light(1f))
                }
            }
        }
    }
}

private val musicArrowEase = CubicBezierEasing(.42f, 0f, .58f, 1f)

private fun musicArrowPulse(phase: Float): Float {
    fun between(start: Float, end: Float, from: Float, to: Float): Float =
        from + (to - from) * musicArrowEase.transform(((phase - start) / (end - start)).coerceIn(0f, 1f))
    return when {
        phase < .24f -> between(0f, .24f, .68f, .94f)
        phase < .52f -> between(.24f, .52f, .94f, .78f)
        phase < .76f -> between(.52f, .76f, .78f, .9f)
        else -> between(.76f, 1f, .9f, .68f)
    }
}

/** Exact reviewed Radix chevron silhouette, plus only its separate inner-edge trail. */
private fun musicChevronPaths(next: Boolean): Pair<Path, Path> {
    fun x(value: Float, inset: Float = 0f): Float {
        val right = 18f + (value - 5.9f) * 56f / 5.1f - inset
        return if (next) right else 88f - right
    }
    fun y(value: Float) = 6f + (value - 2.9f) * 204f / 9.2f
    val body = Path().apply {
        moveTo(x(6.1584f), y(3.13508f))
        cubicTo(x(6.35985f), y(2.94621f), x(6.67627f), y(2.95642f), x(6.86514f), y(3.15788f))
        lineTo(x(10.6151f), y(7.15788f))
        cubicTo(x(10.7954f), y(7.3502f), x(10.7954f), y(7.64949f), x(10.6151f), y(7.84182f))
        lineTo(x(6.86514f), y(11.8418f))
        cubicTo(x(6.67627f), y(12.0433f), x(6.35985f), y(12.0535f), x(6.1584f), y(11.8646f))
        cubicTo(x(5.95694f), y(11.6757f), x(5.94673f), y(11.3593f), x(6.1356f), y(11.1579f))
        lineTo(x(9.565f), y(7.49985f)); lineTo(x(6.1356f), y(3.84182f))
        cubicTo(x(5.94673f), y(3.64036f), x(5.95694f), y(3.32394f), x(6.1584f), y(3.13508f))
        close()
    }
    val trail = Path().apply {
        moveTo(x(6.1356f, 10f), y(3.84182f))
        lineTo(x(9.565f, 10f), y(7.49985f))
        lineTo(x(6.1356f, 10f), y(11.1579f))
    }
    return body to trail
}

@Composable
private fun MusicSkipButton(next: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(modifier.clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .testTag(if (next) ACTIVE_RUN_PLAYER_NEXT_TAG else ACTIVE_RUN_PLAYER_PREVIOUS_TAG)
        .semantics { contentDescription = if (next) "Next song" else "Previous song" },
        contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(26.dp)) {
            val color = Color.White.copy(alpha = if (enabled) 1f else .35f)
            fun point(x: Float, y: Float) = Offset(size.width * if (next) x else 1f - x, size.height * y)
            val top = point(.28f, .2f); val tip = point(.7f, .5f); val bottom = point(.28f, .8f)
            drawPath(Path().apply { moveTo(top.x, top.y); lineTo(tip.x, tip.y); lineTo(bottom.x, bottom.y); close() }, color)
            drawLine(color, point(.76f, .2f), point(.76f, .8f), 2.5.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun MusicProgress(position: Long, duration: Long, playing: Boolean, reduced: Boolean, modifier: Modifier) {
    val fraction = if (duration > 0L) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f
    Canvas(modifier.testTag(ACTIVE_RUN_PLAYER_PROGRESS_TAG).semantics {
        contentDescription = "Song progress"
        stateDescription = if (duration > 0L) "${position / 1000} of ${duration / 1000} seconds" else "Duration unavailable"
        progressBarRangeInfo = if (duration > 0L) ProgressBarRangeInfo(fraction, 0f..1f) else ProgressBarRangeInfo.Indeterminate
    }) {
        val end = size.width * fraction
        val phase = if (reduced) 0.0 else position / 1000.0 * PI * 1.5
        val amplitude = if (playing) 1.6.dp.toPx() else 0f
        val wave = Path()
        for (x in 0..end.toInt()) {
            val edge = min(1f, min(x / 4.dp.toPx(), (end - x) / 4.dp.toPx())).coerceAtLeast(0f)
            val y = center.y + sin(x / 16.dp.toPx() * 2 * PI - phase).toFloat() * amplitude * edge
            if (x == 0) wave.moveTo(0f, y) else wave.lineTo(x.toFloat(), y)
        }
        drawPath(wave, Color(0xFFF2F6EB), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        if (end + 4.dp.toPx() < size.width) drawLine(Color.White.copy(alpha = .3f),
            Offset(end + 4.dp.toPx(), center.y), Offset(size.width, center.y), 1.5.dp.toPx(), StrokeCap.Round)
    }
}
