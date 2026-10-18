package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.runstate.reflection.MusixmatchSongAnalysis.SongMaterial;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;
import com.runstate.reflection.ReflectionContract.MusicState;
import com.runstate.reflection.ReflectionContract.PreRunEnergy;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared two-call song-study and writer engine; retained trial entry points reproduce accepted auditions. */
final class SongReflectionTrial {
    enum CreativeApproach { AUTO, DIRECT, PERSONA_ONLY, BLEND }

    private static final List<String> BRANCHES = List.of("spent", "feeling_good", "powered_up", "no_selection");
    private static final Set<String> NOTE_FIELDS = Set.of("attitude", "delivery", "artist_context",
            "title_idea", "lyric_echoes", "imagery");
    private static final Set<String> CHOICE_FIELDS = Set.of("lead", "accent", "anchor", "fit", "reflection");
    private static final Set<String> LEADS = Set.of("persona", "title", "artist", "lyric_echo", "message", "run_only");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.:]\\d+)*(?![\\p{L}\\p{N}])");
    private static final Pattern BANNED = Pattern.compile(
            "(?i)\\b(testament|symphony|unleash\\w*|journey|fuel\\w*|crushed it|conquer\\w*|delve\\w*)\\b");
    @FunctionalInterface
    interface SongExchange {
        JsonObject exchange(JsonObject input, String prompt, JsonObject schema) throws ReflectionProviderException;
    }
    private final SongExchange exchange;

    SongReflectionTrial(AnthropicReflectionProvider client) {
        this(java.util.Objects.requireNonNull(client)::exchangeForSongTrial);
    }

    SongReflectionTrial(SongExchange exchange) {
        this.exchange = java.util.Objects.requireNonNull(exchange);
    }

    TrialResult generate(CompletedRunEvidence evidence, SongMaterial material) throws ReflectionProviderException {
        return generate(evidence, material, null, CreativeApproach.AUTO);
    }

    TrialResult generate(CompletedRunEvidence evidence, SongMaterial material, PassageLanding landing)
            throws ReflectionProviderException {
        return generate(evidence, material, landing, CreativeApproach.AUTO);
    }

    TrialResult generate(CompletedRunEvidence evidence, SongMaterial material, PassageLanding landing,
                         CreativeApproach approach) throws ReflectionProviderException {
        java.util.Objects.requireNonNull(approach, "approach");
        requireMatching(evidence, material, landing);
        return write(evidence, study(material), landing, approach);
    }

    /** Source-only work is safe to prepare before final run facts exist. */
    StudiedSong study(SongMaterial material) throws ReflectionProviderException {
        java.util.Objects.requireNonNull(material, "material");
        JsonObject notes = exchange.exchange(songInput(material),
                prompt("song-study-trial-v1.txt"), notesSchema());
        validateNotes(notes, material);
        return new StudiedSong(material, notes);
    }

    TrialResult write(CompletedRunEvidence evidence, StudiedSong studied, PassageLanding landing,
                      CreativeApproach approach) throws ReflectionProviderException {
        java.util.Objects.requireNonNull(approach, "approach");
        SongMaterial material = studied.material();
        requireMatching(evidence, material, landing);
        JsonObject notes = studied.linerNotes();
        validateNotes(notes, material);
        JsonObject facts = facts(evidence);
        JsonObject writerInput = new JsonObject();
        writerInput.add("run_facts", facts);
        writerInput.addProperty("song_title", material.trackTitle());
        writerInput.addProperty("artist_name", material.artistName());
        writerInput.addProperty("creative_approach", approach.name().toLowerCase(Locale.ROOT));
        writerInput.addProperty("liner_notes_provenance", "MODEL_INTERPRETATION_NOT_RUN_EVIDENCE");
        writerInput.add("liner_notes", notes.deepCopy());
        JsonObject context = new JsonObject();
        context.addProperty("provenance", "MUSIXMATCH_INTERPRETATION_NOT_RUN_EVIDENCE");
        context.add("themes", strings(material.themes().stream().map(MusixmatchSongAnalysis.ThemeMaterial::theme).toList()));
        context.add("moods", strings(material.moods()));
        if (material.meaningExplanation() != null) {
            context.addProperty("meaning", material.meaningExplanation());
        }
        writerInput.add("song_context", context);
        writerInput.add("allowed_anchors", strings(anchors(evidence)));
        if (landing != null) {
            JsonObject target = new JsonObject();
            target.addProperty("track_title", material.trackTitle());
            target.addProperty("artist_name", material.artistName());
            target.addProperty("musical_moment", landing.musicalMoment());
            target.addProperty("intended_effect", landing.intendedEffect());
            target.addProperty("provenance", "EDITORIAL_AUDITION_DESCRIPTION_NOT_LYRICS_TIMING_OR_RUN_EVIDENCE");
            writerInput.add("passage_landing", target);
        }
        JsonObject branches = exchange.exchange(writerInput,
                prompt("song-writer-trial-v1.txt"), writerSchema(evidence));
        List<String> warnings = validateBranches(branches, facts, evidence, notes, material, approach);
        return new TrialResult(notes, branches, new CandidateReflections(
                branches.getAsJsonObject("spent").get("reflection").getAsString(),
                branches.getAsJsonObject("feeling_good").get("reflection").getAsString(),
                branches.getAsJsonObject("powered_up").get("reflection").getAsString(),
                branches.getAsJsonObject("no_selection").get("reflection").getAsString()), warnings);
    }

    private static void requireMatching(CompletedRunEvidence evidence, SongMaterial material, PassageLanding landing)
            throws ReflectionProviderException {
        if (evidence.musicContext().state() != MusicState.MUSIC
                || !same(evidence.musicContext().trackTitle(), material.trackTitle())
                || !same(evidence.musicContext().artistName(), material.artistName())) {
            throw invalid("The trial requires exact matched song material.");
        }
        if (landing != null && (!same(landing.trackTitle(), material.trackTitle())
                || !same(landing.artistName(), material.artistName()))) {
            throw invalid("The passage landing requires exact matched song material.");
        }
    }

    /** Bounded memory-only source/validated notes; never stores run facts or generated run replies. */
    record StudiedSong(SongMaterial material, JsonObject linerNotes) {
        StudiedSong {
            java.util.Objects.requireNonNull(material, "material");
            linerNotes = linerNotes.deepCopy();
        }
        @Override public JsonObject linerNotes() { return linerNotes.deepCopy(); }
        @Override public String toString() { return "StudiedSong[sourceAndNotes=redacted]"; }
    }

    /** An editorial listening target, never a lyric source, timestamp or claim about the runner. */
    record PassageLanding(String trackTitle, String artistName, String musicalMoment, String intendedEffect) {
        PassageLanding {
            if (!validLandingText(trackTitle, 200) || !validLandingText(artistName, 200)
                    || !validLandingText(musicalMoment, 400) || !validLandingText(intendedEffect, 400)) {
                throw new IllegalArgumentException("Invalid bounded passage landing.");
            }
        }

        @Override
        public String toString() { return "PassageLanding[editorialText=redacted]"; }
    }

    private static boolean validLandingText(String value, int maximum) {
        return value != null && !value.isBlank() && value.length() <= maximum
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static JsonObject songInput(SongMaterial material) {
        JsonObject song = new JsonObject();
        song.addProperty("source", "MUSIXMATCH_REQUEST_SCOPED");
        song.addProperty("track_title", material.trackTitle());
        song.addProperty("artist_name", material.artistName());
        song.add("genres", strings(material.genres()));
        song.add("moods", strings(material.moods()));
        if (material.meaningExplanation() != null) song.addProperty("meaning", material.meaningExplanation());
        JsonArray themes = new JsonArray();
        material.themes().forEach(theme -> {
            JsonObject item = new JsonObject();
            item.addProperty("theme", theme.theme());
            item.add("quotes", strings(theme.quotes()));
            themes.add(item);
        });
        song.add("themes", themes);
        JsonArray entities = new JsonArray();
        material.entities().forEach(entity -> {
            JsonObject item = new JsonObject();
            item.addProperty("name", entity.name());
            if (entity.description() != null) item.addProperty("description", entity.description());
            item.add("categories", strings(entity.categories()));
            entities.add(item);
        });
        song.add("entities", entities);
        if (material.snippetText() != null) song.addProperty("snippet_text", material.snippetText());
        return song;
    }

    private static JsonObject facts(CompletedRunEvidence evidence) {
        JsonObject facts = AnthropicReflectionProvider.reflectionFacts(evidence);
        DateTimeFormatter readable = DateTimeFormatter.ofPattern("EEEE h:mm a", Locale.US)
                .withZone(ZoneId.of(evidence.startTimezoneId()));
        facts.addProperty("start_time_text", readable.format(Instant.ofEpochMilli(evidence.officialStartEpochMillis())));
        facts.addProperty("finish_time_text", readable.format(Instant.ofEpochMilli(evidence.finishEpochMillis())));
        facts.addProperty("elapsed_duration_text", durationText(
                evidence.finishEpochMillis() - evidence.officialStartEpochMillis()) + " elapsed");
        if (evidence.activeDurationMillis() != null && evidence.activeDurationMillis() > 0) {
            facts.addProperty("active_duration_text", durationText(evidence.activeDurationMillis()) + " active");
        }
        if (evidence.preRunEnergy() != PreRunEnergy.UNKNOWN) {
            facts.addProperty("energy_before", evidence.preRunEnergy().name());
        }
        return facts;
    }

    private static String durationText(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        return minutes + " minutes" + (seconds % 60 == 0 ? "" : " " + seconds % 60 + " seconds");
    }

    private static List<String> anchors(CompletedRunEvidence evidence) {
        List<String> values = new ArrayList<>(List.of("completed_run", "time_of_day", "conditional_post_energy"));
        if (evidence.metrics().distanceMeters() != null) values.add("distance");
        if (evidence.activeDurationMillis() != null && evidence.activeDurationMillis() > 0) values.add("active_duration");
        if (evidence.preRunEnergy() != PreRunEnergy.UNKNOWN) values.add("pre_run_energy");
        if (evidence.weatherContext() != null) values.add("weather_near_start");
        return List.copyOf(values);
    }

    private static void validateNotes(JsonObject notes, SongMaterial source) throws ReflectionProviderException {
        exactFields(notes, NOTE_FIELDS);
        for (String key : List.of("attitude", "delivery", "artist_context", "title_idea")) {
            text(notes, key, 350, key.equals("attitude") || key.equals("delivery"));
        }
        JsonArray echoes = array(notes, "lyric_echoes", 3);
        for (JsonElement value : echoes) {
            if (!value.isJsonObject()) throw invalid("Invalid song-study echo.");
            JsonObject echo = value.getAsJsonObject();
            exactFields(echo, Set.of("words", "theme"));
            String words = text(echo, "words", 160, true);
            String theme = text(echo, "theme", 120, true);
            boolean fromTheme = source.themes().stream().anyMatch(item -> item.theme().equals(theme)
                    && item.quotes().stream().anyMatch(quote -> quote.contains(words)));
            boolean fromSnippet = theme.equals("snippet") && source.snippetText() != null
                    && source.snippetText().contains(words);
            if (!fromTheme && !fromSnippet) throw invalid("Song-study echo is not in its stated source.");
            if (wordCount(words) > 8) {
                // Shorten only an already verified verbatim source span, never invent or rewrite lyrics.
                Matcher word = Pattern.compile("\\S+").matcher(words);
                int end = 0;
                for (int index = 0; index < 8 && word.find(); index++) end = word.end();
                echo.addProperty("words", words.substring(0, end));
            }
        }
        for (JsonElement image : array(notes, "imagery", 4)) {
            if (!image.isJsonPrimitive() || !image.getAsJsonPrimitive().isString()
                    || image.getAsString().isBlank() || image.getAsString().length() > 120) {
                throw invalid("Invalid song-study imagery.");
            }
        }
    }

    private static List<String> validateBranches(JsonObject branches, JsonObject facts, CompletedRunEvidence evidence,
                                         JsonObject notes, SongMaterial source, CreativeApproach approach)
            throws ReflectionProviderException {
        exactFields(branches, Set.copyOf(BRANCHES));
        Set<String> allowedNumbers = numbers(facts.toString());
        List<String> available = anchors(evidence);
        List<String> warnings = new ArrayList<>();
        for (String key : BRANCHES) {
            if (!branches.get(key).isJsonObject()) throw invalid("Invalid trial branch.");
            JsonObject branch = branches.getAsJsonObject(key);
            exactFields(branch, CHOICE_FIELDS);
            String lead = text(branch, "lead", 30, true);
            text(branch, "accent", 200, false);
            String anchor = text(branch, "anchor", 40, true);
            String fit = text(branch, "fit", 10, true);
            String reflection = text(branch, "reflection", 900, true);
            if (!LEADS.contains(lead) || !available.contains(anchor)
                    || !Set.of("strong", "medium", "weak").contains(fit)
                    || key.equals("no_selection") && anchor.equals("conditional_post_energy")) {
                throw invalid("Trial branch selected unavailable evidence.");
            }
            if (wordCount(reflection) > 55) warnings.add(key + ":word_budget_review_required");
            if (BANNED.matcher(reflection).find()) warnings.add(key + ":word_choice_review_required");
            if (!allowedNumbers.containsAll(numbers(reflection))) warnings.add(key + ":numeric_tokens_review_required");
            long usedEchoes = notes.getAsJsonArray("lyric_echoes").asList().stream()
                    .map(value -> value.getAsJsonObject().get("words").getAsString())
                    .distinct().filter(words -> normalizedWords(reflection).contains(normalizedWords(words))).count();
            if (usedEchoes > 1) warnings.add(key + ":multiple_echo_matches_review_required");
            if (lead.equals("lyric_echo") && usedEchoes == 0) warnings.add(key + ":adapted_echo_review_required");
            if (approach == CreativeApproach.PERSONA_ONLY) {
                if (containsPhrase(reflection, source.trackTitle()) || containsPhrase(reflection, source.artistName())) {
                    warnings.add(key + ":persona_only_identity_reference_review_required");
                }
                if (usedEchoes > 0) warnings.add(key + ":persona_only_lyric_echo_review_required");
            }
            if (copiedSourceWords(reflection, source) > 8) warnings.add(key + ":source_overlap_exceeds_display_budget");
            // A numeric-token check is NOT a factual reviewer. This isolated result still needs judgment.
        }
        return List.copyOf(warnings);
    }

    /** Conservative overlap bound, not a detector for remembered or otherwise unsupplied lyrics. */
    static int copiedSourceWords(String output, SongMaterial source) {
        List<String> words = tokens(output);
        boolean[] copied = new boolean[words.size()];
        List<String> passages = new ArrayList<>();
        source.themes().forEach(theme -> passages.addAll(theme.quotes()));
        if (source.snippetText() != null) passages.add(source.snippetText());
        if (source.meaningExplanation() != null) passages.add(source.meaningExplanation());
        for (String passage : passages) {
            List<String> original = tokens(passage);
            for (int target = 0; target + 2 < words.size(); target++) {
                for (int start = 0; start + 2 < original.size(); start++) {
                    if (words.subList(target, target + 3).equals(original.subList(start, start + 3))) {
                        copied[target] = copied[target + 1] = copied[target + 2] = true;
                    }
                }
            }
        }
        int count = 0;
        for (boolean value : copied) if (value) count++;
        return count;
    }

    private static List<String> tokens(String text) {
        return Pattern.compile("[\\p{L}\\p{N}]+(?:['’][\\p{L}]+)?").matcher(text.toLowerCase(Locale.ROOT))
                .results().map(java.util.regex.MatchResult::group).toList();
    }

    private static String normalizedWords(String text) { return " " + String.join(" ", tokens(text)) + " "; }

    private static boolean containsPhrase(String text, String phrase) {
        String quoted = Pattern.quote(phrase.strip().replaceAll("\\s+", " ")).replace(" ", "\\E\\s+\\Q");
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])" + quoted + "(?![\\p{L}\\p{N}])")
                .matcher(text).find();
    }

    private static Set<String> numbers(String text) {
        Set<String> result = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String value = matcher.group();
            if (value.indexOf(':') >= 0) {
                result.add(value.replaceAll("(?<!\\d)0+(?=\\d)", ""));
            } else {
                result.add(new BigDecimal(value).stripTrailingZeros().toPlainString());
            }
        }
        return result;
    }

    private static int wordCount(String value) { return value.strip().split("\\s+").length; }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.strip().replaceAll("\\s+", " ")
                .equalsIgnoreCase(b.strip().replaceAll("\\s+", " "));
    }

    private static void exactFields(JsonObject object, Set<String> expected) throws ReflectionProviderException {
        if (!object.keySet().equals(expected)) throw invalid("Unexpected song-trial fields.");
    }

    private static String text(JsonObject object, String key, int max, boolean nonblank)
            throws ReflectionProviderException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalid("Invalid song-trial text.");
        }
        String result = value.getAsString();
        if (result.length() > max || nonblank && result.isBlank()
                || result.codePoints().anyMatch(c -> Character.isISOControl(c) && !Character.isWhitespace(c))) {
            throw invalid("Invalid song-trial text bounds.");
        }
        return result;
    }

    private static JsonArray array(JsonObject object, String key, int max) throws ReflectionProviderException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonArray() || value.getAsJsonArray().size() > max) {
            throw invalid("Invalid song-trial array.");
        }
        return value.getAsJsonArray();
    }

    private static JsonArray strings(List<String> values) {
        JsonArray result = new JsonArray();
        values.forEach(result::add);
        return result;
    }

    private static JsonObject stringSchema() {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        return schema;
    }

    private static JsonObject arraySchema(JsonObject items) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "array");
        schema.add("items", items);
        return schema;
    }

    private static JsonObject objectSchema(Map<String, JsonObject> fields) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        JsonObject properties = new JsonObject();
        fields.forEach(properties::add);
        schema.add("properties", properties);
        schema.add("required", strings(new ArrayList<>(fields.keySet())));
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject notesSchema() {
        Map<String, JsonObject> fields = new LinkedHashMap<>();
        for (String key : List.of("attitude", "delivery", "artist_context", "title_idea")) fields.put(key, stringSchema());
        fields.put("lyric_echoes", arraySchema(objectSchema(Map.of("words", stringSchema(), "theme", stringSchema()))));
        fields.put("imagery", arraySchema(stringSchema()));
        return objectSchema(fields);
    }

    private static JsonObject enumSchema(List<String> values) {
        JsonObject schema = stringSchema();
        schema.add("enum", strings(values));
        return schema;
    }

    private static JsonObject writerSchema(CompletedRunEvidence evidence) {
        Map<String, JsonObject> branches = new LinkedHashMap<>();
        for (String key : BRANCHES) {
            Map<String, JsonObject> branch = new LinkedHashMap<>();
            branch.put("lead", enumSchema(List.of("persona", "title", "artist", "lyric_echo", "message", "run_only")));
            branch.put("accent", stringSchema());
            branch.put("anchor", enumSchema(anchors(evidence).stream().filter(anchor ->
                    !key.equals("no_selection") || !anchor.equals("conditional_post_energy")).toList()));
            branch.put("fit", enumSchema(List.of("strong", "medium", "weak")));
            branch.put("reflection", stringSchema());
            branches.put(key, objectSchema(branch));
        }
        return objectSchema(branches);
    }

    private static String prompt(String name) throws ReflectionProviderException {
        try (InputStream input = SongReflectionTrial.class.getResourceAsStream("/prompts/" + name)) {
            if (input == null) throw invalid("Song-trial prompt is missing.");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException unavailable) {
            throw invalid("Song-trial prompt is unavailable.");
        }
    }

    private static ReflectionProviderException invalid(String message) {
        return new ReflectionProviderException(FailureType.INVALID_RESPONSE, message);
    }

    /** Source material/notes never appear in an accidental log statement. No result is persisted here. */
    record TrialResult(JsonObject linerNotes, JsonObject branches, CandidateReflections reflections, List<String> reviewWarnings) {
        TrialResult {
            linerNotes = linerNotes.deepCopy();
            branches = branches.deepCopy();
            reviewWarnings = List.copyOf(reviewWarnings);
        }
        @Override public JsonObject linerNotes() { return linerNotes.deepCopy(); }
        @Override public JsonObject branches() { return branches.deepCopy(); }
        @Override public String toString() { return "SongReflectionTrialResult[experimental, content=redacted]"; }
    }
}
