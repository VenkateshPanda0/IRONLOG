package app.ironlog.personal.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Difficulty, from first-week wins to feats only elite lifters reach. */
enum class Tier(val label: String) {
    BRONZE("Bronze"),
    SILVER("Silver"),
    GOLD("Gold"),
    PLATINUM("Platinum"),
    LEGEND("Legend"),
}

enum class AchievementGroup(val label: String) {
    CONSISTENCY("Consistency"),
    STRENGTH("Strength"),
    ELITE("Elite"),
    BODYWEIGHT("Bodyweight"),
    VOLUME("Volume"),
    RECORDS("Records"),
    NUTRITION("Nutrition"),
    BODY("Body"),
    VARIETY("Variety"),
}

data class AchievementDef(
    val id: String,
    val title: String,
    val description: String,
    val tier: Tier,
    val group: AchievementGroup,
    val target: Double,
    val unit: String = "",
)

data class Achievement(val def: AchievementDef, val earnedOn: LocalDate?, val progress: Double) {
    val earned
        get() = earnedOn != null

    val fraction: Float
        get() = (progress / def.target).coerceIn(0.0, 1.0).toFloat()
}

/** Everything the achievements need, derived from local history. */
data class AchievementInput(
    /** Completed sets of completed workouts, any order. */
    val sets: List<ExerciseSet>,
    /** Local start time of each completed workout, by session ID. */
    val sessionStarts: Map<Long, LocalDateTime>,
    val dailyWeightKg: Map<LocalDate, Double>,
    val profileWeightKg: Double?,
    val musclesByExercise: Map<String, List<String>>,
    val mealDays: List<LocalDate>,
    val completeFoodDays: List<LocalDate>,
    val photoDates: List<LocalDate>,
    val goalReachedOn: LocalDate?,
    val plannedPerWeek: Int,
)

object Achievements {
    private val BENCH = setOf("Barbell_Bench_Press_-_Medium_Grip", "Wide-Grip_Barbell_Bench_Press", "Close-Grip_Barbell_Bench_Press")
    private val SQUAT = setOf("Barbell_Full_Squat", "Barbell_Squat", "Front_Barbell_Squat")
    private val DEADLIFT = setOf("Barbell_Deadlift", "Sumo_Deadlift")
    private val PRESS = setOf("Standing_Military_Press")
    private val PULLUPS = setOf("Pullups", "Chin-Up", "Wide-Grip_Rear_Pull-Up")
    private val PUSHUPS = setOf("Pushups", "Push-Ups_With_Feet_Elevated")

    private fun d(id: String, title: String, desc: String, tier: Tier, group: AchievementGroup, target: Double, unit: String = "") =
        AchievementDef(id, title, desc, tier, group, target, unit)

