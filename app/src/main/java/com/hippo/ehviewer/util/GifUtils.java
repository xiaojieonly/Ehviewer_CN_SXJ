package com.hippo.ehviewer.util;

import android.text.TextUtils;
import com.hippo.lib.yorozuya.IOUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class GifUtils {

    private static final int HEADER_SIZE = 6;
    private static final int LSD_SIZE = 7;
    private static final int EXTENSION_INTRODUCER = 0x21;
    private static final int GRAPHICS_CONTROL_LABEL = 0xF9;
    private static final int IMAGE_SEPARATOR = 0x2C;
    private static final int TRAILER = 0x3B;
    private static final int DEFAULT_FRAME_DELAY = 100;

    private GifUtils() {
    }

    public static Boolean isAnimatedGifFile(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return isAnimatedGif(fis);
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean isAnimatedGif(InputStream is) throws IOException {
        return getGifAnimationDuration(is) > 0;
    }

    public static Long getGifAnimationDuration(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            return getGifAnimationDuration(fis);
        } catch (IOException e) {
            return null;
        }
    }

    public static long getGifAnimationDuration(InputStream is) throws IOException {
        if (is == null) {
            return 0;
        }

        byte[] header = new byte[HEADER_SIZE];
        if (readFully(is, header, 0, HEADER_SIZE) < HEADER_SIZE) {
            return 0;
        }
        String signature = new String(header, "ASCII");
        if (!"GIF89a".equals(signature) && !"GIF87a".equals(signature)) {
            return 0;
        }

        byte[] lsd = new byte[LSD_SIZE];
        if (readFully(is, lsd, 0, LSD_SIZE) < LSD_SIZE) {
            return 0;
        }

        int packed = lsd[4] & 0xFF;
        if ((packed & 0x80) != 0) {
            int tableSize = 3 * (1 << ((packed & 0x07) + 1));
            if (!skipFully(is, tableSize)) {
                return 0;
            }
        }

        int pendingDelay = -1;
        int imageCount = 0;
        long totalDelay = 0;

        while (true) {
            int block = is.read();
            if (block == -1 || block == TRAILER) {
                break;
            }
            if (block == IMAGE_SEPARATOR) {
                imageCount++;
                byte[] descriptor = new byte[9];
                if (readFully(is, descriptor, 0, 9) < 9) {
                    return 0;
                }
                int packedFields = descriptor[8] & 0xFF;
                if ((packedFields & 0x80) != 0) {
                    int localTableSize = 3 * (1 << ((packedFields & 0x07) + 1));
                    if (!skipFully(is, localTableSize)) {
                        return 0;
                    }
                }
                if (is.read() == -1) {
                    return 0;
                }
                if (!skipSubBlocks(is)) {
                    return 0;
                }
                int frameDelay = pendingDelay >= 0 ? pendingDelay : DEFAULT_FRAME_DELAY;
                totalDelay += frameDelay;
                pendingDelay = -1;
            } else if (block == EXTENSION_INTRODUCER) {
                int label = is.read();
                if (label == -1) {
                    return 0;
                }
                if (label == GRAPHICS_CONTROL_LABEL) {
                    int blockSize = is.read();
                    if (blockSize == -1) {
                        return 0;
                    }
                    byte[] blockData = new byte[blockSize];
                    if (readFully(is, blockData, 0, blockSize) < blockSize) {
                        return 0;
                    }
                    if (!skipSubBlocks(is)) {
                        return 0;
                    }
                    if (blockSize >= 4) {
                        int delay = ((blockData[2] & 0xFF) << 8) | (blockData[1] & 0xFF);
                        if (delay <= 10) {
                            delay = DEFAULT_FRAME_DELAY;
                        }
                        pendingDelay = delay;
                    }
                } else {
                    if (!skipSubBlocks(is)) {
                        return 0;
                    }
                }
            } else {
                break;
            }
        }

        return imageCount > 1 ? totalDelay : 0;
    }

    private static int readFully(InputStream is, byte[] buffer, int offset, int length)
            throws IOException {
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

    private static boolean skipSubBlocks(InputStream is) throws IOException {
        int blockSize;
        do {
            blockSize = is.read();
            if (blockSize == -1) {
                return false;
            }
            if (blockSize > 0 && !skipFully(is, blockSize)) {
                return false;
            }
        } while (blockSize != 0);
        return true;
    }
}
