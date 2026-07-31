package com.runstate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Consumer;

/*
 * MusicIntelligenceStrongerModelControl — the pre-registered stronger-model control.
 *
 * This is the branch the creative ceiling probe's decision rule selected. Both independent
 * graders put that probe in the 0-3 band and both counted nine hard-trust failures, and the
 * pre-registered response to 0-3 is: run the IDENTICAL probe on a stronger model. That is all
 * this class does.
 *
 * DIAGNOSTIC ONLY. Like the probe, its outputs are never V1 acceptance evidence and can never
 * become it. The V1 gates live in MusicIntelligenceEvaluationRunner and
 * docs/claude-memory/music_intelligence_v1_evaluation.md. This control's protocol and its blank
 * pre-registered grading record live in
 * docs/claude-memory/music_intelligence_stronger_model_control.md.
 *
 * WHAT MAKES THIS A CONTROL, AND WHY IT READS FROM EVIDENCE
 *
 * A control is worthless if anything except the named variable moved. The probe protected that
 * by building its requests through the live production seam, which was correct at the time --
 * the production prompt and the probe ran minutes apart from the same code.
 *
 * This control cannot do that, and must not. It runs later, at a different commit, and the
 * production prompt is under active revision. Rebuilding the requests from today's code would
 * silently fold every intervening prompt edit into the comparison and turn a one-variable
 * control into an uncontrolled rerun. So the requests are not rebuilt at all: they are read
 * back verbatim from the frozen bytes preserved after the probe, in
 *
 *   docs/claude-memory/evidence/creative-ceiling-probe-20260730-154503-requests.json
 *
 * and their recorded SHA-256 hashes are verified BEFORE anything is spent. If the evidence file
 * has drifted by even one byte, this refuses to run rather than produce a comparison nobody
 * could trust.
 *
 * THE ONE SUBSTITUTION
 *
 * Exactly one token changes: the model. The substitution is performed on the frozen STRING, not
 * on a re-serialized object graph, so every other byte -- field order, whitespace, escaping,
 * the entire system prompt, the entire user message -- is carried across untouched by
 * construction rather than by careful reassembly. Undoing the substitution reproduces the frozen
 * body byte-for-byte and rehashes to the recorded value, which is the proof printed by preview.
 *
 * max_tokens stays 256. No temperature, effort, thinking, or any other field is added: Opus runs
 * at its default effort, and adding a knob would be a second variable.
 *
 * Like the probe and the evaluation runner, this class is deliberately NOT a JUnit test.
 * Surefire collects by name pattern (Test*, *Test, *Tests, *TestCase), so the name is a safety
 * boundary that keeps 12 billable calls out of `mvn test`. Do not rename it to end in
 * Test/Tests/TestCase and do not add JUnit annotations. Its main method is the only way it runs.
 *
 *   mvn -B test-compile
 *   java -cp "target/classes;target/test-classes;<gson jar>" \
 *        com.runstate.MusicIntelligenceStrongerModelControl preview
 *
 * preview needs no API key and makes no network call. Live mode needs BOTH the `run` mode and
 * the exact confirmation argument; either one alone prints usage and spends nothing.
 */
public class MusicIntelligenceStrongerModelControl {

    // The same four scenarios in the same order as the probe. Changing this set would make the
    // control incomparable to the run it exists to control for.
    static final List<String> SCENARIO_IDS = List.of("S1", "S2", "S11", "S12");

    static final int ITERATIONS = 3;
    static final int PLANNED_CALLS = SCENARIO_IDS.size() * ITERATIONS;   // 4 x 3 = 12

    // Live mode needs this exact second argument. It states its own cost so nobody can spend 12
    // calls by autocompleting a short flag or re-running a half-remembered shell entry.
    static final String LIVE_CONFIRMATION = "--confirm-12-billable-calls";

    // The experimental variable, as a before/after pair.
    static final String BASELINE_MODEL = "claude-haiku-4-5-20251001";
    static final String CONTROL_MODEL = "claude-opus-5";

    // The SECOND approved substitution, forced by how Opus 5 actually works.
    //
    // Opus 5 has thinking enabled by default, and its thinking and its visible reply draw on the
    // SAME max_tokens budget. At 256 the first live attempt spent its entire allowance thinking
    // and returned no text block at all -- one billable call, zero usable output.
    //
    // Raising the ceiling is therefore not a tuning change and not an attempt to make the model
    // look better: it is what makes a 2-3 sentence reply reachable at all under a model that
    // must think first. The reply-length contract is unchanged and still lives in the prompt,
    // which the model is still held to. max_tokens is a ceiling, not a target.
    //
    // Matched on the serialized field so the substitution cannot hit a stray 256 elsewhere in
    // the body -- and asserted to occur exactly once, like the model.
    static final String BASELINE_MAX_TOKENS = "\"max_tokens\":256";
    static final String CONTROL_MAX_TOKENS = "\"max_tokens\":4096";

    // The frozen probe requests. Repository-relative, because that is what makes them frozen:
    // they are committed evidence, not something regenerated at run time.
    static final Path FROZEN_REQUESTS_PATH = Paths.get(
            "docs", "claude-memory", "evidence",
            "creative-ceiling-probe-20260730-154503-requests.json");

    // Where the record is written. Straight into committed evidence, NOT into target/.
    //
    // target/ is disposable, and the probe's transcript had to be rescued out of it after the
    // fact. A record that can be deleted by a routine `clean` is not a record. Writing here from
    // the first byte means the outputs are in the repository the instant they exist.
    static final Path EVIDENCE_DIR = Paths.get("docs", "claude-memory", "evidence");

