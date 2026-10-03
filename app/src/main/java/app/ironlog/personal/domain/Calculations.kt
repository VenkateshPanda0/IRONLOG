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
    fun timerRemainingMs(endAtEpochMs:Long,nowEpochMs:Long)= (endAtEpochMs-nowEpochMs).coerceAtLeast(0L)
    data class Performance(val sessionId:Long,val startedAt:Long,val status:String,val exerciseId:String,val weightKg:Double,val reps:Int,val volume:Double)
    data class PersonalRecord(val sessionId:Long,val type:String,val value:Double)
    fun newRecords(completed:List<Performance>):List<PersonalRecord> {
        val rows=completed.filter { it.status=="COMPLETED" }.sortedBy { it.startedAt }
        val records=mutableListOf<PersonalRecord>()
        rows.groupBy { it.exerciseId }.forEach { (_,performances) ->
            val bestByRep=mutableMapOf<Int,Double>(); var bestE1rm=Double.NEGATIVE_INFINITY; var bestVolume=Double.NEGATIVE_INFINITY
            performances.forEach { current ->
                for(target in listOf(1,3,5,8,10)) if(current.reps>=target && current.weightKg> (bestByRep[target]?:Double.NEGATIVE_INFINITY)) {
                    records += PersonalRecord(current.sessionId,"${target}RM",current.weightKg); bestByRep[target]=current.weightKg
                }
                e1rm(current.weightKg,current.reps)?.let { value -> if(value>bestE1rm) { records+=PersonalRecord(current.sessionId,"E1RM",value); bestE1rm=value } }
                if(current.volume>bestVolume) { records+=PersonalRecord(current.sessionId,"VOLUME",current.volume); bestVolume=current.volume }
            }
        }
        return records
    }
    fun targetCalories(sex: String, weightKg: Double, heightCm: Double, age: Int, activity: Double, goal: String): Int {
        val offset = if (sex.equals("male", true)) 5 else -161
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + offset
        val change = when (goal) { "LOSE" -> -350.0; "GAIN" -> 250.0; else -> 0.0 }
        return (bmr * activity + change).toInt().coerceAtLeast(1200)
    }
}
