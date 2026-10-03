package app.ironlog.personal

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.repo.*
import app.ironlog.personal.data.seed.SeedLoader
import app.ironlog.personal.data.provider.OpenFoodFactsProvider
import app.ironlog.personal.data.provider.UsdaProvider
import app.ironlog.personal.timer.RestTimerController
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name="ironlog_settings")
class AppContainer(context:Context) {
    private val appContext=context.applicationContext
    val db=Room.databaseBuilder(context,IronlogDatabase::class.java,"ironlog.db").build()
    val workouts=WorkoutRepository(db)
    val nutrition=NutritionRepository(db.dao())
    val body=BodyRepository(db.dao())
    val backup=BackupRepository(db)
    val programs=ProgramRepository(db)
    val seed=SeedLoader(context,db)
    val openFoodFacts=OpenFoodFactsProvider(db.dao())
    val usda=UsdaProvider(null)
    val restTimer=RestTimerController(context,db.dao())
    val profile=db.dao().profile()
    suspend fun saveProfile(value:app.ironlog.personal.data.db.UserProfileEntity)=db.dao().saveProfile(value)
    suspend fun clearPersonalData() { db.withTransaction { val dao=db.dao(); dao.deleteSets(); dao.deleteSessionExercises(); dao.deleteAllSessions(); dao.deleteAllMeals(); dao.deleteAllServings(); dao.deleteAllFoods(); dao.deleteAllWeights(); dao.deleteAllActivePrograms(); dao.deleteAllPrescriptions(); dao.deleteAllProgramDays(); dao.deleteAllPrograms(); dao.deleteAllExercises(); dao.deleteAllProfiles(); dao.deleteAllGoals(); dao.clearRestTimer() } }
    private val themeKey=stringPreferencesKey("theme")
    val theme=context.preferences.data.map { it[themeKey] ?: "DARK" }
    suspend fun setTheme(value:String) { appContext.preferences.edit { it[themeKey]=value } }
}
