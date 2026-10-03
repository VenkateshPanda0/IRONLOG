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
        val foodCsv = context.assets.open(FOODS_ASSET).bufferedReader().use { it.readText() }
        val servingCsv = context.assets.open(SERVINGS_ASSET).bufferedReader().use { it.readText() }
        val foods = FoodSeedCsvParser.parseFoods(foodCsv)
        val portions =
            FoodSeedCsvParser.parseServings(servingCsv).groupBy(FoodServingRecord::sourceRef)
        val dao = database.dao()
        var insertedFoods = 0
        var insertedServings = 0

        foods.forEach { record ->
            val existing = dao.foodBySourceRef(record.sourceRef)
            val foodId = existing?.id ?: dao.insertFoodSeed(record.toEntity())
            if (foodId == -1L) return@forEach
            if (existing == null) insertedFoods += 1

            portions[record.sourceRef].orEmpty().forEach { portion ->
                val insertedId =
                    dao.insertFoodServingSeed(
                        FoodServingEntity(
                            foodId = foodId,
                            label = portion.label,
                            grams = portion.grams,
                        )
                    )
                if (insertedId != -1L) insertedServings += 1
            }
        }
        FoodSeedResult(insertedFoods, insertedServings)
    }

    private fun FoodSeedRecord.toEntity(): FoodEntity =
        FoodEntity(
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

    private companion object {
        const val FOODS_ASSET = "seed/foods.csv"
        const val SERVINGS_ASSET = "seed/food_servings.csv"
    }
}

data class FoodSeedResult(val insertedFoods: Int, val insertedServings: Int)