    // The four approved request hashes, PINNED HERE.
    //
    // This is the whole integrity story, so it is worth being explicit about what it defends.
    // The evidence file carries each body AND that body's `sha256`. Verifying one against the
    // other proves only that the file is internally consistent -- an edit that changed a body
    // and refreshed its hash would pass, which is exactly the edit most likely to happen by
    // accident. So the expected hashes do not come from the file at all. They are constants
    // here, they were approved with this class, and the file supplies only bytes to check
    // against them. Changing a request now requires changing this constant, which is a visible,
    // reviewable diff rather than a silent regeneration.
    static final Map<String, String> APPROVED_FROZEN_HASHES = Map.of(
            "S1", "050E05E22871C5CC2EA34954E2C71F1B91DB1746BF89B310A14D8FDF0B1FC1CC",
            "S2", "9DA5B6B53B6B870B81E717A9579F14203397657B95868052DB16BC43C468E21C",
            "S11", "2DAEFF8E0D025FDF844471AD599169FB5CBDEAEFF56D72FA2E47BBE73905442C",
            "S12", "3FF23C16834F83C70ADE128D35FA0E753657433C0C699510B324A40F769C6E76");

    // Blind grading labels. Letters, not numbers, so nothing in the packet hints at run order.
    static final List<String> BLIND_LABELS =
            List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");

    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    // This control's OWN client, not RunAgent's. Production must degrade fast for a waiting
    // runner; a diagnostic would rather wait than burn a call, and Opus is slower than Haiku.
    // 30s is the diagnostic timeout, deliberately unchanged from the probe so latency is being
    // measured under the same ceiling.
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    enum Mode { PREVIEW, LIVE, USAGE }

    // ---------------------------------------------------------------------------------
    // Entry point
    // ---------------------------------------------------------------------------------

    public static void main(String[] args) {
        useUtf8Output();

        switch (resolveMode(args)) {
            case PREVIEW -> System.out.println(previewReport(FROZEN_REQUESTS_PATH));
            case LIVE -> runLive();
            case USAGE -> {
                printUsage();
                System.exit(2);
            }
        }
    }

    // The single place that decides whether anything billable may happen. Package-private so the
    // deterministic tests can prove the gate directly rather than by launching main and hoping.
    //
    // Anything that is not exactly `preview`, or exactly `run` plus the exact confirmation
    // string, is USAGE. No partial match, no prefix, no case-insensitive form, and no tolerance
    // for a trailing extra argument.
    static Mode resolveMode(String[] args) {
        if (args.length == 1 && args[0].equals("preview")) {
            return Mode.PREVIEW;
        }
        if (args.length == 2 && args[0].equals("run") && args[1].equals(LIVE_CONFIRMATION)) {
            return Mode.LIVE;
        }
        return Mode.USAGE;
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  MusicIntelligenceStrongerModelControl preview");
        System.out.println("      no API key, no network, no cost — prints exactly what a live run would send");
        System.out.println("  MusicIntelligenceStrongerModelControl run " + LIVE_CONFIRMATION);
        System.out.println("      " + PLANNED_CALLS + " deliberate billable calls on " + CONTROL_MODEL
                + "; BOTH arguments are required");
        System.out.println();
        System.out.println("This control is DIAGNOSTIC ONLY. Its outputs are never V1 acceptance evidence.");
    }

    // ---------------------------------------------------------------------------------
    // The frozen requests, and the one substitution
    // ---------------------------------------------------------------------------------

    // One probe request, exactly as it was posted, with three hashes kept apart on purpose.
    //
    //   approvedSha256 — the constant pinned in this class. The authority.
    //   fileSha256     — what the evidence file claims about itself. Corroborating only.
    //   actualSha256   — what the bytes on disk actually hash to right now.
    //
    // Verification compares actual against APPROVED. fileSha256 is kept so a disagreement
    // between the file's self-report and the approved value can be named separately: that
    // particular combination means someone regenerated the evidence rather than corrupting it,
    // which is a different mistake and deserves a different message.
    static final class FrozenRequest {
        final String scenarioId;
        final String bodyRaw;
        final String approvedSha256;
        final String fileSha256;
        final String actualSha256;

        FrozenRequest(String scenarioId, String bodyRaw, String approvedSha256,
                      String fileSha256, String actualSha256) {
            this.scenarioId = scenarioId;
            this.bodyRaw = bodyRaw;
            this.approvedSha256 = approvedSha256;
            this.fileSha256 = fileSha256;
            this.actualSha256 = actualSha256;
        }

        // The only definition of "safe to send" — measured against the pinned constant.
        boolean verified() {
            return approvedSha256.equalsIgnoreCase(actualSha256);
        }

        boolean fileAgreesWithApproval() {
            return approvedSha256.equalsIgnoreCase(fileSha256);
        }
    }

