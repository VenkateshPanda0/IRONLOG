package app.ironlog.personal.domain

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.roundToInt

/*
 * Rule-based physique analysis. A front photo becomes a person mask (from on-device segmentation)
 * plus joint positions (from on-device pose detection). From those this file measures frontal
 * widths at the shoulders, waist, hips and thighs and turns them into ratios that are compared with
 * the target ranges of a chosen physique type. No language model is involved and nothing leaves the
 * phone. Frontal widths are a proxy for circumferences, so targets are approximate guides.
 */

enum class Joint { NOSE, LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW, LEFT_WRIST, RIGHT_WRIST, LEFT_HIP, RIGHT_HIP, LEFT_KNEE, RIGHT_KNEE, LEFT_ANKLE, RIGHT_ANKLE }

data class Point(val x: Float, val y: Float, val confidence: Float = 1f)

/** Person segmentation: true where a pixel belongs to the body. */
class BodyMask(val width: Int, val height: Int, private val pixels: BooleanArray) {
    init {
        require(pixels.size == width * height)
    }

    operator fun get(x: Int, y: Int): Boolean = x in 0 until width && y in 0 until height && pixels[y * width + x]

    companion object {
        fun of(width: Int, height: Int, inside: (Int, Int) -> Boolean) =
            BodyMask(width, height, BooleanArray(width * height) { i -> inside(i % width, i / width) })
    }
}

enum class PhotoIssue(val message: String) {
    BODY_NOT_FOUND("No person found. Stand in the middle of the frame with good light."),
    NOT_FULL_BODY("Your full body must be visible, head to ankles."),
    SIDE_ON("Face the camera straight on. Side-on photos cannot measure width."),
    TILTED("Hold the phone upright at chest height; your shoulders look tilted."),
    ARMS_TOUCHING("Hold your arms slightly away from your body so the waist can be measured."),
}

/** Raw measurements in pixels plus the ratios derived from them. */
data class BodyProportions(
    val shoulderWidth: Double,
    val waistWidth: Double,
    val hipWidth: Double,
    val leftThighWidth: Double,
    val rightThighWidth: Double,
    val bodyHeight: Double,
    val legToTorso: Double,
) {
    val thighWidth = (leftThighWidth + rightThighWidth) / 2
    val vTaper = shoulderWidth / waistWidth
    val waistToHip = waistWidth / hipWidth
    val shoulderToHip = shoulderWidth / hipWidth
    val thighToWaist = thighWidth / waistWidth
    /** 0 = perfectly even legs. */
    val thighAsymmetry = abs(leftThighWidth - rightThighWidth) / max(leftThighWidth, rightThighWidth)
}

data class Analysis(val proportions: BodyProportions?, val issues: List<PhotoIssue>)

object PhysiqueAnalyzer {
    private val required = listOf(Joint.LEFT_SHOULDER, Joint.RIGHT_SHOULDER, Joint.LEFT_HIP, Joint.RIGHT_HIP, Joint.LEFT_KNEE, Joint.RIGHT_KNEE)

    /** Width of the contiguous body run that contains [x] on row [y], tolerating 1-pixel holes. */
    internal fun runAround(mask: BodyMask, x: Int, y: Int): Int {
        if (!mask[x, y]) return 0
        var left = x
        while (mask[left - 1, y] || mask[left - 2, y]) left--
        var right = x
        while (mask[right + 1, y] || mask[right + 2, y]) right++
        return right - left + 1
    }

    /** Runs on a row as (start, end) pairs, left to right. */
    internal fun runs(mask: BodyMask, y: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var x = 0
        while (x < mask.width) {
            if (mask[x, y]) {
                val start = x
                while (x < mask.width && mask[x, y]) x++
                out += start to x - 1
            } else x++
        }
        return out
    }

