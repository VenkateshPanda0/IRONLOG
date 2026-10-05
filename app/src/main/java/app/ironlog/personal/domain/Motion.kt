package app.ironlog.personal.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Stick-figure movement demos. Each movement pattern is two side-view poses (start and end) given
 * as segment directions; the figure is built by forward kinematics from the ankle and then pinned
 * at the pattern's anchor joint (feet for standing work, hands for hanging, hips for lying or
 * seated work) so the anchor stays still while the rest moves. Angles are degrees in screen space:
 * 0 points up, 90 points the way the figure faces, 180 points down, 270 (or -90) points behind.
 */

/** Segment lengths as a fraction of standing height. */
object Body {
    const val SHIN = 0.25
    const val THIGH = 0.25
    const val TORSO = 0.30
    const val NECK = 0.04
    const val HEAD = 0.06
    const val UPPER_ARM = 0.17
    const val FOREARM = 0.15
    const val FOOT = 0.07
}

data class Vec(val x: Double, val y: Double) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)

    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)

    operator fun times(k: Double) = Vec(x * k, y * k)

    companion object {
        /** Unit vector for a direction angle (0 = up, 90 = forward). Screen y grows downward. */
        fun dir(degrees: Double): Vec = (degrees * PI / 180).let { Vec(sin(it), -cos(it)) }
    }
}

/**
 * One pose. [rearThigh]/[rearShin] are measured from the hip downward (hip→knee→ankle) and are
 * only used when the legs split, as in a lunge; otherwise both legs overlap in side view.
 */
data class Pose(
    val shin: Double,
    val thigh: Double,
    val torso: Double,
    val upperArm: Double,
    val forearm: Double,
    val rearThigh: Double? = null,
    val rearShin: Double? = null,
    /** Raises the whole figure off the floor (calf raises). */
    val lift: Double = 0.0,
    /** Raises the shoulders along the torso (shrugs). */
    val shrug: Double = 0.0,
) {
    fun lerp(to: Pose, t: Double): Pose {
        fun mix(a: Double, b: Double): Double {
            // Shortest way round, so 350 -> 10 turns 20 degrees, not 340.
            var d = (b - a) % 360
            if (d > 180) d -= 360
            if (d < -180) d += 360
            return a + d * t
        }
        fun mixN(a: Double?, b: Double?) = if (a != null && b != null) mix(a, b) else a ?: b
        return Pose(
            mix(shin, to.shin), mix(thigh, to.thigh), mix(torso, to.torso), mix(upperArm, to.upperArm), mix(forearm, to.forearm),
            mixN(rearThigh, to.rearThigh), mixN(rearShin, to.rearShin), lift + (to.lift - lift) * t, shrug + (to.shrug - shrug) * t,
        )
    }
}

/** Joint positions of one frame, in height units with the floor at y = 0 (negative is up). */
data class Skeleton(
    val ankle: Vec,
    val toe: Vec,
    val knee: Vec,
    val hip: Vec,
    val shoulder: Vec,
    val neck: Vec,
    val head: Vec,
    val elbow: Vec,
    val hand: Vec,
    val rearKnee: Vec?,
    val rearAnkle: Vec?,
    val rearToe: Vec?,
) {
    val joints get() = listOfNotNull(ankle, toe, knee, hip, shoulder, neck, head, elbow, hand, rearKnee, rearAnkle, rearToe)

    fun translate(by: Vec) =
        Skeleton(ankle + by, toe + by, knee + by, hip + by, shoulder + by, neck + by, head + by, elbow + by, hand + by, rearKnee?.plus(by), rearAnkle?.plus(by), rearToe?.plus(by))
}

enum class Anchor { FEET, HANDS, HIPS }

/** What is drawn around the figure. */
enum class Prop { FLOOR, BENCH, PULLUP_BAR, DIP_BARS, SEAT, PLATFORM, CABLE_HIGH, CABLE_LOW }

/** What the hands hold; decided from the exercise's equipment. */
enum class Implement { NONE, BARBELL, DUMBBELL, KETTLEBELL, CABLE, BAND }

enum class BarPosition { HANDS, BACK, HIPS }

