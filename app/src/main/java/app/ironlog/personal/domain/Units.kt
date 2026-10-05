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
