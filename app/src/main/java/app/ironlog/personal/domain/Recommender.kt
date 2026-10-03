package app.ironlog.personal.domain

enum class ExperienceLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
}

data class TrainingProfile(
    val daysPerWeek: Int,
    val goal: String,
    val equipment: Set<String>,
    val experience: ExperienceLevel = ExperienceLevel.BEGINNER,
    val sessionMinutes: Int = 45,
    val avoidList: Set<String> = emptySet(),
)

data class ExerciseOption(
    val id: String,
    val name: String,
    val primaryMuscles: Set<String>,
    val equipment: String?,
    val level: String?,
    val mechanic: String? = null,
)

data class ExercisePrescription(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class RecommendedDay(val name: String, val exercises: List<ExercisePrescription>)

data class Recommendation(
    val template: String,
    val why: String,
    val days: List<RecommendedDay>,
)

object Recommender {
    data class Split(val title: String, val focus: List<List<String>>)

    fun suggest(profile: TrainingProfile, exercises: List<ExerciseOption>): Recommendation {
        val daysPerWeek = profile.daysPerWeek.coerceIn(2, 6)
        val available = exercises.filter { isAllowed(it, profile) }
        val split = chooseSplit(daysPerWeek)
        val setCount = setsPerExercise(profile.experience)
        val repRange = repRange(profile.goal)
        val exerciseLimit = (profile.sessionMinutes.coerceIn(20, 120) / 9).coerceIn(3, 8)
        val days =
            split.focus.mapIndexed { index, focus ->
                val selected = selectExercises(available, focus, exerciseLimit)
                RecommendedDay(
                    name = dayName(split.title, index),
                    exercises =
                        selected.map { exercise ->
                            val strengthCompound =
                                profile.goal.equals("GET_STRONGER", ignoreCase = true) &&
                                    isCompound(exercise)
                            ExercisePrescription(
                                exerciseId = exercise.id,
                                sets = setCount,
                                repMin = if (strengthCompound) 3 else repRange.first,
                                repMax = if (strengthCompound) 6 else repRange.second,
                                restSeconds = if (isCompound(exercise)) 150 else 90,
                            )
                        },
                )
            }
        val equipmentText =
            profile.equipment.sorted().joinToString().ifEmpty { "available equipment" }
        val goalText =
            when (profile.goal.uppercase()) {
                "LOSE",
                "LOSE_FAT" -> "your fat-loss goal"
                "GAIN",
                "BUILD_MUSCLE" -> "your muscle-building goal"
                "GET_STRONGER" -> "your strength goal"
                else -> "balanced strength training"
            }
        val duration = profile.sessionMinutes.coerceIn(20, 120)
        val why =
            "${split.title} fits $daysPerWeek training days and $duration-minute sessions, uses $equipmentText, and supports $goalText. Exercises are filtered by equipment and your avoid list."
        return Recommendation(split.title, why, days)
    }

    fun suggest(profile: TrainingProfile): Recommendation {
        val split = chooseSplit(profile.daysPerWeek)
        val why =
            "${split.title} fits ${profile.daysPerWeek.coerceIn(2, 6)} training days, your available equipment, and your ${profile.goal.lowercase()} goal."
        return Recommendation(split.title, why, emptyList())
    }

    fun isAllowed(exercise: ExerciseOption, profile: TrainingProfile): Boolean {
        val avoided = profile.avoidList.map { normalize(it) }.filter { it.isNotBlank() }
        val name = normalize(exercise.name)
        if (
            avoided.any { token ->
                name.contains(token) || exercise.primaryMuscles.any { normalize(it) == token }
            }
        ) {
            return false
        }
        val requiredEquipment =
            exercise.equipment?.let(::normalize) ?: return profile.equipment.isEmpty()
        return profile.equipment.isEmpty() ||
            profile.equipment.any { normalize(it) == requiredEquipment }
    }

    fun chooseSplit(daysPerWeek: Int): Split {
        val fullBody =
            listOf(
                listOf(
                    "quadriceps",
                    "chest",
                    "back",
                    "hamstrings",
                    "shoulders",
                    "glutes",
                    "biceps",
                    "triceps",
                    "calves",
                    "abdominals",
                ),
                listOf(
                    "hamstrings",
                    "back",
                    "chest",
                    "quadriceps",
                    "glutes",
                    "shoulders",
                    "triceps",
                    "biceps",
                    "abdominals",
                    "calves",
                ),
                listOf(
                    "chest",
                    "quadriceps",
                    "back",
                    "glutes",
                    "hamstrings",
                    "shoulders",
                    "biceps",
                    "triceps",
                    "calves",
                    "abdominals",
                ),
            )
        val upperLower =
            listOf(
                listOf("chest", "back", "shoulders", "biceps", "triceps"),
                listOf("quadriceps", "hamstrings", "glutes", "calves", "abdominals"),
                listOf("chest", "back", "shoulders", "biceps", "triceps"),
                listOf("quadriceps", "hamstrings", "glutes", "calves", "abdominals"),
            )
        val pushPullLegs =
            listOf(
                listOf("chest", "shoulders", "triceps"),
                listOf("back", "biceps"),
                listOf("quadriceps", "hamstrings", "glutes", "calves"),
            )
        return when (daysPerWeek.coerceIn(2, 6)) {
            2 -> Split("Full Body 3x", fullBody.take(2))
            3 -> Split("Full Body 3x", fullBody)
            4 -> Split("Upper / Lower", upperLower)
            5 -> Split("Push / Pull / Legs", pushPullLegs)
            6 -> Split("Push / Pull / Legs · 2 cycles", pushPullLegs + pushPullLegs)
            else -> Split("Full Body 3x", fullBody)
        }
    }

    private fun selectExercises(
        options: List<ExerciseOption>,
        priorityMuscles: List<String>,
        limit: Int,
    ): List<ExerciseOption> {
        val remaining = options.toMutableList()
        val chosen = mutableListOf<ExerciseOption>()
        priorityMuscles.forEach { target ->
            val exercise =
                remaining
                    .filter { option -> option.primaryMuscles.any { normalize(it) == target } }
                    .sortedWith(
                        compareByDescending<ExerciseOption> { isCompound(it) }.thenBy { it.id }
                    )
                    .firstOrNull()
            if (exercise != null && chosen.size < limit) {
                chosen += exercise
                remaining.remove(exercise)
            }
        }
        return chosen
    }

    private fun setsPerExercise(experience: ExperienceLevel): Int =
        when (experience) {
            ExperienceLevel.BEGINNER -> 3
            ExperienceLevel.INTERMEDIATE,
            ExperienceLevel.ADVANCED -> 4
        }

    private fun repRange(goal: String): Pair<Int, Int> =
        when (goal.uppercase()) {
            "GET_STRONGER" -> 6 to 10
            "BUILD_MUSCLE",
            "GAIN" -> 6 to 12
            "LOSE_FAT",
            "LOSE" -> 8 to 15
            else -> 6 to 12
        }

    private fun isCompound(exercise: ExerciseOption): Boolean =
        exercise.mechanic.equals("compound", ignoreCase = true) ||
            exercise.level.equals("compound", ignoreCase = true)

    private fun dayName(title: String, index: Int): String =
        when (title) {
            "Upper / Lower" ->
                if (index % 2 == 0) "Upper ${index / 2 + 1}" else "Lower ${index / 2 + 1}"
            "Push / Pull / Legs" -> listOf("Push", "Pull", "Legs")[index]
            "Push / Pull / Legs · 2 cycles" ->
                "${listOf("Push", "Pull", "Legs")[index % 3]} ${index / 3 + 1}"
            else -> "Full Body ${index + 1}"
        }

    private fun normalize(value: String): String = value.trim().lowercase().replace('_', ' ')
}
