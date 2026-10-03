package app.ironlog.personal.data.db

import androidx.room.*
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName="exercise", indices=[Index("name")]) data class ExerciseEntity(@PrimaryKey val id:String, val name:String, val category:String="", val equipment:String?=null, val primaryMuscles:String="[]", val instructions:String="[]", val isCustom:Boolean=false, val isFavorite:Boolean=false, val createdAt:Long=System.currentTimeMillis())
@Serializable
@Entity(tableName="program") data class ProgramEntity(@PrimaryKey(autoGenerate=true) val id:Long=0, val name:String, val description:String="", val daysPerWeek:Int=3, val isBuiltIn:Boolean=false, val createdAt:Long=System.currentTimeMillis())
@Serializable
@Entity(tableName="program_day", foreignKeys=[ForeignKey(entity=ProgramEntity::class,parentColumns=["id"],childColumns=["programId"],onDelete=ForeignKey.CASCADE)], indices=[Index("programId")]) data class ProgramDayEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val programId:Long,val weekIndex:Int=1,val dayIndex:Int,val name:String,val isRest:Boolean=false)
@Serializable
@Entity(tableName="program_day_exercise", foreignKeys=[ForeignKey(entity=ProgramDayEntity::class,parentColumns=["id"],childColumns=["programDayId"],onDelete=ForeignKey.CASCADE)],indices=[Index("programDayId")]) data class ProgramDayExerciseEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val programDayId:Long,val exerciseId:String,val orderIndex:Int,val targetSets:Int=3,val repMin:Int=8,val repMax:Int=12,val restSeconds:Int=90,val notes:String="")
@Serializable
@Entity(tableName="active_program") data class ActiveProgramEntity(@PrimaryKey val id:Int=1,val programId:Long,val startDate:String,val currentWeek:Int=1,val currentDay:Int=1,val status:String="ACTIVE")
@Serializable
@Entity(tableName="user_profile") data class UserProfileEntity(@PrimaryKey val id:Int=1,val name:String="",val sex:String="",val age:Int=30,val heightCm:Double=170.0,val weightKg:Double=70.0,val activity:Double=1.4,val goal:String="MAINTAIN",val daysPerWeek:Int=3,val equipment:String="GYM")
@Serializable
@Entity(tableName="workout_session",indices=[Index("startedAt"),Index("status")]) data class WorkoutSessionEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val startedAt:Long=System.currentTimeMillis(),val endedAt:Long?=null,val status:String="IN_PROGRESS",val programId:Long?=null,val programDayName:String?=null,val name:String="Workout",val notes:String="",val totalPausedMs:Long=0,val pausedAt:Long?=null,val lastActiveAt:Long=System.currentTimeMillis())
@Serializable
@Entity(tableName="session_exercise",foreignKeys=[ForeignKey(entity=WorkoutSessionEntity::class,parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE)],indices=[Index("sessionId")]) data class SessionExerciseEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val sessionId:Long,val exerciseId:String,val exerciseNameSnapshot:String,val orderIndex:Int,val status:String="PENDING",val targetSets:Int=3,val repMin:Int=8,val repMax:Int=12,val restSeconds:Int=90,val notes:String="")
@Serializable
@Entity(tableName="set_log",foreignKeys=[ForeignKey(entity=SessionExerciseEntity::class,parentColumns=["id"],childColumns=["sessionExerciseId"],onDelete=ForeignKey.CASCADE)],indices=[Index(value=["sessionExerciseId","setIndex"])]) data class SetLogEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val sessionExerciseId:Long,val setIndex:Int,val type:String="WORKING",val weightKg:Double?=null,val reps:Int?=null,val rpe:Double?=null,val isCompleted:Boolean=false,val completedAt:Long?=null)
@Serializable
@Entity(tableName="rest_timer") data class RestTimerEntity(@PrimaryKey val id:Int=1,val sessionId:Long?,val endAtEpochMs:Long,val durationSec:Int,val isRunning:Boolean)
@Serializable
@Entity(tableName="food",indices=[Index("name")]) data class FoodEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val name:String,val brand:String?=null,val source:String="CUSTOM",val sourceRef:String?=null,val kcalPer100g:Double,val proteinPer100g:Double,val carbsPer100g:Double,val fatPer100g:Double,val fiberPer100g:Double?=null,val isFavorite:Boolean=false,val createdAt:Long=System.currentTimeMillis(),val confidence:String="USER")
@Serializable
@Entity(tableName="food_serving",foreignKeys=[ForeignKey(entity=FoodEntity::class,parentColumns=["id"],childColumns=["foodId"],onDelete=ForeignKey.CASCADE)],indices=[Index("foodId")]) data class FoodServingEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val foodId:Long,val label:String,val grams:Double)
@Serializable
@Entity(tableName="meal_entry",foreignKeys=[ForeignKey(entity=FoodEntity::class,parentColumns=["id"],childColumns=["foodId"],onDelete=ForeignKey.SET_NULL)],indices=[Index("date"),Index("foodId")]) data class MealEntryEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val date:String,val mealType:String,val foodId:Long?=null,val foodNameSnapshot:String,val grams:Double,val kcal:Double,val protein:Double,val carbs:Double,val fat:Double,val fiber:Double?=null,val createdAt:Long=System.currentTimeMillis())
@Serializable
@Entity(tableName="body_weight",indices=[Index("date"),Index(value=["date","createdAt"],unique=true)]) data class BodyWeightEntity(@PrimaryKey(autoGenerate=true) val id:Long=0,val date:String,val weightKg:Double,val note:String?=null,val createdAt:Long=System.currentTimeMillis(),val source:String="MANUAL")
@Serializable
@Entity(tableName="goal") data class GoalEntity(@PrimaryKey val id:Int=1,val goalWeightKg:Double?=null,val targetDate:String?=null,val kcalTarget:Int=2000,val proteinG:Double=120.0,val carbsG:Double=220.0,val fatG:Double=65.0)
