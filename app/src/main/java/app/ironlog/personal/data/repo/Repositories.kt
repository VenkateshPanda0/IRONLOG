package app.ironlog.personal.data.repo

import app.ironlog.personal.data.db.*
import androidx.room.withTransaction
import app.ironlog.personal.domain.Calculations
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class WorkoutRepository(private val db:IronlogDatabase) {
    private val dao=db.dao()
    val active=dao.activeSession(); val history=dao.history()
    val exercises=dao.exercises()
    fun sessionExercises(sessionId:Long)=dao.sessionExercises(sessionId)
    suspend fun session(sessionId:Long)=dao.session(sessionId)
    suspend fun sets(sessionExerciseId:Long)=dao.setsOnce(sessionExerciseId)
    suspend fun addCustomExercise(name:String,id:String) = dao.putExercise(ExerciseEntity(id=id,name=name,isCustom=true))
    suspend fun start(name:String,rows:List<Triple<String,String,Int>>,programId:Long?=null,day:String?=null)=db.withTransaction { dao.startWorkout(name,programId,day,rows) }
    suspend fun completeSet(id:Long,weightKg:Double?,reps:Int?) { dao.set(id)?.let { dao.updateSet(it.copy(weightKg=weightKg,reps=reps,isCompleted=true,completedAt=System.currentTimeMillis())) } }
    suspend fun saveSetDraft(id:Long,weightKg:Double?,reps:Int?) { dao.set(id)?.let { dao.updateSet(it.copy(weightKg=weightKg,reps=reps)) } }
    suspend fun setCompleted(id:Long,completed:Boolean) { dao.set(id)?.let { dao.updateSet(it.copy(isCompleted=completed,completedAt=if(completed) System.currentTimeMillis() else null)) } }
    suspend fun finish(id:Long)=dao.finishSession(id,"COMPLETED",System.currentTimeMillis(),System.currentTimeMillis())
    suspend fun pause(id:Long)=dao.pauseSession(id,System.currentTimeMillis())
    suspend fun resume(id:Long)=dao.resumeSession(id,System.currentTimeMillis())
    suspend fun skipExercise(id:Long)=dao.setExerciseStatus(id,"SKIPPED")
}

class NutritionRepository(private val dao:IronlogDao) {
    fun foods(q:String)=dao.searchFoods(q)
    fun meals(date:LocalDate)=dao.meals(date.toString())
    fun searchFoods(query:String)=dao.searchFoods(query)
    suspend fun addCustom(name:String,kcal:Double,protein:Double,carbs:Double,fat:Double)=dao.addFood(FoodEntity(name=name,kcalPer100g=kcal,proteinPer100g=protein,carbsPer100g=carbs,fatPer100g=fat))
    suspend fun log(food:FoodEntity,date:LocalDate,meal:String,grams:Double)=dao.addMeal(MealEntryEntity(date=date.toString(),mealType=meal,foodId=food.id,foodNameSnapshot=food.name,grams=grams,kcal=Calculations.foodMacro(food.kcalPer100g,grams),protein=Calculations.foodMacro(food.proteinPer100g,grams),carbs=Calculations.foodMacro(food.carbsPer100g,grams),fat=Calculations.foodMacro(food.fatPer100g,grams),fiber=food.fiberPer100g?.let{Calculations.foodMacro(it,grams)}))
    suspend fun copyDay(from:LocalDate,to:LocalDate)=dao.mealsOnce(from.toString()).forEach { dao.addMeal(it.copy(id=0,date=to.toString(),createdAt=System.currentTimeMillis())) }
}

class BodyRepository(private val dao:IronlogDao) {
    val weights:Flow<List<BodyWeightEntity>> = dao.weights()
    suspend fun log(date:LocalDate,kg:Double,note:String?=null)=dao.addWeight(BodyWeightEntity(date=date.toString(),weightKg=kg,note=note))
}

class ProgramRepository(private val db:IronlogDatabase) {
    private val dao=db.dao(); val programs=dao.programs(); val active=dao.activeProgram()
    suspend fun activate(id:Long)=dao.activate(ActiveProgramEntity(programId=id,startDate=LocalDate.now().toString()))
    suspend fun start(id:Long,dayIndex:Int):Long {
        val program=dao.program(id) ?: error("Program missing")
        val day=dao.days(id).getOrNull(dayIndex) ?: error("Program day missing")
        val rows=dao.prescriptions(day.id).map { p -> val ex=dao.exercise(p.exerciseId); Triple(p.exerciseId,ex?.name ?: p.exerciseId,p.targetSets) }
        return db.withTransaction { dao.startWorkout("${program.name} · ${day.name}",id,day.name,rows) }
    }
}
