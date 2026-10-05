package app.ironlog.personal.ui.account

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.cloud.GoogleAccount
import app.ironlog.personal.data.cloud.GoogleSignIn
import app.ironlog.personal.data.cloud.SignInCancelled
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.theme.IronTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private tailrec fun Context.activity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.activity()
        else -> null
    }

/** "Sign in with Google" that stores the account; [onSignedIn] gets it, e.g. to prefill a name. */
@Composable
fun GoogleSignInButton(c: AppContainer, onSignedIn: (GoogleAccount) -> Unit = {}, onMessage: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    SecondaryButton(
        if (busy) "Signing in…" else "Sign in with Google",
        enabled = !busy,
        icon = Icons.Filled.AccountCircle,
        onClick = {
            if (!GoogleSignIn.configured) {
                onMessage(GoogleSignIn.NOT_CONFIGURED)
                return@SecondaryButton
            }
            val activity = context.activity() ?: return@SecondaryButton
            busy = true
            onMessage(null)
            scope.launch {
                runCatching { GoogleSignIn.signIn(activity) }
                    .onSuccess {
                        c.saveAccount(it)
                        onSignedIn(it)
                    }
                    .onFailure { if (it !is SignInCancelled) onMessage("Sign-in failed: ${it.message ?: "unknown error"}") }
                busy = false
            }
        },
    )
}

internal enum class DriveAction { BACKUP, RESTORE }

private val WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

/** Drive backup and restore, including the one-time consent screen. */
class DriveActions internal constructor(
    val busy: State<Boolean>,
    val message: MutableState<String?>,
    private val start: (DriveAction) -> Unit,
) {
    fun backup() = start(DriveAction.BACKUP)

    fun restore() = start(DriveAction.RESTORE)
}

@Composable
fun rememberDriveActions(c: AppContainer): DriveActions {
    val account by c.account.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val message = remember { mutableStateOf<String?>(null) }
    val busy = remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DriveAction?>(null) }

    suspend fun run(action: DriveAction, token: String) {
        busy.value = true
        message.value =
            runCatching {
                    when (action) {
                        DriveAction.BACKUP -> {
                            c.drive.upload(token, c.backup.exportJson())
                            c.setLastDriveBackup(System.currentTimeMillis())
                            "Backed up to Google Drive."
                        }
                        DriveAction.RESTORE -> {
                            val text = c.drive.download(token) ?: return@runCatching "No Ironlog backup found in this Google account."
                            c.backup.importJson(text)
                            "Restored from Google Drive."
                        }
                    }
                }
                .getOrElse { "Google Drive: ${it.message ?: "request failed"}" }
        busy.value = false
    }

    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            val action = pending ?: return@rememberLauncherForActivityResult
            pending = null
            val activity = context.activity() ?: return@rememberLauncherForActivityResult
            val token = if (result.resultCode == Activity.RESULT_OK) GoogleSignIn.tokenFromConsent(activity, result.data) else null
            if (token == null) message.value = "Google Drive access was not granted." else scope.launch { run(action, token) }
        }

    return remember(account?.email) {
        DriveActions(busy, message) { action ->
            val activity = context.activity()
            if (activity != null) {
                message.value = null
                scope.launch {
                    runCatching { GoogleSignIn.authorizeDrive(activity, account?.email) }
                        .onSuccess { result ->
                            val token = result.accessToken
                            if (result.hasResolution()) {
                                pending = action
                                consent.launch(IntentSenderRequest.Builder(result.pendingIntent!!.intentSender).build())
                            } else if (token != null) {
                                run(action, token)
                            } else {
                                message.value = "Google Drive access was not granted."
                            }
                        }
                        .onFailure { message.value = "Google Drive: ${it.message ?: "authorization failed"}" }
                }
            }
        }
    }
}

/** Settings section: optional Google account and backup to the user's own Google Drive. */
@Composable
fun AccountSection(c: AppContainer) {
    val account by c.account.collectAsState(initial = null)
    val lastBackup by c.lastDriveBackup.collectAsState(initial = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val actions = rememberDriveActions(c)
    var message by actions.message
    val busy by actions.busy
    var confirmRestore by remember { mutableStateOf(false) }

    SectionHeader("Account")
    val current = account
    if (current == null) {
        Text(
            "Optional. Ironlog works fully without an account. Signing in lets you back up to your own Google Drive and restore on a new phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GoogleSignInButton(c, onMessage = { message = it })
    } else {
        IronCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(current.name.ifBlank { current.email }.take(1).uppercase())
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.name.ifBlank { "Google account" }, style = MaterialTheme.typography.titleMedium)
                    Text(current.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                lastBackup?.let { "Last Drive backup: ${WHEN.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))}" } ?: "Not backed up to Drive yet.",
                style = MaterialTheme.typography.bodySmall,
                color = if (lastBackup != null) IronTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(if (busy) "Working…" else "Back up to Google Drive", enabled = !busy, icon = Icons.Filled.CloudUpload, onClick = { actions.backup() })
        SecondaryButton("Restore from Google Drive", enabled = !busy, icon = Icons.Filled.CloudDownload, onClick = { confirmRestore = true })
        Text(
            "The backup is one file in Drive's hidden app folder: only Ironlog can read it. Progress and physique photos stay on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = {
                scope.launch {
                    c.saveAccount(null)
                    message = "Signed out. Your data stays on this phone."
                    GoogleSignIn.signOut(context)
                }
            },
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("SIGN OUT")
        }
    }
    AccountMessage(message)

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?") },
            text = { Text("This replaces the data on this phone with your last Drive backup.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        actions.restore()
                    }
                ) {
                    Text("Restore")
                }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
}

/** Shows a message in the accent colour when it reports success, else as an error. */
@Composable
fun AccountMessage(text: String?) {
    text?.let {
        Text(it, color = if (it.startsWith("Backed up") || it.startsWith("Restored") || it.startsWith("Signed out")) IronTheme.colors.accent else MaterialTheme.colorScheme.error)
    }
}
