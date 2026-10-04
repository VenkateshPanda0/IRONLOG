package app.ironlog.personal.data.seed

import android.content.Context
import androidx.room.withTransaction
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.FoodServingEntity
import app.ironlog.personal.data.db.IronlogDatabase

/** One bundled food table and its portions; [defaultSource] applies when rows carry no source column. */
private data class Bundle(val foods: String, val servings: String, val defaultSource: String, val defaultCuisine: String?)

private val BUNDLES =
    listOf(
        Bundle("seed/foods.csv", "seed/food_servings.csv", "USDA", "Ingredient"),
        Bundle("seed/foods_indian.csv", "seed/servings_indian.csv", "INDB", "Indian"),
        Bundle("seed/foods_packaged_in.csv", "seed/servings_packaged_in.csv", "OFF", "Indian (packaged)"),
        Bundle("seed/foods_world.csv", "seed/servings_world.csv", "FNDDS", null),
    )

/** Sources whose portions come only from bundles, so they can be replaced wholesale on reseed. */
private val SEEDED_SOURCES = listOf("USDA", "INDB", "FNDDS")

class FoodSeedLoader(
    private val context: Context,
    private val database: IronlogDatabase,
) {
    suspend fun load(): FoodSeedResult = database.withTransaction {
        val dao = database.dao()
        var insertedFoods = 0
        var insertedServings = 0
        // One query instead of a lookup per food; sourceRef is not indexed.
        val existing = dao.seededIds(SEEDED_SOURCES + "OFF").associate { it.sourceRef to it.id }.toMutableMap()
        dao.deleteSeededServings(SEEDED_SOURCES)

        BUNDLES.forEach { bundle ->
            val foodCsv = context.assets.open(bundle.foods).bufferedReader().use { it.readText() }
            val servingCsv = context.assets.open(bundle.servings).bufferedReader().use { it.readText() }
            val portions = FoodSeedCsvParser.parseServings(servingCsv).groupBy(FoodServingRecord::sourceRef)
            FoodSeedCsvParser.parseFoods(foodCsv).forEach { record ->
                val source = record.source ?: bundle.defaultSource
                val cuisine = record.cuisine ?: bundle.defaultCuisine
                val known = existing[record.sourceRef]
                val foodId =
                    if (known != null) {
                        // Rows from earlier seed versions gain cuisine and popularity tags.
                        dao.tagFood(known, cuisine, record.popularity)
                        known
                    } else {
                        dao.insertFoodSeed(record.toEntity(source, cuisine)).also {
                            if (it != -1L) {
                                insertedFoods += 1
                                existing[record.sourceRef] = it
                            }
                        }
                    }
                if (foodId == -1L) return@forEach
                portions[record.sourceRef].orEmpty().forEach { portion ->
                    val insertedId =
                        dao.insertFoodServingSeed(FoodServingEntity(foodId = foodId, label = portion.label, grams = portion.grams))
                    if (insertedId != -1L) insertedServings += 1
                }
            }
        }
        FoodSeedResult(insertedFoods, insertedServings)
    }

    private fun FoodSeedRecord.toEntity(source: String, cuisine: String?): FoodEntity =
        FoodEntity(
            name = name,
            brand = brand,
            source = source,
            sourceRef = sourceRef,
            kcalPer100g = kcal,
            proteinPer100g = protein,
            carbsPer100g = carbs,
            fatPer100g = fat,
            fiberPer100g = fiber,
            confidence = if (source == "OFF") "MEDIUM" else "HIGH",
            cuisine = cuisine,
            popularity = popularity,
        )
}

data class FoodSeedResult(val insertedFoods: Int, val insertedServings: Int)
