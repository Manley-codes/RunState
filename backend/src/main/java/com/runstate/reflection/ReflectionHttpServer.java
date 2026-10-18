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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Local HTTP boundary, or an explicitly authenticated internal service behind HTTPS ingress. */
public final class ReflectionHttpServer implements AutoCloseable {

    public static final String CANDIDATES_PATH = "/api/reflections/candidates";
    public static final String SPEECH_PATH = "/api/reflections/speech";
    public static final String SONG_PREPARATION_PATH = "/api/reflections/song-study";
    public static final String HEALTH_PATH = "/health";
    public static final String SONG_CUE_CAPABILITY_HEADER = "X-RunState-Song-Cue";
    public static final String SONG_STUDY_CAPABILITY_HEADER = "X-RunState-Song-Study";
    public static final String SONG_PREPARATION_CAPABILITY_HEADER = "X-RunState-Song-Preparation";
    public static final String SONG_SELECTION_CAPABILITY_HEADER = "X-RunState-Song-Selection";
    public static final String SONG_SNIPPETS_CAPABILITY_HEADER = "X-RunState-Song-Snippets";
    public static final String REFLECTION_VOICE_CAPABILITY_HEADER = "X-RunState-Reflection-Voice";
    public static final int MAX_REQUEST_BYTES = 64 * 1024;
    public static final int MAX_RESPONSE_BYTES = 64 * 1024;
    public static final int MAX_AUDIO_RESPONSE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_CONCURRENT_PROVIDER_CALLS = 2;
    public static final int MAX_CACHED_REQUESTS = 256;

    /** Provider mode configured by the launcher, never inferred from generated text. */
    public enum GenerationMode {
        FAKE,
        ANTHROPIC
    }

    /** Injected voice generation receives only the selected reflection's exact text. */
    @FunctionalInterface
    public interface SpeechProvider {
        byte[] synthesize(String text) throws ReflectionProviderException;
    }

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final int SERVER_THREADS = 8;
    private static final int HTTP_QUEUE_CAPACITY = 32;
    private static final Set<String> ALLOWED_HOST_NAMES = Set.of(
            "127.0.0.1",
            "localhost",
            "10.0.2.2"
    );

