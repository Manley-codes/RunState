package com.runstate.mobile.ui

import com.runstate.mobile.data.local.MetricSource
import com.runstate.mobile.data.local.PreRunEnergy
import com.runstate.mobile.data.local.ReflectionBranch
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
