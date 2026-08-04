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

package com.hippo.anotherviewer.webui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Persists the WebUI server connection and sync state. Plays the same role as
 * {@link com.hippo.anotherviewer.smb.SmbSettings}. The bearer token is encrypted via
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

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");

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
        WebUiConfig config = loadConfig();
        // The full clear wipes the per-server scoped high-water marks and key
        // sets along with the connection, so a fresh config starts from a full
        // sync rather than a stale incremental one.
        preferences.edit().clear().apply();
        if (!TextUtils.isEmpty(deviceId)) {
            // Keep a stable device identity across reconfiguration so the server
            // continues to recognise this device after it re-authenticates.
            preferences.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }
        if (config != null) {
            revokeServerToken(config);
        }
    }

    /**
     * Best-effort logout: revokes the token on the server so it cannot be
     * replayed against the device's stored credentials. Fire-and-forget on a
     * background thread; failures are ignored and the server tolerates an
     * already-invalidated token. The config (and thus token) is captured before
     * the prefs are cleared, so the request still carries the old token.
     */
    private static void revokeServerToken(@NonNull WebUiConfig config) {
        String token = config.getToken();
        if (TextUtils.isEmpty(token)) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(config.baseUrl() + "/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token)
                        .post(RequestBody.create("", JSON_MEDIA))
                        .build();
                try (Response response = new OkHttpClient().newCall(request).execute()) {
                    // Best effort; outcome intentionally ignored.
                }
            } catch (Exception ignored) {
                // Fire-and-forget: never surface logout failures.
            }
        }, "webui-logout");
        thread.setDaemon(true);
        thread.start();
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

    /**
     * High-water mark used as the {@code since} value for the next pull,
     * scoped per server ({@code serverKey}, i.e. {@link WebUiConfig#baseUrl()}).
     * A missing scoped entry — including data written under the legacy
     * unscoped key — reads back as 0 so the first sync against that server is
     * a full pull, never a stale incremental one.
     */
    public long lastSyncTimestamp(@NonNull String serverKey) {
        return preferences.getLong(scopedKey(serverKey), 0L);
    }

    public void setLastSyncTimestamp(@NonNull String serverKey, long timestamp) {
        preferences.edit().putLong(scopedKey(serverKey), timestamp).apply();
    }

    /**
     * Legacy unscoped high-water mark, kept for callers that predate
     * per-server scoping (the preferences pull). New code must use the
     * {@code serverKey} overloads.
     */
    public long lastSyncTimestamp() {
        return preferences.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L);
    }

    public void setLastSyncTimestamp(long timestamp) {
        preferences.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp).apply();
    }

    /**
     * Scoped preference key, composed like the engine's per-server key sets
     * (see {@code SiteDbWebUiSyncStore}): {@code serverKey + suffix}. The
     * legacy unscoped {@link #KEY_LAST_SYNC_TIMESTAMP} key stays untouched, so
     * old data is never mistaken for this server's high-water mark.
     */
    private static String scopedKey(@NonNull String serverKey) {
        return serverKey + "." + KEY_LAST_SYNC_TIMESTAMP;
    }

    /**
     * Whether GalleryActivity should read galleries from the WebUI server
     * instead of EH directly (roadmap 2.4). Off by default; pages still stream
     * through the local SpiderDen cache. Tier-2 (browsing proxied via server,
     * ADR-0003 D3) implies it regardless of the manual switch.
     */
    public boolean remoteReadEnabled() {
        return preferences.getBoolean(KEY_REMOTE_READ, false) || clientTier() >= 2;
    }

    public void setRemoteReadEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_REMOTE_READ, enabled).apply();
    }

    // ----- Wave-2 sync policy (ADR-0003 / contract v2 §8) -----

    private static final String KEY_CONFLICT_STRATEGY = "conflict_strategy";
    private static final String KEY_CLIENT_TIER = "client_tier";
    private static final String KEY_AUTO_SYNC_INTERVAL_SEC = "auto_sync_interval_sec";

    public static final String STRATEGY_DEVICE_PRIORITY = "device_priority";
    public static final String STRATEGY_LWW = "lww";
    public static final String STRATEGY_WEB_PRIORITY = "web_priority";

    /** Conflict strategy (D1); invalid stored values fall back to the default. */
    @NonNull
    public String conflictStrategy() {
        String value = preferences.getString(KEY_CONFLICT_STRATEGY, STRATEGY_DEVICE_PRIORITY);
        if (STRATEGY_LWW.equals(value) || STRATEGY_WEB_PRIORITY.equals(value)) return value;
        return STRATEGY_DEVICE_PRIORITY;
    }

    public void setConflictStrategy(@NonNull String strategy) {
        String value = STRATEGY_DEVICE_PRIORITY;
        if (STRATEGY_LWW.equals(strategy) || STRATEGY_WEB_PRIORITY.equals(strategy)) value = strategy;
        preferences.edit().putString(KEY_CONFLICT_STRATEGY, value).apply();
    }

    /** Client behavior tier (D3), 0..3; out-of-range stored values fall back to 1. */
    public int clientTier() {
        int tier = preferences.getInt(KEY_CLIENT_TIER, 1);
        return tier >= 0 && tier <= 3 ? tier : 1;
    }

    public void setClientTier(int tier) {
        preferences.edit().putInt(KEY_CLIENT_TIER, Math.max(0, Math.min(3, tier))).apply();
    }

    /** Auto-sync interval seconds (D4); 0 = network-change only; negative clamps to 0. */
    public int autoSyncIntervalSec() {
        int value = preferences.getInt(KEY_AUTO_SYNC_INTERVAL_SEC, 900);
        return value >= 0 ? value : 900;
    }

    public void setAutoSyncIntervalSec(int seconds) {
        preferences.edit().putInt(KEY_AUTO_SYNC_INTERVAL_SEC, Math.max(0, seconds)).apply();
    }
}
