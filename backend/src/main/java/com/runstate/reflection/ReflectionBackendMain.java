package com.runstate.reflection;

import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Starts local loopback HTTP, or deliberately authenticated internal HTTP for HTTPS ingress. */
public final class ReflectionBackendMain {

    private static final int DEFAULT_PORT = 8_787;
    private static final String PORT_ENVIRONMENT_VARIABLE = "RUNSTATE_REFLECTION_PORT";

    private ReflectionBackendMain() {
    }

    public static void main(String[] args) throws Exception {
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
        try (ReflectionHttpServer server = options.remote()
                ? ReflectionHttpServer.createRemote(provider, port, generationMode, remoteAccess)
                : ReflectionHttpServer.create(provider, port, generationMode)) {
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

    static int configuredPort(Map<String, String> environment) {
        return configuredPort(environment, false);
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
                    "Unsupported arguments. Use no arguments, --fake, --remote, or --remote --fake. " +
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
