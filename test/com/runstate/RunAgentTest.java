package com.runstate;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class RunAgentTest {

    @Test
    void formatPaceCarriesRoundedSixtySecondsIntoNextMinute() {
        // A pace of 7 + 59.6/60 minutes would previously round the seconds to 60,
        // producing the invalid string "7:60". The fix converts total seconds first.
        double pace = 7 + 59.6 / 60.0;
        assertEquals("8:00", RunAgent.formatPace(pace));
    }

    @Test
    void formatPaceKeepsOrdinarySecondsFormatting() {
        // 8.4 minutes per mile = 8 minutes + 0.4 * 60 = 24 seconds → "8:24"
        assertEquals("8:24", RunAgent.formatPace(8.4));
    }

    @Test
    void formatComparisonReturnsEmptyStringForNoInsight() {
        assertEquals("", RunAgent.formatComparison(ComparisonInsight.NONE));
    }

    @Test
    void formatComparisonContainsPerSignalMetadataAndExcludesGlobalLines() {
        // Build a ComparisonInsight with two outcomes and verify the full prompt shape.
        List<ComparisonOutcome> outcomes = List.of(
                new ComparisonOutcome("Bigger energy lift.", 2, "early signal"),
                new ComparisonOutcome("Same effort, but faster.", 8, "strong personal pattern")
        );
        ComparisonInsight insight = new ComparisonInsight("same route", outcomes, null);

        String formatted = RunAgent.formatComparison(insight);

        // Basis header present
        assertTrue(formatted.contains("Comparable run basis: same route"));
        // Per-signal evidence and confidence present for both outcomes
        assertTrue(formatted.contains("evidence-bearing comparable runs: 2"));
        assertTrue(formatted.contains("confidence: early signal"));
        assertTrue(formatted.contains("evidence-bearing comparable runs: 8"));
        assertTrue(formatted.contains("confidence: strong personal pattern"));
        // Old global lines absent — the format moved metadata inside each signal bullet
        assertFalse(formatted.contains("Comparable runs found:"));
        assertFalse(formatted.contains("\nConfidence:"));
    }
}
