package app.ironlog.personal.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Rule-based physique comparison from tape-measure circumferences: shoulders, waist, hips and
 * thigh. Their ratios (the V-taper, or Adonis index, is shoulders over waist) are compared with
 * the target ranges of a chosen physique type. Circumferences are what physique ratios are
 * defined on and can be repeated to about a centimetre, unlike widths read from a photo. No
 * language model is involved and nothing leaves the phone.
 */

/** Circumferences in cm plus height, all taken by the user with a tape measure. */
data class BodyProportions(
    val shouldersCm: Double,
    val waistCm: Double,
    val hipsCm: Double,
    val thighCm: Double,
    val heightCm: Double? = null,
) {
    val vTaper = shouldersCm / waistCm
    val waistToHip = waistCm / hipsCm
    val shoulderToHip = shouldersCm / hipsCm
    val thighToWaist = thighCm / waistCm
    /** Above 0.5 is the common marker that leaning out should come first. */
    val waistToHeight = heightCm?.takeIf { it > 0 }?.let { waistCm / it }

    companion object {
        /** Null unless every value is a plausible adult circumference. */
        fun of(shoulders: Double?, waist: Double?, hips: Double?, thigh: Double?, heightCm: Double?): BodyProportions? {
            if (shoulders == null || waist == null || hips == null || thigh == null) return null
            if (shoulders !in 60.0..200.0 || waist !in 40.0..200.0 || hips !in 50.0..200.0 || thigh !in 25.0..110.0) return null
            return BodyProportions(shoulders, waist, hips, thigh, heightCm)
        }
    }
}

enum class Metric(val label: String, val explain: String) {
    V_TAPER("V-taper", "Shoulders ÷ waist (golden ratio 1.62)"),
    WAIST_HIP("Waist to hip", "Waist ÷ hips"),
    SHOULDER_HIP("Shoulder to hip", "Shoulders ÷ hips"),
    THIGH_WAIST("Leg size", "Thigh ÷ waist"),
}

/** Target for one metric: [target] ± [tolerance]; [higherIsBetter] means overshooting is fine. */
data class MetricTarget(val metric: Metric, val target: Double, val tolerance: Double, val higherIsBetter: Boolean = false, val lowerIsBetter: Boolean = false)

enum class Sex { MALE, FEMALE }

/**
 * Popular physique goals with circumference-ratio targets. Values are coaching heuristics, not
 * judging criteria of any federation.
 */
