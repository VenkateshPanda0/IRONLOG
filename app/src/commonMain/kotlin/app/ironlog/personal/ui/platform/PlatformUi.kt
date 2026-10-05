package app.ironlog.personal.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.backup.SnapshotInfo
import app.ironlog.personal.domain.ReminderMessage
import app.ironlog.personal.ui.train.CardTemplate
import app.ironlog.personal.ui.train.ShareCardData
import okio.Path

/**
 * What the shared screens need from the phone: pictures, sharing, notifications, the barcode
 * scanner, health-store settings and backups. Android and iOS each provide one implementation.
 */
interface PlatformUi {
    /** "Android" or "iOS", for the few texts that differ. */
    val name: String

    // Images
    /** Decodes a file in app storage, downscaled by [sample] (1 = full size); null if unreadable. */
    fun decodeFile(path: Path, sample: Int = 1): ImageBitmap?

    /** Decodes an image bundled with the app (for example "exercises/<id>/0.webp"). */
    fun decodeAsset(path: String, sample: Int = 1): ImageBitmap?

    /** Opens the photo picker; [onPicked] gets a JPEG scaled to at most 1600 px, or null if cancelled. */
    @Composable
    fun rememberPhotoPicker(onPicked: (ByteArray?) -> Unit): () -> Unit

    // Sharing
    fun renderShareCard(data: ShareCardData, template: CardTemplate): ImageBitmap

    fun shareImage(image: ImageBitmap, fileName: String, title: String)

    // Notifications
    fun canNotify(): Boolean

    /** Asks for permission to post notifications when needed, then runs `then` with the outcome. */
    @Composable
    fun rememberNotificationPermission(): (then: (Boolean) -> Unit) -> Unit

    fun openNotificationSettings()

    fun showNotification(message: ReminderMessage)

    // Barcode scanning
    val canScanBarcode: Boolean

    /** Scans a product barcode; [onResult] gets the digits, [onError] a message for the user. */
    fun scanBarcode(onResult: (String) -> Unit, onError: (String) -> Unit)

    // Health store and backups
    /** The health-store section in Settings (Health Connect on Android). */
    @Composable
    fun HealthSection(c: AppContainer)

    /** How automatic backup and moving to a new phone work on this platform. */
    val backupHelp: String

    fun lastSnapshotMillis(): Long?

    /** A backup offered on a new phone's first screen, if any. */
    suspend fun availableSnapshot(): SnapshotInfo?

    suspend fun restoreSnapshot()

    /** Whether "Export everything" and "Import a backup" are available. */
    val canExportImport: Boolean
}

val LocalPlatform = staticCompositionLocalOf<PlatformUi> { error("No PlatformUi provided") }

/** The system back gesture or button; a no-op where the platform has none (iOS uses on-screen back). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
