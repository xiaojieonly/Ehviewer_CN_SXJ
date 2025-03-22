/*
 * Copyright 2019 Hippo Seven
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
package com.hippo.ehviewer.gallery

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.min

internal class Pipe(private val capacity: Int) {
    private val buffer = ByteArray(capacity)

    private var head = 0
    private var tail = 0
    private var full = false

    private var inClosed = false
    private var outClosed = false

    @JvmField
    val inputStream: InputStream = object : InputStream() {
        @Throws(IOException::class)
        override fun read(): Int {
            synchronized(this@Pipe) {
                val bytes = ByteArray(1)
                return if (read(bytes, 0, 1) != -1) {
                    bytes[0].toInt()
                } else {
                    -1
                }
            }
        }

        @Throws(IOException::class)
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            synchronized(this@Pipe) {
                while (true) {
                    if (inClosed) {
                        throw IOException("The InputStream is closed")
                    }
                    if (len == 0) {
                        return 0
                    }

                    if (head == tail && !full) {
                        if (outClosed) {
                            // No bytes available and the OutputStream is closed. So it's the end.
                            return -1
                        } else {
                            // Wait for OutputStream write bytes
                            try {
                                (this@Pipe as Object).wait()
                            } catch (e: InterruptedException) {
                                throw IOException("The thread interrupted", e)
                            }
                        }
                    } else {
                        val read = min(
                            len.toDouble(),
                            ((if (head < tail) tail else capacity) - head).toDouble()
                        ).toInt()
                        System.arraycopy(buffer, head, b, off, read)
                        head += read
                        if (head == capacity) {
                            head = 0
                        }
                        full = false
                        (this@Pipe as Object).notifyAll()
                        return read
                    }
                }
            }
        }

        override fun close() {
            synchronized(this@Pipe) {
                inClosed = true
                (this@Pipe as Object).notifyAll()
            }
        }
    }

    @JvmField
    val outputStream: OutputStream = object : OutputStream() {
        @Throws(IOException::class)
        override fun write(b: Int) {
            synchronized(this@Pipe) {
                val bytes = byteArrayOf(b.toByte())
                write(bytes, 0, 1)
            }
        }

        @Throws(IOException::class)
        override fun write(b: ByteArray, off: Int, len: Int) {
            var off = off
            var len = len
            synchronized(this@Pipe) {
                while (len != 0) {
                    if (outClosed) {
                        throw IOException("The OutputStream is closed")
                    }
                    if (inClosed) {
                        throw IOException("The InputStream is closed")
                    }

                    if (head == tail && full) {
                        // The buffer is full, wait for InputStream read bytes
                        try {
                            (this@Pipe as Object).wait()
                        } catch (e: InterruptedException) {
                            throw IOException("The thread interrupted", e)
                        }
                    } else {
                        val write = min(
                            len.toDouble(),
                            ((if (head <= tail) capacity else head) - tail).toDouble()
                        ).toInt()
                        System.arraycopy(b, off, buffer, tail, write)
                        off += write
                        len -= write
                        tail += write
                        if (tail == capacity) {
                            tail = 0
                        }
                        if (head == tail) {
                            full = true
                        }
                        (this@Pipe as Object).notifyAll()
                    }
                }
            }
        }

        override fun close() {
            synchronized(this@Pipe) {
                outClosed = true
                (this@Pipe as Object).notifyAll()
            }
        }
    }
}
