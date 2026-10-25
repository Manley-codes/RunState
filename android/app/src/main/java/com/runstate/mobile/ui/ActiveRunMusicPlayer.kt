package com.runstate.mobile.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    val hasTrack = player.connected && !player.title.isNullOrBlank()
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
                0f to Color(0x260A1211), .25f to Color(0x050A1211),
                .48f to Color(0x590A1211), .78f to Color(0xCC0A1211),
                1f to Color(0xDB0A1211))))
        }
        if (!roomyText) {
        // The visible icon keeps Spotify's official proportions; the surrounding control is
        // at least 48 dp. The exact official path is bundled in spotify_icon_white.xml.
        Box(Modifier.offset((66f * unit).dp, (56f * unit).dp)
            .size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = openTrack)
            .semantics { contentDescription = "Open in Spotify" }, contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.spotify_icon_white), null, Modifier.size(24.dp))
        }
        Box(Modifier.offset((195f * unit).dp, (54f * unit).dp)
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
            // Keep the title at the reviewed visual position at standard text size, while
            // letting larger text use the upper part of its target instead of overlapping Skip.
            val metadataTopPadding = max(0f, 12f - (LocalDensity.current.fontScale - 1f) * 40f) * unit
            Column(Modifier.offset((65f * unit).dp, (150f * unit).dp)
                .width((137f * unit).dp).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = openTrack)
                .semantics(mergeDescendants = true) {
                    contentDescription = "$fullTitle, $fullArtist. Open in Spotify"
                }.padding(top = metadataTopPadding.dp)) {
                Text(fullTitle, color = Color.White, fontSize = (15f * unit).sp,
                    lineHeight = (21f * unit).sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fullArtist, color = Color(0xFFE0E3E6), fontSize = (12f * unit).sp,
                    lineHeight = (16f * unit).sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp))
            }
            MusicPlayButton(player, onTogglePlayback,
                Modifier.offset((210f * unit).dp, (156f * unit).dp).size(48.dp))
            Row(Modifier.offset((69.5f * unit).dp, (205f * unit).dp)
                .width((170f * unit).dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                MusicSkipButton(false, player.connected && player.canSkipPrevious && !player.busy,
                    onPrevious, Modifier.size((52f * unit).coerceAtLeast(48f).dp))
                MusicProgress(position, player.durationMillis, !player.paused, reducedMotion,
                    Modifier.width((62f * unit).dp).height(12.dp))
                MusicSkipButton(true, player.connected && player.canSkipNext && !player.busy,
                    onNext, Modifier.size((52f * unit).coerceAtLeast(48f).dp))
            }
        } else {
            Column(Modifier.offset((64f * unit).dp, (109f * unit).dp).width((181f * unit).dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    player.connecting -> "Connecting to Spotify…"
                    !player.unavailableMessage.isNullOrBlank() -> player.unavailableMessage
                    player.connected -> "Nothing playing"
                    else -> "Spotify not connected"
                }, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                TextButton(onClick = if (player.connected) onOpenSpotify else onConnect,
                    enabled = !player.busy && (player.connected || (player.canConnect && !player.connecting)),
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (player.connected) "Open Spotify" else "Connect Spotify", color = Color(0xFFDFE8D2))
                }
                if (!player.connected) TextButton(onClick = onOpenSpotify,
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Open Spotify", color = Color.White, fontSize = 12.sp)
                }
            }
        }
        if (hasTrack && !player.unavailableMessage.isNullOrBlank()) {
            Text(player.unavailableMessage, color = Color.White, fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.offset((70f * unit).dp, (111f * unit).dp)
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
                        } else Text(if (player.connected) "Nothing playing" else "Spotify not connected",
                            color = Color.White)
                        player.unavailableMessage?.let { Text(it, color = Color(0xFFE0E3E6)) }
                        if (!player.connected) TextButton(onClick = onConnect,
                            enabled = !player.busy && player.canConnect && !player.connecting) {
                            Text("Connect Spotify", color = Color(0xFFDFE8D2))
                        }
                        TextButton(onClick = if (hasTrack) openTrack else onOpenSpotify) {
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
    Box(modifier.background(Color(0xFF17211D)).testTag(ACTIVE_RUN_PLAYER_ART_TAG)
        .semantics { contentDescription = when {
            layout == RunMusicPlayerLayout.HEADPHONES -> "RunState headphones artwork"
            player.artwork != null -> "Current song album artwork"
            else -> "Album artwork unavailable"
        } }) {
        if (layout == RunMusicPlayerLayout.HEADPHONES) {
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
private fun MusicPlayButton(player: SpotifyLivePlayerState, onClick: () -> Unit, modifier: Modifier) {
    val enabled = player.connected && player.canTogglePlayback && !player.busy
    Box(modifier.clip(if (player.paused) CircleShape else RoundedCornerShape(16.dp))
        .background(Color(0xFFDCE7CF).copy(alpha = if (enabled) 1f else .5f))
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
