package app.ironlog.personal.data.seed

import app.ironlog.personal.data.db.*
import androidx.room.withTransaction

/** Stable, bundled records only. No network access and no invented nutrient values. */
class SeedLoader(private val db:IronlogDatabase) {
    suspend fun load()=db.withTransaction {
        val dao=db.dao()
        listOf("pushup" to "Push-up","squat" to "Bodyweight squat","row" to "Dumbbell row","hinge" to "Hip hinge","press" to "Overhead press","plank" to "Plank").forEach { (id,name) -> dao.putExercise(ExerciseEntity(id,name,category="Strength",equipment="Body weight")) }
        // Built-in templates intentionally stay empty until the licensed seed data is present.
    }
}
