package app.ironlog.personal.domain

import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AchievementsTest {
    private val start = LocalDateTime.parse("2026-01-05T18:00") // a Monday evening

    private fun input(
        sets: List<ExerciseSet>,
        weights: Map<LocalDate, Double> = emptyMap(),
        profileKg: Double? = 80.0,
        muscles: Map<String, List<String>> = emptyMap(),
    ) =
        AchievementInput(
            sets = sets,
            sessionStarts = sets.associate { it.sessionId to start.plusDays(it.sessionId - 1) },
            dailyWeightKg = weights,
            profileWeightKg = profileKg,
            musclesByExercise = muscles,
            mealDays = emptyList(),
            completeFoodDays = emptyList(),
            photoDates = emptyList(),
            goalReachedOn = null,
            plannedPerWeek = 3,
        )

    private fun set(session: Long, id: String, kg: Double?, reps: Int, type: String = "WORKING") =
        ExerciseSet(session, start.plusDays(session - 1).toLocalDate().toEpochDay() * 86_400_000, id, id, type, kg, reps)

    private fun List<Achievement>.of(id: String) = first { it.def.id == id }

    @Test
    fun beginnerEarnsBronzeOnDayOne() {
        val result = Achievements.evaluate(input(listOf(set(1, "Pullups", null, 1), set(1, "Barbell_Bench_Press_-_Medium_Grip", 40.0, 8))))
        assertTrue(result.of("workouts_1").earned)
        assertTrue(result.of("pullups_1").earned)
        assertTrue(result.of("bench_bw_50").earned) // 40 kg at 80 kg bodyweight
        assertFalse(result.of("bench_bw_100").earned)
        assertEquals(0.5, result.of("bench_bw_100").progress, 1e-9)
        assertTrue(result.filter { it.earned }.all { it.def.tier == Tier.BRONZE })
    }

    @Test
    fun strongmanUnlocksLegendTiers() {
        val sets = listOf(
            set(1, "Barbell_Deadlift", 400.0, 1),
            set(1, "Barbell_Full_Squat", 300.0, 1),
            set(1, "Barbell_Bench_Press_-_Medium_Grip", 210.0, 1),
        )
        val result = Achievements.evaluate(input(sets, profileKg = 140.0))
        assertTrue(result.of("elite_deadlift_400").earned)
        assertTrue(result.of("elite_squat_300").earned)
        assertTrue(result.of("elite_total_900").earned) // 400 + 300 + 210
        assertTrue(result.of("deadlift_bw_250").earned) // 400 / 140 = 2.86
        assertFalse(result.of("deadlift_bw_300").earned)
        assertFalse(result.of("elite_bench_250").earned)
    }

    @Test
    fun relativeStrengthUsesBodyweightAtTheTimeAndIgnoresWarmups() {
        val weights = mapOf(LocalDate.parse("2026-01-01") to 100.0, LocalDate.parse("2026-01-06") to 70.0)
        val sets = listOf(set(1, "Barbell_Squat", 140.0, 1), set(3, "Barbell_Squat", 160.0, 1, type = "WARMUP"))
        val result = Achievements.evaluate(input(sets, weights))
        // Session 1 is on 2026-01-05 when bodyweight was 100 kg: 1.4x, not 2x.
        assertTrue(result.of("squat_bw_75").earned)
        assertFalse(result.of("squat_bw_150").earned)
    }

    @Test
    fun weightedPullUpsDoNotCountAsBodyweightReps() {
        val result = Achievements.evaluate(input(listOf(set(1, "Pullups", 20.0, 12))))
        assertFalse(result.of("pullups_10").earned)
    }

    @Test
    fun streakAndVarietyAndVolume() {
        // Three workouts a week for four weeks.
        val sets = (0 until 4).flatMap { week -> listOf(0, 2, 4).map { day -> set(1L + week * 7 + day, "Barbell_Squat", 100.0, 10) } }
        val result = Achievements.evaluate(input(sets))
        assertTrue(result.of("streak_4").earned)
        assertFalse(result.of("streak_12").earned)
        assertEquals(12_000.0, result.of("volume_10000").progress, 0.0)
        assertTrue(result.of("volume_10000").earned)
    }

    @Test
    fun catalogueIsUniqueAndSpansEveryTier() {
        val ids = Achievements.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Tier.entries.forEach { tier -> assertTrue("$tier", Achievements.ALL.count { it.def().tier == tier } >= 8) }
        assertTrue(Achievements.ALL.size >= 75)
        assertNull(Achievements.evaluate(input(emptyList())).of("workouts_1").earnedOn)
    }

    private fun AchievementDef.def() = this
}
