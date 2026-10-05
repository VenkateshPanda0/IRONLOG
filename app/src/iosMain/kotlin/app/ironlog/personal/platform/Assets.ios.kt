package app.ironlog.personal.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy

/** Reads files from the "assets" folder inside the app bundle. */
class IosAssets : AssetReader {
    override fun readBytes(path: String): ByteArray {
        require(".." !in path) { "Bad asset path" }
        val root = NSBundle.mainBundle.resourcePath ?: error("No app bundle")
        val data = NSData.dataWithContentsOfFile("$root/assets/$path") ?: error("Missing asset $path")
        return data.toByteArray()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}
