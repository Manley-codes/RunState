package com.runstate.mobile.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.TelemetryCoverage
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

internal const val GPS_ROUTE_PREVIEW_TAG = "gpsRoutePreview"

/** Coordinates share one scale and are centred in a unit square; north points upward. */
internal data class GpsRouteCoordinate(val x: Double, val y: Double)

/** Every section begins at a recorded anchor. There is never a line between sections. */
internal data class GpsRoutePreviewData(
    val sections: List<List<GpsRouteCoordinate>>,
    val acceptedPointCount: Int,
    val measuredSegmentCount: Int,
    val normalizedWidth: Double,
    val normalizedHeight: Double
) {
    val hasMeasuredMovement: Boolean get() = measuredSegmentCount > 0
}

/**
 * Draw only the geometry accepted by the recorder, using the original segment decisions.
 * A null segment begins a new anchor even when earlier measured distance remains cumulative.
 * Rejected jitter/delayed callbacks do not split a path: the next accepted anchor records a break.
 * This local projection is a path illustration, not a street map or a coverage assessment.
 */
internal fun gpsRoutePreviewFor(
    runId: String,
    points: List<RunLocationPointEntity>
): GpsRoutePreviewData {
    require(points.all { it.runId == runId }) { "GPS points must belong to the displayed run." }
    require(points.map { it.sequenceNumber }.toSet().size == points.size) {
        "GPS point sequence numbers must be unique."
    }
    val accepted = points.sortedBy { it.sequenceNumber }.filter { it.acceptedForDistance }
    if (accepted.isEmpty()) return GpsRoutePreviewData(emptyList(), 0, 0, 0.0, 0.0)

    // Adjacent points around +180/-180 are neighbours, not opposite sides of the world.
    val longitudes = ArrayList<Double>(accepted.size)
    accepted.forEach { point ->
        var longitude = point.longitude
        longitudes.lastOrNull()?.let { previous ->
            while (longitude - previous > 180.0) longitude -= 360.0
            while (longitude - previous < -180.0) longitude += 360.0
        }
        longitudes.add(longitude)
    }
    val latitudeCentre = (accepted.minOf { it.latitude } + accepted.maxOf { it.latitude }) / 2.0
    val longitudeCentre = (longitudes.min() + longitudes.max()) / 2.0
    val longitudeScale = max(cos(Math.toRadians(latitudeCentre)), 0.000001)
    val projected = accepted.mapIndexed { index, point ->
        GpsRouteCoordinate(
            x = (longitudes[index] - longitudeCentre) * longitudeScale,
            y = latitudeCentre - point.latitude
        )
    }
    val minX = projected.minOf { it.x }
    val maxX = projected.maxOf { it.x }
    val minY = projected.minOf { it.y }
    val maxY = projected.maxOf { it.y }
    val width = maxX - minX
    val height = maxY - minY
    val span = max(width, height)
    val normalized = projected.map { coordinate ->
        if (span == 0.0) GpsRouteCoordinate(0.5, 0.5) else GpsRouteCoordinate(
            x = 0.5 + (coordinate.x - (minX + maxX) / 2.0) / span,
            y = 0.5 + (coordinate.y - (minY + maxY) / 2.0) / span
        )
    }

    val sections = mutableListOf<MutableList<GpsRouteCoordinate>>()
    var measuredSegments = 0
    accepted.forEachIndexed { index, point ->
        if (index == 0 || point.segmentDistanceMeters == null) {
            sections.add(mutableListOf(normalized[index]))
        } else {
            sections.last().add(normalized[index])
            if (point.segmentDistanceMeters > 0.0) measuredSegments++
        }
    }
    return GpsRoutePreviewData(
        sections = sections.map { it.toList() },
        acceptedPointCount = accepted.size,
        measuredSegmentCount = measuredSegments,
        normalizedWidth = if (span == 0.0) 0.0 else width / span,
        normalizedHeight = if (span == 0.0) 0.0 else height / span
    )
}

/** A local, offline picture of saved GPS evidence. Optional failure never hides the saved run. */
@Composable
internal fun GpsRoutePreview(
    data: GpsRoutePreviewData,
    modifier: Modifier = Modifier,
    coverage: TelemetryCoverage? = TelemetryCoverage.PARTIAL
) {
    val recordedPathLabel = if (coverage == TelemetryCoverage.COMPLETE) {
        "Recorded GPS path"
    } else {
        "Recorded GPS path · partial"
    }
    Column(
        modifier = modifier.fillMaxWidth().testTag(GPS_ROUTE_PREVIEW_TAG)
    ) {
        Text(
            text = recordedPathLabel,
            color = CompletionStrongInk,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 20.sp
        )
        if (data.acceptedPointCount == 0) {
            Text(
                text = "No usable GPS points were recorded.",
                color = CompletionMutedInk,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(top = 5.dp)
            )
        } else {
            val pathDescription = if (data.hasMeasuredMovement) {
                val prefix = if (coverage == TelemetryCoverage.COMPLETE) "Recorded" else "Partial recorded"
                "$prefix GPS path in ${data.sections.size} separate recorded sections. " +
                    "No line joins unrecorded intervals."
            } else {
                "Recorded GPS locations. Not enough recorded movement to show a measured path."
            }
            Canvas(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    .height(172.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFF0F4F3))
                    .semantics { contentDescription = pathDescription }
            ) {
                val inset = 20.dp.toPx()
                val availableWidth = max(size.width - inset * 2, 1f)
                val availableHeight = max(size.height - inset * 2, 1f)
                val scaleX = if (data.normalizedWidth > 0.0) {
                    availableWidth / data.normalizedWidth
                } else Double.POSITIVE_INFINITY
                val scaleY = if (data.normalizedHeight > 0.0) {
                    availableHeight / data.normalizedHeight
                } else Double.POSITIVE_INFINITY
                val scale = min(scaleX, scaleY).let { if (it.isFinite()) it else 0.0 }
                fun offset(point: GpsRouteCoordinate) = Offset(
                    x = (size.width / 2.0 + (point.x - 0.5) * scale).toFloat(),
                    y = (size.height / 2.0 + (point.y - 0.5) * scale).toFloat()
                )
                val routeBlue = Color(0xFF507F92)
                data.sections.forEach { section ->
                    if (section.size > 1) {
                        val path = Path().apply {
                            val first = offset(section.first())
                            moveTo(first.x, first.y)
                            section.drop(1).forEach { point ->
                                val next = offset(point)
                                lineTo(next.x, next.y)
                            }
                        }
                        drawPath(path, routeBlue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    } else {
                        drawCircle(routeBlue, radius = 3.dp.toPx(), center = offset(section.first()))
                    }
                }
                val first = data.sections.first().first()
                val last = data.sections.last().last()
                drawCircle(Color.White, radius = 6.dp.toPx(), center = offset(first))
                drawCircle(CompletionGreen, radius = 4.dp.toPx(), center = offset(first))
                if (data.acceptedPointCount > 1 && first != last) {
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = offset(last))
                    drawCircle(Color(0xFFAE7254), radius = 4.dp.toPx(), center = offset(last))
                }
            }
            Text(
                text = if (data.hasMeasuredMovement) {
                    "First / last recorded points · unrecorded intervals are left open."
                } else {
                    "Not enough recorded movement to show a measured path."
                },
                color = CompletionMutedInk,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}