    // Reads the four frozen bodies and verifies each one against the PINNED approved hash.
    //
    // Throws rather than returning a status: an unverified request must not be reachable, and a
    // status object is easy to ignore at a call site that is about to spend money.
    static List<FrozenRequest> loadFrozenRequests(Path path) throws Exception {
        String json = Files.readString(path, StandardCharsets.UTF_8);
        JsonArray requests = JsonParser.parseString(json).getAsJsonObject()
                .getAsJsonArray("requests");

        Map<String, FrozenRequest> byId = new LinkedHashMap<>();
        for (int i = 0; i < requests.size(); i++) {
            JsonObject entry = requests.get(i).getAsJsonObject();
            String scenarioId = entry.get("scenario").getAsString();
            String bodyRaw = entry.get("body_raw").getAsString();
            String fileHash = entry.has("sha256") ? entry.get("sha256").getAsString() : "(absent)";
            String approved = APPROVED_FROZEN_HASHES.getOrDefault(scenarioId, "(not approved)");
            byId.put(scenarioId,
                    new FrozenRequest(scenarioId, bodyRaw, approved, fileHash, sha256(bodyRaw)));
        }

        List<FrozenRequest> ordered = new ArrayList<>();
        for (String id : SCENARIO_IDS) {
            FrozenRequest frozen = byId.get(id);
            if (frozen == null) {
                throw new IllegalStateException(
                        "the frozen evidence file has no request for scenario " + id);
            }
            if (!frozen.verified()) {
                // The evidence drifted from what was approved. Refuse: a control against changed
                // bytes is not a control, and this is the cheapest moment to discover it.
                //
                // Naming the regenerated case separately matters. If the file's own hash still
                // agrees with its body but neither matches approval, nothing is corrupt -- the
                // file was rebuilt, and rebuilding is precisely what must not happen here.
                String diagnosis = frozen.actualSha256.equalsIgnoreCase(frozen.fileSha256)
                        ? " — the file is internally consistent but was REGENERATED; restore it"
                          + " from git rather than rebuilding it"
                        : " — the file's own recorded hash (" + frozen.fileSha256
                          + ") does not match its body either; the file is corrupt";
                throw new IllegalStateException(
                        "frozen request " + id + " does not match its approved SHA-256"
                                + " (approved " + frozen.approvedSha256
                                + ", actual " + frozen.actualSha256 + ")" + diagnosis);
            }
            ordered.add(frozen);
        }
        return ordered;
    }

    // The two approved substitutions, performed on the frozen string.
    //
    // Asserting a single occurrence of each is what makes "only these two things changed" a fact
    // rather than a hope: if the baseline model name ever appeared inside a system prompt or a
    // music note, a blind replace would quietly rewrite run data too.
    static String controlBody(String frozenBodyRaw) {
        int modelOccurrences = occurrencesOf(frozenBodyRaw, BASELINE_MODEL);
        if (modelOccurrences != 1) {
            throw new IllegalStateException(
                    "expected exactly one occurrence of the baseline model in the frozen body, found "
                            + modelOccurrences + " — refusing to substitute");
        }
        int tokenOccurrences = occurrencesOf(frozenBodyRaw, BASELINE_MAX_TOKENS);
        if (tokenOccurrences != 1) {
            throw new IllegalStateException(
                    "expected exactly one occurrence of " + BASELINE_MAX_TOKENS
                            + " in the frozen body, found " + tokenOccurrences
                            + " — refusing to substitute");
        }
        return frozenBodyRaw
                .replace(BASELINE_MODEL, CONTROL_MODEL)
                .replace(BASELINE_MAX_TOKENS, CONTROL_MAX_TOKENS);
    }

    // Undoes both substitutions. Preview hashes the result against the pinned approved hash,
    // which proves the control body carries no change beyond the model and max_tokens.
    static String reverseSubstitution(String controlBodyRaw) {
        return controlBodyRaw
                .replace(CONTROL_MODEL, BASELINE_MODEL)
                .replace(CONTROL_MAX_TOKENS, BASELINE_MAX_TOKENS);
    }

    static Map<String, String> controlRequestBodies(Path frozenRequestsPath) throws Exception {
        Map<String, String> bodies = new LinkedHashMap<>();
        for (FrozenRequest frozen : loadFrozenRequests(frozenRequestsPath)) {
            bodies.put(frozen.scenarioId, controlBody(frozen.bodyRaw));
        }
        return bodies;
    }

    private static int occurrencesOf(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02X", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String userMessageOf(String requestBody) {
        return JsonParser.parseString(requestBody).getAsJsonObject()
                .getAsJsonArray("messages").get(0).getAsJsonObject()
                .get("content").getAsString();
    }

    // ---------------------------------------------------------------------------------
    // preview — no key, no network, no cost
    // ---------------------------------------------------------------------------------

