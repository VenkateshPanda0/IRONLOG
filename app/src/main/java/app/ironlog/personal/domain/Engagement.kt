package app.ironlog.personal.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class WorkoutXpInput(
    val date: LocalDate,
    val completedWorkingSets: Int,
    val newPrCount: Int,
    val sessionVolumeKg: Double,
)

data class FoodXpInput(
    val date: LocalDate,
    val kcal: Double,
    val kcalTarget: Double,
    val mealTypes: Set<String>,
)

data class Medal(val id: String, val earnedOn: LocalDate?)

data class EngagementSummary(
    val totalXp: Int,
    val level: Int,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int,
    val workouts: Int,
    val workingSets: Int,
    val personalRecords: Int,
    val lifetimeVolumeKg: Double,
    val medals: List<Medal>,
)

/** Deterministic replay: all totals are derived from source history, never stored counters. */
object EngagementReplay {
    private val workoutThresholds = listOf(1, 10, 50, 100, 250)

    fun xpForLevel(level: Int) = 500 * level * (level + 1) / 2

    fun levelFor(xp: Int): Int {
        var level = 0
        while (xp >= xpForLevel(level + 1)) level++
        return level
    }

    fun replay(
        workouts: List<WorkoutXpInput>,
        foodDays: List<FoodXpInput>,
        photoDates: List<LocalDate> = emptyList(),
        goalReachedOn: LocalDate? = null,
        plannedPerWeek: Int = 3,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): EngagementSummary {
        val done = workouts.sortedBy { it.date }
        val foods = foodDays.associateBy { it.date }
        var xp = 0
        done.forEach {
            xp +=
                100 +
                    (5 * it.completedWorkingSets.coerceAtLeast(0)).coerceAtMost(250) +
                    50 * it.newPrCount.coerceAtLeast(0)
        }
        foods.values.forEach { if (isCompleteFoodDay(it)) xp += 10 }
        val level = levelFor(xp)
        val next = xpForLevel(level + 1)
        val current = xpForLevel(level)
        val medals = mutableListOf<Medal>()
        workoutThresholds.forEach { n ->
            medals += Medal("workouts_$n", done.getOrNull(n - 1)?.date)
        }
        medals += Medal("first_pr", done.firstOrNull { it.newPrCount > 0 }?.date)
        val prDates = done.flatMap { row -> List(row.newPrCount.coerceAtLeast(0)) { row.date } }
        medals += Medal("prs_25", prDates.getOrNull(24))
        medals += Medal("prs_100", prDates.getOrNull(99))
        var volume = 0.0
        var volumeUnlock: LocalDate? = null
        done.forEach { row ->
            volume += row.sessionVolumeKg.coerceAtLeast(0.0)
            if (volumeUnlock == null && volume >= 100_000.0) volumeUnlock = row.date
        }
        medals += Medal("volume_100000kg", volumeUnlock)
        val completeFoodDates = foods.values.filter(::isCompleteFoodDay).map { it.date }
        medals += Medal("food_7_day_streak", streakUnlock(completeFoodDates, 7))
        medals += Medal("food_30_day_streak", streakUnlock(completeFoodDates, 30))
        val weekly =
            done.groupBy { it.date.with(TemporalAdjusters.previousOrSame(weekStart)) }.toSortedMap()
        var streak = 0
        var streakUnlock4: LocalDate? = null
        var streakUnlock12: LocalDate? = null
        var previous: LocalDate? = null
        weekly.forEach { (week, records) ->
            streak =
                if (records.size >= plannedPerWeek.coerceAtLeast(1)) {
                    if (previous != null && week == previous!!.plusWeeks(1)) streak + 1 else 1
                } else 0
            if (streak == 4 && streakUnlock4 == null) streakUnlock4 = records.maxOf { it.date }
            if (streak == 12 && streakUnlock12 == null) streakUnlock12 = records.maxOf { it.date }
            previous = week
        }
        medals += Medal("training_4_week_streak", streakUnlock4)
        medals += Medal("training_12_week_streak", streakUnlock12)
        medals += Medal("first_progress_photo", photoDates.minOrNull())
        medals += Medal("goal_weight_reached", goalReachedOn)
        return EngagementSummary(
            xp,
            level,
            xp - current,
            next - current,
            done.size,
            done.sumOf { it.completedWorkingSets.coerceAtLeast(0) },
            done.sumOf { it.newPrCount.coerceAtLeast(0) },
            volume,
            medals,
        )
    }

    fun isCompleteFoodDay(day: FoodXpInput): Boolean {
        val allMeals = setOf("BREAKFAST", "LUNCH", "DINNER", "SNACK").all { it in day.mealTypes }
        val inRange = day.kcalTarget > 0 && day.kcal in day.kcalTarget * 0.85..day.kcalTarget * 1.15
        return allMeals || inRange
    }

    private fun streakUnlock(dates: List<LocalDate>, threshold: Int): LocalDate? {
        val days = dates.distinct().sorted()
        if (days.isEmpty()) return null
        var length = 1
        for (index in 1 until days.size) {
            length = if (days[index] == days[index - 1].plusDays(1)) length + 1 else 1
            if (length == threshold) return days[index]
        }
        return if (threshold == 1) days.first() else null
    }
}

data class ExerciseCandidate(
    val id: String,
    val name: String,
    val primaryMuscles: Set<String>,
    val equipment: Set<String>,
)

object QuickWorkoutGenerator {
    fun exerciseCount(minutes: Int): Int =
        when {
            minutes <= 15 -> 3
            minutes <= 30 -> 4
            minutes <= 45 -> 6
            else -> 8
        }

    fun generate(
        exercises: List<ExerciseCandidate>,
        targetMuscles: Set<String>,
        equipment: Set<String>,
        avoidList: Set<String>,
        minutes: Int,
    ): List<ExerciseCandidate> {
        val limit = exerciseCount(minutes)
        val avoid = avoidList.map { it.trim().lowercase() }.filter(String::isNotBlank)
        val targets = targetMuscles.map(String::lowercase).toSet()
        val allowed = equipment.map(String::lowercase).toSet()
        return exercises
            .asSequence()
            .filter { candidate ->
                avoid.none { term ->
                    candidate.name.lowercase().contains(term) ||
                        candidate.primaryMuscles.any { it.lowercase().contains(term) }
                }
            }
            .filter { candidate ->
                allowed.isEmpty() ||
                    candidate.equipment.isEmpty() ||
                    candidate.equipment.any { it.lowercase() in allowed }
            }
            .sortedWith(
                compareByDescending<ExerciseCandidate> {
                        it.primaryMuscles.any { muscle -> muscle.lowercase() in targets }
                    }
                    .thenByDescending { candidate ->
                        candidate.equipment.count { it.lowercase() in allowed }
                    }
                    .thenBy { Staples.rank(it.id) }
                    .thenBy { it.name.lowercase() }
            )
            .take(limit)
            .toList()
    }
}
