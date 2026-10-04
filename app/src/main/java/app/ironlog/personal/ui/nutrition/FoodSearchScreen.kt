package app.ironlog.personal.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.FoodEntity
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.Ingredient
import app.ironlog.personal.domain.Nutrition
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.library.formatKg
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.time.LocalDate
import kotlinx.coroutines.launch

private fun FoodEntity.subtitle() =
    listOfNotNull(brand, "%.0f kcal · P %.0f · C %.0f · F %.0f per 100 g".format(kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g))
        .joinToString(" · ")

private fun sourceLabel(food: FoodEntity) =
    when (food.source) {
        "USDA" -> "Ingredient"
        "INDB" -> "Indian"
        "FNDDS" -> food.cuisine ?: "World"
        "OFF" -> if (food.cuisine == "Indian (packaged)") "Packaged · IN" else "Packaged"
        "RECIPE" -> "Recipe"
        else -> "Custom"
    }

/** Search, scan or create a food and log a portion of it to [meal] on [date]. */
@Composable
fun FoodSearchScreen(c: AppContainer, nav: Navigator, meal: String, date: LocalDate) {
    val context = LocalContext.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf("Search") }
    var query by rememberSaveable { mutableStateOf("") }
    var online by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<FoodEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf(app.ironlog.personal.data.repo.FoodFilter.ALL) }
    val local by remember(query, filter) { c.nutrition.foods(query.trim(), filter) }.collectAsState(initial = emptyList())
    val popular by c.nutrition.popularIndian.collectAsState(initial = emptyList())
    val recent by c.nutrition.recent.collectAsState(initial = emptyList())
    val favorites by c.nutrition.favorites.collectAsState(initial = emptyList())
    val mine by c.nutrition.myFoods.collectAsState(initial = emptyList())

    fun scan() {
        val options =
            GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        GmsBarcodeScanning.getClient(context, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                val code = barcode.rawValue ?: return@addOnSuccessListener
                status = "Looking up $code…"
                scope.launch {
                    runCatching { c.openFoodFacts.byBarcode(code) }
                        .onSuccess { food ->
                            if (food == null) status = "No complete nutrition data for barcode $code. Add it as a custom food."
                            else {
                                status = null
                                selected = food
                            }
                        }
                        .onFailure { status = "Barcode lookup needs an internet connection." }
                }
            }
            .addOnFailureListener { status = "The scanner is unavailable on this device. Type the barcode into search instead." }
    }

    Page("Add to ${mealLabel(meal)}", scrollable = false, onBack = { nav.back() }, subtitle = date.toString().takeIf { date != LocalDate.now() }) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                online = emptyList()
                status = null
            },
            placeholder = { Text("Search foods or type a barcode") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = ::scan) { Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan barcode") }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        Segments(listOf("Search", "Recent", "Favourites", "My foods"), tab) { tab = it }
        if (tab == "Search") {
            ChipRow(app.ironlog.personal.data.repo.FoodFilter.entries.toList(), filter, { it.label }, { filter = it })
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }

        val list =
            when (tab) {
                "Recent" -> recent
                "Favourites" -> favorites
                "My foods" -> mine
                else -> if (query.isBlank()) popular.filter { filter.matches(it) } else online + local.filter { food -> online.none { it.id == food.id } }
            }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (tab == "Search" && query.isNotBlank()) {
                item {
                    val digits = query.trim().all(Char::isDigit) && query.trim().length in 8..14
                    SecondaryButton(
                        if (digits) "Look up barcode ${query.trim()}" else "Search Open Food Facts",
                        {
                            status = "Searching…"
                            scope.launch {
                                runCatching {
                                    if (digits) listOfNotNull(c.openFoodFacts.byBarcode(query.trim())) else c.openFoodFacts.search(query)
                                }
                                    .onSuccess {
                                        online = it
                                        status = if (it.isEmpty()) "No packaged foods with complete nutrition found." else "Packaged foods · Open Food Facts (ODbL)"
                                    }
                                    .onFailure { status = "Online search needs an internet connection." }
                            }
                        },
                        icon = Icons.Filled.Search,
                    )
                }
            }
            if (tab == "My foods") {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("Custom food", { creating = true }, Modifier.weight(1f), icon = Icons.Filled.Add)
                        SecondaryButton("Recipe", { nav.open(Routes.RECIPE) }, Modifier.weight(1f), icon = Icons.Filled.MenuBook)
                    }
                }
            }
            if (tab == "Search" && query.isBlank() && list.isNotEmpty()) {
                item { Eyebrow("Popular in India") }
            }
            if (list.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Search,
                        when (tab) {
                            "Recent" -> "Nothing logged yet"
                            "Favourites" -> "No favourites yet"
                            "My foods" -> "No custom foods or recipes"
                            else -> if (query.isBlank()) "Search 16,000 foods" else "No local matches"
                        },
                        when (tab) {
                            "Favourites" -> "Star a food when adding it to keep it here."
                            "Search" -> if (query.isBlank()) "Or scan a barcode for packaged food." else "Try Open Food Facts for packaged foods."
                            else -> "Foods you log appear here."
                        },
                    )
                }
            }
            items(list, key = { it.id }) { food ->
                ListRow(
                    title = food.name,
                    subtitle = food.subtitle(),
                    onClick = {
                        // Close the keyboard so it does not sit behind the portion dialog.
                        focus.clearFocus()
                        selected = food
                    },
                    trailing = { Pill(sourceLabel(food)) },
                )
            }
        }
    }

    selected?.let { food ->
        PortionDialog(c, food, meal, onDismiss = { selected = null }) { grams, chosenMeal ->
            selected = null
            scope.launch {
                c.nutrition.log(food, date, chosenMeal, grams)
                nav.back()
            }
        }
    }
    if (creating) {
        CustomFoodDialog(onDismiss = { creating = false }) { name, kcal, p, carbs, fat ->
            creating = false
            scope.launch {
                val id = c.nutrition.addCustom(name, kcal, p, carbs, fat)
                selected = FoodEntity(id = id, name = name, kcalPer100g = kcal, proteinPer100g = p, carbsPer100g = carbs, fatPer100g = fat)
            }
        }
    }
}

