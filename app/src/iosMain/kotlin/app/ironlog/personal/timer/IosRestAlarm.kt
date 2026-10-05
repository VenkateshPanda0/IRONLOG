package app.ironlog.personal.timer

import app.ironlog.personal.time.nowMillis
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter

/** Rings at the end of a rest period with a local notification (shown even if the app is closed). */
class IosRestAlarm : RestAlarm {
    private val center get() = UNUserNotificationCenter.currentNotificationCenter()

    override fun schedule(endAtMs: Long) {
        cancel()
        val seconds = ((endAtMs - nowMillis()) / 1000.0).coerceAtLeast(1.0)
        val content =
            UNMutableNotificationContent().apply {
                setTitle("Rest is over")
                setBody("Time for your next set.")
                setSound(UNNotificationSound.defaultSound)
            }
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        center.addNotificationRequest(UNNotificationRequest.requestWithIdentifier(ID, content, trigger), withCompletionHandler = null)
    }

    override fun cancel() {
        center.removePendingNotificationRequestsWithIdentifiers(listOf(ID))
        center.removeDeliveredNotificationsWithIdentifiers(listOf(ID))
    }

    private companion object {
        const val ID = "rest-timer"
    }
}
