package com.hippo.ehviewer.sync.nas;

import android.content.Context;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.Settings;

public final class NasConfigStore {
    public static final String KEY_ENABLED = "nas_sync_enabled";
    public static final String KEY_HOST = "nas_sync_host";
    public static final String KEY_SHARE = "nas_sync_share";
    public static final String KEY_DIRECTORY = "nas_sync_directory";
    public static final String KEY_DOMAIN = "nas_sync_domain";
    public static final String KEY_USERNAME = "nas_sync_username";
    public static final String KEY_DOWNLOAD_BEHAVIOR = "nas_download_behavior";
    public static final String KEY_SCHEDULE_ENABLED = "nas_schedule_enabled";
    public static final String KEY_SCHEDULE_MODE = "nas_schedule_mode";
    public static final String KEY_SCHEDULE_HOUR = "nas_schedule_hour";
    public static final String KEY_SCHEDULE_MINUTE = "nas_schedule_minute";
    public static final String DOWNLOAD_PHONE = "phone";
    public static final String DOWNLOAD_PHONE_NAS = "phone_nas";
    public static final String DOWNLOAD_NAS_ONLY = "nas_only";
    public static final String SCHEDULE_BIDIRECTIONAL = "bidirectional";
    public static final String SCHEDULE_UPLOAD = "upload";
    public static final String SCHEDULE_DOWNLOAD = "download";

    private NasConfigStore() {}

    public static boolean isEnabled(@NonNull Context context) {
        return Settings.getBoolean(KEY_ENABLED, false);
    }

    public static boolean isConfigured() {
        return !Settings.getString(KEY_HOST, "").trim().isEmpty()
                && !Settings.getString(KEY_SHARE, "").trim().isEmpty();
    }

    @NonNull
    public static String getDownloadBehavior() {
        return Settings.getString(KEY_DOWNLOAD_BEHAVIOR, DOWNLOAD_PHONE);
    }

    public static boolean isScheduleEnabled() {
        return Settings.getBoolean(KEY_SCHEDULE_ENABLED, false);
    }

    @NonNull
    public static String getScheduleMode() {
        return Settings.getString(KEY_SCHEDULE_MODE, SCHEDULE_BIDIRECTIONAL);
    }

    public static int getScheduleHour() {
        return Settings.getInt(KEY_SCHEDULE_HOUR, 3);
    }

    public static int getScheduleMinute() {
        return Settings.getInt(KEY_SCHEDULE_MINUTE, 0);
    }

    @NonNull
    public static NasSyncConfig load(@NonNull Context context) {
        char[] password = NasCredentialStore.loadPassword(context);
        try {
            return new NasSyncConfig(
                    Settings.getString(KEY_HOST, ""),
                    Settings.getString(KEY_SHARE, ""),
                    Settings.getString(KEY_DIRECTORY, ""),
                    Settings.getString(KEY_DOMAIN, ""),
                    Settings.getString(KEY_USERNAME, ""), password);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }
}
