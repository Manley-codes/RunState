package com.runstate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Deterministic guards for the stronger-model control.
 *
 * Everything here is local: no API key, no network, no MySQL. These tests protect the
 * EXPERIMENT, not the model. A control is only worth paying for if exactly one thing moved, so
 * most of what follows is a diff between the frozen probe request and the control request,
 * asserting that the only difference is the model identifier.
 *
 * The frozen requests are read from committed evidence, so these tests double as a tripwire: if
 * that evidence file is ever edited, regenerated, or corrupted, the build fails here rather than
 * during a paid run.
 */
public class MusicIntelligenceStrongerModelControlTest {

    // The four recorded hashes, copied independently from the evidence file's own `sha256`
    // fields. Deliberately NOT read from the control class or recomputed from the bodies: a test
    // that derives its expectation from the thing under test proves nothing. These are the
    // approved bytes, and an edit to the evidence must fail the build.
    private static final Map<String, String> APPROVED_FROZEN_HASHES = Map.of(
            "S1", "050E05E22871C5CC2EA34954E2C71F1B91DB1746BF89B310A14D8FDF0B1FC1CC",
            "S2", "9DA5B6B53B6B870B81E717A9579F14203397657B95868052DB16BC43C468E21C",
            "S11", "2DAEFF8E0D025FDF844471AD599169FB5CBDEAEFF56D72FA2E47BBE73905442C",
            "S12", "3FF23C16834F83C70ADE128D35FA0E753657433C0C699510B324A40F769C6E76");

    // Pinned so the blind shuffle is reproducible in tests. Live runs seed from the clock.
    private static final long FIXED_SEED = 20260731L;

    // --- The pre-registered control design ---------------------------------------------

    @Test
    void theControlCoversExactlyTheFourProbedScenariosInTheProbeOrder() {
        // Same set and same order as the probe. A control over a different set would not be a
        // control — there would be nothing to compare it against.
        assertEquals(List.of("S1", "S2", "S11", "S12"),
                MusicIntelligenceStrongerModelControl.SCENARIO_IDS);
        assertEquals(MusicIntelligenceCreativeCeilingProbe.SCENARIO_IDS,
                MusicIntelligenceStrongerModelControl.SCENARIO_IDS,
                "the control must probe exactly the scenarios the probe did");
    }

    @Test
    void theControlPlansThreeIterationsPerScenarioAndTwelveCallsInTotal() {
        assertEquals(3, MusicIntelligenceStrongerModelControl.ITERATIONS);
        assertEquals(12, MusicIntelligenceStrongerModelControl.PLANNED_CALLS);
        // Pinned as a product, not just a number, so changing either input without changing the
        // other cannot leave a stale total behind.
        assertEquals(MusicIntelligenceStrongerModelControl.SCENARIO_IDS.size()
                        * MusicIntelligenceStrongerModelControl.ITERATIONS,
                MusicIntelligenceStrongerModelControl.PLANNED_CALLS);
        // ...and it must match the probe's spend exactly, or the two runs are not comparable.
        assertEquals(MusicIntelligenceCreativeCeilingProbe.PLANNED_CALLS,
                MusicIntelligenceStrongerModelControl.PLANNED_CALLS);
    }

    @Test
    void theModelPairIsTheApprovedBaselineAndControl() {
        assertEquals("claude-haiku-4-5-20251001",
                MusicIntelligenceStrongerModelControl.BASELINE_MODEL);
        assertEquals("claude-opus-5",
                MusicIntelligenceStrongerModelControl.CONTROL_MODEL);
        assertNotEquals(MusicIntelligenceStrongerModelControl.BASELINE_MODEL,
                MusicIntelligenceStrongerModelControl.CONTROL_MODEL,
                "a control that did not change the model would measure nothing");
    }

    // --- The frozen evidence still is what it was ---------------------------------------

    @Test
    void theRunnerPinsTheApprovedHashesRatherThanTrustingTheFile() {
        // The integrity boundary. The runner must carry its own expected hashes as constants;
        // if it read them from the evidence file it would only be checking that the file agrees
        // with itself, and a regenerated file agrees with itself perfectly.
        assertEquals(APPROVED_FROZEN_HASHES,
                MusicIntelligenceStrongerModelControl.APPROVED_FROZEN_HASHES,
                "the runner's pinned hashes must match the independently approved values");
    }

    @Test
    void theFrozenEvidenceFileStillMatchesEveryApprovedHash() throws Exception {
        List<MusicIntelligenceStrongerModelControl.FrozenRequest> frozen =
                MusicIntelligenceStrongerModelControl.loadFrozenRequests(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH);

        assertEquals(4, frozen.size());
        for (MusicIntelligenceStrongerModelControl.FrozenRequest request : frozen) {
            assertTrue(request.verified(),
                    request.scenarioId + " no longer matches its approved hash");
            assertEquals(APPROVED_FROZEN_HASHES.get(request.scenarioId), request.approvedSha256,
                    request.scenarioId + "'s pinned approved hash drifted");
            assertEquals(APPROVED_FROZEN_HASHES.get(request.scenarioId), request.actualSha256,
                    request.scenarioId + "'s frozen bytes no longer hash to the approved value");
            assertTrue(request.fileAgreesWithApproval(),
                    request.scenarioId + "'s evidence file self-report disagrees with approval");
        }
    }

