package app.ironlog.personal.domain

import java.time.LocalDate

data class Ingredient(val grams: Double, val kcalPer100g: Double, val proteinPer100g: Double, val carbsPer100g: Double, val fatPer100g: Double)

data class RecipeTotals(
    val totalGrams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val gramsPerServing: Double,
)

data class TargetPreset(val key: String, val label: String, val goal: String, val adjustment: Double)

object Nutrition {
    /** Calorie presets relative to maintenance; macros follow [Calculations.macroTargets]. */
    val PRESETS =
        listOf(
            TargetPreset("CUT", "Fat loss", "LOSE_FAT", -0.20),
            TargetPreset("MAINTAIN", "Maintain", "MAINTAIN", 0.0),
            TargetPreset("LEAN_BULK", "Lean bulk", "BUILD_MUSCLE", 0.10),
            TargetPreset("BULK", "Bulk", "BUILD_MUSCLE", 0.20),
        )

    /** Maintenance from Mifflin–St Jeor × activity, then the preset adjustment; null under 18. */
    fun presetCalories(preset: TargetPreset, sex: String, weightKg: Double, heightCm: Double, age: Int, activity: Double): Int? {
        val maintenance = Calculations.targetCalories(sex, weightKg, heightCm, age, activity, "MAINTAIN") ?: return null
        return (maintenance * (1 + preset.adjustment)).toInt().coerceAtLeast(1200)
    }

    /** Recipe nutrition per 100 g and per serving, from raw ingredient weights. */
    fun recipe(ingredients: List<Ingredient>, servings: Int): RecipeTotals? {
        val grams = ingredients.sumOf { it.grams }
        if (grams <= 0 || servings <= 0) return null
        fun total(per100: (Ingredient) -> Double) = ingredients.sumOf { per100(it) * it.grams / 100.0 }
        val kcal = total { it.kcalPer100g }
        val protein = total { it.proteinPer100g }
        val carbs = total { it.carbsPer100g }
        val fat = total { it.fatPer100g }
        return RecipeTotals(grams, kcal, protein, carbs, fat, kcal / grams * 100, protein / grams * 100, carbs / grams * 100, fat / grams * 100, grams / servings)
    }

    /**
     * Calories eaten minus target for each of the 7 days ending [today]. Days with nothing logged
     * are excluded from the balance rather than counted as a full deficit.
     */
    fun weeklyBalance(kcalByDate: Map<LocalDate, Double>, target: Int, today: LocalDate): Pair<List<Pair<LocalDate, Double>>, Double> {
        val days = (6L downTo 0L).map { today.minusDays(it) }.map { it to (kcalByDate[it] ?: 0.0) }
        val balance = days.filter { it.second > 0 }.sumOf { it.second - target }
        return days to balance
    }
}
