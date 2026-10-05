package app.ironlog.personal.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import app.ironlog.personal.AndroidAppContainer
import app.ironlog.personal.ui.platform.AndroidPlatformUi
import app.ironlog.personal.ui.platform.LocalPlatform
import app.ironlog.personal.ui.theme.IronlogTheme

/** The theme plus the Android platform services, as MainActivity provides them, for screen tests. */
@Composable
fun WithPlatform(c: AndroidAppContainer, content: @Composable () -> Unit) {
    val activity = LocalContext.current as Activity
    // Stateless and cheap, so a new one per composition is fine in tests.
    val platform = AndroidPlatformUi(activity, c)
    CompositionLocalProvider(LocalPlatform provides platform) { IronlogTheme { content() } }
}
