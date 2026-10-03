package app.ironlog.personal.data.seed

import androidx.room.Room
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlinx.coroutines.runBlocking
import app.ironlog.personal.data.db.IronlogDatabase

@RunWith(RobolectricTestRunner::class)
class FoodSeedLoaderTest {
    @Test
    fun loadingBundledUsdaRowsTwiceDoesNotDuplicateThem() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val loader = FoodSeedLoader(context, database)
            val first = loader.load()
            val second = loader.load()

            assertEquals(1_200, first.insertedFoods)
            assertTrue(first.insertedServings > 0)
            assertEquals(0, second.insertedFoods)
            assertEquals(0, second.insertedServings)
            assertEquals(1_200, database.dao().countSeedFoods())
            assertEquals(first.insertedServings, database.dao().countFoodServings())
        } finally {
            database.close()
        }
    }
}
