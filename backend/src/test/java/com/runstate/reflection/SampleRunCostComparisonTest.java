package com.runstate.reflection;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleRunCostComparisonTest {
    private static final String VOICE = "RunStateUsage elevenlabs voice=reflection ";
    private static final String TOKENS = "input=8 output=2 cache_read=1 ";
    private static final String CACHED = TOKENS + "cache_write_total=5 cache_write_5m=2 cache_write_1h=3";
    private static final String UNCACHED = TOKENS + "cache_write_total=0 cache_write_5m=unknown cache_write_1h=unknown";

    @Test void groupsInterleavedCallsByModelAndStepAndSeparatesSubmittedFromBilledVoiceUnits() {
        String first = "RunStateUsage anthropic step=STUDY model=first ";
        String second = "RunStateUsage anthropic step=STUDY model=second ";
        String writer = "RunStateUsage anthropic step=WRITER model=first ";
        String result = report(true, first + "attempted=1", second + "attempted=1", writer + "attempted=1",
                second + "reply=1 " + UNCACHED, first + "reply=1 " + CACHED, writer + "reply=1 " + CACHED,
                first + "attempted=1", first + "reply=1 " + CACHED,
                VOICE + "attempted=1 submitted_chars=84", VOICE + "reply=1 usable_audio=true provider_character_cost=42",
                "RunStateUsage musixmatch attempted=1");

        assertTrue(result.contains("STUDY model=first sends=2 replies=2 usage=complete input=16 output=4 "
                + "cache_read=2 cache_write_total=10 cache_write_5m=4 cache_write_1h=6"));
        assertTrue(result.contains("STUDY model=second sends=1 replies=1 usage=complete input=8 output=2 "
                + "cache_read=1 cache_write_total=0 cache_write_5m=0 cache_write_1h=0"));
        assertTrue(result.contains("WRITER model=first sends=1 replies=1 usage=complete input=8"));
        assertTrue(result.contains("Musixmatch requests attempted=1"));
        assertTrue(result.contains("Reflection voice sends=1 replies=1 submitted_characters=84 provider_character_cost=42"));
        assertTrue(result.contains("Usage for dispatched requests is complete"));
    }

    @Test void oneMissingModelOrVoiceReplyMakesTheFullTotalUnknown() {
        String study = "RunStateUsage anthropic step=STUDY model=first ";
        String result = report(true, study + "attempted=1", study + "reply=1 " + CACHED, study + "attempted=1",
                VOICE + "attempted=1 submitted_chars=8", VOICE + "reply=1 usable_audio=true provider_character_cost=4",
                VOICE + "attempted=1 submitted_chars=8");

        assertTrue(result.contains("sends=2 replies=1 usage=incomplete input=unknown output=unknown"));
        assertTrue(result.contains("sends=2 replies=1 submitted_characters=16 provider_character_cost=unknown"));
        assertTrue(result.contains("Usage is incomplete"));
    }

    @Test void positiveCacheWritesNeedConsistentWindowsForEachReply() {
        String study = "RunStateUsage anthropic step=STUDY model=first ";
        String result = report(true, study + "attempted=1", study + "attempted=1",
                study + "reply=1 " + TOKENS + "cache_write_total=6 cache_write_5m=2 cache_write_1h=3",
                study + "reply=1 " + TOKENS + "cache_write_total=4 cache_write_5m=2 cache_write_1h=3");
        assertTrue(result.contains("usage=incomplete input=16 output=4 cache_read=2 "
                + "cache_write_total=10 cache_write_5m=unknown cache_write_1h=unknown"));

        String missing = report(true, study + "attempted=1", study + "reply=1 " + TOKENS + "cache_write_total=4");
        assertTrue(missing.contains("cache_write_total=4 cache_write_5m=unknown cache_write_1h=unknown"));
    }

    @Test void unknownVoiceUnitsAndUnidentifiedModelRemainIncomplete() {
        String custom = "RunStateUsage anthropic step=WRITER model=custom ";
        String result = report(true, custom + "attempted=1", custom + "reply=1 " + CACHED,
                VOICE + "attempted=1 submitted_chars=8", VOICE + "reply=1 usable_audio=true provider_character_cost=unknown");

        assertTrue(result.contains("model=custom sends=1 replies=1 usage=incomplete input=8"));
        assertTrue(result.contains("submitted_characters=8 provider_character_cost=unknown"));
        assertTrue(result.contains("Usage is incomplete"));
    }

    @Test void chargesAreExplicitlySimulatedOfflineAndZeroSendsNeedNoBillingReceipt() {
        String offline = report(false, VOICE + "attempted=1 submitted_chars=8");
        assertTrue(offline.contains("Simulated providers. No provider charges were incurred."));
        assertFalse(offline.contains("provider_character_cost="));
        assertFalse(offline.contains("Usage for dispatched requests is complete"));

        String noCalls = report(true);
        assertTrue(noCalls.contains("Reflection voice sends=0 replies=0 submitted_characters=0 provider_character_cost=0"));
    }

    private static String report(boolean live, String... events) {
        var output = new ByteArrayOutputStream();
        try (var stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            SampleRunCostComparison.reportUsage(stream, List.of(events), live);
        }
        return output.toString(StandardCharsets.UTF_8);
    }
}
