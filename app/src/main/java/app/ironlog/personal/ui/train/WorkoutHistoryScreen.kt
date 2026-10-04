package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.ironlog.personal.AppContainer
import app.ironlog.personal.ui.nav.Navigator
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.ironlog.personal.data.db.WorkoutSessionEntity
import app.ironlog.personal.ui.components.Page
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(sessions: List<WorkoutSessionEntity>, onBack: () -> Unit) {
    val formatter =
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault())
    Page("Workout history", scrollable = false, onBack = onBack) {
        if (sessions.isEmpty()) {
            Text("No completed workouts yet.")
        } else {
            LazyColumn {
                items(sessions, key = { it.id }) { session ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(session.name, style = MaterialTheme.typography.titleMedium)
                        Text(formatter.format(Instant.ofEpochMilli(session.startedAt)))
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryRoute(container: AppContainer, nav: Navigator) {
    val sessions by container.workouts.history.collectAsState(initial = emptyList())
    HistoryScreen(sessions, onBack = { nav.back() })
}
