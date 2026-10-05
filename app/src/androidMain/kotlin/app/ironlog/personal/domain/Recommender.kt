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
    /** Muscles to bring up (e.g. from a physique check); they get extra sets and an extra exercise. */
    val priorityMuscles: List<String> = emptyList(),
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
        val usage = mutableMapOf<String, Int>()
        val selected =
            split.focus.map { focus ->
                selectExercises(available, focus, exerciseLimit, usage).toMutableList()
            }
        val coverage = ensureCoverage(available, split.focus, selected, usage)
        val priority = expandPriority(profile.priorityMuscles)
        val extras = emphasize(available, split.focus, selected, usage, priority)
        val days =
            selected.mapIndexed { index, picks ->
                RecommendedDay(
                    name = dayName(split.title, index),
                    exercises =
                        picks.map { exercise ->
                            val strengthCompound =
                                profile.goal.equals("GET_STRONGER", ignoreCase = true) &&
                                    isCompound(exercise)
                            val prioritised = exercise.id in extras || exercise.primaryMuscles.any { normalize(it) in priority }
                            ExercisePrescription(
                                exerciseId = exercise.id,
                                // Coverage accessories get less volume; muscles being brought up get more.
                                sets =
                                    when {
                                        prioritised -> (setCount + 1).coerceAtMost(5)
                                        exercise.id in coverage -> (setCount - 1).coerceAtLeast(2)
                                        else -> setCount
                                    },
                                repMin = if (strengthCompound) 3 else repRange.first,
                                repMax = if (strengthCompound) 6 else repRange.second,
                                restSeconds = if (isCompound(exercise)) 150 else 90,
                            )
                        },
                )
            }
        val equipmentText =
            when {
                profile.equipment.isEmpty() -> "available equipment"
                profile.equipment.size > 4 -> "a full gym"
                profile.equipment == setOf("body only") -> "bodyweight only"
                else -> profile.equipment.sorted().joinToString()
            }
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
        val focusText = if (priority.isEmpty()) "" else " Extra volume for ${priority.take(3).joinToString()}."
        val why =
            "${split.title} fits $daysPerWeek training days and $duration-minute sessions, uses $equipmentText, and supports $goalText.$focusText"
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
                listOf("quadriceps", "chest", "lats", "hamstrings", "shoulders", "middle back", "biceps", "triceps", "calves", "abdominals"),
                listOf("hamstrings", "middle back", "chest", "quadriceps", "shoulders", "lats", "triceps", "biceps", "abdominals", "calves"),
                listOf("chest", "quadriceps", "lats", "glutes", "shoulders", "middle back", "biceps", "triceps", "calves", "abdominals"),
            )
        val upper = listOf("chest", "lats", "shoulders", "middle back", "chest", "biceps", "triceps", "shoulders")
        val lower = listOf("quadriceps", "hamstrings", "glutes", "quadriceps", "hamstrings", "calves", "abdominals", "calves")
        val upperLower = listOf(upper, lower, upper, lower)
        val pushPullLegs =
            listOf(
                listOf("chest", "shoulders", "chest", "shoulders", "triceps", "triceps", "chest", "abdominals"),
                listOf("lats", "middle back", "lats", "shoulders", "biceps", "biceps", "middle back", "traps"),
                listOf("quadriceps", "hamstrings", "glutes", "quadriceps", "hamstrings", "calves", "abdominals", "calves"),
            )
        return when (daysPerWeek.coerceIn(2, 6)) {
            2 -> Split("Full Body 2x", fullBody.take(2))
            3 -> Split("Full Body 3x", fullBody)
            4 -> Split("Upper / Lower", upperLower)
            5 -> Split("Push / Pull / Legs", pushPullLegs)
            6 -> Split("Push / Pull / Legs · 2 cycles", pushPullLegs + pushPullLegs)
            else -> Split("Full Body 3x", fullBody)
        }
    }

    /**
     * Picks one exercise per slot. Staple lifts win, exercises already used earlier in the week
     * are deprioritised so repeated days get variety, then compound movements, then ID for a
     * deterministic tie-break.
     */
    private fun selectExercises(
        options: List<ExerciseOption>,
        priorityMuscles: List<String>,
        limit: Int,
        usage: MutableMap<String, Int>,
    ): List<ExerciseOption> {
        val remaining = options.toMutableList()
        val chosen = mutableListOf<ExerciseOption>()
        priorityMuscles.forEach { target ->
            if (chosen.size >= limit) return@forEach
            val exercise =
                remaining
                    .filter { option -> option.primaryMuscles.any { Staples.matches(target, normalize(it)) } }
                    .minWithOrNull(
                        compareBy<ExerciseOption> { usage[it.id] ?: 0 }
                            .thenBy { Staples.rank(it.id) }
                            .thenByDescending { isCompound(it) }
                            .thenBy { it.id }
                    )
            if (exercise != null) {
                chosen += exercise
                remaining.remove(exercise)
                usage[exercise.id] = (usage[exercise.id] ?: 0) + 1
            }
        }
        return chosen
    }

    /** Every muscle group in the exercise library, in the order coverage gaps are filled. */
    val ALL_MUSCLES =
        listOf(
            "chest",
            "lats",
            "middle back",
            "shoulders",
            "quadriceps",
            "hamstrings",
            "glutes",
            "biceps",
            "triceps",
            "calves",
            "abdominals",
            "lower back",
            "traps",
            "forearms",
            "adductors",
            "abductors",
            "neck",
        )

    /** Muscles a missing group is trained alongside; used to choose the day it is added to. */
    private val companions =
        mapOf(
            "chest" to setOf("chest", "shoulders", "triceps"),
            "lats" to setOf("lats", "middle back", "biceps"),
            "middle back" to setOf("lats", "middle back", "biceps"),
            "shoulders" to setOf("shoulders", "chest", "triceps"),
            "quadriceps" to setOf("quadriceps", "hamstrings", "glutes"),
            "hamstrings" to setOf("hamstrings", "quadriceps", "glutes"),
            "glutes" to setOf("glutes", "hamstrings", "quadriceps"),
            "biceps" to setOf("biceps", "lats", "middle back"),
            "triceps" to setOf("triceps", "chest", "shoulders"),
            "calves" to setOf("calves", "quadriceps", "hamstrings"),
            "abdominals" to setOf("abdominals", "quadriceps", "hamstrings"),
            "lower back" to setOf("hamstrings", "glutes", "middle back"),
            "traps" to setOf("middle back", "lats", "shoulders"),
            "forearms" to setOf("biceps", "lats", "middle back"),
            "adductors" to setOf("quadriceps", "glutes", "hamstrings"),
            "abductors" to setOf("glutes", "quadriceps", "hamstrings"),
            "neck" to setOf("shoulders", "traps", "middle back"),
        )

    /**
     * Guarantees that each muscle group with at least one usable exercise is trained once per
     * week. A missing group is added to the day whose template is closest to it (fewest
     * exercises breaks ties), even if that day goes past the session-length estimate.
     * Returns the IDs that were added this way.
     */
    private fun ensureCoverage(
        options: List<ExerciseOption>,
        focus: List<List<String>>,
        selected: List<MutableList<ExerciseOption>>,
        usage: MutableMap<String, Int>,
    ): Set<String> {
        if (selected.isEmpty()) return emptySet()
        val added = mutableSetOf<String>()
        ALL_MUSCLES.forEach { muscle ->
            val covered =
                selected.any { day -> day.any { ex -> ex.primaryMuscles.any { normalize(it) == muscle } } }
            if (covered) return@forEach
            val related = companions[muscle].orEmpty()
            val dayIndex =
                selected.indices.minWithOrNull(
                    compareByDescending<Int> { i -> focus[i].count { it in related } }
                        .thenBy { i -> selected[i].size }
                        .thenBy { it }
                ) ?: return@forEach
            val exercise =
                options
                    .filter { option ->
                        option !in selected[dayIndex] &&
                            option.primaryMuscles.any { normalize(it) == muscle }
                    }
                    .minWithOrNull(
                        compareBy<ExerciseOption> { usage[it.id] ?: 0 }
                            .thenBy { Staples.rank(it.id) }
                            .thenByDescending { isCompound(it) }
                            .thenBy { it.id }
                    ) ?: return@forEach
            selected[dayIndex] += exercise
            usage[exercise.id] = (usage[exercise.id] ?: 0) + 1
            added += exercise.id
        }
        return added
    }

    /** "arms" covers both biceps and triceps; unknown names are dropped. */
    private fun expandPriority(muscles: List<String>): List<String> =
        muscles.map(::normalize).flatMap { if (it == "arms") listOf("biceps", "triceps") else listOf(it) }.filter { it in ALL_MUSCLES }.distinct()

    /**
     * For the two most important [priority] muscles, adds a second exercise on the day whose
     * template suits it best, so the muscle is trained from another angle each week.
     */
    private fun emphasize(
        options: List<ExerciseOption>,
        focus: List<List<String>>,
        selected: List<MutableList<ExerciseOption>>,
        usage: MutableMap<String, Int>,
        priority: List<String>,
    ): Set<String> {
        if (selected.isEmpty()) return emptySet()
        val added = mutableSetOf<String>()
        priority.take(2).forEach { muscle ->
            val related = companions[muscle].orEmpty()
            val dayIndex =
                selected.indices.minWithOrNull(
                    compareByDescending<Int> { i -> focus[i].count { it == muscle } }
                        .thenByDescending { i -> focus[i].count { it in related } }
                        .thenBy { i -> selected[i].size }
                        .thenBy { it }
                ) ?: return@forEach
            val exercise =
                options
                    .filter { option -> option !in selected[dayIndex] && option.primaryMuscles.any { normalize(it) == muscle } }
                    .minWithOrNull(
                        compareBy<ExerciseOption> { usage[it.id] ?: 0 }
                            .thenBy { Staples.rank(it.id) }
                            .thenByDescending { isCompound(it) }
                            .thenBy { it.id }
                    ) ?: return@forEach
            selected[dayIndex] += exercise
            usage[exercise.id] = (usage[exercise.id] ?: 0) + 1
            added += exercise.id
        }
        return added
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
            else -> "Full Body ${'A' + index}"
        }

    private fun normalize(value: String): String = value.trim().lowercase().replace('_', ' ')
}
