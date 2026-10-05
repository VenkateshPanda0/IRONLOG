package app.ironlog.personal.domain

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs the recommender against the real bundled exercise library. */
class RecommenderCoverageTest {
    private val library: List<ExerciseOption> by lazy {
        Json.parseToJsonElement(File("src/androidMain/assets/seed/exercises.json").readText())
            .jsonArray
            .map { it.jsonObject }
            .filter { it["category"]?.jsonPrimitive?.contentOrNull == "strength" }
            .map { row ->
                ExerciseOption(
                    id = row.getValue("id").jsonPrimitive.content,
                    name = row.getValue("name").jsonPrimitive.content,
                    primaryMuscles =
                        row.getValue("primaryMuscles").jsonArray.map { it.jsonPrimitive.content }.toSet(),
                    equipment = row["equipment"]?.jsonPrimitive?.contentOrNull,
                    level = row["level"]?.jsonPrimitive?.contentOrNull,
                    mechanic = row["mechanic"]?.jsonPrimitive?.contentOrNull,
                )
            }
    }

    private val gym =
        setOf("barbell", "dumbbell", "cable", "machine", "body only", "kettlebells", "bands", "e-z curl bar", "other")

    private fun musclesTrained(rec: Recommendation): Set<String> {
        val byId = library.associateBy { it.id }
        return rec.days.flatMap { it.exercises }.flatMap { byId.getValue(it.exerciseId).primaryMuscles }.toSet()
    }

    @Test
    fun everyMuscleGroupIsTrainedEachWeekForEverySplit() {
        for (days in 2..6) {
            for (equipment in listOf(gym, setOf("body only"))) {
                val rec =
                    Recommender.suggest(
                        TrainingProfile(daysPerWeek = days, goal = "BUILD_MUSCLE", equipment = equipment, sessionMinutes = 60),
                        library,
                    )
                val reachable =
                    library
                        .filter { Recommender.isAllowed(it, TrainingProfile(days, "BUILD_MUSCLE", equipment)) }
                        .flatMap { it.primaryMuscles }
                        .toSet()
                val missing = reachable - musclesTrained(rec)
                assertTrue("$days days, $equipment: missing $missing", missing.isEmpty())
            }
        }
    }

    @Test
    fun gymLibraryReachesAllSeventeenMuscleGroups() {
        val rec =
            Recommender.suggest(TrainingProfile(daysPerWeek = 3, goal = "BUILD_MUSCLE", equipment = gym), library)
        assertEquals(Recommender.ALL_MUSCLES.toSet(), musclesTrained(rec))
    }

    @Test
    fun staplesLeadAndBackIsTrained() {
        val rec =
            Recommender.suggest(TrainingProfile(daysPerWeek = 5, goal = "BUILD_MUSCLE", equipment = gym), library)
        val (push, pull, legs) = rec.days
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", push.exercises.first().exerciseId)
        assertEquals("Pullups", pull.exercises.first().exerciseId)
        assertEquals("Barbell_Full_Squat", legs.exercises.first().exerciseId)
        val byId = library.associateBy { it.id }
        assertTrue(pull.exercises.any { "middle back" in byId.getValue(it.exerciseId).primaryMuscles })
    }

    @Test
    fun physiquePriorityAddsVolumeForWeakMuscles() {
        val base = TrainingProfile(daysPerWeek = 4, goal = "BUILD_MUSCLE", equipment = gym, sessionMinutes = 60)
        val plain = Recommender.suggest(base, library)
        val focused = Recommender.suggest(base.copy(priorityMuscles = listOf("shoulders", "lats", "quadriceps")), library)
        val byId = library.associateBy { it.id }
        fun sets(rec: Recommendation, muscle: String) =
            rec.days.flatMap { it.exercises }.filter { muscle in byId.getValue(it.exerciseId).primaryMuscles }.sumOf { it.sets }
        assertTrue(sets(focused, "shoulders") >= sets(plain, "shoulders") + 4)
        assertTrue(sets(focused, "lats") > sets(plain, "lats"))
        assertTrue(sets(focused, "quadriceps") > sets(plain, "quadriceps"))
        assertEquals(sets(plain, "biceps"), sets(focused, "biceps"))
        assertTrue(focused.why.contains("Extra volume for shoulders"))
        // Still every muscle each week.
        assertEquals(musclesTrained(plain), musclesTrained(focused))
    }
}
