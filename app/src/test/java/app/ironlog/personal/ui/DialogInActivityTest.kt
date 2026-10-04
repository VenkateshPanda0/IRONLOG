package app.ironlog.personal.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.ironlog.personal.IronlogApp
import app.ironlog.personal.MainActivity
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.ui.nutrition.PortionDialog
import app.ironlog.personal.ui.theme.IronlogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class DialogInActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun portionDialogSettlesInMainActivity() {
        val c = (compose.activity.application as IronlogApp).container
        val food = runBlocking {
            val id = c.nutrition.addCustom("Plain oats", 379.0, 13.0, 68.0, 7.0)
            FoodEntity(id = id, name = "Plain oats", kcalPer100g = 379.0, proteinPer100g = 13.0, carbsPer100g = 68.0, fatPer100g = 7.0)
        }
        compose.activity.runOnUiThread {
            compose.activity.setContent { IronlogTheme { PortionDialog(c, food, "BREAKFAST", {}, { _, _ -> }) } }
        }
        compose.waitUntilAtLeastOneExists(hasText("ADD"), 10_000)
        compose.waitForIdle()
    }
}
