package app.ironlog.personal.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
    @get:Rule val compose = androidx.compose.ui.test.junit4.createEmptyComposeRule()

    private lateinit var scenario: androidx.test.core.app.ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    private val container
        get() = androidx.test.core.app.ApplicationProvider.getApplicationContext<IronlogApp>().container

    private fun back() = scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screens/$name.png")
    }

    private fun tap(text: String) {
        // Lazy rows only compose what is visible; scroll them until the target exists.
        if (compose.onAllNodes(hasText(text, ignoreCase = true)).fetchSemanticsNodes().isEmpty()) {
            val lists = compose.onAllNodes(androidx.compose.ui.test.hasScrollToNodeAction())
            for (i in 0 until lists.fetchSemanticsNodes().size) {
                if (runCatching { lists[i].performScrollToNode(hasText(text, ignoreCase = true)) }.isSuccess) break
            }
        }
        compose.waitUntilAtLeastOneExists(hasText(text, ignoreCase = true), 10_000)
        val node = compose.onAllNodes(hasText(text, ignoreCase = true))[0]
        // Bring off-screen targets into view; nodes outside a scrollable parent are left as is.
        runCatching { node.performScrollTo() }
        node.performClick()
        compose.waitForIdle()
    }

    private fun inDialog(block: () -> Unit) {
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            block()
            compose.mainClock.advanceTimeBy(1_000)
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun type(label: String, value: String) {
        compose.onNode(hasText(label) and hasSetTextAction()).performTextInput(value)
        compose.waitForIdle()
    }

    @Test
    fun walkMainScreens() {
        // Let the bundled data finish seeding before the first screen is composed.
        val deadline = System.currentTimeMillis() + 120_000
        while (container.seedState.value !is SeedState.Ready) {
            check(System.currentTimeMillis() < deadline) { "Seeding did not finish: ${container.seedState.value}" }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            Thread.sleep(100)
        }
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        scenario.onActivity { activity = it }
        compose.waitUntilAtLeastOneExists(hasText("IRONLOG"), 30_000)
        type("What should we call you?", "Alex Doe")
        shot("00_onboarding_welcome")
        tap("Continue")
        type("Age", "29")
        // US locale: height starts in feet and inches; switching to cm converts what was typed.
        type("Height (ft'in\")", "5'11")
        tap("CM")
        compose.waitUntilAtLeastOneExists(hasText("Height (cm)"), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("180.3"), 10_000)
        // Robolectric runs in a US locale, where pounds are the default; this walk uses kg.
        compose.waitUntilAtLeastOneExists(hasText("Weight (lb)"), 10_000)
        tap("KG")
        compose.waitUntilAtLeastOneExists(hasText("Weight (kg)"), 10_000)
        type("Weight (kg)", "82")
        tap("Male")
        shot("00_onboarding_body")
        tap("Continue")
        shot("00_onboarding_goal")
        tap("Continue")
        compose.waitUntilAtLeastOneExists(hasText("Your goal look", ignoreCase = true), 10_000)
        tap("Classic")
        compose.waitUntilAtLeastOneExists(hasText("extra volume", substring = true), 10_000)
        shot("00_onboarding_physique")
        tap("Continue")
        shot("00_onboarding_training")
        tap("Continue")
        compose.waitUntilAtLeastOneExists(hasText("kcal", substring = true), 10_000)
        shot("00_onboarding_plan")
        tap("Start training")

        // First run: the app tour over the real screens.
        compose.waitUntilAtLeastOneExists(hasText("Take the tour", ignoreCase = true), 20_000)
        shot("01_tour_welcome")
        tap("Take the tour")
        compose.waitUntilAtLeastOneExists(hasText("Home is your day", ignoreCase = true), 10_000)
        tap("Next")
        compose.waitUntilAtLeastOneExists(hasText("Start a workout", ignoreCase = true), 10_000)
        shot("01_tour_start_workout")
        tap("Next")
        compose.waitUntilAtLeastOneExists(hasText("All programs", ignoreCase = true), 10_000) // the tour switched to Train
        shot("01_tour_train")
        repeat(4) { tap("Next") } // Library, Nutrition, Progress, then back to Home for the profile
        compose.waitUntilAtLeastOneExists(hasText("You and your settings", ignoreCase = true), 10_000)
        shot("01_tour_profile")
        tap("Next")
        tap("Let's go")
        compose.waitUntilDoesNotExist(hasText("Take the tour", ignoreCase = true), 10_000)

        compose.waitUntilAtLeastOneExists(hasText("Good", substring = true, ignoreCase = true), 20_000)
        // The next workout card must show the real prescription count, not the empty default.
        compose.waitUntilDoesNotExist(hasText("0 exercises · 0 sets", substring = true), 10_000)
        shot("01_home")
        tap("Turn on")
        compose.waitUntilDoesNotExist(hasText("Never miss a training day", ignoreCase = true), 10_000)

        // Daily section: check in, water, habits.
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitUntilAtLeastOneExists(hasText("Morning check-in", ignoreCase = true), 10_000)
        tap("+500")
        compose.waitUntilAtLeastOneExists(hasText("0.5 L"), 10_000)
        shot("01_home_daily")
        // The check-in dialog's taps are covered by CheckInDialogTest; record it directly here.
        runBlocking {
            container.wellness.setSleep(java.time.LocalDate.now(), 7.5, 4)
            container.wellness.checkIn(java.time.LocalDate.now(), 4, 2, 2, 4)
        }
        compose.waitUntilAtLeastOneExists(hasText("Slept", substring = true), 10_000)
        tap("Add habits")
        compose.waitUntilAtLeastOneExists(hasText("+ Creatine"), 10_000)
        tap("+ Creatine")
        tap("+ Multivitamin")
        compose.waitUntilAtLeastOneExists(hasText("CREATINE"), 10_000)
        shot("14_habits")
        back()
        compose.waitUntilAtLeastOneExists(hasText("Creatine"), 10_000)
        tap("Creatine")
        compose.waitUntilAtLeastOneExists(hasText("1 day streak"), 10_000)
        shot("01_home_daily_done")
        tap("Log cardio")
        compose.waitUntilAtLeastOneExists(hasText("Log session", ignoreCase = true), 10_000)
        type("Duration (minutes)", "26")
        type("Distance (km)", "5")
        shot("15_cardio")
        tap("Save Run")
        compose.waitUntilAtLeastOneExists(hasText("Run · 26 min", substring = true), 10_000)
        back()
        compose.onRoot().performTouchInput { swipeDown() }
        compose.onRoot().performTouchInput { swipeDown() }

        tap("View program")
        compose.waitUntilAtLeastOneExists(hasText("Schedule", ignoreCase = true), 10_000)
        shot("05_program_detail")
        back()

        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Achievements", substring = true, ignoreCase = true), 10_000)
        shot("06_profile_new")
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Backup and new phone", ignoreCase = true), 10_000)
        shot("06_settings")
        compose.onNode(hasText("Streak saver", ignoreCase = true)).performScrollTo()
        compose.waitUntilAtLeastOneExists(hasText("18:00"), 10_000)
        compose.onAllNodesWithContentDescription("15 minutes later")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("18:15"), 10_000)
        shot("06_settings_reminders")
        compose.onNode(hasText("How Ironlog uses health data", ignoreCase = true)).performScrollTo()
        shot("06_settings_health")
        // Health Connect is not installed under Robolectric; a fake source stands in for it.
        container.healthSource =
            object : app.ironlog.personal.data.health.HealthSource {
                override suspend fun read(from: java.time.LocalDate, to: java.time.LocalDate) =
                    listOf(app.ironlog.personal.data.health.HealthDay(to, 8421, 6.9)).filter { it.date in from..to }

                override suspend fun granted() = app.ironlog.personal.data.health.HealthAccess.entries.toSet()

                override suspend fun readSessions(from: java.time.Instant, to: java.time.Instant, withDistance: Boolean) =
                    java.time.LocalDate.now().atTime(6, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().let { start ->
                        listOf(app.ironlog.personal.data.health.HealthSession("watch-1", start, start.plusSeconds(28 * 60), app.ironlog.personal.domain.CardioType.RUN, "Watch", 5.2))
                    }
            }
        runBlocking {
            container.setHealthSync(true)
            container.syncHealth()
        }
        back()
        back()
        // Synced steps show their source; the sleep typed in earlier is kept.
        compose.onRoot().performTouchInput { swipeUp() }
        compose.waitUntilAtLeastOneExists(hasText("8,421"), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Health Connect", substring = true), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Slept 7.5 h"), 10_000)
        shot("01_home_health_connect")
        compose.onRoot().performTouchInput { swipeDown() }
        compose.onRoot().performTouchInput { swipeDown() }

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
        compose.onAllNodesWithContentDescription("Show demo")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("START"), 5_000)
        shot("09_workout_demo")
        compose.onAllNodesWithContentDescription("Hide demo")[0].performClick()
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
        // Coach, warm-ups, plates and supersets.
        compose.waitUntilAtLeastOneExists(hasText("COACH", substring = true), 5_000)
        compose.onAllNodesWithContentDescription("Exercise options")[0].performClick()
        tap("Add warm-up sets")
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("W")).fetchSemanticsNodes().size >= 3 }
        compose.onRoot().performTouchInput { swipeDown() }
        shot("10_warmups_coach")
        compose.onAllNodesWithContentDescription("Exercise options")[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("Plate calculator"), 5_000)
        compose.onNode(hasText("Plate calculator")).performClick()
        inDialog {
            com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/10_plates.png")
            compose.onNode(hasText("DONE")).performClick()
        }
        compose.onAllNodesWithContentDescription("Exercise options")[0].performClick()
        tap("Superset with next")
        compose.waitUntilAtLeastOneExists(hasText("SUPERSET A", substring = true), 5_000)
        compose.onNode(hasText("SUPERSET A", substring = true)).performScrollTo()
        shot("10_superset")
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
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Achievements", substring = true, ignoreCase = true), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("First Rep", ignoreCase = true), 10_000)
        shot("13_profile")
        compose.onRoot().performTouchInput { swipeUp() }
        shot("13_profile_medals")
        back()

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
        compose.waitUntilAtLeastOneExists(hasText("START"), 10_000)
        shot("08_exercise_detail")
        compose.mainClock.advanceTimeBy(1_500)
        compose.waitUntilAtLeastOneExists(hasText("END"), 10_000)
        shot("08_exercise_detail_end")
        // The animated figure is one tap away (compose tests hold it at its start pose).
        tap("Motion")
        compose.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasContentDescription("Barbell Full Squat animated demonstration"), 10_000)
        shot("08_exercise_detail_motion")
        back()

        tap("Nutrition")
        compose.waitUntilAtLeastOneExists(hasText("Breakfast", ignoreCase = true), 10_000)
        shot("03_nutrition_empty")
        compose.onNodeWithContentDescription("Add food to Breakfast").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Add to Breakfast", ignoreCase = true), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("Popular in India", ignoreCase = true), 10_000)
        shot("03_food_popular")
        compose.onNode(hasSetTextAction()).performTextInput("oats dry")
        compose.waitUntilAtLeastOneExists(hasText("Cereals, oats", substring = true), 10_000)
        shot("03_food_search")
        compose.onAllNodes(hasText("Cereals, oats", substring = true))[0].performClick()
        // Dialog windows keep requesting frames under Robolectric (verified: no recomposition
        // loop), so dialog steps advance the clock by hand instead of waiting for idle.
        inDialog {
            com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/03_portion.png")
            compose.onNode(hasText("ADD")).performClick()
        }
        compose.waitUntilAtLeastOneExists(hasText("remaining", substring = true), 10_000)
        shot("03_nutrition")
        compose.onNodeWithContentDescription("Edit targets").performClick()
        inDialog {
            com.github.takahirom.roborazzi.captureScreenRoboImage("build/screens/03_targets.png")
            compose.onNode(hasText("CANCEL")).performClick()
        }

        // Test-only sample weigh-ins: a gentle downward trend over six weeks.
        runBlocking {
            val today = java.time.LocalDate.now()
            for (day in 42 downTo 1 step 2) container.body.log(today.minusDays(day.toLong()), 84.0 - (42 - day) * 0.05 + (day % 3) * 0.2)
            listOf(android.graphics.Color.DKGRAY, android.graphics.Color.GRAY).forEachIndexed { i, color ->
                val file = java.io.File(activity.cacheDir, "sample$i.png")
                val bitmap = android.graphics.Bitmap.createBitmap(300, 400, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                container.body.addPhoto(activity.contentResolver, android.net.Uri.fromFile(file), today.minusDays(30L * (1 - i)))
            }
        }
        tap("Progress")
        compose.waitUntilAtLeastOneExists(hasText("7-day avg", ignoreCase = true), 10_000)
        shot("04_progress_weight")
        tap("Strength")
        compose.waitUntilAtLeastOneExists(hasText("Top lifts", ignoreCase = true), 10_000)
        shot("04_progress_strength")
        tap("Volume")
        compose.waitUntilAtLeastOneExists(hasText("Sets per muscle", substring = true, ignoreCase = true), 10_000)
        shot("04_progress_volume")
        tap("Photos")
        compose.waitUntilAtLeastOneExists(hasText("First vs latest", ignoreCase = true), 10_000)
        shot("04_progress_photos")
        tap("Cardio")
        compose.waitUntilAtLeastOneExists(hasText("By type", ignoreCase = true), 10_000)
        shot("04_progress_cardio")
        tap("Daily")
        compose.waitUntilAtLeastOneExists(hasText("Water · 14 days", ignoreCase = true), 10_000)
        shot("04_progress_daily")
        tap("Body")
        compose.waitUntilAtLeastOneExists(hasText("Log measurements", substring = true, ignoreCase = true), 10_000)
        type("Waist (cm)", "84")
        type("Arm (cm)", "38")
        tap("Save measurements")
        compose.waitUntilAtLeastOneExists(hasText("Waist · 84 cm"), 10_000)
        shot("04_progress_body")
    

        // Physique check by tape measure. Measurements typed in earlier (waist 84) prefill the form.
        tap("Photos")
        tap("Physique check")
        compose.waitUntilAtLeastOneExists(hasText("How to measure", ignoreCase = true), 10_000)
        shot("16_physique")
        type("Shoulders (cm)", "118")
        type("Hips (cm)", "98")
        type("Thigh (cm)", "58")
        compose.waitUntilAtLeastOneExists(hasText("match to Classic", substring = true), 10_000)
        compose.onNode(hasText("match to Classic", substring = true)).performScrollTo()
        shot("16_physique_report")
        compose.onRoot().performTouchInput { swipeUp() }
        shot("16_physique_findings")
        tap("Save this check")
        compose.waitUntilAtLeastOneExists(hasText("History", ignoreCase = true), 10_000)
        compose.onNode(hasText("History", ignoreCase = true)).performScrollTo()
        shot("16_physique_history")
        tap("Build a program with this focus")
        compose.waitUntilAtLeastOneExists(hasText("Classic focus", ignoreCase = true), 10_000)
        compose.onRoot().performTouchInput { swipeUp() }
        shot("17_builder_focus")
        back()
        back()

        // Switching to pounds changes every weight shown, without touching the stored kilograms.
        val storedBefore = runBlocking { container.body.weights.first().map { it.weightKg } to container.dao().allLoggedSets().first().map { it.weightKg } }
        runBlocking { container.setWeightUnit(app.ironlog.personal.domain.WeightUnit.LB) }
        tap("Weight")
        compose.waitUntilAtLeastOneExists(hasText("Weight today (lb)"), 10_000)
        compose.waitUntilAtLeastOneExists(hasText("lb change", substring = true), 10_000)
        shot("04_progress_weight_lb")
        tap("Strength")
        compose.waitUntilAtLeastOneExists(hasText("lb", substring = true), 10_000)
        shot("04_progress_strength_lb")
        val storedAfter = runBlocking { container.body.weights.first().map { it.weightKg } to container.dao().allLoggedSets().first().map { it.weightKg } }
        org.junit.Assert.assertEquals(storedBefore, storedAfter)

        // Inches and miles: measurements, cardio distance and pace follow; stored cm and km do not change.
        val lengthsBefore = runBlocking { container.wellness.measurements.first().map { it.waistCm } to container.wellness.cardio.first().map { it.distanceKm } }
        runBlocking { container.setLengthUnit(app.ironlog.personal.domain.LengthUnit.IN) }
        tap("Body")
        compose.waitUntilAtLeastOneExists(hasText("Waist · 33.1 in"), 10_000)
        shot("04_progress_body_in")
        tap("Cardio")
        compose.waitUntilAtLeastOneExists(hasText("/mi", substring = true), 10_000)
        shot("04_progress_cardio_mi")
        val lengthsAfter = runBlocking { container.wellness.measurements.first().map { it.waistCm } to container.wellness.cardio.first().map { it.distanceKm } }
        org.junit.Assert.assertEquals(lengthsBefore, lengthsAfter)
    
        // The tour can be replayed from Settings and skipped at any step.
        tap("Home")
        compose.onNodeWithContentDescription("Profile and settings").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Replay app tour"), 10_000)
        tap("Replay app tour")
        compose.waitUntilAtLeastOneExists(hasText("Take the tour", ignoreCase = true), 10_000)
        tap("Skip tour")
        compose.waitUntilDoesNotExist(hasText("Take the tour", ignoreCase = true), 10_000)
        org.junit.Assert.assertEquals(false, runBlocking { container.tourPending.first() })
    }
}
