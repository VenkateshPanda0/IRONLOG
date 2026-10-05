package app.ironlog.personal.data.backup

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.ironlog.personal.data.repo.BackupRepository
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Only plain file names are accepted for photos, so an archive can never write outside its folder. */
internal fun safePhotoName(name: String): String? = app.ironlog.personal.data.safeFileName(name)

/** Copies at most [limit] bytes; larger entries are rejected rather than filling memory or disk. */
private fun java.io.InputStream.copyLimited(out: java.io.OutputStream, limit: Long): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val n = read(buffer)
        if (n < 0) return total
        total += n
        if (total > limit) throw IllegalArgumentException("Backup entry is too large")
        out.write(buffer, 0, n)
    }
}

/**
 * Everything in one file: `ironlog_backup.json` plus `photos/<name>` for progress photos. Also
 * reads the plain JSON files that older versions exported.
 */
class BackupArchive(private val backup: BackupRepository, private val photoDir: File) {
    suspend fun export(out: OutputStream) =
        withContext(Dispatchers.IO) {
            val json = backup.exportJson()
            val photos = backup.peek(json).photos.mapNotNull { p -> safePhotoName(p.fileName)?.let { File(photoDir, it) }?.takeIf { it.isFile } }
            ZipOutputStream(out.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(JSON))
                zip.write(json.toByteArray())
                zip.closeEntry()
                photos.forEach { file ->
                    zip.putNextEntry(ZipEntry("photos/${file.name}"))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }

    /** Imports a .zip from [export] or a plain .json backup. */
    suspend fun import(input: InputStream) =
        withContext(Dispatchers.IO) {
            val stream = BufferedInputStream(input)
            stream.mark(4)
            val zip = stream.read() == 'P'.code && stream.read() == 'K'.code
            stream.reset()
            if (!zip) {
                val bytes = java.io.ByteArrayOutputStream().also { stream.copyLimited(it, MAX_JSON) }
                backup.importJson(bytes.toString(Charsets.UTF_8.name()))
                return@withContext
            }
            // Unpack photos to a staging folder first; nothing changes unless the JSON imports.
            val staging = File(photoDir.parentFile, "photos-import").apply { deleteRecursively(); mkdirs() }
            var json: String? = null
            try {
                ZipInputStream(stream).use { z ->
                    var entry = z.nextEntry
                    var entries = 0
                    var photoBytes = 0L
                    while (entry != null) {
                        // Bounded so a crafted archive (zip bomb) cannot exhaust memory or storage.
                        if (++entries > MAX_ENTRIES) throw IllegalArgumentException("Backup has too many files")
                        when {
                            entry.name == JSON -> json = java.io.ByteArrayOutputStream().also { z.copyLimited(it, MAX_JSON) }.toString(Charsets.UTF_8.name())
                            entry.name.startsWith("photos/") ->
                                safePhotoName(entry.name.removePrefix("photos/"))?.let { name ->
                                    photoBytes += File(staging, name).outputStream().use { z.copyLimited(it, MAX_PHOTO) }
                                    if (photoBytes > MAX_PHOTOS_TOTAL) throw IllegalArgumentException("Backup photos are too large")
                                }
                        }
                        entry = z.nextEntry
                    }
                }
                val text = json ?: throw IllegalArgumentException("This .zip has no Ironlog backup inside")
                val staged = staging.list().orEmpty().toSet()
                backup.importJson(text) { name -> name in staged || File(photoDir, name).isFile }
                photoDir.mkdirs()
                staging.listFiles().orEmpty().forEach { it.copyTo(File(photoDir, it.name), overwrite = true) }
            } finally {
                staging.deleteRecursively()
            }
        }

    companion object {
        const val JSON = "ironlog_backup.json"
        private const val MAX_JSON = 200L * 1024 * 1024
        private const val MAX_PHOTO = 30L * 1024 * 1024
        private const val MAX_PHOTOS_TOTAL = 4L * 1024 * 1024 * 1024
        private const val MAX_ENTRIES = 20_000
    }
}

/** What an automatic snapshot would bring back, shown before restoring. */

/**
 * Keeps a compressed snapshot of all data, plus reduced copies of progress photos, in
 * files/backup. Android's Auto Backup copies only that folder to the user's Google account (see
 * res/xml/data_extraction_rules.xml), so a new phone with the same account gets it back on install.
 */
class AutoBackup(private val backup: BackupRepository, private val dir: File, private val photoDir: File) {
    private val snapshot get() = File(dir, "snapshot.json.gz")
    private val photoCopies get() = File(dir, "photos")
    private val lock = Mutex()

