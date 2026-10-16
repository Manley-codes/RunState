package com.runstate.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import com.runstate.mobile.data.local.DistanceUnit
import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.ReflectionStatus
import com.runstate.mobile.data.local.RunEntity
import com.runstate.mobile.data.local.RunMusicObservationEntity
import com.runstate.mobile.data.local.RunReflectionEntity
import com.runstate.mobile.data.local.RunTransitionEntity
import com.runstate.mobile.data.local.RunTrackingMode
import com.runstate.mobile.data.local.StoredRunState
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.run.RunMetrics
import com.runstate.mobile.run.RunTimeline
import com.runstate.mobile.run.TransitionHistoryCoverage
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** Live metric refresh cadence. Timestamps, not tick counts, remain the source of truth. */
internal const val METRIC_TICK_MILLIS = 1_000L

/** Text-only values the current foundation screen can draw without owning run data. */
internal data class RunMetricsDisplay(
    val elapsed: String,
    val active: String,
    val distance: String,
    val averagePace: String,
    val sourceLabel: String,
    val gpsDistanceAvailable: Boolean = false
)

/** A factual confirmation from the completed row, never a generated interpretation. */
internal fun savedRunReceiptFor(run: RunEntity): String {
    require(run.state == StoredRunState.COMPLETED)
    val distance = run.finalDistanceMeters?.let { meters ->
        val (unitMeters, singular, plural) = when (run.displayDistanceUnit) {
            DistanceUnit.MILES -> Triple(1_609.344, "mile", "miles")
            DistanceUnit.KILOMETERS -> Triple(1_000.0, "kilometer", "kilometers")
            null -> return@let null
        }
        val units = meters / unitMeters
        val amount = if (units > 0 && units < 0.01) "less than 0.01" else
            String.format(Locale.US, "%.2f", units).trimEnd('0').trimEnd('.')
        val unit = if (amount == "1") singular else plural
        val provenance = when (run.metricSource) {
            MetricSource.FIXTURE -> "Fixture distance, "
            MetricSource.GPS -> if (run.telemetryCoverage == TelemetryCoverage.PARTIAL)
                "Partial GPS distance, " else ""
            null -> return@let null
        }
        "$provenance$amount $unit."
    }
    val duration = run.finishEpochMillis?.let { finish ->
        val seconds = (finish - run.officialStartEpochMillis).coerceAtLeast(0) / 1_000L
        val parts = listOf(seconds / 3_600L to "hour", seconds / 60L % 60 to "minute",
            seconds % 60 to "second").filter { it.first > 0 }
        val words = if (parts.isEmpty()) "0 seconds" else parts.joinToString(", ") { (count, unit) ->
            "$count $unit${if (count == 1L) "" else "s"}"
        }
        "Elapsed time, $words."
    }
    return listOfNotNull("Run saved.", distance, duration).joinToString(" ")
}

/** Converts stored-unit-independent metrics into stable runner-facing text. */
internal fun runMetricsDisplayFor(metrics: RunMetrics): RunMetricsDisplay {
    val (metersPerUnit, distanceSuffix, paceSuffix) = when (metrics.displayUnit) {
        DistanceUnit.MILES -> Triple(1_609.344, "mi", "/mi")
        DistanceUnit.KILOMETERS -> Triple(1_000.0, "km", "/km")
    }

    val distance = metrics.distanceMeters?.let { meters ->
        String.format(Locale.US, "%.2f %s", meters / metersPerUnit, distanceSuffix)
    } ?: "—"

    val paceIsTrustworthy = metrics.source != MetricSource.GPS ||
        metrics.coverage == TelemetryCoverage.COMPLETE
    val averagePace = metrics.paceSecondsPerMeter?.takeIf { paceIsTrustworthy }
        ?.let { secondsPerMeter ->
        val totalSeconds = (secondsPerMeter * metersPerUnit).roundToLong()
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        String.format(Locale.US, "%d:%02d %s", minutes, seconds, paceSuffix)
    } ?: "—"

    return RunMetricsDisplay(
        elapsed = formatDuration(metrics.elapsedMillis),
        active = metrics.activeMillis?.let(::formatDuration) ?: "—",
        distance = distance,
        averagePace = averagePace,
        gpsDistanceAvailable = metrics.source == MetricSource.GPS && metrics.distanceMeters != null,
        sourceLabel = when (metrics.source) {
            MetricSource.FIXTURE -> "Fixture metrics"
            MetricSource.GPS -> when (metrics.coverage) {
                TelemetryCoverage.COMPLETE -> "GPS metrics"
                TelemetryCoverage.PARTIAL -> "GPS metrics · partial"
                TelemetryCoverage.UNAVAILABLE -> "GPS distance unavailable"
            }
            null -> "Metrics unavailable"
        }
    )
}

