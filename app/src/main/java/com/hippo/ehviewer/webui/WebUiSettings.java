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

package com.hippo.ehviewer.webui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Persists the WebUI server connection and sync state. Plays the same role as
 * {@link com.hippo.ehviewer.smb.SmbSettings}. The bearer token is encrypted via
 * {@link WebUiCredentialStore}; everything else is plain preferences.
 */
public final class WebUiSettings {

    private static final String PREFS = "webui_settings";
    private static final String KEY_PROTOCOL = "protocol";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";
    private static final String KEY_REMOTE_READ = "remote_read_enabled";

    private final SharedPreferences preferences;
    private final WebUiCredentialStore credentialStore;

    public WebUiSettings(Context context) {
        this.preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.credentialStore = new WebUiCredentialStore(context);
    }

    /**
     * Returns the saved connection, or {@code null} if the server has not been
     * configured (no host). A non-null config does not imply the token is valid.
     */
    @Nullable
    public WebUiConfig loadConfig() {
        String host = preferences.getString(KEY_HOST, "");
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        String protocol = preferences.getString(KEY_PROTOCOL, WebUiConfig.PROTOCOL_HTTP);
        int port = preferences.getInt(KEY_PORT, WebUiConfig.DEFAULT_PORT);
        String username = preferences.getString(KEY_USERNAME, "");
        String token = credentialStore.load(preferences.getString(KEY_TOKEN, ""));
        return new WebUiConfig(protocol, host, port, username, token);
    }

    public boolean isConfigured() {
        return loadConfig() != null;
    }

    /**
     * Persists the connection. The supplied {@code token} is stored encrypted.
     * Call this after a successful login/connection test.
     */
    public void saveConfig(@NonNull WebUiConfig config) {
        preferences.edit()
                .putString(KEY_PROTOCOL, config.getProtocol())
                .putString(KEY_HOST, config.getHost())
                .putInt(KEY_PORT, config.getPort())
                .putString(KEY_USERNAME, config.getUsername())
                .putString(KEY_TOKEN, credentialStore.save(config.getToken()))
                .apply();
    }

    public void clearConfig() {
        String deviceId = preferences.getString(KEY_DEVICE_ID, "");
        preferences.edit().clear().apply();
        if (!TextUtils.isEmpty(deviceId)) {
            // Keep a stable device identity across reconfiguration so the server
            // continues to recognise this device after it re-authenticates.
            preferences.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
    }

    /**
     * Stable per-install device identifier in the contract's
     * {@code {platform}-{uuid}} format (see sync-conflict-rules.md §7).
     */
    @NonNull
    public String deviceId() {
        String id = preferences.getString(KEY_DEVICE_ID, "");
        if (TextUtils.isEmpty(id)) {
            id = "android-" + UUID.randomUUID();
            preferences.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    /** High-water mark used as the {@code since} value for the next pull. */
    public long lastSyncTimestamp() {
        return preferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L);
    }

    public void setLastSyncTimestamp(long timestamp) {
        preferences.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply();
    }

    /**
     * Whether GalleryActivity should read galleries from the WebUI server
     * instead of EH directly when a server is configured (roadmap 2.4).
     * Off by default; pages still stream through the local SpiderDen cache.
     */
    public boolean remoteReadEnabled() {
        return preferences.getBoolean(KEY_REMOTE_READ, false);
    }

    public void setRemoteReadEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_REMOTE_READ, enabled).apply();
    }
}