    fun analyze(mask: BodyMask, joints: Map<Joint, Point>): Analysis {
        val issues = mutableListOf<PhotoIssue>()
        val area = (0 until mask.height step 4).sumOf { y -> (0 until mask.width step 4).count { x -> mask[x, y] } }
        if (area < mask.width * mask.height / 16 / 50) return Analysis(null, listOf(PhotoIssue.BODY_NOT_FOUND))
        if (required.any { (joints[it]?.confidence ?: 0f) < 0.5f }) return Analysis(null, listOf(PhotoIssue.NOT_FULL_BODY))
        val ls = joints.getValue(Joint.LEFT_SHOULDER)
        val rs = joints.getValue(Joint.RIGHT_SHOULDER)
        val lh = joints.getValue(Joint.LEFT_HIP)
        val rh = joints.getValue(Joint.RIGHT_HIP)
        val lk = joints.getValue(Joint.LEFT_KNEE)
        val rk = joints.getValue(Joint.RIGHT_KNEE)
        val ankles = listOfNotNull(joints[Joint.LEFT_ANKLE], joints[Joint.RIGHT_ANKLE]).filter { it.confidence >= 0.5f }
        if (ankles.isEmpty()) issues += PhotoIssue.NOT_FULL_BODY

        val shoulderY = (ls.y + rs.y) / 2
        val hipY = (lh.y + rh.y) / 2
        val torso = hipY - shoulderY
        if (torso <= 10) return Analysis(null, listOf(PhotoIssue.BODY_NOT_FOUND))
        val tilt = Math.toDegrees(atan2((rs.y - ls.y).toDouble(), (rs.x - ls.x).toDouble())).let { abs(abs(it) - if (abs(it) > 90) 180.0 else 0.0) }
        if (tilt > 10) issues += PhotoIssue.TILTED
        val centerAt = { y: Float ->
            val t = ((y - shoulderY) / torso).coerceIn(0f, 1.5f)
            ((ls.x + rs.x) / 2 * (1 - t) + (lh.x + rh.x) / 2 * t).roundToInt()
        }
        fun widthAt(y: Float) = runAround(mask, centerAt(y), y.roundToInt()).toDouble()

        // Shoulders: widest row just below the shoulder joints (deltoid breadth).
        val shoulder = (0..15).maxOf { widthAt(shoulderY + torso * it / 100f) }
        // Waist: narrowest row in the lower torso.
        val waist = (45..90).minOf { widthAt(shoulderY + torso * it / 100f) }
        // Hips: widest row around the hip joints.
        val hip = (-5..20).maxOf { widthAt(hipY + torso * it / 100f) }
        // Thighs: the two leg runs a third of the way from hip to knee.
        val kneeY = (lk.y + rk.y) / 2
        val thighY = (hipY + (kneeY - hipY) * 0.33f).roundToInt()
        val cx = centerAt(hipY)
        val legRuns = runs(mask, thighY)
        val leftLeg = legRuns.filter { it.second < cx + 2 }.maxByOrNull { it.second - it.first }
        val rightLeg = legRuns.filter { it.first > cx - 2 }.maxByOrNull { it.second - it.first }
        val (thighA, thighB) =
            if (leftLeg != null && rightLeg != null && leftLeg != rightLeg) {
                (leftLeg.second - leftLeg.first + 1.0) to (rightLeg.second - rightLeg.first + 1.0)
            } else {
                // Legs touching: split the single run in half.
                val w = runAround(mask, cx, thighY) / 2.0
                w to w
            }

        val top = (0 until mask.height).firstOrNull { y -> (0 until mask.width step 2).any { mask[it, y] } } ?: 0
        val bottom = ankles.maxOfOrNull { it.y } ?: kneeY
        val height = (bottom - top).toDouble().coerceAtLeast(1.0)
        val legs = (bottom - hipY).toDouble()

        // Frontal shoulder breadth is roughly a quarter of height; side-on photos are far narrower.
        if (shoulder / height < 0.16) issues += PhotoIssue.SIDE_ON
        // Arms hanging against the torso make the "waist" row include the arms. Wrists held clear of
        // the body sit outside the shoulder line; wrists inside it are resting on the torso or hips.
        val wrists = listOfNotNull(joints[Joint.LEFT_WRIST], joints[Joint.RIGHT_WRIST]).filter { it.confidence >= 0.5f }
        if (wrists.size == 2) {
            val waistCx = centerAt(shoulderY + torso * 0.7f)
            if (wrists.all { abs(it.x - waistCx) < shoulder * 0.62 }) issues += PhotoIssue.ARMS_TOUCHING
        }
        if (waist <= 0 || hip <= 0) return Analysis(null, issues + PhotoIssue.BODY_NOT_FOUND)
        return Analysis(BodyProportions(shoulder, waist, hip, thighA, thighB, height, legs / torso), issues)
    }
}

