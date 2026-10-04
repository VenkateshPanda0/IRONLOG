package app.ironlog.personal.ui.physique

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import app.ironlog.personal.domain.BodyProportions
import app.ironlog.personal.domain.FigureShape

/** The user's measured proportions as a drawable shape, with the waist as the unit. */
fun shapeOf(p: BodyProportions) = FigureShape(p.vTaper, 1.0, p.hipWidth / p.waistWidth, p.thighToWaist, 0.26)

/** A goal shape rescaled to the same waist as [reference], so outlines can be compared. */
fun FigureShape.matchedTo(reference: FigureShape): FigureShape {
    val k = reference.waist / waist
    return FigureShape(shoulder * k, reference.waist, hip * k, thigh * k, arm * k)
}

/**
 * A front-view silhouette drawn from relative widths: no photos, just geometry. [fill] paints the
 * body; [outline] draws an optional second shape on top (e.g. the goal over the user's own).
 */
@Composable
fun PhysiqueFigure(
    shape: FigureShape,
    modifier: Modifier,
    fill: Color,
    outline: FigureShape? = null,
    outlineColor: Color = Color.Unspecified,
) {
    Canvas(modifier) {
        // Scale so the widest of the two shapes fits the canvas with room for the arms.
        val widest = maxOf(shape.shoulder + 2.4 * shape.arm, outline?.let { it.shoulder + 2.4 * it.arm } ?: 0.0, shape.hip * 1.1, outline?.hip?.times(1.1) ?: 0.0)
        val unit = minOf(size.width * 0.96f / widest.toFloat(), size.height * 0.2f)
        // Overlapping limbs must not double up a translucent fill, so the body is drawn opaque
        // into one layer and the layer carries the transparency.
        drawIntoCanvas { it.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), Paint().apply { alpha = fill.alpha }) }
        drawFigure(shape, unit, Fill, fill.copy(alpha = 1f))
        drawIntoCanvas { it.restore() }
        if (outline != null) {
            drawFigure(outline, unit, Stroke(width = size.minDimension * 0.018f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))), outlineColor)
        }
    }
}

private fun DrawScope.drawFigure(s: FigureShape, unit: Float, style: androidx.compose.ui.graphics.drawscope.DrawStyle, color: Color) {
    val h = size.height
    val cx = size.width / 2
    val sh = (s.shoulder * unit / 2).toFloat()
    val wa = (s.waist * unit / 2).toFloat()
    val hip = (s.hip * unit / 2).toFloat()
    val thigh = (s.thigh * unit).toFloat()
    val arm = (s.arm * unit).toFloat()
    val neck = unit * 0.16f
    val gap = unit * 0.03f
    val kneeCenter = hip - thigh * 0.55f
    val ankleCenter = kneeCenter * 0.85f
    // One side of the outline from the neck down the outside of the leg and back up the inside.
    val side =
        listOf(
            Offset(neck, h * 0.13f),
            Offset(neck, h * 0.155f),
            Offset(sh * 0.78f, h * 0.18f),
            Offset(sh, h * 0.215f),
            Offset(sh - arm * 0.55f, h * 0.29f),
            Offset(maxOf(wa * 1.04f, sh - arm * 0.9f), h * 0.33f),
            Offset(wa, h * 0.44f),
            Offset(hip, h * 0.52f),
            Offset(hip * 0.99f, h * 0.6f),
            Offset(kneeCenter + thigh * 0.36f, h * 0.75f),
            Offset(ankleCenter + thigh * 0.42f, h * 0.83f),
            Offset(ankleCenter + thigh * 0.17f, h * 0.95f),
            Offset(ankleCenter + thigh * 0.3f, h * 0.985f),
            Offset(ankleCenter - thigh * 0.2f, h * 0.985f),
            Offset(ankleCenter - thigh * 0.14f, h * 0.93f),
            Offset(ankleCenter - thigh * 0.24f, h * 0.83f),
            Offset(kneeCenter - thigh * 0.3f, h * 0.75f),
            Offset(gap, h * 0.585f),
        )
    val path = Path()
    side.forEachIndexed { i, p -> if (i == 0) path.moveTo(cx + p.x, p.y) else path.lineTo(cx + p.x, p.y) }
    side.asReversed().forEach { p -> path.lineTo(cx - p.x, p.y) }
    path.close()
    val round = PathEffect.cornerPathEffect(unit * 0.22f)
    val styled = if (style is Stroke) Stroke(style.width, pathEffect = PathEffect.chainPathEffect(style.pathEffect ?: round, round)) else style
    drawPath(path, color, style = styled)
    drawCircle(color, radius = unit * 0.3f, center = Offset(cx, h * 0.075f), style = styled)
    // Arms: upper arm from the deltoid to the elbow, forearm to the wrist, held slightly out.
    for (dir in listOf(1f, -1f)) {
        val shoulderPt = Offset(cx + dir * (sh - arm * 0.45f), h * 0.225f)
        val elbow = Offset(cx + dir * (sh + arm * 0.35f), h * 0.37f)
        val wrist = Offset(cx + dir * (sh + arm * 0.75f), h * 0.5f)
        if (style is Stroke) {
            drawLine(color, shoulderPt, elbow, style.width, StrokeCap.Round, style.pathEffect)
            drawLine(color, elbow, wrist, style.width, StrokeCap.Round, style.pathEffect)
        } else {
            drawLine(color, shoulderPt, elbow, arm, StrokeCap.Round)
            drawLine(color, elbow, wrist, arm * 0.78f, StrokeCap.Round)
        }
    }
}
