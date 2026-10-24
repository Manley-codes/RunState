package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.AnthropicReflectionProvider.TransportResponse;
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
    void separateSourceStudyAndFreshFactsWriterHaveExactlyTheColdInputsAndFrozenPrompts() throws Exception {
        var coldInputs = new java.util.ArrayList<JsonObject>();
        var warmInputs = new java.util.ArrayList<JsonObject>();
        var coldPrompts = new java.util.ArrayList<String>();
        var warmPrompts = new java.util.ArrayList<String>();
        var cold = new SongReflectionTrial((input, prompt, schema) -> {
            coldInputs.add(input.deepCopy()); coldPrompts.add(prompt);
            return input.has("run_facts") ? branches() : notes();
        });
        var warm = new SongReflectionTrial((input, prompt, schema) -> {
            warmInputs.add(input.deepCopy()); warmPrompts.add(prompt);
            return input.has("run_facts") ? branches() : notes();
        });
        var facts = evidence(PreRunEnergy.MODERATE);
        var expected = cold.generate(facts, material());
        var studied = warm.study(material());
        assertEquals(1, warmInputs.size());
        assertFalse(warmInputs.get(0).has("run_facts"));
        var result = warm.write(facts, studied, null, SongReflectionTrial.CreativeApproach.AUTO);
        assertEquals(expected.reflections(), result.reflections());
        assertEquals(coldInputs, warmInputs);
        assertEquals(coldPrompts, warmPrompts);
        String[] hashes = {"41ddf6d72b2e12487ed6efeddd647297d0f33b4c2c55d9615434a20f352865cc",
                "a3f07cad9f9ab8483d9e251e95345733cc803b823888f7635936e4f9109a3128"};
        for (int index = 0; index < hashes.length; index++) {
            String normalized = warmPrompts.get(index).replace("\r\n", "\n");
            assertEquals(hashes[index], java.util.HexFormat.of().formatHex(java.security.MessageDigest
                    .getInstance("SHA-256").digest(normalized.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        }
    }

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
                assertFalse(input.has("passage_landing"));
                assertFalse(input.has("creative_approach"));
                assertFalse(input.toString().contains("4.20"));
                assertTrue(wire.get("system").getAsString().contains("before seeing any run"));
                return response(notes());
            }
            assertEquals(2, calls.get());
            assertEquals(notes(), input.get("liner_notes"));
            assertFalse(input.has("passage_landing"));
            assertEquals("auto", input.get("creative_approach").getAsString());
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
    void optionalPassageLandingReachesOnlyWriterAsEditorialContext() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
        var landing = new SongReflectionTrial.PassageLanding(" a fictional   morning ", " fixture artist ",
                "A quiet musical turn becomes an assured answer.", "Let the incoming passage complete the thought.");
        var client = new AnthropicReflectionProvider(config, request -> {
            JsonObject input = JsonParser.parseString(JsonParser.parseString(request.body()).getAsJsonObject()
                    .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            if (calls.incrementAndGet() == 1) {
                assertFalse(input.has("passage_landing"));
                assertFalse(input.toString().contains(landing.musicalMoment()));
                assertFalse(input.toString().contains(landing.intendedEffect()));
                return response(notes());
            }
            JsonObject target = input.getAsJsonObject("passage_landing");
            assertEquals("auto", input.get("creative_approach").getAsString());
            assertEquals(5, target.size());
            assertEquals(material().trackTitle(), target.get("track_title").getAsString());
            assertEquals(material().artistName(), target.get("artist_name").getAsString());
            assertEquals(landing.musicalMoment(), target.get("musical_moment").getAsString());
            assertEquals(landing.intendedEffect(), target.get("intended_effect").getAsString());
            assertEquals("EDITORIAL_AUDITION_DESCRIPTION_NOT_LYRICS_TIMING_OR_RUN_EVIDENCE",
                    target.get("provenance").getAsString());
            assertFalse(input.getAsJsonObject("run_facts").has("passage_landing"));
            assertFalse(input.getAsJsonObject("run_facts").toString().contains(landing.musicalMoment()));
            assertEquals(notes(), input.get("liner_notes"));
            return response(branches());
        });
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material(), landing);
        assertEquals(2, calls.get());
        assertEquals(branches(), result.branches());
        assertEquals("PassageLanding[editorialText=redacted]", landing.toString());
    }

    @Test
    void mismatchedPassageIdentityNeverStartsPaidStudy() throws Exception {
        for (String[] identity : List.of(new String[]{"Other Song", "Fixture Artist"},
                new String[]{"A Fictional Morning", "Other Artist"})) {
            AtomicInteger calls = new AtomicInteger();
            var landing = new SongReflectionTrial.PassageLanding(identity[0], identity[1], "A quiet turn.", "A warm close.");
            assertThrows(ReflectionProviderException.class, () -> new SongReflectionTrial(
                    fake(calls, UnaryOperator.identity(), UnaryOperator.identity()))
                    .generate(evidence(PreRunEnergy.MODERATE), material(), landing));
            assertEquals(0, calls.get());
        }
    }

    @Test
    void passageThemeMustBelongToTheStudiedMaterialBeforeColdOrPreparedWriterCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var trial = new SongReflectionTrial(fake(calls, UnaryOperator.identity(), UnaryOperator.identity()));
        var landing = new SongReflectionTrial.PassageLanding("A Fictional Morning", "Fixture Artist",
                "A timed phrase.", "A warm close.", "TIMED_SOURCE_LINE_WINDOW_NOT_AUDITIONED_OR_RUN_EVIDENCE",
                List.of("theme from a different song"), false);
        assertThrows(ReflectionProviderException.class,
                () -> trial.generate(evidence(PreRunEnergy.MODERATE), material(), landing));
        assertEquals(0, calls.get());
        var studied = trial.study(material());
        assertEquals(1, calls.get());
        assertThrows(ReflectionProviderException.class, () -> trial.write(evidence(PreRunEnergy.MODERATE), studied,
                landing, SongReflectionTrial.CreativeApproach.AUTO));
        assertEquals(1, calls.get());
    }

    @Test
    void invalidPassageFieldsAreRejectedBeforeAnyPaidCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var trial = new SongReflectionTrial(fake(calls, UnaryOperator.identity(), UnaryOperator.identity()));
        for (int field = 0; field < 4; field++) {
            for (String invalid : java.util.Arrays.asList(null, " ", "contains\ncontrol", "x".repeat(field < 2 ? 201 : 401))) {
                String[] values = {"A Fictional Morning", "Fixture Artist", "A quiet turn.", "A warm close."};
                values[field] = invalid;
                assertThrows(IllegalArgumentException.class, () -> trial.generate(evidence(PreRunEnergy.MODERATE), material(),
                        new SongReflectionTrial.PassageLanding(values[0], values[1], values[2], values[3])));
            }
        }
        assertEquals(0, calls.get());
        assertDoesNotThrow(() -> new SongReflectionTrial.PassageLanding("t".repeat(200), "a".repeat(200),
                "m".repeat(400), "e".repeat(400)));
    }

    @Test
    void writerReceivesBoundedSourceMeaningAndLabelsWithoutRawQuotesOrSnippet() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SongMaterial base = material();
        SongMaterial source = new SongMaterial(base.trackTitle(), base.artistName(), base.providerTrackId(),
                base.genres(), base.themes(), base.moods(), base.meaningExplanation(), base.entities(),
                "FICTIONAL_SNIPPET_NOT_FOR_WRITER_CONTEXT");
        var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
        var client = new AnthropicReflectionProvider(config, request -> {
            JsonObject input = JsonParser.parseString(JsonParser.parseString(request.body()).getAsJsonObject()
                    .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            if (calls.incrementAndGet() == 1) {
                assertFalse(input.has("song_context"));
                assertFalse(input.has("run_facts"));
                assertEquals(source.snippetText(), input.get("snippet_text").getAsString());
                return response(notes());
            }
            JsonObject context = input.getAsJsonObject("song_context");
            assertEquals(4, context.size());
            assertEquals("MUSIXMATCH_INTERPRETATION_NOT_RUN_EVIDENCE", context.get("provenance").getAsString());
            assertEquals("new beginnings", context.getAsJsonArray("themes").get(0).getAsString());
            assertEquals("hope", context.getAsJsonArray("moods").get(0).getAsString());
            assertEquals(source.meaningExplanation(), context.get("meaning").getAsString());
            assertFalse(context.toString().contains(source.themes().get(0).quotes().get(0)));
            assertFalse(context.toString().contains(source.snippetText()));
            assertFalse(context.has("quotes"));
            assertFalse(context.has("snippet_text"));
            assertFalse(input.getAsJsonObject("run_facts").has("song_context"));
            return response(branches());
        });
        new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), source);
        assertEquals(2, calls.get());
    }

    @Test
    void absentSourceMeaningIsOmittedWithoutInventingThemesOrAddingCalls() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        SongMaterial source = new SongMaterial(material().trackTitle(), material().artistName(), "1",
                List.of(), List.of(), List.of("warm"), null, List.of(), null);
        var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
        var client = new AnthropicReflectionProvider(config, request -> {
            if (calls.incrementAndGet() == 1) {
                JsonObject study = notes();
                study.add("lyric_echoes", new JsonArray());
                return response(study);
            }
            JsonObject input = JsonParser.parseString(JsonParser.parseString(request.body()).getAsJsonObject()
                    .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
            JsonObject context = input.getAsJsonObject("song_context");
            assertEquals(3, context.size());
            assertFalse(context.has("meaning"));
            assertTrue(context.getAsJsonArray("themes").isEmpty());
            assertEquals("warm", context.getAsJsonArray("moods").get(0).getAsString());
            assertFalse(input.has("passage_landing"));
            return response(branches());
        });
        new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), source);
        assertEquals(2, calls.get());
    }

    @Test
    void forwardedMeaningCountsTowardSourceBudgetWithoutAHiddenRewriteCall() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String meaning = "The steady morning belongs to whoever keeps showing up";
        SongMaterial base = material();
        SongMaterial source = new SongMaterial(base.trackTitle(), base.artistName(), base.providerTrackId(),
                base.genres(), base.themes(), base.moods(), meaning, base.entities(), null);
        var client = fake(calls, UnaryOperator.identity(), output -> {
            output.getAsJsonObject("spent").addProperty("reflection", meaning + ".");
            return output;
        });
        var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), source);
        assertEquals(9, SongReflectionTrial.copiedSourceWords(result.reflections().spent(), source));
        assertFalse(SongReflectionTrial.copiedSourceWords(result.reflections().spent(), source) <= 8);
        assertTrue(result.reviewWarnings().contains("spent:source_overlap_exceeds_display_budget"));
        assertEquals(2, calls.get());
        assertEquals(8, SongReflectionTrial.copiedSourceWords(
                "The steady morning belongs to whoever keeps showing", source));
    }

    @Test
    void creativeApproachReachesOnlyWriterWithoutChangingFourBranchOutput() throws Exception {
        for (SongReflectionTrial.CreativeApproach approach : SongReflectionTrial.CreativeApproach.values()) {
            AtomicInteger calls = new AtomicInteger();
            var config = AnthropicReflectionProvider.configurationFrom(Map.of("ANTHROPIC_API_KEY", "fake-key"));
            var client = new AnthropicReflectionProvider(config, request -> {
                JsonObject input = JsonParser.parseString(JsonParser.parseString(request.body()).getAsJsonObject()
                        .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString()).getAsJsonObject();
                if (calls.incrementAndGet() == 1) {
                    assertFalse(input.has("creative_approach"));
                    return response(notes());
                }
                assertEquals(approach.name().toLowerCase(java.util.Locale.ROOT),
                        input.get("creative_approach").getAsString());
                return response(branches());
            });
            var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material(), null, approach);
            assertEquals(2, calls.get());
            assertEquals(branches(), result.branches());
            assertTrue(result.reviewWarnings().isEmpty());
        }
    }

    @Test
    void missingCreativeApproachNeverStartsPaidStudy() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var trial = new SongReflectionTrial(fake(calls, UnaryOperator.identity(), UnaryOperator.identity()));
        assertThrows(NullPointerException.class,
                () -> trial.generate(evidence(PreRunEnergy.MODERATE), material(), null, null));
        assertEquals(0, calls.get());
    }

    @Test
    void personaOnlyFlagsIdentityAndVerifiedEchoesWithoutRewritingAnyBranch() throws Exception {
        JsonObject output = branches();
        output.getAsJsonObject("spent").addProperty("reflection", "A fresh start belongs to this finish.");
        output.getAsJsonObject("feeling_good").addProperty("reflection", "A FICTIONAL   MORNING has room for this finish.");
        output.getAsJsonObject("powered_up").addProperty("reflection", "FIXTURE ARTIST can stay on the record.");
        for (SongReflectionTrial.CreativeApproach approach : SongReflectionTrial.CreativeApproach.values()) {
            AtomicInteger calls = new AtomicInteger();
            var client = fake(calls, UnaryOperator.identity(), ignored -> output.deepCopy());
            var result = new SongReflectionTrial(client).generate(evidence(PreRunEnergy.MODERATE), material(), null, approach);
            assertEquals(output, result.branches());
            assertEquals(2, calls.get());
            List<String> creativeWarnings = result.reviewWarnings().stream()
                    .filter(warning -> warning.contains(":persona_only_")).toList();
            if (approach == SongReflectionTrial.CreativeApproach.PERSONA_ONLY) {
                assertEquals(List.of("spent:persona_only_lyric_echo_review_required",
                        "feeling_good:persona_only_identity_reference_review_required",
                        "powered_up:persona_only_identity_reference_review_required"), creativeWarnings);
            } else {
                assertTrue(creativeWarnings.isEmpty());
            }
        }
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

    private static CompletedRunEvidence trialEvidence(String scenario) {
        return switch (scenario) {
            case "standard" -> evidence(PreRunEnergy.MODERATE);
            case "morning_feeling_good" -> fictionalRun("2026-10-17T12:30:00Z", 33, 32, 3.0, PreRunEnergy.MODERATE);
            case "evening_no_selection" -> fictionalRun("2026-10-14T23:20:00Z", 45, 43, 4.0, PreRunEnergy.UNKNOWN);
            case "late_morning_powered_up" -> fictionalRun("2026-10-17T14:10:00Z", 30, 28, 2.75, PreRunEnergy.LOW);
            default -> throw new IllegalArgumentException("Unknown fictional audition scenario.");
        };
    }

    private static CompletedRunEvidence fictionalRun(String startText, int elapsedMinutes,
                                                      int activeMinutes, double miles, PreRunEnergy energy) {
        long start = Instant.parse(startText).toEpochMilli();
        return new CompletedRunEvidence(start, start + elapsedMinutes * 60_000L, "America/Chicago",
                activeMinutes * 60_000L,
                new RunMetricsEvidence(miles * 1609.344, MetricSource.GPS, TelemetryCoverage.COMPLETE, DistanceUnit.MILES),
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
            if ("1".equals(environment.get("RUNSTATE_COMPARE_BASELINE"))) {
                throw new IllegalArgumentException("Retired v1.8 comparison is unavailable; use the preserved accepted trial.");
            }
            String title = trialIdentity(environment, "RUNSTATE_TRIAL_SONG_TITLE", "Stronger");
            String artist = trialIdentity(environment, "RUNSTATE_TRIAL_SONG_ARTIST", "Kanye West");
            String scenario = environment.getOrDefault("RUNSTATE_TRIAL_RUN_SCENARIO", "standard");
            var baseFacts = trialEvidence(scenario);
            var approach = SongReflectionTrial.CreativeApproach.valueOf(environment
                    .getOrDefault("RUNSTATE_TRIAL_CREATIVE_APPROACH", "AUTO").toUpperCase(java.util.Locale.ROOT));
            String selectedBranch = environment.getOrDefault("RUNSTATE_TRIAL_ENERGY_BRANCH", "spent");
            if (!List.of("spent", "feeling_good", "powered_up", "no_selection").contains(selectedBranch)) {
                throw new IllegalArgumentException("Unknown conditional audition branch.");
            }
            String landingMoment = environment.get("RUNSTATE_TRIAL_LANDING_MOMENT");
            String landingEffect = environment.get("RUNSTATE_TRIAL_LANDING_EFFECT");
            var landing = landingMoment == null && landingEffect == null ? null
                    : new SongReflectionTrial.PassageLanding(title, artist, landingMoment, landingEffect);
            var music = MusixmatchSongAnalysis.fromEnvironment(environment).orElseThrow();
            var config = AnthropicReflectionProvider.configurationFrom(environment);
            var realTransport = AnthropicReflectionProvider.productionTransport(config);
            AnthropicReflectionProvider.Transport measured = request -> {
                paidCalls.incrementAndGet();
                return realTransport.send(request);
            };
            stage = "Musixmatch material";
            String explicitTrackId = environment.get("RUNSTATE_TRIAL_CATALOG_TRACK_ID");
            SongMaterial material = (explicitTrackId == null
                    ? music.lookupMaterial(title, artist)
                    : music.lookupMaterialByTrackId(title, artist, explicitTrackId)).orElseThrow();
            var run = new CompletedRunEvidence(baseFacts.officialStartEpochMillis(), baseFacts.finishEpochMillis(),
                    baseFacts.startTimezoneId(), baseFacts.activeDurationMillis(), baseFacts.metrics(), baseFacts.preRunEnergy(),
                    new MusicContext(MusicState.MUSIC, title, artist, null, MusicProvenance.CONTROLLED));
            stage = "song study and writer";
            var result = new SongReflectionTrial(new AnthropicReflectionProvider(config, measured)).generate(run, material, landing, approach);
            JsonObject report = new JsonObject();
            report.addProperty("fictional_run", true);
            report.addProperty("fictional_run_scenario", scenario);
            report.addProperty("creative_approach", approach.name());
            report.addProperty("selected_energy_branch", selectedBranch);
            report.addProperty("song_title", material.trackTitle());
            report.addProperty("song_artist", material.artistName());
            report.addProperty("explicit_catalog_entry", explicitTrackId != null);
            report.addProperty("passage_landing_supplied", landing != null);
            report.addProperty("model", config.musicEditorModel());
            report.addProperty("theme_count", material.themes().size());
            report.addProperty("mood_count", material.moods().size());
            report.addProperty("meaning_characters", material.meaningExplanation() == null
                    ? 0 : material.meaningExplanation().length());
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
            if ("1".equals(environment.get("RUNSTATE_TRIAL_REVIEW_ALL"))) {
                JsonObject candidates = new JsonObject();
                int remainingSourceWords = 8;
                for (String branch : List.of("spent", "feeling_good", "powered_up", "no_selection")) {
                    JsonObject candidate = result.branches().getAsJsonObject(branch);
                    int overlap = SongReflectionTrial.copiedSourceWords(candidate.get("reflection").getAsString(), material);
                    if (overlap <= remainingSourceWords) {
                        candidates.add(branch, candidate.deepCopy());
                        remainingSourceWords -= overlap;
                    } else {
                        JsonObject omitted = new JsonObject();
                        omitted.addProperty("text_omitted_for_shared_source_budget", true);
                        candidates.add(branch, omitted);
                    }
                }
                report.add("trial_candidates", candidates);
            } else if (SongReflectionTrial.copiedSourceWords(result.branches().getAsJsonObject(selectedBranch)
                    .get("reflection").getAsString(), material) <= 8) {
                report.add(selectedBranch.equals("spent") ? "trial_spent" : "trial_selected",
                        result.branches().getAsJsonObject(selectedBranch));
            } else {
                report.addProperty("trial_spent_text_omitted_for_source_overlap", true);
            }
            report.addProperty("other_branches_generated_and_mechanically_checked", 3);
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

    private static String trialIdentity(java.util.Map<String, String> environment, String key, String fallback) {
        String value = environment.getOrDefault(key, fallback).strip();
        if (value.isEmpty() || value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid controlled trial identity.");
        }
        return value;
    }
}
