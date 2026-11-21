package com.runstate.reflection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class BackendConfigurationCheckTest {
    @TempDir Path directory;
    private static final String TOKEN = "private-test-token-never-print-0123456789-ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final List<String> STORES = List.of("written-reflections.sqlite3",
            "reflection-speech.sqlite3", "reflection-allowances.sqlite3", "provider-spend.sqlite3");

    private Map<String, String> settings(Path storage) {
        return new HashMap<>(Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", "runstate.example.com",
                "RUNSTATE_DEMO_TOKEN", TOKEN,
                "RUNSTATE_SPEECH_REPLAY_DIR", storage.toAbsolutePath().toString(),
                "RUNSTATE_RUNNER_MONTHLY_USD", "0",
                "RUNSTATE_GLOBAL_MONTHLY_USD", "0"));
    }

    private Check check(Map<String, String> environment, String attemptLimit) {
        var bytes = new ByteArrayOutputStream();
        int status = ReflectionBackendMain.checkConfiguration(environment, attemptLimit,
                new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return new Check(status, bytes.toString(StandardCharsets.UTF_8));
    }

    private record Check(int status, String output) { }

    @Test void zeroBudgetCreatesAllStoresAndReopensWithoutErasingSavedWorkOrReservingMore() throws Exception {
        Path storage = directory.resolve("private-store");
        var environment = settings(storage);
        Check initial = check(environment, "1");
        assertEquals(0, initial.status(), initial.output());
        assertTrue(initial.output().contains("new paid calls disabled"));
        for (String name : STORES) assertTrue(Files.isRegularFile(storage.resolve(name)), name);

        byte[] audio = {1, 2, 3};
        var speech = new SpeechReplayStore(storage);
        speech.complete(speech.claim("runner", "Saved reflection", true), audio);
        byte[] body = "saved answer bytes".getBytes(StandardCharsets.UTF_8);
        var written = new WrittenReflectionReplayStore(storage);
        written.complete(written.claim("runner", "attempt-one", "run-evidence", true), body, true);
        var limits = new ProviderSpendBudget.Limits(1, 1, 1, 1, 0);
        var budget = new ProviderSpendBudget(storage, limits);
        assertEquals(ProviderSpendBudget.Decision.ALLOWED,
                budget.reserve("runner", ProviderSpendBudget.Provider.ANTHROPIC));
        var trial = new BackendAccessPolicy.Principal("runner", BackendAccessPolicy.Tier.TRIAL);
        var allowance = new ReflectionAllowance(storage, Clock.systemUTC());
        allowance.activate(trial);
        assertEquals(1, allowance.admit(trial, UUID.randomUUID().toString()).remaining());

        assertEquals(0, check(environment, "1").status());
        assertEquals(0, check(environment, "1").status());
        assertArrayEquals(audio, new SpeechReplayStore(storage)
                .claim("runner", "Saved reflection", false).audio());
        var replay = new WrittenReflectionReplayStore(storage)
                .claim("runner", "attempt-two", "run-evidence", false);
        assertEquals(WrittenReflectionReplayStore.State.CACHED, replay.state());
        assertArrayEquals(body, replay.body());
        assertTrue(replay.songStudyAcknowledged());
        assertEquals(ProviderSpendBudget.Decision.RUNNER_LIMIT,
                new ProviderSpendBudget(storage, limits).reserve("runner", ProviderSpendBudget.Provider.ANTHROPIC));
        assertEquals(1, new ReflectionAllowance(storage, Clock.systemUTC()).status(trial).remaining());
    }

    @Test void missingOrMalformedAmountsFailBeforeCreatingStorage() {
        Path storage = directory.resolve("not-created");
        for (String name : List.of("RUNSTATE_RUNNER_MONTHLY_USD", "RUNSTATE_GLOBAL_MONTHLY_USD")) {
            var missing = settings(storage);
            missing.remove(name);
            assertEquals(1, check(missing, "1").status());
            for (String value : List.of("", "-1", "NaN", "1e3", "0.0000001")) {
                var invalid = settings(storage);
                invalid.put(name, value);
                assertEquals(1, check(invalid, "1").status(), name + ": " + value);
            }
        }
        assertFalse(Files.exists(storage));
    }

    @Test void eitherZeroMonthlyCapIntentionallyDisablesCallsWithoutProviderCeilings() {
        var environment = settings(directory.resolve("zero-caps"));
        environment.put("ELEVENLABS_API_KEY", "unused-voice-key");
        environment.put("MUSIXMATCH_API_KEY", "unused-song-key");
        environment.put("RUNSTATE_RUNNER_MONTHLY_USD", "1");
        Check globalZero = check(environment, "1");
        assertEquals(0, globalZero.status(), globalZero.output());
        assertTrue(globalZero.output().contains("new paid calls disabled"));
        environment.put("RUNSTATE_RUNNER_MONTHLY_USD", "0");
        environment.put("RUNSTATE_GLOBAL_MONTHLY_USD", "1");
        assertEquals(0, check(environment, "1").status());
    }

    @Test void positiveCapsRequireCeilingsForEachEnabledProvider() {
        var environment = settings(directory.resolve("enabled-providers"));
        environment.put("RUNSTATE_RUNNER_MONTHLY_USD", "1");
        environment.put("RUNSTATE_GLOBAL_MONTHLY_USD", "2");
        assertEquals(1, check(environment, "1").status());
        environment.put("RUNSTATE_ANTHROPIC_MAX_CALL_USD", ".01");
        assertEquals(0, check(environment, "1").status());
        environment.put("ELEVENLABS_API_KEY", "unused-voice-key");
        assertEquals(1, check(environment, "1").status());
        environment.put("RUNSTATE_ELEVENLABS_MAX_CALL_USD", ".01");
        environment.put("MUSIXMATCH_API_KEY", "unused-song-key");
        assertEquals(1, check(environment, "1").status());
        environment.put("RUNSTATE_MUSIXMATCH_MAX_CALL_USD", "0");
        assertEquals(0, check(environment, "1").status());
    }

    @Test void remoteAccessPortAndJvmAttemptSettingsAreValidatedWithoutTouchingStorage() {
        Path storage = directory.resolve("invalid-configuration");
        for (String limit : new String[]{null, "", "0", "2"}) {
            assertEquals(1, check(settings(storage), limit).status());
        }
        Map<String, String> badValues = Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", "https://runstate.example.com/private",
                "RUNSTATE_DEMO_TOKEN", "weak",
                "RUNSTATE_REFLECTION_BIND_ADDRESS", "localhost",
                "RUNSTATE_REFLECTION_PORT", "65536",
                "PORT", "not-a-port");
        for (var invalid : badValues.entrySet()) {
            var environment = settings(storage);
            environment.put(invalid.getKey(), invalid.getValue());
            assertEquals(1, check(environment, "1").status(), invalid.getKey());
        }
        var conflicting = settings(storage);
        conflicting.put("RUNSTATE_ACCESS_GRANTS_JSON", "[]");
        assertEquals(1, check(conflicting, "1").status());
        assertFalse(Files.exists(storage));
    }

    @Test void missingRelativeOrFileStorageIsRejected() throws Exception {
        var missing = settings(directory.resolve("missing-setting"));
        missing.remove("RUNSTATE_SPEECH_REPLAY_DIR");
        assertEquals(1, check(missing, "1").status());
        for (String invalid : List.of("", "relative-storage")) {
            var environment = settings(directory.resolve("unused"));
            environment.put("RUNSTATE_SPEECH_REPLAY_DIR", invalid);
            assertEquals(1, check(environment, "1").status());
        }
        Path file = directory.resolve("ordinary-file");
        Files.writeString(file, "preserve me");
        assertEquals(1, check(settings(file), "1").status());
        assertEquals("preserve me", Files.readString(file));
    }

    @Test void corruptExistingStoreFailsWithoutReplacingIt() throws Exception {
        for (String name : STORES) {
            Path storage = directory.resolve("corrupt-" + name);
            Files.createDirectory(storage);
            privatePermissions(storage, "rwx------");
            Path corrupted = storage.resolve(name);
            Files.writeString(corrupted, "private corrupt database marker");
            privatePermissions(corrupted, "rw-------");
            Check result = check(settings(storage), "1");
            assertEquals(1, result.status(), name);
            assertEquals("private corrupt database marker", Files.readString(corrupted));
            assertFalse(result.output().contains("private corrupt database marker"));
        }
    }

    @Test void failureOutputDoesNotExposeSecretsPathsOrParserDetails() {
        Path storage = directory.resolve("private-directory-do-not-print");
        var environment = settings(storage);
        environment.put("ANTHROPIC_API_KEY", "private-anthropic-secret");
        environment.put("ELEVENLABS_API_KEY", "private-voice-secret");
        environment.put("MUSIXMATCH_API_KEY", "private-song-secret");
        environment.put("RUNSTATE_GLOBAL_MONTHLY_USD", "private-invalid-amount");
        environment.put("RUNSTATE_REFLECTION_PUBLIC_HOST", "https://private-host.example.com/path");
        Check result = check(environment, "1");
        assertEquals(1, result.status());
        for (String secret : List.of(TOKEN, storage.toString(), "private-anthropic-secret", "private-voice-secret",
                "private-song-secret", "private-invalid-amount", "private-host.example.com")) {
            assertFalse(result.output().contains(secret));
        }
        assertFalse(result.output().contains("Exception"));
        assertFalse(Files.exists(storage));
    }

    @Test void posixPublicDirectoryOrDatabaseIsRejectedWithoutRepairingPermissions() throws Exception {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path storage = directory.resolve("not-private");
        Files.createDirectory(storage);
        privatePermissions(storage, "rwxr-xr-x");
        assertEquals(1, check(settings(storage), "1").status());
        assertEquals(PosixFilePermissions.fromString("rwxr-xr-x"), Files.getPosixFilePermissions(storage));
        privatePermissions(storage, "rwx------");
        assertEquals(0, check(settings(storage), "1").status());
        Path speech = storage.resolve("reflection-speech.sqlite3");
        privatePermissions(speech, "rw-r--r--");
        assertEquals(1, check(settings(storage), "1").status());
        assertEquals(PosixFilePermissions.fromString("rw-r--r--"), Files.getPosixFilePermissions(speech));
    }

    @Test void posixDirectoryAndStoreSymlinksAreRejectedWithoutTouchingTargets() throws Exception {
        assumeTrue(directory.getFileSystem().supportedFileAttributeViews().contains("posix"));
        Path target = directory.resolve("target");
        Files.createDirectory(target);
        privatePermissions(target, "rwx------");
        Path link = directory.resolve("directory-link");
        Files.createSymbolicLink(link, target);
        assertEquals(1, check(settings(link), "1").status());
        assertFalse(Files.exists(target.resolve("written-reflections.sqlite3")));
        Path marker = directory.resolve("marker");
        Files.writeString(marker, "unchanged target");
        Files.createSymbolicLink(target.resolve("provider-spend.sqlite3"), marker);
        assertEquals(1, check(settings(target), "1").status());
        assertEquals("unchanged target", Files.readString(marker));
    }

    @Test void cliExitsWithoutProviderCredentialsOrBindingItsConfiguredPort() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            var environment = settings(directory.resolve("child-check"));
            environment.put("RUNSTATE_REFLECTION_BIND_ADDRESS", "127.0.0.1");
            environment.put("RUNSTATE_REFLECTION_PORT", Integer.toString(occupied.getLocalPort()));
            Check success = child(environment, "--check-config");
            assertEquals(0, success.status(), success.output());
            assertTrue(success.output().contains("Configuration check passed."));
            assertFalse(success.output().contains(TOKEN));
            environment.remove("RUNSTATE_RUNNER_MONTHLY_USD");
            Check failure = child(environment, "--check-config");
            assertEquals(1, failure.status(), failure.output());
            assertTrue(failure.output().contains("Configuration check failed."));
        }
    }

    @Test void cliCheckCannotBeCombinedWithServerLaunchArguments() throws Exception {
        var environment = settings(directory.resolve("no-server"));
        Check result = child(environment, "--check-config", "--remote");
        assertNotEquals(0, result.status());
        assertFalse(result.output().contains(TOKEN));
        assertFalse(Files.exists(directory.resolve("no-server")));
    }

    private static void privatePermissions(Path path, String permissions) throws Exception {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }

    private Check child(Map<String, String> environment, String... arguments) throws Exception {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", executable).toString(),
                "-Djdk.httpclient.redirects.retrylimit=1", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ReflectionBackendMain.class.getName()));
        command.addAll(List.of(arguments));
        Path output = directory.resolve("child-" + UUID.randomUUID() + ".log");
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("RUNSTATE_") || key.startsWith("ANTHROPIC_")
                || key.startsWith("ELEVENLABS_") || key.startsWith("MUSIXMATCH_") || key.equals("PORT")
                || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS") || key.equals("_JAVA_OPTIONS"));
        builder.environment().putAll(environment);
        Process process = builder.start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Configuration check must exit without serving HTTP");
            return new Check(process.exitValue(), Files.readString(output));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }
}
