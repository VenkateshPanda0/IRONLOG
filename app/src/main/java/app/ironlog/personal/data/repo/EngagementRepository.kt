package app.ironlog.personal.data.repo

import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.domain.EngagementInputs
import app.ironlog.personal.domain.EngagementReplay
import app.ironlog.personal.domain.EngagementSummary
import app.ironlog.personal.domain.ExerciseSet
import app.ironlog.personal.domain.Training
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class Engagement(val summary: EngagementSummary, val weeklyStreak: Int)

/** Live XP, level, medals and streak, replayed from history whenever it changes. */
class EngagementRepository(private val dao: IronlogDao, private val zone: ZoneId = ZoneId.systemDefault()) {
    val engagement: Flow<Engagement> =
        combine(dao.allLoggedSets(), dao.foodDays(), dao.photos(), dao.weights(), combine(dao.profile(), dao.goal()) { p, g -> p to g }) {
                sets, foodDays, photos, weights, (profile, goal) ->
                val dateOf = { ms: Long -> Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
                val history = sets.map { ExerciseSet(it.sessionId, it.startedAt, it.exerciseId, it.exerciseName, it.type, it.weightKg, it.reps) }
                val workouts = EngagementInputs.workouts(history, dateOf)
                val food =
                    EngagementInputs.foodDays(
                        foodDays.map { Triple(LocalDate.parse(it.date), it.kcal, it.mealTypes.split(',').toSet()) },
                        goal?.kcalTarget,
                    )
                val daily = weights.groupBy { LocalDate.parse(it.date) }.mapValues { (_, rows) -> rows.maxBy { it.createdAt }.weightKg }
                val start = weights.minByOrNull { it.date }?.weightKg ?: profile?.weightKg
                val perWeek = profile?.daysPerWeek ?: 3
                Engagement(
                    summary =
                        EngagementReplay.replay(
                            workouts = workouts,
                            foodDays = food,
                            photoDates = photos.map { LocalDate.parse(it.date) },
                            goalReachedOn = EngagementInputs.goalReachedOn(daily, start, goal?.goalWeightKg),
                            plannedPerWeek = perWeek,
                        ),
                    weeklyStreak = Training.weeklyStreak(workouts.map { it.date }, perWeek, LocalDate.now(zone)),
                )
            }
            .flowOn(Dispatchers.Default)
}
