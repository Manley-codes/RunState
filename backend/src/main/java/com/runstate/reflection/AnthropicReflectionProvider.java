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
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Anthropic Messages API adapter for the versioned mobile reflection prompt. */
public final class AnthropicReflectionProvider implements ReflectionProvider {

    static final String DEFAULT_MODEL = "claude-haiku-4-5-20251001";
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

    /** Builds the production adapter from backend-only environment configuration. */
    public AnthropicReflectionProvider() throws ReflectionProviderException {
        this(configurationFrom(System.getenv()));
    }

    private AnthropicReflectionProvider(ProviderConfiguration configuration)
            throws ReflectionProviderException {
        this(
                configuration,
                new JdkTransport(configuration.connectionTimeout())
        );
    }

    AnthropicReflectionProvider(ProviderConfiguration configuration, Transport transport)
            throws ReflectionProviderException {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.systemPrompt = loadSystemPrompt();
    }

    @Override
    public CandidateReflections generate(CompletedRunEvidence evidence)
            throws ReflectionProviderException {
        Objects.requireNonNull(evidence, "evidence");

        TransportRequest request = new TransportRequest(
                configuration.endpoint(),
                configuration.apiKey(),
                API_VERSION,
                buildRequestBody(evidence),
                configuration.requestTimeout()
        );

        TransportResponse response;
        try {
            response = transport.send(request);
        } catch (HttpTimeoutException timeout) {
            throw new ReflectionProviderException(
                    FailureType.TIMEOUT,
                    "The Anthropic request timed out.",
                    timeout
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

        return parseSuccessfulResponse(response.body());
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

        return new ProviderConfiguration(
                MESSAGES_ENDPOINT,
                apiKey,
                model,
                DEFAULT_CONNECTION_TIMEOUT,
                DEFAULT_REQUEST_TIMEOUT,
                DEFAULT_MAX_OUTPUT_TOKENS
        );
    }

    private String buildRequestBody(CompletedRunEvidence evidence) {
        JsonObject request = new JsonObject();
        request.addProperty("model", configuration.model());
        request.addProperty("max_tokens", configuration.maxOutputTokens());
        request.addProperty("system", systemPrompt);

        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", serializeEvidence(evidence).toString());
        JsonArray messages = new JsonArray();
        messages.add(message);
        request.add("messages", messages);

        JsonObject format = new JsonObject();
        format.addProperty("type", "json_schema");
        format.add("schema", candidateSchema());
        JsonObject outputConfiguration = new JsonObject();
        outputConfiguration.add("format", format);
        request.add("output_config", outputConfiguration);

        return request.toString();
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
        addNullableString(serializedMusic, "track_title", music.trackTitle());
        addNullableString(serializedMusic, "artist_name", music.artistName());
        addNullableString(serializedMusic, "artist_description", music.artistDescription());
        serialized.add("music_context", serializedMusic);

        return serialized;
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

        String candidateJson = requiredString(textBlock, "text", "Anthropic content block");
        JsonObject candidates = parseObject(candidateJson, "candidate reflection output");
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
            int maxOutputTokens
    ) {

        ProviderConfiguration {
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(apiKey, "apiKey");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(connectionTimeout, "connectionTimeout");
            Objects.requireNonNull(requestTimeout, "requestTimeout");
            if (maxOutputTokens <= 0) {
                throw new IllegalArgumentException("maxOutputTokens must be positive");
            }
        }

        @Override
        public String toString() {
            return "ProviderConfiguration[endpoint=" + endpoint
                    + ", model=" + model
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
