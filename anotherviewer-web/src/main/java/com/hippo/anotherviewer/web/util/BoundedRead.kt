package com.hippo.anotherviewer.web.util

import okhttp3.ResponseBody
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Thrown when an upstream response exceeds the configured size cap. */
class ResponseTooLargeException(val maxBytes: Long) :
    IOException("Upstream response exceeded ${maxBytes} bytes")

/**
 * Read the response body into memory with a hard upper bound.
 *
 * The proxy/fetch paths used to buffer upstream responses with
 * [ResponseBody.bytes] without any cap, letting a huge or hostile upstream
 * drive heap usage unbounded. This streaming variant aborts with
 * [ResponseTooLargeException] as soon as [maxBytes] is exceeded.
 */
fun ResponseBody.bytesBounded(maxBytes: Long): ByteArray {
    require(maxBytes > 0) { "maxBytes must be positive" }
    byteStream().use { input ->
        val out = ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > maxBytes) throw ResponseTooLargeException(maxBytes)
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
