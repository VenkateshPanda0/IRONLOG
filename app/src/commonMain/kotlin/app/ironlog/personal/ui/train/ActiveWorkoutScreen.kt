package app.ironlog.personal.ui.train

import app.ironlog.personal.time.*
import app.ironlog.personal.text.format

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.ExerciseEntity
import app.ironlog.personal.data.db.LoggedSet
import app.ironlog.personal.data.db.SessionExerciseEntity
import app.ironlog.personal.data.db.SetLogEntity
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.Coach
import app.ironlog.personal.domain.CoachKind
import app.ironlog.personal.domain.CoachTip
import app.ironlog.personal.domain.PastSet
import app.ironlog.personal.domain.Plates
import app.ironlog.personal.domain.Warmups
import app.ironlog.personal.domain.WorkoutMath
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.ExerciseBrowser
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SET_TYPES = listOf("WARMUP", "WORKING", "DROP", "REST_PAUSE")

private fun typeLabel(type: String) =
    when (type) {
        "WARMUP" -> "Warm-up"
        "DROP" -> "Drop set"
        "REST_PAUSE" -> "Rest-pause"
        else -> "Working"
    }

private sealed interface Picker {
    data object Add : Picker

    data class Replace(val row: SessionExerciseEntity, val muscle: String?) : Picker
}

@Composable
fun ActiveWorkoutRoute(container: AppContainer, nav: Navigator, sessionId: Long) {
    val session by
        remember(sessionId) { container.workouts.observeSession(sessionId) }.collectAsState(initial = null)
    val current = session ?: return
    if (current.status == "COMPLETED") {
        LaunchedEffect(current.id) { nav.replace(Routes.summary(current.id)) }
        return
    }
    ActiveWorkoutScreen(container, nav, current)
}

