package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

/**
 * The only run stages that may ever be written to storage.
 *
 * The lifecycle enum `RunSessionState` also contains NO_SESSION and COUNTDOWN.
 * Neither of those has a saved run behind it: preparation records nothing, and the
 * countdown is a visual transition before the run becomes official. Reusing the
 * lifecycle enum here would make "a stored run that never officially started" a
 * representable row. Keeping storage to its own three-value list makes that
 * impossible rather than merely discouraged.
 *
 * When lifecycle-to-storage mapping is written later, it must translate through an
 * exhaustive Kotlin `when` expression with no `else` branch. An `else` would silently
 * absorb any stage added to the lifecycle in the future; an exhaustive `when` turns
 * that same addition into a compile error at the one place the decision belongs.
 */
enum class StoredRunState {
    RUNNING,   // The run is official and its session is live.
    PAUSED,    // The same run is saved, but active running time is not increasing.
    COMPLETED  // The run ended and must remain durably saved.
}

/** The system that supplied a completed run's distance evidence. */
enum class MetricSource {
    FIXTURE,
    GPS
}

/** How much of the intended distance evidence was available at completion. */
enum class TelemetryCoverage {
    COMPLETE,
    PARTIAL,
    UNAVAILABLE
}

/** The unit the runner saw when this completed run was finalized. */
enum class DistanceUnit {
    MILES,
    KILOMETERS
}

/**
 * Metric values that become durable in the same write that completes a run.
 *
 * Distance may be absent when the selected source could not produce trustworthy
 * evidence. The other fields still describe that attempted finalization, so an
 * unavailable measurement differs from a row created before metrics existed.
 */
data class FinalRunMetrics(
    val distanceMeters: Double?,
    val source: MetricSource,
    val coverage: TelemetryCoverage,
    val displayUnit: DistanceUnit
) {
    init {
        require(distanceMeters == null || (distanceMeters.isFinite() && distanceMeters >= 0.0)) {
            "Final distance must be null or a finite, non-negative value: $distanceMeters."
        }

        when (coverage) {
            TelemetryCoverage.UNAVAILABLE -> require(distanceMeters == null) {
                "Unavailable telemetry cannot claim a final distance."
            }

            TelemetryCoverage.COMPLETE,
            TelemetryCoverage.PARTIAL -> require(distanceMeters != null) {
                "$coverage telemetry must carry its measured distance."
            }
        }
    }
}

/**
 * One saved run.
 *
 * PAUSED, COMPLETED and the checkpoint are states of this same row, not separate
 * records: a pause, a resume, a completion and every durable checkpoint update the row
 * the initial insert creates. One run is one run row for its whole life.
 *
 * Both text fields are validated at construction and both report the same way, with
 * [IllegalArgumentException] and a message naming the bad value. Storing an identity
 * or a zone that cannot be read back is a defect worth failing on immediately, not
 * one worth discovering when History tries to render the run months later.
 */
