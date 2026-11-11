package com.runstate.reflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.runstate.reflection.SpeechReplayStore.State.*;
import static org.junit.jupiter.api.Assertions.*;

final class SpeechReplayStoreTest {
    @TempDir Path directory;
    private static final Instant START = Instant.parse("2026-11-11T12:00:00Z");
    private static final byte[] AUDIO = {1, 2, 3};

    private SpeechReplayStore storeAt(Instant time, int capacity) throws IOException {
        return new SpeechReplayStore(directory, Clock.fixed(time, ZoneOffset.UTC), capacity);
    }

    @Test void entriesRemainReplayableAtCapacityUntilExactlySevenDays() throws IOException {
        Instant expiry = START.plus(Duration.ofDays(7));
        SpeechReplayStore initial = storeAt(START, 1);
        SpeechReplayStore before = storeAt(expiry.minusMillis(1), 1);
        SpeechReplayStore atExpiry = storeAt(expiry, 1);
        var claim = initial.claim("recipient:alpha", "Reflection A", true);
        initial.complete(claim, AUDIO);
        assertEquals(FULL, before.claim("recipient:alpha", "Reflection B", true).state());
        var replay = before.claim("recipient:alpha", "Reflection A", false);
        assertEquals(CACHED, replay.state());
        assertArrayEquals(AUDIO, replay.audio());
        assertEquals(BUSY, atExpiry.claim("recipient:alpha", "Reflection A", false).state());
        var fresh = atExpiry.claim("recipient:alpha", "Reflection A", true);
        assertEquals(CLAIMED, fresh.state());
        assertNotEquals(claim.reservation(), fresh.reservation());
    }

    @Test void expiredPendingReservationCannotOverwriteTheNextOwnersRecording() throws IOException {
        SpeechReplayStore first = storeAt(START, 1);
        var old = first.claim("owner", "Reflection", true);
        assertThrows(IOException.class, () -> first.complete(old, new byte[0]));
        SpeechReplayStore restarted = storeAt(START.plus(Duration.ofDays(6)), 1);
        assertEquals(PENDING, restarted.claim("owner", "Reflection", true).state());
        assertEquals(FULL, restarted.claim("owner", "Different", true).state());
        SpeechReplayStore expired = storeAt(START.plus(Duration.ofDays(7)), 1);
        var replacement = expired.claim("owner", "Reflection", true);
        assertEquals(CLAIMED, replacement.state());
        assertThrows(IOException.class, () -> first.complete(old, AUDIO));
        expired.complete(replacement, new byte[]{4, 5});
        assertArrayEquals(new byte[]{4, 5}, expired.claim("owner", "Reflection", false).audio());
    }

    @Test void rawTextAndPrincipalAreAbsentAndLossOfDatabaseFailsClosed() throws IOException {
        SpeechReplayStore store = storeAt(START, 2);
        String principal = "recipient:private-principal";
        String text = "The exact private reflection that was spoken.";
        var claim = store.claim(principal, text, true);
        Path database = directory.resolve("reflection-speech.sqlite3");
        String disk = Files.readString(database, StandardCharsets.ISO_8859_1);
        assertFalse(disk.contains(text));
        assertFalse(disk.contains(principal));
        Files.move(database, directory.resolve("offline.sqlite3"));
        assertThrows(IOException.class, () -> store.claim(principal, text, true));
        assertThrows(IOException.class, () -> store.complete(claim, AUDIO));
        assertFalse(Files.exists(database));
        Files.writeString(database, "damaged existing database");
        assertThrows(IOException.class, () -> storeAt(START, 2));
    }
}
