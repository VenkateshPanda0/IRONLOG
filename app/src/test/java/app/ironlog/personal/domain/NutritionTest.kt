package app.ironlog.personal.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NutritionTest {
    @Test
    fun recipeComputesPer100gAndServing() {
        val oats = Ingredient(100.0, 380.0, 13.0, 67.0, 7.0)
        val milk = Ingredient(300.0, 50.0, 3.4, 4.8, 2.0)
        val r = Nutrition.recipe(listOf(oats, milk), servings = 2)!!
        assertEquals(400.0, r.totalGrams, 0.0)
        assertEquals(530.0, r.kcal, 1e-9)
        assertEquals(132.5, r.kcalPer100g, 1e-9)
        assertEquals(200.0, r.gramsPerServing, 0.0)
        assertEquals(23.2, r.protein, 1e-9)
        assertNull(Nutrition.recipe(emptyList(), 1))
    }

    @Test
    fun weeklyBalanceSkipsUnloggedDays() {
        val today = LocalDate.parse("2026-10-07")
        val (days, balance) = Nutrition.weeklyBalance(mapOf(today to 2500.0, today.minusDays(1) to 1800.0), 2000, today)
        assertEquals(7, days.size)
        assertEquals(today, days.last().first)
        assertEquals(300.0, balance, 0.0)
    }

    @Test
    fun presetsAdjustMaintenance() {
        val maintain = Nutrition.presetCalories(Nutrition.PRESETS[1], "MALE", 80.0, 180.0, 30, 1.55)!!
        val cut = Nutrition.presetCalories(Nutrition.PRESETS[0], "MALE", 80.0, 180.0, 30, 1.55)!!
        // Mifflin–St Jeor: 10*80 + 6.25*180 - 5*30 + 5 = 1780; x1.55 = 2759
        assertEquals(2759, maintain)
        assertEquals((2759.0 * 0.8).toInt(), cut)
        assertNull(Nutrition.presetCalories(Nutrition.PRESETS[1], "MALE", 80.0, 180.0, 16, 1.55))
    }
}
