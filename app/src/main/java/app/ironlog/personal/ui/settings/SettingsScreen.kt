package app.ironlog.personal.ui.settings

import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import app.ironlog.personal.AppContainer
import app.ironlog.personal.ui.components.Page

@Composable fun SettingsScreen(c:AppContainer,onTheme:(Boolean)->Unit,onExport:()->Unit,onImport:()->Unit) {
    val profile by c.profile.collectAsState(initial=null); val theme by c.theme.collectAsState(initial="DARK"); val scope=rememberCoroutineScope(); val light=theme=="LIGHT"; var confirmImport by remember { mutableStateOf(false) }; var confirmDelete by remember { mutableStateOf(false) }
    Page("Settings") {
        Text("Profile · ${profile?.name?.ifBlank { "Personal" }?:"Not set"}")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text("Light theme"); Switch(light,{value -> onTheme(value) }) }
        Text("Units · kg / cm"); Text("FoodData Central: disabled until an API key is configured."); Text("Open Food Facts contact: unset@example.invalid")
        Text("Attributions",style=MaterialTheme.typography.titleLarge)
        Text("Exercise data: free-exercise-db by yuhonas, Unlicense.\nNutrition data: FoodData Central, U.S. Department of Agriculture (CC0 1.0).\nPackaged-food data: Open Food Facts contributors, ODbL 1.0, world.openfoodfacts.org/terms-of-use.\nIronlog is independent and not affiliated with any fitness brand.")
        Button(onClick=onExport) { Text("Export backup") }
        OutlinedButton(onClick={confirmImport=true}) { Text("Import backup") }
        OutlinedButton(onClick={confirmDelete=true}) { Text("Delete personal data") }
    }
    if(confirmImport) AlertDialog(onDismissRequest={confirmImport=false},title={Text("Import will replace personal data")},text={Text("Select a backup JSON file. This replaces the current profile, workouts, meals, foods and weights.")},confirmButton={TextButton(onClick={onImport; confirmImport=false}) { Text("Choose backup") }},dismissButton={TextButton(onClick={confirmImport=false}) { Text("Cancel") }})
    if(confirmDelete) AlertDialog(onDismissRequest={confirmDelete=false},title={Text("Delete all app data?")},text={Text("This erases the local profile, workouts, meals, foods and body data. Export a backup first if you want to keep it.")},confirmButton={TextButton(onClick={ scope.launch { c.clearPersonalData(); confirmDelete=false } }) { Text("Delete everything") }},dismissButton={TextButton(onClick={confirmDelete=false}) { Text("Cancel") }})
}

