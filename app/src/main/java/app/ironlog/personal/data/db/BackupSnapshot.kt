package app.ironlog.personal.data.db

import kotlinx.serialization.Serializable

@Serializable
data class BackupSnapshot(
    val schemaVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val programs: List<ProgramEntity> = emptyList(),
    val programDays: List<ProgramDayEntity> = emptyList(),
    val prescriptions: List<ProgramDayExerciseEntity> = emptyList(),
    val activePrograms: List<ActiveProgramEntity> = emptyList(),
    val skippedProgramDays: List<SkippedProgramDayEntity> = emptyList(),
    val profile: List<UserProfileEntity> = emptyList(),
    val sessions: List<WorkoutSessionEntity> = emptyList(),
    val sessionExercises: List<SessionExerciseEntity> = emptyList(),
    val sets: List<SetLogEntity> = emptyList(),
    val foods: List<FoodEntity> = emptyList(),
    val servings: List<FoodServingEntity> = emptyList(),
    val meals: List<MealEntryEntity> = emptyList(),
    val weights: List<BodyWeightEntity> = emptyList(),
    val goals: List<GoalEntity> = emptyList(),
    // Added with wellness and physique tracking; older backups simply leave them empty.
    val cardio: List<CardioSessionEntity> = emptyList(),
    val dailyLogs: List<DailyLogEntity> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val habitChecks: List<HabitCheckEntity> = emptyList(),
    val measurements: List<BodyMeasurementEntity> = emptyList(),
    val physiqueScans: List<PhysiqueScanEntity> = emptyList(),
)
