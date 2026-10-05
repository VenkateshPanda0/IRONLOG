package app.ironlog.personal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import app.ironlog.personal.domain.BarPosition
import app.ironlog.personal.domain.Body
import app.ironlog.personal.domain.Implement
import app.ironlog.personal.domain.MovementPattern
import app.ironlog.personal.domain.Prop
import app.ironlog.personal.domain.Skeleton
import app.ironlog.personal.domain.Vec

/**
 * Draws one frame of a movement pattern as a side-view stick figure with simple props. [t] is the
 * position in the movement: 0 = start, 1 = end.
 */
@Composable
fun StickFigure(
    pattern: MovementPattern,
    implement: Implement,
    t: Double,
    modifier: Modifier,
    body: Color,
    accent: Color,
    muted: Color,
) {
    // Fit the whole range of motion, so the camera never moves during the loop.
    val frames = remember(pattern) { (0..8).map { pattern.skeleton(it / 8.0) } }
    val floorY = remember(pattern) { frames.maxOf { s -> maxOf(s.ankle.y, s.toe.y, s.rearAnkle?.y ?: s.ankle.y) } }
    val bounds =
        remember(pattern) {
            val points = frames.flatMap { it.joints } + listOf(Vec(frames[0].hip.x, floorY))
            val minX = points.minOf { it.x } - 0.18
            val maxX = points.maxOf { it.x } + 0.18
            val minY = points.minOf { it.y } - (if (Prop.CABLE_HIGH in pattern.props) 0.25 else 0.14)
            val maxY = maxOf(points.maxOf { it.y }, floorY) + 0.05
            floatArrayOf(minX.toFloat(), minY.toFloat(), maxX.toFloat(), maxY.toFloat())
        }
    Canvas(modifier) {
        val (minX, minY, maxX, maxY) = bounds.toList()
        val unit = minOf(size.width / (maxX - minX), size.height / (maxY - minY))
        val ox = (size.width - (maxX - minX) * unit) / 2 - minX * unit
        val oy = (size.height - (maxY - minY) * unit) / 2 - minY * unit
        fun p(v: Vec) = Offset(ox + v.x.toFloat() * unit, oy + v.y.toFloat() * unit)
        val limb = unit * 0.034f
        val s = pattern.skeleton(t)
        val start = frames[0]

        // Props first, so the figure is drawn over them.
        if (Prop.FLOOR in pattern.props) drawLine(muted, p(Vec(minX.toDouble(), floorY)), p(Vec(maxX.toDouble(), floorY)), unit * 0.012f)
        if (Prop.BENCH in pattern.props) {
            val (left, right, top) =
                if (pattern == MovementPattern.HIP_THRUST) Triple(start.shoulder.x - 0.14, start.shoulder.x + 0.04, start.shoulder.y + 0.035)
                else Triple(minOf(start.hip.x, start.shoulder.x) - 0.02, maxOf(start.hip.x, start.shoulder.x) + 0.02, start.hip.y + 0.035)
            block(p(Vec(left, top)), p(Vec(right, top + 0.04)), muted)
            listOf(left + 0.03, right - 0.03).forEach { x -> drawLine(muted, p(Vec(x, top + 0.04)), p(Vec(x, floorY)), unit * 0.014f) }
        }
        if (Prop.SEAT in pattern.props) {
            val top = start.hip.y + 0.035
            block(p(Vec(start.hip.x - 0.12, top)), p(Vec(start.hip.x + 0.1, top + 0.04)), muted)
            if (pattern != MovementPattern.LEG_PRESS) drawLine(muted, p(Vec(start.hip.x - 0.01, top + 0.04)), p(Vec(start.hip.x - 0.01, floorY)), unit * 0.016f)
            if (pattern == MovementPattern.LEG_PRESS) {
                // Reclined back pad behind the torso.
                drawLine(muted, p(start.hip + Vec(-0.03, 0.03)), p(start.shoulder + Vec(-0.03, 0.03)), unit * 0.04f, StrokeCap.Round)
            }
        }
        if (Prop.PLATFORM in pattern.props) {
            val along = s.ankle - s.hip
            val len = kotlin.math.hypot(along.x, along.y)
            val perp = Vec(-along.y / len, along.x / len) * 0.13
            val at = s.ankle + Vec(along.x / len, along.y / len) * 0.03
            drawLine(muted, p(at - perp), p(at + perp), unit * 0.03f, StrokeCap.Round)
        }
        if (Prop.PULLUP_BAR in pattern.props || Prop.DIP_BARS in pattern.props) {
            val half = if (Prop.DIP_BARS in pattern.props) 0.12 else 0.22
            drawLine(muted, p(start.hand + Vec(-half, 0.0)), p(start.hand + Vec(half, 0.0)), unit * 0.022f, StrokeCap.Round)
        }
        val pulleyHigh = Vec(s.shoulder.x + 0.22, minY.toDouble() + 0.06)
        if (Prop.CABLE_HIGH in pattern.props) {
            drawCircle(muted, unit * 0.03f, p(pulleyHigh), style = Stroke(unit * 0.01f))
            drawLine(muted, p(pulleyHigh), p(s.hand), unit * 0.008f)
        } else if (implement == Implement.CABLE) {
            drawLine(muted, p(s.hand), p(Vec(s.hand.x + 0.3, floorY - 0.04)), unit * 0.008f)
        } else if (implement == Implement.BAND) {
            drawLine(accent.copy(alpha = 0.7f), p(s.hand), p(Vec(s.ankle.x, floorY)), unit * 0.01f)
        }

        // Rear leg behind, slightly faded.
        val rearKnee = s.rearKnee
        val rearAnkle = s.rearAnkle
        if (rearKnee != null && rearAnkle != null) {
            val faded = body.copy(alpha = 0.45f)
            drawLine(faded, p(s.hip), p(rearKnee), limb, StrokeCap.Round)
            drawLine(faded, p(rearKnee), p(rearAnkle), limb, StrokeCap.Round)
            s.rearToe?.let { drawLine(faded, p(rearAnkle), p(it), limb * 0.8f, StrokeCap.Round) }
        }
        // Body: leg, torso, neck, head, then the near arm.
        bone(body, p(s.toe), p(s.ankle), limb * 0.8f)
        bone(body, p(s.ankle), p(s.knee), limb)
        bone(body, p(s.knee), p(s.hip), limb * 1.15f)
        bone(body, p(s.hip), p(s.shoulder), limb * 1.5f)
        bone(body, p(s.shoulder), p(s.neck), limb * 0.8f)
        drawCircle(body, Body.HEAD.toFloat() * unit, p(s.head))
        bone(body, p(s.shoulder), p(s.elbow), limb * 0.95f)
        bone(body, p(s.elbow), p(s.hand), limb * 0.85f)

        // What the hands hold.
        when (implement) {
            Implement.BARBELL -> {
                val at =
                    when (pattern.bar) {
                        BarPosition.BACK -> s.shoulder + Vec.dir(kotlin.math.atan2(s.shoulder.x - s.hip.x, s.hip.y - s.shoulder.y) * 180 / kotlin.math.PI - 90) * 0.035
                        BarPosition.HIPS -> s.hip + Vec(0.0, -0.09)
                        BarPosition.HANDS -> s.hand
                    }
                drawCircle(accent, unit * 0.1f, p(at), style = Stroke(unit * 0.03f))
                drawCircle(accent, unit * 0.022f, p(at))
            }
            Implement.DUMBBELL -> {
                drawCircle(accent, unit * 0.045f, p(s.hand))
                drawCircle(body, unit * 0.015f, p(s.hand))
            }
            Implement.KETTLEBELL -> {
                drawCircle(accent, unit * 0.05f, p(s.hand + Vec(0.0, 0.06)))
                drawLine(accent, p(s.hand), p(s.hand + Vec(0.0, 0.03)), unit * 0.015f)
            }
            Implement.CABLE, Implement.BAND -> drawCircle(accent, unit * 0.02f, p(s.hand))
            Implement.NONE -> Unit
        }
    }
}

private fun DrawScope.bone(color: Color, a: Offset, b: Offset, width: Float) = drawLine(color, a, b, width, StrokeCap.Round)

private fun DrawScope.block(topLeft: Offset, bottomRight: Offset, color: Color) =
    drawRoundRect(
        color,
        Offset(minOf(topLeft.x, bottomRight.x), minOf(topLeft.y, bottomRight.y)),
        Size(kotlin.math.abs(bottomRight.x - topLeft.x), kotlin.math.abs(bottomRight.y - topLeft.y)),
        CornerRadius(6f, 6f),
    )
