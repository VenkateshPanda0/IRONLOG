package app.ironlog.personal.data.db

import androidx.room.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "exercise", indices = [Index("name"), Index("equipment")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String = "",
    val force: String? = null,
    val level: String? = null,
    val mechanic: String? = null,
    val equipment: String? = null,
    val primaryMuscles: String = "[]",
    val secondaryMuscles: String = "[]",
    val instructions: String = "[]",
    val imagePaths: String = "[]",
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "program", indices = [Index(value = ["name", "isBuiltIn"], unique = true)])
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val daysPerWeek: Int = 3,
    val isBuiltIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "program_day",
    foreignKeys =
        [
            ForeignKey(
                entity = ProgramEntity::class,
                parentColumns = ["id"],
                childColumns = ["programId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [Index("programId"), Index(value = ["programId", "weekIndex", "dayIndex"], unique = true)],
)
data class ProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val weekIndex: Int = 1,
    val dayIndex: Int,
    val name: String,
    val isRest: Boolean = false,
)

@Serializable
@Entity(
    tableName = "program_day_exercise",
    foreignKeys =
        [
            ForeignKey(
                entity = ProgramDayEntity::class,
                parentColumns = ["id"],
                childColumns = ["programDayId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("programDayId"), Index(value = ["programDayId", "orderIndex"], unique = true)],
)
data class ProgramDayExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programDayId: Long,
    val exerciseId: String,
    val orderIndex: Int,
    val targetSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSeconds: Int = 90,
    val originalExerciseId: String? = null,
    val notes: String = "",
)

@Serializable
@Entity(tableName = "active_program")
data class ActiveProgramEntity(
    @PrimaryKey val id: Int = 1,
    val programId: Long,
    val startDate: String,
    val currentWeek: Int = 1,
    val currentDay: Int = 1,
    val status: String = "ACTIVE",
)

@Serializable
@Entity(
    tableName = "skipped_program_day",
    foreignKeys =
        [
            ForeignKey(
                entity = ProgramEntity::class,
                parentColumns = ["id"],
                childColumns = ["programId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["programId", "date"], unique = true)],
)
data class SkippedProgramDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val date: String,
    val dayNameSnapshot: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val sex: String = "",
    val age: Int = 30,
    val heightCm: Double = 170.0,
    val weightKg: Double = 70.0,
    val activity: Double = 1.4,
    val goal: String = "MAINTAIN",
    val daysPerWeek: Int = 3,
    val equipment: String = "GYM",
    val experience: String = "BEGINNER",
    val sessionMinutes: Int = 45,
    val avoidList: String = "",
    val trainingWeekdays: String = "MON,WED,FRI",
    /** [app.ironlog.personal.domain.PhysiqueType] name chosen as the goal look; empty until picked. */
    val physiqueGoal: String = "",
)

@Serializable
@Entity(tableName = "workout_session", indices = [Index("startedAt"), Index("status")])
data class WorkoutSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long = System.currentTimeMillis(),
    val endedAt: Long? = null,
    val status: String = "IN_PROGRESS",
    val programId: Long? = null,
    val programDayName: String? = null,
    val name: String = "Workout",
    val notes: String = "",
    val totalPausedMs: Long = 0,
    val pausedAt: Long? = null,
    val lastActiveAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "session_exercise",
    foreignKeys =
        [
            ForeignKey(
                entity = WorkoutSessionEntity::class,
                parentColumns = ["id"],
                childColumns = ["sessionId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("sessionId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseNameSnapshot: String,
    val orderIndex: Int,
    val status: String = "PENDING",
    val targetSets: Int = 3,
    val repMin: Int = 8,
    val repMax: Int = 12,
    val restSeconds: Int = 90,
    val sourceProgramDayExerciseId: Long? = null,
    val notes: String = "",
    /** Set when the exercise was swapped mid-workout so it can be reverted. */
    val originalExerciseId: String? = null,
    val originalNameSnapshot: String? = null,
)

@Serializable
@Entity(
    tableName = "set_log",
    foreignKeys =
        [
            ForeignKey(
                entity = SessionExerciseEntity::class,
                parentColumns = ["id"],
                childColumns = ["sessionExerciseId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["sessionExerciseId", "setIndex"])],
)
data class SetLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionExerciseId: Long,
    val setIndex: Int,
    val type: String = "WORKING",
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Double? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
)

@Serializable
@Entity(tableName = "rest_timer")
data class RestTimerEntity(
    @PrimaryKey val id: Int = 1,
    val sessionId: Long?,
    val endAtEpochMs: Long,
    val durationSec: Int,
    val isRunning: Boolean,
)

@Serializable
@Entity(tableName = "food", indices = [Index("name")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val source: String = "CUSTOM",
    val sourceRef: String? = null,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val fiberPer100g: Double? = null,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val confidence: String = "USER",
    /** "Indian", "Indian (packaged)", "Italian", "Ingredient"... null for user foods. */
    val cuisine: String? = null,
    /** Source popularity (e.g. Open Food Facts scans); higher ranks first among equals. */
    val popularity: Int = 0,
)

@Serializable
@Entity(
    tableName = "food_serving",
    foreignKeys =
        [
            ForeignKey(
                entity = FoodEntity::class,
                parentColumns = ["id"],
                childColumns = ["foodId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("foodId"), Index(value = ["foodId", "label", "grams"], unique = true)],
)
data class FoodServingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val label: String,
    val grams: Double,
)

@Serializable
@Entity(
    tableName = "meal_entry",
    foreignKeys =
        [
            ForeignKey(
                entity = FoodEntity::class,
                parentColumns = ["id"],
                childColumns = ["foodId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("date"), Index("foodId")],
)
data class MealEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val mealType: String,
    val foodId: Long? = null,
    val foodNameSnapshot: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val fiber: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "body_weight",
    indices = [Index("date"), Index(value = ["date", "createdAt"], unique = true)],
)
data class BodyWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weightKg: Double,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",
    /** Health Connect record ID when this weigh-in was imported from another app. */
    val healthId: String? = null,
)

@Serializable
@Entity(tableName = "goal")
data class GoalEntity(
    @PrimaryKey val id: Int = 1,
    val goalWeightKg: Double? = null,
    val targetDate: String? = null,
    val kcalTarget: Int = 2000,
    val proteinG: Double = 120.0,
    val carbsG: Double = 220.0,
    val fatG: Double = 65.0,
    val stepGoal: Int = 8000,
    val waterGoalMl: Int = 3000,
    val sleepGoalHours: Double = 8.0,
)

/** Progress photo stored as a file in app-private storage; the row keeps only its name. */
@Serializable
@Entity(tableName = "progress_photo", indices = [Index("date")])
data class ProgressPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val fileName: String,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** A completed set joined with its session, used for exercise history and records. */
data class LoggedSet(
    val sessionId: Long,
    val startedAt: Long,
    val sessionName: String,
    val exerciseId: String,
    val exerciseName: String,
    val setIndex: Int,
    val type: String,
    val weightKg: Double?,
    val reps: Int?,
)

/** Per-day nutrition sums for charts and the weekly balance. */
data class DailyTotal(val date: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double)

data class FoodRef(val id: Long, val sourceRef: String)

data class FoodDay(val date: String, val kcal: Double, val mealTypes: String)

/** Run, ride, swim, HIIT or any other conditioning session. */
@Serializable
@Entity(tableName = "cardio_session", indices = [Index("date")])
data class CardioSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val durationMin: Double,
    val distanceKm: Double? = null,
    val calories: Int? = null,
    val avgHeartRate: Int? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Health Connect record ID when this session was imported from another app or a watch. */
    val healthId: String? = null,
)

/** One row per day: steps, water, sleep and the morning readiness check-in. */
@Serializable
@Entity(tableName = "daily_log")
data class DailyLogEntity(
    @PrimaryKey val date: String,
    val steps: Int? = null,
    val waterMl: Int = 0,
    val sleepHours: Double? = null,
    val sleepQuality: Int? = null,
    val energy: Int? = null,
    val soreness: Int? = null,
    val stress: Int? = null,
    val mood: Int? = null,
    /** True when [steps] / [sleepHours] came from Health Connect rather than manual entry. */
    val stepsFromHealth: Boolean = false,
    val sleepFromHealth: Boolean = false,
)

@Serializable
@Entity(tableName = "habit")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "habit_check",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [ForeignKey(entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class HabitCheckEntity(val habitId: Long, val date: String)

@Serializable
@Entity(tableName = "body_measurement", indices = [Index("date")])
data class BodyMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val waistCm: Double? = null,
    val chestCm: Double? = null,
    /** Around the widest point of the shoulders, arms relaxed: the top of the V-taper. */
    val shouldersCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val hipsCm: Double? = null,
    val neckCm: Double? = null,
    val bodyFatPct: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * One physique check: circumferences in cm (shoulder, waist, hip, thigh) and the match to the goal
 * at the time. Rows with a [fileName] predate tape checks and hold widths estimated from a photo.
 */
@Serializable
@Entity(tableName = "physique_scan", indices = [Index("date")])
data class PhysiqueScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val fileName: String,
    val goal: String,
    val shoulder: Double,
    val waist: Double,
    val hip: Double,
    val leftThigh: Double,
    val rightThigh: Double,
    val height: Double,
    val legToTorso: Double,
    val matchScore: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
