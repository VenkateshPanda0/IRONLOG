package app.ironlog.personal.data.repo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.data.db.PhysiqueScanEntity
import app.ironlog.personal.domain.Analysis
import app.ironlog.personal.domain.BodyMask
import app.ironlog.personal.domain.BodyProportions
import app.ironlog.personal.domain.Joint
import app.ironlog.personal.domain.PhysiqueAnalyzer
import app.ironlog.personal.domain.PhysiqueType
import app.ironlog.personal.domain.Point
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/** Finds the person in a photo: a body mask plus joint positions in the same pixel space. */
fun interface BodyDetector {
    suspend fun detect(bitmap: Bitmap): Pair<BodyMask, Map<Joint, Point>>
}

/** ML Kit pose detection and selfie segmentation, both running on the device with bundled models. */
class MlKitBodyDetector : BodyDetector {
    private val landmarks =
        mapOf(
            PoseLandmark.NOSE to Joint.NOSE,
            PoseLandmark.LEFT_SHOULDER to Joint.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER to Joint.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW to Joint.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to Joint.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST to Joint.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST to Joint.RIGHT_WRIST,
            PoseLandmark.LEFT_HIP to Joint.LEFT_HIP,
            PoseLandmark.RIGHT_HIP to Joint.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE to Joint.LEFT_KNEE,
            PoseLandmark.RIGHT_KNEE to Joint.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE to Joint.LEFT_ANKLE,
            PoseLandmark.RIGHT_ANKLE to Joint.RIGHT_ANKLE,
        )

    override suspend fun detect(bitmap: Bitmap): Pair<BodyMask, Map<Joint, Point>> {
        val image = InputImage.fromBitmap(bitmap, 0)
        val poseDetector =
            PoseDetection.getClient(AccuratePoseDetectorOptions.Builder().setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE).build())
        val segmenter = Segmentation.getClient(SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE).build())
        try {
            val pose = poseDetector.process(image).await()
            val joints =
                pose.allPoseLandmarks.mapNotNull { l ->
                    landmarks[l.landmarkType]?.let { it to Point(l.position.x, l.position.y, l.inFrameLikelihood) }
                }.toMap()
            val segmentation = segmenter.process(image).await()
            val buffer = segmentation.buffer.apply { rewind() }
            val w = segmentation.width
            val h = segmentation.height
            val pixels = BooleanArray(w * h) { buffer.float >= 0.5f }
            val mask = BodyMask(w, h, pixels)
            // The mask matches the input size, so joints need no rescaling.
            return mask to joints
        } finally {
            poseDetector.close()
            segmenter.close()
        }
    }
}

private suspend fun <T> Task<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

/** Result of checking one photo; [photo] is kept in app storage only when the scan is saved. */
data class PhysiqueCheck(val photo: File, val analysis: Analysis)

class PhysiqueRepository(private val dao: IronlogDao, private val dir: File) {
    /** Replaced in tests, where ML Kit models are not available. */
    var detector: BodyDetector = MlKitBodyDetector()

    val scans: Flow<List<PhysiqueScanEntity>> = dao.physiqueScans()

    fun photoFile(scan: PhysiqueScanEntity) = File(dir, scan.fileName)

    suspend fun setGoal(type: PhysiqueType) = dao.setPhysiqueGoal(type.name)

    /** Decodes the picked photo upright at about 1024 px, stores a copy and measures it. */
    suspend fun check(resolver: ContentResolver, uri: Uri): PhysiqueCheck {
        val bitmap = withContext(Dispatchers.IO) { decodeUpright(resolver, uri, 1024) }
        val file =
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                File(dir, "scan_${java.util.UUID.randomUUID()}.jpg").also { f ->
                    f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                }
            }
        val (mask, joints) = detector.detect(bitmap)
        bitmap.recycle()
        val analysis = withContext(Dispatchers.Default) { PhysiqueAnalyzer.analyze(mask, joints) }
        return PhysiqueCheck(file, analysis)
    }

    suspend fun save(check: PhysiqueCheck, p: BodyProportions, goal: PhysiqueType, match: Int, date: LocalDate = LocalDate.now()) =
        dao.addPhysiqueScan(
            PhysiqueScanEntity(
                date = date.toString(),
                fileName = check.photo.name,
                goal = goal.name,
                shoulder = p.shoulderWidth,
                waist = p.waistWidth,
                hip = p.hipWidth,
                leftThigh = p.leftThighWidth,
                rightThigh = p.rightThighWidth,
                height = p.bodyHeight,
                legToTorso = p.legToTorso,
                matchScore = match,
            )
        )

    /** Removes the photo of a check that was not saved. */
    suspend fun discard(check: PhysiqueCheck) = withContext(Dispatchers.IO) { check.photo.delete() }

    suspend fun delete(scan: PhysiqueScanEntity) {
        dao.deletePhysiqueScan(scan.id)
        withContext(Dispatchers.IO) { photoFile(scan).delete() }
    }

    suspend fun deleteAll() {
        dao.deleteAllPhysiqueScans()
        withContext(Dispatchers.IO) { dir.deleteRecursively() }
    }

    companion object {
        fun proportions(scan: PhysiqueScanEntity) =
            BodyProportions(scan.shoulder, scan.waist, scan.hip, scan.leftThigh, scan.rightThigh, scan.height, scan.legToTorso)

        /** Gallery photos often carry their rotation in EXIF instead of in the pixels. */
        internal fun decodeUpright(resolver: ContentResolver, uri: Uri, maxEdge: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
            val raw =
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
                    ?: error("Could not read the selected image")
            val rotation =
                runCatching {
                    resolver.openInputStream(uri)?.use {
                        when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                }.getOrNull() ?: 0f
            val scale = minOf(1f, maxEdge.toFloat() / maxOf(raw.width, raw.height))
            if (rotation == 0f && scale == 1f) return raw
            val matrix = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { if (it !== raw) raw.recycle() }
        }
    }
}
