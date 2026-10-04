package app.ironlog.personal.domain

/**
 * Well-known gym lifts from the bundled exercise library, in preference order within each muscle.
 * Recommendations and quick workouts pick these before obscure variations so generated plans
 * read like a coach wrote them.
 */
object Staples {
    private val ordered =
        listOf(
            // chest
            "Barbell_Bench_Press_-_Medium_Grip",
            "Incline_Dumbbell_Press",
            "Dumbbell_Bench_Press",
            "Machine_Bench_Press",
            "Cable_Crossover",
            "Dumbbell_Flyes",
            "Dips_-_Chest_Version",
            "Pushups",
            "Push-Ups_With_Feet_Elevated",
            // lats
            "Pullups",
            "Wide-Grip_Lat_Pulldown",
            "Close-Grip_Front_Lat_Pulldown",
            "Chin-Up",
            "Straight-Arm_Pulldown",
            // middle back
            "Bent_Over_Barbell_Row",
            "Seated_Cable_Rows",
            "One-Arm_Dumbbell_Row",
            "T-Bar_Row_with_Handle",
            "Leverage_High_Row",
            "Inverted_Row",
            // shoulders
            "Standing_Military_Press",
            "Dumbbell_Shoulder_Press",
            "Side_Lateral_Raise",
            "Face_Pull",
            "Seated_Dumbbell_Press",
            "Arnold_Dumbbell_Press",
            "Reverse_Flyes",
            // quadriceps
            "Barbell_Full_Squat",
            "Leg_Press",
            "Hack_Squat",
            "Front_Barbell_Squat",
            "Leg_Extensions",
            "Dumbbell_Lunges",
            "Split_Squat_with_Dumbbells",
            "Goblet_Squat",
            "Bodyweight_Squat",
            "Dumbbell_Step_Ups",
            // hamstrings
            "Romanian_Deadlift",
            "Lying_Leg_Curls",
            "Seated_Leg_Curl",
            "Stiff-Legged_Dumbbell_Deadlift",
            "Good_Morning",
            "Glute_Ham_Raise",
            // glutes
            "Barbell_Hip_Thrust",
            "Butt_Lift_Bridge",
            "Single_Leg_Glute_Bridge",
            // lower back
            "Barbell_Deadlift",
            "Hyperextensions_Back_Extensions",
            // calves
            "Standing_Calf_Raises",
            "Seated_Calf_Raise",
            "Calf_Raise_On_A_Dumbbell",
            "Donkey_Calf_Raises",
            // biceps
            "Barbell_Curl",
            "Dumbbell_Bicep_Curl",
            "Hammer_Curls",
            "Preacher_Curl",
            "Cable_Hammer_Curls_-_Rope_Attachment",
            // triceps
            "Triceps_Pushdown_-_Rope_Attachment",
            "Triceps_Pushdown",
            "Close-Grip_Barbell_Bench_Press",
            "EZ-Bar_Skullcrusher",
            "Cable_Rope_Overhead_Triceps_Extension",
            "Dips_-_Triceps_Version",
            "Standing_Dumbbell_Triceps_Extension",
            "Push-Ups_-_Close_Triceps_Position",
            // abdominals
            "Hanging_Leg_Raise",
            "Cable_Crunch",
            "Plank",
            "Ab_Roller",
            "Crunches",
            "Russian_Twist",
            // traps
            "Barbell_Shrug",
            "Dumbbell_Shrug",
            "Cable_Shrugs",
            // forearms
            "Palms-Up_Barbell_Wrist_Curl_Over_A_Bench",
            "Seated_Dumbbell_Palms-Up_Wrist_Curl",
            "Palms-Down_Wrist_Curl_Over_A_Bench",
            "Cable_Wrist_Curl",
            "Wrist_Roller",
            // adductors and abductors
            "Thigh_Adductor",
            "Band_Hip_Adductions",
            "Thigh_Abductor",
            "Monster_Walk",
            // neck
            "Lying_Face_Down_Plate_Neck_Resistance",
            "Isometric_Neck_Exercise_-_Front_And_Back",
            "Isometric_Neck_Exercise_-_Sides",
        )

    private val rank = ordered.withIndex().associate { (index, id) -> id to index }

    /** Lower is better; exercises outside the staple list share the worst rank. */
    fun rank(id: String): Int = rank[id] ?: Int.MAX_VALUE

    /** Muscle group names used by split templates that cover several dataset muscle labels. */
    private val aliases = mapOf("back" to setOf("back", "lats", "middle back"))

    fun matches(target: String, muscle: String): Boolean =
        muscle == target || muscle in aliases[target].orEmpty()
}
