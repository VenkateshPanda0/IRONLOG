package app.ironlog.personal.platform

/**
 * GETs [url] over HTTPS and returns the body as text. Fails on a non-2xx status and stops reading
 * past [maxBytes], so a broken or hostile server cannot exhaust memory.
 */
expect suspend fun httpGetText(url: String, userAgent: String, maxBytes: Int, timeoutMs: Int = 8000): String

/** Percent-encodes [value] for use in a URL query (UTF-8, spaces as %20). */
fun urlEncode(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        if (c in 'a'.code..'z'.code || c in 'A'.code..'Z'.code || c in '0'.code..'9'.code || c.toChar() in "-._~") append(c.toChar())
        else append('%').append("0123456789ABCDEF"[c shr 4]).append("0123456789ABCDEF"[c and 15])
    }
}
