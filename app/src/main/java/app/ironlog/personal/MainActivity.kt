package app.ironlog.personal

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
    override fun onResume() {
        super.onResume()
        val container = (application as IronlogApp).container
        lifecycleScope.launch { container.syncHealthIfDue() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as IronlogApp).container
        val export =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
                uri ->
                if (uri != null)
                    lifecycleScope.launch {
                        runCatching {
                            val text = withContext(Dispatchers.IO) { container.backup.exportJson() }
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                                it.write(text)
                            } ?: throw IOException("Could not open destination")
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
                            val text =
                                withContext(Dispatchers.IO) {
                                    contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                                        it.readText()
                                    } ?: throw IOException("Could not open backup")
                                }
                            container.backup.importJson(text)
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
            IronlogTheme(theme != "LIGHT") {
                IronlogRoot(
                    container,
                    onTheme = { value ->
                        lifecycleScope.launch { container.setTheme(if (value) "LIGHT" else "DARK") }
                    },
                    onExport = { export.launch("ironlog_backup.json") },
                    onImport = { import.launch(arrayOf("application/json", "text/*")) },
                )
            }
        }
    }
}
