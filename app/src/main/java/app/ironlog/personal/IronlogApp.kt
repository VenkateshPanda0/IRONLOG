package app.ironlog.personal

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IronlogApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.workoutNotifier.start(container.appScope)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.runSeeds()
            // Re-book reminder alarms in case they were lost (force stop, restore, clock change).
            runCatching { container.reminders.apply(container.reminderSettings.first()) }
        }
    }
}
