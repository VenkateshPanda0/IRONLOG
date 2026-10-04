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
        helper.runMigrationsAndValidate(name, 4, true, IronlogDatabase.MIGRATION_1_2, IronlogDatabase.MIGRATION_2_3, IronlogDatabase.MIGRATION_3_4).close()

        // Open with Room itself to prove the migrated schema matches the entities.
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, name)
                .addMigrations(IronlogDatabase.MIGRATION_1_2, IronlogDatabase.MIGRATION_2_3, IronlogDatabase.MIGRATION_3_4)
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
        helper.runMigrationsAndValidate(v2, 4, true, IronlogDatabase.MIGRATION_2_3, IronlogDatabase.MIGRATION_3_4).close()
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, v2)
                .addMigrations(IronlogDatabase.MIGRATION_1_2, IronlogDatabase.MIGRATION_2_3, IronlogDatabase.MIGRATION_3_4)
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
}
