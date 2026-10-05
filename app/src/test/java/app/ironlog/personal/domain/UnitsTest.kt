package app.ironlog.personal.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test
    fun convertsAndFormatsBothWays() {
        assertEquals("100 kg", WeightUnit.KG.format(100.0))
        assertEquals("102.5 kg", WeightUnit.KG.format(102.5))
        assertEquals("220.5 lb", WeightUnit.LB.format(100.0))
        assertEquals("225", WeightUnit.LB.number(WeightUnit.LB.parse("225")!!)) // typed in lb, shown back unchanged
        assertEquals(102.058, WeightUnit.LB.parse("225")!!, 0.001)
        assertEquals(80.5, WeightUnit.KG.parse("80,5")!!, 0.0)
        assertEquals(null, WeightUnit.LB.parse("abc"))
        assertEquals("8,450 kg", WeightUnit.KG.volume(8450.0))
        assertEquals("22.0k lb", WeightUnit.LB.volume(10_000.0))
    }

    @Test
    fun platesRoundToTheUnitsSteps() {
        assertEquals(80.0, WeightUnit.KG.roundToPlates(81.0), 1e-9)
        // 80% of 225 lb is 180 lb exactly; in lb mode drop sets land on 5 lb steps.
        val drop = WorkoutMath.dropWeight(WeightUnit.LB.parse("225"), WeightUnit.LB.plateStepKg)!!
        assertEquals("180", WeightUnit.LB.number(drop))
        assertEquals(80.0, WorkoutMath.dropWeight(100.0)!!, 1e-9)
    }

    @Test
    fun fixedTextsAndDefaults() {
        assertEquals("Bench press 331 lb", WeightUnit.LB.localize("Bench press 150 kg"))
        assertEquals("Lift 220,462 lb in total", WeightUnit.LB.localize("Lift 100,000 kg in total"))
        assertEquals("Bench press 150 kg", WeightUnit.KG.localize("Bench press 150 kg"))
        assertEquals(WeightUnit.LB, WeightUnit.defaultFor(Locale.US))
        assertEquals(WeightUnit.KG, WeightUnit.defaultFor(Locale("en", "IN")))
        assertEquals(WeightUnit.KG, WeightUnit.defaultFor(Locale.UK))
    }
}
