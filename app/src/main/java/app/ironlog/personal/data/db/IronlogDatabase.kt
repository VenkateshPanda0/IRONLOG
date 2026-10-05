package app.ironlog.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IronlogDao {
    @Query("SELECT * FROM user_profile WHERE id=1") fun profile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id=1") suspend fun profileOnce(): UserProfileEntity?

    @Query("SELECT * FROM goal WHERE id=1") fun goal(): Flow<GoalEntity?>

    @Query("SELECT * FROM goal WHERE id=1") suspend fun goalOnce(): GoalEntity?

    @Query("SELECT * FROM exercise") suspend fun allExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM program") suspend fun allPrograms(): List<ProgramEntity>

    @Query("SELECT * FROM program_day") suspend fun allProgramDays(): List<ProgramDayEntity>

    @Query("SELECT * FROM program_day_exercise")
    suspend fun allPrescriptions(): List<ProgramDayExerciseEntity>

    @Query("SELECT * FROM active_program")
    suspend fun allActivePrograms(): List<ActiveProgramEntity>

    @Query("SELECT * FROM skipped_program_day")
    suspend fun allSkippedProgramDays(): List<SkippedProgramDayEntity>

    @Query("SELECT * FROM skipped_program_day WHERE date BETWEEN :start AND :end")
    fun skippedProgramDays(start: String, end: String): Flow<List<SkippedProgramDayEntity>>

    @Query("SELECT * FROM user_profile") suspend fun allProfiles(): List<UserProfileEntity>

    @Query("SELECT * FROM workout_session") suspend fun allSessions(): List<WorkoutSessionEntity>

    @Query("SELECT * FROM session_exercise")
    suspend fun allSessionExercises(): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_log") suspend fun allSets(): List<SetLogEntity>

    @Query("SELECT * FROM food") suspend fun allFoods(): List<FoodEntity>

    @Query("SELECT * FROM food_serving") suspend fun allServings(): List<FoodServingEntity>

    @Query("SELECT * FROM meal_entry") suspend fun allMeals(): List<MealEntryEntity>

    @Query("SELECT * FROM body_weight") suspend fun allWeights(): List<BodyWeightEntity>

    @Query("SELECT * FROM goal") suspend fun allGoals(): List<GoalEntity>

    @Transaction
    suspend fun backupSnapshot() =
        BackupSnapshot(
            exercises = allExercises(),
            programs = allPrograms(),
            programDays = allProgramDays(),
            prescriptions = allPrescriptions(),
            activePrograms = allActivePrograms(),
            skippedProgramDays = allSkippedProgramDays(),
            profile = allProfiles(),
            sessions = allSessions(),
            sessionExercises = allSessionExercises(),
            sets = allSets(),
            foods = allFoods(),
            servings = allServings(),
            meals = allMeals(),
            weights = allWeights(),
            goals = allGoals(),
            cardio = allCardio(),
            dailyLogs = allDailyLogs(),
            habits = allHabits(),
            habitChecks = allHabitChecks(),
            measurements = allMeasurements(),
            physiqueScans = allPhysiqueScans(),
            photos = allPhotos(),
        )

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePhotos(rows: List<ProgressPhotoEntity>)

    @Query("SELECT * FROM cardio_session") suspend fun allCardio(): List<CardioSessionEntity>

    @Query("SELECT * FROM daily_log") suspend fun allDailyLogs(): List<DailyLogEntity>

    @Query("SELECT * FROM habit") suspend fun allHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_check") suspend fun allHabitChecks(): List<HabitCheckEntity>

    @Query("SELECT * FROM body_measurement") suspend fun allMeasurements(): List<BodyMeasurementEntity>

    @Query("SELECT * FROM physique_scan") suspend fun allPhysiqueScans(): List<PhysiqueScanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreCardio(rows: List<CardioSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreDailyLogs(rows: List<DailyLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreHabits(rows: List<HabitEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreHabitChecks(rows: List<HabitCheckEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreMeasurements(rows: List<BodyMeasurementEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePhysiqueScans(rows: List<PhysiqueScanEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreExercises(rows: List<ExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePrograms(rows: List<ProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreProgramDays(rows: List<ProgramDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restorePrescriptions(rows: List<ProgramDayExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreActivePrograms(rows: List<ActiveProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSkippedProgramDays(rows: List<SkippedProgramDayEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreProfiles(rows: List<UserProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSessions(rows: List<WorkoutSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSessionExercises(rows: List<SessionExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreSets(rows: List<SetLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreFoods(rows: List<FoodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreServings(rows: List<FoodServingEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreMeals(rows: List<MealEntryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreWeights(rows: List<BodyWeightEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun restoreGoals(rows: List<GoalEntity>)

    @Query("DELETE FROM set_log") suspend fun deleteSets()

    @Query("DELETE FROM session_exercise") suspend fun deleteSessionExercises()

    @Query("DELETE FROM workout_session") suspend fun deleteAllSessions()

    @Query("DELETE FROM meal_entry") suspend fun deleteAllMeals()

    @Query("DELETE FROM food_serving") suspend fun deleteAllServings()

    @Query("DELETE FROM food") suspend fun deleteAllFoods()

    @Query("DELETE FROM body_weight") suspend fun deleteAllWeights()

    @Query("DELETE FROM active_program") suspend fun deleteAllActivePrograms()

    @Query("DELETE FROM skipped_program_day") suspend fun deleteAllSkippedProgramDays()

    @Query("DELETE FROM program_day_exercise") suspend fun deleteAllPrescriptions()

    @Query("DELETE FROM program_day") suspend fun deleteAllProgramDays()

    @Query("DELETE FROM program") suspend fun deleteAllPrograms()

    @Query("DELETE FROM exercise") suspend fun deleteAllExercises()

    @Query("DELETE FROM user_profile") suspend fun deleteAllProfiles()

    @Query("DELETE FROM goal") suspend fun deleteAllGoals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(value: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveGoal(value: GoalEntity)

    @Query(
        "SELECT * FROM workout_session WHERE status IN ('IN_PROGRESS','PAUSED') ORDER BY startedAt DESC LIMIT 1"
    )
    fun activeSession(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE status='COMPLETED' ORDER BY startedAt DESC")
    fun history(): Flow<List<WorkoutSessionEntity>>

    @Query("SELECT * FROM workout_session WHERE id=:id")
    suspend fun session(id: Long): WorkoutSessionEntity?

    @Query(
        "SELECT COUNT(*) FROM workout_session WHERE programId=:programId AND programDayName=:dayName AND startedAt>=:start AND startedAt<:end AND status='COMPLETED'"
    )
    suspend fun countCompletedProgramDay(programId: Long, dayName: String, start: Long, end: Long): Int

    @Insert suspend fun startSession(session: WorkoutSessionEntity): Long

    @Insert suspend fun addSessionExercise(value: SessionExerciseEntity): Long

    @Update suspend fun updateSessionExercise(value: SessionExerciseEntity)

    @Insert suspend fun addSet(value: SetLogEntity): Long

    @Query("SELECT * FROM session_exercise WHERE sessionId=:id ORDER BY orderIndex")
    fun sessionExercises(id: Long): Flow<List<SessionExerciseEntity>>

    @Query("SELECT * FROM session_exercise WHERE sessionId=:id ORDER BY orderIndex")
    suspend fun sessionExercisesOnce(id: Long): List<SessionExerciseEntity>

    @Query("SELECT * FROM set_log WHERE sessionExerciseId=:id ORDER BY setIndex")
    fun sets(id: Long): Flow<List<SetLogEntity>>

    @Query("SELECT * FROM set_log WHERE sessionExerciseId=:id ORDER BY setIndex")
    suspend fun setsOnce(id: Long): List<SetLogEntity>

    @Update suspend fun updateSet(value: SetLogEntity)

    @Query("SELECT * FROM set_log WHERE id=:id") suspend fun set(id: Long): SetLogEntity?

    @Query(
        "UPDATE workout_session SET status=:status,endedAt=:ended,lastActiveAt=:now WHERE id=:id"
    )
    suspend fun finishSession(id: Long, status: String, ended: Long?, now: Long)

    @Query(
        "UPDATE workout_session SET status='PAUSED',pausedAt=:now,lastActiveAt=:now WHERE id=:id AND status='IN_PROGRESS'"
    )
    suspend fun pauseSession(id: Long, now: Long)

    @Query(
        "UPDATE workout_session SET status='IN_PROGRESS',totalPausedMs=totalPausedMs+CASE WHEN pausedAt IS NULL THEN 0 ELSE :now-pausedAt END,pausedAt=NULL,lastActiveAt=:now WHERE id=:id AND status='PAUSED'"
    )
    suspend fun resumeSession(id: Long, now: Long)

    @Query("UPDATE session_exercise SET status=:status WHERE id=:id")
    suspend fun setExerciseStatus(id: Long, status: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveRestTimer(value: RestTimerEntity)

    @Query("SELECT * FROM rest_timer WHERE id=1") suspend fun restTimer(): RestTimerEntity?

    @Query("DELETE FROM rest_timer WHERE id=1") suspend fun clearRestTimer()

    /** Candidate rows for one search word; the repository applies the remaining words. */
    @Query(
        "SELECT * FROM food WHERE name LIKE '%' || :q || '%' OR brand LIKE '%' || :q || '%' " +
            "ORDER BY isFavorite DESC, length(name) LIMIT 2000"
    )
    fun searchFoods(q: String): Flow<List<FoodEntity>>

    @Query("SELECT id, sourceRef FROM food WHERE source IN (:sources) AND sourceRef IS NOT NULL")
    suspend fun seededIds(sources: List<String>): List<FoodRef>

    /** Bundled portions are replaced on reseed; user foods and online caches keep theirs. */
    @Query("DELETE FROM food_serving WHERE foodId IN (SELECT id FROM food WHERE source IN (:sources))")
    suspend fun deleteSeededServings(sources: List<String>)

    @Query("UPDATE food SET cuisine = :cuisine, popularity = :popularity WHERE id = :id")
    suspend fun tagFood(id: Long, cuisine: String?, popularity: Int)

    /** Curated everyday Indian dishes shown before the user types anything. */
    @Query("SELECT * FROM food WHERE sourceRef IN (:refs)")
    fun foodsByRefs(refs: List<String>): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE id=:id") fun observeFood(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM food WHERE id=:id") suspend fun food(id: Long): FoodEntity?

    @Query("SELECT * FROM food_serving WHERE foodId=:foodId ORDER BY grams")
    fun servings(foodId: Long): Flow<List<FoodServingEntity>>

    @Query("UPDATE food SET isFavorite=:favorite WHERE id=:id") suspend fun setFoodFavorite(id: Long, favorite: Boolean)

    @Query("SELECT * FROM food WHERE isFavorite=1 ORDER BY name") fun favoriteFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE source IN ('CUSTOM','RECIPE') ORDER BY createdAt DESC") fun myFoods(): Flow<List<FoodEntity>>

    @Query(
        "SELECT f.* FROM food f JOIN meal_entry m ON m.foodId = f.id GROUP BY f.id ORDER BY MAX(m.createdAt) DESC LIMIT 30"
    )
    fun recentFoods(): Flow<List<FoodEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addServing(value: FoodServingEntity): Long

    @Update suspend fun updateMeal(value: MealEntryEntity)

    @Query("DELETE FROM meal_entry WHERE id=:id") suspend fun deleteMeal(id: Long)

    @Query("SELECT * FROM meal_entry WHERE id=:id") suspend fun meal(id: Long): MealEntryEntity?

    @Query("SELECT date, SUM(kcal) AS kcal, SUM(protein) AS protein, SUM(carbs) AS carbs, SUM(fat) AS fat FROM meal_entry WHERE date BETWEEN :start AND :end GROUP BY date")
    fun dailyTotals(start: String, end: String): Flow<List<DailyTotal>>

    /** Every logged day with its calories and the set of meals that have entries. */
    @Query("SELECT date, SUM(kcal) AS kcal, GROUP_CONCAT(DISTINCT mealType) AS mealTypes FROM meal_entry GROUP BY date")
    fun foodDays(): Flow<List<FoodDay>>

    @Insert suspend fun addFood(value: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoodSeed(value: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFoodServingSeed(value: FoodServingEntity): Long

    @Query("SELECT COUNT(*) FROM food WHERE source='USDA'") suspend fun countSeedFoods(): Int

    @Query("SELECT COUNT(*) FROM food_serving") suspend fun countFoodServings(): Int

    @Query("SELECT * FROM food WHERE sourceRef=:ref LIMIT 1")
    suspend fun foodBySourceRef(ref: String): FoodEntity?

    @Insert suspend fun addMeal(value: MealEntryEntity): Long

    @Query("SELECT * FROM meal_entry WHERE date=:date ORDER BY createdAt")
    fun meals(date: String): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entry WHERE date=:date ORDER BY createdAt")
    suspend fun mealsOnce(date: String): List<MealEntryEntity>

    @Query("SELECT * FROM body_weight ORDER BY date") fun weights(): Flow<List<BodyWeightEntity>>

    @Insert suspend fun addWeight(value: BodyWeightEntity): Long

    @Query("DELETE FROM body_weight WHERE id=:id") suspend fun deleteWeight(id: Long)

    @Query("SELECT * FROM progress_photo ORDER BY date DESC, createdAt DESC")
    fun photos(): Flow<List<ProgressPhotoEntity>>

    @Query("SELECT * FROM progress_photo") suspend fun allPhotos(): List<ProgressPhotoEntity>

    @Insert suspend fun addPhoto(value: ProgressPhotoEntity): Long

    @Query("DELETE FROM progress_photo WHERE id=:id") suspend fun deletePhoto(id: Long)

    @Query("DELETE FROM progress_photo") suspend fun deleteAllPhotos()

    // Cardio
    @Insert suspend fun addCardio(value: CardioSessionEntity): Long

    @Query("DELETE FROM cardio_session WHERE id=:id") suspend fun deleteCardio(id: Long)

    @Query("SELECT * FROM cardio_session WHERE id=:id") suspend fun cardioById(id: Long): CardioSessionEntity?

    @Query("SELECT * FROM cardio_session WHERE date BETWEEN :from AND :to") suspend fun cardioBetween(from: String, to: String): List<CardioSessionEntity>

    @Query("SELECT * FROM cardio_session WHERE healthId=:healthId LIMIT 1") suspend fun cardioByHealthId(healthId: String): CardioSessionEntity?

    @Query("SELECT * FROM body_weight WHERE id=:id") suspend fun weightById(id: Long): BodyWeightEntity?

    @Query("SELECT * FROM body_weight WHERE date BETWEEN :from AND :to") suspend fun weightsBetween(from: String, to: String): List<BodyWeightEntity>

    @Query("SELECT * FROM workout_session WHERE status='COMPLETED' AND startedAt >= :fromMs") suspend fun completedSince(fromMs: Long): List<WorkoutSessionEntity>

    @Update suspend fun updateWeight(value: BodyWeightEntity)

    @Query("SELECT * FROM cardio_session ORDER BY date DESC, createdAt DESC") fun cardio(): Flow<List<CardioSessionEntity>>

    // Daily log
    @Query("SELECT * FROM daily_log WHERE date=:date") fun dailyLog(date: String): Flow<DailyLogEntity?>

    @Query("SELECT * FROM daily_log WHERE date=:date") suspend fun dailyLogOnce(date: String): DailyLogEntity?

    @Query("SELECT * FROM daily_log ORDER BY date") fun dailyLogs(): Flow<List<DailyLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveDailyLog(value: DailyLogEntity)

    // Habits
    @Query("SELECT * FROM habit WHERE isActive=1 ORDER BY createdAt") fun habits(): Flow<List<HabitEntity>>

    @Insert suspend fun addHabit(value: HabitEntity): Long

    @Query("UPDATE habit SET isActive=0 WHERE id=:id") suspend fun archiveHabit(id: Long)

    @Query("SELECT * FROM habit_check") fun habitChecks(): Flow<List<HabitCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun checkHabit(value: HabitCheckEntity)

    @Query("DELETE FROM habit_check WHERE habitId=:habitId AND date=:date") suspend fun uncheckHabit(habitId: Long, date: String)

    // Measurements
    @Insert suspend fun addMeasurement(value: BodyMeasurementEntity): Long

    @Query("DELETE FROM body_measurement WHERE id=:id") suspend fun deleteMeasurement(id: Long)

    @Query("SELECT * FROM body_measurement ORDER BY date") fun measurements(): Flow<List<BodyMeasurementEntity>>

    @Query("DELETE FROM cardio_session") suspend fun deleteAllCardio()

    @Query("DELETE FROM daily_log") suspend fun deleteAllDailyLogs()

    @Query("DELETE FROM habit") suspend fun deleteAllHabits()

    @Query("DELETE FROM body_measurement") suspend fun deleteAllMeasurements()

    // Physique checks
    @Insert suspend fun addPhysiqueScan(value: PhysiqueScanEntity): Long

    @Query("SELECT * FROM physique_scan ORDER BY date DESC, createdAt DESC") fun physiqueScans(): Flow<List<PhysiqueScanEntity>>

    @Query("DELETE FROM physique_scan WHERE id=:id") suspend fun deletePhysiqueScan(id: Long)

    @Query("DELETE FROM physique_scan") suspend fun deleteAllPhysiqueScans()

    @Query("UPDATE user_profile SET physiqueGoal=:goal WHERE id=1") suspend fun setPhysiqueGoal(goal: String)

    @Query("SELECT * FROM program ORDER BY isBuiltIn DESC,name")
    fun programs(): Flow<List<ProgramEntity>>

    @Query("SELECT * FROM program WHERE id=:id") suspend fun program(id: Long): ProgramEntity?

    @Query("SELECT * FROM program WHERE name=:name LIMIT 1")
    suspend fun programByName(name: String): ProgramEntity?

    @Query("SELECT * FROM program WHERE id=:id") suspend fun programOnce(id: Long): ProgramEntity?

    @Update suspend fun updateProgram(value: ProgramEntity)

    @Query("DELETE FROM program_day WHERE programId=:programId")
    suspend fun deleteProgramDays(programId: Long)

    @Query("DELETE FROM active_program WHERE programId=:programId")
    suspend fun clearActiveProgramFor(programId: Long)

    @Query("SELECT * FROM program_day WHERE programId=:id ORDER BY weekIndex,dayIndex")
    fun observeDays(id: Long): Flow<List<ProgramDayEntity>>

    @Query("SELECT * FROM program_day_exercise WHERE programDayId=:id ORDER BY orderIndex")
    fun observePrescriptions(id: Long): Flow<List<ProgramDayExerciseEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addProgram(value: ProgramEntity): Long

    @Insert suspend fun addProgramDay(value: ProgramDayEntity): Long

    @Insert suspend fun addPrescription(value: ProgramDayExerciseEntity): Long

    @Query("SELECT * FROM program_day WHERE programId=:id ORDER BY weekIndex,dayIndex")
    suspend fun days(id: Long): List<ProgramDayEntity>

    @Query("SELECT * FROM program_day WHERE id=:id")
    suspend fun programDay(id: Long): ProgramDayEntity?

    @Query("SELECT * FROM program_day WHERE programId=:programId AND name=:name LIMIT 1")
    suspend fun programDay(programId: Long, name: String): ProgramDayEntity?

    @Query("SELECT * FROM program_day_exercise WHERE programDayId=:id ORDER BY orderIndex")
    suspend fun prescriptions(id: Long): List<ProgramDayExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun activate(value: ActiveProgramEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addProgramDaySkip(value: SkippedProgramDayEntity): Long

    @Query("SELECT * FROM active_program WHERE id=1")
    fun activeProgram(): Flow<ActiveProgramEntity?>

    @Query("SELECT * FROM active_program WHERE id=1")
    suspend fun activeProgramOnce(): ActiveProgramEntity?

    @Transaction
    suspend fun finishWorkoutAndAdvanceProgram(id: Long, now: Long) {
        val session = session(id) ?: return
        if (session.status !in setOf("IN_PROGRESS", "PAUSED")) return
        finishSession(id, "COMPLETED", now, now)
        if (session.programId == null || session.programDayName == null) return
        val active = activeProgramOnce()?.takeIf { it.programId == session.programId } ?: return
        val completedDay = programDay(session.programId, session.programDayName) ?: return
        val days = days(session.programId)
        if (days.isEmpty()) return
        val nextDay =
            days
                .indexOfFirst { it.id == completedDay.id }
                .let { index ->
                    if (index < 0) 1 else (index + 1) % days.size + 1
                }
        activate(active.copy(currentWeek = 1, currentDay = nextDay))
    }

    @Query("SELECT * FROM exercise WHERE id=:id") suspend fun exercise(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercise ORDER BY name") fun exercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id=:id") fun observeExercise(id: String): Flow<ExerciseEntity?>

    @Query("UPDATE exercise SET isFavorite=:favorite WHERE id=:id")
    suspend fun setExerciseFavorite(id: String, favorite: Boolean)

    @Query("DELETE FROM exercise WHERE id=:id AND isCustom=1")
    suspend fun deleteCustomExerciseRow(id: String)

    @Query("DELETE FROM program_day_exercise WHERE exerciseId=:id")
    suspend fun deletePrescriptionsFor(id: String)

    /** Programs drop the exercise; logged workouts keep their name snapshot and sets. */
    @Transaction
    suspend fun deleteCustomExercise(id: String) {
        if (exercise(id)?.isCustom != true) return
        deletePrescriptionsFor(id)
        deleteCustomExerciseRow(id)
    }

    @Query(
        LOGGED_SET_COLUMNS +
            "JOIN session_exercise e ON l.sessionExerciseId = e.id " +
            "JOIN workout_session s ON e.sessionId = s.id " +
            "WHERE e.exerciseId = :exerciseId AND s.status = 'COMPLETED' AND l.isCompleted = 1 " +
            "ORDER BY s.startedAt DESC, e.orderIndex, l.setIndex"
    )
    fun exerciseHistory(exerciseId: String): Flow<List<LoggedSet>>

    /** Every completed set in every completed workout, oldest first, for records and stats. */
    @Query(
        LOGGED_SET_COLUMNS +
            "JOIN session_exercise e ON l.sessionExerciseId = e.id " +
            "JOIN workout_session s ON e.sessionId = s.id " +
            "WHERE s.status = 'COMPLETED' AND l.isCompleted = 1 " +
            "ORDER BY s.startedAt, e.orderIndex, l.setIndex"
    )
    fun allLoggedSets(): Flow<List<LoggedSet>>

    @Query("SELECT * FROM session_exercise WHERE id=:id") suspend fun sessionExercise(id: Long): SessionExerciseEntity?

    @Query("SELECT * FROM workout_session WHERE id=:id") fun observeSession(id: Long): Flow<WorkoutSessionEntity?>

    @Query(
        "SELECT l.* FROM set_log l JOIN session_exercise e ON l.sessionExerciseId = e.id " +
            "WHERE e.sessionId = :sessionId ORDER BY e.orderIndex, l.setIndex"
    )
    fun sessionSets(sessionId: Long): Flow<List<SetLogEntity>>

    @Query("UPDATE set_log SET setIndex = setIndex + 1 WHERE sessionExerciseId=:exerciseRowId AND setIndex > :after")
    suspend fun shiftSetsAfter(exerciseRowId: Long, after: Int)

    @Query("UPDATE set_log SET setIndex = setIndex + :by WHERE sessionExerciseId=:exerciseRowId")
    suspend fun shiftAllSets(exerciseRowId: Long, by: Int)

    @Query("UPDATE set_log SET rpe=:rpe WHERE id=:id") suspend fun setRpe(id: Long, rpe: Double?)

    @Query("UPDATE session_exercise SET supersetGroup=:group WHERE id IN (:ids)") suspend fun setSupersetGroup(ids: List<Long>, group: Long?)

    @Query("UPDATE set_log SET setIndex = setIndex - 1 WHERE sessionExerciseId=:exerciseRowId AND setIndex > :removed")
    suspend fun closeSetGap(exerciseRowId: Long, removed: Int)

    @Query("DELETE FROM set_log WHERE id=:id") suspend fun deleteSetRow(id: Long)

    @Query("SELECT COALESCE(MAX(setIndex), 0) FROM set_log WHERE sessionExerciseId=:exerciseRowId")
    suspend fun maxSetIndex(exerciseRowId: Long): Int

    @Query("SELECT COALESCE(MAX(orderIndex), -1) FROM session_exercise WHERE sessionId=:sessionId")
    suspend fun maxOrderIndex(sessionId: Long): Int

    @Query("DELETE FROM session_exercise WHERE id=:id") suspend fun deleteSessionExercise(id: Long)

    @Query("DELETE FROM workout_session WHERE id=:id") suspend fun deleteSession(id: Long)

    @Query("UPDATE workout_session SET notes=:notes WHERE id=:id") suspend fun setSessionNotes(id: Long, notes: String)

    /** Inserts a set directly after [after], shifting later sets down by one. */
    @Transaction
    suspend fun insertSetAfter(exerciseRowId: Long, after: Int, type: String, weightKg: Double?): Long {
        shiftSetsAfter(exerciseRowId, after)
        return addSet(SetLogEntity(sessionExerciseId = exerciseRowId, setIndex = after + 1, type = type, weightKg = weightKg))
    }

    @Transaction
    suspend fun removeSet(id: Long) {
        val row = set(id) ?: return
        deleteSetRow(id)
        closeSetGap(row.sessionExerciseId, row.setIndex)
    }

    @Transaction
    suspend fun addExerciseToSession(sessionId: Long, exerciseId: String, name: String, sets: Int): Long {
        val rowId =
            addSessionExercise(
                SessionExerciseEntity(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    exerciseNameSnapshot = name,
                    orderIndex = maxOrderIndex(sessionId) + 1,
                    targetSets = sets,
                )
            )
        repeat(sets) { addSet(SetLogEntity(sessionExerciseId = rowId, setIndex = it + 1)) }
        return rowId
    }

    /** Swaps two adjacent exercises' order; [direction] is -1 for up and +1 for down. */
    @Transaction
    suspend fun moveSessionExercise(id: Long, direction: Int) {
        val row = sessionExercise(id) ?: return
        val rows = sessionExercisesOnce(row.sessionId)
        val index = rows.indexOfFirst { it.id == id }
        val other = rows.getOrNull(index + direction) ?: return
        updateSessionExercise(row.copy(orderIndex = other.orderIndex))
        updateSessionExercise(other.copy(orderIndex = row.orderIndex))
    }

    @Query("DELETE FROM workout_session") suspend fun clearSessions()

    @Query("DELETE FROM meal_entry") suspend fun clearMeals()

    @Query("DELETE FROM body_weight") suspend fun clearWeights()

    @Query("DELETE FROM user_profile") suspend fun clearProfile()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun putExercise(value: ExerciseEntity): Long

    @Query("SELECT COUNT(*) FROM exercise") suspend fun countExercises(): Int

    @Transaction
    suspend fun startWorkout(
        name: String,
        programId: Long?,
        day: String?,
        entries: List<Triple<String, String, ProgramDayExerciseEntity>>,
    ): Long {
        val sessionId =
            startSession(
                WorkoutSessionEntity(name = name, programId = programId, programDayName = day)
            )
        entries.forEachIndexed { order, (exerciseId, exerciseName, prescription) ->
            val exerciseRow =
                addSessionExercise(
                    SessionExerciseEntity(
                        sessionId = sessionId,
                        exerciseId = exerciseId,
                        exerciseNameSnapshot = exerciseName,
                        orderIndex = order,
                        targetSets = prescription.targetSets,
                        repMin = prescription.repMin,
                        repMax = prescription.repMax,
                        restSeconds = prescription.restSeconds,
                        notes = prescription.notes,
                        sourceProgramDayExerciseId =
                            prescription.id.takeIf { programId != null && it > 0 },
                    )
                )
            repeat(prescription.targetSets) { index ->
                addSet(SetLogEntity(sessionExerciseId = exerciseRow, setIndex = index + 1))
            }
        }
        return sessionId
    }
}

@Database(
    entities =
        [
            ExerciseEntity::class,
            ProgramEntity::class,
            ProgramDayEntity::class,
            ProgramDayExerciseEntity::class,
            ActiveProgramEntity::class,
            SkippedProgramDayEntity::class,
            UserProfileEntity::class,
            WorkoutSessionEntity::class,
            SessionExerciseEntity::class,
            SetLogEntity::class,
            RestTimerEntity::class,
            FoodEntity::class,
            FoodServingEntity::class,
            MealEntryEntity::class,
            BodyWeightEntity::class,
            GoalEntity::class,
            ProgressPhotoEntity::class,
            CardioSessionEntity::class,
            DailyLogEntity::class,
            HabitEntity::class,
            HabitCheckEntity::class,
            BodyMeasurementEntity::class,
            PhysiqueScanEntity::class,
        ],
    version = 9,
    exportSchema = true,
)
abstract class IronlogDatabase : RoomDatabase() {
    abstract fun dao(): IronlogDao

    companion object {
        /** v2: exercise swap tracking on session exercises and the progress photo table. */
        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN originalExerciseId TEXT")
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN originalNameSnapshot TEXT")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS progress_photo (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "fileName TEXT NOT NULL, note TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_progress_photo_date ON progress_photo (date)")
                }
            }

        /** v9: supersets in workouts. */
        val MIGRATION_8_9 =
            object : androidx.room.migration.Migration(8, 9) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE session_exercise ADD COLUMN supersetGroup INTEGER")
                }
            }

        /** v8: shoulder circumference, for tape-measure physique checks. */
        val MIGRATION_7_8 =
            object : androidx.room.migration.Migration(7, 8) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE body_measurement ADD COLUMN shouldersCm REAL")
                }
            }

        /** v7: Health Connect record IDs on imported weigh-ins and cardio sessions. */
        val MIGRATION_6_7 =
            object : androidx.room.migration.Migration(6, 7) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE body_weight ADD COLUMN healthId TEXT")
                    db.execSQL("ALTER TABLE cardio_session ADD COLUMN healthId TEXT")
                }
            }

        /** v6: marks steps and sleep imported from Health Connect, so manual entries are never overwritten. */
        val MIGRATION_5_6 =
            object : androidx.room.migration.Migration(5, 6) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE daily_log ADD COLUMN stepsFromHealth INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE daily_log ADD COLUMN sleepFromHealth INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v5: physique goal on the profile and the physique photo check history. */
        val MIGRATION_4_5 =
            object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE user_profile ADD COLUMN physiqueGoal TEXT NOT NULL DEFAULT ''")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS physique_scan (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, fileName TEXT NOT NULL, " +
                            "goal TEXT NOT NULL, shoulder REAL NOT NULL, waist REAL NOT NULL, hip REAL NOT NULL, " +
                            "leftThigh REAL NOT NULL, rightThigh REAL NOT NULL, height REAL NOT NULL, " +
                            "legToTorso REAL NOT NULL, matchScore INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_physique_scan_date ON physique_scan (date)")
                }
            }

        /** v4: cuisine and popularity on foods for Indian-first search and cuisine filters. */
        val MIGRATION_3_4 =
            object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE food ADD COLUMN cuisine TEXT")
                    db.execSQL("ALTER TABLE food ADD COLUMN popularity INTEGER NOT NULL DEFAULT 0")
                }
            }

        /** v3: cardio, daily log (steps, water, sleep, check-in), habits, measurements, wellness goals. */
        val MIGRATION_2_3 =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE goal ADD COLUMN stepGoal INTEGER NOT NULL DEFAULT 8000")
                    db.execSQL("ALTER TABLE goal ADD COLUMN waterGoalMl INTEGER NOT NULL DEFAULT 3000")
                    db.execSQL("ALTER TABLE goal ADD COLUMN sleepGoalHours REAL NOT NULL DEFAULT 8.0")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS cardio_session (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "type TEXT NOT NULL, durationMin REAL NOT NULL, distanceKm REAL, calories INTEGER, avgHeartRate INTEGER, " +
                            "notes TEXT NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cardio_session_date ON cardio_session (date)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS daily_log (date TEXT NOT NULL, steps INTEGER, waterMl INTEGER NOT NULL, " +
                            "sleepHours REAL, sleepQuality INTEGER, energy INTEGER, soreness INTEGER, stress INTEGER, mood INTEGER, PRIMARY KEY(date))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS habit (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, " +
                            "isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS habit_check (habitId INTEGER NOT NULL, date TEXT NOT NULL, PRIMARY KEY(habitId, date), " +
                            "FOREIGN KEY(habitId) REFERENCES habit(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_check_date ON habit_check (date)")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS body_measurement (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, date TEXT NOT NULL, " +
                            "waistCm REAL, chestCm REAL, armCm REAL, thighCm REAL, hipsCm REAL, neckCm REAL, bodyFatPct REAL, createdAt INTEGER NOT NULL)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_body_measurement_date ON body_measurement (date)")
                }
            }
    }
}

private const val LOGGED_SET_COLUMNS =
    "SELECT s.id AS sessionId, s.startedAt AS startedAt, s.name AS sessionName, " +
        "e.exerciseId AS exerciseId, e.exerciseNameSnapshot AS exerciseName, l.setIndex AS setIndex, " +
        "l.type AS type, l.weightKg AS weightKg, l.reps AS reps, l.rpe AS rpe FROM set_log l "
