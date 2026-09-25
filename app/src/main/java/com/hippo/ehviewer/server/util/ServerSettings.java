package com.hippo.ehviewer.server.util;

import androidx.annotation.NonNull;

import com.hippo.ehviewer.Settings;

public final class ServerSettings {

    private static final int DEFAULT_PORT = 8080;

    private ServerSettings() {
    }

    public static boolean isEnabled() {
        return Settings.getServerEnabled();
    }

    public static void setEnabled(boolean enabled) {
        Settings.putServerEnabled(enabled);
    }

    public static int getConfiguredPort() {
        int port = Settings.getServerPort();
        if (port < 1 || port > 65535) {
            return DEFAULT_PORT;
        }
        return port;
    }

    public static void setConfiguredPort(int port) {
        Settings.putServerPort(port);
    }

    public static int getBoundPort() {
        return Settings.getServerBoundPort();
    }

    public static void setBoundPort(int port) {
        Settings.putServerBoundPort(port);
    }

    @NonNull
    public static ServerLog.Level getLogLevel() {
        String raw = Settings.getServerLogLevel();
        try {
            return ServerLog.Level.valueOf(raw);
        } catch (Throwable ignored) {
            return ServerLog.Level.INFO;
        }
    }

    public static void setLogLevel(@NonNull String level) {
        Settings.putServerLogLevel(level);
    }
}
