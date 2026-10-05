package app.ironlog.personal.ui

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.FoodServingEntity
import app.ironlog.personal.ui.nutrition.PortionDialog
import app.ironlog.personal.ui.theme.IronlogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class PortionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun dialogSettlesWithServings() {
        val c = AppContainer(RuntimeEnvironment.getApplication())
        val food = runBlocking {
            val id = c.nutrition.addCustom("Oats", 379.0, 13.0, 68.0, 7.0)
            c.dao().addServing(FoodServingEntity(foodId = id, label = "1 cup", grams = 81.0))
            FoodEntity(id = id, name = "Oats", kcalPer100g = 379.0, proteinPer100g = 13.0, carbsPer100g = 68.0, fatPer100g = 7.0)
        }
        compose.setContent { IronlogTheme { PortionDialog(c, food, "BREAKFAST", {}, { _, _ -> }) } }
        compose.waitUntilAtLeastOneExists(hasText("1 cup"), 10_000)
        compose.waitForIdle()
    }

    @Test
    fun searchThenOpenPortionSettles() {
        val c = AppContainer(RuntimeEnvironment.getApplication())
        runBlocking {
            val id = c.nutrition.addCustom("Oats, dry", 379.0, 13.0, 68.0, 7.0)
            c.dao().addServing(FoodServingEntity(foodId = id, label = "1 cup", grams = 81.0))
            c.dao().addServing(FoodServingEntity(foodId = id, label = "0.33 cup", grams = 27.0))
        }
        compose.setContent {
            IronlogTheme {
                val controller = androidx.navigation.compose.rememberNavController()
                app.ironlog.personal.ui.nutrition.FoodSearchScreen(
                    c, app.ironlog.personal.ui.nav.Navigator(controller), "BREAKFAST", LocalDate.now()
                )
            }
        }
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("oats")
        compose.waitUntilAtLeastOneExists(hasText("Oats, dry"), 10_000)
        compose.onNode(hasText("Oats, dry")).performClick()
        compose.waitUntilAtLeastOneExists(hasText("ADD"), 10_000)
        compose.waitForIdle()
    }

    @Test
    fun dialogSettlesWithoutServings() {
        val c = AppContainer(RuntimeEnvironment.getApplication())
        val food = runBlocking {
            val id = c.nutrition.addCustom("Plain oats", 379.0, 13.0, 68.0, 7.0)
            FoodEntity(id = id, name = "Plain oats", kcalPer100g = 379.0, proteinPer100g = 13.0, carbsPer100g = 68.0, fatPer100g = 7.0)
        }
        compose.setContent { IronlogTheme { PortionDialog(c, food, "BREAKFAST", {}, { _, _ -> }) } }
        compose.waitUntilAtLeastOneExists(hasText("ADD"), 10_000)
        compose.waitForIdle()
    }

    @Test
    fun realSeedSearchThenOpenPortionSettles() {
        val c = AppContainer(RuntimeEnvironment.getApplication())
        runBlocking { c.foodSeed.load() }
        compose.setContent {
            IronlogTheme {
                val controller = androidx.navigation.compose.rememberNavController()
                app.ironlog.personal.ui.nutrition.FoodSearchScreen(
                    c, app.ironlog.personal.ui.nav.Navigator(controller), "BREAKFAST", LocalDate.now()
                )
            }
        }
        compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("oats instant plain")
        compose.waitUntilAtLeastOneExists(hasText("Cereals, oats", substring = true), 90_000)
        compose.onAllNodes(hasText("Cereals, oats", substring = true))[0].performClick()
        compose.waitUntilAtLeastOneExists(hasText("ADD"), 10_000)
        compose.waitForIdle()
    }
}
