package app.ironlog.personal.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngagementReplayTest {
    @Test
    fun levelThresholdsArePinned() {
        assertEquals(500, EngagementReplay.xpForLevel(1))
        assertEquals(1500, EngagementReplay.xpForLevel(2))
        assertEquals(3000, EngagementReplay.xpForLevel(3))
        assertEquals(0, EngagementReplay.levelFor(499))
        assertEquals(1, EngagementReplay.levelFor(500))
        assertEquals(2, EngagementReplay.levelFor(1500))
    }

    @Test
    fun workoutXpCapsSetBonusAndAddsPrBonus() {
        val summary =
            EngagementReplay.replay(
                listOf(WorkoutXpInput(LocalDate.parse("2026-01-01"), 80, 2, 0.0)),
                emptyList(),
            )
        assertEquals(450, summary.totalXp)
    }

    @Test
    fun allWorkoutMilestonesAndPhotoGoalMedalsAreDerived() {
        val workouts =
            (0 until 250).map { i ->
                WorkoutXpInput(
                    LocalDate.parse("2025-01-01").plusDays(i.toLong()),
                    1,
                    if (i < 100) 1 else 0,
                    if (i == 199) 100000.0 else 0.0,
                )
            }
        val photo = LocalDate.parse("2025-03-01")
        val goal = LocalDate.parse("2025-04-01")
        val result =
            EngagementReplay.replay(workouts, emptyList(), listOf(photo), goal, plannedPerWeek = 1)
        assertEquals(250, result.workouts)
        assertEquals(
            LocalDate.parse("2025-01-01"),
            result.medals.first { it.id == "workouts_1" }.earnedOn,
        )
        assertEquals(
            LocalDate.parse("2025-01-10"),
            result.medals.first { it.id == "workouts_10" }.earnedOn,
        )
        assertEquals(
            LocalDate.parse("2025-01-25"),
            result.medals.first { it.id == "prs_25" }.earnedOn,
        )
        assertEquals(
            LocalDate.parse("2025-07-19"),
            result.medals.first { it.id == "volume_100000kg" }.earnedOn,
        )
        assertEquals(photo, result.medals.first { it.id == "first_progress_photo" }.earnedOn)
        assertEquals(goal, result.medals.first { it.id == "goal_weight_reached" }.earnedOn)
        assertTrue(result.medals.first { it.id == "workouts_250" }.earnedOn != null)
    }

    @Test
    fun trainingAndFoodStreakMedalsNeedConsecutivePeriods() {
        val start = LocalDate.parse("2026-01-05")
        val workouts =
            (0 until 12).map { week -> WorkoutXpInput(start.plusWeeks(week.toLong()), 0, 0, 0.0) }
        val food =
            (0 until 30).map { day ->
                FoodXpInput(start.plusDays(day.toLong()), 2000.0, 2000.0, emptySet())
            }
        val medals =
            EngagementReplay.replay(workouts, food, plannedPerWeek = 1).medals.associate {
                it.id to it.earnedOn
            }
        assertEquals(start.plusWeeks(3), medals["training_4_week_streak"])
        assertEquals(start.plusWeeks(11), medals["training_12_week_streak"])
        assertEquals(start.plusDays(6), medals["food_7_day_streak"])
        assertEquals(start.plusDays(29), medals["food_30_day_streak"])
        assertNull(
            EngagementReplay.replay(workouts.take(3), emptyList(), plannedPerWeek = 1)
                .medals
                .first { it.id == "training_4_week_streak" }
                .earnedOn
        )
    }

    @Test
    fun foodXpUsesFourMealsOrCaloriesWithinFifteenPercent() {
        assertTrue(
            EngagementReplay.isCompleteFoodDay(
                FoodXpInput(
                    LocalDate.now(),
                    0.0,
                    2000.0,
                    setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK"),
                )
            )
        )
        assertTrue(
            EngagementReplay.isCompleteFoodDay(
                FoodXpInput(LocalDate.now(), 1700.0, 2000.0, emptySet())
            )
        )
        assertEquals(
            false,
            EngagementReplay.isCompleteFoodDay(
                FoodXpInput(LocalDate.now(), 1699.0, 2000.0, emptySet())
            ),
        )
    }

    @Test
    fun quickWorkoutGeneratorHonorsTimeEquipmentTargetAndAvoidList() {
        val candidates =
            listOf(
                ExerciseCandidate("1", "Cable row", setOf("back"), setOf("cable")),
                ExerciseCandidate("2", "Band row", setOf("back"), setOf("bands")),
                ExerciseCandidate("3", "Squat", setOf("legs"), setOf("bodyweight")),
            )
        val result =
            QuickWorkoutGenerator.generate(
                candidates,
                setOf("back"),
                setOf("bands"),
                setOf("row"),
                30,
            )
        assertTrue(result.isEmpty())
        val allowed =
            QuickWorkoutGenerator.generate(
                candidates,
                setOf("back"),
                setOf("bands"),
                emptySet(),
                30,
            )
        assertEquals(listOf("2"), allowed.map { it.id })
    }
}
