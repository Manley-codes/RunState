package com.runstate.reflection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Optional server-side song analysis. Does not retain lyric, quotation or entity fields. */
public final class MusixmatchSongAnalysis {
    static final int MAX_RESPONSE_BYTES = 128 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(2);
    private static final String API_ORIGIN = "https://api.musixmatch.com";
    private static final String MATCH_PATH = "/ws/1.1/matcher.track.get";
    private static final String ANALYSIS_PATH = "/ws/1.1/track.lyrics.analysis.get";
    private static final int MAX_LABELS = 8;
    private static final int MAX_MEANING_CHARACTERS = 1200;
    private final String apiKey;
    private final Transport transport;

    MusixmatchSongAnalysis(String apiKey, Transport transport) {
        this.apiKey = validatedKey(apiKey);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** Missing credentials disable enrichment; the key belongs only in the backend environment. */
    public static Optional<MusixmatchSongAnalysis> fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String key = environment.get("MUSIXMATCH_API_KEY");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new MusixmatchSongAnalysis(key, new JdkTransport()));
    }

    /** One lookup, no retry. An unavailable analysis must not prevent an ordinary reflection. */
    public Optional<Analysis> lookup(String trackTitle, String artistName) {
        if (!validIdentity(trackTitle) || !validIdentity(artistName)) {
            return Optional.empty();
        }
        try {
            JsonObject matchBody = responseBody(request(MATCH_PATH,
                    "q_track=" + encode(trackTitle) + "&q_artist=" + encode(artistName)));
            JsonObject track = object(matchBody, "track");
            String matchedTitle = identity(track, "track_name");
            String matchedArtist = identity(track, "artist_name");
            if (!normalize(trackTitle).equals(normalize(matchedTitle))
                    || !normalize(artistName).equals(normalize(matchedArtist))) {
                return Optional.empty();
            }
            String trackId = positiveInteger(track.get("track_id"));
            JsonObject analysisBody = responseBody(request(ANALYSIS_PATH, "track_id=" + trackId));
            JsonObject analysis = object(analysisBody, "analysis");
            List<String> themes = labels(analysis, "themes", "main_themes", true);
            List<String> moods = labels(analysis, "moods", "main_moods", false);
            String meaningExplanation = meaningExplanation(analysis);
            if (themes.isEmpty() && moods.isEmpty() && meaningExplanation == null) {
                return Optional.empty();
            }
            return Optional.of(new Analysis(matchedTitle, matchedArtist, trackId, themes, moods,
                    meaningExplanation));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException unavailable) {
            // Provider exceptions can contain the credential-bearing URL. Never retain or log them.
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "MusixmatchSongAnalysis[credentials=redacted]";
    }

    public record Analysis(String trackTitle, String artistName, String providerTrackId,
                           List<String> themes, List<String> moods, String meaningExplanation) {
        public Analysis {
            themes = List.copyOf(themes);
            moods = List.copyOf(moods);
            meaningExplanation = sanitizedMeaning(meaningExplanation);
        }

        public Analysis(String trackTitle, String artistName, String providerTrackId,
                        List<String> themes, List<String> moods) {
            this(trackTitle, artistName, providerTrackId, themes, moods, null);
        }
    }

    private static String meaningExplanation(JsonObject analysis) {
        JsonElement meaning = analysis.get("meaning");
        if (meaning == null || !meaning.isJsonObject()) {
            return null;
        }
        JsonElement explanation = meaning.getAsJsonObject().get("explanation");
        if (explanation == null || !explanation.isJsonPrimitive()
                || !explanation.getAsJsonPrimitive().isString()) {
            return null;
        }
        return sanitizedMeaning(explanation.getAsString());
    }

    /** Quotation removal limits forwarded text; it cannot identify unquoted lyric fragments. */
    private static String sanitizedMeaning(String raw) {
        if (raw == null || raw.length() > MAX_MEANING_CHARACTERS
                || raw.codePoints().anyMatch(c -> Character.isISOControl(c)
                        && !Character.isWhitespace(c))) {
            return null;
        }
        StringBuilder prose = new StringBuilder();
        Deque<Character> closingQuotes = new ArrayDeque<>();
        for (int index = 0; index < raw.length(); index++) {
            char value = raw.charAt(index);
            if (value == '"') {
                if (!closingQuotes.isEmpty() && closingQuotes.peek() == '"') {
                    closingQuotes.pop();
                } else {
                    closingQuotes.push('"');
                }
                prose.append(' ');
            } else if (value == '\u201c') {
                closingQuotes.push('\u201d');
                prose.append(' ');
            } else if (value == '\u201d') {
                if (closingQuotes.isEmpty() || closingQuotes.pop() != '\u201d') {
                    return null;
                }
                prose.append(' ');
            } else if (closingQuotes.isEmpty()) {
                prose.append(value);
            }
        }
        if (!closingQuotes.isEmpty()) {
            return null;
        }
        String explanation = prose.toString().replaceAll("(?U)\\s+", " ").strip();
        return explanation.isBlank() ? null : explanation;
    }

    private TransportResponse request(String path, String query) throws IOException, InterruptedException {
        URI endpoint = URI.create(API_ORIGIN + path + "?" + query + "&apikey=" + encode(apiKey));
        return transport.send(new TransportRequest(endpoint, REQUEST_TIMEOUT));
    }

    private static String validatedKey(String key) {
        if (key == null || key.isBlank() || key.length() > 512
                || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("MUSIXMATCH_API_KEY must contain a valid backend API key.");
        }
        return key;
    }

    private static boolean validIdentity(String value) {
        return value != null && !value.isBlank() && value.length() <= 200
                && value.codePoints().noneMatch(Character::isISOControl);
    }

    private static String identity(JsonObject object, String name) throws IOException {
        String value = string(object.get(name));
        if (!validIdentity(value)) {
            throw invalidResponse();
        }
        return value;
    }

    private static String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static JsonObject responseBody(TransportResponse response) throws IOException {
        if (response == null || response.statusCode() != 200 || response.body() == null
                || response.body().length > MAX_RESPONSE_BYTES) {
            throw invalidResponse();
        }
        String raw = decodeUtf8(response.body());
        try (JsonReader reader = new JsonReader(new StringReader(raw))) {
            reader.setStrictness(Strictness.STRICT);
            validateJson(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw invalidResponse();
            }
        }
        JsonElement parsed = JsonParser.parseString(raw);
        if (!parsed.isJsonObject()) {
            throw invalidResponse();
        }
        JsonObject message = object(parsed.getAsJsonObject(), "message");
        JsonObject header = object(message, "header");
        if (!"200".equals(positiveInteger(header.get("status_code")))) {
            throw invalidResponse();
        }
        return object(message, "body");
    }

    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }

    private static JsonObject object(JsonObject parent, String field) throws IOException {
        JsonElement value = parent.get(field);
        if (value == null || !value.isJsonObject()) {
            throw invalidResponse();
        }
        return value.getAsJsonObject();
    }

    private static String positiveInteger(JsonElement value) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw invalidResponse();
        }
        long number = value.getAsBigDecimal().longValueExact();
        if (number <= 0) {
            throw invalidResponse();
        }
        return Long.toString(number);
    }

    private static String string(JsonElement value) throws IOException {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw invalidResponse();
        }
        return value.getAsString();
    }

    private static List<String> labels(JsonObject analysis, String containerName, String field,
                                       boolean themeObjects) throws IOException {
        if (!analysis.has(containerName)) {
            return List.of();
        }
        JsonObject container = object(analysis, containerName);
        JsonElement values = container.get(field);
        if (values == null || !values.isJsonArray()) {
            throw invalidResponse();
        }
        JsonArray array = values.getAsJsonArray();
        if (array.size() > 64) {
            throw invalidResponse();
        }
        List<String> labels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement entry : array) {
            if (themeObjects && !entry.isJsonObject()) {
                throw invalidResponse();
            }
            String label = string(themeObjects ? entry.getAsJsonObject().get("theme") : entry).strip();
            if (!label.matches("[\\p{L}\\p{M}\\p{N}][\\p{L}\\p{M}\\p{N} '&/(),+\\-]{0,119}")) {
                throw invalidResponse();
            }
            if (labels.size() < MAX_LABELS && seen.add(normalize(label))) {
                labels.add(label);
            }
        }
        return List.copyOf(labels);
    }

    private static void validateJson(JsonReader reader, int depth) throws IOException {
        if (depth > 32) {
            throw invalidResponse();
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                Set<String> names = new HashSet<>();
                reader.beginObject();
                while (reader.hasNext()) {
                    if (!names.add(reader.nextName())) {
                        throw invalidResponse();
                    }
                    validateJson(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                while (reader.hasNext()) {
                    validateJson(reader, depth + 1);
                }
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new MalformedJsonException("Invalid song-analysis response.");
        }
    }

    private static IOException invalidResponse() {
        return new IOException("Song analysis is unavailable.");
    }

    @FunctionalInterface
    interface Transport {
        TransportResponse send(TransportRequest request) throws IOException, InterruptedException;
    }

    record TransportRequest(URI endpoint, Duration timeout) {
        @Override
        public String toString() {
            return "MusixmatchRequest[endpoint=redacted, timeout=" + timeout + "]";
        }
    }

    record TransportResponse(int statusCode, byte[] body) {
        @Override
        public String toString() {
            return "MusixmatchResponse[statusCode=" + statusCode + ", body=redacted]";
        }
    }

    static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

        @Override
        public TransportResponse send(TransportRequest request) throws IOException, InterruptedException {
            HttpRequest http = HttpRequest.newBuilder(request.endpoint()).timeout(request.timeout())
                    .header("Accept", "application/json").GET().build();
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(http,
                    info -> new LimitedBodySubscriber());
            try {
                HttpResponse<byte[]> response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
                return new TransportResponse(response.statusCode(), response.body());
            } catch (ExecutionException | TimeoutException unavailable) {
                future.cancel(true);
                // Never attach the cause: a JDK transport exception can disclose the query API key.
                throw invalidResponse();
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                throw interrupted;
            }
        }
    }

    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(1);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(invalidResponse());
                    return;
                }
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(invalidResponse());
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
