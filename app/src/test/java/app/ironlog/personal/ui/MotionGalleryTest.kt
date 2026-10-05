package app.ironlog.personal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ironlog.personal.domain.Implement
import app.ironlog.personal.domain.MovementPattern
import app.ironlog.personal.ui.components.StickFigure
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders every movement pattern at its start and end to build/screens for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w800dp-h2400dp-mdpi")
class MotionGalleryTest {
    @get:Rule val compose = createComposeRule()

    private val sample =
        mapOf(
            MovementPattern.SQUAT to Implement.BARBELL, MovementPattern.HINGE to Implement.BARBELL, MovementPattern.LUNGE to Implement.DUMBBELL,
            MovementPattern.BENCH_PRESS to Implement.BARBELL, MovementPattern.FLY to Implement.DUMBBELL, MovementPattern.OVERHEAD_PRESS to Implement.BARBELL,
            MovementPattern.ROW to Implement.BARBELL, MovementPattern.CURL to Implement.DUMBBELL, MovementPattern.PUSHDOWN to Implement.CABLE,
            MovementPattern.OVERHEAD_EXTENSION to Implement.DUMBBELL, MovementPattern.RAISE to Implement.DUMBBELL, MovementPattern.UPRIGHT_ROW to Implement.BARBELL,
            MovementPattern.SHRUG to Implement.DUMBBELL, MovementPattern.CALF_RAISE to Implement.DUMBBELL, MovementPattern.HIP_THRUST to Implement.BARBELL,
            MovementPattern.PULLDOWN to Implement.CABLE,
        )

    @Test
    fun gallery() {
        compose.setContent {
            Column(Modifier.background(Color(0xFF111111)).padding(8.dp)) {
                MovementPattern.entries.chunked(3).forEach { row ->
                    Row {
                        row.forEach { m ->
                            Column(Modifier.width(260.dp)) {
                                Text(m.label, color = Color.White, fontSize = 12.sp)
                                Row {
                                    listOf(0.0, 1.0).forEach { t ->
                                        StickFigure(
                                            m, sample[m] ?: Implement.NONE, t,
                                            Modifier.size(125.dp, 95.dp).padding(2.dp).background(Color(0xFF222222)),
                                            Color.White, Color(0xFFD9FF4F), Color(0xFF777777),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.onRoot().captureRoboImage("build/screens/18_motion_gallery.png")
    }
}
