package app.ironlog.personal.domain

import java.time.LocalDate

object ProgressMath {
    /** Trailing 7-day mean for every logged day that has at least 3 readings in its window. */
    fun movingAverage(daily: Map<LocalDate, Double>): List<Pair<LocalDate, Double>> =
        daily.keys.sorted().mapNotNull { day -> Calculations.sevenDayMean(daily, day)?.let { day to it } }

    /** Total working volume per week for the [weeks] weeks ending with the week of [today]. */
    fun weeklyVolume(sets: List<Pair<LocalDate, Double>>, today: LocalDate, weeks: Int): List<Pair<LocalDate, Double>> {
        val current = Training.weekStart(today)
        val byWeek = sets.groupBy({ Training.weekStart(it.first) }, { it.second }).mapValues { it.value.sum() }
        return (weeks - 1 downTo 0).map { back ->
            val week = current.minusWeeks(back.toLong())
            week to (byWeek[week] ?: 0.0)
        }
    }

    /**
     * Working sets per muscle group over the last 7 days, counting each set once for every primary
     * muscle of its exercise. Every group in [muscles] is present, including those with zero sets.
     */
    fun setsPerMuscle(
        sets: List<Pair<LocalDate, String>>,
        musclesByExercise: Map<String, List<String>>,
        today: LocalDate,
        muscles: List<String>,
    ): List<Pair<String, Int>> {
        val since = today.minusDays(6)
        val counts = mutableMapOf<String, Int>()
        sets.filter { it.first in since..today }.forEach { (_, exerciseId) ->
            musclesByExercise[exerciseId].orEmpty().forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return muscles.map { it to (counts[it] ?: 0) }
    }
}
