package app.ironlog.personal.data.repo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate

/** Reads a picked image as a JPEG scaled so the long edge is at most 1600 px. Blocking. */
fun scaledJpeg(resolver: ContentResolver, uri: Uri): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val bitmap =
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: error("Could not read the selected image")
    return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it); bitmap.recycle() }.toByteArray()
}

/**
 * Copies a picked image into app-private storage, scaled so the long edge is at most 1600 px.
 * The original in the user's gallery is not touched.
 */
suspend fun BodyRepository.addPhoto(resolver: ContentResolver, uri: Uri, date: LocalDate) {
    addPhotoJpeg(withContext(Dispatchers.IO) { scaledJpeg(resolver, uri) }, date)
}
