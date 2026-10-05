package app.ironlog.personal.domain

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WellnessTest {
    @Test
    fun caloriesAndPace() {
        assertEquals(490, Wellness.estimatedCalories(CardioType.RUN, 30.0, 100.0))
        assertEquals(5.5, Wellness.paceMinPerKm(27.5, 5.0)!!, 1e-9)
        assertEquals("5:30 /km", Wellness.formatPace(5.5))
        assertNull(Wellness.paceMinPerKm(30.0, null))
    }

    @Test
    fun readinessWeightsAnswersAndAdvises() {
        val great = Wellness.readiness(8.0, 8.0, 5, 5, 1, 1, 5)!!
        assertEquals(100, great.score)
        assertEquals("Primed", great.label)
        val poor = Wellness.readiness(4.0, 8.0, 1, 1, 5, 5, 1)!!
        // Only sleep hours score (0.5 x 40) out of 100 weight.
        assertEquals(20, poor.score)
        assertEquals("Recover", poor.label)
        // Only sleep answered: 6/8 hours.
        assertEquals(75, Wellness.readiness(6.0, 8.0, null, null, null, null, null)!!.score)
        assertNull(Wellness.readiness(null, 8.0, null, null, null, null, null))
    }

    @Test
    fun streaks() {
        val d = LocalDate.parse("2026-10-01")
        val days = listOf(d, d.plusDays(1), d.plusDays(2), d.plusDays(4))
        val (best, reached) = Wellness.streakDates(days, listOf(2, 3, 7))
        assertEquals(3, best)
        assertEquals(d.plusDays(2), reached[3])
        assertNull(reached[7])
        assertEquals(1, Wellness.currentStreak(days, d.plusDays(5)))
        assertEquals(0, Wellness.currentStreak(days, d.plusDays(7)))
    }
}
