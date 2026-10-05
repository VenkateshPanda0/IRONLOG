package app.ironlog.personal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExerciseStatsTest {
    @Test
    fun recordsIgnoreWarmupsAndTrendIsPerSessionBest() {
        val stats =
            ExerciseStats.from(
                listOf(
                    SetRecord(2, 2_000, "WORKING", 100.0, 5),
                    SetRecord(2, 2_000, "WARMUP", 140.0, 1),
                    SetRecord(1, 1_000, "WORKING", 90.0, 8),
                    SetRecord(1, 1_000, "DROP", 60.0, 15),
                )
            )
        assertEquals(2, stats.sessions)
        assertEquals(3, stats.sets)
        assertEquals(100.0, stats.heaviestKg!!, 0.0)
        assertEquals(15, stats.mostReps)
        assertEquals(900.0, stats.bestSetVolumeKg!!, 0.0)
        // 90 x 8 -> 114.0; 100 x 5 -> 116.67; the 15-rep drop set has no e1RM
        assertEquals(listOf(1_000L, 2_000L), stats.e1rmTrend.map { it.first })
        assertEquals(116.67, stats.bestE1rmKg!!, 0.01)
    }

    @Test
    fun bodyweightOnlyHistoryHasNoLoadRecords() {
        val stats = ExerciseStats.from(listOf(SetRecord(1, 1, "WORKING", null, 12)))
        assertNull(stats.bestE1rmKg)
        assertNull(stats.heaviestKg)
        assertEquals(12, stats.mostReps)
    }
}
