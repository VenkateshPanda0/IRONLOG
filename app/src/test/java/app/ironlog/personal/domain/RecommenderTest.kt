package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    @Test
    fun daysSelectTemplates() {
        assertEquals(
            "Full Body 3x",
            Recommender.suggest(TrainingProfile(1, "MAINTAIN", "GYM")).template,
        )
        assertEquals(
            "Full Body 3x",
            Recommender.suggest(TrainingProfile(3, "GAIN", "GYM")).template,
        )
        assertEquals(
            "Upper / Lower",
            Recommender.suggest(TrainingProfile(4, "LOSE", "GYM")).template,
        )
        assertEquals(
            "Push / Pull / Legs",
            Recommender.suggest(TrainingProfile(5, "MAINTAIN", "GYM")).template,
        )
    }

    @Test
    fun reasonReflectsEquipmentAndGoal() {
        val reason = Recommender.suggest(TrainingProfile(2, "LOSE", "BODYWEIGHT")).why
        assertTrue(reason.contains("bodyweight"))
        assertTrue(reason.contains("losing weight"))
    }

    @Test
    fun daysAreClampedForRecommendation() {
        assertEquals(
            "Full Body 3x",
            Recommender.suggest(TrainingProfile(0, "MAINTAIN", "GYM")).template,
        )
    }
}
