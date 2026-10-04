package app.ironlog.personal.ui.wellness

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.BodyMeasurementEntity
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.domain.CardioType
import app.ironlog.personal.domain.ProgressMath
import app.ironlog.personal.domain.Training
import app.ironlog.personal.domain.Wellness
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val SHORT = DateTimeFormatter.ofPattern("d MMM")
private val COMPACT = DateTimeFormatter.ofPattern("d/M")

private data class Metric(val key: String, val label: String, val unit: String, val read: (BodyMeasurementEntity) -> Double?)

private val METRICS =
    listOf(
        Metric("waist", "Waist", "cm") { it.waistCm },
        Metric("chest", "Chest", "cm") { it.chestCm },
        Metric("arm", "Arm", "cm") { it.armCm },
        Metric("thigh", "Thigh", "cm") { it.thighCm },
        Metric("hips", "Hips", "cm") { it.hipsCm },
        Metric("neck", "Neck", "cm") { it.neckCm },
        Metric("bodyfat", "Body fat", "%") { it.bodyFatPct },
    )

@Composable
fun BodySection(c: AppContainer) {
    val rows by c.wellness.measurements.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf(METRICS.associate { it.key to "" }) }
    var metric by rememberSaveable { mutableStateOf("waist") }
    IronCard {
        Eyebrow("Log measurements · fill in any")
        METRICS.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { m ->
                    Field("${m.label} (${m.unit})", values.getValue(m.key), { v -> values = values + (m.key to v) }, number = true, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val parsed = values.mapValues { it.value.replace(',', '.').toDoubleOrNull()?.takeIf { v -> v > 0 } }
        PrimaryButton(
            "Save measurements",
            enabled = parsed.values.any { it != null },
            onClick = {
                scope.launch {
                    c.wellness.addMeasurement(
                        BodyMeasurementEntity(
                            date = LocalDate.now().toString(),
                            waistCm = parsed["waist"], chestCm = parsed["chest"], armCm = parsed["arm"], thighCm = parsed["thigh"],
                            hipsCm = parsed["hips"], neckCm = parsed["neck"], bodyFatPct = parsed["bodyfat"]?.takeIf { it < 75 },
                        )
                    )
                    values = METRICS.associate { it.key to "" }
                }
            },
        )
    }
    if (rows.isEmpty()) {
        EmptyState(Icons.Filled.Straighten, "No measurements yet", "Measure every 2–4 weeks, same time of day, relaxed. Waist is the most useful single number.")
        return
    }
    SectionHeader("Latest")
    METRICS.forEach { m ->
        val series = rows.mapNotNull { r -> m.read(r)?.let { LocalDate.parse(r.date) to it } }
        if (series.isNotEmpty()) {
            val change = series.last().second - series.first().second
            ListRow(
                title = "${m.label} · ${formatKg(series.last().second)} ${m.unit}",
                subtitle = if (series.size > 1) "%+.1f %s since %s".format(change, m.unit, series.first().first.format(SHORT)) else "First entry ${series.first().first.format(SHORT)}",
                onClick = { metric = m.key },
            )
        }
    }
    val chosen = METRICS.first { it.key == metric }
    val series = rows.mapNotNull { r -> chosen.read(r)?.let { LocalDate.parse(r.date) to it } }
    if (series.size >= 2) {
        IronCard {
            Eyebrow("${chosen.label} trend")
            LineChart(
                points = series.map { it.first.toEpochDay().toDouble() to it.second },
                description = "${chosen.label} over ${series.size} measurements",
                format = { "%.1f %s".format(it, chosen.unit) },
                xLabels = series.first().first.format(SHORT) to series.last().first.format(SHORT),
            )
        }
    }
    SectionHeader("Entries")
    rows.reversed().take(20).forEach { r ->
        ListRow(
            title = LocalDate.parse(r.date).format(DateTimeFormatter.ofPattern("d MMM yyyy")),
            subtitle = METRICS.mapNotNull { m -> m.read(r)?.let { "${m.label} ${formatKg(it)}" } }.joinToString(" · "),
            trailing = { IconButton(onClick = { scope.launch { c.wellness.deleteMeasurement(r.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Delete entry") } },
        )
    }
}

@Composable
fun CardioSection(c: AppContainer) {
    val sessions by c.wellness.cardio.collectAsState(initial = emptyList())
    val today = LocalDate.now()
    if (sessions.isEmpty()) {
        EmptyState(Icons.Filled.DirectionsRun, "No cardio yet", "Log runs, rides, swims, HIIT or sport from Home or Train.")
        return
    }
    val dated = sessions.map { LocalDate.parse(it.date) to it }
    val thisWeek = dated.filter { Training.weekStart(it.first) == Training.weekStart(today) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("This week", formatKg(thisWeek.sumOf { it.second.durationMin }), Modifier.weight(1f), "minutes")
        StatTile("Distance", formatKg(dated.filter { it.first > today.minusDays(30) }.sumOf { it.second.distanceKm ?: 0.0 }), Modifier.weight(1f), "km · 30 days")
        StatTile("Sessions", "${sessions.size}", Modifier.weight(1f), "all time")
    }
    val weekly = ProgressMath.weeklyVolume(dated.map { it.first to it.second.durationMin }, today, 12)
    IronCard {
        Eyebrow("Minutes per week · WHO suggests 150+")
        BarChart(
            values = weekly.map { it.second },
            labels = weekly.mapIndexed { i, (w, _) -> if (i % 3 == 2 || i == weekly.lastIndex) w.format(COMPACT) else "" },
            target = 150.0,
            description = "Cardio minutes per week for 12 weeks",
        )
    }
    SectionHeader("By type")
    sessions.groupBy { it.type }.entries.sortedByDescending { e -> e.value.sumOf { it.durationMin } }.forEach { (type, list) ->
        val t = runCatching { CardioType.valueOf(type) }.getOrDefault(CardioType.OTHER)
        val km = list.sumOf { it.distanceKm ?: 0.0 }
        val best = list.mapNotNull { s -> Wellness.paceMinPerKm(s.durationMin, s.distanceKm) }.minOrNull()
        ListRow(
            title = t.label,
            subtitle = "${list.size} session${if (list.size == 1) "" else "s"} · ${formatKg(list.sumOf { it.durationMin })} min" +
                (if (km > 0) " · ${formatKg(km)} km" else "") + (best?.let { " · best pace " + Wellness.formatPace(it) } ?: ""),
        )
    }
}

@Composable
fun DailyTrendsSection(c: AppContainer) {
    val logs by c.wellness.dailyLogs.collectAsState(initial = emptyList())
    val goal by c.goals.current.collectAsState(initial = null)
    val g = goal ?: GoalEntity()
    val today = LocalDate.now()
    if (logs.isEmpty()) {
        EmptyState(Icons.Filled.Bedtime, "Nothing logged yet", "Water, steps, sleep and check-ins from Home build these trends.")
        return
    }
    val byDate = logs.associateBy { LocalDate.parse(it.date) }
    val days = (13L downTo 0L).map { today.minusDays(it) }
    fun labels() = days.mapIndexed { i, d -> if (i % 2 == 1 || i == days.lastIndex) d.dayOfMonth.toString() else "" }
    IronCard {
        Eyebrow("Steps · 14 days")
        val steps = days.map { (byDate[it]?.steps ?: 0).toDouble() }
        Text("%,.0f avg".format(steps.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: 0.0), style = MaterialTheme.typography.headlineSmall)
        BarChart(steps, labels(), "Daily steps for 14 days", target = g.stepGoal.toDouble(), format = { "%,.0f".format(it) })
    }
    IronCard {
        Eyebrow("Water · 14 days")
        val water = days.map { (byDate[it]?.waterMl ?: 0) / 1000.0 }
        BarChart(water, labels(), "Daily water in litres for 14 days", target = g.waterGoalMl / 1000.0, format = { "%.1f L".format(it) })
    }
    val sleep = logs.mapNotNull { l -> l.sleepHours?.let { LocalDate.parse(l.date) to it } }.filter { it.first > today.minusDays(60) }
    if (sleep.isNotEmpty()) {
        IronCard {
            Eyebrow("Sleep · goal ${formatKg(g.sleepGoalHours)} h")
            Text("%.1f h avg".format(sleep.map { it.second }.average()), style = MaterialTheme.typography.headlineSmall)
            LineChart(
                points = sleep.map { it.first.toEpochDay().toDouble() to it.second },
                goal = g.sleepGoalHours,
                description = "Hours slept",
                format = { "%.1f h".format(it) },
                xLabels = sleep.first().first.format(SHORT) to sleep.last().first.format(SHORT),
            )
        }
    }
    val readiness =
        logs.mapNotNull { l ->
            Wellness.readiness(l.sleepHours, g.sleepGoalHours, l.sleepQuality, l.energy, l.soreness, l.stress, l.mood)?.takeIf { l.energy != null }
                ?.let { LocalDate.parse(l.date) to it.score.toDouble() }
        }
    if (readiness.size >= 2) {
        IronCard {
            Eyebrow("Readiness")
            LineChart(
                points = readiness.map { it.first.toEpochDay().toDouble() to it.second },
                description = "Readiness score trend",
                format = { "%.0f".format(it) },
                xLabels = readiness.first().first.format(SHORT) to readiness.last().first.format(SHORT),
            )
        }
    }
}
