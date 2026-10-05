package app.ironlog.personal.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.SeedState
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.ui.onboarding.OnboardingScreen
import app.ironlog.personal.ui.theme.IronlogTheme
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A new phone where Android restored Ironlog's snapshot: onboarding offers it back in one tap. */
@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NewPhoneRestoreTest {
    @get:Rule val compose = createComposeRule()
    private val app = ApplicationProvider.getApplicationContext<IronlogApp>()
    private val c get() = app.container

    @Test
    fun welcomeBackRestoresEverything() {
        while (c.seedState.value !is SeedState.Ready) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(50)
        }
        runBlocking {
            c.saveProfile(UserProfileEntity(name = "Arjun"))
            repeat(3) { c.workouts.finish(c.workouts.start("Workout ${it + 1}", emptyList())) }
            c.backupIfChanged()
            // Android restores only files/backup onto the new phone's fresh install.
            val dir = File(app.filesDir, "backup")
            val cloud = File(app.cacheDir, "cloud").apply { deleteRecursively() }
            dir.copyRecursively(cloud)
            c.clearPersonalData()
            cloud.copyRecursively(dir, overwrite = true)
        }
        compose.setContent { IronlogTheme { androidx.compose.material3.Surface { OnboardingScreen(c) } } }
        compose.waitUntilAtLeastOneExists(hasText("Welcome back, Arjun", ignoreCase = true), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("3 workouts", substring = true), 10_000)
        compose.onRoot().captureRoboImage("build/screens/00_onboarding_welcome_back.png")
        compose.onNode(hasText("Restore my progress", ignoreCase = true)).performClick()
        compose.waitUntil(10_000) { runBlocking { c.dao().profileOnce() } != null }
        assertEquals("Arjun", runBlocking { c.dao().profileOnce() }!!.name)
    }
}
