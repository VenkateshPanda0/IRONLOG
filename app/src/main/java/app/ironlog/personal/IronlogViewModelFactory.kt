package app.ironlog.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.ironlog.personal.ui.nutrition.NutritionViewModel
import java.time.LocalDate

class IronlogViewModelFactory(
    private val container: AppContainer,
    private val date: LocalDate = LocalDate.now(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel =
            when {
                modelClass.isAssignableFrom(NutritionViewModel::class.java) ->
                    NutritionViewModel(container.nutrition, date)
                else -> error("Unknown ViewModel: ${modelClass.name}")
            }
        return modelClass.cast(viewModel)
    }
}
