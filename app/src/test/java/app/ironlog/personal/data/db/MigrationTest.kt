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
        helper.runMigrationsAndValidate(name, 2, true, IronlogDatabase.MIGRATION_1_2).close()

        // Open with Room itself to prove the migrated schema matches the entities.
        val db =
            Room.databaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java, name)
                .addMigrations(IronlogDatabase.MIGRATION_1_2)
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
}
