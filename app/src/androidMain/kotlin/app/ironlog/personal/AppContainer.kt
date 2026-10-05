package app.ironlog.personal

import android.content.Context
import app.ironlog.personal.time.*

import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import okio.Path.Companion.toOkioPath
import app.ironlog.personal.data.transaction
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.provider.OpenFoodFactsProvider
import app.ironlog.personal.data.repo.*
import app.ironlog.personal.data.seed.FoodSeedLoader
import app.ironlog.personal.data.seed.ProgramSeedLoader
import app.ironlog.personal.data.seed.SeedLoader
import app.ironlog.personal.timer.RestTimerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "ironlog_settings")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db = Room.databaseBuilder(context, IronlogDatabase::class.java, "ironlog.db")
            .addMigrations(app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_1_2, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_2_3, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_3_4, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_4_5, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_5_6, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_6_7, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_7_8, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_8_9)
            .build()
    val workouts = WorkoutRepository(db)
    val nutrition = NutritionRepository(db.dao())
    val body = BodyRepository(db.dao(), java.io.File(context.filesDir, "photos").toOkioPath()) { healthDeleted(it.healthId, "weight:" + app.ironlog.personal.data.health.HealthIds.weight(it.id)) }
    val goals = GoalRepository(db.dao())
    val engagement = EngagementRepository(db.dao())
    val wellness = WellnessRepository(db) { healthDeleted(it.healthId, "session:" + app.ironlog.personal.data.health.HealthIds.cardio(it.id)) }
    val physique = PhysiqueRepository(db, java.io.File(context.filesDir, "physique").toOkioPath())
    val backup = BackupRepository(db)
    private val photoDir = java.io.File(context.filesDir, "photos")
    val archive = app.ironlog.personal.data.backup.BackupArchive(backup, photoDir)
    val autoBackup = app.ironlog.personal.data.backup.AutoBackup(backup, java.io.File(context.filesDir, "backup"), photoDir)
    /** Background work that must outlive a screen, such as writing the backup snapshot. */
    val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    @Volatile private var dataChanged = autoBackup.lastWritten() == null

    init {
        // Any write to the user's data marks the snapshot stale; the rest timer is not data.
        db.invalidationTracker.addObserver(
            object : androidx.room.InvalidationTracker.Observer(
                arrayOf(
                    "exercise", "program", "program_day", "program_day_exercise", "active_program", "skipped_program_day", "user_profile",
                    "workout_session", "session_exercise", "set_log", "food", "food_serving", "meal_entry", "body_weight", "goal",
                    "progress_photo", "cardio_session", "daily_log", "habit", "habit_check", "body_measurement", "physique_scan",
                )
            ) {
                override fun onInvalidated(tables: Set<String>) {
                    dataChanged = true
                }
            }
        )
    }

    /**
     * Refreshes the snapshot Android backs up, when anything changed. Never before onboarding is
     * done: on a new phone that would overwrite the restored snapshot with an empty one.
     */
    suspend fun backupIfChanged() {
        if (!dataChanged || db.dao().profileOnce() == null) return
        dataChanged = false
        runCatching { autoBackup.write() }.onFailure { dataChanged = true }
    }
    val programs = ProgramRepository(db)
    private val assets = app.ironlog.personal.platform.AndroidAssets(context)
    val seed = SeedLoader(assets, db)
    val foodSeed = FoodSeedLoader(assets, db)
    val programSeed = ProgramSeedLoader(assets, db)
    val openFoodFacts = OpenFoodFactsProvider(db.dao())
    val restTimer = RestTimerController(context, db.dao())
    /** Workout to open when the app is launched from the live workout notification. */
    val openWorkoutRequest = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)
    private val mutableSeedState = MutableStateFlow<SeedState>(SeedState.Loading)
    val seedState: StateFlow<SeedState> = mutableSeedState

    fun updateSeedState(state: SeedState): Unit {
        mutableSeedState.value = state
    }

    val profile = db.dao().profile()

    fun dao() = db.dao()

    suspend fun saveProfile(value: app.ironlog.personal.data.db.UserProfileEntity) =
        db.dao().saveProfile(value)

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
        autoBackup.clear()
        appContext.preferences.edit {
            it.remove(healthIgnoredKey)
            it.remove(healthPendingKey)
            it.remove(healthSyncedAtKey)
            it[healthSyncKey] = false
        }
        dataChanged = false
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
    val reminders = app.ironlog.personal.reminders.ReminderScheduler(appContext)

    val reminderSettings =
        context.preferences.data.map { p ->
            val d = app.ironlog.personal.domain.ReminderSettings()
            app.ironlog.personal.domain.ReminderSettings(
                workout = p[reminderWorkoutKey] ?: d.workout,
                workoutMinutes = p[reminderWorkoutTimeKey] ?: d.workoutMinutes,
                streak = p[reminderStreakKey] ?: d.streak,
                streakMinutes = p[reminderStreakTimeKey] ?: d.streakMinutes,
            )
        }

    /** True once the Home card offering reminders was answered either way. */
    val reminderPromptDismissed = context.preferences.data.map { it[reminderPromptKey] ?: false }

    suspend fun saveReminderSettings(value: app.ironlog.personal.domain.ReminderSettings) {
        appContext.preferences.edit {
            it[reminderWorkoutKey] = value.workout
            it[reminderWorkoutTimeKey] = value.workoutMinutes
            it[reminderStreakKey] = value.streak
            it[reminderStreakTimeKey] = value.streakMinutes
            it[reminderPromptKey] = true
        }
        reminders.apply(value)
    }

    suspend fun dismissReminderPrompt() {
        appContext.preferences.edit { it[reminderPromptKey] = true }
    }

    private val healthIgnoredKey = stringSetPreferencesKey("health_ignored")
    private val healthPendingKey = stringSetPreferencesKey("health_pending_deletes")

    /**
     * A deleted import is remembered so it is not imported again; a deleted Ironlog record is
     * queued for removal from Health Connect at the next sync (only if sync is on).
     */
    private suspend fun healthDeleted(importedId: String?, ownClientId: String) {
        if (importedId != null) appContext.preferences.edit { it[healthIgnoredKey] = it[healthIgnoredKey].orEmpty() + importedId }
        else if (healthSyncEnabled.first()) appContext.preferences.edit { it[healthPendingKey] = it[healthPendingKey].orEmpty() + ownClientId }
    }

    private val tombstones =
        object : app.ironlog.personal.data.health.HealthTombstones {
            override suspend fun ignored() = appContext.preferences.data.first()[healthIgnoredKey].orEmpty()

            override suspend fun pendingDeletes() = appContext.preferences.data.first()[healthPendingKey].orEmpty()

            override suspend fun clearPendingDeletes(done: Set<String>) {
                appContext.preferences.edit { it[healthPendingKey] = it[healthPendingKey].orEmpty() - done }
            }
        }

    private val healthSyncKey = booleanPreferencesKey("health_sync")
    private val healthSyncedAtKey = longPreferencesKey("health_synced_at")
    val healthConnect = app.ironlog.personal.data.health.HealthConnect(appContext)

    /** Replaced in tests, where Health Connect is not installed. */
    var healthSource: app.ironlog.personal.data.health.HealthSource = healthConnect

    val healthSyncEnabled = context.preferences.data.map { it[healthSyncKey] ?: false }
    val healthSyncedAt = context.preferences.data.map { it[healthSyncedAtKey] }

    suspend fun setHealthSync(on: Boolean) {
        appContext.preferences.edit {
            it[healthSyncKey] = on
            if (!on) it.remove(healthSyncedAtKey)
        }
    }

    /** Imports steps and sleep; 30 days the first time, then the last week to pick up late edits. */
    suspend fun syncHealth(): app.ironlog.personal.data.health.SyncResult {
        val first = healthSyncedAt.first() == null
        val result = app.ironlog.personal.data.health.HealthSyncer(db, healthSource, tombstones).sync(days = if (first) 30 else 7)
        appContext.preferences.edit { it[healthSyncedAtKey] = nowMillis() }
        return result
    }

    /** Called when the app comes to the foreground; Health Connect only allows reads then. */
    suspend fun syncHealthIfDue(minGapMs: Long = 15 * 60_000L) {
        if (!healthSyncEnabled.first()) return
        val last = healthSyncedAt.first() ?: 0L
        if (nowMillis() - last < minGapMs) return
        if (healthSource === healthConnect && !healthConnect.hasPermissions()) return
        runCatching { syncHealth() }
    }

    private val weightUnitKey = stringPreferencesKey("weight_unit")

    /** kg or lb for showing and typing weights; data is always stored in kg. */
    val weightUnit =
        context.preferences.data.map { app.ironlog.personal.domain.WeightUnit.of(it[weightUnitKey]) ?: app.ironlog.personal.domain.WeightUnit.defaultFor() }

    suspend fun setWeightUnit(value: app.ironlog.personal.domain.WeightUnit) {
        appContext.preferences.edit { it[weightUnitKey] = value.name }
    }

    private val lengthUnitKey = stringPreferencesKey("length_unit")

    /** cm/km or in/mi for showing and typing lengths and distances; storage stays cm and km. */
    val lengthUnit =
        context.preferences.data.map { app.ironlog.personal.domain.LengthUnit.of(it[lengthUnitKey]) ?: app.ironlog.personal.domain.LengthUnit.defaultFor() }

    suspend fun setLengthUnit(value: app.ironlog.personal.domain.LengthUnit) {
        appContext.preferences.edit { it[lengthUnitKey] = value.name }
    }

    val workoutNotifier = app.ironlog.personal.timer.WorkoutNotifier(appContext, db.dao(), weightUnit)
    private val workoutNotifAskedKey = booleanPreferencesKey("workout_notification_asked")

    /** True the first time only: the live workout notification asks for permission once. */
    suspend fun firstWorkoutNotificationAsk(): Boolean {
        if (context().preferences.data.first()[workoutNotifAskedKey] == true) return false
        context().preferences.edit { it[workoutNotifAskedKey] = true }
        return true
    }

    private fun context() = appContext

    private val tourPendingKey = booleanPreferencesKey("tour_pending")

    /** True after onboarding (or "Replay app tour") until the tour is finished or skipped. */
    val tourPending = context.preferences.data.map { it[tourPendingKey] ?: false }

    suspend fun setTourPending(value: Boolean) {
        appContext.preferences.edit { it[tourPendingKey] = value }
    }

    private val themeKey = stringPreferencesKey("theme")
    private val seedVersionKey = intPreferencesKey("seed_version")
    val theme = context.preferences.data.map { it[themeKey] ?: "DARK" }

    suspend fun setTheme(value: String) {
        appContext.preferences.edit { it[themeKey] = value }
    }

    suspend fun seedVersion(): Int =
        appContext.preferences.data.map { it[seedVersionKey] ?: 0 }.first()

    suspend fun setSeedVersion(value: Int): Unit {
        appContext.preferences.edit { it[seedVersionKey] = value }
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
