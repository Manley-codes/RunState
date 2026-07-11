package com.runstate;

// The four typed comparison signals evaluated by the strict RunStyle path
// (ComparisonService.evaluateStrict). They mirror the four positive signals that
// analyze() already derives, but as TYPED values StrictEvidence can carry — so
// RunStyleService (Step 3) reads which signals fired instead of parsing the prompt
// strings. No display text here on purpose: these are internal evidence, never shown.
enum RunStyleSignal {
    STATE_LIFT,        // bigger start-to-finish energy lift than the usual comparable run
    QUIET_GAIN,        // same output, clearly lower effort — progress the pace won't show yet
    SAME_COST_BETTER,  // about the same effort, but faster
    DEMAND_EXPLAINED   // clearly higher effort, but justified by a PR or more distance
}
