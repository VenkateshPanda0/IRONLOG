package app.ironlog.personal.domain

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculationsTest {
    @Test
    fun conversionsRoundTrip() {
        assertEquals(10.0, Calculations.lbToKg(Calculations.kgToLb(10.0)), 1e-9)
        assertEquals(100.0, Calculations.inToCm(Calculations.cmToIn(100.0)), 1e-9)
    }

    @Test
    fun foodMacrosScaleByGrams() {
        assertEquals(50.0, Calculations.foodMacro(100.0, 50.0), 1e-9)
    }

    @Test
    fun onlyCompletedWorkingAndDropSetsContributeVolume() {
        assertEquals(40.0, Calculations.setVolume(20.0, 2, true, "WORKING"), 1e-9)
        assertEquals(0.0, Calculations.setVolume(20.0, 2, true, "WARMUP"), 1e-9)
        assertEquals(0.0, Calculations.setVolume(20.0, 2, false, "WORKING"), 1e-9)
    }

    @Test
    fun epleyIsLimitedToOneThroughTwelveReps() {
        assertEquals(100.0, Calculations.e1rm(100.0, 1)!!, 1e-9)
        assertEquals(120.0, Calculations.e1rm(100.0, 6)!!, 1e-9)
        assertNull(Calculations.e1rm(100.0, 13))
    }

    @Test
    fun sevenDayMeanNeedsThreeRecordedDays() {
        val day = LocalDate.parse("2026-01-07")
        assertNull(Calculations.sevenDayMean(mapOf(day to 70.0, day.minusDays(2) to 72.0), day))
        assertEquals(
            71.0,
            Calculations.sevenDayMean(
                mapOf(day to 70.0, day.minusDays(2) to 72.0, day.minusDays(6) to 71.0),
                day,
            )!!,
            1e-9,
        )
    }

    @Test
    fun timerUsesAbsoluteEndTimeAndClamps() {
        assertEquals(5000L, Calculations.timerRemainingMs(15000L, 10000L))
        assertEquals(0L, Calculations.timerRemainingMs(10000L, 15000L))
    }

    @Test
    fun weightRangesFilterByCalendarMonthAndKeepActualPoints() {
        val end = LocalDate.parse("2026-10-03")
        val data =
            mapOf(end.minusMonths(2) to 71.0, end.minusDays(2) to 70.5, end.minusMonths(4) to 72.0)
        assertEquals(listOf(70.5), Calculations.filterWeightRange(data, end, "1M").values.toList())
        assertEquals(2, Calculations.filterWeightRange(data, end, "3M").size)
        assertEquals(3, Calculations.filterWeightRange(data, end, "ALL").size)
    }

    @Test
    fun prReplayFlagsStrictImprovementsOnly() {
        val records =
            listOf(
                Calculations.Performance(1, 100, "COMPLETED", "squat", 80.0, 5, 1200.0),
                Calculations.Performance(2, 200, "COMPLETED", "squat", 80.0, 5, 1200.0),
                Calculations.Performance(3, 300, "COMPLETED", "squat", 82.5, 5, 1300.0),
                Calculations.Performance(4, 400, "DISCARDED", "squat", 200.0, 5, 9000.0),
            )
        val fresh = Calculations.newRecords(records).filter { it.type == "5RM" }
        assertEquals(listOf(1L, 3L), fresh.map { it.sessionId })
    }
}
