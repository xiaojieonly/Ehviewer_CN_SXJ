package com.hippo.anotherviewer.web.service

import okhttp3.CookieJar
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * P4: per-request curl `--max-time` —— 请求方可通过 OkHttp request tag
 * （Int 类型，秒）覆盖默认 60s，钳 1..maxTimeoutSec；无 tag 的调用
 * （如阅读器页图）维持默认值。tag 只是 OkHttp 元数据，不进线路。
 */
class CurlSiteExecutorTest {

    private val executor = CurlSiteExecutor(CookieJar.NO_COOKIES, proxy = { null })

    private fun request(tagSec: Int? = null): Request {
        val builder = Request.Builder().url("https://e-hentai.org/g/1/t1/")
        if (tagSec != null) {
            builder.tag(Int::class.javaObjectType, tagSec)
        }
        return builder.build()
    }

    // ── resolveMaxTimeSec ───────────────────────────────────────

    @Test
    fun `request without tag keeps the default 60s`() {
        assertEquals(60L, executor.resolveMaxTimeSec(request()))
    }

    @Test
    fun `request tag overrides the max time`() {
        assertEquals(10L, executor.resolveMaxTimeSec(request(tagSec = 10)))
    }

    @Test
    fun `request tag above the ceiling is clamped to maxTimeoutSec`() {
        assertEquals(60L, executor.resolveMaxTimeSec(request(tagSec = 500)))
    }

    @Test
    fun `request tag below one second is clamped up to 1`() {
        assertEquals(1L, executor.resolveMaxTimeSec(request(tagSec = 0)))
    }

    // ── buildCommand 落到 curl --max-time 参数 ───────────────────

    @Test
    fun `curl command carries the tagged max time`(@TempDir tmp: File) {
        val outFile = File(tmp, "out.bin")
        val headerFile = File(tmp, "hdr.txt")
        val request = request(tagSec = 10)

        val cmd = executor.buildCommand(request, outFile, headerFile, null)

        val maxTimeIdx = cmd.indexOf("--max-time")
        assertEquals("--max-time", cmd[maxTimeIdx])
        assertEquals("10", cmd[maxTimeIdx + 1])
    }

    @Test
    fun `curl command falls back to the default max time without a tag`(@TempDir tmp: File) {
        val outFile = File(tmp, "out.bin")
        val headerFile = File(tmp, "hdr.txt")
        val request = request()

        val cmd = executor.buildCommand(request, outFile, headerFile, null)

        val maxTimeIdx = cmd.indexOf("--max-time")
        assertEquals("60", cmd[maxTimeIdx + 1])
    }
}
