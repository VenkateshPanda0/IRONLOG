package app.ironlog.personal.domain

import java.time.LocalDate

/** Builds [EngagementReplay] inputs from raw history so XP is always derived, never stored. */
object EngagementInputs {
    /**
     * One entry per completed workout with its working sets, volume and personal bests (measured
     * against earlier sessions only, as on the summary screen).
     */
    fun workouts(history: List<ExerciseSet>, dateOf: (Long) -> LocalDate): List<WorkoutXpInput> {
        val bests = WorkoutMath.personalBestCounts(history)
        return history.groupBy { it.sessionId }.map { (sessionId, sets) ->
            WorkoutXpInput(
                date = dateOf(sets.first().startedAt),
                completedWorkingSets = sets.count { !it.type.equals("WARMUP", true) },
                newPrCount = bests[sessionId] ?: 0,
                sessionVolumeKg = WorkoutMath.volume(sets),
            )
        }
    }

    fun foodDays(days: List<Triple<LocalDate, Double, Set<String>>>, kcalTarget: Int?): List<FoodXpInput> =
        days.map { (date, kcal, meals) -> FoodXpInput(date, kcal, (kcalTarget ?: 0).toDouble(), meals) }

    /**
     * First day the daily weight reached the goal, in the direction of travel from [startKg].
     * Null when there is no goal or it has not been reached.
     */
    fun goalReachedOn(daily: Map<LocalDate, Double>, startKg: Double?, goalKg: Double?): LocalDate? {
        if (startKg == null || goalKg == null || startKg == goalKg) return null
        val losing = goalKg < startKg
        return daily.toSortedMap().entries.firstOrNull { (_, kg) -> if (losing) kg <= goalKg else kg >= goalKg }?.key
    }
}

data class MedalInfo(val id: String, val title: String, val description: String)

object Medals {
    val ALL =
        listOf(
            MedalInfo("workouts_1", "First Rep", "Finish your first workout"),
            MedalInfo("workouts_10", "Ten Down", "Finish 10 workouts"),
            MedalInfo("workouts_50", "Half Century", "Finish 50 workouts"),
            MedalInfo("workouts_100", "Centurion", "Finish 100 workouts"),
            MedalInfo("workouts_250", "Iron Regular", "Finish 250 workouts"),
            MedalInfo("first_pr", "New Standard", "Set your first personal best"),
            MedalInfo("prs_25", "Record Breaker", "Set 25 personal bests"),
            MedalInfo("prs_100", "Unstoppable", "Set 100 personal bests"),
            MedalInfo("volume_100000kg", "100 Tonnes", "Lift 100,000 kg in total"),
            MedalInfo("training_4_week_streak", "Consistent", "Hit your weekly target 4 weeks in a row"),
            MedalInfo("training_12_week_streak", "Relentless", "Hit your weekly target 12 weeks in a row"),
            MedalInfo("food_7_day_streak", "Dialed In", "Log a complete food day 7 days in a row"),
            MedalInfo("food_30_day_streak", "Fuel Master", "Log a complete food day 30 days in a row"),
            MedalInfo("first_progress_photo", "Day One", "Add your first progress photo"),
            MedalInfo("goal_weight_reached", "Goal Weight", "Reach your goal body weight"),
        )

    fun info(id: String) = ALL.firstOrNull { it.id == id } ?: MedalInfo(id, id, "")
}
