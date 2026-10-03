package app.ironlog.personal.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.*
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.TrainingProfile
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable private fun Page(title:String,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) { Text(title,style=MaterialTheme.typography.headlineMedium); content() }
}
@Composable private fun Field(label:String,value:String,onValue:(String)->Unit,number:Boolean=false) {
    OutlinedTextField(value,onValue,label={Text(label)},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=if(number) KeyboardType.Decimal else KeyboardType.Text),modifier=Modifier.fillMaxWidth())
}

@Composable fun OnboardingScreen(c:AppContainer) {
    val scope=rememberCoroutineScope(); var name by remember { mutableStateOf("") }; var weight by remember { mutableStateOf("70") }; var height by remember { mutableStateOf("170") }; var age by remember { mutableStateOf("30") }; var goal by remember { mutableStateOf("MAINTAIN") }; var days by remember { mutableStateOf("3") }; var equipment by remember { mutableStateOf("GYM") }
    val w=weight.toDoubleOrNull()?:70.0; val h=height.toDoubleOrNull()?:170.0; val a=age.toIntOrNull()?:30
    Page("Set up Ironlog") {
        Text("Your profile stays on this device. Update it any time in Settings.")
        Field("Name",name,{name=it}); Field("Weight (kg)",weight,{weight=it},true); Field("Height (cm)",height,{height=it},true); Field("Age",age,{age=it},true); Field("Training days per week",days,{days=it},true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("LOSE","MAINTAIN","GAIN").forEach { FilterChip(goal==it,{goal=it},label={Text(it)}) } }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("GYM","BODYWEIGHT").forEach { FilterChip(equipment==it,{equipment=it},label={Text(it)}) } }
        val suggestion=Recommender.suggest(TrainingProfile(days.toIntOrNull()?:3,goal,equipment))
        Text("Suggested template · ${suggestion.template}",style=MaterialTheme.typography.titleMedium); Text(suggestion.why)
        Text("Starting estimate: ${Calculations.targetCalories("female",w,h,a,1.4,goal)} kcal/day · preliminary")
        Button(onClick={scope.launch { c.saveProfile(UserProfileEntity(name=name,weightKg=w,heightCm=h,age=a,goal=goal,equipment=equipment,daysPerWeek=(days.toIntOrNull()?:3).coerceIn(1,7))) }},modifier=Modifier.fillMaxWidth()) { Text("Save profile") }
    }
}

@Composable fun HomeScreen(c:AppContainer,onTrain:()->Unit) {
    val active by c.workouts.active.collectAsState(initial=null); val history by c.workouts.history.collectAsState(initial=emptyList()); val weights by c.body.weights.collectAsState(initial=emptyList())
    Page("Today") {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(if(active==null) "Ready when you are" else "Workout in progress",style=MaterialTheme.typography.titleLarge); Text(active?.name?:"Start a quick workout or choose a program."); Button(onClick=onTrain) { Text(if(active==null) "Train" else "Resume workout") } } }
        Text("This week · ${history.count { it.startedAt>=System.currentTimeMillis()-7*86400000L }} completed workouts")
        Text(weights.lastOrNull()?.let { "Latest weight · %.1f kg".format(it.weightKg) }?:"No weight entries yet")
        Text("Meals logged today are shown in Nutrition.")
    }
}

@Composable fun TrainScreen(c:AppContainer) {
    val active by c.workouts.active.collectAsState(initial=null); val programs by c.programs.programs.collectAsState(initial=emptyList()); val history by c.workouts.history.collectAsState(initial=emptyList()); val exercises by c.db.dao().exercises().collectAsState(initial=emptyList()); val scope=rememberCoroutineScope(); var current by remember { mutableStateOf<WorkoutSessionEntity?>(null) }; var showHistory by remember { mutableStateOf(false) }; var exerciseName by remember { mutableStateOf("") }
    Page("Train") {
        if(active!=null) { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text("Resume ${active!!.name}"); Button(onClick={scope.launch { if(active!!.status=="PAUSED") c.workouts.resume(active!!.id); current=c.db.dao().session(active!!.id) }}) { Text("Open workout") }; OutlinedButton(onClick={scope.launch { c.workouts.finish(active!!.id); c.restTimer.skip() }}) { Text("Finish") } } } }
        Text("Programs",style=MaterialTheme.typography.titleLarge)
        if(programs.isEmpty()) Text("Built-in program data is not bundled in this delivery. Use Quick workout.") else programs.forEach { Text(it.name) }
        Field("Add a custom exercise",exerciseName,{exerciseName=it})
        OutlinedButton(onClick={scope.launch { if(exerciseName.isNotBlank()) { c.db.dao().putExercise(ExerciseEntity(id="custom_${java.util.UUID.randomUUID()}",name=exerciseName,isCustom=true)); exerciseName="" } }}) { Text("Save exercise") }
        Text("Exercise library · ${exercises.size} on-device entries")
        Button(onClick={scope.launch { val rows=exercises.take(4).map { Triple(it.id,it.name,3) }; val id=c.workouts.start("Quick workout",rows); current=c.db.dao().session(id) },enabled=active==null && exercises.isNotEmpty()) { Text("Start quick workout") }
        if(exercises.isEmpty()) Text("Create a custom exercise above before starting a quick workout.")
        TextButton(onClick={showHistory=true}) { Text("Workout history (${history.size})") }
    }
    current?.let { WorkoutDialog(c,it,{current=null}) }
    if(showHistory) AlertDialog(onDismissRequest={showHistory=false},title={Text("History")},text={Column { if(history.isEmpty()) Text("No completed workouts yet.") else history.take(10).forEach { Text("${it.name} · ${it.startedAt}") } }},confirmButton={TextButton(onClick={showHistory=false}) { Text("Done") }})
}

