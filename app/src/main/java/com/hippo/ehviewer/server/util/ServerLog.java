package com.hippo.ehviewer.server.util;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ServerLog {

    public enum Level {
        DEBUG,
        INFO,
        ERROR
    }

    public static final class Entry {
        public final long timestamp;
        @NonNull
        public final Level level;
        @NonNull
        public final String message;

        Entry(long timestamp, @NonNull Level level, @NonNull String message) {
            this.timestamp = timestamp;
            this.level = level;
            this.message = message;
        }

        @NonNull
        public String format() {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(timestamp));
            return time + " [" + level.name() + "] " + message;
        }
    }

    private static final int MAX_ENTRIES = 500;
    private static final ArrayList<Entry> ENTRIES = new ArrayList<>();

    private ServerLog() {
    }

    public static synchronized void clear() {
        ENTRIES.clear();
    }

    public static synchronized void d(@NonNull String message) {
        log(Level.DEBUG, message);
    }

    public static synchronized void i(@NonNull String message) {
        log(Level.INFO, message);
    }

    public static synchronized void e(@NonNull String message) {
        log(Level.ERROR, message);
    }

    public static synchronized void log(@NonNull Level level, @NonNull String message) {
        Level current = ServerSettings.getLogLevel();
        if (!shouldRecord(level, current)) {
            return;
        }
        ENTRIES.add(new Entry(System.currentTimeMillis(), level, message));
        if (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.remove(0);
        }
    }

    @NonNull
    public static synchronized List<Entry> snapshot() {
        return new ArrayList<>(ENTRIES);
    }

    @NonNull
    public static synchronized String dumpText() {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : ENTRIES) {
            sb.append(entry.format()).append('\n');
        }
        return sb.toString();
    }

    private static boolean shouldRecord(@NonNull Level incoming, @NonNull Level current) {
        if (current == Level.DEBUG) {
            return true;
        }
        if (current == Level.INFO) {
            return incoming == Level.INFO || incoming == Level.ERROR;
        }
        return incoming == Level.ERROR;
    }
}
