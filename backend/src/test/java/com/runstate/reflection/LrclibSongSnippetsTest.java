package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.runstate.reflection.ReflectionContract.MusicContext;
import com.runstate.reflection.ReflectionContract.MusicProvenance;
import com.runstate.reflection.ReflectionContract.MusicState;
import com.runstate.reflection.ReflectionContract.SongCandidate;
import com.runstate.reflection.ReflectionContract.SongRecording;
import com.runstate.reflection.ReflectionContract.SongSnippetSelectionMethod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LrclibSongSnippetsTest {
    @Test void nonRepeatingTimedVerseWorksWithoutAnalysisOnlyForNewOptedInClients() {
        String verse = "[00:01]A fresh beginning here\n[00:05]Another unique phrase\n[00:10]The final different line\n[00:14]";
        assertTrue(LrclibSongSnippets.derive(candidate(), null, bytes(response(verse))).isEmpty());
        var window = LrclibSongSnippets.derive(candidate(), null, bytes(response(verse)), true).orElseThrow();
        assertEquals(SongSnippetSelectionMethod.TIMED_LINE_WINDOW, window.selectionMethod());
        assertEquals(2, window.version()); assertEquals(5_000, window.startPositionMillis());
        assertEquals(9_000, window.durationMillis());
        assertEquals("lrclib_v2_42_5000_14000", window.cueId());
    }

    @Test void absentAlbumDoesNotDiscardAnOtherwiseMatchedRecordingAndHooksStillWin() {
        var song = new SongCandidate("2", candidate().musicContext(), null, new SongRecording(null, 180_000));
        var window = LrclibSongSnippets.derive(song, null, bytes(response(LRC)), true).orElseThrow();
        assertNull(window.albumName()); assertTrue(window.matches(song));
        assertEquals(SongSnippetSelectionMethod.TITLE_HOOK, window.selectionMethod());
        assertEquals(10_250, window.startPositionMillis());
        var wrong = response(LRC); wrong.addProperty("duration", 220);
        assertTrue(LrclibSongSnippets.derive(song, null, bytes(wrong), true).isEmpty());
    }
    private static final String LRC = "[00:00.00]An unrelated opening line\n"
            + "[00:10.25]Bright horizon is ahead\n[00:14.00]Keep this moment close\n[00:17.00]\n"
            + "[00:30.00]Bright horizon is ahead\n[00:34.00]Keep this moment close\n[00:37.00]\n"
            + "[00:50.00]Bright horizon is ahead\n[00:54.00]Keep this moment close\n[00:57.00]\n";

    private static SongCandidate candidate() {
        return new SongCandidate("2", new MusicContext(MusicState.MUSIC, "Bright Horizon", "Fixture Artist",
                null, MusicProvenance.MEDIA_SESSION_PARTIAL), null, new SongRecording("Fixture Album", 180_000));
    }

    private static MusixmatchSongAnalysis.SongMaterial material() {
        return new MusixmatchSongAnalysis.SongMaterial("Bright Horizon", "Fixture Artist", "1", List.of(),
                List.of(new MusixmatchSongAnalysis.ThemeMaterial("possibility", List.of("Bright horizon is ahead"))),
                List.of("hopeful"), null, List.of(), null);
    }

    private static JsonObject response(String lrc) {
        JsonObject body = new JsonObject();
        body.addProperty("id", 42); body.addProperty("trackName", "Bright Horizon");
        body.addProperty("artistName", "Fixture Artist"); body.addProperty("albumName", "Fixture Album");
        body.addProperty("duration", 180); body.addProperty("instrumental", false);
        body.addProperty("syncedLyrics", lrc);
        body.addProperty("plainLyrics", "This raw source text must never become an API response or model input.");
        return body;
    }

    private static byte[] bytes(JsonObject body) { return body.toString().getBytes(StandardCharsets.UTF_8); }

    @Test void derivesRepeatedTitleAtRealTimestampsAndPrefersAnAvailableGap() {
        var value = LrclibSongSnippets.derive(candidate(), material(), bytes(response(LRC))).orElseThrow();
        assertEquals("lrclib_v1_42_10250_17000", value.cueId());
        assertEquals(10_250, value.startPositionMillis()); assertEquals(6_750, value.durationMillis());
        assertEquals(SongSnippetSelectionMethod.TITLE_HOOK, value.selectionMethod());
        assertEquals("Fixture Album", value.albumName()); assertEquals(180_000, value.trackDurationMillis());
        assertTrue(value.matches(candidate())); assertFalse(value.toString().contains("horizon"));
    }

    @Test void equallyStrongTitleHookGetsRoomToLandInsteadOfAlwaysChoosingShortestFragment() {
        // Synthetic wording, with the field report's 2.91-second opening window.
        String lrc = "[00:11.840]Bright horizon is ahead\n[00:14.750]Keep moving through this moment\n"
                + "[00:18.000]Another line begins here\n[00:21.000]Next part of the verse\n"
                + "[00:40.000]Bright horizon is ahead\n[00:42.910]Keep moving through this moment\n"
                + "[00:46.160]Another line begins here\n[00:49.160]Next part of the verse";
        var value = LrclibSongSnippets.derive(candidate(), material(), bytes(response(lrc)), true).orElseThrow();
        assertEquals(SongSnippetSelectionMethod.TITLE_HOOK, value.selectionMethod());
        assertEquals(11_840, value.startPositionMillis());
        assertEquals(6_160, value.durationMillis());
        assertEquals("lrclib_v2_42_11840_18000", value.cueId());
    }

    @Test void aShortSourceMarkedGapStillWinsWithoutManufacturedPadding() {
        String lrc = "[00:11.840]Bright horizon is ahead\n[00:14.750]\n[00:16.000]A new verse begins\n"
                + "[00:40.000]Bright horizon is ahead\n[00:43.000]Keep moving through this moment\n"
                + "[00:46.000]Another line begins here\n[00:49.000]Next part of the verse";
        var value = LrclibSongSnippets.derive(candidate(), material(), bytes(response(lrc)), true).orElseThrow();
        assertEquals(11_840, value.startPositionMillis());
        assertEquals(2_910, value.durationMillis());
        assertEquals("lrclib_v2_42_11840_14750", value.cueId());
    }

    @Test void anOnlyAvailableShortHookRemainsUsableInsteadOfBecomingAnOpeningFallback() {
        String lrc = "[00:11.840]Bright horizon is ahead\n[00:14.750]A long following verse\n"
                + "[00:40.000]Bright horizon is ahead\n[00:42.910]Another long following verse\n[01:10.000]";
        var value = LrclibSongSnippets.derive(candidate(), null, bytes(response(lrc)), true).orElseThrow();
        assertEquals(SongSnippetSelectionMethod.TITLE_HOOK, value.selectionMethod());
        assertEquals(11_840, value.startPositionMillis());
        assertEquals(2_910, value.durationMillis());
    }

    @Test void repeatedNonTitlePhraseIsEligibleButAnUnrepeatedVerseIsNot() {
        JsonObject repeated = response(LRC.replace("Bright horizon is ahead", "A new morning has arrived"));
        var value = LrclibSongSnippets.derive(candidate(), material(), bytes(repeated)).orElseThrow();
        assertEquals(SongSnippetSelectionMethod.REPEATED_HOOK, value.selectionMethod());
        String verse = "[00:01]A fresh beginning here\n[00:05]Another unique phrase\n[00:10]The final different line\n[00:14]";
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(response(verse))).isEmpty());
    }

    @Test void matchedMusixmatchSupportingPassageBreaksAnOtherwiseEqualHookTie() {
        String lrc = "[00:01]Earlier unrelated recurring line\n[00:04]\n[00:10]A new morning has arrived\n[00:13]\n"
                + "[00:20]Earlier unrelated recurring line\n[00:23]\n[00:30]A new morning has arrived\n[00:33]\n"
                + "[00:40]Earlier unrelated recurring line\n[00:43]\n[00:50]A new morning has arrived\n[00:53]";
        var source = new MusixmatchSongAnalysis.SongMaterial("Bright Horizon", "Fixture Artist", "1", List.of(),
                List.of(new MusixmatchSongAnalysis.ThemeMaterial("new beginnings", List.of("A new morning has arrived"))),
                List.of(), null, List.of(), null);
        var value = LrclibSongSnippets.derive(candidate(), source, bytes(response(lrc))).orElseThrow();
        assertEquals(10_000, value.startPositionMillis());
    }

    @Test void selectedWindowCarriesOnlyItsMatchedThemeLabelsWithoutSourceWords() {
        var source = new MusixmatchSongAnalysis.SongMaterial("Bright Horizon", "Fixture Artist", "1", List.of(),
                List.of(new MusixmatchSongAnalysis.ThemeMaterial("possibility",
                                List.of("Bright horizon is ahead", "Bright horizon is ahead today")),
                        new MusixmatchSongAnalysis.ThemeMaterial("distance", List.of("An unrelated opening line")),
                        new MusixmatchSongAnalysis.ThemeMaterial("care", List.of("Keep this moment close"))),
                List.of(), null, List.of(), null);
        var match = LrclibSongSnippets.deriveMatch(candidate(), source, bytes(response(LRC)), true).orElseThrow();
        assertEquals(10_250, match.snippet().startPositionMillis());
        assertEquals(6_750, match.snippet().durationMillis());
        assertEquals(List.of(new AnthropicReflectionProvider.SnippetThemeLink(0, "possibility"),
                new AnthropicReflectionProvider.SnippetThemeLink(2, "care")), match.themeLinks());
        String retained = new com.google.gson.Gson().toJson(match);
        assertFalse(retained.contains("is ahead"));
        assertFalse(retained.contains("Keep this moment"));
        assertFalse(retained.contains("unrelated"));
        assertFalse(match.toString().contains("possibility"));
        assertFalse(match.themeLinks().toString().contains("care"));
    }

    @Test void shortUnrelatedWordsDoNotCreateThemeLinksAndContextUsesOneTimingRequest() {
        var source = new MusixmatchSongAnalysis.SongMaterial("Bright Horizon", "Fixture Artist", "1", List.of(),
                List.of(new MusixmatchSongAnalysis.ThemeMaterial("coincidence", List.of("Bright horizon"))),
                List.of(), null, List.of(), null);
        AtomicInteger requests = new AtomicInteger();
        var lookup = new LrclibSongSnippets(request -> {
            requests.incrementAndGet();
            return new LrclibSongSnippets.Response(200, bytes(response(LRC)));
        });
        var match = lookup.lookupMatch(candidate(), source, Duration.ofSeconds(5), true).orElseThrow();
        assertEquals(1, requests.get());
        assertTrue(match.themeLinks().isEmpty());
        assertEquals(SongSnippetSelectionMethod.TITLE_HOOK, match.snippet().selectionMethod());
        var wrong = response(LRC); wrong.addProperty("artistName", "Another artist");
        assertTrue(LrclibSongSnippets.deriveMatch(candidate(), source, bytes(wrong), true).isEmpty());
    }

    @Test void exactRecordingChecksRejectOtherAlbumsVersionsArtistsAndDurationMismatches() {
        for (String key : List.of("trackName", "artistName", "albumName")) {
            var body = response(LRC); body.addProperty(key, body.get(key).getAsString() + " (Live)");
            assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isEmpty(), key);
        }
        var body = response(LRC); body.addProperty("duration", 182.001);
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isEmpty());
        body.addProperty("duration", 182);
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isPresent());
        body.addProperty("trackName", "  BRIGHT HORIZON ");
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isPresent());
    }

    @Test void absentInstrumentalOrAmbiguousTimedContentCannotAcknowledgeAClip() {
        for (String lrc : List.of("", "No timestamps here", "[offset:250]\n" + LRC,
                LRC + "[00:10.25]Another simultaneous line", LRC + "[03:01]Outside recording")) {
            assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(response(lrc))).isEmpty());
        }
        var body = response(LRC); body.addProperty("instrumental", true);
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isEmpty());
        body.remove("instrumental");
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(body)).isEmpty());
    }

    @Test void neverInventsAnEndForTheLastLineOrAnOverlongWindow() {
        String lrc = "[00:01]Bright horizon is ahead\n[00:20]Bright horizon is ahead\n[00:40]Bright horizon is ahead";
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), bytes(response(lrc))).isEmpty());
    }

    @Test void supportsMultipleTimeTagsAndFractionPrecisionWithoutChangingSourceTimes() {
        String lrc = "[00:10.2][00:30.200][00:50.20]Bright horizon is ahead\n[00:13][00:33][00:53]";
        var value = LrclibSongSnippets.derive(candidate(), material(), bytes(response(lrc))).orElseThrow();
        assertEquals(10_200, value.startPositionMillis()); assertEquals(2_800, value.durationMillis());
    }

    @Test void sendsOnlySelectedRecordingIdentityOnceWithFiveSecondMaximumAndNoCredential() {
        var captured = new AtomicReference<LrclibSongSnippets.Request>();
        var calls = new AtomicInteger();
        var lookup = new LrclibSongSnippets(request -> {
            captured.set(request); calls.incrementAndGet(); return new LrclibSongSnippets.Response(200, bytes(response(LRC)));
        });
        assertTrue(lookup.lookup(candidate(), material(), Duration.ofSeconds(20)).isPresent());
        assertEquals(1, calls.get()); assertEquals(Duration.ofSeconds(5), captured.get().timeout());
        assertEquals("https", captured.get().endpoint().getScheme()); assertEquals("lrclib.net", captured.get().endpoint().getHost());
        assertEquals("/api/get", captured.get().endpoint().getPath());
        assertTrue(captured.get().endpoint().getRawQuery().contains("album_name=Fixture+Album"));
        assertFalse(captured.get().toString().contains("Fixture"));
        lookup.lookup(candidate(), material(), Duration.ofMillis(400));
        assertEquals(Duration.ofMillis(400), captured.get().timeout());
    }

    @Test void failedOptionalSourceDoesNotRetryAndMissingRecordingMakesNoRequest() {
        for (int status : List.of(301, 403, 404, 429, 500, 503)) {
            AtomicInteger calls = new AtomicInteger();
            var lookup = new LrclibSongSnippets(request -> {
                calls.incrementAndGet(); return new LrclibSongSnippets.Response(status, bytes(response(LRC)));
            });
            assertTrue(lookup.lookup(candidate(), material(), Duration.ofSeconds(5)).isEmpty());
            assertEquals(1, calls.get());
            assertTrue(lookup.lookup(new SongCandidate("2", candidate().musicContext(), null), material(), Duration.ofSeconds(5)).isEmpty());
            assertEquals(1, calls.get());
        }
        var lookup = new LrclibSongSnippets(request -> { throw new IOException("Private source detail"); });
        assertTrue(lookup.lookup(candidate(), material(), Duration.ofSeconds(5)).isEmpty());
    }

    @Test void interruptionIsPreservedWithoutFurtherWork() {
        var lookup = new LrclibSongSnippets(request -> { throw new InterruptedException("private"); });
        try {
            assertTrue(lookup.lookup(candidate(), material(), Duration.ofSeconds(5)).isEmpty());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
    }

    @Test void overlargeOrInvalidContentIsUnavailableAndSubscriberCancelsAtTheLimit() {
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), new byte[LrclibSongSnippets.MAX_RESPONSE_BYTES + 1]).isEmpty());
        assertTrue(LrclibSongSnippets.derive(candidate(), material(), "<error>".getBytes(StandardCharsets.UTF_8)).isEmpty());
        var subscriber = new LrclibSongSnippets.LimitedBodySubscriber();
        AtomicBoolean cancelled = new AtomicBoolean();
        subscriber.onSubscribe(new Flow.Subscription() {
            public void request(long count) { }
            public void cancel() { cancelled.set(true); }
        });
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[LrclibSongSnippets.MAX_RESPONSE_BYTES]), ByteBuffer.wrap(new byte[1])));
        assertTrue(cancelled.get()); assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test void serviceRetryAfterIsHonoredWithoutOverflowOrTruncatingLongCooldowns() {
        assertEquals(1_000, LrclibSongSnippets.JdkTransport.retryDelayMillis("1"));
        assertEquals(7_200_000, LrclibSongSnippets.JdkTransport.retryDelayMillis("7200"));
        assertEquals(Long.MAX_VALUE, LrclibSongSnippets.JdkTransport.retryDelayMillis(Long.toString(Long.MAX_VALUE)));
        assertEquals(1_000, LrclibSongSnippets.JdkTransport.retryDelayMillis("invalid"));
        String future = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC).plusHours(3)
                .format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
        assertTrue(LrclibSongSnippets.JdkTransport.retryDelayMillis(future) > 7_200_000);
    }
}
