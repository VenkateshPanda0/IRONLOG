package app.ironlog.personal.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.ProgramDayExerciseEntity
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.ExerciseOption
import app.ironlog.personal.domain.ExperienceLevel
import app.ironlog.personal.domain.RecommendedDay
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.theme.IronTheme
import java.time.LocalDate
import kotlinx.coroutines.launch

private const val STEPS = 6

private val GOALS =
    listOf(
        Choice("LOSE_FAT", "Lose fat", "Calorie deficit while keeping strength"),
        Choice("BUILD_MUSCLE", "Build muscle", "Hypertrophy focus with a small surplus"),
        Choice("GET_STRONGER", "Get stronger", "Heavier compounds, lower rep ranges"),
        Choice("MAINTAIN", "Maintain", "Train consistently at maintenance calories"),
    )

private val ACTIVITY =
    listOf(
        1.2 to "Sedentary · desk job, little walking",
        1.375 to "Light · on your feet some of the day",
        1.55 to "Moderate · active job or lots of walking",
        1.725 to "Very active · physical work every day",
    )

private data class Choice(val key: String, val title: String, val body: String)

@Composable
fun OnboardingScreen(c: AppContainer, onImport: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var sex by rememberSaveable { mutableStateOf("UNSPECIFIED") }
    var weight by rememberSaveable { mutableStateOf("") }
    var height by rememberSaveable { mutableStateOf("") }
    var age by rememberSaveable { mutableStateOf("") }
    var goal by rememberSaveable { mutableStateOf("BUILD_MUSCLE") }
    var activity by rememberSaveable { mutableDoubleStateOf(1.375) }
    var experience by rememberSaveable { mutableStateOf(ExperienceLevel.BEGINNER) }
    var weekdays by rememberSaveable { mutableStateOf(listOf("MON", "WED", "FRI")) }
    var minutes by rememberSaveable { mutableIntStateOf(60) }
    var equipment by rememberSaveable { mutableStateOf("GYM") }
    var physique by rememberSaveable { mutableStateOf("") }
    val physiqueType = app.ironlog.personal.ui.physique.physiqueGoalOf(physique)
    var saving by remember { mutableStateOf(false) }

    val unit = LocalWeightUnit.current
    val w = unit.parse(weight)?.takeIf { it in 25.0..400.0 }
    val h = height.toDoubleOrNull()?.takeIf { it in 100.0..250.0 }
    val a = age.toIntOrNull()?.takeIf { it in 13..100 }
    val daysPerWeek = weekdays.size

    val exercises by c.workouts.exercises.collectAsState(initial = emptyList())
    val suggestion =
        remember(exercises, daysPerWeek, goal, experience, minutes, equipment, physiqueType) {
            Recommender.suggest(
                TrainingProfile(
                    daysPerWeek = daysPerWeek.coerceIn(2, 6),
                    goal = goal,
                    equipment = if (equipment == "BODYWEIGHT") setOf("body only") else GYM_EQUIPMENT,
                    experience = experience,
                    sessionMinutes = minutes,
                    avoidList = emptySet(),
                    priorityMuscles = physiqueType?.emphasis.orEmpty(),
                ),
                exercises
                    .filter { it.category.equals("strength", ignoreCase = true) }
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
    val kcal = if (w != null && h != null && a != null) Calculations.targetCalories(sex, w, h, a, activity, goal) else null
    val macros = kcal?.let { Calculations.macroTargets(it, w!!) }

    val canContinue =
        when (step) {
            0 -> true
            1 -> w != null && h != null && a != null
            4 -> daysPerWeek in 2..6
            else -> true
        }

    BackHandler(enabled = step > 0) { step-- }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { step-- }, enabled = step > 0) {
                if (step > 0) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(STEPS) { index ->
                    Box(
                        Modifier.weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (index <= step) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                }
            }
            Spacer(Modifier.width(48.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(24.dp))
                    Text("IRONLOG", style = MaterialTheme.typography.displayMedium)
                    Text(
                        "Train with a plan. Log every set. Track what you eat. Your data stays on this phone and in your own phone backup.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Field("What should we call you?", name, { name = it })
                    RestoreOptions(c, onImport)
                }
                1 -> {
                    StepTitle("About you", "Used for calorie and macro estimates.")
                    Eyebrow("Sex")
                    ChipRow(listOf("MALE", "FEMALE", "UNSPECIFIED"), sex, { it.lowercase().replaceFirstChar(Char::uppercase) }, { sex = it })
                    Field("Age", age, { age = it }, number = true)
                    Field("Height (cm)", height, { height = it }, number = true)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Field("Weight (${unit.label})", weight, { weight = it }, number = true, modifier = Modifier.weight(1f))
                        UnitSwitch(unit) { next ->
                            // Keep the typed amount meaning the same weight in the new unit.
                            unit.parse(weight)?.let { weight = next.number(it) }
                            scope.launch { c.setWeightUnit(next) }
                        }
                    }
                    if (a != null && a < 18) {
                        Text(
                            "Under 18: calorie targets are not estimated. You can set them yourself later.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                2 -> {
                    StepTitle("Your goal", "This shapes your program and calorie target.")
                    GOALS.forEach { choice ->
                        OptionCard(choice.title, choice.body, goal == choice.key) { goal = choice.key }
                    }
                    Eyebrow("Daily activity outside training")
                    ACTIVITY.forEach { (factor, label) ->
                        OptionCard(label.substringBefore(" ·"), label.substringAfter("· "), activity == factor) {
                            activity = factor
                        }
                    }
                }
                3 -> {
                    StepTitle("Your goal look", "Pick the physique you are training for. Later, compare your tape measurements with it.")
                    app.ironlog.personal.ui.physique.PhysiqueTypePicker(
                        app.ironlog.personal.domain.PhysiqueType.forSex(sex),
                        physiqueType,
                    ) { physique = if (physique == it.name) "" else it.name }
                    Text(
                        if (physiqueType == null) "Optional: skip if you are not sure yet." else "Your program will give extra volume to ${physiqueType.emphasis.take(3).joinToString()}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                4 -> {
                    StepTitle("Training", "Pick the days you can train. 2 to 6 days.")
                    WeekdaySelector(weekdays.toSet()) { day ->
                        weekdays = if (day in weekdays) weekdays - day else weekdays + day
                    }
                    Text(
                        "$daysPerWeek days per week",
                        color = if (daysPerWeek in 2..6) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                    Eyebrow("Experience")
                    ChipRow(ExperienceLevel.entries.toList(), experience, { it.name.lowercase().replaceFirstChar(Char::uppercase) }, { experience = it })
                    Eyebrow("Session length")
                    ChipRow(listOf(30, 45, 60, 75, 90), minutes, { "$it min" }, { minutes = it })
                    Eyebrow("Where do you train?")
                    OptionCard("Gym", "Barbells, dumbbells, cables and machines", equipment == "GYM") { equipment = "GYM" }
                    OptionCard("Bodyweight", "No equipment needed", equipment == "BODYWEIGHT") { equipment = "BODYWEIGHT" }
                }
                else -> {
                    StepTitle("Your plan", "You can change all of this later.")
                    IronCard {
                        Eyebrow("Program")
                        Text(suggestion.template.uppercase(), style = MaterialTheme.typography.headlineSmall)
                        Text(suggestion.why, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        suggestion.days.forEach { day ->
                            Text("${day.name} · ${day.exercises.size} exercises", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                    IronCard {
                        Eyebrow("Daily nutrition")
                        if (macros != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text("${macros.kcal}", style = MaterialTheme.typography.displaySmall)
                                Text(" kcal", modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                StatTile("Protein", "%.0f".format(macros.proteinG), Modifier.weight(1f), "g")
                                StatTile("Carbs", "%.0f".format(macros.carbsG), Modifier.weight(1f), "g")
                                StatTile("Fat", "%.0f".format(macros.fatG), Modifier.weight(1f), "g")
                            }
                            Text(
                                "Mifflin–St Jeor estimate. Not medical advice.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text("Set your calorie and macro targets in Settings.")
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Box(Modifier.padding(20.dp)) {
            PrimaryButton(
                text = if (step < STEPS - 1) "Continue" else "Start training",
                enabled = canContinue && !saving,
                onClick = {
                    if (step < STEPS - 1) {
                        step++
                        return@PrimaryButton
                    }
                    val kg = w ?: return@PrimaryButton
                    saving = true
                    scope.launch {
                        if (suggestion.days.any { it.exercises.isNotEmpty() }) {
                            val programId =
                                c.programs.saveRecommended(
                                    name = suggestion.template,
                                    description = suggestion.why,
                                    daysPerWeek = daysPerWeek,
                                    days = programDays(suggestion.days),
                                )
                            c.programs.activate(programId)
                        }
                        macros?.let {
                            c.goals.save(GoalEntity(kcalTarget = it.kcal, proteinG = it.proteinG, carbsG = it.carbsG, fatG = it.fatG))
                        }
                        c.body.log(LocalDate.now(), kg, "Starting weight")
                        // Saving the profile last switches the app to the main screens.
                        c.saveProfile(
                            UserProfileEntity(
                                name = name.trim(),
                                sex = sex,
                                weightKg = kg,
                                heightCm = h ?: 170.0,
                                age = a ?: 30,
                                activity = activity,
                                goal = goal,
                                equipment = equipment,
                                daysPerWeek = daysPerWeek,
                                experience = experience.name,
                                sessionMinutes = minutes,
                                trainingWeekdays = WEEKDAYS.filter { it in weekdays }.joinToString(","),
                                physiqueGoal = physique,
                            )
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Spacer(Modifier.height(8.dp))
    Text(title.uppercase(), style = MaterialTheme.typography.headlineLarge)
    Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionCard(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun programDays(days: List<RecommendedDay>): List<Pair<String, List<ProgramDayExerciseEntity>>> =
    days.map { day ->
        day.name to
            day.exercises.mapIndexed { index, p ->
                ProgramDayExerciseEntity(
                    programDayId = 0,
                    exerciseId = p.exerciseId,
                    orderIndex = index,
                    targetSets = p.sets,
                    repMin = p.repMin,
                    repMax = p.repMax,
                    restSeconds = p.restSeconds,
                )
            }
    }

private val WEEKDAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
fun WeekdaySelector(selected: Set<String>, onToggle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WEEKDAYS.forEach { day ->
            val on = day in selected
            Surface(
                onClick = { onToggle(day) },
                shape = CircleShape,
                color = if (on) IronTheme.colors.accent else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (on) IronTheme.colors.onAccent else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(day.take(1), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
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

/**
 * New phone? Android restores Ironlog's snapshot from the user's Google account on install, so a
 * "welcome back" card can bring everything back in one tap. A backup file works anywhere.
 */
@Composable
private fun RestoreOptions(c: AppContainer, onImport: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snapshot by rememberLoaded<app.ironlog.personal.data.backup.SnapshotInfo?>(null, c) { c.autoBackup.available() }
    var restoring by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    snapshot?.let { s ->
        IronCard {
            Eyebrow("Backup found")
            Text(if (s.name.isBlank()) "WELCOME BACK" else "WELCOME BACK, ${s.name.uppercase()}", style = MaterialTheme.typography.titleLarge)
            val date = java.time.Instant.ofEpochMilli(s.exportedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            Text(
                "Your progress from ${date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"))}: " +
                    "${s.workouts} workout${if (s.workouts == 1) "" else "s"}" + (if (s.photos > 0) " and ${s.photos} progress photo${if (s.photos == 1) "" else "s"}" else "") +
                    ", with your program, food log, levels and medals.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryButton(
                if (restoring) "Restoring…" else "Restore my progress",
                enabled = !restoring,
                onClick = {
                    restoring = true
                    scope.launch {
                        // Restoring brings back the profile, which ends onboarding.
                        runCatching { c.autoBackup.restore() }.onFailure {
                            error = "Restore failed: ${it.message ?: "the backup could not be read"}"
                            restoring = false
                        }
                    }
                },
            )
        }
    }
    TextButton(onClick = onImport, contentPadding = PaddingValues(0.dp)) { Text("RESTORE FROM A BACKUP FILE") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

/** Compact kg / lb toggle next to a weight field. */
@Composable
fun UnitSwitch(selected: app.ironlog.personal.domain.WeightUnit, onSelect: (app.ironlog.personal.domain.WeightUnit) -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        app.ironlog.personal.domain.WeightUnit.entries.forEach { u ->
            val on = u == selected
            Surface(
                onClick = { if (!on) onSelect(u) },
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            ) {
                Text(u.label.uppercase(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
