package app.ironlog.personal.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.ironlog.personal.AppContainer
import app.ironlog.personal.ui.screens.*

private val tabs=listOf("Home","Train","Nutrition","Progress","Settings")
@Composable fun IronlogRoot(container:AppContainer,onTheme:(Boolean)->Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val profile by container.profile.collectAsState(initial=null)
    Scaffold(bottomBar={ if(profile!=null) NavigationBar { tabs.forEachIndexed { i,label -> NavigationBarItem(selected==i,onClick={selected=i},icon={Text(listOf("⌂","＋","◉","↗","⚙")[i])},label={Text(label)}) } } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { when(selected) {
            0 -> if(profile==null) OnboardingScreen(container) else HomeScreen(container,onTrain={selected=1})
            1 -> TrainScreen(container)
            2 -> NutritionScreen(container)
            3 -> ProgressScreen(container)
            else -> SettingsScreen(container,onTheme)
        } }
    }
}
