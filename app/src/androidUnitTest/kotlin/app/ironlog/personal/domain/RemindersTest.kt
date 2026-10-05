package app.ironlog.personal.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.DayOfWeek.*
import app.ironlog.personal.time.*

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemindersTest {
    private val monday = LocalDate.parse("2026-10-05")
    private val mwf = setOf(MONDAY, WEDNESDAY, FRIDAY)

    private fun state(today: LocalDate, dates: List<LocalDate> = emptyList(), inProgress: Boolean = false, perWeek: Int = 3) =
        ReminderState(today, mwf, perWeek, dates, inProgress, "Push", 6)

    @Test
    fun nextAtIsLaterTodayOrTomorrow() {
        assertEquals(LocalDateTime.parse("2026-10-05T18:00"), Reminders.nextAt(LocalDateTime.parse("2026-10-05T09:00"), 18 * 60))
        assertEquals(LocalDateTime.parse("2026-10-06T18:00"), Reminders.nextAt(LocalDateTime.parse("2026-10-05T18:00"), 18 * 60))
        assertEquals(LocalDateTime.parse("2026-10-06T06:30"), Reminders.nextAt(LocalDateTime.parse("2026-10-05T19:00"), 6 * 60 + 30))
        assertEquals("06:30", Reminders.format(390))
    }

    @Test
    fun workoutReminderOnlyOnUntrainedTrainingDays() {
        val m = Reminders.workout(state(monday, listOf(monday.minusDays(3))))!!
        assertEquals("Training day", m.title)
        assertEquals("Push is up next · 6 exercises.", m.text)
        assertNull(Reminders.workout(state(monday.plusDays(1)))) // Tuesday is a rest day
        assertNull(Reminders.workout(state(monday, listOf(monday)))) // already trained
        assertNull(Reminders.workout(state(monday, inProgress = true)))
        assertEquals("Your first workout is waiting", Reminders.workout(state(monday))!!.title)
        val comeback = Reminders.workout(state(monday, listOf(monday.minusDays(10))))!!
        assertTrue(comeback.text.startsWith("It's been 10 days"))
    }

    @Test
    fun streakNudgeOnlyWhenTodayDecidesTheWeek() {
        val friday = monday.plusDays(4)
        val sunday = monday.plusDays(6)
        // Three a week, one done by Friday: still 3 days left for 2 sessions, no nudge.
        assertNull(Reminders.streak(state(friday, listOf(monday))))
        // Two done by Sunday: today is the last chance.
        val lastWeek = listOf(-7L, -5L, -3L).map { monday.plusDays(it) }
        val nudge = Reminders.streak(state(sunday, lastWeek + listOf(monday, monday.plusDays(2))))!!
        assertEquals("Keep your 1-week streak", nudge.title)
        assertTrue(nudge.text.contains("Today is the last day"))
        // Already trained today, target reached, or no longer reachable: quiet.
        assertNull(Reminders.streak(state(sunday, listOf(monday, monday.plusDays(2), sunday))))
        assertNull(Reminders.streak(state(sunday, listOf(monday, monday.plusDays(2), monday.plusDays(4)))))
        assertNull(Reminders.streak(state(sunday, emptyList())))
        // Saturday with two still needed: both remaining days count.
        val saturday = monday.plusDays(5)
        assertEquals("Hit your weekly goal", Reminders.streak(state(saturday, listOf(monday)))!!.title)
    }
}
