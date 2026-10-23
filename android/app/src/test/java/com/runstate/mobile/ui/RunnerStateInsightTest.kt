package com.runstate.mobile.ui

import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
import com.runstate.mobile.data.local.RunIntention
import com.runstate.mobile.data.local.TelemetryCoverage
import com.runstate.mobile.data.local.PostRunEffort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RunnerStateInsightTest {
    @Test
    fun `fewer than three paired runs stays silent`() {
        assertNull(runnerStateInsightFor(emptyList()))
        assertNull(runnerStateInsightFor(listOf(run(1))))
        assertNull(runnerStateInsightFor(listOf(run(1), run(2))))
    }

    @Test
    fun `three paired runs describe only the reported finish frequency`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2), run(3))))
        assertEquals("After starting Low", insight.heading)
        assertEquals(3, insight.matchingRunCount)
        assertEquals("You finished Feeling Good in 3 of 3 runs you logged with Low starting Energy.",
            insight.description)
        assertEquals("Based on your Energy check-ins. 3 of your latest 3 GPS runs have both answers.",
            insight.coverageDescription)
    }

    @Test
    fun `a majority has an explicit denominator rather than a causal or scale claim`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2), run(3),
            run(4, post = ReflectionBranch.SPENT))))
        assertEquals(1, insight.spentRuns)
        assertEquals(3, insight.feelingGoodRuns)
        assertEquals(0, insight.poweredUpRuns)
        assertEquals("You finished Feeling Good in 3 of 4 runs you logged with Low starting Energy.",
            insight.description)
        assertFalse(insight.description.contains("improv", ignoreCase = true))
        assertFalse(insight.description.contains("because", ignoreCase = true))
    }

    @Test
    fun `a tied finish distribution states both counts without picking a winner`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2),
            run(3, post = ReflectionBranch.SPENT), run(4, post = ReflectionBranch.SPENT))))
        assertEquals("Your finishes varied: Spent in 2 of 4 runs; Feeling Good in 2 of 4 runs.",
            insight.description)
    }

    @Test
    fun `a unique plurality below half still describes all finishes`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2), run(3),
            run(4, post = ReflectionBranch.SPENT), run(5, post = ReflectionBranch.SPENT),
            run(6, post = ReflectionBranch.POWERED_UP), run(7, post = ReflectionBranch.POWERED_UP))))
        assertEquals("Your finishes varied: Feeling Good in 3 of 7 runs; Spent in 2 of 7 runs; " +
            "Powered Up in 2 of 7 runs.", insight.description)
    }

    @Test
    fun `half of finishes is not presented as a majority`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2),
            run(3, post = ReflectionBranch.SPENT), run(4, post = ReflectionBranch.POWERED_UP))))
        assertTrue(insight.description.startsWith("Your finishes varied:"))
    }

    @Test
    fun `newest starting group with enough paired runs gets the single insight`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(
            run(1, pre = PreRunEnergy.HIGH), run(2), run(3), run(4), run(5),
            run(6, pre = PreRunEnergy.HIGH), run(7, pre = PreRunEnergy.HIGH))))
        assertEquals(PreRunEnergy.HIGH, insight.startingEnergy)
        assertEquals(3, insight.matchingRunCount)
        assertEquals(7, insight.pairedGpsRunCount)
    }

    @Test
    fun `newer under threshold group does not hide an adequately supported group`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(
            run(1, pre = PreRunEnergy.HIGH), run(2), run(3), run(4))))
        assertEquals(PreRunEnergy.LOW, insight.startingEnergy)
        assertEquals(3, insight.matchingRunCount)
    }

    @Test
    fun `missing answers and no selection stay unknown even when display labels exist`() {
        val items = listOf(run(1), run(2), run(3, pre = null).copy(preRunEnergyLabel = "Low"),
            run(4, post = null).copy(energyLabel = "Feeling Good"),
            run(5, post = ReflectionBranch.NO_SELECTION), run(6))
        val insight = requireNotNull(runnerStateInsightFor(items))
        assertEquals(3, insight.matchingRunCount)
        assertEquals(6, insight.recentGpsRunCount)
        assertEquals(3, insight.pairedGpsRunCount)
        assertTrue(insight.coverageDescription.contains("3 of your latest 6 GPS runs"))
    }

    @Test
    fun `samples test data and unknown or mismatched sources do not become personal evidence`() {
        val excluded = listOf(
            run(3).copy(isTestData = true),
            run(4).copy(isGpsRun = false, metricSource = MetricSource.FIXTURE),
            run(5).copy(metricSource = MetricSource.FIXTURE),
            run(6).copy(isGpsRun = false),
            run(7).copy(metricSource = null))
        assertNull(runnerStateInsightFor(listOf(run(1), run(2)) + excluded))
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2), run(8)) + excluded))
        assertEquals(3, insight.recentGpsRunCount)
    }

    @Test
    fun `invalid and shortened uuid strings are excluded`() {
        val bad = listOf("", "not-a-run", "1-1-1-1-1", " 123e4567-e89b-12d3-a456-426614174000")
            .map { run(90).copy(runId = it) }
        assertNull(runnerStateInsightFor(listOf(run(1), run(2)) + bad))
    }

    @Test
    fun `every duplicate identity is excluded even with case or source differences`() {
        val first = run(1)
        val duplicate = first.copy(runId = first.runId.uppercase(Locale.US),
            preRunEnergy = PreRunEnergy.HIGH, isTestData = true)
        val insight = requireNotNull(runnerStateInsightFor(listOf(first, run(2), run(3),
            run(4), duplicate)))
        assertEquals(3, insight.recentGpsRunCount)
        assertEquals(3, insight.matchingRunCount)
    }

    @Test
    fun `canonical uppercase uuid is accepted without a duplicate`() {
        val insight = requireNotNull(runnerStateInsightFor(listOf(
            run(1).let { it.copy(runId = it.runId.uppercase(Locale.US)) }, run(2), run(3))))
        assertEquals(3, insight.matchingRunCount)
    }

    @Test
    fun `unverified test provenance stays excluded even when history display flag is false`() {
        val blocked = run(3).copy(isTestData = false, insightEligible = false)
        assertNull(runnerStateInsightFor(listOf(run(1), run(2), blocked)))
        val insight = requireNotNull(runnerStateInsightFor(listOf(run(1), run(2), run(4), blocked)))
        assertEquals(3, insight.recentGpsRunCount)
        assertEquals(3, insight.matchingRunCount)
    }

    @Test
    fun `recent twenty is bounded before missing answers are removed`() {
        val newest = (1..18).map { run(it, pre = null) }
        assertNull(runnerStateInsightFor(newest + listOf(run(19), run(20), run(21))))
        val insight = requireNotNull(runnerStateInsightFor((1..17).map { run(it, pre = null) } +
            listOf(run(18), run(19), run(20), run(21, pre = PreRunEnergy.HIGH))))
        assertEquals(20, insight.recentGpsRunCount)
        assertEquals(3, insight.matchingRunCount)
    }

    @Test
    fun `sample rows do not consume the recent genuine run window`() {
        val samples = (1..25).map { run(it).copy(isTestData = true, metricSource = MetricSource.FIXTURE) }
        val insight = requireNotNull(runnerStateInsightFor(samples + listOf(run(26), run(27), run(28))))
        assertEquals(3, insight.recentGpsRunCount)
    }

    @Test
    fun `editing an answer recomputes the count and can remove unsupported insight`() {
        val original = listOf(run(1), run(2), run(3))
        val revised = original.map { if (it.runId == run(1).runId)
            it.copy(energy = ReflectionBranch.POWERED_UP) else it }
        val insight = requireNotNull(runnerStateInsightFor(revised))
        assertEquals(2, insight.feelingGoodRuns)
        assertEquals(1, insight.poweredUpRuns)
        assertEquals(3, requireNotNull(runnerStateInsightFor(original)).feelingGoodRuns)
        assertNull(runnerStateInsightFor(revised.map { if (it.runId == run(1).runId)
            it.copy(energy = null) else it }))
    }

    @Test
    fun `spent finish is reported neutrally without comparing it to starting high`() {
        val insight = requireNotNull(runnerStateInsightFor((1..3).map {
            run(it, pre = PreRunEnergy.HIGH, post = ReflectionBranch.SPENT)
        }))
        assertEquals("After starting High", insight.heading)
        assertEquals("You finished Spent in 3 of 3 runs you logged with High starting Energy.",
            insight.description)
    }

    @Test
    fun `empty current foundation explains collecting without inventing a pattern`() {
        val empty = runnerEnergyFoundationFor(emptyList())
        assertTrue(empty.runs.isEmpty())
        assertNull(empty.categoryInsight)
        assertNull(empty.easyComparison)
        assertEquals(0, empty.pairedRuns.size)
        assertTrue(empty.coverageDescription.startsWith("No runs with the current questions"))
        val legacy = runnerEnergyFoundationFor((1..3).map { run(it) })
        assertEquals(3, legacy.earlierQuestionRunCount)
        assertTrue(legacy.runs.isEmpty())
        assertEquals(3, requireNotNull(legacy.earlierInsight).matchingRunCount)
    }

    @Test
    fun `a first current run is inspectable without waiting for three answers`() {
        val saved = currentRun(1)
        val foundation = runnerEnergyFoundationFor(listOf(saved))
        assertEquals(listOf(saved), foundation.runs)
        assertEquals(1, foundation.pairedRuns.size)
        assertEquals(1, foundation.numericEffortCount)
        assertNull(foundation.categoryInsight)
        assertFalse(requireNotNull(foundation.easyComparison).hasEnoughRuns)
    }

    @Test
    fun `legacy future and mismatched question versions never support current counts`() {
        val current = currentRun(1)
        val mismatched = currentRun(2).copy(postRunEnergyQuestionVersion = null)
        val editedLegacy = run(3).copy(postRunEnergyQuestionVersion = 2)
        val future = currentRun(4).copy(energyQuestionVersion = 3, postRunEnergyQuestionVersion = 3)
        val foundation = runnerEnergyFoundationFor(listOf(current, mismatched, editedLegacy, future, run(5)))
        assertEquals(5, foundation.recentGpsRunCount)
        assertEquals(2, foundation.runs.size)
        assertEquals(listOf(current), foundation.pairedRuns)
        assertNull(foundation.categoryInsight)
        assertNull(foundation.earlierInsight)
    }

    @Test
    fun `unknown and unanswered current reports remain in coverage and source rows`() {
        val rows = listOf(currentRun(1), currentRun(2).copy(preRunEnergy = null),
            currentRun(3).copy(energy = ReflectionBranch.NO_SELECTION),
            currentRun(4).copy(energy = null, postRunEffortRating = null))
        val foundation = runnerEnergyFoundationFor(rows)
        assertEquals(4, foundation.runs.size)
        assertEquals(1, foundation.pairedRuns.size)
        assertEquals(3, foundation.numericEffortCount)
        assertNull(foundation.categoryInsight)
        assertTrue(foundation.coverageDescription.startsWith("1 of 4"))
    }

    @Test
    fun `version separation occurs after recent run cap and cannot resurrect older evidence`() {
        val recentLegacy = (1..18).map { run(it) }
        val foundation = runnerEnergyFoundationFor(recentLegacy + (19..21).map { currentRun(it) })
        assertEquals(20, foundation.recentGpsRunCount)
        assertEquals(2, foundation.runs.size)
        assertNull(foundation.categoryInsight)
    }

    @Test
    fun `samples duplicate identities and unfinalized feedback cannot enter new evidence`() {
        val genuine = currentRun(1)
        val duplicate = genuine.copy(runId = genuine.runId.uppercase(Locale.US))
        val rows = listOf(genuine, duplicate, currentRun(2).copy(isTestData = true),
            currentRun(3).copy(metricSource = MetricSource.FIXTURE),
            currentRun(4).copy(insightEligible = false), currentRun(5))
        val foundation = runnerEnergyFoundationFor(rows)
        assertEquals(listOf(currentRun(5)), foundation.runs)
        assertEquals(1, requireNotNull(foundation.easyComparison).runs.size)
    }

    @Test
    fun `new counts recompute from corrected answers without altering stored text`() {
        val rows = (1..3).map { currentRun(it) }
        val original = runnerEnergyFoundationFor(rows)
        assertEquals(3, requireNotNull(original.categoryInsight).feelingGoodRuns)
        val corrected = rows.mapIndexed { index, row -> if (index == 0) row.copy(energy = ReflectionBranch.SPENT) else row }
        val revised = runnerEnergyFoundationFor(corrected)
        assertEquals(2, requireNotNull(revised.categoryInsight).feelingGoodRuns)
        assertEquals(1, revised.categoryInsight.spentRuns)
        assertEquals(rows.map { it.reflectionText }, revised.runs.map { it.reflectionText })
    }

    @Test
    fun `easy grouping uses intention complete metrics and normalized route rather than best outcome`() {
        val anchor = currentRun(1).copy(routeLabel = "  Park  Loop ")
        val matching = currentRun(2).copy(routeLabel = "PARK LOOP", energy = ReflectionBranch.SPENT)
        val missingAnswers = currentRun(3).copy(preRunEnergy = null, energy = null, postRunEffortRating = null)
        val excluded = listOf(currentRun(4).copy(runIntention = RunIntention.HARD_EFFORT),
            currentRun(5).copy(runIntention = null), currentRun(6).copy(routeLabel = "Other loop"),
            currentRun(7).copy(telemetryCoverage = TelemetryCoverage.PARTIAL),
            currentRun(8).copy(routeLabel = null), currentRun(9).copy(activeDurationMillis = null))
        val comparison = requireNotNull(runnerEnergyFoundationFor(listOf(anchor, matching, missingAnswers) + excluded).easyComparison)
        assertEquals(listOf(anchor, matching, missingAnswers), comparison.runs)
        assertTrue(comparison.hasEnoughRuns)
        assertEquals(2, comparison.pairedCount)
        assertEquals(2, comparison.effortRatings.size)
    }

    @Test
    fun `both distance and active time must meet inclusive twenty percent rule`() {
        val rows = listOf(currentRun(1),
            currentRun(2).copy(distanceMeters = 6_000.0, activeDurationMillis = 2_160_000L),
            currentRun(3).copy(distanceMeters = 4_000.0, activeDurationMillis = 1_440_000L),
            currentRun(4).copy(distanceMeters = 6_001.0),
            currentRun(5).copy(activeDurationMillis = 2_160_001L),
            currentRun(6).copy(distanceMeters = Double.NaN),
            currentRun(7).copy(distanceMeters = 0.0),
            currentRun(8).copy(activeDurationMillis = 0L))
        val comparison = requireNotNull(runnerEnergyFoundationFor(rows).easyComparison)
        assertEquals(rows.take(3), comparison.runs)
    }

    @Test
    fun `latest eligible anchor never changes route to find a larger or happier group`() {
        val newest = currentRun(1).copy(routeLabel = "New loop", energy = ReflectionBranch.SPENT)
        val comparison = requireNotNull(runnerEnergyFoundationFor(listOf(newest) + (2..8).map { currentRun(it) }).easyComparison)
        assertEquals(newest.runId, comparison.anchor.runId)
        assertEquals(listOf(newest), comparison.runs)
        assertFalse(comparison.hasEnoughRuns)
    }

    @Test
    fun `effort distribution keeps the high reports and ignores legacy conversions`() {
        val rows = listOf(currentRun(1).copy(postRunEffortRating = 2),
            currentRun(2).copy(postRunEffortRating = 9),
            currentRun(3).copy(postRunEffortRating = null, effort = PostRunEffort.SMOOTH),
            currentRun(4).copy(postRunEffortRating = 1, postRunEffortQuestionVersion = null))
        val comparison = requireNotNull(runnerEnergyFoundationFor(rows).easyComparison)
        assertEquals(listOf(2, 9), comparison.effortRatings)
        assertEquals("Reported Effort (Session effort · 0–10): 2/10 on 1 run; 9/10 on 1 run. 2 of 3 runs answered.", comparison.effortDescription)
        assertFalse(comparison.effortDescription.contains("improv", ignoreCase = true))
    }

    @Test
    fun `cleared numeric effort does not resurface the old categorical answer`() {
        val cleared = currentRun(1).copy(postRunEffortRating = null, effort = PostRunEffort.SMOOTH)
        assertEquals("Not recorded", historyEffortLabel(cleared))
        assertEquals("Smooth", historyEffortLabel(run(2).copy(effort = PostRunEffort.SMOOTH)))
    }

    @Test
    fun `effort comparisons keep original new and unknown scales separate including zero`() {
        val current = currentRun(1).copy(postRunEffortRating = 0)
        val old = currentRun(2).copy(postRunEffortQuestionVersion = 1)
        val unknown = currentRun(3).copy(postRunEffortQuestionVersion = null)
        val comparison = requireNotNull(easyRunComparisonFor(listOf(current, old, unknown, currentRun(4))))
        assertEquals(listOf(current, currentRun(4)), comparison.runs)
        assertEquals(listOf(0, 4), comparison.effortRatings)
        assertEquals(listOf(old), requireNotNull(easyRunComparisonFor(listOf(old, current))).runs)
        assertNull(easyRunComparisonFor(listOf(unknown, current)))
        assertEquals("0 / 10 · Rest", historyEffortLabel(current))
        assertEquals("4 / 10 · Moderate · earlier scale", historyEffortLabel(old))
        assertEquals("4 / 10 · scale unknown", historyEffortLabel(unknown))
    }

    @Test
    fun `intention feedback states reports without judging mismatch or prescribing pace`() {
        val easy = currentRun(1).copy(postRunEffortRating = 7)
        assertEquals("Planned: Keep it easy. Reported: 7/10 — Very hard.", intentionEffortDescription(easy))
        assertEquals("Planned: Hard effort. Reported: 2/10 — Easy.", intentionEffortDescription(
            easy.copy(runIntention = RunIntention.HARD_EFFORT, postRunEffortRating = 2)))
        assertEquals("Planned: Steady. Reported: 6/10.", intentionEffortDescription(
            easy.copy(runIntention = RunIntention.STEADY, postRunEffortRating = 6)))
        assertEquals("Planned: Keep it easy. Reported: 7/10 — Hard (earlier scale).",
            intentionEffortDescription(easy.copy(postRunEffortQuestionVersion = 1)))
        assertNull(intentionEffortDescription(easy.copy(runIntention = null)))
        assertNull(intentionEffortDescription(easy.copy(postRunEffortRating = null)))
        assertNull(intentionEffortDescription(easy.copy(postRunEffortQuestionVersion = null)))
    }

    @Test
    fun `provenance distinguishes immediate edited and unknown original answer times`() {
        val row = currentRun(1).copy(finishEpochMillis = 10_000L, postRunEnergyRecordedAtEpochMillis = 20_000L,
            postRunEnergyUpdatedAtEpochMillis = 20_000L)
        assertEquals("Answered within a minute of finishing", energyAnswerTimingDescription(row))
        assertEquals("Answered within a minute of finishing · edited later",
            energyAnswerTimingDescription(row.copy(postRunEnergyUpdatedAtEpochMillis = 100_000L)))
        assertEquals("Original answer time unknown · edited later", energyAnswerTimingDescription(
            row.copy(postRunEnergyRecordedAtEpochMillis = null, postRunEnergyUpdatedAtEpochMillis = 100_000L)))
    }

    private fun currentRun(number: Int): RunHistoryItem = run(number).copy(
        energyQuestionVersion = 2, postRunEnergyQuestionVersion = 2,
        postRunEffortQuestionVersion = 2, postRunEffortRating = 4,
        runIntention = RunIntention.KEEP_EASY, routeLabel = "Park Loop",
        telemetryCoverage = TelemetryCoverage.COMPLETE,
        distanceMeters = 5_000.0, activeDurationMillis = 1_800_000L,
        officialStartEpochMillis = 1_700_000_000_000L - number * 86_400_000L,
        startTimezoneId = "America/Chicago"
    )

    private fun run(
        number: Int,
        pre: PreRunEnergy? = PreRunEnergy.LOW,
        post: ReflectionBranch? = ReflectionBranch.FEELING_GOOD
    ) = RunHistoryItem(
        runId = "123e4567-e89b-12d3-a456-${number.toString().padStart(12, '0')}",
        dateLabel = "Saved date",
        metrics = null,
        reflectionStatus = "READY",
        reflectionText = "Saved reply",
        energyLabel = null,
        isTestData = false,
        isGpsRun = true,
        energy = post,
        preRunEnergy = pre,
        metricSource = MetricSource.GPS
    )
}
