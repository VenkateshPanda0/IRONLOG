package app.ironlog.personal.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WorkoutUiState(val active:WorkoutSessionEntity?=null,val history:List<WorkoutSessionEntity> = emptyList())
class WorkoutViewModel(repository:WorkoutRepository):ViewModel() {
    val state:StateFlow<WorkoutUiState> = combine(repository.active,repository.history) { active,history -> WorkoutUiState(active,history) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5_000),WorkoutUiState())
}