@Composable
fun ActiveWorkoutScreen(container: AppContainer, nav: Navigator, session: WorkoutSessionEntity) {
    val w = container.workouts
    val exercises by remember(session.id) { w.sessionExercises(session.id) }.collectAsState(initial = emptyList())
    val sets by remember(session.id) { w.sessionSets(session.id) }.collectAsState(initial = emptyList())
    val library by w.exercises.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var now by remember { mutableLongStateOf(nowMillis()) }
    var restMs by remember { mutableLongStateOf(0L) }
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    // The live workout notification and rest alerts need permission; ask once.
    val platform = app.ironlog.personal.ui.platform.LocalPlatform.current
    val askNotifications = platform.rememberNotificationPermission()
    LaunchedEffect(Unit) {
        if (!platform.canNotify() && container.firstWorkoutNotificationAsk()) askNotifications {}
    }
    LaunchedEffect(session.id) {
        while (true) {
            now = nowMillis()
            restMs = container.restTimer.remainingMs()
            delay(1_000)
        }
    }
    app.ironlog.personal.ui.platform.PlatformBackHandler { nav.back() }

    val setsByRow = sets.groupBy { it.sessionExerciseId }
    val done = sets.count { it.isCompleted }
    val elapsed = WorkoutMath.elapsedMs(session.startedAt, session.endedAt, session.totalPausedMs, session.pausedAt, now)
    val paused = session.status == "PAUSED"

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Header: minimise, title, timer, finish
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.back() }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimise workout")
            }
            Column(Modifier.weight(1f)) {
                Text(session.name.substringBefore(" · ").uppercase(), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    (if (paused) "Paused · " else "") + formatDuration(elapsed) + " · $done/${sets.size} sets",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = {
                    scope.launch { if (paused) w.resume(session.id) else w.pause(session.id) }
                }
            ) {
                Icon(
                    if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (paused) "Resume timer" else "Pause timer",
                )
            }
            Button(onClick = { confirmFinish = true }, shape = CircleShape) { Text("FINISH") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Workout options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Discard workout") },
                        onClick = {
                            menu = false
                            confirmDiscard = true
                        },
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { if (sets.isEmpty()) 0f else done / sets.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            color = IronTheme.colors.accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (exercises.isEmpty()) {
                item {
                    EmptyState(Icons.Filled.Add, "No exercises yet", "Add exercises from the library to start logging.")
                }
            }
            // Superset letters follow the order the groups first appear in.
            val letters = exercises.mapNotNull { it.supersetGroup }.distinct().withIndex().associate { (i, g) -> g to ('A' + i).toString() }
            items(exercises, key = { it.id }) { row ->
                val members = row.supersetGroup?.let { g -> exercises.filter { it.supersetGroup == g } }.orEmpty()
                val restAfter = members.isEmpty() || members.last().id == row.id
                val nextInRound = if (restAfter) null else members.getOrNull(members.indexOfFirst { it.id == row.id } + 1)
                ExerciseCard(
                    container = container,
                    nav = nav,
                    row = row,
                    sets = setsByRow[row.id].orEmpty(),
                    isFirst = row.id == exercises.first().id,
                    isLast = row.id == exercises.last().id,
                    onReplace = {
                        val muscle = library.firstOrNull { it.id == row.exerciseId }?.primaryMuscleList?.firstOrNull()
                        picker = Picker.Replace(row, muscle)
                    },
                    exercise = library.firstOrNull { it.id == row.exerciseId },
                    superset = row.supersetGroup?.let { letters[it] },
                    nextInRound = nextInRound?.exerciseNameSnapshot,
                    // In a superset, go straight to the next exercise; rest after the round.
                    onCompleted = { if (restAfter) container.restTimer.start(session.id, row.restSeconds) else container.restTimer.skip() },
                )
            }
            item {
                SecondaryButton("Add exercise", { picker = Picker.Add }, icon = Icons.Filled.Add)
                Spacer(Modifier.height(24.dp))
            }
        }

        if (restMs > 0) RestBar(container, restMs) { restMs = it }
    }

    picker?.let { current ->
        ExercisePickerDialog(
            title = if (current is Picker.Replace) "Replace ${current.row.exerciseNameSnapshot}" else "Add exercise",
            exercises = library,
            highlightMuscle = (current as? Picker.Replace)?.muscle,
            onDismiss = { picker = null },
            onPick = { exercise ->
                picker = null
                scope.launch {
                    when (current) {
                        Picker.Add -> w.addExercise(session.id, exercise)
                        is Picker.Replace -> w.replaceExercise(current.row.id, exercise)
                    }
                }
            },
        )
    }
    if (confirmFinish) {
        val open = sets.count { !it.isCompleted }
        ConfirmDialog(
            title = "Finish workout?",
            body =
                if (open > 0) "$open sets are not ticked off. Only completed sets are saved to your history."
                else "Nice work. Your sets will be saved to your history.",
            confirm = "Finish",
            onConfirm = {
                confirmFinish = false
                scope.launch {
                    container.restTimer.skip()
                    // The route observes the status change and opens the summary.
                    w.finish(session.id)
                }
            },
            onDismiss = { confirmFinish = false },
        )
    }
    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard workout?",
            body = "This deletes the session and every set logged in it.",
            confirm = "Discard",
            onConfirm = {
                confirmDiscard = false
                scope.launch {
                    container.restTimer.skip()
                    w.discard(session.id)
                    nav.back()
                }
            },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun ExerciseCard(
    container: AppContainer,
    nav: Navigator,
    row: SessionExerciseEntity,
    sets: List<SetLogEntity>,
    isFirst: Boolean,
    isLast: Boolean,
    onReplace: () -> Unit,
    exercise: ExerciseEntity?,
    superset: String?,
    nextInRound: String?,
    onCompleted: suspend () -> Unit,
) {
    val w = container.workouts
    val scope = rememberCoroutineScope()
    val history by remember(row.exerciseId) { w.exerciseHistory(row.exerciseId) }.collectAsState(initial = emptyList())
    // Most recent earlier session for this exercise; this session is not completed so never included.
    val previous: List<LoggedSet> = history.groupBy { it.sessionId }.values.firstOrNull().orEmpty()
    var menu by remember { mutableStateOf(false) }
    var editingNotes by remember { mutableStateOf(false) }
    var showDemo by remember { mutableStateOf(false) }
    var notes by remember(row.id) { mutableStateOf(row.notes) }
    var plates by remember { mutableStateOf<Double?>(null) }
    val skipped = row.status == "SKIPPED"
    val unit = LocalWeightUnit.current
    val barbell = exercise?.equipment.equals("barbell", ignoreCase = true) || row.exerciseNameSnapshot.contains("barbell", ignoreCase = true)
    val weighted = barbell || exercise?.equipment?.lowercase() in setOf("dumbbell", "cable", "machine", "kettlebells", "e-z curl bar")
    val tip =
        remember(history, row.repMin, row.repMax, unit, exercise?.id) {
            val sessions = history.filter { it.type == "WORKING" }.groupBy { it.sessionId }.values.map { list -> list.map { PastSet(it.weightKg, it.reps, it.rpe) } }
            Coach.suggest(sessions, row.repMin, row.repMax, unit, Coach.isLowerBody(exercise?.primaryMuscleList.orEmpty(), exercise?.mechanic))
        }
    // Warm-ups ramp to today's heaviest working weight entered so far, else the coach's pick.
    val workKg = sets.filter { it.type == "WORKING" }.mapNotNull { it.weightKg }.filter { it > 0 }.maxOrNull() ?: tip.weightKg

    IronCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(MaterialTheme.shapes.small)
                    .clickable(onClickLabel = if (showDemo) "Hide demo" else "Show demo") { showDemo = !showDemo }
                    .semantics { contentDescription = if (showDemo) "Hide demo" else "Show demo" }
            ) {
                ExerciseThumb(row.exerciseId, row.exerciseNameSnapshot, size = 52.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    row.exerciseNameSnapshot.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.clickable(onClickLabel = "Open exercise") { nav.open(Routes.exercise(row.exerciseId)) },
                )
                if (superset != null) {
                    Text(
                        "SUPERSET $superset" + (nextInRound?.let { " · then $it" } ?: " · rest after this"),
                        style = MaterialTheme.typography.labelMedium,
                        color = IronTheme.colors.accent,
                    )
                }
                Text(
                    "Target ${row.targetSets} × ${row.repMin}–${row.repMax} · rest ${row.restSeconds}s" +
                        (row.originalNameSnapshot?.let { " · swapped from $it" } ?: "") +
                        (if (skipped) " · skipped" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Exercise options") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Replace exercise") }, onClick = { menu = false; onReplace() })
                    if (row.originalExerciseId != null) {
                        DropdownMenuItem(
                            text = { Text("Revert to ${row.originalNameSnapshot}") },
                            onClick = { menu = false; scope.launch { w.revertExercise(row.id) } },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (notes.isBlank()) "Add note" else "Edit note") },
                        onClick = { menu = false; editingNotes = true },
                    )
                    if (!isLast) DropdownMenuItem(text = { Text(if (superset == null) "Superset with next" else "Add next to superset") }, onClick = { menu = false; scope.launch { w.supersetWithNext(row) } })
                    if (superset != null) DropdownMenuItem(text = { Text("Leave superset") }, onClick = { menu = false; scope.launch { w.leaveSuperset(row) } })
                    if (weighted && workKg != null && sets.none { it.type == "WARMUP" }) {
                        DropdownMenuItem(
                            text = { Text("Add warm-up sets") },
                            onClick = { menu = false; scope.launch { w.addWarmups(row.id, Warmups.plan(workKg, unit, barbell)) } },
                        )
                    }
                    if (barbell) DropdownMenuItem(text = { Text("Plate calculator") }, onClick = { menu = false; plates = workKg ?: Plates.barKg(unit) })
                    if (!isFirst) DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; scope.launch { w.moveExercise(row.id, -1) } })
                    if (!isLast) DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; scope.launch { w.moveExercise(row.id, 1) } })
                    DropdownMenuItem(
                        text = { Text(if (skipped) "Unskip" else "Skip exercise") },
                        onClick = { menu = false; scope.launch { if (skipped) w.unskipExercise(row.id) else w.skipExercise(row.id) } },
                    )
                    DropdownMenuItem(text = { Text("Remove from workout") }, onClick = { menu = false; scope.launch { w.removeExercise(row.id) } })
                }
            }
        }
        if (showDemo) ExerciseDemo(row.exerciseId, row.exerciseNameSnapshot)
        if (previous.isNotEmpty()) {
            Text(
                "Last time · " + previous.filter { it.type != "WARMUP" }.joinToString("  ") { "${unit.number(it.weightKg ?: 0.0)}×${it.reps ?: 0}" },
                style = MaterialTheme.typography.bodySmall,
                color = IronTheme.colors.accent,
                maxLines = 2,
            )
        }
        if (!skipped) CoachRow(tip, unit, barbell, onUse = { kg -> scope.launch { w.applyWeight(row.id, kg) } }, onPlates = { plates = it })
        if (editingNotes) {
            Field("Note", notes, { notes = it })
            TextButton(onClick = { editingNotes = false; scope.launch { w.setExerciseNotes(row.id, notes) } }) { Text("SAVE NOTE") }
        } else if (notes.isNotBlank()) {
            Text("“$notes”", style = MaterialTheme.typography.bodySmall)
        }
        if (!skipped) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Header("SET", Modifier.width(44.dp))
                Header("PREVIOUS", Modifier.weight(1.3f))
                Header(unit.label.uppercase(), Modifier.weight(1f))
                Header("REPS", Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
            var workingNumber = 0
            val previousWorking = previous.filter { it.type == "WORKING" }
            sets.forEach { set ->
                if (set.type == "WORKING") workingNumber++
                // Working set N is compared with last time's working set N; drop, rest-pause and
                // warm-up sets fall back to the previous set in this session.
                val hintSource = if (set.type == "WORKING") previousWorking.getOrNull(workingNumber - 1) else null
                val prior = sets.lastOrNull { it.setIndex < set.setIndex && it.isCompleted }
                val hintWeight = hintSource?.weightKg ?: prior?.weightKg
                val hintReps = hintSource?.reps ?: prior?.reps
                SetRow(
                    set = set,
                    label = when (set.type) { "WARMUP" -> "W"; "DROP" -> "D"; "REST_PAUSE" -> "RP"; else -> "$workingNumber" },
                    previous = hintSource?.let { "${unit.number(it.weightKg ?: 0.0)} × ${it.reps ?: 0}" } ?: "—",
                    hintWeight = hintWeight,
                    hintReps = hintReps,
                    onDraft = { weight, reps -> scope.launch { w.saveSetDraft(set.id, weight, reps) } },
                    onToggle = { typedWeight, typedReps ->
                        scope.launch {
                            if (set.isCompleted) w.setCompleted(set.id, false)
                            else {
                                // What is in the boxes right now wins, even if the debounced
                                // draft has not been saved yet; blanks fall back to the hints.
                                w.completeSet(set.id, typedWeight ?: hintWeight, typedReps ?: hintReps)
                                onCompleted()
                            }
                        }
                    },
                    onType = { type -> scope.launch { w.setType(set.id, type) } },
                    onAddSub = { type -> scope.launch { w.addSubSet(set.id, type, unit.plateStepKg) } },
                    onRpe = { rpe -> scope.launch { w.setRpe(set.id, rpe) } },
                    onDelete = { scope.launch { w.removeSet(set.id) } },
                )
            }
            TextButton(onClick = { scope.launch { w.addSet(row.id) } }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("ADD SET")
            }
        }
    }
    plates?.let { PlateDialog(it, unit) { plates = null } }
}

