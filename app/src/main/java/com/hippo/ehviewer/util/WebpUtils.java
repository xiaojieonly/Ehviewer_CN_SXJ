/*
 * Copyright 2026 Hippo Seven
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

package com.hippo.ehviewer.util;

import android.text.TextUtils;
import com.hippo.lib.yorozuya.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class WebpUtils {

    private static final int HEADER_SIZE = 30;
    private static final int VP8X_CHUNK_SIZE = 10;
    private static final int ANIMATION_FLAG = 0x02;

    private WebpUtils() {
    }

    /**
     * Detect if a local WebP file contains animation information.
     *
     * @param path local file path
     * @return true if animated, false if static, null if the path cannot be inspected
     */
    public static Boolean isAnimatedWebpFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return isAnimatedWebp(fis);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Detect WebP animation from the beginning of a WebP image stream.
     *
     * @param is WebP input stream
     * @return true if animated, false otherwise
     * @throws IOException on stream errors
     */
    public static boolean isAnimatedWebp(InputStream is) throws IOException {
        if (is == null) {
            return false;
        }

        byte[] header = new byte[HEADER_SIZE];
        int read = 0;
        while (read < HEADER_SIZE) {
            int n = is.read(header, read, HEADER_SIZE - read);
            if (n == -1) {
                break;
            }
            read += n;
        }
        if (read < HEADER_SIZE) {
            return false;
        }

        if (!isRiffWebp(header)) {
            return false;
        }

        return hasAnimationFlag(header);
    }

    public static Long getAnimatedWebpDuration(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return getAnimatedWebpDuration(fis);
        } catch (IOException e) {
            return null;
        }
    }

    public static long getAnimatedWebpDuration(InputStream is) throws IOException {
        if (is == null) {
            return 0;
        }

        byte[] header = new byte[12];
        if (readFully(is, header, 0, header.length) < header.length) {
            return 0;
        }

        if (!isRiffWebp(header)) {
            return 0;
        }

        boolean hasAnimation = false;
        long duration = 0;
        byte[] chunkHeader = new byte[8];

        while (true) {
            int read = readFully(is, chunkHeader, 0, chunkHeader.length);
            if (read < chunkHeader.length) {
                break;
            }
            String chunkTag = new String(chunkHeader, 0, 4, "ASCII");
            long chunkSize = readLittleEndian(chunkHeader, 4) & 0xFFFFFFFFL;
            if ("ANMF".equals(chunkTag) && chunkSize >= 20) {
                byte[] chunkBody = new byte[20];
                if (readFully(is, chunkBody, 0, chunkBody.length) < chunkBody.length) {
                    break;
                }
                int frameDelay = (int) readLittleEndian24(chunkBody, 17);
                if (frameDelay <= 10) {
                    frameDelay = 100;
                }
                duration += frameDelay;
                hasAnimation = true;
                long skipBytes = chunkSize - chunkBody.length;
                if (skipBytes > 0 && !skipFully(is, skipBytes)) {
                    break;
                }
            } else {
                if (!skipFully(is, chunkSize)) {
                    break;
                }
            }
            if ((chunkSize & 1) == 1) {
                if (!skipFully(is, 1)) {
                    break;
                }
            }
        }
        return hasAnimation ? duration : 0;
    }

    private static int readFully(InputStream is, byte[] buffer, int offset, int length) throws IOException {
        int read = 0;
        while (read < length) {
            int n = is.read(buffer, offset + read, length - read);
            if (n == -1) {
                break;
            }
            read += n;
        }
        return read;
    }

    private static boolean skipFully(InputStream is, long length) throws IOException {
        while (length > 0) {
            long skipped = is.skip(length);
            if (skipped <= 0) {
                int b = is.read();
                if (b == -1) {
                    return false;
                }
                skipped = 1;
            }
            length -= skipped;
        }
        return true;
    }

    private static long readLittleEndian24(byte[] data, int offset) {
        return ((data[offset] & 0xFFL))
                | ((data[offset + 1] & 0xFFL) << 8)
                | ((data[offset + 2] & 0xFFL) << 16);
    }

    private static boolean isRiffWebp(byte[] header) {
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    private static boolean hasAnimationFlag(byte[] header) {
        if (header[12] != 'V' || header[13] != 'P' || header[14] != '8' || header[15] != 'X') {
            return false;
        }
        int size = readLittleEndian(header, 16);
        if (size < VP8X_CHUNK_SIZE) {
            return false;
        }
        return (header[20] & ANIMATION_FLAG) != 0;
    }

    private static int readLittleEndian(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
