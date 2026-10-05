package app.ironlog.personal.ui.train

import app.ironlog.personal.time.DateTimeFormatter
import kotlinx.datetime.Instant
import app.ironlog.personal.time.ZoneId
import app.ironlog.personal.time.*
import app.ironlog.personal.text.format

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.LoggedSet
import app.ironlog.personal.domain.ExerciseSet
import app.ironlog.personal.domain.WorkoutMath
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.library.formatSet
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.launch

private fun LoggedSet.toExerciseSet() = ExerciseSet(sessionId, startedAt, exerciseId, exerciseName, type, weightKg, reps)

fun formatVolume(kg: Double, unit: app.ironlog.personal.domain.WeightUnit): String = unit.volume(kg)

@Composable
fun HistoryRoute(container: AppContainer, nav: Navigator) {
    val unit = LocalWeightUnit.current
    val sessions by container.workouts.history.collectAsState(initial = emptyList())
    val allSets by container.workouts.allLoggedSets.collectAsState(initial = emptyList())
    val zone = remember { ZoneId.systemDefault() }
    val month = remember { DateTimeFormatter.ofPattern("MMMM yyyy") }
    val day = remember { DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").withZone(zone) }
    val bySession = remember(allSets) { allSets.groupBy { it.sessionId } }
    Page("History", scrollable = false, onBack = { nav.back() }, subtitle = "${sessions.size} workouts") {
        if (sessions.isEmpty()) {
            EmptyState(Icons.Filled.FitnessCenter, "No workouts yet", "Finished workouts show up here.")
        }
        val groups = sessions.groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate().withDayOfMonth(1) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { (start, rows) ->
                item(key = "h$start") { SectionHeader(start.format(month)) }
                items(rows, key = { it.id }) { session ->
                    val sets = bySession[session.id].orEmpty()
                    val duration = WorkoutMath.elapsedMs(session.startedAt, session.endedAt, session.totalPausedMs, null, session.endedAt ?: session.startedAt)
                    ListRow(
                        title = session.name,
                        subtitle = day.format(Instant.ofEpochMilli(session.startedAt)) + " · " + formatDuration(duration) +
                            " · ${sets.count { it.type != "WARMUP" }} sets · " + formatVolume(WorkoutMath.volume(sets.map { it.toExerciseSet() }), unit),
                        onClick = { nav.open(Routes.summary(session.id)) },
                        trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                    )
                }
            }
        }
    }
}

/** Post-workout summary; also the read-only detail view for past sessions in history. */
@Composable
fun WorkoutSummaryScreen(container: AppContainer, nav: Navigator, sessionId: Long) {
    val unit = LocalWeightUnit.current
    val session by remember(sessionId) { container.workouts.observeSession(sessionId) }.collectAsState(initial = null)
    val allSets by container.workouts.allLoggedSets.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var template by remember { mutableStateOf(CardTemplate.MONO) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = session ?: run {
        Page("Workout", onBack = { nav.back() }) { Text("This workout no longer exists.") }
        return
    }
    var notes by remember(current.id) { mutableStateOf(current.notes) }
    val summary = remember(allSets, sessionId) { WorkoutMath.summarize(sessionId, allSets.map { it.toExerciseSet() }) }
    val duration = WorkoutMath.elapsedMs(current.startedAt, current.endedAt, current.totalPausedMs, null, current.endedAt ?: nowMillis())
    val date = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(current.startedAt))
    val justFinished = current.endedAt != null && nowMillis() - current.endedAt < 30 * 60_000

    val card = remember(summary, template, current.name, duration, unit) {
        renderShareCard(
            ShareCardData(
                title = current.name,
                date = date,
                duration = formatDuration(duration),
                volume = formatVolume(summary.volumeKg, unit),
                sets = "${summary.sets}",
                bests = summary.personalBests.map { "${it.exerciseName} · ${it.kind} ${unit.format(it.valueKg)}" },
                lines = summary.exercises.map { it.name to (it.bestSet?.let { s -> formatSet(s.weightKg, s.reps, unit) } ?: "${it.sets} sets") },
            ),
            template,
        )
    }

    Page(
        if (justFinished) "Workout complete" else current.name,
        onBack = { nav.back() },
        subtitle = if (justFinished) current.name + " · " + date else date,
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete workout") }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Time", formatDuration(duration), Modifier.weight(1f))
            StatTile("Volume", formatVolume(summary.volumeKg, unit), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Sets", "${summary.sets}", Modifier.weight(1f))
            StatTile("PRs", "${summary.personalBests.size}", Modifier.weight(1f))
        }
        if (summary.personalBests.isNotEmpty()) {
            SectionHeader("Personal bests")
            summary.personalBests.forEach { best ->
                ListRow(
                    title = best.exerciseName,
                    subtitle = "${best.kind} ${unit.format(best.valueKg)} · previous ${unit.format(best.previousKg)}",
                    leading = { Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = IronTheme.colors.accent) },
                )
            }
        }
        SectionHeader("Exercises")
        if (summary.exercises.isEmpty()) Text("No completed sets were logged.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        summary.exercises.forEach { recap ->
            ListRow(
                title = recap.name,
                subtitle = "${recap.sets} ${if (recap.sets == 1) "set" else "sets"} · ${formatVolume(recap.volumeKg, unit)}",
                trailing = {
                    Text(recap.bestSet?.let { formatSet(it.weightKg, it.reps, unit) } ?: "", style = MaterialTheme.typography.titleSmall)
                },
            )
        }
        SectionHeader("Notes")
        Field("How did it go?", notes, { notes = it })
        if (notes != current.notes) {
            TextButton(onClick = { scope.launch { container.workouts.setSessionNotes(current.id, notes) } }) { Text("SAVE NOTES") }
        }

        SectionHeader("Share")
        ChipRow(CardTemplate.entries.toList(), template, { it.label }, { template = it })
        Image(
            card.asImageBitmap(),
            contentDescription = "Share card preview",
            modifier = Modifier.fillMaxWidth(0.7f).align(Alignment.CenterHorizontally).aspectRatio(1080f / 1350f),
        )
        SecondaryButton("Share workout", { shareCard(context, card, current.id) }, icon = Icons.Filled.Share)
        PrimaryButton("Done", { nav.back() })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this workout?",
            body = "Its sets are removed from history and records.",
            confirm = "Delete",
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    container.workouts.discard(current.id)
                    nav.back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}
