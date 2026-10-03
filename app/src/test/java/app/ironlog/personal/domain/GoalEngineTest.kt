package app.ironlog.personal.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalEngineTest {
    private val today = LocalDate.parse("2026-10-03")

    private fun trend(start: Double, end: Double): Map<LocalDate, Double> =
        mapOf(
            today.minusDays(28) to start,
            today.minusDays(14) to (start + end) / 2,
            today.minusDays(6) to end,
            today.minusDays(3) to end,
            today to end,
        )

    @Test
    fun lossGoalCanBeOnTrack() {
        val result =
            GoalEngine.evaluate(
                WeightGoalInput(80.0, 70.0, today.plusDays(70), today, trend(75.6, 74.0))
            )
        assertEquals(GoalStatus.ON_TRACK, result.status)
        assertEquals(60.0, result.percentComplete!!, 0.01)
        assertEquals(0.5, result.weeklyRateKg!!, 0.01)
    }

    @Test
    fun gainGoalUsesOppositeDirectionAndClamp() {
        val result =
            GoalEngine.evaluate(
                WeightGoalInput(60.0, 70.0, today.plusDays(70), today, trend(64.4, 66.0))
            )
        assertEquals(GoalStatus.ON_TRACK, result.status)
        assertEquals(60.0, result.percentComplete!!, 0.01)
        val over =
            GoalEngine.evaluate(
                WeightGoalInput(60.0, 70.0, today.plusDays(70), today, trend(70.0, 72.0))
            )
        assertEquals(100.0, over.percentComplete!!, 0.01)
    }

    @Test
    fun insufficientTrendNeedsData() {
        val sparse = mapOf(today to 75.0, today.minusDays(2) to 75.2)
        assertEquals(
            GoalStatus.NEEDS_DATA,
            GoalEngine.evaluate(WeightGoalInput(80.0, 70.0, today.plusDays(60), today, sparse))
                .status,
        )
        assertNull(
            GoalEngine.evaluate(
                    WeightGoalInput(80.0, 80.0, today.plusDays(60), today, trend(80.0, 80.0))
                )
                .percentComplete
        )
    }

    @Test
    fun movingAwayIsBehindAndNoProjection() {
        val result =
            GoalEngine.evaluate(
                WeightGoalInput(80.0, 70.0, today.plusDays(60), today, trend(74.0, 76.0))
            )
        assertEquals(GoalStatus.BEHIND, result.status)
        assertNull(result.projectedDate)
    }

    @Test
    fun bmiWaistAndAgeGates() {
        assertEquals(25.0, GoalEngine.bmi(70.0, 167.332), 0.01)
        assertEquals(0.5, GoalEngine.waistHeightRatio(85.0, 170.0)!!, 0.001)
        assertFalse(GoalEngine.showBodyRatios(false))
        assertTrue(GoalEngine.showBodyRatios(true))
        assertFalse(GoalEngine.calorieEstimateAllowed(17))
        assertTrue(GoalEngine.calorieEstimateAllowed(18))
        assertFalse(GoalEngine.weightLossSuggestionAllowed(30, 17.5))
        assertTrue(GoalEngine.weightLossSuggestionAllowed(30, 22.0))
    }
}
