package app.ironlog.personal

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IronlogApp:Application() {
    lateinit var container:AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container=AppContainer(this)
        CoroutineScope(Dispatchers.IO).launch { container.seed.load() }
    }
}
