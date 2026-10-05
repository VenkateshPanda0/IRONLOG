package app.ironlog.personal.data.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Exercise list columns are stored as JSON string arrays; malformed values read as empty. */
private fun jsonList(value: String): List<String> =
    runCatching { Json.parseToJsonElement(value).jsonArray.map { it.jsonPrimitive.content } }
        .getOrDefault(emptyList())

val ExerciseEntity.primaryMuscleList: List<String>
    get() = jsonList(primaryMuscles)

val ExerciseEntity.secondaryMuscleList: List<String>
    get() = jsonList(secondaryMuscles)

val ExerciseEntity.instructionList: List<String>
    get() = jsonList(instructions)

/** Generated programs are stored with a " (Recommended)" suffix; the UI shows a Custom tag instead. */
val ProgramEntity.displayName: String
    get() = name.removeSuffix(" (Recommended)")
