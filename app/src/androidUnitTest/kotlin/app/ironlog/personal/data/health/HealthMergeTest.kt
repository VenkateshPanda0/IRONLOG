package app.ironlog.personal.data.health

import app.ironlog.personal.data.db.DailyLogEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HealthMergeTest {
    private val day = LocalDate.parse("2026-10-05")
    private val utc = ZoneOffset.UTC

    @Test
    fun emptyDaysAreFilledAndMarked() {
        val m = HealthMerge.merge(null, HealthDay(day, 8421, 7.2))!!
        assertEquals(8421, m.steps)
        assertEquals(7.2, m.sleepHours!!, 0.0)
        assertEquals(true, m.stepsFromHealth)
        assertEquals(true, m.sleepFromHealth)
        assertEquals(3000 + 0, HealthMerge.merge(DailyLogEntity(date = day.toString(), waterMl = 3000), HealthDay(day, 10, null))!!.waterMl)
    }

    @Test
    fun manualSleepIsKeptAndManualStepsOnlyRise() {
        val manual = DailyLogEntity(date = day.toString(), steps = 9000, sleepHours = 8.0)
        assertNull(HealthMerge.merge(manual, HealthDay(day, 5000, 6.5)))
        val higher = HealthMerge.merge(manual, HealthDay(day, 12000, 6.5))!!
        assertEquals(12000, higher.steps)
        assertEquals(true, higher.stepsFromHealth)
        assertEquals(8.0, higher.sleepHours!!, 0.0)
        assertEquals(false, higher.sleepFromHealth)
    }

    @Test
    fun importedValuesRefreshAndUnchangedDaysAreSkipped() {
        val imported = DailyLogEntity(date = day.toString(), steps = 4000, sleepHours = 6.0, stepsFromHealth = true, sleepFromHealth = true)
        val later = HealthMerge.merge(imported, HealthDay(day, 3500, 6.8))!!
        assertEquals(3500, later.steps) // a corrected count from the source replaces our own import
        assertEquals(6.8, later.sleepHours!!, 0.0)
        assertNull(HealthMerge.merge(later, HealthDay(day, 3500, 6.8)))
        assertNull(HealthMerge.merge(null, HealthDay(day, 0, null)))
    }

    @Test
    fun sleepCountsAgainstTheWakeUpDayMinusAwakeTime() {
        val night = SleepSpan(Instant.parse("2026-10-04T23:00:00Z"), Instant.parse("2026-10-05T07:00:00Z"), Duration.ofMinutes(30))
        val nap = SleepSpan(Instant.parse("2026-10-05T14:00:00Z"), Instant.parse("2026-10-05T14:40:00Z"))
        val blip = SleepSpan(Instant.parse("2026-10-05T16:00:00Z"), Instant.parse("2026-10-05T16:10:00Z"))
        assertEquals(mapOf(day to 8.2), HealthMerge.sleepByDate(listOf(night, nap, blip), utc))
    }
}