/** Choose grams or a declared serving, see macros update live, then log. */
@Composable
fun PortionDialog(c: AppContainer, food: FoodEntity, meal: String, onDismiss: () -> Unit, onAdd: (Double, String) -> Unit) {
    val servings by remember(food.id) { c.nutrition.servings(food.id) }.collectAsState(initial = emptyList())
    val live by remember(food.id) { c.nutrition.food(food.id) }.collectAsState(initial = food)
    val scope = rememberCoroutineScope()
    var unit by remember { mutableStateOf("g") }
    var amount by remember { mutableStateOf("100") }
    var chosenMeal by remember { mutableStateOf(meal) }
    LaunchedEffect(servings) {
        // Default to the first declared serving (e.g. "1 serving") when there is one.
        servings.firstOrNull()?.let {
            if (unit == "g") {
                unit = it.label
                amount = "1"
            }
        }
    }
    val perUnit = if (unit == "g") 1.0 else servings.firstOrNull { it.label == unit }?.grams ?: 1.0
    val grams = (amount.replace(',', '.').toDoubleOrNull() ?: 0.0) * perUnit
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(food.name, modifier = Modifier.weight(1f), maxLines = 3, style = MaterialTheme.typography.titleMedium)
                val favorite = live?.isFavorite == true
                IconButton(onClick = { scope.launch { c.nutrition.setFavorite(food.id, !favorite) } }) {
                    Icon(
                        if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (favorite) "Remove favourite" else "Add favourite",
                        tint = if (favorite) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                food.brand?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (food.source == "INDB" && servings.isNotEmpty()) {
                    // INDB per-100 g values follow the recipe's ingredient weights; servings are the reliable unit.
                    Text(
                        "Tip: log Indian dishes by serving (e.g. ${servings.first().label}); gram values follow the recipe's raw ingredients.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ChipFlow(listOf("g") + servings.map { it.label }, unit, { if (it == "g") "grams" else it }, {
                    unit = it
                    amount = if (it == "g") "100" else "1"
                })
                Field(if (unit == "g") "Amount (g)" else "Servings", amount, { amount = it }, number = true)
                if (unit != "g") Text("= ${formatKg(grams)} g", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("kcal", "%.0f".format(Calculations.foodMacro(food.kcalPer100g, grams)), Modifier.weight(1f))
                    StatTile("P", "%.1f".format(Calculations.foodMacro(food.proteinPer100g, grams)), Modifier.weight(1f))
                    StatTile("C", "%.1f".format(Calculations.foodMacro(food.carbsPer100g, grams)), Modifier.weight(1f))
                    StatTile("F", "%.1f".format(Calculations.foodMacro(food.fatPer100g, grams)), Modifier.weight(1f))
                }
                ChipFlow(MEALS.map { it.first }, chosenMeal, ::mealLabel, { chosenMeal = it })
            }
        },
        confirmButton = { TextButton(enabled = grams > 0, onClick = { onAdd(grams, chosenMeal) }) { Text("ADD") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

@Composable
private fun CustomFoodDialog(onDismiss: () -> Unit, onSave: (String, Double, Double, Double, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var protein by remember { mutableStateOf("") }
    var carbs by remember { mutableStateOf("") }
    var fat by remember { mutableStateOf("") }
    val values = listOf(kcal, protein, carbs, fat).map { it.replace(',', '.').toDoubleOrNull() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom food") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Values per 100 g, from the label.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Field("Name", name, { name = it })
                Field("Calories (kcal)", kcal, { kcal = it }, number = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("Protein", protein, { protein = it }, number = true, modifier = Modifier.weight(1f))
                    Field("Carbs", carbs, { carbs = it }, number = true, modifier = Modifier.weight(1f))
                    Field("Fat", fat, { fat = it }, number = true, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && values.all { it != null && it >= 0 },
                onClick = { onSave(name.trim(), values[0]!!, values[1]!!, values[2]!!, values[3]!!) },
            ) { Text("SAVE") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}

/** Build a recipe from logged-food ingredients; saved as a food with a per-serving portion. */
@Composable
fun RecipeScreen(c: AppContainer, nav: Navigator) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var servings by rememberSaveable { mutableStateOf("2") }
    var query by remember { mutableStateOf("") }
    var ingredients by remember { mutableStateOf(listOf<Pair<FoodEntity, Double>>()) }
    var picking by remember { mutableStateOf<FoodEntity?>(null) }
    val results by remember(query) { c.nutrition.foods(query.trim()) }.collectAsState(initial = emptyList())
    val totals =
        Nutrition.recipe(
            ingredients.map { (f, g) -> Ingredient(g, f.kcalPer100g, f.proteinPer100g, f.carbsPer100g, f.fatPer100g) },
            servings.toIntOrNull() ?: 0,
        )
    Page("New recipe", onBack = { nav.back() }) {
        Field("Recipe name", name, { name = it })
        Field("Servings", servings, { servings = it }, number = true)
        SectionHeader("Ingredients")
        if (ingredients.isEmpty()) Text("Search below and add ingredients by weight.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        ingredients.forEachIndexed { index, (food, grams) ->
            ListRow(
                title = food.name,
                subtitle = "${formatKg(grams)} g · %.0f kcal".format(Calculations.foodMacro(food.kcalPer100g, grams)),
                trailing = {
                    IconButton(onClick = { ingredients = ingredients.filterIndexed { i, _ -> i != index } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ingredient")
                    }
                },
            )
        }
        totals?.let { t ->
            IronCard {
                Eyebrow("Per serving · ${formatKg(t.gramsPerServing)} g")
                val n = servings.toIntOrNull() ?: 1
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("kcal", "%.0f".format(t.kcal / n), Modifier.weight(1f))
                    StatTile("P", "%.1f".format(t.protein / n), Modifier.weight(1f))
                    StatTile("C", "%.1f".format(t.carbs / n), Modifier.weight(1f))
                    StatTile("F", "%.1f".format(t.fat / n), Modifier.weight(1f))
                }
            }
        }
        PrimaryButton(
            "Save recipe",
            enabled = name.isNotBlank() && totals != null,
            onClick = {
                scope.launch {
                    c.nutrition.addRecipe(name.trim(), ingredients, servings.toInt())
                    nav.back()
                }
            },
        )
        SectionHeader("Add ingredient")
        Field("Search foods", query, { query = it })
        if (query.isNotBlank()) {
            results.take(15).forEach { food ->
                ListRow(title = food.name, subtitle = food.subtitle(), onClick = { picking = food })
            }
        }
    }
    picking?.let { food ->
        var grams by remember(food.id) { mutableStateOf("100") }
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(food.name, maxLines = 2) },
            text = { Field("Amount (g)", grams, { grams = it }, number = true) },
            confirmButton = {
                val g = grams.replace(',', '.').toDoubleOrNull()
                TextButton(enabled = g != null && g > 0, onClick = {
                    ingredients = ingredients + (food to g!!)
                    picking = null
                    query = ""
                }) { Text("ADD") }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text("CANCEL") } },
        )
    }
}
