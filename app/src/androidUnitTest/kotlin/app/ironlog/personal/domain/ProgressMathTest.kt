package app.ironlog.personal.domain

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMathTest {
    private val today = LocalDate.parse("2026-10-07") // Wednesday

    @Test
    fun movingAverageStartsAfterThreeReadings() {
        val daily = mapOf(today.minusDays(2) to 80.0, today.minusDays(1) to 81.0, today to 82.0)
        assertEquals(listOf(today to 81.0), ProgressMath.movingAverage(daily))
    }

    @Test
    fun weeklyVolumeIncludesEmptyWeeksOldestFirst() {
        val sets = listOf(today to 1000.0, today.minusDays(1) to 500.0, today.minusWeeks(2) to 200.0)
        val weeks = ProgressMath.weeklyVolume(sets, today, 3)
        assertEquals(listOf(200.0, 0.0, 1500.0), weeks.map { it.second })
        assertEquals(LocalDate.parse("2026-10-05"), weeks.last().first)
    }

    @Test
    fun setsPerMuscleCoversEveryGroupAndOnlyLastSevenDays() {
        val result =
            ProgressMath.setsPerMuscle(
                sets = listOf(today to "squat", today to "squat", today.minusDays(8) to "bench"),
                musclesByExercise = mapOf("squat" to listOf("quadriceps"), "bench" to listOf("chest")),
                today = today,
                muscles = listOf("chest", "quadriceps", "neck"),
            )
        assertEquals(listOf("chest" to 0, "quadriceps" to 2, "neck" to 0), result)
    }
}