enum class MovementPattern(
    val label: String,
    val anchor: Anchor,
    val start: Pose,
    val end: Pose,
    val props: Set<Prop> = setOf(Prop.FLOOR),
    /** Where a barbell sits when it is not in the hands. */
    val bar: BarPosition = BarPosition.HANDS,
) {
    SQUAT("Squat", Anchor.FEET, Pose(0.0, 0.0, 0.0, 210.0, 330.0), Pose(32.0, -75.0, 42.0, 240.0, 350.0), bar = BarPosition.BACK),
    HINGE("Hip hinge", Anchor.FEET, Pose(18.0, -50.0, 68.0, 180.0, 180.0), Pose(0.0, 0.0, 0.0, 180.0, 180.0)),
    LUNGE(
        "Lunge", Anchor.FEET,
        Pose(0.0, 0.0, 0.0, 180.0, 180.0, rearThigh = 185.0, rearShin = 190.0),
        Pose(12.0, -78.0, 0.0, 180.0, 180.0, rearThigh = 200.0, rearShin = 262.0),
    ),
    BENCH_PRESS("Lying press", Anchor.HIPS, Pose(0.0, -90.0, -90.0, 160.0, 0.0), Pose(0.0, -90.0, -90.0, 0.0, 0.0), setOf(Prop.FLOOR, Prop.BENCH)),
    FLY("Fly", Anchor.HIPS, Pose(0.0, -90.0, -90.0, 180.0, 60.0), Pose(0.0, -90.0, -90.0, 5.0, 0.0), setOf(Prop.FLOOR, Prop.BENCH)),
    OVERHEAD_PRESS("Overhead press", Anchor.FEET, Pose(0.0, 0.0, 0.0, 160.0, 5.0), Pose(0.0, 0.0, -4.0, 2.0, 0.0)),
    ROW("Row", Anchor.FEET, Pose(12.0, -25.0, 70.0, 180.0, 180.0), Pose(12.0, -25.0, 70.0, 255.0, 175.0)),
    PULLUP("Pull-up", Anchor.HANDS, Pose(0.0, 0.0, 0.0, 0.0, 0.0), Pose(10.0, -5.0, 5.0, 168.0, 12.0), setOf(Prop.PULLUP_BAR)),
    PULLDOWN("Pulldown", Anchor.HIPS, Pose(0.0, -90.0, -8.0, 4.0, 0.0), Pose(0.0, -90.0, -12.0, 165.0, 20.0), setOf(Prop.FLOOR, Prop.SEAT, Prop.CABLE_HIGH)),
    DIP("Dip", Anchor.HANDS, Pose(40.0, 0.0, 8.0, 180.0, 180.0), Pose(40.0, 0.0, 22.0, 268.0, 182.0), setOf(Prop.DIP_BARS)),
    PUSHUP("Push-up", Anchor.FEET, Pose(66.0, 66.0, 66.0, 180.0, 180.0), Pose(79.0, 79.0, 79.0, 262.0, 172.0)),
    PLANK("Plank", Anchor.FEET, Pose(80.0, 80.0, 80.0, 180.0, 90.0), Pose(79.0, 79.0, 79.0, 180.0, 90.0)),
    CURL("Curl", Anchor.FEET, Pose(0.0, 0.0, 0.0, 180.0, 180.0), Pose(0.0, 0.0, -3.0, 170.0, 15.0)),
    PUSHDOWN("Pushdown", Anchor.FEET, Pose(0.0, 0.0, 8.0, 180.0, 45.0), Pose(0.0, 0.0, 8.0, 180.0, 180.0), setOf(Prop.FLOOR, Prop.CABLE_HIGH)),
    OVERHEAD_EXTENSION("Overhead extension", Anchor.FEET, Pose(0.0, 0.0, 0.0, 350.0, 190.0), Pose(0.0, 0.0, 0.0, 355.0, 0.0)),
    RAISE("Raise", Anchor.FEET, Pose(0.0, 0.0, 0.0, 180.0, 175.0), Pose(0.0, 0.0, 0.0, 90.0, 88.0)),
    UPRIGHT_ROW("Upright row", Anchor.FEET, Pose(0.0, 0.0, 0.0, 180.0, 180.0), Pose(0.0, 0.0, 0.0, 280.0, 150.0)),
    SHRUG("Shrug", Anchor.FEET, Pose(0.0, 0.0, 0.0, 180.0, 180.0), Pose(0.0, 0.0, 0.0, 180.0, 180.0, shrug = 0.035)),
    CALF_RAISE("Calf raise", Anchor.FEET, Pose(0.0, 0.0, 0.0, 180.0, 180.0), Pose(0.0, 0.0, 0.0, 180.0, 180.0, lift = 0.05)),
    CRUNCH("Crunch", Anchor.HIPS, Pose(-40.0, 225.0, -90.0, -90.0, 90.0), Pose(-40.0, 225.0, -45.0, -45.0, 135.0)),
    LEG_RAISE("Leg raise", Anchor.HANDS, Pose(0.0, 0.0, 0.0, 0.0, 0.0), Pose(-90.0, -90.0, 2.0, 0.0, 0.0), setOf(Prop.PULLUP_BAR)),
    HIP_THRUST("Hip thrust", Anchor.FEET, Pose(-12.0, 230.0, -65.0, 115.0, 115.0), Pose(-8.0, -88.0, -90.0, 90.0, 90.0), setOf(Prop.FLOOR, Prop.BENCH), bar = BarPosition.HIPS),
    LEG_PRESS("Leg press", Anchor.HIPS, Pose(280.0, 190.0, -68.0, 150.0, 150.0), Pose(225.0, 225.0, -68.0, 150.0, 150.0), setOf(Prop.SEAT, Prop.PLATFORM)),
    LEG_EXTENSION("Leg extension", Anchor.HIPS, Pose(0.0, -90.0, -10.0, 180.0, 180.0), Pose(-90.0, -90.0, -10.0, 180.0, 180.0), setOf(Prop.FLOOR, Prop.SEAT)),
    LEG_CURL("Leg curl", Anchor.HIPS, Pose(-90.0, -90.0, -10.0, 180.0, 180.0), Pose(25.0, -90.0, -10.0, 180.0, 180.0), setOf(Prop.FLOOR, Prop.SEAT)),
    ;

    /** Builds the skeleton for progress [t] (0 = start, 1 = end), pinned at the anchor. */
    fun skeleton(t: Double): Skeleton {
        val pose = start.lerp(end, t)
        val free = Motion.build(pose)
        val pin = Motion.build(start)
        val target =
            when (anchor) {
                Anchor.FEET -> pin.ankle
                Anchor.HANDS -> pin.hand
                Anchor.HIPS -> pin.hip
            }
        val current =
            when (anchor) {
                Anchor.FEET -> free.ankle
                Anchor.HANDS -> free.hand
                Anchor.HIPS -> free.hip
            }
        return free.translate(target - current + Vec(0.0, -pose.lift))
    }
}

