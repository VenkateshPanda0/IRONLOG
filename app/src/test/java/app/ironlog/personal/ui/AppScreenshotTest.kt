package app.ironlog.personal.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
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

        // Log an earlier session with today's exercises so hints and PRs have history.
        runBlocking {
            val active = container.programs.active.first()!!
            val day = container.programs.nextDay(active.programId)
            val rows = container.programs.prescriptions(day.id).first()
            val names = container.workouts.exercises.first().associate { it.id to it.name }
            val earlier = container.workouts.start("Earlier session", rows.map { Triple(it.exerciseId, names.getValue(it.exerciseId), 2) })
            container.dao().sessionSets(earlier).first().forEach { container.workouts.completeWithFallback(it.id, 60.0, 8) }
            container.workouts.finish(earlier)
        }
        tap("Start workout")
        compose.waitUntilAtLeastOneExists(hasText("Last time", substring = true), 10_000)
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("70")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        Thread.sleep(500)
        compose.onAllNodesWithContentDescription("Complete set")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Mark set not done").fetchSemanticsNodes().size == 1 }
        compose.onAllNodesWithContentDescription("Complete set")[0].performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Mark set not done").fetchSemanticsNodes().size == 2 }
        compose.waitUntilAtLeastOneExists(hasText("REST"), 10_000)
        shot("09_active_workout")
        compose.onAllNodes(hasText("1"))[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add drop set after"), 5_000)
        shot("10_set_menu")
        tap("Add drop set after")
        compose.waitUntilAtLeastOneExists(hasText("D"), 5_000)
        shot("10_drop_set_added")
        tap("Finish")
        compose.waitUntilAtLeastOneExists(hasText("Finish workout?"), 5_000)
        compose.onAllNodes(hasText("FINISH"))[1].performClick()
        compose.waitUntilAtLeastOneExists(hasText("Workout complete", ignoreCase = true), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Personal bests", ignoreCase = true), 10_000)
        shot("11_summary")
        compose.onRoot().performTouchInput { swipeUp() }
        compose.onRoot().performTouchInput { swipeUp() }
        shot("12_summary_share")
        tap("Done")

        tap("Train")
        compose.waitUntilAtLeastOneExists(hasText("All programs", ignoreCase = true), 10_000)
        shot("02_train")

        tap("Library")
        compose.waitUntilAtLeastOneExists(hasText("results", substring = true), 10_000)
        shot("07_library")
        compose.onNode(hasSetTextAction()).performTextInput("full squat")
        compose.waitUntilAtLeastOneExists(hasText("Barbell Full Squat"), 10_000)
        shot("07_library_search")
        tap("Barbell Full Squat")
        compose.waitUntilAtLeastOneExists(hasText("How to", ignoreCase = true), 10_000)
        shot("08_exercise_detail")
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

        tap("Nutrition")
        shot("03_nutrition")

        tap("Progress")
        shot("04_progress")
    }
}
