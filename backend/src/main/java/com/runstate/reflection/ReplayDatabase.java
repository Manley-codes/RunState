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
import java.util.HexFormat;
import java.util.Properties;

/** SQLite settings and private file handling shared by the two paid-request replay stores. */
final class ReplayDatabase {
    private final String databaseUrl;
    private final String deleteExpiredSql;

    ReplayDatabase(Path directory, String filename, String deleteExpiredSql, long now,
                   String... schemaStatements) throws IOException {
        this.deleteExpiredSql = deleteExpiredSql;
        Path folder = directory.toAbsolutePath().normalize();
        Path file = folder.resolve(filename);
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
        // Do not let a running instance silently create a fresh ledger if its file disappears.
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
                    if (tables.getInt(1) != 0) throw new IOException("Unrecognized replay database");
                }
                for (String statement : schemaStatements) sql.executeUpdate(statement);
                sql.executeUpdate("PRAGMA user_version = 1");
            } else if (version != 1) {
                throw new IOException("Unsupported replay schema");
            }
            purgeExpired(db, now);
            db.commit();
        } catch (SQLException e) {
            throw new IOException("Cannot open replay storage", e);
        }
    }

    Connection open() throws SQLException {
        Properties options = new Properties();
        options.setProperty("busy_timeout", "5000");
        // IMMEDIATE makes the read-and-reserve transaction exclusive across processes.
        options.setProperty("transaction_mode", "IMMEDIATE");
        options.setProperty("synchronous", "FULL");
        options.setProperty("secure_delete", "ON");
        options.setProperty("foreign_keys", "ON");
        return DriverManager.getConnection(databaseUrl, options);
    }

    void purgeExpired(Connection db, long now) throws SQLException {
        try (PreparedStatement purge = db.prepareStatement(deleteExpiredSql)) {
            purge.setLong(1, now);
            purge.executeUpdate();
        }
    }

    static String scopedKey(String namespace, String principalId, String value) {
        return scopedKey(namespace, principalId, value.getBytes(StandardCharsets.UTF_8));
    }

    static String scopedKey(String namespace, String principalId, byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(namespace.getBytes(StandardCharsets.UTF_8));
            // The authenticated principal cannot contain NUL.
            digest.update(principalId.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
