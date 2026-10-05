package app.ironlog.personal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.health.HealthIds
import app.ironlog.personal.data.provider.OpenFoodFactsProvider
import app.ironlog.personal.data.repo.BackupRepository
import app.ironlog.personal.data.repo.BodyRepository
import app.ironlog.personal.data.repo.EngagementRepository
import app.ironlog.personal.data.repo.GoalRepository
import app.ironlog.personal.data.repo.NutritionRepository
import app.ironlog.personal.data.repo.PhysiqueRepository
import app.ironlog.personal.data.repo.ProgramRepository
import app.ironlog.personal.data.repo.WellnessRepository
import app.ironlog.personal.data.repo.WorkoutRepository
import app.ironlog.personal.data.seed.FoodSeedLoader
import app.ironlog.personal.data.seed.ProgramSeedLoader
import app.ironlog.personal.data.seed.SeedLoader
import app.ironlog.personal.data.transaction
import app.ironlog.personal.domain.LengthUnit
import app.ironlog.personal.domain.ReminderSettings
import app.ironlog.personal.domain.WeightUnit
import app.ironlog.personal.platform.AssetReader
import app.ironlog.personal.platform.ioDispatcher
import app.ironlog.personal.reminders.ReminderScheduling
import app.ironlog.personal.time.nowMillis
import app.ironlog.personal.timer.RestAlarm
import app.ironlog.personal.timer.RestTimerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import okio.Path

/**
 * Everything the screens use, shared by Android and iOS. Each platform passes in its database,
 * settings store, storage folder, bundled files, reminder scheduler and rest alarm, and may extend
 * this class with platform features (Android adds Health Connect, backups and the live
 * workout notification).
 */
