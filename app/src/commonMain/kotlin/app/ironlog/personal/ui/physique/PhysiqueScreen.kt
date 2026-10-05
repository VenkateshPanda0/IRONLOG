package app.ironlog.personal.ui.physique

import app.ironlog.personal.text.format

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ironlog.personal.AppContainer
import app.ironlog.personal.data.db.PhysiqueScanEntity
import app.ironlog.personal.data.repo.PhysiqueRepository
import app.ironlog.personal.domain.BodyProportions
import app.ironlog.personal.domain.MetricResult
import app.ironlog.personal.domain.PhysiqueCoach
import app.ironlog.personal.domain.PhysiqueReport
import app.ironlog.personal.domain.PhysiqueType
import app.ironlog.personal.ui.components.*
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme
import kotlinx.coroutines.launch

fun physiqueGoalOf(name: String?): PhysiqueType? = PhysiqueType.entries.firstOrNull { it.name == name }

/** Muscles to prioritise: the latest check's findings when there is one, else the goal's emphasis. */
fun physiqueFocus(goal: PhysiqueType?, latest: PhysiqueScanEntity?): List<String> =
    when {
        goal == null -> emptyList()
        latest == null -> goal.emphasis
        else -> PhysiqueCoach.report(PhysiqueRepository.proportions(latest), goal).priorityMuscles
    }

