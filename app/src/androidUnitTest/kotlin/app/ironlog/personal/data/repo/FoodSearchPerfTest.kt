package app.ironlog.personal.data.repo

import app.ironlog.personal.time.*

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
        // Indian packaged products are found by brand and filtered by cuisine.
        val haldiram = c.nutrition.foods("bhujia", app.ironlog.personal.data.repo.FoodFilter.PACKAGED).first()
        assertTrue(haldiram.isNotEmpty() && haldiram.all { it.source == "OFF" })
        assertTrue(c.nutrition.foods("pizza", app.ironlog.personal.data.repo.FoodFilter.WORLD).first().all { it.source == "FNDDS" })
        val cases =
            mapOf(
                // Indian dishes come first...
                "egg" to { name: String -> name == "Boiled egg (Ubla anda)" },
                "roti" to { name: String -> name == "Chapati/Roti" },
                "dal" to { name: String -> "dal" in name.lowercase() },
                "dosa" to { name: String -> name == "Plain dosa" },
                "paneer" to { name: String -> "paneer" in name.lowercase() },
                // ...then other cuisines and ingredients.
                "oats dry" to { name: String -> "oats" in name.lowercase() && "dry" in name.lowercase() },
                "chicken breast" to { name: String -> "chicken" in name.lowercase() && "breast" in name.lowercase() },
                "pizza" to { name: String -> "pizza" in name.lowercase() },
                "banana" to { name: String -> name.startsWith("Banana") && name.split(' ', ',').count { it.isNotBlank() } <= 3 },
                "milk" to { name: String -> name.startsWith("Milk") },
            )
        for ((query, expected) in cases) {
            val start = nowMillis()
            val results = c.nutrition.foods(query).first()
            val took = nowMillis() - start
            assertTrue("$query -> ${results.firstOrNull()?.name}", results.isNotEmpty() && expected(results.first().name))
            assertTrue("$query took $took ms", took < 2_000)
            println("SEARCH '$query' -> " + results.take(3).joinToString(" | ") { "${it.name} [${it.source}]" })
        }
    }
}
