package app.ironlog.personal.data.repo

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import androidx.room.withTransaction
import app.ironlog.personal.data.db.BackupSnapshot
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class BackupRepository(private val db: IronlogDatabase) {
    private val json = Json {
        ignoreUnknownKeys = false
        prettyPrint = true
    }

    suspend fun exportJson(): String = json.encodeToString(db.dao().backupSnapshot())

    /** Reads a backup without importing it, e.g. to show what a restore would bring back. */
    fun peek(text: String): BackupSnapshot = json.decodeFromString(text)

    /**
     * Replaces all data with [text]. [hasPhoto] says whether a photo's image file is available;
     * rows without one are skipped so no broken photos appear.
     */
    suspend fun importJson(text: String, hasPhoto: (String) -> Boolean = { true }) {
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
            dao.deleteAllPhotos()
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
            dao.restorePhotos(backup.photos.filter { hasPhoto(it.fileName) })
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
            value.sets.all { s ->
                (s.weightKg == null || (s.weightKg.isFinite() && s.weightKg in 0.0..2000.0)) &&
                    (s.reps == null || s.reps in 0..1000) &&
                    (s.rpe == null || (s.rpe.isFinite() && s.rpe in 0.0..10.0))
            }
        ) {
            "Backup contains invalid set data"
        }
        val sessionIds = value.sessions.map { it.id }.toSet()
        require(value.sessionExercises.all { it.sessionId in sessionIds }) {
            "A workout exercise references a missing session"
        }
        val habits = value.habits.map { it.id }.toSet()
        require(value.habitChecks.all { it.habitId in habits }) { "A habit check references a missing habit" }
        require(
            (value.cardio.map { it.date } + value.dailyLogs.map { it.date } + value.measurements.map { it.date } + value.physiqueScans.map { it.date } +
                value.habitChecks.map { it.date } + value.photos.map { it.date })
                .all { runCatching { LocalDate.parse(it) }.isSuccess }
        ) {
            "Backup contains invalid wellness dates"
        }
        require(
            value.cardio.all { it.durationMin.isFinite() && it.durationMin in 0.0..1440.0 && (it.distanceKm == null || (it.distanceKm.isFinite() && it.distanceKm in 0.0..1000.0)) } &&
                value.measurements.all { m -> listOfNotNull(m.waistCm, m.chestCm, m.shouldersCm, m.armCm, m.thighCm, m.hipsCm, m.neckCm, m.bodyFatPct).all { it.isFinite() && it in 0.0..300.0 } } &&
                value.physiqueScans.all { p -> listOf(p.shoulder, p.waist, p.hip, p.leftThigh, p.rightThigh, p.height).all { it.isFinite() && it >= 0 } }
        ) {
            "Backup contains invalid body or cardio data"
        }
        require(
            value.goals.all { g ->
                g.kcalTarget in 0..20_000 && g.stepGoal in 1..200_000 && g.waterGoalMl in 1..20_000 &&
                    g.sleepGoalHours.isFinite() && g.sleepGoalHours in 1.0..24.0 &&
                    listOf(g.proteinG, g.carbsG, g.fatG).all { it.isFinite() && it >= 0 }
            } && value.profile.all { p -> p.weightKg.isFinite() && p.weightKg > 0 && p.heightCm.isFinite() && p.heightCm > 0 && p.daysPerWeek in 1..7 }
        ) {
            "Backup contains invalid goals or profile"
        }
        // Photo names become file paths; anything but a plain file name is refused.
        require(value.photos.all { app.ironlog.personal.data.safeFileName(it.fileName) != null }) {
            "Backup contains an unsafe photo file name"
        }
        val foods = value.foods.map { it.id }.toSet()
        require(value.meals.all { it.foodId == null || it.foodId in foods }) {
            "A meal references a missing food"
        }
    }
}
