package app.ironlog.personal.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao interface IronlogDao {
    @Query("SELECT * FROM user_profile WHERE id=1") fun profile(): Flow<UserProfileEntity?>
    @Query("SELECT * FROM user_profile WHERE id=1") suspend fun profileOnce(): UserProfileEntity?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveProfile(value:UserProfileEntity)
    @Query("SELECT * FROM workout_session WHERE status IN ('IN_PROGRESS','PAUSED') ORDER BY startedAt DESC LIMIT 1") fun activeSession(): Flow<WorkoutSessionEntity?>
    @Query("SELECT * FROM workout_session WHERE status='COMPLETED' ORDER BY startedAt DESC") fun history(): Flow<List<WorkoutSessionEntity>>
    @Query("SELECT * FROM workout_session WHERE id=:id") suspend fun session(id:Long):WorkoutSessionEntity?
    @Insert suspend fun startSession(session:WorkoutSessionEntity):Long
    @Insert suspend fun addSessionExercise(value:SessionExerciseEntity):Long
    @Insert suspend fun addSet(value:SetLogEntity):Long
    @Query("SELECT * FROM session_exercise WHERE sessionId=:id ORDER BY orderIndex") fun sessionExercises(id:Long):Flow<List<SessionExerciseEntity>>
    @Query("SELECT * FROM session_exercise WHERE sessionId=:id ORDER BY orderIndex") suspend fun sessionExercisesOnce(id:Long):List<SessionExerciseEntity>
    @Query("SELECT * FROM set_log WHERE sessionExerciseId=:id ORDER BY setIndex") fun sets(id:Long):Flow<List<SetLogEntity>>
    @Query("SELECT * FROM set_log WHERE sessionExerciseId=:id ORDER BY setIndex") suspend fun setsOnce(id:Long):List<SetLogEntity>
    @Update suspend fun updateSet(value:SetLogEntity)
    @Query("SELECT * FROM set_log WHERE id=:id") suspend fun set(id:Long):SetLogEntity?
    @Query("UPDATE workout_session SET status=:status,endedAt=:ended,lastActiveAt=:now WHERE id=:id") suspend fun finishSession(id:Long,status:String,ended:Long?,now:Long)
    @Query("UPDATE workout_session SET status='PAUSED',pausedAt=:now,lastActiveAt=:now WHERE id=:id AND status='IN_PROGRESS'") suspend fun pauseSession(id:Long,now:Long)
    @Query("UPDATE workout_session SET status='IN_PROGRESS',totalPausedMs=totalPausedMs+CASE WHEN pausedAt IS NULL THEN 0 ELSE :now-pausedAt END,pausedAt=NULL,lastActiveAt=:now WHERE id=:id AND status='PAUSED'") suspend fun resumeSession(id:Long,now:Long)
    @Query("UPDATE session_exercise SET status=:status WHERE id=:id") suspend fun setExerciseStatus(id:Long,status:String)
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun saveRestTimer(value:RestTimerEntity)
    @Query("SELECT * FROM rest_timer WHERE id=1") suspend fun restTimer():RestTimerEntity?
    @Query("DELETE FROM rest_timer WHERE id=1") suspend fun clearRestTimer()
    @Query("SELECT * FROM food WHERE name LIKE '%' || :q || '%' ORDER BY name LIMIT 100") fun searchFoods(q:String):Flow<List<FoodEntity>>
    @Insert suspend fun addFood(value:FoodEntity):Long
    @Query("SELECT * FROM food WHERE sourceRef=:ref LIMIT 1") suspend fun foodBySourceRef(ref:String):FoodEntity?
    @Insert suspend fun addMeal(value:MealEntryEntity):Long
    @Query("SELECT * FROM meal_entry WHERE date=:date ORDER BY createdAt") fun meals(date:String):Flow<List<MealEntryEntity>>
    @Query("SELECT * FROM meal_entry WHERE date=:date ORDER BY createdAt") suspend fun mealsOnce(date:String):List<MealEntryEntity>
    @Query("SELECT * FROM body_weight ORDER BY date") fun weights():Flow<List<BodyWeightEntity>>
    @Insert suspend fun addWeight(value:BodyWeightEntity):Long
    @Query("SELECT * FROM program ORDER BY isBuiltIn DESC,name") fun programs():Flow<List<ProgramEntity>>
    @Query("SELECT * FROM program WHERE id=:id") suspend fun program(id:Long):ProgramEntity?
    @Insert suspend fun addProgram(value:ProgramEntity):Long
    @Insert suspend fun addProgramDay(value:ProgramDayEntity):Long
    @Insert suspend fun addPrescription(value:ProgramDayExerciseEntity):Long
    @Query("SELECT * FROM program_day WHERE programId=:id ORDER BY weekIndex,dayIndex") suspend fun days(id:Long):List<ProgramDayEntity>
    @Query("SELECT * FROM program_day_exercise WHERE programDayId=:id ORDER BY orderIndex") suspend fun prescriptions(id:Long):List<ProgramDayExerciseEntity>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun activate(value:ActiveProgramEntity)
    @Query("SELECT * FROM active_program WHERE id=1") fun activeProgram():Flow<ActiveProgramEntity?>
    @Query("SELECT * FROM exercise WHERE id=:id") suspend fun exercise(id:String):ExerciseEntity?
    @Query("SELECT * FROM exercise ORDER BY name") fun exercises():Flow<List<ExerciseEntity>>
    @Query("DELETE FROM workout_session") suspend fun clearSessions()
    @Query("DELETE FROM meal_entry") suspend fun clearMeals()
    @Query("DELETE FROM body_weight") suspend fun clearWeights()
    @Query("DELETE FROM user_profile") suspend fun clearProfile()
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun putExercise(value:ExerciseEntity):Long
    @Transaction suspend fun startWorkout(name:String,programId:Long?,day:String?,entries:List<Triple<String,String,Int>>):Long {
        val sessionId=startSession(WorkoutSessionEntity(name=name,programId=programId,programDayName=day))
        entries.forEachIndexed { order,(exerciseId,exerciseName,sets) ->
            val exerciseRow=addSessionExercise(SessionExerciseEntity(sessionId=sessionId,exerciseId=exerciseId,exerciseNameSnapshot=exerciseName,orderIndex=order,targetSets=sets))
            repeat(sets) { index -> addSet(SetLogEntity(sessionExerciseId=exerciseRow,setIndex=index+1)) }
        }
        return sessionId
    }
}

@Database(entities=[ExerciseEntity::class,ProgramEntity::class,ProgramDayEntity::class,ProgramDayExerciseEntity::class,ActiveProgramEntity::class,UserProfileEntity::class,WorkoutSessionEntity::class,SessionExerciseEntity::class,SetLogEntity::class,RestTimerEntity::class,FoodEntity::class,FoodServingEntity::class,MealEntryEntity::class,BodyWeightEntity::class,GoalEntity::class],version=1,exportSchema=true)
abstract class IronlogDatabase:RoomDatabase(){ abstract fun dao():IronlogDao }
