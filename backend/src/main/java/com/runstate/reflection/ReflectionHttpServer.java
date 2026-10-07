package com.runstate.reflection;

import com.runstate.reflection.ReflectionApiJson.ApiRequest;
import com.runstate.reflection.ReflectionApiJson.InvalidJsonException;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Loopback-only HTTP boundary around one {@link ReflectionProvider}. */
public final class ReflectionHttpServer implements AutoCloseable {

    public static final String CANDIDATES_PATH = "/api/reflections/candidates";
    public static final int MAX_REQUEST_BYTES = 64 * 1024;

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final int SERVER_THREADS = 8;
    private static final Set<String> ALLOWED_HOST_NAMES = Set.of(
            "127.0.0.1",
            "localhost",
            "10.0.2.2"
    );

    private final ReflectionProvider provider;
    private final HttpServer server;
    private final ExecutorService executor;
    private final ConcurrentMap<String, RequestWork> workByRequestId = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ReflectionHttpServer(ReflectionProvider provider, int port) throws IOException {
        this.provider = Objects.requireNonNull(provider, "provider");
        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        this.server = HttpServer.create(new InetSocketAddress(loopback, port), 0);
        this.executor = Executors.newFixedThreadPool(
                SERVER_THREADS,
                new ServerThreadFactory()
        );
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    /** Creates a server on 127.0.0.1. Port zero asks the operating system for a free test port. */
    public static ReflectionHttpServer create(ReflectionProvider provider, int port)
            throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        return new ReflectionHttpServer(provider, port);
    }

    public void start() {
        if (closed.get()) {
            throw new IllegalStateException("server is closed");
        }
        server.start();
    }

    public InetSocketAddress address() {
        return server.getAddress();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
            executor.shutdownNow();
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        EndpointResponse response;
        try {
            response = route(exchange);
        } catch (RequestTooLargeException tooLarge) {
            response = error(
                    413,
                    "request_too_large",
                    "The JSON request exceeds the allowed size."
            );
        } catch (InvalidJsonException invalidJson) {
            response = error(
                    400,
                    "invalid_json",
                    "The request body must contain valid JSON with unique keys."
            );
        } catch (IllegalArgumentException invalidRequest) {
            response = error(
                    400,
                    "invalid_request",
                    "The request does not match the reflection contract."
            );
        } catch (RuntimeException unexpected) {
            response = error(
                    500,
                    "internal_error",
                    "The reflection request could not be completed."
            );
        }

        writeResponse(exchange, response);
    }

    private EndpointResponse route(HttpExchange exchange)
            throws IOException, InvalidJsonException, RequestTooLargeException {
        if (!hasAllowedHost(exchange) || hasOrigin(exchange)) {
            return error(
                    403,
                    "request_rejected",
                    "The request is not allowed by this local server."
            );
        }
        if (!CANDIDATES_PATH.equals(exchange.getRequestURI().getPath())) {
            return error(404, "not_found", "The requested endpoint does not exist.");
        }
        if (exchange.getRequestURI().getRawQuery() != null) {
            return error(400, "invalid_request", "This endpoint does not accept query parameters.");
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            return error(405, "method_not_allowed", "Only POST is supported for this endpoint.");
        }
        if (!isJsonContentType(exchange.getRequestHeaders().getFirst("Content-Type"))) {
            return error(
                    415,
                    "unsupported_media_type",
                    "Content-Type must be application/json."
            );
        }
        if (!isIdentityEncoding(exchange.getRequestHeaders().getFirst("Content-Encoding"))) {
            return error(
                    415,
                    "unsupported_media_type",
                    "Compressed request bodies are not supported."
            );
        }

        byte[] body = readBoundedBody(exchange);
        ApiRequest request = ReflectionApiJson.parseRequest(body);
        return generateOnce(request);
    }

