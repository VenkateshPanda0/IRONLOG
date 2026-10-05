package app.ironlog.personal

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.provider.OpenFoodFactsProvider
import app.ironlog.personal.data.provider.UsdaProvider
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
            .addMigrations(IronlogDatabase.MIGRATION_1_2, IronlogDatabase.MIGRATION_2_3, IronlogDatabase.MIGRATION_3_4, IronlogDatabase.MIGRATION_4_5, IronlogDatabase.MIGRATION_5_6, IronlogDatabase.MIGRATION_6_7)
            .build()
    val workouts = WorkoutRepository(db)
    val nutrition = NutritionRepository(db.dao())
    val body = BodyRepository(db.dao(), java.io.File(context.filesDir, "photos")) { healthDeleted(it.healthId, "weight:" + app.ironlog.personal.data.health.HealthIds.weight(it.id)) }
    val goals = GoalRepository(db.dao())
    val engagement = EngagementRepository(db.dao())
    val wellness = WellnessRepository(db) { healthDeleted(it.healthId, "session:" + app.ironlog.personal.data.health.HealthIds.cardio(it.id)) }
    val physique = PhysiqueRepository(db.dao(), java.io.File(context.filesDir, "physique"))
    val backup = BackupRepository(db)
    val programs = ProgramRepository(db)
    val seed = SeedLoader(context, db)
    val foodSeed = FoodSeedLoader(context, db)
    val programSeed = ProgramSeedLoader(context, db)
    val openFoodFacts = OpenFoodFactsProvider(db.dao())
    val usda = UsdaProvider(null)
    val restTimer = RestTimerController(context, db.dao())
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
        db.withTransaction {
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

    private val accountEmailKey = stringPreferencesKey("google_email")
    private val accountNameKey = stringPreferencesKey("google_name")
    private val accountPhotoKey = stringPreferencesKey("google_photo")
    private val driveBackupKey = longPreferencesKey("drive_backup_at")
    val drive = app.ironlog.personal.data.cloud.DriveBackup()

    /** The optional Google account; null when signed out. */
    val account =
        context.preferences.data.map { p ->
            p[accountEmailKey]?.let { app.ironlog.personal.data.cloud.GoogleAccount(it, p[accountNameKey].orEmpty(), p[accountPhotoKey]) }
        }

    /** When this phone last backed up to Google Drive (epoch ms), if ever. */
    val lastDriveBackup = context.preferences.data.map { it[driveBackupKey] }

    suspend fun saveAccount(value: app.ironlog.personal.data.cloud.GoogleAccount?) {
        appContext.preferences.edit {
            if (value == null) {
                it.remove(accountEmailKey)
                it.remove(accountNameKey)
                it.remove(accountPhotoKey)
                it.remove(driveBackupKey)
            } else {
                it[accountEmailKey] = value.email
                it[accountNameKey] = value.name
                if (value.photoUrl != null) it[accountPhotoKey] = value.photoUrl else it.remove(accountPhotoKey)
            }
        }
    }

    suspend fun setLastDriveBackup(at: Long) {
        appContext.preferences.edit { it[driveBackupKey] = at }
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
        appContext.preferences.edit { it[healthSyncedAtKey] = System.currentTimeMillis() }
        return result
    }

    /** Called when the app comes to the foreground; Health Connect only allows reads then. */
    suspend fun syncHealthIfDue(minGapMs: Long = 15 * 60_000L) {
        if (!healthSyncEnabled.first()) return
        val last = healthSyncedAt.first() ?: 0L
        if (System.currentTimeMillis() - last < minGapMs) return
        if (healthSource === healthConnect && !healthConnect.hasPermissions()) return
        runCatching { syncHealth() }
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
