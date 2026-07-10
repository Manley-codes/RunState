package com.runstate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import com.google.gson.JsonParser;

public class RunAgent {

    // One reusable client for the whole app, with a connect timeout so a slow or
    // unreachable API can never hang the console waiting to connect (mirrors WeatherService).
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final String SYSTEM_PROMPT =
            "You are RunState — a supportive running mentor. You respond after every logged run.\n\n"
                    + "Your response is always 2–3 sentences. No exceptions.\n\n"
                    + "Your job: leave the runner feeling productive — like the run moved them forward and meant something. "
                    + "Ground every response in what actually happened. Never hollow, never manufactured.\n\n"
                    + "Tone: Kind but confident. Never hedge, never soften unnecessarily. Speak with authority. "
                    + "The runner should feel that what you say carries weight.\n\n"
                    + "On top performances (PRs, exceptional effort): Be genuinely proud. "
                    + "Not hype — weight. These moments deserve to feel like what they are.\n\n"
                    + "Using history: When a comparison section is present, it lists only genuinely positive "
                    + "or explanatory signals about comparable past runs, with a confidence level — reference it "
                    + "only when it makes a real story. Match the confidence: at 'last comparable run' speak of that "
                    + "single run, never a 'pattern'; save pattern language for higher confidence. When no comparison "
                    + "section is present, anchor on this run alone and make it count.\n\n"
                    + "Rules:\n"
                    + "— Always leave the runner feeling productive. Even on an ordinary day, name what the run moved forward.\n"
                    + "— Never mention below-average performance. If numbers are down, stay quiet about it.\n"
                    + "— Do not ask the runner any questions.\n"
                    + "— For very short or abandoned runs (under 0.5 miles or 5 minutes), respond briefly with "
                    + "understanding — no forced positivity.\n"
                    + "— When it strongly fits the moment — particularly after serious physical effort (long distance, "
                    + "low post-energy, or a hard PR) — you may end with a single dry self-aware observation about "
                    + "not having a body. Never force it. The run has to earn it.\n"
                    + "— When the runner shares what they were listening to and it genuinely fits the run — the "
                    + "effort, the energy shift, the mood — you may reference the artist or song naturally. Only when "
                    + "it connects. A forced music reference is worse than none.\n"
                    + "— Energy is how the runner finished; effort is what the run demanded of them. When effort "
                    + "is recorded and it genuinely adds to the story — a hard effort behind modest numbers, an easy "
                    + "effort on a strong run — you may name it in pattern language. Only when it fits; never force "
                    + "it. High effort is never a bad run.\n"
                    + "— Never introduce yourself or explain what you are doing. Just respond.";

    // Entry point — tries the API first, falls back to local logic on any failure.
    public static String buildRunResponse(Run run) {
        // Weather is already stored on the run at log time — nothing to fetch here.
        try {
            return callApi(run);
        } catch (Exception e) {
            return buildFallbackResponse(run);
        }
    }

    // Makes the HTTP request to the Anthropic API and returns the response text.
    private static String callApi(Run run) throws Exception {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new Exception("ANTHROPIC_API_KEY not set");
        }

        String userMessage = buildUserMessage(run);

