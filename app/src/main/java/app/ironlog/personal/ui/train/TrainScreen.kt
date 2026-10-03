package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ironlog.personal.AppContainer
import app.ironlog.personal.IronlogViewModelFactory
import app.ironlog.personal.data.db.SetLogEntity
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.domain.ExerciseCandidate
import app.ironlog.personal.domain.QuickWorkoutGenerator
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import app.ironlog.personal.ui.workouts.WorkoutViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrainScreen(container: AppContainer) {
    val workoutViewModel: WorkoutViewModel = viewModel(factory = IronlogViewModelFactory(container))
    val state by workoutViewModel.state.collectAsState()
    val exercises by workoutViewModel.exercises.collectAsState(initial = emptyList())
    val programs by container.programs.programs.collectAsState(initial = emptyList())
    val profile by container.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var selectedSession by remember { mutableStateOf<WorkoutSessionEntity?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var exerciseName by remember { mutableStateOf("") }

    val activeWorkout = selectedSession
    when {
        activeWorkout != null -> ActiveWorkoutScreen(
            container = container,
            viewModel = workoutViewModel,
            session = activeWorkout,
            onClose = { selectedSession = null },
        )
        showHistory -> HistoryScreen(state.history, onBack = { showHistory = false })
        else -> Page("Train") {
            state.active?.let { active ->
                Text("Workout in progress · ${active.name}", style = MaterialTheme.typography.titleLarge)
                Button(onClick = {
                    scope.launch {
                        if (active.status == "PAUSED") workoutViewModel.resume(active.id)
                        selectedSession = workoutViewModel.session(active.id)
                    }
                }) { Text("Resume workout") }
                OutlinedButton(onClick = {
                    scope.launch {
                        workoutViewModel.finish(active.id)
                        container.restTimer.skip()
                    }
                }) { Text("Finish workout") }
            }

            Text("Programs", style = MaterialTheme.typography.titleLarge)
            if (programs.isEmpty()) {
                Text("No programs are available yet. Add an exercise to start a quick workout.")
            } else {
                programs.forEach { program -> Text(program.name) }
            }

            Field("Add a custom exercise", exerciseName, { exerciseName = it })
            OutlinedButton(onClick = {
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
            }) { Text("Save exercise") }
            Text("Exercise library · ${exercises.size} on-device entries")
            Button(
                enabled = state.active == null && exercises.isNotEmpty(),
                onClick = {
                    scope.launch {
                        val availableEquipment = if (profile?.equipment.equals("BODYWEIGHT", ignoreCase = true)) {
                            setOf("body only")
                        } else {
                            setOf("barbell", "dumbbell", "cable", "machine", "body only", "kettlebells")
                        }
                        val candidates = exercises.map { exercise ->
                            ExerciseCandidate(
                                id = exercise.id,
                                name = exercise.name,
                                primaryMuscles = runCatching {
                                    Json.parseToJsonElement(exercise.primaryMuscles).jsonArray.mapNotNull { muscle ->
                                        muscle.jsonPrimitive.contentOrNull
                                    }.toSet()
                                }.getOrDefault(emptySet()),
                                equipment = listOfNotNull(exercise.equipment).toSet(),
                            )
                        }
                        val targetMuscles = setOf(
                            "chest", "back", "shoulders", "quadriceps", "hamstrings", "glutes", "abdominals",
                        )
                        val generated = QuickWorkoutGenerator.generate(
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
            ) { Text("Start quick workout") }
            TextButton(onClick = { showHistory = true }) {
                Text("Workout history (${state.history.size})")
            }
        }
    }
}

