package app.ironlog.personal.data.seed

import androidx.room.Room
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ExerciseSeedLoaderTest {
    @Test
    fun bundledExerciseAssetSeedsOnceAndKeepsAllRecords() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val loader = SeedLoader(app.ironlog.personal.platform.AndroidAssets(context), database)

            assertEquals(876, loader.load())
            assertEquals(0, loader.load())
            assertEquals(876, database.dao().countExercises())
        } finally {
            database.close()
        }
    }
}
