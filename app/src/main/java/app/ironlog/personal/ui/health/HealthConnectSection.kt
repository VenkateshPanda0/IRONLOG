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
import app.ironlog.personal.data.health.HealthAccess
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
    var access by remember { mutableStateOf<Set<HealthAccess>?>(null) }
    val granted = access?.isNotEmpty()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled, status) { if (status == HealthStatus.AVAILABLE) access = c.healthConnect.granted() }

    fun syncNow() {
        busy = true
        scope.launch {
            message =
                runCatching { c.syncHealth() }
                    .fold(
                        { r -> r.summary() },
                        { "Sync failed: ${it.message ?: "Health Connect did not respond"}" },
                    )
            busy = false
        }
    }

    val request =
        rememberLauncherForActivityResult(HealthConnect.permissionContract()) { result ->
            val now = c.healthConnect.accessFor(result)
            access = now
            scope.launch {
                c.setHealthSync(now.isNotEmpty())
                if (now.isNotEmpty()) syncNow() else message = "No access was granted, so nothing can sync."
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
                        Text("SYNC WITH HEALTH CONNECT", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Steps and sleep in; weigh-ins and workouts both ways. Values you entered yourself are kept.",
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
                if (enabled && access != null) {
                    AccessRow("Steps and sleep", "in", HealthAccess.STEPS_SLEEP in access!!, null)
                    AccessRow("Weight", "in · out", HealthAccess.READ_WEIGHT in access!!, HealthAccess.WRITE_WEIGHT in access!!)
                    AccessRow("Workouts and cardio", "in · out", HealthAccess.READ_EXERCISE in access!!, HealthAccess.WRITE_EXERCISE in access!!)
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
            if (enabled && granted == true && access!!.size < HealthAccess.entries.size) {
                TextButton(onClick = { request.launch(c.healthConnect.permissions) }, contentPadding = PaddingValues(0.dp)) { Text("ALLOW MORE DATA TYPES") }
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

/** One data type with whether reading (and writing, when it applies) is allowed. */
@Composable
private fun AccessRow(label: String, direction: String, read: Boolean, write: Boolean?) {
    val all = read && (write ?: true)
    val none = !read && write != true
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            when {
                all -> direction
                none -> "not allowed"
                read -> "in only"
                else -> "out only"
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (none) MaterialTheme.colorScheme.onSurfaceVariant else IronTheme.colors.success,
        )
    }
}
