package app.ironlog.personal.data.repo

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import androidx.room.Room
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class WellnessRepositoryTest {
    private lateinit var db: IronlogDatabase
    private lateinit var repo: WellnessRepository
    private val today = LocalDate.parse("2026-10-04")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java).allowMainThreadQueries().build()
        repo = WellnessRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test
    fun dailyFieldsUpdateIndependently() = runBlocking {
        repo.addWater(today, 250)
        repo.addWater(today, 500)
        repo.setSteps(today, 10_432)
        repo.setSleep(today, 7.5, 4)
        repo.checkIn(today, 4, 2, 3, 5)
        val day = repo.day(today).first()!!
        assertEquals(750, day.waterMl)
        assertEquals(10_432, day.steps)
        assertEquals(7.5, day.sleepHours!!, 0.0)
        assertEquals(4, day.energy)
        repo.addWater(today, -1000)
        assertEquals(0, repo.day(today).first()!!.waterMl)
    }

    @Test
    fun habitsToggleAndArchive() = runBlocking {
        val creatine = repo.addHabit(" Creatine ")
        repo.setHabitDone(creatine, today, true)
        repo.setHabitDone(creatine, today.minusDays(1), true)
        repo.setHabitDone(creatine, today.minusDays(1), false)
        assertEquals(listOf(today.toString()), repo.habitChecks.first().map { it.date })
        assertEquals("Creatine", repo.habits.first().single().name)
        repo.archiveHabit(creatine)
        assertEquals(0, repo.habits.first().size)
    }
}
