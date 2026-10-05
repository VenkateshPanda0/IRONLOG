package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tape-measure proportions against goal physiques, with realistic circumferences in cm. */
class PhysiqueTest {
    private fun body(shoulders: Double, waist: Double, hips: Double, thigh: Double, height: Double? = 178.0) = BodyProportions(shoulders, waist, hips, thigh, height)

    @Test
    fun ratiosAndPlausibilityChecks() {
        val p = body(121.0, 76.0, 95.0, 62.0)
        assertEquals(1.59, p.vTaper, 0.01)
        assertEquals(0.80, p.waistToHip, 0.01)
        assertEquals(0.82, p.thighToWaist, 0.01)
        assertEquals(0.43, p.waistToHeight!!, 0.01)
        assertNotNull(BodyProportions.of(121.0, 76.0, 95.0, 62.0, null))
        assertNull(BodyProportions.of(121.0, 76.0, 95.0, null, null))
        assertNull(BodyProportions.of(12.1, 76.0, 95.0, 62.0, null)) // typed in metres or a typo
    }

    @Test
    fun averageManAgainstClassicNeedsTaperLegsAndACut() {
        // Typical untrained build: 112 / 92 / 100 / 56 cm at 175 cm tall.
        val report = PhysiqueCoach.report(body(112.0, 92.0, 100.0, 56.0, 175.0), PhysiqueType.CLASSIC)
        assertFalse(report.results.first { it.target.metric == Metric.V_TAPER }.withinTarget)
        assertTrue(report.findings.any { it.title == "Build your V-taper" })
        assertTrue(report.findings.any { it.title == "Bring up your legs" })
        assertEquals(listOf("shoulders", "lats"), report.priorityMuscles.take(2))
        assertEquals(CalorieDirection.CUT, report.calories) // waist / height 0.53
        assertTrue(report.match < 50)
    }

    @Test
    fun classicBuildMatchesAndLeanSizeGapMeansBulk() {
        val classic = PhysiqueCoach.report(body(124.0, 76.0, 95.0, 63.0), PhysiqueType.CLASSIC)
        assertEquals(100, classic.match)
        assertTrue(classic.findings.isEmpty())
        // Lean but small legs for a bodybuilder: grow, don't diet.
        val bb = PhysiqueCoach.report(body(122.0, 77.0, 95.0, 60.0), PhysiqueType.BODYBUILDER)
        assertEquals(CalorieDirection.LEAN_BULK, bb.calories)
        assertTrue(bb.findings.any { it.title == "Bring up your legs" })
    }

    @Test
    fun womenGoalsUseWaistToHipAndShoulderToHip() {
        val p = body(98.0, 72.0, 100.0, 58.0, 165.0)
        val bikini = PhysiqueCoach.report(p, PhysiqueType.BIKINI)
        // 0.72 waist-to-hip is inside Bikini's 0.70 (+0.03) but not Wellness's 0.65 (+0.03).
        assertTrue(bikini.results.first { it.target.metric == Metric.WAIST_HIP }.withinTarget)
        assertTrue(bikini.findings.none { it.title == "Tighten the waist" })
        val wellness = PhysiqueCoach.report(p, PhysiqueType.WELLNESS)
        assertFalse(wellness.results.first { it.target.metric == Metric.WAIST_HIP }.withinTarget)
        assertTrue("glutes" in wellness.priorityMuscles)
        assertNotNull(PhysiqueType.forSex("FEMALE").firstOrNull { it == PhysiqueType.BIKINI })
        assertTrue(PhysiqueType.forSex("MALE").none { it.sex == Sex.FEMALE })
    }
}
