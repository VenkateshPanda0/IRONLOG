package app.ironlog.personal.domain

import app.ironlog.personal.time.ChronoUnit
import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*


enum class GoalStatus {
    AHEAD,
    ON_TRACK,
    BEHIND,
    NEEDS_DATA,
}

data class WeightGoalInput(
    val startKg: Double,
    val targetKg: Double,
    val targetDate: LocalDate?,
    val today: LocalDate,
    val dailyTrendKg: Map<LocalDate, Double>,
)

data class WeightGoalProgress(
    val currentKg: Double?,
    val remainingKg: Double?,
    val percentComplete: Double?,
    val weeklyRateKg: Double?,
    val requiredWeeklyRateKg: Double?,
    val projectedDate: LocalDate?,
    val status: GoalStatus,
)

object GoalEngine {
    fun evaluate(input: WeightGoalInput): WeightGoalProgress {
        val direction =
            when {
                input.targetKg > input.startKg -> 1.0
                input.targetKg < input.startKg -> -1.0
                else -> 0.0
            }
        val current = Calculations.sevenDayMean(input.dailyTrendKg, input.today)
        if (direction == 0.0 || current == null)
            return WeightGoalProgress(current, null, null, null, null, null, GoalStatus.NEEDS_DATA)
        val span = input.startKg - input.targetKg
        val percent = ((input.startKg - current) / span * 100.0).coerceIn(0.0, 100.0)
        val remaining =
            if (direction > 0) (input.targetKg - current).coerceAtLeast(0.0)
            else (current - input.targetKg).coerceAtLeast(0.0)
        val points =
            input.dailyTrendKg
                .filterKeys { it >= input.today.minusDays(28) && it <= input.today }
                .toSortedMap()
        val first = points.entries.firstOrNull()
        val last = points.entries.lastOrNull()
        val actualRate =
            if (
                points.size >= 3 &&
                    first != null &&
                    last != null &&
                    ChronoUnit.DAYS.between(first.key, last.key) >= 14
            ) {
                (last.value - first.value) * direction /
                    ChronoUnit.DAYS.between(first.key, last.key) * 7.0
            } else null
        val daysToTarget =
            input.targetDate?.let { ChronoUnit.DAYS.between(input.today, it) }?.takeIf { it > 0 }
        val required = if (daysToTarget != null) remaining / daysToTarget * 7.0 else null
        val projected =
            if (actualRate != null && actualRate > 0.0 && remaining > 0)
                input.today.plusDays(kotlin.math.ceil(remaining / actualRate * 7.0).toLong())
            else if (remaining == 0.0) input.today else null
        val status =
            when {
                remaining == 0.0 -> GoalStatus.AHEAD
                actualRate == null || required == null -> GoalStatus.NEEDS_DATA
                actualRate <= 0.0 -> GoalStatus.BEHIND
                actualRate >= required * 1.10 -> GoalStatus.AHEAD
                actualRate >= required * 0.90 -> GoalStatus.ON_TRACK
                else -> GoalStatus.BEHIND
            }
        return WeightGoalProgress(
            current,
            remaining,
            percent,
            actualRate,
            required,
            projected,
            status,
        )
    }

    fun bmi(weightKg: Double, heightCm: Double): Double? =
        if (weightKg > 0 && heightCm > 0) weightKg / ((heightCm / 100.0) * (heightCm / 100.0))
        else null

    fun waistHeightRatio(waistCm: Double, heightCm: Double): Double? =
        if (waistCm > 0 && heightCm > 0) waistCm / heightCm else null

    fun showBodyRatios(userOptedIn: Boolean) = userOptedIn

    fun calorieEstimateAllowed(age: Int) = age >= 18

    fun weightLossSuggestionAllowed(age: Int, bmi: Double?) =
        age >= 18 && (bmi == null || bmi >= 18.5)
}
