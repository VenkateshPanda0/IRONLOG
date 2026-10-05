package app.ironlog.personal.data.provider

import kotlinx.serialization.json.putJsonObject

import kotlinx.serialization.json.put

import kotlinx.serialization.json.buildJsonObject

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenFoodFactsParseTest {
    @Suppress("TestFunctionName")
    private fun JSONObject(text: String) = Json.parseToJsonElement(text).jsonObject

    @Test
    fun parsesSearchHitWithBrandArrayAndServing() {
        val hit =
            JSONObject(
                """{"code":"0894700010137","product_name":"Nonfat Greek Yogurt","brands":["Chobani"],
                "serving_size":"1 cup (170 g)","serving_quantity":170,
                "nutriments":{"energy-kcal_100g":52.9,"proteins_100g":9.41,"carbohydrates_100g":3.53,"fat_100g":0}}"""
            )
        val food = OpenFoodFactsProvider.parseProduct(hit, "off:0894700010137")!!
        assertEquals("Chobani", food.brand)
        assertEquals(9.41, food.proteinPer100g, 0.0)
        assertEquals("1 cup (170 g)" to 170.0, OpenFoodFactsProvider.parseServing(hit))
    }

    @Test
    fun parsesProductApiWithBrandStringAndRejectsMissingMacros() {
        val ok =
            JSONObject(
                """{"status":1,"product":{"product_name":"Nutella","brands":"Ferrero, Nutella",
                "nutriments":{"energy-kcal_100g":539,"proteins_100g":6.3,"carbohydrates_100g":57.5,"fat_100g":30.9}}}"""
            )
        assertEquals("Ferrero", OpenFoodFactsProvider.parseProduct(ok, "off:1")!!.brand)
        val missing = JSONObject("""{"product_name":"x","nutriments":{"energy-kcal_100g":100}}""")
        assertNull(OpenFoodFactsProvider.parseProduct(missing, "off:2"))
        assertNull(OpenFoodFactsProvider.parseServing(missing))
    }

    @Test
    fun hostileOrBrokenProductsAreRejectedOrTrimmed() {
        fun product(kcal: Double, protein: Double, name: String = "X") =
            buildJsonObject { put("product_name", name); putJsonObject("nutriments") { put("energy-kcal_100g", kcal); put("proteins_100g", protein); put("carbohydrates_100g", 1); put("fat_100g", 1) } }
        assertNull(OpenFoodFactsProvider.parseProduct(product(90_000.0, 10.0), "off:1"))
        assertNull(OpenFoodFactsProvider.parseProduct(product(400.0, 250.0), "off:1"))
        assertEquals(120, OpenFoodFactsProvider.parseProduct(product(400.0, 10.0, "A".repeat(5_000)), "off:1")!!.name.length)
    }

    @Test
    fun nonBarcodeScansAreNeverSentToTheNetwork() = kotlinx.coroutines.runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(org.robolectric.RuntimeEnvironment.getApplication(), app.ironlog.personal.data.db.IronlogDatabase::class.java).allowMainThreadQueries().build()
        try {
            val provider = OpenFoodFactsProvider(db.dao())
            // QR text, URLs and path tricks return immediately (no request is made in tests).
            listOf("https://evil.example/x", "../../etc", "12ab", "123").forEach { assertNull(provider.byBarcode(it)) }
        } finally {
            db.close()
        }
    }
}
