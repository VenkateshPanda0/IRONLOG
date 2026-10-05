package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachingTest {
    private val kg = WeightUnit.KG
    private val lb = WeightUnit.LB

    @Test
    fun platesPerSideInBothUnits() {
        assertEquals(listOf(25.0, 15.0, 2.5), Plates.load(105.0, kg).perSide)
        assertEquals(105.0, Plates.load(105.0, kg).loadedKg, 1e-9)
        assertEquals(emptyList<Double>(), Plates.load(20.0, kg).perSide)
        assertEquals(listOf(45.0, 45.0), Plates.load(lb.toKg(225.0), lb).perSide)
        val odd = Plates.load(101.0, kg) // 1 kg cannot be split into 1.25 kg pairs
        assertEquals(listOf(25.0, 15.0), odd.perSide)
        assertEquals(1.0, odd.shortByKg(101.0), 1e-9)
    }

    @Test
    fun warmupsRampToTheWorkingWeight() {
        assertEquals(listOf(WarmupSet(20.0, 10), WarmupSet(40.0, 5), WarmupSet(60.0, 3), WarmupSet(80.0, 1)), Warmups.plan(100.0, kg, barbell = true))
        // Light work skips steps that would round to the bar or repeat.
        assertEquals(listOf(WarmupSet(20.0, 10), WarmupSet(25.0, 3), WarmupSet(32.5, 1)), Warmups.plan(40.0, kg, barbell = true))
        assertEquals(listOf(WarmupSet(15.0, 8), WarmupSet(22.5, 3)), Warmups.plan(30.0, kg, barbell = false))
        val lbPlan = Warmups.plan(lb.toKg(225.0), lb, barbell = true).map { lb.number(it.weightKg) to it.reps }
        assertEquals(listOf("45" to 10, "90" to 5, "135" to 3, "180" to 1), lbPlan)
        assertTrue(Warmups.plan(0.0, kg, true).isEmpty())
    }

    private fun session(vararg sets: Pair<Double, Int>, rpe: Double? = null) = sets.map { PastSet(it.first, it.second, rpe) }

    @Test
    fun doubleProgression() {
        // All sets at the top of 8-12: add 2.5 kg upper body, 5 kg lower body.
        val topped = listOf(session(60.0 to 12, 60.0 to 12, 60.0 to 12))
        assertEquals(62.5, Coach.suggest(topped, 8, 12, kg, false).weightKg!!, 1e-9)
        assertEquals(65.0, Coach.suggest(topped, 8, 12, kg, true).weightKg!!, 1e-9)
        assertEquals(CoachKind.INCREASE, Coach.suggest(topped, 8, 12, kg, false).kind)
        // Easy (RPE 7) earns a double jump; pounds use 5 lb steps.
        assertEquals(65.0, Coach.suggest(listOf(session(60.0 to 12, 60.0 to 12, rpe = 7.0)), 8, 12, kg, false).weightKg!!, 1e-9)
        assertEquals("140", lb.number(Coach.suggest(listOf(session(lb.toKg(135.0) to 12, lb.toKg(135.0) to 12)), 8, 12, lb, false).weightKg!!))
        // In range: same weight, one more rep than the weakest set.
        val mid = Coach.suggest(listOf(session(60.0 to 10, 60.0 to 9, 60.0 to 8)), 8, 12, kg, false)
        assertEquals(CoachKind.REPEAT, mid.kind)
        assertEquals(60.0, mid.weightKg!!, 1e-9)
        assertEquals(9, mid.repsLow)
        assertEquals("60 kg × 9–12", Coach.label(mid, kg))
    }

    @Test
    fun deloadAfterTwoMissesAndSpecialCases() {
        val miss = session(100.0 to 6, 100.0 to 5)
        val again = Coach.suggest(listOf(miss, session(100.0 to 7, 100.0 to 6)), 8, 12, kg, true)
        assertEquals(CoachKind.DELOAD, again.kind)
        assertEquals(90.0, again.weightKg!!, 1e-9)
        // One miss only: hold.
        assertEquals(CoachKind.REPEAT, Coach.suggest(listOf(miss, session(95.0 to 9)), 8, 12, kg, true).kind)
        // First time and bodyweight.
        assertEquals(CoachKind.START, Coach.suggest(emptyList(), 8, 12, kg, false).kind)
        val pullups = Coach.suggest(listOf(listOf(PastSet(null, 8), PastSet(null, 7))), 6, 10, kg, false)
        assertNull(pullups.weightKg)
        assertEquals(9, pullups.repsLow)
        assertEquals(CoachKind.INCREASE, Coach.suggest(listOf(listOf(PastSet(null, 16))), 6, 10, kg, false).kind)
        assertTrue(Coach.isLowerBody(listOf("quadriceps"), "compound"))
        assertTrue(!Coach.isLowerBody(listOf("calves"), "isolation"))
    }

    @Test
    fun invertedRangeFromABadBackupDoesNotCrash() {
        val tip = Coach.suggest(listOf(session(60.0 to 9)), 12, 8, kg, false)
        assertEquals(CoachKind.REPEAT, tip.kind)
        assertEquals(10, tip.repsLow)
    }
}
