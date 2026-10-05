package app.ironlog.personal.domain

import java.util.Locale
import kotlin.math.roundToLong

/**
 * How weights are shown and typed. Everything is stored in kilograms; this only converts at the
 * edges, so switching units never changes logged data.
 */
enum class WeightUnit(val label: String, private val perKg: Double, /** Smallest plate jump per side pair, in kg. */ val plateStepKg: Double) {
    KG("kg", 1.0, 2.5),
    LB("lb", 2.20462262185, 5.0 / 2.20462262185);

    fun fromKg(kg: Double): Double = kg * perKg

    fun toKg(value: Double): Double = value / perKg

    /** "100", "102.5" or "220.5": up to one decimal, as typed into a weight field. */
    fun number(kg: Double): String = trim(fromKg(kg))

    /** "100 kg" or "220.5 lb". */
    fun format(kg: Double): String = "${number(kg)} $label"

    /** Session and lifetime volume: "8,450 kg", "18.6k lb". */
    fun volume(kg: Double): String {
        val v = fromKg(kg)
        return if (v >= 10_000) "%.1fk %s".format(v / 1000, label) else "%,.0f %s".format(v, label)
    }

    /** Parses a typed weight (comma or dot decimal) into kg; null if not a number. */
    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.let(::toKg)

    /** Rounds a weight in kg to the nearest plate step of this unit (2.5 kg or 5 lb). */
    fun roundToPlates(kg: Double): Double = (kg / plateStepKg).roundToLong() * plateStepKg

    /**
     * Rewrites "150 kg" style amounts in fixed texts (achievement descriptions) into this unit,
     * e.g. "Bench press 150 kg" becomes "Bench press 331 lb".
     */
    fun localize(text: String): String =
        if (this == KG) text
        else Regex("""(\d[\d,]*(?:\.\d+)?) kg\b""").replace(text) { m ->
            val kg = m.groupValues[1].replace(",", "").toDouble()
            "%,.0f %s".format(fromKg(kg), label)
        }

    companion object {
        private fun trim(v: Double): String {
            val rounded = Math.round(v * 10) / 10.0
            return if (rounded % 1.0 == 0.0) "%.0f".format(rounded) else "%.1f".format(rounded)
        }

        /** Pounds where they are the everyday unit (United States, Liberia, Myanmar); kg elsewhere. */
        fun defaultFor(locale: Locale = Locale.getDefault()): WeightUnit = if (locale.country in setOf("US", "LR", "MM")) LB else KG

        fun of(name: String?): WeightUnit? = entries.firstOrNull { it.name == name }
    }
}

/**
 * How lengths are shown and typed: body measurements and height, and cardio distance and pace.
 * Storage stays in cm and km. IN shows inches, feet and inches for height, and miles.
 */
enum class LengthUnit(val label: String, val distanceLabel: String, private val perCm: Double, private val perKm: Double) {
    CM("cm", "km", 1.0, 1.0),
    IN("in", "mi", 1 / 2.54, 1 / 1.609344);

    fun fromCm(cm: Double) = cm * perCm

    fun toCm(value: Double) = value / perCm

    /** "84", "84.5" or "33.1": up to one decimal. */
    fun number(cm: Double): String = trim1(fromCm(cm))

    /** "84 cm" or "33.1 in". */
    fun format(cm: Double) = "${number(cm)} $label"

    fun parse(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.let(::toCm)

    /** Height: "180" in cm, or 5'11" in feet and inches. */
    fun height(cm: Double): String =
        if (this == CM) trim1(cm)
        else {
            val inches = Math.round(cm / 2.54).toInt()
            "${inches / 12}'${inches % 12}\""
        }

    /**
     * Parses a height into cm. In inches mode it accepts 5'11", 5' 11, 5 11, 5ft 11in, plain
     * inches (71) or plain feet (5.9 or less).
     */
    fun parseHeight(text: String): Double? {
        if (this == CM) return parse(text)
        val t = text.trim().lowercase()
        Regex("""^(\d)(?:\s*(?:'|ft|feet|’)\s*|\s+)(\d{1,2}(?:[.,]\d+)?)\s*(?:"|in|inches|”|'')?$""").find(t)?.let { m ->
            val feet = m.groupValues[1].toInt()
            val inches = m.groupValues[2].replace(',', '.').toDouble()
            if (inches < 12) return (feet * 12 + inches) * 2.54
        }
        Regex("""^(\d)\s*(?:'|ft|feet|’)$""").find(t)?.let { return it.groupValues[1].toInt() * 12 * 2.54 }
        val plain = t.replace(',', '.').toDoubleOrNull() ?: return null
        return if (plain < 9) plain * 12 * 2.54 else plain * 2.54
    }

    fun fromKm(km: Double) = km * perKm

    fun toKm(value: Double) = value / perKm

    /** "5.04 km" or "3.13 mi": up to two decimals. */
    fun distance(km: Double): String = "${trim2(fromKm(km))} $distanceLabel"

    fun distanceNumber(km: Double): String = trim2(fromKm(km))

    fun parseDistance(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.let(::toKm)

    /** Pace from minutes per km: "4:48 /km" or "7:43 /mi". */
    fun pace(minPerKm: Double): String {
        val perUnit = minPerKm / perKm
        val total = Math.round(perUnit * 60).toInt()
        return "%d:%02d /%s".format(total / 60, total % 60, distanceLabel)
    }

    /** Rewrites fixed texts: "Run 5 km at under 5:00 /km" becomes "Run 3.1 mi at under 8:03 /mi". */
    fun localize(text: String): String {
        if (this == CM) return text
        val paced =
            Regex("""(\d+):(\d{2}) /km""").replace(text) { m -> pace(m.groupValues[1].toInt() + m.groupValues[2].toInt() / 60.0) }
        return Regex("""(\d[\d,]*(?:\.\d+)?) km\b""").replace(paced) { m ->
            val km = m.groupValues[1].replace(",", "").toDouble()
            val v = fromKm(km)
            (if (v >= 100) "%,.0f".format(v) else trim1(v)) + " " + distanceLabel
        }
    }

    companion object {
        /** Inches, feet and miles where they are the everyday units (United States, Liberia, Myanmar). */
        fun defaultFor(locale: Locale = Locale.getDefault()): LengthUnit = if (locale.country in setOf("US", "LR", "MM")) IN else CM

        fun of(name: String?): LengthUnit? = entries.firstOrNull { it.name == name }
    }
}

private fun trim1(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r % 1.0 == 0.0) "%.0f".format(r) else "%.1f".format(r)
}

private fun trim2(v: Double): String {
    val r = Math.round(v * 100) / 100.0
    return when {
        r % 1.0 == 0.0 -> "%.0f".format(r)
        (r * 10) % 1.0 == 0.0 -> "%.1f".format(r)
        else -> "%.2f".format(r)
    }
}
