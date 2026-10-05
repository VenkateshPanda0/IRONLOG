package app.ironlog.personal.reminders

import app.ironlog.personal.AppContainer
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.domain.ReminderSettings
import app.ironlog.personal.domain.Reminders
import app.ironlog.personal.time.atTime
import app.ironlog.personal.time.now
import app.ironlog.personal.time.plusDays
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS cannot run app code when a reminder is due, so the same rules decide a week ahead which
 * days get a workout reminder or a streak nudge. The plan is redone whenever settings or workouts
 * change, so a reminder for a day you have already trained disappears.
 */
class IosReminders(private val container: () -> AppContainer) : ReminderScheduling {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun apply(settings: ReminderSettings) {
        val c = container()
        c.appScope.launch { plan(c, settings) }
    }

    /** Re-plans with the saved settings, for app start and after a workout. */
    fun refresh() {
        val c = container()
        c.appScope.launch { plan(c, c.reminderSettings.first()) }
    }

    private suspend fun plan(c: AppContainer, settings: ReminderSettings) {
        center.removePendingNotificationRequestsWithIdentifiers((0 until DAYS).flatMap { listOf("workout-$it", "streak-$it") })
        if (!settings.workout && !settings.streak) return
        val today = LocalDate.now()
        val base = c.reminderState(today)
        val now = LocalDateTime.now()
        for (offset in 0 until DAYS) {
            val day = today.plusDays(offset)
            val state = base.copy(today = day)
            if (settings.workout) book("workout-$offset", day, settings.workoutMinutes, now, Reminders.workout(state))
            if (settings.streak) book("streak-$offset", day, settings.streakMinutes, now, Reminders.streak(state))
        }
    }

    private fun book(id: String, day: LocalDate, minutes: Int, now: LocalDateTime, message: ReminderMessage?) {
        message ?: return
        val at = day.atTime(LocalTime(minutes / 60, minutes % 60))
        if (at <= now) return
        val components =
            NSDateComponents().apply {
                setYear(day.year.toLong())
                setMonth(day.monthNumber.toLong())
                setDay(day.dayOfMonth.toLong())
                setHour((minutes / 60).toLong())
                setMinute((minutes % 60).toLong())
            }
        val content =
            UNMutableNotificationContent().apply {
                setTitle(message.title)
                setBody(message.text)
                setSound(UNNotificationSound.defaultSound)
            }
        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(id, content, trigger), withCompletionHandler = null)
    }

    private companion object {
        const val DAYS = 7
    }
}
