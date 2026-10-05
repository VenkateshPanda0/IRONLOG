package app.ironlog.personal.data.health

import androidx.room.withTransaction
import app.ironlog.personal.data.db.DailyLogEntity
import app.ironlog.personal.data.db.IronlogDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day's totals from Health Connect. Sleep belongs to the day you woke up. */
data class HealthDay(val date: LocalDate, val steps: Int?, val sleepHours: Double?)

/** One sleep session; [awake] is time inside it marked awake or out of bed. */
data class SleepSpan(val start: Instant, val end: Instant, val awake: Duration = Duration.ZERO)

/** Where health data comes from; Health Connect on a phone, a fake in tests. */
fun interface HealthSource {
    suspend fun read(from: LocalDate, to: LocalDate): List<HealthDay>
}

object HealthMerge {
    /**
     * Sleep per wake-up date: session length minus awake time, naps included, rounded to 0.1 h.
     * Sessions shorter than 15 minutes are ignored as noise.
     */
    fun sleepByDate(spans: List<SleepSpan>, zone: ZoneId): Map<LocalDate, Double> =
        spans
            .map { it.end.atZone(zone).toLocalDate() to (Duration.between(it.start, it.end) - it.awake) }
            .filter { (_, asleep) -> asleep >= Duration.ofMinutes(15) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, list) -> Math.round(list.fold(Duration.ZERO, Duration::plus).toMinutes() / 6.0) / 10.0 }

    /**
     * Applies a Health Connect day to the stored row. Values typed in by hand win: steps are only
     * replaced by a higher count (the phone saw more walking than was entered), sleep never.
     * Values imported earlier are refreshed. Returns null when nothing changes.
     */
    fun merge(existing: DailyLogEntity?, day: HealthDay): DailyLogEntity? {
        val row = existing ?: DailyLogEntity(date = day.date.toString())
        var out = row
        val steps = day.steps?.takeIf { it > 0 }?.coerceAtMost(200_000)
        if (steps != null && (row.steps == null || row.stepsFromHealth || steps > row.steps)) {
            out = out.copy(steps = steps, stepsFromHealth = true)
        }
        val sleep = day.sleepHours?.takeIf { it > 0 }?.coerceAtMost(24.0)
        if (sleep != null && (row.sleepHours == null || row.sleepFromHealth)) {
            out = out.copy(sleepHours = sleep, sleepFromHealth = true)
        }
        return out.takeIf { it != existing && (existing != null || it != row) }
    }
}

data class SyncResult(val daysRead: Int, val daysUpdated: Int)

class HealthSyncer(private val db: IronlogDatabase, private val source: HealthSource) {
    /** Imports the last [days] days, today included, in one transaction. */
    suspend fun sync(days: Int = 30, today: LocalDate = LocalDate.now()): SyncResult {
        val read = source.read(today.minusDays(days - 1L), today)
        var updated = 0
        db.withTransaction {
            val dao = db.dao()
            read.forEach { day ->
                HealthMerge.merge(dao.dailyLogOnce(day.date.toString()), day)?.let {
                    dao.saveDailyLog(it)
                    updated++
                }
            }
        }
        return SyncResult(read.size, updated)
    }
}