    @Test
    void loadingRefusesWhenAFrozenBodyNoLongerMatchesTheApprovedHash(@TempDir Path tempDir)
            throws Exception {
        // One byte changed inside a body, with the file's own hash left alone — a corrupt file.
        String tampered = Files.readString(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        StandardCharsets.UTF_8)
                .replace("Runner: Runner", "Runner: Manley");
        Path path = tempDir.resolve("tampered.json");
        Files.writeString(path, tampered, StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MusicIntelligenceStrongerModelControl.loadFrozenRequests(path));
        assertTrue(failure.getMessage().contains("does not match its approved SHA-256"),
                "the refusal must name the hash mismatch: " + failure.getMessage());
        assertTrue(failure.getMessage().contains("the file is corrupt"),
                "a body/hash disagreement must be diagnosed as corruption: " + failure.getMessage());
    }

    @Test
    void loadingRefusesAConsistentlyRegeneratedFileThatNoLongerMatchesApproval(
            @TempDir Path tempDir) throws Exception {
        // The case the old design could not catch: a body changed AND its recorded hash
        // refreshed to match. The file is perfectly self-consistent and completely wrong.
        String original = Files.readString(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                StandardCharsets.UTF_8);
        String tamperedBody = original.replace("Runner: Runner", "Runner: Manley");

        // Recompute S1's hash over its regenerated body and swap it in, the way a regeneration
        // script would.
        String regeneratedS1 = extractBodyRaw(tamperedBody, "S1");
        String refreshed = tamperedBody.replace(
                APPROVED_FROZEN_HASHES.get("S1"),
                MusicIntelligenceStrongerModelControl.sha256(regeneratedS1));

        Path path = tempDir.resolve("regenerated.json");
        Files.writeString(path, refreshed, StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MusicIntelligenceStrongerModelControl.loadFrozenRequests(path));
        assertTrue(failure.getMessage().contains("REGENERATED"),
                "a self-consistent but unapproved file must be diagnosed as regenerated: "
                        + failure.getMessage());
        assertTrue(failure.getMessage().contains("restore it from git"),
                "the operator must be told not to rebuild it: " + failure.getMessage());
    }

