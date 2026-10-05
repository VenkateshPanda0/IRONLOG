package app.ironlog.personal.domain

import app.ironlog.personal.time.ChronoUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import app.ironlog.personal.time.*
import app.ironlog.personal.text.format


/** What the user turned on. Times are minutes after midnight, local time. */
data class ReminderSettings(
    val workout: Boolean = false,
    val workoutMinutes: Int = 18 * 60,
    val streak: Boolean = false,
    val streakMinutes: Int = 20 * 60,
)

enum class ReminderKind { WORKOUT, STREAK }

data class ReminderMessage(val kind: ReminderKind, val title: String, val text: String)

/** Today's training state, read from the database when an alarm fires. */
data class ReminderState(
    val today: LocalDate,
    val trainingDays: Set<DayOfWeek>,
    val perWeek: Int,
    val workoutDates: List<LocalDate>,
    val workoutInProgress: Boolean,
    /** Next program day, e.g. "Push" with its exercise count; null without an active program. */
    val nextDayName: String? = null,
    val nextDayExercises: Int = 0,
)

/**
 * Decides whether a reminder is worth sending. Alarms fire every day at the chosen time; these
 * rules keep quiet on rest days, after the day's workout, and when a nudge could not help.
 */
object Reminders {
    /** The next time [minutes] after midnight occurs strictly after [now]. */
    fun nextAt(now: LocalDateTime, minutes: Int): LocalDateTime {
        val time = LocalTime.of((minutes / 60).coerceIn(0, 23), (minutes % 60).coerceIn(0, 59))
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    fun format(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** On a training day without a workout yet: what to train, or a comeback nudge after a gap. */
    fun workout(s: ReminderState): ReminderMessage? {
        if (s.today.dayOfWeek !in s.trainingDays || s.workoutInProgress || s.today in s.workoutDates) return null
        val last = s.workoutDates.filter { it < s.today }.maxOrNull()
        val gap = last?.let { ChronoUnit.DAYS.between(it, s.today).toInt() }
        val plan =
            s.nextDayName?.let { name -> "$name is up next" + if (s.nextDayExercises > 0) " · ${s.nextDayExercises} exercises." else "." }
                ?: "Open Ironlog to start a workout."
        return when {
            gap != null && gap >= 7 -> ReminderMessage(ReminderKind.WORKOUT, "Ready for a comeback?", "It's been $gap days. Even a short session restarts the habit. $plan")
            last == null -> ReminderMessage(ReminderKind.WORKOUT, "Your first workout is waiting", plan)
            else -> ReminderMessage(ReminderKind.WORKOUT, "Training day", plan)
        }
    }

    /**
     * Evening nudge when the weekly target is still reachable only if the user trains today:
     * the sessions still needed equal the days left in the week (Monday to Sunday).
     */
    fun streak(s: ReminderState): ReminderMessage? {
        if (s.workoutInProgress || s.today in s.workoutDates) return null
        val target = s.perWeek.coerceAtLeast(1)
        val weekStart = Training.weekStart(s.today)
        val done = s.workoutDates.count { it >= weekStart && it <= s.today }
        val needed = target - done
        val daysLeft = 8 - s.today.dayOfWeek.value
        if (needed <= 0 || needed != daysLeft) return null
        val streak = Training.weeklyStreak(s.workoutDates, target, weekStart.minusDays(1))
        val title = if (streak > 0) "Keep your $streak-week streak" else "Hit your weekly goal"
        val text =
            if (needed == 1) "One more workout this week reaches $target. Today is the last day for it."
            else "You need $needed more workouts this week, so every day counts from today."
        return ReminderMessage(ReminderKind.STREAK, title, text)
    }
}
