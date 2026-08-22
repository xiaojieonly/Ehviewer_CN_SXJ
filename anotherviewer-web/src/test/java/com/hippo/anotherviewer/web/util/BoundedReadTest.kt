package com.hippo.anotherviewer.web.util

import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/** MASTER-2026-08-22 S2：上游响应有界读取语义。 */
class BoundedReadTest {

    private fun bodyOf(content: ByteArray) = content.toResponseBody(null)

    @Test
    fun `reads body under the limit unchanged`() {
        val data = ByteArray(1024) { (it % 251).toByte() }
        assertArrayEquals(data, bodyOf(data).bytesBounded(4096))
    }

    @Test
    fun `reads body exactly at the limit`() {
        val data = ByteArray(64)
        assertArrayEquals(data, bodyOf(data).bytesBounded(64))
    }

    @Test
    fun `throws ResponseTooLargeException when body exceeds the limit`() {
        val e = assertThrows(ResponseTooLargeException::class.java) {
            bodyOf(ByteArray(65)).bytesBounded(64)
        }
        assertEquals(64L, e.maxBytes)
    }

    @Test
    fun `empty body reads to empty array`() {
        assertArrayEquals(ByteArray(0), bodyOf(ByteArray(0)).bytesBounded(16))
    }
}
