package com.runstate;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * Step 1 (RunStyle V1) tests for the run-context layer — the RunContext value object,
 * the way Run delegates to it null-safely, and the legacy music-mode inference.
 *
 * These tests use no database or external service. Most are pure value/formatting checks;
 * one captures Run History console output to protect the shared-summary integration. The
 * DB round-trip cases from the design's Step 1 list still need live MySQL and are exercised
 * by hand against the migrated schema.
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

    // --- Context summary in run summaries ------------------------------------

    // A fully populated context must produce the exact line with fields in the
    // fixed contract order: Surface | Company | Shoes: <label> | Music: <note>.
    @Test
    void contextSummary_fullyPopulated_hasCorrectLineAndOrder() {
        RunContext context = new RunContext(
                SurfaceType.TRAIL, RunCompany.WITH_OTHERS, "Pegasus 41",
                MusicMode.MUSIC, "Midnight Marauders");
        Run run = runWith(context);
        assertTrue(run.getRunSummary().contains(
                "Context: Trail | With others | Shoes: Pegasus 41 | Music: Midnight Marauders"));
    }

    // When only some fields are recorded, the absent ones must be silently omitted
    // with no doubled separators or dangling labels.
    @Test
    void contextSummary_partialContext_omitsMissingValues() {
        RunContext context = new RunContext(SurfaceType.ROAD, null, null, null, null);
        String summary = runWith(context).getRunSummary();
        assertTrue(summary.contains("Context: Road"));
        assertFalse(summary.contains("Shoes:"));
        assertFalse(summary.contains("Music"));
        assertFalse(summary.contains("| |"));
    }

    // When no context field is present, the Context: line must be absent entirely —
    // do not print "Context: " or "Not recorded".
    @Test
    void contextSummary_emptyContext_producesNoLine() {
        String summary = runWith(RunContext.EMPTY).getRunSummary();
        assertFalse(summary.contains("Context:"));
        assertFalse(summary.contains("Not recorded"));
    }

    // MUSIC with a free-text note → "Music: <note>".
    @Test
    void contextSummary_musicWithNote_formattedCorrectly() {
        RunContext context = new RunContext(null, null, null, MusicMode.MUSIC, "Kind of Blue");
        assertTrue(runWith(context).getRunSummary().contains("Music: Kind of Blue"));
    }

    // MUSIC with no note → "Music" — no trailing colon or blank after it.
    @Test
    void contextSummary_musicWithoutNote_showsMusicLabel() {
        RunContext context = new RunContext(null, null, null, MusicMode.MUSIC, null);
        String summary = runWith(context).getRunSummary();
        assertTrue(summary.contains("Context: Music"));
        // Without a note, there must be no colon after "Music".
        assertFalse(summary.contains("Music: "));
    }

    // NO_MUSIC → "No music".
    @Test
    void contextSummary_noMusic_showsNoMusicLabel() {
        RunContext context = new RunContext(null, null, null, MusicMode.NO_MUSIC, null);
        assertTrue(runWith(context).getRunSummary().contains("Context: No music"));
    }

    // NO_MUSIC overrides any stray note — the note must be invisible.
    @Test
    void contextSummary_noMusicOverridesStrayNote() {
        RunContext context = new RunContext(null, null, null, MusicMode.NO_MUSIC, "some note");
        String summary = runWith(context).getRunSummary();
        assertTrue(summary.contains("No music"));
        assertFalse(summary.contains("some note"));
    }

    // Null mode with a note (legacy row) → "Music: <note>" so stored data is not hidden.
    @Test
    void contextSummary_nullModeWithNotePreservesNote() {
        RunContext context = new RunContext(null, null, null, null, "Old vinyl session");
        assertTrue(runWith(context).getRunSummary().contains("Music: Old vinyl session"));
    }

    // Exercise the public Run History surface so a later display refactor cannot
    // accidentally drop context while the lower-level summary test stays green.
    @Test
    void runHistoryDisplaysContextLine() {
        RunContext context = new RunContext(
                SurfaceType.TRAIL, RunCompany.SOLO, null,
                MusicMode.MUSIC, "Midnight Marauders");
        Run run = runWith(context);
        Runner runner = new Runner(
                1, "runner", "Test", "Runner", "Houston", "TX", "runner@example.com");
        runner.loadRun(run);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try {
            System.setOut(new PrintStream(captured));
            runner.displayRunHistory();
        } finally {
            System.setOut(original);
        }

        assertTrue(captured.toString().contains(
                "Context: Trail | Solo | Music: Midnight Marauders"));
    }
}
