package app.ironlog.personal.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(c: AppContainer) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("70") }
    var height by remember { mutableStateOf("170") }
    var age by remember { mutableStateOf("30") }
    var goal by remember { mutableStateOf("MAINTAIN") }
    var days by remember { mutableStateOf("3") }
    var equipment by remember { mutableStateOf("GYM") }
    var sex by remember { mutableStateOf("UNSPECIFIED") }
    var activity by remember { mutableDoubleStateOf(1.4) }
    val w = weight.toDoubleOrNull() ?: 70.0
    val h = height.toDoubleOrNull() ?: 170.0
    val a = age.toIntOrNull() ?: 30
    Page("Set up Ironlog") {
        Text("Your profile stays on this device. Update it any time in Settings.")
        Field("Name", name, { name = it })
        Field("Weight (kg)", weight, { weight = it }, true)
        Field("Height (cm)", height, { height = it }, true)
        Field("Age", age, { age = it }, true)
        Field("Training days per week", days, { days = it }, true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("MALE", "FEMALE", "UNSPECIFIED").forEach {
                FilterChip(sex == it, { sex = it }, label = { Text(it) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("LOSE_FAT", "MAINTAIN", "BUILD_MUSCLE", "GET_STRONGER").forEach {
                FilterChip(goal == it, { goal = it }, label = { Text(it) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf(1.2, 1.375, 1.55, 1.725).forEach { factor ->
                FilterChip(
                    activity == factor,
                    { activity = factor },
                    label = { Text(factor.toString()) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("GYM", "BODYWEIGHT").forEach {
                FilterChip(equipment == it, { equipment = it }, label = { Text(it) })
            }
        }
        val suggestion =
            Recommender.suggest(TrainingProfile(days.toIntOrNull() ?: 3, goal, equipment))
        Text(
            "Suggested template · ${suggestion.template}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(suggestion.why)
        val kcal = Calculations.targetCalories(sex, w, h, a, activity, goal)
        Text(
            kcal?.let {
                "Mifflin–St Jeor estimate · $it kcal · protein ${"%.0f".format(w*2)} g · fat ${"%.0f".format(it*.25/9)} g · carbs ${"%.0f".format((it-w*2*4-it*.25)/4)} g. Estimates only."
            } ?: "Under 18: set calorie and macro targets manually."
        )
        Text("These estimates are not medical advice.")
        Button(
            onClick = {
                scope.launch {
                    val chosenDays = (days.toIntOrNull() ?: 3).coerceIn(2, 6)
                    c.saveProfile(
                        UserProfileEntity(
                            name = name,
                            sex = sex,
                            weightKg = w,
                            heightCm = h,
                            age = a,
                            activity = activity,
                            goal = goal,
                            equipment = equipment,
                            daysPerWeek = chosenDays,
                        )
                    )
                    c.body.log(LocalDate.now(), w, "Starting weight")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save profile")
        }
    }
}
