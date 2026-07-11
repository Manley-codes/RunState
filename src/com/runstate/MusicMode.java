package com.runstate;

// Whether the runner had music at all — a clean yes/no, kept separate from the
// free-text "what were you listening to" note. This distinction matters: NO_MUSIC
// (deliberately silent) must never be confused with null (never asked / not
// recorded). Legacy rows infer MUSIC only when music text exists; a null here is
// never turned into NO_MUSIC (see design_runstyle_v1). Secondary context only.
public enum MusicMode {
    MUSIC("Music"),
    NO_MUSIC("No music");

    private final String label;

    MusicMode(String label) {
        this.label = label;
    }

    // Returns the runner-facing label shown in prompts, history, and the AI message.
    public String getLabel() {
        return label;
    }
}
