package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;
import com.runstate.reflection.ReflectionContract.MusicContext;
import com.runstate.reflection.ReflectionContract.RunMetricsEvidence;
import com.runstate.reflection.ReflectionContract.TelemetryCoverage;
import com.runstate.reflection.ReflectionContract.WeatherContext;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Anthropic Messages API adapter for the versioned mobile reflection prompt. */
public final class AnthropicReflectionProvider implements ReflectionProvider {

    static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";
    static final String DEFAULT_MUSIC_EDITOR_MODEL = "claude-sonnet-4-6";
    static final String DEFAULT_SONG_WRITER_MODEL = "claude-opus-5-5";
    static final Duration SONG_PIPELINE_TIMEOUT = Duration.ofSeconds(90);
    static final Duration DEFAULT_CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(45);
    static final int DEFAULT_MAX_OUTPUT_TOKENS = 2_048;

    private static final URI MESSAGES_ENDPOINT = URI.create("https://api.anthropic.com/v1/messages");
    private static final String API_VERSION = "2023-06-01";
    private static final String PROMPT_RESOURCE = "/prompts/mobile-reflection-v1.txt";
    private static final List<String> CANDIDATE_KEYS_IN_ORDER = List.of(
            "spent",
            "feeling_good",
            "powered_up",
            "no_selection"
    );
    private static final Set<String> CANDIDATE_KEYS = Set.copyOf(CANDIDATE_KEYS_IN_ORDER);
    private final ProviderConfiguration configuration;
    private final Transport transport;
    private final String systemPrompt;
    private final SongMaterialLookup songAnalysisLookup;

    /** Bounded matched song material only; the lookup never receives run facts or listening history. */
    @FunctionalInterface
    interface SongMaterialLookup {
        Optional<MusixmatchSongAnalysis.SongMaterial> lookup(String trackTitle, String artistName);
    }

    /** Builds the production adapter from backend-only environment configuration. */
    public AnthropicReflectionProvider() throws ReflectionProviderException {
        this(configurationFrom(System.getenv()), configuredSongAnalysis(System.getenv()));
    }

    private AnthropicReflectionProvider(ProviderConfiguration configuration, SongMaterialLookup songAnalysisLookup)
            throws ReflectionProviderException {
        this(
                configuration,
                productionTransport(configuration),
                songAnalysisLookup
        );
    }

    AnthropicReflectionProvider(ProviderConfiguration configuration, Transport transport)
            throws ReflectionProviderException {
        this(configuration, transport, (title, artist) -> Optional.empty());
    }

    /** Shares the unchanged production HTTP path with the explicitly enabled live integration check. */
    static Transport productionTransport(ProviderConfiguration configuration) {
        return new JdkTransport(configuration.connectionTimeout());
    }

    AnthropicReflectionProvider(ProviderConfiguration configuration, Transport transport,
                               SongMaterialLookup songAnalysisLookup) throws ReflectionProviderException {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.songAnalysisLookup = Objects.requireNonNull(songAnalysisLookup, "songAnalysisLookup");
        this.systemPrompt = loadSystemPrompt();
    }

    @Override
    public CandidateReflections generate(CompletedRunEvidence evidence) throws ReflectionProviderException {
        return generateForDelivery(evidence).candidates();
    }