object Motion {
    fun build(p: Pose): Skeleton {
        val ankle = Vec(0.0, 0.0)
        val knee = ankle + Vec.dir(p.shin) * Body.SHIN
        val hip = knee + Vec.dir(p.thigh) * Body.THIGH
        val shoulder = hip + Vec.dir(p.torso) * (Body.TORSO + p.shrug)
        val neck = shoulder + Vec.dir(p.torso) * Body.NECK
        val head = neck + Vec.dir(p.torso) * Body.HEAD
        val elbow = shoulder + Vec.dir(p.upperArm) * Body.UPPER_ARM
        val hand = elbow + Vec.dir(p.forearm) * Body.FOREARM
        // Feet point forward, perpendicular to the shin.
        val toe = ankle + Vec.dir(p.shin + 90) * Body.FOOT
        val rearKnee = p.rearThigh?.let { hip + Vec.dir(it) * Body.THIGH }
        val rearAnkle = if (rearKnee != null && p.rearShin != null) rearKnee + Vec.dir(p.rearShin) * Body.SHIN else null
        val rearToe = if (rearAnkle != null && p.rearShin != null) rearAnkle + Vec.dir(p.rearShin + 270) * Body.FOOT * 0.8 else null
        return Skeleton(ankle, toe, knee, hip, shoulder, neck, head, elbow, hand, rearKnee, rearAnkle, rearToe)
    }

