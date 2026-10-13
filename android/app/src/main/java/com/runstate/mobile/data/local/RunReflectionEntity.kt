package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.UUID

enum class ReflectionStatus { PENDING, READY, FAILED }

enum class ReflectionBranch { SPENT, FEELING_GOOD, POWERED_UP, NO_SELECTION }

/**
 * One durable reflection attempt and selection for a completed run.
 *
 * Evidence and the selection deadline are captured once. Retry changes the request
 * identity, never the evidence. Once READY, the selected text is permanent; later
 * Energy answers remain useful feedback without rewriting what the runner read.
 */
@Entity(
    tableName = "run_reflections",
    foreignKeys = [ForeignKey(
        entity = RunEntity::class,
        parentColumns = ["run_id"],
        childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class RunReflectionEntity(
    @PrimaryKey @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "request_id") val requestId: String,
    @ColumnInfo(name = "evidence_json") val evidenceJson: String,
    @ColumnInfo(name = "selection_deadline_epoch_millis") val selectionDeadlineEpochMillis: Long,
    @ColumnInfo(name = "status") val status: ReflectionStatus = ReflectionStatus.PENDING,
    @ColumnInfo(name = "selected_branch") val selectedBranch: ReflectionBranch? = null,
    @ColumnInfo(name = "post_run_energy") val postRunEnergy: ReflectionBranch? = null,
    @ColumnInfo(name = "candidates_json") val candidatesJson: String? = null,
    @ColumnInfo(name = "text") val text: String? = null,
    @ColumnInfo(name = "finalized_at_epoch_millis") val finalizedAtEpochMillis: Long? = null,
    @ColumnInfo(name = "is_test_data", defaultValue = "1") val isTestData: Boolean = true
) {
    init {
        require(canonicalUuid(runId) && canonicalUuid(requestId)) {
            "Reflection identities must be canonical lowercase UUIDs."
        }
        require(evidenceJson.isNotBlank()) { "A reflection requires its saved evidence." }
        require(postRunEnergy != ReflectionBranch.NO_SELECTION) {
            "No selection is not an Energy answer."
        }
        require(candidatesJson == null || candidatesJson.isNotBlank())
        if (status == ReflectionStatus.READY) {
            require(selectedBranch != null && !text.isNullOrBlank() && finalizedAtEpochMillis != null) {
                "A ready reflection requires its selected branch, exact text and finalization time."
            }
            require(candidatesJson == null) { "Ready reflections discard unused candidates." }
        } else {
            require(text == null && finalizedAtEpochMillis == null) {
                "Only a ready reflection may claim finalized text."
            }
        }
    }
}

private fun canonicalUuid(value: String): Boolean = try {
    UUID.fromString(value).toString() == value
} catch (_: IllegalArgumentException) {
    false
}
