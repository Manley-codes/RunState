package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.AnthropicReflectionProvider.TransportResponse;
import com.runstate.reflection.MusixmatchSongAnalysis.Analysis;
import com.runstate.reflection.MusixmatchSongAnalysis.SongMaterial;
import com.runstate.reflection.MusixmatchSongAnalysis.ThemeMaterial;
import com.runstate.reflection.ReflectionContract.*;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class SongReflectionTrialTest {
    @Test
    void studiesTheSongBeforeSeeingRunFactsAndWritesFromValidatedNotes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
        var client = new AnthropicReflectionProvider(config, request -> {
            JsonObject wire = JsonParser.parseString(request.body()).getAsJsonObject();
            assertEquals(config.musicEditorModel(), wire.get("model").getAsString());
            JsonObject input = JsonParser.parseString(wire.getAsJsonArray("messages").get(0)
                    .getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            assertFalse(request.toString().contains("fake-key"));
            if (calls.incrementAndGet() == 1) {
                assertEquals("A Fictional Morning", input.get("track_title").getAsString());
                assertTrue(input.has("themes"));
                assertFalse(input.has("run_facts"));
                assertFalse(input.toString().contains("4.20"));
                assertTrue(wire.get("system").getAsString().contains("before seeing any run"));
                return response(notes());
            }
            assertEquals(2, calls.get());
            assertEquals(notes(), input.get("liner_notes"));
            JsonObject facts = input.getAsJsonObject("run_facts");
            assertEquals("4.20 miles", facts.get("distance_text").getAsString());
            assertEquals("Saturday 7:00 AM", facts.get("start_time_text").getAsString());
            assertEquals("40 minutes active", facts.get("active_duration_text").getAsString());
            assertEquals("42 minutes elapsed", facts.get("elapsed_duration_text").getAsString());
            JsonObject schemas = wire.getAsJsonObject("output_config").getAsJsonObject("format")
                    .getAsJsonObject("schema").getAsJsonObject("properties");
            assertFalse(schemas.getAsJsonObject("no_selection").getAsJsonObject("properties")
                    .getAsJsonObject("anchor").getAsJsonArray("enum").toString().contains("conditional_post_energy"));
            assertFalse(input.has("themes"));
            assertFalse(input.has("snippet_text"));
            assertFalse(facts.has("pace"));
            assertFalse(facts.has("route"));
            return response(branches());
        });
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material());
        assertEquals(2, calls.get());
        assertEquals("4.2 miles complete. Leave room for the rest of this morning.", result.reflections().spent());
        JsonObject copy = result.linerNotes();
        copy.addProperty("attitude", "mutated");
        assertEquals("Quiet confidence in a fresh start.", result.linerNotes().get("attitude").getAsString());
        assertFalse(result.toString().contains("A fresh start"));
        assertTrue(result.toString().contains("redacted"));
    }

    @Test
    void rejectsAnInventedOrMisattributedLyricBeforeWriterCall() throws Exception {
        for (String[] mutation : List.of(new String[]{"words", "invented fixture lyric"},
                new String[]{"theme", "a different theme"},
                new String[]{"words", "one two three four five six seven eight nine"})) {
            AtomicInteger calls = new AtomicInteger();
            var client = fake(calls, note -> {
                note.getAsJsonArray("lyric_echoes").get(0).getAsJsonObject().addProperty(mutation[0], mutation[1]);
                return note;
            }, UnaryOperator.identity());
            assertThrows(ReflectionProviderException.class,
                    () -> new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material()));
            assertEquals(1, calls.get());
        }
    }

    @Test
    void wrongSongNeverStartsPaidStudy() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SongMaterial wrong = new SongMaterial("Other Song", "Fixture Artist", "1", List.of(),
                material().themes(), List.of(), null, List.of(), null);
        assertThrows(ReflectionProviderException.class, () -> new SongReflectionTrial(
                fake(calls, UnaryOperator.identity(), UnaryOperator.identity()))
                .generate(evidence(PreRunEnergy.MODERATE), wrong));
        assertEquals(0, calls.get());
    }

    @Test
    void wholeNumericTokensFlagInventedCombinationsDespiteAllowedIndividualDigits() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = fake(calls, UnaryOperator.identity(), output -> {
            output.getAsJsonObject("spent").addProperty("reflection", "24 miles complete.");
            return output;
        });
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material());
        assertTrue(result.reviewWarnings().contains("spent:numeric_tokens_review_required"));
        assertEquals(2, calls.get()); // No hidden retry, editor, fallback or extra charge.
    }

    @Test
    void unknownStartAndMissingWeatherStayOutOfWriterEvidence() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
        var client = new AnthropicReflectionProvider(config, request -> {
            if (calls.incrementAndGet() == 1) return response(notes());
            JsonObject input = JsonParser.parseString(JsonParser.parseString(request.body()).getAsJsonObject()
                    .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            assertFalse(input.getAsJsonObject("run_facts").has("energy_before"));
            assertFalse(input.getAsJsonObject("run_facts").has("weather_temperature_fahrenheit"));
            assertFalse(input.getAsJsonArray("allowed_anchors").toString().contains("pre_run_energy"));
            return response(branches());
        });
        new SongReflectionTrial(client).generate(evidence(PreRunEnergy.UNKNOWN), material());
    }

    @Test
    void unavailableAndNoSelectionAnchorsAreRejected() throws Exception {
        for (String[] change : List.of(new String[]{"spent", "weather_near_start"},
                new String[]{"no_selection", "conditional_post_energy"},
                new String[]{"spent", "usual_pace"})) {
            AtomicInteger calls = new AtomicInteger();
            var client = fake(calls, UnaryOperator.identity(), output -> {
                output.getAsJsonObject(change[0]).addProperty("anchor", change[1]);
                return output;
            });
            assertThrows(ReflectionProviderException.class,
                    () -> new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material()));
        }
    }

    @Test
    void allFourBranchesAndBoundedWritingAreRequired() throws Exception {
        for (String mutation : List.of("missing", "blank", "banned", "long", "extra")) {
            AtomicInteger calls = new AtomicInteger();
            var client = fake(calls, UnaryOperator.identity(), output -> {
                switch (mutation) {
                    case "missing" -> output.remove("no_selection");
                    case "extra" -> output.addProperty("extra", "unexpected");
                    case "blank" -> output.getAsJsonObject("spent").addProperty("reflection", " ");
                    case "banned" -> output.getAsJsonObject("spent").addProperty("reflection", "Unleash the morning.");
                    case "long" -> output.getAsJsonObject("spent").addProperty("reflection", "word ".repeat(56));
                }
                return output;
            });
            if (mutation.equals("banned") || mutation.equals("long")) {
                var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material());
                assertTrue(result.reviewWarnings().contains("spent:" + (mutation.equals("long")
                        ? "word_budget_review_required" : "word_choice_review_required")));
            } else {
                assertThrows(ReflectionProviderException.class,
                        () -> new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material()));
            }
        }
    }

    @Test
    void flagsMultipleEchoesForHumanReviewWithoutDiscardingExperimentalWriting() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var client = fake(calls, note -> {
            JsonObject echo = new JsonObject();
            echo.addProperty("words", "fictional morning");
            echo.addProperty("theme", "new beginnings");
            note.getAsJsonArray("lyric_echoes").add(echo);
            return note;
        }, output -> {
            output.getAsJsonObject("spent").addProperty("reflection", "A fresh start and a fictional morning.");
            return output;
        });
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material());
        assertTrue(result.reviewWarnings().contains("spent:multiple_echo_matches_review_required"));
        assertEquals(2, calls.get());
    }

    @Test
    void sourceOverlapCountsWholeCopiedPassagesAcrossPunctuation() {
        assertEquals(7, SongReflectionTrial.copiedSourceWords(
                "A fresh START, for this fictional morning!", material()));
        assertEquals(0, SongReflectionTrial.copiedSourceWords("A completed run and a quiet moment.", material()));
    }

    @Test
    void longButVerifiedStudyExcerptIsShortenedWithoutRegenerationOrRewriting() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String line = "A fresh start for this fictional morning comes around again";
        SongMaterial longer = new SongMaterial(material().trackTitle(), material().artistName(), "1", List.of(),
                List.of(new ThemeMaterial("new beginnings", List.of(line))), List.of("hope"), null, List.of(), null);
        var client = fake(calls, note -> {
            note.getAsJsonArray("lyric_echoes").get(0).getAsJsonObject().addProperty("words", line);
            return note;
        }, UnaryOperator.identity());
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), longer);
        assertEquals("A fresh start for this fictional morning comes", result.linerNotes()
                .getAsJsonArray("lyric_echoes").get(0).getAsJsonObject().get("words").getAsString());
        assertEquals(2, calls.get());
    }

    @Test
    void trialAcceptsNewerReasoningEnvelopesWithoutForwardingReasoning() throws Exception {
        for (String type : List.of("thinking", "redacted_thinking")) {
            AtomicInteger calls = new AtomicInteger();
            var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
            var client = new AnthropicReflectionProvider(config, request -> {
                if (calls.incrementAndGet() == 1) {
                    JsonObject envelope = JsonParser.parseString(response(notes()).body()).getAsJsonObject();
                    JsonArray blocks = new JsonArray();
                    JsonObject reasoning = new JsonObject();
                    reasoning.addProperty("type", type);
                    reasoning.addProperty(type.equals("thinking") ? "thinking" : "data", "PRIVATE_SYNTHETIC_MARKER");
                    blocks.add(reasoning);
                    blocks.addAll(envelope.getAsJsonArray("content"));
                    envelope.add("content", blocks);
                    return new TransportResponse(200, envelope.toString());
                }
                assertFalse(request.body().contains("PRIVATE_SYNTHETIC_MARKER"));
                return response(branches());
            });
            new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material());
            assertEquals(2, calls.get());
        }
    }

    private static AnthropicReflectionProvider fake(AtomicInteger calls, UnaryOperator<JsonObject> study,
                                                    UnaryOperator<JsonObject> writer) throws Exception {
        return new AnthropicReflectionProvider(AnthropicReflectionProvider.configurationFrom(
                Map.of("ANTHROPIC_API_KEY", "fake-key")), request ->
                response(calls.incrementAndGet() == 1 ? study.apply(notes()) : writer.apply(branches())));
    }

    private static TransportResponse response(JsonObject output) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("stop_reason", "end_turn");
        JsonObject block = new JsonObject();
        block.addProperty("type", "text");
        block.addProperty("text", output.toString());
        JsonArray content = new JsonArray();
        content.add(block);
        envelope.add("content", content);
        return new TransportResponse(200, envelope.toString());
    }

    private static JsonObject notes() {
        return JsonParser.parseString("""
                {"attitude":"Quiet confidence in a fresh start.","delivery":"Warm, concise and assured.",
                 "artist_context":"","title_idea":"A new morning", "imagery":[],
                 "lyric_echoes":[{"words":"A fresh start","theme":"new beginnings"}]}
                """).getAsJsonObject();
    }

    private static JsonObject branches() {
        JsonObject result = new JsonObject();
        for (String key : List.of("spent", "feeling_good", "powered_up", "no_selection")) {
            JsonObject branch = new JsonObject();
            branch.addProperty("lead", "persona");
            branch.addProperty("accent", "");
            branch.addProperty("anchor", "distance");
            branch.addProperty("fit", "medium");
            branch.addProperty("reflection", "4.2 miles complete. Leave room for the rest of this morning.");
            result.add(key, branch);
        }
        return result;
    }

    private static SongMaterial material() {
        return new SongMaterial("A Fictional Morning", "Fixture Artist", "1", List.of("Soul"),
                List.of(new ThemeMaterial("new beginnings", List.of("A fresh start for this fictional morning"))),
                List.of("hope"), "A fictional record about fresh starts.", List.of(), null);
    }

    private static CompletedRunEvidence evidence(PreRunEnergy energy) {
        long start = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli();
        return new CompletedRunEvidence(start, start + 42 * 60_000L, "America/Chicago", 40 * 60_000L,
                new RunMetricsEvidence(4.2 * 1609.344, MetricSource.GPS, TelemetryCoverage.COMPLETE, DistanceUnit.MILES),
                energy, new MusicContext(MusicState.MUSIC, "A Fictional Morning", "Fixture Artist", null,
                MusicProvenance.CONTROLLED));
    }

    /** Explicit manual harness, outside JUnit/Surefire so real source lyrics never enter test reports. */
    public static void main(String[] args) {
        if (!"1".equals(System.getenv("RUNSTATE_LIVE_SONG_TRIAL"))) {
            System.out.println("Live song trial is disabled.");
            return;
        }
        String stage = "configuration";
        AtomicInteger paidCalls = new AtomicInteger();
        long started = System.nanoTime();
        try {
            var environment = System.getenv();
            var music = MusixmatchSongAnalysis.fromEnvironment(environment).orElseThrow();
            var config = AnthropicReflectionProvider.configurationFrom(environment);
            var realTransport = AnthropicReflectionProvider.productionTransport(config);
            AnthropicReflectionProvider.Transport measured = request -> {
                paidCalls.incrementAndGet();
                return realTransport.send(request);
            };
            stage = "Musixmatch material";
            SongMaterial material = music.lookupMaterial("Stronger", "Kanye West").orElseThrow();
            var baseFacts = evidence(PreRunEnergy.MODERATE);
            var run = new CompletedRunEvidence(baseFacts.officialStartEpochMillis(), baseFacts.finishEpochMillis(),
                    baseFacts.startTimezoneId(), baseFacts.activeDurationMillis(), baseFacts.metrics(), baseFacts.preRunEnergy(),
                    new MusicContext(MusicState.MUSIC, "Stronger", "Kanye West", null, MusicProvenance.CONTROLLED));
            stage = "song study and writer";
            var result = new SongReflectionTrial(new AnthropicReflectionProvider(config, measured)).generate(run, material);
            JsonObject report = new JsonObject();
            report.addProperty("fictional_run", true);
            report.addProperty("model", config.musicEditorModel());
            report.addProperty("theme_count", material.themes().size());
            report.addProperty("supporting_quote_count", material.themes().stream().mapToInt(t -> t.quotes().size()).sum());
            report.addProperty("snippet_available", material.snippetText() != null);
            JsonObject study = result.linerNotes();
            int echoes = study.getAsJsonArray("lyric_echoes").size();
            report.addProperty("validated_lyric_echo_count", echoes);
            report.addProperty("study_attitude_characters", study.get("attitude").getAsString().length());
            report.addProperty("study_delivery_characters", study.get("delivery").getAsString().length());
            JsonArray warnings = new JsonArray();
            result.reviewWarnings().forEach(warnings::add);
            report.add("requires_human_review", warnings);
            if (SongReflectionTrial.copiedSourceWords(result.reflections().spent(), material) <= 8) {
                report.add("trial_spent", result.branches().getAsJsonObject("spent"));
            } else {
                report.addProperty("trial_spent_text_omitted_for_source_overlap", true);
            }
            report.addProperty("other_branches_generated_and_mechanically_checked", 3);
            if ("1".equals(System.getenv("RUNSTATE_COMPARE_BASELINE"))) {
                stage = "v1.8 baseline";
                var analysis = new Analysis(material.trackTitle(), material.artistName(), material.providerTrackId(),
                        material.themes().stream().map(ThemeMaterial::theme).toList(), material.moods(), material.meaningExplanation());
                try {
                    var baseline = new AnthropicReflectionProvider(config, measured,
                            (title, artist) -> Optional.of(analysis)).generate(run);
                    if (SongReflectionTrial.copiedSourceWords(baseline.spent(), material) <= 2) {
                        report.addProperty("v1_8_baseline_spent", baseline.spent());
                    } else {
                        report.addProperty("baseline_text_omitted_for_source_overlap", true);
                    }
                } catch (ReflectionProviderException unavailable) {
                    report.addProperty("baseline_status", unavailable.failureType().name());
                }
            }
            report.addProperty("anthropic_calls", paidCalls.get());
            report.addProperty("seconds", (System.nanoTime() - started) / 1_000_000_000.0);
            System.out.println(report);
        } catch (Exception unavailable) {
            String reason = unavailable instanceof ReflectionProviderException failure
                    ? failure.failureType().name() + ": " + failure.getMessage() : "source unavailable";
            System.out.println("Song trial stopped during " + stage + "; calls=" + paidCalls.get()
                    + "; " + reason + ". No output was installed or saved.");
            System.exit(1);
        }
    }
}
