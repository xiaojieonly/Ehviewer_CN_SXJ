/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.yorozuya;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class FileUtils {
    private static final char[] FORBIDDEN_FILENAME_CHARACTERS = {
            '\\',
            '/',
            ':',
            '*',
            '?',
            '"',
            '<',
            '>',
            '|',
    };

    private FileUtils() {
    }

    public static boolean ensureFile(File file) {
        return file != null && (!file.exists() || file.isFile());
    }

    public static boolean ensureDirectory(File file) {
        if (file != null) {
            if (file.exists()) {
                return file.isDirectory();
            } else {
                return file.mkdirs();
            }
        } else {
            return false;
        }
    }

    /**
     * Convert byte to human readable string.<br/>
     * http://stackoverflow.com/questions/3758606/
     *
     * @param bytes the bytes to convert
     * @param si    si units
     * @return the human readable string
     */
    public static String humanReadableByteCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    /**
     * Try to delete file, dir and it's children
     *
     * @param file the file to delete
     *             The dir to deleted
     */
    public static boolean delete(File file) {
        if (file == null) {
            return false;
        }
        boolean success = true;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                success &= delete(f);
            }
        }
        /*
        final File to = new File(file.getAbsolutePath()
                + System.currentTimeMillis());
        if (file.renameTo(to)) {
            success &= to.delete();
        } else
            success &= file.delete();
        }
        */
        success &= file.delete();
        return success;
    }

    public static boolean deleteContent(File file) {
        if (file == null) {
            return false;
        }

        boolean success = true;
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                success &= delete(f);
            }
        }
        return success;
    }

    /**
     * @return {@code null} for get exception
     */
    @Nullable
    public static String read(File file) {
        if (file == null) {
            return null;
        }

        InputStream is = null;
        try {
            is = new FileInputStream(file);
            return IOUtils.readString(is, "utf-8");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    public static boolean write(File file, String str) {
        if (file == null) {
            return false;
        }
        if (str == null) {
            return true;
        }

        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            os.write(str.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtils.closeQuietly(os);
        }
    }

    public static String sanitizeFilename(@NonNull String filename) {
        // Remove forbidden_filename_characters
        filename = StringUtils.remove(filename, FORBIDDEN_FILENAME_CHARACTERS);

        // Ensure utf-8 byte count <= 255
        int byteCount = 0;
        int length = 0;
        for (int len = filename.length(); length < len; length++) {
            char ch = filename.charAt(length);
            if (ch <= 0x7F) {
                byteCount++;
            } else if (ch <= 0x7FF) {
                byteCount += 2;
            } else if (Character.isHighSurrogate(ch)) {
                byteCount += 4;
                ++length;
            } else {
                byteCount += 3;
            }
            // Meet max byte count
            if (byteCount > 255) {
                filename = filename.substring(0, length);
                break;
            }
        }

        // Trim
        return StringUtils.trim(filename);
    }

    /**
     * Get extension from filename
     *
     * @param filename the complete filename
     * @return null for can't find extension, "" empty String for ending with . dot
     */
    public static String getExtensionFromFilename(@Nullable String filename) {
        if (null == filename) {
            return null;
        }

        int index = filename.lastIndexOf('.');
        if (index != -1) {
            return filename.substring(index + 1);
        } else {
            return null;
        }
    }

    /**
     * Get name from filename
     *
     * @param filename the complete filename
     * @return null for start with . dot
     */
    public static String getNameFromFilename(@Nullable String filename) {
        if (null == filename) {
            return null;
        }

        int index = filename.lastIndexOf('.');
        if (index != -1) {
            String name = filename.substring(0, index);
            return TextUtils.isEmpty(name) ? null : name;
        } else {
            return filename;
        }
    }

    /**
     * Create a temp file, you need to delete it by you self.
     *
     * @param parent    The temp file's parent
     * @param extension The extension of temp file
     * @return The temp file or null
     */
    @Nullable
    public static File createTempFile(@Nullable File parent, @Nullable String extension) {
        if (parent == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            String filename = Long.toString(now + i);
            if (extension != null) {
                filename = filename + '.' + extension;
            }
            File tempFile = new File(parent, filename);
            if (!tempFile.exists()) {
                return tempFile;
            }
        }

        // Unbelievable
        return null;
    }

    /**
     * Create a temp dir, you need to delete it by you self.
     *
     * @param parent The temp file's parent
     * @return The temp dir or null
     */
    @Nullable
    public static File createTempDir(@Nullable File parent) {
        if (parent == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            String filename = Long.toString(now + i);
            File tempFile = new File(parent, filename);
            if (!tempFile.exists() && tempFile.mkdirs()) {
                return tempFile;
            }
        }

        // Unbelievable
        return null;
    }

    /**
     * Only support file now
     */
    public static boolean rename(@NonNull File from, @NonNull File to) {
        if (!from.isFile() || to.exists()) {
            return false;
        }

        boolean ok = from.renameTo(to);
        if (ok && !from.exists() && to.isFile()) {
            return true;
        }

        // Copy content
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(from);
            os = new FileOutputStream(to);
            IOUtils.copy(is, os);
            ok = true;
        } catch (IOException e) {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
            ok = false;
        }

        if (!ok) {
            to.delete();
            return false;
        }

        // delete old one
        from.delete();

        return true;
    }
}
