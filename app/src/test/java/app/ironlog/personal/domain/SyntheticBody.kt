package app.ironlog.personal.domain

import kotlin.math.abs

/**
 * Synthetic front-view bodies with known widths that stand in for camera photos, so the geometry
 * can be checked exactly without a device or ML models.
 */
object SyntheticBody {
    const val W = 400
    const val H = 800
    private const val cx = 200

    /** A blocky person: head, deltoids, tapering torso, hips, two legs; arms out unless [armsIn]. */
    fun build(shoulder: Int, waist: Int, hip: Int, thigh: Int, armsIn: Boolean = false, leftThighExtra: Int = 0, sideOn: Boolean = false): Pair<BodyMask, Map<Joint, Point>> {
        val scale = if (sideOn) 0.45 else 1.0
        val s = (shoulder * scale).toInt()
        val wa = (waist * scale).toInt()
        val hi = (hip * scale).toInt()
        val th = (thigh * scale).toInt()
        val shoulderY = 160
        val hipY = 400
        val kneeY = 580
        val mask =
            BodyMask.of(W, H) { x, y ->
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
}