    @Override
    public GeneratedReflections generateForDelivery(CompletedRunEvidence evidence)
            throws ReflectionProviderException {
        Objects.requireNonNull(evidence, "evidence");
        long started = System.nanoTime();
        MusicContext music = evidence.musicContext();
        Optional<MusixmatchSongAnalysis.SongMaterial> material = Optional.empty();
        if (music.state() == ReflectionContract.MusicState.MUSIC
                && music.trackTitle() != null && music.artistName() != null) {
            try {
                Optional<MusixmatchSongAnalysis.SongMaterial> lookedUp = songAnalysisLookup.lookup(
                        music.trackTitle(), music.artistName());
                if (lookedUp != null) material = lookedUp.filter(source ->
                        sameSongText(music.trackTitle(), source.trackTitle())
                                && sameSongText(music.artistName(), source.artistName()));
            } catch (RuntimeException unavailable) {
                // Optional source failure permits ordinary text, never a paired-song acknowledgement.
                // Do not log the exception: provider URLs can carry credentials.
            }
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new ReflectionProviderException(FailureType.TRANSPORT, "The reflection request was interrupted.");
        }
        if (material.isPresent()) {
            SongReflectionTrial.PassageLanding landing = acceptedLanding(music);
            SongReflectionTrial engine = new SongReflectionTrial((input, prompt, schema) -> {
                // The fixed audition prompt/engine stay intact; real-app source boundaries remain explicit.
                if (input.has("run_facts")) addObservedContext(evidence, input.getAsJsonObject("run_facts"));
                return exchangeSong(input, prompt, schema, configuration.songWriterModel(), remainingSongTimeout(started));
            });
            SongReflectionTrial.TrialResult result = engine.generate(evidence, material.get(), landing);
            // Mechanical source/number/schema checks are bounds, not a semantic factual oracle.
            if (!result.reviewWarnings().isEmpty()) {
                throw invalidResponse("The song reflection did not pass its bounded output checks.");
            }
            return new GeneratedReflections(result.reflections(), landing == null ? null : music.songCue().cueId());
        }
        JsonObject serialized = serializeEvidence(evidence);
        // An editorial landing alone must not masquerade as an analysis-derived paired reply.
        serialized.getAsJsonObject("music_context").remove("song_cue");
        CandidateReflections ordinary = parseSuccessfulResponse(exchange(buildRequestBody(serialized,
                systemPrompt, candidateSchema(), configuration.model()), configuration.requestTimeout()));
        return new GeneratedReflections(ordinary, null);
    }

    private static void addObservedContext(CompletedRunEvidence evidence, JsonObject facts) {
        if (evidence.musicContext().provenance() == ReflectionContract.MusicProvenance.MEDIA_SESSION_PARTIAL) {
            facts.addProperty("music_observation_scope", "ONE_PARTIAL_METADATA_SNAPSHOT_NOT_COMPLETE_LISTENING_OR_MUSIC_EFFECT");
        }
        WeatherContext weather = evidence.weatherContext();
        if (weather != null) {
            JsonObject source = new JsonObject();
            source.addProperty("source", weather.source().name());
            source.addProperty("resolution", weather.resolution().name());
            source.addProperty("scope", "MODELED_AREA_ESTIMATE_NEAR_RUN_START_NOT_CONTINUOUS_WHOLE_RUN_OBSERVATION");
            source.addProperty("valid_from_epoch_millis", weather.validFromEpochMillis());
            source.addProperty("valid_to_epoch_millis", weather.validToEpochMillis());
            source.addProperty("temperature_celsius", weather.temperatureCelsius());
            source.addProperty("apparent_temperature_celsius", weather.apparentTemperatureCelsius());
            source.addProperty("relative_humidity_percent", weather.relativeHumidityPercent());
            source.addProperty("wind_speed_kmh", weather.windSpeedKmh());
            source.addProperty("wmo_weather_code", weather.weatherCode());
            facts.add("weather_context", source);
        }
    }

    private Duration remainingSongTimeout(long started) throws ReflectionProviderException {
        Duration remaining = SONG_PIPELINE_TIMEOUT.minusNanos(System.nanoTime() - started);
        if (remaining.isNegative() || remaining.isZero()) {
            throw new ReflectionProviderException(FailureType.TIMEOUT, "The song reflection deadline expired.");
        }
        return remaining.compareTo(configuration.requestTimeout()) < 0 ? remaining : configuration.requestTimeout();
    }

