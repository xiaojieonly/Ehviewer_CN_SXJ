/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.lib.yorozuya;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public final class IOUtils {
    private static final int EOF = -1;
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;
    private static final int MAX_LINE_LENGTH = 131072; // 128KB

    private IOUtils() {
    }

    /**
     * Close the closeable stuff. Don't worry about anything.
     *
     * @param is the closeable stuff
     */
    public static void closeQuietly(Closeable is) {
        try {
            if (is != null) {
                is.close();
            }
        } catch (IOException e) {
            // Ignore
        }
    }

    /**
     * Copy bytes from an <code>InputStream</code> to an
     * <code>OutputStream</code>.
     *
     * @param input  the InputStream
     * @param output the OutputStream
     * @return the number of bytes copied
     * @throws IOException
     */
    public static long copy(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while (EOF != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Returns the ASCII characters up to but not including the next "\r\n", or
     * "\n".
     *
     * @param in the InputStream to read from
     * @return the line content without line terminator
     * @throws EOFException if the stream is exhausted before the next
     *                              newline character.
     * @throws IOException if the line exceeds maxLength (prevents OOM on corrupted files)
     */
    public static String readAsciiLine(final InputStream in) throws IOException {
        return readAsciiLine(in, MAX_LINE_LENGTH);
    }

    /**
     * Returns the ASCII characters up to but not including the next "\r\n", or
     * "\n", with a maximum line length to prevent OOM on corrupted/malformed files.
     *
     * @param in       the InputStream to read from
     * @param maxLength maximum allowed line length in characters
     * @return the line content without line terminator
     * @throws EOFException if the stream is exhausted before the next
     *                              newline character.
     * @throws IOException if the line exceeds maxLength
     */
    public static String readAsciiLine(final InputStream in, final int maxLength) throws IOException {
        final StringBuilder result = new StringBuilder(80);
        while (true) {
            final int c = in.read();
            if (c == -1) {
                throw new EOFException();
            } else if (c == '\n') {
                break;
            }
            if (result.length() >= maxLength) {
                throw new IOException("Line too long");
            }
            result.append((char) c);
        }
        final int length = result.length();
        if (length > 0 && result.charAt(length - 1) == '\r') {
            result.setLength(length - 1);
        }
        return result.toString();
    }

    public static String readString(final InputStream is, String encoding) throws IOException {
        InputStreamReader reader = new InputStreamReader(is, encoding);
        StringBuilder sb = new StringBuilder();

        char[] buffer = new char[DEFAULT_BUFFER_SIZE];
        int n;
        while (EOF != (n = reader.read(buffer))) {
            sb.append(buffer, 0, n);
        }

        return sb.toString();
    }

    public static byte[] getAllByte(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(is, baos);
        return baos.toByteArray();
    }
}
