package app.ironlog.personal.data.seed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodSeedCsvParserTest {
    @Test
    fun quotedFieldsAndMalformedRowsAreHandled() {
        val csv = fixture("food_seed_fixture.csv")

        val foods = FoodSeedCsvParser.parseFoods(csv)

        assertEquals(2, foods.size)
        assertEquals("Beans, cooked", foods.first().name)
        assertEquals("Quoted \"test\" food", foods.last().name)
        assertEquals(127.0, foods.first().kcal, 0.0)
    }

    @Test
    fun missingFiberIsAllowedButMissingRequiredMacrosAreDropped() {
        val foods = FoodSeedCsvParser.parseFoods(fixture("food_seed_fixture.csv"))

        assertEquals(null, foods.last().fiber)
        assertTrue(foods.none { it.sourceRef == "1003" || it.sourceRef == "1004" })
    }

    @Test
    fun malformedAndNonPositivePortionsAreIgnored() {
        val servings = FoodSeedCsvParser.parseServings(fixture("food_servings_fixture.csv"))

        assertEquals(listOf("1 cup, cooked", "1 cup"), servings.map { it.label })
        assertEquals(listOf(177.0, 200.0), servings.map { it.grams })
    }

    private fun fixture(name: String): String {
        val resource = requireNotNull(javaClass.classLoader?.getResource(name))
        return resource.readText()
    }
}