    private boolean hasAllowedHost(HttpExchange exchange) {
        List<String> hostValues = exchange.getRequestHeaders().get("Host");
        if (hostValues == null || hostValues.size() != 1) {
            return false;
        }

        String value = hostValues.get(0);
        int separator = value.lastIndexOf(':');
        if (separator <= 0 || separator != value.indexOf(':')) {
            return false;
        }

        String hostName = value.substring(0, separator).toLowerCase(Locale.ROOT);
        String port = value.substring(separator + 1);
        return ALLOWED_HOST_NAMES.contains(hostName)
                && Integer.toString(server.getAddress().getPort()).equals(port);
    }

    private static boolean hasOrigin(HttpExchange exchange) {
        return exchange.getRequestHeaders().get("Origin") != null;
    }

    private EndpointResponse generateOnce(ApiRequest request) {
        String requestId = request.identifiers().requestId();

        while (true) {
            RequestWork existing = workByRequestId.get(requestId);
            if (existing != null) {
                if (!existing.request().equals(request)) {
                    return error(
                            409,
                            "request_id_conflict",
                            "The requestId is already associated with different evidence."
                    );
                }
                return existing.response().join();
            }

            RequestWork created = new RequestWork(request, new CompletableFuture<>());
            if (workByRequestId.putIfAbsent(requestId, created) == null) {
                EndpointResponse response = callProvider(request);
                created.response().complete(response);
                return response;
            }
        }
    }

    private EndpointResponse callProvider(ApiRequest request) {
        try {
            CandidateReflections candidates = provider.generate(request.evidence());
            return new EndpointResponse(200, ReflectionApiJson.success(request, candidates));
        } catch (ReflectionProviderException failure) {
            return providerFailure(failure.failureType());
        } catch (RuntimeException unexpected) {
            return error(
                    500,
                    "internal_error",
                    "The reflection request could not be completed."
            );
        }
    }

    private static EndpointResponse providerFailure(FailureType failureType) {
        return switch (failureType) {
            case TIMEOUT -> error(
                    504,
                    "provider_timeout",
                    "The reflection provider timed out."
            );
            case CONFIGURATION, TRANSPORT -> error(
                    503,
                    "provider_unavailable",
                    "The reflection provider is unavailable."
            );
            case PROVIDER_ERROR, REFUSAL, TRUNCATED, INVALID_RESPONSE -> error(
                    502,
                    "provider_failure",
                    "The reflection provider could not produce valid candidates."
            );
        };
    }

    private static byte[] readBoundedBody(HttpExchange exchange)
            throws IOException, RequestTooLargeException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                long declaredLength = Long.parseLong(contentLength);
                if (declaredLength < 0) {
                    throw new IllegalArgumentException("Content-Length cannot be negative");
                }
                if (declaredLength > MAX_REQUEST_BYTES) {
                    throw new RequestTooLargeException();
                }
            } catch (NumberFormatException invalidLength) {
                throw new IllegalArgumentException("Content-Length must be an integer", invalidLength);
            }
        }

        try (InputStream input = exchange.getRequestBody();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4_096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_REQUEST_BYTES) {
                    throw new RequestTooLargeException();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static boolean isJsonContentType(String candidate) {
        if (candidate == null) {
            return false;
        }
        String mediaType = candidate.split(";", 2)[0].trim();
        return "application/json".equalsIgnoreCase(mediaType);
    }

    private static boolean isIdentityEncoding(String candidate) {
        return candidate == null
                || candidate.isBlank()
                || "identity".equals(candidate.trim().toLowerCase(Locale.ROOT));
    }

    private static EndpointResponse error(int status, String code, String message) {
        return new EndpointResponse(status, ReflectionApiJson.error(code, message));
    }

    private static void writeResponse(HttpExchange exchange, EndpointResponse response)
            throws IOException {
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        try {
            exchange.sendResponseHeaders(response.status(), body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private record RequestWork(
            ApiRequest request,
            CompletableFuture<EndpointResponse> response
    ) {
    }

    private record EndpointResponse(int status, String body) {
    }

    private static final class RequestTooLargeException extends Exception {
    }

    private static final class ServerThreadFactory implements ThreadFactory {

        private final AtomicInteger threadNumber = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(
                    runnable,
                    "runstate-reflection-http-" + threadNumber.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        }
    }
}
