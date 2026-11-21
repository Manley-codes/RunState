package com.runstate.reflection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real process boundaries; all provider work is simulated and all files are temporary. */
class BackendProcessRestartTest {
    @TempDir Path directory;
    private static final String HOST = "restart.runstate.example";
    private static final String TOKEN = "restart-fixture-only-ABCDEFGHIJKLMNOPQRSTUVWXYZ-0123456789";
    private static final String TEXT = "Your saved reflection — exactly as written.";
    private static final byte[] AUDIO = {'I', 'D', '3', 0, 1, (byte) 255, 2};
    private static final String RUN = JsonParser.parseString(ReflectionHttpServerTest.validRequest())
            .getAsJsonObject().get("runId").getAsString();

    @Test void completedRepliesAllowancesAndSpendingSurviveASeparateJvm() throws Exception {
        Response written, audio;
        try (Child first = start(directory, "complete")) {
            admit(first, RUN);
            written = request(first, "POST", ReflectionHttpServer.CANDIDATES_PATH,
                    ReflectionHttpServerTest.validRequest());
            audio = request(first, "POST", ReflectionHttpServer.SPEECH_PATH, speech());
            assertEquals(200, written.status(), written.text());
            assertEquals(200, audio.status(), audio.text());
            assertArrayEquals(AUDIO, audio.body());
            assertRemaining(first, 1);
        }
        List<String> paidSends = events(directory);
        assertEquals(List.of("writer", "voice"), paidSends);
        for (String file : List.of("written-reflections.sqlite3", "reflection-speech.sqlite3",
                "reflection-allowances.sqlite3", "provider-spend.sqlite3")) {
            assertTrue(Files.isRegularFile(directory.resolve("stores").resolve(file)), file);
        }
        try (Child restarted = start(directory, "complete")) {
            assertRemaining(restarted, 1);
            Response replay = request(restarted, "POST", ReflectionHttpServer.CANDIDATES_PATH,
                    ReflectionHttpServerTest.validRequest());
            assertEquals(200, replay.status(), replay.text());
            assertArrayEquals(written.body(), replay.body());
            JsonObject retry = JsonParser.parseString(ReflectionHttpServerTest.validRequest()).getAsJsonObject();
            retry.addProperty("requestId", UUID.randomUUID().toString());
            Response newRequestReplay = request(restarted, "POST", ReflectionHttpServer.CANDIDATES_PATH, retry.toString());
            assertEquals(200, newRequestReplay.status(), newRequestReplay.text());
            JsonObject expected = JsonParser.parseString(written.text()).getAsJsonObject();
            expected.add("requestId", retry.get("requestId"));
            assertEquals(expected, JsonParser.parseString(newRequestReplay.text()));
            Response voiceReplay = request(restarted, "POST", ReflectionHttpServer.SPEECH_PATH, speech());
            assertEquals(200, voiceReplay.status(), voiceReplay.text());
            assertArrayEquals(audio.body(), voiceReplay.body());
            assertRemaining(restarted, 1);

            JsonObject next = JsonParser.parseString(ReflectionHttpServerTest.validRequest()).getAsJsonObject();
            String nextRun = UUID.randomUUID().toString();
            next.addProperty("runId", nextRun);
            next.addProperty("requestId", UUID.randomUUID().toString());
            assertEquals(200, request(restarted, "POST", ReflectionHttpServer.RUN_ACCESS_PATH,
                    "{\"runId\":\"" + nextRun + "\"}").status());
            assertError(request(restarted, "POST", ReflectionHttpServer.CANDIDATES_PATH, next.toString()),
                    429, "spend_limit_reached");
        }
        assertEquals(paidSends, events(directory), "Restart/replay/denial must not send another provider request");
    }

