/*
 * Copyright 2026 BrokenDog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.spider

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream
import kotlin.math.min

/**
 * 从 CBOR map 形式的 .ehviewer 中只取出 gid 和 token。
 * pTokenMap 等嵌套内容只跳过，不放进内存。
 */
internal object CborSpiderHeader {
    private const val MAX_TOKEN_CHARS = 128
    private const val MAX_KEY_CHARS = 64
    private const val MAX_DEPTH = 16
    private const val MAX_BYTES = 32L * 1024 * 1024
    private const val BREAK = 0xFF

    /**
     * 已读到的 gid 与 token。gid 小于 0 或 token 为空时不会构造。
     */
    class Fields(
        @JvmField val gid: Long,
        @JvmField val token: String,
    )

    /**
     * 解析顶层定长或不定长 map。gid 小于 0、token 为空，或开头不是 map 时返回 null。
     * gid 和 token 若已读到，后面的字段跳过失败仍返回这两个值。
     */
    @JvmStatic
    fun read(input: InputStream?): Fields? {
        if (input == null) {
            return null
        }
        val cursor = Cursor(input)
        var gid = -1L
        var token: String? = null
        try {
            val head = cursor.readByte()
            val major = head ushr 5
            val info = head and 0x1F
            if (major != 5) {
                return null
            }
            val indefinite = info == 31
            val count = if (indefinite) -1L else cursor.readArgument(info)
            var index = 0L
            while (indefinite || index < count) {
                if (indefinite && cursor.peekBreak()) {
                    cursor.consumeBreak()
                    break
                }
                val key = cursor.readTextOrSkip(MAX_KEY_CHARS)
                if (key == "gid") {
                    val value = cursor.readIntegerOrSkip()
                    if (value != null) {
                        gid = value
                    }
                } else if (key == "token") {
                    val value = cursor.readTextOrSkip(MAX_TOKEN_CHARS)
                    if (!value.isNullOrEmpty()) {
                        token = value
                    }
                } else {
                    cursor.skipValue(0)
                }
                index++
            }
        } catch (ignored: IOException) {
            // gid、token 写在 pTokenMap 之前。后面的字段跳过失败时，仍返回已经读到的两个值。
        } catch (ignored: OutOfMemoryError) {
            // 嵌套内容过大时同样保留已读到的 gid、token。
        }
        if (gid < 0 || token.isNullOrEmpty()) {
            return null
        }
        return Fields(gid, token)
    }

    /**
     * 带字节上限的 CBOR 读取游标。peek 不计入已读字节，避免回退破坏上限判断。
     */
    private class Cursor(input: InputStream) {
        private val inputStream = PushbackInputStream(input, 1)
        private val skipBuffer = ByteArray(4096)
        private var bytesRead = 0L

        /**
         * 读取一个字节并计入总字节数。流结束或超过 [MAX_BYTES] 时中止，避免损坏文件拖垮解析。
         */
        fun readByte(): Int {
            val value = inputStream.read()
            if (value < 0) {
                throw EOFException()
            }
            bytesRead++
            if (bytesRead > MAX_BYTES) {
                throw IOException("cbor too large")
            }
            return value
        }

        /**
         * 查看下一字节是不是 break，并把它退回流中。不增加已读计数。
         */
        fun peekBreak(): Boolean {
            val value = inputStream.read()
            if (value < 0) {
                throw EOFException()
            }
            inputStream.unread(value)
            return value == BREAK
        }

        /**
         * 消费一个 break。当前位置不是 break 时说明不定长结构已经损坏。
         */
        fun consumeBreak() {
            if (readByte() != BREAK) {
                throw IOException("expected cbor break")
            }
        }

        /**
         * 按附加信息读取无符号参数。8 字节且最高位为 1 时超出有符号 long，读完剩余字节后失败。
         */
        fun readArgument(info: Int): Long {
            if (info < 24) {
                return info.toLong()
            }
            val width = when (info) {
                24 -> 1
                25 -> 2
                26 -> 4
                27 -> 8
                else -> throw IOException("bad cbor argument")
            }
            var value = 0L
            for (i in 0 until width) {
                val b = readByte()
                if (i == 0 && width == 8 && (b and 0x80) != 0) {
                    repeat(width - 1) {
                        readByte()
                    }
                    throw IOException("cbor integer overflow")
                }
                value = (value shl 8) or (b and 0xFF).toLong()
            }
            return value
        }

        /**
         * 当前位置应是文本时读出内容。类型不对则整值跳过并返回 null，避免把后续字段错位。
         * 超过 [maxChars] 字节的文本同样丢弃，防止超长 key 或 token 进内存。
         */
        fun readTextOrSkip(maxChars: Int): String? {
            val head = readByte()
            if ((head ushr 5) != 3) {
                skipWithHead(head, 0)
                return null
            }
            return readTextBody(head and 0x1F, maxChars)
        }

        /**
         * 读 CBOR 无符号或负数整数。不是整数时跳过该值并返回 null；负数值无法放进 long 时也返回 null。
         */
        fun readIntegerOrSkip(): Long? {
            val head = readByte()
            val major = head ushr 5
            val info = head and 0x1F
            if (major != 0 && major != 1) {
                skipWithHead(head, 0)
                return null
            }
            if (info == 31) {
                throw IOException("bad cbor integer")
            }
            val unsigned = readArgument(info)
            if (major == 0) {
                return unsigned
            }
            if (unsigned == Long.MAX_VALUE) {
                return null
            }
            return -1L - unsigned
        }

