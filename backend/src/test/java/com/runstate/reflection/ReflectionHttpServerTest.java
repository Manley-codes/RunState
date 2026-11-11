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
import com.runstate.reflection.ReflectionContract.SongCueContext;
import com.runstate.reflection.ReflectionContract.SongCueSource;
import com.runstate.reflection.ReflectionContract.TelemetryCoverage;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectionHttpServerTest {
    @Test void v2ObservedEndingIsExplicitIdempotentAndSeparateFromCreativeCue() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var provider = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongSelection() { return true; }
            @Override public boolean supportsSongSnippets() { return true; }
            @Override public boolean supportsSongSnippetFallbacks() { return true; }
            @Override public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String id,
                    List<ReflectionContract.SongCandidate> songs, boolean lookup, boolean fallbacks) {
                assertTrue(lookup); assertTrue(fallbacks); calls.incrementAndGet();
                var song = songs.get(1);
                var snippet = new ReflectionContract.SongSnippet("recording_v2_opening_0_8000",
                        "RUNSTATE_RECORDING", "opening", song.musicContext().trackTitle(), song.musicContext().artistName(),
                        song.recording().albumName(), song.recording().durationMillis(), 0, 8_000,
                        ReflectionContract.SongSnippetSelectionMethod.RECORDING_OPENING_FALLBACK, 2);
                return new GeneratedReflections(exactCandidates(), null, song.candidateId(), SongSelectionMethod.OBSERVED_FALLBACK, snippet);
            }
        };
        try (var server = startRemote(provider, 2, 8)) {
            var request = snippetRequest(); request.addProperty("allowSongSnippetFallbacks", true);
            var first = rawPost(server, remoteHeaders(), request.toString()); assertEquals(200, first.status());
            var json = JsonParser.parseString(first.body()).getAsJsonObject();
            assertFalse(json.has("songCueId")); assertEquals("OBSERVED_FALLBACK", json.get("songSelectionMethod").getAsString());
            assertEquals(2, json.getAsJsonObject("songSnippet").get("version").getAsInt());
            assertEquals(first, rawPost(server, remoteHeaders(), request.toString())); assertEquals(1, calls.get());
            request.addProperty("allowSongSnippetFallbacks", false);
            assertError(rawPost(server, remoteHeaders(), request.toString()), 409, "request_id_conflict");
        }
    }

    @Test void fallbackFlagNeedsConsentAndCannotBeReturnedToLegacyClient() throws Exception {
        var json = snippetRequest(); json.addProperty("allowSongSnippetFallbacks", true);
        json.addProperty("allowSongSnippetLookup", false);
        assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.parseRequest(json.toString().getBytes(StandardCharsets.UTF_8)));
        var legacy = ReflectionApiJson.parseRequest(snippetRequest().toString().getBytes(StandardCharsets.UTF_8));
        var song = legacy.songCandidates().get(1);
        var snippet = new ReflectionContract.SongSnippet("recording_v2_opening_0_8000", "RUNSTATE_RECORDING", "opening",
                song.musicContext().trackTitle(), song.musicContext().artistName(), song.recording().albumName(),
                song.recording().durationMillis(), 0, 8_000, ReflectionContract.SongSnippetSelectionMethod.RECORDING_OPENING_FALLBACK, 2);
        var generated = new ReflectionProvider.GeneratedReflections(exactCandidates(), null, song.candidateId(),
                ReflectionProvider.SongSelectionMethod.OBSERVED_FALLBACK, snippet);
        assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.success(legacy, generated, ReflectionHttpServer.GenerationMode.ANTHROPIC));
        assertThrows(IllegalArgumentException.class, () -> new ReflectionProvider.GeneratedReflections(exactCandidates(),
                snippet.cueId(), song.candidateId(), ReflectionProvider.SongSelectionMethod.OBSERVED_FALLBACK, snippet));
    }
    private static JsonObject snippetRequest() {
        JsonObject request = multiSongRequest(); request.addProperty("allowSongSnippetLookup", true);
        for (var element : request.getAsJsonArray("songCandidates")) {
            JsonObject recording = new JsonObject(); recording.addProperty("albumName", "Observed Album");
            recording.addProperty("durationMillis", 180_000); element.getAsJsonObject().add("recording", recording);
        }
        return request;
    }

    private static ReflectionContract.SongSnippet generatedSnippet(ReflectionContract.SongCandidate song) {
        return new ReflectionContract.SongSnippet("lrclib_v1_42_10000_15000", "LRCLIB", "42",
                song.musicContext().trackTitle(), song.musicContext().artistName(), song.recording().albumName(),
                song.recording().durationMillis(), 10_000, 5_000,
                ReflectionContract.SongSnippetSelectionMethod.TITLE_HOOK, 1);
    }

    private static ReflectionProvider snippetProvider(AtomicInteger calls) {
        return new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongSelection() { return true; }
            @Override public boolean supportsSongSnippets() { return true; }
            @Override public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String preparationId,
                    List<ReflectionContract.SongCandidate> songs, boolean allowLookup) {
                calls.incrementAndGet(); assertTrue(allowLookup);
                var snippet = generatedSnippet(songs.get(1));
                return new GeneratedReflections(exactCandidates(), snippet.cueId(), "2", SongSelectionMethod.CONTEXT_MATCH, snippet);
            }
        };
    }

    @Test void dynamicSnippetConsentRecordingAndExactOutputAreAuthenticatedAndIdempotent() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var server = startRemote(snippetProvider(calls), 2, 8)) {
            var request = snippetRequest();
            assertError(rawPost(server, List.of("Host: " + DEMO_HOST), request.toString()), 401, "unauthorized");
            assertEquals(0, calls.get());
            var first = rawPost(server, remoteHeaders(), request.toString()); assertEquals(200, first.status());
            var body = JsonParser.parseString(first.body()).getAsJsonObject();
            assertEquals("2", body.get("selectedSongId").getAsString());
            assertEquals("lrclib_v1_42_10000_15000", body.get("songCueId").getAsString());
            JsonObject snippet = body.getAsJsonObject("songSnippet");
            assertEquals(java.util.Set.of("cueId", "source", "sourceId", "trackTitle", "artistName", "albumName",
                    "trackDurationMillis", "startPositionMillis", "durationMillis", "selectionMethod", "version"), snippet.keySet());
            assertEquals("LRCLIB", snippet.get("source").getAsString()); assertEquals("42", snippet.get("sourceId").getAsString());
            assertEquals(first, rawPost(server, remoteHeaders(), request.toString())); assertEquals(1, calls.get());
            for (String change : List.of("consent", "album", "duration")) {
                JsonObject altered = request.deepCopy();
                if (change.equals("consent")) altered.addProperty("allowSongSnippetLookup", false);
                if (change.equals("album")) altered.getAsJsonArray("songCandidates").get(1).getAsJsonObject()
                        .getAsJsonObject("recording").addProperty("albumName", "Another Album");
                if (change.equals("duration")) altered.getAsJsonArray("songCandidates").get(1).getAsJsonObject()
                        .getAsJsonObject("recording").addProperty("durationMillis", 181_000);
                assertError(rawPost(server, remoteHeaders(), altered.toString()), 409, "request_id_conflict");
            }
            assertEquals(1, calls.get());
        }
    }

    @Test void snippetCapabilityExistsOnHealthAndDeliveryOnlyWhenSupported() throws Exception {
        for (boolean supported : List.of(false, true)) {
            try (var server = start(supported ? snippetProvider(new AtomicInteger()) : selectingProvider(new AtomicInteger()))) {
                var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + server.address().getPort() + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(supported ? "1" : null, health.headers().firstValue(ReflectionHttpServer.SONG_SNIPPETS_CAPABILITY_HEADER).orElse(null));
                var delivery = post(server, snippetRequest().toString());
                assertEquals(supported ? 200 : 503, delivery.statusCode());
                assertEquals(supported ? "1" : null, delivery.headers().firstValue(ReflectionHttpServer.SONG_SNIPPETS_CAPABILITY_HEADER).orElse(null));
                if (supported) assertEquals("1", delivery.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElse(null));
            }
        }
    }

    @Test void snippetRequestRejectsImplicitConsentUnknownRecordingFieldsAndInvalidDurations() throws Exception {
        List<JsonObject> invalid = new java.util.ArrayList<>();
        for (String value : List.of("null", "\"true\"", "1", "{}")) {
            var request = snippetRequest(); request.add("allowSongSnippetLookup", JsonParser.parseString(value)); invalid.add(request);
        }
        var noSongs = snippetRequest(); noSongs.remove("songCandidates"); invalid.add(noSongs);
        for (String recording : List.of("null", "{}", "{\"durationMillis\":999}", "{\"durationMillis\":3600001}",
                "{\"durationMillis\":180000.5}", "{\"durationMillis\":\"180000\"}",
                "{\"durationMillis\":180000,\"albumName\":null}", "{\"durationMillis\":180000,\"albumName\":\"\"}",
                "{\"durationMillis\":180000,\"spotifyUri\":\"private\"}")) {
            var request = snippetRequest(); request.getAsJsonArray("songCandidates").get(1).getAsJsonObject()
                    .add("recording", JsonParser.parseString(recording)); invalid.add(request);
        }
        AtomicInteger calls = new AtomicInteger();
        try (var server = start(snippetProvider(calls))) {
            for (var request : invalid) assertError(post(server, request.toString()), 400, "invalid_request");
            assertEquals(0, calls.get());
        }
        var parsed = ReflectionApiJson.parseRequest(multiSongRequest().toString().getBytes(StandardCharsets.UTF_8));
        assertFalse(parsed.allowSongSnippetLookup());
    }

    @Test void dynamicSnippetCannotAcknowledgeAnotherCandidateRecordingOrUnapprovedProvider() throws Exception {
        var request = snippetRequest();
        var parsed = ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8));
        var snippet = generatedSnippet(parsed.songCandidates().get(1));
        var method = ReflectionProvider.SongSelectionMethod.CONTEXT_MATCH;
        assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.success(parsed,
                new ReflectionProvider.GeneratedReflections(exactCandidates(), snippet.cueId(), "1", method, snippet),
                ReflectionHttpServer.GenerationMode.ANTHROPIC));
        request.addProperty("allowSongSnippetLookup", false);
        var noConsent = ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.success(noConsent,
                new ReflectionProvider.GeneratedReflections(exactCandidates(), snippet.cueId(), "2", method, snippet),
                ReflectionHttpServer.GenerationMode.ANTHROPIC));
        assertThrows(IllegalArgumentException.class, () -> new ReflectionProvider.GeneratedReflections(exactCandidates(), "wrong", "2", method, snippet));
        request = snippetRequest(); request.getAsJsonArray("songCandidates").get(1).getAsJsonObject()
                .getAsJsonObject("recording").addProperty("durationMillis", 180_500);
        var wrongDuration = ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.success(wrongDuration,
                new ReflectionProvider.GeneratedReflections(exactCandidates(), snippet.cueId(), "2", method, snippet),
                ReflectionHttpServer.GenerationMode.ANTHROPIC));
    }

    private static JsonObject multiSongRequest() {
        JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
        com.google.gson.JsonArray songs = new com.google.gson.JsonArray();
        for (int id = 1; id <= 2; id++) {
            JsonObject song = new JsonObject(); song.addProperty("candidateId", Integer.toString(id));
            JsonObject music = new JsonObject();
            music.addProperty("state", "MUSIC"); music.addProperty("provenance", "MEDIA_SESSION_PARTIAL");
            music.addProperty("trackTitle", "Observed song " + id); music.addProperty("artistName", "Observed artist");
            song.add("musicContext", music); songs.add(song);
        }
        request.add("songCandidates", songs);
        return request;
    }

    private static ReflectionProvider selectingProvider(AtomicInteger calls) {
        return new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongSelection() { return true; }
            @Override public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String preparationId,
                    List<ReflectionContract.SongCandidate> songs) {
                calls.incrementAndGet();
                return new GeneratedReflections(exactCandidates(), null, songs.get(1).candidateId(), SongSelectionMethod.CONTEXT_MATCH);
            }
        };
    }

    @Test
    void multiSongSelectionIsAuthenticatedIdempotentAndBindsTheEntireCandidateSet() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (var server = startRemote(selectingProvider(calls), 2, 8)) {
            JsonObject request = multiSongRequest();
            assertError(rawPost(server, List.of("Host: " + DEMO_HOST), request.toString()), 401, "unauthorized");
            assertEquals(0, calls.get());
            var first = rawPost(server, remoteHeaders(), request.toString());
            assertEquals(200, first.status());
            JsonObject output = JsonParser.parseString(first.body()).getAsJsonObject();
            assertEquals("2", output.get("selectedSongId").getAsString());
            assertEquals("CONTEXT_MATCH", output.get("songSelectionMethod").getAsString());
            assertFalse(output.has("songCueId"));
            assertEquals(first, rawPost(server, remoteHeaders(), request.toString()));
            assertEquals(1, calls.get());
            request.getAsJsonArray("songCandidates").get(1).getAsJsonObject()
                    .addProperty("songPreparationId", SECOND_REQUEST_ID);
            assertError(rawPost(server, remoteHeaders(), request.toString()), 409, "request_id_conflict");
            request = multiSongRequest();
            request.getAsJsonArray("songCandidates").get(1).getAsJsonObject().getAsJsonObject("musicContext")
                    .addProperty("trackTitle", "Changed song");
            assertError(rawPost(server, remoteHeaders(), request.toString()), 409, "request_id_conflict");
            request = multiSongRequest();
            request.getAsJsonArray("songCandidates").get(1).getAsJsonObject().addProperty("loadedAtFinish", true);
            assertError(rawPost(server, remoteHeaders(), request.toString()), 409, "request_id_conflict");
            assertEquals(1, calls.get());
        }
    }

    @Test
    void selectionCapabilityRequiresSupportingProviderAndUnsupportedRequestsAreHonest() throws Exception {
        for (boolean supported : List.of(false, true)) {
            try (var server = start(supported ? selectingProvider(new AtomicInteger()) : evidence -> exactCandidates())) {
                var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + server.address().getPort() + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(supported ? "1" : null,
                        health.headers().firstValue(ReflectionHttpServer.SONG_SELECTION_CAPABILITY_HEADER).orElse(null));
                assertEquals(supported ? "1" : null, health.headers()
                        .firstValue(ReflectionHttpServer.SONG_LOADED_AT_FINISH_CAPABILITY_HEADER).orElse(null));
                assertEquals(supported ? 200 : 503, post(server, multiSongRequest().toString()).statusCode());
            }
        }
    }

    @Test
    void songCandidateParsingRejectsUnknownDuplicateMalformedAndUnboundedInputs() throws Exception {
        List<JsonObject> invalid = new java.util.ArrayList<>();
        for (String value : List.of("null", "{}", "[]", "[null]", "[1]")) {
            JsonObject request = multiSongRequest(); request.add("songCandidates", JsonParser.parseString(value)); invalid.add(request);
        }
        for (String id : List.of("", "01", "-1", "1.0", "12345678901", "1e2", " ")) {
            JsonObject request = multiSongRequest(); request.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                    .addProperty("candidateId", id); invalid.add(request);
        }
        for (String field : List.of("lyrics", "listeningDuration", "runFacts")) {
            JsonObject request = multiSongRequest(); request.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                    .addProperty(field, "not accepted"); invalid.add(request);
        }
        for (String mark : List.of("\"true\"", "1", "null", "{}")) {
            JsonObject request = multiSongRequest(); request.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                    .add("loadedAtFinish", JsonParser.parseString(mark)); invalid.add(request);
        }
        for (String field : List.of("candidateId", "musicContext")) {
            JsonObject request = multiSongRequest(); request.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                    .remove(field); invalid.add(request);
        }
        for (String invalidMusic : List.of("null", "{\"state\":\"UNKNOWN\"}",
                "{\"state\":\"MUSIC\",\"trackTitle\":\"Title\",\"artistName\":\"Artist\"}")) {
            JsonObject request = multiSongRequest(); request.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                    .add("musicContext", JsonParser.parseString(invalidMusic)); invalid.add(request);
        }
        JsonObject duplicateId = multiSongRequest(); duplicateId.getAsJsonArray("songCandidates").get(1)
                .getAsJsonObject().addProperty("candidateId", "1"); invalid.add(duplicateId);
        JsonObject duplicateIdentity = multiSongRequest(); duplicateIdentity.getAsJsonArray("songCandidates").get(1)
                .getAsJsonObject().getAsJsonObject("musicContext").addProperty("trackTitle", " OBSERVED  SONG 1 ");
        invalid.add(duplicateIdentity);
        JsonObject tooMany = multiSongRequest();
        while (tooMany.getAsJsonArray("songCandidates").size() <= 12) {
            tooMany.getAsJsonArray("songCandidates").add(tooMany.getAsJsonArray("songCandidates").get(0).deepCopy());
        }
        invalid.add(tooMany);
        JsonObject badPreparation = multiSongRequest(); badPreparation.getAsJsonArray("songCandidates").get(0)
                .getAsJsonObject().addProperty("songPreparationId", "not-a-uuid"); invalid.add(badPreparation);
        AtomicInteger calls = new AtomicInteger();
        try (var server = start(selectingProvider(calls))) {
            for (JsonObject request : invalid) assertError(post(server, request.toString()), 400, "invalid_request");
            assertEquals(0, calls.get());
        }
        String duplicateJsonKey = multiSongRequest().toString().replace("\"candidateId\":\"1\"",
                "\"candidateId\":\"1\",\"candidateId\":\"3\"");
        assertThrows(ReflectionApiJson.InvalidJsonException.class, () -> ReflectionApiJson.parseRequest(
                duplicateJsonKey.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void eachSongCarriesItsOwnLoadedAtFinishMarkAndUnmarkedIsTheDefault() throws Exception {
        assertEquals(List.of(false, false), ReflectionApiJson.parseRequest(multiSongRequest().toString()
                .getBytes(StandardCharsets.UTF_8)).songCandidates().stream()
                .map(ReflectionContract.SongCandidate::loadedAtFinish).toList());
        JsonObject request = multiSongRequest();
        request.getAsJsonArray("songCandidates").get(1).getAsJsonObject().addProperty("loadedAtFinish", true);
        assertEquals(List.of(false, true), ReflectionApiJson.parseRequest(request.toString()
                .getBytes(StandardCharsets.UTF_8)).songCandidates().stream()
                .map(ReflectionContract.SongCandidate::loadedAtFinish).toList());
    }

    @Test
    void selectedCueMustBelongToTheChosenCandidateNeverLegacyOrAnotherCandidate() throws Exception {
        JsonObject request = multiSongRequest();
        request.getAsJsonObject("evidence").getAsJsonObject("musicContext").add("songCue", validSongCue());
        JsonObject chosenCue = validSongCue(); chosenCue.addProperty("cueId", "chosen_cue");
        request.getAsJsonArray("songCandidates").get(1).getAsJsonObject().getAsJsonObject("musicContext")
                .add("songCue", chosenCue);
        var parsed = ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8));
        var method = ReflectionProvider.SongSelectionMethod.CONTEXT_MATCH;
        String output = ReflectionApiJson.success(parsed, new ReflectionProvider.GeneratedReflections(
                exactCandidates(), "chosen_cue", "2", method), ReflectionHttpServer.GenerationMode.ANTHROPIC);
        assertTrue(output.contains("chosen_cue"));
        for (var bad : List.of(new ReflectionProvider.GeneratedReflections(exactCandidates(), "chosen_cue", "1", method),
                new ReflectionProvider.GeneratedReflections(exactCandidates(), validSongCue().get("cueId").getAsString(), "2", method),
                new ReflectionProvider.GeneratedReflections(exactCandidates(), null, "99", method),
                new ReflectionProvider.GeneratedReflections(exactCandidates(), validSongCue().get("cueId").getAsString()))) {
            assertThrows(IllegalArgumentException.class, () -> ReflectionApiJson.success(parsed, bad,
                    ReflectionHttpServer.GenerationMode.ANTHROPIC));
        }
        String fallback = ReflectionApiJson.success(parsed, new ReflectionProvider.GeneratedReflections(exactCandidates(), null),
                ReflectionHttpServer.GenerationMode.ANTHROPIC);
        assertFalse(fallback.contains("selectedSongId")); assertFalse(fallback.contains("songCueId"));
    }

    @Test
    void sourceOnlySongPreparationIsAuthenticatedStrictAndReturnsNoSourceMaterial() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = preparingProvider(calls);
        try (ReflectionHttpServer server = startRemote(provider, 2, 2)) {
            String path = ReflectionHttpServer.SONG_PREPARATION_PATH;
            String body = preparationBody(REQUEST_ID);
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST), body), 401, "unauthorized");
            assertError(rawRequest(server, "POST", path,
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN,
                            "Origin: https://example.com"), body), 403, "request_rejected");
            assertError(rawRequest(server, "POST", path, List.of("Host: wrong.example",
                    "Authorization: Bearer " + DUMMY_DEMO_TOKEN), body), 403, "request_rejected");
            assertError(rawRequest(server, "GET", path, remoteHeaders(), body), 405, "method_not_allowed");
            assertError(rawRequest(server, "POST", path + "?facts=true", remoteHeaders(), body), 400, "invalid_request");
            assertError(rawRequest(server, "POST", path,
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN,
                            "Content-Encoding: gzip"), body), 415, "unsupported_media_type");
            for (String invalid : List.of(body.replace("}", ",\"distanceMeters\":5000}"),
                    body.replace("}", ",\"evidence\":{}}"), body.replace(REQUEST_ID, REQUEST_ID.toUpperCase()),
                    body.replace("Controlled track", "x".repeat(201)), body.replace("Controlled track", "\\ud800"))) {
                assertError(rawRequest(server, "POST", path, remoteHeaders(), invalid), 400, "invalid_request");
            }
            assertError(rawRequest(server, "POST", path, remoteHeaders(),
                    body.replace("\"trackTitle\":", "\"trackTitle\":\"duplicate\",\"trackTitle\":")), 400, "invalid_json");
            assertError(rawRequest(server, "POST", path, remoteHeaders(), " ".repeat(ReflectionHttpServer.MAX_REQUEST_BYTES + 1)),
                    413, "request_too_large");
            assertThrows(ReflectionApiJson.InvalidJsonException.class, () -> ReflectionApiJson.parseSongPreparationRequest(
                    new byte[]{(byte) 0xc3, 0x28}));
            assertEquals(0, calls.get());
            RawHttpResponse prepared = rawRequest(server, "POST", path, remoteHeaders(), body);
            assertEquals(200, prepared.status());
            JsonObject response = JsonParser.parseString(prepared.body()).getAsJsonObject();
            assertEquals(java.util.Set.of("preparationId", "state", "songStudyVersion"), response.keySet());
            assertEquals(REQUEST_ID, response.get("preparationId").getAsString());
            assertEquals("READY", response.get("state").getAsString());
            assertEquals(1, response.get("songStudyVersion").getAsInt());
            assertFalse(prepared.body().contains("Controlled track"));
            assertEquals(prepared, rawRequest(server, "POST", path, remoteHeaders(), body));
            assertEquals(1, calls.get());
            assertError(rawRequest(server, "POST", path, remoteHeaders(), body.replace("Controlled track", "Other song")),
                    409, "preparation_id_conflict");
        }
    }

    @Test
    void preparationCapabilityRequiresSupportingProviderAndMissingProviderIsHonest() throws Exception {
        for (boolean supported : List.of(false, true)) {
            ReflectionProvider provider = supported ? preparingProvider(new AtomicInteger()) : evidence -> exactCandidates();
            try (ReflectionHttpServer server = start(provider)) {
                var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                        + server.address().getPort() + "/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(supported ? "1" : null,
                        health.headers().firstValue(ReflectionHttpServer.SONG_PREPARATION_CAPABILITY_HEADER).orElse(null));
                RawHttpResponse result = rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                        List.of(hostHeader(server, "127.0.0.1")), preparationBody(REQUEST_ID));
                assertEquals(supported ? 200 : 503, result.status());
            }
        }
    }

    @Test
    void earlyPreparationJoinsDuplicatesLeavesFinalCapacityAndDoesNotCacheBusyIds() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request)
                    throws ReflectionProviderException {
                calls.incrementAndGet(); entered.countDown();
                try { if (!release.await(3, TimeUnit.SECONDS)) throw new ReflectionProviderException(FailureType.TIMEOUT, "Fixture timeout"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new ReflectionProviderException(FailureType.TRANSPORT, "Fixture interrupted"); }
                return SongPreparationState.READY;
            }
        };
        try (ReflectionHttpServer server = startRemote(provider, 2, 2)) {
            java.util.function.Supplier<RawHttpResponse> firstRequest = () -> {
                try { return rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                        remoteHeaders(), preparationBody(REQUEST_ID)); }
                catch (Exception failure) { throw new CompletionException(failure); }
            };
            CompletableFuture<RawHttpResponse> first = CompletableFuture.supplyAsync(firstRequest);
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            CompletableFuture<RawHttpResponse> duplicate = CompletableFuture.supplyAsync(firstRequest);
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                    remoteHeaders(), preparationBody(SECOND_REQUEST_ID)), 503, "server_busy");
            assertEquals(200, rawPost(server, remoteHeaders(), validRequest()).status());
            release.countDown();
            assertEquals(first.get(3, TimeUnit.SECONDS), duplicate.get(3, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                    remoteHeaders(), preparationBody(SECOND_REQUEST_ID)).status());
            assertEquals(2, calls.get());
        } finally { release.countDown(); }
        try (ReflectionHttpServer server = startRemote(preparingProvider(calls), 1, 2)) {
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                    remoteHeaders(), preparationBody(THIRD_REQUEST_ID)), 503, "server_busy");
            assertEquals(200, rawPost(server, remoteHeaders(), validRequest()).status());
        }
    }

    @Test
    void preparationCacheIsSeparateBoundedAndFinalHandleIsPartOfRequestIdentity() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> deliveredHandle = new AtomicReference<>();
        ReflectionProvider provider = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) { calls.incrementAndGet(); return SongPreparationState.READY; }
            @Override public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String preparationId) {
                deliveredHandle.set(preparationId); return new GeneratedReflections(exactCandidates(), null);
            }
        };
        try (ReflectionHttpServer server = startRemote(provider, 2, 2)) {
            for (int index = 0; index < 32; index++) {
                String id = new java.util.UUID(1, index).toString();
                assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                        remoteHeaders(), preparationBody(id)).status());
            }
            assertEquals(32, calls.get());
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                    remoteHeaders(), preparationBody(THIRD_REQUEST_ID)), 503, "server_capacity");
            JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
            request.addProperty("songPreparationId", REQUEST_ID);
            assertEquals(200, rawPost(server, remoteHeaders(), request.toString()).status());
            assertEquals(REQUEST_ID, deliveredHandle.get());
            request.addProperty("songPreparationId", SECOND_REQUEST_ID);
            assertError(rawPost(server, remoteHeaders(), request.toString()), 409, "request_id_conflict");
            request.addProperty("requestId", SECOND_REQUEST_ID);
            request.addProperty("songPreparationId", "not-a-uuid");
            assertError(rawPost(server, remoteHeaders(), request.toString()), 400, "invalid_request");
            request.remove("songPreparationId");
            request.getAsJsonObject("evidence").addProperty("songPreparationId", REQUEST_ID);
            assertError(rawPost(server, remoteHeaders(), request.toString()), 400, "invalid_request");
        }
    }

    private static ReflectionProvider preparingProvider(AtomicInteger calls) {
        return new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) {
                calls.incrementAndGet(); return SongPreparationState.READY;
            }
        };
    }

    private static String preparationBody(String id) {
        JsonObject body = new JsonObject();
        body.addProperty("preparationId", id); body.addProperty("trackTitle", "Controlled track");
        body.addProperty("artistName", "Controlled artist"); return body.toString();
    }

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

    @TempDir Path speechDirectory;

    @Test
    void reflectionSpeechPreservesExactTextAndReturnsPrivateMp3Bytes() throws Exception {
        String exact = "  Your exact reflection.\nA second line — unchanged.  ";
        byte[] audio = new byte[]{'I', 'D', '3', 0, (byte) 0xff, 1};
        AtomicReference<String> spoken = new AtomicReference<>();
        AtomicInteger reflections = new AtomicInteger();
        try (ReflectionHttpServer server = ReflectionHttpServer.create(evidence -> {
            reflections.incrementAndGet();
            return exactCandidates();
        }, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC, text -> {
            spoken.set(text);
            return audio;
        }, null, new SpeechReplayStore(speechDirectory))) {
            server.start();
            HttpResponse<byte[]> response = client.send(speechRequest(server, speechBody(exact)),
                    HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.HEALTH_PATH)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertArrayEquals(audio, response.body()),
                    () -> assertEquals(exact, spoken.get()),
                    () -> assertEquals("audio/mpeg", response.headers().firstValue("Content-Type").orElseThrow()),
                    () -> assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()),
                    () -> assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow()),
                    () -> assertEquals("1", health.headers().firstValue(
                            ReflectionHttpServer.REFLECTION_VOICE_CAPABILITY_HEADER).orElseThrow()),
                    () -> assertEquals("{\"status\":\"ok\"}", health.body()),
                    () -> assertEquals(0, reflections.get())
            );
        }
    }

    @Test
    void missingSpeechProviderDoesNotAdvertiseVoiceAndFailsWithoutCallingReflection() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        })) {
            HttpResponse<String> response = client.send(speechRequest(server, speechBody("Exact reflection.")),
                    HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.HEALTH_PATH)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, response.statusCode());
            assertTrue(health.headers().firstValue(ReflectionHttpServer.REFLECTION_VOICE_CAPABILITY_HEADER).isEmpty());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void invalidSpeechJsonTypesExtrasDuplicatesLengthAndUnicodeNeverReachProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startSpeech(text -> {
            calls.incrementAndGet();
            return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
        })) {
            List<String> badJson = List.of("not-json", "[]", "{\"text\":\"first\",\"text\":\"second\"}",
                    "{\"text\":\"valid\"} trailing");
            for (String body : badJson) {
                assertEquals(400, client.send(speechRequest(server, body), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            for (String body : List.of("{}", "{\"text\":null}", "{\"text\":3}", "{\"text\":[]}",
                    "{\"text\":\"exact\",\"voice_id\":\"unrequested\"}", speechBody(""), speechBody("  \t\n"),
                    speechBody("x".repeat(4_001)), "{\"text\":\"" + "\\" + "ud800\"}")) {
                assertEquals(400, client.send(speechRequest(server, body), HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            HttpRequest malformedUtf8 = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.SPEECH_PATH))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{(byte) 0xc3, 0x28})).build();
            assertEquals(400, client.send(malformedUtf8, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(0, calls.get());
            assertEquals(200, client.send(speechRequest(server, speechBody("x".repeat(4_000))),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void speechRetainsRemoteAuthenticationHostOriginAndHttpGuardsBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemoteSpeech(evidence -> exactCandidates(), text -> {
            calls.incrementAndGet();
            return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
        }, 1, 4)) {
            String path = ReflectionHttpServer.SPEECH_PATH;
            String body = speechBody("Exact reflection.");
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST), "{"),
                    401, "unauthorized");
            for (List<String> headers : List.of(
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer incorrect"),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN,
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN))) {
                assertError(rawRequest(server, "POST", path, headers, body), 401, "unauthorized");
            }
            for (List<String> headers : List.of(
                    List.of("Host: foreign.example", "Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Origin: null"),
                    List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Origin:"))) {
                assertError(rawRequest(server, "POST", path, headers, body), 403, "request_rejected");
            }
            assertError(rawRequest(server, "GET", path, remoteHeaders(), body), 405, "method_not_allowed");
            assertError(rawRequest(server, "POST", path + "?text=ignored", remoteHeaders(), body), 400, "invalid_request");
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST,
                    "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Content-Type: text/plain"), body),
                    415, "unsupported_media_type");
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST,
                    "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "Content-Encoding: gzip"), body),
                    415, "unsupported_media_type");
            assertError(rawRequest(server, "POST", path, remoteHeaders(),
                    "x".repeat(ReflectionHttpServer.MAX_REQUEST_BYTES + 1)), 413, "request_too_large");
            assertEquals(0, calls.get());
            assertEquals(200, rawRequest(server, "POST", path, remoteHeaders(), body).status());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void speechReplayDoesNotConsumeTheReflectionGenerationCache() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemoteSpeech(evidence -> exactCandidates(), text -> {
            calls.incrementAndGet();
            return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
        }, 1, 1)) {
            for (int index = 0; index < 3; index++) {
                assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH,
                        remoteHeaders(), speechBody("Same exact reflection.")).status());
            }
            assertEquals(1, calls.get());
            assertEquals(200, rawPost(server, remoteHeaders(), validRequest()).status());
        }
    }

    @Test
    void newPhoneAttemptAfterRestartGetsOriginalFamilySnippetAndAcknowledgement(@TempDir Path directory)
            throws Exception {
        AtomicInteger calls = new AtomicInteger();
        JsonObject request = snippetRequest();
        JsonObject original;
        try (var first = startWritten(snippetProvider(calls), directory)) {
            var result = post(first, request.toString());
            assertEquals(200, result.statusCode());
            assertEquals("1", result.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElseThrow());
            original = JsonParser.parseString(result.body()).getAsJsonObject();
            assertEquals(4, original.getAsJsonObject("candidates").size());
            assertEquals("2", original.get("selectedSongId").getAsString());
            assertEquals("lrclib_v1_42_10000_15000", original.getAsJsonObject("songSnippet").get("cueId").getAsString());
        }
        // A provider capability can be temporarily unavailable after restart. Saved writing still replays.
        ReflectionProvider offlineWriter = evidence -> { throw new AssertionError("Replay called writer"); };
        try (var restarted = startWritten(offlineWriter, directory)) {
            request.addProperty("requestId", SECOND_REQUEST_ID);
            var retry = post(restarted, request.toString());
            assertEquals(200, retry.statusCode());
            assertEquals("1", retry.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElseThrow());
            assertEquals("1", retry.headers().firstValue(ReflectionHttpServer.SONG_SNIPPETS_CAPABILITY_HEADER).orElseThrow());
            original.addProperty("requestId", SECOND_REQUEST_ID);
            assertEquals(original, JsonParser.parseString(retry.body()));
            assertEquals(1, calls.get());
            for (String id : List.of(REQUEST_ID, SECOND_REQUEST_ID)) {
                request.addProperty("requestId", id);
                request.addProperty("runId", OTHER_RUN_ID);
                assertError(post(restarted, request.toString()), 409, "request_id_conflict");
            }
            assertEquals(1, calls.get());
        }
    }

    @Test
    void freshAttemptAfterSnippetConsentChangesCannotReceiveOldSnippet(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        JsonObject consent = snippetRequest();
        try (var first = startWritten(snippetProvider(calls), directory)) {
            assertEquals(200, post(first, consent.toString()).statusCode());
        }
        try (var restarted = startWritten(selectingProvider(calls), directory)) {
            consent.addProperty("allowSongSnippetLookup", false);
            assertError(post(restarted, consent.toString()), 409, "request_id_conflict");
            consent.addProperty("requestId", SECOND_REQUEST_ID);
            var changed = post(restarted, consent.toString());
            assertEquals(200, changed.statusCode());
            assertFalse(JsonParser.parseString(changed.body()).getAsJsonObject().has("songSnippet"));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void savedWritingBelongsToTheAuthenticatedRunner(@TempDir Path directory) throws Exception {
        String otherToken = "B".repeat(43);
        String expiry = "2099-01-01T00:00:00Z";
        var policy = BackendAccessPolicy.fromJson("[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, expiry, true)
                + "," + accessGrant("runner-b", otherToken, expiry, true) + "]");
        var access = ReflectionHttpServer.RemoteAccess.individual("127.0.0.1", DEMO_HOST, policy);
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = evidence -> new CandidateReflections("Runner result " + calls.incrementAndGet(),
                FEELING_GOOD, POWERED_UP, NO_SELECTION);
        try (var server = ReflectionHttpServer.createRemote(provider, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                access, 2, 8, null, null, null, new WrittenReflectionReplayStore(directory))) {
            server.start();
            String body = validRequest();
            assertEquals(200, rawPost(server, accessHeaders(DUMMY_DEMO_TOKEN), body).status());
            assertError(rawPost(server, List.of("Host: " + DEMO_HOST), body), 401, "unauthorized");
            var other = rawPost(server, accessHeaders(otherToken), body);
            assertEquals(200, other.status());
            assertEquals("Runner result 2", JsonParser.parseString(other.body()).getAsJsonObject()
                    .getAsJsonObject("candidates").get("spent").getAsString());
            var owner = rawPost(server, accessHeaders(DUMMY_DEMO_TOKEN), body.replace(REQUEST_ID, SECOND_REQUEST_ID));
            assertEquals(200, owner.status());
            assertEquals("Runner result 1", JsonParser.parseString(owner.body()).getAsJsonObject()
                    .getAsJsonObject("candidates").get("spent").getAsString());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void twoWritersReceivingDifferentPhoneAttemptsReserveOnePaidCall(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        ReflectionProvider provider = evidence -> {
            calls.incrementAndGet();
            started.countDown();
            try {
                if (!finish.await(5, TimeUnit.SECONDS)) throw new AssertionError("Writer timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return exactCandidates();
        };
        try (var first = startWritten(provider, directory); var other = startWritten(provider, directory)) {
            var pending = postAsync(first, validRequest());
            String retryBody = validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID);
            try {
                assertTrue(started.await(2, TimeUnit.SECONDS));
                assertError(post(other, retryBody), 503, "reflection_pending");
                assertEquals(1, calls.get());
            } finally {
                finish.countDown();
            }
            assertEquals(200, pending.get(5, TimeUnit.SECONDS).statusCode());
            assertEquals(200, post(other, retryBody).statusCode());
            assertEquals(1, calls.get());
        }
    }

    @Test
    void uncertainWriterAttemptCannotBecomeANewPaidAttemptAfterRestart(@TempDir Path directory) throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = evidence -> {
            if (calls.incrementAndGet() == 1) throw new ReflectionProviderException(FailureType.TIMEOUT, "fake timeout");
            return exactCandidates();
        };
        try (var first = startWritten(provider, directory)) {
            assertError(post(first, validRequest()), 504, "provider_timeout");
        }
        try (var restarted = startWritten(provider, directory)) {
            var retry = post(restarted, validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID));
            assertTrue(retry.statusCode() >= 400, "An uncertain paid attempt cannot be repeated");
            assertEquals(1, calls.get());
            assertEquals(200, post(restarted, validRequest().replace(REQUEST_ID, THIRD_REQUEST_ID)
                    .replace(RUN_ID, OTHER_RUN_ID)).statusCode());
            assertEquals(2, calls.get());
        }
    }

    @Test
    void missingOrUnreadableHostedStorageAdmitsNoPaidWriter(@TempDir Path directory) throws Exception {
        var mode = ReflectionHttpServer.GenerationMode.ANTHROPIC;
        assertNull(ReflectionBackendMain.configuredWrittenReplayStore(mode, Map.of(), true));
        assertNull(ReflectionBackendMain.configuredWrittenReplayStore(mode,
                Map.of("RUNSTATE_SPEECH_REPLAY_DIR", "relative"), true));
        var misplaced = Files.createFile(directory.resolve("ordinary-file"));
        assertNull(ReflectionBackendMain.configuredWrittenReplayStore(mode,
                Map.of("RUNSTATE_SPEECH_REPLAY_DIR", misplaced.toString()), true));
        assertNotNull(ReflectionBackendMain.configuredWrittenReplayStore(mode,
                Map.of("RUNSTATE_SPEECH_REPLAY_DIR", directory.toString()), true));
        ReflectionProvider provider = evidence -> { throw new AssertionError("Storage must precede a paid call"); };
        try (var server = ReflectionHttpServer.create(provider, 0, mode, null, null, null, null)) {
            server.start();
            assertError(post(server, validRequest()), 503, "reflection_storage_unavailable");
        }
    }

    @Test
    void monthlyLimitsProtectAllPaidRoutesAndAllowFreeReplaysAfterRestart(@TempDir Path directory) throws Exception {
        String secondToken = "B".repeat(43);
        String expiry = "2099-01-01T00:00:00Z";
        String grants = "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, expiry, true) + ","
                + accessGrant("runner-b", secondToken, expiry, true) + "]";
        var access = ReflectionHttpServer.RemoteAccess.individual("127.0.0.1", DEMO_HOST, BackendAccessPolicy.fromJson(grants));
        var runnerA = accessHeaders(DUMMY_DEMO_TOKEN);
        var runnerB = accessHeaders(secondToken);
        var writtenCalls = new AtomicInteger();
        var voiceCalls = new AtomicInteger();
        var lookupCalls = new AtomicInteger();
        var writer = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) {
                ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.ANTHROPIC);
                writtenCalls.incrementAndGet();
                return exactCandidates();
            }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) {
                ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.MUSIXMATCH);
                lookupCalls.incrementAndGet();
                return SongPreparationState.READY;
            }
        };
        ReflectionHttpServer.SpeechProvider voice = text -> {
            ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.ELEVENLABS);
            return ("ID3-" + voiceCalls.incrementAndGet()).getBytes(StandardCharsets.US_ASCII);
        };
        var limits = new ProviderSpendBudget.Limits(30_000, 50_000, 20_000L, 10_000L, 10_000L);
        String secondRun = validRequest().replace(RUN_ID, OTHER_RUN_ID).replace(REQUEST_ID, SECOND_REQUEST_ID);
        String speechPath = ReflectionHttpServer.SPEECH_PATH;
        String runVoicePath = ReflectionHttpServer.RUN_VOICE_PATH;
        String preparationPath = ReflectionHttpServer.SONG_PREPARATION_PATH;
        String savedText = speechBody("Your first reflection.");
        String newText = speechBody("Your next reflection.");
        RawHttpResponse savedWritten;
        RawHttpResponse savedAudio;
        try (var server = startBudgeted(directory, access, writer, voice, limits)) {
            assertError(rawPost(server, List.of("Host: " + DEMO_HOST), validRequest()), 401, "unauthorized");
            assertEquals(0, writtenCalls.get());
            savedWritten = rawPost(server, runnerA, validRequest());
            savedAudio = rawRequest(server, "POST", speechPath, runnerA, savedText);
            assertEquals(200, savedWritten.status());
            assertEquals(200, savedAudio.status());
            assertError(rawPost(server, runnerA, secondRun), 429, "spend_limit_reached");
            assertError(rawRequest(server, "POST", speechPath, runnerA, newText), 429, "spend_limit_reached");
            assertError(rawRequest(server, "POST", runVoicePath, runnerA, speechBody("One mile.")), 429, "spend_limit_reached");
            assertError(rawRequest(server, "POST", preparationPath, runnerA, preparationBody(REQUEST_ID)), 429, "spend_limit_reached");

            assertEquals(200, rawRequest(server, "POST", preparationPath, runnerB, preparationBody(REQUEST_ID)).status());
            assertEquals(200, rawRequest(server, "POST", runVoicePath, runnerB, speechBody("One mile.")).status());
            assertError(rawRequest(server, "POST", runVoicePath, runnerB, speechBody("Two miles.")), 429, "spend_limit_reached");
        }
        try (var restarted = startBudgeted(directory, access, writer, voice, limits)) {
            assertError(rawRequest(restarted, "POST", preparationPath, runnerB, preparationBody(SECOND_REQUEST_ID)),
                    429, "spend_limit_reached");
            assertEquals(savedWritten, rawPost(restarted, runnerA, validRequest()));
            assertEquals(savedAudio, rawRequest(restarted, "POST", speechPath, runnerA, savedText));
            assertEquals(1, writtenCalls.get());
            assertEquals(2, voiceCalls.get());
            assertEquals(1, lookupCalls.get());
        }
        var raised = new ProviderSpendBudget.Limits(100_000, 200_000, 20_000L, 10_000L, 10_000L);
        try (var restarted = startBudgeted(directory, access, writer, voice, raised)) {
            assertEquals(200, rawPost(restarted, runnerA, secondRun).status());
            assertEquals(200, rawRequest(restarted, "POST", speechPath, runnerA, newText).status());
            assertEquals(2, writtenCalls.get());
            assertEquals(3, voiceCalls.get());
        }
    }

    @Test
    void budgetDenialAfterOnePaidWriterCallPreservesReplayProtection(@TempDir Path directory) throws Exception {
        var access = new ReflectionHttpServer.RemoteAccess("127.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN);
        var calls = new AtomicInteger();
        ReflectionProvider writer = evidence -> {
            for (int step = 0; step < 2; step++) {
                ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.ANTHROPIC);
                calls.incrementAndGet();
            }
            return exactCandidates();
        };
        var limits = new ProviderSpendBudget.Limits(10_000, 200_000, 10_000L, 10_000L, 10_000L);
        try (var first = startBudgeted(directory, access, writer, null, limits)) {
            assertError(rawPost(first, remoteHeaders(), validRequest()), 429, "spend_limit_reached");
            assertEquals(1, calls.get());
        }
        var raised = new ProviderSpendBudget.Limits(100_000, 200_000, 10_000L, 10_000L, 10_000L);
        try (var restarted = startBudgeted(directory, access, writer, null, raised)) {
            var sameRun = validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID);
            assertError(rawPost(restarted, remoteHeaders(), sameRun), 503, "reflection_pending");
            assertEquals(1, calls.get());
            var newRun = validRequest().replace(REQUEST_ID, THIRD_REQUEST_ID).replace(RUN_ID, OTHER_RUN_ID);
            assertEquals(200, rawPost(restarted, remoteHeaders(), newRun).status());
            assertEquals(3, calls.get());
        }
    }

    @Test
    void missingSpendConfigurationAllowsSavedRepliesAndBlocksNewProviderCalls(@TempDir Path directory) throws Exception {
        var access = new ReflectionHttpServer.RemoteAccess("127.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN);
        var writtenCalls = new AtomicInteger();
        var voiceCalls = new AtomicInteger();
        ReflectionProvider writer = evidence -> {
            ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.ANTHROPIC);
            writtenCalls.incrementAndGet();
            return exactCandidates();
        };
        ReflectionHttpServer.SpeechProvider voice = text -> {
            ProviderSpendScope.beforeSend(ProviderSpendBudget.Provider.ELEVENLABS);
            voiceCalls.incrementAndGet();
            return "ID3saved".getBytes(StandardCharsets.US_ASCII);
        };
        var limits = new ProviderSpendBudget.Limits(100_000, 200_000, 10_000L, 10_000L, 10_000L);
        String text = speechBody("Previously generated speech.");
        String speechPath = ReflectionHttpServer.SPEECH_PATH;
        try (var first = startBudgeted(directory, access, writer, voice, limits)) {
            assertEquals(200, rawPost(first, remoteHeaders(), validRequest()).status());
            assertEquals(200, rawRequest(first, "POST", speechPath, remoteHeaders(), text).status());
        }
        try (var restarted = ReflectionHttpServer.createRemote(writer, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                access, 2, 256, voice, voice, new SpeechReplayStore(directory), new WrittenReflectionReplayStore(directory), null)) {
            restarted.start();
            assertEquals(200, rawPost(restarted, remoteHeaders(), validRequest()).status());
            assertEquals(200, rawRequest(restarted, "POST", speechPath, remoteHeaders(), text).status());
            String newRun = validRequest().replace(REQUEST_ID, SECOND_REQUEST_ID).replace(RUN_ID, OTHER_RUN_ID);
            assertError(rawPost(restarted, remoteHeaders(), newRun), 503, "spend_control_unavailable");
            assertError(rawRequest(restarted, "POST", speechPath, remoteHeaders(), speechBody("New speech.")),
                    503, "spend_control_unavailable");
            assertEquals(1, writtenCalls.get());
            assertEquals(1, voiceCalls.get());
        }
    }

    @Test void scopedPreviewBindsPreparationWritingAndOnlyOneSelectedVoiceToAnAdmittedRun(@TempDir Path directory)
            throws Exception {
        var observed = new AtomicInteger();
        var writer = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) {
                observed.incrementAndGet();
                return exactCandidates();
            }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) {
                observed.incrementAndGet();
                return SongPreparationState.READY;
            }
        };
        ReflectionHttpServer.SpeechProvider voice = text -> ("audio-" + observed.incrementAndGet()).getBytes(StandardCharsets.US_ASCII);
        var headers = accessHeaders(DUMMY_DEMO_TOKEN);
        var runHeaders = new java.util.ArrayList<>(headers);
        runHeaders.add(ReflectionHttpServer.RUN_ID_HEADER + ": " + RUN_ID);
        var replayHeaders = new java.util.ArrayList<>(headers);
        replayHeaders.add(ReflectionHttpServer.REPLAY_ONLY_HEADER + ": 1");
        String replayQuery = "{\"runId\":\"" + RUN_ID + "\",\"replayOnly\":true}";
        var tracking = startScoped(directory, "tracking", writer, voice);
        try (tracking) {
            assertEquals("tracking", JsonParser.parseString(rawRequest(tracking, "GET", ReflectionHttpServer.ACCESS_PATH,
                    headers, "").body()).getAsJsonObject().get("status").getAsString());
            assertError(rawPost(tracking, headers, validRequest()), 403, "reflection_access_required");
            assertError(rawRequest(tracking, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, "{\"runId\":\"" + RUN_ID + "\"}"),
                    403, "reflection_access_required");
            assertError(rawRequest(tracking, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH, runHeaders, preparationBody(REQUEST_ID)),
                    403, "reflection_access_required");
            assertError(rawRequest(tracking, "POST", ReflectionHttpServer.RUN_VOICE_PATH, runHeaders, speechBody("Mile.")),
                    403, "run_voice_not_included");
            assertError(rawRequest(tracking, "POST", ReflectionHttpServer.SPEECH_PATH, runHeaders, speechBody(SPENT)),
                    403, "reflection_access_required");
            assertEquals(0, observed.get());
        }
        RawHttpResponse savedWritten, savedVoice;
        try (var server = startScoped(directory, "trial", writer, voice)) {
            assertError(rawPost(server, headers, validRequest()), 403, "reflection_access_required");
            assertError(rawRequest(server, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, replayQuery),
                    403, "reflection_replay_unavailable");
            assertError(rawRequest(server, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, "{\"runId\":\"" + RUN_ID + "\"}"),
                    403, "reflection_access_required");
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.TRIAL_PATH, headers, "{}").status());
            assertError(rawPost(server, headers, validRequest()), 403, "run_admission_required");
            String runBody = "{\"runId\":\"" + RUN_ID + "\"}";
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, runBody).status());
            var lastSlot = rawRequest(server, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, runBody.replace(RUN_ID, OTHER_RUN_ID));
            assertEquals(0, JsonParser.parseString(lastSlot.body()).getAsJsonObject().get("remaining").getAsInt());
            assertError(rawRequest(server, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, runBody.replace(RUN_ID, THIRD_REQUEST_ID)),
                    403, "reflection_limit_reached");
            assertError(rawPost(server, headers, validRequest().replace(RUN_ID, THIRD_REQUEST_ID)), 403, "run_admission_required");
            assertError(rawPost(server, replayHeaders, validRequest()), 403, "reflection_replay_unavailable");
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH, headers, preparationBody(REQUEST_ID)),
                    403, "run_admission_required");
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH, runHeaders,
                    preparationBody(REQUEST_ID)).status());
            savedWritten = rawPost(server, headers, validRequest());
            assertEquals(200, savedWritten.status());
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH, runHeaders, speechBody("Unrelated.")),
                    403, "reflection_voice_not_included");
            savedVoice = rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH, runHeaders, speechBody(SPENT));
            assertEquals(200, savedVoice.status());
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH, runHeaders, speechBody(POWERED_UP)),
                    403, "reflection_voice_not_included");
            assertEquals(3, observed.get());
        }
        try (var downgraded = startScoped(directory, "tracking", writer, voice)) {
            assertEquals(200, rawRequest(downgraded, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, replayQuery).status());
            assertError(rawRequest(downgraded, "POST", ReflectionHttpServer.RUN_ACCESS_PATH, headers, replayQuery.replace(RUN_ID, OTHER_RUN_ID)),
                    403, "reflection_replay_unavailable");
            assertEquals(savedWritten, rawPost(downgraded, replayHeaders, validRequest()));
            assertEquals(savedVoice, rawRequest(downgraded, "POST", ReflectionHttpServer.SPEECH_PATH, headers, speechBody(SPENT)));
            assertEquals(3, observed.get());
        }
    }

    private static ReflectionHttpServer startScoped(Path directory, String tier, ReflectionProvider writer,
            ReflectionHttpServer.SpeechProvider voice) throws Exception {
        JsonObject grant = JsonParser.parseString(accessGrant("runner", DUMMY_DEMO_TOKEN, "2099-01-01T00:00:00Z", true)).getAsJsonObject();
        grant.addProperty("reflectionTier", tier);
        var access = ReflectionHttpServer.RemoteAccess.individual("127.0.0.1", DEMO_HOST, BackendAccessPolicy.fromJson("[" + grant + "]"));
        var server = ReflectionHttpServer.createRemote(writer, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                access, 2, 256, voice, voice, new SpeechReplayStore(directory), new WrittenReflectionReplayStore(directory), null,
                new ReflectionAllowance(directory, java.time.Clock.systemUTC()));
        server.start();
        return server;
    }

    private static ReflectionHttpServer startBudgeted(Path directory, ReflectionHttpServer.RemoteAccess access,
            ReflectionProvider writer, ReflectionHttpServer.SpeechProvider voice, ProviderSpendBudget.Limits limits)
            throws IOException {
        var server = ReflectionHttpServer.createRemote(writer, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                access, 2, 256, voice, voice, new SpeechReplayStore(directory), new WrittenReflectionReplayStore(directory),
                new ProviderSpendBudget(directory, limits));
        server.start();
        return server;
    }

    private ReflectionHttpServer startWritten(ReflectionProvider provider, Path directory) throws IOException {
        var server = ReflectionHttpServer.create(provider, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                null, null, null, new WrittenReflectionReplayStore(directory));
        server.start();
        return server;
    }

    @Test
    void hostedVoiceRequiresUsableExplicitStorage() {
        ReflectionHttpServer.SpeechProvider voice = text -> { throw new AssertionError("Unexpected synthesis"); };
        assertNull(ReflectionBackendMain.configuredSpeechReplayStore(voice, Map.of(), true));
        assertNull(ReflectionBackendMain.configuredSpeechReplayStore(voice,
                Map.of("RUNSTATE_SPEECH_REPLAY_DIR", "relative-directory"), true));
        var configured = Map.of("RUNSTATE_SPEECH_REPLAY_DIR", speechDirectory.toString());
        assertNotNull(ReflectionBackendMain.configuredSpeechReplayStore(voice, configured, true));
        assertNull(ReflectionBackendMain.configuredSpeechReplayStore(voice,
                Map.of("RUNSTATE_SPEECH_REPLAY_DIR", speechDirectory.resolve("reflection-speech.sqlite3").toString()), true));
    }

    @Test
    void restartedServerReplaysTheChargedReflectionOnlyToItsAuthenticatedRunner() throws Exception {
        String firstToken = DUMMY_DEMO_TOKEN;
        String rotatedToken = "R".repeat(43);
        String otherToken = "B".repeat(43);
        String expiry = "2099-01-01T00:00:00Z";
        String text = "  First mile.\nThat last turn was yours.  ";
        AtomicInteger providerCalls = new AtomicInteger();
        ReflectionHttpServer.SpeechProvider speech = ignored ->
                ("ID3-result-" + providerCalls.incrementAndGet()).getBytes(StandardCharsets.US_ASCII);
        String initialGrants = "[" + accessGrant("runner-a", firstToken, expiry, true) + "]";
        RawHttpResponse original;
        try (var first = startIndividual(evidence -> exactCandidates(), BackendAccessPolicy.fromJson(initialGrants), speech, null)) {
            original = rawRequest(first, "POST", ReflectionHttpServer.SPEECH_PATH, accessHeaders(firstToken), speechBody(text));
            assertEquals(new RawHttpResponse(200, "ID3-result-1"), original);
        }
        String newGrants = "[" + accessGrant("runner-a", rotatedToken, expiry, true) + ","
                + accessGrant("runner-b", otherToken, expiry, true) + "]";
        // Fresh HTTP server, access policy and store instance; only the on-disk directory survives.
        try (var restarted = startIndividual(evidence -> exactCandidates(), BackendAccessPolicy.fromJson(newGrants), speech, null)) {
            assertError(rawRequest(restarted, "POST", ReflectionHttpServer.SPEECH_PATH, accessHeaders(firstToken),
                    speechBody(text)), 401, "unauthorized");
            assertEquals(original, rawRequest(restarted, "POST", ReflectionHttpServer.SPEECH_PATH,
                    accessHeaders(rotatedToken), speechBody(text)));
            assertEquals(1, providerCalls.get(), "Retry after restart must replay the first paid result");
            assertEquals(new RawHttpResponse(200, "ID3-result-2"), rawRequest(restarted, "POST",
                    ReflectionHttpServer.SPEECH_PATH, accessHeaders(otherToken), speechBody(text)));
            assertEquals(new RawHttpResponse(200, "ID3-result-3"), rawRequest(restarted, "POST",
                    ReflectionHttpServer.SPEECH_PATH, accessHeaders(rotatedToken), speechBody(text.strip())));
            assertEquals(3, providerCalls.get());
        }
    }

    @Test
    void twoServersSharingStorageDoNotChargeForTheSameInFlightSpeech() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        String body = speechBody("The last half mile.");
        ReflectionHttpServer.SpeechProvider speech = ignored -> {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Speech provider did not release");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
        };
        try (var first = startRemoteSpeech(evidence -> exactCandidates(), speech, 1, 4);
             var second = startRemoteSpeech(evidence -> exactCandidates(), speech, 1, 4)) {
            var inFlight = CompletableFuture.supplyAsync(() -> {
                try { return rawRequest(first, "POST", ReflectionHttpServer.SPEECH_PATH, remoteHeaders(), body); }
                catch (Exception failure) { throw new CompletionException(failure); }
            });
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertError(rawRequest(second, "POST", ReflectionHttpServer.SPEECH_PATH, remoteHeaders(), body),
                        503, "speech_pending");
                assertEquals(1, calls.get());
            } finally { release.countDown(); }
            RawHttpResponse completed = inFlight.get(3, TimeUnit.SECONDS);
            assertEquals(new RawHttpResponse(200, "ID3fixture"), completed);
            assertEquals(completed, rawRequest(second, "POST", ReflectionHttpServer.SPEECH_PATH, remoteHeaders(), body));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void missingReplayStorageDisablesOnlyPaidReflectionSpeech() throws Exception {
        var calls = new AtomicInteger();
        ReflectionHttpServer.SpeechProvider speech = text -> { calls.incrementAndGet(); return new byte[]{1}; };
        try (var server = ReflectionHttpServer.create(evidence -> exactCandidates(), 0,
                ReflectionHttpServer.GenerationMode.FAKE, speech, text -> new byte[]{2}, null)) {
            server.start();
            var health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.HEALTH_PATH)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(health.headers().firstValue(ReflectionHttpServer.REFLECTION_VOICE_CAPABILITY_HEADER).isEmpty());
            assertEquals("1", health.headers().firstValue(ReflectionHttpServer.RUN_VOICE_CAPABILITY_HEADER).orElseThrow());
            assertEquals(503, client.send(speechRequest(server, speechBody("Reflection.")),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, client.send(runVoiceRequest(server, speechBody("One mile.")),
                    HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            assertEquals(0, calls.get());
        }
    }

    @Test
    void speechAndReflectionGenerationShareTheSameBoundedProviderSlots() throws Exception {
        for (boolean firstIsSpeech : List.of(true, false)) {
            String freshSpeech = speechBody(firstIsSpeech ? "Speech starts first." : "Reflection starts first.");
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            ReflectionProvider reflections = evidence -> {
                if (!firstIsSpeech) {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                return exactCandidates();
            };
            ReflectionHttpServer.SpeechProvider speech = text -> {
                if (firstIsSpeech) {
                    entered.countDown();
                    try { release.await(5, TimeUnit.SECONDS); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                }
                return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
            };
            try (ReflectionHttpServer server = startRemoteSpeech(reflections, speech, 1, 4)) {
                CompletableFuture<RawHttpResponse> running = CompletableFuture.supplyAsync(() -> {
                    try {
                        return firstIsSpeech ? rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH,
                                remoteHeaders(), freshSpeech) : rawPost(server, remoteHeaders(), validRequest());
                    } catch (Exception failure) { throw new CompletionException(failure); }
                });
                try {
                    assertTrue(entered.await(2, TimeUnit.SECONDS));
                    RawHttpResponse busy = firstIsSpeech ? rawPost(server, remoteHeaders(), validRequest())
                            : rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH,
                                    remoteHeaders(), freshSpeech);
                    assertError(busy, 503, "server_busy");
                } finally {
                    release.countDown();
                }
                assertEquals(200, running.get(3, TimeUnit.SECONDS).status());
            }
        }
    }

    @Test
    void speechFailuresAreSanitizedReleaseSlotsAndProtectAmbiguousCharges() throws Exception {
        for (FailureType type : FailureType.values()) {
            AtomicInteger calls = new AtomicInteger();
            try (ReflectionHttpServer server = startSpeech(text -> {
                if (calls.incrementAndGet() == 1) {
                    throw new ReflectionProviderException(type, "private-voice-text-and-key");
                }
                return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
            })) {
                HttpResponse<String> failed = client.send(speechRequest(server, speechBody("Exact reflection.")),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(type == FailureType.TIMEOUT ? 504
                        : type == FailureType.CONFIGURATION || type == FailureType.TRANSPORT ? 503 : 502,
                        failed.statusCode());
                assertFalse(failed.body().contains("private-voice-text-and-key"));
                assertFalse(failed.body().contains("Exact reflection."));
                assertTrue(failed.headers().firstValue("Content-Type").orElseThrow().startsWith("application/json"));
                assertError(client.send(speechRequest(server, speechBody("Exact reflection.")),
                        HttpResponse.BodyHandlers.ofString()), 503, "speech_pending");
                assertEquals(1, calls.get());
                assertEquals(200, client.send(speechRequest(server, speechBody("A different reflection.")),
                        HttpResponse.BodyHandlers.ofString()).statusCode());
                assertEquals(2, calls.get());
            }
        }
    }

    @Test
    void emptyOversizedOrUnexpectedSpeechOutputReturnsSanitizedJsonErrors() throws Exception {
        for (byte[] audio : List.of(new byte[0], new byte[ReflectionHttpServer.MAX_AUDIO_RESPONSE_BYTES + 1])) {
            try (ReflectionHttpServer server = startSpeech(text -> audio)) {
                assertEquals(502, client.send(speechRequest(server, speechBody("Exact reflection.")),
                        HttpResponse.BodyHandlers.ofString()).statusCode());
            }
        }
        try (ReflectionHttpServer server = startSpeech(text -> {
            throw new IllegalStateException("private-voice-text-and-key");
        })) {
            HttpResponse<String> response = client.send(speechRequest(server, speechBody("Exact reflection.")),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());
            assertFalse(response.body().contains("private-voice-text-and-key"));
        }
    }

    @Test
    void runVoiceIsSeparateFromTheReflectionVoiceAdvertisedOnlyWhenConfiguredAndBounded() throws Exception {
        byte[] runAudio = new byte[]{'I', 'D', '3', 7};
        AtomicReference<String> reflectionSpoken = new AtomicReference<>();
        AtomicReference<String> runSpoken = new AtomicReference<>();
        try (ReflectionHttpServer server = ReflectionHttpServer.create(evidence -> exactCandidates(), 0,
                ReflectionHttpServer.GenerationMode.ANTHROPIC, text -> {
                    reflectionSpoken.set(text);
                    return new byte[]{'I', 'D', '3', 1};
                }, text -> {
                    runSpoken.set(text);
                    return runAudio;
                }, new SpeechReplayStore(speechDirectory))) {
            server.start();
            HttpResponse<byte[]> response = client.send(runVoiceRequest(server, speechBody("Starting in ten.")),
                    HttpResponse.BodyHandlers.ofByteArray());
            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.HEALTH_PATH)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertArrayEquals(runAudio, response.body()),
                    () -> assertEquals("Starting in ten.", runSpoken.get()),
                    () -> assertNull(reflectionSpoken.get()),
                    () -> assertEquals("audio/mpeg", response.headers().firstValue("Content-Type").orElseThrow()),
                    () -> assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow()),
                    () -> assertEquals("1", health.headers().firstValue(
                            ReflectionHttpServer.RUN_VOICE_CAPABILITY_HEADER).orElseThrow()),
                    () -> assertEquals("1", health.headers().firstValue(
                            ReflectionHttpServer.REFLECTION_VOICE_CAPABILITY_HEADER).orElseThrow())
            );
            runSpoken.set(null);
            assertEquals(400, client.send(runVoiceRequest(server, speechBody("x".repeat(501))),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            assertNull(runSpoken.get());
            assertEquals(200, client.send(runVoiceRequest(server, speechBody("x".repeat(500))),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            // The reflection voice keeps its own path, provider and longer limit.
            assertEquals(200, client.send(speechRequest(server, speechBody("x".repeat(4_000))),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals("x".repeat(4_000), reflectionSpoken.get());
        }
        try (ReflectionHttpServer server = startSpeech(text -> new byte[]{'I', 'D', '3'})) {
            HttpResponse<String> health = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
                    + server.address().getPort() + ReflectionHttpServer.HEALTH_PATH)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(health.headers().firstValue(ReflectionHttpServer.RUN_VOICE_CAPABILITY_HEADER).isEmpty());
            assertEquals(503, client.send(runVoiceRequest(server, speechBody("One.")),
                    HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void runVoiceKeepsRemoteAuthenticationBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = ReflectionHttpServer.createRemote(evidence -> exactCandidates(), 0,
                ReflectionHttpServer.GenerationMode.ANTHROPIC,
                new ReflectionHttpServer.RemoteAccess("127.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN), null, text -> {
                    calls.incrementAndGet();
                    return "ID3fixture".getBytes(StandardCharsets.US_ASCII);
                })) {
            server.start();
            String path = ReflectionHttpServer.RUN_VOICE_PATH;
            String body = speechBody("One.");
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST), body),
                    401, "unauthorized");
            assertError(rawRequest(server, "POST", path, List.of("Host: " + DEMO_HOST,
                    "Authorization: Bearer incorrect"), body), 401, "unauthorized");
            assertEquals(0, calls.get());
            assertEquals(200, rawRequest(server, "POST", path, remoteHeaders(), body).status());
            assertEquals(1, calls.get());
            // Without a reflection voice, the reflection path still reports it is not configured.
            assertError(rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH, remoteHeaders(), body),
                    503, "provider_unavailable");
            assertEquals(1, calls.get());
        }
    }

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
            try (ReflectionHttpServer server = ReflectionHttpServer.create(evidence -> fixed, 0, mode, null, null, null,
                    mode == ReflectionHttpServer.GenerationMode.ANTHROPIC ? new WrittenReflectionReplayStore(speechDirectory) : null)) {
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
    void actualPairedGenerationAcknowledgementAndVersionSurviveIdempotentReplay() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
        JsonObject music = request.getAsJsonObject("evidence").getAsJsonObject("musicContext");
        music.remove("artistDescription");
        music.addProperty("provenance", "MEDIA_SESSION_PARTIAL");
        music.add("songCue", validSongCue());
        ReflectionProvider paired = new ReflectionProvider() {
            public CandidateReflections generate(CompletedRunEvidence evidence) {
                throw new AssertionError("HTTP must use the delivery result, not infer an acknowledgement.");
            }
            public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence) {
                calls.incrementAndGet();
                return new GeneratedReflections(exactCandidates(), evidence.musicContext().songCue().cueId());
            }
        };
        try (ReflectionHttpServer server = start(paired)) {
            HttpResponse<String> first = post(server, request.toString());
            HttpResponse<String> duplicate = post(server, request.toString());
            assertEquals(200, first.statusCode());
            assertEquals(first.body(), duplicate.body());
            assertEquals("controlled_cue_v1", JsonParser.parseString(first.body()).getAsJsonObject()
                    .get("songCueId").getAsString());
            assertEquals("1", first.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElseThrow());
            assertEquals("1", duplicate.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElseThrow());
            assertEquals(1, calls.get());
        }
        ReflectionProvider forged = new ReflectionProvider() {
            public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence) {
                return new GeneratedReflections(exactCandidates(), "another_cue_v1");
            }
        };
        try (ReflectionHttpServer server = start(forged)) {
            HttpResponse<String> response = post(server, request.toString());
            assertEquals(500, response.statusCode());
            assertFalse(response.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).isPresent());
        }
    }

    @Test
    void optionalCuratedSongCueReachesProviderAndLegacyMusicStillParses() throws Exception {
        AtomicReference<CompletedRunEvidence> captured = new AtomicReference<>();
        JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
        JsonObject music = request.getAsJsonObject("evidence").getAsJsonObject("musicContext");
        music.remove("artistDescription");
        music.addProperty("provenance", "MEDIA_SESSION_PARTIAL");
        music.add("songCue", validSongCue());
        try (ReflectionHttpServer server = start(evidence -> {
            captured.set(evidence);
            return exactCandidates();
        })) {
            HttpResponse<String> response = post(server, request.toString());
            assertEquals(200, response.statusCode());
            assertFalse(JsonParser.parseString(response.body()).getAsJsonObject().has("songCueId"),
                    "A plain provider result must not echo an unacknowledged request cue.");
            assertFalse(response.headers().firstValue(ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).isPresent());
            assertAll(
                    () -> assertEquals(new SongCueContext("controlled_cue_v1", SongCueSource.RUNSTATE_CURATED,
                            "Self-directed momentum", "Recognize completing this run without inventing struggle."),
                            captured.get().musicContext().songCue()),
                    () -> assertEquals(MusicProvenance.MEDIA_SESSION_PARTIAL,
                            captured.get().musicContext().provenance()),
                    () -> assertNull(captured.get().musicContext().artistDescription())
            );
        }
        music.remove("songCue");
        assertNull(ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8))
                .evidence().musicContext().songCue());
        music.add("songCue", null);
        assertNull(ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8))
                .evidence().musicContext().songCue());
    }

    @Test
    void invalidSongCueBodiesNeverCallProviderOrAcceptLyricsAndAudioFields() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            providerCalls.incrementAndGet();
            return exactCandidates();
        })) {
            for (String field : List.of("cueId", "source", "theme", "connectionNote")) {
                JsonObject missing = validSongCue();
                missing.remove(field);
                JsonObject nullField = validSongCue();
                nullField.add(field, null);
                JsonObject wrongType = validSongCue();
                wrongType.addProperty(field, 12);
                for (JsonObject invalid : List.of(missing, nullField, wrongType)) {
                    JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                    request.getAsJsonObject("evidence").getAsJsonObject("musicContext").add("songCue", invalid);
                    assertError(post(server, request.toString()), 400, "invalid_request");
                }
            }
            for (String field : List.of("lyrics", "audioUri", "passageStartMillis")) {
                JsonObject extra = validSongCue();
                extra.addProperty(field, "not accepted");
                JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                request.getAsJsonObject("evidence").getAsJsonObject("musicContext").add("songCue", extra);
                assertError(post(server, request.toString()), 400, "invalid_request");
            }
            for (String invalid : List.of("[]", "\"not an object\"", "false",
                    validSongCue().toString().replace("RUNSTATE_CURATED", "SPOTIFY"),
                    validSongCue().toString().replace("controlled_cue_v1", "../unbounded-catalog"))) {
                JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                request.getAsJsonObject("evidence").getAsJsonObject("musicContext")
                        .add("songCue", JsonParser.parseString(invalid));
                assertError(post(server, request.toString()), 400, "invalid_request");
            }
        }
        assertEquals(0, providerCalls.get());
    }

    private static JsonObject validSongCue() {
        JsonObject cue = new JsonObject();
        cue.addProperty("cueId", "controlled_cue_v1");
        cue.addProperty("source", "RUNSTATE_CURATED");
        cue.addProperty("theme", "Self-directed momentum");
        cue.addProperty("connectionNote", "Recognize completing this run without inventing struggle.");
        return cue;
    }

    @Test
    void optionalHourlyWeatherReachesProviderWithoutLookupCoordinates() throws Exception {
        JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
        request.getAsJsonObject("evidence").add("weatherContext", validWeather());
        AtomicReference<CompletedRunEvidence> captured = new AtomicReference<>();
        try (ReflectionHttpServer server = start(evidence -> {
            captured.set(evidence);
            return exactCandidates();
        })) {
            assertEquals(200, post(server, request.toString()).statusCode());
            assertAll(
                    () -> assertEquals(1_780_000_000_000L, captured.get().weatherContext().targetEpochMillis()),
                    () -> assertEquals(48.5, captured.get().weatherContext().relativeHumidityPercent()),
                    () -> assertEquals(21.5, captured.get().weatherContext().temperatureCelsius())
            );
        }
        request.getAsJsonObject("evidence").remove("weatherContext");
        assertNull(ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8))
                .evidence().weatherContext());
        request.getAsJsonObject("evidence").add("weatherContext", null);
        assertNull(ReflectionApiJson.parseRequest(request.toString().getBytes(StandardCharsets.UTF_8))
                .evidence().weatherContext());
    }

    @Test
    void rejectsMalformedOrUnownedWeatherBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        })) {
            for (String field : validWeather().keySet()) {
                JsonObject incomplete = validWeather();
                incomplete.remove(field);
                JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                request.getAsJsonObject("evidence").add("weatherContext", incomplete);
                assertError(post(server, request.toString()), 400, "invalid_request");
                JsonObject withNull = validWeather();
                withNull.add(field, null);
                request.getAsJsonObject("evidence").add("weatherContext", withNull);
                assertError(post(server, request.toString()), 400, "invalid_request");
            }
            for (String invalidJson : List.of(
                    "[]", "true", "{\"state\":\"UNKNOWN\"}",
                    validWeather().toString().replace("\"weatherCode\":0", "\"weatherCode\":0.5"),
                    validWeather().toString().replace("\"temperatureCelsius\":21.5", "\"temperatureCelsius\":1e999"),
                    validWeather().toString().replace("OPEN_METEO", "OTHER"),
                    validWeather().toString().replace("HOURLY", "DAILY"),
                    validWeather().toString().replace("\"targetEpochMillis\":1780000000000",
                            "\"targetEpochMillis\":1780000000001"),
                    validWeather().toString().replace("\"weatherCode\":0", "\"weatherCode\":4294967296")
            )) {
                JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
                request.getAsJsonObject("evidence").add("weatherContext", JsonParser.parseString(invalidJson));
                assertError(post(server, request.toString()), 400, "invalid_request");
            }
            JsonObject coordinates = validWeather();
            coordinates.addProperty("latitude", 29.72);
            JsonObject request = JsonParser.parseString(validRequest()).getAsJsonObject();
            request.getAsJsonObject("evidence").add("weatherContext", coordinates);
            assertError(post(server, request.toString()), 400, "invalid_request");
            assertEquals(0, calls.get());
        }
    }

    private static JsonObject validWeather() {
        long start = 1_780_000_000_000L;
        long hour = start / 3_600_000L * 3_600_000L;
        JsonObject weather = new JsonObject();
        weather.addProperty("source", "OPEN_METEO");
        weather.addProperty("resolution", "HOURLY");
        weather.addProperty("targetEpochMillis", start);
        weather.addProperty("validFromEpochMillis", hour);
        weather.addProperty("validToEpochMillis", hour + 3_600_000L);
        weather.addProperty("fetchedAtEpochMillis", start + 1_800_000L);
        weather.addProperty("temperatureCelsius", 21.5);
        weather.addProperty("apparentTemperatureCelsius", 20.0);
        weather.addProperty("relativeHumidityPercent", 48.5);
        weather.addProperty("windSpeedKmh", 8.0);
        weather.addProperty("weatherCode", 0);
        return weather;
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
    void rejectsMissingOrDuplicateHostBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        })) {
            List<List<String>> rejectedHeaders = List.of(
                    List.of(),
                    List.of(hostHeader(server, "127.0.0.1"), hostHeader(server, "localhost"))
            );
            for (List<String> headers : rejectedHeaders) {
                assertMissingOrDuplicateHostRejected(rawPost(server, headers, validRequest()));
                assertEquals(0, calls.get(), "Rejected Host headers must not reach the provider: " + headers);
            }
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
            int wrongPort = server.address().getPort() == 65_535
                    ? 65_534
                    : server.address().getPort() + 1;
            List<List<String>> rejectedHeaders = List.of(
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
    void individualConfigurationCannotSilentlyFallBackToTheSharedOwnerCredential() {
        Map<String, String> bothModes = Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", DEMO_HOST,
                "RUNSTATE_DEMO_TOKEN", DUMMY_DEMO_TOKEN,
                "RUNSTATE_ACCESS_GRANTS_JSON", "[]");
        assertThrows(IllegalArgumentException.class, () -> ReflectionBackendMain.configuredRemoteAccess(bothModes));
        assertThrows(IllegalArgumentException.class, () -> ReflectionBackendMain.configuredRemoteAccess(Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", DEMO_HOST, "RUNSTATE_ACCESS_GRANTS_JSON", "")));
        assertThrows(IllegalArgumentException.class, () -> ReflectionBackendMain.configuredRemoteAccess(Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", DEMO_HOST, "RUNSTATE_ACCESS_GRANTS_JSON", "[{}]")));
        assertFalse(ReflectionBackendMain.configuredRemoteAccess(Map.of(
                "RUNSTATE_REFLECTION_PUBLIC_HOST", DEMO_HOST, "RUNSTATE_ACCESS_GRANTS_JSON", "[]"))
                .toString().contains("[]"));
    }

    @Test
    void individualRequestsAndCredentialRotationKeepTheirOwnCachedResults() throws Exception {
        String secondToken = "B".repeat(43), rotatedToken = "C".repeat(43);
        String grants = "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, "2099-01-01T00:00:00Z", true)
                + "," + accessGrant("runner-b", secondToken, "2099-01-01T00:00:00Z", true)
                + "," + accessGrant("runner-a", rotatedToken, "2099-01-01T00:00:00Z", true) + "]";
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = evidence -> {
            String text = "Private result " + calls.incrementAndGet();
            return new CandidateReflections(text, text, text, text);
        };
        try (var server = startIndividual(provider, BackendAccessPolicy.fromJson(grants), null, null)) {
            var first = rawPost(server, accessHeaders(DUMMY_DEMO_TOKEN), validRequest());
            var second = rawPost(server, accessHeaders(secondToken), validRequest());
            assertEquals(200, first.status());
            assertEquals(200, second.status());
            assertTrue(first.body().contains("Private result 1"));
            assertTrue(second.body().contains("Private result 2"));
            assertEquals(first, rawPost(server, accessHeaders(rotatedToken), validRequest()));
            assertEquals(second, rawPost(server, accessHeaders(secondToken), validRequest()));
            assertError(rawPost(server, accessHeaders(DUMMY_DEMO_TOKEN),
                    validRequest().replace(RUN_ID, OTHER_RUN_ID)), 409, "request_id_conflict");
            assertEquals(2, calls.get());
            for (String secret : List.of("runner-a", "runner-b", DUMMY_DEMO_TOKEN, secondToken)) {
                assertFalse(first.body().contains(secret));
                assertFalse(second.body().contains(secret));
            }
        }
    }

    @Test
    void simultaneousRecipientsDoNotJoinEachOthersInFlightWork() throws Exception {
        String secondToken = "B".repeat(43);
        String grants = "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, "2099-01-01T00:00:00Z", true)
                + "," + accessGrant("runner-b", secondToken, "2099-01-01T00:00:00Z", true) + "]";
        var bothEntered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = evidence -> {
            int number = calls.incrementAndGet();
            bothEntered.countDown();
            try {
                if (!release.await(4, TimeUnit.SECONDS)) throw new IllegalStateException("Test release timed out");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
            }
            String text = "Private result " + number;
            return new CandidateReflections(text, text, text, text);
        };
        try (var server = startIndividual(provider, BackendAccessPolicy.fromJson(grants), null, null)) {
            var first = rawPostAsync(server, remoteHeaders(), validRequest());
            var second = rawPostAsync(server, accessHeaders(secondToken), validRequest());
            try {
                assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
            } finally {
                release.countDown();
            }
            var firstResult = first.get(3, TimeUnit.SECONDS);
            var secondResult = second.get(3, TimeUnit.SECONDS);
            assertEquals(200, firstResult.status()); assertEquals(200, secondResult.status());
            assertFalse(firstResult.equals(secondResult));
            assertEquals(2, calls.get());
        }
    }

    @Test
    void individualExpiryIsRecheckedBeforeCachedResultsAndEveryPaidEndpoint() throws Exception {
        var now = new AtomicReference<>(java.time.Instant.parse("2026-11-07T12:00:00Z"));
        var clock = new java.time.Clock() {
            @Override public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            @Override public java.time.Clock withZone(java.time.ZoneId zone) { return this; }
            @Override public java.time.Instant instant() { return now.get(); }
        };
        String grants = "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, "2026-11-07T12:00:01Z", true) + "]";
        AtomicInteger calls = new AtomicInteger();
        var provider = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) {
                calls.incrementAndGet(); return exactCandidates();
            }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) {
                calls.incrementAndGet(); return SongPreparationState.READY;
            }
        };
        ReflectionHttpServer.SpeechProvider voice = text -> { calls.incrementAndGet(); return new byte[]{1, 2}; };
        try (var server = startIndividual(provider, BackendAccessPolicy.fromJson(grants, clock), voice, voice)) {
            assertEquals(200, rawPost(server, remoteHeaders(), validRequest()).status());
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SPEECH_PATH,
                    remoteHeaders(), speechBody("Reflection.")).status());
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.RUN_VOICE_PATH,
                    remoteHeaders(), speechBody("One mile.")).status());
            assertEquals(200, rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                    remoteHeaders(), preparationBody(REQUEST_ID)).status());
            assertEquals(4, calls.get());
            now.set(java.time.Instant.parse("2026-11-07T12:00:01Z"));
            assertError(rawPost(server, remoteHeaders(), validRequest()), 401, "unauthorized");
            for (String path : List.of(ReflectionHttpServer.CANDIDATES_PATH, ReflectionHttpServer.SPEECH_PATH,
                    ReflectionHttpServer.RUN_VOICE_PATH, ReflectionHttpServer.SONG_PREPARATION_PATH)) {
                assertError(rawRequest(server, "POST", path, remoteHeaders(), "{"), 401, "unauthorized");
            }
            assertEquals(4, calls.get());
            assertEquals(200, rawRequest(server, "GET", ReflectionHttpServer.HEALTH_PATH,
                    List.of("Host: " + DEMO_HOST), "").status());
        }
    }

    @Test
    void disabledRemovedMissingOrInvalidIndividualCredentialsMakeNoProviderCalls() throws Exception {
        String activeToken = "A".repeat(43);
        AtomicInteger calls = new AtomicInteger();
        ReflectionProvider provider = evidence -> { calls.incrementAndGet(); return exactCandidates(); };
        ReflectionHttpServer.SpeechProvider voice = text -> { calls.incrementAndGet(); return new byte[]{1}; };
        for (String grants : List.of("[]", "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN,
                "2099-01-01T00:00:00Z", false) + "]", "[" + accessGrant("runner-a", activeToken,
                "2099-01-01T00:00:00Z", true) + "]")) {
            try (var server = startIndividual(provider, BackendAccessPolicy.fromJson(grants), voice, voice)) {
                for (String path : List.of(ReflectionHttpServer.CANDIDATES_PATH, ReflectionHttpServer.SPEECH_PATH,
                        ReflectionHttpServer.RUN_VOICE_PATH, ReflectionHttpServer.SONG_PREPARATION_PATH)) {
                    for (List<String> headers : List.of(remoteHeaders(), List.of("Host: " + DEMO_HOST),
                            List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + activeToken,
                                    "Authorization: Bearer " + activeToken))) {
                        assertError(rawRequest(server, "POST", path, headers, "{"), 401, "unauthorized");
                    }
                }
            }
        }
        assertEquals(0, calls.get());
    }

    @Test
    void individualPreparationHandlesAreIsolatedForSingleAndMultipleSongLookups() throws Exception {
        String secondToken = "B".repeat(43);
        String grants = "[" + accessGrant("runner-a", DUMMY_DEMO_TOKEN, "2099-01-01T00:00:00Z", true)
                + "," + accessGrant("runner-b", secondToken, "2099-01-01T00:00:00Z", true) + "]";
        Map<String, String> prepared = new java.util.concurrent.ConcurrentHashMap<>();
        var delivered = new AtomicReference<String>();
        var deliveredCandidates = new AtomicReference<List<ReflectionContract.SongCandidate>>();
        var deliveredFlags = new AtomicReference<List<Boolean>>();
        AtomicInteger preparations = new AtomicInteger();
        var provider = new ReflectionProvider() {
            @Override public CandidateReflections generate(CompletedRunEvidence evidence) { return exactCandidates(); }
            @Override public boolean supportsSongPreparation() { return true; }
            @Override public boolean supportsSongSelection() { return true; }
            @Override public boolean supportsSongSnippets() { return true; }
            @Override public boolean supportsSongSnippetFallbacks() { return true; }
            @Override public SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request) {
                preparations.incrementAndGet();
                prepared.put(request.trackTitle(), request.preparationId());
                return SongPreparationState.READY;
            }
            @Override public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String handle,
                    List<ReflectionContract.SongCandidate> candidates, boolean snippets, boolean fallbacks) {
                delivered.set(handle); deliveredCandidates.set(candidates);
                deliveredFlags.set(List.of(snippets, fallbacks));
                return new GeneratedReflections(exactCandidates(), null);
            }
        };
        // Recreating the server proves stable namespacing, not durable response deduplication.
        Map<String, String> firstHandles = new java.util.HashMap<>();
        for (int restart = 0; restart < 2; restart++) {
            try (var server = startIndividual(provider, BackendAccessPolicy.fromJson(grants), null, null)) {
                for (String token : List.of(DUMMY_DEMO_TOKEN, secondToken)) {
                    String title = token.equals(DUMMY_DEMO_TOKEN) ? "Controlled track" : "Other track";
                    String prepBody = preparationBody(REQUEST_ID).replace("Controlled track", title);
                    var response = rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                            accessHeaders(token), prepBody);
                    assertEquals(200, response.status());
                    assertEquals(REQUEST_ID, JsonParser.parseString(response.body()).getAsJsonObject()
                            .get("preparationId").getAsString());
                    assertEquals(response, rawRequest(server, "POST", ReflectionHttpServer.SONG_PREPARATION_PATH,
                            accessHeaders(token), prepBody));
                    String handle = prepared.get(title);
                    assertFalse(REQUEST_ID.equals(handle));
                    assertEquals(handle, java.util.UUID.fromString(handle).toString());
                    assertFalse(response.body().contains(handle));
                    if (restart == 0) firstHandles.put(title, handle);
                    else assertEquals(firstHandles.get(title), handle);

                    JsonObject single = JsonParser.parseString(validRequest().replace("Controlled track", title))
                            .getAsJsonObject();
                    single.addProperty("songPreparationId", REQUEST_ID);
                    assertEquals(200, rawPost(server, accessHeaders(token), single.toString()).status());
                    assertEquals(handle, delivered.get());

                    JsonObject multi = multiSongRequest();
                    multi.addProperty("requestId", SECOND_REQUEST_ID);
                    multi.addProperty("allowSongSnippetLookup", true);
                    multi.addProperty("allowSongSnippetFallbacks", true);
                    multi.getAsJsonArray("songCandidates").get(0).getAsJsonObject()
                            .addProperty("songPreparationId", REQUEST_ID);
                    JsonObject recording = new JsonObject();
                    recording.addProperty("albumName", "Controlled album");
                    recording.addProperty("durationMillis", 180_000);
                    multi.getAsJsonArray("songCandidates").get(0).getAsJsonObject().add("recording", recording);
                    // The finish-song mark must survive the same recipient scoping as preparation IDs.
                    int loadedIndex = token.equals(DUMMY_DEMO_TOKEN) ? 0 : 1;
                    multi.getAsJsonArray("songCandidates").get(loadedIndex).getAsJsonObject()
                            .addProperty("loadedAtFinish", true);
                    assertEquals(200, rawPost(server, accessHeaders(token), multi.toString()).status());
                    assertEquals(loadedIndex == 0 ? List.of(true, false) : List.of(false, true),
                            deliveredCandidates.get().stream()
                                    .map(ReflectionContract.SongCandidate::loadedAtFinish).toList());
                    assertEquals(handle, deliveredCandidates.get().get(0).songPreparationId());
                    assertEquals("1", deliveredCandidates.get().get(0).candidateId());
                    assertEquals(new ReflectionContract.SongRecording("Controlled album", 180_000),
                            deliveredCandidates.get().get(0).recording());
                    assertEquals("Observed song 1", deliveredCandidates.get().get(0).musicContext().trackTitle());
                    assertEquals("Observed artist", deliveredCandidates.get().get(0).musicContext().artistName());
                    assertNull(deliveredCandidates.get().get(1).songPreparationId());
                    assertEquals(List.of(true, true), deliveredFlags.get());
                }
                assertFalse(prepared.get("Controlled track").equals(prepared.get("Other track")));
            }
        }
        assertEquals(4, preparations.get());
    }

    private static String accessGrant(String principal, String token, String expiry, boolean enabled) throws Exception {
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.US_ASCII)));
        JsonObject grant = new JsonObject();
        grant.addProperty("principalId", principal); grant.addProperty("tokenSha256", hash);
        grant.addProperty("expiresAt", expiry); grant.addProperty("enabled", enabled);
        return grant.toString();
    }

    private static List<String> accessHeaders(String token) {
        return List.of("Host: " + DEMO_HOST, "Authorization: Bearer " + token);
    }

    private ReflectionHttpServer startIndividual(ReflectionProvider provider, BackendAccessPolicy policy,
            ReflectionHttpServer.SpeechProvider voice, ReflectionHttpServer.SpeechProvider runVoice) throws Exception {
        var server = ReflectionHttpServer.createRemote(provider, 0, ReflectionHttpServer.GenerationMode.FAKE,
                ReflectionHttpServer.RemoteAccess.individual("127.0.0.1", DEMO_HOST, policy),
                ReflectionHttpServer.MAX_CONCURRENT_PROVIDER_CALLS, ReflectionHttpServer.MAX_CACHED_REQUESTS,
                voice, runVoice, voice == null ? null : new SpeechReplayStore(speechDirectory));
        server.start();
        return server;
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
    void remoteMissingOrDuplicateHostIsRejectedEvenWithAValidToken() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try (ReflectionHttpServer server = startRemote(evidence -> {
            calls.incrementAndGet();
            return exactCandidates();
        }, 2, 8)) {
            List<List<String>> rejectedHeaders = List.of(
                    List.of("Authorization: Bearer " + DUMMY_DEMO_TOKEN),
                    List.of("Host: " + DEMO_HOST, "Host: " + DEMO_HOST,
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN)
            );
            for (List<String> headers : rejectedHeaders) {
                assertMissingOrDuplicateHostRejected(rawPost(server, headers, validRequest()));
                assertEquals(0, calls.get(), "Rejected Host headers must not reach the provider");
            }
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
                    List.of("Host: localhost:" + server.address().getPort(),
                            "Authorization: Bearer " + DUMMY_DEMO_TOKEN, "X-Forwarded-Host: " + DEMO_HOST),
                    List.of("Host: " + DEMO_HOST + ":8443", "Authorization: Bearer " + DUMMY_DEMO_TOKEN),
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
    void localHealthAdvertisesSongCueWithoutChangingBodyOrCallingProvider() throws Exception {
        AtomicInteger providerCalls = new AtomicInteger();
        try (ReflectionHttpServer server = start(evidence -> {
            providerCalls.incrementAndGet();
            return exactCandidates();
        })) {
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.address().getPort() + "/health"))
                            .GET().build(), HttpResponse.BodyHandlers.ofString());
            assertAll(
                    () -> assertEquals(200, response.statusCode()),
                    () -> assertEquals("{\"status\":\"ok\"}", response.body()),
                    () -> assertEquals("1", response.headers().firstValue(
                            ReflectionHttpServer.SONG_CUE_CAPABILITY_HEADER).orElseThrow()),
                    () -> assertEquals("1", response.headers().firstValue(
                            ReflectionHttpServer.SONG_STUDY_CAPABILITY_HEADER).orElseThrow()),
                    () -> assertEquals(0, providerCalls.get())
            );
            JsonObject oldResponse = JsonParser.parseString(post(server, validRequest()).body()).getAsJsonObject();
            assertFalse(oldResponse.has("songCueId"));
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

    @Test
    void framedResponseEndsAtItsByteLengthWithoutWaitingForConnectionClose() throws Exception {
        String body = "{\"message\":\"Exact response — café\"}";
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        byte[] headerBytes = ("HTTP/1.1 413 Payload Too Large\r\nContent-Length: " + bodyBytes.length
                + "\r\nContent-Type: application/json\r\n\r\n").getBytes(StandardCharsets.US_ASCII);
        byte[] response = new byte[headerBytes.length + bodyBytes.length];
        System.arraycopy(headerBytes, 0, response, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, response, headerBytes.length, bodyBytes.length);
        InputStream resetAfterResponse = new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position == response.length) throw new IOException("Connection reset after complete response");
                return Byte.toUnsignedInt(response[position++]);
            }
        };

        assertEquals(new RawHttpResponse(413, body), readFramedResponse(resetAfterResponse));
    }

    @Test
    void framedResponseRejectsTruncationResetAndUnboundedOrAmbiguousFraming() {
        byte[] truncated = "HTTP/1.1 403 Forbidden\r\nContent-Length: 5\r\n\r\nabc"
                .getBytes(StandardCharsets.US_ASCII);
        InputStream resetBeforeComplete = new InputStream() {
            private int position;

            @Override
            public int read() throws IOException {
                if (position == truncated.length) throw new IOException("Connection reset before complete response");
                return Byte.toUnsignedInt(truncated[position++]);
            }
        };
        assertAll(
                () -> assertThrows(EOFException.class,
                        () -> readFramedResponse(new ByteArrayInputStream(truncated))),
                () -> assertThrows(IOException.class, () -> readFramedResponse(resetBeforeComplete)),
                () -> assertThrows(AssertionError.class, () -> readFramedResponse(new ByteArrayInputStream(
                        ("HTTP/1.1 200 OK\r\nContent-Length: " + (ReflectionHttpServer.MAX_RESPONSE_BYTES + 1)
                                + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII)))),
                () -> assertThrows(AssertionError.class, () -> readFramedResponse(new ByteArrayInputStream(
                        "HTTP/1.1 200 OK\r\nContent-Length: 3\r\nContent-Length: 3\r\n\r\nabc"
                                .getBytes(StandardCharsets.US_ASCII)))),
                () -> assertThrows(AssertionError.class, () -> readFramedResponse(new ByteArrayInputStream(
                        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Length: 3\r\n\r\nabc"
                                .getBytes(StandardCharsets.US_ASCII)))),
                () -> assertThrows(AssertionError.class, () -> readFramedResponse(new ByteArrayInputStream(
                        ("HTTP/1.1 200 OK\r\nX-Padding: " + "x".repeat(16 * 1024))
                                .getBytes(StandardCharsets.US_ASCII))))
        );
    }

    private ReflectionHttpServer startSpeech(ReflectionHttpServer.SpeechProvider speech) throws Exception {
        ReflectionHttpServer server = ReflectionHttpServer.create(evidence -> exactCandidates(), 0,
                ReflectionHttpServer.GenerationMode.ANTHROPIC, speech, null,
                new SpeechReplayStore(Files.createTempDirectory(speechDirectory, "voice-")));
        server.start();
        return server;
    }

    private ReflectionHttpServer startRemoteSpeech(ReflectionProvider provider,
            ReflectionHttpServer.SpeechProvider speech, int providerCalls, int cachedRequests) throws Exception {
        ReflectionHttpServer server = ReflectionHttpServer.createRemote(provider, 0,
                ReflectionHttpServer.GenerationMode.ANTHROPIC,
                new ReflectionHttpServer.RemoteAccess("127.0.0.1", DEMO_HOST, DUMMY_DEMO_TOKEN),
                providerCalls, cachedRequests, speech, null, new SpeechReplayStore(speechDirectory),
                new WrittenReflectionReplayStore(Files.createTempDirectory(speechDirectory, "written-")));
        server.start();
        return server;
    }

    private static String speechBody(String text) {
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        return body.toString();
    }

    private static HttpRequest runVoiceRequest(ReflectionHttpServer server, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.address().getPort()
                        + ReflectionHttpServer.RUN_VOICE_PATH)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
    }

    private static HttpRequest speechRequest(ReflectionHttpServer server, String body) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.address().getPort()
                        + ReflectionHttpServer.SPEECH_PATH)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
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

        try (Socket socket = new Socket()) {
            socket.connect(server.address(), 2_000);
            socket.setSoTimeout(5_000);
            socket.getOutputStream().write(requestHead.getBytes(StandardCharsets.US_ASCII));
            // Oversized declared lengths must be rejected from headers, even before the payload arrives.
            // Sending unread bytes while the peer closes can reset the socket before Windows delivers its 413.
            if (bodyBytes.length <= ReflectionHttpServer.MAX_REQUEST_BYTES) socket.getOutputStream().write(bodyBytes);
            socket.getOutputStream().flush();
            if (bodyBytes.length > ReflectionHttpServer.MAX_REQUEST_BYTES) socket.shutdownOutput();
            return readFramedResponse(socket.getInputStream());
        }
    }

    /** The server sends fixed-length responses; TCP EOF is not part of their HTTP framing. */
    private static RawHttpResponse readFramedResponse(InputStream input) throws IOException {
        ByteArrayOutputStream headers = new ByteArrayOutputStream();
        int delimiterBytes = 0;
        byte[] delimiter = {'\r', '\n', '\r', '\n'};
        while (delimiterBytes < delimiter.length) {
            int next = input.read();
            if (next < 0) throw new EOFException("Server returned incomplete HTTP headers");
            if (headers.size() >= 16 * 1024) throw new AssertionError("HTTP response headers exceed the test limit");
            headers.write(next);
            delimiterBytes = next == delimiter[delimiterBytes] ? delimiterBytes + 1 : next == '\r' ? 1 : 0;
        }
        String[] lines = headers.toString(StandardCharsets.US_ASCII).split("\r\n");
        String[] statusParts = lines[0].split(" ", 3);
        if (statusParts.length < 2 || !"HTTP/1.1".equals(statusParts[0]) ||
                !statusParts[1].matches("[1-5][0-9]{2}")) {
            throw new AssertionError("Server returned an invalid HTTP status line");
        }

        Integer contentLength = null;
        for (int index = 1; index < lines.length; index++) {
            int separator = lines[index].indexOf(':');
            if (separator <= 0) throw new AssertionError("Server returned an invalid HTTP header");
            String name = lines[index].substring(0, separator);
            String value = lines[index].substring(separator + 1).trim();
            if ("Transfer-Encoding".equalsIgnoreCase(name)) {
                throw new AssertionError("A fixed-length test response cannot use Transfer-Encoding");
            }
            if ("Content-Length".equalsIgnoreCase(name)) {
                if (contentLength != null || !value.matches("[0-9]{1,6}")) {
                    throw new AssertionError("HTTP response needs one bounded Content-Length");
                }
                contentLength = Integer.parseInt(value);
                if (contentLength > ReflectionHttpServer.MAX_RESPONSE_BYTES) {
                    throw new AssertionError("HTTP response body exceeds the test limit");
                }
            }
        }
        if (contentLength == null) throw new AssertionError("HTTP response is missing Content-Length");
        byte[] body = input.readNBytes(contentLength);
        if (body.length != contentLength) throw new EOFException("Server returned an incomplete HTTP response body");

        return new RawHttpResponse(
                Integer.parseInt(statusParts[1]),
                new String(body, StandardCharsets.UTF_8)
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
        assertEquals(expectedStatus, response.status(), "Unexpected HTTP error status");
        JsonObject error = JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonObject("error");
        assertAll(
                () -> assertEquals(expectedCode, error.get("code").getAsString()),
                () -> assertTrue(error.get("message").getAsString().length() > 0)
        );
    }

    private static void assertMissingOrDuplicateHostRejected(RawHttpResponse response) {
        // JDK 17.0.21+ rejects these HTTP/1.1 requests with 400 before our handler.
        // Older JDKs reach RunState's own Host guard, which must still return its exact error.
        if (response.status() != 400) {
            assertError(response, 403, "request_rejected");
        }
    }

    static String validRequest() {
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
