package app.ironlog.personal.ui.tour

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.ironlog.personal.ui.nav.Navigator
import app.ironlog.personal.ui.nav.Routes
import app.ironlog.personal.ui.theme.IronTheme

/** Screen positions of the things the tour points at, reported by the screens themselves. */
class TourAnchors {
    val bounds = mutableStateMapOf<String, Rect>()
}

val LocalTourAnchors = staticCompositionLocalOf { TourAnchors() }

/** Marks a UI element the tour can spotlight. */
fun Modifier.tourAnchor(key: String): Modifier =
    composed {
        val anchors = LocalTourAnchors.current
        onGloballyPositioned { anchors.bounds[key] = it.boundsInRoot() }
    }

object TourKeys {
    const val START_WORKOUT = "start_workout"
    const val PROFILE = "profile"
    fun tab(route: String) = "tab_$route"
}

/** One stop: the tab to show, the element to spotlight (if any) and what to say. */
data class TourStep(val title: String, val body: String, val route: String?, val anchor: String?)

fun tourSteps(name: String): List<TourStep> =
    listOf(
        TourStep(
            name.trim().substringBefore(' ').let { first -> if (first.isBlank()) "Welcome to Ironlog" else "Welcome, $first" },
            "A one-minute tour of where everything is. You can skip it now and replay it any time from Settings.",
            Routes.HOME, null,
        ),
        TourStep(
            "Home is your day",
            "Today's workout from your program, your week at a glance, and quick logging for water, steps, sleep and habits.",
            Routes.HOME, TourKeys.tab(Routes.HOME),
        ),
        TourStep(
            "Start a workout",
            "Tap here when you're at the gym. Each exercise shows last time's numbers and a coach suggestion: tick a set and the rest timer starts. Set details, rest and a Done button also appear on your lock screen.",
            Routes.HOME, TourKeys.START_WORKOUT,
        ),
        TourStep(
            "Train",
            "Switch or build programs, start a quick workout by muscle group and time, log cardio and see your workout history.",
            Routes.TRAIN, TourKeys.tab(Routes.TRAIN),
        ),
        TourStep(
            "Library",
            "876 exercises with photo and animated demos, instructions, your records and estimated 1RM trend for each.",
            Routes.LIBRARY, TourKeys.tab(Routes.LIBRARY),
        ),
        TourStep(
            "Nutrition",
            "Log meals from 15,800 built-in foods, Indian dishes first, or scan a barcode. Calories and protein are tracked against your targets.",
            Routes.NUTRITION, TourKeys.tab(Routes.NUTRITION),
        ),
        TourStep(
            "Progress",
            "Body weight trend, measurements, strength and volume charts, cardio and progress photos, plus the physique check against your goal look.",
            Routes.PROGRESS, TourKeys.tab(Routes.PROGRESS),
        ),
        TourStep(
            "You and your settings",
            "Your level, 183 medals to chase, and Settings: kg or lb, reminders, Health Connect, backup to a new phone, and this tour.",
            Routes.HOME, TourKeys.PROFILE,
        ),
        TourStep(
            "You're all set",
            "Start your first workout whenever you're ready. Consistency beats intensity: show up, log it, and let the coach handle the numbers.",
            Routes.HOME, null,
        ),
    )

/**
 * Guided tour over the real app: switches tabs as it goes and spotlights the element it
 * describes. Touches outside the card are blocked so the tour cannot be lost halfway.
 */
@Composable
fun AppTour(name: String, nav: Navigator, onClose: () -> Unit) {
    val steps = remember(name) { tourSteps(name) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val anchors = LocalTourAnchors.current
    LaunchedEffect(index) { step.route?.let(nav::tab) }
    BackHandler { if (index > 0) index-- else onClose() }
    val target = step.anchor?.let { anchors.bounds[it] }
    val density = LocalDensity.current
    val accent = IronTheme.colors.accent

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .pointerInput(Unit) { detectTapGestures { } }
            .semantics { paneTitle = "App tour" }
    ) {
        val heightPx = with(density) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            target?.let { r ->
                val pad = 8.dp.toPx()
                val hole = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
                drawRoundRect(Color.Transparent, hole.topLeft, hole.size, CornerRadius(16.dp.toPx()), blendMode = BlendMode.Clear)
                drawRoundRect(accent, hole.topLeft, hole.size, CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
            }
        }
        // The card sits on the side of the screen away from the spotlight.
        val below = target != null && target.center.y < heightPx / 2
        val cardModifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .then(
                    when {
                        target == null -> Modifier.align(Alignment.Center)
                        below -> Modifier.align(Alignment.TopCenter).padding(top = with(density) { target.bottom.toDp() } + 24.dp)
                        else -> Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { (heightPx - target.top).toDp() } + 24.dp)
                    }
                )
        Surface(cardModifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    steps.indices.forEach { i ->
                        Box(Modifier.size(width = if (i == index) 18.dp else 6.dp, height = 6.dp).clip(CircleShape).background(if (i <= index) accent else MaterialTheme.colorScheme.surfaceContainerHighest))
                    }
                }
                Text(step.title.uppercase(), style = MaterialTheme.typography.titleLarge)
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (index < steps.lastIndex) TextButton(onClick = onClose) { Text("SKIP TOUR") }
                    Spacer(Modifier.weight(1f))
                    if (index > 0) TextButton(onClick = { index-- }) { Text("BACK") }
                    Button(
                        onClick = { if (index < steps.lastIndex) index++ else onClose() },
                        shape = CircleShape,
                    ) {
                        Text(
                            when (index) {
                                0 -> "TAKE THE TOUR"
                                steps.lastIndex -> "LET'S GO"
                                else -> "NEXT"
                            }
                        )
                    }
                }
            }
        }
    }
}
