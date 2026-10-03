package app.ironlog.personal.data.provider

import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.data.db.IronlogDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Food-only network boundary. Only the user's explicit food query/barcode is sent. */
class OpenFoodFactsProvider(private val dao:IronlogDao,private val contact:String="unset@example.invalid") {
    suspend fun byBarcode(barcode:String):FoodEntity?=withContext(Dispatchers.IO) {
        val ref="off:$barcode"
        dao.foodBySourceRef(ref)?.let { return@withContext it }
        val json=request("https://world.openfoodfacts.org/api/v2/product/${encodePath(barcode)}.json?fields=product_name,brands,nutriments")
        parseProduct(json,ref)?.also { dao.addFood(it) }
    }
    suspend fun search(text:String):List<FoodEntity> = withContext(Dispatchers.IO) {
        if(text.isBlank()) return@withContext emptyList()
        val encoded=URLEncoder.encode(text,"UTF-8")
        val json=request("https://world.openfoodfacts.org/api/v2/search?search_terms=$encoded&fields=code,product_name,brands,nutriments&page_size=20")
        val products=json.optJSONArray("products") ?: return@withContext emptyList()
        buildList {
            for(index in 0 until products.length()) {
                val p=products.optJSONObject(index) ?: continue
                val code=p.optString("code").takeIf(String::isNotBlank) ?: continue
                val ref="off:$code"
                val cached=dao.foodBySourceRef(ref)
                add(cached ?: parseProduct(p,ref)?.also { dao.addFood(it) } ?: continue)
            }
        }
    }
    private fun request(url:String):JSONObject {
        val connection=URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout=8000; connection.readTimeout=8000; connection.setRequestProperty("User-Agent","Ironlog/0.1 ($contact)")
        return try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) } finally { connection.disconnect() }
    }
    private fun parseProduct(json:JSONObject,ref:String):FoodEntity? {
        if(json.has("status") && json.optInt("status",1)!=1) return null
        val product=json.optJSONObject("product") ?: json
        val nutrients=product.optJSONObject("nutriments") ?: return null
        val kcal=nutrients.optDouble("energy-kcal_100g",Double.NaN)
        val protein=nutrients.optDouble("proteins_100g",Double.NaN)
        val carbs=nutrients.optDouble("carbohydrates_100g",Double.NaN)
        val fat=nutrients.optDouble("fat_100g",Double.NaN)
        if(listOf(kcal,protein,carbs,fat).any { !it.isFinite() || it<0 }) return null
        val title=product.optString("product_name").trim().ifBlank { "Packaged food" }
        return FoodEntity(name=title,brand=product.optString("brands").takeIf(String::isNotBlank),source="OFF",sourceRef=ref,kcalPer100g=kcal,proteinPer100g=protein,carbsPer100g=carbs,fatPer100g=fat,fiberPer100g=nutrients.optDouble("fiber_100g").takeIf(Double::isFinite),confidence="MEDIUM")
    }
    private fun encodePath(value:String)=URLEncoder.encode(value,"UTF-8").replace("+","%20")
}

/** Placeholder provider reports unavailable unless an API key is configured. */
class UsdaProvider(private val apiKey:String?) {
    val isEnabled get()=!apiKey.isNullOrBlank()
    fun disabledReason():String?=if(isEnabled) null else "USDA FoodData Central lookup is disabled: FDC_API_KEY is not configured."
}