    private final ReflectionProvider provider;
    private final SpeechProvider speechProvider;
    private final GenerationMode generationMode;
    private final HttpServer server;
    private final ExecutorService executor;
    private final RemoteAccess remoteAccess;
    private final Semaphore providerSlots;
    private final Semaphore songPreparationSlots;
    private final int maxCachedRequests;
    private final Object cacheLock = new Object();
    private final Map<String, RequestWork> workByRequestId = new HashMap<>();
    private final Map<String, SongPreparationWork> songPreparationById = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ReflectionHttpServer(
            ReflectionProvider provider,
            int port,
            GenerationMode generationMode,
            RemoteAccess remoteAccess,
            int maxConcurrentProviderCalls,
            int maxCachedRequests,
            SpeechProvider speechProvider
    ) throws IOException {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.speechProvider = speechProvider;
        this.generationMode = Objects.requireNonNull(generationMode, "generationMode");
        requirePort(port);
        if (maxConcurrentProviderCalls < 1 || maxConcurrentProviderCalls >= SERVER_THREADS ||
                maxCachedRequests < maxConcurrentProviderCalls || maxCachedRequests > MAX_CACHED_REQUESTS) {
            throw new IllegalArgumentException("Invalid reflection server resource limits.");
        }
        this.remoteAccess = remoteAccess;
        this.providerSlots = new Semaphore(maxConcurrentProviderCalls);
        // Speculative work may use only one slot, leaving the other for a completed run/voice.
        this.songPreparationSlots = new Semaphore(maxConcurrentProviderCalls >= 2 ? 1 : 0);
        this.maxCachedRequests = maxCachedRequests;
        InetAddress bindAddress = remoteAccess == null
                ? InetAddress.getByName("127.0.0.1") : remoteAccess.bindAddress;
        this.server = HttpServer.create(new InetSocketAddress(bindAddress, port), HTTP_QUEUE_CAPACITY);
        this.executor = new ThreadPoolExecutor(
                SERVER_THREADS, SERVER_THREADS, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(HTTP_QUEUE_CAPACITY),
                new ServerThreadFactory(), new ThreadPoolExecutor.AbortPolicy()
        );
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    /** Creates a FAKE-mode server for local tests and injected fake providers. */
    public static ReflectionHttpServer create(ReflectionProvider provider, int port)
            throws IOException {
        return create(provider, port, GenerationMode.FAKE);
    }

    /** Creates a server on 127.0.0.1. Port zero asks the operating system for a free test port. */
    public static ReflectionHttpServer create(
            ReflectionProvider provider,
            int port,
            GenerationMode generationMode
    ) throws IOException {
        return create(provider, port, generationMode, null);
    }

    /** Voice is optional; legacy callers keep their existing reflection-only behavior. */
    public static ReflectionHttpServer create(ReflectionProvider provider, int port,
            GenerationMode generationMode, SpeechProvider speechProvider) throws IOException {
        return new ReflectionHttpServer(provider, port, generationMode, null,
                MAX_CONCURRENT_PROVIDER_CALLS, MAX_CACHED_REQUESTS, speechProvider);
    }

    /**
     * Deliberate remote mode. This is internal HTTP: a private ingress must terminate HTTPS,
     * preserve the configured public Host, bound slow requests, and block direct public access.
     * Forwarded headers do not establish TLS or alter authentication/Host checks.
     */
    public static ReflectionHttpServer createRemote(
            ReflectionProvider provider,
            int port,
            GenerationMode generationMode,
            RemoteAccess remoteAccess
    ) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess, null);
    }

    public static ReflectionHttpServer createRemote(ReflectionProvider provider, int port,
            GenerationMode generationMode, RemoteAccess remoteAccess, SpeechProvider speechProvider)
            throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess,
                MAX_CONCURRENT_PROVIDER_CALLS, MAX_CACHED_REQUESTS, speechProvider);
    }

    /** Smaller limits let loopback tests prove admission without filling the demo cache. */
    static ReflectionHttpServer createRemote(
            ReflectionProvider provider,
            int port,
            GenerationMode generationMode,
            RemoteAccess remoteAccess,
            int maxConcurrentProviderCalls,
            int maxCachedRequests
    ) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess,
                maxConcurrentProviderCalls, maxCachedRequests, null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port,
            GenerationMode generationMode, RemoteAccess remoteAccess, int maxConcurrentProviderCalls,
            int maxCachedRequests, SpeechProvider speechProvider) throws IOException {
        return new ReflectionHttpServer(provider, port, generationMode,
                Objects.requireNonNull(remoteAccess, "remoteAccess"),
                maxConcurrentProviderCalls, maxCachedRequests, speechProvider);
    }

    private static void requirePort(int port) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
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
                    "The request is not allowed by this server."
            );
        }
        if (HEALTH_PATH.equals(exchange.getRequestURI().getRawPath())) {
            if (exchange.getRequestURI().getRawQuery() != null) {
                return error(400, "invalid_request", "This endpoint does not accept query parameters.");
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "GET");
                return error(405, "method_not_allowed", "Only GET is supported for this endpoint.");
            }
            // Liveness only: no provider call, credentials, run data, or configuration disclosure.
            exchange.getResponseHeaders().set(SONG_CUE_CAPABILITY_HEADER, "1");
            exchange.getResponseHeaders().set(SONG_STUDY_CAPABILITY_HEADER, "1");
            if (provider.supportsSongPreparation()) {
                exchange.getResponseHeaders().set(SONG_PREPARATION_CAPABILITY_HEADER, "1");
            }
            if (provider.supportsSongSelection()) {
                exchange.getResponseHeaders().set(SONG_SELECTION_CAPABILITY_HEADER, "1");
            }
            if (provider.supportsSongSnippets()) {
                exchange.getResponseHeaders().set(SONG_SNIPPETS_CAPABILITY_HEADER, "1");
            }
            if (speechProvider != null) {
                exchange.getResponseHeaders().set(REFLECTION_VOICE_CAPABILITY_HEADER, "1");
            }
            return new EndpointResponse(200, "{\"status\":\"ok\"}");
        }
        if (remoteAccess != null && !remoteAccess.authorizes(exchange)) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
            return error(401, "unauthorized", "A valid demo access token is required.");
        }
        boolean speech = SPEECH_PATH.equals(exchange.getRequestURI().getRawPath());
        boolean songPreparation = SONG_PREPARATION_PATH.equals(exchange.getRequestURI().getRawPath());
        if (!speech && !songPreparation && !CANDIDATES_PATH.equals(exchange.getRequestURI().getRawPath())) {
            return error(404, "not_found", "The requested endpoint does not exist.");
        }
        if (exchange.getRequestURI().getRawQuery() != null) {
            return error(400, "invalid_request", "This endpoint does not accept query parameters.");
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            return error(405, "method_not_allowed", "Only POST is supported for this endpoint.");
        }
        List<String> contentTypes = exchange.getRequestHeaders().get("Content-Type");
        if (contentTypes == null || contentTypes.size() != 1 || !isJsonContentType(contentTypes.get(0))) {
            return error(
                    415,
                    "unsupported_media_type",
                    "Content-Type must be application/json."
            );
        }
        List<String> encodings = exchange.getRequestHeaders().get("Content-Encoding");
        if (encodings != null && (encodings.size() != 1 || !isIdentityEncoding(encodings.get(0)))) {
            return error(
                    415,
                    "unsupported_media_type",
                    "Compressed request bodies are not supported."
            );
        }

        byte[] body = readBoundedBody(exchange);
        if (speech) {
            return synthesizeSpeech(ReflectionApiJson.parseSpeechRequest(body));
        }
        if (songPreparation) {
            return prepareSongOnce(ReflectionApiJson.parseSongPreparationRequest(body));
        }
        ApiRequest request = ReflectionApiJson.parseRequest(body);
        if (provider.supportsSongSnippets()) {
            exchange.getResponseHeaders().set(SONG_SNIPPETS_CAPABILITY_HEADER, "1");
        } else if (request.allowSongSnippetLookup()) {
            return error(503, "provider_unavailable", "Song snippet lookup is not configured.");
        }
        if (!request.songCandidates().isEmpty() && !provider.supportsSongSelection()) {
            return error(503, "provider_unavailable", "Song selection is not configured.");
        }
        return generateOnce(request);
    }

    private boolean hasAllowedHost(HttpExchange exchange) {
        List<String> hostValues = exchange.getRequestHeaders().get("Host");
        if (hostValues == null || hostValues.size() != 1) {
            return false;
        }

        String value = hostValues.get(0);
        if (remoteAccess != null) {
            return remoteAccess.allowsHost(value);
        }
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
        RequestWork work;
        boolean owner;
        synchronized (cacheLock) {
            RequestWork existing = workByRequestId.get(requestId);
            if (existing != null) {
                if (!existing.request().equals(request)) {
                    return error(
                            409,
                            "request_id_conflict",
                            "The requestId is already associated with different evidence."
                    );
                }
                work = existing;
                owner = false;
            } else {
                // Never evict a completed ID and silently pay to regenerate its exact duplicate.
                if (workByRequestId.size() >= maxCachedRequests) {
                    return error(503, "server_capacity", "The demo request capacity has been reached.");
                }
                if (!providerSlots.tryAcquire()) {
                    // Busy requests are not cached; retry with the same ID is safe after admission frees.
                    return error(503, "server_busy", "The reflection service is busy. Try again later.");
                }
                work = new RequestWork(request, new CompletableFuture<>());
                workByRequestId.put(requestId, work);
                owner = true;
            }
        }
        if (!owner) return work.response().join();

        EndpointResponse response = error(500, "internal_error", "The reflection request could not be completed.");
        try {
            response = callProvider(request);
            return response;
        } finally {
            // Even an abnormal provider exit must unblock duplicates and release its bounded slot.
            work.response().complete(response);
            providerSlots.release();
        }
    }

    private EndpointResponse callProvider(ApiRequest request) {
        try {
            ReflectionProvider.GeneratedReflections generated = provider.generateForDelivery(
                    request.evidence(), request.songPreparationId(), request.songCandidates(), request.allowSongSnippetLookup());
            String body = ReflectionApiJson.success(request, generated, generationMode);
            if (body.getBytes(StandardCharsets.UTF_8).length > MAX_RESPONSE_BYTES) {
                return providerFailure(FailureType.INVALID_RESPONSE);
            }
            return new EndpointResponse(
                    200, body.getBytes(StandardCharsets.UTF_8), JSON_CONTENT_TYPE,
                    generated.songCueId() != null
            );
        } catch (ReflectionProvider.SongPreparationConflictException conflict) {
            return error(409, "preparation_id_conflict", "The preparationId belongs to a different song.");
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

    private EndpointResponse prepareSongOnce(ReflectionContract.SongPreparationRequest request) {
        if (!provider.supportsSongPreparation()) {
            return error(503, "provider_unavailable", "Song preparation is not configured.");
        }
        SongPreparationWork work;
        boolean owner;
        synchronized (cacheLock) {
            long now = System.nanoTime();
            songPreparationById.values().removeIf(item -> item.response().isDone()
                    && now - item.admittedNanos() >= AnthropicReflectionProvider.SONG_PREPARATION_TTL.toNanos());
            SongPreparationWork existing = songPreparationById.get(request.preparationId());
            if (existing != null) {
                if (!sameSongIdentity(existing.request(), request)) {
                    return error(409, "preparation_id_conflict", "The preparationId belongs to a different song.");
                }
                work = existing;
                owner = false;
            } else {
                if (songPreparationById.size() >= AnthropicReflectionProvider.MAX_PREPARED_SONGS) {
                    return error(503, "server_capacity", "The song preparation capacity has been reached.");
                }
                if (!songPreparationSlots.tryAcquire()) {
                    return error(503, "server_busy", "The song preparation service is busy.");
                }
                if (!providerSlots.tryAcquire()) {
                    songPreparationSlots.release();
                    return error(503, "server_busy", "The reflection service is busy. Try again later.");
                }
                work = new SongPreparationWork(request, now, new CompletableFuture<>());
                songPreparationById.put(request.preparationId(), work);
                owner = true;
            }
        }
        if (!owner) return work.response().join();
        EndpointResponse response = error(500, "internal_error", "Song preparation could not complete.");
        try {
            ReflectionProvider.SongPreparationState state = provider.prepareSong(request);
            if (state == null) response = providerFailure(FailureType.INVALID_RESPONSE);
            else response = new EndpointResponse(200,
                    ReflectionApiJson.songPreparationResponse(request.preparationId(), state));
            return response;
        } catch (ReflectionProvider.SongPreparationConflictException conflict) {
            return response = error(409, "preparation_id_conflict", "The preparationId belongs to a different song.");
        } catch (ReflectionProviderException failure) {
            return response = providerFailure(failure.failureType());
        } catch (RuntimeException failure) {
            return response;
        } finally {
            work.response().complete(response);
            providerSlots.release();
            songPreparationSlots.release();
        }
    }

    private static boolean sameSongIdentity(ReflectionContract.SongPreparationRequest first,
                                            ReflectionContract.SongPreparationRequest next) {
        return first.trackTitle().strip().replaceAll("\\s+", " ").equalsIgnoreCase(
                next.trackTitle().strip().replaceAll("\\s+", " "))
                && first.artistName().strip().replaceAll("\\s+", " ").equalsIgnoreCase(
                next.artistName().strip().replaceAll("\\s+", " "));
    }

    private EndpointResponse synthesizeSpeech(String text) {
        if (speechProvider == null) {
            return error(503, "provider_unavailable", "The reflection voice is not configured.");
        }
        if (!providerSlots.tryAcquire()) {
            return error(503, "server_busy", "The reflection service is busy. Try again later.");
        }
        try {
            byte[] audio = speechProvider.synthesize(text);
            if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_RESPONSE_BYTES) {
                return speechProviderFailure(FailureType.INVALID_RESPONSE);
            }
            // Audio remains request-scoped: no cache entry, filename or durable storage.
            return new EndpointResponse(200, audio, "audio/mpeg");
        } catch (ReflectionProviderException failure) {
            return speechProviderFailure(failure.failureType());
        } catch (RuntimeException unexpected) {
            return error(500, "internal_error", "The reflection voice could not be generated.");
        } finally {
            providerSlots.release();
        }
    }

    private static EndpointResponse speechProviderFailure(FailureType failureType) {
        return switch (failureType) {
            case TIMEOUT -> error(504, "provider_timeout", "The reflection voice provider timed out.");
            case CONFIGURATION, TRANSPORT -> error(503, "provider_unavailable",
                    "The reflection voice provider is unavailable.");
            case PROVIDER_ERROR, REFUSAL, TRUNCATED, INVALID_RESPONSE -> error(502, "provider_failure",
                    "The reflection voice provider could not produce valid audio.");
        };
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
        List<String> lengths = exchange.getRequestHeaders().get("Content-Length");
        if (lengths != null && lengths.size() != 1) {
            throw new IllegalArgumentException("Content-Length must appear once");
        }
        String contentLength = lengths == null ? null : lengths.get(0);
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

    /** Validated ingress settings. Never print or expose the opaque token as configuration text. */
    public static final class RemoteAccess {
        private final InetAddress bindAddress;
        private final String publicAuthority;
        private final byte[] authorizationBytes;

        public RemoteAccess(String bindAddress, String publicAuthority, String demoToken) {
            this.bindAddress = numericIpv4(bindAddress);
            this.publicAuthority = publicAuthority(publicAuthority);
            if (demoToken == null || !demoToken.matches("[A-Za-z0-9_-]{43,128}")) {
                throw new IllegalArgumentException(
                        "RUNSTATE_DEMO_TOKEN requires 43 to 128 URL-safe characters from at least 32 random bytes."
                );
            }
            this.authorizationBytes = ("Bearer " + demoToken).getBytes(StandardCharsets.US_ASCII);
        }

        private boolean authorizes(HttpExchange exchange) {
            List<String> values = exchange.getRequestHeaders().get("Authorization");
            if (values == null || values.size() != 1) return false;
            String supplied = values.get(0);
            return supplied.length() <= 7 + 128 && MessageDigest.isEqual(
                    authorizationBytes, supplied.getBytes(StandardCharsets.UTF_8));
        }

        private boolean allowsHost(String supplied) {
            String normalized = supplied.toLowerCase(Locale.ROOT);
            return publicAuthority.equals(normalized) ||
                    (!publicAuthority.contains(":") && (publicAuthority + ":443").equals(normalized));
        }

        private static InetAddress numericIpv4(String supplied) {
            if (supplied == null || !supplied.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) {
                throw new IllegalArgumentException("RUNSTATE_REFLECTION_BIND_ADDRESS must be a numeric IPv4 address.");
            }
            byte[] bytes = new byte[4];
            String[] parts = supplied.split("\\.");
            for (int index = 0; index < parts.length; index++) {
                int part = Integer.parseInt(parts[index]);
                if (part > 255 || !Integer.toString(part).equals(parts[index])) {
                    throw new IllegalArgumentException("RUNSTATE_REFLECTION_BIND_ADDRESS must be a numeric IPv4 address.");
                }
                bytes[index] = (byte) part;
            }
            try {
                return InetAddress.getByAddress(bytes);
            } catch (IOException impossible) {
                throw new IllegalStateException("The IPv4 bind address could not be created.");
            }
        }

        private static String publicAuthority(String supplied) {
            if (supplied == null || supplied.isBlank() || supplied.length() > 259) {
                throw invalidPublicHost();
            }
            String normalized = supplied.toLowerCase(Locale.ROOT);
            String[] parts = normalized.split(":", -1);
            if (parts.length > 2) throw invalidPublicHost();
            String host = parts[0];
            String label = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?";
            if (host.length() > 253 || !host.matches(label + "(?:\\." + label + ")+") ||
                    host.matches("[0-9.]+") || host.endsWith(".localhost")) {
                throw invalidPublicHost();
            }
            if (parts.length == 2) {
                if (!parts[1].matches("[0-9]{1,5}")) throw invalidPublicHost();
                int port = Integer.parseInt(parts[1]);
                if (port < 1 || port > 65_535 || !Integer.toString(port).equals(parts[1])) {
                    throw invalidPublicHost();
                }
                if (port == 443) return host;
            }
            return normalized;
        }

        private static IllegalArgumentException invalidPublicHost() {
            return new IllegalArgumentException(
                    "RUNSTATE_REFLECTION_PUBLIC_HOST must be an ASCII public hostname with an optional HTTPS port."
            );
        }

        @Override
        public String toString() {
            return "RemoteAccess[credentials=redacted]";
        }
    }

    private static void writeResponse(HttpExchange exchange, EndpointResponse response)
            throws IOException {
        byte[] body = response.body();
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        if (response.songStudyAcknowledged()) {
            exchange.getResponseHeaders().set(SONG_STUDY_CAPABILITY_HEADER, "1");
        }
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

    private record SongPreparationWork(ReflectionContract.SongPreparationRequest request, long admittedNanos,
                                       CompletableFuture<EndpointResponse> response) {
        @Override public String toString() { return "SongPreparationWork[identity=redacted]"; }
    }

    private record EndpointResponse(int status, byte[] body, String contentType, boolean songStudyAcknowledged) {
        EndpointResponse(int status, String body) {
            this(status, body.getBytes(StandardCharsets.UTF_8), JSON_CONTENT_TYPE, false);
        }
        EndpointResponse(int status, byte[] body, String contentType) {
            this(status, body, contentType, false);
        }
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
