package app.ironlog.personal.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.ironlog.personal.domain.LengthUnit
import app.ironlog.personal.domain.WeightUnit

/** The weight unit the user chose; every screen reads it to show and parse weights. */
val LocalWeightUnit = staticCompositionLocalOf { WeightUnit.KG }

/** The length unit the user chose for measurements, height and distance. */
val LocalLengthUnit = staticCompositionLocalOf { LengthUnit.CM }

/** Compact two-or-more option toggle, e.g. KG | LB next to a field. */
@Composable
fun <T> UnitToggle(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer)) {
        options.forEach { option ->
            val on = option == selected
            Surface(
                onClick = { if (!on) onSelect(option) },
                shape = CircleShape,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
            ) {
                Text(label(option).uppercase(), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}
