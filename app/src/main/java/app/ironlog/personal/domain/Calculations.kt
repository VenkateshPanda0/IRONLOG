package app.ironlog.personal.domain

import java.time.LocalDate

object Calculations {
    const val KG_TO_LB = 2.2046226218
    fun kgToLb(kg: Double) = kg * KG_TO_LB
    fun lbToKg(lb: Double) = lb / KG_TO_LB
    fun cmToIn(cm: Double) = cm / 2.54
    fun inToCm(inches: Double) = inches * 2.54
    fun foodMacro(per100g: Double, grams: Double) = per100g * grams / 100.0
    fun setVolume(weightKg: Double?, reps: Int?, completed: Boolean, type: String): Double =
        if (completed && (type == "WORKING" || type == "DROP")) (weightKg ?: 0.0) * (reps ?: 0) else 0.0
    fun e1rm(weightKg: Double, reps: Int): Double? = if (reps in 1..12) weightKg * (1.0 + reps / 30.0) else null
    fun sevenDayMean(values: Map<LocalDate, Double>, end: LocalDate): Double? {
        val points = (0L..6L).mapNotNull { values[end.minusDays(it)] }
        return if (points.size >= 3) points.average() else null
    }
    fun targetCalories(sex: String, weightKg: Double, heightCm: Double, age: Int, activity: Double, goal: String): Int {
        val offset = if (sex.equals("male", true)) 5 else -161
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + offset
        val change = when (goal) { "LOSE" -> -350.0; "GAIN" -> 250.0; else -> 0.0 }
        return (bmr * activity + change).toInt().coerceAtLeast(1200)
    }
}