    @Test
    void loadingRefusesWhenAProbedScenarioIsMissingEntirely(@TempDir Path tempDir)
            throws Exception {
        String withoutS11 = Files.readString(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        StandardCharsets.UTF_8)
                .replace("\"scenario\": \"S11\"", "\"scenario\": \"S99\"");
        Path path = tempDir.resolve("missing.json");
        Files.writeString(path, withoutS11, StandardCharsets.UTF_8);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> MusicIntelligenceStrongerModelControl.loadFrozenRequests(path));
        assertTrue(failure.getMessage().contains("S11"),
                "the refusal must name the missing scenario: " + failure.getMessage());
    }

    // --- One variable, and only one ----------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("controlledScenarioIds")
    void everyControlRequestDiffersFromTheFrozenProbeRequestOnlyInTheModel(String scenarioId)
            throws Exception {
        String frozen = frozenBody(scenarioId);
        String control = MusicIntelligenceStrongerModelControl.controlBody(frozen);

        JsonObject frozenJson = parse(frozen);
        JsonObject controlJson = parse(control);

        // Field order too, not just membership: the body is the experiment's fixed apparatus.
        assertEquals(new ArrayList<>(frozenJson.keySet()), new ArrayList<>(controlJson.keySet()),
                "the control body must keep the frozen field set and order");

        assertEquals(frozenJson.get("max_tokens"), controlJson.get("max_tokens"));
        assertEquals(256, controlJson.get("max_tokens").getAsInt(), "max_tokens must stay 256");
        assertEquals(frozenJson.get("system"), controlJson.get("system"),
                "the system prompt must survive the model swap untouched");
        // JsonArray equality is structural, so this compares the whole user message — role,
        // content, enum tokens, field ordering, every character.
        assertEquals(frozenJson.getAsJsonArray("messages"), controlJson.getAsJsonArray("messages"));

        // ...and the one thing that IS supposed to move.
        assertEquals("claude-haiku-4-5-20251001", frozenJson.get("model").getAsString());
        assertEquals("claude-opus-5", controlJson.get("model").getAsString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controlledScenarioIds")
    void undoingTheSubstitutionReproducesTheFrozenBodyByteForByte(String scenarioId)
            throws Exception {
        String frozen = frozenBody(scenarioId);
        String control = MusicIntelligenceStrongerModelControl.controlBody(frozen);

        // The strongest available proof that nothing else moved. Structural JSON equality could
        // still hide a whitespace or escaping difference; byte equality after reversing the one
        // substitution cannot.
        assertEquals(frozen,
                MusicIntelligenceStrongerModelControl.reverseSubstitution(control),
                scenarioId + ": reversing the model swap must reproduce the frozen bytes exactly");
        assertEquals(APPROVED_FROZEN_HASHES.get(scenarioId),
                MusicIntelligenceStrongerModelControl.sha256(
                        MusicIntelligenceStrongerModelControl.reverseSubstitution(control)),
                scenarioId + ": the restored body must rehash to the approved value");

        // ...and the substitution really is a single-token edit of the frozen string.
        assertEquals(frozen.length()
                        - "claude-haiku-4-5-20251001".length() + "claude-opus-5".length(),
                control.length(),
                scenarioId + ": exactly one model identifier's worth of characters may change");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controlledScenarioIds")
    void noTuningFieldIsAddedAnywhere(String scenarioId) throws Exception {
        JsonObject control = parse(
                MusicIntelligenceStrongerModelControl.controlBody(frozenBody(scenarioId)));

        // Opus runs at its default effort. Every one of these would be a second variable.
        assertFalse(control.has("temperature"), "temperature must not be sent");
        assertFalse(control.has("effort"), "effort must not be sent");
        assertFalse(control.has("thinking"), "thinking must not be sent");
        assertFalse(control.has("top_p"), "top_p must not be sent");
        assertFalse(control.has("top_k"), "top_k must not be sent");

        // Exactly the four fields the probe sent, and no fifth.
        assertEquals(Set.of("model", "max_tokens", "system", "messages"), control.keySet());
    }

    @Test
    void theSubstitutionRefusesWhenTheModelNameDoesNotAppearExactlyOnce() {
        // If the baseline model name ever appeared inside a system prompt or a music note, a
        // blind replace would rewrite run data too. The guard is asserted, not assumed.
        IllegalStateException none = assertThrows(IllegalStateException.class,
                () -> MusicIntelligenceStrongerModelControl.controlBody("{\"model\":\"other\"}"));
        assertTrue(none.getMessage().contains("found 0"), none.getMessage());

        IllegalStateException twice = assertThrows(IllegalStateException.class,
                () -> MusicIntelligenceStrongerModelControl.controlBody(
                        "{\"model\":\"claude-haiku-4-5-20251001\","
                                + "\"system\":\"claude-haiku-4-5-20251001\"}"));
        assertTrue(twice.getMessage().contains("found 2"), twice.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("controlledScenarioIds")
    void theControlledUserMessagesKeepTheirProductionEnumTokens(String scenarioId)
            throws Exception {
        // Held constant on purpose. A model that only reads well against prettier input has not
        // been tested, so the control must not quietly clean up what the probe sent.
        String content = parse(MusicIntelligenceStrongerModelControl
                .controlRequestBodies(MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH)
                .get(scenarioId))
                .getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString();

        assertTrue(content.contains("Music reply stage: "),
                scenarioId + " must still carry the probe's stage line");
        assertTrue(content.contains("(had music)"),
                scenarioId + " must still carry the probe's music-state token");
    }

    // --- The cost gate -----------------------------------------------------------------

    @Test
    void previewIsTheOnlyModeThatNeedsNoKeyAndSpendsNothing() {
        assertEquals(MusicIntelligenceStrongerModelControl.Mode.PREVIEW,
                MusicIntelligenceStrongerModelControl.resolveMode(new String[]{"preview"}));

        // Building the whole report here is the proof that preview needs no key and no network:
        // this suite runs with ANTHROPIC_API_KEY unset and no connectivity assumption, and a
        // report that touched either would fail or hang instead of returning.
        String report = MusicIntelligenceStrongerModelControl.previewReport(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH);

        assertTrue(report.contains("claude-opus-5"));
        assertTrue(report.contains("claude-haiku-4-5-20251001"));
        assertTrue(report.contains("planned calls  : 12"));
        assertTrue(report.contains("iterations     : 3 per scenario"));
        assertTrue(report.contains("S1, S2, S11, S12"));
        assertTrue(report.contains("request timeout: 30s"));
        assertTrue(report.contains("DIAGNOSTIC ONLY"),
                "the preview must say plainly that this is not V1 evidence");
        assertTrue(report.contains("not sent       : temperature, effort, thinking"),
                "the preview must state that no tuning field is sent");

        // Same output twice — a report that reached the network or the clock would not be.
        assertEquals(report, MusicIntelligenceStrongerModelControl.previewReport(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH));
    }

    @Test
    void thePreviewShowsEveryRequestHashAndTheModelOnlyDiffProof() {
        String report = MusicIntelligenceStrongerModelControl.previewReport(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH);

        for (String scenarioId : MusicIntelligenceStrongerModelControl.SCENARIO_IDS) {
            assertTrue(report.contains(APPROVED_FROZEN_HASHES.get(scenarioId)),
                    "preview must show " + scenarioId + "'s recorded request hash");
        }
        // The proof an operator actually has to read before authorizing the spend.
        assertTrue(report.contains("reverse proof   : undoing the substitution reproduces the frozen body EXACTLY"),
                "preview must prove the substitution is reversible");
        assertTrue(report.contains("(APPROVED, pinned in the runner)"),
                "preview must say the expected hashes are pinned, not read from the file");
        assertTrue(report.contains("file self-claim :"),
                "preview must show the file's self-report separately from approval");
        assertFalse(report.contains("DISAGREES"),
                "the evidence file's self-report must agree with the pinned approval");
        assertTrue(report.contains("temperature     : absent"));
        assertTrue(report.contains("system          : unchanged (structurally equal)"));
        assertTrue(report.contains("messages        : unchanged (structurally equal)"));
        assertTrue(report.contains("max_tokens      : 256  unchanged"));
        assertFalse(report.contains("MISMATCH"), "no hash in the preview may mismatch");
        assertFalse(report.contains("**CHANGED**"), "nothing but the model may be reported changed");
    }

    @Test
    void previewRefusesVisiblyRatherThanPretendingWhenTheEvidenceIsUnverifiable(
            @TempDir Path tempDir) throws Exception {
        String tampered = Files.readString(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        StandardCharsets.UTF_8)
                .replace("Runner: Runner", "Runner: Manley");
        Path path = tempDir.resolve("tampered.json");
        Files.writeString(path, tampered, StandardCharsets.UTF_8);

        String report = MusicIntelligenceStrongerModelControl.previewReport(path);

        assertTrue(report.contains("ABORTED"), "a preview over bad evidence must not look normal");
        assertTrue(report.contains("does not match its approved SHA-256"));
        assertFalse(report.contains("To run it for real"),
                "a refusing preview must not offer the live command");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("argumentsThatMustNotStartALiveRun")
    void liveModeCannotBeginWithoutBothTheRunModeAndTheExactConfirmation(String description,
                                                                        String[] args) {
        assertEquals(MusicIntelligenceStrongerModelControl.Mode.USAGE,
                MusicIntelligenceStrongerModelControl.resolveMode(args),
                description + " must print usage and spend nothing");
    }

    @Test
    void liveModeBeginsOnlyForTheExactTwoArgumentForm() {
        assertEquals(MusicIntelligenceStrongerModelControl.Mode.LIVE,
                MusicIntelligenceStrongerModelControl.resolveMode(
                        new String[]{"run", "--confirm-12-billable-calls"}));
        // The flag states its own cost, so it cannot be typed by accident or half-remembered.
        assertEquals("--confirm-12-billable-calls",
                MusicIntelligenceStrongerModelControl.LIVE_CONFIRMATION);
        assertTrue(MusicIntelligenceStrongerModelControl.LIVE_CONFIRMATION
                        .contains(String.valueOf(MusicIntelligenceStrongerModelControl.PLANNED_CALLS)),
                "the confirmation argument must name the number of calls it authorizes");
    }

    // --- Spend safety ------------------------------------------------------------------
    //
    // These drive the real control loop with a counting reply source, so no network call happens
    // and the assertions are about the exact number of calls that WOULD have been billed. That
    // is the only way to verify a promise about a call that must not be made.

    @Test
    void nothingIsSpentWhenTheFrozenEvidenceDoesNotVerify(@TempDir Path tempDir) throws Exception {
        String tampered = Files.readString(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        StandardCharsets.UTF_8)
                .replace("Runner: Runner", "Runner: Manley");
        Path path = tempDir.resolve("tampered.json");
        Files.writeString(path, tampered, StandardCharsets.UTF_8);

        CountingReplies replies = new CountingReplies();
        CountingSink sink = new CountingSink();
        CountingSink packet = new CountingSink();

        MusicIntelligenceStrongerModelControl.LiveResult result =
                MusicIntelligenceStrongerModelControl.executeControl(
                        path, FIXED_SEED, replies, sink, packet, line -> { });

        assertEquals(0, replies.calls, "unverified evidence must cost nothing");
        assertEquals(0, result.callsAttempted);
        assertEquals(0, sink.writes, "not even the transcript header may be written");
        assertEquals(0, packet.writes, "not even the packet header may be written");
        assertTrue(result.stopped());
        assertTrue(result.stopReason.startsWith(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUEST_MISMATCH),
                "the stop reason must name the evidence, not the API: " + result.stopReason);
    }

    @Test
    void nothingIsSpentWhenTheTranscriptCannotBeCreated() {
        CountingReplies replies = new CountingReplies();
        List<String> console = new ArrayList<>();

        MusicIntelligenceStrongerModelControl.LiveResult result =
                MusicIntelligenceStrongerModelControl.executeControl(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        FIXED_SEED,
                        replies,
                        content -> { throw new java.io.IOException("disk is read-only"); },
                        new CountingSink(),
                        console::add);

        // Zero. Twelve replies that cannot be written down are worth nothing and still cost full
        // price, so a disk problem must surface while it is free.
        assertEquals(0, replies.calls, "no call may be made when the transcript cannot be created");
        assertEquals(0, result.callsAttempted);
        assertTrue(result.stopped());
        assertTrue(result.stopReason.startsWith(
                        MusicIntelligenceStrongerModelControl.TRANSCRIPT_UNAVAILABLE),
                "the stop reason must name the transcript, not the API: " + result.stopReason);
        assertTrue(result.stopReason.contains("disk is read-only"),
                "the real cause must reach the operator");
    }

    @Test
    void aReplyThatCannotBeSavedSurvivesInTheConsoleAndNoFurtherCallIsMade() {
        CountingReplies replies = new CountingReplies();
        List<String> console = new ArrayList<>();

        // Header write succeeds; the save after the first completed reply fails.
        MusicIntelligenceStrongerModelControl.LiveResult result =
                MusicIntelligenceStrongerModelControl.executeControl(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        FIXED_SEED,
                        replies,
                        new FailAfterFirstWrite(),
                        new CountingSink(),
                        console::add);

        // Exactly one call was paid for, and the run stopped rather than buying an eleventh reply
        // it had nowhere to put.
        assertEquals(1, replies.calls, "the run must stop before the next call");
        assertEquals(1, result.callsAttempted);
        assertEquals(11,
                MusicIntelligenceStrongerModelControl.PLANNED_CALLS - result.callsAttempted,
                "the operator must be able to see how many calls went unspent");

        assertTrue(result.stopped());
        assertTrue(result.stopReason.startsWith(
                        MusicIntelligenceStrongerModelControl.TRANSCRIPT_WRITE_FAILED),
                "the stop reason must name the save failure: " + result.stopReason);
        assertEquals("S1 iteration 1", result.failedAt);

        // The paid-for reply is not lost with the file. Console before disk is what saves it.
        assertTrue(console.stream().anyMatch(line -> line.contains("reply for S1 iteration 1")),
                "the completed reply must remain in the console after the save failed");
    }

    @Test
    void anApiFailureStopsTheRunAndIsNeverTurnedIntoAModelOutput() {
        FailingReplies replies = new FailingReplies();
        CountingSink sink = new CountingSink();
        CountingSink packet = new CountingSink();
        List<String> console = new ArrayList<>();

        MusicIntelligenceStrongerModelControl.LiveResult result =
                MusicIntelligenceStrongerModelControl.executeControl(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        FIXED_SEED, replies, sink, packet, console::add);

        // One call was issued and failed; the other eleven were not bought against a broken state.
        assertEquals(1, replies.calls);
        assertEquals(1, result.callsAttempted);
        assertTrue(result.stopped());
        assertTrue(result.stopReason.startsWith(
                        MusicIntelligenceStrongerModelControl.API_FAILURE),
                "the stop reason must name the API failure: " + result.stopReason);
        assertTrue(result.stopReason.contains("HTTP 529"),
                "the real cause must reach the operator, not a generic category");
        assertEquals("S1 iteration 1", result.failedAt);

        // The failure must not be laundered into something gradable. Only the header was written,
        // and no reply text or latency was recorded for a call that produced nothing.
        assertEquals(1, sink.writes, "only the pre-call header may have been written");
        assertEquals(1, packet.writes, "only the pre-call packet header may have been written");
        assertFalse(sink.last.contains("**Iteration 1**"),
                "a failed call must not appear in the transcript as an output");
        assertTrue(packet.last.contains("Outputs recorded so far: 0 of 12"),
                "a failed call must not appear in the blind packet as an output");
        assertTrue(result.latenciesMs.isEmpty(),
                "no latency may be recorded for a call that produced nothing");
    }

    @Test
    void everyCompletedReplyIsSavedBeforeTheNextCallIsMadeAndLatencyIsRecorded() {
        CountingReplies replies = new CountingReplies();
        CountingSink sink = new CountingSink();
        CountingSink packet = new CountingSink();

        MusicIntelligenceStrongerModelControl.LiveResult result =
                MusicIntelligenceStrongerModelControl.executeControl(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                        FIXED_SEED, replies, sink, packet, line -> { });

        assertFalse(result.stopped(), "the clean path must not report a stop reason");
        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS, replies.calls);
        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS, result.callsAttempted);

        // One write before call 1, one after each completed reply, and one for the latency
        // summary — so a failure at any point leaves every earlier paid-for output on disk.
        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS + 2, sink.writes);
        // The packet gets the header plus one write per completed reply.
        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS + 1, packet.writes);
        assertTrue(sink.last.contains("reply for S12 iteration 3"),
                "the final transcript must carry the last reply");
        assertTrue(sink.last.contains("reply for S1 iteration 1"),
                "the transcript accumulates rather than overwriting earlier replies");

        // Per-call latency: one entry per completed call, and a table in the record.
        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS,
                result.latenciesMs.size(), "every completed call must record a latency");
        assertTrue(result.latenciesMs.stream().allMatch(ms -> ms >= 0));
        assertTrue(sink.last.contains("## Latency"),
                "the transcript must carry the per-call latency table");
        assertTrue(sink.last.contains("| Call | Scenario | Iteration | ms |"));
    }

    @Test
    void theTranscriptRecordsTheVerifiedFrozenHashesBeforeAnyReply() {
        CountingSink sink = new CountingSink();

        MusicIntelligenceStrongerModelControl.executeControl(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                FIXED_SEED, new CountingReplies(), sink, new CountingSink(), line -> { });

        // Provenance travels with the outputs. A transcript that did not say which frozen bytes
        // produced it could not be re-verified later.
        for (String scenarioId : MusicIntelligenceStrongerModelControl.SCENARIO_IDS) {
            assertTrue(sink.last.contains(APPROVED_FROZEN_HASHES.get(scenarioId)),
                    "the transcript must record " + scenarioId + "'s verified frozen hash");
        }
        assertTrue(sink.last.contains("claude-opus-5"));
        assertTrue(sink.last.contains("claude-haiku-4-5-20251001"));
    }

    @Test
    void theRealTranscriptSinkReportsFailureRatherThanSwallowingIt(@TempDir Path tempDir)
            throws Exception {
        // A file where a directory would have to be — createDirectories cannot succeed, which is
        // the shape of the real failure the abort path depends on detecting.
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "not a directory");

        MusicIntelligenceStrongerModelControl.TranscriptSink sink =
                MusicIntelligenceStrongerModelControl.transcriptSink(
                        blocker.resolve("control.md"));

        assertThrows(Exception.class, () -> sink.write("anything"),
                "the sink must propagate failure — a swallowed one would let the run continue");
    }

    @Test
    void theRealTranscriptSinkWritesUtf8AndOverwritesInPlace(@TempDir Path tempDir)
            throws Exception {
        Path path = tempDir.resolve("nested").resolve("control.md");
        MusicIntelligenceStrongerModelControl.TranscriptSink sink =
                MusicIntelligenceStrongerModelControl.transcriptSink(path);

        sink.write("first");
        sink.write("first + second — em dash ’ →");

        // UTF-8 explicitly: every fixture music note carries an em dash, and the platform default
        // encoding on Windows would corrupt the record it exists to preserve.
        assertEquals("first + second — em dash ’ →",
                Files.readString(path, StandardCharsets.UTF_8));
    }

    // --- Model-blind grading -----------------------------------------------------------

    @Test
    void everyPlannedCallGetsExactlyOneBlindLabel() {
        Map<String, String> assignment =
                MusicIntelligenceStrongerModelControl.blindAssignment(FIXED_SEED);

        assertEquals(MusicIntelligenceStrongerModelControl.PLANNED_CALLS, assignment.size());
        assertEquals(new TreeSet<>(MusicIntelligenceStrongerModelControl.BLIND_LABELS),
                new TreeSet<>(assignment.values()),
                "every label must be used exactly once — a collision would lose an output");
        for (String scenarioId : MusicIntelligenceStrongerModelControl.SCENARIO_IDS) {
            for (int iteration = 1; iteration <= 3; iteration++) {
                assertTrue(assignment.containsKey(scenarioId + " iteration " + iteration));
            }
        }
    }

    @Test
    void theBlindOrderIsShuffledAndReproducibleFromItsSeed() {
        Map<String, String> first =
                MusicIntelligenceStrongerModelControl.blindAssignment(FIXED_SEED);

        // Reproducible: the same seed recovers the same mapping, which is what makes the
        // transcript's un-blinding key trustworthy after the fact.
        assertEquals(first, MusicIntelligenceStrongerModelControl.blindAssignment(FIXED_SEED));

        // ...and actually shuffled, rather than handing out A,B,C in run order.
        List<String> inRunOrder = new ArrayList<>(first.values());
        assertNotEquals(MusicIntelligenceStrongerModelControl.BLIND_LABELS, inRunOrder,
                "labels must not be assigned in run order");
        assertNotEquals(first, MusicIntelligenceStrongerModelControl.blindAssignment(FIXED_SEED + 1),
                "a different seed must produce a different blind order");
    }

    @Test
    void theBlindPacketCarriesTheFactsAndOutputsButNoModelLatencyOrBaseline() {
        CountingSink packet = new CountingSink();

        MusicIntelligenceStrongerModelControl.executeControl(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                FIXED_SEED, new NeutralReplies(), new CountingSink(), packet, line -> { });

        String blind = packet.last;

        // What a grader needs: every output, and the facts to check it against.
        assertTrue(blind.contains("Outputs recorded so far: 12 of 12"));
        for (String label : MusicIntelligenceStrongerModelControl.BLIND_LABELS) {
            assertTrue(blind.contains("## Output " + label), "packet must carry output " + label);
        }
        assertTrue(blind.contains("Pace: 9:29 min/mile"), "packet must carry the run facts");
        assertTrue(blind.contains("Music: Eminem — Lose Yourself (had music)"));

        // The rubric is inlined, so a grader never has to open a document that would un-blind
        // them just by being titled what it is titled.
        assertTrue(blind.contains("| **Hit** |"), "the packet must carry the labels inline");
        assertTrue(blind.contains("## Hard trust checks"),
                "the packet must carry the trust checks inline");

        // What must NOT be there. Any of these would let a grader reason about provenance
        // instead of quality.
        String lower = blind.toLowerCase();
        assertFalse(lower.contains("opus"), "the packet must not name the model");
        assertFalse(lower.contains("haiku"), "the packet must not name the baseline model");
        assertFalse(blind.contains(MusicIntelligenceStrongerModelControl.CONTROL_MODEL));
        assertFalse(blind.contains(MusicIntelligenceStrongerModelControl.BASELINE_MODEL));
        assertFalse(lower.contains("stronger"),
                "the packet must not reveal that a stronger model is on trial");
        assertFalse(lower.contains("control"),
                "the packet must not reveal that this is a control run");
        assertFalse(lower.contains("baseline"), "the packet must not mention a baseline");
        assertFalse(lower.contains("probe"), "the packet must not reference the probe");
        assertFalse(lower.contains("latency"), "the packet must not carry latency");
        assertFalse(lower.contains("iteration"), "the packet must not reveal iteration numbers");
        assertFalse(blind.contains("Cowork"), "the packet must not carry the probe's reviewers");
        assertFalse(blind.contains("Codex"));
        assertFalse(blind.contains("0 Hit"), "the packet must not carry any earlier tally");
        assertFalse(blind.contains("2 Hit"));
        for (String scenarioId : MusicIntelligenceStrongerModelControl.SCENARIO_IDS) {
            assertFalse(blind.contains("## " + scenarioId),
                    "the packet must not group outputs by scenario");
        }
        for (String hash : APPROVED_FROZEN_HASHES.values()) {
            assertFalse(blind.contains(hash), "the packet must not carry request hashes");
        }
    }

    @Test
    void theBlindPacketFilenameDoesNotUnblindTheRunBeforeItIsOpened() {
        // The first thing a grader sees. "stronger-model-control-...md" would announce the whole
        // experiment from the file listing.
        String packet = MusicIntelligenceStrongerModelControl
                .gradingPacketPath("20260731-120000").getFileName().toString().toLowerCase();

        assertFalse(packet.contains("stronger"), "the packet filename must not say 'stronger'");
        assertFalse(packet.contains("control"), "the packet filename must not say 'control'");
        assertFalse(packet.contains("opus"));
        assertFalse(packet.contains("haiku"));
        assertTrue(packet.startsWith("blind-grading-packet-"));
    }

    @Test
    void theUnblindingKeyLivesInTheTranscriptNotThePacket() {
        CountingSink transcript = new CountingSink();
        CountingSink packet = new CountingSink();

        MusicIntelligenceStrongerModelControl.executeControl(
                MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH,
                FIXED_SEED, new CountingReplies(), transcript, packet, line -> { });

        // The mapping must be recoverable — otherwise the blinding could never be undone and the
        // outputs could not be attributed to scenarios at all.
        assertTrue(transcript.last.contains("Un-blinding key"));
        assertTrue(transcript.last.contains("blind seed: " + FIXED_SEED));
        assertTrue(transcript.last.contains("| Blind label | Scenario | Iteration |"));

        // ...and it must not be reachable from the packet a grader reads.
        assertFalse(packet.last.contains("Un-blinding key"));
        assertFalse(packet.last.contains(String.valueOf(FIXED_SEED)));
    }

    @Test
    void theBlindPacketWritesGoStraightToCommittedEvidenceNotTarget() {
        // The probe's transcript had to be rescued out of target/ after the fact. Both records
        // now land in docs/ from the first byte, so there is nothing left to remember to save.
        Path transcript = MusicIntelligenceStrongerModelControl.transcriptPath("20260731-120000");
        Path packet = MusicIntelligenceStrongerModelControl.gradingPacketPath("20260731-120000");

        assertTrue(transcript.startsWith(Path.of("docs", "claude-memory", "evidence")),
                "the transcript must be written into committed evidence: " + transcript);
        assertTrue(packet.startsWith(Path.of("docs", "claude-memory", "evidence")),
                "the blind packet must be written into committed evidence: " + packet);
        assertFalse(transcript.startsWith("target"), "target/ is disposable");
        assertFalse(packet.startsWith("target"), "target/ is disposable");
        assertNotEquals(transcript, packet, "the two records must not overwrite each other");
    }

    // --- Boundaries this pass must not cross -------------------------------------------

    @Test
    void theControlIsNotCollectedByTheOrdinaryMavenSuite() {
        // Surefire collects by NAME. This is the boundary that keeps 12 billable calls out of
        // `mvn test`, so it is asserted rather than trusted to a comment.
        String name = MusicIntelligenceStrongerModelControl.class.getSimpleName();
        assertFalse(name.endsWith("Test"), "must not end in Test");
        assertFalse(name.endsWith("Tests"), "must not end in Tests");
        assertFalse(name.endsWith("TestCase"), "must not end in TestCase");
        assertFalse(name.startsWith("Test"), "must not start with Test");

        // ...and no annotation can pull it in either way.
        for (Method method : MusicIntelligenceStrongerModelControl.class.getDeclaredMethods()) {
            for (java.lang.annotation.Annotation annotation : method.getAnnotations()) {
                assertFalse(annotation.annotationType().getName().startsWith("org.junit"),
                        "no control method may carry a JUnit annotation: " + method.getName());
            }
        }
        assertEquals(0, MusicIntelligenceStrongerModelControl.class.getAnnotations().length,
                "the control class must carry no annotations at all");
    }

    @Test
    void noNetworkCapableMethodIsReachableFromATest() {
        // The only method that builds an HttpRequest is private, so nothing in this suite can
        // reach the wire even by mistake. Everything the tests DO drive takes an injected
        // ReplySource, which is why the counting stubs above prove what they prove.
        Set<String> reachable = new TreeSet<>();
        for (Method method : MusicIntelligenceStrongerModelControl.class.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) {
                continue;
            }
            reachable.add(method.getName());
        }
        assertEquals(Set.of("main", "resolveMode", "loadFrozenRequests", "controlBody",
                        "reverseSubstitution", "controlRequestBodies", "sha256",
                        "previewReport", "executeControl", "transcriptSink",
                        "blindAssignment", "blindPacket", "transcriptPath", "gradingPacketPath"),
                reachable,
                "the control must expose only its no-network seams plus main");

        // callControlApi is the wire, and it stays private.
        assertThrows(NoSuchMethodException.class,
                () -> MusicIntelligenceStrongerModelControl.class.getDeclaredMethod(
                        "callControlApi", String.class, String.class, String.class),
                "callControlApi must not gain a package-visible overload");
        Method wire = assertDoesNotThrow(
                () -> MusicIntelligenceStrongerModelControl.class.getDeclaredMethod(
                        "callControlApi", String.class, String.class));
        assertTrue(Modifier.isPrivate(wire.getModifiers()),
                "the HTTP path must stay private so no test can reach it");
    }

    @Test
    void theControlAddsNoProductionSeamToRunAgent() {
        // The control is test-side, and this pass changed no production file. What a test CAN
        // check deterministically is the surface: if a new package-private or public method had
        // been opened on RunAgent to serve the control, this fails.
        Set<String> exposed = new TreeSet<>();
        for (Method method : RunAgent.class.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) {
                continue;
            }
            exposed.add(method.getName());
        }
        assertEquals(Set.of("buildRunResponse", "buildRequestBody", "buildFallbackResponse",
                        "formatComparison", "formatPace"),
                exposed,
                "the control must not widen RunAgent's surface — it is a test-side experiment");
    }

    @Test
    void theControlReadsFrozenEvidenceRatherThanRebuildingFromProduction() {
        // The whole reason this class exists in this shape. If it ever starts calling the
        // evaluation runner's live seam, later prompt edits would silently enter the control and
        // it would stop being a control at all.
        Set<String> exposed = new TreeSet<>();
        for (Method method : MusicIntelligenceEvaluationRunner.class.getDeclaredMethods()) {
            if (method.isSynthetic() || Modifier.isPrivate(method.getModifiers())) {
                continue;
            }
            exposed.add(method.getName());
        }
        assertEquals(Set.of("main", "scenarioUserMessages", "scenarioRequestBodies"), exposed,
                "the evaluation runner's surface must be unchanged by this pass");

        // And the proof that the control does not use it: the frozen bytes are the probe's, not
        // today's. Today's production prompt is under revision, so these must NOT be equal.
        String frozenSystem = parse(assertDoesNotThrow(
                () -> frozenBody("S1"))).get("system").getAsString();
        String todaysSystem = parse(MusicIntelligenceEvaluationRunner.scenarioRequestBodies()
                .get("S1")).get("system").getAsString();
        assertNotEquals(todaysSystem, frozenSystem,
                "the frozen probe request must be the ablated prompt, not today's production one");
    }

    // --- helpers -----------------------------------------------------------------------

    private static Stream<String> controlledScenarioIds() {
        return MusicIntelligenceStrongerModelControl.SCENARIO_IDS.stream();
    }

    private static String extractBodyRaw(String json, String scenarioId) {
        for (var element : JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("requests")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("scenario").getAsString().equals(scenarioId)) {
                return entry.get("body_raw").getAsString();
            }
        }
        return fail("no body_raw for " + scenarioId);
    }

    private static String frozenBody(String scenarioId) throws Exception {
        for (MusicIntelligenceStrongerModelControl.FrozenRequest frozen
                : MusicIntelligenceStrongerModelControl.loadFrozenRequests(
                        MusicIntelligenceStrongerModelControl.FROZEN_REQUESTS_PATH)) {
            if (frozen.scenarioId.equals(scenarioId)) {
                return frozen.bodyRaw;
            }
        }
        return fail("no frozen request for " + scenarioId);
    }

    private static Stream<Arguments> argumentsThatMustNotStartALiveRun() {
        return Stream.of(
                Arguments.of("no arguments", new String[]{}),
                Arguments.of("run without confirmation", new String[]{"run"}),
                Arguments.of("confirmation without run", new String[]{"--confirm-12-billable-calls"}),
                Arguments.of("preview plus confirmation",
                        new String[]{"preview", "--confirm-12-billable-calls"}),
                Arguments.of("truncated confirmation", new String[]{"run", "--confirm"}),
                Arguments.of("near-miss confirmation", new String[]{"run", "--confirm-12-billable-call"}),
                Arguments.of("wrong case", new String[]{"run", "--CONFIRM-12-BILLABLE-CALLS"}),
                Arguments.of("padded confirmation", new String[]{"run", " --confirm-12-billable-calls"}),
                Arguments.of("trailing extra argument",
                        new String[]{"run", "--confirm-12-billable-calls", "again"}),
                Arguments.of("the evaluation runner's smoke mode", new String[]{"smoke"}),
                Arguments.of("the evaluation runner's final mode", new String[]{"final"})
        );
    }

    // Stands in for the Anthropic call. Counts invocations, because "no further call is made" is
    // the actual claim under test and a count is the only way to check it.
    private static final class CountingReplies
            implements MusicIntelligenceStrongerModelControl.ReplySource {
        int calls;

        @Override
        public String reply(String scenarioId, int iteration, String requestBody) {
            calls++;
            return "reply for " + scenarioId + " iteration " + iteration;
        }
    }

    // Replies whose text carries none of the words the blind packet must not contain.
    //
    // CountingReplies returns "reply for S1 iteration 1", which is useful for tracing writes but
    // useless for testing blinding: it would trip the assertions on its own text rather than on
    // the packet's scaffolding. The runner cannot police what a model writes, so the blinding
    // guarantee is about what the PACKET adds — and that is what these replies isolate.
    private static final class NeutralReplies
            implements MusicIntelligenceStrongerModelControl.ReplySource {
        int calls;

        @Override
        public String reply(String scenarioId, int iteration, String requestBody) {
            return "Reply text " + (++calls) + ".";
        }
    }

    // Fails the way the real wire fails: an exception, never a string that could be graded.
    private static final class FailingReplies
            implements MusicIntelligenceStrongerModelControl.ReplySource {
        int calls;

        @Override
        public String reply(String scenarioId, int iteration, String requestBody) throws Exception {
            calls++;
            throw new Exception("HTTP 529");
        }
    }

    private static final class CountingSink
            implements MusicIntelligenceStrongerModelControl.TranscriptSink {
        int writes;
        String last = "";

        @Override
        public void write(String content) {
            writes++;
            last = content;
        }
    }

    // Lets the header through, then fails — the "reply arrived but could not be saved" case.
    private static final class FailAfterFirstWrite
            implements MusicIntelligenceStrongerModelControl.TranscriptSink {
        int writes;

        @Override
        public void write(String content) throws Exception {
            if (++writes > 1) {
                throw new java.io.IOException("no space left on device");
            }
        }
    }

    private static JsonObject parse(String body) {
        assertNotNull(body, "a request body was missing");
        return JsonParser.parseString(body).getAsJsonObject();
    }
}
