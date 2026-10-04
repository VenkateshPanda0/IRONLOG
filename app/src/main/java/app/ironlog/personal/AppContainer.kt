package app.ironlog.personal

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.withTransaction
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.provider.OpenFoodFactsProvider
import app.ironlog.personal.data.provider.UsdaProvider
import app.ironlog.personal.data.repo.*
import app.ironlog.personal.data.seed.FoodSeedLoader
import app.ironlog.personal.data.seed.ProgramSeedLoader
import app.ironlog.personal.data.seed.SeedLoader
import app.ironlog.personal.timer.RestTimerController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.preferences by preferencesDataStore(name = "ironlog_settings")

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val db = Room.databaseBuilder(context, IronlogDatabase::class.java, "ironlog.db").build()
    val workouts = WorkoutRepository(db)
    val nutrition = NutritionRepository(db.dao())
    val body = BodyRepository(db.dao())
    val goals = GoalRepository(db.dao())
    val backup = BackupRepository(db)
    val programs = ProgramRepository(db)
    val seed = SeedLoader(context, db)
    val foodSeed = FoodSeedLoader(context, db)
    val programSeed = ProgramSeedLoader(context, db)
    val openFoodFacts = OpenFoodFactsProvider(db.dao())
    val usda = UsdaProvider(null)
    val restTimer = RestTimerController(context, db.dao())
    private val mutableSeedState = MutableStateFlow<SeedState>(SeedState.Loading)
    val seedState: StateFlow<SeedState> = mutableSeedState

    fun updateSeedState(state: SeedState): Unit {
        mutableSeedState.value = state
    }

    val profile = db.dao().profile()
    suspend fun saveProfile(value: app.ironlog.personal.data.db.UserProfileEntity) =
        db.dao().saveProfile(value)

    suspend fun clearPersonalData() {
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
            dao.deleteAllPrescriptions()
            dao.deleteAllProgramDays()
            dao.deleteAllPrograms()
            dao.deleteAllExercises()
            dao.deleteAllProfiles()
            dao.deleteAllGoals()
            dao.clearRestTimer()
        }
    }

    private val themeKey = stringPreferencesKey("theme")
    private val seedVersionKey = intPreferencesKey("seed_version")
    val theme = context.preferences.data.map { it[themeKey] ?: "DARK" }

    suspend fun setTheme(value: String) {
        appContext.preferences.edit { it[themeKey] = value }
    }

    suspend fun seedVersion(): Int =
        appContext.preferences.data.map { it[seedVersionKey] ?: 0 }.first()

    suspend fun setSeedVersion(value: Int): Unit {
        appContext.preferences.edit { it[seedVersionKey] = value }
    }
}

sealed interface SeedState {
    data object Loading : SeedState

    data class Ready(
        val insertedExercises: Int,
        val insertedFoods: Int,
        val insertedServings: Int,
    ) : SeedState

    data class Failed(val reason: String) : SeedState
}
