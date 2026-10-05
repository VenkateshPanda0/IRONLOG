package app.ironlog.personal.domain

import kotlin.math.roundToInt

/** Plates on each side of the bar, heaviest first, in the unit's own numbers (kg or lb). */
data class PlateLoad(val barKg: Double, val perSide: List<Double>, val loadedKg: Double) {
    /** Weight that cannot be made with the available plates (rounding left over). */
    fun shortByKg(targetKg: Double) = targetKg - loadedKg
}

object Plates {
    private val KG_PLATES = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    private val LB_PLATES = listOf(45.0, 35.0, 25.0, 10.0, 5.0, 2.5)

    /** A standard Olympic bar: 20 kg, or 45 lb. */
    fun barKg(unit: WeightUnit): Double = if (unit == WeightUnit.KG) 20.0 else unit.toKg(45.0)

    /** Greedy is exact for standard plate sets: each plate is at least the sum of the smaller pairs' gaps. */
    fun load(targetKg: Double, unit: WeightUnit, barKg: Double = barKg(unit)): PlateLoad {
        val plates = if (unit == WeightUnit.KG) KG_PLATES else LB_PLATES
        var side = unit.fromKg((targetKg - barKg) / 2) + 1e-6
        val out = mutableListOf<Double>()
        for (p in plates) while (side >= p) {
            out += p
            side -= p
        }
        return PlateLoad(barKg, out, barKg + 2 * unit.toKg(out.sum()))
    }
}

data class WarmupSet(val weightKg: Double, val reps: Int)

object Warmups {
    /**
     * Ramp to [workKg]: the empty bar for 10 when barbell, then about 40% × 5, 60% × 3 and 80% × 1,
     * each rounded to plates. Steps that would round to the bar, repeat a weight or reach the
     * working weight are dropped. Dumbbell and machine work gets 50% × 8 and 75% × 3.
     */
    fun plan(workKg: Double, unit: WeightUnit, barbell: Boolean): List<WarmupSet> {
        if (workKg <= 0) return emptyList()
        val bar = Plates.barKg(unit)
        val steps = if (barbell) listOf(0.4 to 5, 0.6 to 3, 0.8 to 1) else listOf(0.5 to 8, 0.75 to 3)
        val out = mutableListOf<WarmupSet>()
        if (barbell && workKg > bar * 1.25) out += WarmupSet(bar, 10)
        for ((fraction, reps) in steps) {
            val kg = unit.roundToPlates(workKg * fraction)
            if (kg <= 0 || kg >= workKg - 1e-6) continue
            if (barbell && kg <= bar + 1e-6) continue
            if (out.any { kotlin.math.abs(it.weightKg - kg) < 1e-6 }) continue
            out += WarmupSet(kg, reps)
        }
        return out
    }
}

/** One completed working set from an earlier session. */
data class PastSet(val weightKg: Double?, val reps: Int?, val rpe: Double? = null)

enum class CoachKind { INCREASE, REPEAT, DELOAD, START }

data class CoachTip(val kind: CoachKind, val weightKg: Double?, val repsLow: Int, val repsHigh: Int, val reason: String)

/**
 * Double progression: work within a rep range at one weight; once every working set reaches the
 * top of the range, add the smallest sensible jump; if two sessions in a row miss the bottom of
 * the range, take 10% off and build back. Effort ratings (RPE) refine the jump.
 */