        /**
         * 跳过一个完整值。深度从当前层继续累加，超过 [MAX_DEPTH] 时由 [skipWithHead] 中止。
         */
        fun skipValue(depth: Int) {
            skipWithHead(readByte(), depth)
        }

        /**
         * 按已读出的头部跳过一个值。容器递归增加深度，防止恶意嵌套把栈打满。
         */
        private fun skipWithHead(head: Int, depth: Int) {
            if (depth > MAX_DEPTH) {
                throw IOException("cbor too deep")
            }
            val major = head ushr 5
            val info = head and 0x1F
            when (major) {
                0, 1 -> {
                    if (info == 31) {
                        throw IOException("bad cbor integer")
                    }
                    readArgument(info)
                }
                2, 3 -> skipString(major, info)
                4 -> skipContainer(info, depth, map = false)
                5 -> skipContainer(info, depth, map = true)
                6 -> {
                    if (info == 31) {
                        throw IOException("bad cbor tag")
                    }
                    readArgument(info)
                    skipValue(depth)
                }
                7 -> skipSimple(info)
                else -> throw IOException("bad cbor major")
            }
        }

        /**
         * 跳过 simple / float。不定长 break 不能出现在值的位置。
         */
        private fun skipSimple(info: Int) {
            when {
                info < 24 -> return
                info == 24 -> readByte()
                info == 25 -> skipBytes(2)
                info == 26 -> skipBytes(4)
                info == 27 -> skipBytes(8)
                else -> throw IOException("bad cbor simple")
            }
        }

        /**
         * 跳过字节串或文本。不定长时只接受同主类型的定长分块，直到 break。
         */
        private fun skipString(major: Int, info: Int) {
            if (info != 31) {
                skipBytes(readArgument(info))
                return
            }
            while (!peekBreak()) {
                val chunk = readByte()
                if ((chunk ushr 5) != major || (chunk and 0x1F) == 31) {
                    throw IOException("bad indefinite string")
                }
                skipBytes(readArgument(chunk and 0x1F))
            }
            consumeBreak()
        }

        /**
         * 跳过数组或 map。map 的每一项是键和值两个值；不定长以 break 结束。
         */
        private fun skipContainer(info: Int, depth: Int, map: Boolean) {
            val items = if (map) 2 else 1
            if (info == 31) {
                while (!peekBreak()) {
                    repeat(items) {
                        skipValue(depth + 1)
                    }
                }
                consumeBreak()
                return
            }
            val count = readArgument(info)
            var index = 0L
            while (index < count) {
                repeat(items) {
                    skipValue(depth + 1)
                }
                index++
            }
        }

        /**
         * 读取文本内容。按字节长度和 [maxChars] 比较，超长则跳过剩余字节并返回 null。
         * 不定长文本在判定超长后仍要把后续分块读完，否则外层 map 会错位。
         */
        private fun readTextBody(info: Int, maxChars: Int): String? {
            if (info == 31) {
                val builder = StringBuilder()
                var tooLong = false
                while (!peekBreak()) {
                    val chunk = readByte()
                    if ((chunk ushr 5) != 3 || (chunk and 0x1F) == 31) {
                        throw IOException("bad indefinite text")
                    }
                    val length = readArgument(chunk and 0x1F)
                    if (tooLong || builder.length.toLong() + length > maxChars) {
                        tooLong = true
                        skipBytes(length)
                    } else {
                        builder.append(readUtf8(length))
                    }
                }
                consumeBreak()
                return if (tooLong) null else builder.toString()
            }
            val length = readArgument(info)
            if (length > maxChars.toLong()) {
                skipBytes(length)
                return null
            }
            return readUtf8(length)
        }

        /**
         * 按字节数读取 UTF-8。长度按字节计，调用方必须已经确认不超过字符上限。
         */
        private fun readUtf8(length: Long): String {
            if (length > Int.MAX_VALUE) {
                throw IOException("cbor text too large")
            }
            val data = ByteArray(length.toInt())
            var offset = 0
            while (offset < data.size) {
                val read = inputStream.read(data, offset, data.size - offset)
                if (read < 0) {
                    throw EOFException()
                }
                offset += read
                bytesRead += read.toLong()
                if (bytesRead > MAX_BYTES) {
                    throw IOException("cbor too large")
                }
            }
            return String(data, Charsets.UTF_8)
        }

        /**
         * 丢弃指定字节数，不分配与长度等大的缓冲区。长度为负或中途读尽时失败。
         */
        private fun skipBytes(length: Long) {
            if (length < 0) {
                throw IOException("negative cbor length")
            }
            var left = length
            while (left > 0) {
                val want = min(skipBuffer.size.toLong(), left).toInt()
                val read = inputStream.read(skipBuffer, 0, want)
                if (read < 0) {
                    throw EOFException()
                }
                left -= read.toLong()
                bytesRead += read.toLong()
                if (bytesRead > MAX_BYTES) {
                    throw IOException("cbor too large")
                }
            }
        }
    }
}
