package app.ironlog.personal.data.backup

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.SeedState
import app.ironlog.personal.data.db.UserProfileEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TransferTest {
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val c get() = app.container
    private val photos get() = File(app.filesDir, "photos")

    @Before
    fun seeded() {
        val deadline = nowMillis() + 120_000
        while (c.seedState.value !is SeedState.Ready) {
            check(nowMillis() < deadline)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
    }

    /** A profile, one finished workout and one progress photo. */
    private fun grind() = runBlocking {
        c.saveProfile(UserProfileEntity(name = "Venkatesh", weightKg = 78.0))
        val id = c.workouts.start("Push", emptyList())
        c.workouts.finish(id)
        val image = File(app.cacheDir, "pic.png")
        android.graphics.Bitmap.createBitmap(1200, 1600, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.GRAY) }
            .let { b -> image.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        c.body.addPhoto(app.contentResolver, android.net.Uri.fromFile(image), LocalDate.now())
    }

    @Test
    fun zipCarriesDataAndPhotosToAnotherPhone() = runBlocking {
        grind()
        val zip = ByteArrayOutputStream().also { c.archive.export(it) }.toByteArray()
        // "New phone": everything gone.
        c.clearPersonalData()
        assertNull(c.dao().profileOnce())
        assertFalse(photos.exists() && photos.list().orEmpty().isNotEmpty())
        c.archive.import(ByteArrayInputStream(zip))
        assertEquals("Venkatesh", c.dao().profileOnce()!!.name)
        assertEquals(1, c.workouts.history.first().size)
        val photo = c.body.photos.first().single()
        assertTrue(c.body.photoFile(photo).isFile)
    }

    @Test
    fun hostileZipEntriesCannotEscapeAndOldJsonStillImports() = runBlocking {
        grind()
        val json = c.backup.exportJson()
        val evil = ByteArrayOutputStream()
        ZipOutputStream(evil).use { z ->
            z.putNextEntry(ZipEntry(BackupArchive.JSON)); z.write(json.toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("photos/../../shared_prefs/evil.xml")); z.write("x".toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("../escape.txt")); z.write("x".toByteArray()); z.closeEntry()
        }
        c.archive.import(ByteArrayInputStream(evil.toByteArray()))
        assertFalse(File(app.filesDir.parentFile, "shared_prefs/evil.xml").exists())
        assertFalse(File(app.filesDir.parentFile, "escape.txt").exists())
        assertEquals("Venkatesh", c.dao().profileOnce()!!.name)
        // A plain .json from older versions imports too.
        c.saveProfile(c.dao().profileOnce()!!.copy(name = "Changed"))
        c.archive.import(ByteArrayInputStream(json.toByteArray()))
        assertEquals("Venkatesh", c.dao().profileOnce()!!.name)
    }

    @Test
    fun newPhoneRestoresFromTheAutomaticSnapshot() = runBlocking {
        // Before onboarding nothing is written, so a restored snapshot is never overwritten.
        c.clearPersonalData()
        c.backupIfChanged()
        assertNull(c.autoBackup.available())
        grind()
        c.backupIfChanged()
        val dir = File(app.filesDir, "backup")
        val snapshot = File(dir, "snapshot.json.gz")
        println("SNAPSHOT bytes=${snapshot.length()} photos=${File(dir, "photos").listFiles().orEmpty().sumOf { it.length() }}")
        assertTrue("snapshot fits Auto Backup's 25 MB with room for photos", snapshot.length() < 10L * 1024 * 1024)
        // Simulate Android restoring only files/backup onto a fresh install.
        val saved = File(app.cacheDir, "cloud").apply { deleteRecursively() }
        dir.copyRecursively(saved)
        c.clearPersonalData()
        saved.copyRecursively(dir, overwrite = true)
        val info = c.autoBackup.available()
        assertNotNull(info)
        assertEquals("Venkatesh", info!!.name)
        assertEquals(1, info.workouts)
        assertEquals(1, info.photos)
        c.autoBackup.restore()
        assertEquals("Venkatesh", c.dao().profileOnce()!!.name)
        assertEquals(1, c.workouts.history.first().size)
        val photo = c.body.photos.first().single()
        val restored = c.body.photoFile(photo)
        assertTrue(restored.isFile)
        // The cloud copy is reduced to 1024 px on the long edge.
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(restored.path, bounds)
        assertTrue(maxOf(bounds.outWidth, bounds.outHeight) <= 1024)
    }

    @Test
    fun unchangedDataIsNotRewrittenAndDeletingDataDropsTheSnapshot() = runBlocking {
        grind()
        c.backupIfChanged()
        // Let Room's asynchronous change notifications from grind() arrive and be written first.
        repeat(5) { Thread.sleep(100); c.backupIfChanged() }
        val snapshot = File(app.filesDir, "backup/snapshot.json.gz")
        snapshot.setLastModified(1_000)
        c.backupIfChanged() // nothing changed since
        assertEquals(1_000, snapshot.lastModified())
        c.body.log(LocalDate.now(), 77.5)
        // Room reports table changes asynchronously.
        repeat(50) { if (snapshot.lastModified() == 1_000L) { Thread.sleep(20); c.backupIfChanged() } }
        assertTrue(snapshot.lastModified() != 1_000L)
        c.clearPersonalData()
        assertNull(c.autoBackup.available())
    }
}
