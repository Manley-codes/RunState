package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Manual loopback comparison. Never launched by Surefire, the mobile app, or the hosted backend. */
public final class SampleRunCostComparison {
    private record Song(String title, String artist) {}
    private record ModelStep(String step, String model) {}
    private static final List<String> TOKEN_FIELDS = List.of("input", "output", "cache_read",
            "cache_write_total", "cache_write_5m", "cache_write_1h");
    // Public stand-ins. Each eligible observation represents at least one minute of this synthetic run.
    private static final List<Song> SONGS = List.of(new Song("Blinding Lights", "The Weeknd"),
            new Song("Levitating", "Dua Lipa"), new Song("Shake It Off", "Taylor Swift"),
            new Song("Happy", "Pharrell Williams"), new Song("Uptown Funk", "Mark Ronson"),
            new Song("Rolling in the Deep", "Adele"), new Song("Bad Romance", "Lady Gaga"),
            new Song("Firework", "Katy Perry"), new Song("Freedom! '90", "George Michael"),
            new Song("Don't Stop Me Now", "Queen"));
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(5)).build();

    public static void main(String[] args) throws Exception {
        boolean live = Arrays.equals(args, new String[]{"--live"});
        if (!live && !Arrays.equals(args, new String[]{"--offline"}))
            throw new IllegalArgumentException("Specify --offline or explicitly opt into --live.");
        Map<String, String> env = System.getenv();
        Path storage;
        ProviderSpendBudget budget;
        if (live) {
            if (!"1".equals(env.get("RUNSTATE_SAMPLE_LIVE")) || !"1".equals(env.get("RUNSTATE_LOCAL_USAGE_METER"))
                    || !"1".equals(System.getProperty("jdk.httpclient.redirects.retrylimit")))
                throw new IllegalStateException("Live samples require explicit opt-in, usage measurement, and the one-attempt JVM setting.");
            var limits = ProviderSpendBudget.Limits.fromEnvironment(env);
            if (limits.runnerMonthly() <= 0 || limits.globalMonthly() <= 0 || limits.anthropicMaxPerCall() == null ||
                    limits.elevenLabsMaxPerCall() == null || limits.musixmatchMaxPerCall() == null)
                throw new IllegalStateException("Set approved monthly budgets and per-call ceilings for all three providers.");
            for (String name : List.of("ANTHROPIC_API_KEY", "ELEVENLABS_API_KEY", "MUSIXMATCH_API_KEY", "RUNSTATE_SPEECH_REPLAY_DIR"))
                if (env.getOrDefault(name, "").isBlank()) throw new IllegalStateException("Missing backend setting " + name);
            storage = Path.of(env.get("RUNSTATE_SPEECH_REPLAY_DIR"));
            if (!storage.isAbsolute()) throw new IllegalStateException("Use the existing absolute private replay directory.");
            budget = new ProviderSpendBudget(storage, limits);
        } else {
            // Disposable offline data and artificial spend units have no connection to the operator's ledger.
            storage = Files.createTempDirectory("runstate-offline-comparison-");
            storage.toFile().deleteOnExit();
            budget = new ProviderSpendBudget(storage, new ProviderSpendBudget.Limits(1000, 1000, 1, 1, 1));
        }
        try {
            for (int songs : List.of(4, 10)) compare(songs, live, storage, budget, env);
        } finally {
            if (!live) try (var files = Files.list(storage)) {
                files.forEach(file -> file.toFile().deleteOnExit());
            }
        }
    }

    private static void compare(int songCount, boolean live, Path storage, ProviderSpendBudget budget,
                                Map<String,String> env) throws Exception {
        var counters = new ByteArrayOutputStream();
        var meter = LocalProviderUsage.configured(Map.of("RUNSTATE_LOCAL_USAGE_METER","1"), false, new PrintStream(counters));
        ReflectionProvider model = live ? new AnthropicReflectionProvider() : AnthropicReflectionProviderTest.comparisonModel(
                MusixmatchSongAnalysisTest.comparisonCatalog()::lookupReflectionMaterial);
        var voice = live ? ElevenLabsReflectionSpeech.fromEnvironment(env).orElseThrow() :
                new ElevenLabsReflectionSpeech("offline-only", request ->
                        new ElevenLabsReflectionSpeech.TransportResponse(200, "audio/mpeg", new byte[]{1,2}));
        boolean complete = false;
        int ready = 0;
        try (var server = new ReflectionHttpServer(model, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC, null,
                2, 256, voice, null, new SpeechReplayStore(storage), new WrittenReflectionReplayStore(storage), budget, null, meter)) {
            server.start();
            URI base = URI.create("http://127.0.0.1:" + server.address().getPort());
            JsonObject run = JsonParser.parseString(ReflectionHttpServerTest.validRequest()).getAsJsonObject();
            run.addProperty("requestId", UUID.randomUUID().toString());
            run.addProperty("runId", UUID.randomUUID().toString());
            JsonObject evidence = run.getAsJsonObject("evidence");
            long duration = songCount * 210_000L;
            evidence.addProperty("activeDurationMillis", duration);
            evidence.addProperty("finishEpochMillis", evidence.get("officialStartEpochMillis").getAsLong() + duration);
            evidence.getAsJsonObject("metrics").addProperty("distanceMeters", songCount * 500);
            JsonArray candidates = new JsonArray();
            for (int i = 0; i < songCount; i++) {
                Song song = SONGS.get(i);
                String prepId = UUID.randomUUID().toString();
                JsonObject prep = new JsonObject();
                prep.addProperty("preparationId", prepId);
                prep.addProperty("trackTitle", song.title());
                prep.addProperty("artistName", song.artist());
                boolean prepared = postJson(base, ReflectionHttpServer.SONG_PREPARATION_PATH, prep).get("state").getAsString().equals("READY");
                if (prepared) ready++;
                JsonObject music = prep.deepCopy();
                music.remove("preparationId");
                music.addProperty("state", "MUSIC");
                music.addProperty("provenance", "MEDIA_SESSION_PARTIAL");
                JsonObject candidate = new JsonObject();
                candidate.addProperty("candidateId", Integer.toString(i+1));
                if (prepared) candidate.addProperty("songPreparationId", prepId);
                candidate.add("musicContext", music);
                candidate.addProperty("loadedAtFinish", i+1 == songCount);
                candidates.add(candidate);
                if (i+1 == songCount) evidence.add("musicContext", music.deepCopy());
            }
            run.add("songCandidates", candidates);
            JsonObject written = postJson(base, ReflectionHttpServer.CANDIDATES_PATH, run);
            if (!written.has("selectedSongId")) throw new IOException("No eligible song was selected.");
            JsonObject speech = new JsonObject();
            String energyAnswer = songCount == 4 ? "feeling_good" : "powered_up";
            speech.addProperty("text", written.getAsJsonObject("candidates").get(energyAnswer).getAsString());
            byte[] audio = post(base, ReflectionHttpServer.SPEECH_PATH, speech);
            if (audio.length == 0) throw new IOException("Missing voice response.");
            if (!live) {
                int measuredBeforeRetry = counters.size();
                if (!written.equals(postJson(base, ReflectionHttpServer.CANDIDATES_PATH, run)) ||
                        !Arrays.equals(audio, post(base, ReflectionHttpServer.SPEECH_PATH, speech)) ||
                        counters.size() != measuredBeforeRetry)
                    throw new IllegalStateException("An exact written or spoken retry changed the reply or called a provider.");
                System.out.println("  Written and spoken retries: identical replies, zero additional sends.");
            }
            complete = true;
        } finally {
            List<String> lines = counters.toString(StandardCharsets.UTF_8).lines().toList();
            System.out.printf("Sample mode=%s eligible=%d studies_ready=%d flow_complete=%s%n",
                    live ? "LIVE" : "OFFLINE", songCount, ready, complete);
            reportUsage(System.out, lines, live);
            if (!live && complete && (ready != songCount ||
                    lines.stream().noneMatch(line -> line.equals("RunStateUsage musixmatch attempted=1")) ||
                    lines.stream().filter(line -> line.startsWith("RunStateUsage elevenlabs voice=reflection attempted=1 ")).count() != 1 ||
                    List.of("STUDY","CHOOSER","WRITER").stream().anyMatch(step -> lines.stream().noneMatch(line ->
                            line.contains("step=" + step + " ") && line.contains("attempted=1")))))
                throw new IllegalStateException("Offline fixture missed a required provider path.");
        }
    }

    /** The meter groups one isolated run. Only its fixed metadata fields reach the report. */
    static void reportUsage(PrintStream output, List<String> lines, boolean live) {
        var modelSteps = lines.stream().filter(line -> line.startsWith("RunStateUsage anthropic "))
                .collect(Collectors.groupingBy(line -> new ModelStep(field(line, "step"), field(line, "model")),
                        LinkedHashMap::new, Collectors.toList()));
        boolean usageComplete = true;
        for (var group : modelSteps.entrySet()) {
            var modelStep = group.getKey();
            var attempts = events(group.getValue(), "attempted");
            var replies = events(group.getValue(), "reply");
            output.printf("  Anthropic step=%s model=%s sends=%d replies=%d",
                    modelStep.step(), modelStep.model(), attempts.size(), replies.size());
            if (live) {
                var totals = new LinkedHashMap<String, String>();
                for (String field : TOKEN_FIELDS) {
                    totals.put(field, replies.size() == attempts.size() ? sum(replies, field) : "unknown");
                }
                boolean known = !totals.containsValue("unknown") && !modelStep.model().equals("custom")
                        && !modelStep.model().equals("unknown");
                usageComplete &= known;
                output.print(" usage=" + (known ? "complete" : "incomplete"));
                totals.forEach((name, total) -> output.print(" " + name + "=" + total));
            }
            output.println();
        }
        long catalog = lines.stream().filter(line -> line.equals("RunStateUsage musixmatch attempted=1")).count();
        output.printf("  Musixmatch requests attempted=%d%n", catalog);
        var speech = lines.stream().filter(line -> line.startsWith("RunStateUsage elevenlabs voice=reflection ")).toList();
        var attempts = events(speech, "attempted");
        var replies = events(speech, "reply");
        output.printf("  Reflection voice sends=%d replies=%d submitted_characters=%s",
                attempts.size(), replies.size(), sum(attempts, "submitted_chars"));
        if (live) {
            String units = attempts.size() == replies.size() ? sum(replies, "provider_character_cost") : "unknown";
            usageComplete &= !units.equals("unknown");
            output.print(" provider_character_cost=" + units);
        }
        output.println();
        if (!live) {
            output.println("  Simulated providers. No provider charges were incurred.");
        } else if (usageComplete) {
            output.println("  Usage for dispatched requests is complete. Apply your account rates separately.");
        } else {
            output.println("  Usage is incomplete. Reconcile missing details with provider billing before pricing.");
        }
    }

    private static List<String> events(List<String> lines, String marker) {
        return lines.stream().filter(line -> field(line, marker).equals("1")).toList();
    }

    private static String field(String line, String name) {
        String prefix = name + "=";
        return Arrays.stream(line.split(" ")).filter(part -> part.startsWith(prefix))
                .map(part -> part.substring(prefix.length())).findFirst().orElse("unknown");
    }

    private static String sum(List<String> lines, String field) {
        BigInteger total = BigInteger.ZERO;
        for (String line : lines) {
            BigInteger value = switch (field) {
                case "cache_write_5m", "cache_write_1h" -> cacheWindow(line, field);
                default -> number(line, field);
            };
            if (value == null) return "unknown";
            total = total.add(value);
        }
        return total.toString();
    }

    private static BigInteger number(String line, String name) {
        String text = field(line, name);
        return text.matches("[0-9]{1,16}") ? new BigInteger(text) : null;
    }

    private static BigInteger cacheWindow(String line, String name) {
        BigInteger total = number(line, "cache_write_total");
        BigInteger fiveMinutes = number(line, "cache_write_5m");
        BigInteger oneHour = number(line, "cache_write_1h");
        // A reported zero aggregate covers both windows, even when the optional breakdown is absent.
        if (BigInteger.ZERO.equals(total) && (fiveMinutes == null || fiveMinutes.signum() == 0)
                && (oneHour == null || oneHour.signum() == 0)) return BigInteger.ZERO;
        if (fiveMinutes == null || oneHour == null || !fiveMinutes.add(oneHour).equals(total)) return null;
        return name.equals("cache_write_5m") ? fiveMinutes : oneHour;
    }

    private static JsonObject postJson(URI base, String path, JsonObject body) throws Exception {
        return JsonParser.parseString(new String(post(base, path, body), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static byte[] post(URI base, String path, JsonObject body) throws Exception {
        var request = HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) throw new IOException("Sample " + path + " returned HTTP " + response.statusCode());
        return response.body();
    }
}