    /** Smooth back-and-forth: start, ease to the end, pause, ease back. [phase] is in [0, 1). */
    fun progress(phase: Double): Double {
        val p = ((phase % 1.0) + 1.0) % 1.0
        return when {
            p < 0.1 -> 0.0
            p < 0.45 -> ease((p - 0.1) / 0.35)
            p < 0.55 -> 1.0
            p < 0.9 -> 1.0 - ease((p - 0.55) / 0.35)
            else -> 0.0
        }
    }

    private fun ease(t: Double) = 0.5 - cos(t * PI) / 2

    private val rules: List<Pair<Regex, MovementPattern>> =
        listOf(
            "pull.?ups?|chin.?ups?|muscle.?up" to MovementPattern.PULLUP,
            "pull-?downs?|pullover" to MovementPattern.PULLDOWN,
            "(hanging|lying|captain).*(leg|knee) raise|(leg|knee) raise|toes.to.bar" to MovementPattern.LEG_RAISE,
            "\\bdips?\\b" to MovementPattern.DIP,
            "push.?ups?|press.?ups?" to MovementPattern.PUSHUP,
            "plank" to MovementPattern.PLANK,
            "leg press" to MovementPattern.LEG_PRESS,
            "leg extensions?" to MovementPattern.LEG_EXTENSION,
            "leg curls?|hamstring curl" to MovementPattern.LEG_CURL,
            "calf|calves" to MovementPattern.CALF_RAISE,
            "hip thrust|glute bridge|butt lift|\\bbridge\\b|hip lift" to MovementPattern.HIP_THRUST,
            "split squat|lunge|step.?ups?|bulgarian" to MovementPattern.LUNGE,
            "squat|hack|thruster" to MovementPattern.SQUAT,
            "deadlift|good ?morning|romanian|stiff.legged|rack pull|hyperextension|back extension|swing|clean|snatch|pull through" to MovementPattern.HINGE,
            "\\bfly|flye|crossover|pec deck|butterfly" to MovementPattern.FLY,
            "bench press|floor press|chest press|board press|(lying|incline|decline).*press|press.*(incline|decline)" to MovementPattern.BENCH_PRESS,
            "upright.*row" to MovementPattern.UPRIGHT_ROW,
            "face pull|reverse fly|rear delt|\\brows?\\b|rowing" to MovementPattern.ROW,
            "shrug" to MovementPattern.SHRUG,
            "skull ?crusher|(overhead|french|lying).*(triceps?|tricep) extension|triceps? extension" to MovementPattern.OVERHEAD_EXTENSION,
            "push-?down|kickback|triceps|tricep" to MovementPattern.PUSHDOWN,
            "curl" to MovementPattern.CURL,
            "(lateral|side|front|rear|y-|scaption)\\s*raise|\\braise" to MovementPattern.RAISE,
            "crunch|sit-?ups?|v-?ups?|jackknife|ab roller|rollout|russian twist|tuck" to MovementPattern.CRUNCH,
            "press|jerk|arnold" to MovementPattern.OVERHEAD_PRESS,
        ).map { (pattern, movement) -> Regex(pattern, RegexOption.IGNORE_CASE) to movement }

    /** The movement drawn for an exercise, or null when no pattern fits (photos are shown instead). */
    fun patternFor(name: String): MovementPattern? = rules.firstOrNull { (regex, _) -> regex.containsMatchIn(name) }?.second

    fun implementFor(name: String, equipment: String?): Implement {
        val text = "${equipment.orEmpty()} $name".lowercase()
        return when {
            "kettlebell" in text -> Implement.KETTLEBELL
            "dumbbell" in text -> Implement.DUMBBELL
            "cable" in text -> Implement.CABLE
            "band" in text -> Implement.BAND
            "barbell" in text || "e-z" in text || "ez bar" in text || "smith" in text || "trap bar" in text -> Implement.BARBELL
            else -> Implement.NONE
        }
    }
}
