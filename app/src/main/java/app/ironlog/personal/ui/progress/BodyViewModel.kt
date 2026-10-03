package app.ironlog.personal.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ironlog.personal.data.db.BodyWeightEntity
import app.ironlog.personal.data.repo.BodyRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class BodyUiState(
    val weights: List<BodyWeightEntity> = emptyList(),
    val loading: Boolean = true,
)

class BodyViewModel(repository: BodyRepository) : ViewModel() {
    val state: StateFlow<BodyUiState> =
        repository.weights
            .map { BodyUiState(it, false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyUiState())
}
