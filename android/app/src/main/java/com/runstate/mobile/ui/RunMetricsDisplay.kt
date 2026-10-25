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
import com.runstate.mobile.data.local.RunWeatherEntity
import com.runstate.mobile.data.local.RunWeatherStatus
import com.runstate.mobile.run.RunMetrics
import com.runstate.mobile.run.RunTimeline
import kotlinx.coroutines.CancellationException
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
    val gpsDistanceAvailable: Boolean = false,
    val currentMilePace: String = "—"
)

/** A factual confirmation from the completed row, never a generated interpretation. */
internal fun savedRunReceiptFor(run: RunEntity): String = savedRunReceiptFor(run, null)

/** Pace needs the actual saved timeline; an omitted list must never imply the run had no pauses. */
internal fun savedRunReceiptFor(run: RunEntity, transitions: List<RunTransitionEntity>?): String {
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
    // The runner-facing time excludes pauses. An omitted/incomplete saved timeline must
    // not substitute wall-clock total and quietly count breaks as running.
    val duration = transitions?.let { completedHistoryMetrics(run, it)?.activeMillis }?.let { activeMillis ->
        val seconds = activeMillis / 1_000L
        val parts = listOf(seconds / 3_600L to "hour", seconds / 60L % 60 to "minute",
            seconds % 60 to "second").filter { it.first > 0 }
        val words = if (parts.isEmpty()) "0 seconds" else parts.joinToString(", ") { (count, unit) ->
            "$count $unit${if (count == 1L) "" else "s"}"
        }
        "Active time, $words."
    }
    val pace = savedAveragePaceReceiptFor(run, transitions)
    return listOfNotNull(if (run.activityFinalized) "Run saved." else "Run complete.",
        distance, duration, pace).joinToString(" ")
}

