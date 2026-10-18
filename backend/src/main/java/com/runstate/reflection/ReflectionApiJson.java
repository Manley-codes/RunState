package com.runstate.reflection;

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
import com.runstate.reflection.ReflectionContract.DistanceUnit;
import com.runstate.reflection.ReflectionContract.MetricSource;
import com.runstate.reflection.ReflectionContract.MusicContext;
import com.runstate.reflection.ReflectionContract.MusicProvenance;
import com.runstate.reflection.ReflectionContract.MusicState;
import com.runstate.reflection.ReflectionContract.PreRunEnergy;
import com.runstate.reflection.ReflectionContract.RequestIdentifiers;
import com.runstate.reflection.ReflectionContract.RunMetricsEvidence;
import com.runstate.reflection.ReflectionContract.SongCueContext;
import com.runstate.reflection.ReflectionContract.SongCueSource;
import com.runstate.reflection.ReflectionContract.TelemetryCoverage;
import com.runstate.reflection.ReflectionContract.WeatherContext;
import com.runstate.reflection.ReflectionContract.WeatherSource;
import com.runstate.reflection.ReflectionContract.WeatherResolution;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Strict JSON translation at the local HTTP boundary. Domain validation stays in the contract. */
final class ReflectionApiJson {

    private static final int MAX_JSON_DEPTH = 32;
    private static final Set<String> REQUEST_FIELDS = Set.of("requestId", "runId", "evidence");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "officialStartEpochMillis",
            "finishEpochMillis",
            "startTimezoneId",
            "activeDurationMillis",
            "metrics",
            "preRunEnergy",
            "musicContext",
            "weatherContext"
    );
    private static final Set<String> REQUIRED_EVIDENCE_FIELDS = Set.of(
            "officialStartEpochMillis",
            "finishEpochMillis",
            "startTimezoneId"
    );
    private static final Set<String> METRIC_FIELDS = Set.of(
            "distanceMeters",
            "source",
            "coverage",
            "displayUnit"
    );
    private static final Set<String> MUSIC_FIELDS = Set.of(
            "state",
            "trackTitle",
            "artistName",
            "artistDescription",
            "provenance",
            "songCue"
    );
    private static final Set<String> SONG_CUE_FIELDS = Set.of(
            "cueId", "source", "theme", "connectionNote"
    );
    private static final Set<String> WEATHER_FIELDS = Set.of(
            "source", "resolution", "targetEpochMillis", "validFromEpochMillis", "validToEpochMillis",
            "fetchedAtEpochMillis", "temperatureCelsius", "apparentTemperatureCelsius",
            "relativeHumidityPercent", "windSpeedKmh", "weatherCode"
    );

    private ReflectionApiJson() {
    }

    static ApiRequest parseRequest(byte[] utf8Json) throws InvalidJsonException {
        String rawJson = decodeUtf8(utf8Json);
        JsonObject root = parseStrictObject(rawJson);
        requireFields(root, REQUEST_FIELDS, REQUEST_FIELDS, "request");

        RequestIdentifiers identifiers = new RequestIdentifiers(
                requiredString(root, "requestId"),
                requiredString(root, "runId")
        );
        CompletedRunEvidence evidence = parseEvidence(requiredObject(root, "evidence"));
        return new ApiRequest(identifiers, evidence);
    }

    /** Speech is an exact-text boundary, not a second prompt or a route to arbitrary voice settings. */
    static String parseSpeechRequest(byte[] utf8Json) throws InvalidJsonException {
        JsonObject root = parseStrictObject(decodeUtf8(utf8Json));
        requireFields(root, Set.of("text"), Set.of("text"), "speech request");
        String text = requiredString(root, "text");
        if (text.isBlank() || text.length() > 4_000) {
            throw new IllegalArgumentException("Speech text must contain 1 to 4000 characters.");
        }
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (Character.isHighSurrogate(character)) {
                if (++index >= text.length() || !Character.isLowSurrogate(text.charAt(index))) {
                    throw new IllegalArgumentException("Speech text must contain valid Unicode.");
                }
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Speech text must contain valid Unicode.");
            }
        }
        return text;
    }

    static String success(
            ApiRequest request,
            CandidateReflections candidates,
            ReflectionHttpServer.GenerationMode generationMode
    ) {
        return success(request, candidates, generationMode, null);
    }

    static String success(ApiRequest request, CandidateReflections candidates,
                          ReflectionHttpServer.GenerationMode generationMode, String acknowledgedCueId) {
        if (acknowledgedCueId != null && (request.evidence().musicContext().songCue() == null
                || !acknowledgedCueId.equals(request.evidence().musicContext().songCue().cueId()))) {
            throw new IllegalArgumentException("Acknowledged cue must match the request.");
        }
        JsonObject candidateJson = new JsonObject();
        candidateJson.addProperty("spent", candidates.spent());
        candidateJson.addProperty("feeling_good", candidates.feeling_good());
        candidateJson.addProperty("powered_up", candidates.powered_up());
        candidateJson.addProperty("no_selection", candidates.no_selection());

        JsonObject response = new JsonObject();
        response.addProperty("requestId", request.identifiers().requestId());
        response.addProperty("runId", request.identifiers().runId());
        response.addProperty("generationMode", generationMode.name());
        if (acknowledgedCueId != null) {
            response.addProperty("songCueId", acknowledgedCueId);
        }
        response.add("candidates", candidateJson);
        return response.toString();
    }

    static String error(String code, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("code", code);
        error.addProperty("message", message);

        JsonObject response = new JsonObject();
        response.add("error", error);
        return response.toString();
    }

    private static CompletedRunEvidence parseEvidence(JsonObject evidence) {
        requireFields(evidence, EVIDENCE_FIELDS, REQUIRED_EVIDENCE_FIELDS, "evidence");

        return new CompletedRunEvidence(
                requiredLong(evidence, "officialStartEpochMillis"),
                requiredLong(evidence, "finishEpochMillis"),
                requiredString(evidence, "startTimezoneId"),
                optionalLong(evidence, "activeDurationMillis"),
                optionalMetrics(evidence, "metrics"),
                optionalEnum(evidence, "preRunEnergy", PreRunEnergy.class),
                optionalMusic(evidence, "musicContext"),
                optionalWeather(evidence, "weatherContext")
        );
    }

    private static RunMetricsEvidence optionalMetrics(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object or null");
        }

        JsonObject metrics = value.getAsJsonObject();
        requireFields(metrics, METRIC_FIELDS, Set.of(), key);
        return new RunMetricsEvidence(
                optionalDouble(metrics, "distanceMeters"),
                optionalEnum(metrics, "source", MetricSource.class),
                optionalEnum(metrics, "coverage", TelemetryCoverage.class),
                optionalEnum(metrics, "displayUnit", DistanceUnit.class)
        );
    }

    private static MusicContext optionalMusic(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object or null");
        }

        JsonObject music = value.getAsJsonObject();
        requireFields(music, MUSIC_FIELDS, Set.of(), key);
        return new MusicContext(
                optionalEnum(music, "state", MusicState.class),
                optionalString(music, "trackTitle"),
                optionalString(music, "artistName"),
                optionalString(music, "artistDescription"),
                optionalEnum(music, "provenance", MusicProvenance.class),
                optionalSongCue(music, "songCue")
        );
    }

    private static SongCueContext optionalSongCue(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object or null");
        }
        JsonObject cue = value.getAsJsonObject();
        requireFields(cue, SONG_CUE_FIELDS, SONG_CUE_FIELDS, key);
        return new SongCueContext(
                requiredString(cue, "cueId"),
                optionalEnum(cue, "source", SongCueSource.class),
                requiredString(cue, "theme"),
                requiredString(cue, "connectionNote")
        );
    }

    private static WeatherContext optionalWeather(JsonObject parent, String key) {
        JsonElement value = parent.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object or null");
        }
        JsonObject weather = value.getAsJsonObject();
        requireFields(weather, WEATHER_FIELDS, WEATHER_FIELDS, key);
        long code = requiredLong(weather, "weatherCode");
        if (code < Integer.MIN_VALUE || code > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("weatherCode must be an integer in range");
        }
        return new WeatherContext(
                optionalEnum(weather, "source", WeatherSource.class),
                optionalEnum(weather, "resolution", WeatherResolution.class),
                requiredLong(weather, "targetEpochMillis"),
                requiredLong(weather, "validFromEpochMillis"),
                requiredLong(weather, "validToEpochMillis"),
                requiredLong(weather, "fetchedAtEpochMillis"),
                requiredDouble(weather, "temperatureCelsius"),
                requiredDouble(weather, "apparentTemperatureCelsius"),
                requiredDouble(weather, "relativeHumidityPercent"),
                requiredDouble(weather, "windSpeedKmh"),
                (int) code
        );
    }

    private static void requireFields(
            JsonObject object,
            Set<String> allowed,
            Set<String> required,
            String label
    ) {
        if (!allowed.containsAll(object.keySet())) {
            throw new IllegalArgumentException(label + " contains unsupported fields");
        }
        if (!object.keySet().containsAll(required)) {
            throw new IllegalArgumentException(label + " is missing required fields");
        }
    }

    private static JsonObject requiredObject(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(key + " must be an object");
        }
        return value.getAsJsonObject();
    }

    private static String requiredString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null
                || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        return value.getAsString();
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(key + " must be a string or null");
        }
        return value.getAsString();
    }

    private static long requiredLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        return exactLong(value, key);
    }

    private static Long optionalLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        return exactLong(value, key);
    }

    private static long exactLong(JsonElement value, String key) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be an integer");
        }
        try {
            return new BigDecimal(value.getAsString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException invalidNumber) {
            throw new IllegalArgumentException(key + " must be an integer", invalidNumber);
        }
    }

    private static Double optionalDouble(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(key + " must be a number or null");
        }
        try {
            return Double.valueOf(value.getAsString());
        } catch (NumberFormatException invalidNumber) {
            throw new IllegalArgumentException(key + " must be a number or null", invalidNumber);
        }
    }

    private static double requiredDouble(JsonObject object, String key) {
        Double value = optionalDouble(object, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " must be a number");
        }
        return value;
    }

    private static <E extends Enum<E>> E optionalEnum(
            JsonObject object,
            String key,
            Class<E> enumType
    ) {
        String value = optionalString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException unsupportedValue) {
            throw new IllegalArgumentException(key + " contains an unsupported value", unsupportedValue);
        }
    }

    private static String decodeUtf8(byte[] encoded) throws InvalidJsonException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException invalidUtf8) {
            throw new InvalidJsonException("Request body is not valid UTF-8 JSON.", invalidUtf8);
        }
    }

    private static JsonObject parseStrictObject(String rawJson) throws InvalidJsonException {
        try {
            validateStrictJson(rawJson);
            JsonElement parsed = JsonParser.parseString(rawJson);
            if (!parsed.isJsonObject()) {
                throw new InvalidJsonException("Request body must be a JSON object.");
            }
            return parsed.getAsJsonObject();
        } catch (InvalidJsonException invalidJson) {
            throw invalidJson;
        } catch (IOException | IllegalStateException | JsonParseException | NumberFormatException malformed) {
            throw new InvalidJsonException("Request body contains malformed JSON.", malformed);
        }
    }

    private static void validateStrictJson(String rawJson) throws IOException {
        try (JsonReader reader = new JsonReader(new StringReader(rawJson))) {
            reader.setStrictness(Strictness.STRICT);
            readJsonValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new MalformedJsonException("JSON contains trailing content");
            }
        }
    }

    private static void readJsonValue(JsonReader reader, int depth) throws IOException {
        if (depth > MAX_JSON_DEPTH) {
            throw new MalformedJsonException("JSON nesting is too deep");
        }

        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) {
                        throw new MalformedJsonException("JSON contains a duplicate field");
                    }
                    readJsonValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    readJsonValue(reader, depth + 1);
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new MalformedJsonException("JSON contains an unexpected token");
        }
    }

    record ApiRequest(RequestIdentifiers identifiers, CompletedRunEvidence evidence) {
    }

    static final class InvalidJsonException extends Exception {

        InvalidJsonException(String message) {
            super(message);
        }

        InvalidJsonException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
