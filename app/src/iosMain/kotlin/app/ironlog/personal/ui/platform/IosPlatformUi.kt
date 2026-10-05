package app.ironlog.personal.ui.platform

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.backup.SnapshotInfo
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.platform.AssetReader
import app.ironlog.personal.platform.appFileSystem
import app.ironlog.personal.platform.toByteArray
import app.ironlog.personal.platform.toNSData
import app.ironlog.personal.ui.components.SectionHeader
import app.ironlog.personal.ui.train.CardTemplate
import app.ironlog.personal.ui.train.ShareCardData
import app.ironlog.personal.ui.train.renderShareCardSkia
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import okio.Path
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Photos.PHPhotoLibrary
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.CGRectMake

@OptIn(ExperimentalForeignApi::class)
class IosPlatformUi(private val assets: AssetReader) : PlatformUi {
    override val name = "iOS"

    private val notifications get() = UNUserNotificationCenter.currentNotificationCenter()

    // Notification permission is only known asynchronously on iOS; keep the last answer.
    private var notificationsAllowed = false

    init {
        refreshNotificationPermission()
    }

    private fun refreshNotificationPermission(then: (() -> Unit)? = null) {
        notifications.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            notificationsAllowed = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
            then?.let { onMain(it) }
        }
    }

    // Images
    override fun decodeFile(path: Path, sample: Int): ImageBitmap? =
        runCatching { decode(appFileSystem.read(path) { readByteArray() }, sample) }.getOrNull()

    override fun decodeAsset(path: String, sample: Int): ImageBitmap? = runCatching { decode(assets.readBytes(path), sample) }.getOrNull()

    private fun decode(bytes: ByteArray, sample: Int): ImageBitmap {
        val image = Image.makeFromEncoded(bytes)
        if (sample <= 1) return image.toComposeImageBitmap()
        val w = (image.width / sample).coerceAtLeast(1)
        val h = (image.height / sample).coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(w, h)
        surface.canvas.drawImageRect(image, Rect.makeWH(w.toFloat(), h.toFloat()))
        return surface.makeImageSnapshot().toComposeImageBitmap()
    }

    @Composable
    override fun rememberPhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit {
        val picker = remember { PhotoPicker() }
        picker.onPicked = onPicked
        return { picker.open() }
    }

    // Sharing
    override fun renderShareCard(data: ShareCardData, template: CardTemplate): ImageBitmap = renderShareCardSkia(data, template)

    override fun shareImage(image: ImageBitmap, fileName: String, title: String) {
        val png = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)?.bytes ?: return
        val uiImage = UIImage.imageWithData(png.toNSData()) ?: return
        val sheet = UIActivityViewController(activityItems = listOf(uiImage), applicationActivities = null)
        val top = topViewController() ?: return
        // On iPad the sheet is a popover and needs an anchor.
        sheet.popoverPresentationController?.sourceView = top.view
        top.presentViewController(sheet, animated = true, completion = null)
    }

    // Notifications
    override fun canNotify(): Boolean = notificationsAllowed

    @Composable
    override fun rememberNotificationPermission(): (then: (Boolean) -> Unit) -> Unit = { then ->
        notifications.requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge) { granted, _ ->
            notificationsAllowed = granted
            onMain { then(granted) }
        }
    }

    override fun openNotificationSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    override fun showNotification(message: ReminderMessage) {
        val content =
            UNMutableNotificationContent().apply {
                setTitle(message.title)
                setBody(message.text)
                setSound(UNNotificationSound.defaultSound)
            }
        notifications.addNotificationRequest(UNNotificationRequest.requestWithIdentifier("test-${message.kind}", content, null), withCompletionHandler = null)
    }

    // Barcode scanning: type the barcode into search on iPhone for now.
    override val canScanBarcode = false

    override fun scanBarcode(onResult: (String) -> Unit, onError: (String) -> Unit) =
        onError("Type the barcode into search instead.")

    @Composable
    override fun HealthSection(c: AppContainer) {
        SectionHeader("Apple Health")
        Text(
            "Syncing with Apple Health is not available on iPhone yet. Steps, sleep and weight can be logged in Ironlog directly.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    override val backupHelp =
        "Automatic: your iPhone's iCloud backup (or a backup on your computer) includes Ironlog. " +
            "When you set up a new iPhone from that backup, Ironlog comes back with all your data and photos."

    override fun lastSnapshotMillis(): Long? = null

    override suspend fun availableSnapshot(): SnapshotInfo? = null

    override suspend fun restoreSnapshot() = Unit

    override val canExportImport = false
}

private fun onMain(action: () -> Unit) = dispatch_async(dispatch_get_main_queue()) { action() }

internal fun topViewController(): UIViewController? {
    var top = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

/** Apple's photo picker (no photo-library permission needed); returns a JPEG of at most 1600 px. */
@OptIn(ExperimentalForeignApi::class)
private class PhotoPicker : NSObject(), PHPickerViewControllerDelegateProtocol {
    var onPicked: (ByteArray?) -> Unit = {}

    fun open() {
        val config = PHPickerConfiguration(PHPhotoLibrary.sharedPhotoLibrary()).apply {
            setFilter(PHPickerFilter.imagesFilter)
            setSelectionLimit(1)
        }
        val picker = PHPickerViewController(config)
        picker.delegate = this
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }

    override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val result = didFinishPicking.firstOrNull() as? PHPickerResult
        if (result == null) {
            onPicked(null)
            return
        }
        result.itemProvider.loadDataRepresentationForTypeIdentifier("public.image") { data: NSData?, _ ->
            val jpeg = data?.let { scaledJpeg(it) }
            onMain { onPicked(jpeg) }
        }
    }

    private fun scaledJpeg(data: NSData): ByteArray? {
        val image = UIImage.imageWithData(data) ?: return null
        val (w, h) = image.size.useContents { width to height }
        val scale = minOf(1.0, 1600.0 / maxOf(w, h))
        val target = CGSizeMake(w * scale, h * scale)
        val renderer = UIGraphicsImageRenderer(target)
        val scaled = renderer.imageWithActions { _ -> image.drawInRect(CGRectMake(0.0, 0.0, w * scale, h * scale)) }
        return UIImageJPEGRepresentation(scaled, 0.88)?.toByteArray()
    }
}
