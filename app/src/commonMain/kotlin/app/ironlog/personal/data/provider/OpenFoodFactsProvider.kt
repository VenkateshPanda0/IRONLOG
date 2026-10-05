package app.ironlog.personal.data.provider

import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.FoodServingEntity
import app.ironlog.personal.data.db.IronlogDao
import app.ironlog.personal.platform.httpGetText
import app.ironlog.personal.platform.ioDispatcher
import app.ironlog.personal.platform.urlEncode
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Food-only network boundary. Only the user's explicit food query/barcode is sent. */
class OpenFoodFactsProvider(
    private val dao: IronlogDao,
    /** Open Food Facts asks apps to identify themselves; the project page is the contact. */
    private val contact: String = "https://github.com/VenkateshPanda0/IRONLOG",
) {
    suspend fun byBarcode(scanned: String): FoodEntity? =
        withContext(ioDispatcher) {
            // Product barcodes are 6 to 14 digits; anything else (a QR code's text) is not looked up.
            val barcode = scanned.trim().takeIf { it.matches(Regex("\\d{6,14}")) } ?: return@withContext null
            val ref = "off:$barcode"
            dao.foodBySourceRef(ref)?.let {
                return@withContext it
            }
            val json =
                request(
                    "https://world.openfoodfacts.org/api/v2/product/${encodePath(barcode)}.json?fields=product_name,brands,nutriments,serving_size,serving_quantity"
                )
            store(json, ref)
        }

    /**
     * Full-text search through Open Food Facts' search service (search.openfoodfacts.org); the
     * legacy /cgi and /api/v2 search endpoints ignore free text or are rate limited.
     */
    suspend fun search(text: String): List<FoodEntity> =
        withContext(ioDispatcher) {
            if (text.isBlank()) return@withContext emptyList()
            val encoded = urlEncode(text.trim())
            val json =
                request(
                    "https://search.openfoodfacts.org/search?q=$encoded&page_size=25&fields=code,product_name,brands,nutriments,serving_size,serving_quantity"
                )
            val products = json["hits"] as? JsonArray ?: return@withContext emptyList()
            buildList {
                for (element in products) {
                    val p = element as? JsonObject ?: continue
                    val code = (p["code"] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank) ?: continue
                    val ref = "off:$code"
                    val cached = dao.foodBySourceRef(ref)
                    add(cached ?: store(p, ref) ?: continue)
                }
            }
        }

    /** Saves a parsed product and its declared serving size, if any. */
    private suspend fun store(product: JsonObject, ref: String): FoodEntity? {
        val food = parseProduct(product, ref) ?: return null
        val id = dao.addFood(food)
        parseServing(product["product"] as? JsonObject ?: product)?.let { (label, grams) ->
            dao.insertFoodServingSeed(FoodServingEntity(foodId = id, label = label, grams = grams))
        }
        return food.copy(id = id)
    }

    private suspend fun request(url: String): JsonObject =
        Json.parseToJsonElement(httpGetText(url, "Ironlog/0.1 ($contact)", MAX_RESPONSE)).jsonObject

    companion object {
        private const val MAX_RESPONSE = 4 * 1024 * 1024

        /** Parses a product object (or a v2 response wrapping one); incomplete macros return null. */
        fun parseProduct(json: JsonObject, ref: String): FoodEntity? {
            if ("status" in json && (json.primitive("status")?.intOrNull ?: 1) != 1) return null
            val product = json["product"] as? JsonObject ?: json
            val nutrients = product["nutriments"] as? JsonObject ?: return null
            val kcal = nutrients.number("energy-kcal_100g")
            val protein = nutrients.number("proteins_100g")
            val carbs = nutrients.number("carbohydrates_100g")
            val fat = nutrients.number("fat_100g")
            if (listOf(kcal, protein, carbs, fat).any { !it.isFinite() || it < 0 }) return null
            // Crowd-sourced labels: reject values no food can have per 100 g.
            if (kcal > 950 || protein > 100 || carbs > 100 || fat > 100 || protein + carbs + fat > 105) return null
            val title = product.text("product_name").trim().take(120).ifBlank { "Packaged food" }
            // The search service returns brands as an array, the product API as a comma string.
            val brand =
                (product["brands"] as? JsonArray)?.let { array ->
                    array.map { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content.orEmpty() }.firstOrNull { it.isNotBlank() }
                }?.take(60) ?: product.text("brands").substringBefore(',').trim().take(60).takeIf(String::isNotBlank)
            return FoodEntity(
                name = title,
                brand = brand,
                source = "OFF",
                sourceRef = ref,
                kcalPer100g = kcal,
                proteinPer100g = protein,
                carbsPer100g = carbs,
                fatPer100g = fat,
                fiberPer100g = nutrients.number("fiber_100g").takeIf { it.isFinite() && it in 0.0..100.0 },
                confidence = "MEDIUM",
            )
        }

        /** The label's declared serving, e.g. ("1 cup (170 g)", 170.0), when it is given in grams. */
        fun parseServing(product: JsonObject): Pair<String, Double>? {
            val grams = product.number("serving_quantity").takeIf { it.isFinite() && it > 0 && it <= 2000 } ?: return null
            val label = product.text("serving_size").trim().take(60).ifBlank { "1 serving" }
            return label to grams
        }

        private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

        /** A number, or a number written as text; NaN when missing or not a number. */
        private fun JsonObject.number(key: String): Double = primitive(key)?.doubleOrNull ?: Double.NaN

        /** A string value, or "" when missing, null or not a string. */
        private fun JsonObject.text(key: String): String = primitive(key)?.takeIf { it.isString }?.content.orEmpty()
    }

    private fun encodePath(value: String) = urlEncode(value)
}
