package com.runstate.reflection;

import com.runstate.reflection.BackendAccessPolicy.Principal;
import com.runstate.reflection.BackendAccessPolicy.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ReflectionAllowanceTest {
    @TempDir Path directory;
    static final Principal trial = new Principal("runner-1", Tier.TRIAL);
    static final Principal pilot = new Principal("runner-2", Tier.PILOT);
    static final Instant START = Instant.parse("2026-11-27T12:00:00Z");
    static final byte[] REPLY = """
            {"candidates":{"spent":"A","feeling_good":"B","powered_up":"C","no_selection":"D"}}
            """.getBytes(StandardCharsets.UTF_8);
    static String id() { return UUID.randomUUID().toString(); }

    ReflectionAllowance at(Instant now) throws Exception {
        return new ReflectionAllowance(directory, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void trialRequiresOptInLastsSevenFullDaysAndNeverRestarts() throws Exception {
        var first = at(START);
        String run = id();
        assertEquals("available", first.status(trial).status());
        assertThrows(ReflectionAllowance.Denied.class, () -> first.admit(trial, run));
        var active = first.activate(trial);
        assertEquals(2, active.remaining());
        assertEquals(START.plusSeconds(7 * 86_400).toEpochMilli(), active.end());
        assertEquals(1, first.admit(trial, run).remaining());
        var later = at(START.plusSeconds(86_399));
        assertEquals(active.end(), later.activate(trial).end());
        assertEquals(1, later.admit(trial, run).remaining());
        var stale = at(START.plusSeconds(86_400));
        assertEquals(2, stale.status(trial).remaining()); // an unanswered, abandoned hold expires.
        assertThrows(ReflectionAllowance.Denied.class, () -> stale.reservePreparation(trial, run, id()));
        var end = at(START.plusSeconds(7 * 86_400));
        assertEquals("expired", end.activate(trial).status());
        assertThrows(ReflectionAllowance.Denied.class, () -> end.admit(trial, id()));
        assertThrows(ReflectionAllowance.Denied.class, () -> end.startWriting(trial, run, "unpaid evidence"));
        assertThrows(ReflectionAllowance.Denied.class, () -> end.selectVoice(trial, run, "unpaid voice"));
    }

    @Test void eightOperatorAssignedPilotSlotsRenewOncePerUtcMonth() throws Exception {
        var now = at(START);
        for (int used = 1; used <= 8; used++) {
            String run = id();
            assertEquals(8 - used, now.admit(pilot, run).remaining());
            now.startWriting(pilot, run, "evidence");
            now.settle(pilot, run, "evidence", REPLY);
        }
        assertEquals(0, at(Instant.parse("2026-11-30T23:59:59Z")).status(pilot).remaining());
        assertThrows(ReflectionAllowance.Denied.class, () -> now.admit(pilot, id()));
        assertEquals(7, at(Instant.parse("2026-12-01T00:00:00Z")).admit(pilot, id()).remaining());
        assertThrows(ReflectionAllowance.Denied.class, () -> now.activate(pilot));
    }

    @Test void simultaneousDevicesCanReserveOnlyTwoDistinctTrialRuns() throws Exception {
        var first = at(START);
        var second = at(START);
        first.activate(trial);
        var pool = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        try {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 10; i++) {
                var client = i % 2 == 0 ? first : second;
                Callable<Boolean> book = () -> {
                    start.await();
                    try { client.admit(trial, id()); return true; }
                    catch (ReflectionAllowance.Denied denied) { return false; }
                };
                results.add(pool.submit(book));
            }
            start.countDown();
            int admitted = 0;
            for (var result : results) if (result.get(10, TimeUnit.SECONDS)) admitted++;
            assertEquals(2, admitted);
            assertEquals(0, second.status(trial).remaining());
        } finally { pool.shutdownNow(); }
    }

    @Test void repeatedPrepsUseOneRunAndOnlyTheSavedWriterOutputCanBeVoiced() throws Exception {
        var store = at(START);
        String run = id();
        store.activate(trial);
        store.admit(trial, run);
        String firstPrep = id();
        store.reservePreparation(trial, run, firstPrep);
        assertThrows(ReflectionAllowance.Denied.class, () -> store.reservePreparation(trial, run, firstPrep));
        for (int i = 1; i < 12; i++) store.reservePreparation(trial, run, id());
        assertThrows(ReflectionAllowance.Denied.class, () -> store.reservePreparation(trial, run, id()));
        assertEquals(1, store.status(trial).remaining());
        store.startWriting(trial, run, "original");
        assertThrows(ReflectionAllowance.Denied.class, () -> store.startWriting(trial, run, "changed"));
        store.settle(trial, run, "changed", REPLY);
        assertThrows(ReflectionAllowance.Denied.class, () -> store.selectVoice(trial, run, "A"));
        store.settle(trial, run, "original", REPLY);
        assertTrue(store.selectVoice(trial, run, "A"));
        store.releaseUnsentVoice(trial, run, "A");
        assertTrue(store.selectVoice(trial, run, "B"));
        assertFalse(store.selectVoice(trial, run, "B"));
        assertThrows(ReflectionAllowance.Denied.class, () -> store.selectVoice(trial, run, "C"));
        assertThrows(ReflectionAllowance.Denied.class, () -> store.selectVoice(trial, run, "arbitrary"));
        assertThrows(ReflectionAllowance.Denied.class, () -> store.selectVoice(pilot, run, "B"));
        assertThrows(ReflectionAllowance.Denied.class, () -> store.reservePreparation(trial, run, id()));
    }

    @Test void neverDispatchedPreparationOrWriterImmediatelyReleasesAnOtherwiseEmptySlot() throws Exception {
        var store = at(START);
        store.activate(trial);
        String run = id(), prep = id();
        store.admit(trial, run);
        store.reservePreparation(trial, run, prep);
        store.releaseUnsentPreparation(trial, run, prep);
        assertEquals(2, store.status(trial).remaining());
        store.admit(trial, run);
        store.startWriting(trial, run, "ordinary");
        store.releaseUnsentWriter(trial, run);
        assertEquals(2, store.status(trial).remaining());
    }

    @Test void abandoningRunsDoesNotRenewThePeriodPreparationBudget() throws Exception {
        var store = at(START);
        store.activate(trial);
        for (int i = 0; i < 2; i++) {
            String run = id();
            store.admit(trial, run);
            for (int p = 0; p < 12; p++) store.reservePreparation(trial, run, id());
        }
        var nextDay = at(START.plusSeconds(86_400));
        String run = id();
        assertEquals(1, nextDay.admit(trial, run).remaining());
        assertThrows(ReflectionAllowance.Denied.class, () -> nextDay.reservePreparation(trial, run, id()));
        nextDay.startWriting(trial, run, "can still finish");
    }
}
