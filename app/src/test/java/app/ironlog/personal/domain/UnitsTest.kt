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

class LengthUnitTest {
    @Test
    fun measurementsHeightsDistancesAndPace() {
        assertEquals("84 cm", LengthUnit.CM.format(84.0))
        assertEquals("33.1 in", LengthUnit.IN.format(84.0))
        assertEquals(84.07, LengthUnit.IN.parse("33.1")!!, 0.01)
        assertEquals("180", LengthUnit.CM.height(180.0))
        assertEquals("5'11\"", LengthUnit.IN.height(180.0))
        listOf("5'11\"", "5' 11", "5 11", "5ft 11in", "71").forEach { assertEquals(it, 180.34, LengthUnit.IN.parseHeight(it)!!, 0.01) }
        assertEquals(152.4, LengthUnit.IN.parseHeight("5'")!!, 0.01)
        assertEquals(176.8, LengthUnit.IN.parseHeight("5.8")!!, 0.1) // feet with a decimal
        assertEquals(null, LengthUnit.IN.parseHeight("tall"))
        assertEquals("5.04 km", LengthUnit.CM.distance(5.04))
        assertEquals("3.13 mi", LengthUnit.IN.distance(5.04))
        assertEquals(5.0, LengthUnit.IN.parseDistance("3.10686")!!, 0.001)
        assertEquals("4:48 /km", LengthUnit.CM.pace(4.8))
        assertEquals("7:43 /mi", LengthUnit.IN.pace(4.8))
    }

    @Test
    fun fixedTextsAndDefaults() {
        assertEquals("Run 3.1 mi or more at under 8:03 /mi", LengthUnit.IN.localize("Run 5 km or more at under 5:00 /km"))
        assertEquals("Cover 311 mi in total", LengthUnit.IN.localize("Cover 500 km in total"))
        assertEquals("Run 26.2 mi in one session", LengthUnit.IN.localize("Run 42.2 km in one session"))
        assertEquals("Run 5 km in one session", LengthUnit.CM.localize("Run 5 km in one session"))
        assertEquals(LengthUnit.IN, LengthUnit.defaultFor(Locale.US))
        assertEquals(LengthUnit.CM, LengthUnit.defaultFor(Locale("en", "IN")))
    }
}
