package app.ironlog.personal.time

import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/*
 * The small part of java.time the app uses, rebuilt on kotlinx-datetime so the same code runs on
 * Android and iOS. Names and behaviour follow java.time; only what Ironlog calls is here.
 */

fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

object ZoneId {
    fun systemDefault(): TimeZone = TimeZone.currentSystemDefault()
}

/** A moment seen in a time zone, like java.time.ZonedDateTime. */
data class ZonedDateTime(val instant: Instant, val zone: TimeZone) {
    private val local get() = instant.toLocalDateTime(zone)
    fun toLocalDate(): LocalDate = local.date
    fun toLocalDateTime(): LocalDateTime = local
    fun toLocalTime(): LocalTime = local.time
    fun toInstant(): Instant = instant
    val hour: Int get() = local.hour
    val minute: Int get() = local.minute
}

// LocalDate
fun LocalDate.Companion.now(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun LocalDate.Companion.of(year: Int, month: Int, day: Int): LocalDate = LocalDate(year, month, day)
fun LocalDate.Companion.ofEpochDay(day: Long): LocalDate = LocalDate.fromEpochDays(day.toInt())
val LocalDate.Companion.MIN: LocalDate get() = LocalDate(-9999, 1, 1)
fun LocalDate.plusDays(days: Long): LocalDate = plus(days.toInt(), DateTimeUnit.DAY)
fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Long): LocalDate = minus(days.toInt(), DateTimeUnit.DAY)
fun LocalDate.minusDays(days: Int): LocalDate = minus(days, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Long): LocalDate = plus(weeks.toInt() * 7, DateTimeUnit.DAY)
fun LocalDate.plusWeeks(weeks: Int): LocalDate = plus(weeks * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(weeks: Long): LocalDate = minus(weeks.toInt() * 7, DateTimeUnit.DAY)
fun LocalDate.minusWeeks(weeks: Int): LocalDate = minus(weeks * 7, DateTimeUnit.DAY)
fun LocalDate.plusMonths(months: Long): LocalDate = plus(DatePeriod(months = months.toInt()))
fun LocalDate.plusMonths(months: Int): LocalDate = plus(DatePeriod(months = months))
fun LocalDate.minusMonths(months: Long): LocalDate = minus(DatePeriod(months = months.toInt()))
fun LocalDate.minusMonths(months: Int): LocalDate = minus(DatePeriod(months = months))
fun LocalDate.minusYears(years: Long): LocalDate = minus(DatePeriod(years = years.toInt()))
fun LocalDate.minusYears(years: Int): LocalDate = minus(DatePeriod(years = years))
fun LocalDate.toEpochDay(): Long = toEpochDays().toLong()
fun LocalDate.isBefore(other: LocalDate): Boolean = this < other
fun LocalDate.isAfter(other: LocalDate): Boolean = this > other
fun LocalDate.withDayOfMonth(day: Int): LocalDate = LocalDate(year, monthNumber, day)
fun LocalDate.lengthOfMonth(): Int = withDayOfMonth(1).plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY).dayOfMonth
fun LocalDate.atStartOfDay(zone: TimeZone): ZonedDateTime = ZonedDateTime(atStartOfDayIn(zone), zone)
fun LocalDate.with(adjuster: (LocalDate) -> LocalDate): LocalDate = adjuster(this)
fun LocalDate.format(formatter: DateTimeFormatter): String = formatter.format(this)

/** ISO number (Monday 1 .. Sunday 7), java.time's DayOfWeek.getValue(). */
val DayOfWeek.value: Int get() = isoDayNumber

// Instant
fun Instant.Companion.ofEpochMilli(millis: Long): Instant = fromEpochMilliseconds(millis)
fun Instant.atZone(zone: TimeZone): ZonedDateTime = ZonedDateTime(this, zone)
fun Instant.toEpochMilli(): Long = toEpochMilliseconds()