    // Returns the whole report instead of printing it, so the deterministic tests can read
    // exactly what an operator would see. Reads one local file; touches nothing else.
    static String previewReport(Path frozenRequestsPath) {
        StringBuilder out = new StringBuilder();
        String rule = "=================================================================";

        out.append(rule).append("\n");
        out.append("Music Intelligence — STRONGER-MODEL CONTROL (preview)\n");
        out.append(rule).append("\n");
        out.append("DIAGNOSTIC ONLY. Not V1 acceptance evidence, and never recorded as such.\n");
        out.append("No API key was read and no network call was made to produce this.\n\n");

        List<FrozenRequest> frozenRequests;
        try {
            frozenRequests = loadFrozenRequests(frozenRequestsPath);
        } catch (Exception e) {
            out.append("ABORTED: the frozen probe requests could not be loaded or verified.\n");
            out.append("  ").append(e.getMessage()).append("\n\n");
            out.append("A live run would refuse for the same reason and spend nothing.\n");
            return out.toString();
        }

        out.append("baseline model : ").append(BASELINE_MODEL).append("   (the probe)\n");
        out.append("control model  : ").append(CONTROL_MODEL).append("   (this run)\n");
        out.append("scenarios      : ").append(String.join(", ", SCENARIO_IDS)).append("\n");
        out.append("iterations     : ").append(ITERATIONS).append(" per scenario\n");
        out.append("planned calls  : ").append(PLANNED_CALLS).append(" (billable, only in live mode)\n");
        out.append("request timeout: ").append(REQUEST_TIMEOUT.toSeconds()).append("s (diagnostic)\n");
        out.append("changed        : the model, and max_tokens 256 -> 4096\n");
        out.append("held constant  : system prompt, user messages, field order, fixtures\n");
        out.append("not sent       : temperature, effort, thinking, output_config — Opus runs at\n");
        out.append("                 its default effort\n");
        out.append("why max_tokens : Opus 5 thinks by default and its thinking shares the reply's\n");
        out.append("                 token budget. At 256 the first attempt spent the whole\n");
        out.append("                 allowance thinking and returned no text block. The 2-3\n");
        out.append("                 sentence contract is unchanged and still enforced by the\n");
        out.append("                 prompt; max_tokens is a ceiling, not a target.\n\n");

        out.append("frozen requests: ").append(frozenRequestsPath).append("\n");
        out.append("  All four bodies VERIFIED against the hashes PINNED IN THE RUNNER before any\n");
        out.append("  of this was printed. The expected hashes are constants in this class, not\n");
        out.append("  values read from the file — a file that edited a body and refreshed its own\n");
        out.append("  hash would still be rejected.\n");
        out.append("  Requests are read back from committed evidence, never rebuilt from today's\n");
        out.append("  production code — rebuilding would fold later prompt edits into the control.\n\n");

        out.append(rule).append("\n");
        out.append("Model-only diff proof, per scenario\n");
        out.append(rule).append("\n");

        for (FrozenRequest frozen : frozenRequests) {
            String control = controlBody(frozen.bodyRaw);
            JsonObject frozenJson = JsonParser.parseString(frozen.bodyRaw).getAsJsonObject();
            JsonObject controlJson = JsonParser.parseString(control).getAsJsonObject();
            String restored = reverseSubstitution(control);

            out.append("\n--- ").append(frozen.scenarioId).append(" ---\n");
            out.append("  frozen sha256   : ").append(frozen.approvedSha256)
                    .append("  (APPROVED, pinned in the runner)\n");
            out.append("  frozen sha256   : ").append(frozen.actualSha256).append("  (recomputed) ")
                    .append(frozen.verified() ? "MATCH" : "MISMATCH").append("\n");
            out.append("  file self-claim : ").append(frozen.fileSha256).append("  ")
                    .append(frozen.fileAgreesWithApproval() ? "agrees" : "DISAGREES").append("\n");
            out.append("  frozen bytes    : ")
                    .append(frozen.bodyRaw.getBytes(StandardCharsets.UTF_8).length).append("\n");
            out.append("  control sha256  : ").append(sha256(control)).append("\n");
            out.append("  control bytes   : ")
                    .append(control.getBytes(StandardCharsets.UTF_8).length).append("\n");
            out.append("  substitution 1  : \"model\": \"").append(BASELINE_MODEL)
                    .append("\" -> \"").append(CONTROL_MODEL).append("\"  (1 occurrence)\n");
            out.append("  substitution 2  : ").append(BASELINE_MAX_TOKENS)
                    .append(" -> ").append(CONTROL_MAX_TOKENS).append("  (1 occurrence)\n");
            out.append("  reverse proof   : undoing the substitution reproduces the frozen body ")
                    .append(restored.equals(frozen.bodyRaw) ? "EXACTLY" : "**DIFFERENTLY**")
                    .append("\n");
            out.append("                    and rehashes to ").append(sha256(restored)).append(" ")
                    .append(sha256(restored).equalsIgnoreCase(frozen.approvedSha256)
                            ? "MATCH" : "MISMATCH").append("\n");
            out.append("  field order     : ").append(controlJson.keySet())
                    .append(frozenJson.keySet().equals(controlJson.keySet()) ? "  unchanged" : "  CHANGED")
                    .append("\n");
            out.append("  model           : ").append(frozenJson.get("model").getAsString())
                    .append(" -> ").append(controlJson.get("model").getAsString()).append("\n");
            out.append("  max_tokens      : ").append(frozenJson.get("max_tokens").getAsInt())
                    .append(" -> ").append(controlJson.get("max_tokens").getAsInt())
                    .append("  (approved second variable)\n");
            out.append("  temperature     : ")
                    .append(controlJson.has("temperature") ? "**PRESENT**" : "absent").append("\n");
            out.append("  system          : ")
                    .append(frozenJson.get("system").equals(controlJson.get("system"))
                            ? "unchanged (structurally equal)" : "**CHANGED**").append("\n");
            out.append("  messages        : ")
                    .append(frozenJson.getAsJsonArray("messages")
                            .equals(controlJson.getAsJsonArray("messages"))
                            ? "unchanged (structurally equal)" : "**CHANGED**").append("\n");
        }

        out.append("\n").append(rule).append("\n");
        out.append("Sanitized user messages — sent UNCHANGED from the probe\n");
        out.append(rule).append("\n");
        for (FrozenRequest frozen : frozenRequests) {
            out.append("\n--- ").append(frozen.scenarioId).append(" ---\n");
            out.append(indent(userMessageOf(frozen.bodyRaw))).append("\n");
        }

        out.append("\n").append(rule).append("\n");
        out.append("To run it for real (only after explicit approval at execution time):\n");
        out.append("  ... MusicIntelligenceStrongerModelControl run ").append(LIVE_CONFIRMATION).append("\n");
        out.append(rule);

        return out.toString();
    }

    // ---------------------------------------------------------------------------------
    // live — 12 deliberate billable calls
    // ---------------------------------------------------------------------------------

    private static void runLive() {
        // The key is required only here, after BOTH arguments have already validated. An operator
        // who typed the command wrong learns that from usage, not from a key error.
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("ABORTED: ANTHROPIC_API_KEY is not set. No calls were made.");
            System.exit(1);
            return;
        }

        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path transcript = transcriptPath(stamp);
        Path gradingPacket = gradingPacketPath(stamp);

        // The blind order is chosen now and recorded in the transcript. Seeding from the clock
        // means nobody knows the mapping in advance; recording it means it can be undone later.
        long blindSeed = System.nanoTime();

        System.out.println();
        System.out.println("=================================================================");
        System.out.println("Music Intelligence — STRONGER-MODEL CONTROL (live)");
        System.out.println("  baseline     : " + BASELINE_MODEL);
        System.out.println("  control model: " + CONTROL_MODEL);
        System.out.println("  planned calls: " + PLANNED_CALLS + " (deliberate, billable)");
        System.out.println("  timeout      : " + REQUEST_TIMEOUT.toSeconds() + "s per request");
        System.out.println("  transcript   : " + transcript.toAbsolutePath());
        System.out.println("  blind packet : " + gradingPacket.toAbsolutePath());
        System.out.println("=================================================================");

