package app.ironlog.personal.data.health

import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.data.db.CardioSessionEntity
import app.ironlog.personal.domain.CardioType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Two-way sync of weight and workouts against an in-memory Health Connect. */
@RunWith(RobolectricTestRunner::class)
class HealthTwoWayTest {
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val c get() = app.container
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.now()

    private fun at(daysAgo: Long, hour: Int): Instant = today.minusDays(daysAgo).atTime(hour, 0).atZone(zone).toInstant()

    private class FakeHealth(var access: Set<HealthAccess> = HealthAccess.entries.toSet()) : HealthSource {
        val weights = mutableListOf<HealthWeight>()
        val sessions = mutableListOf<HealthSession>()
        val writtenWeights = linkedMapOf<String, OutWeight>()
        val writtenSessions = linkedMapOf<String, OutSession>()
        val deleted = mutableListOf<String>()

        override suspend fun read(from: LocalDate, to: LocalDate) = emptyList<HealthDay>()

        override suspend fun granted() = access

        override suspend fun readWeights(from: Instant, to: Instant) = weights.filter { it.time in from..to }

        override suspend fun readSessions(from: Instant, to: Instant, withDistance: Boolean) =
            sessions.filter { it.start in from..to }.map { if (withDistance) it else it.copy(distanceKm = null) }

        // Keyed by client ID: writing the same record twice updates it, as Health Connect does.
        override suspend fun writeWeights(items: List<OutWeight>) = items.forEach { writtenWeights[it.clientId] = it }

        override suspend fun writeSessions(items: List<OutSession>) = items.forEach { writtenSessions[it.clientId] = it }

        override suspend fun deleteWeights(clientIds: List<String>) {
            deleted += clientIds
            clientIds.forEach { writtenWeights.remove(it) }
        }

        override suspend fun deleteSessions(clientIds: List<String>) {
            deleted += clientIds
            clientIds.forEach { writtenSessions.remove(it) }
        }
    }

    @Before
    fun reset() = runBlocking { c.setHealthSync(false) }

    @Test
    fun scaleWeighInsFillEmptyDaysButNeverReplaceYourOwn() = runBlocking {
        val fake = FakeHealth()
        c.healthSource = fake
        c.setHealthSync(true)
        c.body.log(today.minusDays(1), 80.0) // typed in by hand
        fake.weights += HealthWeight("scale-1", at(1, 7), 79.2) // same day: ignored
        fake.weights += HealthWeight("scale-2", at(2, 7), 79.6)
        fake.weights += HealthWeight("scale-3", at(2, 9), 79.4) // later the same day wins
        val result = c.syncHealth()
        assertEquals(1, result.weightsIn)
        val rows = c.dao().weightsBetween(today.minusDays(5).toString(), today.toString()).associateBy { it.date }
        assertEquals(80.0, rows.getValue(today.minusDays(1).toString()).weightKg, 0.0)
        val imported = rows.getValue(today.minusDays(2).toString())
        assertEquals(79.4, imported.weightKg, 0.0)
        assertEquals("scale-3", imported.healthId)
        // A corrected reading updates the import; your own weigh-in goes out, the import does not.
        fake.weights.replaceAll { if (it.id == "scale-3") it.copy(kg = 79.3) else it }
        c.syncHealth()
        assertEquals(79.3, c.dao().weightById(imported.id)!!.weightKg, 0.0)
        val mine = rows.getValue(today.minusDays(1).toString())
        assertEquals(setOf(HealthIds.weight(mine.id)), fake.writtenWeights.keys)
        // Deleting the import keeps it gone.
        c.body.deleteWeight(imported.id)
        c.syncHealth()
        assertNull(c.dao().weightsBetween(today.minusDays(2).toString(), today.minusDays(2).toString()).firstOrNull())
    }

    @Test
    fun watchSessionsBecomeCardioAndIronlogWorkoutsGoOut() = runBlocking {
        val fake = FakeHealth()
        c.healthSource = fake
        c.setHealthSync(true)
        fake.sessions += HealthSession("watch-run", at(1, 6), at(1, 6).plusSeconds(1800), CardioType.RUN, "Morning run", 5.04)
        fake.sessions += HealthSession("other-lift", at(1, 18), at(1, 19), null, "Gym", null) // strength from another app: skipped
        fake.sessions += HealthSession("blip", at(1, 20), at(1, 20).plusSeconds(120), CardioType.WALK, null, 0.1) // under 5 minutes
        val workout = c.workouts.start("Push", emptyList())
        c.workouts.finish(workout)
        c.wellness.addCardio(CardioSessionEntity(date = today.toString(), type = "CYCLE", durationMin = 40.0, distanceKm = 15.0))
        val first = c.syncHealth()
        assertEquals(1, first.sessionsIn)
        val imported = c.dao().cardioByHealthId("watch-run")!!
        assertEquals("RUN", imported.type)
        assertEquals(30.0, imported.durationMin, 0.0)
        assertEquals(5.04, imported.distanceKm!!, 0.0)
        assertEquals("Morning run", imported.notes)
        // Out: the strength workout and the hand-logged ride; never the imported run.
        val out = fake.writtenSessions.values.associateBy { it.clientId }
        assertEquals(null, out.getValue(HealthIds.workout(workout)).type)
        assertEquals("Push", out.getValue(HealthIds.workout(workout)).title)
        val ride = c.dao().cardioBetween(today.toString(), today.toString()).single { it.healthId == null }
        assertEquals(CardioType.CYCLE, out.getValue(HealthIds.cardio(ride.id)).type)
        assertEquals(40 * 60L, java.time.Duration.between(out.getValue(HealthIds.cardio(ride.id)).start, out.getValue(HealthIds.cardio(ride.id)).end).seconds)
        assertEquals(2, fake.writtenSessions.size)
        // Re-syncing creates no duplicates either way.
        val second = c.syncHealth()
        assertEquals(0, second.sessionsIn)
        assertEquals(2, fake.writtenSessions.size)
        // Deleting your ride removes it from Health Connect at the next sync; deleting the import stops re-import.
        c.wellness.deleteCardio(ride.id)
        c.wellness.deleteCardio(imported.id)
        c.syncHealth()
        assertTrue(HealthIds.cardio(ride.id) in fake.deleted)
        assertNull(c.dao().cardioByHealthId("watch-run"))
        assertEquals(1, fake.writtenSessions.size)
    }

    @Test
    fun onlyGrantedPartsSync() = runBlocking {
        val fake = FakeHealth(setOf(HealthAccess.READ_EXERCISE))
        c.healthSource = fake
        c.setHealthSync(true)
        fake.sessions += HealthSession("ride", at(0, 7), at(0, 8), CardioType.CYCLE, null, 20.0)
        c.body.log(today, 81.0)
        val result = c.syncHealth()
        assertEquals(1, result.sessionsIn)
        assertNull(c.dao().cardioByHealthId("ride")!!.distanceKm) // no distance access
        assertTrue(fake.writtenWeights.isEmpty())
        assertTrue(fake.writtenSessions.isEmpty())
        assertTrue(c.healthSyncedAt.first() != null)
    }
}
