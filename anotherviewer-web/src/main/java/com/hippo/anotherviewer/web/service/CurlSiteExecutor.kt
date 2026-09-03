package com.hippo.anotherviewer.web.service

import okhttp3.CookieJar
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.slf4j.LoggerFactory
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/**
 * Network-stack replacement for Gallery Site traffic that executes every
 * request through the system `curl` binary instead of OkHttp's TLS stack.
 *
 * Cloudflare fingerprints the TLS ClientHello (JA3) of Gallery Site requests
 * and rejects the vanilla Java handshake (both JSSE and the Java-assembled
 * Conscrypt ClientHello) on the strict exhentai paths — the system curl
 * (SecureTransport/LibreSSL) fingerprint is admitted. Rather than fighting
 * that in Java, this interceptor hands the raw request (headers, cookies,
 * proxy, URL) to curl and synthesises an OkHttp [Response] from its output,
 * so the rest of the pipeline (SiteEngine parsing, cookie jar, proxy
 * selector) keeps working unchanged.
 *
 * Registered as an application interceptor on the shared Gallery Site
 * client: at that point cookies are not yet attached (that happens in
 * OkHttp's BridgeInterceptor), so this interceptor reads them from the
 * [CookieJar] itself and replays Set-Cookie headers back into it.
 */
class CurlSiteExecutor(
    private val cookieJar: CookieJar,
    private val proxy: () -> Proxy?,
    private val maxTimeoutSec: Long = 60,
) : Interceptor {

    companion object {
        private const val RESPONSE_TEMPLATE = "%{http_code}\n%{content_type}"
    }

    private val logger = LoggerFactory.getLogger(CurlSiteExecutor::class.java)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isGallerySiteHost(request.url.host)) {
            // Non-site traffic (never expected on this client) falls back to
            // the normal stack.
            return chain.proceed(request)
        }

        val maxTimeSec = resolveMaxTimeSec(request)
        val outFile = File.createTempFile("curl-site", ".bin")
        val metaFile = File.createTempFile("curl-meta", ".txt")
        val headerFile = File.createTempFile("curl-hdr", ".txt")
        var bodyFile: File? = null
        try {
            bodyFile = writeBodyToTemp(request)
            val cmd = buildCommand(request, outFile, headerFile, bodyFile, maxTimeSec)
            // curl's -w template writes to stdout; capture it into metaFile.
            val process = ProcessBuilder(cmd)
                .redirectOutput(metaFile)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(maxTimeSec, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                throw java.io.IOException("curl timed out for ${request.url}")
            }
            val exit = process.exitValue()
            if (exit != 0) {
                throw java.io.IOException("curl exited $exit for ${request.url}")
            }

            val meta = metaFile.readText().split("\n", limit = 2)
            val code = meta.getOrElse(0) { "0" }.toIntOrNull() ?: 0
            val contentType = meta.getOrElse(1) { "" }
            val bytes = outFile.readBytes()

            val responseHeaders = okhttp3.Headers.Builder()
                .add("Content-Type", contentType.ifEmpty { "application/octet-stream" })
            // Replay raw Set-Cookie lines so OkHttp's BridgeInterceptor (which
            // runs after this application interceptor) stores them in the jar.
            rawSetCookies(headerFile.readText()).forEach { responseHeaders.add("Set-Cookie", it) }

            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(if (code == 200) "OK" else "Curl")
                .headers(responseHeaders.build())
                .body(bytes.toResponseBody(contentType.ifEmpty { "application/octet-stream" }.toMediaTypeOrNull()))
                .build()
        } finally {
            outFile.delete()
            metaFile.delete()
            headerFile.delete()
            bodyFile?.delete()
        }
    }

    /** Persists a non-empty request body (EH api.php POSTs JSON) for curl's --data-binary. */
    private fun writeBodyToTemp(request: Request): File? {
        val body = request.body ?: return null
        if (body.contentLength() == 0L) return null
        val file = File.createTempFile("curl-body", ".bin")
        file.outputStream().use { os ->
            CurlBodyWriter.writeBody(body, os)
        }
        return file
    }

    /**
     * P4: per-request curl `--max-time`（秒）。请求方可用 OkHttp request tag
     * （[Int] 类型）携带覆盖值，钳 1..[maxTimeoutSec]；无 tag（如阅读器页图）
     * 维持 [maxTimeoutSec]（默认 60s）。tag 只是 OkHttp 的元数据，不进线路，
     * 无需在任何方向剥离。
     */
    internal fun resolveMaxTimeSec(request: Request): Long {
        // 注意 tag 类型用 Integer（javaObjectType）：OkHttp Builder.tag 会
        // type.cast(value)，原始类型 int.class 对装箱值会 CCE。
        val tagged = request.tag(Int::class.javaObjectType) ?: return maxTimeoutSec
        return tagged.coerceIn(1, maxTimeoutSec.toInt()).toLong()
    }

    internal fun buildCommand(
        request: Request,
        outFile: File,
        headerFile: File,
        bodyFile: File?,
        maxTimeSec: Long = resolveMaxTimeSec(request),
    ): List<String> {
        val cmd = mutableListOf(
            "curl", "-sS", "--compressed",
            "-o", outFile.absolutePath,
            "-D", headerFile.absolutePath,
            "-w", RESPONSE_TEMPLATE,
            "--max-time", maxTimeSec.toString(),
            "-A", request.header("User-Agent") ?: "Mozilla/5.0",
        )

        if (bodyFile != null) {
            cmd += listOf("--data-binary", "@" + bodyFile.absolutePath)
        }

        val referer = request.header("Referer")
        if (!referer.isNullOrEmpty()) {
            cmd += listOf("-e", referer)
        }

        val cookies = cookieJar.loadForRequest(request.url)
        if (cookies.isNotEmpty()) {
            cmd += listOf("-H", "Cookie: " + cookies.joinToString("; ") { "${it.name}=${it.value}" })
        }

        val proxy = proxy()
        if (proxy != null && proxy.type() != Proxy.Type.DIRECT && proxy.address() is InetSocketAddress) {
            val addr = proxy.address() as InetSocketAddress
            val scheme = if (proxy.type() == Proxy.Type.SOCKS) "socks5" else "http"
            cmd += listOf("-x", "$scheme://${addr.hostString}:${addr.port}")
        }

        request.headers.forEach { (name, value) ->
            if (name.equals("User-Agent", ignoreCase = true) ||
                name.equals("Referer", ignoreCase = true) ||
                name.equals("Cookie", ignoreCase = true) ||
                name.equals("Host", ignoreCase = true) ||
                // curl derives these from --data-binary / the body itself.
                name.equals("Content-Length", ignoreCase = true) ||
                name.equals("Transfer-Encoding", ignoreCase = true)) {
                return@forEach
            }
            cmd += listOf("-H", "$name: $value")
        }

        cmd += request.url.toString()
        return cmd
    }

    /** Raw Set-Cookie header lines from curl's -D output. */
    private fun rawSetCookies(raw: String): List<String> {
        val result = mutableListOf<String>()
        for (line in raw.lines()) {
            val trimmed = line.trim()
            if (trimmed.startsWith("set-cookie:", ignoreCase = true)) {
                result.add(trimmed)
            }
        }
        return result
    }

    private fun isGallerySiteHost(host: String): Boolean =
        host.equals("exhentai.org") || host.endsWith(".exhentai.org") ||
            host.equals("e-hentai.org") || host.endsWith(".e-hentai.org") ||
            host.equals("lofi.e-hentai.org") || host.endsWith(".lofi.e-hentai.org") ||
            host.equals("ehgt.org") || host.endsWith(".ehgt.org")
}
