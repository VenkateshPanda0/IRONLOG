package app.ironlog.personal.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.ui.components.Page

@Composable fun HomeScreen(c:AppContainer,onTrain:()->Unit) {
    val active by c.workouts.active.collectAsState(initial=null); val history by c.workouts.history.collectAsState(initial=emptyList()); val weights by c.body.weights.collectAsState(initial=emptyList())
    Page("Today") {
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(if(active==null) "Ready when you are" else "Workout in progress",style=MaterialTheme.typography.titleLarge); Text(active?.name?:"Start a quick workout or choose a program."); Button(onClick=onTrain) { Text(if(active==null) "Train" else "Resume workout") } } }
        Text("This week · ${history.count { it.startedAt>=System.currentTimeMillis()-7*86400000L }} completed workouts")
        Text(weights.lastOrNull()?.let { "Latest weight · %.1f kg".format(it.weightKg) }?:"No weight entries yet")
        Text("Meals logged today are shown in Nutrition.")
    }
}

