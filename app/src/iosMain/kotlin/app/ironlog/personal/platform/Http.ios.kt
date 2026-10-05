package app.ironlog.personal.platform

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual suspend fun httpGetText(url: String, userAgent: String, maxBytes: Int, timeoutMs: Int): String =
    suspendCancellableCoroutine { continuation ->
        val nsUrl = NSURL.URLWithString(url) ?: run {
            continuation.resumeWithException(IllegalArgumentException("Bad URL"))
            return@suspendCancellableCoroutine
        }
        val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
            setValue(userAgent, forHTTPHeaderField = "User-Agent")
            setTimeoutInterval(timeoutMs / 1000.0)
        }
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, response, error ->
            val status = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
            when {
                error != null -> continuation.resumeWithException(RuntimeException(error.localizedDescription))
                status !in 200..299 -> continuation.resumeWithException(RuntimeException("HTTP $status from ${nsUrl.host}"))
                data == null -> continuation.resume("")
                data.length.toLong() > maxBytes -> continuation.resumeWithException(RuntimeException("Response too large"))
                else -> continuation.resume(NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() ?: "")
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }
