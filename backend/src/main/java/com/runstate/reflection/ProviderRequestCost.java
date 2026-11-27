package com.runstate.reflection;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Conservative reservations, not measured bills. Reverify prices when a model or plan changes.
 * November 26, 2026: the supported Anthropic models cost at most $4/$20 per million
 * input/output tokens. Count every assembled JSON UTF-8 byte as an input token, plus
 * 8,192 tokens for provider framing, and reserve the full configured output allowance.
 * Multilingual v2 uses $0.10 per 1,000 UTF-16 units (above the verified $0.08 rate).
 * Unknown Anthropic models keep the operator's full configured per-call reservation.
 */
final class ProviderRequestCost {
    private static final Set<String> VERIFIED_MODELS = Set.of(
            "claude-haiku-4-5-20251001", "claude-sonnet-4-6", "claude-opus-5-5");
    private static final long FRAMING_TOKENS = 8_192;

    private ProviderRequestCost() { }

    static Long anthropicMicros(String model, String requestBody, int maxOutputTokens) {
        if (model == null || !VERIFIED_MODELS.contains(model)) return null;
        if (requestBody == null || requestBody.isEmpty() || maxOutputTokens <= 0)
            throw new IllegalArgumentException("Invalid provider request cost input.");
        long inputTokens = Math.addExact(requestBody.getBytes(StandardCharsets.UTF_8).length, FRAMING_TOKENS);
        return Math.addExact(Math.multiplyExact(inputTokens, 4L), Math.multiplyExact(maxOutputTokens, 20L));
    }

    static long elevenLabsMicros(String text) {
        if (text == null || text.isEmpty()) throw new IllegalArgumentException("Invalid voice cost input.");
        // UTF-16 units deliberately count supplementary characters twice rather than under-reserve them.
        return Math.multiplyExact((long) text.length(), 100L);
    }
}