private val historyDateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.US)
private val completionDateFormat = DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US)

/** Completion uses its saved finish and run timezone; missing history never becomes today's time. */
internal fun completionDateLabelFor(run: RunEntity): String? {
    if (run.state != StoredRunState.COMPLETED) return null
    val finish = run.finishEpochMillis ?: return null
    return try {
        completionDateFormat.format(
            Instant.ofEpochMilli(finish).atZone(ZoneId.of(run.startTimezoneId))
        )
    } catch (_: DateTimeException) {
        null
    }
}

/** Uses only durable facts; opening History never asks the reflection provider to write again. */
internal fun historyItemFor(
    run: RunEntity,
    transitions: List<RunTransitionEntity>,
    reflection: RunReflectionEntity?,
    musicObservations: List<RunMusicObservationEntity> = emptyList()
): RunHistoryItem {
    require(run.state == StoredRunState.COMPLETED) { "History requires a completed run." }
    require(reflection == null || reflection.runId == run.runId) {
        "A reflection must belong to the displayed run."
    }
    require(musicObservations.all { it.runId == run.runId }) {
        "Music observations must belong to the displayed run."
    }

    val dateLabel = historyDateFormat.format(
        Instant.ofEpochMilli(run.officialStartEpochMillis).atZone(ZoneId.of(run.startTimezoneId))
    )

    // A version-1 completed row may have no finish. Its time cannot be reconstructed.
    val metrics = if (run.finishEpochMillis == null) {
        null
    } else {
        completedHistoryMetrics(run, transitions)?.let(::runMetricsDisplayFor)
    }

    return RunHistoryItem(
        runId = run.runId,
        dateLabel = dateLabel,
        metrics = metrics,
        reflectionStatus = reflection?.status?.name,
        reflectionText = reflection?.text,
        energyLabel = when (reflection?.postRunEnergy) {
            ReflectionBranch.SPENT -> "Spent"
            ReflectionBranch.FEELING_GOOD -> "Feeling Good"
            ReflectionBranch.POWERED_UP -> "Powered Up"
            ReflectionBranch.NO_SELECTION, null -> null
        },
        effort = run.postRunEffort,
        energy = reflection?.postRunEnergy,
        isGpsRun = run.trackingMode == RunTrackingMode.GPS,
        isTestData = reflection?.takeIf { it.status == ReflectionStatus.READY }?.isTestData == true,
        preRunEnergyLabel = when (run.preRunEnergy) {
            PreRunEnergy.LOW -> "Low"
            PreRunEnergy.MODERATE -> "Moderate"
            PreRunEnergy.HIGH -> "High"
            null -> null
        },
        musicObservationRequested = run.musicObservationRequested,
        musicObservationCount = musicObservations.size,
        lastObservedMedia = musicObservations.maxByOrNull { it.sequenceNumber }?.let { last ->
            val label = listOfNotNull(last.title, last.artist).joinToString(" — ")
            val source = if (last.sourcePackage == "com.spotify.music") "Spotify" else last.sourcePackage
            if (label.isBlank()) source else "$label · $source"
        },
        reflectionMusicSummary = reflectionMusicSummaryFor(run, reflection)
    )
}

private val historyEvidenceJson = GsonBuilder().setStrictness(Strictness.STRICT).create()

