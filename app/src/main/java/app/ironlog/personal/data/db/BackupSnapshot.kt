package app.ironlog.personal.data.db

import kotlinx.serialization.Serializable

@Serializable data class BackupSnapshot(
    val schemaVersion:Int=1,
    val exportedAt:Long=System.currentTimeMillis(),
    val exercises:List<ExerciseEntity> = emptyList(),
    val programs:List<ProgramEntity> = emptyList(),
    val programDays:List<ProgramDayEntity> = emptyList(),
    val prescriptions:List<ProgramDayExerciseEntity> = emptyList(),
    val activePrograms:List<ActiveProgramEntity> = emptyList(),
    val profile:List<UserProfileEntity> = emptyList(),
    val sessions:List<WorkoutSessionEntity> = emptyList(),
    val sessionExercises:List<SessionExerciseEntity> = emptyList(),
    val sets:List<SetLogEntity> = emptyList(),
    val foods:List<FoodEntity> = emptyList(),
    val servings:List<FoodServingEntity> = emptyList(),
    val meals:List<MealEntryEntity> = emptyList(),
    val weights:List<BodyWeightEntity> = emptyList(),
    val goals:List<GoalEntity> = emptyList()
)
