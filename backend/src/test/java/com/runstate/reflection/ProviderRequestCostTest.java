package com.runstate.reflection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ProviderRequestCostTest {
    @Test void knownModelsReserveTheSameConservativeInputAndOutputRates() {
        for (String model : new String[]{"claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-opus-5-5"}) {
            assertEquals(73_736L, ProviderRequestCost.anthropicMicros(model, "{}", 2_048));
        }
    }

    @Test void countsUtf8BytesAndTheEntireAssembledBodyRatherThanJavaCharacterCount() {
        String ascii = "{\"text\":\"abc\"}";
        String unicode = "{\"text\":\"中🎵\"}";
        long difference = ProviderRequestCost.anthropicMicros("claude-opus-5-5", unicode, 2_048)
                - ProviderRequestCost.anthropicMicros("claude-opus-5-5", ascii, 2_048);
        assertEquals(16, difference); // Seven UTF-8 bytes versus three, despite both having three UTF-16 units.
        assertEquals(20L, ProviderRequestCost.anthropicMicros("claude-opus-5-5", ascii, 2_049)
                - ProviderRequestCost.anthropicMicros("claude-opus-5-5", ascii, 2_048));
    }

    @Test void largestSupportedRequestsFitTheirExplicitActivationCeilings() {
        assertEquals(4_268_032L, ProviderRequestCost.anthropicMicros("claude-opus-5-5",
                "x".repeat(AnthropicReflectionProvider.MAX_REQUEST_BODY_BYTES), 2_048));
        assertEquals(400_000L, ProviderRequestCost.elevenLabsMicros(
                "x".repeat(ElevenLabsReflectionSpeech.MAX_TEXT_LENGTH)));
        assertEquals(50_000L, ProviderRequestCost.elevenLabsMicros(
                "x".repeat(ElevenLabsReflectionSpeech.MAX_RUN_VOICE_TEXT_LENGTH)));
    }

    @Test void unknownModelsDoNotInheritAnEstimateAndInvalidKnownInputsAreRejected() {
        for (String model : new String[]{"custom-model", "claude-opus-5-5-future", "claude-opus-5-6"})
            assertNull(ProviderRequestCost.anthropicMicros(model, "{}", 2_048));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderRequestCost.anthropicMicros("claude-opus-5-5", "{}", 0));
        assertThrows(IllegalArgumentException.class, () -> ProviderRequestCost.elevenLabsMicros(""));
        assertEquals(500L, ProviderRequestCost.elevenLabsMicros("Hi 🎵"));
    }
}
