package app.ironlog.personal.data.provider

import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.FoodServingEntity
import app.ironlog.personal.data.db.IronlogDao
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Food-only network boundary. Only the user's explicit food query/barcode is sent. */
class OpenFoodFactsProvider(
    private val dao: IronlogDao,
    private val contact: String = "unset@example.invalid",
) {
    suspend fun byBarcode(barcode: String): FoodEntity? =
        withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext emptyList()
            val encoded = URLEncoder.encode(text.trim(), "UTF-8")
            val json =
                request(
                    "https://search.openfoodfacts.org/search?q=$encoded&page_size=25&fields=code,product_name,brands,nutriments,serving_size,serving_quantity"
                )
            val products = json.optJSONArray("hits") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until products.length()) {
                    val p = products.optJSONObject(index) ?: continue
                    val code = p.optString("code").takeIf(String::isNotBlank) ?: continue
                    val ref = "off:$code"
                    val cached = dao.foodBySourceRef(ref)
                    add(cached ?: store(p, ref) ?: continue)
                }
            }
        }

    /** Saves a parsed product and its declared serving size, if any. */
    private suspend fun store(product: JSONObject, ref: String): FoodEntity? {
        val food = parseProduct(product, ref) ?: return null
        val id = dao.addFood(food)
        parseServing(product.optJSONObject("product") ?: product)?.let { (label, grams) ->
            dao.insertFoodServingSeed(FoodServingEntity(foodId = id, label = label, grams = grams))
        }
        return food.copy(id = id)
    }

    private fun request(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "Ironlog/0.1 ($contact)")
        return try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Parses a product object (or a v2 response wrapping one); incomplete macros return null. */
        fun parseProduct(json: JSONObject, ref: String): FoodEntity? {
            if (json.has("status") && json.optInt("status", 1) != 1) return null
            val product = json.optJSONObject("product") ?: json
            val nutrients = product.optJSONObject("nutriments") ?: return null
            val kcal = nutrients.optDouble("energy-kcal_100g", Double.NaN)
            val protein = nutrients.optDouble("proteins_100g", Double.NaN)
            val carbs = nutrients.optDouble("carbohydrates_100g", Double.NaN)
            val fat = nutrients.optDouble("fat_100g", Double.NaN)
            if (listOf(kcal, protein, carbs, fat).any { !it.isFinite() || it < 0 }) return null
            val title = product.optString("product_name").trim().ifBlank { "Packaged food" }
            // The search service returns brands as an array, the product API as a comma string.
            val brand =
                product.optJSONArray("brands")?.let { array ->
                    (0 until array.length()).map { array.optString(it) }.firstOrNull { it.isNotBlank() }
                } ?: product.optString("brands").substringBefore(',').trim().takeIf(String::isNotBlank)
            return FoodEntity(
                name = title,
                brand = brand,
                source = "OFF",
                sourceRef = ref,
                kcalPer100g = kcal,
                proteinPer100g = protein,
                carbsPer100g = carbs,
                fatPer100g = fat,
                fiberPer100g = nutrients.optDouble("fiber_100g").takeIf(Double::isFinite),
                confidence = "MEDIUM",
            )
        }

        /** The label's declared serving, e.g. ("1 cup (170 g)", 170.0), when it is given in grams. */
        fun parseServing(product: JSONObject): Pair<String, Double>? {
            val grams = product.optDouble("serving_quantity", Double.NaN).takeIf { it.isFinite() && it > 0 && it <= 2000 }
                ?: product.optString("serving_quantity").toDoubleOrNull()?.takeIf { it > 0 && it <= 2000 }
                ?: return null
            val label = product.optString("serving_size").trim().ifBlank { "1 serving" }
            return label to grams
        }
    }

    private fun encodePath(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}

/** Placeholder provider reports unavailable unless an API key is configured. */
class UsdaProvider(private val apiKey: String?) {
    val isEnabled
        get() = !apiKey.isNullOrBlank()

    fun disabledReason(): String? =
        if (isEnabled) null
        else "USDA FoodData Central lookup is disabled: FDC_API_KEY is not configured."
}
