package app.ironlog.personal.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.instructionList
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.data.db.secondaryMuscleList
import app.ironlog.personal.domain.ExerciseStats
import app.ironlog.personal.domain.SetRecord
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.theme.IronTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun ExerciseDetailScreen(container: AppContainer, nav: Navigator, exerciseId: String) {
    val exercise by remember(exerciseId) { container.workouts.exercise(exerciseId) }.collectAsState(initial = null)
    val history by remember(exerciseId) { container.workouts.exerciseHistory(exerciseId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("About") }
    var confirmDelete by remember { mutableStateOf(false) }
    val dates = remember { DateTimeFormatter.ofPattern("d MMM yyyy").withZone(ZoneId.systemDefault()) }
    val current = exercise ?: run {
        Page("Exercise", onBack = { nav.back() }) { Text("This exercise no longer exists.") }
        return
    }
    val stats = remember(history) {
        ExerciseStats.from(history.map { SetRecord(it.sessionId, it.startedAt, it.type, it.weightKg, it.reps) })
    }

    Page(
        current.name,
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { scope.launch { container.workouts.setFavorite(current.id, !current.isFavorite) } }) {
                Icon(
                    if (current.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (current.isFavorite) "Remove favourite" else "Add favourite",
                    tint = if (current.isFavorite) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (current.isCustom) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete custom exercise")
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOfNotNull(current.equipment, current.level, current.mechanic, "Custom".takeIf { current.isCustom })
                .forEach { Pill(it) }
        }
        ExerciseDemo(current.id, current.name)
        Segments(listOf("About", "History", "Records"), tab) { tab = it }
        when (tab) {
            "About" -> {
                IronCard {
                    Eyebrow("Primary")
                    Text(current.primaryMuscleList.joinToString { it.titleCase() }.ifEmpty { "—" }, style = MaterialTheme.typography.titleMedium)
                    if (current.secondaryMuscleList.isNotEmpty()) {
                        Eyebrow("Secondary")
                        Text(current.secondaryMuscleList.joinToString { it.titleCase() })
                    }
                }
                val steps = current.instructionList
                if (steps.isNotEmpty()) {
                    SectionHeader("How to")
                    steps.forEachIndexed { index, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.titleMedium,
                                color = IronTheme.colors.accent,
                                modifier = Modifier.width(20.dp),
                            )
                            Text(step, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Text("No instructions for this exercise.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            "History" -> {
                if (history.isEmpty()) {
                    EmptyState(Icons.Outlined.StarBorder, "No sets logged yet", "Completed sets for this exercise appear here.")
                }
                history.groupBy { it.sessionId }.forEach { (_, sets) ->
                    IronCard {
                        Eyebrow(dates.format(Instant.ofEpochMilli(sets.first().startedAt)))
                        Text(sets.first().sessionName, style = MaterialTheme.typography.titleSmall)
                        sets.forEach { set ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Set ${set.setIndex}" + if (set.type != "WORKING") " · ${set.type.lowercase()}" else "")
                                Text(formatSet(set.weightKg, set.reps), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Est. 1RM", stats.bestE1rmKg?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f), "kg")
                    StatTile("Heaviest", stats.heaviestKg?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f), "kg")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("Most reps", stats.mostReps?.toString() ?: "—", Modifier.weight(1f), "reps")
                    StatTile("Best set", stats.bestSetVolumeKg?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f), "kg volume")
                }
                StatTile("Logged", "${stats.sets}", Modifier.fillMaxWidth(), "sets in ${stats.sessions} sessions")
                if (stats.e1rmTrend.size >= 2) {
                    IronCard {
                        Eyebrow("Estimated 1RM trend")
                        LineChart(
                            points = stats.e1rmTrend.map { it.first.toDouble() to it.second },
                            description = "Estimated one-rep max over ${stats.e1rmTrend.size} sessions",
                            format = { "%.0f kg".format(it) },
                        )
                    }
                } else {
                    Text(
                        "Log this exercise with weight in two or more sessions to see a strength trend.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete ${current.name}?",
            body = "Past workouts keep their logged sets.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.workouts.deleteCustomExercise(current.id)
                    nav.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

fun formatSet(weightKg: Double?, reps: Int?): String =
    when {
        weightKg != null && weightKg > 0 && reps != null -> "%s kg × %d".format(formatKg(weightKg), reps)
        reps != null -> "$reps reps"
        weightKg != null -> "%s kg".format(formatKg(weightKg))
        else -> "—"
    }

fun formatKg(value: Double): String = if (value % 1.0 == 0.0) "%.0f".format(value) else "%.1f".format(value)
