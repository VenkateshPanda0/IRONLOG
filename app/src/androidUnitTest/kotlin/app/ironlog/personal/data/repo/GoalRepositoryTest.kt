package app.ironlog.personal.data.repo

import androidx.room.Room
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GoalRepositoryTest {
    @Test
    fun nutritionTargetsPersistInRoom() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database =
            Room.inMemoryDatabaseBuilder(context, IronlogDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val repository = GoalRepository(database.dao())
            val target =
                GoalEntity(
                    kcalTarget = 2150,
                    proteinG = 140.0,
                    carbsG = 240.0,
                    fatG = 70.0,
                )

            repository.save(target)

            assertEquals(target, repository.currentOnce())
        } finally {
            database.close()
        }
    }
}
