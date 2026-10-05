package app.ironlog.personal.domain

import java.io.File
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    private fun dist(a: Vec, b: Vec) = hypot(a.x - b.x, a.y - b.y)

    @Test
    fun anchorStaysPutAndBonesKeepTheirLength() {
        MovementPattern.entries.forEach { m ->
            val first = m.skeleton(0.0)
            listOf(0.25, 0.5, 0.75, 1.0).forEach { t ->
                val s = m.skeleton(t)
                val (a, b) =
                    when (m.anchor) {
                        Anchor.FEET -> first.ankle to s.ankle
                        Anchor.HANDS -> first.hand to s.hand
                        Anchor.HIPS -> first.hip to s.hip
                    }
                val lifted = if (m == MovementPattern.CALF_RAISE) Body.FOOT else 1e-9
                assertTrue("${m.name} anchor moved at $t", dist(a, b) <= lifted)
                assertEquals(Body.SHIN, dist(s.ankle, s.knee), 1e-9)
                assertEquals(Body.THIGH, dist(s.knee, s.hip), 1e-9)
                assertEquals(Body.UPPER_ARM, dist(s.shoulder, s.elbow), 1e-9)
                assertEquals(Body.FOREARM, dist(s.elbow, s.hand), 1e-9)
            }
        }
    }

    @Test
    fun movementsActuallyMoveTheRightJoints() {
        fun travel(m: MovementPattern, pick: (Skeleton) -> Vec) = dist(pick(m.skeleton(0.0)), pick(m.skeleton(1.0)))
        // Squat: hips drop a lot, feet stay planted.
        val squatTop = MovementPattern.SQUAT.skeleton(0.0)
        val squatBottom = MovementPattern.SQUAT.skeleton(1.0)
        assertTrue(squatBottom.hip.y - squatTop.hip.y > 0.15)
        // Curl: the hand rises towards the shoulder, the elbow barely moves.
        assertTrue(travel(MovementPattern.CURL) { it.hand } > 0.2)
        assertTrue(travel(MovementPattern.CURL) { it.elbow } < 0.05)
        // Pull-up: the body rises to the fixed bar.
        assertTrue(MovementPattern.PULLUP.skeleton(0.0).head.y - MovementPattern.PULLUP.skeleton(1.0).head.y > 0.15)
        // Bench press: hands travel up from the chest.
        assertTrue(MovementPattern.BENCH_PRESS.skeleton(0.0).hand.y - MovementPattern.BENCH_PRESS.skeleton(1.0).hand.y > 0.2)
        // Every pattern except the plank shows visible movement somewhere.
        MovementPattern.entries.filter { it != MovementPattern.PLANK }.forEach { m ->
            val a = m.skeleton(0.0).joints
            val b = m.skeleton(1.0).joints
            assertTrue(m.name, a.zip(b).maxOf { (p, q) -> dist(p, q) } > 0.03)
        }
    }

    @Test
    fun standingFiguresKeepFeetOnTheFloorAndHeadsUp() {
        listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.CURL, MovementPattern.OVERHEAD_PRESS, MovementPattern.LUNGE).forEach { m ->
            listOf(0.0, 0.5, 1.0).forEach { t ->
                val s = m.skeleton(t)
                assertEquals(0.0, s.ankle.y, 1e-9)
                assertTrue("${m.name} head below hips", s.head.y < s.hip.y)
                s.rearAnkle?.let { assertTrue("${m.name} rear foot below floor", it.y < 0.02) }
            }
        }
        // The push-up hand stays close to the floor at the bottom.
        assertTrue(abs(MovementPattern.PUSHUP.skeleton(1.0).hand.y) < 0.05)
        assertTrue(abs(MovementPattern.PUSHUP.skeleton(0.0).hand.y) < 0.05)
    }

    @Test
    fun loopEasesOutAndBack() {
        assertEquals(0.0, Motion.progress(0.0), 0.0)
        assertEquals(1.0, Motion.progress(0.5), 0.0)
        assertEquals(0.0, Motion.progress(0.95), 0.0)
        assertTrue(Motion.progress(0.3) in 0.3..0.8)
    }

    @Test
    fun namesMapToSensiblePatterns() {
        val expect =
            mapOf(
                "Barbell Full Squat" to MovementPattern.SQUAT,
                "Barbell Deadlift" to MovementPattern.HINGE,
                "Romanian Deadlift" to MovementPattern.HINGE,
                "Barbell Bench Press - Medium Grip" to MovementPattern.BENCH_PRESS,
                "Incline Dumbbell Press" to MovementPattern.BENCH_PRESS,
                "Standing Military Press" to MovementPattern.OVERHEAD_PRESS,
                "Bent Over Barbell Row" to MovementPattern.ROW,
                "Pullups" to MovementPattern.PULLUP,
                "Wide-Grip Lat Pulldown" to MovementPattern.PULLDOWN,
                "Dips - Triceps Version" to MovementPattern.DIP,
                "Pushups" to MovementPattern.PUSHUP,
                "Dumbbell Bicep Curl" to MovementPattern.CURL,
                "Triceps Pushdown - Rope Attachment" to MovementPattern.PUSHDOWN,
                "Side Lateral Raise" to MovementPattern.RAISE,
                "Hanging Leg Raise" to MovementPattern.LEG_RAISE,
                "Leg Press" to MovementPattern.LEG_PRESS,
                "Lying Leg Curls" to MovementPattern.LEG_CURL,
                "Leg Extensions" to MovementPattern.LEG_EXTENSION,
                "Standing Calf Raises" to MovementPattern.CALF_RAISE,
                "Barbell Hip Thrust" to MovementPattern.HIP_THRUST,
                "Dumbbell Lunges" to MovementPattern.LUNGE,
                "Barbell Shrug" to MovementPattern.SHRUG,
                "Upright Barbell Row" to MovementPattern.UPRIGHT_ROW,
                "Crunches" to MovementPattern.CRUNCH,
                "Dumbbell Flyes" to MovementPattern.FLY,
                "Face Pull" to MovementPattern.ROW,
            )
        expect.forEach { (name, pattern) -> assertEquals(name, pattern, Motion.patternFor(name)) }
        assertNull(Motion.patternFor("Hamstring Stretch"))
        assertEquals(Implement.DUMBBELL, Motion.implementFor("Dumbbell Bicep Curl", "dumbbell"))
        assertEquals(Implement.BARBELL, Motion.implementFor("Barbell Full Squat", "barbell"))
        assertEquals(Implement.NONE, Motion.implementFor("Pullups", "body only"))
    }

    @Test
    fun mostOfTheStrengthLibraryAndEveryStapleGetsAnAnimation() {
        val strength =
            Json.parseToJsonElement(File("src/main/assets/seed/exercises.json").readText()).jsonArray.map { it.jsonObject }
                .filter { it["category"]?.jsonPrimitive?.contentOrNull in setOf("strength", "powerlifting", "olympic weightlifting") }
        val mapped = strength.count { Motion.patternFor(it.getValue("name").jsonPrimitive.content) != null }
        println("MOTION coverage: $mapped / ${strength.size}")
        assertTrue("only $mapped of ${strength.size}", mapped >= strength.size * 0.75)
        val names = strength.associate { it.getValue("id").jsonPrimitive.content to it.getValue("name").jsonPrimitive.content }
        // Sideways leg moves, neck isometrics and the wrist roller cannot be shown in a side view;
        // those keep their photo demos.
        val sideViewless = Regex("adduct|abduct|monster walk|neck|wrist roller", RegexOption.IGNORE_CASE)
        val missing = Staples.ids.filter { it in names && !sideViewless.containsMatchIn(names.getValue(it)) && Motion.patternFor(names.getValue(it)) == null }
        assertTrue("staples without animation: $missing", missing.isEmpty())
    }
}
