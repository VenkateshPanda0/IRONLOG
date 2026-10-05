package app.ironlog.personal.data.health

import androidx.room.withTransaction
import app.ironlog.personal.data.db.BodyWeightEntity
import app.ironlog.personal.data.db.CardioSessionEntity
import app.ironlog.personal.data.db.DailyLogEntity
import app.ironlog.personal.domain.CardioType
import app.ironlog.personal.data.db.IronlogDatabase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One day's totals from Health Connect. Sleep belongs to the day you woke up. */
data class HealthDay(val date: LocalDate, val steps: Int?, val sleepHours: Double?)

/** One sleep session; [awake] is time inside it marked awake or out of bed. */
data class SleepSpan(val start: Instant, val end: Instant, val awake: Duration = Duration.ZERO)

/** The kinds of access Ironlog can hold; each part of a sync runs only when its access is granted. */
enum class HealthAccess { STEPS_SLEEP, READ_WEIGHT, WRITE_WEIGHT, READ_EXERCISE, WRITE_EXERCISE, READ_DISTANCE }

/** A weigh-in stored in Health Connect by another app (a smart scale, say). */
data class HealthWeight(val id: String, val time: Instant, val kg: Double)

/** An exercise session from another app; [type] is null for kinds Ironlog does not import. */
data class HealthSession(val id: String, val start: Instant, val end: Instant, val type: CardioType?, val title: String?, val distanceKm: Double?)

/** Something Ironlog writes; [clientId] makes re-sending it an update, never a duplicate. */
data class OutWeight(val clientId: String, val version: Long, val time: Instant, val kg: Double)

/** [type] null means a strength workout. */
data class OutSession(val clientId: String, val version: Long, val start: Instant, val end: Instant, val type: CardioType?, val title: String, val notes: String)

/**
 * Where health data comes from and goes to: Health Connect on a phone, a fake in tests. Reads
 * return only other apps' records; Ironlog's own writes are filtered out by the implementation.
 */
fun interface HealthSource {
    suspend fun read(from: LocalDate, to: LocalDate): List<HealthDay>

    suspend fun granted(): Set<HealthAccess> = setOf(HealthAccess.STEPS_SLEEP)

    suspend fun readWeights(from: Instant, to: Instant): List<HealthWeight> = emptyList()

    suspend fun readSessions(from: Instant, to: Instant, withDistance: Boolean): List<HealthSession> = emptyList()

    suspend fun writeWeights(items: List<OutWeight>) {}

    suspend fun writeSessions(items: List<OutSession>) {}

    suspend fun deleteWeights(clientIds: List<String>) {}

    suspend fun deleteSessions(clientIds: List<String>) {}
}

/** Remembers local deletions so they reach Health Connect and imports are not re-imported. */
interface HealthTombstones {
    /** Health Connect IDs of imported records the user deleted in Ironlog. */
    suspend fun ignored(): Set<String>

    /** Client IDs ("weight:…" or "session:…") of Ironlog's own records still to delete there. */
    suspend fun pendingDeletes(): Set<String>

