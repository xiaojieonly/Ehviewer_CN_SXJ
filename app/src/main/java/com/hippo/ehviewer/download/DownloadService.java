/*
 * Copyright 2016 Hippo Seven
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

package com.hippo.ehviewer.download;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.ui.scene.DownloadsScene;
import com.hippo.scene.StageActivity;
import com.hippo.util.ReadableTime;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.SimpleHandler;
import com.hippo.yorozuya.collect.LongList;
import com.hippo.yorozuya.collect.SparseJBArray;
import com.hippo.yorozuya.collect.SparseJLArray;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class DownloadService extends Service implements DownloadManager.DownloadListener {

    public static final String ACTION_START = "start";
    public static final String ACTION_START_RANGE = "start_range";
    public static final String ACTION_START_ALL = "start_all";
    public static final String ACTION_STOP = "stop";
    public static final String ACTION_STOP_RANGE = "stop_range";
    public static final String ACTION_STOP_CURRENT = "stop_current";
    public static final String ACTION_STOP_ALL = "stop_all";
    public static final String ACTION_DELETE = "delete";
    public static final String ACTION_DELETE_RANGE = "delete_range";

    public static final String ACTION_CLEAR = "clear";

    public static final String KEY_GALLERY_INFO = "gallery_info";
    public static final String KEY_LABEL = "label";
    public static final String KEY_GID = "gid";
    public static final String KEY_GID_LIST = "gid_list";

    private static final int ID_DOWNLOADING = 1;
    private static final int ID_DOWNLOADED = 2;
    private static final int ID_509 = 3;

    @Nullable
    private NotificationManager mNotifyManager;
    @Nullable
    private DownloadManager mDownloadManager;
    private NotificationCompat.Builder mDownloadingBuilder;
    private NotificationCompat.Builder mDownloadedBuilder;
    private NotificationCompat.Builder m509dBuilder;
    private NotificationDelay mDownloadingDelay;
    private NotificationDelay mDownloadedDelay;
    private NotificationDelay m509Delay;


    private final static SparseJBArray sItemStateArray = new SparseJBArray();
    private final static SparseJLArray<String> sItemTitleArray = new SparseJLArray<>();

    private static int sFailedCount;
    private static int sFinishedCount;
    private static int sDownloadedCount;

    private String CHANNEL_ID;

    public static void clear() {
        sFailedCount = 0;
        sFinishedCount = 0;
        sDownloadedCount = 0;
        sItemStateArray.clear();
        sItemTitleArray.clear();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        CHANNEL_ID = getPackageName()+".download";
        mNotifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            mNotifyManager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, getString(R.string.download_service),
                    NotificationManager.IMPORTANCE_LOW));
        }
        mDownloadManager = EhApplication.getDownloadManager(getApplicationContext());
        mDownloadManager.setDownloadListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mNotifyManager = null;
        if (mDownloadManager != null) {
            mDownloadManager.setDownloadListener(null);
            mDownloadManager = null;
        }
        mDownloadingBuilder = null;
        mDownloadedBuilder = null;
        m509dBuilder = null;
        if (mDownloadingDelay != null) {
            mDownloadingDelay.release();
        }
        if (mDownloadedDelay != null) {
            mDownloadedDelay.release();
        }
        if (m509Delay != null) {
            m509Delay.release();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        handleIntent(intent);
        return START_STICKY;
    }

    private void handleIntent(Intent intent) {
        String action = null;
        if (intent != null) {
            action = intent.getAction();
        }

        if (ACTION_START.equals(action)) {
            GalleryInfo gi = intent.getParcelableExtra(KEY_GALLERY_INFO);
            String label = intent.getStringExtra(KEY_LABEL);
            if (gi != null && mDownloadManager != null) {
                mDownloadManager.startDownload(gi, label);
            }
        } else if (ACTION_START_RANGE.equals(action)) {
            LongList gidList = intent.getParcelableExtra(KEY_GID_LIST);
            if (gidList != null && mDownloadManager != null) {
                mDownloadManager.startRangeDownload(gidList);
            }
        } else if (ACTION_START_ALL.equals(action)) {
            if (mDownloadManager != null) {
                mDownloadManager.startAllDownload();
            }
        } else if (ACTION_STOP.equals(action)) {
            long gid = intent.getLongExtra(KEY_GID, -1);
            if (gid != -1 && mDownloadManager != null) {
                mDownloadManager.stopDownload(gid);
            }
        } else if (ACTION_STOP_CURRENT.equals(action)) {
            if (mDownloadManager != null) {
                mDownloadManager.stopCurrentDownload();
            }
        } else if (ACTION_STOP_RANGE.equals(action)) {
            LongList gidList = intent.getParcelableExtra(KEY_GID_LIST);
            if (gidList != null && mDownloadManager != null) {
                mDownloadManager.stopRangeDownload(gidList);
            }
        } else if (ACTION_STOP_ALL.equals(action)) {
            if (mDownloadManager != null) {
                mDownloadManager.stopAllDownload();
            }
        } else if (ACTION_DELETE.equals(action)) {
            long gid = intent.getLongExtra(KEY_GID, -1);
            if (gid != -1 && mDownloadManager != null) {
                mDownloadManager.deleteDownload(gid);
            }
        } else if (ACTION_DELETE_RANGE.equals(action)) {
            LongList gidList = intent.getParcelableExtra(KEY_GID_LIST);
            if (gidList != null && mDownloadManager != null) {
                mDownloadManager.deleteRangeDownload(gidList);
            }
        } else if (ACTION_CLEAR.equals(action)) {
            clear();
        }

        checkStopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        throw new IllegalStateException("No bindService");
    }

    @SuppressWarnings("deprecation")
    private void ensureDownloadingBuilder() {
        if (mDownloadingBuilder != null) {
            return;
        }

        Intent stopAllIntent = new Intent(this, DownloadService.class);
        stopAllIntent.setAction(ACTION_STOP_ALL);
        PendingIntent piStopAll = PendingIntent.getService(this, 0, stopAllIntent, 0);

        mDownloadingBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setColor(getResources().getColor(R.color.colorPrimary))
                .addAction(R.drawable.ic_pause_x24, getString(R.string.stat_download_action_stop_all), piStopAll)
                .setShowWhen(false)
                .setChannelId(CHANNEL_ID);

        mDownloadingDelay = new NotificationDelay(this, mNotifyManager, mDownloadingBuilder, ID_DOWNLOADING);
    }

    private void ensureDownloadedBuilder() {
        if (mDownloadedBuilder != null) {
            return;
        }

        Intent clearIntent = new Intent(this, DownloadService.class);
        clearIntent.setAction(ACTION_CLEAR);
        PendingIntent piClear = PendingIntent.getService(this, 0, clearIntent, 0);

        Bundle bundle = new Bundle();
        bundle.putString(DownloadsScene.KEY_ACTION, DownloadsScene.ACTION_CLEAR_DOWNLOAD_SERVICE);
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setAction(StageActivity.ACTION_START_SCENE);
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene.class.getName());
        activityIntent.putExtra(StageActivity.KEY_SCENE_ARGS, bundle);
        PendingIntent piActivity = PendingIntent.getActivity(DownloadService.this, 0,
                activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mDownloadedBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentTitle(getString(R.string.stat_download_done_title))
                .setDeleteIntent(piClear)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(piActivity)
                .setChannelId(CHANNEL_ID);

        mDownloadedDelay = new NotificationDelay(this, mNotifyManager, mDownloadedBuilder, ID_DOWNLOADED);
    }

    private void ensure509Builder() {
        if (m509dBuilder != null) {
            return;
        }

        m509dBuilder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher))
                .setContentText(getString(R.string.stat_509_alert_title))
                .setContentText(getString(R.string.stat_509_alert_text))
                .setAutoCancel(true)
                .setOngoing(false)
                .setCategory(NotificationCompat.CATEGORY_ERROR)
                .setChannelId(CHANNEL_ID);

        m509Delay = new NotificationDelay(this, mNotifyManager, m509dBuilder, ID_509);
    }

    @Override
    public void onGet509() {
        if (mNotifyManager == null) {
            return;
        }

        ensure509Builder();
        m509dBuilder.setWhen(System.currentTimeMillis());
        m509Delay.show();
    }

    @Override
    public void onStart(DownloadInfo info) {
        if (mNotifyManager == null) {
            return;
        }

        ensureDownloadingBuilder();

        Bundle bundle = new Bundle();
        bundle.putLong(DownloadsScene.KEY_GID, info.gid);
        Intent activityIntent = new Intent(this, MainActivity.class);
        activityIntent.setAction(StageActivity.ACTION_START_SCENE);
        activityIntent.putExtra(StageActivity.KEY_SCENE_NAME, DownloadsScene.class.getName());
        activityIntent.putExtra(StageActivity.KEY_SCENE_ARGS, bundle);
        PendingIntent piActivity = PendingIntent.getActivity(DownloadService.this, 0,
                activityIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        mDownloadingBuilder.setContentTitle(EhUtils.getSuitableTitle(info))
                .setContentText(null)
                .setContentInfo(null)
                .setProgress(0, 0, true)
                .setContentIntent(piActivity);

        mDownloadingDelay.startForeground();
    }

    private void onUpdate(DownloadInfo info) {
        if (mNotifyManager == null) {
            return;
        }
        ensureDownloadingBuilder();

        long speed = info.speed;
        if (speed < 0) {
            speed = 0;
        }
        String text = FileUtils.humanReadableByteCount(speed, false) + "/S";
        long remaining = info.remaining;
        if (remaining >= 0) {
            text = getString(R.string.download_speed_text_2, text, ReadableTime.getShortTimeInterval(remaining));
        } else {
            text = getString(R.string.download_speed_text, text);
        }
        mDownloadingBuilder.setContentTitle(EhUtils.getSuitableTitle(info))
                .setContentText(text)
                .setContentInfo(info.total == -1 || info.finished == -1 ? null : info.finished + "/" + info.total)
                .setProgress(info.total, info.finished, false);

        mDownloadingDelay.startForeground();
    }

    @Override
    public void onDownload(DownloadInfo info) {
        onUpdate(info);
    }

    @Override
    public void onGetPage(DownloadInfo info) {
        onUpdate(info);
    }

    @Override
    public void onFinish(DownloadInfo info) {
        if (mNotifyManager == null) {
            return;
        }

        if (null != mDownloadingDelay) {
            mDownloadingDelay.cancel();
        }

        ensureDownloadedBuilder();

        boolean finish = info.state == DownloadInfo.STATE_FINISH;
        long gid = info.gid;
        int index = sItemStateArray.indexOfKey(gid);
        if (index < 0) { // Not contain
            sItemStateArray.put(gid, finish);
            sItemTitleArray.put(gid, EhUtils.getSuitableTitle(info));
            sDownloadedCount++;
            if (finish) {
                sFinishedCount++;
            } else {
                sFailedCount++;
            }
        } else { // Contain
            boolean oldFinish = sItemStateArray.valueAt(index);
            sItemStateArray.put(gid, finish);
            sItemTitleArray.put(gid, EhUtils.getSuitableTitle(info));
            if (oldFinish && !finish) {
                sFinishedCount--;
                sFailedCount++;
            } else if (!oldFinish && finish) {
                sFinishedCount++;
                sFailedCount--;
            }
        }

        String text;
        boolean needStyle;
        if (sFinishedCount != 0 && sFailedCount == 0) {
            if (sFinishedCount == 1) {
                if (sItemTitleArray.size() >= 1) {
                    text = getString(R.string.stat_download_done_line_succeeded, sItemTitleArray.valueAt(0));
                } else {
                    Log.d("TAG", "WTF, sItemTitleArray is null");
                    text = getString(R.string.error_unknown);
                }
                needStyle = false;
            } else {
                text = getString(R.string.stat_download_done_text_succeeded, sFinishedCount);
                needStyle = true;
            }
        } else if (sFinishedCount == 0 && sFailedCount != 0) {
            if (sFailedCount == 1) {
                if (sItemTitleArray.size() >= 1) {
                    text = getString(R.string.stat_download_done_line_failed, sItemTitleArray.valueAt(0));
                } else {
                    Log.d("TAG", "WTF, sItemTitleArray is null");
                    text = getString(R.string.error_unknown);
                }
                needStyle = false;
            } else {
                text = getString(R.string.stat_download_done_text_failed, sFailedCount);
                needStyle = true;
            }
        } else {
            text = getString(R.string.stat_download_done_text_mix, sFinishedCount, sFailedCount);
            needStyle = true;
        }

        NotificationCompat.InboxStyle style;
        if (needStyle) {
            style = new NotificationCompat.InboxStyle();
            style.setBigContentTitle(getString(R.string.stat_download_done_title));
            SparseJBArray stateArray = sItemStateArray;
            SparseJLArray<String> titleArray = sItemTitleArray;
            for (int i = 0, n = stateArray.size(); i < n; i++) {
                long id = stateArray.keyAt(i);
                boolean fin = stateArray.valueAt(i);
                String title = titleArray.get(id);
                if (title == null) {
                    continue;
                }
                style.addLine(getString(fin ? R.string.stat_download_done_line_succeeded :
                                R.string.stat_download_done_line_failed, title));
            }
        } else {
            style = null;
        }

        mDownloadedBuilder.setContentText(text)
                .setStyle(style)
                .setWhen(System.currentTimeMillis())
                .setNumber(sDownloadedCount);

        mDownloadedDelay.show();

        checkStopSelf();
    }

    @Override
    public void onCancel(DownloadInfo info) {
        if (mNotifyManager == null) {
            return;
        }

        if (null != mDownloadingDelay) {
            mDownloadingDelay.cancel();
        }

        checkStopSelf();
    }

    private void checkStopSelf() {
        if (mDownloadManager == null || mDownloadManager.isIdle()) {
            stopForeground(true);
            stopSelf();
        }
    }

    // TODO Include all notification in one delay
    // Avoid frequent notification
    private static class NotificationDelay implements Runnable {

        @IntDef({OPS_NOTIFY, OPS_CANCEL, OPS_START_FOREGROUND})
        @Retention(RetentionPolicy.SOURCE)
        private @interface Ops {}

        private static final int OPS_NOTIFY = 0;
        private static final int OPS_CANCEL = 1;
        private static final int OPS_START_FOREGROUND = 2;

        private static final long DELAY = 1000; // 1s

        private Service mService;
        private final NotificationManager mNotifyManager;
        private final NotificationCompat.Builder mBuilder;
        private final int mId;

        private long mLastTime;
        private boolean mPosted;
        // false for show, true for cancel
        @Ops
        private int mOps;

        public NotificationDelay(Service service, NotificationManager notifyManager,
                NotificationCompat.Builder builder, int id) {
            mService = service;
            mNotifyManager = notifyManager;
            mBuilder = builder;
            mId = id;
        }

        public void release() {
            mService = null;
        }

        public void show() {
            if (mPosted) {
                mOps = OPS_NOTIFY;
            } else {
                long now = SystemClock.currentThreadTimeMillis();
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    mNotifyManager.notify(mId, mBuilder.build());
                } else {
                    // Too quick, post delay
                    mOps = OPS_NOTIFY;
                    mPosted = true;
                    SimpleHandler.getInstance().postDelayed(this, DELAY);
                }
                mLastTime = now;
            }
        }

        public void cancel() {
            if (mPosted) {
                mOps = OPS_CANCEL;
            } else {
                long now = SystemClock.currentThreadTimeMillis();
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    mNotifyManager.cancel(mId);
                } else {
                    // Too quick, post delay
                    mOps = OPS_CANCEL;
                    mPosted = true;
                    SimpleHandler.getInstance().postDelayed(this, DELAY);
                }
            }
        }

        public void startForeground() {
            if (mPosted) {
                mOps = OPS_START_FOREGROUND;
            } else {
                long now = SystemClock.currentThreadTimeMillis();
                if (now - mLastTime > DELAY) {
                    // Wait long enough, do it now
                    if (mService != null) {
                        mService.startForeground(mId, mBuilder.build());
                    }
                } else {
                    // Too quick, post delay
                    mOps = OPS_START_FOREGROUND;
                    mPosted = true;
                    SimpleHandler.getInstance().postDelayed(this, DELAY);
                }
            }
        }

        @Override
        public void run() {
            mPosted = false;
            switch (mOps) {
                case OPS_NOTIFY:
                    mNotifyManager.notify(mId, mBuilder.build());
                    break;
                case OPS_CANCEL:
                    mNotifyManager.cancel(mId);
                    break;
                case OPS_START_FOREGROUND:
                    if (mService != null) {
                        mService.startForeground(mId, mBuilder.build());
                    }
                    break;
            }
        }
    }
}