open class AppContainer(
    val db: IronlogDatabase,
    val preferences: DataStore<Preferences>,
    filesDir: Path,
    assets: AssetReader,
    val reminders: ReminderScheduling,
    restAlarm: RestAlarm,
) {
    val workouts = WorkoutRepository(db)
    val nutrition = NutritionRepository(db.dao())
    val body = BodyRepository(db.dao(), filesDir / "photos") { healthDeleted(it.healthId, "weight:" + HealthIds.weight(it.id)) }
    val goals = GoalRepository(db.dao())
    val engagement = EngagementRepository(db.dao())
    val wellness = WellnessRepository(db) { healthDeleted(it.healthId, "session:" + HealthIds.cardio(it.id)) }
    val physique = PhysiqueRepository(db, filesDir / "physique")
    val backup = BackupRepository(db)
    /** Background work that must outlive a screen, such as writing the backup snapshot. */
    val appScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    val programs = ProgramRepository(db)
    val seed = SeedLoader(assets, db)
    val foodSeed = FoodSeedLoader(assets, db)
    val programSeed = ProgramSeedLoader(assets, db)
    val openFoodFacts = OpenFoodFactsProvider(db.dao())
    val restTimer = RestTimerController(db.dao(), restAlarm)
    /** Workout to open when the app is launched from the live workout notification. */
    val openWorkoutRequest = MutableStateFlow<Long?>(null)
    private val mutableSeedState = MutableStateFlow<SeedState>(SeedState.Loading)
    val seedState: StateFlow<SeedState> = mutableSeedState

    fun updateSeedState(state: SeedState): Unit {
        mutableSeedState.value = state
    }

    val profile = db.dao().profile()

    fun dao() = db.dao()

    suspend fun saveProfile(value: app.ironlog.personal.data.db.UserProfileEntity) =
        db.dao().saveProfile(value)

    /** Platform clean-up after a wipe, such as dropping a backup snapshot. */
    protected open suspend fun onPersonalDataCleared() {}

    suspend fun clearPersonalData() {
        db.transaction {
            val dao = db.dao()
            dao.deleteSets()
            dao.deleteSessionExercises()
            dao.deleteAllSessions()
            dao.deleteAllMeals()
            dao.deleteAllServings()
            dao.deleteAllFoods()
            dao.deleteAllWeights()
            dao.deleteAllActivePrograms()
            dao.deleteAllSkippedProgramDays()
            dao.deleteAllPrescriptions()
            dao.deleteAllProgramDays()
            dao.deleteAllPrograms()
            dao.deleteAllExercises()
            dao.deleteAllProfiles()
            dao.deleteAllGoals()
            dao.clearRestTimer()
            dao.deleteAllCardio()
            dao.deleteAllDailyLogs()
            dao.deleteAllHabits()
            dao.deleteAllMeasurements()
        }
        physique.deleteAll()
        body.deleteAllPhotos()
        // The snapshot would otherwise offer the deleted data back on the next onboarding.
        onPersonalDataCleared()
        preferences.edit {
            it.remove(healthIgnoredKey)
            it.remove(healthPendingKey)
            it.remove(healthSyncedAtKey)
            it[healthSyncKey] = false
        }
        // The wipe also removes bundled library rows; restore them so the app stays usable.
        setSeedVersion(0)
        runSeeds()
    }

    /** Loads bundled exercises, foods and programs when the stored seed version is outdated. */
    suspend fun runSeeds() {
        runCatching {
                val currentVersion = seedVersion()
                val shouldLoadExercises = currentVersion < EXERCISE_SEED_VERSION
                val exerciseCount = if (shouldLoadExercises) seed.load() else 0
                val shouldLoadFoods = currentVersion < FOOD_SEED_VERSION
                val foodCounts = if (shouldLoadFoods) foodSeed.load() else null
                val shouldLoadPrograms = currentVersion < PROGRAM_SEED_VERSION
                if (shouldLoadExercises || shouldLoadPrograms) programSeed.load()
                val latestSeedVersion =
                    maxOf(EXERCISE_SEED_VERSION, FOOD_SEED_VERSION, PROGRAM_SEED_VERSION)
                if (currentVersion < latestSeedVersion) setSeedVersion(latestSeedVersion)
                updateSeedState(
                    SeedState.Ready(
                        insertedExercises = exerciseCount,
                        insertedFoods = foodCounts?.insertedFoods ?: 0,
                        insertedServings = foodCounts?.insertedServings ?: 0,
                    )
                )
            }
            .onFailure { error ->
                updateSeedState(SeedState.Failed(error.message ?: "Seed setup failed"))
            }
    }

    private val reminderWorkoutKey = booleanPreferencesKey("reminder_workout")
    private val reminderWorkoutTimeKey = intPreferencesKey("reminder_workout_minutes")
    private val reminderStreakKey = booleanPreferencesKey("reminder_streak")
    private val reminderStreakTimeKey = intPreferencesKey("reminder_streak_minutes")
    private val reminderPromptKey = booleanPreferencesKey("reminder_prompt_dismissed")

    val reminderSettings: Flow<ReminderSettings> =
        preferences.data.map { p ->
            val d = ReminderSettings()
            ReminderSettings(
                workout = p[reminderWorkoutKey] ?: d.workout,
                workoutMinutes = p[reminderWorkoutTimeKey] ?: d.workoutMinutes,
                streak = p[reminderStreakKey] ?: d.streak,
                streakMinutes = p[reminderStreakTimeKey] ?: d.streakMinutes,
            )
        }

    /** True once the Home card offering reminders was answered either way. */
    val reminderPromptDismissed = preferences.data.map { it[reminderPromptKey] ?: false }

    suspend fun saveReminderSettings(value: ReminderSettings) {
        preferences.edit {
            it[reminderWorkoutKey] = value.workout
            it[reminderWorkoutTimeKey] = value.workoutMinutes
            it[reminderStreakKey] = value.streak
            it[reminderStreakTimeKey] = value.streakMinutes
            it[reminderPromptKey] = true
        }
        reminders.apply(value)
    }

    suspend fun dismissReminderPrompt() {
        preferences.edit { it[reminderPromptKey] = true }
    }

    private val healthIgnoredKey = stringSetPreferencesKey("health_ignored")
    private val healthPendingKey = stringSetPreferencesKey("health_pending_deletes")
    private val healthSyncKey = booleanPreferencesKey("health_sync")
    protected val healthSyncedAtKey = longPreferencesKey("health_synced_at")

    /**
     * A deleted import is remembered so it is not imported again; a deleted Ironlog record is
     * queued for removal from the health store at the next sync (only if sync is on).
     */
    private suspend fun healthDeleted(importedId: String?, ownClientId: String) {
        if (importedId != null) preferences.edit { it[healthIgnoredKey] = it[healthIgnoredKey].orEmpty() + importedId }
        else if (healthSyncEnabled.first()) preferences.edit { it[healthPendingKey] = it[healthPendingKey].orEmpty() + ownClientId }
    }

    suspend fun healthIgnored(): Set<String> = preferences.data.first()[healthIgnoredKey].orEmpty()

    suspend fun healthPendingDeletes(): Set<String> = preferences.data.first()[healthPendingKey].orEmpty()

    suspend fun clearHealthPendingDeletes(done: Set<String>) {
        preferences.edit { it[healthPendingKey] = it[healthPendingKey].orEmpty() - done }
    }

    val healthSyncEnabled = preferences.data.map { it[healthSyncKey] ?: false }
    val healthSyncedAt = preferences.data.map { it[healthSyncedAtKey] }

    suspend fun setHealthSync(on: Boolean) {
        preferences.edit {
            it[healthSyncKey] = on
            if (!on) it.remove(healthSyncedAtKey)
        }
    }

    private val weightUnitKey = stringPreferencesKey("weight_unit")

    /** kg or lb for showing and typing weights; data is always stored in kg. */
    val weightUnit = preferences.data.map { WeightUnit.of(it[weightUnitKey]) ?: WeightUnit.defaultFor() }

    suspend fun setWeightUnit(value: WeightUnit) {
        preferences.edit { it[weightUnitKey] = value.name }
    }

    private val lengthUnitKey = stringPreferencesKey("length_unit")

    /** cm/km or in/mi for showing and typing lengths and distances; storage stays cm and km. */
    val lengthUnit = preferences.data.map { LengthUnit.of(it[lengthUnitKey]) ?: LengthUnit.defaultFor() }

    suspend fun setLengthUnit(value: LengthUnit) {
        preferences.edit { it[lengthUnitKey] = value.name }
    }

    private val workoutNotifAskedKey = booleanPreferencesKey("workout_notification_asked")

    /** True the first time only: the live workout notification asks for permission once. */
    suspend fun firstWorkoutNotificationAsk(): Boolean {
        if (preferences.data.first()[workoutNotifAskedKey] == true) return false
        preferences.edit { it[workoutNotifAskedKey] = true }
        return true
    }

    private val tourPendingKey = booleanPreferencesKey("tour_pending")

    /** True after onboarding (or "Replay app tour") until the tour is finished or skipped. */
    val tourPending = preferences.data.map { it[tourPendingKey] ?: false }

    suspend fun setTourPending(value: Boolean) {
        preferences.edit { it[tourPendingKey] = value }
    }

    private val themeKey = stringPreferencesKey("theme")
    private val seedVersionKey = intPreferencesKey("seed_version")
    val theme = preferences.data.map { it[themeKey] ?: "DARK" }

    suspend fun setTheme(value: String) {
        preferences.edit { it[themeKey] = value }
    }

    suspend fun seedVersion(): Int = preferences.data.map { it[seedVersionKey] ?: 0 }.first()

    suspend fun setSeedVersion(value: Int): Unit {
        preferences.edit { it[seedVersionKey] = value }
    }

    /** Stamps the time of a finished health sync. */
    suspend fun markHealthSynced() {
        preferences.edit { it[healthSyncedAtKey] = nowMillis() }
    }
}

private const val EXERCISE_SEED_VERSION = 2
// 4: full SR Legacy table (7,793 foods) with amount-bearing portion labels.
// 5: Indian dishes (INDB), Indian packaged products (Open Food Facts) and world dishes (FNDDS).
private const val FOOD_SEED_VERSION = 5
private const val PROGRAM_SEED_VERSION = 3

sealed interface SeedState {
    data object Loading : SeedState

    data class Ready(
        val insertedExercises: Int,
        val insertedFoods: Int,
        val insertedServings: Int,
    ) : SeedState

    data class Failed(val reason: String) : SeedState
}