    suspend fun clearPendingDeletes(done: Set<String>)
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

data class SyncResult(
    val daysRead: Int,
    val daysUpdated: Int,
    val weightsIn: Int = 0,
    val weightsOut: Int = 0,
    val sessionsIn: Int = 0,
    val sessionsOut: Int = 0,
) {
    fun summary(): String {
        val parts =
            listOfNotNull(
                "$daysRead days of steps and sleep".takeIf { daysRead > 0 },
                "$weightsIn weigh-ins in".takeIf { weightsIn > 0 },
                "$sessionsIn sessions in".takeIf { sessionsIn > 0 },
                "$weightsOut weigh-ins and $sessionsOut workouts shared".takeIf { weightsOut + sessionsOut > 0 },
            )
        return if (parts.isEmpty()) "Nothing new in Health Connect." else "Synced: " + parts.joinToString(", ") + "."
    }
}

class HealthSyncer(
    private val db: IronlogDatabase,
    private val source: HealthSource,
    private val tombstones: HealthTombstones? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /**
     * Two-way sync over the last [days] days, today included. Steps, sleep, other apps' weigh-ins
     * and cardio come in; Ironlog's weigh-ins, workouts and cardio go out. Each part runs only with
     * its access granted, so a partial grant still syncs what it can.
     */
    suspend fun sync(days: Int = 30, today: LocalDate = LocalDate.now()): SyncResult {
        val from = today.minusDays(days - 1L)
        val fromInstant = from.atStartOfDay(zone).toInstant()
        val toInstant = today.plusDays(1).atStartOfDay(zone).toInstant()
        val access = source.granted()
        val dao = db.dao()
        val ignored = tombstones?.ignored().orEmpty()

        var daysRead = 0
        var daysUpdated = 0
        if (HealthAccess.STEPS_SLEEP in access) {
            val read = source.read(from, today)
            daysRead = read.size
            db.withTransaction {
                read.forEach { day ->
                    HealthMerge.merge(dao.dailyLogOnce(day.date.toString()), day)?.let {
                        dao.saveDailyLog(it)
                        daysUpdated++
                    }
                }
            }
        }

        var weightsIn = 0
        if (HealthAccess.READ_WEIGHT in access) {
            // One weigh-in per day: the latest. A day already logged in Ironlog keeps its own value,
            // and a day whose import was deleted in Ironlog stays empty.
            val latest =
                source.readWeights(fromInstant, toInstant)
                    .groupBy { it.time.atZone(zone).toLocalDate() }
                    .filterValues { list -> list.none { it.id in ignored } }
                    .mapValues { (_, list) -> list.maxBy { it.time } }
            db.withTransaction {
                val existing = dao.weightsBetween(from.toString(), today.toString()).groupBy { it.date }
                latest.forEach { (date, w) ->
                    val rows = existing[date.toString()].orEmpty()
                    val imported = rows.firstOrNull { it.healthId == w.id }
                    when {
                        imported != null && kotlin.math.abs(imported.weightKg - w.kg) > 1e-6 -> dao.updateWeight(imported.copy(weightKg = w.kg))
                        imported == null && rows.isEmpty() -> {
                            dao.addWeight(BodyWeightEntity(date = date.toString(), weightKg = w.kg, createdAt = w.time.toEpochMilli(), source = SOURCE, healthId = w.id))
                            weightsIn++
                        }
                    }
                }
            }
        }

        var sessionsIn = 0
        if (HealthAccess.READ_EXERCISE in access) {
            val sessions = source.readSessions(fromInstant, toInstant, HealthAccess.READ_DISTANCE in access)
            db.withTransaction {
                sessions.filter { it.type != null && it.id !in ignored && Duration.between(it.start, it.end) >= Duration.ofMinutes(5) }.forEach { s ->
                    if (dao.cardioByHealthId(s.id) == null) {
                        dao.addCardio(
                            CardioSessionEntity(
                                date = s.start.atZone(zone).toLocalDate().toString(),
                                type = s.type!!.name,
                                durationMin = Duration.between(s.start, s.end).toMinutes().toDouble(),
                                distanceKm = s.distanceKm?.takeIf { it > 0 }?.let { Math.round(it * 100) / 100.0 },
                                notes = s.title.orEmpty(),
                                createdAt = s.end.toEpochMilli(),
                                healthId = s.id,
                            )
                        )
                        sessionsIn++
                    }
                }
            }
        }

        var weightsOut = 0
        if (HealthAccess.WRITE_WEIGHT in access) {
            val mine = dao.weightsBetween(from.toString(), today.toString()).filter { it.healthId == null }
            source.writeWeights(mine.map { OutWeight(HealthIds.weight(it.id), it.createdAt, weighInTime(it), it.weightKg) })
            weightsOut = mine.size
        }

        var sessionsOut = 0
        if (HealthAccess.WRITE_EXERCISE in access) {
            val workouts =
                dao.completedSince(fromInstant.toEpochMilli()).filter { it.endedAt != null && it.endedAt > it.startedAt }.map {
                    OutSession(HealthIds.workout(it.id), it.endedAt!!, Instant.ofEpochMilli(it.startedAt), Instant.ofEpochMilli(it.endedAt), null, it.name, it.notes)
                }
            val cardio =
                dao.cardioBetween(from.toString(), today.toString()).filter { it.healthId == null && it.durationMin > 0 }.map { c ->
                    val type = runCatching { CardioType.valueOf(c.type) }.getOrDefault(CardioType.OTHER)
                    val end = cardioEnd(c)
                    val note = listOfNotNull(c.distanceKm?.let { "%.2f km".format(it) }, c.notes.takeIf { it.isNotBlank() }).joinToString(" · ")
                    OutSession(HealthIds.cardio(c.id), c.createdAt, end.minusSeconds((c.durationMin * 60).toLong()), end, type, type.label, note)
                }
            source.writeSessions(workouts + cardio)
            sessionsOut = workouts.size + cardio.size
        }

        tombstones?.let { t ->
            val pending = t.pendingDeletes()
            if (pending.isNotEmpty()) {
                val done = mutableSetOf<String>()
                val weights = pending.filter { it.startsWith("weight:") }
                val sessions = pending.filter { it.startsWith("session:") }
                if (weights.isNotEmpty() && HealthAccess.WRITE_WEIGHT in access) {
                    source.deleteWeights(weights.map { it.removePrefix("weight:") })
                    done += weights
                }
                if (sessions.isNotEmpty() && HealthAccess.WRITE_EXERCISE in access) {
                    source.deleteSessions(sessions.map { it.removePrefix("session:") })
                    done += sessions
                }
                t.clearPendingDeletes(done)
            }
        }
        return SyncResult(daysRead, daysUpdated, weightsIn, weightsOut, sessionsIn, sessionsOut)
    }

    /** When the weigh-in was logged, or 08:00 on its day when it was entered for another date. */
    private fun weighInTime(w: BodyWeightEntity): Instant {
        val logged = Instant.ofEpochMilli(w.createdAt)
        val date = LocalDate.parse(w.date)
        return if (logged.atZone(zone).toLocalDate() == date) logged else date.atTime(8, 0).atZone(zone).toInstant()
    }

    /** Cardio is logged after the session, so its log time is the end; back-dated entries end at 19:00. */
    private fun cardioEnd(c: CardioSessionEntity): Instant {
        val logged = Instant.ofEpochMilli(c.createdAt)
        val date = LocalDate.parse(c.date)
        return if (logged.atZone(zone).toLocalDate() == date) logged else date.atTime(19, 0).atZone(zone).toInstant()
    }

    companion object {
        const val SOURCE = "HEALTH_CONNECT"
    }
}
