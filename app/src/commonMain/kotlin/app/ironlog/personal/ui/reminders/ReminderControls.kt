package app.ironlog.personal.ui.reminders

import app.ironlog.personal.time.value
import app.ironlog.personal.reminders.reminderState
import app.ironlog.personal.ui.platform.LocalPlatform
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.domain.ReminderKind
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.domain.ReminderSettings
import app.ironlog.personal.domain.Reminders
import app.ironlog.personal.domain.Training
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.launch

private val DAY_SHORT = mapOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat", 7 to "Sun")

/** Settings section for workout reminders and the streak saver. */
@Composable
fun ReminderSection(c: AppContainer) {
    val settings by c.reminderSettings.collectAsState(initial = ReminderSettings())
    val profile by c.profile.collectAsState(initial = null)
    val platform = LocalPlatform.current
    val scope = rememberCoroutineScope()
    val ask = platform.rememberNotificationPermission()
    var allowed by remember { mutableStateOf(platform.canNotify()) }
    var note by remember { mutableStateOf<String?>(null) }

    fun save(value: ReminderSettings) {
        val turningOn = (value.workout && !settings.workout) || (value.streak && !settings.streak)
        scope.launch { c.saveReminderSettings(value) }
        if (turningOn) ask { granted -> allowed = granted }
    }

    val days = Training.parseWeekdays(profile?.trainingWeekdays.orEmpty()).sortedBy { it.value }.joinToString { DAY_SHORT.getValue(it.value) }
    SectionHeader("Reminders")
    IronCard {
        ToggleRow(
            "Workout reminder",
            if (days.isBlank()) "On your training days" else "On $days, unless you've already trained",
            settings.workout,
        ) { save(settings.copy(workout = it)) }
        if (settings.workout) TimeStepper(settings.workoutMinutes) { save(settings.copy(workoutMinutes = it)) }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        ToggleRow(
            "Streak saver",
            "An evening nudge only when today's workout decides your weekly goal",
            settings.streak,
        ) { save(settings.copy(streak = it)) }
        if (settings.streak) TimeStepper(settings.streakMinutes) { save(settings.copy(streakMinutes = it)) }
    }
    if ((settings.workout || settings.streak) && !allowed) {
        Text("Notifications are blocked for Ironlog, so reminders can't appear.", color = MaterialTheme.colorScheme.error)
        SecondaryButton(
            "Allow notifications",
            onClick = {
                ask { granted ->
                    allowed = granted
                    // Denied before: only the system settings can change it now.
                    if (!granted) platform.openNotificationSettings()
                }
            },
        )
    }
    TextButton(
        onClick = {
            ask { granted ->
                allowed = granted
                if (!granted) {
                    note = "Allow notifications to see reminders."
                    return@ask
                }
                scope.launch {
                    val state = c.reminderState()
                    val message =
                        Reminders.workout(state.copy(trainingDays = state.trainingDays + state.today.dayOfWeek, workoutDates = state.workoutDates - state.today))
                            ?: ReminderMessage(ReminderKind.WORKOUT, "Reminders are on", "This is how a workout reminder will look.")
                    platform.showNotification(message)
                    note = "Test notification sent."
                }
            }
        },
        contentPadding = PaddingValues(0.dp),
    ) {
        Text("SEND A TEST NOTIFICATION")
    }
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Time of day in 15-minute steps; no dialog, so it works one-handed between sets. */
@Composable
private fun TimeStepper(minutes: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("At", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((minutes - 15).mod(24 * 60)) }) { Icon(Icons.Filled.Remove, contentDescription = "15 minutes earlier") }
        Text(Reminders.format(minutes), style = MaterialTheme.typography.headlineSmall)
        IconButton(onClick = { onChange((minutes + 15).mod(24 * 60)) }) { Icon(Icons.Filled.Add, contentDescription = "15 minutes later") }
    }
}

/** One-time Home card offering reminders; disappears once answered. */
@Composable
fun ReminderPromptCard(c: AppContainer) {
    val dismissed by c.reminderPromptDismissed.collectAsState(initial = true)
    val settings by c.reminderSettings.collectAsState(initial = ReminderSettings())
    val scope = rememberCoroutineScope()
    val ask = LocalPlatform.current.rememberNotificationPermission()
    if (dismissed || settings.workout) return
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = IronTheme.colors.accent)
            Spacer(Modifier.width(10.dp))
            Text("NEVER MISS A TRAINING DAY", style = MaterialTheme.typography.titleMedium)
        }
        Text(
            "Get a reminder at ${Reminders.format(settings.workoutMinutes)} on your training days, plus a nudge when your weekly streak is on the line. Change it any time in Settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                "Turn on",
                modifier = Modifier.weight(1f),
                onClick = {
                    scope.launch { c.saveReminderSettings(settings.copy(workout = true, streak = true)) }
                    ask {}
                },
            )
            SecondaryButton("Not now", modifier = Modifier.weight(1f), onClick = { scope.launch { c.dismissReminderPrompt() } })
        }
    }
}
