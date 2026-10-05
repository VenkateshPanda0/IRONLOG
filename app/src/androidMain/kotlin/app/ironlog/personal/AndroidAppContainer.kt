package app.ironlog.personal

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.ironlog.personal.data.backup.AutoBackup
import app.ironlog.personal.data.backup.BackupArchive
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.db.IronlogMigrations
import app.ironlog.personal.data.health.HealthConnect
import app.ironlog.personal.data.health.HealthSource
import app.ironlog.personal.data.health.HealthSyncer
import app.ironlog.personal.data.health.HealthTombstones
import app.ironlog.personal.data.health.SyncResult
import app.ironlog.personal.platform.AndroidAssets
import app.ironlog.personal.reminders.ReminderScheduler
import app.ironlog.personal.time.nowMillis
import app.ironlog.personal.timer.AndroidRestAlarm
import app.ironlog.personal.timer.WorkoutNotifier
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath

private val Context.preferences by preferencesDataStore(name = "ironlog_settings")

/** The shared container plus Android's extras: Health Connect, backups and the workout notification. */
class AndroidAppContainer(context: Context) :
    AppContainer(
        db = Room.databaseBuilder(context, IronlogDatabase::class.java, "ironlog.db").addMigrations(*IronlogMigrations.ALL).build(),
        preferences = context.applicationContext.preferences,
        filesDir = context.filesDir.toOkioPath(),
        assets = AndroidAssets(context),
        reminders = ReminderScheduler(context.applicationContext),
        restAlarm = AndroidRestAlarm(context.applicationContext),
    ) {
    private val appContext = context.applicationContext
    private val photoDir = java.io.File(context.filesDir, "photos")
    val archive = BackupArchive(backup, photoDir)
    val autoBackup = AutoBackup(backup, java.io.File(context.filesDir, "backup"), photoDir)
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

    override suspend fun onPersonalDataCleared() {
        autoBackup.clear()
        dataChanged = false
    }

    private val tombstones =
        object : HealthTombstones {
            override suspend fun ignored() = healthIgnored()

            override suspend fun pendingDeletes() = healthPendingDeletes()

            override suspend fun clearPendingDeletes(done: Set<String>) = clearHealthPendingDeletes(done)
        }

    val healthConnect = HealthConnect(appContext)

    /** Replaced in tests, where Health Connect is not installed. */
    var healthSource: HealthSource = healthConnect

    /** Imports steps and sleep; 30 days the first time, then the last week to pick up late edits. */
    suspend fun syncHealth(): SyncResult {
        val first = healthSyncedAt.first() == null
        val result = HealthSyncer(db, healthSource, tombstones).sync(days = if (first) 30 else 7)
        markHealthSynced()
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

    val workoutNotifier = WorkoutNotifier(appContext, db.dao(), weightUnit)
}
