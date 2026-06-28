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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.ui.SmbBackupProgressActivity;
import com.hippo.unifile.UniFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmbBackupService extends Service {

    private static final String TAG = "SmbBackupService";
    private static final int SMB_READ_BUFFER_BYTES = 256 * 1024;
    private static final String CHANNEL_ID = "smb_backup";
    private static final int NOTIFICATION_ID = 0x734D62; // "Smb"
    private static final String ACTION_START = "com.hippo.ehviewer.smb.START";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.smb.CANCEL";
    private static final String EXTRA_AGGRESSIVE = "aggressive";

    private static final AtomicBoolean sRunning = new AtomicBoolean(false);
    private static final Object sProgressLock = new Object();

    @Nullable
    private static ProgressSnapshot sProgressSnapshot;

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;
    private ExecutorService mExecutor;
    private PowerManager.WakeLock mWakeLock;
    private volatile boolean mCancelled;
    private volatile boolean mAggressive;
    private Intent mLastStartIntent;
    private BroadcastReceiver mScreenStateReceiver;

    public static boolean isRunning() {
        return sRunning.get();
    }

    @Nullable
    public static ProgressSnapshot getProgressSnapshot() {
        synchronized (sProgressLock) {
            return sProgressSnapshot;
        }
    }

    public static void start(Context context) {
        startWithAggressive(context, false);
    }

    public static void startWithAggressive(Context context, boolean aggressive) {
        Intent intent = new Intent(context, SmbBackupService.class);
        intent.setAction(ACTION_START);
        intent.putExtra(EXTRA_AGGRESSIVE, aggressive);
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
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(getString(R.string.smb_backup_service_desc));
            channel.setShowBadge(false);
            mNotifyManager.createNotificationChannel(channel);
        }

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(getString(R.string.settings_download_smb_backup_syncing))
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        Intent mainIntent = new Intent(this, SmbBackupProgressActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
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
            unregisterScreenStateReceiver();
            stopSelf();
            return START_NOT_STICKY;
        }

        if (sRunning.compareAndSet(false, true)) {
            mCancelled = false;
            mAggressive = intent != null && intent.getBooleanExtra(EXTRA_AGGRESSIVE, false);
            mLastStartIntent = intent != null ? new Intent(intent) : null;
            startForeground(NOTIFICATION_ID, mBuilder.build());
            acquireWakeLock();
            registerScreenStateReceiver();
            mExecutor.execute(this::doSync);
        } else {
            stopSelf();
        }
        return START_REDELIVER_INTENT;
    }

    private void doSync() {
        Log.d(TAG, "doSync started, aggressive=" + mAggressive);
        SmbBackupSettings backupSettings = new SmbBackupSettings(this);
        SmbConfig config = backupSettings.loadConfigIfEnabled();
        if (config == null) {
            Log.d(TAG, "config is null, aborting");
            finish(null);
            return;
        }
        Log.d(TAG, "config loaded: " + config.getHost() + "/" + config.getShare());

        UniFile localDir = Settings.getDownloadLocation();
        Log.d(TAG, "download location: " + (localDir != null ? localDir.getUri() : "null"));
        if (localDir == null
                || (localDir.getUri() != null && "smb".equals(localDir.getUri().getScheme()))) {
            Log.d(TAG, "local dir is null or SMB - aborting");
            finish(null);
            return;
        }

        UniFile[] localDirs = localDir.listFiles();
        Log.d(TAG, "found " + (localDirs != null ? localDirs.length : 0) + " directories");
        if (localDirs == null || localDirs.length == 0) {
            Log.d(TAG, "no directories found - aborting");
            finish(new int[]{0, 0});
            return;
        }

        int total = localDirs.length;
        int successCount = 0;
        int failCount = 0;

        boolean aggressiveMode = mAggressive;
        long ramBufferSize = aggressiveMode ? backupSettings.getRamBufferSize(this) : 0;
        Log.d(TAG, "Resolved backup mode, aggressive=" + aggressiveMode
                + ", ramBufferSize=" + ramBufferSize);

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
                                
                                if (aggressiveMode && ramBufferSize > 0) {
                                    Log.d(TAG, "Using aggressive RAM-buffer upload for " + filePath
                                            + ", threshold=" + ramBufferSize);
                                    try {
                                        uploadWithRamBuffer(connection, file, filePath, ramBufferSize);
                                    } catch (OutOfMemoryError e) {
                                        Log.w(TAG, "Aggressive upload ran out of memory, falling back to stream for "
                                                + filePath, e);
                                        uploadWithStream(connection, file, filePath);
                                    }
                                } else {
                                    if (aggressiveMode) {
                                        Log.d(TAG, "Aggressive mode requested but RAM buffer unavailable, using stream for "
                                                + filePath);
                                    }
                                    uploadWithStream(connection, file, filePath);
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

    private void uploadWithStream(SmbConnection connection, UniFile file, String smbPath)
            throws IOException {
        try (InputStream is = file.openInputStream()) {
            connection.writeFile(smbPath, is);
        }
    }

    private void uploadWithRamBuffer(SmbConnection connection, UniFile file, String smbPath, long bufferSize) throws IOException {
        int readBufferSize = (int) Math.max(8192L, Math.min(bufferSize, SMB_READ_BUFFER_BYTES));
        byte[] buffer = new byte[readBufferSize];
        java.io.ByteArrayOutputStream ramBuffer = new java.io.ByteArrayOutputStream(
                Math.min(readBufferSize * 4, 1024 * 1024));
        long flushThreshold = Math.max(readBufferSize, bufferSize);
        boolean append = false;
        
        try (InputStream is = file.openInputStream()) {
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                ramBuffer.write(buffer, 0, bytesRead);
                if (ramBuffer.size() >= flushThreshold) {
                    append = flushRamBufferToSmb(connection, smbPath, ramBuffer, append);
                    ramBuffer.reset();
                }
            }
            if (ramBuffer.size() > 0) {
                flushRamBufferToSmb(connection, smbPath, ramBuffer, append);
            }
        }
    }

    private boolean flushRamBufferToSmb(SmbConnection connection, String smbPath,
            java.io.ByteArrayOutputStream ramBuffer, boolean append) throws IOException {
        byte[] data = ramBuffer.toByteArray();
        try (InputStream is = new java.io.ByteArrayInputStream(data)) {
            connection.writeFile(smbPath, is, append);
        }
        return true;
    }

    private void updateNotification(int current, int total, String text, int speedBps) {
        updateProgressSnapshot(current, total, text, speedBps);
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
        unregisterScreenStateReceiver();
        releaseWakeLock();
        sRunning.set(false);
        clearProgressSnapshot();

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
        if (mWakeLock == null || !mWakeLock.isHeld()) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ehviewer:smb_backup");
            mWakeLock.acquire();
        }
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
        unregisterScreenStateReceiver();
        clearProgressSnapshot();
        releaseWakeLock();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        super.onDestroy();
    }

    private void registerScreenStateReceiver() {
        if (mScreenStateReceiver != null) return;
        mScreenStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    Log.d(TAG, "Screen off - ensuring WakeLock is held");
                    acquireWakeLock();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    Log.d(TAG, "Screen on - ensuring WakeLock is held");
                    acquireWakeLock();
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mScreenStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mScreenStateReceiver, filter);
        }
    }

    private void unregisterScreenStateReceiver() {
        if (mScreenStateReceiver != null) {
            try {
                unregisterReceiver(mScreenStateReceiver);
            } catch (Exception e) {
                Log.w(TAG, "Failed to unregister screen state receiver", e);
            }
            mScreenStateReceiver = null;
        }
    }

    private void updateProgressSnapshot(int current, int total, String text, int speedBps) {
        synchronized (sProgressLock) {
            sProgressSnapshot = new ProgressSnapshot(current, total, text, speedBps, mAggressive);
        }
    }

    private static void clearProgressSnapshot() {
        synchronized (sProgressLock) {
            sProgressSnapshot = null;
        }
    }

    public static final class ProgressSnapshot {
        public final int current;
        public final int total;
        public final String text;
        public final int speedBps;
        public final boolean aggressive;

        private ProgressSnapshot(int current, int total, String text, int speedBps,
                boolean aggressive) {
            this.current = current;
            this.total = total;
            this.text = text;
            this.speedBps = speedBps;
            this.aggressive = aggressive;
        }
    }
}
