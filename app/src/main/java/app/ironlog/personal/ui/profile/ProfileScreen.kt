package app.ironlog.personal.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.domain.Medal
import app.ironlog.personal.domain.Medals
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import app.ironlog.personal.ui.train.formatVolume
import java.time.format.DateTimeFormatter

private val EARNED = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun ProfileScreen(c: AppContainer, nav: Navigator) {
    val profile by c.profile.collectAsState(initial = null)
    val engagement by c.engagement.engagement.collectAsState(initial = null)
    Page(
        "Profile",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { nav.open(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) {
        val name = profile?.name?.ifBlank { null } ?: "Athlete"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier.size(72.dp).clip(CircleShape).background(IronTheme.colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.split(' ').filter(String::isNotBlank).take(2).joinToString("") { it.take(1) }.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = IronTheme.colors.onAccent,
                )
            }
            Column {
                Text(name.uppercase(), style = MaterialTheme.typography.headlineSmall)
                profile?.let {
                    Text(
                        "${it.experience.lowercase().replaceFirstChar(Char::uppercase)} · ${it.goal.replace('_', ' ').lowercase()} · ${it.daysPerWeek} days / week",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        val summary = engagement?.summary
        if (summary == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Page
        }
        IronCard {
            Row(verticalAlignment = Alignment.Bottom) {
                // Replay levels start at 0; people expect to start at level 1.
                Text("LEVEL ${summary.level + 1}", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                Text("${summary.totalXp} XP", style = MaterialTheme.typography.titleMedium, color = IronTheme.colors.accent)
            }
            LinearProgressIndicator(
                progress = { if (summary.xpForNextLevel > 0) summary.xpIntoLevel / summary.xpForNextLevel.toFloat() else 0f },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                color = IronTheme.colors.accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            Text(
                "${summary.xpForNextLevel - summary.xpIntoLevel} XP to level ${summary.level + 2}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Earn XP for every workout (100), working set (5, up to 250), personal best (50) and complete food day (10).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("Lifetime stats")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Workouts", "${summary.workouts}", Modifier.weight(1f))
            StatTile("Sets", "${summary.workingSets}", Modifier.weight(1f))
            StatTile("PRs", "${summary.personalRecords}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Volume", formatVolume(summary.lifetimeVolumeKg), Modifier.weight(1f), "lifted")
            StatTile("Streak", "${engagement?.weeklyStreak ?: 0}", Modifier.weight(1f), "weeks on target")
        }

        val earned = summary.medals.count { it.earnedOn != null }
        SectionHeader("Medals · $earned/${summary.medals.size}")
        summary.medals
            .sortedWith(compareByDescending<Medal> { it.earnedOn != null }.thenByDescending { it.earnedOn })
            .chunked(3)
            .forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { medal -> MedalTile(medal, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        SecondaryButton("Settings", { nav.open(Routes.SETTINGS) }, icon = Icons.Filled.Settings)
    }
}

@Composable
private fun MedalTile(medal: Medal, modifier: Modifier) {
    val info = Medals.info(medal.id)
    val earned = medal.earnedOn != null
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium) {
        Column(
            Modifier.padding(12.dp).heightIn(min = 150.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(if (earned) IronTheme.colors.accent else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (earned) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = if (earned) "Earned" else "Locked",
                    tint = if (earned) IronTheme.colors.onAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                info.title.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Text(
                medal.earnedOn?.format(EARNED) ?: info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