@Entity(tableName = "runs")
data class RunEntity(

    /**
     * The permanent UUID the phone generates when a run becomes official.
     *
     * This is the primary key on purpose. A separate auto-generated numeric id would
     * give one run two names and reopen the duplication problems the UUID prevents.
     * Because it is the key, its text has to be exact: `run_id` is compared as TEXT by
     * SQLite, so two spellings of the same UUID would be two different runs.
     */
    @PrimaryKey
    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "state")
    val state: StoredRunState,

    /** When the run officially began, as epoch milliseconds. */
    @ColumnInfo(name = "official_start_epoch_millis")
    val officialStartEpochMillis: Long,

    /**
     * The IANA zone the run started in, such as `America/Chicago`.
     *
     * Stored per run so a morning run in Houston still reads as a morning run after
     * the phone travels or changes its zone setting.
     */
    @ColumnInfo(name = "start_timezone_id")
    val startTimezoneId: String,

    /**
     * The last point in the run confirmed durable, as epoch milliseconds.
     *
     * Recovery restores only through this value and never invents distance or active
     * time for a gap beyond it. At the initial insert it equals the official start,
     * because nothing past the start has been confirmed yet.
     */
    @ColumnInfo(name = "last_checkpoint_epoch_millis")
    val lastCheckpointEpochMillis: Long,

    /**
     * When the run ended, as epoch milliseconds, or null while it has not ended.
     *
     * Nullable and defaulted, so every version-1 call site keeps compiling unchanged.
     *
     * The nullability is a compatibility requirement, not convenience. Version 1 had no
     * finish column at all, so a row inserted directly as COMPLETED under version 1 has
     * no finish time anywhere to recover, and the migration is forbidden to invent one.
     * A constructor rule demanding a finish on every COMPLETED row would therefore make
     * that real, already-stored row unreadable: the app would crash reading its own
     * history rather than admit that one old value is unknown.
     *
     * The requirement belongs to the write path instead. Every new completion in
     * [RunDao] writes a non-null finish inside its transaction, so null marks an
     * inherited unknown rather than a shape today's code is allowed to produce.
     *
     * Declared immediately before the version-3 additions so the column the first
     * migration appended remains in the same position in every later schema.
     */
    @ColumnInfo(name = "finish_epoch_millis")
    val finishEpochMillis: Long? = null,

    /** Distance frozen when this run completed, or null when it was unavailable. */
    @ColumnInfo(name = "final_distance_meters")
    val finalDistanceMeters: Double? = null,

    /** Which system supplied [finalDistanceMeters], or null on a pre-version-3 row. */
    @ColumnInfo(name = "metric_source")
    val metricSource: MetricSource? = null,

    /** Completeness of the metric evidence, or null on a pre-version-3 row. */
    @ColumnInfo(name = "telemetry_coverage")
    val telemetryCoverage: TelemetryCoverage? = null,

    /** The runner's display unit at finalization, or null on a pre-version-3 row. */
    @ColumnInfo(name = "display_distance_unit")
    val displayDistanceUnit: DistanceUnit? = null,

    /**
     * Whether pause/resume history is known complete from the official start.
     *
     * Version-1 and version-2 rows migrate with null because their shape cannot prove
     * that fact. Every run newly created by version 3 writes true. Keeping unknown
     * distinct prevents recovery from deriving believable distance from missing time.
     */
    @ColumnInfo(name = "transition_history_complete")
    val transitionHistoryComplete: Boolean? = null
) {
    init {

        // Canonical-identity guard.
        require(isCanonicalUuidText(runId)) {
            "A run id must be canonical lowercase UUID text: $runId"
        }

        // Malformed-zone guard. This only rejects an id java.time cannot resolve; it
        // cannot tell whether a resolvable one is the right zone. The eventual
        // production creator must supply the phone's actual zone id.
        require(isResolvableZoneId(startTimezoneId)) {
            "A run's start timezone must be a resolvable zone id: $startTimezoneId"
        }

        require(
            finalDistanceMeters == null ||
                (finalDistanceMeters.isFinite() && finalDistanceMeters >= 0.0)
        ) {
            "A run's final distance must be null or a finite, non-negative value: " +
                "$finalDistanceMeters."
        }

        val hasAnyFinalMetric =
            finalDistanceMeters != null ||
            metricSource != null ||
            telemetryCoverage != null ||
            displayDistanceUnit != null

        if (hasAnyFinalMetric) {
            require(state == StoredRunState.COMPLETED) {
                "Only a COMPLETED run may have finalized metrics, but this run is $state."
            }

            require(finishEpochMillis != null) {
                "Finalized metrics require the run's durable finish time."
            }

            require(
                metricSource != null &&
                    telemetryCoverage != null &&
                    displayDistanceUnit != null
            ) {
                "Finalized metrics require source, coverage and display unit together."
            }

            when (telemetryCoverage) {
                TelemetryCoverage.UNAVAILABLE -> require(finalDistanceMeters == null) {
                    "Unavailable telemetry cannot claim a final distance."
                }

                TelemetryCoverage.COMPLETE,
                TelemetryCoverage.PARTIAL -> require(finalDistanceMeters != null) {
                    "$telemetryCoverage telemetry must carry its measured distance."
                }
            }
        }

        if (finishEpochMillis != null) {

            // A finish on a run that has not ended is not missing information, it is
            // contradictory information: the row would claim the run is still going and
            // that it already ended. Null is tolerated because it is honest about a gap
            // version 1 could not record; a finish on a live run is refused because
            // nothing legitimate can produce one.
            require(state == StoredRunState.COMPLETED) {
                "Only a COMPLETED run may have a finish time, but this run is $state."
            }

            // A run that ended before it began is not a recoverable record. No migrated
            // row can trip this: the new column is created empty and only new
            // completions ever fill it.
            require(finishEpochMillis >= officialStartEpochMillis) {
                "A run's finish cannot precede its official start: " +
                    "$finishEpochMillis is before $officialStartEpochMillis"
            }
        }
    }
}

/**
 * Reports whether [candidate] is UUID text that survives a round trip unchanged.
 *
 * The round trip is the whole rule, and it is stricter than parsing alone. `UUID`
 * accepts input this project must not store — uppercase hex, and short groups such as
 * `1-1-1-1-1` — then normalizes it on the way back out. Requiring the parsed value to
 * print back identically therefore accepts only canonical lowercase 36-character text,
 * while rejecting missing dashes, shortened groups, non-hex characters and empty text.
 *
 * It deliberately does not restrict the UUID version. Which generator the phone uses
 * is a separate decision; this only fixes how the identity is spelled.
 */
private fun isCanonicalUuidText(candidate: String): Boolean =
    try {
        UUID.fromString(candidate).toString() == candidate
    } catch (unparseable: IllegalArgumentException) {
        false
    }

/**
 * Reports whether [candidate] is a zone id `java.time` can resolve.
 *
 * The `DateTimeException` is converted to a boolean here so the caller can raise the
 * same `IllegalArgumentException` it raises for a bad UUID. A raw `DateTimeException`
 * escaping the constructor would make one kind of bad argument look like a different
 * kind of failure to every caller and test.
 */
private fun isResolvableZoneId(candidate: String): Boolean =
    try {
        ZoneId.of(candidate)
        true
    } catch (invalidZone: DateTimeException) {
        false
    }
