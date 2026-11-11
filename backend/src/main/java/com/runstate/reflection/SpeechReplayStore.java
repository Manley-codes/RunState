package com.runstate.reflection;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** A committed, audio-less row means the provider may already have charged for this request. */
final class SpeechReplayStore {
    static final int MAX_ENTRIES = 128;
    static final long RETENTION_MILLIS = Duration.ofDays(7).toMillis();
    private static final int MAX_AUDIO = ReflectionHttpServer.MAX_AUDIO_RESPONSE_BYTES;
    private static final String VOICE_IDENTITY = "reflection-v1\0" + ElevenLabsReflectionSpeech.ENDPOINT
            + "\0" + ElevenLabsReflectionSpeech.MODEL_ID + "\0";

    enum State { CLAIMED, CACHED, PENDING, FULL, BUSY }
    record Claim(State state, String key, String reservation, byte[] audio) { }

    private final ReplayDatabase database;
    private final Clock clock;
    private final int capacity;

    SpeechReplayStore(Path directory) throws IOException {
        this(directory, Clock.systemUTC(), MAX_ENTRIES);
    }

    SpeechReplayStore(Path directory, Clock clock, int capacity) throws IOException {
        if (capacity < 1 || capacity > MAX_ENTRIES) throw new IllegalArgumentException("Invalid replay capacity");
        this.capacity = capacity;
        this.clock = clock;
        database = new ReplayDatabase(directory, "reflection-speech.sqlite3",
                "DELETE FROM speech_replays WHERE expires_at <= ?", clock.millis(), """
                CREATE TABLE speech_replays (
                    replay_key TEXT PRIMARY KEY,
                    reservation TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    audio BLOB CHECK(audio IS NULL OR length(audio) BETWEEN 1 AND %d)
                ) STRICT
                """.formatted(MAX_AUDIO));
    }

    Claim claim(String principalId, String text, boolean allowNew) throws IOException {
        String key = ReplayDatabase.scopedKey(VOICE_IDENTITY, principalId, text);
        try (Connection db = database.open()) {
            db.setAutoCommit(false);
            long now = clock.millis();
            database.purgeExpired(db, now);
            Claim result;
            try (PreparedStatement lookup = db.prepareStatement("SELECT audio FROM speech_replays WHERE replay_key=?")) {
                lookup.setString(1, key);
                try (ResultSet row = lookup.executeQuery()) {
                    if (row.next()) {
                        byte[] audio = row.getBytes(1);
                        result = new Claim(audio == null ? State.PENDING : State.CACHED, key, null, audio);
                    } else {
                        result = null;
                    }
                }
            }
            if (result == null) result = reserve(db, key, now, allowNew);
            db.commit();
            return result;
        } catch (SQLException e) {
            throw new IOException("Cannot reserve or read reflection speech", e);
        }
    }

    private Claim reserve(Connection db, String key, long now, boolean allowNew) throws SQLException {
        if (!allowNew) return new Claim(State.BUSY, key, null, null);
        try (Statement sql = db.createStatement();
             ResultSet count = sql.executeQuery("SELECT count(*) FROM speech_replays")) {
            count.next();
            if (count.getInt(1) >= capacity) return new Claim(State.FULL, key, null, null);
        }
        String reservation = UUID.randomUUID().toString();
        try (PreparedStatement insert = db.prepareStatement(
                "INSERT INTO speech_replays (replay_key,reservation,expires_at) VALUES (?,?,?)")) {
            insert.setString(1, key);
            insert.setString(2, reservation);
            insert.setLong(3, now + RETENTION_MILLIS);
            insert.executeUpdate();
        }
        return new Claim(State.CLAIMED, key, reservation, null);
    }

    void releaseUnsent(Claim claim) throws IOException {
        try (Connection db = database.open(); PreparedStatement release = db.prepareStatement(
                "DELETE FROM speech_replays WHERE replay_key=? AND reservation=? AND audio IS NULL")) {
            release.setString(1, claim.key());
            release.setString(2, claim.reservation());
            release.executeUpdate();
        } catch (SQLException failure) {
            throw new IOException("Cannot release unsent speech reservation", failure);
        }
    }

    void complete(Claim claim, byte[] audio) throws IOException {
        if (claim.state() != State.CLAIMED || audio == null || audio.length == 0 || audio.length > MAX_AUDIO) {
            throw new IOException("Invalid reflection speech completion");
        }
        // A stale provider result must never overwrite a newer reservation for the same input.
        try (Connection db = database.open(); PreparedStatement update = db.prepareStatement(
                "UPDATE speech_replays SET audio=?, expires_at=? WHERE replay_key=? AND reservation=? AND audio IS NULL")) {
            update.setBytes(1, audio);
            update.setLong(2, clock.millis() + RETENTION_MILLIS);
            update.setString(3, claim.key());
            update.setString(4, claim.reservation());
            if (update.executeUpdate() != 1) throw new IOException("Speech reservation is no longer current");
        } catch (SQLException e) {
            throw new IOException("Cannot save reflection speech", e);
        }
    }

}
