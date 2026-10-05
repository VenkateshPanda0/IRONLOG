package app.ironlog.personal.platform

import android.content.Context

/** Reads files from the APK's assets folder. */
class AndroidAssets(context: Context) : AssetReader {
    private val assets = context.applicationContext.assets

    override fun readBytes(path: String): ByteArray = assets.open(path).use { it.readBytes() }
}