    private fun ladder(prefix: String, group: AchievementGroup, unit: String, vararg steps: Triple<Double, String, Tier>, desc: (Double) -> String) =
        steps.map { (target, title, tier) -> d("${prefix}_${target.toLong()}", title, desc(target), tier, group, target, unit) }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) "%,.0f".format(v) else "%s".format(v)

    private fun bw(lift: String, prefix: String, vararg steps: Triple<Double, String, Tier>) =
        steps.map { (ratio, title, tier) ->
            d("${prefix}_bw_${(ratio * 100).toInt()}", title, "$lift ${fmt(ratio)}× your bodyweight", tier, AchievementGroup.STRENGTH, ratio, "× bodyweight")
        }

    val ALL: List<AchievementDef> =
        buildList {
            // Consistency
            addAll(
                ladder(
                    "workouts", AchievementGroup.CONSISTENCY, "workouts",
                    Triple(1.0, "First Rep", Tier.BRONZE), Triple(5.0, "Getting Started", Tier.BRONZE), Triple(10.0, "Ten Down", Tier.BRONZE),
                    Triple(25.0, "Regular", Tier.SILVER), Triple(50.0, "Half Century", Tier.SILVER), Triple(100.0, "Centurion", Tier.GOLD),
                    Triple(250.0, "Iron Regular", Tier.PLATINUM), Triple(500.0, "Lifer", Tier.PLATINUM), Triple(1000.0, "Iron Monk", Tier.LEGEND),
                ) { "Finish ${fmt(it)} workout${if (it == 1.0) "" else "s"}" }
            )
            addAll(
                ladder(
                    "streak", AchievementGroup.CONSISTENCY, "weeks",
                    Triple(2.0, "Two in a Row", Tier.BRONZE), Triple(4.0, "Consistent", Tier.SILVER), Triple(12.0, "Relentless", Tier.GOLD),
                    Triple(26.0, "Half-Year Habit", Tier.PLATINUM), Triple(52.0, "Unbroken Year", Tier.LEGEND), Triple(104.0, "Two-Year Machine", Tier.LEGEND),
                ) { "Hit your weekly workout target ${fmt(it)} weeks in a row" }
            )
            // Time in the game: these cannot be rushed.
            add(d("tenure_182", "Six Months In", "Still training 6 months after your first workout", Tier.SILVER, AchievementGroup.CONSISTENCY, 182.0, "days"))
            add(d("tenure_365", "One Year Strong", "Still training 1 year after your first workout", Tier.GOLD, AchievementGroup.CONSISTENCY, 365.0, "days"))
            add(d("tenure_730", "Two-Year Veteran", "Still training 2 years after your first workout", Tier.PLATINUM, AchievementGroup.CONSISTENCY, 730.0, "days"))
            add(d("tenure_1095", "Three-Year Legend", "Still training 3 years after your first workout", Tier.LEGEND, AchievementGroup.CONSISTENCY, 1095.0, "days"))
            add(d("months_3", "Solid Quarter", "3 calendar months in a row with 8+ workouts each", Tier.BRONZE, AchievementGroup.CONSISTENCY, 3.0, "months"))
            add(d("months_6", "Half-Year Grind", "6 calendar months in a row with 8+ workouts each", Tier.SILVER, AchievementGroup.CONSISTENCY, 6.0, "months"))
            add(d("months_12", "Year-Round", "12 calendar months in a row with 8+ workouts each", Tier.GOLD, AchievementGroup.CONSISTENCY, 12.0, "months"))
            add(d("months_24", "Two Years, No Days Off", "24 calendar months in a row with 8+ workouts each", Tier.LEGEND, AchievementGroup.CONSISTENCY, 24.0, "months"))
            add(d("early_bird", "Early Riser", "Start a workout before 6:00", Tier.BRONZE, AchievementGroup.CONSISTENCY, 1.0))
            add(d("night_owl", "Night Shift", "Start a workout after 22:00", Tier.BRONZE, AchievementGroup.CONSISTENCY, 1.0))
            // Strength relative to bodyweight
            addAll(bw("Bench press", "bench", Triple(0.5, "Bench Starter", Tier.BRONZE), Triple(1.0, "Bodyweight Bench", Tier.SILVER), Triple(1.5, "Big Bench", Tier.GOLD), Triple(2.0, "Double Bodyweight Bench", Tier.PLATINUM), Triple(2.5, "Bench Titan", Tier.LEGEND)))
            addAll(bw("Squat", "squat", Triple(0.75, "Squat Starter", Tier.BRONZE), Triple(1.5, "Strong Legs", Tier.SILVER), Triple(2.0, "Double Bodyweight Squat", Tier.GOLD), Triple(2.5, "Squat Monster", Tier.PLATINUM), Triple(3.0, "Squat Titan", Tier.LEGEND)))
            addAll(bw("Deadlift", "deadlift", Triple(1.0, "First Pull", Tier.BRONZE), Triple(2.0, "Double Bodyweight Pull", Tier.SILVER), Triple(2.5, "Strong Back", Tier.GOLD), Triple(3.0, "Triple Bodyweight Pull", Tier.PLATINUM), Triple(3.5, "Deadlift Titan", Tier.LEGEND)))
            addAll(bw("Overhead press", "press", Triple(0.5, "Press Starter", Tier.BRONZE), Triple(0.75, "Solid Press", Tier.SILVER), Triple(1.0, "Bodyweight Press", Tier.GOLD), Triple(1.25, "Boulder Shoulders", Tier.PLATINUM), Triple(1.5, "Press Titan", Tier.LEGEND)))
            // Absolute numbers only elite lifters reach
            add(d("elite_bench_150", "150 Club Bench", "Bench press 150 kg", Tier.GOLD, AchievementGroup.ELITE, 150.0, "kg"))
            add(d("elite_bench_200", "200 Bench", "Bench press 200 kg", Tier.PLATINUM, AchievementGroup.ELITE, 200.0, "kg"))
            add(d("elite_bench_250", "Raw Legend Bench", "Bench press 250 kg", Tier.LEGEND, AchievementGroup.ELITE, 250.0, "kg"))
            add(d("elite_squat_200", "200 Squat", "Squat 200 kg", Tier.GOLD, AchievementGroup.ELITE, 200.0, "kg"))
            add(d("elite_squat_300", "300 Squat", "Squat 300 kg", Tier.LEGEND, AchievementGroup.ELITE, 300.0, "kg"))
            add(d("elite_deadlift_250", "250 Pull", "Deadlift 250 kg", Tier.GOLD, AchievementGroup.ELITE, 250.0, "kg"))
            add(d("elite_deadlift_300", "300 Pull", "Deadlift 300 kg", Tier.PLATINUM, AchievementGroup.ELITE, 300.0, "kg"))
            add(d("elite_deadlift_400", "Strongman", "Deadlift 400 kg", Tier.LEGEND, AchievementGroup.ELITE, 400.0, "kg"))
            add(d("elite_total_500", "500 Total", "Best bench + squat + deadlift reach 500 kg", Tier.GOLD, AchievementGroup.ELITE, 500.0, "kg"))
            add(d("elite_total_700", "700 Total", "Best bench + squat + deadlift reach 700 kg", Tier.PLATINUM, AchievementGroup.ELITE, 700.0, "kg"))
            add(d("elite_total_900", "Elite Total", "Best bench + squat + deadlift reach 900 kg", Tier.LEGEND, AchievementGroup.ELITE, 900.0, "kg"))
            // Bodyweight feats
            addAll(
                ladder(
                    "pullups", AchievementGroup.BODYWEIGHT, "reps",
                    Triple(1.0, "First Pull-Up", Tier.BRONZE), Triple(10.0, "Pull-Up Ten", Tier.SILVER), Triple(20.0, "Pull-Up Machine", Tier.GOLD),
                    Triple(30.0, "Pull-Up Thirty", Tier.PLATINUM), Triple(40.0, "Gravity Optional", Tier.LEGEND),
                ) { "${fmt(it)} unweighted pull-up${if (it == 1.0) "" else "s"} in one set" }
            )
            addAll(
                ladder(
                    "pushups", AchievementGroup.BODYWEIGHT, "reps",
                    Triple(10.0, "Push-Up Ten", Tier.BRONZE), Triple(25.0, "Push-Up 25", Tier.SILVER), Triple(50.0, "Push-Up 50", Tier.GOLD),
                    Triple(100.0, "Push-Up Century", Tier.LEGEND),
                ) { "${fmt(it)} push-ups in one set" }
            )
            // Volume
            addAll(
                ladder(
                    "volume", AchievementGroup.VOLUME, "kg",
                    Triple(1_000.0, "First Tonne", Tier.BRONZE), Triple(10_000.0, "10 Tonnes", Tier.BRONZE), Triple(100_000.0, "100 Tonnes", Tier.SILVER),
                    Triple(1_000_000.0, "Million Kilo Club", Tier.GOLD), Triple(5_000_000.0, "Mountain Mover", Tier.PLATINUM),
                    Triple(10_000_000.0, "10,000 Tonnes", Tier.LEGEND), Triple(25_000_000.0, "Lifetime of Iron", Tier.LEGEND),
                ) { "Lift ${fmt(it)} kg in total" }
            )
            addAll(
                ladder(
                    "session_volume", AchievementGroup.VOLUME, "kg",
                    Triple(5_000.0, "Big Day", Tier.SILVER), Triple(10_000.0, "Ten-Tonne Session", Tier.GOLD),
                    Triple(20_000.0, "Twenty-Tonne Session", Tier.PLATINUM), Triple(40_000.0, "Forty-Tonne Session", Tier.LEGEND),
                ) { "Lift ${fmt(it)} kg in a single workout" }
            )
            add(d("session_sets_25", "Volume Day", "Complete 25 working sets in one workout", Tier.SILVER, AchievementGroup.VOLUME, 25.0, "sets"))
            add(d("session_sets_50", "Marathon Session", "Complete 50 working sets in one workout", Tier.PLATINUM, AchievementGroup.VOLUME, 50.0, "sets"))
            // Records
            addAll(
                ladder(
                    "prs", AchievementGroup.RECORDS, "PRs",
                    Triple(1.0, "New Standard", Tier.BRONZE), Triple(10.0, "On the Rise", Tier.SILVER), Triple(25.0, "Record Breaker", Tier.SILVER),
                    Triple(100.0, "Unstoppable", Tier.GOLD), Triple(250.0, "PR Machine", Tier.PLATINUM), Triple(500.0, "Ever Stronger", Tier.LEGEND),
                ) { "Set ${fmt(it)} personal best${if (it == 1.0) "" else "s"}" }
            )
            // Nutrition
            add(d("first_meal", "First Bite", "Log your first meal", Tier.BRONZE, AchievementGroup.NUTRITION, 1.0))
            addAll(
                ladder(
                    "food_streak", AchievementGroup.NUTRITION, "days",
                    Triple(7.0, "Dialed In", Tier.SILVER), Triple(30.0, "Fuel Master", Tier.GOLD),
                    Triple(100.0, "Hundred-Day Diet", Tier.PLATINUM), Triple(365.0, "Year of Discipline", Tier.LEGEND),
                ) { "Log a complete food day ${fmt(it)} days in a row" }
            )
            // Body
            add(d("first_weigh_in", "On the Scale", "Log your first weigh-in", Tier.BRONZE, AchievementGroup.BODY, 1.0))
            add(d("weigh_ins_30", "Data Driven", "Log 30 weigh-ins", Tier.SILVER, AchievementGroup.BODY, 30.0, "weigh-ins"))
            add(d("weigh_ins_365", "Scale Devotee", "Log 365 weigh-ins", Tier.PLATINUM, AchievementGroup.BODY, 365.0, "weigh-ins"))
            add(d("first_photo", "Day One", "Add your first progress photo", Tier.BRONZE, AchievementGroup.BODY, 1.0))
            add(d("photos_12", "Visual Proof", "Add 12 progress photos", Tier.SILVER, AchievementGroup.BODY, 12.0, "photos"))
            add(d("goal_weight", "Goal Weight", "Reach your goal body weight", Tier.GOLD, AchievementGroup.BODY, 1.0))
            // Variety
            add(d("all_muscles_week", "Complete Physique", "Train all 17 muscle groups within one week", Tier.GOLD, AchievementGroup.VARIETY, 17.0, "groups"))
            add(d("exercises_25", "Explorer", "Log 25 different exercises", Tier.SILVER, AchievementGroup.VARIETY, 25.0, "exercises"))
            add(d("exercises_75", "Movement Library", "Log 75 different exercises", Tier.GOLD, AchievementGroup.VARIETY, 75.0, "exercises"))
            add(d("exercises_150", "Encyclopedia", "Log 150 different exercises", Tier.LEGEND, AchievementGroup.VARIETY, 150.0, "exercises"))
        }

    fun evaluate(input: AchievementInput): List<Achievement> {
        val sets = input.sets.sortedBy { it.startedAt }
        val dateOf = { set: ExerciseSet -> input.sessionStarts[set.sessionId]?.toLocalDate() }
        val sessions = input.sessionStarts.entries.sortedBy { it.value }
        val workoutDates = sessions.map { it.value.toLocalDate() }
        val working = sets.filter { !it.type.equals("WARMUP", true) }
        val results = mutableMapOf<String, Pair<LocalDate?, Double>>()

        fun count(prefix: String, dates: List<LocalDate>) {
            ALL.filter { it.id.startsWith(prefix + "_") }.forEach { def ->
                results[def.id] = dates.getOrNull(def.target.toInt() - 1) to dates.size.toDouble()
            }
        }

        /** Best running value over time; earned on the first date it reaches each target. */
        fun peak(ids: List<AchievementDef>, series: List<Pair<LocalDate, Double>>) {
            var best = 0.0
            val reached = mutableMapOf<String, LocalDate>()
            series.sortedBy { it.first }.forEach { (date, value) ->
                best = maxOf(best, value)
                ids.forEach { def -> if (best >= def.target && def.id !in reached) reached[def.id] = date }
            }
            ids.forEach { results[it.id] = reached[it.id] to best }
        }

        fun bodyweightOn(date: LocalDate): Double? =
            input.dailyWeightKg.filterKeys { it <= date }.maxByOrNull { it.key }?.value
                ?: input.profileWeightKg
                ?: input.dailyWeightKg.minByOrNull { it.key }?.value

        // Consistency
        count("workouts", workoutDates)
        val perWeek = input.plannedPerWeek.coerceAtLeast(1)
        val byWeek = workoutDates.groupingBy { it.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) }.eachCount().toSortedMap()
        var run = 0
        var bestRun = 0
        var previous: LocalDate? = null
        val streakReached = mutableMapOf<String, LocalDate>()
        byWeek.forEach { (week, n) ->
            run = if (n >= perWeek) (if (previous != null && week == previous!!.plusWeeks(1)) run + 1 else 1) else 0
            if (n >= perWeek) previous = week
            bestRun = maxOf(bestRun, run)
            ALL.filter { it.id.startsWith("streak_") }.forEach { def ->
                if (run >= def.target && def.id !in streakReached) streakReached[def.id] = week.plusDays(6)
            }
        }
        ALL.filter { it.id.startsWith("streak_") }.forEach { results[it.id] = streakReached[it.id] to bestRun.toDouble() }
        // Tenure: earned by the first workout at least N days after the first one.
        workoutDates.firstOrNull()?.let { first ->
            val span = java.time.temporal.ChronoUnit.DAYS.between(first, workoutDates.last()).toDouble()
            ALL.filter { it.id.startsWith("tenure_") }.forEach { def ->
                results[def.id] = workoutDates.firstOrNull { java.time.temporal.ChronoUnit.DAYS.between(first, it) >= def.target } to span
            }
        }
        // Consecutive calendar months with at least 8 workouts.
        run {
            val months = workoutDates.groupingBy { java.time.YearMonth.from(it) }.eachCount().filterValues { it >= 8 }.keys.sorted()
            var length = 0
            var best = 0
            val reached = mutableMapOf<String, LocalDate>()
            months.forEachIndexed { i, month ->
                length = if (i > 0 && months[i - 1].plusMonths(1) == month) length + 1 else 1
                best = maxOf(best, length)
                ALL.filter { it.id.startsWith("months_") }.forEach { def ->
                    if (length >= def.target && def.id !in reached) reached[def.id] = month.atEndOfMonth()
                }
            }
            ALL.filter { it.id.startsWith("months_") }.forEach { results[it.id] = reached[it.id] to best.toDouble() }
        }
        val early = sessions.firstOrNull { it.value.hour < 6 }?.value?.toLocalDate()
        val late = sessions.firstOrNull { it.value.hour >= 22 }?.value?.toLocalDate()
        results["early_bird"] = early to (if (early != null) 1.0 else 0.0)
        results["night_owl"] = late to (if (late != null) 1.0 else 0.0)

        // Strength: heaviest weight actually lifted for at least one rep, relative to bodyweight then.
        fun lifts(ids: Set<String>) =
            working.filter { it.exerciseId in ids && (it.weightKg ?: 0.0) > 0 && (it.reps ?: 0) >= 1 }
                .mapNotNull { set -> dateOf(set)?.let { it to set.weightKg!! } }
        fun relative(ids: Set<String>, prefix: String) {
            val series = lifts(ids).mapNotNull { (date, kg) -> bodyweightOn(date)?.takeIf { it > 0 }?.let { date to kg / it } }
            peak(ALL.filter { it.id.startsWith("${prefix}_bw_") }, series)
        }
        relative(BENCH, "bench")
        relative(SQUAT, "squat")
        relative(DEADLIFT, "deadlift")
        relative(PRESS, "press")
        peak(ALL.filter { it.id.startsWith("elite_bench_") }, lifts(BENCH))
        peak(ALL.filter { it.id.startsWith("elite_squat_") }, lifts(SQUAT))
        peak(ALL.filter { it.id.startsWith("elite_deadlift_") }, lifts(DEADLIFT))
        // Total: running best of each lift, summed whenever any of them improves.
        run {
            var b = 0.0
            var s = 0.0
            var dl = 0.0
            val totals =
                working.mapNotNull { set ->
                    val date = dateOf(set) ?: return@mapNotNull null
                    val kg = set.weightKg?.takeIf { (set.reps ?: 0) >= 1 } ?: return@mapNotNull null
                    when (set.exerciseId) {
                        in BENCH -> b = maxOf(b, kg)
                        in SQUAT -> s = maxOf(s, kg)
                        in DEADLIFT -> dl = maxOf(dl, kg)
                        else -> return@mapNotNull null
                    }
                    date to (b + s + dl)
                }
            peak(ALL.filter { it.id.startsWith("elite_total_") }, totals)
        }

        // Bodyweight feats: reps in one unweighted set.
        fun reps(ids: Set<String>) =
            working.filter { it.exerciseId in ids && (it.weightKg ?: 0.0) == 0.0 }.mapNotNull { set -> dateOf(set)?.let { it to (set.reps ?: 0).toDouble() } }
        peak(ALL.filter { it.id.startsWith("pullups_") }, reps(PULLUPS))
        peak(ALL.filter { it.id.startsWith("pushups_") }, reps(PUSHUPS))

        // Volume
        val bySession = working.groupBy { it.sessionId }
        val sessionVolume = bySession.mapNotNull { (id, rows) -> input.sessionStarts[id]?.toLocalDate()?.let { it to WorkoutMath.volume(rows) } }
        var cumulative = 0.0
        val running = sessionVolume.sortedBy { it.first }.map { (date, v) -> cumulative += v; date to cumulative }
        peak(ALL.filter { it.id.startsWith("volume_") }, running)
        peak(ALL.filter { it.id.startsWith("session_volume_") }, sessionVolume)
        peak(
            ALL.filter { it.id.startsWith("session_sets_") },
            bySession.mapNotNull { (id, rows) -> input.sessionStarts[id]?.toLocalDate()?.let { it to rows.size.toDouble() } },
        )

        // Records: personal bests per session, measured against earlier sessions.
        val bests = WorkoutMath.personalBestCounts(input.sets)
        val prDates = sessions.flatMap { (id, start) -> List(bests[id] ?: 0) { start.toLocalDate() } }
        count("prs", prDates)

        // Nutrition
        val meals = input.mealDays.sorted()
        results["first_meal"] = meals.firstOrNull() to meals.size.coerceAtMost(1).toDouble()
        run {
            val days = input.completeFoodDays.distinct().sorted()
            var length = 0
            var best = 0
            val reached = mutableMapOf<String, LocalDate>()
            days.forEachIndexed { i, day ->
                length = if (i > 0 && days[i - 1].plusDays(1) == day) length + 1 else 1
                best = maxOf(best, length)
                ALL.filter { it.id.startsWith("food_streak_") }.forEach { def -> if (length >= def.target && def.id !in reached) reached[def.id] = day }
            }
            ALL.filter { it.id.startsWith("food_streak_") }.forEach { results[it.id] = reached[it.id] to best.toDouble() }
        }

        // Body
        val weighIns = input.dailyWeightKg.keys.sorted()
        results["first_weigh_in"] = weighIns.firstOrNull() to weighIns.size.coerceAtMost(1).toDouble()
        results["weigh_ins_30"] = weighIns.getOrNull(29) to weighIns.size.toDouble()
        results["weigh_ins_365"] = weighIns.getOrNull(364) to weighIns.size.toDouble()
        val photos = input.photoDates.sorted()
        results["first_photo"] = photos.firstOrNull() to photos.size.coerceAtMost(1).toDouble()
        results["photos_12"] = photos.getOrNull(11) to photos.size.toDouble()
        results["goal_weight"] = input.goalReachedOn to (if (input.goalReachedOn != null) 1.0 else 0.0)

        // Variety
        run {
            val muscleDates =
                working.mapNotNull { set -> dateOf(set)?.let { date -> input.musclesByExercise[set.exerciseId].orEmpty().map { it to date } } }.flatten()
            var bestCovered = 0
            var earned: LocalDate? = null
            muscleDates.map { it.second }.distinct().sorted().forEach { end ->
                val covered = muscleDates.filter { it.second in end.minusDays(6)..end }.map { it.first }.toSet().size
                bestCovered = maxOf(bestCovered, covered)
                if (earned == null && covered >= 17) earned = end
            }
            results["all_muscles_week"] = earned to bestCovered.toDouble()
            val firstUse = working.mapNotNull { set -> dateOf(set)?.let { set.exerciseId to it } }.groupBy({ it.first }, { it.second }).mapValues { it.value.min() }
            val order = firstUse.values.sorted()
            listOf("exercises_25", "exercises_75", "exercises_150").forEach { id ->
                val n = ALL.first { it.id == id }.target.toInt()
                results[id] = order.getOrNull(n - 1) to order.size.toDouble()
            }
        }

        return ALL.map { def ->
            val (date, progress) = results[def.id] ?: (null to 0.0)
            Achievement(def, date, progress)
        }
    }
}
