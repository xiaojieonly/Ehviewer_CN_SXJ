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

package com.hippo.yorozuya;

import android.os.Looper;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OSUtils {
    private static final String PROCFS_MEMFILE = "/proc/meminfo";
    private static final Pattern PROCFS_MEMFILE_FORMAT =
            Pattern.compile("^([a-zA-Z]*):[ \t]*([0-9]*)[ \t]kB");
    private static final String MEMTOTAL_STRING = "MemTotal";
    private static long sTotalMem = Long.MIN_VALUE;

    private OSUtils() {
    }

    public static void checkMainLoop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("It is in not main loop!");
        }
    }

    /**
     * Get application allocated memory size
     */
    public static long getAppAllocatedMemory() {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }

    /**
     * Get application max memory size
     */
    public static long getAppMaxMemory() {
        return Runtime.getRuntime().maxMemory();
    }

    /**
     * Get device RAM size
     */
    public static long getTotalMemory() {
        if (sTotalMem == Long.MIN_VALUE) {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new FileReader(PROCFS_MEMFILE), 64);
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher matcher = PROCFS_MEMFILE_FORMAT.matcher(line);
                    if (matcher.find() && MEMTOTAL_STRING.equals(matcher.group(1))) {
                        long mem = NumberUtils.parseLongSafely(matcher.group(2), -1L);
                        if (mem != -1L) {
                            mem *= 1024;
                        }
                        sTotalMem = mem;
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                IOUtils.closeQuietly(reader);
            }
            if (sTotalMem == Long.MIN_VALUE) {
                sTotalMem = -1L;
            }
        }
        return sTotalMem;
    }
}
