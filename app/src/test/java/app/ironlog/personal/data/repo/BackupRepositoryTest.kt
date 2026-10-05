package app.ironlog.personal.data.repo

import androidx.room.Room
import app.ironlog.personal.data.db.IronlogDatabase
import app.ironlog.personal.data.db.UserProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private lateinit var db: IronlogDatabase
    private lateinit var backup: BackupRepository

    @Before
    fun setUp() {
        db =
            Room.inMemoryDatabaseBuilder(
                    RuntimeEnvironment.getApplication(),
                    IronlogDatabase::class.java,
                )
                .allowMainThreadQueries()
                .build()
        backup = BackupRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun profileSurvivesExportAndImport() = runBlocking {
        db.dao().saveProfile(UserProfileEntity(name = "Local profile", weightKg = 68.4))
        val content = backup.exportJson()
        db.dao().saveProfile(UserProfileEntity(name = "Temporary"))
        backup.importJson(content)
        assertEquals("Local profile", db.dao().profileOnce()?.name)
        assertEquals(68.4, db.dao().profileOnce()?.weightKg ?: 0.0, 0.0001)
    }

    @Test
    fun corruptBackupIsRejected() = runBlocking {
        try {
            backup.importJson("not-json")
            throw AssertionError("invalid backup was accepted")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun wellnessAndPhysiqueSurviveExportAndImport() = runBlocking {
        val dao = db.dao()
        dao.addCardio(app.ironlog.personal.data.db.CardioSessionEntity(date = "2026-10-01", type = "RUN", durationMin = 30.0, distanceKm = 5.0))
        dao.saveDailyLog(app.ironlog.personal.data.db.DailyLogEntity(date = "2026-10-01", steps = 9000, waterMl = 2500))
        val habit = dao.addHabit(app.ironlog.personal.data.db.HabitEntity(name = "Creatine"))
        dao.checkHabit(app.ironlog.personal.data.db.HabitCheckEntity(habit, "2026-10-01"))
        dao.addMeasurement(app.ironlog.personal.data.db.BodyMeasurementEntity(date = "2026-10-01", waistCm = 82.0))
        dao.addPhysiqueScan(app.ironlog.personal.data.db.PhysiqueScanEntity(date = "2026-10-01", fileName = "a.jpg", goal = "CLASSIC", shoulder = 180.0, waist = 110.0, hip = 130.0, leftThigh = 60.0, rightThigh = 60.0, height = 700.0, legToTorso = 1.4, matchScore = 70))
        val content = backup.exportJson()
        dao.deleteAllCardio()
        dao.deleteAllHabits()
        dao.deleteAllPhysiqueScans()
        backup.importJson(content)
        assertEquals(1, dao.allCardio().size)
        assertEquals(9000, dao.dailyLogOnce("2026-10-01")?.steps)
        assertEquals(listOf("Creatine"), dao.allHabits().map { it.name })
        assertEquals(1, dao.allHabitChecks().size)
        assertEquals(82.0, dao.allMeasurements().single().waistCm!!, 0.0)
        assertEquals(70, dao.allPhysiqueScans().single().matchScore)
    }

    @Test
    fun oldBackupWithoutWellnessStillImports() = runBlocking {
        backup.importJson("""{"schemaVersion":1,"profile":[{"id":1,"name":"Old"}]}""")
        assertEquals("Old", db.dao().profileOnce()?.name)
    }
}