/** Describes saved context only; it does not claim a request succeeded or music shaped the reply. */
private fun reflectionMusicSummaryFor(run: RunEntity, reflection: RunReflectionEntity?): String? {
    if (!run.musicObservationRequested) return null
    if (!run.musicReflectionSharingAllowed) return "Music observations kept on this phone"
    val allowed = "Music sharing allowed for this run"
    val snapshot = reflection?.evidenceJson ?: return allowed
    val music = try {
        val evidence = historyEvidenceJson.fromJson(snapshot, JsonObject::class.java)
        evidence?.get("musicContext")?.takeIf { it.isJsonObject }?.asJsonObject ?: return allowed
    } catch (_: RuntimeException) {
        return allowed
    }
    fun text(name: String): String? = music.get(name)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString
    fun absent(name: String): Boolean = music.get(name)?.isJsonNull != false

    return when {
        text("state") == "MUSIC" && text("provenance") == "MEDIA_SESSION_PARTIAL" &&
            !text("trackTitle").isNullOrBlank() && !text("artistName").isNullOrBlank() &&
            absent("artistDescription") -> "One Spotify report included in reflection context · partial"
        text("state") == "UNKNOWN" &&
            listOf("trackTitle", "artistName", "artistDescription", "provenance").all(::absent) ->
            "No Spotify report included in reflection context"
        else -> allowed
    }
}

private fun completedHistoryMetrics(
    run: RunEntity,
    transitions: List<RunTransitionEntity>
): RunMetrics? {
    if (run.transitionHistoryComplete == true && transitions.any { it.runId != run.runId }) {
        return null
    }

    // A malformed timeline must not stop History from showing the saved run and exact reply.
    return try {
        val finish = requireNotNull(run.finishEpochMillis)
        val elapsed = requireNotNull(
            RunTimeline.elapsedMillis(
                officialStartEpochMillis = run.officialStartEpochMillis,
                finishEpochMillis = finish,
                currentState = StoredRunState.COMPLETED,
                nowEpochMillis = finish
            )
        )
        val timelineActive = RunTimeline.activeMillis(
            officialStartEpochMillis = run.officialStartEpochMillis,
            finishEpochMillis = finish,
            currentState = StoredRunState.COMPLETED,
            transitions = if (run.transitionHistoryComplete == true) transitions else emptyList(),
            nowEpochMillis = finish,
            historyCoverage = if (run.transitionHistoryComplete == true) {
                TransitionHistoryCoverage.COMPLETE
            } else {
                TransitionHistoryCoverage.INCOMPLETE
            }
        )
        // A GPS service can die while the run row remains RUNNING. History has no
        // durable proof that the whole interval was observed, so a partial GPS record
        // must not reconstruct an apparently precise active duration from wall time.
        val active = if (run.metricSource == MetricSource.GPS &&
            run.telemetryCoverage != TelemetryCoverage.COMPLETE
        ) null else timelineActive
        RunMetrics(
            elapsedMillis = elapsed,
            activeMillis = active,
            distanceMeters = run.finalDistanceMeters,
            paceSecondsPerMeter = if (
                run.metricSource == MetricSource.GPS &&
                run.telemetryCoverage != TelemetryCoverage.COMPLETE
            ) null else RunTimeline.averagePaceSecondsPerMeter(
                activeDurationMillis = active,
                distanceMeters = run.finalDistanceMeters
            ),
            displayUnit = run.displayDistanceUnit ?: DistanceUnit.MILES,
            source = run.metricSource,
            coverage = run.telemetryCoverage ?: TelemetryCoverage.UNAVAILABLE
        )
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/**
 * Runs [onTick] only while a live metric surface is active and the Activity is visible.
 *
 * Dropping below STARTED cancels the loop. Returning performs an immediate fresh read from
 * the process coordinator, so time spent backgrounded is reconstructed from timestamps
 * instead of from ticks nobody observed.
 */
@Composable
internal fun RunMetricsTicker(
    active: Boolean,
    lifecycle: Lifecycle,
    onTick: suspend () -> Unit
) {
    val currentOnTick by rememberUpdatedState(onTick)

    LaunchedEffect(active, lifecycle) {
        if (active) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    currentOnTick()
                    delay(METRIC_TICK_MILLIS)
                }
            }
        }
    }
}

/** Hours remain visible from the first second so the layout never changes shape mid-run. */
private fun formatDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
}
