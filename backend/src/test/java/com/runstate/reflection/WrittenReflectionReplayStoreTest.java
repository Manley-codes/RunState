package com.runstate.reflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.runstate.reflection.WrittenReflectionReplayStore.State.*;
import static org.junit.jupiter.api.Assertions.*;

final class WrittenReflectionReplayStoreTest {
    @TempDir Path directory;
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");
    private static final byte[] RESULT = "saved answer".getBytes(StandardCharsets.UTF_8);

    private WrittenReflectionReplayStore storeAt(long seconds, int capacity) throws IOException {
        return new WrittenReflectionReplayStore(directory, Clock.fixed(START.plusSeconds(seconds), ZoneOffset.UTC), capacity);
    }

    @Test void resultsAndRequestIdBindingsExpire24HoursAfterCompletion() throws IOException {
        var first = storeAt(0, 1);
        var claim = first.claim("runner A", "request A", "run evidence A", true);
        assertEquals(CLAIMED, claim.state());
        assertFalse(first.hasSavedRun("runner A", "run"));
        var finishing = storeAt(15, 1);
        finishing.complete(claim, RESULT, true, "runner A", "run");
        var lastSecond = storeAt(24 * 3600 + 14, 1);
        assertTrue(lastSecond.hasSavedRun("runner A", "run"));
        assertFalse(lastSecond.hasSavedRun("runner B", "run"));
        var replay = lastSecond.claim("runner A", "retry B", "run evidence A", false);
        assertEquals(CACHED, replay.state());
        assertArrayEquals(RESULT, replay.body());
        assertTrue(replay.songStudyAcknowledged());
        assertEquals(CONFLICT, lastSecond.claim("runner A", "retry B", "run evidence B", false).state());
        assertEquals(FULL, lastSecond.claim("runner B", "request A", "run evidence A", true).state());
        var atExpiry = storeAt(24 * 3600 + 15, 1);
        assertFalse(atExpiry.hasSavedRun("runner A", "run"));
        assertEquals(BUSY, atExpiry.claim("runner A", "request A", "run evidence A", false).state());
        assertEquals(CLAIMED, atExpiry.claim("runner A", "retry B", "run evidence B", true).state());
    }

    @Test void pendingReservationIsBoundedAndCannotOverwriteLaterWork() throws IOException {
        var first = storeAt(0, 2);
        String metadata = "run with " + (char) 0xd800;
        var initial = first.claim("runner", "attempt", metadata, true);
        assertEquals(CONFLICT, first.claim("runner", "attempt", "run with ?", true).state());
        var beforeExpiry = storeAt(24 * 3600 - 1, 2);
        assertEquals(PENDING, beforeExpiry.claim("runner", "retry", metadata, true).state());
        assertEquals(CLAIMED, beforeExpiry.claim("other", "attempt", metadata, true).state());
        var afterExpiry = storeAt(24 * 3600, 2);
        assertThrows(IOException.class, () -> afterExpiry.complete(initial, RESULT, false));
        var next = afterExpiry.claim("runner", "retry", metadata, true);
        assertEquals(CLAIMED, next.state());
        assertThrows(IOException.class, () -> first.complete(initial, RESULT, true));
        afterExpiry.complete(next, RESULT, false);
        assertFalse(afterExpiry.claim("runner", "retry", metadata, true).songStudyAcknowledged());
    }

    @Test void excessiveRetryIdsDoNotEvictKnownIdsOrBlockFreshWork() throws IOException {
        var store = storeAt(0, 2);
        var original = store.claim("runner", "request-0", "evidence", true);
        store.complete(original, RESULT, true);
        for (int i = 1; i < WrittenReflectionReplayStore.MAX_REQUEST_IDS_PER_ENTRY; i++) {
            assertEquals(CACHED, store.claim("runner", "request-" + i, "evidence", false).state());
        }
        assertEquals(FULL, store.claim("runner", "excess", "evidence", false).state());
        assertEquals(CACHED, store.claim("runner", "request-0", "evidence", false).state());
        assertEquals(CONFLICT, store.claim("runner", "request-1", "other evidence", true).state());
        assertEquals(CLAIMED, store.claim("runner", "excess", "other evidence", true).state());
    }

    @Test void privateInputsAreHashedAndAnUnavailableLedgerCannotAdmitWork() throws IOException {
        var store = storeAt(0, 1);
        var claim = store.claim("private-runner", "private-request", "private-evidence", true);
        store.complete(claim, RESULT, true);
        Path file = directory.resolve("written-reflections.sqlite3");
        String contents = Files.readString(file, StandardCharsets.ISO_8859_1);
        assertTrue(contents.contains("saved answer"));
        assertFalse(contents.contains("private-runner"));
        assertFalse(contents.contains("private-request"));
        assertFalse(contents.contains("private-evidence"));
        Files.move(file, directory.resolve("offline.sqlite3"));
        assertThrows(IOException.class, () -> store.claim("private-runner", "private-request", "private-evidence", true));
        assertFalse(Files.exists(file));
    }
}
