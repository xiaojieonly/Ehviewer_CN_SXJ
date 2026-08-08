package com.hippo.anotherviewer.web.service;

import okhttp3.RequestBody;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Serialises an OkHttp request body to an OutputStream.
 *
 * Lives in Java on purpose: okio's {@code Okio.buffer}/{@code Okio.sink} are
 * deprecated at Kotlin ERROR level (moved to extension functions), which the
 * Kotlin compiler rejects outright; the Java compiler only warns.
 */
public final class CurlBodyWriter {

    private CurlBodyWriter() {
    }

    @SuppressWarnings("deprecation")
    public static void writeBody(RequestBody body, OutputStream out) throws IOException {
        okio.BufferedSink sink = okio.Okio.buffer(okio.Okio.sink(out));
        body.writeTo(sink);
        // Okio buffers eagerly; flush so the bytes actually reach the stream
        // (the caller owns the stream and closes it).
        sink.flush();
    }
}
