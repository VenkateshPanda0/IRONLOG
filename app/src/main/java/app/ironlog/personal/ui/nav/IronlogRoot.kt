package app.ironlog.personal.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.ironlog.personal.AppContainer
import app.ironlog.personal.SeedState
import app.ironlog.personal.ui.home.HomeScreen
import app.ironlog.personal.ui.nutrition.NutritionScreen
import app.ironlog.personal.ui.onboarding.OnboardingScreen
import app.ironlog.personal.ui.progress.ProgressScreen
import app.ironlog.personal.ui.settings.SettingsScreen
import app.ironlog.personal.ui.train.TrainScreen

private val tabs=listOf("Home","Train","Nutrition","Progress","Settings")
@Composable fun IronlogRoot(container:AppContainer,onTheme:(Boolean)->Unit,onExport:()->Unit,onImport:()->Unit) {
    var selected by remember { mutableIntStateOf(0) }
    val profile by container.profile.collectAsState(initial=null)
    val seedState by container.seedState.collectAsState()
    Scaffold(bottomBar={ if(profile!=null) NavigationBar { tabs.forEachIndexed { i,label -> NavigationBarItem(selected==i,onClick={selected=i},icon={Text(listOf("⌂","＋","◉","↗","⚙")[i])},label={Text(label)}) } } }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val state = seedState) {
                SeedState.Loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text("Setting up Ironlog…")
                }
                is SeedState.Failed -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("Setup failed: ${state.reason}")
                }
                is SeedState.Ready -> when(selected) {
                    0 -> if(profile==null) OnboardingScreen(container) else HomeScreen(container,onTrain={selected=1})
                    1 -> TrainScreen(container)
                    2 -> NutritionScreen(container)
                    3 -> ProgressScreen(container)
                    else -> SettingsScreen(container,onTheme,onExport,onImport)
                }
            }
        }
    }
}
