package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;
import com.runstate.reflection.ReflectionContract.DistanceUnit;
import com.runstate.reflection.ReflectionContract.MetricSource;
import com.runstate.reflection.ReflectionContract.MusicContext;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String headers = String.join("\r\n", requestHeaders);
        if (!headers.isEmpty()) {
            headers += "\r\n";
        }
        String requestHead = "POST " + ReflectionHttpServer.CANDIDATES_PATH + " HTTP/1.1\r\n"
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
