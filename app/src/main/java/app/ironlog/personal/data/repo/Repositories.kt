package app.ironlog.personal.data.repo

import androidx.room.withTransaction
import app.ironlog.personal.data.db.*
import app.ironlog.personal.domain.Calculations
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
                    "${program.name} · ${day.name}",
                    id,
                    day.name,
                    rows,
                )
            sessionId
        }
    }
}
