package app.ironlog.personal.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngagementInputsTest {
    private val day0 = LocalDate.parse("2026-10-01")

    @Test
    fun workoutsCarrySetsVolumeAndPersonalBests() {
        val history =
            listOf(
                ExerciseSet(1, 0, "bench", "Bench", "WORKING", 80.0, 5),
                ExerciseSet(1, 0, "bench", "Bench", "WARMUP", 40.0, 10),
                ExerciseSet(2, 86_400_000, "bench", "Bench", "WORKING", 85.0, 5),
            )
        val inputs = EngagementInputs.workouts(history) { ms -> day0.plusDays(ms / 86_400_000) }.sortedBy { it.date }
        assertEquals(listOf(1, 1), inputs.map { it.completedWorkingSets })
        assertEquals(listOf(0, 2), inputs.map { it.newPrCount })
        assertEquals(400.0, inputs.first().sessionVolumeKg, 0.0)
        val summary = EngagementReplay.replay(inputs, emptyList())
        // 2 workouts x 100 + 2 x 5 per set + 2 PRs x 50
        assertEquals(310, summary.totalXp)
        assertTrue(summary.medals.first { it.id == "first_pr" }.earnedOn == day0.plusDays(1))
    }

    @Test
    fun goalReachedFollowsDirection() {
        val daily = mapOf(day0 to 82.0, day0.plusDays(5) to 80.5, day0.plusDays(9) to 79.9)
        assertEquals(day0.plusDays(9), EngagementInputs.goalReachedOn(daily, 83.0, 80.0))
        assertNull(EngagementInputs.goalReachedOn(daily, 78.0, 85.0))
        assertNull(EngagementInputs.goalReachedOn(daily, 80.0, null))
    }

    @Test
    fun everyReplayMedalHasDisplayInfo() {
        val ids = EngagementReplay.replay(emptyList(), emptyList()).medals.map { it.id }
        assertEquals(ids.toSet(), Medals.ALL.map { it.id }.toSet())
    }
}
