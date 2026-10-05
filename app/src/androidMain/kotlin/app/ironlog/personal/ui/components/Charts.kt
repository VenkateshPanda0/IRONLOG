package app.ironlog.personal.ui.components

import app.ironlog.personal.text.format

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.ironlog.personal.ui.theme.IronTheme

/**
 * Time-series line chart. [points] are (x, y) where x is any increasing number (epoch day or
 * millis). An optional [secondary] series (e.g. a moving average) and horizontal [goal] line are
 * drawn on the same scale. Only real data is plotted; nothing is interpolated beyond the points.
 */
@Composable
fun LineChart(
    points: List<Pair<Double, Double>>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    secondary: List<Pair<Double, Double>> = emptyList(),
    goal: Double? = null,
    format: (Double) -> String = { "%.1f".format(it) },
    xLabels: Pair<String, String>? = null,
) {
    if (points.isEmpty()) return
    val accent = IronTheme.colors.accent
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ys = points.map { it.second } + secondary.map { it.second } + listOfNotNull(goal)
    val rawMin = ys.min()
    val rawMax = ys.max()
    val pad = ((rawMax - rawMin) * 0.12).takeIf { it > 0 } ?: (rawMax.coerceAtLeast(1.0) * 0.05)
    val minY = rawMin - pad
    val maxY = rawMax + pad
    val minX = points.minOf { it.first }
    val maxX = points.maxOf { it.first }
    val spanX = (maxX - minX).takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            Text(format(rawMax), style = MaterialTheme.typography.labelSmall, color = muted)
        }
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            fun x(v: Double) = if (points.size == 1) size.width / 2 else ((v - minX) / spanX * size.width).toFloat()
            fun y(v: Double) = (size.height - (v - minY) / (maxY - minY) * size.height).toFloat()
            repeat(4) { i ->
                val gy = size.height * i / 3f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
            }
            goal?.let {
                drawLine(
                    muted,
                    Offset(0f, y(it)),
                    Offset(size.width, y(it)),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
            if (points.size > 1) {
                val line = Path()
                val fill = Path()
                points.forEachIndexed { i, (px, py) ->
                    if (i == 0) {
                        line.moveTo(x(px), y(py))
                        fill.moveTo(x(px), size.height)
                        fill.lineTo(x(px), y(py))
                    } else {
                        line.lineTo(x(px), y(py))
                        fill.lineTo(x(px), y(py))
                    }
                }
                fill.lineTo(x(points.last().first), size.height)
                fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color.Transparent)))
                val raw = if (secondary.isNotEmpty()) accent.copy(alpha = 0.45f) else accent
                drawPath(line, raw, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            }
            if (secondary.size > 1) {
                val avg = Path()
                secondary.forEachIndexed { i, (px, py) ->
                    if (i == 0) avg.moveTo(x(px), y(py)) else avg.lineTo(x(px), y(py))
                }
                drawPath(avg, accent, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            }
            points.forEach { (px, py) -> drawCircle(accent, 3.5.dp.toPx(), Offset(x(px), y(py))) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(format(rawMin), style = MaterialTheme.typography.labelSmall, color = muted)
            xLabels?.let { (start, end) ->
                Text("$start – $end", style = MaterialTheme.typography.labelSmall, color = muted)
            }
        }
    }
}

/** Vertical bars with labels underneath; [highlight] marks one bar (e.g. the current week). */
@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    description: String,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    target: Double? = null,
    highlight: Int? = null,
    format: (Double) -> String = { "%.0f".format(it) },
) {
    if (values.isEmpty()) return
    val accent = IronTheme.colors.accent
    val bar = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (values + listOfNotNull(target)).max().takeIf { it > 0 } ?: 1.0
    Column(modifier) {
        Text(format(max), style = MaterialTheme.typography.labelSmall, color = muted)
        Canvas(Modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
            val slot = size.width / values.size
            val width = slot * 0.6f
            values.forEachIndexed { i, v ->
                val h = (v / max * size.height).toFloat()
                drawRoundRect(
                    color = if (i == highlight || (highlight == null && i == values.lastIndex)) accent else bar,
                    topLeft = Offset(i * slot + (slot - width) / 2, size.height - h),
                    size = androidx.compose.ui.geometry.Size(width, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                )
            }
            target?.let {
                val ty = (size.height - it / max * size.height).toFloat()
                drawLine(
                    muted,
                    Offset(0f, ty),
                    Offset(size.width, ty),
                    1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)),
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
