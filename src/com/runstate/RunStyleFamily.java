package com.runstate;

// The three PRIMARY RunStyle patterns. The declaration order is meaningful: it encodes
// the tie-break priority (Efficiency Gain > State Lift > Controlled Finish) used when two
// families reach the same stage with the same rates — the earlier constant wins. Each
// carries a neutral, association-worded observation shown as the primary line (never a
// title, never a cause).
enum RunStyleFamily {
    EFFICIENCY_GAIN("You're holding your pace at a lower cost than you used to."),
    STATE_LIFT("Your runs keep lifting how you feel from start to finish."),
    CONTROLLED_FINISH("You keep finishing strong without overreaching.");

    private final String observation;

    RunStyleFamily(String observation) {
        this.observation = observation;
    }

    // The primary-line sentence describing this pattern to the runner.
    String getObservation() {
        return observation;
    }
}
