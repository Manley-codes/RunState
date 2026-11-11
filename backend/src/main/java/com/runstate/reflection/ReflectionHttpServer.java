package com.runstate.reflection;

import com.runstate.reflection.ReflectionApiJson.ApiRequest;
import com.runstate.reflection.ReflectionApiJson.InvalidJsonException;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import com.runstate.reflection.BackendAccessPolicy.Principal;
import com.runstate.reflection.BackendAccessPolicy.Tier;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    /** The separately chosen voice for countdown, facts and announcements; never the reflection voice. */
    public static final String RUN_VOICE_PATH = "/api/run-voice/speech";
    static final String ACCESS_PATH = "/api/reflections/access";
    static final String TRIAL_PATH = "/api/reflections/trial";
    static final String RUN_ACCESS_PATH = "/api/reflections/run-access";
    static final String ACCESS_HEADER = "X-RunState-Reflection-Access";
    static final String RUN_ID_HEADER = "X-RunState-Run-Id";
    static final String REPLAY_ONLY_HEADER = "X-RunState-Replay-Only";
    public static final String SONG_PREPARATION_PATH = "/api/reflections/song-study";
    public static final String HEALTH_PATH = "/health";
    public static final String SONG_CUE_CAPABILITY_HEADER = "X-RunState-Song-Cue";
    public static final String SONG_STUDY_CAPABILITY_HEADER = "X-RunState-Song-Study";
    public static final String SONG_PREPARATION_CAPABILITY_HEADER = "X-RunState-Song-Preparation";
    public static final String SONG_SELECTION_CAPABILITY_HEADER = "X-RunState-Song-Selection";
    public static final String SONG_SNIPPETS_CAPABILITY_HEADER = "X-RunState-Song-Snippets";
    public static final String SONG_SNIPPET_FALLBACKS_CAPABILITY_HEADER = "X-RunState-Song-Snippet-Fallbacks";
    public static final String SONG_LOADED_AT_FINISH_CAPABILITY_HEADER = "X-RunState-Song-Loaded-At-Finish";
    public static final String REFLECTION_VOICE_CAPABILITY_HEADER = "X-RunState-Reflection-Voice";
    public static final String RUN_VOICE_CAPABILITY_HEADER = "X-RunState-Run-Voice";
    public static final int MAX_RUN_VOICE_TEXT_LENGTH = 500;
    /** Short run lines get their own slots so a countdown or announcement never waits on a reflection. */
    public static final int MAX_CONCURRENT_RUN_VOICE_CALLS = 2;
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
    private final SpeechReplayStore speechReplayStore;
    private final WrittenReflectionReplayStore writtenReplayStore;
    private final ProviderSpendBudget spendBudget;
    private final LocalProviderUsage usage;
    private final ReflectionAllowance allowance;
    private final SpeechProvider runVoiceProvider;
    private final GenerationMode generationMode;
    private final HttpServer server;
    private final ExecutorService executor;
    private final RemoteAccess remoteAccess;
    private final Semaphore providerSlots;
    private final Semaphore songPreparationSlots;
    private final Semaphore runVoiceSlots = new Semaphore(MAX_CONCURRENT_RUN_VOICE_CALLS);
    private final int maxCachedRequests;
    private final Object cacheLock = new Object();
    private final Map<OwnedRequestId, RequestWork> workByRequestId = new HashMap<>();
    private final Map<OwnedRequestId, SongPreparationWork> songPreparationById = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    private ReflectionHttpServer(
            ReflectionProvider provider,
            int port,
            GenerationMode generationMode,
            RemoteAccess remoteAccess,
            int maxConcurrentProviderCalls,
            int maxCachedRequests,
            SpeechProvider speechProvider,
            SpeechProvider runVoiceProvider,
            SpeechReplayStore speechReplayStore,
            WrittenReflectionReplayStore writtenReplayStore,
            ProviderSpendBudget spendBudget,
            ReflectionAllowance allowance
    ) throws IOException {
        this(provider, port, generationMode, remoteAccess, maxConcurrentProviderCalls, maxCachedRequests,
                speechProvider, runVoiceProvider, speechReplayStore, writtenReplayStore, spendBudget, allowance,
                LocalProviderUsage.configured(System.getenv(), remoteAccess != null, System.out));
    }

    ReflectionHttpServer(ReflectionProvider provider, int port, GenerationMode generationMode, RemoteAccess remoteAccess,
            int maxConcurrentProviderCalls, int maxCachedRequests, SpeechProvider speechProvider, SpeechProvider runVoiceProvider,
            SpeechReplayStore speechReplayStore, WrittenReflectionReplayStore writtenReplayStore, ProviderSpendBudget spendBudget,
            ReflectionAllowance allowance, LocalProviderUsage usage) throws IOException {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.speechProvider = speechProvider;
        this.speechReplayStore = speechReplayStore;
        this.writtenReplayStore = writtenReplayStore;
        this.spendBudget = spendBudget;
        this.usage = remoteAccess == null ? usage : null;
        this.allowance = allowance;
        this.runVoiceProvider = runVoiceProvider;
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
        return create(provider, port, generationMode, speechProvider, null);
    }

    /** Both voices are optional and independent; the run voice never changes the reflection voice. */
    public static ReflectionHttpServer create(ReflectionProvider provider, int port,
            GenerationMode generationMode, SpeechProvider speechProvider, SpeechProvider runVoiceProvider)
            throws IOException {
        return configuredServer(provider, port, generationMode, null, MAX_CONCURRENT_PROVIDER_CALLS,
                MAX_CACHED_REQUESTS, speechProvider, runVoiceProvider);
    }

    static ReflectionHttpServer create(ReflectionProvider provider, int port, GenerationMode generationMode,
            SpeechProvider speechProvider, SpeechProvider runVoiceProvider, SpeechReplayStore replayStore)
            throws IOException {
        return create(provider, port, generationMode, speechProvider, runVoiceProvider, replayStore, null);
    }

    static ReflectionHttpServer create(ReflectionProvider provider, int port, GenerationMode generationMode,
            SpeechProvider speechProvider, SpeechProvider runVoiceProvider, SpeechReplayStore replayStore,
            WrittenReflectionReplayStore writtenStore) throws IOException {
        return new ReflectionHttpServer(provider, port, generationMode, null,
                MAX_CONCURRENT_PROVIDER_CALLS, MAX_CACHED_REQUESTS, speechProvider, runVoiceProvider, replayStore, writtenStore, null, null);
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
        return createRemote(provider, port, generationMode, remoteAccess, speechProvider, null);
    }

    public static ReflectionHttpServer createRemote(ReflectionProvider provider, int port,
            GenerationMode generationMode, RemoteAccess remoteAccess, SpeechProvider speechProvider,
            SpeechProvider runVoiceProvider) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess,
                MAX_CONCURRENT_PROVIDER_CALLS, MAX_CACHED_REQUESTS, speechProvider, runVoiceProvider);
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
        return createRemote(provider, port, generationMode, remoteAccess, maxConcurrentProviderCalls,
                maxCachedRequests, speechProvider, null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port,
            GenerationMode generationMode, RemoteAccess remoteAccess, int maxConcurrentProviderCalls,
            int maxCachedRequests, SpeechProvider speechProvider, SpeechProvider runVoiceProvider)
            throws IOException {
        return configuredServer(provider, port, generationMode, Objects.requireNonNull(remoteAccess, "remoteAccess"),
                maxConcurrentProviderCalls, maxCachedRequests, speechProvider, runVoiceProvider);
    }

    private static ReflectionHttpServer configuredServer(ReflectionProvider provider, int port, GenerationMode mode,
            RemoteAccess access, int providerSlots, int cacheSize, SpeechProvider voice, SpeechProvider runVoice)
            throws IOException {
        Map<String, String> env = System.getenv();
        boolean remote = access != null;
        boolean usesProviders = mode != GenerationMode.FAKE || voice != null || runVoice != null;
        return new ReflectionHttpServer(provider, port, mode, access, providerSlots, cacheSize, voice, runVoice,
                ReflectionBackendMain.configuredSpeechReplayStore(voice, env, remote),
                ReflectionBackendMain.configuredWrittenReplayStore(mode, env, remote),
                usesProviders ? ReflectionBackendMain.configuredSpendBudget(env, remote,
                        System.getProperty("jdk.httpclient.redirects.retrylimit")) : null,
                remote && access.accessPolicy.needsRunAllowances() ? ReflectionBackendMain.configuredAllowance(env) : null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port,
            GenerationMode generationMode, RemoteAccess remoteAccess, int maxConcurrentProviderCalls,
            int maxCachedRequests, SpeechProvider speechProvider, SpeechProvider runVoiceProvider,
            SpeechReplayStore replayStore) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess, maxConcurrentProviderCalls, maxCachedRequests,
                speechProvider, runVoiceProvider, replayStore, null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port, GenerationMode generationMode,
            RemoteAccess remoteAccess, int maxConcurrentProviderCalls, int maxCachedRequests,
            SpeechProvider speechProvider, SpeechProvider runVoiceProvider, SpeechReplayStore replayStore,
            WrittenReflectionReplayStore writtenStore) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess, maxConcurrentProviderCalls,
                maxCachedRequests, speechProvider, runVoiceProvider, replayStore, writtenStore, null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port, GenerationMode generationMode,
            RemoteAccess remoteAccess, int maxConcurrentProviderCalls, int maxCachedRequests,
            SpeechProvider speechProvider, SpeechProvider runVoiceProvider, SpeechReplayStore replayStore,
            WrittenReflectionReplayStore writtenStore, ProviderSpendBudget spendBudget) throws IOException {
        return createRemote(provider, port, generationMode, remoteAccess, maxConcurrentProviderCalls, maxCachedRequests,
                speechProvider, runVoiceProvider, replayStore, writtenStore, spendBudget, null);
    }

    static ReflectionHttpServer createRemote(ReflectionProvider provider, int port, GenerationMode generationMode,
            RemoteAccess remoteAccess, int maxConcurrentProviderCalls, int maxCachedRequests,
            SpeechProvider speechProvider, SpeechProvider runVoiceProvider, SpeechReplayStore replayStore,
            WrittenReflectionReplayStore writtenStore, ProviderSpendBudget spendBudget, ReflectionAllowance allowance) throws IOException {
        return new ReflectionHttpServer(provider, port, generationMode,
                Objects.requireNonNull(remoteAccess, "remoteAccess"),
                maxConcurrentProviderCalls, maxCachedRequests, speechProvider, runVoiceProvider, replayStore, writtenStore, spendBudget, allowance);
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
        } catch (ReflectionAllowance.Denied denied) {
            response = deniedAccess(denied);
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
            exchange.getResponseHeaders().set(ACCESS_HEADER, "1");
            exchange.getResponseHeaders().set(SONG_CUE_CAPABILITY_HEADER, "1");
            exchange.getResponseHeaders().set(SONG_STUDY_CAPABILITY_HEADER, "1");
            if (provider.supportsSongPreparation()) {
                exchange.getResponseHeaders().set(SONG_PREPARATION_CAPABILITY_HEADER, "1");
            }
            if (provider.supportsSongSelection()) {
                exchange.getResponseHeaders().set(SONG_SELECTION_CAPABILITY_HEADER, "1");
                // Song candidates may mark the song still loaded at the finish, which gets no clip.
                exchange.getResponseHeaders().set(SONG_LOADED_AT_FINISH_CAPABILITY_HEADER, "1");
            }
            if (provider.supportsSongSnippets()) {
                exchange.getResponseHeaders().set(SONG_SNIPPETS_CAPABILITY_HEADER, "1");
            }
            if (provider.supportsSongSnippetFallbacks()) {
                exchange.getResponseHeaders().set(SONG_SNIPPET_FALLBACKS_CAPABILITY_HEADER, "1");
            }
            if (speechProvider != null && speechReplayStore != null) {
                exchange.getResponseHeaders().set(REFLECTION_VOICE_CAPABILITY_HEADER, "1");
            }
            if (runVoiceProvider != null) {
                exchange.getResponseHeaders().set(RUN_VOICE_CAPABILITY_HEADER, "1");
            }
            return new EndpointResponse(200, "{\"status\":\"ok\"}");
        }
        Principal runner = new Principal("local-loopback");
        if (remoteAccess != null) {
            Optional<BackendAccessPolicy.Principal> principal = remoteAccess.authenticate(exchange);
            if (principal.isEmpty()) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer");
                return error(401, "unauthorized", "A valid access token is required.");
            }
            runner = principal.get();
        }
        boolean speech = SPEECH_PATH.equals(exchange.getRequestURI().getRawPath());
        boolean runVoice = RUN_VOICE_PATH.equals(exchange.getRequestURI().getRawPath());
        boolean songPreparation = SONG_PREPARATION_PATH.equals(exchange.getRequestURI().getRawPath());
        String path = exchange.getRequestURI().getRawPath();
        boolean accessStatus = ACCESS_PATH.equals(path);
        boolean activation = TRIAL_PATH.equals(path), admission = RUN_ACCESS_PATH.equals(path);
        if (!speech && !runVoice && !songPreparation && !accessStatus && !activation && !admission && !CANDIDATES_PATH.equals(path)) {
            return error(404, "not_found", "The requested endpoint does not exist.");
        }
        if (exchange.getRequestURI().getRawQuery() != null) {
            return error(400, "invalid_request", "This endpoint does not accept query parameters.");
        }
        String method = accessStatus ? "GET" : "POST";
        if (!method.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", method);
            return error(405, "method_not_allowed", "Only " + method + " is supported for this endpoint.");
        }
        if (accessStatus) {
            return accessResponse(runner, path, null);
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
        if (activation || admission) return accessResponse(runner, path, body);
        try (var scope = ProviderSpendScope.open(spendBudget, replayOwner(runner.id()), usage)) {
            return authorizedPost(exchange, runner, body, speech, runVoice, songPreparation);
        }
    }

    private EndpointResponse accessResponse(Principal runner, String path, byte[] body) throws InvalidJsonException {
        ReflectionApiJson.RunAdmission run = null;
        if (body != null) {
            if (TRIAL_PATH.equals(path)) ReflectionApiJson.requireEmptyObject(body);
            else run = ReflectionApiJson.parseRunAdmission(body);
        }
        try {
            ReflectionAllowance.View view;
            if (run != null && run.replayOnly()) {
                if (writtenReplayStore == null) return writtenStorageUnavailable();
                if (!writtenReplayStore.hasSavedRun(replayOwner(runner.id()), run.runId()))
                    throw new ReflectionAllowance.Denied("reflection_replay_unavailable");
                run = null; // Return status without allocating a slot or calling a provider.
            }
            if (runner.tier() == Tier.OWNER || runner.tier() == Tier.TRACKING) {
                if (TRIAL_PATH.equals(path) || (run != null && runner.tier() == Tier.TRACKING))
                    throw new ReflectionAllowance.Denied("reflection_access_required");
                view = ReflectionAllowance.basicView(runner.tier());
            } else if (allowance == null) return accessStorageUnavailable();
            else if (TRIAL_PATH.equals(path)) view = allowance.activate(runner);
            else if (run != null) view = allowance.admit(runner, run.runId());
            else view = allowance.status(runner);
            return new EndpointResponse(200, view.json(runner.tier()));
        } catch (IOException storageFailure) {
            return accessStorageUnavailable();
        }
    }

    private static EndpointResponse deniedAccess(ReflectionAllowance.Denied denied) {
        return error(403, denied.code, "A reflection is not available for this run.");
    }

    private static EndpointResponse accessStorageUnavailable() {
        return error(503, "reflection_access_unavailable", "Reflection access storage is unavailable.");
    }

    private static String requiredRunHeader(HttpExchange exchange) {
        List<String> ids = exchange.getRequestHeaders().get(RUN_ID_HEADER);
        if (ids == null || ids.size() != 1) throw new ReflectionAllowance.Denied("run_admission_required");
        ReflectionContract.requireCanonicalUuid(ids.get(0), "runId");
        return ids.get(0);
    }

    private EndpointResponse authorizedPost(HttpExchange exchange, Principal runner, byte[] body,
            boolean speech, boolean runVoice, boolean songPreparation) throws InvalidJsonException {
        if (speech) {
            // Configured recipient IDs cannot impersonate the loopback or legacy owner on disk.
            String runId = runner.tier() == Tier.OWNER || runner.tier() == Tier.TRACKING ? null : requiredRunHeader(exchange);
            return synthesizeSpeech(runner, runId, ReflectionApiJson.parseSpeechRequest(body));
        }
        if (runVoice) {
            if (runner.tier() != Tier.OWNER) throw new ReflectionAllowance.Denied("run_voice_not_included");
            return synthesizeRunVoice(ReflectionApiJson.parseSpeechRequest(body));
        }
        if (songPreparation) {
            if (runner.tier() == Tier.TRACKING) throw new ReflectionAllowance.Denied("reflection_access_required");
            if (runner.tier() != Tier.OWNER && allowance == null) return accessStorageUnavailable();
            String runId = runner.tier() == Tier.OWNER ? null : requiredRunHeader(exchange);
            return prepareSongOnce(ReflectionApiJson.parseSongPreparationRequest(body), runner, runId);
        }
        ApiRequest request = ReflectionApiJson.parseRequest(body);
        SnippetCapabilities available = new SnippetCapabilities(provider.supportsSongSnippets(),
                provider.supportsSongSnippetFallbacks());
        List<String> replayOnly = exchange.getRequestHeaders().get(REPLAY_ONLY_HEADER);
        EndpointResponse response;
        if (replayOnly != null) {
            if (!replayOnly.equals(List.of("1"))) throw new IllegalArgumentException("Invalid replay-only header.");
            response = writtenReplayStore == null ? writtenStorageUnavailable() : generateWithReplay(request, runner, available,
                    deniedAccess(new ReflectionAllowance.Denied("reflection_replay_unavailable")));
        } else response = generateOnce(request, runner, available);
        SnippetCapabilities capabilities = response.snippetCapabilities() == null ? available : response.snippetCapabilities();
        if (capabilities.snippets()) exchange.getResponseHeaders().set(SONG_SNIPPETS_CAPABILITY_HEADER, "1");
        if (capabilities.fallbacks()) exchange.getResponseHeaders().set(SONG_SNIPPET_FALLBACKS_CAPABILITY_HEADER, "1");
        return response;
    }

    private String replayOwner(String principalId) {
        return remoteAccess != null && remoteAccess.individualAccess ? "recipient:" + principalId : principalId;
    }

    private EndpointResponse unsupportedRequest(ApiRequest request, SnippetCapabilities available) {
        if (request.allowSongSnippetLookup() && !available.snippets()) {
            return error(503, "provider_unavailable", "Song snippet lookup is not configured.");
        }
        if (request.allowSongSnippetFallbacks() && !available.fallbacks()) {
            return error(503, "provider_unavailable", "Song snippet fallbacks are not configured.");
        }
        if (!request.songCandidates().isEmpty() && !provider.supportsSongSelection()) {
            return error(503, "provider_unavailable", "Song selection is not configured.");
        }
        return null;
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

    private EndpointResponse generateOnce(ApiRequest request, Principal runner, SnippetCapabilities available) {
        String principalId = runner.id();
        EndpointResponse unsupported = unsupportedRequest(request, available);
        if (writtenReplayStore == null) {
            if (runner.tier() != Tier.OWNER) return runner.tier() == Tier.TRACKING
                    ? deniedAccess(new ReflectionAllowance.Denied("reflection_access_required")) : writtenStorageUnavailable();
            if (generationMode != GenerationMode.FAKE) return writtenStorageUnavailable();
            if (unsupported != null) return unsupported;
        }
        OwnedRequestId requestId = new OwnedRequestId(principalId, request.identifiers().requestId());
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
                if (writtenReplayStore == null) {
                    // The fake demo keeps its bounded, process-local results.
                    if (workByRequestId.size() >= maxCachedRequests) {
                        return error(503, "server_capacity", "The demo request capacity has been reached.");
                    }
                    if (!providerSlots.tryAcquire()) {
                        return error(503, "server_busy", "The reflection service is busy. Try again later.");
                    }
                }
                work = new RequestWork(request, new CompletableFuture<>());
                workByRequestId.put(requestId, work);
                owner = true;
            }
        }
        if (!owner) return work.response().join();

        EndpointResponse response = error(500, "internal_error", "The reflection request could not be completed.");
        try {
            response = writtenReplayStore == null ? callProvider(request, principalId)
                    : generateWithReplay(request, runner, available, unsupported);
            return response;
        } catch (ProviderSpendScope.Denied denied) {
            response = spendDenial(denied);
            if (writtenReplayStore == null && !denied.attempted()) {
                synchronized (cacheLock) { workByRequestId.remove(requestId, work); }
            }
            return response;
        } finally {
            // Even an abnormal provider exit must unblock duplicates and release its bounded slot.
            work.response().complete(response);
            if (writtenReplayStore == null) {
                providerSlots.release();
            } else {
                // This map only joins simultaneous requests. SQLite owns completed results and their expiry.
                synchronized (cacheLock) { workByRequestId.remove(requestId); }
            }
        }
    }

    private EndpointResponse generateWithReplay(ApiRequest request, Principal runner,
            SnippetCapabilities available, EndpointResponse unsupported) {
        String principalId = runner.id();
        boolean limited = runner.tier() == Tier.TRIAL || runner.tier() == Tier.PILOT;
        if (unsupported == null) {
            if (runner.tier() == Tier.TRACKING) unsupported = deniedAccess(new ReflectionAllowance.Denied("reflection_access_required"));
            else if (limited && allowance == null) unsupported = accessStorageUnavailable();
        }
        boolean slot = unsupported == null && providerSlots.tryAcquire();
        try {
            String input = ReflectionApiJson.writtenOperationIdentity(request, generationMode);
            String runId = request.identifiers().runId();
            var claim = writtenReplayStore.claim(replayOwner(principalId), request.identifiers().requestId(),
                    input, slot);
            return switch (claim.state()) {
                case CONFLICT -> error(409, "request_id_conflict", "The requestId is already associated with different evidence.");
                case FULL -> error(503, "server_capacity", "The reflection request capacity has been reached.");
                case BUSY -> unsupported != null ? unsupported :
                        error(503, "server_busy", "The reflection service is busy. Try again later.");
                case PENDING -> error(503, "reflection_pending", "This reflection request has already been reserved.");
                case CACHED -> {
                    var replay = ReflectionApiJson.restoreWrittenReplay(claim.body(), request.identifiers());
                    if (limited && allowance != null) allowance.settle(runner, runId, input, replay.body());
                    yield new EndpointResponse(200, replay.body(), JSON_CONTENT_TYPE, claim.songStudyAcknowledged(),
                            new SnippetCapabilities(replay.snippets(), replay.fallbacks()));
                }
                case CLAIMED -> {
                    EndpointResponse generated;
                    if (limited) {
                        try {
                            allowance.startWriting(runner, runId, input);
                        } catch (ReflectionAllowance.Denied denied) {
                            writtenReplayStore.releaseUnsent(claim);
                            yield deniedAccess(denied);
                        } catch (IOException unavailable) {
                            writtenReplayStore.releaseUnsent(claim);
                            yield accessStorageUnavailable();
                        }
                    }
                    try {
                        generated = callProvider(request, principalId);
                    } catch (ProviderSpendScope.Denied denied) {
                        // A partially sent generation must retain its duplicate-charge protection.
                        if (!denied.attempted()) {
                            writtenReplayStore.releaseUnsent(claim);
                            if (limited) allowance.releaseUnsentWriter(runner, runId);
                        }
                        throw denied;
                    }
                    if (generated.status() == 200) {
                        byte[] saved = ReflectionApiJson.writtenReplayPayload(generated.body(),
                                available.snippets(), available.fallbacks());
                        writtenReplayStore.complete(claim, saved, generated.songStudyAcknowledged(), replayOwner(principalId), runId);
                        if (limited) allowance.settle(runner, runId, input, generated.body());
                    }
                    yield generated;
                }
            };
        } catch (IOException failure) {
            return writtenStorageUnavailable();
        } finally {
            if (slot) providerSlots.release();
        }
    }

    private static EndpointResponse writtenStorageUnavailable() {
        return error(503, "reflection_storage_unavailable", "Written reflection storage is unavailable.");
    }

    private static EndpointResponse spendDenial(ProviderSpendScope.Denied denied) {
        return denied.unavailable()
                ? error(503, "spend_control_unavailable", "Paid provider access is currently unavailable.")
                : error(429, "spend_limit_reached", "The monthly allowance for paid features has been reached.");
    }

    private EndpointResponse callProvider(ApiRequest request, String principalId) {
        try {
            // Keep client IDs in responses/cache comparisons; isolate every provider preparation lookup.
            List<ReflectionContract.SongCandidate> candidates = request.songCandidates().stream()
                    .map(candidate -> new ReflectionContract.SongCandidate(candidate.candidateId(),
                            candidate.musicContext(), scopedPreparationId(principalId, candidate.songPreparationId()),
                            candidate.recording(), candidate.loadedAtFinish()))
                    .toList();
            ReflectionProvider.GeneratedReflections generated = provider.generateForDelivery(
                    request.evidence(), scopedPreparationId(principalId, request.songPreparationId()),
                    candidates, request.allowSongSnippetLookup(),
                    request.allowSongSnippetFallbacks());
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
        } catch (ProviderSpendScope.Denied denied) {
            throw denied;
        } catch (RuntimeException unexpected) {
            return error(
                    500,
                    "internal_error",
                    "The reflection request could not be completed."
            );
        }
    }

    private EndpointResponse prepareSongOnce(ReflectionContract.SongPreparationRequest request, Principal runner, String runId) {
        String principalId = runner.id();
        if (!provider.supportsSongPreparation()) {
            return error(503, "provider_unavailable", "Song preparation is not configured.");
        }
        SongPreparationWork work;
        boolean owner;
        OwnedRequestId preparationId = new OwnedRequestId(principalId, request.preparationId());
        synchronized (cacheLock) {
            long now = System.nanoTime();
            songPreparationById.values().removeIf(item -> item.response().isDone()
                    && now - item.admittedNanos() >= AnthropicReflectionProvider.SONG_PREPARATION_TTL.toNanos());
            SongPreparationWork existing = songPreparationById.get(preparationId);
            if (existing != null) {
                if (!Objects.equals(existing.runId(), runId) || !sameSongIdentity(existing.request(), request)) {
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
                work = new SongPreparationWork(request, runId, now, new CompletableFuture<>());
                songPreparationById.put(preparationId, work);
                owner = true;
            }
        }
        if (!owner) return work.response().join();
        EndpointResponse response = error(500, "internal_error", "Song preparation could not complete.");
        try {
            if (runId != null) allowance.reservePreparation(runner, runId, request.preparationId());
            ReflectionProvider.SongPreparationState state = provider.prepareSong(
                    new ReflectionContract.SongPreparationRequest(
                            scopedPreparationId(principalId, request.preparationId()),
                            request.trackTitle(), request.artistName()));
            if (state == null) response = providerFailure(FailureType.INVALID_RESPONSE);
            else response = new EndpointResponse(200,
                    ReflectionApiJson.songPreparationResponse(request.preparationId(), state));
            return response;
        } catch (ReflectionProvider.SongPreparationConflictException conflict) {
            return response = error(409, "preparation_id_conflict", "The preparationId belongs to a different song.");
        } catch (ReflectionProviderException failure) {
            return response = providerFailure(failure.failureType());
        } catch (ProviderSpendScope.Denied denied) {
            if (!denied.attempted()) {
                synchronized (cacheLock) { songPreparationById.remove(preparationId, work); }
                if (runId != null) {
                    try {
                        allowance.releaseUnsentPreparation(runner, runId, request.preparationId());
                    } catch (IOException unavailable) { return response = accessStorageUnavailable(); }
                }
            }
            return response = spendDenial(denied);
        } catch (ReflectionAllowance.Denied denied) {
            synchronized (cacheLock) { songPreparationById.remove(preparationId, work); }
            return response = deniedAccess(denied);
        } catch (IOException storageFailure) {
            synchronized (cacheLock) { songPreparationById.remove(preparationId, work); }
            return response = accessStorageUnavailable();
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

    /** A stable, non-secret provider handle; no caller can select another principal's namespace. */
    private String scopedPreparationId(String principalId, String clientId) {
        if (clientId == null || remoteAccess == null || !remoteAccess.individualAccess) return clientId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    ("runstate-song-preparation-v1\u0000" + principalId + "\u0000" + clientId)
                            .getBytes(StandardCharsets.UTF_8));
            // UUID v8 carries application-defined bits; keep the standard UUID variant.
            digest[6] = (byte) ((digest[6] & 0x0f) | 0x80);
            digest[8] = (byte) ((digest[8] & 0x3f) | 0x80);
            ByteBuffer bytes = ByteBuffer.wrap(digest);
            return new UUID(bytes.getLong(), bytes.getLong()).toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 support is unavailable.");
        }
    }

    private EndpointResponse synthesizeSpeech(Principal runner, String runId, String text) {
        if (speechProvider == null) {
            return error(503, "provider_unavailable", "The reflection voice is not configured.");
        }
        if (speechReplayStore == null) {
            return error(503, "speech_storage_unavailable", "Reflection speech storage is unavailable.");
        }
        boolean canGenerate = runner.tier() == Tier.OWNER || (runner.tier() != Tier.TRACKING && allowance != null);
        boolean hasProviderSlot = canGenerate && providerSlots.tryAcquire();
        try {
            SpeechReplayStore.Claim claim = speechReplayStore.claim(replayOwner(runner.id()), text, hasProviderSlot);
            return switch (claim.state()) {
                case CACHED -> new EndpointResponse(200, claim.audio(), "audio/mpeg");
                case PENDING -> error(503, "speech_pending", "This speech request has already been reserved.");
                case BUSY, FULL -> canGenerate ? error(503, "server_busy", "The reflection service is busy. Try again later.")
                        : runner.tier() == Tier.TRACKING ? deniedAccess(new ReflectionAllowance.Denied("reflection_access_required"))
                        : accessStorageUnavailable();
                case CLAIMED -> {
                    byte[] audio;
                    boolean newlySelected = false;
                    if (runner.tier() != Tier.OWNER) {
                        try {
                            newlySelected = allowance.selectVoice(runner, runId, text);
                        } catch (ReflectionAllowance.Denied denied) {
                            speechReplayStore.releaseUnsent(claim);
                            yield deniedAccess(denied);
                        } catch (IOException unavailable) {
                            speechReplayStore.releaseUnsent(claim);
                            yield accessStorageUnavailable();
                        }
                    }
                    try {
                        audio = speechProvider.synthesize(text);
                    } catch (ProviderSpendScope.Denied denied) {
                        if (!denied.attempted()) {
                            // Keep the replay reservation until any still-unspent branch choice is released.
                            if (newlySelected) allowance.releaseUnsentVoice(runner, runId, text);
                            speechReplayStore.releaseUnsent(claim);
                        }
                        yield spendDenial(denied);
                    }
                    if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_RESPONSE_BYTES) {
                        yield speechProviderFailure(FailureType.INVALID_RESPONSE);
                    }
                    // A lost HTTP reply can be replayed only after the completed audio is durable.
                    speechReplayStore.complete(claim, audio);
                    yield new EndpointResponse(200, audio, "audio/mpeg");
                }
            };
        } catch (IOException failure) {
            return error(503, "speech_storage_unavailable", "Reflection speech storage is unavailable.");
        } catch (ReflectionProviderException failure) {
            return speechProviderFailure(failure.failureType());
        } catch (RuntimeException unexpected) {
            return error(500, "internal_error", "The reflection voice could not be generated.");
        } finally {
            if (hasProviderSlot) providerSlots.release();
        }
    }

    /** Run announcements use their own slots, shorter limit and request-scoped audio. */
    private EndpointResponse synthesizeRunVoice(String text) {
        if (runVoiceProvider == null) {
            return error(503, "provider_unavailable", "The run voice is not configured.");
        }
        if (text.length() > MAX_RUN_VOICE_TEXT_LENGTH) {
            throw new IllegalArgumentException("Run voice text must contain 1 to 500 characters.");
        }
        if (!runVoiceSlots.tryAcquire()) {
            return error(503, "server_busy", "The run voice is busy. Try again later.");
        }
        try {
            byte[] audio = runVoiceProvider.synthesize(text);
            if (audio == null || audio.length == 0 || audio.length > MAX_AUDIO_RESPONSE_BYTES) {
                return speechProviderFailure(FailureType.INVALID_RESPONSE);
            }
            // Audio remains request-scoped: no cache entry, filename or durable storage.
            return new EndpointResponse(200, audio, "audio/mpeg");
        } catch (ProviderSpendScope.Denied denied) {
            return spendDenial(denied);
        } catch (ReflectionProviderException failure) {
            return speechProviderFailure(failure.failureType());
        } catch (RuntimeException unexpected) {
            return error(500, "internal_error", "The run voice could not be generated.");
        } finally {
            runVoiceSlots.release();
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
        private final BackendAccessPolicy accessPolicy;
        private final boolean individualAccess;

        public RemoteAccess(String bindAddress, String publicAuthority, String demoToken) {
            this(bindAddress, publicAuthority, BackendAccessPolicy.legacy(demoToken), false);
        }

        static RemoteAccess individual(String bindAddress, String publicAuthority, BackendAccessPolicy accessPolicy) {
            return new RemoteAccess(bindAddress, publicAuthority, accessPolicy, true);
        }

        private RemoteAccess(String bindAddress, String publicAuthority,
                             BackendAccessPolicy accessPolicy, boolean individualAccess) {
            this.bindAddress = numericIpv4(bindAddress);
            this.publicAuthority = publicAuthority(publicAuthority);
            this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
            this.individualAccess = individualAccess;
        }

        private Optional<BackendAccessPolicy.Principal> authenticate(HttpExchange exchange) {
            return accessPolicy.authenticate(exchange.getRequestHeaders().get("Authorization"));
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

    private record OwnedRequestId(String principalId, String requestId) {
        @Override public String toString() { return "OwnedRequestId[redacted]"; }
    }

    private record RequestWork(
            ApiRequest request,
            CompletableFuture<EndpointResponse> response
    ) {
    }

    private record SongPreparationWork(ReflectionContract.SongPreparationRequest request, String runId, long admittedNanos,
                                       CompletableFuture<EndpointResponse> response) {
        @Override public String toString() { return "SongPreparationWork[identity=redacted]"; }
    }

    private record SnippetCapabilities(boolean snippets, boolean fallbacks) { }

    private record EndpointResponse(int status, byte[] body, String contentType, boolean songStudyAcknowledged,
                                   SnippetCapabilities snippetCapabilities) {
        EndpointResponse(int status, byte[] body, String contentType, boolean songStudyAcknowledged) {
            this(status, body, contentType, songStudyAcknowledged, null);
        }

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
