package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.ExerciseEntity
import app.ironlog.personal.data.db.ProgramDayExerciseEntity
import app.ironlog.personal.data.db.ProgramEntity
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.data.db.displayName
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.ExerciseCandidate
import app.ironlog.personal.domain.ExerciseOption
import app.ironlog.personal.domain.ExperienceLevel
import app.ironlog.personal.domain.QuickWorkoutGenerator
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.launch

private val QUICK_TARGETS =
    linkedMapOf(
        "Full body" to setOf("chest", "lats", "middle back", "shoulders", "quadriceps", "hamstrings", "glutes"),
        "Upper" to setOf("chest", "lats", "middle back", "shoulders", "biceps", "triceps"),
        "Lower" to setOf("quadriceps", "hamstrings", "glutes", "calves"),
        "Push" to setOf("chest", "shoulders", "triceps"),
        "Pull" to setOf("lats", "middle back", "biceps", "traps"),
        "Arms" to setOf("biceps", "triceps", "forearms"),
        "Core" to setOf("abdominals", "lower back"),
        // Stretching exercises from the library, for recovery days.
        "Mobility" to app.ironlog.personal.domain.Recommender.ALL_MUSCLES.toSet(),
    )

@Composable
fun TrainScreen(container: AppContainer, nav: Navigator) {
    val active by container.workouts.active.collectAsState(initial = null)
    val history by container.workouts.history.collectAsState(initial = emptyList())
    val exercises by container.workouts.exercises.collectAsState(initial = emptyList())
    val programs by container.programs.programs.collectAsState(initial = emptyList())
    val activeProgram by container.programs.active.collectAsState(initial = null)
    val profile by container.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var quickFocus by remember { mutableStateOf("Full body") }
    var quickMinutes by remember { mutableIntStateOf(45) }
    var confirmFinish by remember { mutableStateOf(false) }

    Page(
        "Train",
        actions = {
            IconButton(onClick = { nav.open(Routes.HISTORY) }) {
                Icon(Icons.Filled.History, contentDescription = "Workout history")
            }
        },
    ) {
        active?.let { session ->
            IronCard {
                Eyebrow("In progress")
                Text(session.name, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(
                        "Resume",
                        icon = Icons.Filled.PlayArrow,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                if (session.status == "PAUSED") container.workouts.resume(session.id)
                                nav.workout(session.id)
                            }
                        },
                    )
                    SecondaryButton("Finish", { confirmFinish = true }, Modifier.weight(1f))
                }
            }
            if (confirmFinish) {
                ConfirmDialog(
                    title = "Finish this workout?",
                    body = "Completed sets are saved to your history.",
                    confirm = "Finish",
                    onConfirm = {
                        confirmFinish = false
                        scope.launch {
                            container.workouts.finish(session.id)
                            container.restTimer.skip()
                        }
                    },
                    onDismiss = { confirmFinish = false },
                )
            }
        }

        val current = programs.firstOrNull { it.id == activeProgram?.programId }
        SectionHeader("Your program")
        if (current != null) {
            ProgramCard(current, isActive = true, onClick = { nav.open(Routes.program(current.id)) })
        } else {
            IronCard {
                Text("No active program", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Activate a program below or build one tailored to your profile.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SecondaryButton(
            "Build a program for me",
            { nav.open(Routes.BUILDER) },
            icon = Icons.Filled.AutoAwesome,
        )

        SectionHeader("Cardio & conditioning")
        IronCard {
            Text(
                "Runs, rides, swims, HIIT and sport count toward your week, XP and achievements. Use the Mobility quick workout on recovery days.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton("Log cardio", { nav.open(Routes.CARDIO) }, icon = Icons.Filled.DirectionsRun)
        }

        SectionHeader("All programs")
        programs.filter { it.id != current?.id }.forEach { program ->
            ProgramCard(program, isActive = false, onClick = { nav.open(Routes.program(program.id)) })
        }

        SectionHeader("Quick workout")
        IronCard {
            Text(
                "Generated from the exercise library for your equipment.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipRow(QUICK_TARGETS.keys.toList(), quickFocus, { it }, { quickFocus = it })
            ChipRow(listOf(15, 30, 45, 60), quickMinutes, { "$it min" }, { quickMinutes = it })
            PrimaryButton(
                "Generate & start",
                icon = Icons.Filled.Bolt,
                enabled = active == null && exercises.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val id =
                            startQuickWorkout(
                                container,
                                exercises,
                                profile,
                                quickFocus,
                                QUICK_TARGETS.getValue(quickFocus),
                                quickMinutes,
                            )
                        id?.let(nav::workout)
                    }
                },
            )
            TextButton(
                enabled = active == null,
                onClick = {
                    scope.launch { nav.workout(container.workouts.start("Empty workout", emptyList())) }
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("START EMPTY WORKOUT")
            }
        }

        ListRow(
            title = "Workout history",
            subtitle = "${history.size} completed sessions",
            onClick = { nav.open(Routes.HISTORY) },
            leading = { Icon(Icons.Filled.History, contentDescription = null) },
            trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        )
    }
}

@Composable
private fun ProgramCard(program: ProgramEntity, isActive: Boolean, onClick: () -> Unit) {
    IronCard(onClick = onClick) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) Pill("Active", IronTheme.colors.accent.copy(alpha = 0.25f))
            Pill(if (program.isBuiltIn) "Built-in" else "Custom")
            Pill("${program.daysPerWeek} days / week")
        }
        Text(program.displayName.uppercase(), style = MaterialTheme.typography.titleLarge)
        if (program.description.isNotBlank()) {
            Text(
                program.description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun equipmentFor(profile: UserProfileEntity?): Set<String> =
    if (profile?.equipment.equals("BODYWEIGHT", ignoreCase = true)) setOf("body only")
    else GYM_EQUIPMENT

private suspend fun startQuickWorkout(
    container: AppContainer,
    exercises: List<ExerciseEntity>,
    profile: UserProfileEntity?,
    label: String,
    targets: Set<String>,
    minutes: Int,
): Long? {
    val mobility = label == "Mobility"
    val candidates =
        exercises
            .filter { if (mobility) it.category.equals("stretching", true) else it.category.equals("strength", true) || it.isCustom }
            .map {
                ExerciseCandidate(
                    id = it.id,
                    name = it.name,
                    primaryMuscles = it.primaryMuscleList.toSet(),
                    equipment = listOfNotNull(it.equipment).toSet(),
                )
            }
            // Only exercises that hit the chosen focus are useful here.
            .filter { candidate -> candidate.primaryMuscles.any { it in targets } }
    val avoid =
        profile?.avoidList.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty).toSet()
    // Spread picks across the target muscles instead of taking alphabetically-first matches.
    val pool =
        QuickWorkoutGenerator.generate(candidates, targets, if (mobility) emptySet() else equipmentFor(profile), avoid, 240)
    val perMuscle = pool.groupBy { it.primaryMuscles.first { m -> m in targets } }
    val limit = QuickWorkoutGenerator.exerciseCount(minutes)
    val picked = mutableListOf<ExerciseCandidate>()
    var round = 0
    while (picked.size < limit && perMuscle.values.any { it.size > round }) {
        targets.forEach { muscle ->
            perMuscle[muscle]?.getOrNull(round)?.let { if (picked.size < limit) picked += it }
        }
        round++
    }
    if (picked.isEmpty()) return null
    return container.workouts.start("Quick · $label", picked.map { Triple(it.id, it.name, if (mobility) 2 else 3) })
}

/** Program builder: suggests a split from the profile and saves it as a custom program. */
@Composable
fun ProgramBuilderScreen(container: AppContainer, nav: Navigator) {
    val exercises by container.workouts.exercises.collectAsState(initial = emptyList())
    val profile by container.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var days by remember { mutableIntStateOf(3) }
    var minutes by remember { mutableIntStateOf(45) }
    var goal by remember { mutableStateOf("BUILD_MUSCLE") }
    var experience by remember { mutableStateOf(ExperienceLevel.BEGINNER) }
    var avoid by remember { mutableStateOf("") }

    profile?.let { current ->
        LaunchedEffect(current.id) {
            days = current.daysPerWeek.coerceIn(2, 6)
            minutes = current.sessionMinutes
            goal = current.goal
            experience =
                runCatching { ExperienceLevel.valueOf(current.experience) }
                    .getOrDefault(ExperienceLevel.BEGINNER)
            avoid = current.avoidList
        }
    }

    val namesById = remember(exercises) { exercises.associate { it.id to it.name } }
    val recommendation =
        remember(exercises, days, minutes, goal, experience, avoid, profile?.equipment) {
            Recommender.suggest(
                TrainingProfile(
                    daysPerWeek = days,
                    goal = goal,
                    equipment = equipmentFor(profile),
                    experience = experience,
                    sessionMinutes = minutes,
                    avoidList = avoid.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                ),
                exercises
                    .filter { it.category.equals("strength", true) }
                    .map {
                        ExerciseOption(
                            id = it.id,
                            name = it.name,
                            primaryMuscles = it.primaryMuscleList.toSet(),
                            equipment = it.equipment,
                            level = it.level,
                            mechanic = it.mechanic,
                        )
                    },
            )
        }

    Page("Program builder", onBack = { nav.back() }) {
        Eyebrow("Days per week")
        ChipRow((2..6).toList(), days, { "$it" }, { days = it })
        Eyebrow("Session length")
        ChipRow(listOf(30, 45, 60, 75, 90), minutes, { "$it min" }, { minutes = it })
        Eyebrow("Goal")
        ChipRow(
            listOf("BUILD_MUSCLE", "GET_STRONGER", "LOSE_FAT", "MAINTAIN"),
            goal,
            { it.replace('_', ' ') },
            { goal = it },
        )
        Eyebrow("Experience")
        ChipRow(
            ExperienceLevel.entries.toList(),
            experience,
            { it.name.lowercase().replaceFirstChar(Char::uppercase) },
            { experience = it },
        )
        Field("Exercises or muscles to avoid (comma separated)", avoid, { avoid = it })

        SectionHeader(recommendation.template)
        Text(recommendation.why, color = MaterialTheme.colorScheme.onSurfaceVariant)
        recommendation.days.forEach { day ->
            IronCard {
                Text(day.name.uppercase(), style = MaterialTheme.typography.titleMedium)
                if (day.exercises.isEmpty()) {
                    Text("No matching exercises for this day with the current filters.")
                }
                day.exercises.forEach { prescription ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            namesById[prescription.exerciseId] ?: prescription.exerciseId,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${prescription.sets} × ${prescription.repMin}–${prescription.repMax}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        message?.let { Text(it, color = IronTheme.colors.accent) }
        PrimaryButton(
            "Save and activate",
            enabled = profile != null && recommendation.days.any { it.exercises.isNotEmpty() },
            onClick = {
                scope.launch {
                    val current = profile ?: return@launch
                    val programDays =
                        recommendation.days.map { day ->
                            day.name to
                                day.exercises.mapIndexed { order, prescription ->
                                    ProgramDayExerciseEntity(
                                        programDayId = 0,
                                        exerciseId = prescription.exerciseId,
                                        orderIndex = order,
                                        targetSets = prescription.sets,
                                        repMin = prescription.repMin,
                                        repMax = prescription.repMax,
                                        restSeconds = prescription.restSeconds,
                                    )
                                }
                        }
                    val programId =
                        container.programs.saveRecommended(
                            recommendation.template,
                            recommendation.why,
                            days,
                            programDays,
                        )
                    container.programs.activate(programId)
                    container.saveProfile(
                        current.copy(
                            daysPerWeek = days,
                            sessionMinutes = minutes,
                            goal = goal,
                            experience = experience.name,
                            avoidList = avoid,
                        )
                    )
                    message = "Saved and activated ${recommendation.template}."
                    nav.replace(Routes.program(programId))
                }
            },
        )
    }
}

/** Program overview: schedule, per-day prescriptions, activation and starting any day. */
@Composable
fun ProgramDetailScreen(container: AppContainer, nav: Navigator, programId: Long) {
    val programs by container.programs.programs.collectAsState(initial = emptyList())
    val program = programs.firstOrNull { it.id == programId }
    val activeProgram by container.programs.active.collectAsState(initial = null)
    val active by container.workouts.active.collectAsState(initial = null)
    val exercises by container.workouts.exercises.collectAsState(initial = emptyList())
    val days by remember(programId) { container.programs.days(programId) }.collectAsState(initial = emptyList())
    val prescriptionsByDay by
        remember(days) { container.programs.prescriptionsForDays(days) }
            .collectAsState(initial = emptyMap())
    val scope = rememberCoroutineScope()
    val isActive = activeProgram?.programId == programId
    val nextDayId =
        days.getOrNull(((activeProgram?.currentDay ?: 1) - 1).coerceAtLeast(0).mod(days.size.coerceAtLeast(1)))?.id
    val namesById = remember(exercises) { exercises.associate { it.id to it.name } }
    var editing by remember { mutableStateOf(false) }

    if (program == null) {
        Page("Program", onBack = { nav.back() }) { Text("This program no longer exists.") }
        return
    }
    Page(program.displayName, onBack = { nav.back() }) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isActive) Pill("Active", IronTheme.colors.accent.copy(alpha = 0.25f))
            Pill("${program.daysPerWeek} days / week")
            Pill("${days.size} sessions per cycle")
        }
        if (program.description.isNotBlank()) {
            Text(program.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isActive) {
            SecondaryButton("Active program", {}, enabled = false)
        } else {
            PrimaryButton("Activate program", { scope.launch { container.programs.activate(program.id) } })
        }
        if (!program.isBuiltIn && program.name.endsWith(" (Recommended)")) {
            TextButton(onClick = { editing = !editing }) {
                Text(if (editing) "CLOSE" else "RENAME PROGRAM")
            }
            if (editing) RenameProgram(container, program) { editing = false }
        }

        SectionHeader("Schedule")
        days.forEachIndexed { index, day ->
            val prescriptions = prescriptionsByDay[day.id].orEmpty()
            IronCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Day ${index + 1}")
                        Text(day.name.uppercase(), style = MaterialTheme.typography.titleLarge)
                    }
                    if (isActive && day.id == nextDayId) Pill("Up next", IronTheme.colors.accent.copy(alpha = 0.25f))
                }
                if (day.isRest) Text("Rest day")
                prescriptions.forEach { p ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(namesById[p.exerciseId] ?: p.exerciseId, modifier = Modifier.weight(1f))
                        Text(
                            "${p.targetSets} × ${p.repMin}–${p.repMax}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!day.isRest && prescriptions.isNotEmpty()) {
                    SecondaryButton(
                        if (active != null) "Finish current workout first" else "Start ${day.name}",
                        enabled = active == null,
                        icon = Icons.Filled.PlayArrow,
                        onClick = {
                            scope.launch {
                                if (!isActive) container.programs.activate(program.id)
                                nav.workout(container.programs.start(program.id, day.id))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RenameProgram(container: AppContainer, program: ProgramEntity, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(program.id) { mutableStateOf(program.name.removeSuffix(" (Recommended)")) }
    var description by remember(program.id) { mutableStateOf(program.description) }
    var error by remember { mutableStateOf<String?>(null) }
    Field("Program name", name, { name = it })
    Field("Description", description, { description = it })
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    PrimaryButton(
        "Save",
        enabled = name.isNotBlank(),
        onClick = {
            scope.launch {
                runCatching { container.programs.renameRecommended(program.id, name.trim(), description) }
                    .onSuccess { onDone() }
                    .onFailure { error = it.message }
            }
        },
    )
}

private val GYM_EQUIPMENT =
    setOf(
        "barbell",
        "dumbbell",
        "cable",
        "machine",
        "body only",
        "kettlebells",
        "bands",
        "e-z curl bar",
        "exercise ball",
        "medicine ball",
        "other",
    )
