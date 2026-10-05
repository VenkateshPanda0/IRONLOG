package app.ironlog.personal.data.repo

import kotlinx.datetime.LocalDate
import app.ironlog.personal.time.*

import androidx.room.Room
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.IronlogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class NutritionRepositoryTest {
    private lateinit var db: IronlogDatabase
    private lateinit var repo: NutritionRepository
    private val today = LocalDate.parse("2026-10-07")

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), IronlogDatabase::class.java).allowMainThreadQueries().build()
        repo = NutritionRepository(db.dao())
    }

    @After fun tearDown() = db.close()

    @Test
    fun editingGramsRescalesAndDeleteRemoves() = runBlocking {
        val id = repo.addCustom("Rice", 130.0, 2.7, 28.0, 0.3)
        val rice = db.dao().food(id)!!
        val entry = repo.log(rice, today, "LUNCH", 200.0)
        repo.updateGrams(entry, 300.0, "DINNER")
        val updated = repo.meals(today).first().single()
        assertEquals(390.0, updated.kcal, 1e-9)
        assertEquals("DINNER", updated.mealType)
        assertEquals(listOf(rice.id), repo.recent.first().map { it.id })
        repo.deleteEntry(entry)
        assertTrue(repo.meals(today).first().isEmpty())
    }

    @Test
    fun recipeIsSavedWithServingAndLogsPerServing() = runBlocking {
        val oats = db.dao().food(repo.addCustom("Oats", 380.0, 13.0, 67.0, 7.0))!!
        val milk = db.dao().food(repo.addCustom("Milk", 50.0, 3.4, 4.8, 2.0))!!
        val recipeId = repo.addRecipe("Porridge", listOf(oats to 100.0, milk to 300.0), 2)!!
        val recipe = db.dao().food(recipeId)!!
        assertEquals("RECIPE", recipe.source)
        val serving = repo.servings(recipeId).first().single()
        assertEquals("1 serving", serving.label)
        assertEquals(200.0, serving.grams, 0.0)
        repo.log(recipe, today, "BREAKFAST", serving.grams)
        assertEquals(265.0, repo.meals(today).first().single().kcal, 1e-9)
        assertEquals(listOf("Porridge", "Milk", "Oats").toSet(), repo.myFoods.first().map { it.name }.toSet())
        val totals = repo.dailyTotals(today.minusDays(6), today).first().single()
        assertEquals(265.0, totals.kcal, 1e-9)
    }

    @Test
    fun searchMatchesAllWordsInAnyOrderAndPrefersGenericNames() = runBlocking {
        repo.addCustom("Chicken, broilers or fryers, breast, meat only, cooked, roasted", 165.0, 31.0, 0.0, 3.6)
        repo.addCustom("Chicken breast sandwich, fast food, with mayonnaise and lettuce", 250.0, 15.0, 25.0, 10.0)
        repo.addCustom("Chicken thigh, roasted", 209.0, 26.0, 0.0, 10.9)
        val names = repo.foods("breast chicken").first().map { it.name }
        assertEquals(2, names.size)
        assertTrue(names.none { "thigh" in it })
        assertTrue(repo.foods("  ").first().isEmpty())
    }
}
