package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    @Test
    fun splitMatrixIsStableForTrainingDays() {
        assertEquals("Full Body 2x", Recommender.chooseSplit(2).title)
        assertEquals(2, Recommender.chooseSplit(2).focus.size)
        assertEquals("Full Body 3x", Recommender.chooseSplit(3).title)
        assertEquals("Upper / Lower", Recommender.chooseSplit(4).title)
        assertEquals("Push / Pull / Legs", Recommender.chooseSplit(5).title)
        assertEquals(3, Recommender.chooseSplit(5).focus.size)
        assertEquals("Push / Pull / Legs · 2 cycles", Recommender.chooseSplit(6).title)
        assertEquals(6, Recommender.chooseSplit(6).focus.size)
    }

    @Test
    fun prescriptionsRespectEquipmentAvoidListGoalAndExperience() {
        val exercises =
            listOf(
                ExerciseOption(
                    "bench",
                    "Bench Press",
                    setOf("chest"),
                    "barbell",
                    "beginner",
                    "compound",
                ),
                ExerciseOption(
                    "pushup",
                    "Push Up",
                    setOf("chest"),
                    "body only",
                    "beginner",
                    "compound",
                ),
                ExerciseOption("row", "Cable Row", setOf("back"), "cable", "beginner", "compound"),
                ExerciseOption(
                    "squat",
                    "Barbell Squat",
                    setOf("quadriceps"),
                    "barbell",
                    "beginner",
                    "compound",
                ),
                ExerciseOption(
                    "curl",
                    "Biceps Curl",
                    setOf("biceps"),
                    "dumbbell",
                    "beginner",
                    "isolation",
                ),
            )
        val recommendation =
            Recommender.suggest(
                TrainingProfile(
                    daysPerWeek = 4,
                    goal = "GET_STRONGER",
                    equipment = setOf("barbell", "cable"),
                    experience = ExperienceLevel.ADVANCED,
                    sessionMinutes = 55,
                    avoidList = setOf("row"),
                ),
                exercises,
            )

        assertEquals(4, recommendation.days.size)
        assertTrue(recommendation.days.flatMap { it.exercises }.all { it.sets == 4 })
        assertTrue(recommendation.days.all { it.exercises.isNotEmpty() })
        assertTrue(
            recommendation.days
                .flatMap { it.exercises }
                .filter { it.exerciseId == "pushup" }
                .all { it.repMin == 6 && it.repMax == 10 }
        )
        assertTrue(
            recommendation.days
                .flatMap { it.exercises }
                .filter { it.exerciseId == "bench" }
                .all { it.repMin == 3 && it.repMax == 6 }
        )
        assertFalse(recommendation.days.flatMap { it.exercises }.any { it.exerciseId == "row" })
        assertTrue(recommendation.why.contains("55-minute sessions"))
        assertTrue(recommendation.why.contains("strength goal"))
    }

    @Test
    fun disallowedEquipmentAndAvoidedMusclesAreExcluded() {
        val profile =
            TrainingProfile(
                daysPerWeek = 3,
                goal = "MAINTAIN",
                equipment = setOf("body only"),
                avoidList = setOf("shoulders"),
            )
        assertFalse(
            Recommender.isAllowed(
                ExerciseOption(
                    "press",
                    "Overhead Press",
                    setOf("shoulders"),
                    "body only",
                    "beginner",
                    "compound",
                ),
                profile,
            )
        )
        assertFalse(
            Recommender.isAllowed(
                ExerciseOption(
                    "squat",
                    "Goblet Squat",
                    setOf("quadriceps"),
                    "dumbbell",
                    "beginner",
                    "compound",
                ),
                profile,
            )
        )
    }
}
