package com.runstate.reflection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Properties;
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

    private final String databaseUrl;
    private final Clock clock;
    private final int capacity;

    SpeechReplayStore(Path directory) throws IOException {
        this(directory, Clock.systemUTC(), MAX_ENTRIES);
    }

    SpeechReplayStore(Path directory, Clock clock, int capacity) throws IOException {
        if (capacity < 1 || capacity > MAX_ENTRIES) throw new IllegalArgumentException("Invalid replay capacity");
        this.capacity = capacity;
        this.clock = clock;
        Path folder = directory.toAbsolutePath().normalize();
        Path file = folder.resolve("reflection-speech.sqlite3");
        boolean posix = folder.getFileSystem().supportedFileAttributeViews().contains("posix");
        if (posix) {
            Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
        } else {
            Files.createDirectories(folder);
        }
        try {
            if (posix) {
                Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            } else {
                Files.createFile(file);
            }
        } catch (FileAlreadyExistsException existing) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid replay file");
        }
        // mode=rw prevents a running instance silently creating a fresh ledger if the file disappears.
        databaseUrl = "jdbc:sqlite:" + file.toUri().toASCIIString() + "?mode=rw";
        try (Connection db = open(); Statement sql = db.createStatement()) {
            db.setAutoCommit(false);
            int version;
            try (ResultSet schema = sql.executeQuery("PRAGMA user_version")) {
                schema.next();
                version = schema.getInt(1);
            }
            if (version == 0) {
                try (ResultSet tables = sql.executeQuery("SELECT count(*) FROM sqlite_schema")) {
                    tables.next();
                    if (tables.getInt(1) != 0) throw new IOException("Unrecognized speech replay database");
                }
                sql.executeUpdate("""
                        CREATE TABLE speech_replays (
                            replay_key TEXT PRIMARY KEY,
                            reservation TEXT NOT NULL,
                            expires_at INTEGER NOT NULL,
                            audio BLOB CHECK(audio IS NULL OR length(audio) BETWEEN 1 AND %d)
                        ) STRICT
                        """.formatted(MAX_AUDIO));
                sql.executeUpdate("PRAGMA user_version = 1");
            } else if (version != 1) {
                throw new IOException("Unsupported speech replay schema");
            }
            purgeExpired(db, clock.millis());
            db.commit();
        } catch (SQLException e) {
            throw new IOException("Cannot open speech replay storage", e);
        }
    }

    private Connection open() throws SQLException {
        Properties options = new Properties();
        options.setProperty("busy_timeout", "5000");
        // Serialize the lookup, capacity check and reservation even across independent processes.
        options.setProperty("transaction_mode", "IMMEDIATE");
        options.setProperty("synchronous", "FULL");
        options.setProperty("secure_delete", "ON");
        return DriverManager.getConnection(databaseUrl, options);
    }

    Claim claim(String principalId, String text, boolean allowNew) throws IOException {
        String key = replayKey(principalId, text);
        try (Connection db = open()) {
            db.setAutoCommit(false);
            long now = clock.millis();
            purgeExpired(db, now);
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

    void complete(Claim claim, byte[] audio) throws IOException {
        if (claim.state() != State.CLAIMED || audio == null || audio.length == 0 || audio.length > MAX_AUDIO) {
            throw new IOException("Invalid reflection speech completion");
        }
        // A stale provider result must never overwrite a newer reservation for the same input.
        try (Connection db = open(); PreparedStatement update = db.prepareStatement(
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

    private static void purgeExpired(Connection db, long now) throws SQLException {
        try (PreparedStatement purge = db.prepareStatement("DELETE FROM speech_replays WHERE expires_at <= ?")) {
            purge.setLong(1, now);
            purge.executeUpdate();
        }
    }

    private static String replayKey(String principalId, String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(VOICE_IDENTITY.getBytes(StandardCharsets.UTF_8));
            // Authenticated principal IDs cannot contain NUL. Neither raw inputs nor credentials are stored.
            digest.update(principalId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
