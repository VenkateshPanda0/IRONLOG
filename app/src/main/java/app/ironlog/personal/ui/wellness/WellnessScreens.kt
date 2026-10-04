package app.ironlog.personal.ui.wellness

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.CardioSessionEntity
import app.ironlog.personal.data.db.DailyLogEntity
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.HabitCheckEntity
import app.ironlog.personal.domain.CardioType
import app.ironlog.personal.domain.Wellness
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import app.ironlog.personal.ui.train.formatDuration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SHORT = DateTimeFormatter.ofPattern("EEE d MMM")

val HABIT_SUGGESTIONS = listOf("Creatine", "Multivitamin", "10 min stretching", "No alcohol", "Meditate", "8 h in bed", "Protein goal hit", "Read 10 pages")

/** Home dashboard block: readiness, water, steps, sleep and today's habits. */
@Composable
fun DailySection(c: AppContainer, nav: Navigator) {
    val today = remember { LocalDate.now() }
    val day by remember(today) { c.wellness.day(today) }.collectAsState(initial = null)
    val goal by c.goals.current.collectAsState(initial = null)
    val habits by c.wellness.habits.collectAsState(initial = emptyList())
    val checks by c.wellness.habitChecks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var checkIn by remember { mutableStateOf(false) }
    var steps by remember { mutableStateOf(false) }
    val g = goal ?: GoalEntity()
    val log = day ?: DailyLogEntity(date = today.toString())

    SectionHeader("Daily")
    val readiness = Wellness.readiness(log.sleepHours, g.sleepGoalHours, log.sleepQuality, log.energy, log.soreness, log.stress, log.mood)
    IronCard(onClick = { checkIn = true }) {
        Eyebrow("Readiness")
        if (readiness == null || log.energy == null) {
            Text("MORNING CHECK-IN", style = MaterialTheme.typography.titleLarge)
            Text("Sleep, energy, soreness, stress and mood: 20 seconds to know how hard to train today.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryButton("Check in", { checkIn = true }, icon = Icons.Filled.Bedtime)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(readiness.score / 100f, size = 84.dp, stroke = 8.dp, description = "Readiness ${readiness.score}") {
                    Text("${readiness.score}", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(readiness.label.uppercase(), style = MaterialTheme.typography.titleLarge)
                    Text(readiness.advice, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    log.sleepHours?.let { Text("Slept ${formatKg(it)} h", style = MaterialTheme.typography.labelMedium) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IronCard(Modifier.weight(1f), padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = IronTheme.colors.protein, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Eyebrow("Water")
            }
            Text("%.1f L".format(log.waterMl / 1000.0), style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { (log.waterMl / g.waterGoalMl.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = IronTheme.colors.protein,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Text("of %.1f L".format(g.waterGoalMl / 1000.0), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalButton(onClick = { scope.launch { c.wellness.addWater(today, 250) } }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+250") }
                FilledTonalButton(onClick = { scope.launch { c.wellness.addWater(today, 500) } }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("+500") }
            }
        }
        IronCard(Modifier.weight(1f), padding = 14.dp, onClick = { steps = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsWalk, contentDescription = null, tint = IronTheme.colors.carbs, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Eyebrow("Steps")
            }
            Text(log.steps?.let { "%,d".format(it) } ?: "—", style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { ((log.steps ?: 0) / g.stepGoal.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = IronTheme.colors.carbs,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Text("of %,d".format(g.stepGoal), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = { steps = true }, contentPadding = PaddingValues(horizontal = 10.dp)) { Text("Update") }
        }
    }
    IronCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Habits today", Modifier.weight(1f))
            TextButton(onClick = { nav.open(Routes.HABITS) }) { Text(if (habits.isEmpty()) "ADD HABITS" else "MANAGE") }
        }
        if (habits.isEmpty()) {
            Text("Track supplements, stretching, no alcohol or anything else you want to do daily.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val doneToday = checks.filter { it.date == today.toString() }.map { it.habitId }.toSet()
        habits.forEach { habit ->
            val done = habit.id in doneToday
            ListRow(
                title = habit.name,
                subtitle = "${Wellness.currentStreak(checks.filter { it.habitId == habit.id }.map { LocalDate.parse(it.date) }, today)} day streak",
                onClick = { scope.launch { c.wellness.setHabitDone(habit.id, today, !done) } },
                leading = {
                    Icon(
                        if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                        contentDescription = if (done) "Done" else "Not done",
                        tint = if (done) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
    SecondaryButton("Log cardio", { nav.open(Routes.CARDIO) }, icon = Icons.Filled.DirectionsRun)

    if (checkIn) CheckInDialog(c, log, today) { checkIn = false }
    if (steps) StepsDialog(log.steps, onDismiss = { steps = false }) { value ->
        steps = false
        scope.launch { c.wellness.setSteps(today, value) }
    }
}

@Composable
private fun ScaleRow(label: String, low: String, high: String, value: Int?, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("$low → $high", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Equal-width 1–5 cells.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                val on = value == n
                Surface(
                    onClick = { onValue(n) },
                    shape = MaterialTheme.shapes.small,
                    color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f).height(40.dp).semantics { selected = on },
                ) {
                    Box(contentAlignment = Alignment.Center) { Text("$n", style = MaterialTheme.typography.titleSmall) }
                }
            }
        }
    }
}

@Composable
fun CheckInDialog(c: AppContainer, log: DailyLogEntity, date: LocalDate, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var sleep by remember { mutableStateOf(log.sleepHours?.let(::formatKg).orEmpty()) }
    var quality by remember { mutableStateOf(log.sleepQuality) }
    var energy by remember { mutableStateOf(log.energy) }
    var soreness by remember { mutableStateOf(log.soreness) }
    var stress by remember { mutableStateOf(log.stress) }
    var mood by remember { mutableStateOf(log.mood) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Morning check-in") },
        text = {
            // Scrolls on short screens where six questions do not fit.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Field("Hours slept", sleep, { sleep = it }, number = true)
                ScaleRow("Sleep quality", "poor", "great", quality) { quality = it }
                ScaleRow("Energy", "drained", "fired up", energy) { energy = it }
                ScaleRow("Soreness", "none", "very sore", soreness) { soreness = it }
                ScaleRow("Stress", "calm", "maxed out", stress) { stress = it }
                ScaleRow("Mood", "low", "great", mood) { mood = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = energy != null && soreness != null && stress != null && mood != null,
                onClick = {
                    scope.launch {
                        c.wellness.setSleep(date, sleep.replace(',', '.').toDoubleOrNull(), quality)
                        c.wellness.checkIn(date, energy!!, soreness!!, stress!!, mood!!)
                        onDone()
                    }
                },
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("CANCEL") } },
    )
}

@Composable
private fun StepsDialog(current: Int?, onDismiss: () -> Unit, onSave: (Int?) -> Unit) {
    var value by remember { mutableStateOf(current?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Steps today") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Steps", value, { value = it.filter(Char::isDigit) }, number = true)
                Text("Copy the number from your phone or watch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value.toIntOrNull()) }) { Text("SAVE") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

/** Start a timed cardio session or log one after the fact; history below. */
@Composable
fun CardioScreen(c: AppContainer, nav: Navigator) {
    val sessions by c.wellness.cardio.collectAsState(initial = emptyList())
    val profile by c.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var type by rememberSaveable { mutableStateOf(CardioType.RUN) }
    var minutes by rememberSaveable { mutableStateOf("") }
    var distance by rememberSaveable { mutableStateOf("") }
    var calories by rememberSaveable { mutableStateOf("") }
    var heartRate by rememberSaveable { mutableStateOf("") }
    var startedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var pausedMs by rememberSaveable { mutableLongStateOf(0L) }
    var pausedAt by rememberSaveable { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (startedAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val weight = profile?.weightKg ?: 75.0
    val elapsedMs = startedAt?.let { (now - it - pausedMs - (pausedAt?.let { p -> now - p } ?: 0L)).coerceAtLeast(0) } ?: 0L
    val mins = minutes.replace(',', '.').toDoubleOrNull()
    val km = distance.replace(',', '.').toDoubleOrNull()

    Page("Cardio", onBack = { nav.back() }) {
        ChipRow(CardioType.entries.toList(), type, { it.label }, { type = it })
        IronCard {
            Eyebrow("Timer")
            Text(formatDuration(elapsedMs), style = MaterialTheme.typography.displayMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (startedAt == null) {
                    PrimaryButton("Start ${type.label}", { startedAt = System.currentTimeMillis(); pausedMs = 0; pausedAt = null }, Modifier.weight(1f), icon = Icons.Filled.PlayArrow)
                } else {
                    SecondaryButton(
                        if (pausedAt != null) "Resume" else "Pause",
                        {
                            val t = System.currentTimeMillis()
                            pausedAt?.let { pausedMs += t - it; pausedAt = null } ?: run { pausedAt = t }
                        },
                        Modifier.weight(1f),
                        icon = if (pausedAt != null) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    )
                    PrimaryButton(
                        "Stop",
                        {
                            minutes = "%.1f".format(elapsedMs / 60_000.0)
                            startedAt = null
                            pausedAt = null
                        },
                        Modifier.weight(1f),
                        icon = Icons.Filled.Stop,
                    )
                }
            }
        }
        IronCard {
            Eyebrow("Log session")
            Field("Duration (minutes)", minutes, { minutes = it }, number = true)
            if (type.hasDistance) Field("Distance (km)", distance, { distance = it }, number = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Calories (optional)", calories, { calories = it }, number = true, modifier = Modifier.weight(1f))
                Field("Avg heart rate", heartRate, { heartRate = it }, number = true, modifier = Modifier.weight(1f))
            }
            if (mins != null && mins > 0) {
                Text(
                    listOfNotNull(
                        Wellness.paceMinPerKm(mins, km)?.let { "Pace " + Wellness.formatPace(it) },
                        if (calories.isBlank()) "≈ ${Wellness.estimatedCalories(type, mins, weight)} kcal (estimate)" else null,
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PrimaryButton(
                "Save ${type.label}",
                enabled = mins != null && mins > 0 && startedAt == null,
                onClick = {
                    scope.launch {
                        c.wellness.addCardio(
                            CardioSessionEntity(
                                date = LocalDate.now().toString(),
                                type = type.name,
                                durationMin = mins!!,
                                distanceKm = km?.takeIf { type.hasDistance && it > 0 },
                                calories = calories.toIntOrNull() ?: Wellness.estimatedCalories(type, mins, weight),
                                avgHeartRate = heartRate.toIntOrNull(),
                            )
                        )
                        minutes = ""
                        distance = ""
                        calories = ""
                        heartRate = ""
                    }
                },
            )
        }
        SectionHeader("History")
        if (sessions.isEmpty()) EmptyState(Icons.Filled.DirectionsRun, "No cardio yet", "Runs, rides, swims, HIIT and mobility sessions show up here.")
        sessions.take(50).forEach { s ->
            val t = runCatching { CardioType.valueOf(s.type) }.getOrDefault(CardioType.OTHER)
            ListRow(
                title = "${t.label} · ${formatKg(s.durationMin)} min" + (s.distanceKm?.let { " · ${formatKg(it)} km" } ?: ""),
                subtitle = LocalDate.parse(s.date).format(SHORT) +
                    (Wellness.paceMinPerKm(s.durationMin, s.distanceKm)?.let { " · " + Wellness.formatPace(it) } ?: "") +
                    (s.calories?.let { " · $it kcal" } ?: "") + (s.avgHeartRate?.let { " · $it bpm" } ?: ""),
                trailing = {
                    IconButton(onClick = { scope.launch { c.wellness.deleteCardio(s.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Delete session") }
                },
            )
        }
    }
}

@Composable
fun HabitsScreen(c: AppContainer, nav: Navigator) {
    val habits by c.wellness.habits.collectAsState(initial = emptyList())
    val checks by c.wellness.habitChecks.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    val today = LocalDate.now()
    Page("Habits", onBack = { nav.back() }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field("New habit", name, { name = it }, modifier = Modifier.weight(1f))
            FilledIconButton(enabled = name.isNotBlank(), onClick = { scope.launch { c.wellness.addHabit(name); name = "" } }) {
                Icon(Icons.Filled.Add, contentDescription = "Add habit")
            }
        }
        val existing = habits.map { it.name.lowercase() }.toSet()
        val suggestions = HABIT_SUGGESTIONS.filter { it.lowercase() !in existing }
        if (suggestions.isNotEmpty()) {
            Eyebrow("Suggestions")
            ChipRow(suggestions, null, { "+ $it" }, { s -> scope.launch { c.wellness.addHabit(s) } })
        }
        if (habits.isEmpty()) EmptyState(Icons.Filled.CheckCircle, "No habits yet", "Add one above. Tick it off on Home each day to build a streak.")
        habits.forEach { habit ->
            val days = checks.filter { it.habitId == habit.id }.map { LocalDate.parse(it.date) }
            val (best, _) = Wellness.streakDates(days, emptyList())
            IronCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(habit.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Current ${Wellness.currentStreak(days, today)} · best $best · ${days.count { it > today.minusDays(30) }}/30 days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { scope.launch { c.wellness.archiveHabit(habit.id) } }) { Icon(Icons.Filled.Delete, contentDescription = "Remove habit") }
                }
                // Last 14 days as dots.
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (13 downTo 0).forEach { back ->
                        val d = today.minusDays(back.toLong())
                        val done = d in days
                        Surface(
                            onClick = { scope.launch { c.wellness.setHabitDone(habit.id, d, !done) } },
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (done) IronTheme.colors.accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.weight(1f).aspectRatio(1f),
                        ) {}
                    }
                }
            }
        }
    }
}

/** Shared by Home for the checked-today set. */
fun List<HabitCheckEntity>.datesFor(habitId: Long) = filter { it.habitId == habitId }.map { LocalDate.parse(it.date) }
