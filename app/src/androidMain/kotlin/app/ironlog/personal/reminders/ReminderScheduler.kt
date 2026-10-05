package app.ironlog.personal.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.MainActivity
import app.ironlog.personal.R
import app.ironlog.personal.domain.ReminderKind
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.domain.ReminderSettings
import app.ironlog.personal.domain.ReminderState
import app.ironlog.personal.domain.Reminders
import app.ironlog.personal.domain.Training
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One inexact daily alarm per enabled reminder. Each firing decides from current data whether to
 * notify, then books the next day, so changes to training days need no rescheduling.
 */
class ReminderScheduler(private val context: Context) {
    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun apply(settings: ReminderSettings, now: LocalDateTime = LocalDateTime.now()) {
        set(ReminderKind.WORKOUT, settings.workout, settings.workoutMinutes, now)
        set(ReminderKind.STREAK, settings.streak, settings.streakMinutes, now)
    }

    private fun set(kind: ReminderKind, on: Boolean, minutes: Int, now: LocalDateTime) {
        val pending = pendingIntent(context, kind)
        alarms.cancel(pending)
        if (!on) return
        val at = Reminders.nextAt(now, minutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Inexact but Doze-safe; no exact-alarm permission is needed for a reminder.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    companion object {
        const val CHANNEL_ID = "ironlog_reminders"
        const val EXTRA_KIND = "kind"

        fun pendingIntent(context: Context, kind: ReminderKind): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                100 + kind.ordinal,
                Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind.name),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        /** Posts [message]; tapping it opens the app on Home, where today's workout starts. */
        fun notify(context: Context, message: ReminderMessage) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Training-day reminders and streak nudges"
                }
            )
            if (!canNotify(context)) return
            val open =
                PendingIntent.getActivity(
                    context,
                    200,
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            manager.notify(
                300 + message.kind.ordinal,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle(message.title)
                    .setContentText(message.text)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .build(),
            )
        }

        /** Reads today's training state from the database. */
        suspend fun state(app: IronlogApp, today: LocalDate = LocalDate.now()): ReminderState {
            val c = app.container
            val zone = ZoneId.systemDefault()
            val profile = c.profile.first()
            val dates = c.workouts.history.first().map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
            val active = c.programs.active.first()
            val next = active?.let { runCatching { c.programs.nextDay(it.programId) }.getOrNull() }
            val exercises = next?.let { c.programs.prescriptions(it.id).first().size } ?: 0
            return ReminderState(
                today = today,
                trainingDays = Training.parseWeekdays(profile?.trainingWeekdays.orEmpty()),
                perWeek = profile?.daysPerWeek ?: 3,
                workoutDates = dates,
                workoutInProgress = c.workouts.active.first() != null,
                nextDayName = next?.name,
                nextDayExercises = exercises,
            )
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val kind = runCatching { ReminderKind.valueOf(intent?.getStringExtra(ReminderScheduler.EXTRA_KIND).orEmpty()) }.getOrNull() ?: return
        val app = context.applicationContext as IronlogApp
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = app.container.reminderSettings.first()
                // Book tomorrow first, so a failure below never stops future reminders.
                ReminderScheduler(context).apply(settings)
                if (app.container.profile.first() != null) {
                    val state = ReminderScheduler.state(app)
                    val message = if (kind == ReminderKind.WORKOUT) Reminders.workout(state) else Reminders.streak(state)
                    if (message != null) ReminderScheduler.notify(context, message)
                }
            } finally {
                done.finish()
            }
        }
    }
}

/** Alarms are cleared by a reboot, an app update or a clock change; book them again. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    private val actions =
        setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

    override fun onReceive(context: Context, intent: Intent?) {
        // The receiver is exported for system broadcasts; ignore anything else sent to it.
        if (intent?.action !in actions) return
        val app = context.applicationContext as IronlogApp
        val done = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ReminderScheduler(context).apply(app.container.reminderSettings.first())
            } finally {
                done.finish()
            }
        }
    }
}
