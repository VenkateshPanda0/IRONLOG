package app.ironlog.personal.data.repo

import app.ironlog.personal.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** Search quality and speed against the full bundled USDA table. */
@RunWith(RobolectricTestRunner::class)
class FoodSearchPerfTest {
    @Test
    fun staplesRankFirstAndSearchIsFast() = runBlocking {
        val c = AppContainer(RuntimeEnvironment.getApplication())
        c.foodSeed.load()
        val cases =
            mapOf(
                "egg" to { name: String -> name.startsWith("Egg") },
                "oats dry" to { name: String -> "oats" in name.lowercase() && "dry" in name.lowercase() },
                "chicken breast" to { name: String -> name.startsWith("Chicken") && "breast" in name.lowercase() },
                "banana" to { name: String -> name.startsWith("Banana") },
            )
        for ((query, expected) in cases) {
            val start = System.currentTimeMillis()
            val results = c.nutrition.foods(query).first()
            val took = System.currentTimeMillis() - start
            assertTrue("$query -> ${results.firstOrNull()?.name}", results.isNotEmpty() && expected(results.first().name))
            assertTrue("$query took $took ms", took < 2_000)
        }
    }
}
