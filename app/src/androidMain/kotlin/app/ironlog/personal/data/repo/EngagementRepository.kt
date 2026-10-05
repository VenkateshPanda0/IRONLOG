package app.ironlog.personal.data.repo

import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.Achievement
import app.ironlog.personal.domain.AchievementInput
import app.ironlog.personal.domain.Achievements
import app.ironlog.personal.domain.PhysiqueRecord
import app.ironlog.personal.domain.EngagementInputs
import app.ironlog.personal.domain.EngagementReplay
import app.ironlog.personal.domain.EngagementSummary
import app.ironlog.personal.domain.ExerciseSet
import app.ironlog.personal.domain.Tier
import app.ironlog.personal.domain.CardioRecord
import app.ironlog.personal.domain.CardioType
import app.ironlog.personal.domain.DayRecord
import app.ironlog.personal.domain.WellnessInput
import app.ironlog.personal.domain.WellnessXp
import app.ironlog.personal.domain.Training
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

data class Engagement(
    val summary: EngagementSummary,
    val weeklyStreak: Int,
    val achievements: List<Achievement>,
    val wellnessXp: Int = 0,
) {
    /** Training XP, wellness XP and a bonus for each earned achievement, scaled by difficulty. */
    val totalXp: Int = summary.totalXp + wellnessXp + achievements.filter { it.earned }.sumOf { tierXp(it.def.tier) }
    val level: Int = EngagementReplay.levelFor(totalXp)
    val xpIntoLevel: Int = totalXp - EngagementReplay.xpForLevel(level)
    val xpForNextLevel: Int = EngagementReplay.xpForLevel(level + 1) - EngagementReplay.xpForLevel(level)

    companion object {
        fun tierXp(tier: Tier) =
            when (tier) {
                Tier.BRONZE -> 25
                Tier.SILVER -> 50
                Tier.GOLD -> 100
                Tier.PLATINUM -> 250
                Tier.LEGEND -> 1000
            }
    }
}

/** Live XP, level, streak and achievements, replayed from history whenever it changes. */
class EngagementRepository(private val dao: IronlogDao, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val training = combine(dao.allLoggedSets(), dao.history(), dao.exercises()) { sets, sessions, exercises -> Triple(sets, sessions, exercises) }
    private val body = combine(dao.foodDays(), dao.photos(), dao.weights()) { food, photos, weights -> Triple(food, photos, weights) }
    private val settings = combine(dao.profile(), dao.goal(), dao.physiqueScans()) { profile, goal, scans -> Triple(profile, goal, scans) }
    private val wellnessRows =
        combine(dao.cardio(), dao.dailyLogs(), dao.habitChecks(), dao.measurements()) { cardio, days, checks, measurements ->
            WellnessRows(cardio, days, checks, measurements)
        }

    private data class WellnessRows(
        val cardio: List<app.ironlog.personal.data.db.CardioSessionEntity>,
        val days: List<app.ironlog.personal.data.db.DailyLogEntity>,
        val checks: List<app.ironlog.personal.data.db.HabitCheckEntity>,
        val measurements: List<app.ironlog.personal.data.db.BodyMeasurementEntity>,
    )

    val engagement: Flow<Engagement> =
        combine(training, body, settings, wellnessRows) { (sets, sessions, exercises), (foodDays, photos, weights), (profile, goal, scans), rows ->
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
                val goalReached = EngagementInputs.goalReachedOn(daily, start, goal?.goalWeightKg)
                val perWeek = profile?.daysPerWeek ?: 3
                val photoDates = photos.map { LocalDate.parse(it.date) }
                val g = goal ?: app.ironlog.personal.data.db.GoalEntity()
                val stretching = exercises.filter { it.category.equals("stretching", true) }.map { it.id }.toSet()
                val wellness =
                    WellnessInput(
                        cardio = rows.cardio.map {
                            CardioRecord(LocalDate.parse(it.date), runCatching { CardioType.valueOf(it.type) }.getOrDefault(CardioType.OTHER), it.durationMin, it.distanceKm)
                        },
                        days = rows.days.map { DayRecord(LocalDate.parse(it.date), it.steps, it.waterMl, it.sleepHours, it.energy != null) },
                        habitDays = rows.checks.groupBy({ it.habitId }, { LocalDate.parse(it.date) }),
                        // A workout made only of stretching exercises counts as a mobility session.
                        mobilityDates = sets.groupBy { it.sessionId }.filterValues { list -> list.all { it.exerciseId in stretching } }.values.map { dateOf(it.first().startedAt) },
                        measurementDates = rows.measurements.map { LocalDate.parse(it.date) },
                        waist = rows.measurements.mapNotNull { m -> m.waistCm?.let { LocalDate.parse(m.date) to it } },
                        stepGoal = g.stepGoal,
                        waterGoalMl = g.waterGoalMl,
                        sleepGoalHours = g.sleepGoalHours,
                    )
                val achievements =
                    Achievements.evaluate(
                        AchievementInput(
                            sets = history,
                            sessionStarts = sessions.associate { it.id to Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDateTime() },
                            dailyWeightKg = daily,
                            profileWeightKg = profile?.weightKg,
                            musclesByExercise = exercises.associate { it.id to it.primaryMuscleList },
                            mealDays = foodDays.map { LocalDate.parse(it.date) },
                            completeFoodDays = food.filter(EngagementReplay::isCompleteFoodDay).map { it.date },
                            photoDates = photoDates,
                            goalReachedOn = goalReached,
                            plannedPerWeek = perWeek,
                            wellness = wellness,
                            physique = scans.map { PhysiqueRecord(LocalDate.parse(it.date), it.matchScore, it.shoulder / it.waist) },
                        )
                    )
                Engagement(
                    summary = EngagementReplay.replay(workouts, food, photoDates, goalReached, perWeek),
                    weeklyStreak = Training.weeklyStreak(sessions.map { dateOf(it.startedAt) }, perWeek, LocalDate.now(zone)),
                    achievements = achievements,
                    wellnessXp = WellnessXp.events(wellness).sumOf { it.second },
                )
            }
            .flowOn(Dispatchers.Default)
}
