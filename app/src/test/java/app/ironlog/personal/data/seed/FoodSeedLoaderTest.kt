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

            assertEquals(7_793, first.insertedFoods)
            assertTrue(first.insertedServings > 14_000)
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
        } finally {
            database.close()
        }
    }
}