enum class Metric(val label: String, val explain: String) {
    V_TAPER("V-taper", "Shoulder width ÷ waist width"),
    WAIST_HIP("Waist to hip", "Waist width ÷ hip width"),
    SHOULDER_HIP("Shoulder to hip", "Shoulder width ÷ hip width"),
    THIGH_WAIST("Leg size", "Thigh width ÷ waist width"),
}

/** Target for one metric: [target] ± [tolerance]; [higherIsBetter] means overshooting is fine. */
data class MetricTarget(val metric: Metric, val target: Double, val tolerance: Double, val higherIsBetter: Boolean = false, val lowerIsBetter: Boolean = false)

enum class Sex { MALE, FEMALE }

/**
 * Popular physique goals with approximate frontal-width targets. Values are coaching heuristics
 * for photo comparison, not judging criteria of any federation.
 */
enum class PhysiqueType(
    val title: String,
    val sex: Sex,
    val tagline: String,
    val description: String,
    val targets: List<MetricTarget>,
    val emphasis: List<String>,
    /** Drawing proportions for the illustration: shoulder, waist, hip, thigh, arm (relative). */
    val shape: FigureShape,
) {
    CLASSIC("Classic", Sex.MALE, "Golden-era V-taper", "Wide shoulders, small waist and full legs in balance. The look classic bodybuilding is judged on.",
        listOf(MetricTarget(Metric.V_TAPER, 1.62, 0.07, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.68, 0.06, higherIsBetter = true)),
        listOf("shoulders", "lats", "quadriceps", "hamstrings"), FigureShape(1.62, 1.0, 1.08, 0.68, 0.30)),
    MENS_PHYSIQUE("Men's Physique", Sex.MALE, "Beach-ready upper body", "Very wide shoulders and lats over a tight waist; legs lean rather than huge.",
        listOf(MetricTarget(Metric.V_TAPER, 1.65, 0.07, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.58, 0.07)),
        listOf("shoulders", "lats", "chest", "abdominals"), FigureShape(1.66, 0.96, 1.02, 0.58, 0.28)),
    BODYBUILDER("Bodybuilder", Sex.MALE, "Maximum muscle", "As much size as possible everywhere with a tapered waist and big legs.",
        listOf(MetricTarget(Metric.V_TAPER, 1.58, 0.07, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.76, 0.07, higherIsBetter = true)),
        listOf("shoulders", "lats", "chest", "quadriceps", "hamstrings", "arms"), FigureShape(1.6, 1.08, 1.12, 0.78, 0.36)),
    ATHLETIC("Athletic", Sex.MALE, "Strong and quick", "Muscular but mobile: broad shoulders, solid legs, a waist that is lean but not extreme.",
        listOf(MetricTarget(Metric.V_TAPER, 1.45, 0.07, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.62, 0.06)),
        listOf("shoulders", "lats", "quadriceps", "glutes"), FigureShape(1.46, 1.0, 1.05, 0.62, 0.26)),
    POWERLIFTER("Powerlifter", Sex.MALE, "Raw strength", "A thick, strong trunk and big legs. Waist size is not the goal; strength is.",
        listOf(MetricTarget(Metric.V_TAPER, 1.30, 0.08, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.70, 0.07, higherIsBetter = true)),
        listOf("quadriceps", "hamstrings", "lower back", "chest", "traps"), FigureShape(1.32, 1.22, 1.22, 0.72, 0.34)),
    LEAN("Lean & Fit", Sex.MALE, "Slim and defined", "A lean, defined frame with moderate muscle: the look of a runner or swimmer.",
        listOf(MetricTarget(Metric.V_TAPER, 1.38, 0.07, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.56, 0.07)),
        listOf("shoulders", "lats", "abdominals"), FigureShape(1.38, 0.92, 0.98, 0.55, 0.22)),
    BIKINI("Bikini", Sex.FEMALE, "Toned curves", "Rounded glutes and shoulders, a small waist and a soft hourglass shape.",
        listOf(MetricTarget(Metric.WAIST_HIP, 0.70, 0.04, lowerIsBetter = true), MetricTarget(Metric.SHOULDER_HIP, 1.00, 0.06)),
        listOf("glutes", "shoulders", "hamstrings", "abdominals"), FigureShape(1.30, 0.86, 1.30, 0.64, 0.20)),
    WELLNESS("Wellness", Sex.FEMALE, "Strong lower body", "Big, strong glutes and legs with a tight waist and athletic upper body.",
        listOf(MetricTarget(Metric.WAIST_HIP, 0.67, 0.04, lowerIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.78, 0.07, higherIsBetter = true)),
        listOf("glutes", "quadriceps", "hamstrings", "abductors"), FigureShape(1.28, 0.86, 1.38, 0.78, 0.21)),
    FIGURE("Figure", Sex.FEMALE, "Sculpted V and curves", "Wider shoulders and back with a small waist and shapely legs.",
        listOf(MetricTarget(Metric.SHOULDER_HIP, 1.06, 0.06, higherIsBetter = true), MetricTarget(Metric.WAIST_HIP, 0.70, 0.04, lowerIsBetter = true)),
        listOf("shoulders", "lats", "glutes", "quadriceps"), FigureShape(1.42, 0.88, 1.30, 0.66, 0.23)),
    ATHLETIC_F("Athletic", Sex.FEMALE, "Strong and capable", "Functional muscle all over: strong legs, defined arms and shoulders.",
        listOf(MetricTarget(Metric.SHOULDER_HIP, 1.00, 0.06), MetricTarget(Metric.WAIST_HIP, 0.75, 0.05, lowerIsBetter = true)),
        listOf("quadriceps", "glutes", "shoulders", "lats"), FigureShape(1.32, 0.95, 1.28, 0.66, 0.22));

    companion object {
        fun forSex(sex: String?): List<PhysiqueType> =
            when (sex?.uppercase()) {
                "MALE" -> entries.filter { it.sex == Sex.MALE }
                "FEMALE" -> entries.filter { it.sex == Sex.FEMALE }
                else -> entries.toList()
            }
    }
}

