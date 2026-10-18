package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.runstate.reflection.MusixmatchSongAnalysis.Analysis;
import com.runstate.reflection.MusixmatchSongAnalysis.EntityMaterial;
import com.runstate.reflection.MusixmatchSongAnalysis.SongMaterial;
import com.runstate.reflection.MusixmatchSongAnalysis.ThemeMaterial;
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
    void promotedLookupChecksKnownOriginalIdsAndDoesNotStripRecordingVersions() {
        Map<String, List<String>> originals = Map.of("460374491", List.of("Lovely Day", "Bill Withers"),
                "176269002", List.of("Stronger", "Kanye West"), "445073103", List.of("Good as Hell", "Lizzo"),
                "260345489", List.of("God's Plan", "Drake"));
        for (var entry : originals.entrySet()) {
            List<TransportRequest> calls = new ArrayList<>();
            var client = provider(request -> {
                calls.add(request);
                if (calls.size() == 1) {
                    assertEquals("/ws/1.1/track.get", request.endpoint().getPath());
                    assertEquals(entry.getKey(), query(request).get("track_id"));
                    JsonObject track = new JsonObject();
                    track.addProperty("track_id", Long.parseLong(entry.getKey()));
                    track.addProperty("track_name", entry.getValue().get(0));
                    track.addProperty("artist_name", entry.getValue().get(1));
                    JsonObject body = new JsonObject(); body.add("track", track);
                    return envelope(200, body);
                }
                return calls.size() == 2 ? analysisResponse(materialAnalysis()) : envelope(404, new JsonObject());
            });
            assertEquals(entry.getKey(), client.lookupReflectionMaterial(entry.getValue().get(0),
                    entry.getValue().get(1)).orElseThrow().providerTrackId());
            assertEquals(3, calls.size());
        }
        AtomicInteger count = new AtomicInteger();
        var client = provider(request -> {
            count.incrementAndGet();
            assertEquals("/ws/1.1/matcher.track.get", request.endpoint().getPath());
            return envelope(404, new JsonObject());
        });
        assertTrue(client.lookupReflectionMaterial("Stronger (Live)", "Kanye West").isEmpty());
        assertEquals(1, count.get());
    }

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
                () -> assertEquals("The song connects everyday optimism with continuing forward.",
                        result.meaningExplanation()),
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
    void rejectsMalformedLabelsAndDoesNotUseQuotesAsFallback() {
        for (String raw : List.of("[]", "{}", "{\"meaning\":{\"quotes\":[\"DISCARDED quote\"]}}",
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

    @Test
    void allowsMeaningOnlyContextFromTheSameMatchedAnalysisRequest() {
        JsonObject analysis = new JsonObject();
        JsonObject meaning = new JsonObject();
        meaning.addProperty("explanation", "  The song\n\tconnects  everyday optimism with continuing forward.  ");
        analysis.add("meaning", meaning);
        List<TransportRequest> requests = new ArrayList<>();
        MusixmatchSongAnalysis provider = provider(request -> {
            requests.add(request);
            JsonObject body = new JsonObject();
            body.add("analysis", analysis);
            return requests.size() == 1 ? matched("Sunday Best", "Surfaces") : envelope(200, body);
        });

        Analysis result = provider.lookup("Sunday Best", "Surfaces").orElseThrow();

        assertEquals(List.of(), result.themes());
        assertEquals(List.of(), result.moods());
        assertEquals("The song connects everyday optimism with continuing forward.", result.meaningExplanation());
        assertEquals(2, requests.size());
        assertEquals("/ws/1.1/track.lyrics.analysis.get", requests.get(1).endpoint().getPath());
    }

    @Test
    void removesPairedStraightCurlyAndNestedDoubleQuotedPassagesWithoutJoiningWords() {
        for (String explanation : List.of(
                "The \"DISCARDED quote\"song connects everyday optimism with continuing forward.",
                "The song \u201cDISCARDED quote\u201d connects everyday optimism with continuing forward.",
                "The song \u201cDISCARDED \"NESTED\" quote\u201d connects everyday optimism with continuing forward.",
                "The song \"DISCARDED \u201cNESTED\u201d quote\" connects everyday optimism with continuing forward.")) {
            Analysis result = new Analysis("Sunday Best", "Surfaces", "12345", List.of(), List.of(), explanation);
            assertEquals("The song connects everyday optimism with continuing forward.", result.meaningExplanation());
            assertFalse(result.toString().contains("DISCARDED"));
            assertFalse(result.toString().contains("NESTED"));
        }
    }

    @Test
    void keepsValidLabelsWhenMeaningIsMissingMalformedOversizedOrHasUnmatchedQuotes() {
        for (String meaningJson : List.of("null", "[]", "true", "\"wrong container\"", "{}",
                "{\"explanation\":null}", "{\"explanation\":123}", "{\"explanation\":[]}",
                "{\"explanation\":{}}", "{\"explanation\":\"   \\t\\n\"}",
                "{\"explanation\":\"text \\u0000 control\"}",
                "{\"explanation\":\"text \\u007f control\"}",
                "{\"explanation\":\"text \\\"unclosed\"}",
                "{\"explanation\":\"text \u201cunclosed\"}",
                "{\"explanation\":\"unmatched\u201d text\"}",
                "{\"explanation\":\"text \u201cmismatched\\\" text\"}",
                "{\"explanation\":\"\\\"quoted only\\\"\"}",
                "{\"explanation\":\"" + "a".repeat(1201) + "\"}")) {
            JsonObject analysis = validAnalysis();
            analysis.add("meaning", com.google.gson.JsonParser.parseString(meaningJson));
            AtomicInteger calls = new AtomicInteger();
            Analysis result = lookupAnalysis(analysis, calls).orElseThrow();
            assertEquals(null, result.meaningExplanation(), meaningJson);
            assertEquals(List.of("everyday optimism", "perseverance"), result.themes());
            assertEquals(List.of("uplifting", "reflective"), result.moods());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void preservesLabelOnlyBehaviorAndTheFiveArgumentConstructor() {
        JsonObject analysis = validAnalysis();
        analysis.remove("meaning");
        Analysis fromProvider = lookupAnalysis(analysis, new AtomicInteger()).orElseThrow();
        Analysis oldConstructor = new Analysis("Sunday Best", "Surfaces", "12345",
                List.of("everyday optimism", "perseverance"), List.of("uplifting", "reflective"));
        assertEquals(oldConstructor, fromProvider);
        assertEquals(null, oldConstructor.meaningExplanation());
    }

    @Test
    void acceptsTheExplanationLimitAndNeverCopiesLyricsQuoteOrEntityFields() {
        JsonObject analysis = validAnalysis();
        analysis.getAsJsonObject("meaning").addProperty("explanation", "a".repeat(1200));
        analysis.addProperty("lyrics", "DISCARDED lyric fixture");
        analysis.addProperty("snippet", "DISCARDED snippet fixture");
        analysis.addProperty("explanation", "DISCARDED root explanation fixture");
        AtomicInteger calls = new AtomicInteger();
        Analysis result = lookupAnalysis(analysis, calls).orElseThrow();
        assertEquals("a".repeat(1200), result.meaningExplanation());
        assertFalse(result.toString().contains("DISCARDED"));
        assertEquals(2, calls.get());
    }

    @Test
    void unavailableMeaningWithoutLabelsDoesNotInventSongContext() {
        JsonObject analysis = new JsonObject();
        JsonObject meaning = new JsonObject();
        meaning.addProperty("explanation", "\"DISCARDED quote only\"");
        analysis.add("meaning", meaning);
        assertTrue(lookupAnalysis(analysis, new AtomicInteger()).isEmpty());
    }

    @Test
    void explicitMaterialTrialRetainsBoundedSourcesAndRequestsSnippetOnlyAfterAnalysis() {
        List<TransportRequest> requests = new ArrayList<>();
        MusixmatchSongAnalysis provider = provider(request -> {
            requests.add(request);
            return switch (requests.size()) {
                case 1 -> materialMatch();
                case 2 -> analysisResponse(materialAnalysis());
                default -> snippetResponse("FICTIONAL snippet text", 0, 0);
            };
        });

        SongMaterial result = provider.lookupMaterial("Sunday Best", "Surfaces").orElseThrow();

        assertAll(
                () -> assertEquals("Sunday Best", result.trackTitle()),
                () -> assertEquals("Surfaces", result.artistName()),
                () -> assertEquals("12345", result.providerTrackId()),
                () -> assertEquals(List.of("Pop", "R&B/Soul"), result.genres()),
                () -> assertEquals(List.of("FICTIONAL supporting line", "FICTIONAL second line"),
                        result.themes().get(0).quotes()),
                () -> assertEquals("everyday optimism", result.themes().get(0).theme()),
                () -> assertEquals(List.of("uplifting", "reflective"), result.moods()),
                () -> assertEquals("The song uses \"FICTIONAL meaning quotation\" to express optimism.",
                        result.meaningExplanation()),
                () -> assertEquals(List.of(new EntityMaterial("FICTIONAL place",
                        "FICTIONAL model description", List.of("place"))), result.entities()),
                () -> assertEquals("FICTIONAL snippet text", result.snippetText()),
                () -> assertEquals(3, requests.size()),
                () -> assertEquals("/ws/1.1/track.snippet.get", requests.get(2).endpoint().getPath()),
                () -> assertEquals(Map.of("track_id", "12345", "apikey", DUMMY_KEY), query(requests.get(2))),
                () -> assertTrue(requests.stream().allMatch(request ->
                        request.timeout().equals(MusixmatchSongAnalysis.REQUEST_TIMEOUT))),
                () -> assertTrue(requests.stream().noneMatch(request ->
                        request.endpoint().getPath().equals("/ws/1.1/track.lyrics.get"))),
                () -> assertFalse(result.toString().contains("FICTIONAL")),
                () -> assertFalse(result.themes().get(0).toString().contains("FICTIONAL")),
                () -> assertFalse(result.entities().get(0).toString().contains("FICTIONAL"))
        );
        // Explicit whitelist records cannot carry the provider's matches, positions or URLs.
        assertEquals(List.of("name", "description", "categories"), Arrays.stream(EntityMaterial.class
                .getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test
    void explicitTrackIdTrialVerifiesIdentityThenReusesBoundedMaterialRequests() {
        List<TransportRequest> requests = new ArrayList<>();
        SongMaterial result = provider(request -> {
            requests.add(request);
            return switch (requests.size()) {
                case 1 -> materialMatch();
                case 2 -> analysisResponse(materialAnalysis());
                default -> snippetResponse("FICTIONAL snippet text", 0, 0);
            };
        }).lookupMaterialByTrackId("  SUNDAY   BEST ", " surfaces ", "12345").orElseThrow();

        assertAll(
                () -> assertEquals("Sunday Best", result.trackTitle()),
                () -> assertEquals("Surfaces", result.artistName()),
                () -> assertEquals("12345", result.providerTrackId()),
                () -> assertEquals(List.of("Pop", "R&B/Soul"), result.genres()),
                () -> assertEquals("FICTIONAL snippet text", result.snippetText()),
                () -> assertEquals(List.of("/ws/1.1/track.get", "/ws/1.1/track.lyrics.analysis.get",
                        "/ws/1.1/track.snippet.get"), requests.stream().map(r -> r.endpoint().getPath()).toList()),
                () -> assertTrue(requests.stream().allMatch(r ->
                        query(r).equals(Map.of("track_id", "12345", "apikey", DUMMY_KEY))))
        );
    }

    @Test
    void explicitTrackIdTrialRejectsInvalidIdOrIdentityWithoutAnyRequest() {
        AtomicInteger calls = new AtomicInteger();
        MusixmatchSongAnalysis provider = provider(request -> {
            calls.incrementAndGet();
            return materialMatch();
        });
        for (String id : Arrays.asList(null, "", "0", "-1", "01", " 12345", "12345 ", "1.0", "1e3",
                "123&apikey=other", "https://example.invalid/123", "10000000000000000000")) {
            assertTrue(provider.lookupMaterialByTrackId("Sunday Best", "Surfaces", id).isEmpty());
        }
        assertTrue(provider.lookupMaterialByTrackId(null, "Surfaces", "12345").isEmpty());
        assertTrue(provider.lookupMaterialByTrackId("Sunday Best", " ", "12345").isEmpty());
        assertEquals(0, calls.get());
    }

    @Test
    void explicitTrackIdTrialStopsBeforeAnalysisForWrongOrMissingReturnedIdentity() {
        for (String invalidField : List.of("track_id", "track_name", "artist_name", "missing_id", "text_id")) {
            JsonObject track = new JsonObject();
            track.addProperty("track_id", 12345);
            track.addProperty("track_name", "Sunday Best");
            track.addProperty("artist_name", "Surfaces");
            switch (invalidField) {
                case "track_id" -> track.addProperty("track_id", 67890);
                case "track_name" -> track.addProperty("track_name", "Sunday Best (Live)");
                case "artist_name" -> track.addProperty("artist_name", "Surfaces feat. Another Artist");
                case "missing_id" -> track.remove("track_id");
                case "text_id" -> track.addProperty("track_id", "12345");
            }
            JsonObject body = new JsonObject();
            body.add("track", track);
            AtomicInteger calls = new AtomicInteger();
            assertTrue(provider(request -> {
                calls.incrementAndGet();
                return envelope(200, body);
            }).lookupMaterialByTrackId("Sunday Best", "Surfaces", "12345").isEmpty());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void explicitTrackIdTrialFailsClosedForMissingAnalysisAndTransportInterruption() {
        AtomicInteger calls = new AtomicInteger();
        assertTrue(provider(request -> calls.incrementAndGet() == 1
                ? materialMatch() : envelope(404, new JsonObject()))
                .lookupMaterialByTrackId("Sunday Best", "Surfaces", "12345").isEmpty());
        assertEquals(2, calls.get());
        assertTrue(provider(request -> null)
                .lookupMaterialByTrackId("Sunday Best", "Surfaces", "12345").isEmpty());
        for (int interruptedCall : List.of(1, 2, 3)) {
            calls.set(0);
            try {
                assertTrue(provider(request -> {
                    int call = calls.incrementAndGet();
                    if (call == interruptedCall) throw new InterruptedException("secret URL");
                    return call == 1 ? materialMatch() : analysisResponse(materialAnalysis());
                }).lookupMaterialByTrackId("Sunday Best", "Surfaces", "12345").isEmpty());
                assertTrue(Thread.currentThread().isInterrupted());
                assertEquals(interruptedCall, calls.get());
            } finally {
                Thread.interrupted();
            }
        }
        try {
            Thread.currentThread().interrupt();
            calls.set(0);
            assertTrue(provider(request -> {
                calls.incrementAndGet();
                return materialMatch();
            }).lookupMaterialByTrackId("Sunday Best", "Surfaces", "12345").isEmpty());
            assertEquals(0, calls.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void richLookupDoesNotChangeTheOrdinaryDiscardingPathOrRequestASnippet() {
        AtomicInteger calls = new AtomicInteger();
        Analysis result = provider(request -> calls.incrementAndGet() == 1
                ? materialMatch() : analysisResponse(materialAnalysis()))
                .lookup("Sunday Best", "Surfaces").orElseThrow();
        assertEquals(2, calls.get());
        assertEquals("The song uses to express optimism.", result.meaningExplanation());
        assertFalse(result.toString().contains("FICTIONAL"));
    }

    @Test
    void richRecordsDefensivelyCopyNestedListsAndRedactTheirDiagnostics() {
        List<String> quotes = new ArrayList<>(List.of("FICTIONAL lyric"));
        List<String> genres = new ArrayList<>(List.of("Pop"));
        List<ThemeMaterial> themes = new ArrayList<>(List.of(new ThemeMaterial("optimism", quotes)));
        List<String> moods = new ArrayList<>(List.of("warm"));
        List<String> categories = new ArrayList<>(List.of("place"));
        EntityMaterial entity = new EntityMaterial("FICTIONAL entity", "FICTIONAL description", categories);
        List<EntityMaterial> entities = new ArrayList<>(List.of(entity));
        SongMaterial material = new SongMaterial("Sunday Best", "Surfaces", "12345", genres,
                themes, moods, "FICTIONAL meaning", entities, "FICTIONAL snippet");
        quotes.clear(); genres.clear(); themes.clear(); moods.clear(); categories.clear(); entities.clear();
        assertAll(
                () -> assertEquals(List.of("FICTIONAL lyric"), material.themes().get(0).quotes()),
                () -> assertEquals(List.of("Pop"), material.genres()),
                () -> assertEquals(List.of("warm"), material.moods()),
                () -> assertEquals(List.of("place"), material.entities().get(0).categories()),
                () -> assertThrows(UnsupportedOperationException.class, () -> material.themes().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> material.genres().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> material.moods().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> material.entities().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> material.themes().get(0).quotes().clear()),
                () -> assertThrows(UnsupportedOperationException.class, () -> entity.categories().clear()),
                () -> assertEquals("SongMaterial[providerText=redacted]", material.toString()),
                () -> assertEquals("ThemeMaterial[providerText=redacted]", material.themes().get(0).toString()),
                () -> assertEquals("EntityMaterial[providerText=redacted]", entity.toString())
        );
    }

    @Test
    void richLookupKeepsExactRecordingAndFeaturedArtistBoundariesBeforeMoreRequests() {
        for (String[] identity : List.of(new String[]{"Sunday Best (Live)", "Surfaces"},
                new String[]{"Sunday Best - Remastered", "Surfaces"},
                new String[]{"Sunday Best", "Surfaces feat. Another Artist"})) {
            AtomicInteger calls = new AtomicInteger();
            assertTrue(provider(request -> {
                calls.incrementAndGet();
                return matched(identity[0], identity[1]);
            }).lookupMaterial("Sunday Best", "Surfaces").isEmpty());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void unavailableOrEmptyAnalysisCannotBecomeRichMaterialFromGenresEntitiesOrSnippet() {
        JsonObject entitiesOnly = new JsonObject();
        entitiesOnly.add("entities", materialAnalysis().get("entities"));
        for (TransportResponse unavailable : List.of(envelope(404, new JsonObject()),
                envelope(200, new JsonObject()), analysisResponse(new JsonObject()), analysisResponse(entitiesOnly))) {
            AtomicInteger calls = new AtomicInteger();
            assertTrue(provider(request -> calls.incrementAndGet() == 1 ? materialMatch() : unavailable)
                    .lookupMaterial("Sunday Best", "Surfaces").isEmpty());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void richMaterialAcceptsEveryExactTextLimitWithoutTruncatingItsWords() {
        JsonObject analysis = materialAnalysis();
        JsonArray quotes = new JsonArray();
        for (int index = 0; index < 4; index++) quotes.add("q".repeat(240));
        analysis.getAsJsonObject("themes").getAsJsonArray("main_themes").get(0).getAsJsonObject()
                .add("quotes", quotes);
        analysis.getAsJsonObject("meaning").addProperty("explanation", "m".repeat(1200));
        SongMaterial result = lookupMaterial(analysis, snippetResponse("s".repeat(400), 0, 0)).orElseThrow();
        assertEquals(4, result.themes().get(0).quotes().size());
        assertEquals(240, result.themes().get(0).quotes().get(0).length());
        assertEquals(1200, result.meaningExplanation().length());
        assertEquals(400, result.snippetText().length());
    }

    @Test
    void richMaterialOmitsOverLimitOptionalTextRatherThanSilentlyTruncatingSources() {
        for (String[] quoteValues : List.of(new String[]{"q".repeat(241)},
                new String[]{"one", "two", "three", "four", "five"})) {
            JsonObject analysis = materialAnalysis();
            JsonArray quotes = new JsonArray();
            for (String quote : quoteValues) quotes.add(quote);
            analysis.getAsJsonObject("themes").getAsJsonArray("main_themes").get(0).getAsJsonObject()
                    .add("quotes", quotes);
            analysis.getAsJsonObject("meaning").addProperty("explanation", "m".repeat(1201));
            SongMaterial result = lookupMaterial(analysis, snippetResponse("s".repeat(401), 0, 0)).orElseThrow();
            assertEquals(List.of(), result.themes().get(0).quotes());
            assertEquals(null, result.meaningExplanation());
            assertEquals(null, result.snippetText());
            assertEquals(List.of("uplifting", "reflective"), result.moods());
        }
    }

    @Test
    void richMaterialRejectsExcessiveCoreThemesOrMoodsBeforeRequestingSnippet() {
        for (String field : List.of("themes", "moods")) {
            JsonObject analysis = materialAnalysis();
            JsonArray values = new JsonArray();
            for (int index = 0; index < 9; index++) {
                if (field.equals("themes")) {
                    JsonObject theme = new JsonObject();
                    theme.addProperty("theme", "theme " + index);
                    values.add(theme);
                } else values.add("mood " + index);
            }
            analysis.getAsJsonObject(field).add(field.equals("themes") ? "main_themes" : "main_moods", values);
            AtomicInteger calls = new AtomicInteger();
            assertTrue(provider(request -> calls.incrementAndGet() == 1
                    ? materialMatch() : analysisResponse(analysis))
                    .lookupMaterial("Sunday Best", "Surfaces").isEmpty());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void richMaterialKeepsOptionalEntitiesBoundedAndOnlyKnownFlatFields() {
        JsonObject analysis = materialAnalysis();
        JsonObject entity = analysis.getAsJsonObject("entities").getAsJsonArray("entity_list")
                .get(0).getAsJsonObject();
        entity.getAsJsonObject("model_metadata").addProperty("description", "x".repeat(401));
        JsonArray invalidCategories = new JsonArray();
        invalidCategories.add(new JsonObject());
        entity.add("categories", invalidCategories);
        SongMaterial result = lookupMaterial(analysis, snippetResponse("snippet", 0, 0)).orElseThrow();
        assertEquals("FICTIONAL wiki description", result.entities().get(0).description());
        assertEquals(List.of(), result.entities().get(0).categories());

        JsonArray tooMany = new JsonArray();
        for (int index = 0; index < 9; index++) tooMany.add(entity.deepCopy());
        analysis.getAsJsonObject("entities").add("entity_list", tooMany);
        assertEquals(List.of(), lookupMaterial(analysis, snippetResponse("snippet", 0, 0))
                .orElseThrow().entities());
    }

    @Test
    void restrictedInstrumentalMalformedAndUnavailableSnippetsDoNotEraseGoodAnalysis() {
        for (TransportResponse snippet : List.of(snippetResponse("restricted text", 1, 0),
                snippetResponse("instrumental text", 0, 1), envelope(404, new JsonObject()),
                envelope(402, new JsonObject()), response(200, "{} {}"),
                response(200, "{\"message\":{},\"message\":{}}"))) {
            SongMaterial result = lookupMaterial(materialAnalysis(), snippet).orElseThrow();
            assertEquals(null, result.snippetText());
            assertEquals(2, result.themes().size());
        }
    }

    @Test
    void richLookupSanitizesFailuresAndPreservesAnalysisWhenOnlySnippetTransportFails() {
        for (int failingCall : List.of(1, 2, 3)) {
            AtomicInteger calls = new AtomicInteger();
            Optional<SongMaterial> result = provider(request -> {
                int call = calls.incrementAndGet();
                if (call == failingCall) throw new IOException("secret endpoint?apikey=" + DUMMY_KEY);
                return call == 1 ? materialMatch() : analysisResponse(materialAnalysis());
            }).lookupMaterial("Sunday Best", "Surfaces");
            assertEquals(failingCall == 3, result.isPresent());
            assertEquals(failingCall, calls.get());
            if (result.isPresent()) assertEquals(null, result.get().snippetText());
        }
    }

    @Test
    void richSnippetInterruptionRestoresInterruptAndRetainsAlreadyFetchedAnalysis() {
        AtomicInteger calls = new AtomicInteger();
        try {
            SongMaterial result = provider(request -> {
                int call = calls.incrementAndGet();
                if (call == 3) throw new InterruptedException("secret URL");
                return call == 1 ? materialMatch() : analysisResponse(materialAnalysis());
            }).lookupMaterial("Sunday Best", "Surfaces").orElseThrow();
            assertEquals(null, result.snippetText());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    private static Optional<SongMaterial> lookupMaterial(JsonObject analysis, TransportResponse snippet) {
        AtomicInteger calls = new AtomicInteger();
        return provider(request -> switch (calls.incrementAndGet()) {
            case 1 -> materialMatch();
            case 2 -> analysisResponse(analysis);
            default -> snippet;
        }).lookupMaterial("Sunday Best", "Surfaces");
    }

    private static TransportResponse materialMatch() {
        JsonObject track = new JsonObject();
        track.addProperty("track_id", 12345);
        track.addProperty("track_name", "Sunday Best");
        track.addProperty("artist_name", "Surfaces");
        JsonArray genres = new JsonArray();
        for (String name : List.of("Pop", "R&B/Soul")) {
            JsonObject genre = new JsonObject();
            genre.addProperty("music_genre_name", name);
            genre.addProperty("music_genre_vanity", "IGNORED genre URL");
            JsonObject wrapper = new JsonObject();
            wrapper.add("music_genre", genre);
            genres.add(wrapper);
        }
        JsonObject primary = new JsonObject();
        primary.add("music_genre_list", genres);
        track.add("primary_genres", primary);
        JsonObject body = new JsonObject();
        body.add("track", track);
        return envelope(200, body);
    }

    private static JsonObject materialAnalysis() {
        JsonObject analysis = validAnalysis();
        JsonArray quotes = new JsonArray();
        quotes.add("FICTIONAL supporting line");
        quotes.add("FICTIONAL second line");
        analysis.getAsJsonObject("themes").getAsJsonArray("main_themes").get(0).getAsJsonObject()
                .add("quotes", quotes);
        analysis.getAsJsonObject("meaning").addProperty("explanation",
                "The song uses \"FICTIONAL meaning quotation\" to express optimism.");
        JsonObject entity = new JsonObject();
        entity.addProperty("entity_name", "FICTIONAL place");
        JsonArray categories = new JsonArray();
        categories.add("place");
        entity.add("categories", categories);
        JsonObject model = new JsonObject();
        model.addProperty("description", "FICTIONAL model description");
        model.addProperty("private_extra", "IGNORED model field");
        entity.add("model_metadata", model);
        JsonObject wiki = new JsonObject();
        wiki.addProperty("description", "FICTIONAL wiki description");
        wiki.addProperty("thumbnail", "IGNORED https://example.invalid/image");
        entity.add("wikidata", wiki);
        entity.addProperty("matches", "IGNORED raw lyrics and offsets");
        entity.addProperty("wikipedia", "IGNORED https://example.invalid/article");
        JsonArray entities = new JsonArray();
        entities.add(entity);
        JsonObject entityContainer = new JsonObject();
        entityContainer.add("entity_list", entities);
        entityContainer.addProperty("description", "IGNORED container prose");
        analysis.add("entities", entityContainer);
        analysis.addProperty("lyrics", "IGNORED full lyric body");
        return analysis;
    }

    private static TransportResponse snippetResponse(String text, int restricted, int instrumental) {
        JsonObject snippet = new JsonObject();
        snippet.addProperty("snippet_body", text);
        snippet.addProperty("restricted", restricted);
        snippet.addProperty("instrumental", instrumental);
        snippet.addProperty("script_tracking_url", "IGNORED https://example.invalid/tracking");
        JsonObject body = new JsonObject();
        body.add("snippet", snippet);
        return envelope(200, body);
    }

    private static TransportResponse analysisResponse(JsonObject analysis) {
        JsonObject body = new JsonObject();
        body.add("analysis", analysis);
        return envelope(200, body);
    }

    private static Optional<Analysis> lookupAnalysis(JsonObject analysis, AtomicInteger calls) {
        JsonObject body = new JsonObject();
        body.add("analysis", analysis);
        return provider(request -> calls.incrementAndGet() == 1
                ? matched("Sunday Best", "Surfaces") : envelope(200, body))
                .lookup("Sunday Best", "Surfaces");
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
        meaning.addProperty("explanation", "The song \u201cDISCARDED title fixture\u201d connects everyday optimism with continuing forward.");
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
