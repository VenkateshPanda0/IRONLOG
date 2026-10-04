package app.ironlog.personal.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class DayMark {
    DONE,
    PLANNED,
    MISSED,
    REST,
}

data class WeekDay(val date: LocalDate, val mark: DayMark, val isToday: Boolean)

object Training {
    fun weekStart(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

    /**
     * The seven days of the week containing [today]. A day is DONE when a workout was completed,
     * PLANNED when it is a training weekday today or later, MISSED when a past training weekday has
     * no workout, otherwise REST.
     */
    fun weekStrip(
        today: LocalDate,
        workoutDates: Set<LocalDate>,
        trainingWeekdays: Set<DayOfWeek>,
    ): List<WeekDay> {
        val start = weekStart(today)
        return (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            val mark =
                when {
                    date in workoutDates -> DayMark.DONE
                    date.dayOfWeek !in trainingWeekdays -> DayMark.REST
                    date < today -> DayMark.MISSED
                    else -> DayMark.PLANNED
                }
            WeekDay(date, mark, date == today)
        }
    }

    /**
     * Consecutive weeks, ending with the current week, in which at least [perWeek] workouts were
     * completed. An unfinished current week does not break the streak; it simply is not counted
     * until it reaches the target.
     */
    fun weeklyStreak(workoutDates: Collection<LocalDate>, perWeek: Int, today: LocalDate): Int {
        val target = perWeek.coerceAtLeast(1)
        val counts = workoutDates.groupingBy { weekStart(it) }.eachCount()
        val currentWeek = weekStart(today)
        var streak = if ((counts[currentWeek] ?: 0) >= target) 1 else 0
        var week = currentWeek.minusWeeks(1)
        while ((counts[week] ?: 0) >= target) {
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun parseWeekdays(value: String): Set<DayOfWeek> =
        value
            .split(',')
            .mapNotNull { token ->
                DayOfWeek.entries.firstOrNull { it.name.startsWith(token.trim().uppercase()) }
                    ?.takeIf { token.isNotBlank() }
            }
            .toSet()
}
