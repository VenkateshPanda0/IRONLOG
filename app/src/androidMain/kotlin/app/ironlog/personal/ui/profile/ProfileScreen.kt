package app.ironlog.personal.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.repo.Engagement
import app.ironlog.personal.domain.Achievement
import app.ironlog.personal.domain.AchievementGroup
import app.ironlog.personal.domain.EngagementReplay
import app.ironlog.personal.domain.Tier
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import app.ironlog.personal.ui.train.formatVolume
import java.time.format.DateTimeFormatter

private val EARNED = DateTimeFormatter.ofPattern("d MMM yyyy")

@Composable
fun tierColor(tier: Tier): Color =
    when (tier) {
        Tier.BRONZE -> Color(0xFFCD8B5A)
        Tier.SILVER -> Color(0xFFC9CED6)
        Tier.GOLD -> Color(0xFFFFC94A)
        Tier.PLATINUM -> Color(0xFF8FE3F0)
        Tier.LEGEND -> IronTheme.colors.accent
    }

@Composable
fun ProfileScreen(c: AppContainer, nav: Navigator) {
    val profile by c.profile.collectAsState(initial = null)
    val engagement by c.engagement.engagement.collectAsState(initial = null)
    var group by rememberSaveable { mutableStateOf("All") }
    Page(
        "Profile",
        onBack = { nav.back() },
        actions = {
            IconButton(onClick = { nav.open(Routes.SETTINGS) }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
        },
    ) {
        val name = profile?.name?.ifBlank { null } ?: "Athlete"
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(72.dp).clip(CircleShape).background(IronTheme.colors.accent), contentAlignment = Alignment.Center) {
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

        val e = engagement
        if (e == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            return@Page
        }
        LevelCard(e)
        app.ironlog.personal.ui.physique.PhysiqueCard(c, nav)

        SectionHeader("Lifetime stats")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Workouts", "${e.summary.workouts}", Modifier.weight(1f))
            StatTile("Sets", "${e.summary.workingSets}", Modifier.weight(1f))
            StatTile("PRs", "${e.summary.personalRecords}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Volume", formatVolume(e.summary.lifetimeVolumeKg, LocalWeightUnit.current), Modifier.weight(1f), "lifted")
            StatTile("Streak", "${e.weeklyStreak}", Modifier.weight(1f), "weeks on target")
        }

        val earned = e.achievements.count { it.earned }
        SectionHeader("Achievements · $earned/${e.achievements.size}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tier.entries.forEach { tier ->
                val all = e.achievements.filter { it.def.tier == tier }
                Column(
                    Modifier.weight(1f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(tierColor(tier)))
                    Text("${all.count { it.earned }}/${all.size}", style = MaterialTheme.typography.titleSmall)
                    Text(tier.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        ChipRow(listOf("All") + AchievementGroup.entries.map { it.label }, group, { it }, { group = it })
        e.achievements
            .filter { group == "All" || it.def.group.label == group }
            .sortedWith(
                compareByDescending<Achievement> { it.earned }
                    .thenByDescending { it.earnedOn }
                    // Locked: closest to completion first, so the next goal is always on top.
                    .thenByDescending { it.fraction }
                    .thenBy { it.def.tier.ordinal }
            )
            .forEach { AchievementRow(it) }
        SecondaryButton("Settings", { nav.open(Routes.SETTINGS) }, icon = Icons.Filled.Settings)
    }
}

@Composable
private fun LevelCard(e: Engagement) {
    val maxed = e.level >= EngagementReplay.MAX_LEVEL - 1
    IronCard {
        Eyebrow(EngagementReplay.title(e.level))
        Row(verticalAlignment = Alignment.Bottom) {
            // Replay levels start at 0; people expect to start at level 1.
            Text(
                if (maxed) "MAX LEVEL" else "LEVEL ${e.level + 1}",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Text("%,d XP".format(e.totalXp), style = MaterialTheme.typography.titleMedium, color = IronTheme.colors.accent)
        }
        LinearProgressIndicator(
            progress = { if (maxed) 1f else e.xpIntoLevel / e.xpForNextLevel.toFloat() },
            modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
            color = IronTheme.colors.accent,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
        Text(
            if (maxed) "Level ${EngagementReplay.MAX_LEVEL} reached. Legend achievements are what's left."
            else "%,d XP to level ${e.level + 2} · level ${EngagementReplay.MAX_LEVEL} takes about two years of consistent training".format(e.xpForNextLevel - e.xpIntoLevel),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "XP: workout 100 · working set 5 · personal best 50 · cardio 50 + 1/min · habit 5 · step, water or sleep goal 10 · check-in 5 · complete food day 10 · achievements 25 (Bronze) to 1,000 (Legend).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatProgress(value: Double, unit: String, weight: app.ironlog.personal.domain.WeightUnit, length: app.ironlog.personal.domain.LengthUnit): String =
    when {
        unit == "km" -> "%s %s".format(length.distanceNumber(value), length.distanceLabel)
        unit == "× bodyweight" -> "%.2f×".format(value)
        unit == "kg" && weight.fromKg(value) >= 1_000 -> "%,.0f %s".format(weight.fromKg(value), weight.label)
        unit == "kg" -> weight.format(value)
        else -> "%,.0f".format(value)
    }

@Composable
private fun AchievementRow(a: Achievement) {
    val weight = LocalWeightUnit.current
    val length = LocalLengthUnit.current
    val color = tierColor(a.def.tier)
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp)
                    .clip(CircleShape)
                    .background(if (a.earned) color else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (a.earned) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = if (a.earned) "Earned" else "Locked",
                    tint = if (a.earned) Color.Black else color,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(a.def.title.uppercase(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                    Text(a.def.tier.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)
                }
                Text(length.localize(weight.localize(a.def.description)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (a.earned) {
                    Text("Earned ${a.earnedOn!!.format(EARNED)}", style = MaterialTheme.typography.labelSmall, color = color)
                } else if (a.def.target > 1) {
                    LinearProgressIndicator(
                        progress = { a.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        drawStopIndicator = {},
                    )
                    Text(
                        "${formatProgress(a.progress, a.def.unit, weight, length)} / ${formatProgress(a.def.target, a.def.unit, weight, length)}" +
                            if (a.def.unit.isNotEmpty() && a.def.unit != "kg" && a.def.unit != "km" && a.def.unit != "× bodyweight") " ${a.def.unit}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
