package app.ironlog.personal.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.GoalEntity
import app.ironlog.personal.data.db.MealEntryEntity
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.Nutrition
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

val MEALS = listOf("BREAKFAST" to "Breakfast", "LUNCH" to "Lunch", "DINNER" to "Dinner", "SNACK" to "Snacks")

fun mealLabel(key: String) = MEALS.firstOrNull { it.first == key }?.second ?: key.lowercase().replaceFirstChar(Char::uppercase)

@Composable
fun NutritionScreen(c: AppContainer, nav: Navigator) {
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    val day = LocalDate.parse(date)
    val today = LocalDate.now()
    val entries by remember(date) { c.nutrition.meals(day) }.collectAsState(initial = emptyList())
    val goal by c.goals.current.collectAsState(initial = null)
    val week by remember(date) { c.nutrition.dailyTotals(day.minusDays(6), day) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    var editing by remember { mutableStateOf<MealEntryEntity?>(null) }
    var editingTargets by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf<String?>(null) }

    val kcal = entries.sumOf { it.kcal }
    val target = goal?.kcalTarget?.toDouble()

    Page(
        "Nutrition",
        actions = {
            IconButton(onClick = { focus.clearFocus(); editingTargets = true }) { Icon(Icons.Filled.Tune, contentDescription = "Edit targets") }
        },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { date = day.minusDays(1).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
            }
            Text(
                when (day) {
                    today -> "TODAY"
                    today.minusDays(1) -> "YESTERDAY"
                    else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM")).uppercase()
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { date = day.plusDays(1).toString() }, enabled = day < today) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
            }
        }

        IronCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = if (target != null && target > 0) (kcal / target).toFloat() else 0f,
                    size = 128.dp,
                    stroke = 12.dp,
                    description = "Calories eaten",
                ) {
                    Text("%.0f".format(kcal), style = MaterialTheme.typography.headlineSmall)
                    Text("kcal eaten", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (target != null) {
                        val left = target - kcal
                        Text("%.0f".format(kotlin.math.abs(left)), style = MaterialTheme.typography.displaySmall)
                        Text(
                            if (left >= 0) "kcal remaining of %.0f".format(target) else "kcal over target",
                            color = if (left >= 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Text("No target set", style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { editingTargets = true }) { Text("SET TARGETS") }
                    }
                }
            }
            MacroBar("Protein", entries.sumOf { it.protein }, goal?.proteinG, IronTheme.colors.protein)
            MacroBar("Carbs", entries.sumOf { it.carbs }, goal?.carbsG, IronTheme.colors.carbs)
            MacroBar("Fat", entries.sumOf { it.fat }, goal?.fatG, IronTheme.colors.fat)
        }

        MEALS.forEach { (key, label) ->
            val items = entries.filter { it.mealType == key }
            IronCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (items.isEmpty()) "Nothing logged"
                            else "%.0f kcal · P %.0f · C %.0f · F %.0f".format(items.sumOf { it.kcal }, items.sumOf { it.protein }, items.sumOf { it.carbs }, items.sumOf { it.fat }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalIconButton(onClick = { nav.open(Routes.food(key, day)) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add food to $label")
                    }
                }
                items.forEach { entry ->
                    ListRow(
                        title = entry.foodNameSnapshot,
                        subtitle = "${formatKg(entry.grams)} g · P %.0f · C %.0f · F %.0f".format(entry.protein, entry.carbs, entry.fat),
                        onClick = { editing = entry },
                        trailing = { Text("%.0f".format(entry.kcal), style = MaterialTheme.typography.titleSmall) },
                    )
                }
            }
        }

        SecondaryButton(
            "Copy ${if (day == today) "yesterday" else day.minusDays(1).format(DateTimeFormatter.ofPattern("d MMM"))}",
            {
                scope.launch {
                    c.nutrition.copyDay(day.minusDays(1), day)
                    copied = "Copied the previous day's meals."
                }
            },
            icon = Icons.Filled.ContentCopy,
        )
        copied?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }

        if (target != null) {
            val byDate = week.associate { LocalDate.parse(it.date) to it.kcal }
            val (days, balance) = Nutrition.weeklyBalance(byDate, target.toInt(), day)
            IronCard {
                Eyebrow("Weekly balance")
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%+,.0f".format(balance), style = MaterialTheme.typography.displaySmall)
                    Text(" kcal", modifier = Modifier.padding(bottom = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    when {
                        days.none { it.second > 0 } -> "Log meals to see your weekly balance."
                        balance < 0 -> "Under target across logged days this week."
                        else -> "Over target across logged days this week."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BarChart(
                    values = days.map { it.second },
                    labels = days.map { it.first.dayOfWeek.name.take(1) },
                    target = target,
                    description = "Calories for the last 7 days against the target",
                )
            }
        }
    }

    editing?.let { entry ->
        EntryDialog(entry, onDismiss = { editing = null }, onSave = { grams, meal ->
            editing = null
            scope.launch { c.nutrition.updateGrams(entry.id, grams, meal) }
        }, onDelete = {
            editing = null
            scope.launch { c.nutrition.deleteEntry(entry.id) }
        })
    }
    if (editingTargets) TargetsDialog(c, goal) { editingTargets = false }
}

@Composable
private fun EntryDialog(entry: MealEntryEntity, onDismiss: () -> Unit, onSave: (Double, String) -> Unit, onDelete: () -> Unit) {
    var grams by remember { mutableStateOf(formatKg(entry.grams)) }
    var meal by remember { mutableStateOf(entry.mealType) }
    val value = grams.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.foodNameSnapshot, maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Amount (g)", grams, { grams = it }, number = true)
                ChipFlow(MEALS.map { it.first }, meal, ::mealLabel, { meal = it })
                if (value != null && value > 0) {
                    val f = value / entry.grams
                    Text("%.0f kcal · P %.1f · C %.1f · F %.1f".format(entry.kcal * f, entry.protein * f, entry.carbs * f, entry.fat * f))
                }
            }
        },
        confirmButton = { TextButton(enabled = value != null && value > 0, onClick = { onSave(value!!, meal) }) { Text("SAVE") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("DELETE", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("CANCEL") }
            }
        },
    )
}