@Composable private fun WorkoutDialog(c:AppContainer,session:WorkoutSessionEntity,onClose:()->Unit) {
    val rows by c.db.dao().sessionExercises(session.id).collectAsState(initial=emptyList()); val scope=rememberCoroutineScope(); var sets by remember { mutableStateOf<Map<Long,List<SetLogEntity>>>(emptyMap()) }
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(rows) { rows.forEach { e -> sets=sets+(e.id to c.db.dao().setsOnce(e.id)) } }
    LaunchedEffect(session.id) { while(true) { remaining=c.restTimer.remainingMs(); delay(1000) } }
    AlertDialog(onDismissRequest=onClose,title={Text(session.name)},text={Column(Modifier.heightIn(max=500.dp)) {
        if(remaining>0) Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("Rest · %02d:%02d".format(remaining/60000,(remaining%60000)/1000),style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={scope.launch { c.restTimer.adjust(-15) }}) { Text("−15s") }
            TextButton(onClick={scope.launch { c.restTimer.adjust(15) }}) { Text("+15s") }
            TextButton(onClick={scope.launch { c.restTimer.skip() }}) { Text("Skip") }
        }
        LazyColumn { items(rows) { e -> Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(e.exerciseNameSnapshot,style=MaterialTheme.typography.titleMedium)
            (sets[e.id]?:emptyList()).forEach { s ->
                var kg by remember(s.id) { mutableStateOf(s.weightKg?.toString().orEmpty()) }; var reps by remember(s.id) { mutableStateOf(s.reps?.toString().orEmpty()) }
                LaunchedEffect(kg,reps) { delay(250); c.workouts.saveSetDraft(s.id,kg.toDoubleOrNull(),reps.toIntOrNull()) }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text("Set ${s.setIndex}")
                    OutlinedTextField(kg,{kg=it},label={Text("kg")},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                    OutlinedTextField(reps,{reps=it},label={Text("reps")},modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
                    Checkbox(s.isCompleted,onCheckedChange={checked -> scope.launch { c.workouts.setCompleted(s.id,checked); if(checked) c.restTimer.start(session.id,e.restSeconds) } })
                }
            }
        } } }
    }},confirmButton={TextButton(onClick={scope.launch { c.workouts.finish(session.id); c.restTimer.skip(); onClose() }}) { Text("Finish workout") }},dismissButton={TextButton(onClick={scope.launch { c.workouts.pause(session.id); onClose() }}) { Text("Pause") }})
}

@Composable fun NutritionScreen(c:AppContainer) {
    val today=LocalDate.now(); val meals by c.nutrition.meals(today).collectAsState(initial=emptyList()); val scope=rememberCoroutineScope(); var query by remember { mutableStateOf("") }; var foods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }; var onlineFoods by remember { mutableStateOf<List<FoodEntity>>(emptyList()) }; var lookupMessage by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var kcal by remember { mutableStateOf("") }; var protein by remember { mutableStateOf("") }; var carbs by remember { mutableStateOf("") }; var fat by remember { mutableStateOf("") }
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

@Composable fun ProgressScreen(c:AppContainer) {
    val weights by c.body.weights.collectAsState(initial=emptyList()); val scope=rememberCoroutineScope(); var value by remember { mutableStateOf("") }
    Page("Progress") {
        Text("Body weight",style=MaterialTheme.typography.titleLarge); Field("Weight (kg)",value,{value=it},true)
        Button(onClick={scope.launch { value.toDoubleOrNull()?.let { c.body.log(LocalDate.now(),it); value="" } }}) { Text("Add today's weight") }
        if(weights.isEmpty()) Text("No weight data yet. Log an entry to start your trend.") else {
            val grouped=weights.groupBy { LocalDate.parse(it.date) }.mapValues { (_,rows)->rows.maxBy{it.createdAt}.weightKg }; val mean=Calculations.sevenDayMean(grouped,LocalDate.now())
            Text("${weights.size} entries · latest %.1f kg".format(grouped.values.last())); Text(mean?.let { "7-day mean %.1f kg".format(it) }?:"7-day mean appears after 3 logged days.")
            LazyColumn { items(weights.reversed()) { Text("${it.date} · %.1f kg".format(it.weightKg)) } }
        }
    }
}

@Composable fun SettingsScreen(c:AppContainer,onTheme:(Boolean)->Unit) {
    val profile by c.profile.collectAsState(initial=null); val scope=rememberCoroutineScope(); var light by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }
    Page("Settings") {
        Text("Profile · ${profile?.name?.ifBlank { "Personal" }?:"Not set"}")
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { Text("Light theme"); Switch(light,{light=it; onTheme(!it); scope.launch { c.setTheme(if(it) "LIGHT" else "DARK") }}) }
        Text("Units · kg / cm"); Text("FoodData Central: disabled until an API key is configured."); Text("Open Food Facts contact: unset@example.invalid")
        Text("Attributions",style=MaterialTheme.typography.titleLarge)
        Text("Exercise data: free-exercise-db by yuhonas, Unlicense.\nNutrition data: FoodData Central, U.S. Department of Agriculture (CC0 1.0).\nPackaged-food data: Open Food Facts contributors, ODbL 1.0, world.openfoodfacts.org/terms-of-use.\nIronlog is independent and not affiliated with any fitness brand.")
        Text("Export/import is not implemented yet.")
        OutlinedButton(onClick={confirm=true}) { Text("Delete personal data") }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("Delete personal data?")},text={Text("This removes workouts, meals, weights and your profile.")},confirmButton={TextButton(onClick={scope.launch { c.clearPersonalData(); confirm=false }}) { Text("Delete") }},dismissButton={TextButton(onClick={confirm=false}) { Text("Cancel") }})
}
