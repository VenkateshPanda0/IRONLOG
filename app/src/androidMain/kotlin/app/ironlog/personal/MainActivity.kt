package app.ironlog.personal

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import app.ironlog.personal.ui.nav.IronlogRoot
import app.ironlog.personal.ui.theme.IronlogTheme
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_WORKOUT = "open_workout"
    }

    private fun handle(intent: android.content.Intent?) {
        val id = intent?.getLongExtra(EXTRA_OPEN_WORKOUT, -1L) ?: -1L
        if (id > 0) (application as IronlogApp).container.openWorkoutRequest.value = id
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app is the moment to refresh what Android backs up.
        val container = (application as IronlogApp).container
        container.appScope.launch { container.backupIfChanged() }
    }

    override fun onResume() {
        super.onResume()
        val container = (application as IronlogApp).container
        lifecycleScope.launch { container.syncHealthIfDue() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as IronlogApp).container
        handle(intent)
        val export =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {
                uri ->
                if (uri != null)
                    lifecycleScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { container.archive.export(it) }
                                    ?: throw IOException("Could not open destination")
                            }
                        }
                            .onFailure {
                                Toast.makeText(
                                        this@MainActivity,
                                        "Backup export failed",
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            }
                            .onSuccess {
                                Toast.makeText(
                                        this@MainActivity,
                                        "Backup exported",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                    }
            }
        val import =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null)
                    lifecycleScope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) {
                                contentResolver.openInputStream(uri)?.use { container.archive.import(it) }
                                    ?: throw IOException("Could not open backup")
                            }
                        }
                            .onFailure {
                                Toast.makeText(
                                        this@MainActivity,
                                        "Backup import failed: ${it.message}",
                                        Toast.LENGTH_LONG,
                                    )
                                    .show()
                            }
                            .onSuccess {
                                Toast.makeText(
                                        this@MainActivity,
                                        "Backup imported",
                                        Toast.LENGTH_SHORT,
                                    )
                                    .show()
                            }
                    }
            }
        setContent {
            val theme by container.theme.collectAsState(initial = "DARK")
            val platform = androidx.compose.runtime.remember { app.ironlog.personal.ui.platform.AndroidPlatformUi(this@MainActivity, container) }
            androidx.compose.runtime.CompositionLocalProvider(app.ironlog.personal.ui.platform.LocalPlatform provides platform) {
            IronlogTheme(theme != "LIGHT") {
                IronlogRoot(
                    container,
                    onTheme = { value ->
                        lifecycleScope.launch { container.setTheme(if (value) "LIGHT" else "DARK") }
                    },
                    onExport = { export.launch("ironlog_backup_${LocalDate.now()}.zip") },
                    onImport = { import.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "text/*")) },
                )
            }
            }
        }
    }
}
