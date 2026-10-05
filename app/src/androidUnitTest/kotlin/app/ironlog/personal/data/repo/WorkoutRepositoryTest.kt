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

    @Test
    fun warmupsGoFirstAndCoachWeightFillsOpenWorkingSets() = runBlocking {
        val session = repo.start("Test", listOf(Triple("squat", "Squat", 3)))
        val row = db.dao().sessionExercisesOnce(session).single()
        val first = db.dao().setsOnce(row.id).first()
        repo.completeWithFallback(first.id, 95.0, 8) // already done: keeps its weight
        repo.addWarmups(row.id, app.ironlog.personal.domain.Warmups.plan(100.0, app.ironlog.personal.domain.WeightUnit.KG, barbell = true))
        repo.applyWeight(row.id, 100.0)
        val sets = db.dao().setsOnce(row.id)
        assertEquals(listOf("WARMUP", "WARMUP", "WARMUP", "WARMUP", "WORKING", "WORKING", "WORKING"), sets.map { it.type })
        assertEquals((1..7).toList(), sets.map { it.setIndex })
        assertEquals(listOf(20.0, 40.0, 60.0, 80.0, 95.0, 100.0, 100.0), sets.map { it.weightKg })
        assertEquals(listOf(10, 5, 3, 1), sets.take(4).map { it.reps })
    }

    @Test
    fun supersetsLinkGrowAndDissolve() = runBlocking {
        val session = repo.start("Test", listOf(Triple("a", "A", 2), Triple("b", "B", 2), Triple("c", "C", 2)))
        fun rows() = runBlocking { db.dao().sessionExercisesOnce(session).sortedBy { it.orderIndex } }
        repo.supersetWithNext(rows()[0])
        val (a, b, c) = rows()
        assertEquals(a.supersetGroup, b.supersetGroup)
        assertNull(c.supersetGroup)
        repo.supersetWithNext(rows()[1]) // B joins C: one giant set
        assertEquals(1, rows().map { it.supersetGroup }.distinct().size)
        repo.leaveSuperset(rows()[2])
        repo.leaveSuperset(rows()[1]) // only A left: group dissolves
        assertEquals(listOf<Long?>(null, null, null), rows().map { it.supersetGroup })
    }

    @Test
    fun effortIsStoredAndReachesHistory() = runBlocking {
        val session = repo.start("Test", listOf(Triple("bench", "Bench", 1)))
        val set = db.dao().sessionSets(session).first().single()
        repo.completeWithFallback(set.id, 60.0, 10)
        repo.setRpe(set.id, 8.5)
        repo.finish(session)
        assertEquals(8.5, repo.allLoggedSets.first().single().rpe!!, 0.0)
    }
}