    private static SongReflectionTrial.PassageLanding acceptedLanding(MusicContext music) {
        if (music.songCue() == null) return null;
        String[] definition = switch (music.songCue().cueId()) {
            case "lovely_day_bill_withers_v1" -> new String[]{"Lovely Day", "Bill Withers",
                    "A compact sequence of the recognizable repeated-title chorus.",
                    "A warm affirmative musical response to one concrete part of this completed run. Keep reported feelings conditional on each branch, without claiming perfect weather or a perfect whole day."};
            case "stronger_kanye_west_v1" -> new String[]{"Stronger", "Kanye West",
                    "The already auditioned forceful electronic opening, brief and recognizable.",
                    "A concise self-assured finish to this completed run. Let attitude and cadence carry the connection without names, quoted lyrics or claimed training gains."};
            case "good_as_hell_lizzo_v1" -> new String[]{"Good as Hell", "Lizzo",
                    "A short recognizable title response near the end of a chorus, followed by an annotated gap.",
                    "A bright playful musical response to this completed run. Combine attitude with one light source-grounded song detail. Keep Energy conditional, without inferred training gains or a relationship story."};
            case "gods_plan_drake_v1" -> new String[]{"God's Plan", "Drake",
                    "A short recognizable title hook and its following statement.",
                    "Let the record answer a relaxed, self-assured finish to this completed run. A connection through attitude and delivery can work without explaining the song or claiming why this runner ran."};
            case "sunday_best_surfaces_v2" -> new String[]{"Sunday Best", "Surfaces",
                    "Everyday optimism: noticing something good in showing up and finishing",
                    "A small good thing to take from finishing leads into the early upbeat vocal hook. Feeling Good can acknowledge that reported feeling; Spent recognizes finishing without claiming good feelings. With unknown Energy, the positive note is completing the run, not an invented mood. This is editorial interpretation, not lyric text, artist persona or evidence that music changed the run."};
            default -> null;
        };
        if (definition == null || !sameSongText(music.trackTitle(), definition[0])
                || !sameSongText(music.artistName(), definition[1])
                || !music.songCue().theme().equals(definition[2])
                || !music.songCue().connectionNote().equals(definition[3])) return null;
        return new SongReflectionTrial.PassageLanding(definition[0], definition[1], definition[2], definition[3]);
    }

    private String exchange(String requestBody, Duration timeout) throws ReflectionProviderException {
        if (Thread.currentThread().isInterrupted()) {
            throw new ReflectionProviderException(FailureType.TRANSPORT,
                    "The Anthropic request was interrupted.");
        }
        TransportRequest request = new TransportRequest(
                configuration.endpoint(),
                configuration.apiKey(),
                API_VERSION,
                requestBody,
                timeout
        );

        TransportResponse response;
        try {
            response = transport.send(request);
        } catch (HttpTimeoutException timedOut) {
            throw new ReflectionProviderException(
                    FailureType.TIMEOUT,
                    "The Anthropic request timed out.",
                    timedOut
            );
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ReflectionProviderException(
                    FailureType.TRANSPORT,
                    "The Anthropic request was interrupted.",
                    interrupted
            );
        } catch (IOException connectionFailure) {
            throw new ReflectionProviderException(
                    FailureType.TRANSPORT,
                    "The Anthropic connection failed.",
                    connectionFailure
            );
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ReflectionProviderException(
                    FailureType.PROVIDER_ERROR,
                    "Anthropic returned HTTP status " + response.statusCode() + "."
            );
        }

        return response.body();
    }

