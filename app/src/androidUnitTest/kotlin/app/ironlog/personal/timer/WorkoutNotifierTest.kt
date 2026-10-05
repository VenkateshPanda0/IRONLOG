package app.ironlog.personal.timer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.domain.WeightUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class WorkoutNotifierTest {
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val c get() = app.container
    private val notifications get() = shadowOf(app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)

    @Before
    fun grant() {
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { c.setWeightUnit(WeightUnit.KG) } // Robolectric's US locale defaults to lb
    }

    private fun live() = notifications.getNotification(WorkoutNotifier.NOTIFICATION_ID)

    private fun waitFor(what: String, check: () -> Boolean) {
        repeat(200) {
            shadowOf(android.os.Looper.getMainLooper()).idle()
            if (check()) return
            Thread.sleep(25)
        }
        throw AssertionError("timed out waiting for $what")
    }

    @Test
    fun nextSetFollowsOrderAndSupersetRounds() = runBlocking {
        val id = c.workouts.start("Upper", listOf(Triple("a", "Bench", 2), Triple("b", "Row", 2), Triple("x", "Curl", 1)))
        val dao = c.dao()
        var rows = dao.sessionExercisesOnce(id).sortedBy { it.orderIndex }
        fun sets() = runBlocking { dao.sessionSets(id).first() }
        assertEquals("Bench", WorkoutFlow.next(rows, sets())!!.first.exerciseNameSnapshot)
        c.workouts.supersetWithNext(rows[0])
        rows = dao.sessionExercisesOnce(id).sortedBy { it.orderIndex }
        assertTrue(!WorkoutFlow.restsAfter(rows[0], rows) && WorkoutFlow.restsAfter(rows[1], rows) && WorkoutFlow.restsAfter(rows[2], rows))
        // Bench set 1 done: Row comes next, then back to Bench for round 2.
        c.workouts.completeWithFallback(sets().first { it.sessionExerciseId == rows[0].id }.id, 60.0, 8)
        assertEquals("Row", WorkoutFlow.next(rows, sets())!!.first.exerciseNameSnapshot)
        Thread.sleep(5)
        c.workouts.completeWithFallback(sets().first { it.sessionExerciseId == rows[1].id }.id, 50.0, 10)
        assertEquals("Bench", WorkoutFlow.next(rows, sets())!!.first.exerciseNameSnapshot)
        c.workouts.discard(id)
    }

    @Test
    fun liveNotificationShowsTheNextSetAndItsButtonsWork() {
        // A finished session gives the hints: 60 kg × 8 last time.
        runBlocking {
            val earlier = c.workouts.start("Earlier", listOf(Triple("bench", "Bench Press", 1)))
            c.dao().sessionSets(earlier).first().forEach { c.workouts.completeWithFallback(it.id, 60.0, 8) }
            c.workouts.finish(earlier)
        }
        val id = runBlocking { c.workouts.start("Push", listOf(Triple("bench", "Bench Press", 2))) }
        waitFor("live notification for Push") { live()?.let { shadowOf(it).contentTitle == "Push" } == true }
        val first = shadowOf(live())
        assertEquals("Push", first.contentTitle)
        assertTrue(first.contentText.toString(), first.contentText.toString().startsWith("Bench Press · set 1 of 2 · 60 kg × 8 reps"))
        assertEquals(listOf("Done"), live().actions.map { it.title.toString() })
        assertTrue(live().flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)

        // "Done" from the lock screen logs the set with the hints and starts the rest.
        live().actions.first().actionIntent.send()
        waitFor("rest buttons") { live()?.actions?.size == 3 }
        val set = runBlocking { c.dao().sessionSets(id).first().minBy { it.setIndex } }
        assertTrue(set.isCompleted)
        assertEquals(60.0, set.weightKg!!, 0.0)
        assertEquals(8, set.reps)
        assertEquals("Rest · next up", shadowOf(live()).contentTitle)
        assertEquals(listOf("Done", "+30 s", "Skip rest"), live().actions.map { it.title.toString() })

        live().actions.last().actionIntent.send() // Skip rest
        waitFor("rest cleared") { live()?.actions?.size == 1 }

        // Finishing removes it.
        runBlocking { c.workouts.finish(id) }
        waitFor("notification gone") { live() == null }
        assertNull(live())
    }
}
