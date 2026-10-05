package app.ironlog.personal.data.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val name = "migration-test.db"
    private val all = arrayOf(app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_1_2, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_2_3, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_3_4, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_4_5, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_5_6, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_6_7, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_7_8, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_8_9)

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), IronlogDatabase::class.java)

    @Test
    fun v1WorkoutDataSurvivesMigrationToV2() {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO workout_session (id, startedAt, endedAt, status, name, notes, totalPausedMs, lastActiveAt) VALUES (1, 1000, 2000, 'COMPLETED', 'Push', '', 0, 2000)")
            execSQL("INSERT INTO session_exercise (id, sessionId, exerciseId, exerciseNameSnapshot, orderIndex, status, targetSets, repMin, repMax, restSeconds, notes) VALUES (1, 1, 'bench', 'Bench', 0, 'PENDING', 3, 8, 12, 90, '')")
            execSQL("INSERT INTO set_log (id, sessionExerciseId, setIndex, type, weightKg, reps, isCompleted) VALUES (1, 1, 1, 'WORKING', 100.0, 5, 1)")
            close()
        }
        helper.runMigrationsAndValidate(name, 9, true, *all).close()

        // Open with Room itself to prove the migrated schema matches the entities.
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, name)
                .addMigrations(*all)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                val row = db.dao().sessionExercisesOnce(1).single()
                assertEquals("Bench", row.exerciseNameSnapshot)
                assertNull(row.originalExerciseId)
                val sets = db.dao().allLoggedSets().first()
                assertEquals(100.0, sets.single().weightKg!!, 0.0)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v2GoalsGainWellnessDefaultsAndNewTablesWork() {
        val v2 = "migration-v2.db"
        helper.createDatabase(v2, 2).apply {
            execSQL("INSERT INTO goal (id, goalWeightKg, targetDate, kcalTarget, proteinG, carbsG, fatG) VALUES (1, 75.0, NULL, 2400, 160.0, 250.0, 70.0)")
            close()
        }
        helper.runMigrationsAndValidate(v2, 9, true, *all).close()
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, v2)
                .addMigrations(*all)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                val goal = db.dao().goalOnce()!!
                assertEquals(2400, goal.kcalTarget)
                assertEquals(8000, goal.stepGoal)
                assertEquals(3000, goal.waterGoalMl)
                db.dao().saveDailyLog(DailyLogEntity(date = "2026-10-04", steps = 9000, waterMl = 1500))
                assertEquals(9000, db.dao().dailyLogOnce("2026-10-04")!!.steps)
                val habit = db.dao().addHabit(HabitEntity(name = "Creatine"))
                db.dao().checkHabit(HabitCheckEntity(habit, "2026-10-04"))
                db.dao().checkHabit(HabitCheckEntity(habit, "2026-10-04"))
                assertEquals(1, db.dao().habitChecks().first().size)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v4ProfileKeepsDataAndGainsPhysiqueGoalAndScans() {
        val v4 = "migration-v4.db"
        helper.createDatabase(v4, 4).apply {
            execSQL("INSERT INTO user_profile (id, name, sex, age, heightCm, weightKg, activity, goal, daysPerWeek, equipment, experience, sessionMinutes, avoidList, trainingWeekdays) VALUES (1, 'Ravi', 'MALE', 28, 175.0, 72.0, 1.5, 'GAIN', 4, 'GYM', 'INTERMEDIATE', 60, '', 'MON,TUE,THU,FRI')")
            close()
        }
        helper.runMigrationsAndValidate(v4, 9, true, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_4_5, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_5_6, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_6_7, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_7_8, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_8_9).close()
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, v4)
                .addMigrations(*all)
                .allowMainThreadQueries()
                .build()
        try {
            runBlocking {
                val profile = db.dao().profile().first()!!
                assertEquals("Ravi", profile.name)
                assertEquals("", profile.physiqueGoal)
                db.dao().setPhysiqueGoal("CLASSIC")
                assertEquals("CLASSIC", db.dao().profile().first()!!.physiqueGoal)
                db.dao().addPhysiqueScan(PhysiqueScanEntity(date = "2026-10-04", fileName = "a.jpg", goal = "CLASSIC", shoulder = 180.0, waist = 110.0, hip = 130.0, leftThigh = 60.0, rightThigh = 61.0, height = 690.0, legToTorso = 1.4, matchScore = 72))
                assertEquals(72, db.dao().physiqueScans().first().single().matchScore)
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun v5DailyLogsKeepValuesAndCountAsManual() {
        val v5 = "migration-v5.db"
        helper.createDatabase(v5, 5).apply {
            execSQL("INSERT INTO daily_log (date, steps, waterMl, sleepHours) VALUES ('2026-10-01', 7000, 1500, 7.5)")
            close()
        }
        helper.runMigrationsAndValidate(v5, 9, true, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_5_6, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_6_7, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_7_8, app.ironlog.personal.data.db.IronlogMigrations.MIGRATION_8_9).close()
        val db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, v5).addMigrations(*all).allowMainThreadQueries().build()
        try {
            runBlocking {
                val day = db.dao().dailyLogOnce("2026-10-01")!!
                assertEquals(7000, day.steps)
                assertEquals(false, day.stepsFromHealth)
                assertEquals(false, day.sleepFromHealth)
            }
        } finally {
            db.close()
        }
    }
}
