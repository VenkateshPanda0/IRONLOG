package app.ironlog.personal.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.ExperienceLevel
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    c: AppContainer,
    onTheme: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val profile by c.profile.collectAsState(initial = null)
    val nutritionGoal by c.goals.current.collectAsState(initial = null)
    val theme by c.theme.collectAsState(initial = "DARK")
    val scope = rememberCoroutineScope()
    val light = theme == "LIGHT"
    var confirmImport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editName by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    var editWeight by remember(profile) { mutableStateOf(profile?.weightKg?.toString().orEmpty()) }
    var editHeight by remember(profile) { mutableStateOf(profile?.heightCm?.toString().orEmpty()) }
    var editAge by remember(profile) { mutableStateOf(profile?.age?.toString().orEmpty()) }
    var editDays by remember(profile) { mutableStateOf(profile?.daysPerWeek?.toString().orEmpty()) }
    var editMinutes by
        remember(profile) { mutableStateOf(profile?.sessionMinutes?.toString().orEmpty()) }
    var editAvoid by remember(profile) { mutableStateOf(profile?.avoidList.orEmpty()) }
    var editSex by remember(profile) { mutableStateOf(profile?.sex ?: "UNSPECIFIED") }
    var editGoal by remember(profile) { mutableStateOf(profile?.goal ?: "MAINTAIN") }
    var editActivity by remember(profile) { mutableDoubleStateOf(profile?.activity ?: 1.4) }
    var editEquipment by remember(profile) { mutableStateOf(profile?.equipment ?: "GYM") }
    var editExperience by
        remember(profile) {
            mutableStateOf(
                runCatching { ExperienceLevel.valueOf(profile?.experience ?: "BEGINNER") }
                    .getOrDefault(ExperienceLevel.BEGINNER)
            )
        }
    var targetKcal by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.kcalTarget?.toString() ?: "2000")
        }
    var targetProtein by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.proteinG?.toString() ?: "120")
        }
    var targetCarbs by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.carbsG?.toString() ?: "220")
        }
    var targetFat by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.fatG?.toString() ?: "65")
        }
    Page("Settings") {
        Text("Profile · ${profile?.name?.ifBlank { "Personal" }?:"Not set"}")
        profile?.let { savedProfile ->
            Text("Edit profile", style = MaterialTheme.typography.titleLarge)
            Field("Name", editName, { editName = it })
            Field("Weight (kg)", editWeight, { editWeight = it }, true)
            Field("Height (cm)", editHeight, { editHeight = it }, true)
            Field("Age", editAge, { editAge = it }, true)
            Text("Sex")
            ChoiceRow(listOf("MALE", "FEMALE", "UNSPECIFIED"), editSex) { editSex = it }
            Text("Goal")
            ChoiceRow(
                listOf("LOSE_FAT", "MAINTAIN", "BUILD_MUSCLE", "GET_STRONGER"),
                editGoal,
            ) {
                editGoal = it
            }
            Text("Activity level")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1.2, 1.375, 1.55, 1.725).forEach { factor ->
                    FilterChip(
                        selected = editActivity == factor,
                        onClick = { editActivity = factor },
                        label = { Text(factor.toString()) },
                    )
                }
            }
            Text("Equipment")
            ChoiceRow(listOf("GYM", "BODYWEIGHT"), editEquipment) { editEquipment = it }
            Text("Experience")
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ExperienceLevel.entries.forEach { level ->
                    FilterChip(
                        selected = editExperience == level,
                        onClick = { editExperience = level },
                        label = { Text(level.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    )
                }
            }
            Field("Training days per week", editDays, { editDays = it }, true)
            Field("Session length (minutes)", editMinutes, { editMinutes = it }, true)
            Field("Exercises or movements to avoid", editAvoid, { editAvoid = it })
            Button(
                onClick = {
                    val weightKg =
                        editWeight.toDoubleOrNull()?.takeIf { it > 0 } ?: savedProfile.weightKg
                    val heightCm =
                        editHeight.toDoubleOrNull()?.takeIf { it > 0 } ?: savedProfile.heightCm
                    val age = editAge.toIntOrNull()?.coerceIn(1, 120) ?: savedProfile.age
                    val updated =
                        savedProfile.copy(
                            name = editName.trim(),
                            weightKg = weightKg,
                            heightCm = heightCm,
                            age = age,
                            sex = editSex,
                            activity = editActivity,
                            goal = editGoal,
                            daysPerWeek =
                                editDays.toIntOrNull()?.coerceIn(2, 6) ?: savedProfile.daysPerWeek,
                            equipment = editEquipment,
                            experience = editExperience.name,
                            sessionMinutes =
                                editMinutes.toIntOrNull()?.coerceIn(20, 120)
                                    ?: savedProfile.sessionMinutes,
                            avoidList = editAvoid,
                        )
                    scope.launch {
                        c.saveProfile(updated)
                        Calculations.targetCalories(
                                updated.sex,
                                updated.weightKg,
                                updated.heightCm,
                                updated.age,
                                updated.activity,
                                updated.goal,
                            )
                            ?.let { calories ->
                                val protein = updated.weightKg * 2
                                val fat = calories * .25 / 9
                                val carbs =
                                    ((calories - protein * 4 - fat * 9) / 4).coerceAtLeast(0.0)
                                c.goals.save(
                                    (c.goals.currentOnce() ?: GoalEntity()).copy(
                                        kcalTarget = calories,
                                        proteinG = protein,
                                        carbsG = carbs,
                                        fatG = fat,
                                    )
                                )
                            }
                    }
                }
            ) {
                Text("Save profile and recalculate targets")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Light theme")
            Switch(light, { value -> onTheme(value) })
        }
        Text("Nutrition targets", style = MaterialTheme.typography.titleLarge)
        Text("Calculated targets can be edited here, including for profiles under 18.")
        Field("Calories (kcal)", targetKcal, { targetKcal = it }, true)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Field("Protein (g)", targetProtein, { targetProtein = it }, true)
            Field("Carbs (g)", targetCarbs, { targetCarbs = it }, true)
            Field("Fat (g)", targetFat, { targetFat = it }, true)
        }
        Button(
            onClick = {
                val calories = targetKcal.toIntOrNull()?.takeIf { it in 500..10000 }
                val protein = targetProtein.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                val carbs = targetCarbs.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                val fat = targetFat.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 }
                if (calories != null && protein != null && carbs != null && fat != null) {
                    scope.launch {
                        c.goals.save(
                            (nutritionGoal ?: GoalEntity()).copy(
                                kcalTarget = calories,
                                proteinG = protein,
                                carbsG = carbs,
                                fatG = fat,
                            )
                        )
                    }
                }
            }
        ) {
            Text("Save nutrition targets")
        }
        Text("Units · kg / cm")
        Text("FoodData Central: disabled until an API key is configured.")
        Text("Open Food Facts contact: unset@example.invalid")
        Text("Attributions", style = MaterialTheme.typography.titleLarge)
        Text(
            "Exercise data: free-exercise-db by yuhonas, Unlicense.\nNutrition data: FoodData Central, U.S. Department of Agriculture (CC0 1.0).\nPackaged-food data: Open Food Facts contributors, ODbL 1.0, world.openfoodfacts.org/terms-of-use.\nIronlog is independent and not affiliated with any fitness brand."
        )
        Button(onClick = onExport) { Text("Export backup") }
        OutlinedButton(onClick = { confirmImport = true }) { Text("Import backup") }
        OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete personal data") }
    }
    if (confirmImport)
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import will replace personal data") },
            text = {
                Text(
                    "Select a backup JSON file. This replaces the current profile, workouts, meals, foods and weights."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImport
                        confirmImport = false
                    }
                ) {
                    Text("Choose backup")
                }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text("Cancel") } },
        )
    if (confirmDelete)
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all app data?") },
            text = {
                Text(
                    "This erases the local profile, workouts, meals, foods and body data. Export a backup first if you want to keep it."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            c.clearPersonalData()
                            confirmDelete = false
                        }
                    }
                ) {
                    Text("Delete everything")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
}

@Composable
private fun ChoiceRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.replace('_', ' ')) },
            )
        }
    }
}