    static ProviderConfiguration configurationFrom(Map<String, String> environment)
            throws ReflectionProviderException {
        Objects.requireNonNull(environment, "environment");

        String apiKey = environment.get("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank() || !isSafeHeaderValue(apiKey)) {
            throw new ReflectionProviderException(
                    FailureType.CONFIGURATION,
                    "ANTHROPIC_API_KEY must contain a valid backend API key."
            );
        }

        String configuredModel = environment.get("ANTHROPIC_MODEL");
        String model = configuredModel == null ? DEFAULT_MODEL : configuredModel.trim();
        if (model.isEmpty() || !model.matches("[A-Za-z0-9._-]+")) {
            throw new ReflectionProviderException(
                    FailureType.CONFIGURATION,
                    "ANTHROPIC_MODEL must contain a valid model identifier."
            );
        }
        String configuredEditor = environment.get("ANTHROPIC_MUSIC_EDITOR_MODEL");
        String editorModel = configuredEditor == null ? DEFAULT_MUSIC_EDITOR_MODEL : configuredEditor.trim();
        if (editorModel.isEmpty() || !editorModel.matches("[A-Za-z0-9._-]+")) {
            throw new ReflectionProviderException(FailureType.CONFIGURATION,
                    "ANTHROPIC_MUSIC_EDITOR_MODEL must contain a valid model identifier.");
        }

        String writerModel = environment.getOrDefault("ANTHROPIC_SONG_WRITER_MODEL", DEFAULT_SONG_WRITER_MODEL).trim();
        if (writerModel.isEmpty() || !writerModel.matches("[A-Za-z0-9._-]+")) {
            throw new ReflectionProviderException(FailureType.CONFIGURATION,
                    "ANTHROPIC_SONG_WRITER_MODEL must contain a valid model identifier.");
        }

        return new ProviderConfiguration(
                MESSAGES_ENDPOINT,
                apiKey,
                model,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_REQUEST_TIMEOUT,
                DEFAULT_MAX_OUTPUT_TOKENS,
                editorModel,
                writerModel
        );
    }

    private static SongMaterialLookup configuredSongAnalysis(Map<String, String> environment)
            throws ReflectionProviderException {
        try {
            return MusixmatchSongAnalysis.fromEnvironment(environment)
                    .<SongMaterialLookup>map(client -> client::lookupReflectionMaterial)
                    .orElse((title, artist) -> Optional.empty());
        } catch (IllegalArgumentException invalidConfiguration) {
            throw new ReflectionProviderException(FailureType.CONFIGURATION,
                    "MUSIXMATCH_API_KEY must contain a valid backend API key.");
        }
    }

    private String buildRequestBody(JsonObject serialized, String prompt, JsonObject schema, String model) {
        JsonObject request = new JsonObject();
        request.addProperty("model", model);
        request.addProperty("max_tokens", configuration.maxOutputTokens());
        request.addProperty("system", prompt);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", serialized.toString());
        JsonArray messages = new JsonArray();
        messages.add(message);
        request.add("messages", messages);

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("schema", schema);
        JsonObject outputConfiguration = new JsonObject();
        outputConfiguration.add("format", format);
        request.add("output_config", outputConfiguration);

        return request.toString();
    }

    /** Compatibility entry for reproducible auditions; production uses the same exchange/parser with its own model. */
    JsonObject exchangeForSongTrial(JsonObject input, String prompt, JsonObject schema)
            throws ReflectionProviderException {
        return exchangeSong(input, prompt, schema, configuration.musicEditorModel(), configuration.requestTimeout());
    }

    private JsonObject exchangeSong(JsonObject input, String prompt, JsonObject schema, String model, Duration timeout)
            throws ReflectionProviderException {
        String raw = exchange(buildRequestBody(input, prompt, schema, model), timeout);
        JsonObject response = parseObject(raw, "song trial response");
        JsonElement blocks = response.get("content");
        if (blocks != null && blocks.isJsonArray()) {
            JsonArray textOnly = new JsonArray();
            for (JsonElement block : blocks.getAsJsonArray()) {
                if (!block.isJsonObject()) throw invalidResponse("Invalid song trial content block.");
                String type = requiredString(block.getAsJsonObject(), "type", "song trial content block");
                if ("text".equals(type)) textOnly.add(block);
                else if (!"thinking".equals(type) && !"redacted_thinking".equals(type)) {
                    throw invalidResponse("Unsupported song trial content block.");
                }
                // Newer models may return reasoning blocks. Never print, retain or forward them.
            }
            response.add("content", textOnly);
        }
        return parseObject(successfulOutput(response.toString()), "song trial output");
    }

