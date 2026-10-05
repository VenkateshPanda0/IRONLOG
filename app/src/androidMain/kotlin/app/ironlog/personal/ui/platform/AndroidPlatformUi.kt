package app.ironlog.personal.ui.platform

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import app.ironlog.personal.AndroidAppContainer
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.backup.SnapshotInfo
import app.ironlog.personal.data.repo.scaledJpeg
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.reminders.ReminderScheduler
import app.ironlog.personal.ui.health.HealthConnectSection
import app.ironlog.personal.ui.train.CardTemplate
import app.ironlog.personal.ui.train.ShareCardData
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path

class AndroidPlatformUi(private val activity: Activity, private val container: AndroidAppContainer) : PlatformUi {
    override val name = "Android"

    override fun decodeFile(path: Path, sample: Int): ImageBitmap? =
        BitmapFactory.decodeFile(path.toString(), BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()

    override fun decodeAsset(path: String, sample: Int): ImageBitmap? =
        runCatching {
                activity.assets.open(path).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            }
            .getOrNull()
            ?.asImageBitmap()

    @Composable
    override fun rememberPhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
        val scope = rememberCoroutineScope()
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
                if (uri == null) onPicked(null)
                else scope.launch { onPicked(runCatching { withContext(Dispatchers.IO) { scaledJpeg(activity.contentResolver, uri) } }.getOrNull()) }
            }
        return { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }

    override fun renderShareCard(data: ShareCardData, template: CardTemplate): ImageBitmap =
        app.ironlog.personal.ui.train.renderShareCard(data, template).asImageBitmap()

    /** Writes the image to app cache and opens the system share sheet. */
    override fun shareImage(image: ImageBitmap, fileName: String, title: String) {
        val dir = File(activity.cacheDir, "shares").apply { mkdirs() }
        val file = File(dir, fileName)
        file.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
        val intent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        activity.startActivity(Intent.createChooser(intent, title))
    }

    override fun canNotify() = ReminderScheduler.canNotify(activity)

    /** Asks for notification permission on Android 13+, then runs `then` with the outcome. */
    @Composable
    override fun rememberNotificationPermission(): (then: (Boolean) -> Unit) -> Unit {
        var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
        val launcher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                pending?.invoke(granted)
                pending = null
            }
        return { then ->
            if (canNotify()) then(true)
            else {
                pending = then
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Denied twice: Android only lets the user change it in system settings.
    override fun openNotificationSettings() {
        activity.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun showNotification(message: ReminderMessage) = ReminderScheduler.notify(activity, message)

    override val canScanBarcode = true

    override fun scanBarcode(onResult: (String) -> Unit, onError: (String) -> Unit) {
        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        GmsBarcodeScanning.getClient(activity, options)
            .startScan()
            .addOnSuccessListener { barcode -> barcode.rawValue?.let(onResult) }
            .addOnFailureListener { onError("The scanner is unavailable on this device. Type the barcode into search instead.") }
    }

    @Composable
    override fun HealthSection(c: AppContainer) = HealthConnectSection(c as AndroidAppContainer)

    override val backupHelp =
        "Automatic: Android backs up Ironlog to your Google account (Settings › Google › Backup on your phone). " +
            "On a new phone signed in to the same account, install Ironlog and tap Restore on the first screen. " +
            "Phone-to-phone transfer during setup copies everything, full-size photos included."

    override fun lastSnapshotMillis(): Long? = container.autoBackup.lastWritten()

    override suspend fun availableSnapshot(): SnapshotInfo? = container.autoBackup.available()

    override suspend fun restoreSnapshot() = container.autoBackup.restore()

    override val canExportImport = true
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