/** The coach's suggestion for today, with a one-tap "use" for the weight. */
@Composable
private fun CoachRow(tip: CoachTip, unit: app.ironlog.personal.domain.WeightUnit, barbell: Boolean, onUse: (Double) -> Unit, onPlates: (Double) -> Unit) {
    Surface(color = IronTheme.colors.accent.copy(alpha = 0.08f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (tip.kind) {
                        CoachKind.INCREASE -> "COACH · GO UP"
                        CoachKind.REPEAT -> "COACH · SAME WEIGHT"
                        CoachKind.DELOAD -> "COACH · DELOAD"
                        CoachKind.START -> "COACH"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = IronTheme.colors.accent,
                    modifier = Modifier.weight(1f),
                )
                if (tip.kind != CoachKind.START) Text(Coach.label(tip, unit), style = MaterialTheme.typography.titleSmall)
            }
            Text(tip.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val kg = tip.weightKg
            if (kg != null && tip.kind != CoachKind.START) {
                Row {
                    TextButton(onClick = { onUse(kg) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("USE ${unit.format(kg).uppercase()}") }
                    if (barbell) {
                        Spacer(Modifier.width(12.dp))
                        TextButton(onClick = { onPlates(kg) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("PLATES") }
                    }
                }
            }
        }
    }
}

/** Which plates go on each side for a target weight, for the user's unit and bar. */
@Composable
private fun PlateDialog(initialKg: Double, unit: app.ironlog.personal.domain.WeightUnit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(unit.number(initialKg)) }
    val bars = if (unit == app.ironlog.personal.domain.WeightUnit.KG) listOf(20.0, 15.0, 10.0) else listOf(45.0, 35.0, 25.0).map(unit::toKg)
    var barKg by remember { mutableDoubleStateOf(bars.first()) }
    val target = unit.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Plate calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Total (${unit.label})", text, { text = it }, number = true)
                Eyebrow("Bar")
                ChipRow(bars, barKg, { unit.format(it) }, { barKg = it })
                when {
                    target == null -> Text("Enter a weight.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    target < barKg -> Text("Lighter than the bar.", color = MaterialTheme.colorScheme.error)
                    else -> {
                        val load = Plates.load(target, unit, barKg)
                        Eyebrow("Each side")
                        if (load.perSide.isEmpty()) Text("Just the bar.")
                        else Text(load.perSide.joinToString("  ·  ") { formatKg(it) } + " ${unit.label}", style = MaterialTheme.typography.titleLarge)
                        if (load.shortByKg(target) > 0.01) {
                            Text("Closest you can load: ${unit.format(load.loadedKg)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("DONE") } },
    )
}

@Composable
private fun Header(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SetRow(
    set: SetLogEntity,
    label: String,
    previous: String,
    hintWeight: Double?,
    hintReps: Int?,
    onDraft: (Double?, Int?) -> Unit,
    /** Called with the weight (kg) and reps currently typed, null where a box is blank. */
    onToggle: (Double?, Int?) -> Unit,
    onType: (String) -> Unit,
    onAddSub: (String) -> Unit,
    onRpe: (Double?) -> Unit,
    onDelete: () -> Unit,
) {
    val unit = LocalWeightUnit.current
    val shownWeight = set.weightKg?.let(unit::number).orEmpty()
    var weight by remember(set.id, set.weightKg, unit) { mutableStateOf(shownWeight) }
    var reps by remember(set.id, set.reps) { mutableStateOf(set.reps?.toString().orEmpty()) }
    var menu by remember { mutableStateOf(false) }
    val doneColor = IronTheme.colors.accent.copy(alpha = 0.14f)
    LaunchedEffect(weight, reps) {
        delay(300)
        val r = reps.toIntOrNull()
        // Compare the text, not the converted number: a 100 kg set shown as 220.5 lb must not be
        // rewritten as 100.02 kg just because it was displayed.
        val weightChanged = weight != shownWeight
        if (weightChanged || r != set.reps) onDraft(if (weightChanged) unit.parse(weight) else set.weightKg, r)
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (set.isCompleted) doneColor else androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            TextButton(onClick = { menu = true }, contentPadding = PaddingValues(0.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = when (set.type) {
                            "WARMUP" -> IronTheme.colors.carbs
                            "DROP", "REST_PAUSE" -> IronTheme.colors.protein
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                    )
                    set.rpe?.let { Text("@" + formatKg(it), style = MaterialTheme.typography.labelSmall, color = IronTheme.colors.accent) }
                }
            }
            DropdownMenu(menu, { menu = false }) {
                SET_TYPES.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(typeLabel(type) + if (type == set.type) "  ✓" else "") },
                        onClick = { menu = false; onType(type) },
                    )
                }
                HorizontalDivider()
                // Effort: RPE 10 = nothing left, 8 = two reps left. Feeds the coach.
                Text("Effort (RPE)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(6.0, 7.0, 8.0, 9.0, 10.0).forEach { value ->
                        val on = set.rpe == value
                        Surface(
                            onClick = { menu = false; onRpe(if (on) null else value) },
                            shape = CircleShape,
                            color = if (on) IronTheme.colors.accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (on) IronTheme.colors.onAccent else MaterialTheme.colorScheme.onSurface,
                        ) { Text(formatKg(value), Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge) }
                    }
                }
                HorizontalDivider()
                DropdownMenuItem(text = { Text("Add drop set after") }, onClick = { menu = false; onAddSub("DROP") })
                DropdownMenuItem(text = { Text("Add rest-pause set after") }, onClick = { menu = false; onAddSub("REST_PAUSE") })
                DropdownMenuItem(text = { Text("Delete set") }, onClick = { menu = false; onDelete() })
            }
        }
        Text(
            previous,
            modifier = Modifier.weight(1.3f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        NumberCell(weight, { weight = it }, hintWeight?.let(unit::number), KeyboardType.Decimal, Modifier.weight(1f))
        NumberCell(reps, { reps = it }, hintReps?.toString(), KeyboardType.Number, Modifier.weight(1f))
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            Surface(
                onClick = {
                    val typedWeight = if (weight == shownWeight) set.weightKg else unit.parse(weight)
                    onToggle(typedWeight?.takeIf { it >= 0 }, reps.toIntOrNull()?.takeIf { it >= 0 })
                },
                shape = MaterialTheme.shapes.small,
                color = if (set.isCompleted) IronTheme.colors.accent else MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = if (set.isCompleted) "Mark set not done" else "Complete set",
                        tint = if (set.isCompleted) IronTheme.colors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Compact numeric input; the [hint] shows last time's value and is used if left blank. */
@Composable
private fun NumberCell(
    value: String,
    onValue: (String) -> Unit,
    hint: String?,
    keyboard: KeyboardType,
    modifier: Modifier,
) {
    Box(
        modifier
            .padding(horizontal = 4.dp)
            .height(36.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (value.isEmpty() && hint != null) {
            Text(hint, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), style = MaterialTheme.typography.titleSmall)
        }
        BasicTextField(
            value = value,
            onValueChange = { text -> onValue(text.filter { it.isDigit() || it == '.' || it == ',' }.take(6)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.titleSmall.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(IronTheme.colors.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RestBar(container: AppContainer, remainingMs: Long, onRemaining: (Long) -> Unit) {
    val scope = rememberCoroutineScope()
    Surface(color = IronTheme.colors.accent, contentColor = IronTheme.colors.onAccent) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("REST", style = MaterialTheme.typography.labelMedium)
                Text(formatDuration(remainingMs), style = MaterialTheme.typography.headlineMedium)
            }
            listOf(-15 to "−15", 15 to "+15").forEach { (delta, label) ->
                TextButton(onClick = {
                    scope.launch {
                        container.restTimer.adjust(delta)
                        onRemaining(container.restTimer.remainingMs())
                    }
                }) { Text(label, color = IronTheme.colors.onAccent, style = MaterialTheme.typography.titleMedium) }
            }
            Button(
                onClick = { scope.launch { container.restTimer.skip(); onRemaining(0) } },
                colors = ButtonDefaults.buttonColors(containerColor = IronTheme.colors.onAccent, contentColor = IronTheme.colors.accent),
                shape = CircleShape,
            ) { Text("SKIP") }
        }
    }
}

@Composable
private fun ExercisePickerDialog(
    title: String,
    exercises: List<ExerciseEntity>,
    highlightMuscle: String?,
    onDismiss: () -> Unit,
    onPick: (ExerciseEntity) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title.uppercase(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), maxLines = 2)
                    TextButton(onClick = onDismiss) { Text("CLOSE") }
                }
                if (highlightMuscle != null) {
                    Text(
                        "Showing ${highlightMuscle} alternatives. Clear the filter to see everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExerciseBrowser(exercises, onPick, Modifier.weight(1f), highlightMuscle = highlightMuscle)
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = total % 3600 / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
