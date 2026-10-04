package app.ironlog.personal.ui.train

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.ironlog.personal.data.db.SetLogEntity
import kotlinx.coroutines.delay

@Composable
fun WorkoutSetRow(
    set: SetLogEntity,
    onDraft: (Double?, Int?) -> Unit,
    onComplete: (Boolean) -> Unit,
) {
    var weight by remember(set.id) { mutableStateOf(set.weightKg?.toString().orEmpty()) }
    var reps by remember(set.id) { mutableStateOf(set.reps?.toString().orEmpty()) }
    LaunchedEffect(weight, reps) {
        delay(250)
        onDraft(weight.toDoubleOrNull(), reps.toIntOrNull())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Set ${set.setIndex}")
        OutlinedTextField(
            value = weight,
            onValueChange = { weight = it },
            label = { Text("kg") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        OutlinedTextField(
            value = reps,
            onValueChange = { reps = it },
            label = { Text("reps") },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Checkbox(checked = set.isCompleted, onCheckedChange = onComplete)
    }
}
