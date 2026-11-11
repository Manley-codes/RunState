package com.runstate.reflection;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;

/** Reserves an operator-configured upper bound before each potentially billable provider send. */
final class ProviderSpendBudget {
    enum Provider { ANTHROPIC, ELEVENLABS, MUSIXMATCH }
    enum Decision { ALLOWED, RUNNER_LIMIT, GLOBAL_LIMIT, UNCONFIGURED }

    // Monetary values are integer millionths of a US dollar. A missing ceiling disables that provider.
    record Limits(long runnerMonthly, long globalMonthly, Long anthropicMaxPerCall,
                  Long elevenLabsMaxPerCall, Long musixmatchMaxPerCall) {
        Limits {
            if (runnerMonthly < 0 || globalMonthly < 0
                    || (anthropicMaxPerCall != null && anthropicMaxPerCall <= 0)
                    || (elevenLabsMaxPerCall != null && elevenLabsMaxPerCall <= 0)
                    || (musixmatchMaxPerCall != null && musixmatchMaxPerCall < 0)) {
                throw new IllegalArgumentException("Invalid provider spending limits");
            }
        }

        Limits(long runnerMonthly, long globalMonthly, long anthropicMaxPerCall,
               long elevenLabsMaxPerCall, long musixmatchMaxPerCall) {
            this(runnerMonthly, globalMonthly, Long.valueOf(anthropicMaxPerCall),
                    Long.valueOf(elevenLabsMaxPerCall), Long.valueOf(musixmatchMaxPerCall));
        }

        static Limits fromEnvironment(Map<String, String> environment) {
            return new Limits(
                    requiredAmount(environment, "RUNSTATE_RUNNER_MONTHLY_USD"),
                    requiredAmount(environment, "RUNSTATE_GLOBAL_MONTHLY_USD"),
                    optionalAmount(environment, "RUNSTATE_ANTHROPIC_MAX_CALL_USD"),
                    optionalAmount(environment, "RUNSTATE_ELEVENLABS_MAX_CALL_USD"),
                    optionalAmount(environment, "RUNSTATE_MUSIXMATCH_MAX_CALL_USD"));
        }

        private static long requiredAmount(Map<String, String> environment, String name) {
            Long amount = optionalAmount(environment, name);
            if (amount == null) throw new IllegalArgumentException("Missing " + name);
            return amount;
        }

        private static Long optionalAmount(Map<String, String> environment, String name) {
            String value = environment.get(name);
            if (value == null) return null;
            String amount = value.strip();
            if (!amount.matches("(?:[0-9]{1,13}(?:\\.[0-9]{1,6})?|\\.[0-9]{1,6})")) {
                throw new IllegalArgumentException("Invalid USD amount for " + name);
            }
            try {
                return new BigDecimal(amount).movePointRight(6).longValueExact();
            } catch (ArithmeticException outOfRange) {
                throw new IllegalArgumentException("USD amount exceeds supported range for " + name);
            }
        }

        private Long ceiling(Provider provider) {
            return switch (provider) {
                case ANTHROPIC -> anthropicMaxPerCall;
                case ELEVENLABS -> elevenLabsMaxPerCall;
                case MUSIXMATCH -> musixmatchMaxPerCall;
            };
        }
    }

    private static final String GLOBAL_SCOPE = "global";
    private final ReplayDatabase database;
    private final Limits limits;
    private final Clock clock;

    ProviderSpendBudget(Path directory, Limits limits) throws IOException {
        this(directory, limits, Clock.systemUTC());
    }

    ProviderSpendBudget(Path directory, Limits limits, Clock clock) throws IOException {
        this.limits = Objects.requireNonNull(limits);
        this.clock = Objects.requireNonNull(clock);
        // Purge only together with recording the new month, so a clock correction cannot reset a cap.
        database = new ReplayDatabase(directory, "provider-spend.sqlite3",
                "DELETE FROM monthly_spend WHERE month_start < ?", Long.MIN_VALUE, """
                CREATE TABLE monthly_spend (
                    month_start INTEGER NOT NULL,
                    scope TEXT NOT NULL,
                    reserved_micros INTEGER NOT NULL CHECK(reserved_micros >= 0),
                    PRIMARY KEY (month_start, scope)
                ) STRICT
                """);
    }

    Decision reserve(String authenticatedPrincipal, Provider provider) throws IOException {
        if (authenticatedPrincipal == null || authenticatedPrincipal.isBlank()) {
            throw new IllegalArgumentException("Authenticated provider spending identity is required");
        }
        Long charge = limits.ceiling(provider);
        if (charge == null) return Decision.UNCONFIGURED;
        if (limits.runnerMonthly() == 0) return Decision.RUNNER_LIMIT;
        if (limits.globalMonthly() == 0) return Decision.GLOBAL_LIMIT;
        String runnerScope = ReplayDatabase.scopedKey("provider-spend-v1\0", authenticatedPrincipal, "");
        try (Connection db = database.open()) {
            db.setAutoCommit(false); // The shared SQLite helper uses BEGIN IMMEDIATE across server processes.
            long month = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).withDayOfMonth(1).toEpochDay();
            try (PreparedStatement future = db.prepareStatement(
                    "SELECT 1 FROM monthly_spend WHERE month_start > ? LIMIT 1")) {
                future.setLong(1, month);
                try (ResultSet row = future.executeQuery()) {
                    if (row.next()) throw new IOException("Provider spending clock precedes its recorded month");
                }
            }
            if (!debit(db, month, runnerScope, charge, limits.runnerMonthly())) {
                db.rollback();
                return Decision.RUNNER_LIMIT;
            }
            if (!debit(db, month, GLOBAL_SCOPE, charge, limits.globalMonthly())) {
                db.rollback(); // Both counters move together, including when only the global limit is full.
                return Decision.GLOBAL_LIMIT;
            }
            database.purgeExpired(db, month);
            db.commit();
            return Decision.ALLOWED;
        } catch (SQLException failure) {
            throw new IOException("Cannot reserve provider spending", failure);
        }
    }

    private static boolean debit(Connection db, long month, String scope, long charge, long limit)
            throws SQLException {
        if (charge > limit) return false;
        // Subtraction before addition avoids overflow even at the largest accepted USD amount.
        try (PreparedStatement update = db.prepareStatement("""
                INSERT INTO monthly_spend (month_start, scope, reserved_micros) VALUES (?, ?, ?)
                ON CONFLICT(month_start, scope) DO UPDATE SET
                    reserved_micros = reserved_micros + excluded.reserved_micros
                WHERE reserved_micros < ? AND reserved_micros <= ?
                """)) {
            update.setLong(1, month);
            update.setString(2, scope);
            update.setLong(3, charge);
            update.setLong(4, limit);
            update.setLong(5, limit - charge);
            return update.executeUpdate() == 1;
        }
    }
}
