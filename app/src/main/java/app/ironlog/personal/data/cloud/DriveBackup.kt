package app.ironlog.personal.data.cloud

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Minimal HTTP seam so Drive calls can be tested without a network. */
fun interface Http {
    /** Returns the status code and the response body. */
    fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, String>
}

object UrlConnectionHttp : Http {
    override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 20_000
            connection.readTimeout = 60_000
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}

data class DriveFile(val id: String, val modifiedTime: String?)

class DriveException(message: String) : Exception(message)

/**
 * Stores one backup file in the Drive app data folder: a hidden folder only Ironlog can read,
 * which does not appear in the user's Drive file list and does not count other apps' files.
 */
class DriveBackup(private val http: Http = UrlConnectionHttp) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun auth(token: String) = mapOf("Authorization" to "Bearer $token")

    private fun check(result: Pair<Int, String>, what: String): String {
        val (code, body) = result
        if (code !in 200..299) {
            val reason = runCatching { json.parseToJsonElement(body).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content }.getOrNull()
            throw DriveException("$what failed (HTTP $code)${reason?.let { ": $it" } ?: ""}")
        }
        return body
    }

    suspend fun find(token: String): DriveFile? =
        withContext(Dispatchers.IO) {
            val q = URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
            val body =
                check(
                    http.request("GET", "$API/files?spaces=appDataFolder&q=$q&fields=files(id,modifiedTime)&orderBy=modifiedTime%20desc", auth(token), null),
                    "Finding the backup",
                )
            json.parseToJsonElement(body).jsonObject["files"]?.jsonArray?.firstOrNull()?.jsonObject?.let {
                DriveFile(it.getValue("id").jsonPrimitive.content, it["modifiedTime"]?.jsonPrimitive?.content)
            }
        }

    /** Creates the backup file the first time, then replaces its content. */
    suspend fun upload(token: String, content: String): DriveFile {
        val existing = find(token)
        return withContext(Dispatchers.IO) {
            val bytes = content.toByteArray()
            val body =
                if (existing == null) {
                    val boundary = "ironlog-${System.nanoTime()}"
                    val metadata = """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
                    val multipart =
                        ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n" +
                            "--$boundary\r\nContent-Type: application/json\r\n\r\n").toByteArray() + bytes + "\r\n--$boundary--".toByteArray()
                    check(
                        http.request("POST", "$UPLOAD/files?uploadType=multipart&fields=id,modifiedTime", auth(token) + ("Content-Type" to "multipart/related; boundary=$boundary"), multipart),
                        "Uploading the backup",
                    )
                } else {
                    // HttpURLConnection has no PATCH; Google APIs accept the override header.
                    check(
                        http.request(
                            "POST",
                            "$UPLOAD/files/${existing.id}?uploadType=media&fields=id,modifiedTime",
                            auth(token) + mapOf("Content-Type" to "application/json", "X-HTTP-Method-Override" to "PATCH"),
                            bytes,
                        ),
                        "Uploading the backup",
                    )
                }
            json.parseToJsonElement(body).jsonObject.let { DriveFile(it.getValue("id").jsonPrimitive.content, it["modifiedTime"]?.jsonPrimitive?.content) }
        }
    }

    /** The backup text, or null when this account has never backed up. */
    suspend fun download(token: String): String? {
        val file = find(token) ?: return null
        return withContext(Dispatchers.IO) { check(http.request("GET", "$API/files/${file.id}?alt=media", auth(token), null), "Downloading the backup") }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val FILE_NAME = "ironlog_backup.json"
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
    }
}
