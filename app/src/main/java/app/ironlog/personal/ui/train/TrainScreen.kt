package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ironlog.personal.AppContainer
import app.ironlog.personal.IronlogViewModelFactory
import app.ironlog.personal.data.db.ProgramDayExerciseEntity
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.domain.ExerciseCandidate
import app.ironlog.personal.domain.ExerciseOption
import app.ironlog.personal.domain.ExperienceLevel
import app.ironlog.personal.domain.QuickWorkoutGenerator
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import app.ironlog.personal.ui.workouts.WorkoutViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

@Composable
fun TrainScreen(container: AppContainer) {
    val workoutViewModel: WorkoutViewModel = viewModel(factory = IronlogViewModelFactory(container))
    val state by workoutViewModel.state.collectAsState()
    val exercises by workoutViewModel.exercises.collectAsState(initial = emptyList())
    val programs by container.programs.programs.collectAsState(initial = emptyList())
    val activeProgram by container.programs.active.collectAsState(initial = null)
    val profile by container.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedSession by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var exerciseName by remember { mutableStateOf("") }
    var goalProfileMessage by remember { mutableStateOf<String?>(null) }
    var showRecommendation by remember { mutableStateOf(false) }
    var recommendationDays by remember { mutableStateOf("3") }
    var recommendationMinutes by remember {
        mutableStateOf(profile?.sessionMinutes?.toString() ?: "45")
    }
    var recommendationGoal by remember { mutableStateOf(profile?.goal ?: "MAINTAIN") }
    var recommendationExperience by remember {
        mutableStateOf(
            runCatching { ExperienceLevel.valueOf(profile?.experience ?: "BEGINNER") }
                .getOrDefault(ExperienceLevel.BEGINNER)
        )
    }
    var recommendationAvoid by remember { mutableStateOf(profile?.avoidList ?: "") }

    profile?.let { current ->
        LaunchedEffect(current) {
            recommendationDays = current.daysPerWeek.toString()
            recommendationMinutes = current.sessionMinutes.toString()
            recommendationGoal = current.goal
            recommendationExperience =
                runCatching { ExperienceLevel.valueOf(current.experience) }
                    .getOrDefault(ExperienceLevel.BEGINNER)
            recommendationAvoid = current.avoidList
        }
    }

    val activeWorkout = selectedSession
    when {
        activeWorkout != null ->
            ActiveWorkoutScreen(
                container = container,
                viewModel = workoutViewModel,
                session = activeWorkout,
                onClose = { selectedSession = null },
            )
        showHistory -> HistoryScreen(state.history, onBack = { showHistory = false })
        else ->
            Page("Train") {
                state.active?.let { active ->
                    Text(
                        "Workout in progress · ${active.name}",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                if (active.status == "PAUSED") workoutViewModel.resume(active.id)
                                selectedSession = workoutViewModel.session(active.id)
                            }
                        }
                    ) {
                        Text("Resume workout")
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                workoutViewModel.finish(active.id)
                                container.restTimer.skip()
                            }
                        }
                    ) {
                        Text("Finish workout")
                    }
                }

                Text("Programs", style = MaterialTheme.typography.titleLarge)
                if (programs.isEmpty()) {
                    Text("No programs are available yet. Add an exercise to start a quick workout.")
                } else {
                    programs.forEach { program ->
                        Text(program.name, style = MaterialTheme.typography.titleMedium)
                        Text(program.description)
                        if (!program.isBuiltIn) {
                            val editingRecommendedProgram = program.name.endsWith(" (Recommended)")
                            var editedProgramName by
                                remember(program.id, program.name) {
                                    mutableStateOf(program.name.removeSuffix(" (Recommended)"))
                                }
                            var editedProgramDescription by
                                remember(program.id, program.description) {
                                    mutableStateOf(program.description)
                                }
                            Field("Program name", editedProgramName, { editedProgramName = it })
                            Field(
                                "Program description",
                                editedProgramDescription,
                                { editedProgramDescription = it },
                            )
                            OutlinedButton(
                                enabled =
                                    editingRecommendedProgram && editedProgramName.isNotBlank(),
                                onClick = {
                                    scope.launch {
                                        runCatching {
                                            container.programs.renameRecommended(
                                                program.id,
                                                editedProgramName.trim(),
                                                editedProgramDescription,
                                            )
                                        }
                                            .onSuccess {
                                                goalProfileMessage = "Program details saved."
                                            }
                                            .onFailure { goalProfileMessage = it.message }
                                    }
                                },
                            ) {
                                Text("Save program details")
                            }
                        }
                        OutlinedButton(
                            enabled = activeProgram?.programId != program.id,
                            onClick = { scope.launch { container.programs.activate(program.id) } },
                        ) {
                            Text(
                                if (activeProgram?.programId == program.id) "Active program"
                                else "Activate"
                            )
                        }
                        val programDays by
                            container.programs
                                .days(program.id)
                                .collectAsState(initial = emptyList())
                        val isActiveProgram = activeProgram?.programId == program.id
                        val nextProgramDayId =
                            programDays
                                .getOrNull(
                                    ((activeProgram?.currentDay ?: 1) - 1)
                                        .coerceAtLeast(0)
                                        .mod(programDays.size.coerceAtLeast(1))
                                )
                                ?.id
                        val prescriptionsByDay by
                            container.programs
                                .prescriptionsForDays(programDays)
                                .collectAsState(initial = emptyMap())
                        programDays.forEach { day ->
                            Text(
                                if (isActiveProgram && day.id == nextProgramDayId) {
                                    "Next · ${day.name}"
                                } else {
                                    day.name
                                },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            val prescriptions = prescriptionsByDay[day.id].orEmpty()
                            prescriptions.forEach { prescription ->
                                val exerciseNameForProgram =
                                    exercises.firstOrNull { it.id == prescription.exerciseId }?.name
                                        ?: prescription.exerciseId
                                Text(
                                    "$exerciseNameForProgram · ${prescription.targetSets} × ${prescription.repMin}–${prescription.repMax}"
                                )
                            }
                            Button(
                                enabled =
                                    state.active == null &&
                                        !day.isRest &&
                                        (!isActiveProgram || day.id == nextProgramDayId),
                                onClick = {
                                    scope.launch {
                                        val sessionId = container.programs.start(program.id, day.id)
                                        selectedSession = workoutViewModel.session(sessionId)
                                    }
                                },
                            ) {
                                Text(
                                    if (isActiveProgram && day.id != nextProgramDayId) {
                                        "Finish next workout first"
                                    } else {
                                        "Start ${day.name}"
                                    }
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { showRecommendation = !showRecommendation }) {
                    Text(
                        if (showRecommendation) "Hide program builder"
                        else "Build a program for my profile"
                    )
                }
                if (showRecommendation) {
                    Field(
                        "Training days per week (2–6)",
                        recommendationDays,
                        { recommendationDays = it },
                        true,
                    )
                    Field(
                        "Session length in minutes",
                        recommendationMinutes,
                        { recommendationMinutes = it },
                        true,
                    )
                    Text("Goal")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("BUILD_MUSCLE", "GET_STRONGER", "LOSE_FAT", "MAINTAIN").forEach {
                            goal ->
                            FilterChip(
                                selected = recommendationGoal == goal,
                                onClick = { recommendationGoal = goal },
                                label = { Text(goal.replace('_', ' ')) },
                            )
                        }
                    }
                    Text("Experience")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExperienceLevel.entries.forEach { level ->
                            FilterChip(
                                selected = recommendationExperience == level,
                                onClick = { recommendationExperience = level },
                                label = {
                                    Text(level.name.lowercase().replaceFirstChar(Char::uppercase))
                                },
                            )
                        }
                    }
                    Field(
                        "Exercises or muscles to avoid",
                        recommendationAvoid,
                        { recommendationAvoid = it },
                    )
                    val equipment =
                        if (profile?.equipment.equals("BODYWEIGHT", true)) setOf("body only")
                        else GYM_EQUIPMENT
                    val recommendation =
                        Recommender.suggest(
                            TrainingProfile(
                                daysPerWeek = recommendationDays.toIntOrNull() ?: 3,
                                goal = recommendationGoal,
                                equipment = equipment,
                                experience = recommendationExperience,
                                sessionMinutes = recommendationMinutes.toIntOrNull() ?: 45,
                                avoidList =
                                    recommendationAvoid
                                        .split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .toSet(),
                            ),
                            exercises
                                .filter { it.category.equals("strength", true) }
                                .map { exercise ->
                                    ExerciseOption(
                                        id = exercise.id,
                                        name = exercise.name,
                                        primaryMuscles =
                                            runCatching {
                                                    Json.parseToJsonElement(exercise.primaryMuscles)
                                                        .jsonArray
                                                        .map { it.jsonPrimitive.content }
                                                        .toSet()
                                                }
                                                .getOrDefault(emptySet()),
                                        equipment = exercise.equipment,
                                        level = exercise.level,
                                        mechanic = exercise.mechanic,
                                    )
                                },
                        )
                    Text(
                        "Suggested · ${recommendation.template}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(recommendation.why)
                    recommendation.days.forEach { day ->
                        Text(
                            "${day.name} · ${day.exercises.size} exercises",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        day.exercises.forEach { prescription ->
                            val exerciseName =
                                exercises.firstOrNull { it.id == prescription.exerciseId }?.name
                                    ?: prescription.exerciseId
                            Text(
                                "$exerciseName · ${prescription.sets} × ${prescription.repMin}–${prescription.repMax}"
                            )
                        }
                    }
                    goalProfileMessage?.let { Text(it) }
                    Button(
                        enabled = recommendation.days.any { it.exercises.isNotEmpty() },
                        onClick = {
                            scope.launch {
                                profile?.let { current ->
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
                                            recommendationDays.toIntOrNull()?.coerceIn(2, 6) ?: 3,
                                            programDays,
                                        )
                                    container.programs.activate(programId)
                                    container.saveProfile(
                                        current.copy(
                                            daysPerWeek =
                                                recommendationDays.toIntOrNull()?.coerceIn(2, 6)
                                                    ?: current.daysPerWeek,
                                            sessionMinutes =
                                                recommendationMinutes
                                                    .toIntOrNull()
                                                    ?.coerceIn(20, 120) ?: current.sessionMinutes,
                                            goal = recommendationGoal,
                                            experience = recommendationExperience.name,
                                            avoidList = recommendationAvoid,
                                        )
                                    )
                                    goalProfileMessage =
                                        "Saved and activated ${recommendation.template}."
                                }
                            }
                        },
                    ) {
                        Text("Save and activate this program")
                    }
                }

                Field("Add a custom exercise", exerciseName, { exerciseName = it })
                OutlinedButton(
                    onClick = {
                        val name = exerciseName.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                workoutViewModel.addCustomExercise(
                                    name,
                                    "custom_${java.util.UUID.randomUUID()}",
                                )
                                exerciseName = ""
                            }
                        }
                    }
                ) {
                    Text("Save exercise")
                }
                Text("Exercise library · ${exercises.size} on-device entries")
                Button(
                    enabled = state.active == null && exercises.isNotEmpty(),
                    onClick = {
                        scope.launch {
                            val availableEquipment =
                                if (profile?.equipment.equals("BODYWEIGHT", ignoreCase = true)) {
                                    setOf("body only")
                                } else {
                                    setOf(
                                        "barbell",
                                        "dumbbell",
                                        "cable",
                                        "machine",
                                        "body only",
                                        "kettlebells",
                                    )
                                }
                            val candidates = exercises.map { exercise ->
                                ExerciseCandidate(
                                    id = exercise.id,
                                    name = exercise.name,
                                    primaryMuscles =
                                        runCatching {
                                                Json.parseToJsonElement(exercise.primaryMuscles)
                                                    .jsonArray
                                                    .mapNotNull { muscle ->
                                                        muscle.jsonPrimitive.contentOrNull
                                                    }
                                                    .toSet()
                                            }
                                            .getOrDefault(emptySet()),
                                    equipment = listOfNotNull(exercise.equipment).toSet(),
                                )
                            }
                            val targetMuscles =
                                setOf(
                                    "chest",
                                    "back",
                                    "shoulders",
                                    "quadriceps",
                                    "hamstrings",
                                    "glutes",
                                    "abdominals",
                                )
                            val generated =
                                QuickWorkoutGenerator.generate(
                                    exercises = candidates,
                                    targetMuscles = targetMuscles,
                                    equipment = availableEquipment,
                                    avoidList = emptySet(),
                                    minutes = 30,
                                )
                            val namesById = exercises.associateBy { it.id }
                            val rows = generated.mapNotNull { candidate ->
                                namesById[candidate.id]?.let { Triple(it.id, it.name, 3) }
                            }
                            val id = workoutViewModel.start("Quick workout", rows)
                            selectedSession = workoutViewModel.session(id)
                        }
                    },
                ) {
                    Text("Start quick workout")
                }
                TextButton(onClick = { showHistory = true }) {
                    Text("Workout history (${state.history.size})")
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
