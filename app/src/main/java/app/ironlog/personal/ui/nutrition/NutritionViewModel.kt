package app.ironlog.personal.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ironlog.personal.data.db.MealEntryEntity
import app.ironlog.personal.data.repo.NutritionRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class NutritionUiState(
    val entries: List<MealEntryEntity> = emptyList(),
    val loading: Boolean = true,
)

class NutritionViewModel(repository: NutritionRepository, date: LocalDate = LocalDate.now()) :
    ViewModel() {
    val state: StateFlow<NutritionUiState> =
        repository
            .meals(date)
            .map { NutritionUiState(it, false) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NutritionUiState())
}