/** Relative widths used to draw a type's illustration (waist = 1.0 baseline scaled by [waist]). */
data class FigureShape(val shoulder: Double, val waist: Double, val hip: Double, val thigh: Double, val arm: Double)

data class MetricResult(val target: MetricTarget, val value: Double) {
    /** Positive when below target in the direction that matters. */
    val gap: Double = target.target - value

    val withinTarget: Boolean =
        when {
            target.higherIsBetter -> value >= target.target - target.tolerance
            target.lowerIsBetter -> value <= target.target + target.tolerance
            else -> abs(gap) <= target.tolerance
        }

    /** 100 at target, falling to 0 three tolerances away. */
    val score: Int =
        if (withinTarget) 100 else (100 - (abs(gap) - target.tolerance) / (3 * target.tolerance) * 100).coerceIn(0.0, 99.0).toInt()
}

enum class CalorieDirection(val label: String, val advice: String) {
    CUT("Lean out", "A 300–500 kcal daily deficit will shrink the waist faster than any exercise; keep protein high and lifting heavy."),
    RECOMP("Recomposition", "Eat near maintenance with high protein and train hard; the waist tightens while muscle grows."),
    LEAN_BULK("Lean bulk", "A 200–300 kcal surplus with high protein builds the size your goal needs without much fat."),
}

data class Finding(val title: String, val detail: String, val muscles: List<String>)

