package com.runstate.reflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static com.runstate.reflection.ProviderSpendBudget.Decision.*;
import static com.runstate.reflection.ProviderSpendBudget.Provider.*;
import static org.junit.jupiter.api.Assertions.*;

final class ProviderSpendBudgetTest {
    @TempDir Path directory;
    private static final String LAST_MINUTE = "2026-11-30T23:59:59Z";

    private ProviderSpendBudget open(ProviderSpendBudget.Limits limits, String time) throws IOException {
        // The ledger must roll over in UTC regardless of the server's local timezone.
        return new ProviderSpendBudget(directory, limits, Clock.fixed(Instant.parse(time), ZoneId.of("America/Chicago")));
    }

    @Test void independentRunnerAndSharedLimitsSurviveReopenAndDenyWithoutPartialDebit() throws IOException {
        var caps = new ProviderSpendBudget.Limits(6, 9, 3, 2, 0);
        var first = open(caps, LAST_MINUTE);
        assertEquals(ALLOWED, first.reserve("runner-A-private", ANTHROPIC));
        assertEquals(ALLOWED, first.reserve("runner-A-private", ELEVENLABS));
        var restarted = open(caps, LAST_MINUTE);
        assertEquals(RUNNER_LIMIT, restarted.reserve("runner-A-private", ELEVENLABS));
        assertEquals(ALLOWED, restarted.reserve("runner-B", ELEVENLABS));
        assertEquals(GLOBAL_LIMIT, restarted.reserve("runner-B", ANTHROPIC));
        assertEquals(ALLOWED, restarted.reserve("runner-B", ELEVENLABS));
        assertEquals(GLOBAL_LIMIT, restarted.reserve("runner-C", ANTHROPIC));
        assertFalse(Files.readString(directory.resolve("provider-spend.sqlite3"), StandardCharsets.ISO_8859_1)
                .contains("runner-A-private"));
    }

    @Test void monthRollsOverAtUtcMidnightAndBackwardClockCannotRechargePreviousMonth() throws IOException {
        var caps = new ProviderSpendBudget.Limits(1, 2, 1, 1, 0);
        var november = open(caps, LAST_MINUTE);
        assertEquals(ALLOWED, november.reserve("runner", ANTHROPIC));
        assertEquals(RUNNER_LIMIT, november.reserve("runner", ELEVENLABS));
        var december = open(caps, "2026-12-01T00:00:00Z");
        assertEquals(ALLOWED, december.reserve("runner", ELEVENLABS));
        assertThrows(IOException.class, () -> november.reserve("runner", ANTHROPIC));
        assertEquals(RUNNER_LIMIT, december.reserve("runner", ANTHROPIC));
    }

    @Test void concurrentServerConnectionsCannotOverspendEitherLimit() throws Exception {
        var caps = new ProviderSpendBudget.Limits(2, 4, 1, 1, 0);
        var first = open(caps, LAST_MINUTE);
        var second = open(caps, LAST_MINUTE);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var results = new ArrayList<Future<ProviderSpendBudget.Decision>>();
            for (int i = 0; i < 16; i++) {
                var budget = i % 4 < 2 ? first : second;
                String principal = "runner-" + (i % 3);
                results.add(pool.submit(() -> {
                    start.await();
                    return budget.reserve(principal, ANTHROPIC);
                }));
            }
            start.countDown();
            int[] admitted = new int[3];
            for (int i = 0; i < results.size(); i++) {
                if (results.get(i).get(10, TimeUnit.SECONDS) == ALLOWED) admitted[i % 3]++;
            }
            assertEquals(4, admitted[0] + admitted[1] + admitted[2]);
            for (int runnerCalls : admitted) assertTrue(runnerCalls <= 2);
            assertEquals(GLOBAL_LIMIT, second.reserve("another", ANTHROPIC));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test void missingLedgerAndOverflowCannotAdmitExtraSends() throws IOException {
        var caps = new ProviderSpendBudget.Limits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, 1, 0);
        var budget = open(caps, LAST_MINUTE);
        assertEquals(ALLOWED, budget.reserve("runner", ANTHROPIC));
        assertEquals(RUNNER_LIMIT, budget.reserve("runner", ELEVENLABS));
        assertEquals(GLOBAL_LIMIT, budget.reserve("another", ELEVENLABS));
        Path ledger = directory.resolve("provider-spend.sqlite3");
        Files.move(ledger, directory.resolve("unavailable.sqlite3"));
        assertThrows(IOException.class, () -> budget.reserve("another", ELEVENLABS));
        assertFalse(Files.exists(ledger));
    }

