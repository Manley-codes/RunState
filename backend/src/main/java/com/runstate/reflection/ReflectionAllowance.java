package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.BackendAccessPolicy.Principal;
import com.runstate.reflection.BackendAccessPolicy.Tier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

/** Optional pilot entitlements. Every slot belongs to the authenticated runner and one immutable run. */
final class ReflectionAllowance {
    private static final long DAY = Duration.ofDays(1).toMillis();
    private static final int TRIAL_RUNS = 2;
    private static final int PILOT_RUNS = 8;
    private static final int PREPARATIONS_PER_RUN = 12;
    private final ReplayDatabase database;
    private final Clock clock;

    ReflectionAllowance(Path directory, Clock clock) throws IOException {
        this.clock = clock;
        database = new ReplayDatabase(directory, "reflection-allowances.sqlite3",
                "DELETE FROM admissions WHERE keep_until <= ?", clock.millis(), """
                CREATE TABLE trials (owner TEXT PRIMARY KEY, started INTEGER NOT NULL) STRICT
                """, """
                CREATE TABLE admissions (
                    id TEXT PRIMARY KEY, owner TEXT NOT NULL, tier TEXT NOT NULL, period_end INTEGER NOT NULL,
                    keep_until INTEGER NOT NULL, state TEXT NOT NULL CHECK(state IN ('held','writing','done')),
                    operation TEXT, selected_voice TEXT
                ) STRICT
                """, """
                CREATE INDEX monthly_admissions ON admissions(owner, tier, period_end)
                """, """
                CREATE TABLE preparations (id TEXT PRIMARY KEY, run TEXT NOT NULL REFERENCES admissions(id) ON DELETE CASCADE) STRICT
                """, """
                CREATE INDEX preparations_by_run ON preparations(run)
                """, """
                CREATE TABLE period_preparations(owner TEXT NOT NULL,tier TEXT NOT NULL,period_end INTEGER NOT NULL,
                    count INTEGER NOT NULL,PRIMARY KEY(owner,tier,period_end)) STRICT
                """, """
                CREATE TABLE permitted_voices (run TEXT NOT NULL REFERENCES admissions(id) ON DELETE CASCADE,
                    text_hash TEXT NOT NULL, PRIMARY KEY(run,text_hash)) STRICT
                """);
    }

    record View(String status, int remaining, long end) {
        String json(Tier tier) {
            JsonObject json = new JsonObject();
            json.addProperty("tier", tier.name().toLowerCase(Locale.ROOT));
            json.addProperty("status", status);
            if (remaining >= 0) json.addProperty("remaining", remaining);
            if (end > 0) json.addProperty("expiresAt", Instant.ofEpochMilli(end).toString());
            return json.toString();
        }
    }

    static final class Denied extends RuntimeException {
        final String code;
        Denied(String code) { super("Reflection access refused."); this.code = code; }
    }

    static View basicView(Tier tier) {
        return tier == Tier.OWNER ? new View("owner", -1, 0) : new View("tracking", 0, 0);
    }

    View status(Principal runner) throws IOException {
        return transaction((db, now) -> view(db, runner, now));
    }

    View activate(Principal runner) throws IOException {
        if (runner.tier() != Tier.TRIAL) throw new Denied("trial_unavailable");
        return transaction((db, now) -> {
            update(db, "INSERT OR IGNORE INTO trials(owner,started) VALUES(?,?)", owner(runner), now);
            return view(db, runner, now);
        });
    }

    View admit(Principal runner, String runId) throws IOException {
        return transaction((db, now) -> {
            View view = active(db, runner, now);
            String run = run(runner, runId);
            if (number(db, "SELECT count(*) FROM admissions WHERE id=? AND tier=? AND period_end=?",
                    run, runner.tier().name(), view.end()) > 0) return view; // The final slot still belongs to this run.
            if (view.remaining() == 0) throw new Denied("reflection_limit_reached");
            // Completed/partially-written runs cannot be re-opened against a new payment period.
            if (number(db, "SELECT count(*) FROM admissions WHERE id=?", run) != 0)
                throw new Denied("reflection_already_reserved");
            update(db, "INSERT INTO admissions(id,owner,tier,period_end,keep_until,state) VALUES(?,?,?,?,?,'held')",
                    run, owner(runner), runner.tier().name(), view.end(), Math.min(view.end(), now + DAY));
            return view(db, runner, now);
        });
    }

