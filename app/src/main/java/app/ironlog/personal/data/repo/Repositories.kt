package app.ironlog.personal.data.repo

import androidx.room.withTransaction
import app.ironlog.personal.data.db.*
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.WorkoutMath
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class WorkoutRepository(private val db: IronlogDatabase) {
    private val dao = db.dao()
    val active = dao.activeSession()
    val history = dao.history()
    val exercises = dao.exercises()

    fun sessionExercises(sessionId: Long) = dao.sessionExercises(sessionId)

    suspend fun session(sessionId: Long) = dao.session(sessionId)

    suspend fun sets(sessionExerciseId: Long) = dao.setsOnce(sessionExerciseId)

    suspend fun addCustomExercise(name: String, id: String) =
        dao.putExercise(ExerciseEntity(id = id, name = name, isCustom = true))

    suspend fun addCustomExercise(name: String, muscle: String, equipment: String?): String {
        val id = "custom_${java.util.UUID.randomUUID()}"
        dao.putExercise(
            ExerciseEntity(
                id = id,
                name = name,
                category = "strength",
                equipment = equipment,
                primaryMuscles = kotlinx.serialization.json.JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(muscle))).toString(),
                isCustom = true,
            )
        )
        return id
    }

    fun exercise(id: String) = dao.observeExercise(id)

    fun exerciseHistory(id: String) = dao.exerciseHistory(id)

    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setExerciseFavorite(id, favorite)

    suspend fun deleteCustomExercise(id: String) = dao.deleteCustomExercise(id)

    suspend fun start(
        name: String,
        rows: List<Triple<String, String, Int>>,
        programId: Long? = null,
        day: String? = null,
    ) = db.withTransaction {
        dao.startWorkout(
            name,
            programId,
            day,
            rows.mapIndexed { index, (exerciseId, exerciseName, sets) ->
                Triple(
                    exerciseId,
                    exerciseName,
                    ProgramDayExerciseEntity(
                        programDayId = 0,
                        exerciseId = exerciseId,
                        orderIndex = index,
                        targetSets = sets,
                        repMin = 8,
                        repMax = 12,
                        restSeconds = 90,
                    ),
                )
            },
        )
    }

    suspend fun completeSet(id: Long, weightKg: Double?, reps: Int?) {
        dao.set(id)?.let {
            dao.updateSet(
                it.copy(
                    weightKg = weightKg,
                    reps = reps,
                    isCompleted = true,
                    completedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    suspend fun saveSetDraft(id: Long, weightKg: Double?, reps: Int?) {
        dao.set(id)?.let { dao.updateSet(it.copy(weightKg = weightKg, reps = reps)) }
    }

    suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.set(id)?.let {
            dao.updateSet(
                it.copy(
                    isCompleted = completed,
                    completedAt = if (completed) System.currentTimeMillis() else null,
                )
            )
        }
    }

    suspend fun finish(id: Long) =
        dao.finishWorkoutAndAdvanceProgram(id, System.currentTimeMillis())

    suspend fun pause(id: Long) = dao.pauseSession(id, System.currentTimeMillis())

    suspend fun resume(id: Long) = dao.resumeSession(id, System.currentTimeMillis())

    suspend fun skipExercise(id: Long) = dao.setExerciseStatus(id, "SKIPPED")

    suspend fun unskipExercise(id: Long) = dao.setExerciseStatus(id, "PENDING")

    fun observeSession(id: Long) = dao.observeSession(id)

    fun sessionSets(sessionId: Long) = dao.sessionSets(sessionId)

    val allLoggedSets = dao.allLoggedSets()

    /** Marks a set done; blank fields are filled from [fallbackWeight]/[fallbackReps] (the hint). */
    suspend fun completeWithFallback(id: Long, fallbackWeight: Double?, fallbackReps: Int?) {
        val row = dao.set(id) ?: return
        dao.updateSet(
            row.copy(
                weightKg = row.weightKg ?: fallbackWeight,
                reps = row.reps ?: fallbackReps,
                isCompleted = true,
                completedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun setType(id: Long, type: String) {
        dao.set(id)?.let { dao.updateSet(it.copy(type = type)) }
    }

    /** Adds a drop or rest-pause set right after [setId]; drop sets start at a lighter load. */
    suspend fun addSubSet(setId: Long, type: String): Long? {
        val row = dao.set(setId) ?: return null
        val weight = if (type == "DROP") WorkoutMath.dropWeight(row.weightKg) else row.weightKg
        return dao.insertSetAfter(row.sessionExerciseId, row.setIndex, type, weight)
    }

    suspend fun addSet(exerciseRowId: Long) {
        val index = dao.maxSetIndex(exerciseRowId)
        dao.insertSetAfter(exerciseRowId, index, "WORKING", null)
    }

    suspend fun removeSet(id: Long) = dao.removeSet(id)

    suspend fun addExercise(sessionId: Long, exercise: ExerciseEntity, sets: Int = 3) =
        dao.addExerciseToSession(sessionId, exercise.id, exercise.name, sets)

    /** Swaps the exercise but keeps the logged sets; the first swap remembers the original. */
    suspend fun replaceExercise(rowId: Long, exercise: ExerciseEntity) {
        val row = dao.sessionExercise(rowId) ?: return
        dao.updateSessionExercise(
            row.copy(
                exerciseId = exercise.id,
                exerciseNameSnapshot = exercise.name,
                originalExerciseId = row.originalExerciseId ?: row.exerciseId,
                originalNameSnapshot = row.originalNameSnapshot ?: row.exerciseNameSnapshot,
            )
        )
    }

    suspend fun revertExercise(rowId: Long) {
        val row = dao.sessionExercise(rowId) ?: return
        val original = row.originalExerciseId ?: return
        dao.updateSessionExercise(
            row.copy(
                exerciseId = original,
                exerciseNameSnapshot = row.originalNameSnapshot ?: original,
                originalExerciseId = null,
                originalNameSnapshot = null,
            )
        )
    }

    suspend fun moveExercise(rowId: Long, direction: Int) = dao.moveSessionExercise(rowId, direction)

    suspend fun removeExercise(rowId: Long) = dao.deleteSessionExercise(rowId)

    suspend fun setExerciseNotes(rowId: Long, notes: String) {
        dao.sessionExercise(rowId)?.let { dao.updateSessionExercise(it.copy(notes = notes)) }
    }

    suspend fun setSessionNotes(id: Long, notes: String) = dao.setSessionNotes(id, notes)

    /** Deletes the session and everything logged in it. */
    suspend fun discard(id: Long) = dao.deleteSession(id)
}

enum class FoodFilter(val label: String) {
    ALL("All"),
    INDIAN("Indian"),
    PACKAGED("Packaged"),
    WORLD("World"),
    INGREDIENTS("Ingredients"),
    MINE("Mine");

    fun matches(food: FoodEntity): Boolean =
        when (this) {
            ALL -> true
            INDIAN -> food.cuisine?.startsWith("Indian") == true
            PACKAGED -> food.source == "OFF"
            WORLD -> food.source == "FNDDS"
            INGREDIENTS -> food.source == "USDA"
            MINE -> food.source == "CUSTOM" || food.source == "RECIPE"
        }
}

/** Words that describe a plain food rather than make it a dish ("Bananas, raw", "Milk, whole"). */
private val BASIC_DESCRIPTORS =
    setOf("raw", "fresh", "whole", "cooked", "boiled", "plain", "ripe", "dried", "skim", "nonfat", "lowfat", "white", "brown", "green", "red", "yellow", "uncooked", "unsweetened", "nfs", "regular")

private val POPULAR_INDIAN =
    listOf(
        "INDB:ASC096", // Chapati/Roti
        "INDB:ASC113", // Boiled rice
        "INDB:ASC151", // Moong dal
        "INDB:ASC165", // Rajmah curry
        "INDB:ASC162", // Chickpea curry (chole)
        "INDB:ASC097", // Plain paratha
        "INDB:ASC098", // Aloo paratha
        "INDB:ASC144", // Idli
        "INDB:BFP148", // Plain dosa
        "INDB:ASC146", // Masala dosa
        "INDB:ASC167", // Sambar
        "INDB:BFP044", // Poha
        "INDB:BFP039", // Upma
        "INDB:BFP144", // Khichdi
        "INDB:ASC215", // Palak paneer
        "INDB:ASC191", // Matar paneer
        "INDB:ASC240", // Chicken curry
        "INDB:ASC241", // Tandoori chicken
        "INDB:ASC242", // Butter chicken
        "INDB:OSR139", // Dal makhani
        "INDB:ASC122", // Mutton biryani
        "INDB:ASC114", // Plain pulao
        "INDB:ASC126", // Curd rice
        "INDB:ASC171", // Aloo gobi
        "INDB:ASC056", // Boiled egg
        "INDB:ASC061", // Omelette
        "INDB:BFP240", // Egg curry
        "INDB:OSR100", // Besan chilla
        "INDB:ASC001", // Chai
        "INDB:ASC021", // Sweet lassi
    )

class NutritionRepository(private val dao: IronlogDao) {
    /**
     * Every word must appear in the name or brand, in any order ("chicken breast" finds
     * "Chicken, broilers or fryers, breast"). Ranking: favourites; then foods where every word is a
     * whole word; then everyday Indian staples; then plain foods named by a one-word query; then Indian
     * dishes, Indian packaged products, world dishes and finally raw
     * ingredients; then word-start matches, popularity and shorter names. Scoring runs off the main
     * thread. [filter] narrows to one group (see [FoodFilter]).
     */
    fun foods(q: String, filter: FoodFilter = FoodFilter.ALL): Flow<List<FoodEntity>> {
        val words = searchWords(q)
        if (words.isEmpty()) return flowOf(emptyList())
        val anchor = words.maxBy { it.length }
        return dao.searchFoods(anchor)
            .map { rows ->
                rows
                    .filter { filter.matches(it) }
                    .mapNotNull { food ->
                        val text = (food.name + " " + food.brand.orEmpty()).lowercase()
                        if (!words.all { it in text }) return@mapNotNull null
                        val nameWords = searchWords(food.name + " " + food.brand.orEmpty())
                        val whole = words.all { w -> nameWords.any { it == w || it == w + "s" } }
                        // "Bananas, raw" for "banana": a short name led by the query is the basic food.
                        val basic =
                            (food.source == "USDA" || food.source == "FNDDS") && words.size == 1 && nameWords.size <= 3 &&
                                nameWords.first().let { it == words[0] || it == words[0] + "s" } &&
                                nameWords.drop(1).all { it in BASIC_DESCRIPTORS }
                        Ranked(food, whole, basic, sourceRank(food), matchScore(nameWords, words))
                    }
                    .sortedWith(
                        compareByDescending<Ranked> { it.food.isFavorite }
                            .thenByDescending { it.whole }
                            // Everyday Indian staples first, in curated order (boiled egg before egg nog)...
                            .thenBy { POPULAR_INDIAN.indexOf(it.food.sourceRef).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                            // ...then plain foods named by the query ("Bananas, raw"), then by source.
                            .thenByDescending { it.basic }
                            .thenBy { it.sourceRank }
                            .thenByDescending { it.score }
                            .thenByDescending { it.food.popularity }
                            .thenBy { it.food.name.length }
                    )
                    .take(100)
                    .map { it.food }
            }
            .flowOn(kotlinx.coroutines.Dispatchers.Default)
    }

    private class Ranked(val food: FoodEntity, val whole: Boolean, val basic: Boolean, val sourceRank: Int, val score: Int)

    /** Lower ranks first: the user's own foods, Indian dishes, Indian packaged, world, ingredients. */
    private fun sourceRank(food: FoodEntity): Int =
        when {
            food.source == "CUSTOM" || food.source == "RECIPE" -> 0
            food.source == "INDB" -> 1
            food.source == "OFF" && food.cuisine == "Indian (packaged)" -> 2
            food.source == "FNDDS" && food.cuisine == "Indian" -> 2
            food.source == "FNDDS" -> 3
            food.source == "OFF" -> 4
            else -> 5
        }

    /** Everyday Indian dishes shown before anything is typed, in this order. */
    val popularIndian: Flow<List<FoodEntity>> =
        dao.foodsByRefs(POPULAR_INDIAN).map { rows -> rows.sortedBy { POPULAR_INDIAN.indexOf(it.sourceRef) } }

    private fun searchWords(text: String) =
        text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * 2 points per query word that is a whole word of the name, 1 per word it starts, and 3 more
     * when it is the name's first word (USDA names lead with the food: "Egg, whole, raw").
     */
    private fun matchScore(nameWords: List<String>, query: List<String>): Int =
        query.count { q -> nameWords.firstOrNull().let { it == q || it == q + "s" } } * 3 +
            query.sumOf { q ->
            when {
                nameWords.any { it == q || it == q + "s" } -> 2
                nameWords.any { it.startsWith(q) } -> 1
                else -> 0
            }.toInt()
        }

    fun meals(date: LocalDate) = dao.meals(date.toString())

    suspend fun addCustom(name: String, kcal: Double, protein: Double, carbs: Double, fat: Double) =
        dao.addFood(
            FoodEntity(
                name = name,
                kcalPer100g = kcal,
                proteinPer100g = protein,
                carbsPer100g = carbs,
                fatPer100g = fat,
            )
        )

    suspend fun log(food: FoodEntity, date: LocalDate, meal: String, grams: Double) =
        dao.addMeal(
            MealEntryEntity(
                date = date.toString(),
                mealType = meal,
                foodId = food.id,
                foodNameSnapshot = food.name,
                grams = grams,
                kcal = Calculations.foodMacro(food.kcalPer100g, grams),
                protein = Calculations.foodMacro(food.proteinPer100g, grams),
                carbs = Calculations.foodMacro(food.carbsPer100g, grams),
                fat = Calculations.foodMacro(food.fatPer100g, grams),
                fiber = food.fiberPer100g?.let { Calculations.foodMacro(it, grams) },
            )
        )

    val favorites = dao.favoriteFoods()
    val recent = dao.recentFoods()
    val myFoods = dao.myFoods()

    fun food(id: Long) = dao.observeFood(id)

    fun servings(foodId: Long) = dao.servings(foodId)

    fun dailyTotals(start: LocalDate, end: LocalDate) = dao.dailyTotals(start.toString(), end.toString())

    suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFoodFavorite(id, favorite)

    /** Rescales a logged entry to a new weight; values stay proportional to what was logged. */
    suspend fun updateGrams(entryId: Long, grams: Double, mealType: String) {
        val entry = dao.meal(entryId) ?: return
        if (grams <= 0 || entry.grams <= 0) return
        val factor = grams / entry.grams
        dao.updateMeal(
            entry.copy(
                grams = grams,
                mealType = mealType,
                kcal = entry.kcal * factor,
                protein = entry.protein * factor,
                carbs = entry.carbs * factor,
                fat = entry.fat * factor,
                fiber = entry.fiber?.times(factor),
            )
        )
    }

    suspend fun deleteEntry(entryId: Long) = dao.deleteMeal(entryId)

    /** Saves a recipe as a food with per-100 g values and a "1 serving" portion. */
    suspend fun addRecipe(name: String, ingredients: List<Pair<FoodEntity, Double>>, servings: Int): Long? {
        val totals =
            app.ironlog.personal.domain.Nutrition.recipe(
                ingredients.map { (food, grams) ->
                    app.ironlog.personal.domain.Ingredient(grams, food.kcalPer100g, food.proteinPer100g, food.carbsPer100g, food.fatPer100g)
                },
                servings,
            ) ?: return null
        val id =
            dao.addFood(
                FoodEntity(
                    name = name,
                    source = "RECIPE",
                    kcalPer100g = totals.kcalPer100g,
                    proteinPer100g = totals.proteinPer100g,
                    carbsPer100g = totals.carbsPer100g,
                    fatPer100g = totals.fatPer100g,
                    confidence = "USER",
                )
            )
        dao.addServing(FoodServingEntity(foodId = id, label = "1 serving", grams = totals.gramsPerServing))
        return id
    }

    suspend fun copyDay(from: LocalDate, to: LocalDate) =
        dao.mealsOnce(from.toString()).forEach {
            dao.addMeal(
                it.copy(id = 0, date = to.toString(), createdAt = System.currentTimeMillis())
            )
        }
}

class BodyRepository(
    private val dao: IronlogDao,
    private val photoDir: java.io.File,
    /** Told about each deleted weigh-in, so the deletion can reach Health Connect. */
    private val onWeightDeleted: suspend (BodyWeightEntity) -> Unit = {},
) {
    val weights: Flow<List<BodyWeightEntity>> = dao.weights()
    val photos: Flow<List<ProgressPhotoEntity>> = dao.photos()

    suspend fun log(date: LocalDate, kg: Double, note: String? = null) =
        dao.addWeight(BodyWeightEntity(date = date.toString(), weightKg = kg, note = note))

    suspend fun deleteWeight(id: Long) {
        val row = dao.weightById(id) ?: return
        dao.deleteWeight(id)
        onWeightDeleted(row)
    }

    fun photoFile(photo: ProgressPhotoEntity) = java.io.File(photoDir, photo.fileName)

    /**
     * Copies the picked image into app-private storage, scaled so the long edge is at most 1600 px.
     * The original in the user's gallery is not touched.
     */
    suspend fun addPhoto(resolver: android.content.ContentResolver, uri: android.net.Uri, date: LocalDate) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
            val bitmap =
                resolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: error("Could not read the selected image")
            photoDir.mkdirs()
            val name = "photo_${java.util.UUID.randomUUID()}.jpg"
            java.io.File(photoDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, it) }
            bitmap.recycle()
            dao.addPhoto(ProgressPhotoEntity(date = date.toString(), fileName = name))
        }

    suspend fun deletePhoto(photo: ProgressPhotoEntity) {
        dao.deletePhoto(photo.id)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photoFile(photo).delete() }
    }

    suspend fun deleteAllPhotos() {
        dao.deleteAllPhotos()
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { photoDir.deleteRecursively() }
    }
}

class GoalRepository(private val dao: IronlogDao) {
    val current = dao.goal()

    suspend fun currentOnce(): GoalEntity? = dao.goalOnce()

    suspend fun save(value: GoalEntity) = dao.saveGoal(value)
}

class ProgramRepository(private val db: IronlogDatabase) {
    private val dao = db.dao()
    val programs = dao.programs()
    val active = dao.activeProgram()
    val exerciseOptions = dao.exercises()

    fun days(programId: Long): Flow<List<ProgramDayEntity>> = dao.observeDays(programId)

    fun prescriptions(dayId: Long): Flow<List<ProgramDayExerciseEntity>> =
        dao.observePrescriptions(dayId)

    fun prescriptionsForDays(
        days: List<ProgramDayEntity>
    ): Flow<Map<Long, List<ProgramDayExerciseEntity>>> {
        if (days.isEmpty()) return flowOf(emptyMap())
        val dayFlows = days.map { day ->
            dao.observePrescriptions(day.id).combine(flowOf(day.id)) { rows, id -> id to rows }
        }
        return combine(dayFlows) { rows -> rows.toMap() }
    }

    suspend fun activate(id: Long) =
        dao.activate(ActiveProgramEntity(programId = id, startDate = LocalDate.now().toString()))

    suspend fun renameRecommended(id: Long, name: String, description: String): Unit {
        val program = dao.programOnce(id) ?: error("Program missing")
        require(!program.isBuiltIn) { "Built-in programs are read-only." }
        val nameConflict = dao.programByName("$name (Recommended)")
        require(nameConflict == null || nameConflict.id == id) {
            "A program with that name already exists."
        }
        dao.updateProgram(program.copy(name = "$name (Recommended)", description = description))
    }

    suspend fun nextDay(programId: Long): ProgramDayEntity {
        val activeProgram = dao.activeProgramOnce()?.takeIf { it.programId == programId }
        val programDays = dao.days(programId)
        require(programDays.isNotEmpty()) { "This program has no scheduled days." }
        val currentIndex = activeProgram?.currentDay?.minus(1) ?: 0
        return programDays.getOrNull(currentIndex.mod(programDays.size)) ?: programDays.first()
    }

    suspend fun saveRecommended(
        name: String,
        description: String,
        daysPerWeek: Int,
        days: List<Pair<String, List<ProgramDayExerciseEntity>>>,
    ): Long = db.withTransaction {
        val programName = "$name (Recommended)"
        val existing = dao.programByName(programName)
        val programId =
            if (existing == null) {
                val inserted =
                    dao.addProgram(
                        ProgramEntity(
                            name = programName,
                            description = description,
                            daysPerWeek = daysPerWeek,
                            isBuiltIn = false,
                        )
                    )
                if (inserted != -1L) inserted
                else
                    dao.programByName(programName)?.id
                        ?: error("Could not save recommended program")
            } else {
                require(!existing.isBuiltIn) {
                    "Built-in programs are read-only; duplicate it before editing."
                }
                dao.updateProgram(
                    existing.copy(description = description, daysPerWeek = daysPerWeek)
                )
                dao.deleteProgramDays(existing.id)
                existing.id
            }
        days.forEachIndexed { dayIndex, (dayName, exercises) ->
            val dayId =
                dao.addProgramDay(
                    ProgramDayEntity(programId = programId, dayIndex = dayIndex + 1, name = dayName)
                )
            exercises.forEach { exercise ->
                dao.addPrescription(exercise.copy(id = 0, programDayId = dayId))
            }
        }
        programId
    }

    suspend fun start(id: Long, dayId: Long): Long {
        val program = dao.program(id) ?: error("Program missing")
        val day =
            dao.programDay(dayId)?.takeIf { it.programId == id } ?: error("Program day missing")
        val rows =
            dao.prescriptions(day.id).map { p ->
                val ex = dao.exercise(p.exerciseId)
                Triple(p.exerciseId, ex?.name ?: p.exerciseId, p)
            }
        return db.withTransaction {
            val sessionId =
                dao.startWorkout(
                    // Day first so the workout header reads "Push · PPL" rather than the program.
                    "${day.name} · ${program.name.removeSuffix(" (Recommended)")}",
                    id,
                    day.name,
                    rows,
                )
            sessionId
        }
    }
}