enum class PhysiqueType(
    val title: String,
    val sex: Sex,
    val tagline: String,
    val description: String,
    val targets: List<MetricTarget>,
    val emphasis: List<String>,
    /** Drawing proportions for the illustration: shoulder, waist, hip, thigh, arm (relative widths). */
    val shape: FigureShape,
) {
    CLASSIC("Classic", Sex.MALE, "Golden-era V-taper", "Wide shoulders, small waist and full legs in balance. The look classic bodybuilding is judged on.",
        listOf(MetricTarget(Metric.V_TAPER, 1.62, 0.05, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.82, 0.05, higherIsBetter = true)),
        listOf("shoulders", "lats", "quadriceps", "hamstrings"), FigureShape(1.62, 1.0, 1.08, 0.68, 0.30)),
    MENS_PHYSIQUE("Men's Physique", Sex.MALE, "Beach-ready upper body", "Very wide shoulders and lats over a tight waist; legs lean rather than huge.",
        listOf(MetricTarget(Metric.V_TAPER, 1.65, 0.05, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.72, 0.06)),
        listOf("shoulders", "lats", "chest", "abdominals"), FigureShape(1.66, 0.96, 1.02, 0.58, 0.28)),
    BODYBUILDER("Bodybuilder", Sex.MALE, "Maximum muscle", "As much size as possible everywhere with a tapered waist and big legs.",
        listOf(MetricTarget(Metric.V_TAPER, 1.58, 0.05, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.90, 0.06, higherIsBetter = true)),
        listOf("shoulders", "lats", "chest", "quadriceps", "hamstrings", "arms"), FigureShape(1.6, 1.08, 1.12, 0.78, 0.36)),
    ATHLETIC("Athletic", Sex.MALE, "Strong and quick", "Muscular but mobile: broad shoulders, solid legs, a waist that is lean but not extreme.",
        listOf(MetricTarget(Metric.V_TAPER, 1.50, 0.05, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.76, 0.06)),
        listOf("shoulders", "lats", "quadriceps", "glutes"), FigureShape(1.46, 1.0, 1.05, 0.62, 0.26)),
    POWERLIFTER("Powerlifter", Sex.MALE, "Raw strength", "A thick, strong trunk and big legs. Waist size is not the goal; strength is.",
        listOf(MetricTarget(Metric.V_TAPER, 1.35, 0.06, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.85, 0.06, higherIsBetter = true)),
        listOf("quadriceps", "hamstrings", "lower back", "chest", "traps"), FigureShape(1.32, 1.22, 1.22, 0.72, 0.34)),
    LEAN("Lean & Fit", Sex.MALE, "Slim and defined", "A lean, defined frame with moderate muscle: the look of a runner or swimmer.",
        listOf(MetricTarget(Metric.V_TAPER, 1.45, 0.05, higherIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.68, 0.06)),
        listOf("shoulders", "lats", "abdominals"), FigureShape(1.38, 0.92, 0.98, 0.55, 0.22)),
    BIKINI("Bikini", Sex.FEMALE, "Toned curves", "Rounded glutes and shoulders, a small waist and a soft hourglass shape.",
        listOf(MetricTarget(Metric.WAIST_HIP, 0.70, 0.03, lowerIsBetter = true), MetricTarget(Metric.SHOULDER_HIP, 1.00, 0.05)),
        listOf("glutes", "shoulders", "hamstrings", "abdominals"), FigureShape(1.30, 0.86, 1.30, 0.64, 0.20)),
    WELLNESS("Wellness", Sex.FEMALE, "Strong lower body", "Big, strong glutes and legs with a tight waist and athletic upper body.",
        listOf(MetricTarget(Metric.WAIST_HIP, 0.65, 0.03, lowerIsBetter = true), MetricTarget(Metric.THIGH_WAIST, 0.95, 0.06, higherIsBetter = true)),
        listOf("glutes", "quadriceps", "hamstrings", "abductors"), FigureShape(1.28, 0.86, 1.38, 0.78, 0.21)),
    FIGURE("Figure", Sex.FEMALE, "Sculpted V and curves", "Wider shoulders and back with a small waist and shapely legs.",
        listOf(MetricTarget(Metric.SHOULDER_HIP, 1.08, 0.05, higherIsBetter = true), MetricTarget(Metric.WAIST_HIP, 0.70, 0.03, lowerIsBetter = true)),
        listOf("shoulders", "lats", "glutes", "quadriceps"), FigureShape(1.42, 0.88, 1.30, 0.66, 0.23)),
    ATHLETIC_F("Athletic", Sex.FEMALE, "Strong and capable", "Functional muscle all over: strong legs, defined arms and shoulders.",
        listOf(MetricTarget(Metric.SHOULDER_HIP, 1.02, 0.05), MetricTarget(Metric.WAIST_HIP, 0.74, 0.04, lowerIsBetter = true)),
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

    /** 100 at target, falling to 0 five tolerances beyond it, so months of progress show. */
    val score: Int =
        if (withinTarget) 100 else (100 - (abs(gap) - target.tolerance) / (5 * target.tolerance) * 100).coerceIn(0.0, 99.0).toInt()
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
     * Compares measured proportions with the goal and turns each gap into a training focus. The
     * waist-to-height ratio decides whether leaning out comes first: above 0.5 it does.
     */
    fun report(p: BodyProportions, type: PhysiqueType): PhysiqueReport {
        val waistToHeight = p.waistToHeight
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
