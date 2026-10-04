package app.ironlog.personal.ui.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ironlog.personal.AppContainer
import app.ironlog.personal.IronlogViewModelFactory
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
fun NutritionScreen(container: AppContainer) {
    val today = LocalDate.now()
    val nutritionViewModel: NutritionViewModel =
        viewModel(factory = IronlogViewModelFactory(container, today))
    val nutritionState by nutritionViewModel.state.collectAsState()
    val goal by container.goals.current.collectAsState(initial = null)
    val scope = rememberCoroutineScope()
    val meals = nutritionState.entries
    var query by remember { mutableStateOf("") }
    var foods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var onlineFoods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var lookupMessage by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }

    LaunchedEffect(query) {
        container.nutrition.foods(query).collect { foods = it }
    }

    Page("Nutrition", scrollable = false) {
        Text("Today · $today")
        Text(
            "${meals.sumOf { it.kcal }.toInt()} kcal · " +
                "P ${"%.1f".format(meals.sumOf { it.protein })} g · " +
                "C ${"%.1f".format(meals.sumOf { it.carbs })} g · " +
                "F ${"%.1f".format(meals.sumOf { it.fat })} g"
        )
        goal?.let { target ->
            Text(
                "Targets · ${target.kcalTarget} kcal · " +
                    "P ${"%.0f".format(target.proteinG)} g · " +
                    "C ${"%.0f".format(target.carbsG)} g · " +
                    "F ${"%.0f".format(target.fatG)} g"
            )
        } ?: Text("Nutrition targets are not set yet. Complete your profile to calculate them.")
        Field("Search local foods", query, { query = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = query.isNotBlank(),
                onClick = {
                    scope.launch {
                        lookupMessage = "Searching Open Food Facts…"
                        runCatching { container.openFoodFacts.search(query) }
                            .onSuccess { results ->
                                onlineFoods = results
                                lookupMessage =
                                    if (results.isEmpty()) {
                                        "No complete nutrition result found."
                                    } else {
                                        "Packaged-food data · Open Food Facts (ODbL 1.0)"
                                    }
                            }
                            .onFailure {
                                lookupMessage =
                                    "Food lookup unavailable. Check connection and try again."
                            }
                    }
                },
            ) {
                Text("Search online")
            }
            OutlinedButton(
                enabled = query.isNotBlank(),
                onClick = {
                    scope.launch {
                        lookupMessage = "Looking up barcode…"
                        runCatching { container.openFoodFacts.byBarcode(query) }
                            .onSuccess { food ->
                                onlineFoods = listOfNotNull(food)
                                lookupMessage =
                                    if (food == null) {
                                        "No complete barcode result found."
                                    } else {
                                        "Open Food Facts (ODbL 1.0)"
                                    }
                            }
                            .onFailure { lookupMessage = "Barcode lookup unavailable." }
                    }
                },
            ) {
                Text("Lookup barcode")
            }
        }
        if (lookupMessage.isNotBlank()) Text(lookupMessage)
        onlineFoods.forEach { food ->
            FoodRow(food) {
                scope.launch { container.nutrition.log(food, today, "SNACK", 100.0) }
            }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(foods, key = { it.id }) { food ->
                FoodRow(food) {
                    scope.launch { container.nutrition.log(food, today, "SNACK", 100.0) }
                }
            }
        }
        Text("Create custom food · values per 100 g")
        Field("Food name", name, { name = it })
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Field("kcal", kcal, { kcal = it }, number = true)
            Field("protein", protein, { protein = it }, number = true)
            Field("carbs", carbs, { carbs = it }, number = true)
            Field("fat", fat, { fat = it }, number = true)
        }
        Button(
            onClick = {
                val values = listOf(kcal, protein, carbs, fat).mapNotNull(String::toDoubleOrNull)
                if (
                    name.isNotBlank() && values.size == 4 && values.all { it.isFinite() && it >= 0 }
                ) {
                    scope.launch {
                        container.nutrition.addCustom(
                            name,
                            values[0],
                            values[1],
                            values[2],
                            values[3],
                        )
                    }
                }
            }
        ) {
            Text("Save food")
        }
        if (meals.isEmpty()) Text("No meals logged today yet.")
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onLog: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("${food.name} · ${food.brand.orEmpty()} · per 100 g")
        TextButton(onClick = onLog) { Text("Log 100 g") }
    }
}
