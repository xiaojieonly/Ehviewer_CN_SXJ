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

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.Settings;
import com.hippo.anotherviewer.ui.SmbBackupProgressActivity;
import com.hippo.unifile.UniFile;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmbBackupService extends Service {

    private static final String TAG = "SmbBackupService";
    private static final String CHANNEL_ID = "smb_backup";
    private static final int NOTIFICATION_ID = 0x734D62; // "Smb"
    private static final String ACTION_START = "com.hippo.anotherviewer.smb.START";
    private static final String ACTION_CANCEL = "com.hippo.anotherviewer.smb.CANCEL";
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
            stopSelf();
            return START_NOT_STICKY;
        }

        if (sRunning.compareAndSet(false, true)) {
            mCancelled = false;
            mAggressive = intent != null && intent.getBooleanExtra(EXTRA_AGGRESSIVE, false);
            mLastStartIntent = intent != null ? new Intent(intent) : null;
            startForeground(NOTIFICATION_ID, mBuilder.build());
            acquireWakeLock();
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

        boolean aggressiveMode = mAggressive;
        long ramBufferSize = aggressiveMode ? backupSettings.getRamBufferSize(this) : 0;
        Log.d(TAG, "Resolved backup mode, aggressive=" + aggressiveMode
                + ", ramBufferSize=" + ramBufferSize);

        SmbSyncEngine.Source source = SmbSyncEngine.uniFileSource(localDir);

        SmbSyncEngine.Options options = new SmbSyncEngine.Options();
        options.aggressive = aggressiveMode;
        options.ramBufferSize = ramBufferSize;

        final int[] galleryIndex = new int[1];
        final int[] galleryTotal = new int[1];
        final String[] galleryName = new String[1];

        SmbSyncEngine.Callback callback = new SmbSyncEngine.Callback() {
            @Override
            public void onScan(int total) {
                updateNotification(0, total, getString(R.string.settings_download_smb_backup_scanning), 0);
            }

            @Override
            public void onGallery(int index, int total, String name) {
                galleryIndex[0] = index;
                galleryTotal[0] = total;
                galleryName[0] = name;
                updateNotification(index, total, name, 0);
            }

            @Override
            public void onFile(int fileIndex, int fileTotal, String name) {
                updateNotification(galleryIndex[0], galleryTotal[0],
                        galleryName[0] + " (" + fileIndex + "/" + fileTotal + ")", 0);
            }

            @Override
            public boolean isCancelled() {
                return mCancelled;
            }

            @Override
            public void onSpeed(long bytesPerSecond) {
            }
        };

        SmbSyncEngine.Result result = SmbSyncEngine.sync(config, source, callback, options);

        finish(new int[]{result.success, result.fail});
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }

    private void acquireWakeLock() {
        if (mWakeLock == null || !mWakeLock.isHeld()) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "anotherviewer:smb_backup");
            mWakeLock.acquire(30 * 60 * 1000L);
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
        clearProgressSnapshot();
        releaseWakeLock();
        if (mExecutor != null) {
            mExecutor.shutdownNow();
        }
        super.onDestroy();
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
