package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runstate.reflection.MusixmatchSongAnalysis.Analysis;
import com.runstate.reflection.MusixmatchSongAnalysis.Transport;
import com.runstate.reflection.MusixmatchSongAnalysis.TransportRequest;
import com.runstate.reflection.MusixmatchSongAnalysis.TransportResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusixmatchSongAnalysisTest {
    private static final String DUMMY_KEY = "not-a-real-key+secret=value";

    @Test
    void usesDocumentedMatcherThenAnalysisWirePathsAndQueryAuthentication() {
        List<TransportRequest> requests = new ArrayList<>();
        MusixmatchSongAnalysis provider = provider(request -> {
            requests.add(request);
            return requests.size() == 1 ? matched("Sunday Best", "Surfaces") : analysisResponse();
        });

        Analysis result = provider.lookup("Sunday Best", "Surfaces").orElseThrow();

        assertAll(
                () -> assertEquals("Sunday Best", result.trackTitle()),
                () -> assertEquals("Surfaces", result.artistName()),
                () -> assertEquals("12345", result.providerTrackId()),
                () -> assertEquals(List.of("everyday optimism", "perseverance"), result.themes()),
                () -> assertEquals(List.of("uplifting", "reflective"), result.moods()),
                () -> assertEquals(2, requests.size()),
                () -> assertEquals("https", requests.get(0).endpoint().getScheme()),
                () -> assertEquals("api.musixmatch.com", requests.get(0).endpoint().getHost()),
                () -> assertEquals("/ws/1.1/matcher.track.get", requests.get(0).endpoint().getPath()),
                () -> assertEquals(Map.of("q_track", "Sunday Best", "q_artist", "Surfaces", "apikey", DUMMY_KEY),
                        query(requests.get(0))),
                () -> assertEquals("/ws/1.1/track.lyrics.analysis.get", requests.get(1).endpoint().getPath()),
                () -> assertEquals(Map.of("track_id", "12345", "apikey", DUMMY_KEY), query(requests.get(1))),
                () -> assertEquals(MusixmatchSongAnalysis.REQUEST_TIMEOUT, requests.get(0).timeout()),
                () -> assertFalse(requests.get(0).toString().contains(DUMMY_KEY)),
                () -> assertFalse(provider.toString().contains(DUMMY_KEY)),
                () -> assertFalse(result.toString().contains("DISCARDED")),
                () -> assertThrows(UnsupportedOperationException.class, () -> result.themes().add("new"))
        );
    }

    @Test
    void missingOrBlankEnvironmentDisablesWithoutNeedingAnAccount() {
        assertTrue(MusixmatchSongAnalysis.fromEnvironment(Map.of()).isEmpty());
        assertTrue(MusixmatchSongAnalysis.fromEnvironment(Map.of("MUSIXMATCH_API_KEY", " \t")).isEmpty());
        assertTrue(MusixmatchSongAnalysis.fromEnvironment(Map.of("MUSIXMATCH_API_KEY", "test-key")).isPresent());
    }

    @Test
    void rejectsUnsafeConfigurationWithoutEchoingItsContents() {
        for (String key : List.of("secret\r\ninvalid", "secret key", "secret-" + "x".repeat(512))) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> MusixmatchSongAnalysis.fromEnvironment(Map.of("MUSIXMATCH_API_KEY", key)));
            assertFalse(failure.toString().contains("secret"));
            assertEquals(null, failure.getCause());
        }
    }

    @Test
    void toleratesCaseAndSpacingWithoutDroppingRecordingVersion() {
        AtomicInteger calls = new AtomicInteger();
        MusixmatchSongAnalysis provider = provider(request -> calls.incrementAndGet() == 1
                ? matched("  Sunday  Best ", "SURFACES") : analysisResponse());
        assertTrue(provider.lookup("sunday best", "surfaces").isPresent());
        assertEquals(2, calls.get());
    }

    @Test
    void refusesWrongArtistOrAlternateRecordingBeforeAnalysisRequest() {
        for (String[] identity : List.of(
                new String[]{"Sunday Best (Live)", "Surfaces"},
                new String[]{"Sunday Best - Remix", "Surfaces"},
                new String[]{"Sunday Best", "Another Artist"},
                new String[]{"Another Song", "Surfaces"})) {
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> {
                calls.incrementAndGet();
                return matched(identity[0], identity[1]);
            });
            assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void rejectsInvalidLocalInputsBeforeAnyRequest() {
        AtomicInteger calls = new AtomicInteger();
        MusixmatchSongAnalysis provider = provider(request -> {
            calls.incrementAndGet();
            return analysisResponse();
        });
        for (String value : Arrays.asList(null, "", "   ", "song\nother", "x".repeat(201))) {
            assertTrue(provider.lookup(value, "artist").isEmpty());
            assertTrue(provider.lookup("song", value).isEmpty());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void doesNotRetryHttpFailuresRedirectsOrNestedProviderStatusFailures() {
        for (TransportResponse unavailable : List.of(
                response(401, "{}"), response(403, "{}"), response(429, "{}"), response(503, "{}"),
                response(302, "{}"), envelope(401, new JsonObject()), envelope(404, new JsonObject()),
                envelope(402, new JsonObject()))) {
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> {
                calls.incrementAndGet();
                return unavailable;
            });
            assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void analysisFailureAlsoFailsOpenWithoutRetry() {
        AtomicInteger calls = new AtomicInteger();
        MusixmatchSongAnalysis provider = provider(request -> calls.incrementAndGet() == 1
                ? matched("Sunday Best", "Surfaces") : envelope(404, new JsonObject()));
        assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
        assertEquals(2, calls.get());
    }

    @Test
    void connectionTimeoutAndPotentiallyCredentialBearingExceptionsDoNotEscape() {
        for (IOException failure : List.of(new HttpTimeoutException("secret URL"),
                new IOException("https://api.musixmatch.com?apikey=" + DUMMY_KEY))) {
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> {
                calls.incrementAndGet();
                throw failure;
            });
            assertEquals(Optional.empty(), provider.lookup("Sunday Best", "Surfaces"));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void restoresThreadInterruptionInsteadOfContinuingRequests() {
        MusixmatchSongAnalysis provider = provider(request -> {
            throw new InterruptedException("secret URL");
        });
        try {
            assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void rejectsMalformedAmbiguousExcessiveOrInvalidUtf8Responses() {
        List<TransportResponse> invalid = new ArrayList<>();
        for (String raw : List.of("not-json", "[]", "{}", "{message:{}}", "{} {}",
                "{\"message\":{},\"message\":{}}", "{\"message\":{\"header\":{\"status_code\":\"200\"},\"body\":{}}}",
                "[".repeat(34) + "0" + "]".repeat(34))) {
            invalid.add(response(200, raw));
        }
        invalid.add(new TransportResponse(200, new byte[MusixmatchSongAnalysis.MAX_RESPONSE_BYTES + 1]));
        invalid.add(new TransportResponse(200, new byte[]{(byte) 0xc3, (byte) 0x28}));
        for (TransportResponse response : invalid) {
            assertTrue(provider(request -> response).lookup("Sunday Best", "Surfaces").isEmpty());
        }
    }

    @Test
    void rejectsMissingOrInvalidProviderTrackIdWithoutAnalysisRequest() {
        for (String invalidId : List.of("null", "\"12345\"", "-1", "0", "1.5", "1e40")) {
            JsonObject body = new JsonObject();
            JsonObject track = new JsonObject();
            track.addProperty("track_name", "Sunday Best");
            track.addProperty("artist_name", "Surfaces");
            track.add("track_id", com.google.gson.JsonParser.parseString(invalidId));
            body.add("track", track);
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> {
                calls.incrementAndGet();
                return envelope(200, body);
            });
            assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void requiresRealLabelArraysAndDoesNotUseMeaningOrQuotesAsFallback() {
        for (String raw : List.of("[]", "{}", "{\"meaning\":{\"explanation\":\"DISCARDED explanation\"}}",
                "{\"themes\":{\"main_themes\":\"optimism\"}}",
                "{\"themes\":{\"main_themes\":[\"optimism\"]}}",
                "{\"themes\":{\"main_themes\":[{\"quotes\":[\"DISCARDED quote\"]}]}}",
                "{\"moods\":{\"main_moods\":[{}]}}",
                "{\"moods\":{\"main_moods\":[\"line one\\nline two\"]}}",
                "{\"moods\":{\"main_moods\":[\"" + "x".repeat(121) + "\"]}}")) {
            JsonObject body = new JsonObject();
            body.add("analysis", com.google.gson.JsonParser.parseString(raw));
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> calls.incrementAndGet() == 1
                    ? matched("Sunday Best", "Surfaces") : envelope(200, body));
            assertTrue(provider.lookup("Sunday Best", "Surfaces").isEmpty());
        }
    }

    @Test
    void deduplicatesAndBoundsLabelsAndDiscardsOtherAnalysisData() {
        JsonObject analysis = validAnalysis();
        JsonArray moods = new JsonArray();
        moods.add("Uplifting");
        moods.add("uplifting");
        for (int index = 0; index < 20; index++) {
            moods.add("mood " + index);
        }
        analysis.getAsJsonObject("moods").add("main_moods", moods);
        JsonObject body = new JsonObject();
        body.add("analysis", analysis);
        AtomicInteger calls = new AtomicInteger();
        MusixmatchSongAnalysis provider = provider(request -> calls.incrementAndGet() == 1
                ? matched("Sunday Best", "Surfaces") : envelope(200, body));
        Analysis result = provider.lookup("Sunday Best", "Surfaces").orElseThrow();
        assertEquals(8, result.moods().size());
        assertEquals("Uplifting", result.moods().get(0));
        assertFalse(result.toString().contains("DISCARDED"));
    }

    @Test
    void allowsThemeOnlyOrMoodOnlyResultsWithoutInventingTheMissingLabels() {
        for (String remove : List.of("themes", "moods")) {
            JsonObject analysis = validAnalysis();
            analysis.remove(remove);
            JsonObject body = new JsonObject();
            body.add("analysis", analysis);
            AtomicInteger calls = new AtomicInteger();
            MusixmatchSongAnalysis provider = provider(request -> calls.incrementAndGet() == 1
                    ? matched("Sunday Best", "Surfaces") : envelope(200, body));
            Analysis result = provider.lookup("Sunday Best", "Surfaces").orElseThrow();
            assertEquals(remove.equals("themes"), result.themes().isEmpty());
            assertEquals(remove.equals("moods"), result.moods().isEmpty());
        }
    }

    private static MusixmatchSongAnalysis provider(Transport transport) {
        return new MusixmatchSongAnalysis(DUMMY_KEY, transport);
    }

    private static Map<String, String> query(TransportRequest request) {
        Map<String, String> query = new LinkedHashMap<>();
        for (String pair : request.endpoint().getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return query;
    }

    private static TransportResponse matched(String title, String artist) {
        JsonObject track = new JsonObject();
        track.addProperty("track_id", 12345);
        track.addProperty("track_name", title);
        track.addProperty("artist_name", artist);
        JsonObject body = new JsonObject();
        body.add("track", track);
        return envelope(200, body);
    }

    private static TransportResponse analysisResponse() {
        JsonObject body = new JsonObject();
        body.add("analysis", validAnalysis());
        return envelope(200, body);
    }

    private static JsonObject validAnalysis() {
        JsonArray themes = new JsonArray();
        for (String label : List.of("everyday optimism", "perseverance")) {
            JsonObject theme = new JsonObject();
            theme.addProperty("theme", label);
            JsonArray quotes = new JsonArray();
            quotes.add("DISCARDED fictional quote fixture");
            theme.add("quotes", quotes);
            themes.add(theme);
        }
        JsonObject themeContainer = new JsonObject();
        themeContainer.add("main_themes", themes);
        JsonArray moods = new JsonArray();
        moods.add("uplifting");
        moods.add("reflective");
        JsonObject moodContainer = new JsonObject();
        moodContainer.add("main_moods", moods);
        JsonObject meaning = new JsonObject();
        meaning.addProperty("explanation", "DISCARDED fictional explanation fixture");
        JsonObject analysis = new JsonObject();
        analysis.add("themes", themeContainer);
        analysis.add("moods", moodContainer);
        analysis.add("meaning", meaning);
        analysis.addProperty("entities", "DISCARDED fictional entity fixture");
        return analysis;
    }

    private static TransportResponse envelope(int status, JsonObject body) {
        JsonObject header = new JsonObject();
        header.addProperty("status_code", status);
        JsonObject message = new JsonObject();
        message.add("header", header);
        message.add("body", body);
        JsonObject envelope = new JsonObject();
        envelope.add("message", message);
        return response(200, envelope.toString());
    }

    private static TransportResponse response(int httpStatus, String body) {
        return new TransportResponse(httpStatus, body.getBytes(StandardCharsets.UTF_8));
    }
}
