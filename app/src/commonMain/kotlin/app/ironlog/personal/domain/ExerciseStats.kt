package app.ironlog.personal.domain

/** Minimal completed-set view used by exercise statistics; mirrors the Room LoggedSet row. */
data class SetRecord(
    val sessionId: Long,
    val startedAt: Long,
    val type: String,
    val weightKg: Double?,
    val reps: Int?,
)

data class ExerciseStats(
    val sessions: Int,
    val sets: Int,
    val bestE1rmKg: Double?,
    val heaviestKg: Double?,
    val mostReps: Int?,
    val bestSetVolumeKg: Double?,
    /** Best estimated 1RM per session, oldest first, for charting. */
    val e1rmTrend: List<Pair<Long, Double>>,
) {
    companion object {
        /** Warm-up sets never count towards records. */
        fun from(rows: List<SetRecord>): ExerciseStats {
            val working = rows.filter { !it.type.equals("WARMUP", ignoreCase = true) }
            val weighted = working.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
            val trend =
                weighted
                    .groupBy { it.sessionId }
                    .mapNotNull { (_, sets) ->
                        sets.mapNotNull { Calculations.e1rm(it.weightKg!!, it.reps!!) }.maxOrNull()?.let {
                            sets.first().startedAt to it
                        }
                    }
                    .sortedBy { it.first }
            return ExerciseStats(
                sessions = working.map { it.sessionId }.distinct().size,
                sets = working.size,
                bestE1rmKg = trend.maxOfOrNull { it.second },
                heaviestKg = weighted.maxOfOrNull { it.weightKg!! },
                mostReps = working.mapNotNull { it.reps }.maxOrNull(),
                bestSetVolumeKg = weighted.maxOfOrNull { it.weightKg!! * it.reps!! },
                e1rmTrend = trend,
            )
        }
    }
}
