package app.ironlog.personal

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class IronlogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                val currentVersion = container.seedVersion()
                val shouldLoadExercises = currentVersion < EXERCISE_SEED_VERSION
                val exerciseCount = if (shouldLoadExercises) container.seed.load() else 0
                val shouldLoadFoods = currentVersion < FOOD_SEED_VERSION
                val foodCounts = if (shouldLoadFoods) container.foodSeed.load() else null
                val shouldLoadPrograms = currentVersion < PROGRAM_SEED_VERSION
                if (shouldLoadExercises || shouldLoadPrograms) container.programSeed.load()
                val latestSeedVersion =
                    maxOf(EXERCISE_SEED_VERSION, FOOD_SEED_VERSION, PROGRAM_SEED_VERSION)
                if (currentVersion < latestSeedVersion) container.setSeedVersion(latestSeedVersion)
                container.updateSeedState(
                    SeedState.Ready(
                        insertedExercises = exerciseCount,
                        insertedFoods = foodCounts?.insertedFoods ?: 0,
                        insertedServings = foodCounts?.insertedServings ?: 0,
                    )
                )
            }
                .onFailure { error ->
                    container.updateSeedState(
                        SeedState.Failed(error.message ?: "Seed setup failed")
                    )
                }
        }
    }

    private companion object {
        const val EXERCISE_SEED_VERSION = 2
        const val FOOD_SEED_VERSION = 2
        const val PROGRAM_SEED_VERSION = 3
    }
}
