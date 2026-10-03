package app.ironlog.personal.domain

data class TrainingProfile(val daysPerWeek:Int,val goal:String,val equipment:String)
data class Recommendation(val template:String,val why:String)

object Recommender {
    fun suggest(profile:TrainingProfile):Recommendation {
        val days=profile.daysPerWeek.coerceIn(1,7)
        val template=when {
            days<=3 -> "Full Body 3x"
            days==4 -> "Upper / Lower"
            else -> "Push / Pull / Legs"
        }
        val equipmentNote=if(profile.equipment.equals("BODYWEIGHT",true)) "using bodyweight options" else "using your selected equipment"
        val goalNote=when(profile.goal.uppercase()) { "LOSE" -> "to support consistent training while losing weight"; "GAIN" -> "to support progressive strength training"; else -> "for a balanced strength routine" }
        return Recommendation(template,"Chosen for $days training days per week, $equipmentNote, and your goal $goalNote.")
    }
}
