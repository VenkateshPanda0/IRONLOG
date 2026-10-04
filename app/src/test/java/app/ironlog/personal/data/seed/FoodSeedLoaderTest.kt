package app.ironlog.personal.data.seed

import androidx.room.Room
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class FoodSeedLoaderTest {
    @Test
    fun loadingBundledUsdaRowsTwiceDoesNotDuplicateThem() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val loader = FoodSeedLoader(context, database)
            val first = loader.load()
            val second = loader.load()

            // USDA ingredients + Indian dishes + Indian packaged products + world dishes.
            assertTrue("inserted ${first.insertedFoods}", first.insertedFoods > 15_500)
            assertTrue(first.insertedServings > 30_000)
            // Re-running adds no foods and replaces bundled portions without duplicating them.
            assertEquals(0, second.insertedFoods)
            assertEquals(first.insertedServings, second.insertedServings)
            assertEquals(7_793, database.dao().countSeedFoods())
            assertEquals(first.insertedServings, database.dao().countFoodServings())
            // Staples that the old keyword subset missed.
            val eggs = database.dao().foodBySourceRef("171287")!!
            assertEquals("Egg, whole, raw, fresh", eggs.name)
            val labels = database.dao().servings(eggs.id).first().map { it.label }
            assertTrue("1 large" in labels)
            assertTrue(database.dao().foodBySourceRef("173904")!!.name.startsWith("Cereals, oats"))
            // Indian dishes with Indian portions.
            val roti = database.dao().foodBySourceRef("INDB:ASC096")!!
            assertEquals("Chapati/Roti", roti.name)
            assertEquals("Indian", roti.cuisine)
            assertEquals(36.0, database.dao().servings(roti.id).first().single { it.label == "1 chapati" }.grams, 1.0)
            assertEquals("INDB", roti.source)
            // World dishes are tagged by cuisine; ingredients by type.
            val all = database.dao().allFoods()
            assertTrue(all.any { it.source == "FNDDS" && it.cuisine == "Italian" && "pizza" in it.name.lowercase() })
            assertTrue(all.count { it.source == "OFF" && it.cuisine == "Indian (packaged)" } > 1_500)
            assertEquals("Ingredient", eggs.cuisine)
        } finally {
            database.close()
        }
    }
}
