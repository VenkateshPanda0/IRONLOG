package app.ironlog.personal.data.seed

import android.content.Context
import androidx.room.Room
import app.ironlog.personal.data.db.ActiveProgramEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ProgramSeedLoaderTest {
    @Test
    fun bundledProgramsReferenceSeededExercisesAndLoadIdempotently() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            SeedLoader(context, database).load()
            val loader = ProgramSeedLoader(context, database)

            assertEquals(3, loader.load())
            assertEquals(0, loader.load())
            assertEquals(3, database.dao().allPrograms().count { it.isBuiltIn })
            assertEquals(10, database.dao().allProgramDays().size)
            assertEquals(50, database.dao().allPrescriptions().size)
            val exerciseIds = database.dao().allExercises().mapTo(mutableSetOf()) { it.id }
            database.dao().allPrescriptions().forEach { assertTrue(it.exerciseId in exerciseIds) }

            val dao = database.dao()
            val program = dao.allPrograms().first { it.isBuiltIn }
            val firstDay = dao.days(program.id).first()
            dao.activate(ActiveProgramEntity(programId = program.id, startDate = "2026-10-04"))
            val prescriptions = dao.prescriptions(firstDay.id)
            val sessionId =
                dao.startWorkout(
                    "${program.name} · ${firstDay.name}",
                    program.id,
                    firstDay.name,
                    prescriptions.map { prescription ->
                        Triple(
                            prescription.exerciseId,
                            dao.exercise(prescription.exerciseId)?.name ?: prescription.exerciseId,
                            prescription,
                        )
                    },
                )
            val snapshots = dao.sessionExercisesOnce(sessionId)
            assertEquals(prescriptions.map { it.targetSets }, snapshots.map { it.targetSets })
            assertEquals(prescriptions.map { it.repMin }, snapshots.map { it.repMin })
            assertEquals(prescriptions.map { it.restSeconds }, snapshots.map { it.restSeconds })
            assertEquals(1, dao.activeProgramOnce()?.currentDay)

            dao.finishWorkoutAndAdvanceProgram(sessionId, 100L)
            assertEquals(2, dao.activeProgramOnce()?.currentDay)
            dao.finishWorkoutAndAdvanceProgram(sessionId, 200L)
            assertEquals("COMPLETED", dao.session(sessionId)?.status)
            assertEquals(2, dao.activeProgramOnce()?.currentDay)

            val quickSessionId = dao.startWorkout("Quick workout", null, null, emptyList())
            dao.finishWorkoutAndAdvanceProgram(quickSessionId, 300L)
            assertEquals(2, dao.activeProgramOnce()?.currentDay)
        } finally {
            database.close()
        }
    }

    @Test
    fun seedValidationRejectsMissingExerciseReferences() {
        val badProgram =
            ProgramSeedLoader.ProgramSeed(
                name = "Broken",
                description = "Test fixture",
                daysPerWeek = 3,
                goal = "MAINTAIN",
                days =
                    listOf(
                        ProgramSeedLoader.DaySeed(
                            name = "Day A",
                            exercises =
                                listOf(ProgramSeedLoader.ExerciseSeed("missing-id", 3, 6, 12, 90)),
                        )
                    ),
            )
        try {
            validateSeed(RuntimeEnvironment.getApplication(), badProgram)
            fail("Expected missing exercise reference to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("missing-id"))
        }
    }

    @Test
    fun seedValidationRejectsTooManyExercisesInOneDay() {
        val exercises = (1..9).map { ProgramSeedLoader.ExerciseSeed("exercise-$it", 3, 6, 12, 90) }
        val day = ProgramSeedLoader.DaySeed("Day A", exercises)
        val program =
            ProgramSeedLoader.ProgramSeed(
                name = "Too many exercises",
                description = "Test fixture",
                daysPerWeek = 3,
                goal = "MAINTAIN",
                days = listOf(day),
            )
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            try {
                ProgramSeedLoader(context, database)
                    .validate(
                        listOf(program),
                        exercises.mapTo(mutableSetOf()) { it.id },
                    )
                fail("Expected oversized day to be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("supported exercise count"))
            }
        } finally {
            database.close()
        }
    }

    private fun validateSeed(
        context: Context,
        program: ProgramSeedLoader.ProgramSeed,
        exerciseIds: Set<String> = emptySet(),
    ) {
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            ProgramSeedLoader(context, database).validate(listOf(program), exerciseIds)
        } finally {
            database.close()
        }
    }
}
