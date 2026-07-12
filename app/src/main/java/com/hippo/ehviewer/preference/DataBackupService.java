package com.hippo.ehviewer.preference;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.util.ReadableTime;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;

public final class DataBackupService extends Service {
    private static final String ACTION_EXPORT = "com.hippo.ehviewer.backup.EXPORT";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.backup.CANCEL";
    private static final String CHANNEL_ID = "data_backup";
    private static final int NOTIFICATION_ID = 0x45484442;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NotificationManager notifications;
    private long startedAt;
    private long lastNotifyAt;
    private volatile boolean cancelled;

    public static void startExport(Context context) {
        ContextCompat.startForegroundService(context,
                new Intent(context, DataBackupService.class).setAction(ACTION_EXPORT));
    }

    @Override public void onCreate() {
        super.onCreate();
        notifications = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.settings_advanced_export_data),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            notifications.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            return START_NOT_STICKY;
        }
        if (!ACTION_EXPORT.equals(intent.getAction())) return START_NOT_STICKY;
        cancelled = false;
        startedAt = SystemClock.elapsedRealtime();
        startForeground(NOTIFICATION_ID, notification(getString(R.string.backup_preparing),
                0, -1, false));
        executor.execute(() -> export(startId));
        return START_NOT_STICKY;
    }

    private void export(int startId) {
        File directory = AppConfig.getExternalDataDir();
        File target = directory != null ? new File(directory,
                ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".db") : null;
        boolean success = false;
        try {
            success = target != null && EhDB.exportDB(this, target, (current, total) -> {
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("Data backup cancelled");
                }
                long now = SystemClock.elapsedRealtime();
                if (current < total && now - lastNotifyAt < 250L) return;
                lastNotifyAt = now;
                notifications.notify(NOTIFICATION_ID,
                        notification(getString(R.string.backup_copying), current, total, false));
            });
        } catch (CancellationException ignored) {
            cancelled = true;
        } catch (RuntimeException error) {
            Log.e("DataBackupService", "Unable to export database", error);
        }
        String text = cancelled ? getString(R.string.backup_cancelled)
                : success ? getString(R.string.settings_advanced_export_data_to,
                target.getPath()) : getString(R.string.settings_advanced_export_data_failed);
        notifications.notify(NOTIFICATION_ID, notification(text, 0, 0, true));
        stopForeground(false);
        stopSelfResult(startId);
    }

    private android.app.Notification notification(String text, int current, int total,
                                                   boolean finished) {
        PendingIntent content = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent cancel = PendingIntent.getService(this, 1,
                new Intent(this, DataBackupService.class).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(finished ? R.string.backup_finished
                        : R.string.backup_running))
                .setContentText(text).setContentIntent(content).setOnlyAlertOnce(true)
                .setOngoing(!finished).setAutoCancel(finished);
        if (!finished) {
            if (total > 0) {
                long elapsed = Math.max(1L, SystemClock.elapsedRealtime() - startedAt);
                long remain = current > 0 ? elapsed * Math.max(0, total - current) / current : -1;
                String detail = current + "/" + total;
                if (remain >= 0) detail += " · " + String.format(Locale.getDefault(),
                        "ETA %02d:%02d", remain / 60000, remain / 1000 % 60);
                builder.setSubText(detail).setProgress(total, Math.min(current, total), false);
            } else builder.setProgress(0, 0, true);
            builder.addAction(0, getString(android.R.string.cancel), cancel);
        }
        return builder.build();
    }

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        cancelled = true;
        executor.shutdownNow();
        super.onDestroy();
    }
}
