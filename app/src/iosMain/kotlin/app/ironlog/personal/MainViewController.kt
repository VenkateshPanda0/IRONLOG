package app.ironlog.personal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.platform.IosAssets
import app.ironlog.personal.platform.ioDispatcher
import app.ironlog.personal.reminders.IosReminders
import app.ironlog.personal.timer.IosRestAlarm
import app.ironlog.personal.ui.nav.IronlogRoot
import app.ironlog.personal.ui.platform.IosPlatformUi
import app.ironlog.personal.ui.platform.LocalPlatform
import app.ironlog.personal.ui.theme.IronlogTheme
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIViewController

/** The app's data lives in Application Support, which iCloud and computer backups include. */
@OptIn(ExperimentalForeignApi::class)
private fun dataDir(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(NSApplicationSupportDirectory, NSUserDomainMask, null, true, null)
    return requireNotNull(url?.path) { "No Application Support folder" }
}

private val iosContainer: AppContainer by lazy {
    val dir = dataDir()
    lateinit var container: AppContainer
    val reminders = IosReminders { container }
    container =
        AppContainer(
            db = Room.databaseBuilder<IronlogDatabase>(name = "$dir/ironlog.db").setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(ioDispatcher).build(),
            preferences = PreferenceDataStoreFactory.createWithPath(produceFile = { "$dir/ironlog_settings.preferences_pb".toPath() }),
            filesDir = dir.toPath(),
            assets = IosAssets(),
            reminders = reminders,
            restAlarm = IosRestAlarm(),
        )
    container.appScope.launch {
        container.runSeeds()
        reminders.refresh()
    }
    // A finished workout can cancel today's reminder: plan the week again when history changes.
    container.appScope.launch { container.workouts.history.drop(1).collect { reminders.refresh() } }
    container
}

/** Entry point called from Swift: the whole Ironlog UI in one view controller. */
fun MainViewController(): UIViewController =
    ComposeUIViewController {
        val container = iosContainer
        val platform = remember { IosPlatformUi(IosAssets()) }
        val theme by container.theme.collectAsState(initial = "DARK")
        CompositionLocalProvider(LocalPlatform provides platform) {
            IronlogTheme(theme != "LIGHT") {
                IronlogRoot(
                    container,
                    onTheme = { light -> container.appScope.launch { container.setTheme(if (light) "LIGHT" else "DARK") } },
                    onExport = {},
                    onImport = {},
                )
            }
        }
    }
