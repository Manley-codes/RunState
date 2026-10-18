package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.ReflectionContract.SongCandidate;
import com.runstate.reflection.ReflectionContract.SongSnippet;
import com.runstate.reflection.ReflectionContract.SongSnippetSelectionMethod;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Optional exact-recording timing lookup. Lyric text exists only while deriving a bounded window. */
final class LrclibSongSnippets {
    static final Duration MAX_TIMEOUT = Duration.ofSeconds(5);
    static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int MAX_LINES = 1_024;
    private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):([0-5]\\d)(?:[.:](\\d{1,3}))?]");
    private static final String USER_AGENT = "RunStatePrototype/0.1 (https://runstate-reflections.onrender.com)";
    private final Transport transport;

    LrclibSongSnippets() { this(new JdkTransport()); }
    LrclibSongSnippets(Transport transport) { this.transport = java.util.Objects.requireNonNull(transport); }

    Optional<SongSnippet> lookup(SongCandidate candidate, MusixmatchSongAnalysis.SongMaterial material,
                                 Duration remaining) {
        if (candidate.recording() == null || Thread.currentThread().isInterrupted()
                || remaining.isZero() || remaining.isNegative()) return Optional.empty();
        Duration timeout = remaining.compareTo(MAX_TIMEOUT) < 0 ? remaining : MAX_TIMEOUT;
        String query = "track_name=" + encode(candidate.musicContext().trackTitle())
                + "&artist_name=" + encode(candidate.musicContext().artistName())
                + "&duration=" + candidate.recording().durationMillis() / 1000.0;
        if (candidate.recording().albumName() != null) query += "&album_name=" + encode(candidate.recording().albumName());
        try {
            Response response = transport.send(new Request(URI.create("https://lrclib.net/api/get?" + query), timeout));
            if (response.status() != 200 || response.body().length > MAX_RESPONSE_BYTES) return Optional.empty();
            return derive(candidate, material, response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | RuntimeException unavailable) {
            // Optional source availability must not break the completed run or disclose source text.
            return Optional.empty();
        }
    }

    static Optional<SongSnippet> derive(SongCandidate candidate, MusixmatchSongAnalysis.SongMaterial material,
                                        byte[] body) {
        try {
            if (body.length > MAX_RESPONSE_BYTES || candidate.recording() == null) return Optional.empty();
            JsonObject source = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String id = source.get("id").getAsBigDecimal().toBigIntegerExact().toString();
            if (!id.matches("[1-9][0-9]{0,18}")) return Optional.empty();
            String title = text(source, "trackName"), artist = text(source, "artistName");
            if (!same(title, candidate.musicContext().trackTitle()) || !same(artist, candidate.musicContext().artistName())
                    || !same(material.trackTitle(), candidate.musicContext().trackTitle())
                    || !same(material.artistName(), candidate.musicContext().artistName())) return Optional.empty();
            String album = candidate.recording().albumName();
            if (album != null && !same(album, text(source, "albumName"))) return Optional.empty();
            double seconds = source.get("duration").getAsDouble();
            if (!Double.isFinite(seconds) || seconds < 1 || seconds > 3600) return Optional.empty();
            long sourceMillis = Math.round(seconds * 1000);
            long observedMillis = candidate.recording().durationMillis();
            if (Math.abs(sourceMillis - observedMillis) > 2_000
                    || !source.get("instrumental").isJsonPrimitive()
                    || !source.getAsJsonPrimitive("instrumental").isBoolean()
                    || source.get("instrumental").getAsBoolean()) return Optional.empty();
            List<Line> lines = timedLines(text(source, "syncedLyrics"), Math.min(sourceMillis, observedMillis));
            if (lines.size() < 3) return Optional.empty();
            var counts = new HashMap<String, Integer>();
            lines.stream().filter(line -> !line.words().isEmpty()).forEach(line -> counts.merge(line.words(), 1, Integer::sum));
            String titleWords = words(candidate.musicContext().trackTitle());
            List<String> supporting = material.themes().stream().flatMap(theme -> theme.quotes().stream())
                    .map(LrclibSongSnippets::words).filter(value -> value.split(" ").length >= 3).toList();
            Window best = null;
            for (int index = 0; index < lines.size() - 1; index++) {
                Line first = lines.get(index);
                if (first.words().isEmpty()) continue;
                int repeated = counts.getOrDefault(first.words(), 0);
                boolean titleHook = titleWords.length() >= 4 && containsWords(first.words(), titleWords)
                        && repeated >= 2;
                boolean repeatedHook = repeated >= 3 && first.words().split(" ").length >= 3;
                if (!titleHook && !repeatedHook) continue;
                // End only at an actual subsequent line timestamp. These are approximate line boundaries,
                // never word-level proof that the singer has completed a sentence.
                for (int next = index + 1; next <= Math.min(index + 3, lines.size() - 1); next++) {
                    long duration = lines.get(next).millis() - first.millis();
                    if (duration > 10_000) break;
                    if (lines.get(next).millis() > observedMillis - 2_000) break;
                    if (duration < 2_000) continue;
                    String phrase = String.join(" ", lines.subList(index, next).stream().map(Line::words).toList());
                    boolean supported = supporting.stream().anyMatch(quote -> containsWords(phrase, quote)
                            || (phrase.split(" ").length >= 3 && containsWords(quote, phrase)));
                    int score = (titleHook ? 100 : 0) + Math.min(repeated, 6) * 10
                            + (supported ? 30 : 0) + (lines.get(next).words().isEmpty() ? 8 : 0);
                    Window candidateWindow = new Window(first.millis(), duration, score,
                            titleHook ? SongSnippetSelectionMethod.TITLE_HOOK : SongSnippetSelectionMethod.REPEATED_HOOK);
                    if (best == null || candidateWindow.score() > best.score()) best = candidateWindow;
                    // Prefer the shortest complete line-window unless the following window has actual stronger evidence.
                    if (lines.get(next).words().isEmpty()) break;
                }
            }
            if (best == null) return Optional.empty();
            return Optional.of(new SongSnippet("lrclib_v1_" + id + "_" + best.start() + "_"
                    + (best.start() + best.duration()), "LRCLIB", id, candidate.musicContext().trackTitle(),
                    candidate.musicContext().artistName(), album, observedMillis, best.start(), best.duration(), best.method(), 1));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    private record Line(long millis, String words) {
        @Override public String toString() { return "TimedLine[content=redacted]"; }
    }
    private record Window(long start, long duration, int score, SongSnippetSelectionMethod method) { }

    private static List<Line> timedLines(String lrc, long trackMillis) {
        if (lrc == null || lrc.length() > MAX_RESPONSE_BYTES) return List.of();
        List<Line> lines = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            if (raw.toLowerCase(Locale.ROOT).startsWith("[offset:")) return List.of();
            Matcher matcher = STAMP.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find() && matcher.start() == end) {
                String fraction = matcher.group(3);
                long millis = Long.parseLong(matcher.group(1)) * 60_000 + Long.parseLong(matcher.group(2)) * 1000
                        + (fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3)));
                if (millis > trackMillis) return List.of();
                times.add(millis); end = matcher.end();
            }
            if (times.isEmpty()) continue;
            String phrase = words(raw.substring(end));
            for (long millis : times) {
                lines.add(new Line(millis, phrase));
                if (lines.size() > MAX_LINES) return List.of();
            }
        }
        lines.sort(Comparator.comparingLong(Line::millis));
        for (int index = 1; index < lines.size(); index++) {
            // Ambiguous simultaneous lines are not reliable playback boundaries.
            if (lines.get(index).millis() <= lines.get(index - 1).millis()) return List.of();
        }
        return lines;
    }

    private static String text(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive() || !object.getAsJsonPrimitive(key).isString()) return null;
        return object.get(key).getAsString();
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static boolean same(String first, String second) {
        return first != null && second != null && identity(first).equals(identity(second));
    }
    private static String identity(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replace('\u2019', '\'').strip()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static String words(String text) {
        return identity(text).replaceAll("[^\\p{L}\\p{N}]+", " ").strip().replaceAll("\\s+", " ");
    }
    private static boolean containsWords(String whole, String part) {
        return !part.isEmpty() && (" " + whole + " ").contains(" " + part + " ");
    }

    @FunctionalInterface interface Transport { Response send(Request request) throws IOException, InterruptedException; }
    record Request(URI endpoint, Duration timeout) {
        @Override public String toString() { return "SongTimingRequest[identity=redacted]"; }
    }
    record Response(int status, byte[] body) {
        @Override public String toString() { return "SongTimingResponse[status=" + status + ", body=redacted]"; }
    }

    static final class JdkTransport implements Transport {
        private static final Semaphore SOURCE_SLOT = new Semaphore(1);
        private static volatile long nextAllowedNanos;
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(MAX_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build();

        @Override public Response send(Request request) throws IOException, InterruptedException {
            long started = System.nanoTime();
            if (!SOURCE_SLOT.tryAcquire()) throw new IOException("Song timing source is busy.");
            try {
                long wait = nextAllowedNanos == 0 ? 0 : nextAllowedNanos - started;
                if (wait >= request.timeout().toNanos()) throw new IOException("Song timing source is cooling down.");
                if (wait > 0) TimeUnit.NANOSECONDS.sleep(wait);
                Duration timeout = request.timeout().minusNanos(System.nanoTime() - started);
                if (timeout.isNegative() || timeout.isZero()) throw new IOException("Song timing request expired.");
                nextAllowedNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350);
                HttpRequest http = HttpRequest.newBuilder(request.endpoint()).timeout(timeout)
                        .header("Accept", "application/json").header("User-Agent", USER_AGENT).GET().build();
                CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(http, info -> new LimitedBodySubscriber());
                try {
                    HttpResponse<byte[]> response = future.get(timeout.toNanos(), TimeUnit.NANOSECONDS);
                    if (response.statusCode() == 429 || response.statusCode() == 503) {
                        long delay = retryDelayMillis(response.headers().firstValue("Retry-After").orElse("1"));
                        nextAllowedNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay);
                    }
                    return new Response(response.statusCode(), response.body());
                } catch (ExecutionException | TimeoutException unavailable) {
                    future.cancel(true); throw new IOException("Song timing source unavailable.");
                } catch (InterruptedException interrupted) {
                    future.cancel(true); throw interrupted;
                }
            } finally { SOURCE_SLOT.release(); }
        }

        static long retryDelayMillis(String value) {
            try { return Math.max(1_000, TimeUnit.SECONDS.toMillis(Long.parseLong(value))); }
            catch (RuntimeException notSeconds) {
                try { return Math.max(1_000, ZonedDateTime.parse(value,
                        DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
                catch (RuntimeException invalid) { return 1_000; }
            }
        }
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody() { return body; }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; subscription.request(1); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - bytes.size()) {
                    subscription.cancel(); body.completeExceptionally(new IOException("Song timing response too large.")); return;
                }
                byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable ignored) { body.completeExceptionally(new IOException("Song timing response failed.")); }
        @Override public void onComplete() { body.complete(bytes.toByteArray()); }
    }
}
