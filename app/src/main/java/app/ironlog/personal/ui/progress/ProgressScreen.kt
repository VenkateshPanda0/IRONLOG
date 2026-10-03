package app.ironlog.personal.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ironlog.personal.AppContainer
import app.ironlog.personal.IronlogViewModelFactory
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun ProgressScreen(c: AppContainer) {
    val bodyViewModel: BodyViewModel = viewModel(factory = IronlogViewModelFactory(c))
    val bodyState by bodyViewModel.state.collectAsState()
    val weights = bodyState.weights
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var range by remember { mutableStateOf("3M") }
    Page("Progress") {
        Text("Body weight", style = MaterialTheme.typography.titleLarge)
        Field("Weight (kg)", value, { value = it }, true)
        Button(
            onClick = {
                scope.launch {
                    value.toDoubleOrNull()?.let {
                        c.body.log(LocalDate.now(), it)
                        value = ""
                    }
                }
            }
        ) {
            Text("Add today's weight")
        }
        if (weights.isEmpty()) Text("No weight data yet. Log an entry to start your trend.")
        else {
            val grouped =
                weights
                    .groupBy { LocalDate.parse(it.date) }
                    .mapValues { (_, rows) -> rows.maxBy { it.createdAt }.weightKg }
            val visible = Calculations.filterWeightRange(grouped, LocalDate.now(), range)
            val mean = Calculations.sevenDayMean(grouped, LocalDate.now())
            Text("${weights.size} entries · latest %.1f kg".format(grouped.values.last()))
            Text(
                mean?.let { "7-day mean %.1f kg".format(it) }
                    ?: "7-day mean appears after 3 logged days."
            )
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("1M", "3M", "6M", "1Y", "ALL").forEach {
                    FilterChip(range == it, { range = it }, label = { Text(it) })
                }
            }
            WeightChart(visible)
            LazyColumn {
                items(weights.reversed()) { Text("${it.date} · %.1f kg".format(it.weightKg)) }
            }
        }
    }
}

@Composable
private fun WeightChart(points: Map<LocalDate, Double>) {
    if (points.isEmpty()) return
    val entries = points.values.toList()
    val min = entries.minOrNull() ?: return
    val max = entries.maxOrNull() ?: return
    val spread = (max - min).takeIf { it > 0.0 } ?: 1.0
    val accent = MaterialTheme.colorScheme.primary
    Canvas(
        Modifier.fillMaxWidth().height(180.dp).semantics {
            contentDescription = "Body weight chart with ${points.size} recorded daily points"
        }
    ) {
        if (entries.size == 1) {
            drawCircle(
                color = accent,
                radius = 6.dp.toPx(),
                center = Offset(size.width / 2, size.height / 2),
            )
            return@Canvas
        }
        val path = Path()
        entries.forEachIndexed { index, weight ->
            val x = index.toFloat() / (entries.size - 1) * size.width
            val y = size.height - ((weight - min) / spread).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(accent, 5.dp.toPx(), Offset(x, y))
        }
        drawPath(
            path,
            accent,
            style =
                androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                ),
        )
    }
}