    private static boolean sameSongText(String expected, String actual) {
        return expected != null && actual != null && expected.strip().replaceAll("\\s+", " ")
                .equalsIgnoreCase(actual.strip().replaceAll("\\s+", " "));
    }

    private static JsonObject serializeEvidence(CompletedRunEvidence evidence) {
        JsonObject serialized = new JsonObject();
        serialized.addProperty("official_start_epoch_millis", evidence.officialStartEpochMillis());
        serialized.addProperty("finish_epoch_millis", evidence.finishEpochMillis());
        serialized.addProperty("start_timezone_id", evidence.startTimezoneId());
        addNullableNumber(serialized, "active_duration_millis", evidence.activeDurationMillis());

        RunMetricsEvidence metrics = evidence.metrics();
        JsonObject serializedMetrics = new JsonObject();
        addNullableNumber(serializedMetrics, "distance_meters", metrics.distanceMeters());
        addNullableEnum(serializedMetrics, "source", metrics.source());
        addNullableEnum(serializedMetrics, "coverage", metrics.coverage());
        addNullableEnum(serializedMetrics, "display_unit", metrics.displayUnit());
        serialized.add("metrics", serializedMetrics);

        serialized.addProperty("pre_run_energy", evidence.preRunEnergy().name());

        MusicContext music = evidence.musicContext();
        JsonObject serializedMusic = new JsonObject();
        serializedMusic.addProperty("state", music.state().name());
        addNullableEnum(serializedMusic, "provenance", music.provenance());
        addNullableString(serializedMusic, "track_title", music.trackTitle());
        addNullableString(serializedMusic, "artist_name", music.artistName());
        addNullableString(serializedMusic, "artist_description", music.artistDescription());
        if (music.songCue() != null) {
            JsonObject cue = new JsonObject();
            cue.addProperty("cue_id", music.songCue().cueId());
            cue.addProperty("source", music.songCue().source().name());
            cue.addProperty("theme", music.songCue().theme());
            cue.addProperty("connection_note", music.songCue().connectionNote());
            serializedMusic.add("song_cue", cue);
        }
        serialized.add("music_context", serializedMusic);
        if (evidence.weatherContext() != null) {
            WeatherContext weather = evidence.weatherContext();
            JsonObject serializedWeather = new JsonObject();
            serializedWeather.addProperty("source", weather.source().name());
            serializedWeather.addProperty("resolution", weather.resolution().name());
            serializedWeather.addProperty("target_epoch_millis", weather.targetEpochMillis());
            serializedWeather.addProperty("valid_from_epoch_millis", weather.validFromEpochMillis());
            serializedWeather.addProperty("valid_to_epoch_millis", weather.validToEpochMillis());
            serializedWeather.addProperty("fetched_at_epoch_millis", weather.fetchedAtEpochMillis());
            serializedWeather.addProperty("temperature_celsius", weather.temperatureCelsius());
            serializedWeather.addProperty("apparent_temperature_celsius", weather.apparentTemperatureCelsius());
            serializedWeather.addProperty("relative_humidity_percent", weather.relativeHumidityPercent());
            serializedWeather.addProperty("wind_speed_kmh", weather.windSpeedKmh());
            serializedWeather.addProperty("weather_code", weather.weatherCode());
            serialized.add("weather_context", serializedWeather);
        }
        serialized.add("reflection_facts", reflectionFacts(evidence));

        return serialized;
    }

