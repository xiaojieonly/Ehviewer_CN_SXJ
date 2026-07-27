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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.ui.MainActivity;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SmbUploadService extends Service {

    private static final String CHANNEL_ID = "smb_upload";
    private static final int NOTIFICATION_ID = 0x734D55;
    private static final String ACTION_START = "com.hippo.ehviewer.smb.UPLOAD_START";
    private static final String ACTION_CANCEL = "com.hippo.ehviewer.smb.UPLOAD_CANCEL";

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
        Intent intent = new Intent(context, SmbUploadService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void cancel(Context context) {
        Intent intent = new Intent(context, SmbUploadService.class);
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
                    getString(R.string.smb_upload_service_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.smb_upload_service_desc));
            mNotifyManager.createNotificationChannel(channel);
        }

        mBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle(getString(R.string.settings_download_smb_upload_syncing))
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        mBuilder.setContentIntent(pi);

        Intent cancelIntent = new Intent(this, SmbUploadService.class);
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
            mExecutor.execute(this::doUpload);
        } else {
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void doUpload() {
        SmbSettings smbSettings = new SmbSettings(this);
        SmbConfig config = smbSettings.loadConfig();
        if (config == null) {
            finish(null);
            return;
        }

        File cacheDir = SmbCacheSettings.getSmbCacheDir(this);
        File[] galleries = cacheDir.listFiles();
        if (galleries == null || galleries.length == 0) {
            finish(null);
            return;
        }

        SmbSyncEngine.Source source = SmbSyncEngine.fileSource(cacheDir);

        SmbSyncEngine.Options options = new SmbSyncEngine.Options();
        options.deleteAfterUpload = true;

        SmbSyncEngine.Callback callback = new SmbSyncEngine.Callback() {
            @Override
            public void onScan(int total) {
                updateNotification(0, total, getString(R.string.settings_download_smb_upload_scanning), 0);
            }

            @Override
            public void onGallery(int index, int total, String name) {
                updateNotification(index, total, name, 0);
            }

            @Override
            public void onFile(int fileIndex, int fileTotal, String name) {
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
                    .setContentText(getString(R.string.settings_download_smb_upload_sync_done, result[0], result[1]))
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
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ehviewer:smb_upload");
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
