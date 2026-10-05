package app.ironlog.personal.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.domain.ReminderKind
import app.ironlog.personal.domain.ReminderSettings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderSchedulerTest {
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val alarms get() = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
    private val notifications get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    private val today = LocalDate.now()

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun profile(days: String) = runBlocking {
        app.container.saveProfile(UserProfileEntity(name = "Test", trainingWeekdays = days, daysPerWeek = 3))
    }

    private fun fire(kind: String) {
        app.sendBroadcast(Intent(app, ReminderReceiver::class.java).putExtra(ReminderScheduler.EXTRA_KIND, kind))
        // The receiver works on a background coroutine; let it finish.
        repeat(100) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (notifications.allNotifications.isNotEmpty()) return
            Thread.sleep(50)
        }
    }

    @Test
    fun enabledRemindersBookTheNextDailyTimeAndDisablingCancels() {
        val now = LocalDateTime.of(today, java.time.LocalTime.of(9, 0))
        ReminderScheduler(app).apply(ReminderSettings(workout = true, workoutMinutes = 18 * 60, streak = false), now)
        val booked = alarms.scheduledAlarms.single()
        assertEquals(now.withHour(18).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), booked.triggerAtTime)
        ReminderScheduler(app).apply(ReminderSettings(workout = true, streak = true), now)
        assertEquals(2, alarms.scheduledAlarms.size)
        ReminderScheduler(app).apply(ReminderSettings(), now)
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

    @Test
    fun alarmOnATrainingDayPostsAReminderAndBooksTomorrow() {
        profile(today.dayOfWeek.name.take(3))
        runBlocking { app.container.saveReminderSettings(ReminderSettings(workout = true)) }
        fire("WORKOUT")
        val posted = notifications.allNotifications.single()
        assertEquals("Your first workout is waiting", shadowOf(posted).contentTitle)
        assertTrue(posted.contentIntent != null)
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
    }

    @Test
    fun restDaysStayQuiet() {
        profile(today.plusDays(1).dayOfWeek.name.take(3))
        runBlocking { app.container.saveReminderSettings(ReminderSettings(workout = true)) }
        fire("WORKOUT")
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun blockedNotificationsPostNothing() {
        shadowOf(app).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        profile(today.dayOfWeek.name.take(3))
        fire("WORKOUT")
        assertTrue(notifications.allNotifications.isEmpty())
    }

    @Test
    fun rebootRebooksAlarmsButSpoofedIntentsAreIgnored() {
        runBlocking { app.container.saveReminderSettings(ReminderSettings(workout = true)) }
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(ReminderScheduler.pendingIntent(app, ReminderKind.WORKOUT))
        ReminderRescheduleReceiver().onReceive(app, Intent("com.example.SPOOF"))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        app.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED).setClass(app, ReminderRescheduleReceiver::class.java))
        repeat(100) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (alarms.scheduledAlarms.isNotEmpty()) return
            Thread.sleep(50)
        }
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
    }
}
