package app.ironlog.personal.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutUiState(
    val active: WorkoutSessionEntity? = null,
    val history: List<WorkoutSessionEntity> = emptyList(),
)

class WorkoutViewModel(private val repository: WorkoutRepository) : ViewModel() {
    val state: StateFlow<WorkoutUiState> =
        combine(repository.active, repository.history) { active, history ->
                WorkoutUiState(active, history)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    val exercises = repository.exercises

    fun sessionExercises(sessionId: Long) = repository.sessionExercises(sessionId)

    suspend fun session(sessionId: Long) = repository.session(sessionId)

    suspend fun sets(sessionExerciseId: Long) = repository.sets(sessionExerciseId)

    suspend fun addCustomExercise(name: String, id: String) = repository.addCustomExercise(name, id)

    suspend fun saveSetDraft(id: Long, weightKg: Double?, reps: Int?) =
        repository.saveSetDraft(id, weightKg, reps)

    suspend fun setCompleted(id: Long, completed: Boolean) = repository.setCompleted(id, completed)

    suspend fun finish(id: Long) = repository.finish(id)

    suspend fun pause(id: Long) = repository.pause(id)

    suspend fun resume(id: Long) = repository.resume(id)

    suspend fun start(name: String, rows: List<Triple<String, String, Int>>) =
        repository.start(name, rows)
}
