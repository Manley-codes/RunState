package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * The only two lifecycle events that get their own stored row.
 *
 * Start and completion are deliberately absent. The official start already lives in
 * `runs.official_start_epoch_millis` and completion already lives in the parent's
 * COMPLETED state plus its finish time. Writing a synthetic RUNNING or COMPLETED event
 * here would give those two moments a second home, and two homes for one fact is how
 * they drift apart: a repair to one would silently leave the other wrong.
 *
 * So this table answers exactly one question the parent row cannot — what happened
 * between the start and the finish.
 */
enum class RunTransitionType {
    PAUSE,  // Active running time stopped accumulating at this instant.
    RESUME  // Active running time started accumulating again at this instant.
}

/**
 * One pause or resume inside a run.
 *
 * A parent row holds only the run's *current* state, and it is overwritten on every
 * change. Active duration, however, is the sum of the Running intervals, which means it
 * cannot be recovered from a current state alone — a run showing PAUSED could have
 * paused once or six times, and those are different runs. Each pause and resume is
 * therefore its own row, and the parent row keeps only the latest situation.
 *
 * The rows are owned by the run rather than shared with it: cascade delete removes them
 * with their parent, so deleting a run cannot leave orphaned events behind that a later
 * run reusing nothing would still have to explain.
 */
@Entity(
    tableName = "run_transitions",

    // Composite key. Sequence alone is not unique across the database, and run alone is
    // not unique within a run; the pair is what identifies one event, and making it the
    // key means the same sequence number cannot be issued twice for one run even if a
    // caller tried.
    primaryKeys = ["run_id", "sequence_number"],

    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],

    // The composite primary key already begins with `run_id`, so lookups by run are
    // served without this. It is the explicit child-side index chosen for version 2,
    // and it is part of the exported schema the migration has to reproduce.
    indices = [Index(value = ["run_id"])]
)
data class RunTransitionEntity(

    /** The permanent UUID of the run this event belongs to. */
    @ColumnInfo(name = "run_id")
    val runId: String,

    /**
     * Position of this event within its run, starting at 1.
     *
     * This, not the timestamp, is the authoritative ordering. Two events can carry the
     * same millisecond — a resume tapped and a pause tapped inside the same tick are
     * legitimately equal — and sorting on an equal value has no defined answer, while
     * sorting on the sequence always does.
     *
     * It is not a fix for a wall clock that jumps backwards. Sequence records the order
     * the app observed; it cannot correct a timestamp that was wrong when it was taken.
     * Real clock-correction handling belongs to the later foreground-service timing
     * work, and this slice simply refuses a timestamp that moves backwards instead.
     */
    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Int,

    @ColumnInfo(name = "transition_type")
    val transitionType: RunTransitionType,

    /**
     * When the event happened on the run's durable timeline, as epoch milliseconds.
     *
     * Supplied by the caller rather than read from the clock here, for the same reason
     * the official start is supplied: storage records the timeline it is given, and the
     * session owner is the one that knows what instant the runner's tap belongs to.
     */
    @ColumnInfo(name = "occurred_at_epoch_millis")
    val occurredAtEpochMillis: Long
) {
    init {

        // Numbering starts at 1, so 0 or a negative value means the allocation that
        // produced it was wrong rather than that the run had an unusual history.
        require(sequenceNumber >= 1) {
            "A run transition's sequence number starts at 1: $sequenceNumber"
        }
    }
}
