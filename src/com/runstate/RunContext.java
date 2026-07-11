package com.runstate;

/*
 * RunContext bundles the optional "context" details a runner can attach to a run:
 * surface, company, shoes, whether they had music, and the free-text music note.
 *
 * It is an immutable VALUE OBJECT — the same design as WeatherData. "Immutable"
 * means once it is built its fields never change: they are final and there are no
 * setters, only getters. "Value object" means it exists to GROUP related data, not
 * to have behavior; two RunContexts with the same fields are interchangeable.
 *
 * Why a value object instead of five loose parameters on Run? Because these five
 * belong together (they are all secondary run CONTEXT), and grouping them keeps the
 * Run constructor from growing five more arguments every caller must pass. This is
 * composition: a Run HAS-A RunContext, just as it HAS-A WeatherData.
 *
 * Every field is nullable. null everywhere is a completely valid RunContext — it
 * just means "the runner skipped all of this," which is always allowed.
 */
public final class RunContext {

    // The ground the run was on, or null if not recorded.
    private final SurfaceType surface;

    // Whether the runner was solo or with others, or null if not recorded.
    private final RunCompany company;

    // Free-text, reusable shoe label (e.g. "Pegasus 41"), or null if not recorded.
    private final String shoeLabel;

    // Whether the runner had music at all (MUSIC / NO_MUSIC), or null if not recorded.
    // Kept separate from musicNote so "deliberately no music" stays distinct from
    // "never asked" — see MusicMode.
    private final MusicMode musicMode;

    // The existing free-text "what were you listening to" note, or null if skipped.
    // This is the field that used to live directly on Run as musicContext.
    private final String musicNote;

    // Builds the bundle. Every argument may be null; a run with no context recorded
    // is a RunContext with all-null fields (see the EMPTY constant below).
    public RunContext(SurfaceType surface, RunCompany company, String shoeLabel,
                      MusicMode musicMode, String musicNote) {
        this.surface = surface;
        this.company = company;
        this.shoeLabel = shoeLabel;
        this.musicMode = musicMode;
        this.musicNote = musicNote;
    }

    // A shared all-null bundle for runs with no context. Reusable because the object
    // is immutable — nothing can mutate it, so one instance is safe to share.
    public static final RunContext EMPTY = new RunContext(null, null, null, null, null);

    // Returns the surface, or null if not recorded.
    public SurfaceType getSurface() {
        return surface;
    }

    // Returns the company, or null if not recorded.
    public RunCompany getCompany() {
        return company;
    }

    // Returns the shoe label, or null if not recorded.
    public String getShoeLabel() {
        return shoeLabel;
    }

    // Returns the music mode, or null if not recorded.
    public MusicMode getMusicMode() {
        return musicMode;
    }

    // Returns the free-text music note, or null if skipped.
    public String getMusicNote() {
        return musicNote;
    }
}
