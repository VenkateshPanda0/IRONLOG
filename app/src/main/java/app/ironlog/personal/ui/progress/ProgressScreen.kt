package app.ironlog.personal.ui.progress

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.ProgressPhotoEntity
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.ExerciseStats
import app.ironlog.personal.domain.ProgressMath
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.SetRecord
import app.ironlog.personal.domain.WorkoutMath
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.library.titleCase
import app.ironlog.personal.ui.theme.IronTheme
import app.ironlog.personal.ui.train.formatVolume
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val SHORT = DateTimeFormatter.ofPattern("d MMM")
private val LONG = DateTimeFormatter.ofPattern("d MMM yyyy")
private val COMPACT = DateTimeFormatter.ofPattern("d/M")

@Composable
fun ProgressScreen(c: AppContainer) {
    var tab by rememberSaveable { mutableStateOf("Weight") }
    Page("Progress") {
        Segments(listOf("Weight", "Strength", "Volume", "Photos"), tab) { tab = it }
        when (tab) {
            "Weight" -> WeightSection(c)
            "Strength" -> StrengthSection(c)
            "Volume" -> VolumeSection(c)
            else -> PhotoSection(c)
        }
    }
}

@Composable
private fun WeightSection(c: AppContainer) {
    val weights by c.body.weights.collectAsState(initial = emptyList())
    val goal by c.goals.current.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var entry by remember { mutableStateOf("") }
    var range by rememberSaveable { mutableStateOf("3M") }
    var editingGoal by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val daily =
        remember(weights) {
            weights.groupBy { LocalDate.parse(it.date) }.mapValues { (_, rows) -> rows.maxBy { it.createdAt }.weightKg }
        }
    val visible = Calculations.filterWeightRange(daily, today, range)
    val average = remember(daily) { ProgressMath.movingAverage(daily) }.filter { it.first in visible.keys }
    val mean = Calculations.sevenDayMean(daily, today)
    val monthAgo = Calculations.sevenDayMean(daily, today.minusDays(30))

    IronCard {
        Eyebrow("Log weigh-in")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Weight today (kg)", entry, { entry = it }, number = true, modifier = Modifier.weight(1f))
            Button(
                enabled = entry.replace(',', '.').toDoubleOrNull()?.let { it in 25.0..400.0 } == true,
                onClick = {
                    scope.launch {
                        c.body.log(today, entry.replace(',', '.').toDouble())
                        entry = ""
                    }
                },
            ) { Text("SAVE") }
        }
    }
    if (daily.isEmpty()) {
        EmptyState(Icons.Filled.MonitorWeight, "No weigh-ins yet", "Weigh in a few mornings a week to see your trend.")
        return
    }
    val latest = daily.toSortedMap().entries.last()
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Latest", formatKg(latest.value), Modifier.weight(1f), "kg · ${latest.key.format(SHORT)}")
        StatTile("7-day avg", mean?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f), "kg")
        StatTile(
            "30 days",
            if (mean != null && monthAgo != null) "%+.1f".format(mean - monthAgo) else "—",
            Modifier.weight(1f),
            "kg change",
        )
    }
    ChipRow(listOf("1M", "3M", "6M", "1Y", "ALL"), range, { it }, { range = it })
    IronCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Body weight · 7-day average")
            TextButton(onClick = { editingGoal = true }) {
                Text(goal?.goalWeightKg?.let { "GOAL ${formatKg(it)} KG" } ?: "SET GOAL")
            }
        }
        if (visible.isEmpty()) {
            Text("No weigh-ins in this range.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LineChart(
                points = visible.map { (d, v) -> d.toEpochDay().toDouble() to v },
                secondary = average.map { (d, v) -> d.toEpochDay().toDouble() to v },
                goal = goal?.goalWeightKg,
                description = "Body weight with seven-day average, ${visible.size} weigh-ins",
                format = { "%.1f kg".format(it) },
                xLabels = visible.keys.first().format(SHORT) to visible.keys.last().format(SHORT),
            )
        }
    }
    SectionHeader("Entries")
    weights.sortedWith(compareByDescending<app.ironlog.personal.data.db.BodyWeightEntity> { it.date }.thenByDescending { it.createdAt })
        .take(30)
        .forEach { row ->
            ListRow(
                title = "${formatKg(row.weightKg)} kg",
                subtitle = LocalDate.parse(row.date).format(LONG) + (row.note?.let { " · $it" } ?: ""),
                trailing = {
                    IconButton(onClick = { scope.launch { c.body.deleteWeight(row.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete weigh-in")
                    }
                },
            )
        }
    if (editingGoal) {
        var value by remember { mutableStateOf(goal?.goalWeightKg?.let(::formatKg).orEmpty()) }
        AlertDialog(
            onDismissRequest = { editingGoal = false },
            title = { Text("Goal weight") },
            text = { Field("Goal (kg)", value, { value = it }, number = true) },
            confirmButton = {
                TextButton(onClick = {
                    editingGoal = false
                    scope.launch {
                        val kg = value.replace(',', '.').toDoubleOrNull()?.takeIf { it in 25.0..400.0 }
                        c.goals.save((goal ?: GoalEntity()).copy(goalWeightKg = kg))
                    }
                }) { Text(if (value.isBlank()) "CLEAR" else "SAVE") }
            },
            dismissButton = { TextButton(onClick = { editingGoal = false }) { Text("CANCEL") } },
        )
    }
}

@Composable
private fun StrengthSection(c: AppContainer) {
    val logged by c.workouts.allLoggedSets.collectAsState(initial = emptyList())
    val byExercise = remember(logged) { logged.groupBy { it.exerciseId } }
    // Exercises ranked by how often they were trained.
    val ranked = remember(byExercise) { byExercise.entries.sortedByDescending { e -> e.value.map { it.sessionId }.distinct().size } }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    if (ranked.isEmpty()) {
        EmptyState(Icons.Filled.FitnessCenter, "No lifts logged yet", "Finish a workout with weights to track your strength.")
        return
    }
    val current = selected?.takeIf { it in byExercise } ?: ranked.first().key
    val rows = byExercise.getValue(current)
    val stats = remember(rows) { ExerciseStats.from(rows.map { SetRecord(it.sessionId, it.startedAt, it.type, it.weightKg, it.reps) }) }
    ChipRow(ranked.map { it.key }, current, { id -> byExercise.getValue(id).first().exerciseName }, { selected = it })
    IronCard {
        Eyebrow("Estimated 1RM · ${rows.first().exerciseName}")
        Text(stats.bestE1rmKg?.let { "%.1f kg".format(it) } ?: "—", style = MaterialTheme.typography.displaySmall)
        if (stats.e1rmTrend.size >= 2) {
            val zone = ZoneId.systemDefault()
            fun day(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().format(SHORT)
            LineChart(
                points = stats.e1rmTrend.map { it.first.toDouble() to it.second },
                description = "Estimated one-rep max trend",
                format = { "%.0f kg".format(it) },
                xLabels = day(stats.e1rmTrend.first().first) to day(stats.e1rmTrend.last().first),
            )
        } else {
            Text("Log this lift in two or more sessions to see the trend.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Heaviest", stats.heaviestKg?.let(::formatKg) ?: "—", Modifier.weight(1f), "kg")
        StatTile("Best set", stats.bestSetVolumeKg?.let { "%.0f".format(it) } ?: "—", Modifier.weight(1f), "kg volume")
        StatTile("Sessions", "${stats.sessions}", Modifier.weight(1f))
    }
    SectionHeader("Top lifts")
    ranked.mapNotNull { (id, sets) ->
            ExerciseStats.from(sets.map { SetRecord(it.sessionId, it.startedAt, it.type, it.weightKg, it.reps) }).bestE1rmKg?.let { Triple(id, sets.first().exerciseName, it) }
        }
        .sortedByDescending { it.third }
        .take(10)
        .forEach { (id, name, e1rm) ->
            ListRow(
                title = name,
                subtitle = "Estimated 1RM",
                onClick = { selected = id },
                leading = { ExerciseThumb(id, name, 44.dp) },
                trailing = { Text("%.1f kg".format(e1rm), style = MaterialTheme.typography.titleSmall) },
            )
        }
}

@Composable
private fun VolumeSection(c: AppContainer) {
    val logged by c.workouts.allLoggedSets.collectAsState(initial = emptyList())
    val exercises by c.workouts.exercises.collectAsState(initial = emptyList())
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now()
    val working = remember(logged) { logged.filter { it.type != "WARMUP" } }
    val dated = remember(working) { working.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() to it } }
    val weekly = ProgressMath.weeklyVolume(dated.map { (d, s) -> d to (s.weightKg ?: 0.0) * (s.reps ?: 0) }, today, 12)
    val musclesByExercise = remember(exercises) { exercises.associate { it.id to it.primaryMuscleList } }
    val perMuscle = ProgressMath.setsPerMuscle(dated.map { (d, s) -> d to s.exerciseId }, musclesByExercise, today, Recommender.ALL_MUSCLES)
    if (working.isEmpty()) {
        EmptyState(Icons.AutoMirrored.Filled.ShowChart, "No volume yet", "Finished workouts build your weekly volume here.")
        return
    }
    IronCard {
        Eyebrow("Weekly volume · last 12 weeks")
        Text(formatVolume(weekly.last().second), style = MaterialTheme.typography.displaySmall)
        Text("this week", color = MaterialTheme.colorScheme.onSurfaceVariant)
        BarChart(
            values = weekly.map { it.second },
            labels = weekly.mapIndexed { i, (week, _) -> if (i % 3 == 2 || i == weekly.lastIndex) week.format(COMPACT) else "" },
            description = "Weekly training volume for 12 weeks",
            format = { formatVolume(it) },
        )
    }
    IronCard {
        Eyebrow("Sets per muscle · last 7 days")
        val untrained = perMuscle.count { it.second == 0 }
        Text(
            if (untrained == 0) "Every muscle group trained this week." else "$untrained of ${perMuscle.size} groups not trained this week.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val max = perMuscle.maxOf { it.second }.coerceAtLeast(1)
        perMuscle.sortedByDescending { it.second }.forEach { (muscle, count) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(muscle.titleCase(), modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall)
                Box(
                    Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        Modifier.fillMaxHeight()
                            .fillMaxWidth(count / max.toFloat())
                            .clip(RoundedCornerShape(5.dp))
                            .background(IronTheme.colors.accent)
                    )
                }
                Text(
                    "$count",
                    modifier = Modifier.width(32.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (count == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
        }
    }
    val sessions = remember(working) { working.map { it.sessionId }.distinct().size }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatTile("Lifetime", formatVolume(WorkoutMath.volume(working.map { app.ironlog.personal.domain.ExerciseSet(it.sessionId, it.startedAt, it.exerciseId, it.exerciseName, it.type, it.weightKg, it.reps) })), Modifier.weight(1f))
        StatTile("Sets", "${working.size}", Modifier.weight(1f), "in $sessions workouts")
    }
}

@Composable
private fun PhotoSection(c: AppContainer) {
    val photos by c.body.photos.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var viewing by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    runCatching { c.body.addPhoto(context.contentResolver, uri, LocalDate.now()) }
                        .onFailure { error = "That image could not be imported." }
                }
            }
        }
    PrimaryButton(
        "Add progress photo",
        { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        icon = Icons.Filled.AddAPhoto,
    )
    Text(
        "Photos are copied into Ironlog's private storage and never leave this phone. They are not included in backups.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (photos.isEmpty()) {
        EmptyState(Icons.Filled.AddAPhoto, "No photos yet", "Take one every few weeks in the same light and pose to see change the scale misses.")
        return
    }
    if (photos.size >= 2) {
        IronCard {
            Eyebrow("First vs latest")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(photos.last(), photos.first()).forEach { photo ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PhotoImage(c.body.photoFile(photo), Modifier.fillMaxWidth().aspectRatio(3f / 4f).clickable { viewing = photo })
                        Text(LocalDate.parse(photo.date).format(LONG), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
    SectionHeader("All photos")
    photos.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            row.forEach { photo ->
                PhotoImage(c.body.photoFile(photo), Modifier.weight(1f).aspectRatio(3f / 4f).clickable { viewing = photo }, sample = 4)
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
    viewing?.let { photo ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(LocalDate.parse(photo.date).format(LONG).uppercase(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            viewing = null
                            scope.launch { c.body.deletePhoto(photo) }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "Delete photo") }
                        IconButton(onClick = { viewing = null }) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    }
                    PhotoImage(c.body.photoFile(photo), Modifier.fillMaxWidth().weight(1f), contentScale = ContentScale.Fit)
                }
            }
        }
    }
}

@Composable
private fun PhotoImage(file: File, modifier: Modifier, sample: Int = 2, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by
        produceState<ImageBitmap?>(null, file.path, sample) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }.getOrNull()
            }
        }
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        bitmap?.let { Image(it, contentDescription = "Progress photo", contentScale = contentScale, modifier = Modifier.fillMaxSize()) }
    }
}