/** Popular physique goals as illustrated cards, two per row. */
@Composable
fun PhysiqueTypePicker(types: List<PhysiqueType>, selected: PhysiqueType?, onSelect: (PhysiqueType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        types.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { type ->
                    val on = type == selected
                    Surface(
                        onClick = { onSelect(type) },
                        shape = MaterialTheme.shapes.medium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            PhysiqueFigure(
                                type.shape,
                                Modifier.fillMaxWidth().height(150.dp),
                                fill = if (on) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(type.title.uppercase(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center)
                            Text(type.tagline, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, maxLines = 2)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PhysiqueScreen(c: AppContainer, nav: Navigator) {
    val profile by c.profile.collectAsState(initial = null)
    val scans by c.physique.scans.collectAsState(initial = emptyList())
    val measurements by c.wellness.measurements.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val goal = physiqueGoalOf(profile?.physiqueGoal)
    var picking by remember { mutableStateOf(false) }
    // Prefill from the latest values ever logged, one measurement at a time.
    val latest = remember(measurements) {
        fun last(pick: (app.ironlog.personal.data.db.BodyMeasurementEntity) -> Double?) = measurements.lastOrNull { pick(it) != null }?.let(pick)
        listOf(last { it.shouldersCm }, last { it.waistCm }, last { it.hipsCm }, last { it.thighCm })
    }
    val length = LocalLengthUnit.current
    val shown = remember(latest, length) { latest.map { it?.let(length::number).orEmpty() } }
    var shoulders by remember(shown) { mutableStateOf(shown[0]) }
    var waist by remember(shown) { mutableStateOf(shown[1]) }
    var hips by remember(shown) { mutableStateOf(shown[2]) }
    var thigh by remember(shown) { mutableStateOf(shown[3]) }
    var saved by remember { mutableStateOf(false) }
    // A prefilled value left as shown keeps its stored cm exactly (no in/cm rounding drift).
    fun num(i: Int, text: String) = if (text == shown[i] && latest[i] != null) latest[i] else length.parse(text)
    val proportions = BodyProportions.of(num(0, shoulders), num(1, waist), num(2, hips), num(3, thigh), profile?.heightCm)

    Page("Physique", onBack = { nav.back() }, subtitle = "Compare your proportions with your goal, measured with a tape.") {
        if (goal == null || picking) {
            SectionHeader("Pick your goal physique")
            Text("Choose the look you are training for. Your checks and program focus follow it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PhysiqueTypePicker(PhysiqueType.forSex(profile?.sex), goal) { type ->
                scope.launch { c.physique.setGoal(type) }
                picking = false
            }
            if (goal == null) return@Page
        } else {
            IronCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PhysiqueFigure(goal.shape, Modifier.width(80.dp).height(130.dp), fill = IronTheme.colors.accent)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Eyebrow("Goal physique")
                        Text(goal.title.uppercase(), style = MaterialTheme.typography.titleLarge)
                        Text(goal.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { picking = true }, contentPadding = PaddingValues(0.dp)) { Text("CHANGE GOAL") }
                    }
                }
            }
        }

        SectionHeader("Measure")
        MeasureGuide()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Shoulders (${length.label})", shoulders, { shoulders = it; saved = false }, number = true, modifier = Modifier.weight(1f))
            Field("Waist (${length.label})", waist, { waist = it; saved = false }, number = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Hips (${length.label})", hips, { hips = it; saved = false }, number = true, modifier = Modifier.weight(1f))
            Field("Thigh (${length.label})", thigh, { thigh = it; saved = false }, number = true, modifier = Modifier.weight(1f))
        }
        if (proportions == null) {
            Text("Enter all four measurements in ${if (length.label == "cm") "centimetres" else "inches"} to see how you compare.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val report = remember(proportions, goal) { PhysiqueCoach.report(proportions, goal) }
            ReportSection(proportions, report, previous = scans.firstOrNull { !PhysiqueRepository.isPhotoEstimate(it) })
            PrimaryButton(
                if (saved) "Saved" else "Save this check",
                enabled = !saved,
                onClick = {
                    scope.launch {
                        c.physique.save(proportions, goal, report.match)
                        saved = true
                    }
                },
            )
            SecondaryButton("Build a program with this focus", onClick = { nav.open(Routes.BUILDER) })
        }

        if (scans.isNotEmpty()) History(scans)
        Text(
            "Ratios are coaching guides, not competition criteria. Measure at the same time of day (morning, before eating) every 4 weeks and compare the trend.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeasureGuide() {
    IronCard {
        Eyebrow("How to measure")
        listOf(
            "Shoulders" to "around the widest point, over the deltoids, arms relaxed at your sides",
            "Waist" to "at the navel, standing relaxed, after breathing out",
            "Hips" to "around the widest part of the glutes, feet together",
            "Thigh" to "around the widest part of one thigh, just below the glutes",
        ).forEach { (name, how) ->
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(IronTheme.colors.accent))
                Spacer(Modifier.width(10.dp))
                Text("$name: $how", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("Keep the tape level and snug without pressing into the skin.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReportSection(p: BodyProportions, report: PhysiqueReport, previous: PhysiqueScanEntity?) {
    val you = shapeOf(p)
    val goalShape = report.type.shape.matchedTo(you)
    IronCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${report.match}%", style = MaterialTheme.typography.displaySmall)
            Text(" match to ${report.type.title}", Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        previous?.let {
            val change = report.match - it.matchScore
            Text(
                if (change >= 0) "+$change since your check on ${it.date}" else "$change since your check on ${it.date}",
                style = MaterialTheme.typography.bodySmall,
                color = if (change >= 0) IronTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhysiqueFigure(you, Modifier.width(120.dp).height(200.dp), fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f), outline = goalShape, outlineColor = IronTheme.colors.accent)
                Eyebrow("You · goal dashed")
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhysiqueFigure(goalShape, Modifier.width(120.dp).height(200.dp), fill = IronTheme.colors.accent)
                Eyebrow(report.type.title)
            }
        }
        report.results.forEach { MetricBar(it) }
    }
    report.findings.forEach { finding ->
        IronCard {
            Text(finding.title.uppercase(), style = MaterialTheme.typography.titleMedium)
            Text(finding.detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (report.findings.isEmpty()) {
        IronCard {
            Text("ON TARGET", style = MaterialTheme.typography.titleMedium)
            Text("Your proportions are inside the ${report.type.title} range. Keep building size evenly and stay lean.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    IronCard {
        Eyebrow("Train first")
        ChipFlow(report.priorityMuscles, null, { it.replaceFirstChar(Char::uppercase) }, {})
        Eyebrow("Nutrition")
        Text(report.calories.label.uppercase(), style = MaterialTheme.typography.titleMedium)
        Text(report.calories.advice, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricBar(r: MetricResult) {
    val t = r.target
    val max = t.target * 1.5
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(t.metric.label.uppercase(), style = MaterialTheme.typography.labelMedium)
            Text(
                "%.2f · goal %.2f".format(r.value, t.target),
                style = MaterialTheme.typography.labelMedium,
                color = if (r.withinTarget) IronTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BoxWithConstraints(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            val fraction = (r.value / max).toFloat().coerceIn(0f, 1f)
            val targetAt = (t.target / max).toFloat()
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(if (r.withinTarget) IronTheme.colors.success else IronTheme.colors.accent))
            Box(Modifier.offset(x = maxWidth * targetAt - 1.dp).width(2.dp).fillMaxHeight().background(MaterialTheme.colorScheme.onSurface))
        }
        Text(t.metric.explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun History(scans: List<PhysiqueScanEntity>) {
    val length = LocalLengthUnit.current
    SectionHeader("History")
    val taped = scans.filterNot { PhysiqueRepository.isPhotoEstimate(it) }
    if (taped.size >= 2) {
        val first = taped.last()
        val last = taped.first()
        val vFirst = first.shoulder / first.waist
        val vLast = last.shoulder / last.waist
        Text(
            "V-taper %.2f → %.2f since %s".format(vFirst, vLast, first.date),
            color = if (vLast >= vFirst) IronTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    scans.take(12).forEach { scan ->
        ListRow(
            title = "${scan.matchScore}% · ${physiqueGoalOf(scan.goal)?.title ?: scan.goal}",
            subtitle =
                if (PhysiqueRepository.isPhotoEstimate(scan)) "%s · V-taper %.2f · photo estimate".format(scan.date, scan.shoulder / scan.waist)
                else "%s · V-taper %.2f · waist %s".format(scan.date, scan.shoulder / scan.waist, length.format(scan.waist)),
            leading = { Icon(Icons.Filled.Accessibility, contentDescription = null) },
        )
    }
}

/** Entry card: goal figure with the latest match, or an invitation to pick a goal. */
@Composable
fun PhysiqueCard(c: AppContainer, nav: Navigator) {
    val profile by c.profile.collectAsState(initial = null)
    val scans by c.physique.scans.collectAsState(initial = emptyList())
    val goal = physiqueGoalOf(profile?.physiqueGoal)
    val latest = scans.firstOrNull()
    IronCard(onClick = { nav.open(Routes.PHYSIQUE) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Physique check")
                when {
                    goal == null -> {
                        Text("PICK YOUR GOAL LOOK", style = MaterialTheme.typography.titleMedium)
                        Text("Choose a physique type and compare your measurements with it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    latest == null -> {
                        Text(goal.title.uppercase(), style = MaterialTheme.typography.titleMedium)
                        Text("Measure shoulders, waist, hips and thigh to see how close you are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> {
                        Text("${latest.matchScore}% ${goal.title.uppercase()}", style = MaterialTheme.typography.titleMedium)
                        Text("Last check ${latest.date}. Re-check every 4 weeks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            PhysiqueFigure(
                (goal ?: PhysiqueType.CLASSIC).shape,
                Modifier.width(52.dp).height(84.dp),
                fill = if (goal != null) IronTheme.colors.accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