    @Test void abruptExitAfterEitherPaidSendKeepsItsPendingReservation() throws Exception {
        for (String interrupted : List.of("writer", "voice")) {
            Path scenario = Files.createDirectory(directory.resolve(interrupted));
            String path = interrupted.equals("writer") ? ReflectionHttpServer.CANDIDATES_PATH
                    : ReflectionHttpServer.SPEECH_PATH;
            String body = interrupted.equals("writer") ? ReflectionHttpServerTest.validRequest() : speech();
            try (Child first = start(scenario, interrupted)) {
                admit(first, RUN);
                if (interrupted.equals("voice")) {
                    assertEquals(200, request(first, "POST", ReflectionHttpServer.CANDIDATES_PATH,
                            ReflectionHttpServerTest.validRequest()).status());
                }
                try (Socket ignored = send(first, "POST", path, body)) {
                    awaitSend(scenario, interrupted, first);
                    first.kill(); // No shutdown hook or graceful store cleanup can rescue the state.
                }
            }
            List<String> paidSends = events(scenario);
            assertEquals(interrupted.equals("writer") ? List.of("writer") : List.of("writer", "voice"), paidSends);
            try (Child restarted = start(scenario, "complete")) {
                assertRemaining(restarted, 1);
                assertError(request(restarted, "POST", path, body), 503,
                        interrupted.equals("writer") ? "reflection_pending" : "speech_pending");
            }
            assertEquals(paidSends, events(scenario), "An uncertain paid send must never be automatically repeated");
        }
    }

    private static void admit(Child child, String run) throws Exception {
        assertEquals(200, request(child, "POST", ReflectionHttpServer.TRIAL_PATH, "{}").status());
        assertEquals(200, request(child, "POST", ReflectionHttpServer.RUN_ACCESS_PATH,
                "{\"runId\":\"" + run + "\"}").status());
    }

    private static void assertRemaining(Child child, int expected) throws Exception {
        Response response = request(child, "GET", ReflectionHttpServer.ACCESS_PATH, "");
        assertEquals(200, response.status(), response.text());
        assertEquals(expected, JsonParser.parseString(response.text()).getAsJsonObject().get("remaining").getAsInt());
    }