    @Test void serverRequiresPersistentStorageAndOneTransportAttemptBeforeSpending() throws IOException {
        var settings = new HashMap<>(Map.of(
                "RUNSTATE_RUNNER_MONTHLY_USD", "1", "RUNSTATE_GLOBAL_MONTHLY_USD", "2",
                "RUNSTATE_ELEVENLABS_MAX_CALL_USD", ".01"));
        assertNull(ReflectionBackendMain.configuredSpendBudget(settings, true, "1"));
        settings.put("RUNSTATE_SPEECH_REPLAY_DIR", directory.toString());
        assertNull(ReflectionBackendMain.configuredSpendBudget(settings, true, null));
        assertNull(ReflectionBackendMain.configuredSpendBudget(settings, true, "2"));
        var enabled = ReflectionBackendMain.configuredSpendBudget(settings, true, "1");
        assertNotNull(enabled);
        assertEquals(ALLOWED, enabled.reserve("runner", ELEVENLABS));
    }

    @Test void environmentLimitsRequireExplicitAmountsAndPositivePaidProviderCeilings() throws IOException {
        var settings = new HashMap<>(Map.of(
                "RUNSTATE_RUNNER_MONTHLY_USD", ".20", "RUNSTATE_GLOBAL_MONTHLY_USD", "0.40",
                "RUNSTATE_ELEVENLABS_MAX_CALL_USD", "0.000001", "RUNSTATE_MUSIXMATCH_MAX_CALL_USD", "0"));
        var limits = ProviderSpendBudget.Limits.fromEnvironment(settings);
        assertEquals(200_000, limits.runnerMonthly());
        assertEquals(400_000, limits.globalMonthly());
        assertEquals(1L, limits.elevenLabsMaxPerCall());
        var budget = open(limits, LAST_MINUTE);
        assertEquals(UNCONFIGURED, budget.reserve("runner", ANTHROPIC));
        assertEquals(ALLOWED, budget.reserve("runner", MUSIXMATCH));
        settings.remove("RUNSTATE_MUSIXMATCH_MAX_CALL_USD");
        assertEquals(UNCONFIGURED, open(ProviderSpendBudget.Limits.fromEnvironment(settings), LAST_MINUTE)
                .reserve("runner", MUSIXMATCH));
        assertThrows(IllegalArgumentException.class, () -> ProviderSpendBudget.Limits.fromEnvironment(Map.of()));
        for (String invalid : new String[]{"0", "-1", "0.0000001", "NaN", "1e3", "9223372036854.775808", " "}) {
            settings.put("RUNSTATE_ANTHROPIC_MAX_CALL_USD", invalid);
            assertThrows(IllegalArgumentException.class, () -> ProviderSpendBudget.Limits.fromEnvironment(settings));
        }
        var off = open(new ProviderSpendBudget.Limits(1, 0, 1, 1, 0), LAST_MINUTE);
        assertEquals(GLOBAL_LIMIT, off.reserve("runner", MUSIXMATCH));
        var disabledRunner = open(new ProviderSpendBudget.Limits(0, 1, 1, 1, 0), LAST_MINUTE);
        assertEquals(RUNNER_LIMIT, disabledRunner.reserve("runner", MUSIXMATCH));
    }
}
