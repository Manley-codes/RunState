package com.runstate.reflection;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

/** Optional, loopback-only console measurements for one isolated run at a time. */
final class LocalProviderUsage {
    enum Step { STUDY, CHOOSER, WRITER, ORDINARY, AUDITION }

    private final PrintStream output;
    private static final Set<String> KNOWN_MODELS = Set.of(AnthropicReflectionProvider.DEFAULT_MODEL,
            AnthropicReflectionProvider.DEFAULT_MUSIC_EDITOR_MODEL, AnthropicReflectionProvider.DEFAULT_SONG_WRITER_MODEL);

    private LocalProviderUsage(PrintStream output) { this.output = output; }

    static LocalProviderUsage configured(Map<String, String> env, boolean remote, PrintStream output) {
        return !remote && "1".equals(env.get("RUNSTATE_LOCAL_USAGE_METER")) ? new LocalProviderUsage(output) : null;
    }

    void anthropicAttempt(Step step, String model) {
        line(anthropicFields(step, model) + " attempted=1");
    }

    private static String anthropicFields(Step step, String model) {
        return "anthropic step=" + step + " model=" + (KNOWN_MODELS.contains(model) ? model : "custom");
    }

    void anthropicReply(Step step, String model, String body) {
        JsonObject usage = null;
        try {
            usage = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("usage");
        } catch (RuntimeException unavailable) {
            // An incomplete or non-JSON provider response has no confirmed token count.
        }
        JsonObject cache = usage != null && usage.get("cache_creation") instanceof JsonObject fields ? fields : null;
        line(anthropicFields(step, model) + " reply=1 input=" + tokens(usage, "input_tokens")
                + " output=" + tokens(usage, "output_tokens")
                + " cache_read=" + tokens(usage, "cache_read_input_tokens")
                + " cache_write_total=" + tokens(usage, "cache_creation_input_tokens")
                + " cache_write_5m=" + tokens(cache, "ephemeral_5m_input_tokens")
                + " cache_write_1h=" + tokens(cache, "ephemeral_1h_input_tokens"));
    }

    private static String tokens(JsonObject object, String field) {
        JsonElement value = object == null ? null : object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return "unknown";
        String number = value.getAsString();
        return number.matches("[0-9]{1,16}") ? number : "unknown";
    }

    void voiceAttempt(boolean runVoice, int characters) {
        line("elevenlabs voice=" + (runVoice ? "run" : "reflection") + " attempted=1 submitted_chars=" + characters);
    }

    void voiceReply(boolean runVoice, boolean usable, String characterCost) {
        String units = characterCost != null && characterCost.matches("[0-9]{1,16}") ? characterCost : "unknown";
        line("elevenlabs voice=" + (runVoice ? "run" : "reflection") + " reply=1 usable_audio=" + usable
                + " provider_character_cost=" + units);
    }

    void musixmatchAttempt() { line("musixmatch attempted=1"); }

    private void line(String fixedFields) { output.println("RunStateUsage " + fixedFields); }
}
