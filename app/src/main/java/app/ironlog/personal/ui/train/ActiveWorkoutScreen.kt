package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.SetLogEntity
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.ui.components.Page
import app.ironlog.personal.ui.workouts.WorkoutViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ActiveWorkoutScreen(
    container: AppContainer,
    viewModel: WorkoutViewModel,
    session: WorkoutSessionEntity,
    onClose: () -> Unit,
) {
    val exercises by viewModel.sessionExercises(session.id).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var setsByExercise by remember { mutableStateOf<Map<Long, List<SetLogEntity>>>(emptyMap()) }
    var remainingMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(exercises) {
        exercises.forEach { exercise ->
            setsByExercise = setsByExercise + (exercise.id to viewModel.sets(exercise.id))
        }
    }
    LaunchedEffect(session.id) {
        while (true) {
            remainingMs = container.restTimer.remainingMs()
            delay(1_000)
        }
    }

    Page(session.name) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                onClick = {
                    scope.launch {
                        viewModel.pause(session.id)
                        onClose()
                    }
                }
            ) {
                Text("Pause")
            }
            Button(
                onClick = {
                    scope.launch {
                        viewModel.finish(session.id)
                        container.restTimer.skip()
                        onClose()
                    }
                }
            ) {
                Text("Finish workout")
            }
        }

        if (remainingMs > 0) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rest · %02d:%02d".format(remainingMs / 60_000, remainingMs % 60_000 / 1_000),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = { scope.launch { container.restTimer.adjust(-15) } }) {
                    Text("−15s")
                }
                TextButton(onClick = { scope.launch { container.restTimer.adjust(15) } }) {
                    Text("+15s")
                }
                TextButton(onClick = { scope.launch { container.restTimer.skip() } }) {
                    Text("Skip")
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(exercises, key = { it.id }) { exercise ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleLarge)
                    (setsByExercise[exercise.id] ?: emptyList()).forEach { set ->
                        WorkoutSetRow(
                            set = set,
                            onDraft = { weight, reps ->
                                scope.launch { viewModel.saveSetDraft(set.id, weight, reps) }
                            },
                            onComplete = { completed ->
                                scope.launch {
                                    viewModel.setCompleted(set.id, completed)
                                    setsByExercise =
                                        setsByExercise +
                                            (exercise.id to
                                                (setsByExercise[exercise.id].orEmpty().map { row ->
                                                    if (row.id == set.id)
                                                        row.copy(isCompleted = completed)
                                                    else row
                                                }))
                                    if (completed)
                                        container.restTimer.start(session.id, exercise.restSeconds)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
