package app.ironlog.personal.platform

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun httpGetText(url: String, userAgent: String, maxBytes: Int, timeoutMs: Int): String =
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        connection.setRequestProperty("User-Agent", userAgent)
        try {
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode} from ${connection.url.host}")
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    bytes.write(buffer, 0, n)
                    if (bytes.size() > maxBytes) throw IOException("Response too large")
                }
            }
            bytes.toString(Charsets.UTF_8.name())
        } finally {
            connection.disconnect()
        }
    }
