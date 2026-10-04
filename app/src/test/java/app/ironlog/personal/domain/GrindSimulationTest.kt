package app.ironlog.personal.domain

import app.ironlog.personal.data.repo.Engagement
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulates years of training to check that progression lasts: level 50 should take a dedicated
 * lifter around two years, while early levels and bronze achievements still arrive quickly.
 */
class GrindSimulationTest {
    private val day0 = LocalDate.parse("2026-01-05") // Monday

    private data class Profile(val days: Set<DayOfWeek>, val exercisesPerSession: Int, val setsPerExercise: Int, val foodDaysPerWeek: Int)

    private data class Outcome(
        val weekLevel: (Int) -> Int,
        val capWeek: Int?,
        val earnedShareAfter2Years: Double,
        val tiersEarned: Map<Tier, Int>,
        val legendGroupsAfter2Years: Set<AchievementGroup>,
    )

    private val lifts =
        listOf(
            "Barbell_Full_Squat" to 80.0,
            "Barbell_Bench_Press_-_Medium_Grip" to 60.0,
            "Barbell_Deadlift" to 100.0,
            "Bent_Over_Barbell_Row" to 60.0,
            "Standing_Military_Press" to 40.0,
            "Romanian_Deadlift" to 70.0,
        )

    /** Fast novice gains for six months, then a 2.5 kg jump roughly every 20 weeks (plateaus). */
    private fun load(start: Double, week: Int) = start + 1.25 * minOf(week, 26) + 2.5 * (maxOf(0, week - 26) / 20)

    private fun simulate(p: Profile, years: Int = 3): Outcome {
        val sets = mutableListOf<ExerciseSet>()
        val starts = mutableMapOf<Long, java.time.LocalDateTime>()
        val foodDays = mutableListOf<LocalDate>()
        var session = 0L
        val totalDays = 365L * years
        for (offset in 0 until totalDays) {
            val date = day0.plusDays(offset)
            val week = (offset / 7).toInt()
            if (date.dayOfWeek in p.days) {
                session++
                starts[session] = date.atTime(18, 0)
                val ms = date.toEpochDay() * 86_400_000
                // Rotate exercises across sessions.
                (0 until p.exercisesPerSession).forEach { i ->
                    val (id, start) = lifts[((session + i) % lifts.size).toInt()]
                    repeat(p.setsPerExercise) { sets += ExerciseSet(session, ms, id, id, "WORKING", load(start, week), 8) }
                }
            }
            if (date.dayOfWeek.value <= p.foodDaysPerWeek) foodDays += date
        }
        val dateOf = { ms: Long -> LocalDate.ofEpochDay(ms / 86_400_000) }
        val workouts = EngagementInputs.workouts(sets, dateOf)
        val achievements =
            Achievements.evaluate(
                AchievementInput(
                    sets = sets,
                    sessionStarts = starts,
                    dailyWeightKg = (0 until totalDays step 2).associate { day0.plusDays(it) to 80.0 },
                    profileWeightKg = 80.0,
                    musclesByExercise = emptyMap(),
                    mealDays = foodDays,
                    completeFoodDays = foodDays,
                    photoDates = (0 until years * 12).map { day0.plusMonths(it.toLong()) },
                    goalReachedOn = null,
                    plannedPerWeek = p.days.size,
                )
            )
        // XP timeline: workouts, complete food days and achievement bonuses on the day earned.
        val events = mutableListOf<Pair<LocalDate, Int>>()
        workouts.forEach { w -> events += w.date to (100 + (5 * w.completedWorkingSets).coerceAtMost(250) + 50 * w.newPrCount) }
        foodDays.forEach { events += it to 10 }
        achievements.filter { it.earned }.forEach { events += it.earnedOn!! to Engagement.tierXp(it.def.tier) }
        val byWeek = events.groupBy { ChronoUnit.WEEKS.between(day0, it.first).toInt() }.mapValues { (_, e) -> e.sumOf { it.second } }
        val cumulative = (0..(years * 53)).runningFold(0) { total, week -> total + (byWeek[week] ?: 0) }.drop(1)
        val capXp = EngagementReplay.xpForLevel(EngagementReplay.MAX_LEVEL - 1)
        val capWeek = cumulative.indexOfFirst { it >= capXp }.takeIf { it >= 0 }
        val twoYears = day0.plusYears(2)
        val earnedBy2 = achievements.count { it.earnedOn != null && it.earnedOn!! < twoYears }
        return Outcome(
            weekLevel = { week -> EngagementReplay.levelFor(cumulative[week]) + 1 },
            capWeek = capWeek,
            earnedShareAfter2Years = earnedBy2 / achievements.size.toDouble(),
            tiersEarned = achievements.filter { it.earnedOn != null && it.earnedOn!! < twoYears }.groupingBy { it.def.tier }.eachCount(),
            legendGroupsAfter2Years =
                achievements.filter { it.def.tier == Tier.LEGEND && it.earnedOn != null && it.earnedOn!! < twoYears }.map { it.def.group }.toSet(),
        )
    }

    private val dedicated = Profile(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 5, 4, 7)
    private val regular = Profile(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), 5, 4, 5)
    private val casual = Profile(setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY), 4, 3, 0)

    @Test
    fun dedicatedLifterNeedsAboutTwoYearsForLevelFifty() {
        val o = simulate(dedicated)
        println("SIM dedicated: cap week=${o.capWeek} level@4w=${o.weekLevel(3)} @12w=${o.weekLevel(11)} @1y=${o.weekLevel(51)} @2y=${o.weekLevel(103)} achievements by 2y=${"%.0f".format(o.earnedShareAfter2Years * 100)}% ${o.tiersEarned}")
        val cap = o.capWeek
        assertTrue("capped at week $cap", cap != null && cap in 91..130) // ~1.75 to 2.5 years
        assertTrue(o.weekLevel(3) >= 3) // quick early wins
        assertTrue(o.weekLevel(51) in 25..45)
        // Two flawless years earn the consistency legends, but strength legends stay out of reach
        // for a typical lifter and roughly half the catalogue is still locked.
        assertTrue(o.legendGroupsAfter2Years.none { it in setOf(AchievementGroup.STRENGTH, AchievementGroup.ELITE, AchievementGroup.BODYWEIGHT, AchievementGroup.VOLUME) })
        assertTrue(o.earnedShareAfter2Years < 0.6)
    }

    @Test
    fun regularLifterTakesLongerAndCasualLifterKeepsProgressing() {
        val r = simulate(regular)
        val c = simulate(casual)
        println("SIM regular: cap week=${r.capWeek} @1y=${r.weekLevel(51)} @2y=${r.weekLevel(103)}")
        println("SIM casual: cap week=${c.capWeek} @12w=${c.weekLevel(11)} @1y=${c.weekLevel(51)} @3y=${c.weekLevel(155)}")
        assertTrue("regular capped at week ${r.capWeek}", r.capWeek == null || r.capWeek > 104)
        assertTrue(c.capWeek == null)
        assertTrue(c.weekLevel(11) >= 5)
        assertTrue(c.weekLevel(155) > c.weekLevel(51))
    }
}
