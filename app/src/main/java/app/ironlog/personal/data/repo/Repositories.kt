package app.ironlog.personal.data.repo

import androidx.room.withTransaction
import app.ironlog.personal.data.db.*
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.WorkoutMath
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

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

class NutritionRepository(private val dao: IronlogDao) {
    fun foods(q: String) = dao.searchFoods(q)

    fun meals(date: LocalDate) = dao.meals(date.toString())

    fun searchFoods(query: String) = dao.searchFoods(query)

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

    suspend fun copyDay(from: LocalDate, to: LocalDate) =
        dao.mealsOnce(from.toString()).forEach {
            dao.addMeal(
                it.copy(id = 0, date = to.toString(), createdAt = System.currentTimeMillis())
            )
        }
}

class BodyRepository(private val dao: IronlogDao) {
    val weights: Flow<List<BodyWeightEntity>> = dao.weights()

    suspend fun log(date: LocalDate, kg: Double, note: String? = null) =
        dao.addWeight(BodyWeightEntity(date = date.toString(), weightKg = kg, note = note))
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
