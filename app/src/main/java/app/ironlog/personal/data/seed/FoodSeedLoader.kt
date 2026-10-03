package app.ironlog.personal.data.seed

import android.content.Context
import androidx.room.withTransaction
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.FoodServingEntity
import app.ironlog.personal.data.db.IronlogDatabase

class FoodSeedLoader(
    private val context: Context,
    private val database: IronlogDatabase,
) {
    suspend fun load(): FoodSeedResult = database.withTransaction {
        val foodsCsv = context.assets.open(FOODS_ASSET).bufferedReader().use { it.readText() }
        val servingsCsv = context.assets.open(SERVINGS_ASSET).bufferedReader().use { it.readText() }
        val portionsBySource = parseCsv(servingsCsv).drop(1)
            .mapNotNull(::parseServing)
            .groupBy(FoodServingSeed::sourceRef)
        val dao = database.dao()
        var insertedFoods = 0
        var insertedServings = 0

        parseCsv(foodsCsv).drop(1).forEach { columns ->
            val food = parseFood(columns) ?: return@forEach
            val sourceRef = food.sourceRef ?: return@forEach
            val existing = dao.foodBySourceRef(sourceRef)
            val foodId = existing?.id ?: dao.insertFoodSeed(food)
            if (foodId == -1L) return@forEach
            if (existing == null) insertedFoods += 1
            portionsBySource[sourceRef].orEmpty().forEach { portion ->
                val inserted = dao.insertFoodServingSeed(
                    FoodServingEntity(foodId = foodId, label = portion.label, grams = portion.grams),
                )
                if (inserted != -1L) insertedServings += 1
            }
        }
        FoodSeedResult(insertedFoods, insertedServings)
    }

    private fun parseFood(columns: List<String>): FoodEntity? {
        if (columns.size < 8) return null
        val name = columns[0].trim()
        val sourceRef = columns[7].trim().takeIf(String::isNotEmpty) ?: return null
        val kcal = columns[2].toDoubleOrNull() ?: return null
        val protein = columns[3].toDoubleOrNull() ?: return null
        val carbs = columns[4].toDoubleOrNull() ?: return null
        val fat = columns[5].toDoubleOrNull() ?: return null
        if (name.isBlank() || listOf(kcal, protein, carbs, fat).any { !it.isFinite() || it < 0 }) return null
        val fiber = columns[6].toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
        return FoodEntity(
            name = name,
            source = "USDA",
            sourceRef = sourceRef,
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            fiberPer100g = fiber,
            confidence = "HIGH",
        )
    }

    private fun parseServing(columns: List<String>): FoodServingSeed? {
        if (columns.size < 3) return null
        val sourceRef = columns[0].trim()
        val label = columns[1].trim()
        val grams = columns[2].toDoubleOrNull() ?: return null
        if (sourceRef.isBlank() || label.isBlank() || !grams.isFinite() || grams <= 0) return null
        return FoodServingSeed(sourceRef, label, grams)
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < text.length) {
            val character = text[index]
            when {
                character == '"' && quoted && text.getOrNull(index + 1) == '"' -> {
                    cell.append('"')
                    index += 1
                }
                character == '"' -> quoted = !quoted
                character == ',' && !quoted -> {
                    row += cell.toString()
                    cell.setLength(0)
                }
                (character == '\n' || character == '\r') && !quoted -> {
                    if (character == '\r' && text.getOrNull(index + 1) == '\n') index += 1
                    row += cell.toString()
                    rows += row.toList()
                    row.clear()
                    cell.setLength(0)
                }
                else -> cell.append(character)
            }
            index += 1
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row.toList()
        }
        return rows
    }

    private data class FoodServingSeed(val sourceRef: String, val label: String, val grams: Double)

    private companion object {
        const val FOODS_ASSET = "seed/foods.csv"
        const val SERVINGS_ASSET = "seed/food_servings.csv"
    }
}

data class FoodSeedResult(val insertedFoods: Int, val insertedServings: Int)
