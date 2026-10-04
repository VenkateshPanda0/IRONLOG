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
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.ProgramDayExerciseEntity
import app.ironlog.personal.data.db.UserProfileEntity
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.ExerciseOption
import app.ironlog.personal.domain.ExperienceLevel
import app.ironlog.personal.domain.RecommendedDay
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import java.time.LocalDate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

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
    var experience by remember { mutableStateOf(ExperienceLevel.BEGINNER) }
    var sessionMinutes by remember { mutableStateOf("45") }
    var avoidList by remember { mutableStateOf("") }
    var sex by remember { mutableStateOf("UNSPECIFIED") }
    var activity by remember { mutableDoubleStateOf(1.4) }
    var recommendationMessage by remember { mutableStateOf<String?>(null) }
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
        Text("Experience")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExperienceLevel.entries.forEach { level ->
                FilterChip(
                    selected = experience == level,
                    onClick = { experience = level },
                    label = { Text(level.name.lowercase().replaceFirstChar(Char::uppercase)) },
                )
            }
        }
        Field("Session length (minutes)", sessionMinutes, { sessionMinutes = it }, true)
        Field("Exercises or movements to avoid", avoidList, { avoidList = it })
        val equipmentSet = if (equipment == "BODYWEIGHT") setOf("body only") else GYM_EQUIPMENT
        val exerciseOptions by
            c.workouts.exercises
                .map { rows -> rows.filter { it.category.equals("strength", ignoreCase = true) } }
                .collectAsState(initial = emptyList())
        val suggestion =
            Recommender.suggest(
                TrainingProfile(
                    daysPerWeek = days.toIntOrNull() ?: 3,
                    goal = goal,
                    equipment = equipmentSet,
                    experience = experience,
                    sessionMinutes = sessionMinutes.toIntOrNull() ?: 45,
                    avoidList =
                        avoidList.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
                ),
                exerciseOptions.map { exercise ->
                    ExerciseOption(
                        id = exercise.id,
                        name = exercise.name,
                        primaryMuscles =
                            runCatching {
                                    kotlinx.serialization.json.Json.parseToJsonElement(
                                            exercise.primaryMuscles
                                        )
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
            "Suggested template · ${suggestion.template}",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(suggestion.why)
        suggestion.days.forEach { day ->
            Text(
                "${day.name} · ${day.exercises.size} exercises",
                style = MaterialTheme.typography.titleSmall,
            )
            day.exercises.forEach { prescription ->
                val exerciseName =
                    exerciseOptions.firstOrNull { it.id == prescription.exerciseId }?.name
                        ?: prescription.exerciseId
                Text(
                    "$exerciseName · ${prescription.sets} × ${prescription.repMin}–${prescription.repMax}"
                )
            }
        }
        recommendationMessage?.let { Text(it) }
        Button(
            enabled = suggestion.days.any { it.exercises.isNotEmpty() },
            onClick = {
                scope.launch {
                    val programDays = recommendationProgramDays(suggestion.days)
                    val programId =
                        c.programs.saveRecommended(
                            name = suggestion.template,
                            description = suggestion.why,
                            daysPerWeek = days.toIntOrNull()?.coerceIn(2, 6) ?: 3,
                            days = programDays,
                        )
                    c.programs.activate(programId)
                    recommendationMessage = "Saved and activated ${suggestion.template}."
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save and activate program")
        }
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
                            experience = experience.name,
                            sessionMinutes = sessionMinutes.toIntOrNull()?.coerceIn(20, 120) ?: 45,
                            avoidList = avoidList,
                        )
                    )
                    kcal?.let { calorieTarget ->
                        val proteinTarget = w * 2
                        val fatTarget = calorieTarget * .25 / 9
                        val carbsTarget =
                            ((calorieTarget - proteinTarget * 4 - fatTarget * 9) / 4).coerceAtLeast(0.0)
                        c.goals.save(
                            GoalEntity(
                                kcalTarget = calorieTarget,
                                proteinG = proteinTarget,
                                carbsG = carbsTarget,
                                fatG = fatTarget,
                            )
                        )
                    }
                    c.body.log(LocalDate.now(), w, "Starting weight")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save profile")
        }
    }
}

private fun recommendationProgramDays(
    days: List<RecommendedDay>
): List<Pair<String, List<ProgramDayExerciseEntity>>> = days.map { day ->
    day.name to
        day.exercises.mapIndexed { index, prescription ->
            ProgramDayExerciseEntity(
                programDayId = 0,
                exerciseId = prescription.exerciseId,
                orderIndex = index,
                targetSets = prescription.sets,
                repMin = prescription.repMin,
                repMax = prescription.repMax,
                restSeconds = prescription.restSeconds,
            )
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
