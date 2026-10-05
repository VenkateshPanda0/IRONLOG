package app.ironlog.personal.domain

import kotlin.math.roundToLong

/** A completed set with its exercise, from any workout. */
data class ExerciseSet(
    val sessionId: Long,
    val startedAt: Long,
    val exerciseId: String,
    val exerciseName: String,
    val type: String,
    val weightKg: Double?,
    val reps: Int?,
)

data class PersonalBest(val exerciseName: String, val kind: String, val valueKg: Double, val previousKg: Double)

data class ExerciseRecap(val name: String, val sets: Int, val bestSet: ExerciseSet?, val volumeKg: Double)

data class WorkoutSummary(
    val sets: Int,
    val volumeKg: Double,
    val exercises: List<ExerciseRecap>,
    val personalBests: List<PersonalBest>,
)

object WorkoutMath {
    /** Volume counts every completed non-warm-up set with load and reps. */
    fun volume(sets: List<ExerciseSet>): Double =
        sets.filter { !it.type.equals("WARMUP", true) }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) }

    /** Drop sets start at 80% of the previous load, rounded to the nearest plate step (2.5 kg, or 5 lb in kg). */
    fun dropWeight(weightKg: Double?, plateStepKg: Double = 2.5): Double? =
        weightKg?.takeIf { it > 0 }?.let { (it * 0.8 / plateStepKg).roundToLong() * plateStepKg }

    /** Active training time: wall time minus completed and ongoing pauses. */
    fun elapsedMs(startedAt: Long, endedAt: Long?, totalPausedMs: Long, pausedAt: Long?, now: Long): Long {
        val end = endedAt ?: now
        val ongoingPause = if (endedAt == null && pausedAt != null) now - pausedAt else 0L
        return (end - startedAt - totalPausedMs - ongoingPause).coerceAtLeast(0L)
    }

    /**
     * Summarises [sessionId] from [history] (all completed sets, any session). A personal best
     * only counts when the exercise was logged in an earlier session and this session beats it, so
     * first-time exercises do not flood the list.
     */
    fun summarize(sessionId: Long, history: List<ExerciseSet>): WorkoutSummary {
        val current = history.filter { it.sessionId == sessionId }
        val startedAt = current.firstOrNull()?.startedAt ?: Long.MAX_VALUE
        val earlier = history.filter { it.sessionId != sessionId && it.startedAt < startedAt }
        val bests = mutableListOf<PersonalBest>()
        val recaps =
            current.groupBy { it.exerciseId }.map { (exerciseId, sets) ->
                val working = sets.filter { !it.type.equals("WARMUP", true) }
                val loaded = working.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
                val before = earlier.filter { it.exerciseId == exerciseId && !it.type.equals("WARMUP", true) }
                    .filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
                if (before.isNotEmpty() && loaded.isNotEmpty()) {
                    val name = sets.first().exerciseName
                    val heaviest = loaded.maxOf { it.weightKg!! }
                    val previousHeaviest = before.maxOf { it.weightKg!! }
                    if (heaviest > previousHeaviest) bests += PersonalBest(name, "Heaviest", heaviest, previousHeaviest)
                    val e1rm = loaded.mapNotNull { Calculations.e1rm(it.weightKg!!, it.reps!!) }.maxOrNull()
                    val previousE1rm = before.mapNotNull { Calculations.e1rm(it.weightKg!!, it.reps!!) }.maxOrNull()
                    if (e1rm != null && previousE1rm != null && e1rm > previousE1rm) {
                        bests += PersonalBest(name, "Est. 1RM", e1rm, previousE1rm)
                    }
                }
                ExerciseRecap(
                    name = sets.first().exerciseName,
                    sets = working.size,
                    bestSet = loaded.maxByOrNull { Calculations.e1rm(it.weightKg!!, it.reps!!) ?: it.weightKg!! }
                        ?: working.maxByOrNull { it.reps ?: 0 },
                    volumeKg = volume(sets),
                )
            }
        return WorkoutSummary(
            sets = current.count { !it.type.equals("WARMUP", true) },
            volumeKg = volume(current),
            exercises = recaps,
            personalBests = bests,
        )
    }

    /**
     * Personal bests per session in one chronological pass, with the same rules as [summarize]:
     * heaviest load and best estimated 1RM per exercise, counted only when an earlier session
     * logged that exercise. Linear in history size, so years of logs stay fast.
     */
    fun personalBestCounts(history: List<ExerciseSet>): Map<Long, Int> {
        val heaviest = mutableMapOf<String, Double>()
        val bestE1rm = mutableMapOf<String, Double>()
        val counts = mutableMapOf<Long, Int>()
        history
            .filter { !it.type.equals("WARMUP", true) && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            .groupBy { it.sessionId }
            .entries
            .sortedBy { it.value.first().startedAt }
            .forEach { (sessionId, sets) ->
                var count = 0
                sets.groupBy { it.exerciseId }.forEach { (exercise, rows) ->
                    val top = rows.maxOf { it.weightKg!! }
                    val e1rm = rows.mapNotNull { Calculations.e1rm(it.weightKg!!, it.reps!!) }.maxOrNull()
                    val previousTop = heaviest[exercise]
                    if (previousTop != null) {
                        if (top > previousTop) count++
                        val previousE1rm = bestE1rm[exercise]
                        if (e1rm != null && previousE1rm != null && e1rm > previousE1rm) count++
                    }
                    heaviest[exercise] = maxOf(previousTop ?: top, top)
                    if (e1rm != null) bestE1rm[exercise] = maxOf(bestE1rm[exercise] ?: e1rm, e1rm)
                }
                counts[sessionId] = count
            }
        return counts
    }
}
