package app.ironlog.personal.data.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A tiny in-memory stand-in for the Drive REST API's app data folder. */
class DriveBackupTest {
    private class FakeDrive : Http {
        var content: String? = null
        val calls = mutableListOf<String>()

        override fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?): Pair<Int, String> {
            if (headers["Authorization"] != "Bearer good") return 401 to """{"error":{"code":401,"message":"Invalid Credentials"}}"""
            val override = headers["X-HTTP-Method-Override"]
            calls += (override ?: method) + " " + url.substringBefore('?').substringAfter("googleapis.com")
            return when {
                method == "GET" && url.contains("/files?") -> {
                    assertTrue(url.contains("spaces=appDataFolder"))
                    200 to (if (content == null) """{"files":[]}""" else """{"files":[{"id":"f1","modifiedTime":"2026-10-05T10:00:00Z"}]}""")
                }
                method == "GET" && url.contains("alt=media") -> 200 to content!!
                method == "POST" && override == null -> {
                    val text = String(body!!)
                    assertTrue(text.contains("\"parents\":[\"appDataFolder\"]"))
                    assertTrue(headers.getValue("Content-Type").startsWith("multipart/related; boundary="))
                    content = text.substringAfter("Content-Type: application/json\r\n\r\n").substringBeforeLast("\r\n--")
                    200 to """{"id":"f1","modifiedTime":"2026-10-05T10:00:00Z"}"""
                }
                override == "PATCH" -> {
                    content = String(body!!)
                    200 to """{"id":"f1","modifiedTime":"2026-10-05T11:00:00Z"}"""
                }
                else -> 404 to "{}"
            }
        }
    }

    @Test
    fun createsOnceThenUpdatesAndDownloads() = runBlocking {
        val fake = FakeDrive()
        val drive = DriveBackup(fake)
        assertNull(drive.download("good"))
        drive.upload("good", """{"schemaVersion":1,"v":1}""")
        assertEquals("""{"schemaVersion":1,"v":1}""", drive.download("good"))
        val updated = drive.upload("good", """{"schemaVersion":1,"v":2}""")
        assertEquals("f1", updated.id)
        assertEquals("""{"schemaVersion":1,"v":2}""", drive.download("good"))
        // One file only: the second upload replaced it instead of creating another.
        assertEquals(1, fake.calls.count { it.startsWith("POST /upload") })
        assertEquals(1, fake.calls.count { it.startsWith("PATCH /upload/drive/v3/files/f1") })
    }

    @Test
    fun driveErrorsCarryGooglesMessage() = runBlocking {
        try {
            DriveBackup(FakeDrive()).upload("expired", "{}")
            fail("expected an error")
        } catch (e: DriveException) {
            assertTrue(e.message!!, e.message!!.contains("HTTP 401") && e.message!!.contains("Invalid Credentials"))
        }
    }
}
