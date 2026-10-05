package app.ironlog.personal.data.seed

import app.ironlog.personal.platform.AssetReader

import app.ironlog.personal.data.transaction
import app.ironlog.personal.data.db.ExerciseEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SeedLoader(
    private val assets: AssetReader,
    private val database: IronlogDatabase,
) {
    suspend fun load(): Int = database.transaction {
        val source = assets.readText(EXERCISE_ASSET)
        val records = Json.parseToJsonElement(source).jsonArray
        var inserted = 0
        val dao = database.dao()

        records.forEach { element ->
            val values = element.jsonObject
            val id = values["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val name = values["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
            val row =
                ExerciseEntity(
                    id = id,
                    name = name,
                    category = values["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    force = values["force"]?.jsonPrimitive?.contentOrNull,
                    level = values["level"]?.jsonPrimitive?.contentOrNull,
                    mechanic = values["mechanic"]?.jsonPrimitive?.contentOrNull,
                    equipment = values["equipment"]?.jsonPrimitive?.contentOrNull,
                    primaryMuscles = values["primaryMuscles"]?.toString() ?: "[]",
                    secondaryMuscles = values["secondaryMuscles"]?.toString() ?: "[]",
                    instructions = values["instructions"]?.toString() ?: "[]",
                    imagePaths = values["images"]?.toString() ?: "[]",
                )
            if (dao.putExercise(row) != -1L) inserted += 1
        }
        inserted
    }

    private companion object {
        const val EXERCISE_ASSET = "seed/exercises.json"
    }
}
