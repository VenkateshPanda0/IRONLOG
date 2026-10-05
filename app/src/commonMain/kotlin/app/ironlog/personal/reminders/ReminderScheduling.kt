package app.ironlog.personal.reminders

import app.ironlog.personal.domain.ReminderSettings

/** Books (or cancels) the daily workout and streak reminders on the platform. */
interface ReminderScheduling {
    fun apply(settings: ReminderSettings)
}
