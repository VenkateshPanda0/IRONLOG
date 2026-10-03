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
    private lateinit var db:IronlogDatabase
    private lateinit var backup:BackupRepository
    @Before fun setUp() { db=Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(),IronlogDatabase::class.java).allowMainThreadQueries().build(); backup=BackupRepository(db) }
    @After fun tearDown() { db.close() }
    @Test fun profileSurvivesExportAndImport()=runBlocking {
        db.dao().saveProfile(UserProfileEntity(name="Local profile",weightKg=68.4))
        val content=backup.exportJson()
        db.dao().saveProfile(UserProfileEntity(name="Temporary"))
        backup.importJson(content)
        assertEquals("Local profile",db.dao().profileOnce()?.name)
        assertEquals(68.4,db.dao().profileOnce()?.weightKg ?: 0.0,0.0001)
    }
    @Test fun corruptBackupIsRejected()=runBlocking {
        try { backup.importJson("not-json"); throw AssertionError("invalid backup was accepted") }
        catch(_:IllegalArgumentException) { }
    }
}
