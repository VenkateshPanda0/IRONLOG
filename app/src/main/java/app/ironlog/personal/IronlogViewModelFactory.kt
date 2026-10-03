package app.ironlog.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import app.ironlog.personal.ui.nutrition.NutritionViewModel
import app.ironlog.personal.ui.progress.BodyViewModel
import app.ironlog.personal.ui.workouts.WorkoutViewModel
import java.time.LocalDate

class IronlogViewModelFactory(
    private val container: AppContainer,
    private val date: LocalDate = LocalDate.now(),
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = when {
            modelClass.isAssignableFrom(WorkoutViewModel::class.java) ->
                WorkoutViewModel(container.workouts)
            modelClass.isAssignableFrom(NutritionViewModel::class.java) ->
                NutritionViewModel(container.nutrition, date)
            modelClass.isAssignableFrom(BodyViewModel::class.java) ->
                BodyViewModel(container.body)
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
        return modelClass.cast(viewModel)
    }
}