    /** Writes the snapshot atomically: a crash mid-write leaves the previous one in place. */
    suspend fun write() =
        lock.withLock {
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val json = backup.exportJson()
                val temp = File(dir, "snapshot.tmp")
                GZIPOutputStream(temp.outputStream().buffered()).use { it.write(json.toByteArray()) }
                if (!temp.renameTo(snapshot)) {
                    temp.copyTo(snapshot, overwrite = true)
                    temp.delete()
                }
                syncPhotos(backup.peek(json).photos.sortedBy { it.date }.map { it.fileName })
            }
        }

    /**
     * Auto Backup allows 25 MB per app, so photos are stored at 1024 px and capped at
     * [PHOTO_BUDGET] bytes: the first photo always (the "before"), then the newest first.
     */
    private fun syncPhotos(namesOldestFirst: List<String>) {
        photoCopies.mkdirs()
        val names = namesOldestFirst.mapNotNull(::safePhotoName)
        val order = (names.take(1) + names.drop(1).reversed()).distinct()
        var used = 0L
        val keep = mutableSetOf<String>()
        for (name in order) {
            val copy = File(photoCopies, name)
            if (!copy.isFile) {
                val source = File(photoDir, name).takeIf { it.isFile } ?: continue
                shrink(source, copy) ?: continue
            }
            if (used + copy.length() > PHOTO_BUDGET) {
                copy.delete()
                continue
            }
            used += copy.length()
            keep += name
        }
        photoCopies.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.delete() }
    }

    private fun shrink(source: File, target: File): File? =
        runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
                val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
                val scale = minOf(1f, 1024f / maxOf(decoded.width, decoded.height))
                val bitmap =
                    if (scale < 1f) Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true).also { decoded.recycle() }
                    else decoded
                target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
                bitmap.recycle()
                target
            }
            .getOrNull()

    private fun read(): String? = snapshot.takeIf { it.isFile }?.let { f -> runCatching { GZIPInputStream(f.inputStream()).bufferedReader().use { it.readText() } }.getOrNull() }

    /** The snapshot restored from the user's Google account, if this install has one. */
    suspend fun available(): SnapshotInfo? =
        withContext(Dispatchers.IO) {
            val text = read() ?: return@withContext null
            runCatching { backup.peek(text) }.getOrNull()?.let { s ->
                SnapshotInfo(s.profile.firstOrNull()?.name.orEmpty(), s.exportedAt, s.sessions.count { it.status == "COMPLETED" }, s.photos.size)
            }
        }

    /** Restores the snapshot, copying back the photos it carries. */
    suspend fun restore() =
        withContext(Dispatchers.IO) {
            val text = read() ?: throw IllegalStateException("No automatic backup on this phone")
            val copies = photoCopies.list().orEmpty().toSet()
            backup.importJson(text) { name -> name in copies || File(photoDir, name).isFile }
            photoDir.mkdirs()
            copies.forEach { name -> File(photoCopies, name).let { if (!File(photoDir, name).isFile) it.copyTo(File(photoDir, name)) } }
        }

    /** Forgets the snapshot, e.g. after "Delete personal data", so it cannot be offered back. */
    suspend fun clear() = lock.withLock { withContext(Dispatchers.IO) { dir.deleteRecursively() } }

    fun lastWritten(): Long? = snapshot.takeIf { it.isFile }?.lastModified()

    companion object {
        const val PHOTO_BUDGET = 15L * 1024 * 1024
    }
}
