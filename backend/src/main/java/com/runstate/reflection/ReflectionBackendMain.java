package com.runstate.reflection;

import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/** Starts the local reflection HTTP service. */
public final class ReflectionBackendMain {

    private static final int DEFAULT_PORT = 8_787;
    private static final String PORT_ENVIRONMENT_VARIABLE = "RUNSTATE_REFLECTION_PORT";

    private ReflectionBackendMain() {
    }

    public static void main(String[] args) throws Exception {
        boolean fakeMode = parseFakeMode(args);
        int port = configuredPort(System.getenv());
        ReflectionProvider provider = fakeMode
                ? localFakeProvider()
                : productionProvider();

        try (ReflectionHttpServer server = ReflectionHttpServer.create(provider, port)) {
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(
                    server::close,
                    "runstate-reflection-shutdown"
            ));

            String providerLabel = fakeMode ? "fake provider" : "Anthropic provider";
            System.out.println(
                    "RunState reflection backend (" + providerLabel + ") listening on "
                            + "http://127.0.0.1:" + server.address().getPort()
                            + ReflectionHttpServer.CANDIDATES_PATH
            );
            new CountDownLatch(1).await();
        }
    }

    static int configuredPort(Map<String, String> environment) {
        String configured = environment.get(PORT_ENVIRONMENT_VARIABLE);
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
                    PORT_ENVIRONMENT_VARIABLE + " must be an integer between 1 and 65535",
                    invalidPort
            );
        }
    }

    private static boolean parseFakeMode(String[] args) {
        if (args.length == 0) {
            return false;
        }
        if (args.length == 1 && "--fake".equals(args[0])) {
            return true;
        }
        throw new IllegalArgumentException(
                "Unsupported arguments: " + Arrays.toString(args)
                        + ". Use no arguments or --fake."
        );
    }

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
