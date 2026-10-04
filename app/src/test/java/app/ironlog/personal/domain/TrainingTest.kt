package app.ironlog.personal.domain

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingTest {
    // Wednesday
    private val today = LocalDate.parse("2026-10-07")

    @Test
    fun weekStripMarksDoneMissedPlannedAndRest() {
        val strip =
            Training.weekStrip(
                today,
                workoutDates = setOf(LocalDate.parse("2026-10-05")),
                trainingWeekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.FRIDAY),
            )
        assertEquals(7, strip.size)
        assertEquals(LocalDate.parse("2026-10-05"), strip.first().date)
        assertEquals(
            listOf(
                DayMark.DONE,
                DayMark.MISSED,
                DayMark.REST,
                DayMark.REST,
                DayMark.PLANNED,
                DayMark.REST,
                DayMark.REST,
            ),
            strip.map { it.mark },
        )
        assertEquals(listOf(false, false, true, false, false, false, false), strip.map { it.isToday })
    }

    @Test
    fun streakCountsCompletedWeeksAndToleratesUnfinishedCurrentWeek() {
        val dates =
            listOf(
                // two weeks ago: 3 workouts
                "2026-09-21",
                "2026-09-23",
                "2026-09-25",
                // last week: 3 workouts
                "2026-09-28",
                "2026-09-30",
                "2026-10-02",
                // this week: 1 so far
                "2026-10-05",
            ).map(LocalDate::parse)
        assertEquals(2, Training.weeklyStreak(dates, 3, today))
        assertEquals(3, Training.weeklyStreak(dates, 1, today))
    }

    @Test
    fun streakBreaksOnShortWeek() {
        val dates = listOf("2026-09-21", "2026-09-28").map(LocalDate::parse)
        assertEquals(0, Training.weeklyStreak(dates, 2, today))
    }

    @Test
    fun parsesWeekdayCodes() {
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            Training.parseWeekdays("MON, WED,FRI,"),
        )
    }
}

class MacroTargetsTest {
    @Test
    fun splitsEnergyIntoProteinFatAndCarbs() {
        val t = Calculations.macroTargets(2400, 80.0)
        assertEquals(160.0, t.proteinG, 1e-9)
        assertEquals(66.67, t.fatG, 0.01)
        // 2400 - 640 protein kcal - 600 fat kcal = 1160 kcal of carbs
        assertEquals(290.0, t.carbsG, 1e-9)
    }

    @Test
    fun carbsNeverNegative() {
        assertEquals(0.0, Calculations.macroTargets(1200, 200.0).carbsG, 0.0)
    }
}
