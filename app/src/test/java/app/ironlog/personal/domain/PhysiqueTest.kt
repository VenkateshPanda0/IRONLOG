package app.ironlog.personal.domain

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic front-view bodies with known widths stand in for camera photos, so the geometry can
 * be checked exactly without a device.
 */
class PhysiqueTest {
    private val w = 400
    private val h = 800
    private val cx = 200

    /** A blocky person: head, deltoids, tapering torso, hips, two legs; arms out unless [armsIn]. */
    private fun body(shoulder: Int, waist: Int, hip: Int, thigh: Int, armsIn: Boolean = false, leftThighExtra: Int = 0, sideOn: Boolean = false): Pair<BodyMask, Map<Joint, Point>> {
        val scale = if (sideOn) 0.45 else 1.0
        val s = (shoulder * scale).toInt()
        val wa = (waist * scale).toInt()
        val hi = (hip * scale).toInt()
        val th = (thigh * scale).toInt()
        val shoulderY = 160
        val hipY = 400
        val kneeY = 580
        val mask =
            BodyMask.of(w, h) { x, y ->
                val dx = abs(x - cx)
                when {
                    y in 60..140 -> dx <= 30 // head
                    y in 140..160 -> dx <= 18 // neck
                    y in 160..200 -> dx <= s / 2 // deltoids
                    y in 200..400 -> {
                        // taper from shoulders to the narrowest waist at 70% of the torso, then out to hips
                        val t = (y - 200) / 200.0
                        val half = if (t < 0.7) s / 2 + (wa / 2 - s / 2) * (t / 0.7) else wa / 2 + (hi / 2 - wa / 2) * ((t - 0.7) / 0.3)
                        val armBand = armsIn && y in 210..380 && dx in (half.toInt() + 1)..(half.toInt() + 25)
                        dx <= half || armBand
                    }
                    y in 400..440 -> dx <= hi / 2
                    y in 440..760 -> {
                        // two legs separated by a gap at the centre
                        val gap = 6
                        if (x < cx) (cx - x) in gap..(gap + th + leftThighExtra) else (x - cx) in gap..(gap + th)
                    }
                    else -> false
                }
            }
        val wristX = if (armsIn) s / 2 + 10 else s / 2 + 70
        val joints =
            mapOf(
                Joint.LEFT_SHOULDER to Point(cx - s / 2f + 10, shoulderY.toFloat()),
                Joint.RIGHT_SHOULDER to Point(cx + s / 2f - 10, shoulderY.toFloat()),
                Joint.LEFT_HIP to Point(cx - hi / 4f, hipY.toFloat()),
                Joint.RIGHT_HIP to Point(cx + hi / 4f, hipY.toFloat()),
                Joint.LEFT_KNEE to Point(cx - 30f, kneeY.toFloat()),
                Joint.RIGHT_KNEE to Point(cx + 30f, kneeY.toFloat()),
                Joint.LEFT_ANKLE to Point(cx - 30f, 750f),
                Joint.RIGHT_ANKLE to Point(cx + 30f, 750f),
                Joint.LEFT_WRIST to Point((cx - wristX).toFloat(), 380f),
                Joint.RIGHT_WRIST to Point((cx + wristX).toFloat(), 380f),
            )
        return mask to joints
    }

    @Test
    fun measuresKnownWidths() {
        val (mask, joints) = body(shoulder = 180, waist = 110, hip = 130, thigh = 60)
        val a = PhysiqueAnalyzer.analyze(mask, joints)
        assertTrue(a.issues.toString(), a.issues.isEmpty())
        val p = a.proportions!!
        assertEquals(181.0, p.shoulderWidth, 2.0)
        assertEquals(111.0, p.waistWidth, 3.0)
        assertEquals(131.0, p.hipWidth, 2.0)
        assertEquals(61.0, p.thighWidth, 2.0)
        assertEquals(1.63, p.vTaper, 0.05)
    }

    @Test
    fun flagsSideOnArmsTouchingAndMissingBody() {
        val (side, sideJoints) = body(180, 110, 130, 60, sideOn = true)
        assertTrue(PhotoIssue.SIDE_ON in PhysiqueAnalyzer.analyze(side, sideJoints).issues)
        val (arms, armJoints) = body(180, 110, 130, 60, armsIn = true)
        assertTrue(PhotoIssue.ARMS_TOUCHING in PhysiqueAnalyzer.analyze(arms, armJoints).issues)
        val empty = BodyMask.of(w, h) { _, _ -> false }
        assertEquals(listOf(PhotoIssue.BODY_NOT_FOUND), PhysiqueAnalyzer.analyze(empty, emptyMap()).issues)
        val (mask, joints) = body(180, 110, 130, 60)
        assertEquals(listOf(PhotoIssue.NOT_FULL_BODY), PhysiqueAnalyzer.analyze(mask, joints - Joint.LEFT_KNEE).issues)
    }

    @Test
    fun narrowFrameGetsVTaperAndLegFocusWithCut() {
        val p = PhysiqueAnalyzer.analyze(body(150, 120, 125, 52).first, body(150, 120, 125, 52).second).proportions!!
        val report = PhysiqueCoach.report(p, PhysiqueType.CLASSIC)
        assertFalse(report.results.first { it.target.metric == Metric.V_TAPER }.withinTarget)
        assertTrue(report.findings.any { it.title == "Build your V-taper" })
        assertTrue(report.findings.any { it.title == "Bring up your legs" })
        assertEquals(listOf("shoulders", "lats"), report.priorityMuscles.take(2))
        assertEquals(CalorieDirection.CUT, report.calories)
        assertTrue(report.match < 80)
    }

    @Test
    fun aestheticFrameMatchesClassicAndAsymmetryIsCaught() {
        val (mask, joints) = body(190, 112, 132, 78)
        val p = PhysiqueAnalyzer.analyze(mask, joints).proportions!!
        val classic = PhysiqueCoach.report(p, PhysiqueType.CLASSIC)
        assertEquals(100, classic.match)
        assertEquals(CalorieDirection.RECOMP, classic.calories)
        val (uneven, unevenJoints) = body(190, 112, 132, 60, leftThighExtra = 14)
        val unevenReport = PhysiqueCoach.report(PhysiqueAnalyzer.analyze(uneven, unevenJoints).proportions!!, PhysiqueType.CLASSIC)
        assertTrue(unevenReport.findings.any { it.title == "Even out your legs" })
    }

    @Test
    fun tapeWaistAboveHalfHeightMeansCutAndSizeGapMeansBulk() {
        val p = PhysiqueAnalyzer.analyze(body(190, 112, 132, 50).first, body(190, 112, 132, 50).second).proportions!!
        assertEquals(CalorieDirection.LEAN_BULK, PhysiqueCoach.report(p, PhysiqueType.BODYBUILDER).calories)
        assertEquals(CalorieDirection.CUT, PhysiqueCoach.report(p, PhysiqueType.BODYBUILDER, waistToHeight = 0.55).calories)
        assertNotNull(PhysiqueType.forSex("FEMALE").firstOrNull { it == PhysiqueType.BIKINI })
        assertTrue(PhysiqueType.forSex("MALE").none { it.sex == Sex.FEMALE })
    }
}