// LocalDateTime and LocalTime
fun LocalDateTime.Companion.now(): LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
fun LocalDateTime.atZone(zone: TimeZone): ZonedDateTime = ZonedDateTime(toInstant(zone), zone)
fun LocalDateTime.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun LocalTime.Companion.now(): LocalTime = LocalDateTime.now().time
fun LocalTime.Companion.of(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

object ChronoUnit {
    object DAYS {
        fun between(start: LocalDate, end: LocalDate): Long = start.daysUntil(end).toLong()
    }
    object WEEKS {
        fun between(start: LocalDate, end: LocalDate): Long = start.daysUntil(end).toLong() / 7
    }
}

object TemporalAdjusters {
    fun previousOrSame(day: DayOfWeek): (LocalDate) -> LocalDate = { date ->
        date.minusDays(((date.dayOfWeek.isoDayNumber - day.isoDayNumber) + 7) % 7)
    }
}

data class YearMonth(val year: Int, val month: Int) : Comparable<YearMonth> {
    fun atDay(day: Int): LocalDate = LocalDate(year, month, day)
    fun plusMonths(months: Int): YearMonth = from(atDay(1).plusMonths(months))
    fun plusMonths(months: Long): YearMonth = plusMonths(months.toInt())
    fun minusMonths(months: Int): YearMonth = plusMonths(-months)
    fun minusMonths(months: Long): YearMonth = plusMonths(-months.toInt())
    override fun compareTo(other: YearMonth): Int = compareValuesBy(this, other, { it.year }, { it.month })
    companion object {
        fun from(date: LocalDate): YearMonth = YearMonth(date.year, date.monthNumber)
    }
}

/**
 * English date formatting for the patterns the app uses: EEEE EEE d dd M MM MMM MMMM yy yyyy
 * H HH h hh mm a, with quoted text and other characters copied as they are.
 */
class DateTimeFormatter private constructor(private val pattern: String, private val zone: TimeZone? = null) {
    fun withZone(zone: TimeZone): DateTimeFormatter = DateTimeFormatter(pattern, zone)
    fun format(instant: Instant): String = format(instant.toLocalDateTime(zone ?: error("Formatting an Instant needs withZone")))
    fun format(date: LocalDate): String = render(date, null)
    fun format(dateTime: LocalDateTime): String = render(dateTime.date, dateTime.time)
    fun format(zoned: ZonedDateTime): String = format(zoned.toLocalDateTime())

    private fun render(date: LocalDate, time: LocalTime?): String = buildString {
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            if (c == '\'') {
                val end = pattern.indexOf('\'', i + 1).let { if (it < 0) pattern.length else it }
                append(pattern, i + 1, end)
                i = end + 1
                continue
            }
            var n = 1
            while (i + n < pattern.length && pattern[i + n] == c) n++
            if (c.isLetter()) append(field(c, n, date, time)) else repeat(n) { append(c) }
            i += n
        }
    }

    private fun field(c: Char, n: Int, date: LocalDate, time: LocalTime?): String {
        fun needTime() = time ?: error("Pattern '$pattern' needs a time")
        return when (c) {
            'E' -> DAYS[date.dayOfWeek.isoDayNumber - 1].let { if (n >= 4) it else it.take(3) }
            'd' -> date.dayOfMonth.toString().padStart(n, '0')
            'M' -> when {
                n >= 4 -> MONTHS[date.monthNumber - 1]
                n == 3 -> MONTHS[date.monthNumber - 1].take(3)
                else -> date.monthNumber.toString().padStart(n, '0')
            }
            'y' -> if (n == 2) (date.year % 100).toString().padStart(2, '0') else date.year.toString()
            'H' -> needTime().hour.toString().padStart(n, '0')
            'h' -> (needTime().hour % 12).let { if (it == 0) 12 else it }.toString().padStart(n, '0')
            'm' -> needTime().minute.toString().padStart(n, '0')
            'a' -> if (needTime().hour < 12) "AM" else "PM"
            else -> error("Unsupported date pattern letter '$c' in '$pattern'")
        }
    }

    companion object {
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        private val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
        fun ofPattern(pattern: String): DateTimeFormatter = DateTimeFormatter(pattern)
    }
}

fun LocalDateTime.toLocalDate(): LocalDate = date
fun LocalDateTime.toLocalTime(): LocalTime = time
fun YearMonth.atEndOfMonth(): LocalDate = atDay(1).lengthOfMonth().let { atDay(it) }

/** java.util's toSortedMap: the same entries in key order (a map that keeps insertion order). */
fun <K : Comparable<K>, V> Map<out K, V>.toSortedMap(): Map<K, V> =
    entries.sortedBy { it.key }.associate { it.key to it.value }

fun LocalDate.atTime(time: LocalTime): LocalDateTime = LocalDateTime(this, time)
fun LocalDateTime.isAfter(other: LocalDateTime): Boolean = this > other
fun LocalDateTime.isBefore(other: LocalDateTime): Boolean = this < other
fun LocalDateTime.plusDays(days: Long): LocalDateTime = LocalDateTime(date.plusDays(days), time)
fun LocalDateTime.plusDays(days: Int): LocalDateTime = LocalDateTime(date.plusDays(days), time)
fun LocalDateTime.minusDays(days: Long): LocalDateTime = LocalDateTime(date.minusDays(days), time)
fun LocalDateTime.minusDays(days: Int): LocalDateTime = LocalDateTime(date.minusDays(days), time)
fun LocalDateTime.withHour(hour: Int): LocalDateTime = LocalDateTime(date, LocalTime(hour, time.minute, time.second, time.nanosecond))
fun LocalDateTime.withMinute(minute: Int): LocalDateTime = LocalDateTime(date, LocalTime(time.hour, minute, time.second, time.nanosecond))
fun LocalDateTime.Companion.of(date: LocalDate, time: LocalTime): LocalDateTime = LocalDateTime(date, time)
fun ZonedDateTime.format(formatter: DateTimeFormatter): String = formatter.format(this)
fun LocalDate.Companion.now(zone: TimeZone): LocalDate = Clock.System.todayIn(zone)
/** Accepts null like a failed java.time parse would: throws, for callers wrapping it in runCatching. */
fun LocalDate.Companion.parse(text: String?): LocalDate = LocalDate.parse(text ?: throw IllegalArgumentException("No date"))
fun LocalDate.atTime(hour: Int, minute: Int): LocalDateTime = LocalDateTime(this, LocalTime(hour, minute))
fun LocalDate.plusYears(years: Int): LocalDate = plus(DatePeriod(years = years))
fun LocalDate.plusYears(years: Long): LocalDate = plus(DatePeriod(years = years.toInt()))
