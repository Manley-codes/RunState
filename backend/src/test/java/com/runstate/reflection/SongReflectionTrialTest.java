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

public class SongReflectionTrialTest {
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

    @Test
    void v2StudyDirectionReplacesAutoAndTheGenericEffectWithoutChangingTheWriterPrompt() throws Exception {
        var writerInputs = new java.util.ArrayList<JsonObject>();
        var prompts = new java.util.ArrayList<String>();
        var studySchemas = new java.util.ArrayList<JsonObject>();
        var trial = new SongReflectionTrial((input, prompt, schema) -> {
            prompts.add(prompt);
            if (input.has("run_facts")) {
                writerInputs.add(input.deepCopy());
                return branches();
            }
            studySchemas.add(schema.deepCopy());
            return directedNotes("persona", "A sly, unbothered\n send-off that lets the beat answer.");
        });
        var landing = AnthropicReflectionProvider.automaticLanding(material(),
                SongSnippetSelectionMethod.TITLE_HOOK, List.of());
        var studied = trial.study(material(), SongReflectionTrial.StudyVersion.V2);
        trial.writeWithStudyDirection(evidence(PreRunEnergy.MODERATE), studied, landing);
        assertTrue(prompts.get(0).contains("song study trial v2"));
        assertTrue(studySchemas.get(0).getAsJsonObject("properties").has("intended_effect"));
        JsonObject input = writerInputs.get(0);
        assertEquals("persona_only", input.get("creative_approach").getAsString());
        JsonObject target = input.getAsJsonObject("passage_landing");
        assertEquals("A brief recurring title hook with a source-timed line boundary.",
                target.get("musical_moment").getAsString());
        assertEquals("A sly, unbothered send-off that lets the beat answer.",
                target.get("intended_effect").getAsString());
        assertEquals(notes(), input.get("liner_notes"), "The direction travels in its own slots, not as liner notes");
        String writerPrompt = prompts.get(1).replace("\r\n", "\n");
        assertEquals("a3f07cad9f9ab8483d9e251e95345733cc803b823888f7635936e4f9109a3128",
                java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                        .digest(writerPrompt.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }

    @Test
    void studyVersionsAcceptOnlyTheirOwnFieldsAndKnownConnections() {
        var v1GetsDirection = new SongReflectionTrial((input, prompt, schema) -> directedNotes("direct", "A warm reply."));
        var v2GetsPlainNotes = new SongReflectionTrial((input, prompt, schema) -> notes());
        var unknownConnection = new SongReflectionTrial((input, prompt, schema) -> directedNotes("vibes", "A warm reply."));
        var blankEffect = new SongReflectionTrial((input, prompt, schema) -> directedNotes("blend", " "));
        assertThrows(ReflectionProviderException.class, () -> v1GetsDirection.study(material()));
        assertThrows(ReflectionProviderException.class,
                () -> v2GetsPlainNotes.study(material(), SongReflectionTrial.StudyVersion.V2));
        assertThrows(ReflectionProviderException.class,
                () -> unknownConnection.study(material(), SongReflectionTrial.StudyVersion.V2));
        assertThrows(ReflectionProviderException.class,
                () -> blankEffect.study(material(), SongReflectionTrial.StudyVersion.V2));
    }

    @Test
    void studyDirectionRequiresADirectedStudyAndKeepsAnAbsentLandingAbsent() throws Exception {
        var plain = new SongReflectionTrial((input, prompt, schema) -> input.has("run_facts") ? branches() : notes());
        var v1 = plain.study(material());
        assertThrows(ReflectionProviderException.class,
                () -> plain.writeWithStudyDirection(evidence(PreRunEnergy.MODERATE), v1, null));
        var writerInputs = new java.util.ArrayList<JsonObject>();
        var directed = new SongReflectionTrial((input, prompt, schema) -> {
            if (input.has("run_facts")) {
                writerInputs.add(input.deepCopy());
                return branches();
            }
            return directedNotes("direct", "A warm reply that answers one real moment.");
        });
        var v2 = directed.study(material(), SongReflectionTrial.StudyVersion.V2);
        directed.writeWithStudyDirection(evidence(PreRunEnergy.MODERATE), v2, null);
        assertEquals("direct", writerInputs.get(0).get("creative_approach").getAsString());
        assertFalse(writerInputs.get(0).has("passage_landing"));
        assertEquals(notes(), writerInputs.get(0).get("liner_notes"));
    }

    @Test
    void automaticLandingKeepsProductionDescriptionsForEveryPassageKind() {
        assertNull(AnthropicReflectionProvider.automaticLanding(material(),
                SongSnippetSelectionMethod.RECORDING_OPENING_FALLBACK, List.of()));
        var title = AnthropicReflectionProvider.automaticLanding(material(),
                SongSnippetSelectionMethod.TITLE_HOOK, List.of("new beginnings"));
        assertTrue(title.titleHook());
        assertEquals(List.of("new beginnings"), title.matchedThemes());
        assertEquals("TIMED_SOURCE_DERIVED_HOOK_NOT_AUDITIONED_OR_RUN_EVIDENCE", title.provenance());
        var repeated = AnthropicReflectionProvider.automaticLanding(material(),
                SongSnippetSelectionMethod.REPEATED_HOOK, List.of());
        assertEquals("A brief repeated vocal phrase with a source-timed line boundary.", repeated.musicalMoment());
        assertFalse(repeated.titleHook());
        var line = AnthropicReflectionProvider.automaticLanding(material(),
                SongSnippetSelectionMethod.TIMED_LINE_WINDOW, List.of());
        assertEquals("TIMED_SOURCE_LINE_WINDOW_NOT_AUDITIONED_OR_RUN_EVIDENCE", line.provenance());
        assertTrue(line.intendedEffect().startsWith("Let this short musical response finish the thought naturally."));
    }

    @Test
    void plainStudyAndWholeRunWriterUseTheirOwnPromptsWithBaselineShapes() throws Exception {
        var prompts = new java.util.ArrayList<String>();
        var writerInputs = new java.util.ArrayList<JsonObject>();
        var trial = new SongReflectionTrial((input, prompt, schema) -> {
            prompts.add(prompt);
            if (input.has("run_facts")) {
                writerInputs.add(input.deepCopy());
                return branches();
            }
            assertFalse(schema.getAsJsonObject("properties").has("connection"));
            return notes();
        });
        var studied = trial.study(material(), SongReflectionTrial.StudyVersion.V3);
        trial.write(evidence(PreRunEnergy.MODERATE), studied, null, SongReflectionTrial.CreativeApproach.AUTO,
                SongReflectionTrial.WriterVersion.V2);
        assertTrue(prompts.get(0).contains("song study trial v3"));
        assertTrue(prompts.get(0).contains("never as lines to\nreuse") || prompts.get(0).contains("never as lines to\r\nreuse"));
        assertTrue(prompts.get(1).contains("writer v2"));
        assertTrue(prompts.get(1).replace("\r\n", "\n").contains("never mention someone else going farther"));
        assertEquals("auto", writerInputs.get(0).get("creative_approach").getAsString());
        assertEquals(notes(), writerInputs.get(0).get("liner_notes"));
        assertThrows(ReflectionProviderException.class, () -> new SongReflectionTrial(
                (input, prompt, schema) -> directedNotes("direct", "A warm reply."))
                .study(material(), SongReflectionTrial.StudyVersion.V3));
    }

    @Test
    void spokenClockTimesPassOnlyAsNaturalRoundingsOfTheRealStartOrFinish() throws Exception {
        record Check(CompletedRunEvidence run, String reflection, boolean flagged) { }
        var morning = evidence(PreRunEnergy.MODERATE); // 7:00 to 7:42 AM
        var evening = trialEvidence("evening_no_selection"); // 6:20 to 7:05 PM
        var midnight = fictionalRun("2026-10-17T04:50:00Z", 45, 43, 4.0, PreRunEnergy.UNKNOWN); // 11:50 PM to 12:35 AM
        List<Check> checks = List.of(
                new Check(morning, "Saturday, 7 AM, 4.20 miles, and you're spent.", false),
                new Check(morning, "4.20 miles done before 7:45 on a Saturday.", false),
                new Check(morning, "Out the door at 7:00, back by 7:42, feeling good.", false),
                new Check(morning, "Done before 8 AM on a Saturday.", false),
                new Check(morning, "Wrapped up around 7:40.", false),
                // Each of these four passed the earlier 30-minute window, yet none rounds the start or finish.
                new Check(morning, "Somewhere around 7:20 it clicked.", true),
                new Check(morning, "Out the door at 6:40.", true),
                new Check(morning, "Out the door at 7:13.", true),
                new Check(morning, "Back by 8:05 on a Saturday.", true),
                new Check(morning, "Back by 9:15 on a Saturday morning.", true),
                new Check(morning, "7 miles done by 7:42.", true),
                new Check(evening, "Four miles, done by 7 PM on a Wednesday.", false),
                new Check(evening, "Out the door around 6:15 on a Wednesday.", false),
                new Check(evening, "Four miles, done by 7:30 PM.", true),
                new Check(evening, "Four miles, done by 9 PM on a Wednesday.", true),
                new Check(midnight, "Out the door at 11:45, done by 12:45.", false),
                new Check(midnight, "Done by 1:30.", true));
        for (Check check : checks) {
            var trial = new SongReflectionTrial((input, prompt, schema) ->
                    input.has("run_facts") ? branchesWith(check.reflection()) : notes());
            boolean flagged = trial.generate(check.run(), material()).reviewWarnings().stream()
                    .anyMatch(warning -> warning.endsWith("numeric_tokens_review_required"));
            assertEquals(check.flagged(), flagged, check.reflection());
        }
    }

    @Test
    void noClipWriterChangesOnlyTheTwoSentencesThatPromiseAClip() throws Exception {
        String accepted = promptText(SongReflectionTrial.WriterVersion.V1.prompt);
        String noClip = promptText(SongReflectionTrial.WriterVersion.V1_NOCLIP.prompt);
        String[][] changes = {
                {"another voice. Your original spoken reflection comes next, followed by a short\n"
                        + "significant moment from their song. Make those words and that musical finish\n"
                        + "belong together. Speak to one runner",
                 "another voice. Your original spoken reflection comes next, and this time it is\n"
                        + "the whole ending: no moment from their song plays after it. Give the reflection\n"
                        + "its own landing. Speak to one runner"},
                {"and the record when the clip arrives.", "and the record in your words alone."}};
        String expected = accepted;
        for (String[] change : changes) {
            assertEquals(1, expected.split(java.util.regex.Pattern.quote(change[0]), -1).length - 1, change[0]);
            expected = expected.replace(change[0], change[1]);
        }
        assertEquals(expected, noClip);
    }

    @Test
    void noClipWriterIsSentOnlyWhenAsked() throws Exception {
        List<String> prompts = new java.util.ArrayList<>();
        var trial = new SongReflectionTrial((input, prompt, schema) -> {
            prompts.add(prompt);
            return input.has("run_facts") ? branches() : notes();
        });
        var studied = trial.study(material());
        trial.write(evidence(PreRunEnergy.MODERATE), studied, null, SongReflectionTrial.CreativeApproach.AUTO,
                SongReflectionTrial.WriterVersion.V1_NOCLIP);
        trial.write(evidence(PreRunEnergy.MODERATE), studied, null, SongReflectionTrial.CreativeApproach.AUTO);
        assertTrue(prompts.get(1).contains("no moment from their song plays after it"));
        assertFalse(prompts.get(1).contains("when the clip arrives"));
        assertTrue(prompts.get(2).contains("when the clip arrives"));
    }

    private static String promptText(String name) throws java.io.IOException {
        try (var stream = SongReflectionTrial.class.getResourceAsStream("/prompts/" + name)) {
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static JsonObject branchesWith(String reflection) {
        JsonObject result = branches();
        for (String key : List.of("spent", "feeling_good", "powered_up", "no_selection")) {
            result.getAsJsonObject(key).addProperty("reflection", reflection);
        }
        return result;
    }

    private static JsonObject directedNotes(String connection, String effect) {
        JsonObject result = notes();
        result.addProperty("connection", connection);
        result.addProperty("intended_effect", effect);
        return result;
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
        String batch = System.getenv("RUNSTATE_TRIAL_BATCH");
        if (batch != null) {
            BatchPlan plan = switch (batch) {
                case "batch1" -> BATCH_ONE;
                case "batch2" -> BATCH_TWO;
                case "batch3" -> BATCH_THREE;
                default -> null;
            };
            if (plan == null) {
                System.out.println("Unknown RUNSTATE_TRIAL_BATCH; nothing was run.");
                return;
            }
            runBatch(System.getenv(), plan);
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

    /** One fixed comparison case. Facts are fictional; song information is looked up once per case. */
    private record BatchCase(String key, String title, String artist, String catalogTrackId, String scenario,
                             SongSnippetSelectionMethod passage, int repeats, String referenceLabel,
                             String referenceText) { }

    /**
     * One side of a comparison: which study and writer versions run, whether the study directs, and
     * whether the writer is given the passage landing (the moment it is told will follow its words).
     */
    private record BatchCondition(String name, SongReflectionTrial.StudyVersion study,
                                  SongReflectionTrial.WriterVersion writer, boolean studyDirection,
                                  boolean landingSent) { }

    /**
     * sharedStudy: one study per case feeds both versions, so only the writing differs. listeningNote,
     * when set, tells the reviewer what is heard after the words instead of the passage description.
     */
    private record BatchPlan(String name, String title, BatchCondition before, BatchCondition after,
                             boolean sharedStudy, String listeningNote) { }

    /** Identical fictional facts, songs and automatic passage descriptions in every approved batch. */
    private static final List<BatchCase> BATCH_CASES = List.of(
            new BatchCase("lovely_day", "Lovely Day", "Bill Withers", "460374491", "morning_feeling_good",
                    SongSnippetSelectionMethod.TITLE_HOOK, 1, "Feeling Good",
                    "Saturday, just past eight, three miles behind you and feeling good. Some mornings need a "
                            + "reason to face the day. You went out at seven-thirty and brought one back."),
            new BatchCase("stronger", "Stronger", "Kanye West", "176269002", "evening_no_selection",
                    SongSnippetSelectionMethod.TITLE_HOOK, 1, "No answer",
                    "Four miles. Wednesday. Done by five past seven. Clock out, chest out."),
            new BatchCase("love_yourz", "Love Yourz", "J. Cole", null, "standard",
                    SongSnippetSelectionMethod.REPEATED_HOOK, 3, null, null));

    /** Batch 1, Nov 7, 2026: the study's own direction replaces auto plus the generic landing effect. */
    private static final BatchPlan BATCH_ONE = new BatchPlan("batch1", "Batch 1",
            new BatchCondition("before", SongReflectionTrial.StudyVersion.V1, SongReflectionTrial.WriterVersion.V1, false, true),
            new BatchCondition("after", SongReflectionTrial.StudyVersion.V2, SongReflectionTrial.WriterVersion.V1, true, true),
            false, null);

    /** Batch 2, Nov 7, 2026: plain study notes, plus the writer's own-words and never-shrink principles. */
    private static final BatchPlan BATCH_TWO = new BatchPlan("batch2", "Batch 2",
            new BatchCondition("before", SongReflectionTrial.StudyVersion.V1, SongReflectionTrial.WriterVersion.V1, false, true),
            new BatchCondition("after", SongReflectionTrial.StudyVersion.V3, SongReflectionTrial.WriterVersion.V2, false, true),
            false, null);

    /**
     * Batch 3, Nov 8, 2026: the chosen song was already playing at the finish, so no clip follows.
     * Before is today's writing toward a landing that will not play; after is the no-clip writer.
     */
    private static final BatchPlan BATCH_THREE = new BatchPlan("batch3", "Batch 3",
            new BatchCondition("before", SongReflectionTrial.StudyVersion.V1, SongReflectionTrial.WriterVersion.V1, false, true),
            new BatchCondition("after", SongReflectionTrial.StudyVersion.V1, SongReflectionTrial.WriterVersion.V1_NOCLIP, false, false),
            true, "no clip. The chosen song was already playing when the run ended, so after the reflection "
                    + "that song simply picks up where it was (or stays paused if it was paused).");

    private static final String BATCH_MODEL = "claude-opus-5-5";
    private static final int BATCH_CALL_LIMIT = 20;
    private static final List<String[]> BATCH_BRANCHES = List.of(new String[]{"spent", "Spent"},
            new String[]{"feeling_good", "Feeling Good"}, new String[]{"powered_up", "Powered Up"},
            new String[]{"no_selection", "No answer"});

    private static void runBatch(Map<String, String> environment, BatchPlan plan) {
        AtomicInteger calls = new AtomicInteger();
        long[] tokens = new long[2];
        long started = System.nanoTime();
        String stage = "configuration";
        try {
            Map<String, String> pinned = new java.util.HashMap<>(environment);
            pinned.put("ANTHROPIC_MUSIC_EDITOR_MODEL", BATCH_MODEL);
            var config = AnthropicReflectionProvider.configurationFrom(pinned);
            if (!BATCH_MODEL.equals(config.musicEditorModel())) throw new IllegalStateException("Model pin failed.");
            var realTransport = AnthropicReflectionProvider.productionTransport(config);
            AnthropicReflectionProvider.Transport measured = request -> {
                if (calls.incrementAndGet() > BATCH_CALL_LIMIT) {
                    throw new java.io.IOException("Batch call limit reached.");
                }
                TransportResponse response = realTransport.send(request);
                try {
                    JsonObject usage = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonObject("usage");
                    if (usage != null) {
                        tokens[0] += usage.get("input_tokens").getAsLong();
                        tokens[1] += usage.get("output_tokens").getAsLong();
                    }
                } catch (RuntimeException unreadable) { /* Token counts are reporting only. */ }
                return response;
            };
            stage = "report folder";
            java.nio.file.Path root = java.nio.file.Path.of(environment.getOrDefault("RUNSTATE_TRIAL_REPORT_DIR",
                    "../docs/local/reflection-" + plan.name())).toAbsolutePath().normalize();
            if (!root.toString().replace('\\', '/').contains("/docs/local/")) {
                throw new IllegalStateException("Batch reports must stay in a private docs/local folder.");
            }
            java.nio.file.Path folder = root.resolve(java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'").withZone(java.time.ZoneOffset.UTC).format(Instant.now()));
            java.nio.file.Files.createDirectories(folder);
            stage = "Musixmatch configuration";
            var music = MusixmatchSongAnalysis.fromEnvironment(environment)
                    .orElseThrow(() -> new IllegalStateException("MUSIXMATCH_API_KEY is not set."));
            var trial = new SongReflectionTrial(new AnthropicReflectionProvider(config, measured));
            var random = new java.security.SecureRandom();
            JsonArray generations = new JsonArray();
            JsonObject key = new JsonObject();
            StringBuilder review = new StringBuilder("# " + plan.title() + """
                     — blind review

                    Fictional runs only. Each version shows all four Energy answers from one generation.
                    Versions A and B are shuffled separately in every block, so position tells you nothing.
                    For each block, say which version you would rather hear, and name any clear Hit or Miss.
                    Please don't open generations.json or key.json until you're done.
                    """);
            if (plan.listeningNote() != null) {
                review.append("\nWhat you hear after the words in this batch: ").append(plan.listeningNote())
                        .append(" Judge which version lands better on its own.\n");
            }
            int planned = BATCH_CASES.stream().mapToInt(item -> item.repeats() * 2).sum();
            int finished = 0;
            for (BatchCase item : BATCH_CASES) {
                stage = "Musixmatch material for " + item.key();
                SongMaterial material = (item.catalogTrackId() == null
                        ? music.lookupMaterial(item.title(), item.artist())
                        : music.lookupMaterialByTrackId(item.title(), item.artist(), item.catalogTrackId()))
                        .orElseThrow(() -> new IllegalStateException("No matched Musixmatch material."));
                CompletedRunEvidence base = trialEvidence(item.scenario());
                CompletedRunEvidence run = new CompletedRunEvidence(base.officialStartEpochMillis(),
                        base.finishEpochMillis(), base.startTimezoneId(), base.activeDurationMillis(), base.metrics(),
                        base.preRunEnergy(), new MusicContext(MusicState.MUSIC, material.trackTitle(),
                        material.artistName(), null, MusicProvenance.CONTROLLED));
                var landing = AnthropicReflectionProvider.automaticLanding(material, item.passage(), List.of());
                review.append(batchCaseHeader(item, material, run, landing, plan.listeningNote()));
                for (int repeat = 1; repeat <= item.repeats(); repeat++) {
                    boolean beforeIsA = random.nextBoolean();
                    JsonObject before = null;
                    JsonObject after = null;
                    SongReflectionTrial.StudiedSong shared = null;
                    String sharedFailure = null;
                    long sharedStudyMs = 0;
                    if (plan.sharedStudy()) {
                        stage = item.key() + " repeat " + repeat + " shared study";
                        long studyStarted = System.nanoTime();
                        try {
                            shared = trial.study(material, plan.before().study());
                        } catch (ReflectionProviderException | RuntimeException failure) {
                            sharedFailure = failure instanceof ReflectionProviderException provider
                                    ? provider.failureType().name() + ": " + provider.getMessage()
                                    : failure.getClass().getSimpleName();
                        }
                        sharedStudyMs = (System.nanoTime() - studyStarted) / 1_000_000;
                    }
                    for (BatchCondition condition : List.of(plan.before(), plan.after())) {
                        stage = item.key() + " repeat " + repeat + " " + condition.name();
                        JsonObject record = batchGeneration(trial, item, condition, repeat, material, run,
                                condition.landingSent() ? landing : null, shared, sharedStudyMs, sharedFailure);
                        generations.add(record);
                        if (condition == plan.before()) before = record; else after = record;
                        finished++;
                        System.out.println("[" + finished + "/" + planned + "] " + stage + ": "
                                + record.get("status").getAsString() + "; Anthropic calls so far " + calls.get());
                        writeBatchFiles(folder, generations, key, review.toString(),
                                batchSummary(plan, calls.get(), tokens, started, finished, planned));
                    }
                    review.append(batchReviewBlock("A", repeat, beforeIsA ? before : after));
                    review.append(batchReviewBlock("B", repeat, beforeIsA ? after : before));
                    key.addProperty(item.key() + " repeat " + repeat,
                            beforeIsA ? "A = before, B = after" : "A = after, B = before");
                }
            }
            JsonObject summary = batchSummary(plan, calls.get(), tokens, started, finished, planned);
            writeBatchFiles(folder, generations, key, review.toString(), summary);
            System.out.println(plan.title() + " finished: " + finished + "/" + planned + " generations, "
                    + calls.get() + " Anthropic calls, estimated $"
                    + summary.get("estimated_cost_usd").getAsString() + ". Private report: " + folder);
        } catch (Exception failure) {
            String reason = failure instanceof ReflectionProviderException provider
                    ? provider.failureType().name() + ": " + provider.getMessage()
                    : failure.getClass().getSimpleName() + ": " + failure.getMessage();
            System.out.println(plan.title() + " stopped during " + stage + "; Anthropic calls=" + calls.get()
                    + "; " + reason);
            System.exit(1);
        }
    }

    private static JsonObject batchGeneration(SongReflectionTrial trial, BatchCase item, BatchCondition condition,
            int repeat, SongMaterial material, CompletedRunEvidence run, SongReflectionTrial.PassageLanding landing,
            SongReflectionTrial.StudiedSong shared, long sharedStudyMs, String sharedFailure) {
        JsonObject record = new JsonObject();
        record.addProperty("case", item.key());
        record.addProperty("condition", condition.name());
        record.addProperty("repeat", repeat);
        record.addProperty("model", BATCH_MODEL);
        record.addProperty("study_prompt", condition.study().prompt);
        record.addProperty("writer_prompt", condition.writer().prompt);
        record.addProperty("landing_sent", landing != null);
        if (sharedFailure != null) {
            record.addProperty("status", "failed");
            record.addProperty("failure", "shared study: " + sharedFailure);
            return record;
        }
        try {
            long studyStarted = System.nanoTime();
            var studied = shared != null ? shared : trial.study(material, condition.study());
            record.addProperty("study_ms", shared != null ? sharedStudyMs : (System.nanoTime() - studyStarted) / 1_000_000);
            if (shared != null) record.addProperty("study_shared", true);
            JsonObject notes = studied.linerNotes();
            JsonObject study = new JsonObject();
            for (String field : List.of("attitude", "delivery", "artist_context", "title_idea",
                    "connection", "intended_effect")) {
                if (notes.has(field)) study.add(field, notes.get(field));
            }
            study.add("imagery", notes.get("imagery"));
            study.addProperty("lyric_echo_count", notes.getAsJsonArray("lyric_echoes").size());
            record.add("study", study);
            record.addProperty("creative_approach", condition.studyDirection()
                    ? SongReflectionTrial.approachFor(notes.get("connection").getAsString()).name()
                    : SongReflectionTrial.CreativeApproach.AUTO.name());
            record.addProperty("passage_moment", landing == null ? null : landing.musicalMoment());
            record.addProperty("intended_effect_sent", landing == null ? null : condition.studyDirection()
                    ? notes.get("intended_effect").getAsString().strip().replaceAll("\\s+", " ")
                    : landing.intendedEffect());
            long writeStarted = System.nanoTime();
            var result = condition.studyDirection() ? trial.writeWithStudyDirection(run, studied, landing)
                    : trial.write(run, studied, landing, SongReflectionTrial.CreativeApproach.AUTO, condition.writer());
            record.addProperty("writer_ms", (System.nanoTime() - writeStarted) / 1_000_000);
            JsonObject branches = result.branches();
            for (String[] branch : BATCH_BRANCHES) {
                JsonObject choice = branches.getAsJsonObject(branch[0]);
                if (SongReflectionTrial.copiedSourceWords(choice.get("reflection").getAsString(), material) > 8) {
                    choice.remove("reflection");
                    choice.addProperty("reflection_omitted_for_source_overlap", true);
                }
            }
            record.add("branches", branches);
            JsonArray warnings = new JsonArray();
            result.reviewWarnings().forEach(warnings::add);
            record.add("review_warnings", warnings);
            record.addProperty("status", "generated");
        } catch (ReflectionProviderException | RuntimeException failure) {
            record.addProperty("status", "failed");
            record.addProperty("failure", failure instanceof ReflectionProviderException provider
                    ? provider.failureType().name() + ": " + provider.getMessage()
                    : failure.getClass().getSimpleName());
        }
        return record;
    }

    private static String batchCaseHeader(BatchCase item, SongMaterial material, CompletedRunEvidence run,
            SongReflectionTrial.PassageLanding landing, String listeningNote) {
        JsonObject facts = SongReflectionTrial.facts(run);
        StringBuilder text = new StringBuilder("\n## " + material.trackTitle() + " — " + material.artistName() + "\n\n");
        text.append("Fictional run: ").append(facts.get("start_time_text").getAsString()).append(" to ")
                .append(facts.get("finish_time_text").getAsString());
        if (facts.has("distance_text")) text.append(" · ").append(facts.get("distance_text").getAsString());
        if (facts.has("active_duration_text")) {
            text.append(" · ").append(facts.get("active_duration_text").getAsString());
        }
        text.append(" · Energy before: ")
                .append(facts.has("energy_before") ? facts.get("energy_before").getAsString() : "not answered")
                .append("\n\nMusic that follows: ")
                .append(listeningNote != null ? listeningNote
                        : landing == null ? "the opening of the recording" : landing.musicalMoment()).append("\n");
        if (item.referenceText() != null && listeningNote == null) {
            text.append("\nAccepted reference (").append(item.referenceLabel())
                    .append("; hand-chosen direction and a listened-to passage): ").append(item.referenceText())
                    .append("\n");
        }
        return text.toString();
    }

    private static String batchReviewBlock(String label, int repeat, JsonObject record) {
        StringBuilder text = new StringBuilder("\n### Repeat " + repeat + " — Version " + label + "\n\n");
        if (record == null || !"generated".equals(record.get("status").getAsString())) {
            return text.append("_This version did not generate; generations.json has the reason._\n").toString();
        }
        JsonObject branches = record.getAsJsonObject("branches");
        for (String[] branch : BATCH_BRANCHES) {
            JsonObject choice = branches.getAsJsonObject(branch[0]);
            text.append("- **").append(branch[1]).append(":** ").append(choice.has("reflection")
                    ? choice.get("reflection").getAsString() : "[text omitted: overlaps the supplied song source]")
                    .append("\n");
        }
        return text.toString();
    }

    private static JsonObject batchSummary(BatchPlan plan, int calls, long[] tokens, long started, int finished,
            int planned) {
        JsonObject summary = new JsonObject();
        summary.addProperty("batch", plan.name());
        summary.addProperty("model", BATCH_MODEL);
        summary.addProperty("before", describe(plan.before()));
        summary.addProperty("after", describe(plan.after()));
        summary.addProperty("shared_study", plan.sharedStudy());
        summary.addProperty("generations_finished", finished);
        summary.addProperty("generations_planned", planned);
        summary.addProperty("anthropic_calls", calls);
        summary.addProperty("input_tokens", tokens[0]);
        summary.addProperty("output_tokens", tokens[1]);
        summary.addProperty("estimated_cost_usd", String.format(java.util.Locale.US, "%.2f",
                tokens[0] * 4.0 / 1_000_000 + tokens[1] * 20.0 / 1_000_000));
        summary.addProperty("price_basis", "Opus 5.5 list price: $4 per million input, $20 per million output tokens");
        summary.addProperty("seconds", (System.nanoTime() - started) / 1_000_000_000L);
        return summary;
    }

    private static String describe(BatchCondition condition) {
        return condition.study().prompt + " + " + condition.writer().prompt
                + (condition.studyDirection() ? " (study direction)" : "")
                + (condition.landingSent() ? "" : " (no passage landing sent)");
    }

    private static void writeBatchFiles(java.nio.file.Path folder, JsonArray generations, JsonObject key,
            String review, JsonObject summary) throws java.io.IOException {
        var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        java.nio.file.Files.writeString(folder.resolve("generations.json"), gson.toJson(generations));
        java.nio.file.Files.writeString(folder.resolve("key.json"), gson.toJson(key));
        java.nio.file.Files.writeString(folder.resolve("review.md"), review);
        java.nio.file.Files.writeString(folder.resolve("summary.json"), gson.toJson(summary));
    }

    private static String trialIdentity(java.util.Map<String, String> environment, String key, String fallback) {
        String value = environment.getOrDefault(key, fallback).strip();
        if (value.isEmpty() || value.length() > 200 || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid controlled trial identity.");
        }
        return value;
    }
}
