package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** One physical pair, not a catalog model. Archiving never deletes its run associations. */
@Entity(tableName = "shoe_pairs")
data class ShoePairEntity(
    @PrimaryKey @ColumnInfo(name = "shoe_pair_id") val shoePairId: String,
    @ColumnInfo(name = "brand") val brand: String,
    @ColumnInfo(name = "model") val model: String,
    @ColumnInfo(name = "nickname") val nickname: String? = null,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false
) {
    companion object {
        const val BRAND_MAX_LENGTH = 60
        const val MODEL_MAX_LENGTH = 80
        const val NICKNAME_MAX_LENGTH = 40
    }

    init {
        require(runCatching { UUID.fromString(shoePairId).toString() == shoePairId }.getOrDefault(false)) {
            "A shoe pair needs a canonical lowercase UUID."
        }
        require(brand.isNotBlank() && brand == brand.trim() && brand.length <= BRAND_MAX_LENGTH) {
            "Enter a brand of 1–$BRAND_MAX_LENGTH characters."
        }
        require(model.isNotBlank() && model == model.trim() && model.length <= MODEL_MAX_LENGTH) {
            "Enter a model of 1–$MODEL_MAX_LENGTH characters."
        }
        require(nickname == null || (nickname.isNotBlank() && nickname == nickname.trim() &&
            nickname.length <= NICKNAME_MAX_LENGTH)) {
            "A pair nickname must contain 1–$NICKNAME_MAX_LENGTH characters, or be absent."
        }
        require(createdAtEpochMillis >= 0L) { "A shoe pair's creation time cannot be negative." }
    }
}

/** Selection shares the database transaction with archive/restore, rather than separate preferences. */
@Entity(
    tableName = "shoe_selection",
    foreignKeys = [ForeignKey(
        entity = ShoePairEntity::class,
        parentColumns = ["shoe_pair_id"], childColumns = ["shoe_pair_id"],
        onDelete = ForeignKey.NO_ACTION
    )],
    indices = [Index("shoe_pair_id")]
)
data class ShoeSelectionEntity(
    @PrimaryKey @ColumnInfo(name = "selection_id") val selectionId: Int = 1,
    @ColumnInfo(name = "shoe_pair_id") val shoePairId: String?,
    @ColumnInfo(name = "revision") val revision: Long
) {
    init {
        require(selectionId == 1) { "There is one current shoe selection." }
        require(revision >= 0L) { "Selection revision cannot be negative." }
    }
}

data class ShoeWithMileage(
    @Embedded val pair: ShoePairEntity,
    @ColumnInfo(name = "total_distance_meters") val totalDistanceMeters: Double
)

/** A short-lived Undo receipt; later deliberate choices always take precedence over it. */
data class ShoeArchiveUndo(
    val shoePairId: String,
    val wasSelected: Boolean,
    val selectionRevision: Long
)

@Dao
abstract class ShoeDao {
    /**
     * Mileage is a projection of official saved runs. There is no mutable odometer to increment
     * twice on Save retries, and no pending/fixture/unavailable distance can enter the total.
     * Known PARTIAL GPS distance remains measured distance and is included without extrapolation.
     */
    @Query("""
        SELECT p.*, COALESCE(SUM(CASE WHEN r.state = 'COMPLETED' AND r.activity_finalized = 1
            AND r.tracking_mode = 'GPS' AND r.metric_source = 'GPS'
            AND r.final_distance_meters IS NOT NULL AND r.final_distance_meters >= 0
            AND r.telemetry_coverage IN ('COMPLETE', 'PARTIAL')
            THEN r.final_distance_meters ELSE 0 END), 0.0) AS total_distance_meters
        FROM shoe_pairs p LEFT JOIN runs r ON r.shoe_pair_id = p.shoe_pair_id
        GROUP BY p.shoe_pair_id
        ORDER BY p.archived ASC, p.created_at_epoch_millis DESC, p.shoe_pair_id ASC
    """)
    abstract fun observeShoes(): Flow<List<ShoeWithMileage>>

    @Query("""
        SELECT s.shoe_pair_id FROM shoe_selection s JOIN shoe_pairs p ON p.shoe_pair_id = s.shoe_pair_id
        WHERE s.selection_id = 1 AND p.archived = 0
    """)
    abstract fun observeSelectedPairId(): Flow<String?>

    @Query("""
        SELECT s.shoe_pair_id FROM shoe_selection s JOIN shoe_pairs p ON p.shoe_pair_id = s.shoe_pair_id
        WHERE s.selection_id = 1 AND p.archived = 0
    """)
    abstract suspend fun selectedActivePairId(): String?

    @Query("SELECT * FROM shoe_pairs WHERE shoe_pair_id = :shoePairId")
    abstract suspend fun findPair(shoePairId: String): ShoePairEntity?

    /** Caller retains the UUID and timestamp across a failed UI attempt; retry cannot add a second pair. */
    @Transaction
    open suspend fun addAndSelect(pair: ShoePairEntity) {
        require(!pair.archived) { "Add an active pair, or restore its existing archived entry." }
        val existing = findPair(pair.shoePairId)
        if (existing == null) insertPair(pair)
        else check(existing == pair) { "This pair identity already belongs to a different saved entry." }
        writeSelection(pair.shoePairId)
    }

    @Transaction
    open suspend fun selectPair(shoePairId: String?) {
        if (shoePairId != null) check(findPair(shoePairId)?.archived == false) {
            "Select an active saved shoe pair."
        }
        writeSelection(shoePairId)
    }

    @Transaction
    open suspend fun archivePair(shoePairId: String): ShoeArchiveUndo? {
        val pair = findPair(shoePairId) ?: return null
        if (pair.archived) return null
        val selected = readSelection()
        val wasSelected = selected?.shoePairId == shoePairId
        check(setArchived(shoePairId, true) == 1)
        if (wasSelected) writeSelection(null)
        return ShoeArchiveUndo(shoePairId, wasSelected, readSelection()?.revision ?: 0L)
    }

    @Transaction
    open suspend fun undoArchive(receipt: ShoeArchiveUndo): Boolean {
        val pair = findPair(receipt.shoePairId) ?: return false
        if (!pair.archived) return true
        check(setArchived(receipt.shoePairId, false) == 1)
        val selection = readSelection()
        if (receipt.wasSelected && selection?.shoePairId == null &&
            (selection?.revision ?: 0L) == receipt.selectionRevision) {
            writeSelection(receipt.shoePairId)
        }
        return true
    }

    /** Re-adding an archived entry keeps the same physical pair and every previously saved mile. */
    @Transaction
    open suspend fun restorePair(shoePairId: String, select: Boolean = true): Boolean {
        if (findPair(shoePairId) == null) return false
        check(setArchived(shoePairId, false) == 1)
        if (select) writeSelection(shoePairId)
        return true
    }

    private suspend fun writeSelection(shoePairId: String?) {
        val revision = Math.addExact(readSelection()?.revision ?: 0L, 1L)
        putSelection(ShoeSelectionEntity(shoePairId = shoePairId, revision = revision))
    }

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPair(pair: ShoePairEntity)

    @Query("SELECT * FROM shoe_selection WHERE selection_id = 1")
    protected abstract suspend fun readSelection(): ShoeSelectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun putSelection(selection: ShoeSelectionEntity)

    @Query("UPDATE shoe_pairs SET archived = :archived WHERE shoe_pair_id = :shoePairId")
    protected abstract suspend fun setArchived(shoePairId: String, archived: Boolean): Int
}
