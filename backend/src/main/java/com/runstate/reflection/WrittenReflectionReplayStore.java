package com.runstate.reflection;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Multiple phone request IDs can refer to one generation for the same runner and run evidence. */
final class WrittenReflectionReplayStore {
    static final int MAX_ENTRIES = 256;
    static final int MAX_REQUEST_IDS_PER_ENTRY = 64;
    static final long RETENTION_MILLIS = Duration.ofHours(24).toMillis();
    private static final int MAX_RESULT = ReflectionHttpServer.MAX_RESPONSE_BYTES;

    enum State { CLAIMED, CACHED, PENDING, CONFLICT, FULL, BUSY }
    record Claim(State state, String key, String reservation, byte[] body, boolean songStudyAcknowledged) {
        private static Claim withoutResult(State state) {
            return new Claim(state, null, null, null, false);
        }
    }

    private final ReplayDatabase database;
    private final Clock clock;
    private final int capacity;

    WrittenReflectionReplayStore(Path directory) throws IOException {
        this(directory, Clock.systemUTC(), MAX_ENTRIES);
    }

    WrittenReflectionReplayStore(Path directory, Clock clock, int capacity) throws IOException {
        if (capacity < 1 || capacity > MAX_ENTRIES) throw new IllegalArgumentException("Invalid replay capacity");
        this.clock = clock;
        this.capacity = capacity;
        database = new ReplayDatabase(directory, "written-reflections.sqlite3",
                "DELETE FROM written_replays WHERE expires_at <= ?", clock.millis(), """
                CREATE TABLE written_replays (
                    replay_key TEXT PRIMARY KEY,
                    reservation TEXT NOT NULL,
                    expires_at INTEGER NOT NULL,
                    result BLOB CHECK(result IS NULL OR length(result) BETWEEN 1 AND %d),
                    song_study INTEGER NOT NULL DEFAULT 0 CHECK(song_study IN (0, 1))
                ) STRICT
                """.formatted(MAX_RESULT), """
                CREATE TABLE written_requests (
                    request_key TEXT PRIMARY KEY,
                    replay_key TEXT NOT NULL REFERENCES written_replays(replay_key) ON DELETE CASCADE
                ) STRICT
                """, "CREATE INDEX written_requests_by_replay ON written_requests(replay_key)");
    }

    Claim claim(String principalId, String requestId, String operationIdentity, boolean allowNew) throws IOException {
        // Keep every Java code unit distinct, including isolated surrogates in older accepted song metadata.
        ByteBuffer identity = ByteBuffer.allocate(operationIdentity.length() * Character.BYTES);
        identity.asCharBuffer().put(operationIdentity);
        String key = ReplayDatabase.scopedKey("written-generation-v1\0", principalId, identity.array());
        String requestKey = ReplayDatabase.scopedKey("written-request-id-v1\0", principalId, requestId);
        try (Connection db = database.open()) {
            db.setAutoCommit(false);
            long now = clock.millis();
            database.purgeExpired(db, now);
            String boundKey = lookupRequest(db, requestKey);
            Claim result;
            if (boundKey != null && !boundKey.equals(key)) {
                result = Claim.withoutResult(State.CONFLICT);
            } else {
                result = lookupGeneration(db, key);
                if (result == null) result = reserve(db, key, now, allowNew);
                if (boundKey == null && (result.state() == State.CLAIMED
                        || result.state() == State.CACHED || result.state() == State.PENDING)) {
                    if (!bindRequest(db, requestKey, key)) result = Claim.withoutResult(State.FULL);
                }
            }
            db.commit();
            return result;
        } catch (SQLException failure) {
            throw new IOException("Cannot reserve or read written reflection", failure);
        }
    }

    private static String lookupRequest(Connection db, String requestKey) throws SQLException {
        try (PreparedStatement lookup = db.prepareStatement("SELECT replay_key FROM written_requests WHERE request_key=?")) {
            lookup.setString(1, requestKey);
            try (ResultSet row = lookup.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static Claim lookupGeneration(Connection db, String key) throws SQLException {
        try (PreparedStatement lookup = db.prepareStatement("SELECT result, song_study FROM written_replays WHERE replay_key=?")) {
            lookup.setString(1, key);
            try (ResultSet row = lookup.executeQuery()) {
                if (!row.next()) return null;
                byte[] body = row.getBytes(1);
                if (body == null) return Claim.withoutResult(State.PENDING);
                return new Claim(State.CACHED, key, null, body, row.getBoolean(2));
            }
        }
    }

    private Claim reserve(Connection db, String key, long now, boolean allowNew) throws SQLException {
        if (!allowNew) return Claim.withoutResult(State.BUSY);
        try (Statement sql = db.createStatement();
             ResultSet count = sql.executeQuery("SELECT count(*) FROM written_replays")) {
            count.next();
            if (count.getInt(1) >= capacity) return Claim.withoutResult(State.FULL);
        }
        String reservation = UUID.randomUUID().toString();
        try (PreparedStatement insert = db.prepareStatement(
                "INSERT INTO written_replays (replay_key,reservation,expires_at) VALUES (?,?,?)")) {
            insert.setString(1, key);
            insert.setString(2, reservation);
            insert.setLong(3, now + RETENTION_MILLIS);
            insert.executeUpdate();
        }
        return new Claim(State.CLAIMED, key, reservation, null, false);
    }

    private static boolean bindRequest(Connection db, String requestKey, String key) throws SQLException {
        // Retain every admitted ID until its operation expires, including retries using a new ID.
        try (PreparedStatement insert = db.prepareStatement("""
                INSERT INTO written_requests (request_key, replay_key)
                SELECT ?, ? WHERE (SELECT count(*) FROM written_requests WHERE replay_key=?) < ?
                """)) {
            insert.setString(1, requestKey);
            insert.setString(2, key);
            insert.setString(3, key);
            insert.setInt(4, MAX_REQUEST_IDS_PER_ENTRY);
            return insert.executeUpdate() == 1;
        }
    }

    void complete(Claim claim, byte[] body, boolean songStudyAcknowledged) throws IOException {
        if (claim.state() != State.CLAIMED || body == null || body.length == 0 || body.length > MAX_RESULT) {
            throw new IOException("Invalid written reflection result");
        }
        try (Connection db = database.open(); PreparedStatement update = db.prepareStatement("""
                UPDATE written_replays SET result=?, song_study=?, expires_at=?
                WHERE replay_key=? AND reservation=? AND result IS NULL AND expires_at>?
                """)) {
            long now = clock.millis();
            update.setBytes(1, body);
            update.setBoolean(2, songStudyAcknowledged);
            update.setLong(3, now + RETENTION_MILLIS);
            update.setString(4, claim.key());
            update.setString(5, claim.reservation());
            update.setLong(6, now);
            if (update.executeUpdate() != 1) throw new IOException("Written reflection reservation is no longer current");
        } catch (SQLException failure) {
            throw new IOException("Cannot save written reflection", failure);
        }
    }
}
