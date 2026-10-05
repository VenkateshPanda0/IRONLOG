package app.ironlog.personal

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class IronlogApp : Application() {
    lateinit var container: AndroidAppContainer
        private set
    /** Start-up work (bundled data, reminder alarms); tests wait for it before they begin. */
    lateinit var startup: kotlinx.coroutines.Job
        private set

    override fun onCreate() {
        super.onCreate()
        container = AndroidAppContainer(this)
        container.workoutNotifier.start(container.appScope)
        startup = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            container.runSeeds()
            // Re-book reminder alarms in case they were lost (force stop, restore, clock change).
            runCatching { container.reminders.apply(container.reminderSettings.first()) }
        }
    }
}
