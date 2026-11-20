package com.runstate.reflection;

import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Starts local loopback HTTP, or deliberately authenticated internal HTTP for HTTPS ingress. */
public final class ReflectionBackendMain {

    private static final int DEFAULT_PORT = 8_787;
    private static final String PORT_ENVIRONMENT_VARIABLE = "RUNSTATE_REFLECTION_PORT";
    private static final String SPEECH_REPLAY_DIRECTORY = "RUNSTATE_SPEECH_REPLAY_DIR";

    private ReflectionBackendMain() {
    }

    public static void main(String[] args) throws Exception {
        // This administrative check must exit before constructing providers or opening HTTP.
        if (args.length == 1 && "--check-config".equals(args[0])) {
            int status = checkConfiguration(System.getenv(),
                    System.getProperty("jdk.httpclient.redirects.retrylimit"), System.out);
            if (status != 0) System.exit(status);
            return;
        }
        LaunchOptions options = parseOptions(args);
        Map<String, String> environment = System.getenv();
        int port = configuredPort(environment, options.remote());
        ReflectionHttpServer.RemoteAccess remoteAccess = options.remote()
                ? configuredRemoteAccess(environment) : null;
        ReflectionProvider provider = options.fake()
                ? localFakeProvider()
                : productionProvider();

        ReflectionHttpServer.GenerationMode generationMode = options.fake()
                ? ReflectionHttpServer.GenerationMode.FAKE
                : ReflectionHttpServer.GenerationMode.ANTHROPIC;
        ReflectionHttpServer.SpeechProvider speechProvider = options.fake() ? null
                : ElevenLabsReflectionSpeech.fromEnvironment(environment).orElse(null);
        ReflectionHttpServer.SpeechProvider runVoiceProvider = options.fake() ? null
                : ElevenLabsReflectionSpeech.runVoiceFromEnvironment(environment).orElse(null);
        try (ReflectionHttpServer server = options.remote()
                ? ReflectionHttpServer.createRemote(provider, port, generationMode, remoteAccess,
                        speechProvider, runVoiceProvider)
                : ReflectionHttpServer.create(provider, port, generationMode, speechProvider, runVoiceProvider)) {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(
                    server::close,
                    "runstate-reflection-shutdown"
            ));

            String providerLabel = options.fake() ? "fake provider" : "Anthropic provider";
            System.out.println(
                    "RunState reflection backend (" + providerLabel + ") listening on "
                            + (options.remote()
                            ? "internal port " + server.address().getPort() + " behind required HTTPS ingress"
                            : "http://127.0.0.1:" + server.address().getPort() + ReflectionHttpServer.CANDIDATES_PATH)
            );
            new CountDownLatch(1).await();
        }
    }

    /** Checks remote configuration and actual SQLite access, without contacting a provider. */
    static int checkConfiguration(Map<String, String> environment, String httpAttemptLimit, PrintStream output) {
        boolean valid = true;
        try {
            configuredPort(environment, true);
            configuredRemoteAccess(environment);
            output.println("PASS remote host, access and port configuration.");
        } catch (IllegalArgumentException failure) {
            // Never print the exception: configuration parsers can contain private input.
            output.println("FAIL remote host, access or port configuration; check the backend settings.");
            valid = false;
        }
        if (!"1".equals(httpAttemptLimit)) {
            output.println("FAIL start Java with -Djdk.httpclient.redirects.retrylimit=1.");
            valid = false;
        } else {
            output.println("PASS one outbound HTTP attempt is configured.");
        }

        ProviderSpendBudget.Limits limits = null;
        try {
            var configured = ProviderSpendBudget.Limits.fromEnvironment(environment);
            boolean disabled = configured.runnerMonthly() == 0 || configured.globalMonthly() == 0;
            if (!disabled && (configured.anthropicMaxPerCall() == null
                    || (hasSetting(environment, "ELEVENLABS_API_KEY") && configured.elevenLabsMaxPerCall() == null)
                    || (hasSetting(environment, "MUSIXMATCH_API_KEY") && configured.musixmatchMaxPerCall() == null))) {
                throw new IllegalArgumentException("Missing enabled provider ceiling");
            }
            limits = configured;
            output.println(disabled
                    ? "PASS spending configuration; new paid calls disabled by a zero monthly cap."
                    : "PASS spending configuration; verify provider ceilings against actual plans before activation.");
        } catch (IllegalArgumentException failure) {
            output.println("FAIL monthly amounts or enabled provider ceilings; check the backend spending settings.");
            valid = false;
        }

        Path directory = null;
        try {
            directory = replayDirectory(environment, true);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            output.println("FAIL set RUNSTATE_SPEECH_REPLAY_DIR to an absolute private persistent directory.");
            valid = false;
        }
        // A malformed configuration should not create or modify any store.
        if (valid) {
            try {
                checkPrivateStorage(directory);
                // The normal constructors commit schema/expiry work and reopen SQLite connections.
                // They never reserve provider spending, generate a reply or synthesize speech.
                for (int attempt = 0; attempt < 2; attempt++) {
                    new WrittenReflectionReplayStore(directory);
                    new SpeechReplayStore(directory);
                    new ReflectionAllowance(directory, Clock.systemUTC());
                    new ProviderSpendBudget(directory, limits);
                }
                checkPrivateStorage(directory);
                output.println("PASS all four stores initialized and reopened under the current runtime user.");
                if (!directory.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                    output.println("NOTE Linux file permissions still require checking on the host.");
                }
            } catch (IOException | IllegalArgumentException | SecurityException failure) {
                output.println("FAIL storage access, private permissions or database integrity; check the mounted directory and runtime user.");
                valid = false;
            }
        }
        output.println("NOTE no server was started and no provider calls were made.");
        output.println("NOTE this does not verify a persistent mount, hosted restart, provider credentials or billing rates.");
        output.println(valid ? "Configuration check passed." : "Configuration check failed.");
        return valid ? 0 : 1;
    }

    private static boolean hasSetting(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value != null && !value.isBlank();
    }

    private static void checkPrivateStorage(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid storage directory");
            checkPrivatePermissions(directory);
        }
        for (String filename : new String[]{"written-reflections.sqlite3", "reflection-speech.sqlite3",
                "reflection-allowances.sqlite3", "provider-spend.sqlite3"}) {
            for (String suffix : new String[]{"", "-journal", "-wal", "-shm"}) {
                Path file = directory.resolve(filename + suffix);
                if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Invalid storage file");
                    checkPrivatePermissions(file);
                }
            }
        }
    }

    private static void checkPrivatePermissions(Path path) throws IOException {
        if (path.getFileSystem().supportedFileAttributeViews().contains("posix")
                && Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).stream()
                .anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_"))) {
            throw new IOException("Storage is not private");
        }
    }

    static int configuredPort(Map<String, String> environment) {
        return configuredPort(environment, false);
    }

    static SpeechReplayStore configuredSpeechReplayStore(ReflectionHttpServer.SpeechProvider voice,
            Map<String, String> environment, boolean remote) {
        if (voice == null) return null;
        try {
            return new SpeechReplayStore(replayDirectory(environment, remote));
        } catch (IOException | InvalidPathException | SecurityException failure) {
            System.err.println("Reflection voice disabled: check private persistent storage in " + SPEECH_REPLAY_DIRECTORY);
            return null;
        }
    }

    static WrittenReflectionReplayStore configuredWrittenReplayStore(ReflectionHttpServer.GenerationMode mode,
            Map<String, String> environment, boolean remote) {
        if (mode == ReflectionHttpServer.GenerationMode.FAKE) return null;
        try {
            return new WrittenReflectionReplayStore(replayDirectory(environment, remote));
        } catch (IOException | InvalidPathException | SecurityException failure) {
            System.err.println("Written reflections disabled: check private persistent storage in " + SPEECH_REPLAY_DIRECTORY);
            return null;
        }
    }

    static ProviderSpendBudget configuredSpendBudget(Map<String, String> environment, boolean remote,
            String httpAttemptLimit) {
        try {
            var limits = ProviderSpendBudget.Limits.fromEnvironment(environment);
            if (!"1".equals(httpAttemptLimit)) {
                // OpenJDK can resend a GET after the provider received it and the reply was lost.
                System.err.println("Paid provider calls disabled: start Java with -Djdk.httpclient.redirects.retrylimit=1.");
                return null;
            }
            return new ProviderSpendBudget(replayDirectory(environment, remote), limits);
        } catch (IOException | IllegalArgumentException | SecurityException failure) {
            System.err.println("Paid provider calls disabled: check monthly limits and private persistent storage.");
            return null;
        }
    }

    static ReflectionAllowance configuredAllowance(Map<String, String> env) {
        try {
            return new ReflectionAllowance(replayDirectory(env, true), java.time.Clock.systemUTC());
        } catch (IOException | IllegalArgumentException | SecurityException unavailable) {
            System.err.println("Scoped reflection access requires private persistent storage.");
            return null;
        }
    }

    private static Path replayDirectory(Map<String, String> environment, boolean remote) throws IOException {
        String configured = environment.get(SPEECH_REPLAY_DIRECTORY);
        if (configured == null && !remote) {
            return Path.of(System.getProperty("user.home"), ".runstate", "speech-replay");
        }
        if (configured == null || configured.isBlank()) throw new IOException("Missing persistent replay directory");
        Path directory = Path.of(configured);
        if (!directory.isAbsolute()) throw new IOException("Replay storage requires an absolute directory");
        return directory;
    }

    static int configuredPort(Map<String, String> environment, boolean remote) {
        String configured = environment.get(PORT_ENVIRONMENT_VARIABLE);
        if ((configured == null || configured.isBlank()) && remote) {
            configured = environment.get("PORT");
        }
        if (configured == null || configured.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(configured);
            if (port < 1 || port > 65_535) {
                throw new IllegalArgumentException(
                        PORT_ENVIRONMENT_VARIABLE + " must be between 1 and 65535"
                );
            }
            return port;
        } catch (NumberFormatException invalidPort) {
            throw new IllegalArgumentException(
                    PORT_ENVIRONMENT_VARIABLE + " (or remote PORT) must be an integer between 1 and 65535"
            );
        }
    }

    static ReflectionHttpServer.RemoteAccess configuredRemoteAccess(Map<String, String> environment) {
        if (environment.containsKey("RUNSTATE_ACCESS_GRANTS_JSON")) {
            if (environment.containsKey("RUNSTATE_DEMO_TOKEN")) {
                throw new IllegalArgumentException(
                        "Configure individual access or owner demo access, not both."
                );
            }
            return ReflectionHttpServer.RemoteAccess.individual(
                    environment.getOrDefault("RUNSTATE_REFLECTION_BIND_ADDRESS", "0.0.0.0"),
                    environment.get("RUNSTATE_REFLECTION_PUBLIC_HOST"),
                    BackendAccessPolicy.fromJson(environment.get("RUNSTATE_ACCESS_GRANTS_JSON"))
            );
        }
        return new ReflectionHttpServer.RemoteAccess(
                environment.getOrDefault("RUNSTATE_REFLECTION_BIND_ADDRESS", "0.0.0.0"),
                environment.get("RUNSTATE_REFLECTION_PUBLIC_HOST"),
                environment.get("RUNSTATE_DEMO_TOKEN")
        );
    }

    static LaunchOptions parseOptions(String[] args) {
        boolean fake = false;
        boolean remote = false;
        for (String argument : args) {
            if ("--fake".equals(argument) && !fake) fake = true;
            else if ("--remote".equals(argument) && !remote) remote = true;
            else throw new IllegalArgumentException(
                    "Unsupported arguments. Use no arguments, --fake, --remote, --remote --fake, or --check-config alone. " +
                            "Keep credentials in backend environment variables."
            );
        }
        return new LaunchOptions(fake, remote);
    }

    record LaunchOptions(boolean fake, boolean remote) { }

    private static ReflectionProvider productionProvider()
            throws ReflectionProviderException {
        return new AnthropicReflectionProvider();
    }

    private static ReflectionProvider localFakeProvider() {
        CandidateReflections fixedCandidates = new CandidateReflections(
                "[FAKE] Spent candidate.",
                "[FAKE] Feeling Good candidate.",
                "[FAKE] Powered Up candidate.",
                "[FAKE] No-selection candidate."
        );
        return evidence -> fixedCandidates;
    }
}