data class PhysiqueReport(
    val type: PhysiqueType,
    val results: List<MetricResult>,
    val match: Int,
    val findings: List<Finding>,
    val priorityMuscles: List<String>,
    val calories: CalorieDirection,
)

object PhysiqueCoach {
    fun value(p: BodyProportions, metric: Metric): Double =
        when (metric) {
            Metric.V_TAPER -> p.vTaper
            Metric.WAIST_HIP -> p.waistToHip
            Metric.SHOULDER_HIP -> p.shoulderToHip
            Metric.THIGH_WAIST -> p.thighToWaist
        }

    /**
     * Compares measured proportions with the goal and turns each gap into a training focus.
     * [waistToHeight] from a tape measurement (waist cm ÷ height cm), when known, decides whether
     * leaning out comes first: above 0.5 it does.
     */
    fun report(p: BodyProportions, type: PhysiqueType, waistToHeight: Double? = null): PhysiqueReport {
        val results = type.targets.map { MetricResult(it, value(p, it.metric)) }
        val findings = mutableListOf<Finding>()
        results.filterNot { it.withinTarget }.forEach { r ->
            findings +=
                when (r.target.metric) {
                    Metric.V_TAPER ->
                        Finding(
                            "Build your V-taper",
                            "Your V-taper is %.2f; ${type.title} is around %.2f. Widen the top with side delts and lats, and keep the waist tight.".format(r.value, r.target.target),
                            listOf("shoulders", "lats"),
                        )
                    Metric.WAIST_HIP ->
                        Finding(
                            "Tighten the waist",
                            "Waist-to-hip is %.2f against about %.2f. Lose fat around the waist and build the glutes to widen the hips.".format(r.value, r.target.target),
                            listOf("glutes", "abductors", "abdominals"),
                        )
                    Metric.SHOULDER_HIP ->
                        if (r.value < r.target.target)
                            Finding(
                                "Broaden the shoulders",
                                "Shoulder-to-hip is %.2f against about %.2f. Overhead presses, lateral raises and pull-ups add width up top.".format(r.value, r.target.target),
                                listOf("shoulders", "lats"),
                            )
                        else
                            Finding(
                                "Balance with the lower body",
                                "Your shoulders are wide for this goal (%.2f vs %.2f). Shift volume to glutes and legs.".format(r.value, r.target.target),
                                listOf("glutes", "quadriceps", "hamstrings"),
                            )
                    Metric.THIGH_WAIST ->
                        if (r.value < r.target.target)
                            Finding(
                                "Bring up your legs",
                                "Leg size is %.2f against about %.2f. Squats, leg press and Romanian deadlifts twice a week.".format(r.value, r.target.target),
                                listOf("quadriceps", "hamstrings", "glutes"),
                            )
                        else
                            Finding(
                                "Legs are ahead",
                                "Your legs are big for this goal (%.2f vs %.2f). Keep them, and put extra sets into the upper body.".format(r.value, r.target.target),
                                listOf("shoulders", "lats", "chest"),
                            )
                }
        }
        if (p.thighAsymmetry > 0.10) {
            findings += Finding("Even out your legs", "One thigh measures about ${(p.thighAsymmetry * 100).roundToInt()}% wider. Add single-leg work: split squats and lunges.", listOf("quadriceps", "glutes"))
        }
        val waistIssue = results.any { !it.withinTarget && (it.target.metric == Metric.V_TAPER || it.target.metric == Metric.WAIST_HIP) }
        val sizeIssue = results.any { !it.withinTarget && it.value < it.target.target && it.target.metric != Metric.WAIST_HIP }
        val calories =
            when {
                waistToHeight != null && waistToHeight > 0.5 -> CalorieDirection.CUT
                waistIssue && (waistToHeight == null || waistToHeight > 0.46) -> CalorieDirection.CUT
                sizeIssue && !waistIssue -> CalorieDirection.LEAN_BULK
                else -> CalorieDirection.RECOMP
            }
        val priority = (findings.flatMap { it.muscles } + type.emphasis).distinct().take(5)
        return PhysiqueReport(type, results, results.map { it.score }.average().roundToInt(), findings, priority, calories)
    }
}
