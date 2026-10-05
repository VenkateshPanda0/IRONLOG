package app.ironlog.personal.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.ExerciseEntity
import app.ironlog.personal.data.db.primaryMuscleList
import app.ironlog.personal.domain.Recommender
import app.ironlog.personal.domain.Staples
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.launch

private const val ALL = "All"

private val EQUIPMENT =
    listOf(
        "barbell",
        "dumbbell",
        "cable",
        "machine",
        "body only",
        "kettlebells",
        "bands",
        "e-z curl bar",
        "medicine ball",
        "exercise ball",
        "other",
    )

fun String.titleCase(): String =
    split(' ').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }

/** Search key and parsed muscles computed once per list rather than on every row draw. */
private class IndexedExercise(val exercise: ExerciseEntity) {
    val muscles = exercise.primaryMuscleList
    val key = (exercise.name + " " + muscles.joinToString(" ") + " " + exercise.equipment.orEmpty()).lowercase()
    val subtitle =
        listOfNotNull(
                muscles.firstOrNull()?.titleCase(),
                exercise.equipment?.titleCase(),
                "Custom".takeIf { exercise.isCustom },
            )
            .joinToString(" · ")
}

/**
 * Searchable, filterable exercise list. Used by the Library tab and by the workout screen's add
 * and replace pickers; [highlightMuscle] pre-selects a muscle filter.
 */
@Composable
fun ExerciseBrowser(
    exercises: List<ExerciseEntity>,
    onSelect: (ExerciseEntity) -> Unit,
    modifier: Modifier = Modifier,
    highlightMuscle: String? = null,
    trailing: (@Composable (ExerciseEntity) -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var muscle by remember(highlightMuscle) { mutableStateOf(highlightMuscle ?: ALL) }
    var equipment by remember { mutableStateOf(ALL) }
    var favoritesOnly by remember { mutableStateOf(false) }
    val indexed = remember(exercises) { exercises.map(::IndexedExercise) }
    val terms = query.trim().lowercase().split(' ').filter(String::isNotEmpty)
    val visible =
        remember(indexed, terms, muscle, equipment, favoritesOnly) {
            indexed.filter { row ->
                terms.all { it in row.key } &&
                    (muscle == ALL || muscle in row.muscles) &&
                    (equipment == ALL || row.exercise.equipment == equipment) &&
                    (!favoritesOnly || row.exercise.isFavorite)
            }
                // Favourites, then well-known staple lifts, then alphabetical.
                .sortedWith(
                    compareByDescending<IndexedExercise> { it.exercise.isFavorite }
                        .thenBy { Staples.rank(it.exercise.id) }
                        .thenBy { it.exercise.name }
                )
        }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search ${exercises.size} exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth(),
        )
        ChipRow(listOf(ALL) + Recommender.ALL_MUSCLES, muscle, { it.titleCase() }, { muscle = it })
        ChipRow(listOf(ALL) + EQUIPMENT, equipment, { it.titleCase() }, { equipment = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = favoritesOnly,
                onClick = { favoritesOnly = !favoritesOnly },
                label = { Text("Favourites") },
                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null, Modifier.size(16.dp)) },
            )
            Text(
                if (visible.size == 1) "1 result" else "${visible.size} results",
                modifier = Modifier.align(androidx.compose.ui.Alignment.CenterVertically),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        if (visible.isEmpty()) {
            EmptyState(Icons.Filled.Search, "No exercises found", "Try a different search or clear a filter.")
        }
        LazyColumn(Modifier.weight(1f)) {
            items(visible, key = { it.exercise.id }) { row ->
                ListRow(
                    title = row.exercise.name,
                    subtitle = row.subtitle,
                    onClick = { onSelect(row.exercise) },
                    leading = { ExerciseThumb(row.exercise.id, row.muscles.firstOrNull() ?: row.exercise.name, size = 52.dp) },
                    trailing = trailing?.let { { it(row.exercise) } },
                )
            }
        }
    }
}

@Composable
fun ExerciseLibraryScreen(container: AppContainer, nav: Navigator) {
    val exercises by container.workouts.exercises.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    Page(
        "Library",
        scrollable = false,
        actions = {
            IconButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create custom exercise")
            }
        },
    ) {
        ExerciseBrowser(
            exercises = exercises,
            onSelect = { nav.open(Routes.exercise(it.id)) },
            modifier = Modifier.weight(1f),
            trailing = { exercise ->
                IconButton(onClick = { scope.launch { container.workouts.setFavorite(exercise.id, !exercise.isFavorite) } }) {
                    Icon(
                        if (exercise.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (exercise.isFavorite) "Remove favourite" else "Add favourite",
                        tint = if (exercise.isFavorite) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
    if (creating) {
        CustomExerciseDialog(
            onDismiss = { creating = false },
            onCreate = { name, muscle, equipment ->
                creating = false
                scope.launch {
                    val id = container.workouts.addCustomExercise(name, muscle, equipment)
                    nav.open(Routes.exercise(id))
                }
            },
        )
    }
}

@Composable
fun CustomExerciseDialog(onDismiss: () -> Unit, onCreate: (String, String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("chest") }
    var equipment by remember { mutableStateOf("barbell") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field("Name", name, { name = it })
                Eyebrow("Primary muscle")
                ChipFlow(Recommender.ALL_MUSCLES, muscle, { it.titleCase() }, { muscle = it })
                Eyebrow("Equipment")
                ChipFlow(EQUIPMENT, equipment, { it.titleCase() }, { equipment = it })
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim(), muscle, equipment) }) {
                Text("CREATE")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
    )
}