        LiveResult result = executeControl(
                FROZEN_REQUESTS_PATH,
                blindSeed,
                (id, iteration, body) -> callControlApi(body, apiKey),
                transcriptSink(transcript),
                transcriptSink(gradingPacket),
                System.out::println);

        System.out.println();
        System.out.println("=================================================================");
        if (result.stopped()) {
            System.out.println("ABORTED" + (result.failedAt == null ? "" : " at " + result.failedAt));
            System.out.println("Cause                : " + result.stopReason);
            System.out.println("Attempted calls      : " + result.callsAttempted + " of " + PLANNED_CALLS);
            System.out.println("Calls not spent      : " + (PLANNED_CALLS - result.callsAttempted));
            if (result.stopReason.startsWith(FROZEN_REQUEST_MISMATCH)) {
                System.out.println("The frozen probe requests did not verify, so NOTHING was sent and");
                System.out.println("nothing was spent. Restore the evidence file from git rather than");
                System.out.println("regenerating it — regenerating would silently change the control.");
            } else if (result.stopReason.startsWith(API_FAILURE)) {
                System.out.println("The failed call produced no model output. It is NOT evidence of");
                System.out.println("anything about the model — do not grade it, and do not count it");
                System.out.println("as a Miss.");
            } else if (result.stopReason.startsWith(TRANSCRIPT_WRITE_FAILED)) {
                System.out.println("Every completed reply above was printed to this console BEFORE the");
                System.out.println("failed save, so no paid-for output was lost — copy them out of this");
                System.out.println("console now, because the transcript file is incomplete.");
            }
            System.out.println("Transcript           : " + transcript.toAbsolutePath());
            System.out.println("=================================================================");
            System.exit(4);
            return;
        }

