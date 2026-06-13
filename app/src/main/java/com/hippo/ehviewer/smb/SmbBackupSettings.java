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

package com.hippo.ehviewer.smb;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.unifile.SmbUri;

public final class SmbBackupSettings {

    private static final String PREFS = "smb_backup_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_SHARE = "share";
    private static final String KEY_PATH = "path";
    private static final String KEY_LOGIN_MODE = "login_mode";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_AGGRESSIVE_MODE = "aggressive_mode";

    private static final int DEFAULT_RAM_PERCENT = 15;

    private final SharedPreferences preferences;
    private final SmbCredentialStore credentialStore;

    public SmbBackupSettings(Context context) {
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.credentialStore = new SmbCredentialStore(context);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    @Nullable
    public SmbConfig loadConfig() {
        String host = preferences.getString(KEY_HOST, "");
        int port = preferences.getInt(KEY_PORT, SmbUri.DEFAULT_PORT);
        String share = preferences.getString(KEY_SHARE, "");
        String path = preferences.getString(KEY_PATH, "");
        String username = preferences.getString(KEY_USERNAME, "");
        String password = credentialStore.load(preferences.getString(KEY_PASSWORD, ""));
        if (TextUtils.isEmpty(host) || TextUtils.isEmpty(share)) {
            return null;
        }
        try {
            SmbLoginMode loginMode = SmbLoginMode.valueOf(
                    preferences.getString(KEY_LOGIN_MODE, SmbLoginMode.ANONYMOUS.name()));
            return new SmbConfig(host, port, share, path, loginMode,
                    TextUtils.isEmpty(username) ? null : username,
                    TextUtils.isEmpty(password) ? null : password);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void saveConfig(@NonNull SmbConfig config) {
        String encryptedPassword = credentialStore.save(config.getPassword());
        preferences.edit()
                .putString(KEY_HOST, config.getHost())
                .putInt(KEY_PORT, config.getPort())
                .putString(KEY_SHARE, config.getShare())
                .putString(KEY_PATH, config.getPath())
                .putString(KEY_LOGIN_MODE, config.getLoginMode().name())
                .putString(KEY_USERNAME, config.getUsername())
                .putString(KEY_PASSWORD, encryptedPassword)
                .apply();
    }

    public void clearConfig() {
        preferences.edit().clear().apply();
    }

    public boolean isAggressiveMode() {
        return preferences.getBoolean(KEY_AGGRESSIVE_MODE, false);
    }

    public void setAggressiveMode(boolean enabled) {
        preferences.edit().putBoolean(KEY_AGGRESSIVE_MODE, enabled).apply();
    }

    public long getRamBufferSize(Context context) {
        android.app.ActivityManager am = (android.app.ActivityManager) 
            context.getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo memInfo = new android.app.ActivityManager.MemoryInfo();
        am.getMemoryInfo(memInfo);
        long totalRam = memInfo.totalMem;
        return totalRam * DEFAULT_RAM_PERCENT / 100;
    }

    @Nullable
    public SmbConfig loadConfigIfEnabled() {
        return isEnabled() ? loadConfig() : null;
    }
}
