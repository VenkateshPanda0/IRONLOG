package app.ironlog.personal.time

import app.ironlog.personal.text.format
import java.util.Locale
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** The shared (Android + iOS) formatting must print exactly what java.util / java.time print. */
class SharedFormattingTest {
    private fun java(pattern: String, vararg args: Any?) = String.format(Locale.US, pattern, *args)

    @Test
    fun numbersMatchJavaFormatter() {
        val values = listOf(0.0, -0.0, 0.05, 0.25, 0.5, 1.005, 2.675, 9.995, 12.5, 70.45, 84.0, 99.95, 999.5, 1234.5, 12345.678, -3.25, -0.04, 1e-7, 2.5e9, 152.36)
        val patterns = listOf("%.0f", "%.1f", "%.2f", "%,.0f", "%,.1f", "%+.1f", "%+,.0f", "%.1fk %s", "%.0f %s")
        for (p in patterns) for (v in values) {
            val args: Array<Any?> = if ("%s" in p) arrayOf(v, "kg") else arrayOf(v)
            assertEquals("$p of $v", java(p, *args), p.format(*args))
        }
        for (n in listOf(0, 7, 59, 999, 1000, 1234567, -42)) {
            for (p in listOf("%d", "%,d", "%02d", "%,d XP", "%d:%02d")) {
                val args: Array<Any?> = if (p.count { it == '%' } == 2) arrayOf(n, n) else arrayOf(n)
                assertEquals("$p of $n", java(p, *args), p.format(*args))
            }
        }
        assertEquals(java("%s × %d", "Bench", 8), "%s × %d".format("Bench", 8))
        assertEquals(java("V-taper %.2f → %.2f since %s", 1.234, 1.3, "1 Jan"), "V-taper %.2f → %.2f since %s".format(1.234, 1.3, "1 Jan"))
        assertEquals(java("100%% done"), "100%% done".format())
    }

    @Test
    fun datesMatchJavaTime() {
        val patterns = listOf("EEE d MMM", "EEEE d MMMM yyyy", "EEEE, d MMMM", "MMMM yyyy", "d MMM yyyy", "d MMM", "d/M", "d MMM, HH:mm", "EEE d MMM · h:mm a")
        val start = LocalDate(2024, 1, 1)
        for (day in 0..400 step 7) {
            val date = start.plusDays(day)
            for (hour in listOf(0, 9, 12, 13, 23)) {
                val dt = LocalDateTime(date, LocalTime(hour, (day * 7) % 60))
                for (p in patterns) {
                    val expected = java.time.format.DateTimeFormatter.ofPattern(p, Locale.US).format(dt.toJavaLocalDateTime())
                    assertEquals("$p on $dt", expected, DateTimeFormatter.ofPattern(p).format(dt))
                }
            }
        }
    }

    @Test
    fun dateArithmeticMatchesJavaTime() {
        var date = LocalDate(2023, 12, 25)
        repeat(500) {
            val j = date.toJavaLocalDate()
            assertEquals(j.plusDays(40).toString(), date.plusDays(40).toString())
            assertEquals(j.minusWeeks(3).toString(), date.minusWeeks(3).toString())
            assertEquals(j.plusMonths(1).toString(), date.plusMonths(1).toString())
            assertEquals(j.minusMonths(13).toString(), date.minusMonths(13).toString())
            assertEquals(j.lengthOfMonth(), date.lengthOfMonth())
            assertEquals(j.toEpochDay(), date.toEpochDay())
            assertEquals(
                j.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toString(),
                date.with(TemporalAdjusters.previousOrSame(kotlinx.datetime.DayOfWeek.MONDAY)).toString(),
            )
            assertEquals(j.dayOfWeek.value, date.dayOfWeek.value)
            val other = date.plusDays(it * 3 - 700)
            assertEquals(java.time.temporal.ChronoUnit.DAYS.between(j, other.toJavaLocalDate()), ChronoUnit.DAYS.between(date, other))
            assertEquals(java.time.temporal.ChronoUnit.WEEKS.between(j, other.toJavaLocalDate()), ChronoUnit.WEEKS.between(date, other))
            date = date.plusDays(3)
        }
    }
}
