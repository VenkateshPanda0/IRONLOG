package app.ironlog.personal.data.health

import kotlinx.datetime.toKotlinLocalDate

import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HealthSyncerTest {
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val today = LocalDate.now()

    /** DataStore keeps one instance per process, so settings must be reset between tests. */
    @org.junit.Before
    fun reset() = runBlocking { app.container.setHealthSync(false) }

    private class Fake(val days: List<HealthDay>) : HealthSource {
        var asked: Pair<LocalDate, LocalDate>? = null

        override suspend fun read(from: LocalDate, to: LocalDate): List<HealthDay> {
            asked = from to to
            return days.filter { it.date in from..to }
        }
    }

    @Test
    fun firstSyncImportsAMonthThenAWeekAndManualEntriesSurvive() = runBlocking {
        val c = app.container
        c.wellness.setSleep(today.toKotlinLocalDate(), 8.0, 4) // typed in by hand
        val fake = Fake((0L..40L).map { HealthDay(today.minusDays(it), 5000 + it.toInt(), 7.0) })
        c.healthSource = fake
        c.setHealthSync(true)
        val first = c.syncHealth()
        assertEquals(today.minusDays(29) to today, fake.asked)
        assertEquals(30, first.daysRead)
        val todayRow = c.dao().dailyLogOnce(today.toString())!!
        assertEquals(5000, todayRow.steps)
        assertEquals(8.0, todayRow.sleepHours!!, 0.0) // manual sleep kept
        assertEquals(true, todayRow.stepsFromHealth)
        assertEquals(false, todayRow.sleepFromHealth)
        assertEquals(7.0, c.dao().dailyLogOnce(today.minusDays(3).toString())!!.sleepHours!!, 0.0)
        // Later syncs only look back a week, and an unchanged week updates nothing.
        val second = c.syncHealth()
        assertEquals(today.minusDays(6) to today, fake.asked)
        assertEquals(0, second.daysUpdated)
        // A manual edit takes the value back from Health Connect.
        c.wellness.setSteps(today.toKotlinLocalDate(), 12000)
        assertEquals(false, c.dao().dailyLogOnce(today.toString())!!.stepsFromHealth)
        assertTrue(c.healthSyncedAt.first() != null)
    }

    @Test
    fun foregroundSyncIsThrottledAndOffWhenDisabled() = runBlocking {
        val c = app.container
        val fake = Fake(listOf(HealthDay(today, 4321, null)))
        c.healthSource = fake
        c.syncHealthIfDue()
        assertEquals(null, fake.asked) // disabled
        c.setHealthSync(true)
        c.syncHealthIfDue()
        assertEquals(4321, c.dao().dailyLogOnce(today.toString())!!.steps)
        fake.asked = null
        c.syncHealthIfDue()
        assertEquals(null, fake.asked) // synced moments ago
    }
}