    void reservePreparation(Principal runner, String runId, String preparationId) throws IOException {
        transaction((db, now) -> {
            String run = requireHeld(db, runner, runId, now);
            if (number(db, "SELECT count(*) FROM preparations WHERE run=?", run) >= PREPARATIONS_PER_RUN ||
                    update(db, "INSERT OR IGNORE INTO preparations(id,run) VALUES(?,?)",
                            prep(runner, preparationId), run) == 0)
                throw new Denied("song_preparation_not_available");
            View view = view(db, runner, now);
            int maxPreps = PREPARATIONS_PER_RUN * (runner.tier() == Tier.TRIAL ? TRIAL_RUNS : PILOT_RUNS);
            if (update(db, "INSERT INTO period_preparations VALUES(?,?,?,1) ON CONFLICT(owner,tier,period_end) "
                            + "DO UPDATE SET count=count+1 WHERE count<?", owner(runner), runner.tier().name(),
                    view.end(), maxPreps) == 0) throw new Denied("song_preparation_not_available");
            return null;
        });
    }

    void releaseUnsentPreparation(Principal runner, String runId, String preparationId) throws IOException {
        transaction((db, now) -> {
            String run = run(runner, runId);
            if (update(db, "DELETE FROM preparations WHERE run=? AND id=?", run, prep(runner, preparationId)) == 1) {
                update(db, "UPDATE period_preparations SET count=count-1 WHERE owner=? AND tier=? AND "
                                + "period_end=(SELECT period_end FROM admissions WHERE id=?)",
                        owner(runner), runner.tier().name(), run);
            }
            releaseEmptyRun(db, run);
            return null;
        });
    }

    void startWriting(Principal runner, String runId, String canonicalInput) throws IOException {
        transaction((db, now) -> {
            String run = requireHeld(db, runner, runId, now);
            update(db, "UPDATE admissions SET state='writing',operation=?,keep_until=? WHERE id=?",
                    key("operation", runner, canonicalInput), now + DAY, run);
            return null;
        });
    }

    void releaseUnsentWriter(Principal runner, String runId) throws IOException {
        transaction((db, now) -> {
            String run = run(runner, runId);
            update(db, "UPDATE admissions SET state='held',operation=NULL WHERE id=? AND state='writing'", run);
            releaseEmptyRun(db, run);
            return null;
        });
    }

    private static void releaseEmptyRun(Connection db, String run) throws SQLException {
        update(db, "DELETE FROM admissions WHERE id=? AND state='held' AND NOT EXISTS "
                + "(SELECT 1 FROM preparations WHERE run=?)", run, run);
    }