    private static String speech() {
        JsonObject body = new JsonObject();
        body.addProperty("text", TEXT);
        return body.toString();
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.status(), response.text());
        assertEquals(code, JsonParser.parseString(response.text()).getAsJsonObject()
                .getAsJsonObject("error").get("code").getAsString());
    }

    private static List<String> events(Path directory) throws IOException {
        Path file = directory.resolve("simulated-sends.txt");
        return Files.exists(file) ? Files.readAllLines(file) : List.of();
    }

    private static void awaitSend(Path directory, String name, Child child) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && child.process().isAlive()) {
            if (events(directory).contains(name)) return;
            Thread.sleep(25);
        }
        fail("Child did not record the simulated " + name + " send: " + child.log());
    }

    private static Child start(Path directory, String stallAfter) throws Exception {
        String identity = UUID.randomUUID().toString();
        Path ready = directory.resolve(identity + ".ready");
        Path log = directory.resolve(identity + ".log");
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "-Djdk.httpclient.redirects.retrylimit=1", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                ServerProcess.class.getName(), directory.toString(), ready.toString(), stallAfter)
                .redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("RUNSTATE_") || key.startsWith("ANTHROPIC_")
                || key.startsWith("ELEVENLABS_") || key.startsWith("MUSIXMATCH_") || key.equals("PORT")
                || key.equals("JAVA_TOOL_OPTIONS") || key.equals("JDK_JAVA_OPTIONS") || key.equals("_JAVA_OPTIONS"));
        Process process = builder.start();
        Child starting = new Child(process, 0, log);
        boolean started = false;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (process.isAlive() && System.nanoTime() < deadline) {
                if (Files.exists(ready)) {
                    String port = Files.readString(ready);
                    if (port.matches("[0-9]+")) {
                        started = true;
                        return new Child(process, Integer.parseInt(port), log);
                    }
                }
                Thread.sleep(25);
            }
            fail("Child server did not start: " + starting.log());
            throw new AssertionError();
        } finally {
            if (!started && process.isAlive()) starting.kill();
        }
    }

    private record Child(Process process, int port, Path output) implements AutoCloseable {
        String log() throws IOException { return Files.readString(output); }
        void kill() throws Exception {
            process.destroyForcibly();
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Child must terminate");
        }
        @Override public void close() throws Exception {
            if (!process.isAlive()) return;
            try {
                process.getOutputStream().write('\n');
                process.getOutputStream().flush();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Child must close after stdin stop");
                assertEquals(0, process.exitValue(), log());
            } finally {
                if (process.isAlive()) kill();
            }
        }
    }

    private static Socket send(Child child, String method, String path, String body) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress("127.0.0.1", child.port()), 5_000);
            socket.setSoTimeout(10_000);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String headers = method + " " + path + " HTTP/1.1\r\nHost: " + HOST
                    + "\r\nAuthorization: Bearer " + TOKEN + "\r\n" + ReflectionHttpServer.RUN_ID_HEADER + ": " + RUN
                    + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length + "\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write(headers.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().write(bytes);
            socket.getOutputStream().flush();
            return socket;
        } catch (IOException failure) {
            socket.close();
            throw failure;
        }
    }

    private static Response request(Child child, String method, String path, String body) throws IOException {
        try (Socket socket = send(child, method, path, body)) {
            byte[] bytes = socket.getInputStream().readNBytes(1_048_577);
            assertTrue(bytes.length <= 1_048_576, "Bound the fixture's HTTP response");
            String response = new String(bytes, StandardCharsets.ISO_8859_1);
            int separator = response.indexOf("\r\n\r\n");
            assertTrue(separator >= 0, "Expected a complete HTTP response");
            List<String> headers = Arrays.asList(response.substring(0, separator).split("\r\n"));
            assertFalse(headers.stream().anyMatch(line -> line.regionMatches(true, 0, "Transfer-Encoding:", 0, 18)),
                    "Fixture expects a fixed-length response");
            List<String> lengths = headers.stream()
                    .filter(line -> line.regionMatches(true, 0, "Content-Length:", 0, 15)).toList();
            assertEquals(1, lengths.size(), "Expected exactly one Content-Length");
            int length = Integer.parseInt(lengths.get(0).substring(15).trim());
            assertEquals(length, bytes.length - separator - 4, "HTTP body must match its declared byte length");
            int status = Integer.parseInt(response.substring(0, response.indexOf("\r\n")).split(" ")[1]);
            return new Response(status, Arrays.copyOfRange(bytes, separator + 4, bytes.length));
        }
    }

    private record Response(int status, byte[] body) {
        String text() { return new String(body, StandardCharsets.UTF_8); }
    }

    /** Never constructs a production provider: this entrypoint exists only on the test classpath. */
    public static final class ServerProcess {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            Path storage = directory.resolve("stores");
            Clock clock = Clock.fixed(Instant.parse("2026-11-20T12:00:00Z"), ZoneOffset.UTC);
            String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(TOKEN.getBytes(StandardCharsets.UTF_8)));
            String grants = "[{\"principalId\":\"restart-runner\",\"tokenSha256\":\"" + fingerprint
                    + "\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"enabled\":true,\"reflectionTier\":\"trial\"}]";
            var access = ReflectionHttpServer.RemoteAccess.individual("127.0.0.1", HOST,
                    BackendAccessPolicy.fromJson(grants, clock));
            ReflectionProvider writer = evidence -> {
                sent(directory, "writer", args[2], ProviderSpendBudget.Provider.ANTHROPIC);
                return new ReflectionContract.CandidateReflections(TEXT, "Good finish.", "Strong finish.", "Run complete.");
            };
            ReflectionHttpServer.SpeechProvider voice = text -> {
                sent(directory, "voice", args[2], ProviderSpendBudget.Provider.ELEVENLABS);
                return AUDIO;
            };
            try (var server = ReflectionHttpServer.createRemote(writer, 0, ReflectionHttpServer.GenerationMode.ANTHROPIC,
                    access, 2, 256, voice, null,
                    new SpeechReplayStore(storage, clock, SpeechReplayStore.MAX_ENTRIES),
                    new WrittenReflectionReplayStore(storage, clock, WrittenReflectionReplayStore.MAX_ENTRIES),
                    new ProviderSpendBudget(storage, new ProviderSpendBudget.Limits(2, 2, 1, 1, 0), clock),
                    new ReflectionAllowance(storage, clock))) {
                server.start();
                Files.writeString(Path.of(args[1]), Integer.toString(server.address().getPort()));
                System.in.read();
            }
        }

        private static void sent(Path directory, String name, String stalled, ProviderSpendBudget.Provider provider) {
            ProviderSpendScope.beforeSend(provider);
            try (FileChannel log = FileChannel.open(directory.resolve("simulated-sends.txt"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                ByteBuffer event = StandardCharsets.UTF_8.encode(name + "\n");
                while (event.hasRemaining()) log.write(event);
                log.force(true);
            } catch (IOException failure) { throw new IllegalStateException("Cannot record simulated send", failure); }
            if (name.equals(stalled)) {
                try { new CountDownLatch(1).await(); }
                catch (InterruptedException stop) { Thread.currentThread().interrupt(); throw new IllegalStateException(stop); }
            }
        }
    }
}
