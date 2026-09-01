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

/**
 * One saved run.
 *
 * PAUSED, COMPLETED and the checkpoint field exist in version 1 even though this
 * slice only ever writes an initial RUNNING row. They are part of the same run's
 * life, not separate records: a pause, a completion and every durable checkpoint
 * update the row this insert creates. Leaving them out of version 1 would mean a
 * schema migration for behavior that is already an agreed part of the contract.
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
    val lastCheckpointEpochMillis: Long
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