    /** Called after written replay storage is durable; an exact replay can repair an interrupted settlement. */
    void settle(Principal runner, String runId, String input, byte[] savedResponse) throws IOException {
        transaction((db, now) -> {
            String run = run(runner, runId);
            int settled = update(db, "UPDATE admissions SET state='done',keep_until=max(period_end,?) "
                            + "WHERE id=? AND state='writing' AND operation=?",
                    now + 7 * DAY, run, key("operation", runner, input));
            if (settled == 1) {
                var candidates = JsonParser.parseString(new String(savedResponse, StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("candidates");
                for (String answer : List.of("spent", "feeling_good", "powered_up", "no_selection")) {
                    update(db, "INSERT OR IGNORE INTO permitted_voices VALUES(?,?)",
                            run, key("voice", runner, candidates.get(answer).getAsString()));
                }
            }
            return null;
        });
    }

    boolean selectVoice(Principal runner, String runId, String text) throws IOException {
        return transaction((db, now) -> {
            View view = active(db, runner, now);
            String run = run(runner, runId);
            String voice = key("voice", runner, text);
            boolean first = number(db, "SELECT count(*) FROM admissions WHERE id=? AND selected_voice IS NULL", run) == 1;
            if (update(db, "UPDATE admissions SET selected_voice=? WHERE id=? AND tier=? AND period_end=? "
                        + "AND state='done' AND (selected_voice IS NULL OR selected_voice=?) "
                        + "AND EXISTS(SELECT 1 FROM permitted_voices WHERE run=? AND text_hash=?)",
                    voice, run, runner.tier().name(), view.end(), voice, run, voice) == 0)
                throw new Denied("reflection_voice_not_included");
            return first;
        });
    }

    void releaseUnsentVoice(Principal runner, String runId, String text) throws IOException {
        transaction((db, now) -> {
            update(db, "UPDATE admissions SET selected_voice=NULL WHERE id=? AND selected_voice=?",
                    run(runner, runId), key("voice", runner, text));
            return null;
        });
    }

    private View active(Connection db, Principal runner, long now) throws SQLException {
        View view = view(db, runner, now);
        if (!view.status().equals("active")) throw new Denied("reflection_access_required");
        return view;
    }

    private String requireHeld(Connection db, Principal runner, String runId, long now) throws SQLException {
        View view = active(db, runner, now);
        String run = run(runner, runId);
        if (number(db, "SELECT count(*) FROM admissions WHERE id=? AND tier=? AND period_end=? AND state='held'",
                run, runner.tier().name(), view.end()) == 0) throw new Denied("run_admission_required");
        return run;
    }

    private static View view(Connection db, Principal runner, long now) throws SQLException {
        long end;
        int capacity;
        if (runner.tier() == Tier.TRIAL) {
            long start = number(db, "SELECT started FROM trials WHERE owner=?", owner(runner));
            if (start < 0) return new View("available", TRIAL_RUNS, 0);
            end = start + 7 * DAY;
            if (now < start || now >= end) return new View("expired", 0, end);
            capacity = TRIAL_RUNS;
        } else if (runner.tier() == Tier.PILOT) {
            end = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).toLocalDate()
                    .withDayOfMonth(1).plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            capacity = PILOT_RUNS;
        } else return basicView(runner.tier());
        int used = (int) number(db, "SELECT count(*) FROM admissions WHERE owner=? AND tier=? AND period_end=?",
                owner(runner), runner.tier().name(), end);
        return new View("active", Math.max(0, capacity - used), end);
    }

    private interface Work<T> { T apply(Connection db, long now) throws SQLException; }

    private <T> T transaction(Work<T> work) throws IOException {
        try (Connection db = database.open()) {
            db.setAutoCommit(false); // ReplayDatabase uses SQLite BEGIN IMMEDIATE, including between processes.
            long now = clock.millis();
            database.purgeExpired(db, now);
            update(db, "DELETE FROM period_preparations WHERE period_end<=?", now);
            T result = work.apply(db, now);
            db.commit();
            return result;
        } catch (SQLException failed) {
            throw new IOException("Private reflection access storage is unavailable", failed);
        }
    }

    private static int update(Connection db, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            bind(statement, args);
            return statement.executeUpdate();
        }
    }

    private static long number(Connection db, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            bind(statement, args);
            try (var result = statement.executeQuery()) { return result.next() ? result.getLong(1) : -1; }
        }
    }

    private static void bind(PreparedStatement statement, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
    }

    private static String run(Principal runner, String runId) {
        ReflectionContract.requireCanonicalUuid(runId, "runId");
        return key("run", runner, runId);
    }

    private static String prep(Principal runner, String preparationId) { return key("prep", runner, preparationId); }
    private static String owner(Principal runner) { return key("owner", runner, ""); }
    private static String key(String namespace, Principal runner, String value) {
        return ReplayDatabase.scopedKey("reflection-access-v1-" + namespace + "\0", runner.id(), value);
    }
}
