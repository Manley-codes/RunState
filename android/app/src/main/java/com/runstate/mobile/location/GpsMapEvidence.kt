package com.runstate.mobile.location

import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunLocationPointEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTransitionType
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.run.RunTimingMark

/** Geographic coordinates from local recorded evidence, never the map provider's location API. */
internal data class GpsMapCoordinate(val latitude: Double, val longitude: Double)

internal enum class GpsMapMarkerState { CURRENT, LAST_KNOWN, PAUSED }

internal data class GpsMapMarker(
    val coordinate: GpsMapCoordinate,
    val accuracyMeters: Double?,
    val state: GpsMapMarkerState
)

/** Every section is independent. Map renderers must never join separate sections. */
internal data class GpsMapEvidence(
    val runId: String,
    val isGpsRun: Boolean,
    val sections: List<List<GpsMapCoordinate>> = emptyList(),
    val marker: GpsMapMarker? = null,
    val evidenceUnavailable: Boolean = false
)

/**
 * Route geometry follows committed distance decisions, while the marker may use a committed
 * accurate stationary fix. Neither a rejected fix nor a late callback can move the marker.
 * Stored anchors, rejected observations, missing sequences, pauses and source-time gaps keep
 * sections separate even if an incomplete input accidentally carries a measured segment.
 *
 * CURRENT is deliberately stronger than "the last coordinate we have": source freshness,
 * the current Running interval and its process timing must all agree. Old or recovered records
 * can still show their recorded route and an explicitly last-known marker.
 */
internal fun gpsMapEvidenceFor(
    run: RunEntity,
    transitions: List<RunTransitionEntity>,
    points: List<RunLocationPointEntity>,
    nowEpochMillis: Long,
    nowTimingMark: RunTimingMark?,
    signalStatus: GpsSignalStatus? = null
): GpsMapEvidence {
    val empty = GpsMapEvidence(run.runId, run.trackingMode == RunTrackingMode.GPS)
    if (!empty.isGpsRun) return empty
    if (points.any { it.runId != run.runId } || transitions.any { it.runId != run.runId } ||
        points.map { it.sequenceNumber }.toSet().size != points.size
    ) return empty.copy(evidenceUnavailable = true)

    val orderedTransitions = transitions.sortedBy { it.sequenceNumber }
    val intervals = mapRunningIntervals(run, orderedTransitions)
        ?: return empty.copy(evidenceUnavailable = true)
    val orderedPoints = points.sortedBy { it.sequenceNumber }
    val sections = mutableListOf<MutableList<GpsMapCoordinate>>()
    val config = GpsDistanceConfig()
    var previousSequence = 0
    var previousUsable: RunLocationPointEntity? = null
    var previousUsableInterval: Int? = null
    var previousAcceptedInterval: Int? = null
    var breakBeforeNext = true
    var latestWasUnusable = false

    orderedPoints.forEach { point ->
        if (point.sequenceNumber != previousSequence + 1) breakBeforeNext = true
        previousSequence = point.sequenceNumber
        val intervalIndex = intervals.indexOfLast {
            point.observedAtEpochMillis in it.startEpochMillis..it.endEpochMillis
        }.takeIf { it >= 0 }
        val mono = point.elapsedRealtimeNanos?.div(1_000_000L)
        val previousSession = previousUsableInterval?.let { intervals[it].timingSessionId }
        val thisSession = intervalIndex?.let { intervals[it].timingSessionId }
        val comparableClock = previousUsableInterval == intervalIndex ||
            (previousSession != null && previousSession == thisSession)
        val previousMono = if (comparableClock) {
            previousUsable?.elapsedRealtimeNanos?.div(1_000_000L)
        } else null
        val stationary = !point.acceptedForDistance &&
            point.rejectionReason == GpsSampleStatus.IGNORED_WITHIN_ACCURACY.name
        val harmlessDelayed = !point.acceptedForDistance && point.rejectionReason in mapDelayedReasons &&
            ((mono != null && previousMono != null && mono <= previousMono) ||
                (previousUsableInterval != null && intervalIndex != null &&
                    intervalIndex < previousUsableInterval!! &&
                    point.observedAtEpochMillis < checkNotNull(previousUsable).observedAtEpochMillis))
        if (harmlessDelayed) return@forEach

        if ((!point.acceptedForDistance && !stationary) || intervalIndex == null) {
            breakBeforeNext = true
            // A queued callback from an intentional break is not a new live signal failure.
            if (intervalIndex != null || point.rejectionReason !in mapOutsideRunningReasons) {
                latestWasUnusable = true
            }
            return@forEach
        }

        val accuracy = point.horizontalAccuracyMeters?.toDouble()
        if (accuracy == null || accuracy <= 0.0 || accuracy > config.maxHorizontalAccuracyMeters ||
            mono == null || previousMono?.let { mono <= it } == true
        ) {
            breakBeforeNext = true
            latestWasUnusable = true
            return@forEach
        }

        val sourceGap = previousMono?.let { mono - it > config.maxPointIntervalMillis } == true
        if (point.acceptedForDistance) {
            val coordinate = GpsMapCoordinate(point.latitude, point.longitude)
            if (breakBeforeNext || sourceGap || point.segmentDistanceMeters == null ||
                previousAcceptedInterval != intervalIndex
            ) {
                sections.add(mutableListOf(coordinate))
            } else {
                sections.last().add(coordinate)
            }
            previousAcceptedInterval = intervalIndex
            breakBeforeNext = false
        } else if (sourceGap) {
            // Stationary evidence can refresh the position, but cannot repair a missing route.
            breakBeforeNext = true
        }
        previousUsable = point
        previousUsableInterval = intervalIndex
        latestWasUnusable = false
    }

    val marker = previousUsable?.let { point ->
        val currentInterval = intervals.lastOrNull()
        val current = !latestWasUnusable &&
            signalStatus == GpsSignalStatus.RECORDING &&
            run.state == StoredRunState.RUNNING && run.transitionHistoryComplete == true &&
            currentInterval != null && currentInterval.endEpochMillis == Long.MAX_VALUE &&
            currentMapFix(point, currentInterval, nowEpochMillis, nowTimingMark, config)
        GpsMapMarker(
            coordinate = GpsMapCoordinate(point.latitude, point.longitude),
            accuracyMeters = point.horizontalAccuracyMeters?.toDouble(),
            state = when {
                run.state == StoredRunState.PAUSED -> GpsMapMarkerState.PAUSED
                current -> GpsMapMarkerState.CURRENT
                else -> GpsMapMarkerState.LAST_KNOWN
            }
        )
    }
    return empty.copy(sections = sections.map { it.toList() }, marker = marker)
}

