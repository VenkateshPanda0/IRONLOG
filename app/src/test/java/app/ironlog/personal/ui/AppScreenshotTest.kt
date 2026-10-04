package app.ironlog.personal.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.MainActivity
import app.ironlog.personal.SeedState
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Whole-app smoke test: boots the real activity, seeds the bundled data and walks the main
 * screens, saving a screenshot of each to app/build/screens for visual review.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class AppScreenshotTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private val container
        get() = (compose.activity.application as IronlogApp).container

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun tap(text: String) {
        compose.waitUntilAtLeastOneExists(hasText(text, ignoreCase = true), 10_000)
        compose.onAllNodes(hasText(text, ignoreCase = true))[0].performClick()
        compose.waitForIdle()
    }

    private fun type(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performTextInput(value)
        compose.waitForIdle()
    }

    @Test
    fun walkMainScreens() {
        compose.waitUntil(120_000) { container.seedState.value is SeedState.Ready }
        compose.waitUntilAtLeastOneExists(hasText("IRONLOG"), 20_000)
        type("What should we call you?", "Alex Doe")
        shot("00_onboarding_welcome")
        tap("Continue")
        type("Age", "29")
        type("Height (cm)", "180")
        type("Weight (kg)", "82")
        shot("00_onboarding_body")
        tap("Continue")
        shot("00_onboarding_goal")
        tap("Continue")
        shot("00_onboarding_training")
        tap("Continue")
        compose.waitUntilAtLeastOneExists(hasText("kcal", substring = true), 10_000)
        shot("00_onboarding_plan")
        tap("Start training")

        compose.waitUntilAtLeastOneExists(hasText("Good", substring = true, ignoreCase = true), 20_000)
        // The next workout card must show the real prescription count, not the empty default.
        compose.waitUntilDoesNotExist(hasText("0 exercises · 0 sets", substring = true), 10_000)
        shot("01_home")

        tap("View program")
        compose.waitUntilAtLeastOneExists(hasText("Schedule", ignoreCase = true), 10_000)
        shot("05_program_detail")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Settings", ignoreCase = true), 10_000)
        shot("06_settings")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        tap("Train")
        compose.waitUntilAtLeastOneExists(hasText("All programs", ignoreCase = true), 10_000)
        shot("02_train")

        tap("Nutrition")
        shot("03_nutrition")

        tap("Progress")
        shot("04_progress")
    }
}
