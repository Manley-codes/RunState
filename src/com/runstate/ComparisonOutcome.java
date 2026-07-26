package com.runstate;

// Immutable bundle: one positive/explanatory outcome line together with the
// evidence that supports it. Created by ComparisonService, formatted by RunAgent.
class ComparisonOutcome {

    private final String line;
    private final int evidenceCount;
    private final String confidencePhrase;

    ComparisonOutcome(String line, int evidenceCount, String confidencePhrase) {
        this.line = line;
        this.evidenceCount = evidenceCount;
        this.confidencePhrase = confidencePhrase;
    }

    String getLine() { return line; }
    int getEvidenceCount() { return evidenceCount; }
    String getConfidencePhrase() { return confidencePhrase; }
}
