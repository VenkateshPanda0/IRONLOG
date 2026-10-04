package app.ironlog.personal.ui.nav

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.ironlog.personal.AppContainer
import app.ironlog.personal.SeedState
import app.ironlog.personal.ui.home.HomeScreen
import app.ironlog.personal.ui.library.ExerciseDetailScreen
import app.ironlog.personal.ui.library.ExerciseLibraryScreen
import app.ironlog.personal.ui.nutrition.FoodSearchScreen
import app.ironlog.personal.ui.nutrition.NutritionScreen
import app.ironlog.personal.ui.nutrition.RecipeScreen
import app.ironlog.personal.ui.onboarding.OnboardingScreen
import app.ironlog.personal.ui.profile.ProfileScreen
import app.ironlog.personal.ui.progress.ProgressScreen
import app.ironlog.personal.ui.settings.SettingsScreen
import app.ironlog.personal.ui.train.ActiveWorkoutRoute
import app.ironlog.personal.ui.train.HistoryRoute
import app.ironlog.personal.ui.train.ProgramBuilderScreen
import app.ironlog.personal.ui.train.ProgramDetailScreen
import app.ironlog.personal.ui.train.TrainScreen
import app.ironlog.personal.ui.train.WorkoutSummaryScreen

object Routes {
    const val HOME = "home"
    const val TRAIN = "train"
    const val NUTRITION = "nutrition"
    const val PROGRESS = "progress"
    const val LIBRARY = "library"
    const val EXERCISE = "exercise/{id}"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val BUILDER = "builder"
    const val PROGRAM = "program/{id}"
    const val WORKOUT = "workout/{id}"
    const val SUMMARY = "summary/{id}"
    const val FOOD = "food/{meal}/{date}"
    const val RECIPE = "recipe"

    fun program(id: Long) = "program/$id"

    fun workout(id: Long) = "workout/$id"

    fun summary(id: Long) = "summary/$id"

    fun food(meal: String, date: java.time.LocalDate) = "food/$meal/$date"

    fun exercise(id: String) = "exercise/${android.net.Uri.encode(id)}"
}

private data class Tab(
    val route: String,
    val label: String,
    val selected: ImageVector,
    val unselected: ImageVector,
)

private val tabs =
    listOf(
        Tab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
        Tab(Routes.TRAIN, "Train", Icons.Filled.FitnessCenter, Icons.Outlined.FitnessCenter),
        Tab(Routes.LIBRARY, "Library", Icons.AutoMirrored.Filled.MenuBook, Icons.AutoMirrored.Outlined.MenuBook),
        Tab(Routes.NUTRITION, "Nutrition", Icons.Filled.Restaurant, Icons.Outlined.Restaurant),
        Tab(
            Routes.PROGRESS,
            "Progress",
            Icons.AutoMirrored.Filled.ShowChart,
            Icons.AutoMirrored.Outlined.ShowChart,
        ),
    )

/**
 * Navigation callbacks shared by screens so they never touch the controller directly. Calls are
 * marshalled to the main thread because many are made after a suspending database call.
 */
class Navigator(private val controller: NavHostController) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private fun onMain(action: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) action() else main.post(action)
    }

    fun tab(route: String) = onMain {
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun open(route: String) = onMain { controller.navigate(route) }

    fun workout(id: Long) = onMain { controller.navigate(Routes.workout(id)) { launchSingleTop = true } }

    /** Replaces the current screen, e.g. moving from the active workout to its summary. */
    fun replace(route: String) = onMain {
        controller.navigate(route) {
            controller.currentDestination?.route?.let { popUpTo(it) { inclusive = true } }
        }
    }

    fun back() = onMain { controller.popBackStack() }
}

@Composable
fun IronlogRoot(
    container: AppContainer,
    onTheme: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val profile by container.profile.collectAsState(initial = null)
    val profileLoaded by
        produceState(false, container) { container.profile.collect { value = true } }
    val seedState by container.seedState.collectAsState()
    val controller = rememberNavController()
    val nav = remember(controller) { Navigator(controller) }
    val backStack by controller.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (profile != null && tabs.any { it.route == currentRoute }) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 0.dp,
                ) {
                    tabs.forEach { tab ->
                        val active = currentRoute == tab.route
                        NavigationBarItem(
                            selected = active,
                            onClick = { nav.tab(tab.route) },
                            icon = {
                                Icon(
                                    if (active) tab.selected else tab.unselected,
                                    contentDescription = null,
                                )
                            },
                            label = {
                                Text(
                                    tab.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            colors =
                                NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.surfaceContainerHighest
                                ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val state = seedState) {
                SeedState.Loading -> Loading("Setting up Ironlog…")
                is SeedState.Failed ->
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Setup failed", style = MaterialTheme.typography.headlineSmall)
                        Text(state.reason)
                    }
                is SeedState.Ready ->
                    when {
                        !profileLoaded -> Loading(null)
                        profile == null -> OnboardingScreen(container)
                        else -> IronlogNavHost(container, controller, nav, onTheme, onExport, onImport)
                    }
            }
        }
    }
}

@Composable
private fun Loading(text: String?) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        text?.let {
            Spacer(Modifier.height(12.dp))
            Text(it)
        }
    }
}

@Composable
private fun IronlogNavHost(
    container: AppContainer,
    controller: NavHostController,
    nav: Navigator,
    onTheme: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    NavHost(controller, startDestination = Routes.HOME) {
        composable(Routes.HOME) { HomeScreen(container, nav) }
        composable(Routes.TRAIN) { TrainScreen(container, nav) }
        composable(Routes.LIBRARY) { ExerciseLibraryScreen(container, nav) }
        composable(Routes.EXERCISE, listOf(navArgument("id") { type = NavType.StringType })) {
            ExerciseDetailScreen(container, nav, it.arguments?.getString("id").orEmpty())
        }
        composable(Routes.NUTRITION) { NutritionScreen(container, nav) }
        composable(Routes.FOOD, listOf(navArgument("meal") { type = NavType.StringType }, navArgument("date") { type = NavType.StringType })) {
            FoodSearchScreen(
                container,
                nav,
                it.arguments?.getString("meal") ?: "SNACK",
                runCatching { java.time.LocalDate.parse(it.arguments?.getString("date")) }.getOrDefault(java.time.LocalDate.now()),
            )
        }
        composable(Routes.RECIPE) { RecipeScreen(container, nav) }
        composable(Routes.PROGRESS) { ProgressScreen(container) }
        composable(Routes.PROFILE) { ProfileScreen(container, nav) }
        composable(Routes.SETTINGS) {
            SettingsScreen(container, onTheme, onExport, onImport, onBack = { nav.back() })
        }
        composable(Routes.HISTORY) { HistoryRoute(container, nav) }
        composable(Routes.BUILDER) { ProgramBuilderScreen(container, nav) }
        composable(Routes.PROGRAM, listOf(navArgument("id") { type = NavType.LongType })) {
            ProgramDetailScreen(container, nav, it.arguments?.getLong("id") ?: -1L)
        }
        composable(Routes.WORKOUT, listOf(navArgument("id") { type = NavType.LongType })) {
            ActiveWorkoutRoute(container, nav, it.arguments?.getLong("id") ?: -1L)
        }
        composable(Routes.SUMMARY, listOf(navArgument("id") { type = NavType.LongType })) {
            WorkoutSummaryScreen(container, nav, it.arguments?.getLong("id") ?: -1L)
        }
    }
}