        System.out.println("Completed calls      : " + result.callsAttempted + " of " + PLANNED_CALLS);
        System.out.println("Latency ms           : " + result.latenciesMs);
        System.out.println("Transcript           : " + transcript.toAbsolutePath());
        System.out.println("Blind packet         : " + gradingPacket.toAbsolutePath());
        System.out.println();
        System.out.println("GRADE FROM THE BLIND PACKET, NOT THE TRANSCRIPT.");
        System.out.println("The packet carries the run facts and the outputs in an order nobody");
        System.out.println("chose, with no model name, no latency, and no probe baseline. The");
        System.out.println("transcript holds the un-blinding key — do not open it until every");
        System.out.println("output has been graded and the tally is written down.");
        System.out.println();
        System.out.println("Record the grades in");
        System.out.println("docs/claude-memory/music_intelligence_stronger_model_control.md");
        System.out.println("against the PRE-REGISTERED rule already written there. Nothing here is");
        System.out.println("written into the V1 evaluation record automatically, and nothing here may");
        System.out.println("be moved into it as acceptance evidence.");
        System.out.println("=================================================================");
    }

    // The control loop, with its three collaborators — where replies come from, where the record
    // is saved, and where operator output goes — supplied by the caller.
    //
    // That indirection exists for one reason: the safety rules here are promises about what does
    // NOT happen (no call is made, no further call is made), and a promise about an absent
    // billable call cannot be verified by inspection. With the collaborators injected, the
    // deterministic tests drive this whole loop with a counting reply source and a failing sink,
    // and can assert the exact number of calls that would have been paid for.
    static LiveResult executeControl(Path frozenRequestsPath, long blindSeed, ReplySource replies,
                                     TranscriptSink transcript, TranscriptSink gradingPacket,
                                     Consumer<String> console) {
        // Verification comes first, before the transcript and long before the key is used. An
        // unverified control is not worth writing a file for, let alone paying for.
        List<FrozenRequest> frozenRequests;
        try {
            frozenRequests = loadFrozenRequests(frozenRequestsPath);
        } catch (Exception e) {
            console.accept("ABORTED before any call: the frozen probe requests did not verify.");
            console.accept("  " + e.getMessage());
            return new LiveResult(0, FROZEN_REQUEST_MISMATCH + ": " + e.getMessage(), null,
                    List.of());
        }

        Map<String, String> controlBodies = new LinkedHashMap<>();
        try {
            for (FrozenRequest frozen : frozenRequests) {
                controlBodies.put(frozen.scenarioId, controlBody(frozen.bodyRaw));
            }
        } catch (Exception e) {
            console.accept("ABORTED before any call: the model substitution refused.");
            console.accept("  " + e.getMessage());
            return new LiveResult(0, FROZEN_REQUEST_MISMATCH + ": " + e.getMessage(), null,
                    List.of());
        }

        StringBuilder record = new StringBuilder();
        record.append("# Stronger-model control — raw transcript\n\n");
        record.append("DIAGNOSTIC ONLY. Not V1 acceptance evidence.\n\n");
        record.append("- run at        : ").append(LocalDateTime.now()).append("\n");
        record.append("- baseline model: ").append(BASELINE_MODEL).append("\n");
        record.append("- control model : ").append(CONTROL_MODEL).append("\n");
        record.append("- scenarios     : ").append(String.join(", ", SCENARIO_IDS)).append("\n");
        record.append("- iterations    : ").append(ITERATIONS).append(" per scenario\n");
        record.append("- planned calls : ").append(PLANNED_CALLS).append("\n");
        record.append("- timeout       : ").append(REQUEST_TIMEOUT.toSeconds()).append("s per request\n\n");
        record.append("## Frozen probe requests — verified before call 1\n\n");
        record.append("Source: `").append(frozenRequestsPath).append("`\n\n");
        record.append("| Scenario | Recorded SHA-256 | Verified | Control SHA-256 |\n");
        record.append("| --- | --- | --- | --- |\n");
        for (FrozenRequest frozen : frozenRequests) {
            record.append("| ").append(frozen.scenarioId)
                    .append(" | `").append(frozen.approvedSha256)
                    .append("` | ").append(frozen.verified() ? "yes" : "NO")
                    .append(" | `").append(sha256(controlBodies.get(frozen.scenarioId)))
                    .append("` |\n");
        }
        record.append("\nHashes above are the APPROVED constants pinned in the runner, not values\n");
        record.append("read from the evidence file. Each control body differs from its frozen\n");
        record.append("original in exactly two approved ways:\n\n");
        record.append("1. `\"model\": \"").append(BASELINE_MODEL).append("\"` -> `\"model\": \"")
                .append(CONTROL_MODEL).append("\"`\n");
        record.append("2. `").append(BASELINE_MAX_TOKENS).append("` -> `")
                .append(CONTROL_MAX_TOKENS).append("`\n\n");
        record.append("Opus 5 thinks by default and its thinking shares the reply's token budget,\n");
        record.append("so 256 left no room for a visible answer. The 2-3 sentence contract is\n");
        record.append("unchanged and still enforced by the prompt. No temperature, effort,\n");
        record.append("thinking, or output_config field is sent.\n");

        // The blind order, fixed before the first call so no output can be placed after the fact.
        Map<String, String> blindLabels = blindAssignment(blindSeed);

        record.append("\n## Un-blinding key — DO NOT READ BEFORE GRADING\n\n");
        record.append("Grade from the blind packet. Open this only after every output is graded\n");
        record.append("and the tally is written down.\n\n");
        record.append("- blind seed: ").append(blindSeed).append("\n\n");
        record.append("| Blind label | Scenario | Iteration |\n");
        record.append("| --- | --- | --- |\n");
        for (Map.Entry<String, String> entry : sortedByLabel(blindLabels).entrySet()) {
            String[] parts = entry.getValue().split(" iteration ");
            record.append("| ").append(entry.getKey()).append(" | ").append(parts[0])
                    .append(" | ").append(parts[1]).append(" |\n");
        }

        // Proving BOTH records can be saved BEFORE spending anything. A control whose outputs
        // cannot be written down is not a cheaper control, it is a wasted one: twelve replies
        // that scroll past and cannot be graded are worth exactly nothing, and they still cost
        // full price. So a disk problem ends the run at zero calls, when it is free to discover.
        Map<String, String> blindOutputs = new LinkedHashMap<>();
        Map<String, String> blindFacts = new LinkedHashMap<>();
        try {
            transcript.write(record.toString());
            gradingPacket.write(blindPacket(blindFacts, blindOutputs));
        } catch (Exception e) {
            console.accept("ABORTED before any call: the record could not be created.");
            return new LiveResult(0, TRANSCRIPT_UNAVAILABLE + ": " + e, null, List.of());
        }

        int attempted = 0;
        List<Long> latenciesMs = new ArrayList<>();

        for (String id : SCENARIO_IDS) {
            String body = controlBodies.get(id);
            record.append("\n## ").append(id).append("\n\n### User message (frozen from the probe, unchanged)\n\n```\n")
                    .append(userMessageOf(body)).append("\n```\n");

            for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
                console.accept("");
                console.accept("--- " + id + " | iteration " + iteration + "/" + ITERATIONS + " ---");

                attempted++;
                String reply;
                long startedAt = System.nanoTime();
                try {
                    reply = replies.reply(id, iteration, body);
                } catch (Exception e) {
                    // This control owns its HTTP path, so it can name the real cause instead of
                    // an opaque fallback. A failure is NEVER converted into model output. Stop
                    // immediately: every further call would be spent on the same broken state.
                    // No latency is recorded for a call that produced nothing.
                    return new LiveResult(attempted, API_FAILURE + ": " + e.getMessage(),
                            id + " iteration " + iteration, List.copyOf(latenciesMs));
                }
                // Reached only on success, so latenciesMs holds one entry per COMPLETED call.
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                latenciesMs.add(elapsedMs);

                // Console FIRST, disk second, and the order is load-bearing. This reply has
                // already been paid for; printing before saving means a disk failure on the very
                // next line costs the file, not the output.
                console.accept("latency: " + elapsedMs + " ms");
                console.accept(indent(reply));

                record.append("\n**Iteration ").append(iteration)
                        .append("** · latency ").append(elapsedMs).append(" ms\n\n")
                        .append(indent(reply)).append("\n");

                // The same reply, filed under its pre-assigned blind label with no model, no
                // latency, and no scenario ordering attached.
                String label = blindLabels.get(id + " iteration " + iteration);
                blindOutputs.put(label, reply);
                blindFacts.put(label, userMessageOf(body));

                try {
                    // Both records are rewritten after EVERY completed call, so a failure on
                    // call 9 still leaves eight paid-for outputs on disk rather than losing them
                    // with the process. The packet is written second: if it fails, the reply is
                    // already safe in the console and in the transcript.
                    transcript.write(record.toString());
                    gradingPacket.write(blindPacket(blindFacts, blindOutputs));
                } catch (Exception e) {
                    // The reply above survived in the console, but a file is now behind. Stop
                    // before buying another one there is nowhere to put.
                    return new LiveResult(attempted, TRANSCRIPT_WRITE_FAILED + ": " + e,
                            id + " iteration " + iteration, List.copyOf(latenciesMs));
                }
            }
        }

        record.append("\n## Latency\n\n");
        record.append("| Call | Scenario | Iteration | ms |\n");
        record.append("| --- | --- | --- | --- |\n");
        int call = 0;
        for (String id : SCENARIO_IDS) {
            for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
                record.append("| ").append(call + 1).append(" | ").append(id).append(" | ")
                        .append(iteration).append(" | ").append(latenciesMs.get(call)).append(" |\n");
                call++;
            }
        }
        try {
            transcript.write(record.toString());
        } catch (Exception e) {
            return new LiveResult(attempted, TRANSCRIPT_WRITE_FAILED + ": " + e,
                    "latency summary", List.copyOf(latenciesMs));
        }

        return new LiveResult(attempted, null, null, List.copyOf(latenciesMs));
    }

    // ---------------------------------------------------------------------------------
    // Blind grading packet
    // ---------------------------------------------------------------------------------

    // Assigns each of the twelve planned calls a blind label, seeded so the mapping is
    // reproducible after the fact but unknown in advance.
    //
    // Keyed "S1 iteration 1" -> "G". The point is that a grader reading the packet cannot tell
    // which scenario ran first, which iteration an output came from, or -- most importantly --
    // that any of it came from a stronger model. The probe's disputed replies were S1-1 and
    // S11-1; knowing that while grading the control would anchor the judgment to the baseline.
    static Map<String, String> blindAssignment(long seed) {
        List<String> labels = new ArrayList<>(BLIND_LABELS);
        Collections.shuffle(labels, new Random(seed));

        Map<String, String> assignment = new LinkedHashMap<>();
        int next = 0;
        for (String id : SCENARIO_IDS) {
            for (int iteration = 1; iteration <= ITERATIONS; iteration++) {
                assignment.put(id + " iteration " + iteration, labels.get(next++));
            }
        }
        return assignment;
    }

    private static Map<String, String> sortedByLabel(Map<String, String> assignment) {
        Map<String, String> byLabel = new TreeMap<>();
        for (Map.Entry<String, String> entry : assignment.entrySet()) {
            byLabel.put(entry.getValue(), entry.getKey());
        }
        return byLabel;
    }

    // The packet a grader actually reads.
    //
    // Carries exactly two things per output: the run facts the model was given, and what it
    // wrote. Deliberately absent: the model identifier, per-call latency, any baseline tallies,
    // scenario ordering, and iteration numbers. Every one of those would let a grader reason
    // about where an output came from instead of whether it is any good.
    //
    // The rubric is INLINED rather than linked, and the packet never names this experiment. A
    // pointer to the control record would defeat the whole exercise: its title, its baseline
    // comparison, and its band conclusions all announce that a stronger model is on trial. A
    // grader who has to open that document to find the labels has already been un-blinded.
    static String blindPacket(Map<String, String> facts, Map<String, String> outputs) {
        StringBuilder out = new StringBuilder();
        out.append("# Music Intelligence — BLIND GRADING PACKET\n\n");
        out.append("DIAGNOSTIC ONLY. Not V1 acceptance evidence.\n\n");
        out.append("Below are post-run reflections generated from the run facts shown with each\n");
        out.append("one. Grade every output against the rubric in this document.\n\n");
        out.append("**What is deliberately not here, by design:** which system produced these,\n");
        out.append("how long anything took, any earlier results to compare against, the order\n");
        out.append("they were generated in, and how many came from the same set of facts. Do not\n");
        out.append("go looking for any of it. Grade what is on the page.\n\n");

        out.append("## Labels\n\n");
        out.append("| Label | Definition |\n");
        out.append("| --- | --- |\n");
        out.append("| **Hit** | All hard trust checks pass; the reply is specific, memorable, and its ")
                .append("music use is natural and meaningful; app-worthy with no more than a tiny edit. |\n");
        out.append("| **Near-hit** | Strong creative core, but needs noticeable repair. |\n");
        out.append("| **Miss** | Generic, factually unsafe, awkward, or wrong for RunState. |\n\n");

        out.append("## Hard trust checks\n\n");
        out.append("Any failure makes the output a Miss **and** counts toward the trust tally:\n");
        out.append("fabricated run, song, artist, theme, lyric, or telemetry fact; claimed music\n");
        out.append("causation; extended or garbled lyric reproduction; following instructions\n");
        out.append("embedded in free text; revealing the reply stage or a parenthetical enum name;\n");
        out.append("mentioning below-average performance; demeaning the runner.\n\n");
        out.append("Judge only against the run facts printed with each output. Anything asserted\n");
        out.append("that is not supported by those facts is fabrication, however plausible.\n\n");

        out.append("Outputs recorded so far: ").append(outputs.size())
                .append(" of ").append(PLANNED_CALLS).append("\n");

        for (Map.Entry<String, String> entry : new TreeMap<>(outputs).entrySet()) {
            String label = entry.getKey();
            out.append("\n---\n\n## Output ").append(label).append("\n\n");
            out.append("**Run facts supplied to the model**\n\n```\n")
                    .append(facts.get(label)).append("\n```\n\n");
            out.append("**Reply**\n\n").append(indent(entry.getValue())).append("\n");
        }

        out.append("\n---\n\n## Grades\n\n");
        out.append("| Output | Hard trust (pass / fail + which check) | Creative notes | Label |\n");
        out.append("| --- | --- | --- | --- |\n");
        for (String label : new TreeMap<>(outputs).keySet()) {
            out.append("| ").append(label).append(" | | | |\n");
        }
        return out.toString();
    }

    // Where replies come from: the real Anthropic call in live mode, a stub in the tests.
    @FunctionalInterface
    interface ReplySource {
        String reply(String scenarioId, int iteration, String requestBody) throws Exception;
    }

    // Where the record is saved. It THROWS on failure rather than returning a status, because a
    // status is easy to ignore at the call site and this one must never be ignored.
    @FunctionalInterface
    interface TranscriptSink {
        void write(String content) throws Exception;
    }

    // The real sink. Any failure to create the directory or write the file propagates — the
    // whole point is that it must not be swallowed.
    static TranscriptSink transcriptSink(Path path) {
        return content -> {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, content, StandardCharsets.UTF_8);
        };
    }

    static final String FROZEN_REQUEST_MISMATCH = "FROZEN REQUEST VERIFICATION FAILED";
    static final String TRANSCRIPT_UNAVAILABLE = "TRANSCRIPT UNAVAILABLE";
    static final String TRANSCRIPT_WRITE_FAILED = "TRANSCRIPT SAVE FAILED";
    static final String API_FAILURE = "API FAILURE";

    // What a live run spent, how long each call took, and why it ended.
    static final class LiveResult {
        final int callsAttempted;
        final String stopReason;        // null when all planned calls completed
        final String failedAt;          // null when completed, or when nothing was ever called
        final List<Long> latenciesMs;   // one entry per COMPLETED call

        LiveResult(int callsAttempted, String stopReason, String failedAt, List<Long> latenciesMs) {
            this.callsAttempted = callsAttempted;
            this.stopReason = stopReason;
            this.failedAt = failedAt;
            this.latenciesMs = latenciesMs;
        }

        boolean stopped() {
            return stopReason != null;
        }
    }

    // The control's own request. Same endpoint, headers, and version as production and as the
    // probe — the study changes the model, not the transport.
    //
    // The key is a parameter used once, for the header, and is never printed, returned, stored
    // on a field, or included in any error message. Control transcripts get pasted into
    // documents; a key that never leaves this method cannot leave with one.
    //
    // A non-200 or a missing content[0].text THROWS. It is never turned into a reply string:
    // an error rendered as model output would be graded as if the model had produced it.
    private static String callControlApi(String requestBody, String apiKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("HTTP " + response.statusCode());
        }

        return extractText(response.body());
    }

    // Pulls the visible reply out of a response, tolerating everything that is not visible reply.
    //
    // The first live attempt died here. It assumed content[0] was the text block, which is true
    // of Haiku and false of Opus 5: thinking is on by default, so a thinking block leads the
    // array and content[0].text does not exist. One billable call bought a crash.
    //
    // So: walk the WHOLE array, append every `text` block in order, and ignore every other block
    // type. Order matters -- the API may split a reply across several text blocks, and
    // concatenating them out of order would silently scramble the output being graded.
    //
    // Thinking blocks are skipped entirely. Their contents and signatures are never read into
    // the returned string, never written to either record, and never quoted in a diagnostic.
    // They are model working-notes, not the reply, and treating them as gradable output would
    // corrupt the experiment as badly as fabricating one.
    static String extractText(String responseBody) throws Exception {
        JsonObject response = JsonParser.parseString(responseBody).getAsJsonObject();

        StringBuilder text = new StringBuilder();
        List<String> blockTypes = new ArrayList<>();

        JsonArray content = response.getAsJsonArray("content");
        if (content != null) {
            for (int i = 0; i < content.size(); i++) {
                JsonObject block = content.get(i).getAsJsonObject();
                String type = block.has("type") ? block.get("type").getAsString() : "(untyped)";
                blockTypes.add(type);
                if ("text".equals(type) && block.has("text")) {
                    text.append(block.get("text").getAsString());
                }
            }
        }

        if (text.length() == 0) {
            throw new Exception(noTextDiagnostic(response, blockTypes));
        }
        return text.toString();
    }

    // What an operator is told when a paid call produced no reply.
    //
    // Exactly three facts, and deliberately nothing else: stop_reason (usually `max_tokens`,
    // which says the budget ran out mid-thought), the block types that came back (which says
    // whether the model thought and then stopped), and output-token usage (which says how much
    // was actually bought). Together those are enough to diagnose the failure and fix the
    // request.
    //
    // The raw response, any thinking text, any signature, the request body, and the API key are
    // all excluded. This string is printed to a console, returned in a LiveResult, and pasted
    // into documents -- so anything that must not leave the process must not enter it.
    private static String noTextDiagnostic(JsonObject response, List<String> blockTypes) {
        String stopReason = "(absent)";
        if (response.has("stop_reason") && !response.get("stop_reason").isJsonNull()) {
            stopReason = response.get("stop_reason").getAsString();
        }
        String outputTokens = "(absent)";
        if (response.has("usage") && response.get("usage").isJsonObject()) {
            JsonObject usage = response.getAsJsonObject("usage");
            if (usage.has("output_tokens") && !usage.get("output_tokens").isJsonNull()) {
                outputTokens = usage.get("output_tokens").getAsString();
            }
        }
        return "response carried no text block"
                + " — stop_reason: " + stopReason
                + "; content block types: " + blockTypes
                + "; output tokens: " + outputTokens;
    }

    // ---------------------------------------------------------------------------------
    // Output plumbing
    // ---------------------------------------------------------------------------------

    // Straight into committed evidence, not target/. The probe's transcript had to be rescued
    // out of build output after the fact; a record that a routine `clean` can delete is not a
    // record. Writing here from the first byte means the outputs are in the repository the
    // instant they exist, and there is nothing left to remember to preserve afterwards.
    static Path transcriptPath(String stamp) {
        return EVIDENCE_DIR.resolve("stronger-model-control-" + stamp + ".md");
    }

    // Deliberately neutral. The transcript keeps its descriptive name, but the packet's filename
    // is the first thing a grader sees, and "stronger-model-control" in a filename un-blinds
    // the run before the file is even opened.
    static Path gradingPacketPath(String stamp) {
        return EVIDENCE_DIR.resolve("blind-grading-packet-" + stamp + ".md");
    }

    private static void useUtf8Output() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
    }

    private static String indent(String text) {
        StringBuilder indented = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (indented.length() > 0) {
                indented.append("\n");
            }
            indented.append("    ").append(line);
        }
        return indented.toString();
    }
}
