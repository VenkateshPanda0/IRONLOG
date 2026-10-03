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
                val exerciseCount = if (currentVersion < SEED_VERSION) container.seed.load() else 0
                val foodCounts =
                    if (currentVersion < SEED_VERSION) container.foodSeed.load() else null
                if (currentVersion < SEED_VERSION) container.setSeedVersion(SEED_VERSION)
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
        const val SEED_VERSION = 2
    }
}
