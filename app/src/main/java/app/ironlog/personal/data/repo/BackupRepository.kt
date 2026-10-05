package app.ironlog.personal.data.repo

import androidx.room.withTransaction
import app.ironlog.personal.data.db.BackupSnapshot
import app.ironlog.personal.data.db.IronlogDatabase
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(private val db: IronlogDatabase) {
    private val json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    suspend fun exportJson(): String = json.encodeToString(db.dao().backupSnapshot())

    suspend fun importJson(text: String) {
        val backup = runCatching {
            json.decodeFromString<BackupSnapshot>(text)
        }
            .getOrElse { throw IllegalArgumentException("Backup is invalid or unreadable", it) }
        require(backup.schemaVersion == 1) { "Unsupported backup schema ${backup.schemaVersion}" }
        validate(backup)
        db.withTransaction {
            val dao = db.dao()
            dao.deleteSets()
            dao.deleteSessionExercises()
            dao.deleteAllSessions()
            dao.deleteAllMeals()
            dao.deleteAllServings()
            dao.deleteAllFoods()
            dao.deleteAllWeights()
            dao.deleteAllActivePrograms()
            dao.deleteAllSkippedProgramDays()
            dao.deleteAllPrescriptions()
            dao.deleteAllProgramDays()
            dao.deleteAllPrograms()
            dao.deleteAllExercises()
            dao.deleteAllProfiles()
            dao.deleteAllGoals()
            dao.clearRestTimer()
            dao.deleteAllCardio()
            dao.deleteAllDailyLogs()
            dao.deleteAllHabits()
            dao.deleteAllMeasurements()
            dao.deleteAllPhysiqueScans()
            dao.restoreExercises(backup.exercises)
            dao.restorePrograms(backup.programs)
            dao.restoreProgramDays(backup.programDays)
            dao.restorePrescriptions(backup.prescriptions)
            dao.restoreActivePrograms(backup.activePrograms)
            dao.restoreSkippedProgramDays(backup.skippedProgramDays)
            dao.restoreProfiles(backup.profile)
            dao.restoreSessions(backup.sessions)
            dao.restoreSessionExercises(backup.sessionExercises)
            dao.restoreSets(backup.sets)
            dao.restoreFoods(backup.foods)
            dao.restoreServings(backup.servings)
            dao.restoreMeals(backup.meals)
            dao.restoreWeights(backup.weights)
            dao.restoreGoals(backup.goals)
            dao.restoreCardio(backup.cardio)
            dao.restoreDailyLogs(backup.dailyLogs)
            dao.restoreHabits(backup.habits)
            dao.restoreHabitChecks(backup.habitChecks)
            dao.restoreMeasurements(backup.measurements)
            dao.restorePhysiqueScans(backup.physiqueScans)
        }
    }

    private fun validate(value: BackupSnapshot) {
        require(
            value.profile.size <= 1 && value.activePrograms.size <= 1 && value.goals.size <= 1
        ) {
            "Backup contains duplicate singleton rows"
        }
        val programIds = value.programs.map { it.id }.toSet()
        require(
            value.skippedProgramDays.all {
                it.programId in programIds && runCatching { LocalDate.parse(it.date) }.isSuccess
            } && value.skippedProgramDays.map { it.programId to it.date }.distinct().size ==
                value.skippedProgramDays.size
        ) {
            "Backup contains invalid skipped program days"
        }
        require(
            value.meals.all {
                runCatching { LocalDate.parse(it.date) }.isSuccess &&
                    listOf(it.grams, it.kcal, it.protein, it.carbs, it.fat).all(Double::isFinite) &&
                    it.grams >= 0
            }
        ) {
            "Backup contains invalid meal data"
        }
        require(
            value.weights.all {
                runCatching { LocalDate.parse(it.date) }.isSuccess &&
                    it.weightKg.isFinite() &&
                    it.weightKg > 0
            }
        ) {
            "Backup contains invalid weight data"
        }
        val sessionExerciseIds = value.sessionExercises.map { it.id }.toSet()
        require(value.sets.all { it.sessionExerciseId in sessionExerciseIds }) {
            "A set references a missing exercise row"
        }
        require(
            value.sessionExercises.all { row -> value.sessions.any { it.id == row.sessionId } }
        ) {
            "A workout exercise references a missing session"
        }
        val habits = value.habits.map { it.id }.toSet()
        require(value.habitChecks.all { it.habitId in habits }) { "A habit check references a missing habit" }
        require(
            (value.cardio.map { it.date } + value.dailyLogs.map { it.date } + value.measurements.map { it.date } + value.physiqueScans.map { it.date })
                .all { runCatching { LocalDate.parse(it) }.isSuccess }
        ) {
            "Backup contains invalid wellness dates"
        }
        val foods = value.foods.map { it.id }.toSet()
        require(value.meals.all { it.foodId == null || it.foodId in foods }) {
            "A meal references a missing food"
        }
    }
}
