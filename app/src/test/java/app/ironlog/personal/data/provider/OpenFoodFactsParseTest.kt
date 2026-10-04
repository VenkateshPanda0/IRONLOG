package app.ironlog.personal.data.provider

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OpenFoodFactsParseTest {
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
}
