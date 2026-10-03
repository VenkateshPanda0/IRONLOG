package app.ironlog.personal.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.ui.components.Field
import app.ironlog.personal.ui.components.Page
import kotlinx.coroutines.launch
import java.time.LocalDate
import androidx.lifecycle.viewmodel.compose.viewModel
import app.ironlog.personal.IronlogViewModelFactory
import app.ironlog.personal.ui.nutrition.NutritionViewModel

@Composable fun NutritionScreen(c:AppContainer) {
    val today=LocalDate.now()
    val nutritionViewModel: NutritionViewModel = viewModel(factory = IronlogViewModelFactory(c, today))
    val nutritionState by nutritionViewModel.state.collectAsState()
    val meals = nutritionState.entries; val scope=rememberCoroutineScope(); var query by remember { mutableStateOf("") }; var foods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }; var onlineFoods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }; var lookupMessage by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var kcal by remember { mutableStateOf("") }; var protein by remember { mutableStateOf("") }; var carbs by remember { mutableStateOf("") }; var fat by remember { mutableStateOf("") }
    LaunchedEffect(query) { c.nutrition.foods(query).collect { foods=it } }
    Page("Nutrition") {
        Text("Today · $today")
        Text("${meals.sumOf{it.kcal}.toInt()} kcal · P ${"%.1f".format(meals.sumOf{it.protein})} g · C ${"%.1f".format(meals.sumOf{it.carbs})} g · F ${"%.1f".format(meals.sumOf{it.fat})} g")
        Field("Search local foods",query,{query=it})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={scope.launch { lookupMessage="Searching Open Food Facts…"; runCatching { c.openFoodFacts.search(query) }.onSuccess { onlineFoods=it; lookupMessage=if(it.isEmpty()) "No complete nutrition result found." else "Packaged-food data · Open Food Facts (ODbL 1.0)" }.onFailure { lookupMessage="Food lookup unavailable. Check connection and try again." } }},enabled=query.isNotBlank()) { Text("Search online") }; OutlinedButton(onClick={scope.launch { lookupMessage="Looking up barcode…"; runCatching { c.openFoodFacts.byBarcode(query) }.onSuccess { onlineFoods=listOfNotNull(it); lookupMessage=if(it==null) "No complete barcode result found." else "Open Food Facts (ODbL 1.0)" }.onFailure { lookupMessage="Barcode lookup unavailable." } },enabled=query.isNotBlank()) { Text("Lookup barcode") } }
        if(lookupMessage.isNotBlank()) Text(lookupMessage)
        onlineFoods.forEach { food -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${food.name} · ${food.brand.orEmpty()} · ${food.source}"); TextButton(onClick={scope.launch { c.nutrition.log(food,today,"SNACK",100.0) }}) { Text("Log 100 g") } } }
        LazyColumn(Modifier.weight(1f)) { items(foods) { food -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("${food.name} · per 100 g"); TextButton(onClick={scope.launch { c.nutrition.log(food,today,"SNACK",100.0) }}) { Text("Log 100 g") } } } }
        Text("Create custom food · values per 100 g")
        Field("Food name",name,{name=it}); Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) { Field("kcal",kcal,{kcal=it},true); Field("protein",protein,{protein=it},true); Field("carbs",carbs,{carbs=it},true); Field("fat",fat,{fat=it},true) }
        Button(onClick={scope.launch { if(name.isNotBlank() && listOf(kcal,protein,carbs,fat).all { it.toDoubleOrNull()!=null }) c.nutrition.addCustom(name,kcal.toDouble(),protein.toDouble(),carbs.toDouble(),fat.toDouble()) }}) { Text("Save food") }
        if(meals.isEmpty()) Text("No meals logged today yet.")
    }
}



