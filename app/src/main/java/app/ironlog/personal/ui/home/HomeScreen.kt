package app.ironlog.personal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.displayName
import app.ironlog.personal.domain.Calculations
import app.ironlog.personal.domain.DayMark
import app.ironlog.personal.domain.Training
import app.ironlog.personal.domain.WeekDay
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(c: AppContainer, nav: Navigator) {
    val today = remember { LocalDate.now() }
    val zone = remember { ZoneId.systemDefault() }
    val profile by c.profile.collectAsState(initial = null)
    val active by c.workouts.active.collectAsState(initial = null)
    val history by c.workouts.history.collectAsState(initial = emptyList())
    val weights by c.body.weights.collectAsState(initial = emptyList())
    val goal by c.goals.current.collectAsState(initial = null)
    val meals by remember(today) { c.nutrition.meals(today) }.collectAsState(initial = emptyList())
    val activeProgram by c.programs.active.collectAsState(initial = null)
    val programs by c.programs.programs.collectAsState(initial = emptyList())
    val program = programs.firstOrNull { it.id == activeProgram?.programId }
    val programDays by
        remember(activeProgram?.programId) { c.programs.days(activeProgram?.programId ?: -1L) }
            .collectAsState(initial = emptyList())
    val nextDay =
        activeProgram?.let { current ->
            programDays.getOrNull((current.currentDay - 1).coerceAtLeast(0).mod(programDays.size.coerceAtLeast(1)))
        }
    val nextPrescriptions by
        remember(nextDay?.id) { c.programs.prescriptions(nextDay?.id ?: -1L) }
            .collectAsState(initial = emptyList())
    val engagement by c.engagement.engagement.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val workoutDates =
        history.map { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }.toSet()
    val trainingWeekdays = Training.parseWeekdays(profile?.trainingWeekdays.orEmpty())
    val strip = Training.weekStrip(today, workoutDates, trainingWeekdays)
    val perWeek = profile?.daysPerWeek ?: 3
    val streak = Training.weeklyStreak(workoutDates, perWeek, today)
    val thisWeek = strip.count { it.mark == DayMark.DONE }

    Page(
        title = greeting(profile?.name),
        subtitle = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        actions = {
            engagement?.let {
                Surface(
                    onClick = { nav.open(Routes.PROFILE) },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = IronTheme.colors.accent,
                    contentColor = IronTheme.colors.onAccent,
                ) {
                    Text("LVL ${it.level + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
            IconButton(onClick = { nav.open(Routes.PROFILE) }) {
                Icon(Icons.Outlined.Person, contentDescription = "Profile and settings")
            }
        },
    ) {
        active?.let { session ->
            Surface(
                color = IronTheme.colors.accent,
                contentColor = IronTheme.colors.onAccent,
                shape = MaterialTheme.shapes.medium,
                onClick = {
                    scope.launch {
                        if (session.status == "PAUSED") c.workouts.resume(session.id)
                        nav.workout(session.id)
                    }
                },
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("WORKOUT IN PROGRESS", style = MaterialTheme.typography.labelMedium)
                        Text(session.name, style = MaterialTheme.typography.titleMedium)
                    }
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                }
            }
        }

        IronCard {
            Eyebrow(if (program != null) "Today · ${program.displayName}" else "Today")
            if (program != null && nextDay != null) {
                Text(nextDay.name.uppercase(), style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${nextPrescriptions.size} exercises · ${nextPrescriptions.sumOf { it.targetSets }} sets",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PrimaryButton(
                    text = if (active == null) "Start workout" else "Resume workout",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        scope.launch {
                            val current = active
                            if (current != null) nav.workout(current.id)
                            else nav.workout(c.programs.start(program.id, nextDay.id))
                        }
                    },
                )
                TextButton(onClick = { nav.open(Routes.program(program.id)) }) {
                    Text("VIEW PROGRAM")
                }
            } else {
                Text("NO PROGRAM YET", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Pick a program or build one from your profile to get a daily plan.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrimaryButton("Choose a program", { nav.tab(Routes.TRAIN) })
            }
        }

        WeekStrip(strip)

        app.ironlog.personal.ui.wellness.DailySection(c, nav)

        app.ironlog.personal.ui.physique.PhysiqueCard(c, nav)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("This week", "$thisWeek/$perWeek", Modifier.weight(1f), unit = "workouts")
            StatTile("Streak", "$streak", Modifier.weight(1f), unit = if (streak == 1) "week" else "weeks")
            StatTile("Total", "${history.size}", Modifier.weight(1f), unit = "sessions")
        }

        val kcal = meals.sumOf { it.kcal }
        IronCard(onClick = { nav.tab(Routes.NUTRITION) }) {
            Eyebrow("Nutrition today")
            Row(verticalAlignment = Alignment.CenterVertically) {
                val target = goal?.kcalTarget?.toDouble()
                ProgressRing(
                    progress = if (target != null && target > 0) (kcal / target).toFloat() else 0f,
                    size = 104.dp,
                    description = "Calories eaten today",
                ) {
                    Text("%.0f".format(kcal), style = MaterialTheme.typography.titleLarge)
                    Text(
                        target?.let { "of %.0f".format(it) } ?: "kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MacroBar("Protein", meals.sumOf { it.protein }, goal?.proteinG, IronTheme.colors.protein)
                    MacroBar("Carbs", meals.sumOf { it.carbs }, goal?.carbsG, IronTheme.colors.carbs)
                    MacroBar("Fat", meals.sumOf { it.fat }, goal?.fatG, IronTheme.colors.fat)
                }
            }
        }

        IronCard(onClick = { nav.tab(Routes.PROGRESS) }) {
            Eyebrow("Body weight")
            if (weights.isEmpty()) {
                Text("No weigh-ins yet. Log one in Progress.")
            } else {
                val daily =
                    weights
                        .groupBy { LocalDate.parse(it.date) }
                        .mapValues { (_, rows) -> rows.maxBy { it.createdAt }.weightKg }
                val latest = daily.toSortedMap().entries.last()
                val mean = Calculations.sevenDayMean(daily, today)
                val previousMean = Calculations.sevenDayMean(daily, today.minusDays(7))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("%.1f".format(latest.value), style = MaterialTheme.typography.displaySmall)
                    Text(
                        " kg",
                        modifier = Modifier.padding(bottom = 6.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    when {
                        mean != null && previousMean != null ->
                            "7-day average %.1f kg · %+.1f kg vs last week".format(mean, mean - previousMean)
                        mean != null -> "7-day average %.1f kg".format(mean)
                        else -> "Trend appears after 3 weigh-ins in a week."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun greeting(name: String?): String {
    val hour = java.time.LocalTime.now().hour
    val part =
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    return if (name.isNullOrBlank()) part else "$part, ${name.trim().substringBefore(' ')}"
}

@Composable
private fun WeekStrip(days: List<WeekDay>) {
    val accent = IronTheme.colors.accent
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    day.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                    color =
                        if (day.isToday) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val base =
                    Modifier.size(38.dp)
                        .clip(CircleShape)
                        .then(
                            if (day.isToday)
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier
                        )
                Box(
                    base.background(
                        when (day.mark) {
                            DayMark.DONE -> accent
                            DayMark.PLANNED -> MaterialTheme.colorScheme.surfaceContainerHighest
                            DayMark.MISSED -> MaterialTheme.colorScheme.surfaceContainer
                            DayMark.REST -> Color.Transparent
                        }
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (day.mark == DayMark.DONE) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Workout done",
                            tint = IronTheme.colors.onAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Text(
                            day.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color =
                                if (day.mark == DayMark.MISSED) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
