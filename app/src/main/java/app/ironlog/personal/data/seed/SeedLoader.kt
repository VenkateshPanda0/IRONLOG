package app.ironlog.personal.data.seed

import app.ironlog.personal.data.db.*
import androidx.room.withTransaction
import android.content.Context
import kotlinx.serialization.json.*

/** Stable, bundled records only. No network access and no invented nutrient values. */
class SeedLoader(private val context:Context,private val db:IronlogDatabase) {
    suspend fun load()=db.withTransaction {
        // No dataset assets are bundled in this handoff. Stable IDs and IGNORE conflict handling
        // keep the loader idempotent when a licensed exercise asset is added later.
        val asset=runCatching { context.assets.open("seed/exercises.json").bufferedReader().use { it.readText() } }.getOrNull()
        if(asset!=null) {
            val exercises=Json.parseToJsonElement(asset).jsonArray
            val dao=db.dao()
            exercises.forEach { item ->
                val row=item.jsonObject
                val id=row["id"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                val name=row["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                dao.putExercise(ExerciseEntity(id=id,name=name,category=row["category"]?.jsonPrimitive?.contentOrNull.orEmpty(),equipment=row["equipment"]?.jsonPrimitive?.contentOrNull,primaryMuscles=row["primaryMuscles"]?.toString()?:"[]",instructions=row["instructions"]?.toString()?:"[]"))
            }
        }
    }
}
