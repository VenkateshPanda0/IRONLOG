package app.ironlog.personal.ui.health

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.health.HealthConnect
import app.ironlog.personal.data.health.HealthStatus
import app.ironlog.personal.health.HealthPermissionsActivity
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.theme.IronTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val WHEN = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/** Settings section: optional read-only sync of steps and sleep from Health Connect. */
@Composable
fun HealthConnectSection(c: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val enabled by c.healthSyncEnabled.collectAsState(initial = false)
    val syncedAt by c.healthSyncedAt.collectAsState(initial = null)
    val status = remember { c.healthConnect.status() }
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled, status) { if (status == HealthStatus.AVAILABLE) granted = c.healthConnect.hasPermissions() }

    fun syncNow() {
        busy = true
        scope.launch {
            message =
                runCatching { c.syncHealth() }
                    .fold(
                        { r -> if (r.daysRead == 0) "No steps or sleep found in Health Connect yet." else "Synced ${r.daysRead} days · ${r.daysUpdated} updated." },
                        { "Sync failed: ${it.message ?: "Health Connect did not respond"}" },
                    )
            busy = false
        }
    }

    val request =
        rememberLauncherForActivityResult(HealthConnect.permissionContract()) { result ->
            val ok = result.containsAll(c.healthConnect.permissions)
            granted = ok
            scope.launch {
                c.setHealthSync(ok)
                if (ok) syncNow() else message = "Ironlog needs both steps and sleep access to sync."
            }
        }

    SectionHeader("Health Connect")
    when (status) {
        HealthStatus.UNSUPPORTED -> Text("Health Connect isn't available on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HealthStatus.NEEDS_INSTALL -> {
            Text("Install or update Health Connect to sync steps and sleep from your phone, watch or other apps.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SecondaryButton("Get Health Connect", onClick = { runCatching { context.startActivity(c.healthConnect.installIntent()) } })
        }
        HealthStatus.AVAILABLE -> {
            IronCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SYNC STEPS AND SLEEP", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Read-only. Fills your daily steps and sleep; values you entered yourself are kept.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled && granted != false,
                        onCheckedChange = { on ->
                            if (on) request.launch(c.healthConnect.permissions)
                            else scope.launch {
                                c.setHealthSync(false)
                                message = "Sync off. Imported days stay in your log."
                            }
                        },
                    )
                }
                if (enabled) {
                    Text(
                        syncedAt?.let { "Last synced ${WHEN.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}" } ?: "Not synced yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = IronTheme.colors.success,
                    )
                }
            }
            if (enabled && granted == false) {
                Text("Ironlog's access was removed in Health Connect.", color = MaterialTheme.colorScheme.error)
                SecondaryButton("Grant access again", onClick = { request.launch(c.healthConnect.permissions) })
            }
            if (enabled && granted == true) {
                SecondaryButton(if (busy) "Syncing…" else "Sync now", enabled = !busy, icon = Icons.Filled.Sync, onClick = { syncNow() })
                TextButton(onClick = { runCatching { context.startActivity(c.healthConnect.manageIntent()) } }, contentPadding = PaddingValues(0.dp)) {
                    Text("MANAGE ACCESS IN HEALTH CONNECT")
                }
            }
        }
    }
    TextButton(onClick = { context.startActivity(Intent(context, HealthPermissionsActivity::class.java)) }, contentPadding = PaddingValues(0.dp)) {
        Text("HOW IRONLOG USES HEALTH DATA")
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}
