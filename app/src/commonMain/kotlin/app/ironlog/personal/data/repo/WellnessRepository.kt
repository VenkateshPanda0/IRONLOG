package app.ironlog.personal.data.repo

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import app.ironlog.personal.data.transaction
import app.ironlog.personal.data.db.BodyMeasurementEntity
import app.ironlog.personal.data.db.CardioSessionEntity
import app.ironlog.personal.data.db.DailyLogEntity
import app.ironlog.personal.data.db.HabitCheckEntity
import app.ironlog.personal.data.db.HabitEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.flow.Flow

/** Cardio, steps, water, sleep, readiness check-ins, habits and body measurements. */
class WellnessRepository(
    private val db: IronlogDatabase,
    /** Told about each deleted cardio session, so the deletion can reach Health Connect. */
    private val onCardioDeleted: suspend (CardioSessionEntity) -> Unit = {},
) {
    private val dao = db.dao()

    val cardio: Flow<List<CardioSessionEntity>> = dao.cardio()
    val dailyLogs: Flow<List<DailyLogEntity>> = dao.dailyLogs()
    val habits: Flow<List<HabitEntity>> = dao.habits()
    val habitChecks: Flow<List<HabitCheckEntity>> = dao.habitChecks()
    val measurements: Flow<List<BodyMeasurementEntity>> = dao.measurements()

    fun day(date: LocalDate): Flow<DailyLogEntity?> = dao.dailyLog(date.toString())

    suspend fun addCardio(value: CardioSessionEntity) = dao.addCardio(value)

    suspend fun deleteCardio(id: Long) {
        val row = dao.cardioById(id) ?: return
        dao.deleteCardio(id)
        onCardioDeleted(row)
    }

    /** Read-modify-write of one day's row inside a transaction so quick taps never lose updates. */
    suspend fun updateDay(date: LocalDate, change: (DailyLogEntity) -> DailyLogEntity) =
        db.transaction {
            val current = dao.dailyLogOnce(date.toString()) ?: DailyLogEntity(date = date.toString())
            dao.saveDailyLog(change(current))
        }

    suspend fun addWater(date: LocalDate, ml: Int) = updateDay(date) { it.copy(waterMl = (it.waterMl + ml).coerceIn(0, 20_000)) }

    suspend fun setSteps(date: LocalDate, steps: Int?) = updateDay(date) { it.copy(steps = steps?.coerceIn(0, 200_000), stepsFromHealth = false) }

    suspend fun setSleep(date: LocalDate, hours: Double?, quality: Int?) =
        updateDay(date) { it.copy(sleepHours = hours?.coerceIn(0.0, 24.0), sleepQuality = quality?.coerceIn(1, 5), sleepFromHealth = false) }

    suspend fun checkIn(date: LocalDate, energy: Int, soreness: Int, stress: Int, mood: Int) =
        updateDay(date) { it.copy(energy = energy, soreness = soreness, stress = stress, mood = mood) }

    suspend fun addHabit(name: String) = dao.addHabit(HabitEntity(name = name.trim()))

    suspend fun archiveHabit(id: Long) = dao.archiveHabit(id)

    suspend fun setHabitDone(habitId: Long, date: LocalDate, done: Boolean) =
        if (done) dao.checkHabit(HabitCheckEntity(habitId, date.toString())) else dao.uncheckHabit(habitId, date.toString())

    suspend fun addMeasurement(value: BodyMeasurementEntity) = dao.addMeasurement(value)

    suspend fun deleteMeasurement(id: Long) = dao.deleteMeasurement(id)
}
