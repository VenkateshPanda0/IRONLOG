package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSummaryTest {
    private fun set(session: Long, at: Long, id: String, w: Double?, r: Int?, type: String = "WORKING") =
        ExerciseSet(session, at, id, id.uppercase(), type, w, r)

    @Test
    fun volumeSkipsWarmups() {
        val sets = listOf(set(1, 1, "a", 100.0, 5), set(1, 1, "a", 60.0, 10, "WARMUP"), set(1, 1, "a", 80.0, 8, "DROP"))
        assertEquals(1140.0, WorkoutMath.volume(sets), 0.0)
    }

    @Test
    fun personalBestsOnlyAgainstEarlierSessions() {
        val history =
            listOf(
                set(1, 100, "bench", 90.0, 5),
                set(2, 200, "bench", 95.0, 5),
                set(2, 200, "squat", 140.0, 3), // first time: never a PB
                set(3, 300, "bench", 100.0, 3), // later session must not affect session 2
            )
        val summary = WorkoutMath.summarize(2, history)
        assertEquals(2, summary.sets)
        assertEquals(475.0 + 420.0, summary.volumeKg, 0.0)
        assertEquals(listOf("Heaviest", "Est. 1RM"), summary.personalBests.map { it.kind })
        assertTrue(summary.personalBests.all { it.exerciseName == "BENCH" })
        assertEquals(90.0, summary.personalBests.first().previousKg, 0.0)
    }

    @Test
    fun dropWeightRoundsToPlateStep() {
        assertEquals(80.0, WorkoutMath.dropWeight(100.0)!!, 0.0)
        assertEquals(57.5, WorkoutMath.dropWeight(72.5)!!, 0.0)
        assertEquals(null, WorkoutMath.dropWeight(null))
    }

    @Test
    fun elapsedExcludesPauses() {
        assertEquals(50L, WorkoutMath.elapsedMs(0, null, 30, 80, now = 100))
        assertEquals(70L, WorkoutMath.elapsedMs(0, 100, 30, null, now = 500))
    }
}
