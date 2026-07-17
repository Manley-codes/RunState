package com.runstate;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/*
 * Step 1 (RunStyle V1) tests for the run-context layer — the RunContext value object,
 * the way Run delegates to it null-safely, and the legacy music-mode inference.
 *
 * These are all pure logic (no database, no console), so they run in-process. The
 * DB round-trip cases from the design's Step 1 list (save → reload → display) need a
 * live MySQL and are exercised by hand against the migrated schema; here we prove the
 * in-memory contracts those rows are rebuilt into.
 */
public class RunContextTest {

    // Builds a Run carrying the given context; everything else is a sane default. A
    // null runner is fine — none of these assertions touch runner data.
    private static Run runWith(RunContext context) {
        return new Run(
                0, null, LocalDate.parse("2026-07-10"),
                null, null,
                3.0, DistanceUnit.MILES, 27.0,
                "Cedar Trail", null,
                EnergyLevel.MODERATE, EnergyLevel.MODERATE,
                context,
                null,
                EffortLevel.MODERATE_COST);
    }

    // --- RunContext value object ---------------------------------------------

    @Test
    void emptyContextIsAllNull() {
        // EMPTY stands in for "nothing recorded" — every accessor must read back null.
        assertNull(RunContext.EMPTY.getSurface());
        assertNull(RunContext.EMPTY.getCompany());
        assertNull(RunContext.EMPTY.getShoeLabel());
        assertNull(RunContext.EMPTY.getMusicMode());
        assertNull(RunContext.EMPTY.getMusicNote());
    }

    // --- Run delegates to the bundle -----------------------------------------

    @Test
    void runDelegatesEveryContextGetter() {
        // Arrange: a fully populated context.
        RunContext context = new RunContext(
                SurfaceType.TRAIL, RunCompany.WITH_OTHERS, "Pegasus 41",
                MusicMode.MUSIC, "Midnight Marauders");
        Run run = runWith(context);

        // Assert: Run's flat getters read straight through to the bundle's values,
        // and getMusicContext() returns the note (its old home was a Run field).
        assertEquals(SurfaceType.TRAIL, run.getSurface());
        assertEquals(RunCompany.WITH_OTHERS, run.getRunCompany());
        assertEquals("Pegasus 41", run.getShoeLabel());
        assertEquals(MusicMode.MUSIC, run.getMusicMode());
        assertEquals("Midnight Marauders", run.getMusicContext());
        assertSame(context, run.getRunContext());
    }

    @Test
    void nullContextIsCoercedToEmptyAndGettersStaySafe() {
        // A run logged with no context passes null to the constructor. getRunContext()
        // must never return null (the RunStyle families rely on that), and every flat
        // getter must read back null rather than throwing.
        Run run = runWith(null);
        assertNotNull(run.getRunContext());
        assertSame(RunContext.EMPTY, run.getRunContext());
        assertNull(run.getSurface());
        assertNull(run.getRunCompany());
        assertNull(run.getShoeLabel());
        assertNull(run.getMusicMode());
        assertNull(run.getMusicContext());
    }

    @Test
    void noMusicIsDistinctFromNotRecorded() {
        // The whole point of MusicMode: a deliberately silent run and a run where sound
        // was never asked must be tellable apart at the model level.
        Run silent = runWith(new RunContext(null, null, null, MusicMode.NO_MUSIC, null));
        Run notRecorded = runWith(new RunContext(null, null, null, null, null));

        assertEquals(MusicMode.NO_MUSIC, silent.getMusicMode());
        assertNull(notRecorded.getMusicMode());
        // Neither has a free-text note.
        assertNull(silent.getMusicContext());
        assertNull(notRecorded.getMusicContext());
    }

    // --- Legacy music-mode inference -----------------------------------------

    @Test
    void inferMusicModeStoredValueAlwaysWins() {
        // A row that explicitly stored its mode uses it verbatim — even NO_MUSIC that
        // happens to sit next to a stray note.
        assertEquals(MusicMode.NO_MUSIC, RunStorage.inferMusicMode(MusicMode.NO_MUSIC, "some note"));
        assertEquals(MusicMode.MUSIC, RunStorage.inferMusicMode(MusicMode.MUSIC, null));
    }

    @Test
    void inferMusicModeLegacyNoteImpliesMusic() {
        // A legacy row from before music_mode existed: it has a note but no stored mode,
        // so it clearly had music.
        assertEquals(MusicMode.MUSIC, RunStorage.inferMusicMode(null, "Kanye"));
    }

    @Test
    void inferMusicModeBothNullStaysNull() {
        // No mode, no note → never recorded. Must stay null and must NOT become NO_MUSIC.
        assertNull(RunStorage.inferMusicMode(null, null));
    }
}
