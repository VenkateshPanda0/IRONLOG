package app.ironlog.personal.text

import kotlin.math.abs

/**
 * printf-style formatting that runs on every platform, for the conversions the app uses:
 * %d, %s, %f and %% with the flags `,` (grouping), `+`, `0` and `-`, a width and a precision.
 * Numbers round half-up from their shortest decimal form, as java.util.Formatter does, and
 * always use '.' and ',' so text is the same on every phone.
 */
fun String.format(vararg args: Any?): String {
    val out = StringBuilder()
    var next = 0
    var i = 0
    while (i < length) {
        val c = this[i]
        if (c != '%') {
            out.append(c)
            i++
            continue
        }
        var j = i + 1
        val flags = StringBuilder()
        while (j < length && this[j] in ",+0-") flags.append(this[j++])
        var width = 0
        while (j < length && this[j].isDigit()) width = width * 10 + (this[j++] - '0')
        var precision = -1
        if (j < length && this[j] == '.') {
            j++
            precision = 0
            while (j < length && this[j].isDigit()) precision = precision * 10 + (this[j++] - '0')
        }
        require(j < length) { "Bad format '$this'" }
        val conversion = this[j]
        val body =
            when (conversion) {
                '%' -> "%"
                's' -> args[next++].toString().let { if (precision >= 0) it.take(precision) else it }
                'd' -> integer((args[next++] as Number).toLong(), ',' in flags, '+' in flags)
                'f' -> decimal((args[next++] as Number).toDouble(), if (precision < 0) 6 else precision, ',' in flags, '+' in flags)
                else -> error("Unsupported conversion '%$conversion' in '$this'")
            }
        out.append(
            when {
                body.length >= width -> body
                '-' in flags -> body.padEnd(width)
                '0' in flags && conversion != 's' -> {
                    val sign = if (body.startsWith("-") || body.startsWith("+")) body.take(1) else ""
                    sign + body.drop(sign.length).padStart(width - sign.length, '0')
                }
                else -> body.padStart(width)
            }
        )
        i = j + 1
    }
    return out.toString()
}

private fun group(digits: String): String =
    digits.reversed().chunked(3).joinToString(",").reversed()

private fun integer(value: Long, grouping: Boolean, plus: Boolean): String {
    val digits = abs(value).toString().removePrefix("-")
    val body = if (grouping) group(digits) else digits
    return (if (value < 0) "-" else if (plus) "+" else "") + body
}

private fun decimal(value: Double, precision: Int, grouping: Boolean, plus: Boolean): String {
    if (value.isNaN()) return "NaN"
    if (value.isInfinite()) return if (value > 0) (if (plus) "+Infinity" else "Infinity") else "-Infinity"
    val negative = value < 0 || (value == 0.0 && 1.0 / value < 0)
    // Shortest decimal digits of |value| and the position of the decimal point.
    val (digits, point) = shortestDecimal(abs(value))
    // Round half-up to `precision` places.
    val keep = point + precision
    var kept: String
    if (keep < 0) {
        kept = "0"
    } else {
        val padded = digits.padEnd(keep + 1, '0')
        kept = padded.take(keep).ifEmpty { "0" }
        if (padded[keep] >= '5') kept = increment(kept)
    }
    // kept holds the digits with `precision` decimals at the end.
    val all = kept.padStart(precision + 1, '0')
    var intPart = all.dropLast(precision).trimStart('0').ifEmpty { "0" }
    val fracPart = all.takeLast(precision)
    if (grouping) intPart = group(intPart)
    // Like java.util.Formatter, a negative value keeps its sign even when it rounds to zero.
    val sign = if (negative) "-" else if (plus) "+" else ""
    return sign + intPart + if (precision > 0) ".$fracPart" else ""
}

private fun increment(number: String): String {
    val chars = number.toCharArray()
    var k = chars.size - 1
    while (k >= 0) {
        if (chars[k] == '9') {
            chars[k] = '0'
            k--
        } else {
            chars[k] = chars[k] + 1
            return chars.concatToString()
        }
    }
    return "1" + chars.concatToString()
}

/** Digits without leading zeros and the decimal point position: 12.5 -> ("125", 2), 0.05 -> ("5", -1). */
private fun shortestDecimal(value: Double): Pair<String, Int> {
    if (value == 0.0) return "0" to 1
    val text = value.toString().lowercase()
    val mantissa = text.substringBefore('e')
    val exponent = if ('e' in text) text.substringAfter('e').toInt() else 0
    val intDigits = mantissa.substringBefore('.')
    val fracDigits = if ('.' in mantissa) mantissa.substringAfter('.') else ""
    var digits = intDigits + fracDigits
    var point = intDigits.length + exponent
    val leading = digits.length - digits.trimStart('0').length
    digits = digits.drop(leading)
    point -= leading
    digits = digits.trimEnd('0').ifEmpty { "0" }
    return digits to point
}
