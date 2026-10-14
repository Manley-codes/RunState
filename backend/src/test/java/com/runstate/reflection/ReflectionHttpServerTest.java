package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;
import com.runstate.reflection.ReflectionContract.DistanceUnit;
import com.runstate.reflection.ReflectionContract.MetricSource;
import com.runstate.reflection.ReflectionContract.MusicContext;
import com.runstate.reflection.ReflectionContract.MusicProvenance;
import com.runstate.reflection.ReflectionContract.MusicState;
import com.runstate.reflection.ReflectionContract.PreRunEnergy;
import com.runstate.reflection.ReflectionContract.RunMetricsEvidence;
import com.runstate.reflection.ReflectionContract.TelemetryCoverage;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionHttpServerTest {

    private static final String REQUEST_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String RUN_ID = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";
    private static final String OTHER_RUN_ID = "cccccccc-cccc-4ccc-8ccc-cccccccccccc";
    private static final String SECOND_REQUEST_ID = "dddddddd-dddd-4ddd-8ddd-dddddddddddd";
    private static final String THIRD_REQUEST_ID = "eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee";

    private static final String SPENT = "  Exact spent text.  ";
    private static final String FEELING_GOOD = "Feeling good line one.\nLine two.\tStill exact.";
    private static final String POWERED_UP = "Powered up — exact punctuation.";
    private static final String NO_SELECTION = "No selection stays unknown.";
    private static final String DEMO_HOST = "demo.runstate.example";
    private static final String DUMMY_DEMO_TOKEN = "test-demo-token-0123456789-ABCDEFGHIJKLMNOPQ";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Test
    void localFakeSmokePreservesIdentifiersEvidenceAndExactCandidateText() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<CompletedRunEvidence> capturedEvidence = new AtomicReference<>();
        CandidateReflections fixed = exactCandidates();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            capturedEvidence.set(evidence);
            return fixed;
        };

        try (ReflectionHttpServer server = start(fake)) {
            HttpResponse<String> response = post(server, validRequest());
            JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
            JsonObject candidates = responseJson.getAsJsonObject("candidates");

            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertTrue(server.address().getAddress().isLoopbackAddress()),
                    () -> assertEquals("127.0.0.1", server.address().getAddress().getHostAddress()),
                    () -> assertEquals(REQUEST_ID, responseJson.get("requestId").getAsString()),
                    () -> assertEquals(RUN_ID, responseJson.get("runId").getAsString()),
                    () -> assertEquals("FAKE", responseJson.get("generationMode").getAsString()),
                    () -> assertEquals(SPENT, candidates.get("spent").getAsString()),
                    () -> assertEquals(FEELING_GOOD, candidates.get("feeling_good").getAsString()),
                    () -> assertEquals(POWERED_UP, candidates.get("powered_up").getAsString()),
                    () -> assertEquals(NO_SELECTION, candidates.get("no_selection").getAsString()),
                    () -> assertEquals(knownEvidence(), capturedEvidence.get()),
                    () -> assertEquals(1, calls.get())
            );
        }
    }

    @Test
    void successReportsConfiguredModeWithoutInspectingOrChangingCandidateText() throws Exception {
        CandidateReflections fixed = new CandidateReflections(
                "  [FAKE] This prefix is text, not provider configuration.  ",
                FEELING_GOOD,
                POWERED_UP,
                NO_SELECTION
        );
        for (ReflectionHttpServer.GenerationMode mode : ReflectionHttpServer.GenerationMode.values()) {
            // ANTHROPIC configuration is simulated here; the test makes no external call.
            try (ReflectionHttpServer server = ReflectionHttpServer.create(evidence -> fixed, 0, mode)) {
                server.start();
                HttpResponse<String> response = post(server, validRequest());
                JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonObject candidates = responseJson.getAsJsonObject("candidates");

                assertAll(
                        () -> assertEquals(200, response.statusCode()),
                        () -> assertEquals(mode.name(), responseJson.get("generationMode").getAsString()),
                        () -> assertEquals(fixed.spent(), candidates.get("spent").getAsString()),
                        () -> assertEquals(fixed.feeling_good(), candidates.get("feeling_good").getAsString()),
                        () -> assertEquals(fixed.powered_up(), candidates.get("powered_up").getAsString()),
                        () -> assertEquals(fixed.no_selection(), candidates.get("no_selection").getAsString())
                );
                assertEquals(response.body(), post(server, validRequest()).body());
            }
        }
    }

    @Test
    void partialMusicProvenanceReachesProviderWithoutRewritingReportedIdentity() throws Exception {
        AtomicReference<CompletedRunEvidence> captured = new AtomicReference<>();
        JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
        JsonObject music = request.getAsJsonObject("evidence").getAsJsonObject("musicContext");
        music.addProperty("trackTitle", "  Exact reported title  ");
        music.addProperty("artistName", "Exact reported artist");
        music.remove("artistDescription");
        music.addProperty("provenance", "MEDIA_SESSION_PARTIAL");

        try (ReflectionHttpServer server = start(evidence -> {
            captured.set(evidence);
            return exactCandidates();
        })) {
            HttpResponse<String> response = post(server, request.toString());
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals(new MusicContext(MusicState.MUSIC,
                            "  Exact reported title  ", "Exact reported artist", null,
                            MusicProvenance.MEDIA_SESSION_PARTIAL), captured.get().musicContext())
            );
        }
    }

    @Test
    void optionalProvenanceKeepsLegacyControlledAndUnknownRequestsCompatible() throws Exception {
        JsonObject legacy = JsonParser.parseString(validRequest()).getAsJsonObject();
        assertEquals(MusicProvenance.CONTROLLED, ReflectionApiJson.parseRequest(
                legacy.toString().getBytes(StandardCharsets.UTF_8)).evidence().musicContext().provenance());
        legacy.getAsJsonObject("evidence").getAsJsonObject("musicContext").add("provenance", null);
        assertEquals(knownEvidence(), ReflectionApiJson.parseRequest(
                legacy.toString().getBytes(StandardCharsets.UTF_8)).evidence());

        for (String state : List.of("UNKNOWN", "NO_MUSIC")) {
            JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
            JsonObject music = new JsonObject();
            music.addProperty("state", state);
            music.add("provenance", null);
            request.getAsJsonObject("evidence").add("musicContext", music);
            MusicContext parsed = ReflectionApiJson.parseRequest(
                    request.toString().getBytes(StandardCharsets.UTF_8)).evidence().musicContext();
            assertAll(
                    () -> assertEquals(MusicState.valueOf(state), parsed.state()),
                    () -> assertNull(parsed.provenance()),
                    () -> assertNull(parsed.trackTitle()),
                    () -> assertNull(parsed.artistName()),
                    () -> assertNull(parsed.artistDescription())
            );
        }
    }

    @Test
    void rejectsInvalidMusicProvenanceAndPartialContextBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        })) {
            List<String> invalidContexts = List.of(
                    "{\"state\":\"MUSIC\",\"provenance\":\"UNSUPPORTED\"}",
                    "{\"state\":\"MUSIC\",\"provenance\":true}",
                    "{\"state\":\"UNKNOWN\",\"provenance\":\"MEDIA_SESSION_PARTIAL\"}",
                    "{\"state\":\"NO_MUSIC\",\"provenance\":\"CONTROLLED\"}",
                    "{\"provenance\":\"CONTROLLED\"}",
                    "{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":\"Title\"}",
                    "{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":\" \" ,\"artistName\":\"Artist\"}",
                    "{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":\"Title\",\"artistName\":\"Artist\",\"artistDescription\":\"Inferred persona\"}",
                    "{\"state\":\"MUSIC\",\"provenance\":\"MEDIA_SESSION_PARTIAL\",\"trackTitle\":\"Title\",\"artistName\":\"Artist\",\"lyrics\":\"Extra listening data\"}"
            );
            for (String invalidContext : invalidContexts) {
                JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                request.getAsJsonObject("evidence").add("musicContext", JsonParser.parseString(invalidContext));
                assertError(post(server, request.toString()), 400, "invalid_request");
            }
            assertEquals(0, calls.get());
        }
    }

    @Test
    void allowsConfiguredLocalHostNamesAtTheActualPortWithoutOrigin() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            List<String> hostNames = List.of("127.0.0.1", "localhost", "10.0.2.2");
            List<String> requestIds = List.of(
                    REQUEST_ID,
                    SECOND_REQUEST_ID,
                    THIRD_REQUEST_ID
            );

            for (int index = 0; index < hostNames.size(); index++) {
                String requestId = requestIds.get(index);
                RawHttpResponse response = rawPost(
                        server,
                        List.of(hostHeader(server, hostNames.get(index))),
                        validRequest().replace(REQUEST_ID, requestId)
                );
                JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();

                assertAll(
                        () -> assertEquals(200, response.status()),
                        () -> assertEquals(
                                requestId,
                                responseJson.get("requestId").getAsString()
                        ),
                        () -> assertEquals(RUN_ID, responseJson.get("runId").getAsString())
                );
            }

            assertEquals(3, calls.get());
        }
    }

    @Test
    void rejectsInvalidHostOrAnyOriginBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            String allowedHost = hostHeader(server, "127.0.0.1");
            String secondAllowedHost = hostHeader(server, "localhost");
            int wrongPort = server.address().getPort() == 65_535
                    ? 65_534
                    : server.address().getPort() + 1;
            List<List<String>> rejectedHeaders = List.of(
                    List.of(),
                    List.of(allowedHost, secondAllowedHost),
                    List.of("Host: localhost"),
                    List.of("Host: localhost:not-a-port"),
                    List.of("Host: example.test:" + server.address().getPort()),
                    List.of("Host: 127.0.0.1:" + wrongPort),
                    List.of(allowedHost, "Origin: null"),
                    List.of(allowedHost, "Origin:"),
                    List.of(allowedHost, "Origin: http://localhost:" + server.address().getPort())
            );

            for (List<String> headers : rejectedHeaders) {
                assertError(rawPost(server, headers, validRequest()), 403, "request_rejected");
            }

            assertEquals(0, calls.get());
        }
    }

    @Test
    void rejectsMalformedDuplicateOversizedAndInvalidRequestsBeforeCallingProvider()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            HttpResponse<String> malformed = post(server, "{");
            HttpResponse<String> duplicate = post(
                    server,
                    validRequest().replaceFirst(
                            "\\{",
                            "{\\\"requestId\\\":\\\"" + REQUEST_ID + "\\\","
                    )
            );
            HttpResponse<String> invalidId = post(
                    server,
                    validRequest().replace(REQUEST_ID, REQUEST_ID.toUpperCase())
            );

            JsonObject contradictoryEvidence = JsonParser.parseString(validRequest()).getAsJsonObject();
            contradictoryEvidence.getAsJsonObject("evidence")
                    .addProperty("finishEpochMillis", 1_779_999_999_999L);
            HttpResponse<String> invalidEvidence = post(server, contradictoryEvidence.toString());

            JsonObject unsupportedField = JsonParser.parseString(validRequest()).getAsJsonObject();
            unsupportedField.addProperty("prompt", "Do not accept transport extras");
            HttpResponse<String> extraField = post(server, unsupportedField.toString());

            String oversizedJson = "{\\\"padding\\\":\\\""
                    + "x".repeat(ReflectionHttpServer.MAX_REQUEST_BYTES)
                    + "\\\"}";
            HttpResponse<String> oversized = post(server, oversizedJson);

            assertAll(
                    () -> assertError(malformed, 400, "invalid_json"),
                    () -> assertError(duplicate, 400, "invalid_json"),
                    () -> assertError(invalidId, 400, "invalid_request"),
                    () -> assertError(invalidEvidence, 400, "invalid_request"),
                    () -> assertError(extraField, 400, "invalid_request"),
                    () -> assertError(oversized, 413, "request_too_large"),
                    () -> assertEquals(0, calls.get())
            );
        }
    }

    @Test
    void rejectsUnsupportedMethodsAndMediaTypes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            HttpResponse<String> get = send(
                    server,
                    "GET",
                    HttpRequest.BodyPublishers.noBody(),
                    null
            );
            HttpResponse<String> wrongType = send(
                    server,
                    "POST",
                    HttpRequest.BodyPublishers.ofString(validRequest(), StandardCharsets.UTF_8),
                    "text/plain"
            );

            assertAll(
                    () -> assertError(get, 405, "method_not_allowed"),
                    () -> assertEquals("POST", get.headers().firstValue("Allow").orElseThrow()),
                    () -> assertError(wrongType, 415, "unsupported_media_type"),
                    () -> assertEquals(0, calls.get())
            );
        }
    }

    @Test
    void concurrentDuplicateRequestsShareOneProviderCallAndOneExactResponse() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            providerEntered.countDown();
            try {
                if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                    throw new ReflectionProviderException(
                            FailureType.TIMEOUT,
                            "Fake provider release timed out."
                    );
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new ReflectionProviderException(
                        FailureType.TRANSPORT,
                        "Fake provider was interrupted.",
                        interrupted
                );
            }
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            CompletableFuture<HttpResponse<String>> first = postAsync(server, validRequest());
            assertTrue(providerEntered.await(2, TimeUnit.SECONDS));
            CompletableFuture<HttpResponse<String>> second = postAsync(server, validRequest());
            Thread.sleep(150L);
            releaseProvider.countDown();

            HttpResponse<String> firstResponse = first.get(5, TimeUnit.SECONDS);
            HttpResponse<String> secondResponse = second.get(5, TimeUnit.SECONDS);

            assertAll(
                    () -> assertEquals(200, firstResponse.statusCode()),
                    () -> assertEquals(firstResponse.body(), secondResponse.body()),
                    () -> assertEquals(1, calls.get())
            );
        } finally {
            releaseProvider.countDown();
        }
    }

    @Test
    void reusedRequestIdWithDifferentEvidenceReturnsConflictWithoutAnotherProviderCall()
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider fake = evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        };

        try (ReflectionHttpServer server = start(fake)) {
            HttpResponse<String> first = post(server, validRequest());
            HttpResponse<String> conflict = post(
                    server,
                    validRequest().replace(RUN_ID, OTHER_RUN_ID)
            );

            assertAll(
                    () -> assertEquals(200, first.statusCode()),
                    () -> assertError(conflict, 409, "request_id_conflict"),
                    () -> assertEquals(1, calls.get())
            );
        }
    }

    @Test
    void providerFailuresReturnStableSafeErrors() throws Exception {
        List<FailureCase> failures = List.of(
                new FailureCase(FailureType.TIMEOUT, 504, "provider_timeout"),
                new FailureCase(FailureType.TRANSPORT, 503, "provider_unavailable"),
                new FailureCase(FailureType.CONFIGURATION, 503, "provider_unavailable"),
                new FailureCase(FailureType.PROVIDER_ERROR, 502, "provider_failure"),
                new FailureCase(FailureType.REFUSAL, 502, "provider_failure"),
                new FailureCase(FailureType.TRUNCATED, 502, "provider_failure"),
                new FailureCase(FailureType.INVALID_RESPONSE, 502, "provider_failure")
        );

        for (FailureCase failure : failures) {
            ReflectionProvider fake = evidence -> {
                throw new ReflectionProviderException(
                        failure.type(),
                        "secret-api-key raw prompt raw provider response raw request body"
                );
            };

            try (ReflectionHttpServer server = start(fake)) {
                HttpResponse<String> response = post(server, validRequest());
                assertError(response, failure.status(), failure.code());
                assertAll(
                        () -> assertFalse(response.body().contains("secret-api-key")),
                        () -> assertFalse(response.body().contains("raw prompt")),
                        () -> assertFalse(response.body().contains(REQUEST_ID)),
                        () -> assertFalse(response.body().contains(RUN_ID))
                );
            }
        }
    }

    @Test
    void remoteModeRequiresDeliberateOptionsAndValidatedBackendOnlyConfiguration() {
        assertAll(
                () -> assertEquals(new ReflectionBackendMain.LaunchOptions(false, false),
                        ReflectionBackendMain.parseOptions(new String[]{})),
                () -> assertEquals(new ReflectionBackendMain.LaunchOptions(true, false),
                        ReflectionBackendMain.parseOptions(new String[]{"--fake"})),
                () -> assertEquals(new ReflectionBackendMain.LaunchOptions(false, true),
                        ReflectionBackendMain.parseOptions(new String[]{"--remote"})),
                () -> assertEquals(new ReflectionBackendMain.LaunchOptions(true, true),
                        ReflectionBackendMain.parseOptions(new String[]{"--remote", "--fake"})),
                () -> assertEquals(8787, ReflectionBackendMain.configuredPort(Map.of("PORT", "9000"))),
                () -> assertEquals(9000, ReflectionBackendMain.configuredPort(Map.of("PORT", "9000"), true)),
                () -> assertEquals(8788, ReflectionBackendMain.configuredPort(
                        Map.of("PORT", "9000", "RUNSTATE_REFLECTION_PORT", "8788"), true)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> ReflectionBackendMain.configuredRemoteAccess(Map.of())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReflectionHttpServer.RemoteAccess("0.0.0.0", DEMO_HOST, "weak")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReflectionHttpServer.RemoteAccess("localhost", DEMO_HOST, DUMMY_DEMO_TOKEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReflectionHttpServer.RemoteAccess("999.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReflectionHttpServer.RemoteAccess("127.0.0.1", "https://" + DEMO_HOST,
                                DUMMY_DEMO_TOKEN)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ReflectionHttpServer.RemoteAccess("127.0.0.1", "127.0.0.1:443",
                                DUMMY_DEMO_TOKEN)),
                () -> assertFalse(ReflectionBackendMain.configuredRemoteAccess(Map.of(
                        "RUNSTATE_REFLECTION_PUBLIC_HOST", DEMO_HOST,
                        "RUNSTATE_DEMO_TOKEN", DUMMY_DEMO_TOKEN)).toString().contains(DUMMY_DEMO_TOKEN))
        );
        IllegalArgumentException invalidArgument = assertThrows(IllegalArgumentException.class,
                () -> ReflectionBackendMain.parseOptions(new String[]{DUMMY_DEMO_TOKEN}));
        assertFalse(invalidArgument.getMessage().contains(DUMMY_DEMO_TOKEN));
    }

    @Test
    void remoteAuthenticationPrecedesJsonAndForwardedHeadersCannotAuthorizeRequests() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        }, 2, 8)) {
            List<List<String>> invalidAuthorization = List.of(
                    List.of("Host: " + DEMO_HOST),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer incorrect"),
                    List.of("Host: " + DEMO_HOST, "Authorization:"),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN,
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: " + DEMO_HOST, "X-Forwarded-Proto: https",
                            "X-Forwarded-Authorization: Bearer " + DUMMY_DEMO_TOKEN)
            );
            for (List<String> headers : invalidAuthorization) {
                RawHttpResponse response = rawPost(server, headers, "{");
                assertError(response, 401, "unauthorized");
                assertFalse(response.body().contains(DUMMY_DEMO_TOKEN));
                assertFalse(response.body().contains("incorrect"));
            }
            assertEquals(0, calls.get());
        }
    }

    @Test
    void remoteHostOriginAndStrictBodyGuardsRemainMandatoryWithAValidToken() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        }, 2, 8)) {
            List<List<String>> rejected = List.of(
                    List.of("Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: localhost:" + server.address().getPort(),
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "X-Forwarded-Host: " + DEMO_HOST),
                    List.of("Host: " + DEMO_HOST + ":8443", "Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: " + DEMO_HOST, "Host: " + DEMO_HOST,
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Origin: null"),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Origin:")
            );
            for (List<String> headers : rejected) {
                assertError(rawPost(server, headers, validRequest()), 403, "request_rejected");
            }
            assertError(rawPost(server, remoteHeaders(), "{"), 400, "invalid_json");
            assertError(rawPost(server, remoteHeaders(), validRequest().replace(
                    REQUEST_ID, REQUEST_ID.toUpperCase())), 400, "invalid_request");
            assertError(rawPost(server, remoteHeaders(), "x".repeat(ReflectionHttpServer.MAX_REQUEST_BYTES + 1)),
                    413, "request_too_large");
            assertEquals(0, calls.get());
        }
    }

    @Test
    void authenticatedRemoteResponsesRetainExactTextAndConfiguredMode() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        }, 2, 8)) {
            RawHttpResponse first = rawPost(server, remoteHeaders(), validRequest());
            RawHttpResponse duplicate = rawPost(server, List.of("Host: " + DEMO_HOST.toUpperCase() + ":443",
                    "Authorization: Bearer " + DUMMY_DEMO_TOKEN), validRequest());
            JsonObject response = JsonParser.parseString(first.body()).getAsJsonObject();
            assertAll(
                    () -> assertEquals(200, first.status()),
                    () -> assertEquals(first, duplicate),
                    () -> assertEquals("FAKE", response.get("generationMode").getAsString()),
                    () -> assertEquals(SPENT, response.getAsJsonObject("candidates").get("spent").getAsString()),
                    () -> assertEquals(FEELING_GOOD, response.getAsJsonObject("candidates").get("feeling_good").getAsString()),
                    () -> assertEquals(1, calls.get())
            );
        }
    }

    @Test
    void providerAdmissionIsBoundedWhileIdenticalRequestsStillShareOneCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ReflectionProvider fake = evidence -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Test provider interrupted");
                }
            }
            return exactCandidates();
        };
        try (ReflectionHttpServer server = startRemote(fake, 1, 4)) {
            CompletableFuture<RawHttpResponse> first = rawPostAsync(server, remoteHeaders(), validRequest());
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<RawHttpResponse> duplicate = rawPostAsync(server, remoteHeaders(), validRequest());
            String secondRequest = validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID);
            assertError(rawPost(server, remoteHeaders(), secondRequest), 503, "server_busy");
            assertEquals(1, calls.get());
            release.countDown();
            RawHttpResponse firstResponse = first.get(5, TimeUnit.SECONDS);
            assertEquals(200, firstResponse.status());
            assertEquals(firstResponse, duplicate.get(5, TimeUnit.SECONDS));
            // A busy rejection did not consume/cache this ID: the same request can be admitted later.
            assertEquals(200, rawPost(server, remoteHeaders(), secondRequest).status());
            assertEquals(2, calls.get());
        } finally {
            release.countDown();
        }
    }

    @Test
    void fullCacheRejectsNewIdsWithoutEvictingOriginalSuccessOrFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            if (calls.incrementAndGet() == 2) {
                throw new ReflectionProviderException(FailureType.TIMEOUT, DUMMY_DEMO_TOKEN);
            }
            return exactCandidates();
        }, 1, 2)) {
            RawHttpResponse success = rawPost(server, remoteHeaders(), validRequest());
            String failedRequest = validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID);
            RawHttpResponse failure = rawPost(server, remoteHeaders(), failedRequest);
            assertError(failure, 504, "provider_timeout");
            assertError(rawPost(server, remoteHeaders(), validRequest().replace(REQUEST_ID, THIRD_REQUEST_ID)),
                    503, "server_capacity");
            assertEquals(success, rawPost(server, remoteHeaders(), validRequest()));
            assertEquals(failure, rawPost(server, remoteHeaders(), failedRequest));
            assertError(rawPost(server, remoteHeaders(), validRequest().replace(RUN_ID, OTHER_RUN_ID)),
                    409, "request_id_conflict");
            assertEquals(2, calls.get());
            assertFalse(failure.body().contains(DUMMY_DEMO_TOKEN));
        }
    }

    @Test
    void oversizedCandidateResponseBecomesSafeCachedProviderFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return new CandidateReflections("x".repeat(ReflectionHttpServer.MAX_RESPONSE_BYTES),
                    FEELING_GOOD, POWERED_UP, NO_SELECTION);
        }, 1, 2)) {
            RawHttpResponse response = rawPost(server, remoteHeaders(), validRequest());
            assertError(response, 502, "provider_failure");
            assertEquals(response, rawPost(server, remoteHeaders(), validRequest()));
            assertTrue(response.body().getBytes(StandardCharsets.UTF_8).length < ReflectionHttpServer.MAX_RESPONSE_BYTES);
            assertEquals(1, calls.get());
        }
    }

    @Test
    void remoteHealthIsLivenessOnlyAndRetainsHostOriginGuards() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        }, 1, 2)) {
            RawHttpResponse health = rawRequest(server, "GET", ReflectionHttpServer.HEALTH_PATH,
                    List.of("Host: " + DEMO_HOST), "");
            assertEquals(200, health.status());
            assertEquals("{\"status\":\"ok\"}", health.body());
            assertError(rawRequest(server, "GET", ReflectionHttpServer.HEALTH_PATH,
                    List.of("Host: " + DEMO_HOST, "Origin: null"), ""), 403, "request_rejected");
            assertError(rawRequest(server, "GET", ReflectionHttpServer.HEALTH_PATH,
                    List.of("Host: foreign.example"), ""), 403, "request_rejected");
            assertEquals(0, calls.get());
        }
    }

    private ReflectionHttpServer startRemote(ReflectionProvider provider, int providerCalls, int cachedRequests)
            throws Exception {
        // Remote admission is exercised on loopback. No public port or provider is used.
        ReflectionHttpServer server = ReflectionHttpServer.createRemote(provider, 0,
                ReflectionHttpServer.GenerationMode.FAKE,
                new ReflectionHttpServer.RemoteAccess("127.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN),
                providerCalls, cachedRequests);
        server.start();
        return server;
    }

    private static List<String> remoteHeaders() {
        return List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN);
    }

    private static CompletableFuture<RawHttpResponse> rawPostAsync(
            ReflectionHttpServer server, List<String> headers, String body
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return rawPost(server, headers, body);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        });
    }

    private ReflectionHttpServer start(ReflectionProvider provider) throws Exception {
        ReflectionHttpServer server = ReflectionHttpServer.create(provider, 0);
        server.start();
        return server;
    }

    private HttpResponse<String> post(ReflectionHttpServer server, String body) throws Exception {
        return send(
                server,
                "POST",
                HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8),
                "application/json"
        );
    }

    private static RawHttpResponse rawPost(
            ReflectionHttpServer server,
            List<String> requestHeaders,
            String body
    ) throws Exception {
        return rawRequest(server, "POST", ReflectionHttpServer.CANDIDATES_PATH, requestHeaders, body);
    }

    private static RawHttpResponse rawRequest(
            ReflectionHttpServer server,
            String method,
            String path,
            List<String> requestHeaders,
            String body
    ) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = String.join("\r\n", requestHeaders);
        if (!headers.isEmpty()) {
            headers += "\r\n";
        }
        String requestHead = method + " " + path + " HTTP/1.1\r\n"
                + headers
                + "Content-Type: application/json\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n"
                + "\r\n";

        String rawResponse;
        try (Socket socket = new Socket()) {
            socket.connect(server.address(), 2_000);
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(requestHead.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bodyBytes);
            socket.getOutputStream().flush();
            rawResponse = new String(
                    socket.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }

        int statusLineEnd = rawResponse.indexOf("\r\n");
        int bodyStart = rawResponse.indexOf("\r\n\r\n");
        if (statusLineEnd < 0 || bodyStart < 0) {
            throw new AssertionError("Server returned an incomplete HTTP response");
        }

        String[] statusParts = rawResponse.substring(0, statusLineEnd).split(" ", 3);
        return new RawHttpResponse(
                Integer.parseInt(statusParts[1]),
                rawResponse.substring(bodyStart + 4)
        );
    }

    private static String hostHeader(ReflectionHttpServer server, String hostName) {
        return "Host: " + hostName + ":" + server.address().getPort();
    }

    private CompletableFuture<HttpResponse<String>> postAsync(
            ReflectionHttpServer server,
            String body
    ) {
        HttpRequest request = request(
                server,
                "POST",
                HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8),
                "application/json"
        );
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> send(
            ReflectionHttpServer server,
            String method,
            HttpRequest.BodyPublisher body,
            String contentType
    ) throws Exception {
        return client.send(
                request(server, method, body, contentType),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
    }

    private static HttpRequest request(
            ReflectionHttpServer server,
            String method,
            HttpRequest.BodyPublisher body,
            String contentType
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint(server))
                .timeout(Duration.ofSeconds(5))
                .method(method, body);
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        return builder.build();
    }

    private static URI endpoint(ReflectionHttpServer server) {
        return URI.create(
                "http://127.0.0.1:" + server.address().getPort()
                        + ReflectionHttpServer.CANDIDATES_PATH
        );
    }

    private static void assertError(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedCode
    ) {
        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("error");
        assertAll(
                () -> assertEquals(expectedStatus, response.statusCode()),
                () -> assertEquals(expectedCode, error.get("code").getAsString()),
                () -> assertTrue(error.get("message").getAsString().length() > 0)
        );
    }

    private static void assertError(
            RawHttpResponse response,
            int expectedStatus,
            String expectedCode
    ) {
        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("error");
        assertAll(
                () -> assertEquals(expectedStatus, response.status()),
                () -> assertEquals(expectedCode, error.get("code").getAsString()),
                () -> assertTrue(error.get("message").getAsString().length() > 0)
        );
    }

    private static String validRequest() {
        JsonObject metrics = new JsonObject();
        metrics.addProperty("distanceMeters", 5_000.25);
        metrics.addProperty("source", "FIXTURE");
        metrics.addProperty("coverage", "COMPLETE");
        metrics.addProperty("displayUnit", "MILES");

        JsonObject music = new JsonObject();
        music.addProperty("state", "MUSIC");
        music.addProperty("trackTitle", "Controlled track");
        music.addProperty("artistName", "Controlled artist");
        music.addProperty("artistDescription", "Controlled artist description");

        JsonObject evidence = new JsonObject();
        evidence.addProperty("officialStartEpochMillis", 1_780_000_000_000L);
        evidence.addProperty("finishEpochMillis", 1_780_001_800_000L);
        evidence.addProperty("startTimezoneId", "America/Chicago");
        evidence.addProperty("activeDurationMillis", 1_650_000L);
        evidence.add("metrics", metrics);
        evidence.addProperty("preRunEnergy", "LOW");
        evidence.add("musicContext", music);

        JsonObject request = new JsonObject();
        request.addProperty("requestId", REQUEST_ID);
        request.addProperty("runId", RUN_ID);
        request.add("evidence", evidence);
        return request.toString();
    }

    private static CompletedRunEvidence knownEvidence() {
        return new CompletedRunEvidence(
                1_780_000_000_000L,
                1_780_001_800_000L,
                "America/Chicago",
                1_650_000L,
                new RunMetricsEvidence(
                        5_000.25,
                        MetricSource.FIXTURE,
                        TelemetryCoverage.COMPLETE,
                        DistanceUnit.MILES
                ),
                PreRunEnergy.LOW,
                new MusicContext(
                        MusicState.MUSIC,
                        "Controlled track",
                        "Controlled artist",
                        "Controlled artist description"
                )
        );
    }

    private static CandidateReflections exactCandidates() {
        return new CandidateReflections(SPENT, FEELING_GOOD, POWERED_UP, NO_SELECTION);
    }

    private record FailureCase(FailureType type, int status, String code) {
    }

    private record RawHttpResponse(int status, String body) {
    }
}