    /** Provider-only facts spare the language model unit arithmetic and UTC interpretation. */
    static JsonObject reflectionFacts(CompletedRunEvidence evidence) {
        JsonObject facts = new JsonObject();
        addNullableString(facts, "distance_text", reflectionDistanceText(evidence.metrics()));
        facts.addProperty("elapsed_duration_seconds", BigDecimal.valueOf(
                evidence.finishEpochMillis() - evidence.officialStartEpochMillis(), 3
        ));
        addNullableNumber(facts, "active_duration_seconds", evidence.activeDurationMillis() == null
                ? null : BigDecimal.valueOf(evidence.activeDurationMillis(), 3));

        ZoneId zone = ZoneId.of(evidence.startTimezoneId());
        facts.addProperty("start_local_datetime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(evidence.officialStartEpochMillis()).atZone(zone)
        ));
        facts.addProperty("finish_local_datetime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(evidence.finishEpochMillis()).atZone(zone)
        ));
        WeatherContext weather = evidence.weatherContext();
        if (weather != null) {
            facts.addProperty("weather_temperature_fahrenheit", fahrenheit(weather.temperatureCelsius()));
            facts.addProperty("weather_apparent_temperature_fahrenheit", fahrenheit(weather.apparentTemperatureCelsius()));
            facts.addProperty("weather_valid_from_local_datetime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochMilli(weather.validFromEpochMillis()).atZone(zone)));
            facts.addProperty("weather_valid_to_local_datetime", DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                    Instant.ofEpochMilli(weather.validToEpochMillis()).atZone(zone)));
        }
        return facts;
    }

    private static BigDecimal fahrenheit(double celsius) {
        return BigDecimal.valueOf(celsius).multiply(new BigDecimal("1.8"))
                .add(new BigDecimal("32")).setScale(1, RoundingMode.HALF_UP);
    }

    private static String reflectionDistanceText(RunMetricsEvidence metrics) {
        if (metrics.distanceMeters() == null || metrics.displayUnit() == null) {
            return null;
        }
        BigDecimal meters = BigDecimal.valueOf(metrics.distanceMeters());
        BigDecimal metersPerUnit = switch (metrics.displayUnit()) {
            case MILES -> new BigDecimal("1609.344");
            case KILOMETERS -> new BigDecimal("1000");
        };
        String unit = switch (metrics.displayUnit()) {
            case MILES -> "miles";
            case KILOMETERS -> "kilometers";
        };
        String provenance = switch (metrics.source()) {
            case FIXTURE -> " of sample distance";
            case GPS -> metrics.coverage() == TelemetryCoverage.PARTIAL ? " recorded by partial GPS" : "";
        };
        // Match the native save receipt: tiny positive measurements never become a zero claim.
        if (meters.signum() > 0 && meters.compareTo(metersPerUnit.movePointLeft(2)) < 0) {
            return "less than 0.01 " + unit + provenance;
        }
        return meters.divide(metersPerUnit, 2, RoundingMode.HALF_UP).toPlainString() + " " + unit + provenance;
    }

    private static JsonObject candidateSchema() {
        JsonObject properties = new JsonObject();
        properties.add("spent", stringSchema(
                "Reflection conditional on the runner selecting Spent after this run."
        ));
        properties.add("feeling_good", stringSchema(
                "Reflection conditional on the runner selecting Feeling Good after this run."
        ));
        properties.add("powered_up", stringSchema(
                "Reflection conditional on the runner selecting Powered Up after this run."
        ));
        properties.add("no_selection", stringSchema(
                "Reflection for unknown post-run Energy when the runner makes no selection."
        ));

        JsonArray required = new JsonArray();
        CANDIDATE_KEYS_IN_ORDER.forEach(required::add);

        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", properties);
        schema.add("required", required);
        schema.addProperty("additionalProperties", false);
        return schema;
    }

    private static JsonObject stringSchema(String description) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "string");
        schema.addProperty("description", description);
        return schema;
    }

    private static CandidateReflections parseSuccessfulResponse(String responseBody)
            throws ReflectionProviderException {
        JsonObject candidates = parseObject(successfulOutput(responseBody), "candidate reflection output");
        if (!candidates.keySet().equals(CANDIDATE_KEYS)) {
            throw invalidResponse(
                    "Candidate output must contain exactly spent, feeling_good, powered_up, and no_selection."
            );
        }

        try {
            return new CandidateReflections(
                    requiredString(candidates, "spent", "candidate reflection output"),
                    requiredString(candidates, "feeling_good", "candidate reflection output"),
                    requiredString(candidates, "powered_up", "candidate reflection output"),
                    requiredString(candidates, "no_selection", "candidate reflection output")
            );
        } catch (IllegalArgumentException invalidCandidate) {
            throw new ReflectionProviderException(
                    FailureType.INVALID_RESPONSE,
                    "Candidate reflection output contains blank text.",
                    invalidCandidate
            );
        }
    }

    private static String successfulOutput(String responseBody) throws ReflectionProviderException {
        JsonObject response = parseObject(responseBody, "Anthropic response");
        String stopReason = requiredString(response, "stop_reason", "Anthropic response");

        if ("refusal".equals(stopReason)) {
            throw new ReflectionProviderException(
                    FailureType.REFUSAL,
                    "Anthropic refused the reflection request."
            );
        }
        if ("max_tokens".equals(stopReason)
                || "model_context_window_exceeded".equals(stopReason)) {
            throw new ReflectionProviderException(
                    FailureType.TRUNCATED,
                    "Anthropic returned a truncated reflection response."
            );
        }
        if (!"end_turn".equals(stopReason)) {
            throw invalidResponse("Anthropic returned an unexpected stop reason.");
        }

        JsonElement contentElement = response.get("content");
        if (contentElement == null || !contentElement.isJsonArray()) {
            throw invalidResponse("Anthropic response content must be an array.");
        }

        JsonArray content = contentElement.getAsJsonArray();
        if (content.size() != 1 || !content.get(0).isJsonObject()) {
            throw invalidResponse("Anthropic response must contain exactly one text block.");
        }

        JsonObject textBlock = content.get(0).getAsJsonObject();
        if (!"text".equals(requiredString(textBlock, "type", "Anthropic content block"))) {
            throw invalidResponse("Anthropic response content must be a text block.");
        }

        return requiredString(textBlock, "text", "Anthropic content block");
    }

    private static JsonObject parseObject(String rawJson, String label)
            throws ReflectionProviderException {
        if (rawJson == null) {
            throw invalidResponse(label + " is missing.");
        }

        try {
            validateStrictJson(rawJson);
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject()) {
                throw invalidResponse(label + " must be a JSON object.");
            }
            return parsed.getAsJsonObject();
        } catch (IOException | IllegalStateException | JsonParseException | NumberFormatException malformed) {
            throw new ReflectionProviderException(
                    FailureType.INVALID_RESPONSE,
                    label + " is malformed JSON.",
                    malformed
            );
        }
    }

    private static void validateStrictJson(String rawJson) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(rawJson))) {
            reader.setStrictness(Strictness.STRICT);
            readJsonValue(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedJsonException("JSON contains trailing content");
            }
        }
    }

    private static void readJsonValue(JsonReader reader) throws IOException {
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) {
                        throw new MalformedJsonException("JSON contains a duplicate field");
                    }
                    readJsonValue(reader);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    readJsonValue(reader);
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new MalformedJsonException("JSON contains an unexpected token");
        }
    }

    private static String requiredString(JsonObject object, String key, String label)
            throws ReflectionProviderException {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalidResponse(label + " requires string field " + key + ".");
        }
        return value.getAsString();
    }

    private static String loadSystemPrompt() throws ReflectionProviderException {
        try (InputStream promptStream = AnthropicReflectionProvider.class
                .getResourceAsStream(PROMPT_RESOURCE)) {
            if (promptStream == null) {
                throw new ReflectionProviderException(
                        FailureType.CONFIGURATION,
                        "The mobile reflection prompt resource is missing."
                );
            }

            String prompt = new String(promptStream.readAllBytes(), StandardCharsets.UTF_8);
            if (prompt.isBlank()) {
                throw new ReflectionProviderException(
                        FailureType.CONFIGURATION,
                        "The mobile reflection prompt resource is blank."
                );
            }
            return prompt;
        } catch (IOException readFailure) {
            throw new ReflectionProviderException(
                    FailureType.CONFIGURATION,
                    "The mobile reflection prompt resource could not be read.",
                    readFailure
            );
        }
    }

    private static boolean isSafeHeaderValue(String candidate) {
        return candidate.chars().allMatch(character -> character >= 0x21 && character <= 0x7e);
    }

    private static void addNullableNumber(JsonObject object, String key, Number value) {
        if (value == null) {
            object.add(key, null);
        } else {
            object.addProperty(key, value);
        }
    }

    private static void addNullableString(JsonObject object, String key, String value) {
        if (value == null) {
            object.add(key, null);
        } else {
            object.addProperty(key, value);
        }
    }

    private static void addNullableEnum(JsonObject object, String key, Enum<?> value) {
        if (value == null) {
            object.add(key, null);
        } else {
            object.addProperty(key, value.name());
        }
    }

    private static ReflectionProviderException invalidResponse(String message) {
        return new ReflectionProviderException(FailureType.INVALID_RESPONSE, message);
    }

    record ProviderConfiguration(
            URI endpoint,
            String apiKey,
            String model,
            Duration connectionTimeout,
            Duration requestTimeout,
            int maxOutputTokens,
            String musicEditorModel,
            String songWriterModel
    ) {

        ProviderConfiguration {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(apiKey, "apiKey");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(musicEditorModel, "musicEditorModel");
            Objects.requireNonNull(songWriterModel, "songWriterModel");
            Objects.requireNonNull(connectionTimeout, "connectionTimeout");
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
        }

        ProviderConfiguration(URI endpoint, String apiKey, String model, Duration connectionTimeout,
                              Duration requestTimeout, int maxOutputTokens) {
            this(endpoint, apiKey, model, connectionTimeout, requestTimeout, maxOutputTokens,
                    DEFAULT_MUSIC_EDITOR_MODEL, DEFAULT_SONG_WRITER_MODEL);
        }

        ProviderConfiguration(URI endpoint, String apiKey, String model, Duration connectionTimeout,
                              Duration requestTimeout, int maxOutputTokens, String musicEditorModel) {
            this(endpoint, apiKey, model, connectionTimeout, requestTimeout, maxOutputTokens,
                    musicEditorModel, DEFAULT_SONG_WRITER_MODEL);
        }

        @Override
        public String toString() {
            return "ProviderConfiguration[endpoint=" + endpoint
                    + ", model=" + model
                    + ", musicEditorModel=" + musicEditorModel
                    + ", songWriterModel=" + songWriterModel
                    + ", connectionTimeout=" + connectionTimeout
                    + ", requestTimeout=" + requestTimeout
                    + ", maxOutputTokens=" + maxOutputTokens
                    + "]";
        }
    }

    @FunctionalInterface
    interface Transport {
        TransportResponse send(TransportRequest request) throws IOException, InterruptedException;
    }

    record TransportRequest(
            URI endpoint,
            String apiKey,
            String apiVersion,
            String body,
            Duration timeout
    ) {

        @Override
        public String toString() {
            return "TransportRequest[endpoint=" + endpoint + ", timeout=" + timeout + "]";
        }
    }

    record TransportResponse(int statusCode, String body) {
    }

    private static final class JdkTransport implements Transport {

        private final HttpClient client;

        private JdkTransport(Duration connectionTimeout) {
            client = HttpClient.newBuilder()
                    .connectTimeout(connectionTimeout)
                    .build();
        }

        @Override
        public TransportResponse send(TransportRequest request)
                throws IOException, InterruptedException {
            HttpRequest httpRequest = HttpRequest.newBuilder(request.endpoint())
                    .timeout(request.timeout())
                    .header("content-type", "application/json")
                    .header("anthropic-version", request.apiVersion())
                    .header("x-api-key", request.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(
                    httpRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            return new TransportResponse(response.statusCode(), response.body());
        }
    }
}
