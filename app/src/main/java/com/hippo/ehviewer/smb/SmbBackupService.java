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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.unifile.UniFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmbBackupService extends Service {

    private static final String TAG = "SmbBackupService";
    private static final String CHANNEL_ID = "smb_backup";
    private static final int NOTIFICATION_ID = 0x734D62; // "Smb"
    private static final String ACTION_START = "com.hippo.ehviewer.smb.START";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.smb.CANCEL";

    private static final AtomicBoolean sRunning = new AtomicBoolean(false);

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private ExecutorService mExecutor;
    private PowerManager.WakeLock mWakeLock;
    private volatile boolean mCancelled;

    public static boolean isRunning() {
        return sRunning.get();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, SmbBackupService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, SmbBackupService.class);
        intent.setAction(ACTION_CANCEL);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotifyManager = getSystemService(NotificationManager.class);
        mExecutor = Executors.newSingleThreadExecutor();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    getString(R.string.smb_backup_service_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.smb_backup_service_desc));
            mNotifyManager.createNotificationChannel(channel);
        }

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(getString(R.string.settings_download_smb_backup_syncing))
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setContentIntent(pi);

        Intent cancelIntent = new Intent(this, SmbBackupService.class);
        cancelIntent.setAction(ACTION_CANCEL);
        PendingIntent cancelPi = PendingIntent.getService(this, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mBuilder.addAction(android.R.drawable.ic_delete,
                getString(android.R.string.cancel), cancelPi);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            mCancelled = true;
            stopSelf();
            return START_NOT_STICKY;
        }

        if (sRunning.compareAndSet(false, true)) {
            mCancelled = false;
            startForeground(NOTIFICATION_ID, mBuilder.build());
            acquireWakeLock();
            mExecutor.execute(this::doSync);
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void doSync() {
        SmbBackupSettings backupSettings = new SmbBackupSettings(this);
        SmbConfig config = backupSettings.loadConfigIfEnabled();
        if (config == null) {
            finish(null);
            return;
        }

        UniFile localDir = Settings.getDownloadLocation();
        if (localDir == null || !localDir.isDirectory()
                || (localDir.getUri() != null && "smb".equals(localDir.getUri().getScheme()))) {
            finish(null);
            return;
        }

        UniFile[] localDirs = localDir.listFiles();
        if (localDirs == null || localDirs.length == 0) {
            finish(new int[]{0, 0});
            return;
        }

        int total = localDirs.length;
        int successCount = 0;
        int failCount = 0;

        SmbConnection connection = new SmbConnection(config);
        try {
            connection.open();
            String basePath = config.getPath();
            updateNotification(0, total, getString(R.string.settings_download_smb_backup_scanning), 0);

            for (int i = 0; i < total; i++) {
                if (mCancelled) break;
                UniFile dir = localDirs[i];
                if (!dir.isDirectory()) continue;
                String dirname = dir.getName();
                if (dirname == null || dirname.startsWith(".")) continue;

                String galleryPath = basePath.isEmpty() ? dirname : basePath + "/" + dirname;
                updateNotification(i + 1, total, dirname, 0);

                try {
                    connection.ensureDirectory(galleryPath);

                    UniFile[] files = dir.listFiles();
                    if (files != null) {
                        int fileCount = 0;
                        for (UniFile f : files) {
                            if (f != null && f.getName() != null && !f.getName().startsWith(".")) fileCount++;
                        }
                        int fileIdx = 0;

                        for (int j = 0; j < files.length; j++) {
                            if (mCancelled) break;
                            UniFile file = files[j];
                            String name = file.getName();
                            if (name == null) continue;
                            if (file.isDirectory()) {
                                connection.ensureDirectory(galleryPath + "/" + name);
                            } else {
                                String filePath = galleryPath + "/" + name;
                                if (connection.exists(filePath) && connection.length(filePath) == file.length()) continue;
                                fileIdx++;
                                updateNotification(i + 1, total,
                                        dirname + " (" + fileIdx + "/" + fileCount + ")", 0);
                                try (InputStream is = file.openInputStream()) {
                                    connection.writeFile(filePath, is);
                                }
                            }
                        }
                    }
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync " + dirname, e);
                    failCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "SMB connection failed", e);
            failCount = total;
        } finally {
            connection.close();
        }

        finish(new int[]{successCount, failCount});
    }

    private void updateNotification(int current, int total, String text, int speedBps) {
        mBuilder.setProgress(total, current, false)
                .setContentText(String.format(Locale.US, "%s (%d/%d)", text, current, total));
        if (speedBps > 0) {
            String speed;
            if (speedBps > 1024 * 1024) {
                speed = String.format(Locale.US, "%.1f MB/s", speedBps / (1024.0 * 1024.0));
            } else if (speedBps > 1024) {
                speed = String.format(Locale.US, "%.1f KB/s", speedBps / 1024.0);
            } else {
                speed = speedBps + " B/s";
            }
            mBuilder.setSubText(speed);
        }
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    private void finish(int[] result) {
        releaseWakeLock();
        sRunning.set(false);

        if (result != null) {
            mBuilder.setProgress(0, 0, false)
                    .setContentText(getString(R.string.settings_download_smb_backup_sync_done, result[0], result[1]))
                    .setOngoing(false)
                    .setAutoCancel(true);
            mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
        } else {
            mNotifyManager.cancel(NOTIFICATION_ID);
        }

        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void acquireWakeLock() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ehviewer:smb_backup");
        mWakeLock.acquire(30 * 60 * 1000L);
    }

    private void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
            mWakeLock = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        mCancelled = true;
        releaseWakeLock();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        super.onDestroy();
    }
}
