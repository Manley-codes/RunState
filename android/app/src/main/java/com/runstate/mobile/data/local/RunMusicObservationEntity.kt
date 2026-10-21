package com.runstate.mobile.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.runstate.mobile.music.canonicalSpotifyTrackUri
import java.util.Locale
import java.util.UUID

/** A media-session event observed by RunState; it is not proof of a whole song play. */
enum class MusicObservationKind { SESSION_SNAPSHOT, METADATA_CHANGED, PLAYBACK_CHANGED, PLAYBACK_SAMPLE }

/** The coarse state reported by the other app's media session at observation time. */
enum class MusicPlaybackState { PLAYING, PAUSED, STOPPED, BUFFERING, OTHER, UNKNOWN }

/** A durable claim prevents an interrupted process from paying to prepare the same run again. */
enum class SongPreparationClaimStatus { CLAIMED, READY, UNAVAILABLE }

@Entity(
    tableName = "run_song_preparations",
    primaryKeys = ["run_id", "preparation_id"],
    foreignKeys = [ForeignKey(
        entity = RunEntity::class, parentColumns = ["run_id"], childColumns = ["run_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["preparation_id"], unique = true)]
)
data class SongPreparationClaimEntity(
    @ColumnInfo(name = "run_id") val runId: String,
    @ColumnInfo(name = "preparation_id") val preparationId: String,
    @ColumnInfo(name = "track_title") val trackTitle: String,
    @ColumnInfo(name = "artist_name") val artistName: String,
    @ColumnInfo(name = "observation_sequence_number") val observationSequenceNumber: Int,
    @ColumnInfo(name = "destination_fingerprint") val destinationFingerprint: String,
    @ColumnInfo(name = "claimed_at_epoch_millis") val claimedAtEpochMillis: Long,
    @ColumnInfo(name = "status") val status: SongPreparationClaimStatus = SongPreparationClaimStatus.CLAIMED
) {
    init {
        require(UUID.fromString(runId).toString() == runId) { "A preparation requires a canonical run ID." }
        require(UUID.fromString(preparationId).toString() == preparationId) {
            "A preparation requires a canonical preparation ID."
        }
        require(validPreparationIdentity(trackTitle) && validPreparationIdentity(artistName)) {
            "A preparation requires a bounded title and artist."
        }
        require(observationSequenceNumber >= 1 && claimedAtEpochMillis >= 0) {
            "A preparation requires a valid observation and claim time."
        }
        require(destinationFingerprint.matches(Regex("[a-f0-9]{64}"))) {
            "A preparation requires an opaque destination fingerprint."
        }
    }
}

internal fun validPreparationIdentity(value: String?): Boolean = value != null &&
    value.isNotBlank() && value == value.trim() && value.length <= 200 && value.none(Char::isISOControl)

/** Bounded per-run spending, with no retry of an already claimed identity. */
internal const val MAX_SONG_PREPARATIONS_PER_RUN = 12

/** Match duplicate callbacks without erasing live/remix/version suffixes. */
internal fun songPreparationIdentity(title: String?, artist: String?): Pair<String, String>? {
    val parts = listOf(title?.trim(), artist?.trim())
    if (parts.any { !validPreparationIdentity(it) }) return null
    fun normalized(value: String) = value.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    return normalized(requireNotNull(parts[0])) to normalized(requireNotNull(parts[1]))
}

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
    @ColumnInfo(name = "event_kind") val eventKind: MusicObservationKind,
    /** Exact recording identity reported by Spotify; never inferred for old observations. */
    @ColumnInfo(name = "spotify_track_uri") val spotifyTrackUri: String? = null,
    @ColumnInfo(name = "album_name") val albumName: String? = null,
    @ColumnInfo(name = "track_duration_millis") val trackDurationMillis: Long? = null,
    /** A bounded interval verified by the live observer; old callbacks remain unknown. */
    @ColumnInfo(name = "playback_interval_millis") val playbackIntervalMillis: Long? = null
) {
    init {
        require(sequenceNumber >= 1) { "Music observation sequence must begin at 1." }
        validateMusicObservation(observationKey, observedAtEpochMillis, elapsedRealtimeNanos, sourcePackage)
        validateSpotifyRecording(sourcePackage, spotifyTrackUri, albumName, trackDurationMillis)
        validatePlaybackInterval(playbackIntervalMillis, sourcePackage, playbackState, eventKind, elapsedRealtimeNanos)
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
    val eventKind: MusicObservationKind,
    val spotifyTrackUri: String? = null,
    val albumName: String? = null,
    val trackDurationMillis: Long? = null,
    val playbackIntervalMillis: Long? = null
) {
    init {
        validateMusicObservation(observationKey, observedAtEpochMillis, elapsedRealtimeNanos, sourcePackage)
        validateSpotifyRecording(sourcePackage, spotifyTrackUri, albumName, trackDurationMillis)
        validatePlaybackInterval(playbackIntervalMillis, sourcePackage, playbackState, eventKind, elapsedRealtimeNanos)
    }
}

private fun validatePlaybackInterval(
    intervalMillis: Long?,
    sourcePackage: String,
    playbackState: MusicPlaybackState,
    eventKind: MusicObservationKind,
    elapsedRealtimeNanos: Long?
) {
    if (intervalMillis == null) return
    require(sourcePackage == "com.spotify.music" && intervalMillis in 0L..7_500L) {
        "Observed playback intervals must be bounded Spotify evidence."
    }
    require(intervalMillis == 0L || playbackState == MusicPlaybackState.PLAYING &&
        eventKind != MusicObservationKind.SESSION_SNAPSHOT &&
        elapsedRealtimeNanos != null && elapsedRealtimeNanos >= intervalMillis * 1_000_000L) {
        "Positive playback requires a continuing playing observation with measured timing."
    }
}

private fun validateSpotifyRecording(
    sourcePackage: String,
    trackUri: String?,
    album: String?,
    durationMillis: Long?
) {
    require(sourcePackage == "com.spotify.music" ||
        (trackUri == null && album == null && durationMillis == null)) {
        "Recording details are supported only for an observed Spotify session."
    }
    require(trackUri == null || canonicalSpotifyTrackUri(trackUri) == trackUri) {
        "A Spotify recording requires a canonical track URI."
    }
    require(album == null || validPreparationIdentity(album)) {
        "An observed album must be bounded, nonblank text."
    }
    require(durationMillis == null || durationMillis in 1_000L..86_400_000L) {
        "An observed track duration must be between one second and 24 hours."
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
