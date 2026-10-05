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
import app.ironlog.personal.ui.components.ListRow
import app.ironlog.personal.ui.components.Page
import app.ironlog.personal.ui.components.PrimaryButton
import app.ironlog.personal.ui.components.SecondaryButton
import app.ironlog.personal.ui.components.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    c: AppContainer,
    onTheme: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onBack: () -> Unit,
) {
    val profile by c.profile.collectAsState(initial = null)
    val nutritionGoal by c.goals.current.collectAsState(initial = null)
    val theme by c.theme.collectAsState(initial = "DARK")
    val scope = rememberCoroutineScope()
    val light = theme == "LIGHT"
    var confirmImport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editName by remember(profile) { mutableStateOf(profile?.name.orEmpty()) }
    val unit = app.ironlog.personal.ui.components.LocalWeightUnit.current
    val shownWeight = profile?.weightKg?.let(unit::number).orEmpty()
    var editWeight by remember(profile, unit) { mutableStateOf(shownWeight) }
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
            mutableStateOf(nutritionGoal?.proteinG?.let { "%.0f".format(it) } ?: "120")
        }
    var targetCarbs by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.carbsG?.let { "%.0f".format(it) } ?: "220")
        }
    var targetFat by
        remember(nutritionGoal) {
            mutableStateOf(nutritionGoal?.fatG?.let { "%.0f".format(it) } ?: "65")
        }
    Page("Settings", onBack = onBack) {
        profile?.let { savedProfile ->
            SectionHeader("Profile")
            Field("Name", editName, { editName = it })
            Field("Weight (${unit.label})", editWeight, { editWeight = it }, true)
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
            PrimaryButton(
                text = "Save profile and recalculate targets",
                onClick = {
                    // Untouched, the stored weight is kept exactly (no kg/lb rounding drift).
                    val weightKg =
                        if (editWeight == shownWeight) savedProfile.weightKg
                        else unit.parse(editWeight)?.takeIf { it in 25.0..400.0 } ?: savedProfile.weightKg
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
                                val targets = Calculations.macroTargets(calories, updated.weightKg)
                                c.goals.save(
                                    (c.goals.currentOnce() ?: GoalEntity()).copy(
                                        kcalTarget = targets.kcal,
                                        proteinG = targets.proteinG,
                                        carbsG = targets.carbsG,
                                        fatG = targets.fatG,
                                    )
                                )
                            }
                    }
                },
            )
        }
        SectionHeader("Units")
        ListRow(
            title = "Weight",
            subtitle = "Lifts, body weight, goals and records. Lengths stay in cm.",
            trailing = { app.ironlog.personal.ui.onboarding.UnitSwitch(unit) { scope.launch { c.setWeightUnit(it) } } },
        )
        SectionHeader("Appearance")
        ListRow(
            title = "Light theme",
            subtitle = "Dark is the default",
            trailing = { Switch(light, { value -> onTheme(value) }) },
        )
        SectionHeader("Nutrition targets")
        Text("Calculated targets can be edited here, including for profiles under 18.")
        Field("Calories (kcal)", targetKcal, { targetKcal = it }, true)
        Field("Protein (g)", targetProtein, { targetProtein = it }, true)
        Field("Carbs (g)", targetCarbs, { targetCarbs = it }, true)
        Field("Fat (g)", targetFat, { targetFat = it }, true)
        PrimaryButton(
            text = "Save nutrition targets",
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
            },
        )
        app.ironlog.personal.ui.reminders.ReminderSection(c)
        app.ironlog.personal.ui.health.HealthConnectSection(c)
        SectionHeader("Daily goals")
        DailyGoals(c, nutritionGoal)
        SectionHeader("Data sources")
        Text(
            "All exercises and 15,800 foods are built in and work offline. Only food searches and barcode scans you make are sent to Open Food Facts.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionHeader("Attributions")
        Text(
            "Exercise data and demo images: free-exercise-db by yuhonas (from wrkout/exercises.json), released into the public domain under the Unlicense.\nNutrition data: FoodData Central SR Legacy and FNDDS, U.S. Department of Agriculture (public domain).\nIndian dishes: Indian Nutrient Databank (INDB), Vijayakumar A. et al., Curr Dev Nutr 2024, derived from ICMR-NIN Indian Food Composition Tables 2017.\nPackaged-food data (online lookups and the bundled Indian brands list): Open Food Facts contributors, ODbL 1.0, world.openfoodfacts.org/terms-of-use.\nIronlog is independent and not affiliated with any fitness brand."
        )
        SectionHeader("Backup and new phone")
        val lastSnapshot = remember { c.autoBackup.lastWritten() }
        Text(
            "Automatic: Android backs up Ironlog to your Google account (Settings › Google › Backup on your phone). " +
                "On a new phone signed in to the same account, install Ironlog and tap Restore on the first screen. " +
                "Phone-to-phone transfer during setup copies everything, full-size photos included.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lastSnapshot?.let {
            Text(
                "Latest snapshot: " + java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM, HH:mm")),
                style = MaterialTheme.typography.bodySmall,
                color = app.ironlog.personal.ui.theme.IronTheme.colors.success,
            )
        }
        Text(
            "Manual: export one .zip with all your data and photos, keep it anywhere (Drive, email, a computer) and import it on any phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PrimaryButton("Export everything (.zip)", onExport)
        SecondaryButton("Import a backup", { confirmImport = true })
        SecondaryButton("Delete personal data", { confirmDelete = true })
    }
    if (confirmImport)
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            title = { Text("Import will replace personal data") },
            text = {
                Text(
                    "Choose an Ironlog backup (.zip, or .json from older versions). It replaces everything on this phone: profile, program, workouts, food log, body data and photos."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onImport()
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
                    "This erases the local profile, workouts, meals, foods and body data. Export a backup first if you want to keep it. The automatic snapshot is deleted too."
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
    app.ironlog.personal.ui.components.ChipRow(options, selected, { it.replace('_', ' ') }, onSelect)
}

@Composable
private fun DailyGoals(c: AppContainer, goal: GoalEntity?) {
    val scope = rememberCoroutineScope()
    val current = goal ?: GoalEntity()
    var steps by remember(goal) { mutableStateOf(current.stepGoal.toString()) }
    var water by remember(goal) { mutableStateOf("%.1f".format(current.waterGoalMl / 1000.0)) }
    var sleep by remember(goal) { mutableStateOf(current.sleepGoalHours.toString()) }
    Field("Steps per day", steps, { steps = it }, true)
    Field("Water per day (L)", water, { water = it }, true)
    Field("Sleep per night (h)", sleep, { sleep = it }, true)
    val s = steps.toIntOrNull()?.takeIf { it in 1000..60000 }
    val w = water.replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.5..10.0 }
    val h = sleep.replace(',', '.').toDoubleOrNull()?.takeIf { it in 4.0..12.0 }
    PrimaryButton(
        "Save daily goals",
        enabled = s != null && w != null && h != null,
        onClick = { scope.launch { c.goals.save(current.copy(stepGoal = s!!, waterGoalMl = (w!! * 1000).toInt(), sleepGoalHours = h!!)) } },
    )
}
