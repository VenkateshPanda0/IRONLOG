package app.ironlog.personal.data.repo

import androidx.room.Room
import app.ironlog.personal.data.db.ExerciseEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryTest {
    private lateinit var db: IronlogDatabase
    private lateinit var repo: WorkoutRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = WorkoutRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun dropSetIsInsertedAfterItsParentAndRemovalRenumbers() = runBlocking {
        val session = repo.start("Test", listOf(Triple("bench", "Bench", 3)))
        val row = db.dao().sessionExercisesOnce(session).single()
        val first = db.dao().setsOnce(row.id).first()
        repo.saveSetDraft(first.id, 100.0, 8)

        val drop = repo.addSubSet(first.id, "DROP")!!
        var sets = db.dao().setsOnce(row.id)
        assertEquals(listOf("WORKING", "DROP", "WORKING", "WORKING"), sets.map { it.type })
        assertEquals(listOf(1, 2, 3, 4), sets.map { it.setIndex })
        assertEquals(80.0, sets[1].weightKg!!, 0.0)

        repo.removeSet(drop)
        sets = db.dao().setsOnce(row.id)
        assertEquals(listOf(1, 2, 3), sets.map { it.setIndex })
    }

    @Test
    fun completingBlankSetUsesHintAndFinishedSetsAreLogged() = runBlocking {
        val session = repo.start("Test", listOf(Triple("bench", "Bench", 1)))
        val set = db.dao().sessionSets(session).first().single()
        repo.completeWithFallback(set.id, 60.0, 10)
        repo.finish(session)
        val logged = repo.allLoggedSets.first().single()
        assertEquals(60.0, logged.weightKg!!, 0.0)
        assertEquals(10, logged.reps)
        assertEquals("bench", logged.exerciseId)
    }

    @Test
    fun replaceKeepsOriginalForRevertAndMoveReorders() = runBlocking {
        val session = repo.start("Test", listOf(Triple("bench", "Bench", 2), Triple("row", "Row", 2)))
        val (bench, row) = db.dao().sessionExercisesOnce(session)
        repo.replaceExercise(bench.id, ExerciseEntity(id = "dbpress", name = "DB Press"))
        repo.replaceExercise(bench.id, ExerciseEntity(id = "machine", name = "Machine Press"))
        var swapped = db.dao().sessionExercise(bench.id)!!
        assertEquals("machine", swapped.exerciseId)
        assertEquals("bench", swapped.originalExerciseId)
        assertEquals(2, db.dao().setsOnce(bench.id).size)

        repo.revertExercise(bench.id)
        swapped = db.dao().sessionExercise(bench.id)!!
        assertEquals("Bench", swapped.exerciseNameSnapshot)
        assertNull(swapped.originalExerciseId)

        repo.moveExercise(row.id, -1)
        assertEquals(listOf("row", "bench"), db.dao().sessionExercisesOnce(session).map { it.exerciseId })
    }

    @Test
    fun discardRemovesSessionAndSets() = runBlocking {
        val session = repo.start("Test", listOf(Triple("bench", "Bench", 2)))
        repo.discard(session)
        assertNull(repo.session(session))
        assertEquals(0, db.dao().allSets().size)
    }
}