private fun savedAveragePaceReceiptFor(run: RunEntity, transitions: List<RunTransitionEntity>?): String? {
    if (transitions == null || run.telemetryCoverage != TelemetryCoverage.COMPLETE ||
        run.metricSource == null || run.displayDistanceUnit == null) return null
    // Reuse History's frozen-distance and complete-timeline rules instead of dividing by elapsed time.
    val pace = completedHistoryMetrics(run, transitions)?.paceSecondsPerMeter ?: return null
    val (unitMeters, unit) = when (run.displayDistanceUnit) {
        DistanceUnit.MILES -> 1_609.344 to "mile"
        DistanceUnit.KILOMETERS -> 1_000.0 to "kilometer"
    }
    val perUnit = (pace * unitMeters).takeIf {
        it.isFinite() && it > 0.0 && it < Long.MAX_VALUE.toDouble()
    } ?: return null
    val totalSeconds = perUnit.roundToLong().takeIf { it > 0 } ?: return null
    val words = listOf(totalSeconds / 60L to "minute", totalSeconds % 60L to "second")
        .filter { it.first > 0 }
        .joinToString(", ") { (count, name) -> "$count $name${if (count == 1L) "" else "s"}" }
    return "Average pace, $words per $unit."
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
        // The interval remains one mile even if the overall distance display uses kilometers.
        currentMilePace = metrics.currentMilePaceSecondsPerMeter?.let { pace ->
            val seconds = (pace * 1_609.344).roundToLong()
            String.format(Locale.US, "%d:%02d /mi", seconds / 60L, seconds % 60L)
        } ?: "—",
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

/** Labels the saved provider hour, never a fresh lookup or the conditions for an entire run. */
internal fun weatherSummaryFor(run: RunEntity, record: RunWeatherEntity?): String? {
    if (record == null || record.runId != run.runId || record.status == RunWeatherStatus.DISABLED) return null
    // A process may have ended while a request was pending. Never promise ongoing work from
    // a saved status alone; a genuinely late READY snapshot will refresh this display.
    if (record.status == RunWeatherStatus.PENDING) return "Weather near start · Unavailable"
    val weather = record.readySnapshot()?.takeIf { it.targetEpochMillis == run.officialStartEpochMillis }
        ?: return "Weather near start · Unavailable"
    val metric = run.displayDistanceUnit == DistanceUnit.KILOMETERS
    fun temperature(value: Double) = String.format(Locale.US, "%.0f°%s",
        if (metric) value else value * 9.0 / 5.0 + 32.0, if (metric) "C" else "F")
    val condition = when (weather.weatherCode) {
        0 -> "Clear sky"
        1 -> "Mainly clear"
        2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> return "Weather near start · Unavailable"
    }
    val time = try {
        DateTimeFormatter.ofPattern("h:mm a", Locale.US).format(
            Instant.ofEpochMilli(weather.validFromEpochMillis).atZone(ZoneId.of(run.startTimezoneId)))
    } catch (_: DateTimeException) { return "Weather near start · Unavailable" }
    val wind = String.format(Locale.US, "%.0f %s",
        if (metric) weather.windSpeedKmh else weather.windSpeedKmh / 1.609344,
        if (metric) "km/h" else "mph")
    return "${temperature(weather.temperatureCelsius)} · $condition\n" +
        "Near start · $time hourly estimate\n" +
        "Feels like ${temperature(weather.apparentTemperatureCelsius)} · " +
        "Humidity ${String.format(Locale.US, "%.0f", weather.relativeHumidityPercent)}% · Wind $wind"
}

/** An optional music read must not erase otherwise readable run and reflection history. */
internal suspend fun historyItemWithOptionalMusic(
    run: RunEntity,
    transitions: List<RunTransitionEntity>,
    reflection: RunReflectionEntity?,
    loadMusic: suspend () -> List<RunMusicObservationEntity>
): RunHistoryItem {
    val observations = if (!run.musicObservationRequested) emptyList() else try {
        loadMusic()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
    return historyItemFor(run, transitions, reflection, observations.orEmpty()).copy(
        musicObservationsUnavailable = observations == null
    )
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
    val storedMetrics = if (run.finishEpochMillis == null) {
        null
    } else {
        completedHistoryMetrics(run, transitions)
    }
    val metrics = storedMetrics?.let(::runMetricsDisplayFor)

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
        postRunNote = run.postRunNote,
        energy = reflection?.postRunEnergy,
        isGpsRun = run.trackingMode == RunTrackingMode.GPS,
        isTestData = reflection?.takeIf { it.status == ReflectionStatus.READY }?.isTestData == true,
        preRunEnergyLabel = when (run.preRunEnergy) {
            PreRunEnergy.LOW -> "Low"
            PreRunEnergy.MODERATE -> "Moderate"
            PreRunEnergy.HIGH -> "High"
            null -> null
        },
        preRunEnergy = run.preRunEnergy,
        metricSource = run.metricSource,
        // PENDING defaults to a conservative voice flag, not proof that a real GPS run is fake.
        // A saved Energy check-in must not wait for the reflection provider to answer.
        insightEligible = run.activityFinalized &&
            reflection?.takeIf { it.status == ReflectionStatus.READY }?.isTestData != true,
        musicObservationRequested = run.musicObservationRequested,
        musicObservationCount = musicObservations.size,
        lastObservedMedia = musicObservations.maxByOrNull { it.sequenceNumber }?.let { last ->
            val label = listOfNotNull(last.title, last.artist).joinToString(" — ")
            val source = if (last.sourcePackage == "com.spotify.music") "Spotify" else last.sourcePackage
            if (label.isBlank()) source else "$label · $source"
        },
        reflectionMusicSummary = reflectionMusicSummaryFor(run, reflection),
        telemetryCoverage = run.telemetryCoverage,
        officialStartEpochMillis = run.officialStartEpochMillis,
        finishEpochMillis = run.finishEpochMillis,
        startTimezoneId = run.startTimezoneId,
        activeDurationMillis = storedMetrics?.activeMillis,
        distanceMeters = run.finalDistanceMeters,
        runIntention = run.runIntention,
        routeLabel = run.routeLabel,
        energyQuestionVersion = run.energyQuestionVersion,
        preRunEnergyRecordedAtEpochMillis = run.preRunEnergyRecordedAtEpochMillis,
        postRunEffortRating = run.postRunEffortRating,
        postRunEffortQuestionVersion = run.postRunEffortQuestionVersion,
        postRunEffortRecordedAtEpochMillis = run.postRunEffortRecordedAtEpochMillis,
        postRunEffortUpdatedAtEpochMillis = run.postRunEffortUpdatedAtEpochMillis,
        firstPostRunEffortRating = run.firstPostRunEffortRating,
        postRunEnergyRecordedAtEpochMillis = run.postRunEnergyRecordedAtEpochMillis,
        postRunEnergyUpdatedAtEpochMillis = run.postRunEnergyUpdatedAtEpochMillis,
        postRunEnergyQuestionVersion = run.postRunEnergyQuestionVersion,
        firstPostRunEnergy = run.firstPostRunEnergy,
        firstPostRunEnergyQuestionVersion = run.firstPostRunEnergyQuestionVersion
    )
}

private val historyEvidenceJson = GsonBuilder().setStrictness(Strictness.STRICT).create()

/** Describes saved context only; it does not claim a request succeeded or music shaped the reply. */
private fun reflectionMusicSummaryFor(run: RunEntity, reflection: RunReflectionEntity?): String? {
    if (!run.musicObservationRequested) return null
    if (!run.musicReflectionSharingAllowed) return "Music observations kept on this phone"
    val allowed = "Music sharing allowed for this run"
    val snapshot = reflection?.evidenceJson ?: return allowed
    if (reflection.status == ReflectionStatus.READY) {
        try {
            val selected = historyEvidenceJson.fromJson(snapshot, com.runstate.mobile.reflection.CompletedRunEvidence::class.java)
            val winner = selected.songCandidates.orEmpty().singleOrNull { it.candidateId == selected.selectedSongId }
            if (winner != null && winner.musicContext == selected.musicContext &&
                selected.songSelectionMethod in setOf("CONTEXT_MATCH", "AVAILABLE_FALLBACK", "OBSERVED_FALLBACK")) {
                val label = when (selected.songSelectionMethod) {
                    "CONTEXT_MATCH" -> "Chosen for this run"
                    "OBSERVED_FALLBACK" -> "Song from this run"
                    else -> "Available song match"
                }
                return "$label · ${winner.musicContext.trackTitle} — ${winner.musicContext.artistName}"
            }
        } catch (_: RuntimeException) { /* Legacy or damaged optional metadata uses the existing summary. */ }
    }
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
