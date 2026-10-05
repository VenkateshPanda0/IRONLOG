package app.ironlog.personal.domain

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*
import app.ironlog.personal.text.format


enum class CardioType(val label: String, val met: Double, val hasDistance: Boolean) {
    RUN("Run", 9.8, true),
    WALK("Walk", 3.5, true),
    CYCLE("Cycle", 7.5, true),
    SWIM("Swim", 7.0, true),
    ROW("Row", 7.0, true),
    HIKE("Hike", 6.0, true),
    HIIT("HIIT", 8.0, false),
    SPORT("Sport", 7.0, false),
    MOBILITY("Mobility", 2.5, false),
    OTHER("Other", 5.0, false),
}

data class Readiness(val score: Int, val label: String, val advice: String)

object Wellness {
    /** Estimated energy use from MET × body weight × hours; shown as an estimate only. */
    fun estimatedCalories(type: CardioType, minutes: Double, weightKg: Double): Int =
        (type.met * weightKg * minutes / 60.0).toInt()

    /** Minutes per km, e.g. 5.5 = 5:30 /km; null without distance. */
    fun paceMinPerKm(minutes: Double, km: Double?): Double? = km?.takeIf { it > 0 }?.let { minutes / it }

    fun formatPace(pace: Double): String {
        val total = (pace * 60).toInt()
        return "%d:%02d /km".format(total / 60, total % 60)
    }

    /**
     * Morning readiness from 0 to 100. Sleep against goal counts 40%, sleep quality 15%, and
     * energy, soreness, stress and mood (1–5 scales, soreness and stress inverted) 45%. Missing
     * answers are left out and the rest re-weighted. Null when nothing was answered.
     */
    fun readiness(
        sleepHours: Double?,
        sleepGoal: Double,
        sleepQuality: Int?,
        energy: Int?,
        soreness: Int?,
        stress: Int?,
        mood: Int?,
    ): Readiness? {
        val parts = mutableListOf<Pair<Double, Double>>() // (score 0..1, weight)
        sleepHours?.let { parts += (it / sleepGoal.coerceAtLeast(1.0)).coerceIn(0.0, 1.0) to 40.0 }
        sleepQuality?.let { parts += (it - 1) / 4.0 to 15.0 }
        energy?.let { parts += (it - 1) / 4.0 to 15.0 }
        soreness?.let { parts += (5 - it) / 4.0 to 10.0 }
        stress?.let { parts += (5 - it) / 4.0 to 10.0 }
        mood?.let { parts += (it - 1) / 4.0 to 10.0 }
        if (parts.isEmpty()) return null
        val score = (parts.sumOf { it.first * it.second } / parts.sumOf { it.second } * 100).toInt().coerceIn(0, 100)
        return when {
            score >= 80 -> Readiness(score, "Primed", "Go for it: push for a PR or add a set.")
            score >= 60 -> Readiness(score, "Ready", "Train as planned.")
            score >= 40 -> Readiness(score, "Strained", "Train, but keep a rep or two in reserve and skip extra volume.")
            else -> Readiness(score, "Recover", "Take a rest or mobility day, walk, hydrate and sleep early.")
        }
    }

    /** Longest run of consecutive days, and the date the run first reached each target. */
    fun streakDates(days: Collection<LocalDate>, targets: List<Int>): Pair<Int, Map<Int, LocalDate>> {
        val sorted = days.distinct().sorted()
        var length = 0
        var best = 0
        val reached = mutableMapOf<Int, LocalDate>()
        sorted.forEachIndexed { i, day ->
            length = if (i > 0 && sorted[i - 1].plusDays(1) == day) length + 1 else 1
            best = maxOf(best, length)
            targets.forEach { t -> if (length >= t && t !in reached) reached[t] = day }
        }
        return best to reached
    }

    /** Current streak ending today or yesterday (today may still be in progress). */
    fun currentStreak(days: Collection<LocalDate>, today: LocalDate): Int {
        val set = days.toSet()
        var day = if (today in set) today else today.minusDays(1)
        var count = 0
        while (day in set) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}

/** Minimal views of wellness rows for XP and achievements. */
data class CardioRecord(val date: LocalDate, val type: CardioType, val minutes: Double, val km: Double?)

data class DayRecord(
    val date: LocalDate,
    val steps: Int?,
    val waterMl: Int,
    val sleepHours: Double?,
    val checkedIn: Boolean,
)

data class WellnessInput(
    val cardio: List<CardioRecord>,
    val days: List<DayRecord>,
    /** Habit ID to the dates it was done. */
    val habitDays: Map<Long, List<LocalDate>>,
    val mobilityDates: List<LocalDate>,
    val measurementDates: List<LocalDate>,
    /** Waist measurements in date order, for the waist-loss achievements. */
    val waist: List<Pair<LocalDate, Double>>,
    val stepGoal: Int,
    val waterGoalMl: Int,
    val sleepGoalHours: Double,
)

object WellnessXp {
    /**
     * Dated XP events: cardio 50 + 1 per minute (max 60), each habit 5 (max 25 a day), each
     * daily goal hit (steps, water, sleep) 10, morning check-in 5.
     */
    fun events(input: WellnessInput): List<Pair<LocalDate, Int>> =
        buildList {
            input.cardio.forEach { add(it.date to 50 + it.minutes.toInt().coerceIn(0, 60)) }
            input.habitDays.values.flatten().groupingBy { it }.eachCount().forEach { (date, n) -> add(date to (5 * n).coerceAtMost(25)) }
            input.days.forEach { d ->
                var xp = 0
                if ((d.steps ?: 0) >= input.stepGoal) xp += 10
                if (d.waterMl >= input.waterGoalMl) xp += 10
                if ((d.sleepHours ?: 0.0) >= input.sleepGoalHours) xp += 10
                if (d.checkedIn) xp += 5
                if (xp > 0) add(d.date to xp)
            }
        }
}