private fun currentMapFix(
    point: RunLocationPointEntity,
    interval: MapRunningInterval,
    nowEpochMillis: Long,
    now: RunTimingMark?,
    config: GpsDistanceConfig
): Boolean {
    now ?: return false
    val startMono = interval.startElapsedRealtimeMillis ?: return false
    val sourceMono = point.elapsedRealtimeNanos?.div(1_000_000L) ?: return false
    if (interval.timingSessionId != now.sessionId || sourceMono < startMono ||
        now.elapsedRealtimeMillis < sourceMono || nowEpochMillis < point.observedAtEpochMillis ||
        point.observedAtEpochMillis < interval.startEpochMillis ||
        now.elapsedRealtimeMillis - sourceMono > config.maxPointIntervalMillis ||
        nowEpochMillis - point.observedAtEpochMillis > config.maxPointIntervalMillis
    ) return false
    return try {
        val sourceWall = Math.subtractExact(point.observedAtEpochMillis, interval.startEpochMillis)
        val sourceElapsed = Math.subtractExact(sourceMono, startMono)
        val nowWall = Math.subtractExact(nowEpochMillis, interval.startEpochMillis)
        val nowElapsed = Math.subtractExact(now.elapsedRealtimeMillis, startMono)
        Math.subtractExact(sourceWall, sourceElapsed) in -1_000L..1_000L &&
            Math.subtractExact(nowWall, nowElapsed) in -250L..250L
    } catch (_: ArithmeticException) {
        false
    }
}

private data class MapRunningInterval(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val startElapsedRealtimeMillis: Long?,
    val timingSessionId: String?
)

private fun mapRunningIntervals(
    run: RunEntity,
    transitions: List<RunTransitionEntity>
): List<MapRunningInterval>? {
    var open: MapRunningInterval? = MapRunningInterval(
        run.officialStartEpochMillis, Long.MAX_VALUE,
        run.startElapsedRealtimeMillis, run.timingSessionId
    )
    var previousEpoch = run.officialStartEpochMillis
    val intervals = mutableListOf<MapRunningInterval>()
    transitions.forEachIndexed { index, transition ->
        if (transition.sequenceNumber != index + 1 || transition.occurredAtEpochMillis < previousEpoch) {
            return null
        }
        when (transition.transitionType) {
            RunTransitionType.PAUSE -> {
                val running = open ?: return null
                intervals.add(running.copy(endEpochMillis = transition.occurredAtEpochMillis))
                open = null
            }
            RunTransitionType.RESUME -> {
                if (open != null) return null
                open = MapRunningInterval(transition.occurredAtEpochMillis, Long.MAX_VALUE,
                    transition.elapsedRealtimeMillis, transition.timingSessionId)
            }
        }
        previousEpoch = transition.occurredAtEpochMillis
    }
    val expectedCheckpoint = if (run.state == StoredRunState.COMPLETED) run.finishEpochMillis else previousEpoch
    if (run.lastCheckpointEpochMillis != expectedCheckpoint) return null
    when (run.state) {
        StoredRunState.RUNNING -> intervals.add(open ?: return null)
        StoredRunState.PAUSED -> if (open != null) return null
        StoredRunState.COMPLETED -> {
            val finish = run.finishEpochMillis ?: return null
            if (finish < previousEpoch) return null
            open?.let { intervals.add(it.copy(endEpochMillis = finish)) }
        }
    }
    return intervals
}

private val mapDelayedReasons = setOf(GpsSampleStatus.REJECTED_OUT_OF_ORDER.name, "OUT_OF_ORDER")
private val mapOutsideRunningReasons = setOf(
    "NOT_RUNNING", "BEFORE_CHECKPOINT", GpsSampleStatus.REJECTED_NOT_RUNNING.name
)