@Composable
private fun TargetsDialog(c: AppContainer, goal: GoalEntity?, onDone: () -> Unit) {
    val profile by c.profile.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    var kcal by remember { mutableStateOf(goal?.kcalTarget?.toString() ?: "") }
    var protein by remember { mutableStateOf(goal?.proteinG?.let { "%.0f".format(it) } ?: "") }
    var carbs by remember { mutableStateOf(goal?.carbsG?.let { "%.0f".format(it) } ?: "") }
    var fat by remember { mutableStateOf(goal?.fatG?.let { "%.0f".format(it) } ?: "") }
    var preset by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Daily targets") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                profile?.let { p ->
                    Eyebrow("Presets from your profile")
                    ChipFlow(Nutrition.PRESETS, Nutrition.PRESETS.firstOrNull { it.key == preset }, { it.label }, { choice ->
                        preset = choice.key
                        Nutrition.presetCalories(choice, p.sex, p.weightKg, p.heightCm, p.age, p.activity)?.let { calories ->
                            val t = Calculations.macroTargets(calories, p.weightKg)
                            kcal = t.kcal.toString()
                            protein = "%.0f".format(t.proteinG)
                            carbs = "%.0f".format(t.carbsG)
                            fat = "%.0f".format(t.fatG)
                        }
                    })
                }
                Field("Calories (kcal)", kcal, { kcal = it }, number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Protein g", protein, { protein = it }, number = true, modifier = Modifier.weight(1f))
                    Field("Carbs g", carbs, { carbs = it }, number = true, modifier = Modifier.weight(1f))
                    Field("Fat g", fat, { fat = it }, number = true, modifier = Modifier.weight(1f))
                }
                val macroKcal = listOf(protein to 4, carbs to 4, fat to 9).sumOf { (v, f) -> (v.toDoubleOrNull() ?: 0.0) * f }
                Text("Macros add up to %.0f kcal".format(macroKcal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val values = listOf(protein, carbs, fat).map { it.toDoubleOrNull() }
            val calories = kcal.toIntOrNull()?.takeIf { it in 500..10000 }
            TextButton(
                enabled = calories != null && values.all { it != null && it >= 0 },
                onClick = {
                    scope.launch {
                        c.goals.save((goal ?: GoalEntity()).copy(kcalTarget = calories!!, proteinG = values[0]!!, carbsG = values[1]!!, fatG = values[2]!!))
                        onDone()
                    }
                },
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("CANCEL") } },
    )
}
