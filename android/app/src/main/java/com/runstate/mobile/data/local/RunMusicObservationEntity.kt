package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A media-session event observed by RunState; it is not proof of a whole song play. */
enum class MusicObservationKind { SESSION_SNAPSHOT, METADATA_CHANGED, PLAYBACK_CHANGED }

/** The coarse state reported by the other app's media session at observation time. */
enum class MusicPlaybackState { PLAYING, PAUSED, STOPPED, BUFFERING, OTHER, UNKNOWN }

@Entity(
    tableName = "run_music_observations",
    primaryKeys = ["run_id", "sequence_number"],
    foreignKeys = [
        ForeignKey(
            entity = RunEntity::class,
            parentColumns = ["run_id"],
            childColumns = ["run_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["run_id"]),
        Index(value = ["run_id", "observation_key"], unique = true)
    ]
)
data class RunMusicObservationEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "sequence_number") val sequenceNumber: Int,
    @ColumnInfo(name = "observation_key") val observationKey: String,
    @ColumnInfo(name = "observed_at_epoch_millis") val observedAtEpochMillis: Long,
    /** Since-boot timing is an observation, not a song start or end time. */
    @ColumnInfo(name = "elapsed_realtime_nanos") val elapsedRealtimeNanos: Long?,
    @ColumnInfo(name = "source_package") val sourcePackage: String,
    @ColumnInfo(name = "title") val title: String?,
    @ColumnInfo(name = "artist") val artist: String?,
    @ColumnInfo(name = "playback_state") val playbackState: MusicPlaybackState,
    @ColumnInfo(name = "event_kind") val eventKind: MusicObservationKind
) {
    init {
        require(sequenceNumber >= 1) { "Music observation sequence must begin at 1." }
        validateMusicObservation(observationKey, observedAtEpochMillis, elapsedRealtimeNanos, sourcePackage)
    }
}

/** Inputs captured once per callback. The DAO assigns durable order under a transaction. */
data class RunMusicObservation(
    val runId: String,
    val observationKey: String,
    val observedAtEpochMillis: Long,
    val elapsedRealtimeNanos: Long?,
    val sourcePackage: String,
    val title: String?,
    val artist: String?,
    val playbackState: MusicPlaybackState,
    val eventKind: MusicObservationKind
) {
    init {
        validateMusicObservation(observationKey, observedAtEpochMillis, elapsedRealtimeNanos, sourcePackage)
    }
}

private fun validateMusicObservation(
    key: String,
    observedAtEpochMillis: Long,
    elapsedRealtimeNanos: Long?,
    sourcePackage: String
) {
    require(key.isNotBlank()) { "A music observation needs a stable key." }
    require(observedAtEpochMillis >= 0L) { "Music observation time cannot be negative." }
    require(elapsedRealtimeNanos == null || elapsedRealtimeNanos >= 0L) {
        "Since-boot observation time cannot be negative."
    }
    require(sourcePackage.isNotBlank()) { "A music observation needs its source app package." }
}
