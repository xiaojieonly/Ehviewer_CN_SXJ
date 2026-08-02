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

package com.hippo.anotherviewer.smb;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

public final class SmbCacheSettings {

    private static final String PREFS = "smb_cache_settings";
    private static final String KEY_CACHE_DIR = "cache_dir";
    private static final String KEY_THRESHOLD_PERCENT = "threshold_percent";
    private static final String KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb";

    private static final int DEFAULT_THRESHOLD_PERCENT = 60;
    private static final int DEFAULT_MAX_CACHE_SIZE_MB = 1024;

    private static final int MIN_THRESHOLD_PERCENT = 10;
    private static final int MAX_THRESHOLD_PERCENT = 90;
    private static final int MIN_MAX_CACHE_SIZE_MB = 100;
    private static final int MAX_MAX_CACHE_SIZE_MB = 4096;

    private final SharedPreferences preferences;

    public SmbCacheSettings(@NonNull Context context) {
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Nullable
    public String getCacheDir() {
        return preferences.getString(KEY_CACHE_DIR, null);
    }

    public void setCacheDir(@Nullable String cacheDir) {
        preferences.edit().putString(KEY_CACHE_DIR, cacheDir).apply();
    }

    public int getThresholdPercent() {
        return preferences.getInt(KEY_THRESHOLD_PERCENT, DEFAULT_THRESHOLD_PERCENT);
    }

    public void setThresholdPercent(int thresholdPercent) {
        int clamped = Math.max(MIN_THRESHOLD_PERCENT, Math.min(MAX_THRESHOLD_PERCENT, thresholdPercent));
        preferences.edit().putInt(KEY_THRESHOLD_PERCENT, clamped).apply();
    }

    public int getMaxCacheSizeMB() {
        return preferences.getInt(KEY_MAX_CACHE_SIZE_MB, DEFAULT_MAX_CACHE_SIZE_MB);
    }

    public void setMaxCacheSizeMB(int maxCacheSizeMB) {
        int clamped = Math.max(MIN_MAX_CACHE_SIZE_MB, Math.min(MAX_MAX_CACHE_SIZE_MB, maxCacheSizeMB));
        preferences.edit().putInt(KEY_MAX_CACHE_SIZE_MB, clamped).apply();
    }

    public long getMaxCacheSizeBytes() {
        return (long) getMaxCacheSizeMB() * 1024 * 1024;
    }

    public long getThresholdBytes() {
        return getMaxCacheSizeBytes() * getThresholdPercent() / 100;
    }

    @NonNull
    public static File getSmbCacheDir(@NonNull Context context) {
        File dir = new File(context.getCacheDir(), "smb_cache");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static long getDirSize(@NonNull File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getDirSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }
}
