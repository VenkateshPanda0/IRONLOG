package app.ironlog.personal

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.repo.*
import app.ironlog.personal.data.seed.SeedLoader
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name="ironlog_settings")
class AppContainer(context:Context) {
    private val appContext=context.applicationContext
    val db=Room.databaseBuilder(context,IronlogDatabase::class.java,"ironlog.db").build()
    val workouts=WorkoutRepository(db)
    val nutrition=NutritionRepository(db.dao())
    val body=BodyRepository(db.dao())
    val programs=ProgramRepository(db)
    val seed=SeedLoader(db)
    val profile=db.dao().profile()
    suspend fun saveProfile(value:app.ironlog.personal.data.db.UserProfileEntity)=db.dao().saveProfile(value)
    suspend fun clearPersonalData() { db.withTransaction { db.dao().clearSessions(); db.dao().clearMeals(); db.dao().clearWeights(); db.dao().clearProfile() } }
    private val themeKey=stringPreferencesKey("theme")
    val theme=context.preferences.data.map { it[themeKey] ?: "DARK" }
    suspend fun setTheme(value:String) { appContext.preferences.edit { it[themeKey]=value } }
}