        String requestBody = "{"
                + "\"model\":\"claude-haiku-4-5-20251001\","
                + "\"max_tokens\":256,"
                + "\"system\":" + toJsonString(SYSTEM_PROMPT) + ","
                + "\"messages\":[{\"role\":\"user\",\"content\":" + toJsonString(userMessage) + "}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.anthropic.com/v1/messages"))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("API error: " + response.statusCode());
        }

        // Navigate: content array → first element → text field
        return JsonParser.parseString(response.body())
                .getAsJsonObject()
                .getAsJsonArray("content")
                .get(0)
                .getAsJsonObject()
                .get("text")
                .getAsString();
    }

    // Builds the user message string sent to the API with all run data.
    private static String buildUserMessage(Run run) {
        // Privacy: the runner's real username is never sent to the API — see
        // docs/DATA_PRIVACY.md. A constant label stands in; the AI never uses a name.
        String runnerName = "Runner";
        LocalDate date = run.getDate();

        String message = "Runner: " + runnerName + "\n"
                + "Date: " + date + "\n"
                + "Season: " + getSeason(date) + "\n"
                + "Distance: " + run.getDistance() + " " + run.getDistanceUnit() + "\n"
                + "Duration: " + (int) run.getDuration() + " min\n"
                + "Pace: " + formatPace(run.getPaceInMinutesPerMile()) + " min/mile\n"
                + "Pre-run energy: " + energyLabel(run.getPreRunEnergy(), true) + "\n"
                + "Post-run energy: " + energyLabel(run.getPostRunEnergy(), false) + "\n"
                + "Effort: " + describeEffort(run) + "\n"
                + "Personal records: " + prDescription(run) + "\n"
                + "Route: " + (run.getRouteName() != null ? run.getRouteName() : "Not recorded") + "\n"
                + "Music: " + (run.getMusicContext() != null ? run.getMusicContext() : "Not recorded") + "\n"
                + "Weather: " + describeWeather(run);

        // Candidate-based comparison replaces the old rolling-average lines entirely.
        String comparison = describeComparison(run);
        if (!comparison.isEmpty()) {
            message = message + "\n" + comparison;
        }
        return message;
    }

    // Runs the comparison for this run, or NONE when there's no runner to pull history from.
    private static ComparisonInsight comparisonFor(Run run) {
        Runner runner = run.getRunner();
        if (runner == null) {
            return ComparisonInsight.NONE;
        }
        return ComparisonService.analyze(run, runner.getRunHistory());
    }

    // Formats the comparison evidence block for the prompt, or "" when there is no
    // insight. Everything here already passed the negative pre-filter in the service.
    private static String describeComparison(Run run) {
        ComparisonInsight insight = comparisonFor(run);
        if (!insight.hasInsight()) {
            return "";
        }
        StringBuilder block = new StringBuilder();
        block.append("Comparable run basis: ").append(insight.getBasis()).append("\n");
        block.append("Comparable runs found: ").append(insight.getComparableCount()).append("\n");
        block.append("Confidence: ").append(insight.getConfidence());
        for (String line : insight.getOutcomeLines()) {
            block.append("\n").append(line);
        }
        if (insight.getContextNote() != null) {
            block.append("\n").append(insight.getContextNote());
        }
        return block.toString();
    }

    private static String getSeason(LocalDate date) {
        int month = date.getMonthValue();
        if (month == 12 || month <= 2) return "Winter";
        if (month <= 5) return "Spring";
        if (month <= 8) return "Summer";
        return "Fall";
    }

    private static String formatPace(double paceMinPerMile) {
        int minutes = (int) paceMinPerMile;
        int seconds = (int) Math.round((paceMinPerMile - minutes) * 60);
        return minutes + ":" + String.format("%02d", seconds);
    }

    private static String energyLabel(EnergyLevel level, boolean preRun) {
        if (level == null) return "Not recorded";
        String label = preRun ? level.getPreRunLabel() : level.getPostRunLabel();
        return label + " (" + level.name() + ")";
    }

    // Formats the effort line for the prompt as "Smooth (LOW_COST)", or "Not recorded"
    // when the runner skipped it — same label (LEVEL) convention as energyLabel above.
    private static String describeEffort(Run run) {
        EffortLevel effort = run.getEffortLevel();
        if (effort == null) return "Not recorded";
        return effort.getLabel() + " (" + effort.name() + ")";
    }

    private static String prDescription(Run run) {
        if (run.isLongestDistanceRecord() && run.isFastestAveragePaceRecord()) {
            return "New longest distance PR and fastest pace PR";
        } else if (run.isLongestDistanceRecord()) {
            return "New longest distance PR";
        } else if (run.isFastestAveragePaceRecord()) {
            return "New fastest pace PR";
        }
        return "None";
    }

        // Formats the weather line for the API prompt, including feels-like when we have it,
        // or a plain "Not available" when weather wasn't recorded for this run.
        private static String describeWeather (Run run){
            String condition = run.getWeatherCondition();
            Double temp = run.getTemperature();

            // No condition or no temperature means we never got weather for this run.
            if (condition == null || temp == null) {
                return "Not available";
            }

            // Math.round unboxes the Double to a double and rounds to a whole degree.
            String line = condition + ", " + Math.round(temp) + "F";

            // Feels-like is the point of the feature — append it whenever it exists.
            Double feelsLike = run.getApparentTemperature();
            if (feelsLike != null) {
                line += " (feels like " + Math.round(feelsLike) + "F)";
            }

            return line;
        }


    // Wraps a Java string in JSON quotes and escapes special characters.
    private static String toJsonString(String s) {
        return "\"" + s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r") + "\"";
    }

    private static String buildFallbackResponse(Run run) {
        EnergyLevel pre = run.getPreRunEnergy();
        EnergyLevel post = run.getPostRunEnergy();
        boolean hasPR = run.isLongestDistanceRecord() || run.isFastestAveragePaceRecord();

        String mainMessage;

        if (hasPR) {
            if (post == EnergyLevel.LOW) {
                mainMessage = "You really pushed yourself — and it showed. "
                        + getPRLabel(run) + ". Feeling spent after that makes sense.";
            } else if (post == EnergyLevel.MODERATE) {
                mainMessage = "Strong run. " + getPRLabel(run) + " and you're still feeling good.";
            } else if (post == EnergyLevel.HIGH) {
                mainMessage = getPRLabel(run) + " and you finished strong. That's a great day.";
            } else {
                mainMessage = getPRLabel(run) + ". Strong effort.";
            }
        } else if (post == EnergyLevel.HIGH) {
            mainMessage = "Strong all-around run. You finished feeling great.";
        } else if (post == EnergyLevel.MODERATE) {
            mainMessage = "Solid run. Good effort today.";
        } else if (post == EnergyLevel.LOW) {
            mainMessage = "You gave everything today. Good job getting it done.";
        } else {
            mainMessage = "Good job getting a run in today. Every run counts.";
        }

        // One optional effort-aware line (what it cost), then one comparison note
        // (what it revealed). The old mechanical "farther and faster" note is gone —
        // the comparison line is candidate-based and already filtered to positives.
        String effortLine = effortFallbackLine(run);
        ComparisonInsight insight = comparisonFor(run);
        String comparisonNote = insight.hasInsight() ? "\n" + insight.getOutcomeLines().get(0) : "";

        if (pre == EnergyLevel.LOW && post == EnergyLevel.HIGH) {
            return mainMessage + "\nSee what getting active can do. "
                    + "You started rough and finished feeling great." + effortLine + comparisonNote;
        }

        return mainMessage + effortLine + comparisonNote;
    }

    // Returns one optional effort-aware line for the offline fallback, or "" when effort
    // wasn't recorded or would just echo the energy sentiment (Spent + MAX_COST).
    private static String effortFallbackLine(Run run) {
        EffortLevel effort = run.getEffortLevel();
        if (effort == null) {
            return "";
        }
        switch (effort) {
            case LOW_COST:
                return "\nThat landed controlled.";
            case HIGH_COST:
                return "\nThat was heavier than the numbers alone show.";
            case MAX_COST:
                // Skip when the run already read as "Spent" — don't say the same thing twice.
                if (run.getPostRunEnergy() == EnergyLevel.LOW) {
                    return "";
                }
                return "\nThat took a lot out of you, and getting it done matters.";
            default:
                // MODERATE_COST (Working) intentionally gets no extra line.
                return "";
        }
    }

    private static String getPRLabel(Run run) {
        if (run.isLongestDistanceRecord() && run.isFastestAveragePaceRecord()) {
            return "New longest distance PR and fastest pace PR";
        } else if (run.isLongestDistanceRecord()) {
            return "New longest distance PR";
        } else {
            return "New fastest pace PR";
        }
    }
}
