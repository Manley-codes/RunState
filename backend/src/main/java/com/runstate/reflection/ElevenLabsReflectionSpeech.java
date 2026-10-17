package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.runstate.reflection.ReflectionProvider.FailureType;
import com.runstate.reflection.ReflectionProvider.ReflectionProviderException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Optional reflection-only speech. Keeps provider credentials and generated audio in memory. */
public final class ElevenLabsReflectionSpeech implements ReflectionHttpServer.SpeechProvider {
    static final URI ENDPOINT = URI.create("https://api.elevenlabs.io/v1/text-to-speech/"
            + "DocoybWhaSQMUIs7gLNe?output_format=mp3_44100_128");
    static final String MODEL_ID = "eleven_multilingual_v2";
    static final int MAX_TEXT_LENGTH = 4_000;
    static final int MAX_AUDIO_BYTES = 2 * 1024 * 1024;
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(3);
    private final String apiKey;
    private final Transport transport;

    ElevenLabsReflectionSpeech(String apiKey, Transport transport) {
        this.apiKey = validatedKey(apiKey);
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /** An absent key leaves the existing phone voice available without contacting this provider. */
    public static Optional<ElevenLabsReflectionSpeech> fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String key = environment.get("ELEVENLABS_API_KEY");
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new ElevenLabsReflectionSpeech(key, new JdkTransport()));
    }

    /** One bounded request, with no retries, text rewriting, local files, or retained audio. */
    @Override
    public byte[] synthesize(String text) throws ReflectionProviderException {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            throw failure(FailureType.INVALID_RESPONSE, "Reflection speech text is invalid.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("text", text);
        body.addProperty("model_id", MODEL_ID);
        try {
            TransportResponse response = transport.send(new TransportRequest(
                    ENDPOINT, apiKey, body.toString(), REQUEST_TIMEOUT));
            if (response == null) {
                throw failure(FailureType.INVALID_RESPONSE, "Reflection speech is unavailable.");
            }
            if (response.statusCode() != 200) {
                throw failure(FailureType.PROVIDER_ERROR, "Reflection speech provider rejected the request.");
            }
            if (!isMpegAudio(response.contentType()) || response.body() == null
                    || response.body().length == 0 || response.body().length > MAX_AUDIO_BYTES) {
                throw failure(FailureType.INVALID_RESPONSE, "Reflection speech response is invalid.");
            }
            return response.body().clone();
        } catch (HttpTimeoutException timeout) {
            throw failure(FailureType.TIMEOUT, "Reflection speech timed out.");
        } catch (AudioLimitException tooLarge) {
            throw failure(FailureType.INVALID_RESPONSE, "Reflection speech response is too large.");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw failure(FailureType.TRANSPORT, "Reflection speech was interrupted.");
        } catch (IOException | RuntimeException unavailable) {
            // Exceptions can contain URLs, headers or text. Deliberately discard their causes.
            throw failure(FailureType.TRANSPORT, "Reflection speech connection failed.");
        }
    }

    @Override
    public String toString() {
        return "ElevenLabsReflectionSpeech[credentials=redacted]";
    }

    private static String validatedKey(String key) {
        if (key == null || key.isBlank() || key.length() > 512
                || key.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("ELEVENLABS_API_KEY must contain a valid backend API key.");
        }
        return key;
    }

    private static boolean isMpegAudio(String contentType) {
        return contentType != null
                && contentType.split(";", 2)[0].strip().equalsIgnoreCase("audio/mpeg");
    }

    private static ReflectionProviderException failure(FailureType type, String message) {
        return new ReflectionProviderException(type, message);
    }

    @FunctionalInterface
    interface Transport {
        TransportResponse send(TransportRequest request) throws IOException, InterruptedException;
    }

    record TransportRequest(URI endpoint, String apiKey, String body, Duration timeout) {
        @Override
        public String toString() {
            return "ElevenLabsSpeechRequest[endpoint=redacted, credentials=redacted, body=redacted, timeout="
                    + timeout + "]";
        }
    }

    record TransportResponse(int statusCode, String contentType, byte[] body) {
        @Override
        public String toString() {
            return "ElevenLabsSpeechResponse[statusCode=" + statusCode + ", body=redacted]";
        }
    }

    static final class JdkTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build();

        @Override
        public TransportResponse send(TransportRequest request) throws IOException, InterruptedException {
            HttpRequest http = HttpRequest.newBuilder(request.endpoint()).timeout(request.timeout())
                    .header("xi-api-key", request.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Accept", "audio/mpeg")
                    .POST(HttpRequest.BodyPublishers.ofString(request.body(), StandardCharsets.UTF_8))
                    .build();
            CompletableFuture<HttpResponse<byte[]>> future = client.sendAsync(http,
                    info -> new LimitedBodySubscriber());
            try {
                // This deadline includes the entire body, rather than just response headers.
                HttpResponse<byte[]> response = future.get(request.timeout().toMillis(), TimeUnit.MILLISECONDS);
                return new TransportResponse(response.statusCode(),
                        response.headers().firstValue("Content-Type").orElse(null), response.body());
            } catch (ExecutionException unavailable) {
                future.cancel(true);
                if (unavailable.getCause() instanceof HttpTimeoutException) {
                    throw new HttpTimeoutException("Reflection speech timed out.");
                }
                if (unavailable.getCause() instanceof AudioLimitException) {
                    throw new AudioLimitException();
                }
                throw new IOException("Reflection speech connection failed.");
            } catch (TimeoutException timeout) {
                future.cancel(true);
                throw new HttpTimeoutException("Reflection speech timed out.");
            } catch (InterruptedException interrupted) {
                future.cancel(true);
                throw interrupted;
            }
        }
    }

    static final class AudioLimitException extends IOException {
        AudioLimitException() {
            super("Reflection speech response is too large.");
        }
    }

    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
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
                if (buffer.remaining() > MAX_AUDIO_BYTES - bytes.size()) {
                    subscription.cancel();
                    body.completeExceptionally(new AudioLimitException());
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
            body.completeExceptionally(new IOException("Reflection speech connection failed."));
        }

        @Override
        public void onComplete() {
            body.complete(bytes.toByteArray());
        }
    }
}
