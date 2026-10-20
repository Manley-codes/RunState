package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runstate.reflection.ElevenLabsReflectionSpeech.LimitedBodySubscriber;
import com.runstate.reflection.ElevenLabsReflectionSpeech.Transport;
import com.runstate.reflection.ElevenLabsReflectionSpeech.TransportRequest;
import com.runstate.reflection.ElevenLabsReflectionSpeech.TransportResponse;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElevenLabsReflectionSpeechTest {
    private static final String DUMMY_KEY = "not-a-real-elevenlabs-key";
    private static final byte[] AUDIO = new byte[]{'I', 'D', '3', 4, 0, 0};

    @Test
    void sendsExactTextAndOnlyTheApprovedModelToTheFixedDesignedVoice() throws Exception {
        String text = "  Exact reflection — ‘good’.\nLine two.\tStill exact.  ";
        AtomicReference<TransportRequest> captured = new AtomicReference<>();
        ElevenLabsReflectionSpeech speech = provider(request -> {
            captured.set(request);
            return successful();
        });

        byte[] result = speech.synthesize(text);
        TransportRequest request = captured.get();
        JsonObject body = JsonParser.parseString(request.body()).getAsJsonObject();

        assertAll(
                () -> assertArrayEquals(AUDIO, result),
                () -> assertEquals(URI.create("https://api.elevenlabs.io/v1/text-to-speech/"
                        + "DocoybWhaSQMUIs7gLNe?output_format=mp3_44100_128"), request.endpoint()),
                () -> assertEquals(DUMMY_KEY, request.apiKey()),
                () -> assertEquals(Duration.ofSeconds(20), request.timeout()),
                () -> assertEquals(Set.of("text", "model_id"), body.keySet()),
                () -> assertEquals(text, body.get("text").getAsString()),
                () -> assertEquals("eleven_multilingual_v2", body.get("model_id").getAsString()),
                () -> assertFalse(request.toString().contains(DUMMY_KEY)),
                () -> assertFalse(request.toString().contains(text)),
                () -> assertFalse(speech.toString().contains(DUMMY_KEY)),
                () -> assertFalse(new TransportResponse(200, "audio/mpeg", AUDIO).toString().contains("ID3"))
        );
        result[0] = 0;
        assertEquals('I', AUDIO[0], "Returned data must not expose the transport's backing array.");
    }

    @Test
    void runVoiceUsesItsOwnChosenVoiceWithTheSameModelAndAShorterLimit() throws Exception {
        AtomicReference<TransportRequest> captured = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        ElevenLabsReflectionSpeech runVoice = ElevenLabsReflectionSpeech.runVoice(DUMMY_KEY, request -> {
            calls.incrementAndGet();
            captured.set(request);
            return successful();
        });

        assertArrayEquals(AUDIO, runVoice.synthesize("Starting in ten."));
        JsonObject body = JsonParser.parseString(captured.get().body()).getAsJsonObject();
        assertAll(
                () -> assertEquals(URI.create("https://api.elevenlabs.io/v1/text-to-speech/"
                        + "HEtSzb2EMPNWlQY8Ofqf?output_format=mp3_44100_128"), captured.get().endpoint()),
                () -> assertEquals(Set.of("text", "model_id", "voice_settings"), body.keySet()),
                () -> assertEquals(Set.of("speed"), body.getAsJsonObject("voice_settings").keySet()),
                () -> assertEquals(0.9, body.getAsJsonObject("voice_settings").get("speed").getAsDouble()),
                () -> assertEquals("Starting in ten.", body.get("text").getAsString()),
                () -> assertEquals("eleven_multilingual_v2", body.get("model_id").getAsString()),
                () -> assertEquals(Duration.ofSeconds(20), captured.get().timeout()),
                // The reflection voice keeps its own fixed voice.
                () -> assertEquals(URI.create("https://api.elevenlabs.io/v1/text-to-speech/"
                        + "DocoybWhaSQMUIs7gLNe?output_format=mp3_44100_128"), ElevenLabsReflectionSpeech.ENDPOINT),
                () -> assertFalse(runVoice.toString().contains(DUMMY_KEY))
        );
        assertArrayEquals(AUDIO, runVoice.synthesize("x".repeat(500)));
        ReflectionProviderException tooLong = assertThrows(ReflectionProviderException.class,
                () -> runVoice.synthesize("x".repeat(501)));
        assertEquals(FailureType.INVALID_RESPONSE, tooLong.failureType());
        assertEquals(2, calls.get());
        assertTrue(ElevenLabsReflectionSpeech.runVoiceFromEnvironment(Map.of()).isEmpty());
        assertTrue(ElevenLabsReflectionSpeech.runVoiceFromEnvironment(Map.of("ELEVENLABS_API_KEY", " \t")).isEmpty());
        assertTrue(ElevenLabsReflectionSpeech.runVoiceFromEnvironment(
                Map.of("ELEVENLABS_API_KEY", DUMMY_KEY)).isPresent());
    }

    @Test
    void missingOrBlankKeyDisablesWithoutMakingARequest() {
        assertTrue(ElevenLabsReflectionSpeech.fromEnvironment(Map.of()).isEmpty());
        assertTrue(ElevenLabsReflectionSpeech.fromEnvironment(Map.of("ELEVENLABS_API_KEY", " \t")).isEmpty());
        assertTrue(ElevenLabsReflectionSpeech.fromEnvironment(Map.of("ELEVENLABS_API_KEY", DUMMY_KEY)).isPresent());
    }

    @Test
    void rejectsMalformedKeyWithoutEchoingPrivateContents() {
        for (String key : List.of("private\r\nkey", "private key", "private-" + "x".repeat(512))) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ElevenLabsReflectionSpeech.fromEnvironment(Map.of("ELEVENLABS_API_KEY", key)));
            assertFalse(failure.toString().contains("private"));
            assertNull(failure.getCause());
        }
    }

    @Test
    void invalidTextNeverMakesAnExternalRequest() {
        AtomicInteger calls = new AtomicInteger();
        ElevenLabsReflectionSpeech speech = provider(request -> {
            calls.incrementAndGet();
            return successful();
        });
        for (String text : Arrays.asList(null, "", " \t\n", "private-" + "x".repeat(4_000))) {
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> speech.synthesize(text));
            assertEquals(FailureType.INVALID_RESPONSE, failure.failureType());
            assertFalse(failure.toString().contains("private"));
            assertNull(failure.getCause());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void acceptsTheExactTextLengthLimitWithoutTruncation() throws Exception {
        String text = "x".repeat(4_000);
        AtomicReference<String> actual = new AtomicReference<>();
        provider(request -> {
            actual.set(JsonParser.parseString(request.body()).getAsJsonObject().get("text").getAsString());
            return successful();
        }).synthesize(text);
        assertEquals(text, actual.get());
    }

    @Test
    void acceptsMpegContentTypeCaseAndParameters() throws Exception {
        for (String type : List.of("audio/mpeg", "Audio/MPEG", "audio/mpeg; charset=binary")) {
            assertArrayEquals(AUDIO,
                    provider(request -> new TransportResponse(200, type, AUDIO)).synthesize("Exact reflection."));
        }
    }

    @Test
    void rejectsMissingOrWrongAudioContentType() {
        for (String type : Arrays.asList(null, "", "application/json", "text/html", "audio/mpeg-other", "audio/wav")) {
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> provider(request -> new TransportResponse(200, type, AUDIO)).synthesize("Exact reflection."));
            assertEquals(FailureType.INVALID_RESPONSE, failure.failureType());
            assertNull(failure.getCause());
        }
    }

    @Test
    void rejectsNullEmptyOrOversizedAudioAndAllowsTheExactLimit() throws Exception {
        for (byte[] body : Arrays.asList(null, new byte[0], new byte[ElevenLabsReflectionSpeech.MAX_AUDIO_BYTES + 1])) {
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> provider(request -> new TransportResponse(200, "audio/mpeg", body))
                            .synthesize("Exact reflection."));
            assertEquals(FailureType.INVALID_RESPONSE, failure.failureType());
        }
        assertEquals(ElevenLabsReflectionSpeech.MAX_AUDIO_BYTES,
                provider(request -> new TransportResponse(200, "audio/mpeg",
                        new byte[ElevenLabsReflectionSpeech.MAX_AUDIO_BYTES])).synthesize("Exact reflection.").length);
        assertEquals(FailureType.INVALID_RESPONSE, assertThrows(ReflectionProviderException.class,
                () -> provider(request -> null).synthesize("Exact reflection.")).failureType());
    }

    @Test
    void failsProviderStatusesAndRedirectsWithoutRetryingOrLeakingTheBody() {
        for (int status : List.of(201, 301, 302, 307, 401, 403, 422, 429, 500, 503)) {
            AtomicInteger calls = new AtomicInteger();
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> provider(request -> {
                        calls.incrementAndGet();
                        return new TransportResponse(status, "application/json",
                                "private-provider-diagnostic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }).synthesize("Exact private reflection."));
            assertEquals(FailureType.PROVIDER_ERROR, failure.failureType());
            assertEquals(1, calls.get());
            assertSafeFailure(failure);
        }
    }

    @Test
    void containsTimeoutTransportAndUnexpectedFailuresWithoutOriginalCauses() {
        for (Exception exception : List.of(new HttpTimeoutException("private-URL"),
                new ConnectException("private-" + DUMMY_KEY), new IOException("private-URL"),
                new IllegalStateException("private reflection"), new ElevenLabsReflectionSpeech.AudioLimitException())) {
            AtomicInteger calls = new AtomicInteger();
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> provider(request -> {
                        calls.incrementAndGet();
                        if (exception instanceof IOException io) {
                            throw io;
                        }
                        throw (RuntimeException) exception;
                    }).synthesize("Exact private reflection."));
            FailureType expected = exception instanceof HttpTimeoutException ? FailureType.TIMEOUT
                    : exception instanceof ElevenLabsReflectionSpeech.AudioLimitException
                    ? FailureType.INVALID_RESPONSE : FailureType.TRANSPORT;
            assertEquals(expected, failure.failureType());
            assertEquals(1, calls.get());
            assertSafeFailure(failure);
        }
    }

    @Test
    void preservesInterruptionAndDoesNotRetry() {
        AtomicInteger calls = new AtomicInteger();
        try {
            ReflectionProviderException failure = assertThrows(ReflectionProviderException.class,
                    () -> provider(request -> {
                        calls.incrementAndGet();
                        throw new InterruptedException("private diagnostic");
                    }).synthesize("Exact private reflection."));
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(1, calls.get());
            assertEquals(FailureType.TRANSPORT, failure.failureType());
            assertSafeFailure(failure);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void boundedSubscriberCancelsBeforeAccumulatingOversizedBody() {
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber();
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[ElevenLabsReflectionSpeech.MAX_AUDIO_BYTES])));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1})));
        CompletionException failure = assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join());
        assertTrue(subscription.cancelled);
        assertTrue(failure.getCause() instanceof ElevenLabsReflectionSpeech.AudioLimitException);
        assertNull(failure.getCause().getCause());
    }

    @Test
    void boundedSubscriberPreservesBytesAcrossBuffers() {
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber();
        RecordingSubscription subscription = new RecordingSubscription();
        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{'I', 'D'}), ByteBuffer.wrap(new byte[]{'3', 4})));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{0, 0})));
        subscriber.onComplete();
        assertArrayEquals(AUDIO, subscriber.getBody().toCompletableFuture().join());
        assertFalse(subscription.cancelled);
        assertEquals(3, subscription.requests);
    }

    @Test
    void boundedSubscriberDoesNotRetainPrivateTransportFailure() {
        LimitedBodySubscriber subscriber = new LimitedBodySubscriber();
        subscriber.onSubscribe(new RecordingSubscription());
        subscriber.onError(new IOException("private-" + DUMMY_KEY));
        CompletionException failure = assertThrows(CompletionException.class,
                () -> subscriber.getBody().toCompletableFuture().join());
        assertFalse(failure.getCause().toString().contains("private"));
        assertNull(failure.getCause().getCause());
    }

    private static ElevenLabsReflectionSpeech provider(Transport transport) {
        return new ElevenLabsReflectionSpeech(DUMMY_KEY, transport);
    }

    private static TransportResponse successful() {
        return new TransportResponse(200, "audio/mpeg", AUDIO);
    }

    private static void assertSafeFailure(ReflectionProviderException failure) {
        assertFalse(failure.toString().contains("private"));
        assertFalse(failure.toString().contains(DUMMY_KEY));
        assertNull(failure.getCause());
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private boolean cancelled;
        private int requests;

        @Override
        public void request(long count) {
            requests += (int) count;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