object Coach {
    fun suggest(
        /** Earlier sessions' working sets, most recent session first. */
        sessions: List<List<PastSet>>,
        rangeMin: Int,
        rangeMax: Int,
        unit: WeightUnit,
        /** Squats, deadlifts, leg presses and similar take bigger jumps. */
        lowerBody: Boolean,
    ): CoachTip {
        // A restored backup could carry an inverted range; never let that crash the logger.
        val repMin = minOf(rangeMin, rangeMax).coerceAtLeast(1)
        val repMax = maxOf(rangeMin, rangeMax, repMin)
        val last = sessions.firstOrNull { list -> list.any { (it.reps ?: 0) > 0 } }
            ?: return CoachTip(CoachKind.START, null, repMin, repMax, "First time: pick a weight you can lift for $repMax reps with 2 left in the tank.")
        val loaded = last.filter { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 }
        if (loaded.isEmpty()) {
            // Bodyweight work: progress by reps.
            val best = last.maxOf { it.reps ?: 0 }
            return if (best >= repMax + 5) CoachTip(CoachKind.INCREASE, null, repMin, repMax, "$best reps last time: add weight (a vest or dumbbell) and work back up from $repMin.")
            else CoachTip(CoachKind.REPEAT, null, best + 1, best + 1, "Beat last time: aim for ${best + 1} reps on your best set.")
        }
        val top = loaded.maxOf { it.weightKg!! }
        val atTop = loaded.filter { kotlin.math.abs(it.weightKg!! - top) < 1e-6 }
        val reps = atTop.map { it.reps!! }
        val effort = atTop.mapNotNull { it.rpe }.takeIf { it.isNotEmpty() }?.average()
        val step = if (lowerBody) unit.plateStepKg * 2 else unit.plateStepKg

        if (reps.all { it >= repMax }) {
            // Every set topped out. Easy sets (RPE 7 or less) earn a double jump.
            val jumps = if (effort != null && effort <= 7.0) 2 else 1
            val next = unit.roundToPlates(top + step * jumps)
            val why = "You hit $repMax+ on all ${reps.size} sets at ${unit.format(top)}" + if (jumps == 2) " and it felt easy." else "."
            return CoachTip(CoachKind.INCREASE, next, repMin, repMax, why)
        }
        val missedNow = reps.any { it < repMin }
        val previous = sessions.drop(1).firstOrNull { list -> list.any { (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) > 0 } }
        val missedBefore =
            previous?.filter { (it.weightKg ?: 0.0) >= top - 1e-6 && (it.reps ?: 0) > 0 }?.let { s -> s.isNotEmpty() && s.any { it.reps!! < repMin } } == true
        if (missedNow && missedBefore) {
            val reset = unit.roundToPlates(top * 0.9)
            return CoachTip(CoachKind.DELOAD, reset, repMin, repMax, "Two sessions short of $repMin reps at ${unit.format(top)}. Reset to 90% and build back up; you'll pass it within a few weeks.")
        }
        val target = (reps.min() + 1).coerceIn(repMin, repMax)
        val hard = effort != null && effort >= 9.5
        val why =
            when {
                missedNow -> "Below $repMin on a set last time. Same weight, own every rep before adding more."
                hard -> "Close to failure last time (RPE ${"%.1f".format(effort).removeSuffix(".0")}). Same weight; add a rep only with good form."
                else -> "Same weight, add a rep: last time ${reps.joinToString(", ")}."
            }
        return CoachTip(CoachKind.REPEAT, top, target, repMax, why)
    }

    private val LOWER = setOf("quadriceps", "hamstrings", "glutes", "lower back", "adductors", "abductors")

    /** Lower-body compounds take double jumps; calves and isolation work single ones. */
    fun isLowerBody(primaryMuscles: Collection<String>, mechanic: String?): Boolean =
        primaryMuscles.any { it.lowercase() in LOWER } && mechanic.equals("compound", ignoreCase = true)

    /** Short display: "62.5 kg × 8–12" or "× 9". */
    fun label(tip: CoachTip, unit: WeightUnit): String {
        val reps = if (tip.repsLow == tip.repsHigh) "${tip.repsLow}" else "${tip.repsLow}–${tip.repsHigh}"
        return (tip.weightKg?.let { unit.format(it) + " × " } ?: "× ") + reps
    }

    /** Rounds an RPE input (6 to 10 in halves). */
    fun rpe(value: Double): Double = ((value * 2).roundToInt() / 2.0).coerceIn(5.0, 10.0)
}
