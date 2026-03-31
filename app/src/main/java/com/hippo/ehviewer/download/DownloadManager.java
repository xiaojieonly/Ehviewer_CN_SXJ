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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.Analytics;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.DownloadedFileManager;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.local.LocalGalleryManager;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.DownloadedFile;
import com.hippo.ehviewer.dao.GalleryVersionMap;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.ehviewer.cache.GalleryCacheManager;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.EhEngine;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.client.data.GalleryDetail;
import com.hippo.ehviewer.client.data.GalleryTagGroup;
import okhttp3.OkHttpClient;
import com.hippo.lib.image.Image;
//import com.hippo.lib.image.Image1;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExecutorManager;
import com.hippo.util.IoThreadPoolExecutor;
import com.hippo.lib.yorozuya.ConcurrentPool;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.ObjectUtils;
import com.hippo.lib.yorozuya.SimpleHandler;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.collect.LongList;
import com.hippo.lib.yorozuya.collect.SparseIJArray;
import com.hippo.lib.yorozuya.collect.SparseJLArray;
import com.hippo.ehviewer.util.UiThreadHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DownloadManager implements SpiderQueen.OnSpiderListener {

    private static final String TAG = DownloadManager.class.getSimpleName();

    public static final String DOWNLOAD_INFO_FILENAME = ".ehviewer";
    public static final String DOWNLOAD_INFO_HEADER = "gid,token,title,title_jpn,thumb,category,posted,uploader,rating,rated,simple_lang,simple_tags,thumb_width,thumb_height,span_size,span_index,span_group_index,favorite_slot,favorite_name,pages";

    private final Context mContext;

    // All download info list
    private final LinkedList<DownloadInfo> mAllInfoList;
    // All download info map
    private final SparseJLArray<DownloadInfo> mAllInfoMap;
    // label and info list map, without default label info list
    private final Map<String, LinkedList<DownloadInfo>> mMap;

    private final Map<String, Long> mLabelCountMap;
    // All labels without default label
    private final List<DownloadLabel> mLabelList;
    // Store download info with default label
    private final LinkedList<DownloadInfo> mDefaultInfoList;
// Store download info wait to start（正常下载队列）
	private final LinkedList<DownloadInfo> mWaitList;
	// 复制队列（本地增量）
	private final LinkedList<DownloadInfo> mCopyList;
	private final Object mCopyLock = new Object();
	private boolean mCopyRunning = false;

    private final SpeedReminder mSpeedReminder;

    @Nullable
    private DownloadListener mDownloadListener;
    private final List<DownloadInfoListener> mDownloadInfoListeners;

    @Nullable
    private DownloadInfo mCurrentTask;
    @Nullable
    private SpiderQueen mCurrentSpider;
    
    // UI 线程 Handler，用于从后台线程回调到主线程
    private final Handler mMainHandler;

    // 后台任务管理器
    private final BackgroundTaskManager mBackgroundTaskManager;
    // 当前下载任务的后台任务ID
    @Nullable
    private String mCurrentDownloadTaskId;

    private final ConcurrentPool<NotifyTask> mNotifyTaskPool = new ConcurrentPool<>(5);
    
    // 下载日志记录器
    private final DownloadLogger mDownloadLogger = DownloadLogger.getInstance();
    private final Object mEnsureLock = new Object();
    private int mEnsurePendingCount = 0;
    private boolean mEnsureRunning = false;

    public DownloadManager(Context context) {
        mContext = context;
        
        // 初始化下载日志记录器
        DownloadLogger.initialize(context);
        
        // 初始化后台任务管理器
        mBackgroundTaskManager = BackgroundTaskManager.getInstance();

        // 初始化主线程 Handler
        mMainHandler = new Handler(Looper.getMainLooper());

        // Get all labels
        List<DownloadLabel> labels = EhDB.getAllDownloadLabelList();
        mLabelList = labels;

        // Create list for each label
        HashMap<String, LinkedList<DownloadInfo>> map = new HashMap<>();
        mMap = map;
        for (DownloadLabel label : labels) {
            map.put(label.getLabel(), new LinkedList<>());
        }

        // Create default for non tag
        mDefaultInfoList = new LinkedList<>();

        // Get all info
        List<DownloadInfo> allInfoList = EhDB.getAllDownloadInfo();
        mAllInfoList = new LinkedList<>(allInfoList);

        // Create all info map
        SparseJLArray<DownloadInfo> allInfoMap = new SparseJLArray<>(allInfoList.size() + 10);
        mAllInfoMap = allInfoMap;

        for (int i = 0, n = allInfoList.size(); i < n; i++) {
            DownloadInfo info = allInfoList.get(i);

            if (info.archiveUri != null && info.archiveUri.startsWith("content://")) {
                try {
                    Uri uri = Uri.parse(info.archiveUri);
                    mContext.getContentResolver().takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception e) {
                    // Permission might already be taken or URI might be invalid
                    Log.w("DownloadManager", "Failed to restore URI permission for " + info.archiveUri, e);
                }
            }

            // Add to all info map
            allInfoMap.put(info.gid, info);

            // Add to each label list
            LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list == null) {
                // Can't find the label in label list
                list = new LinkedList<>();
                map.put(info.label, list);
                if (!containLabel(info.label)) {
                    // Add label to DB and list
                    labels.add(EhDB.addDownloadLabel(info.label));
                }
            }
            list.add(info);
        }

        mLabelCountMap = new HashMap<>();

        for (Map.Entry<String, LinkedList<DownloadInfo>> entry : map.entrySet()) {
            mLabelCountMap.put(entry.getKey(), (long) entry.getValue().size());
        }

        mWaitList = new LinkedList<>();
        mCopyList = new LinkedList<>();
        mSpeedReminder = new SpeedReminder();
        mDownloadInfoListeners = new ArrayList<>();
    }

    public void replaceInfo(DownloadInfo newInfo, DownloadInfo oldInfo) {

        for (int i = 0; i < mAllInfoList.size(); i++) {
            if (oldInfo.gid == mAllInfoList.get(i).gid) {
                mAllInfoList.set(i, newInfo);
                break;
            }
        }
        final List<DownloadInfo> infoList = getInfoListForLabel(oldInfo.label);
        if (infoList != null) {
            for (int i = 0; i < infoList.size(); i++) {
                if (oldInfo.gid == infoList.get(i).gid) {
                    infoList.set(i, newInfo);
                    break;
                }
            }
        }

        mAllInfoMap.remove(oldInfo.gid);
        mAllInfoMap.put(newInfo.gid, newInfo);


        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onReplace(newInfo, oldInfo);
        }
    }

    @Nullable
    private LinkedList<DownloadInfo> getInfoListForLabel(String label) {
        if (label == null) {
            return mDefaultInfoList;
        } else {
            return mMap.get(label);
        }
    }

    private void notifyRemove(final DownloadInfo info, final List<DownloadInfo> list, final int index) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onRemove(info, list, index);
            }
        } else {
            mMainHandler.post(() -> {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onRemove(info, list, index);
                }
            });
        }
    }

    private void notifyReload() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onReload();
            }
        } else {
            mMainHandler.post(() -> {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onReload();
                }
            });
        }
    }

    public boolean containLabel(String label) {
        if (label == null) {
            return false;
        }

        for (DownloadLabel raw : mLabelList) {
            if (label.equals(raw.getLabel())) {
                return true;
            }
        }

        return false;
    }

    public boolean containDownloadInfo(long gid) {
        return mAllInfoMap.indexOfKey(gid) >= 0;
    }

    @NonNull
    public List<DownloadLabel> getLabelList() {
        return mLabelList;
    }

    @Nullable
    public long getLabelCount(String label) {
        try {
            if (mLabelCountMap.containsKey(label)) {
                return mLabelCountMap.get(label);
            } else {
                return 0;
            }
        } catch (NullPointerException e) {
            Analytics.recordException(e);
            return 0;
        }
    }

    public List<DownloadInfo> getAllDownloadInfoList() {
        return mAllInfoList;
    }

    @NonNull
    public List<DownloadInfo> getDefaultDownloadInfoList() {
        return mDefaultInfoList;
//        List<DownloadInfo> infoList = new ArrayList<>();
//        int i = 0;
//        while (infoList.size() < 30000) {
//            if (i == mDefaultInfoList.size()) {
//                i = 0;
//            }
//            infoList.add(mDefaultInfoList.get(i));
//            i++;
//        }
//        return infoList;
    }

    @Nullable
    public List<DownloadInfo> getLabelDownloadInfoList(String label) {
        return mMap.get(label);
    }

    public List<GalleryInfo> getDownloadInfoList() {
        return new ArrayList<>(mAllInfoList);
    }

    @Nullable
    public DownloadInfo getDownloadInfo(long gid) {
        return mAllInfoMap.get(gid);
    }

    @Nullable
    public DownloadInfo getNoneDownloadInfo(long gid) {
        if (mCurrentTask != null && mCurrentTask.gid == gid) {
            // Stop current
            stopCurrentDownloadInternal();
        } else {
            // Remove wait
            for (Iterator<DownloadInfo> iterator = mWaitList.iterator(); iterator.hasNext(); ) {
                DownloadInfo info = iterator.next();
                if (info.gid == gid) {
                    info.state = DownloadInfo.STATE_NONE;
                    // Remove from wait list
                    iterator.remove();
                    break;
                }
            }
        }
        return mAllInfoMap.get(gid);
    }

    public int getDownloadState(long gid) {
        DownloadInfo info = mAllInfoMap.get(gid);
        if (null != info) {
            return info.state;
        } else {
            return DownloadInfo.STATE_INVALID;
        }
    }

    public void addDownloadInfoListener(@Nullable DownloadInfoListener downloadInfoListener) {
        mDownloadInfoListeners.add(downloadInfoListener);
    }

    public void removeDownloadInfoListener(@Nullable DownloadInfoListener downloadInfoListener) {
        mDownloadInfoListeners.remove(downloadInfoListener);
    }

    public void setDownloadListener(@Nullable DownloadListener listener) {
        mDownloadListener = listener;
    }

    private void moveToRecycleBin(DownloadInfo info) {
        if (info == null) {
            return;
        }
        try {
            GalleryInfo galleryInfo = new GalleryInfo();
            galleryInfo.gid = info.gid;
            galleryInfo.title = info.title;
            galleryInfo.thumb = info.thumb;
            galleryInfo.uploader = info.uploader;
            galleryInfo.category = info.category;
            galleryInfo.rating = info.rating;

            UniFile galleryDir = SpiderDen.getGalleryDownloadDir(galleryInfo);
            if (galleryDir != null && galleryDir.exists() && galleryDir.getUri() != null) {
                String path = galleryDir.getUri().getPath();
                if (path != null) {
                    LocalGalleryInfo localGalleryInfo = new LocalGalleryInfo(path);
                    LocalGalleryManager.getInstance(mContext).deleteGallery(localGalleryInfo);
                    Log.d(TAG, "Moved download to recycle bin: " + info.title + " (" + path + ")");
                } else {
                    Log.w(TAG, "Could not resolve path from galleryDir URI: " + galleryDir.getUri());
                }
            } else {
                Log.w(TAG, "No local folder to move for download: " + info.title);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to move download to recycle bin", e);
        }
    }

    private void ensureDownload() {
        if (mCurrentTask != null) {
            // Only one download
            Log.d(TAG, "[ENSURE] 已有下载任务在运行: " + mCurrentTask.title);
            return;
        }

        if (mWaitList.isEmpty()) {
            return;
        }

        // 确保在主线程获取SpiderQueen
        SimpleHandler.getInstance().post(() -> {
            if (mCurrentTask != null) {
                return;
            }
            if (mWaitList.isEmpty()) {
                return;
            }

            DownloadInfo info = mWaitList.removeFirst();
            String galleryTitle = EhUtils.getSuitableTitle(info);
            Log.d(TAG, "[ENSURE] 从等待列表获取任务: " + galleryTitle + " (等待列表剩余: " + mWaitList.size() + ")");

            try {
                SpiderQueen spider = SpiderQueen.obtainSpiderQueen(mContext, info, SpiderQueen.MODE_DOWNLOAD);
                synchronized (DownloadManager.this) {
                    mCurrentTask = info;
                    mCurrentSpider = spider;
                    spider.addOnSpiderListener(this);
                    info.state = DownloadInfo.STATE_DOWNLOAD;
                    info.speed = -1;
                    info.remaining = -1;
                    info.total = -1;
                    info.finished = 0;
                    info.downloaded = 0;
                    info.legacy = -1;
                    info.time = System.currentTimeMillis(); // 设置下载开始时间

                    Log.d(TAG, "[ENSURE] 初始化下载状态: " + galleryTitle);

                    // Update in DB
                    EhDB.putDownloadInfo(info);
                    Log.d(TAG, "[ENSURE] 更新下载状态到数据库: " + galleryTitle);

                    // SpiderQueen will start automatically when obtained in download mode
                    Log.d(TAG, "[ENSURE] SpiderQueen已准备就绪: " + galleryTitle);
                }
            } catch (Exception e) {
                Log.e(TAG, "[ENSURE] 获取SpiderQueen失败: " + galleryTitle, e);
                // 恢复到等待状态
                info.state = DownloadInfo.STATE_WAIT;
                EhDB.putDownloadInfo(info);
                mWaitList.addFirst(info);
                return;
            }

            // Start speed count
            mSpeedReminder.start();
            Log.d(TAG, "[ENSURE] 开始速度计数: " + galleryTitle);

            // 创建后台任务
            String taskName = "下载: " + galleryTitle;
            String taskDescription = "正在下载 " + galleryTitle;
            mCurrentDownloadTaskId = mBackgroundTaskManager.getTaskStatusManager().addTask(
                taskName,
                taskDescription,
                null,
                com.hippo.ehviewer.task.BackgroundTask.TaskType.DOWNLOAD,
                false
            );
            Log.d(TAG, "[ENSURE] 创建后台任务: " + mCurrentDownloadTaskId);

            // Notify start downloading
            if (mDownloadListener != null) {
                mDownloadListener.onStart(info);
                Log.d(TAG, "[ENSURE] 通知下载监听器开始下载: " + galleryTitle);
            }

            // Notify state update
            List<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onUpdate(info, list, mWaitList);
                }
            }
        });
    }

    private boolean isLocalGalleryAvailable(long gid) {
        try {
            DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(gid);
            if (checkResult != null) {
                Log.d(TAG, "[CHECK] 本地画廊检测（GID=" + gid + "): total=" + checkResult.totalFiles + ", valid=" + checkResult.validFiles + ", missing=" + checkResult.missingFiles + ", invalid=" + checkResult.invalidFiles);
                return checkResult.totalFiles > 0;
            } else {
                Log.d(TAG, "[CHECK] 本地画廊检测（GID=" + gid + "): 无记录");
                return false;
            }
        } catch (Exception e) {
            Log.w(TAG, "[CHECK] 检测本地画廊是否存在失败: " + gid, e);
            return false;
        }
    }

    private void enqueueCopyInfo(DownloadInfo info) {
        synchronized (mCopyLock) {
            mCopyList.addLast(info);
        }

        // 通知监听器
        List<DownloadInfo> list = getInfoListForLabel(info.label);
        if (list != null) {
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onAdd(info, list, list.size() - 1);
            }
        }

        requestEnsureCopy();
    }

    private void requestEnsureCopy() {
        synchronized (mCopyLock) {
            if (mCopyRunning) {
                return;
            }
            mCopyRunning = true;
        }

        ExecutorManager.getBackgroundExecutor().execute(this::runEnsureCopyLoop);
    }

    private void runEnsureCopyLoop() {
        while (true) {
            DownloadInfo info;
            synchronized (mCopyLock) {
                if (mCopyList.isEmpty()) {
                    mCopyRunning = false;
                    return;
                }
                info = mCopyList.removeFirst();
            }

            processCopyInfo(info);
        }
    }

    private void processCopyInfo(DownloadInfo info) {
        try {
            Log.i(TAG, "[COPY] 开始复制任务: " + EhUtils.getSuitableTitle(info) + " (GID: " + info.gid + ")");
            info.state = DownloadInfo.STATE_DOWNLOAD;
            info.networkCount = 0;
            // 实际复制逻辑：这里只用本地数据库文件信息模拟把本地存在图片标为已下载
            DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(info.gid);
            if (checkResult != null) {
                info.total = checkResult.totalFiles;
                info.copyCount = checkResult.validFiles;
                info.finished = checkResult.validFiles;
                info.downloaded = checkResult.validFiles;
                info.legacy = info.total - info.finished;
            }
            if (info.legacy <= 0) {
                info.state = DownloadInfo.STATE_FINISH;
            } else {
                // 未全部复制完仍然视为失败或等待后续下载（本实现简单标记为失败）
                info.state = DownloadInfo.STATE_FAILED;
            }
            EhDB.putDownloadInfo(info);

            List<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onUpdate(info, list, mWaitList);
                }
            }

            if (mDownloadListener != null) {
                if (info.state == DownloadInfo.STATE_FINISH) {
                    mDownloadListener.onFinish(info);
                } else if (info.state == DownloadInfo.STATE_FAILED) {
                    mDownloadListener.onCancel(info);
                }
            }

            Log.i(TAG, "[COPY] 复制任务完成: " + EhUtils.getSuitableTitle(info) + " (状态: " + info.state + ")");
        } catch (Exception e) {
            Log.e(TAG, "[COPY] 复制任务失败: " + EhUtils.getSuitableTitle(info), e);
        }
    }

    private void requestEnsureDownload() {
        synchronized (mEnsureLock) {
            mEnsurePendingCount++;
            if (mEnsureRunning) {
                return;
            }
            mEnsureRunning = true;
        }

        ExecutorManager.getBackgroundExecutor().execute(this::runEnsureDownloadLoop);
    }

    private void runEnsureDownloadLoop() {
        while (true) {
            synchronized (mEnsureLock) {
                if (mEnsurePendingCount == 0) {
                    mEnsureRunning = false;
                    return;
                }
                mEnsurePendingCount--;
            }

            try {
                ensureDownload();
            } catch (Exception e) {
                Log.e(TAG, "[ENSURE] 处理等待下载任务时出错", e);
            }
        }
    }

    void startDownload(GalleryInfo galleryInfo, @Nullable String label) {
        String galleryTitle = EhUtils.getSuitableTitle(galleryInfo);
        Log.d(TAG, "[START] 开始启动下载: " + galleryTitle + " (GID: " + galleryInfo.gid + ", 标签: " + label + ")");
        
        // 记录下载开始日志
        mDownloadLogger.logDownloadStart(String.valueOf(galleryInfo.gid), galleryTitle, galleryInfo.pages);
        
        if (mCurrentTask != null && mCurrentTask.gid == galleryInfo.gid) {
            Log.d(TAG, "[START] 下载任务已是当前任务，跳过: " + galleryTitle);
            // It is current task
            return;
        }

        // Do nothing in the case of a local compressed file.
        if (galleryInfo instanceof DownloadInfo downloadInfo) {
            if (downloadInfo.archiveUri != null && downloadInfo.archiveUri.startsWith("content://")){
                Log.d(TAG, "[START] 本地压缩文件，跳过下载: " + galleryTitle);
                return;
            }
        }

        // Check in download list
        DownloadInfo info = mAllInfoMap.get(galleryInfo.gid);

        if (info != null) { // Get it in download list
            Log.d(TAG, "[START] 在下载列表中找到任务: " + galleryTitle + " (当前状态: " + info.state + ")");
            Log.i(TAG, "[START] 发现已有记录，检测增量/本地复制条件：incremental=" + Settings.getIncrementalDownloadUpdate() + "，localAvailable=" + isLocalGalleryAvailable(galleryInfo.gid));
            
            // 先设置为等待状态，再进行其他检测
            if (info.state != DownloadInfo.STATE_WAIT && info.state != DownloadInfo.STATE_DOWNLOAD) {
                // Set state DownloadInfo.STATE_WAIT
                info.state = DownloadInfo.STATE_WAIT;
                Log.d(TAG, "[START] 设置状态为等待: " + galleryTitle);
                // Add to wait list
                mWaitList.add(info);

                // Apply queue order if needed
                int downloadQueueOrder = getDownloadQueueOrderSafely();
                if (downloadQueueOrder != Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT) {
                    sortDownloadList(mWaitList, downloadQueueOrder);
                }

                // Update in DB
                EhDB.putDownloadInfo(info);
                Log.d(TAG, "[START] 更新数据库状态: " + galleryTitle);
                // Notify state update
                List<DownloadInfo> list = getInfoListForLabel(info.label);
                if (list != null) {
                    for (DownloadInfoListener l : mDownloadInfoListeners) {
                        l.onUpdate(info, list, mWaitList);
                    }
                }
            } else {
                Log.d(TAG, "[START] 任务已在等待队列中或正在下载: " + galleryTitle);
            }
        } else {
            Log.d(TAG, "[START] 创建新的下载任务: " + galleryTitle);
            
            // 判断是否启用增量下载，根据本地数据库ID筛选复制队列
            info = new DownloadInfo(galleryInfo);
            info.label = label;
            info.time = System.currentTimeMillis();

            if (Settings.getIncrementalDownloadUpdate() && isLocalGalleryAvailable(galleryInfo.gid)) {
                DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(galleryInfo.gid);
            Log.i(TAG, "[START] 本地库命中，加入复制队列: " + galleryTitle + " (GID: " + galleryInfo.gid + ")，本地文件情况：" +
                    (checkResult == null ? "null" : "total=" + checkResult.totalFiles + ", valid=" + checkResult.validFiles + ", missing=" + checkResult.missingFiles + ", invalid=" + checkResult.invalidFiles));
            info.incremental = true;

            if (checkResult != null) {
                info.copyCount = checkResult.validFiles;
                info.networkCount = 0;
                info.total = checkResult.totalFiles;
                info.finished = checkResult.validFiles;
                info.downloaded = checkResult.validFiles;
                info.legacy = info.total - info.finished;
            }

                info.state = DownloadInfo.STATE_WAIT;
                // 插入标签、全局和默认列表
                LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
                if (list == null) {
                    list = new LinkedList<>();
                    mMap.put(info.label, list);
                }
                list.addFirst(info);

                mAllInfoList.addFirst(info);
                mAllInfoMap.put(galleryInfo.gid, info);
                mDefaultInfoList.addFirst(info);

                if (!mLabelCountMap.containsKey(info.label)) {
                    mLabelCountMap.put(info.label, 1L);
                } else {
                    mLabelCountMap.put(info.label, mLabelCountMap.get(info.label) + 1);
                }

                enqueueCopyInfo(info);

                // Db save and notify will be在enqueueCopyInfo中处理
                return;
            }

            // 普通下载
            info.incremental = false;
            info.state = DownloadInfo.STATE_WAIT;

            // Add to label download list
            LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list == null) {
                Log.e(TAG, "[START] 无法找到标签对应的下载列表: " + label);
                return;
            }
            list.addFirst(info);

            // Add to all download list and map
            mAllInfoList.addFirst(info);
            mAllInfoMap.put(galleryInfo.gid, info);

            // Add to wait list
            mWaitList.add(info);
            
            // Apply queue order if needed
            int downloadQueueOrder = getDownloadQueueOrderSafely();
            if (downloadQueueOrder != Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT) {
                sortDownloadList(mWaitList, downloadQueueOrder);
            }

            // Save to
            EhDB.putDownloadInfo(info);
            Log.d(TAG, "[START] 保存新下载任务到数据库: " + galleryTitle);

            // Notify
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onAdd(info, list, list.size() - 1);
            }
            // Make sure download is running
            requestEnsureDownload();
            Log.d(TAG, "[START] 确保新任务下载正在运行: " + galleryTitle);
            // Add it to history
            EhDB.putHistoryInfo(info);
            Log.d(TAG, "[START] 添加到历史记录: " + galleryTitle);
        }
        
        Log.i(TAG, "[START] 下载启动完成: " + galleryTitle);
    }

    public void startRangeDownload(LongList gidList) {
        boolean update = false;
        boolean downloadOrder = Settings.getDownloadOrder();
        
        // 获取下载队列顺序设置
        int downloadQueueOrder = getDownloadQueueOrderSafely();
        
        // 检查是否启用增量下载更新
        boolean incrementalUpdateEnabled = Settings.getIncrementalDownloadUpdate();
        
        // 获取范围内的下载信息并排序
        List<DownloadInfo> rangeDownloadList = new ArrayList<>();
        List<DownloadInfo> waitOnlyList = new ArrayList<>();
        for (int i = 0, n = gidList.size(); i < n; i++) {
            long gid = gidList.get(i);
            DownloadInfo info = mAllInfoMap.get(gid);
            if (info != null) {
                if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                    rangeDownloadList.add(info);
                } else if (info.state == DownloadInfo.STATE_WAIT && !mWaitList.contains(info)) {
                    waitOnlyList.add(info);
                }
            }
        }
        
        // 应用队列顺序排序
        sortDownloadList(rangeDownloadList, downloadQueueOrder);
        
        // 处理范围内的下载项（无论downloadOrder如何，逻辑都是相同的）
        for (DownloadInfo info : rangeDownloadList) {
            if (processRangeDownloadItem(info, incrementalUpdateEnabled, "[RANGE]")) {
                update = true;
            }
        }

        if (!waitOnlyList.isEmpty()) {
            mWaitList.addAll(waitOnlyList);
            sortDownloadList(mWaitList, downloadQueueOrder);
            update = true;
        }

        if (update) {
            // Notify Listener
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onUpdateAll();
            }
        }

        // Ensure download
        requestEnsureDownload();
    }

    public void startAllDownload() {
        startAllDownload(null);
    }
    
    public void startAllDownload(final StartAllDownloadListener listener) {
        // 在后台线程执行耗时操作，使用统一的线程池管理
        ExecutorManager.getBackgroundExecutor().execute(() -> {
            try {
                Log.i(TAG, "[START_ALL] 开始批量启动下载任务");
                if (listener != null) {
                    SimpleHandler.getInstance().post(() -> listener.onStart());
                }
                
                boolean update = false;
                // Start all STATE_NONE and STATE_FAILED item
                LinkedList<DownloadInfo> allInfoList = mAllInfoList;
                LinkedList<DownloadInfo> waitList = mWaitList;
                
                // 获取下载队列顺序设置
                int downloadQueueOrder = getDownloadQueueOrderSafely();
                String queueOrderText = getQueueOrderText(downloadQueueOrder);
                
                // 获取正序倒序设置
                boolean downloadOrder = Settings.getDownloadOrder();
                String orderText = downloadOrder ? "正序" : "倒序";
                
                // 检查是否启用增量下载更新
                boolean incrementalUpdateEnabled = Settings.getIncrementalDownloadUpdate();
                
                int totalCount = 0;
                int eligibleCount = 0;
                
                // 计算非已完成项目数量作为进度总数，同时统计可启动项目数量
                for (DownloadInfo info : allInfoList) {
                    if (info.state != DownloadInfo.STATE_FINISH) {
                        totalCount++;
                    }
                    if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                        eligibleCount++;
                    }
                }
                
                Log.i(TAG, "[START_ALL] 批量启动下载任务 - 非已完成项目: " + totalCount + 
                          ", 可启动项目: " + eligibleCount +
                          ", 队列顺序: " + queueOrderText + 
                          ", 排序方式: " + orderText + 
                          ", 增量更新: " + (incrementalUpdateEnabled ? "启用" : "禁用"));

                // 根据队列顺序设置对下载列表进行排序
                List<DownloadInfo> sortedDownloadList = new ArrayList<>();
                for (DownloadInfo info : allInfoList) {
                    if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                        sortedDownloadList.add(info);
                    }
                }
                
                // 应用队列顺序排序
                sortDownloadList(sortedDownloadList, downloadQueueOrder);
                
                // 记录排序结果
                Log.d(TAG, "[START_ALL] 队列排序完成，排序前5项:");
                for (int i = 0; i < Math.min(5, sortedDownloadList.size()); i++) {
                    DownloadInfo info = sortedDownloadList.get(i);
                    Log.d(TAG, "[START_ALL]   [" + (i+1) + "] " + EhUtils.getSuitableTitle(info) + 
                              " (GID: " + info.gid + ", 页数: " + info.total + ", 状态: " + getStateString(info.state) + ")");
                }

                // 根据正序倒序设置决定遍历方向
                if (downloadOrder) {
                    Log.d(TAG, "[START_ALL] 使用正序处理下载列表");
                    // 正序：从前往后处理
                    update = processDownloadListInOrder(allInfoList, waitList, true, incrementalUpdateEnabled, 
                            listener, totalCount, eligibleCount, "[ALL-正序]");
                } else {
                    Log.d(TAG, "[START_ALL] 使用倒序处理下载列表");
                    // 倒序：从后往前处理
                    update = processDownloadListInOrder(allInfoList, waitList, false, incrementalUpdateEnabled, 
                            listener, totalCount, eligibleCount, "[ALL-倒序]");
                }

                final boolean finalUpdate = update;
                SimpleHandler.getInstance().post(() -> {
                    if (finalUpdate) {
                        // Notify Listener
                        for (DownloadInfoListener l : mDownloadInfoListeners) {
                            l.onUpdateAll();
                        }
                        // Ensure download
                        requestEnsureDownload();
                    }
                    
                    // 记录等待队列的最终状态
                    Log.d(TAG, "[START_ALL] 批量启动完成，等待队列状态:");
                    synchronized (mWaitList) {
                        for (int i = 0; i < Math.min(5, mWaitList.size()); i++) {
                            DownloadInfo info = mWaitList.get(i);
                            Log.d(TAG, "[START_ALL]   等待[" + (i+1) + "] " + EhUtils.getSuitableTitle(info) + 
                                      " (GID: " + info.gid + ", 页数: " + info.total + ")");
                        }
                        if (mWaitList.size() > 5) {
                            Log.d(TAG, "[START_ALL]   ... 等待队列中共有 " + mWaitList.size() + " 个任务");
                        }
                    }
                    
                    if (listener != null) {
                        // 重新计算实际处理的数量
                        int actualProcessedCount = 0;
                        for (DownloadInfo info : allInfoList) {
                            if (info.state == DownloadInfo.STATE_WAIT) {
                                actualProcessedCount++;
                            }
                        }
                        listener.onComplete(actualProcessedCount);
                    }
                });
                
                Log.i(TAG, "[START_ALL] 批量启动完成");
            } catch (Exception e) {
                Log.e(TAG, "[START_ALL] 批量启动出错", e);
                if (listener != null) {
                    SimpleHandler.getInstance().post(() -> listener.onError(e.getMessage()));
                }
            }
        });
    }

    public void addDownload(List<DownloadInfo> downloadInfoList) {
        // 检查当前是否有正在下载的任务，如果有，新任务应该进入等待状态而不是被重置为NONE
        boolean hasActiveDownloads = !mWaitList.isEmpty() || (mCurrentTask != null);
        
        for (DownloadInfo info : downloadInfoList) {
            if (containDownloadInfo(info.gid)) {
                // Contain
                continue;
            }

            // Ensure download state - 修复：如果有活跃下载，保持等待状态
            if (DownloadInfo.STATE_WAIT == info.state ||
                    DownloadInfo.STATE_DOWNLOAD == info.state) {
                if (hasActiveDownloads) {
                    // 如果有活跃下载，保持等待状态
                    info.state = DownloadInfo.STATE_WAIT;
                } else {
                    // 否则重置为NONE状态
                    info.state = DownloadInfo.STATE_NONE;
                }
            }

            // Add to label download list
            LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
            if (null == list) {
                // Can't find the label in label list
                list = new LinkedList<>();
                mMap.put(info.label, list);
                if (!containLabel(info.label)) {
                    // Add label to DB and list
                    mLabelList.add(EhDB.addDownloadLabel(info.label));
                }
            }
            list.add(info);
            // Sort
            Collections.sort(list, DATE_DESC_COMPARATOR);

            // Add to all download list and map
            mAllInfoList.add(info);
            mAllInfoMap.put(info.gid, info);

            // Save to
            EhDB.putDownloadInfo(info);
        }

        // Sort all download list
        Collections.sort(mAllInfoList, DATE_DESC_COMPARATOR);

        // 如果有等待状态的任务，添加到等待队列
        if (hasActiveDownloads) {
            for (DownloadInfo info : downloadInfoList) {
                if (info.state == DownloadInfo.STATE_WAIT && !mWaitList.contains(info)) {
                    mWaitList.add(info);
                }
            }
        }

        // Notify
        new Handler(Looper.getMainLooper()).post(() -> {
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onReload();
            }
        });
        
        // 确保下载继续
        requestEnsureDownload();
    }


    /**
     * 从文件系统读取下载信息
     * @param gid 画廊GID
     * @param title 画廊标题
     * @return DownloadInfo对象，如果读取失败返回null
     */
    @Nullable
    private DownloadInfo readDownloadInfoFromFileSystem(long gid, String title) {
        Log.d(TAG, "[READ_FS] 尝试从文件系统读取下载信息: GID " + gid + ", 标题: " + title);
        
        try {
            // 创建临时GalleryInfo对象
            GalleryInfo tempGalleryInfo = new GalleryInfo();
            tempGalleryInfo.gid = gid;
            tempGalleryInfo.title = title;
            
            // 获取下载目录
            UniFile downloadDir = SpiderDen.getGalleryDownloadDir(tempGalleryInfo);
            if (downloadDir == null || !downloadDir.exists()) {
                Log.w(TAG, "[READ_FS] 下载目录不存在: " + downloadDir);
                return null;
            }
            
            // 读取.ehviewer文件
            UniFile ehviewerFile = downloadDir.findFile(".ehviewer");
            if (ehviewerFile == null) {
                Log.w(TAG, "[READ_FS] 未找到.ehviewer文件");
                return null;
            }
            
            InputStream is = ehviewerFile.openInputStream();
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            is.close();
            
            String content = new String(buffer, StandardCharsets.UTF_8);
            Log.d(TAG, "[READ_FS] .ehviewer文件内容: " + content);
            
            // 解析下载信息
            DownloadInfo info = new DownloadInfo();
            info.gid = gid;
            info.title = title;
            info.time = System.currentTimeMillis();
            
            // 检查.ehviewer文件是否只包含VERSION2
            if (content.trim().equals("VERSION2")) {
                Log.w(TAG, "[READ_FS] .ehviewer文件只包含VERSION2，尝试从文件系统计算进度");
                
                // 尝试从文件系统计算实际进度
                UniFile[] files = downloadDir.listFiles();
                if (files != null) {
                    int imageCount = 0;
                    for (UniFile file : files) {
                        String fileName = file.getName();
                        if (fileName != null && !fileName.startsWith(".") && 
                            (fileName.endsWith(".jpg") || fileName.endsWith(".png") || 
                             fileName.endsWith(".gif") || fileName.endsWith(".webp"))) {
                            imageCount++;
                        }
                    }
                    
                    if (imageCount > 0) {
                        Log.d(TAG, "[READ_FS] 从文件系统计算得到图片数量: " + imageCount);
                        info.finished = imageCount;
                        info.downloaded = imageCount;
                        info.total = imageCount;
                        info.legacy = 0;
                        info.state = DownloadInfo.STATE_FINISH;
                        
                        Log.d(TAG, "[READ_FS] 基于文件系统设置进度信息: 完成=" + info.finished + 
                                  ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                        
                        return info;
                    } else {
                        Log.w(TAG, "[READ_FS] 下载目录中没有找到图片文件");
                        return null;
                    }
                } else {
                    Log.w(TAG, "[READ_FS] 无法列出下载目录中的文件");
                    return null;
                }
            }
            
            // 从文件内容中解析进度信息
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (line.startsWith("finished=")) {
                    try {
                        info.finished = Integer.parseInt(line.substring(9));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "[READ_FS] 解析finished失败: " + line);
                    }
                } else if (line.startsWith("downloaded=")) {
                    try {
                        info.downloaded = Integer.parseInt(line.substring(11));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "[READ_FS] 解析downloaded失败: " + line);
                    }
                } else if (line.startsWith("total=")) {
                    try {
                        info.total = Integer.parseInt(line.substring(6));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "[READ_FS] 解析total失败: " + line);
                    }
                } else if (line.startsWith("legacy=")) {
                    try {
                        info.legacy = Integer.parseInt(line.substring(8));
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "[READ_FS] 解析legacy失败: " + line);
                    }
                }
            }
            
            Log.d(TAG, "[READ_FS] 成功解析下载信息: 完成=" + info.finished + 
                      ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
            
            return info;
            
        } catch (Exception e) {
            Log.e(TAG, "[READ_FS] 从文件系统读取下载信息时发生异常", e);
            return null;
        }
    }
    
    /**
     * 获取状态字符串
     */
    private String getStateString(int state) {
        switch (state) {
            case DownloadInfo.STATE_NONE:
                return "未开始";
            case DownloadInfo.STATE_WAIT:
                return "等待中";
            case DownloadInfo.STATE_DOWNLOAD:
                return "下载中";
            case DownloadInfo.STATE_FAILED:
                return "失败";
            case DownloadInfo.STATE_FINISH:
                return "已完成";
            default:
                return "未知";
        }
    }

    public void addDownloadLabel(List<DownloadLabel> downloadLabelList) {
        for (DownloadLabel label : downloadLabelList) {
            String labelString = label.getLabel();
            if (!containLabel(labelString)) {
                mMap.put(labelString, new LinkedList<>());
                mLabelList.add(EhDB.addDownloadLabel(label));
            }
        }
    }

    public void addDownload(GalleryInfo galleryInfo, @Nullable String label, int state) {
        String galleryTitle = EhUtils.getSuitableTitle(galleryInfo);
        Log.d(TAG, "[DOWNLOAD] 开始添加下载任务: " + galleryTitle + " (GID: " + galleryInfo.gid + ")");

        if (containDownloadInfo(galleryInfo.gid)) {
            Log.d(TAG, "[DOWNLOAD] 下载任务已存在，跳过: " + galleryTitle);
            return;
        }

        DownloadInfo info = new DownloadInfo(galleryInfo);
        info.label = label;
        info.time = System.currentTimeMillis();

        // 判断增量下载（本地存在则进入复制队列）
        if (Settings.getIncrementalDownloadUpdate() && isLocalGalleryAvailable(galleryInfo.gid)) {
            Log.i(TAG, "[DOWNLOAD] 本地库命中，加入复制队列: " + galleryTitle + " (GID: " + galleryInfo.gid + ")");
            info.incremental = true;

            DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(galleryInfo.gid);
            if (checkResult != null) {
                info.copyCount = checkResult.validFiles;
                info.networkCount = 0;
                info.total = checkResult.totalFiles;
                info.finished = checkResult.validFiles;
                info.downloaded = checkResult.validFiles;
                info.legacy = info.total - info.finished;
            }

            info.state = DownloadInfo.STATE_WAIT;
            mDefaultInfoList.addFirst(info);
            mAllInfoList.addFirst(info);
            mAllInfoMap.put(galleryInfo.gid, info);
            enqueueCopyInfo(info);
            EhDB.putDownloadInfo(info);

            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onAdd(info, mDefaultInfoList, 0);
            }
            return;
        }

        // 普通下载流程
        info.incremental = false;
        info.state = state;

        LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
        if (!mLabelCountMap.containsKey(label)) {
            mLabelCountMap.put(label, 1L);
        } else {
            long value = mLabelCountMap.get(label) + 1L;
            mLabelCountMap.put(label, value);
        }
        if (list == null) {
            Log.e(TAG, "[DOWNLOAD] 无法找到标签对应的下载列表: " + label);
            return;
        }
        list.addFirst(info);

        // Add to all download list and map
        mAllInfoList.addFirst(info);
        mAllInfoMap.put(galleryInfo.gid, info);

        // Save to
        EhDB.putDownloadInfo(info);
        Log.d(TAG, "[DOWNLOAD] 下载信息已保存到数据库: " + galleryTitle);

        if (info.state == DownloadInfo.STATE_WAIT) {
            mWaitList.add(info);
        }

        // Notify
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onAdd(info, list, 0);
        }

        // 如果添加的是等待状态的任务，确保下载管理器继续处理
        if (info.state == DownloadInfo.STATE_WAIT) {
            requestEnsureDownload();
        }

        Log.i(TAG, "[DOWNLOAD] 下载任务添加完成: " + galleryTitle + " (标签: " + label + ", 状态: " + state + ")");

        // 如果添加的是等待状态的任务，确保下载管理器继续处理
        if (info.state == DownloadInfo.STATE_WAIT) {
            requestEnsureDownload();
        }

        Log.i(TAG, "[DOWNLOAD] 下载任务添加完成: " + galleryTitle + " (标签: " + label + ", 状态: " + state + ")");
    }

    public void addDownload(GalleryInfo galleryInfo, @Nullable String label) {
        addDownload(galleryInfo, label, DownloadInfo.STATE_NONE);
    }

    public void addDownloadInfo(GalleryInfo galleryInfo, @Nullable String label) {
        if (containDownloadInfo(galleryInfo.gid)) {
            // Contain
            return;
        }

        // It is new download info
        DownloadInfo info = new DownloadInfo(galleryInfo);
        info.label = label;
        info.state = DownloadInfo.STATE_NONE;
        if (info.time == 0) {
            info.time = System.currentTimeMillis();
        }

        // Add to label download list
        LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
        if (list == null) {
            Log.e(TAG, "Can't find download info list with label: " + label);
            return;
        }
        list.addFirst(info);

        // Save to
        EhDB.putDownloadInfo(info);
        mAllInfoMap.put(galleryInfo.gid, info);
    }


    public void stopDownload(long gid) {
        DownloadInfo info = stopDownloadInternal(gid);
        if (info != null) {
            // 记录下载停止日志
            String galleryTitle = EhUtils.getSuitableTitle(info);
            long totalTime = info.time > 0 ? System.currentTimeMillis() - info.time : 0;
            mDownloadLogger.logDownloadComplete(String.valueOf(gid), galleryTitle, 
                totalTime, info.finished, info.pages - info.finished);
            
            // Update listener
            List<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onUpdate(info, list, mWaitList);
                }
            }
            // Ensure download
            requestEnsureDownload();
        }
    }

    void stopCurrentDownload() {
        DownloadInfo info = stopCurrentDownloadInternal();
        if (info != null) {
            // Update listener
            List<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onUpdate(info, list, mWaitList);
                }
            }
            // Ensure download
            requestEnsureDownload();
        }
    }

    public void stopRangeDownload(LongList gidList) {
        stopRangeDownloadInternal(gidList);

        // Update listener
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onUpdateAll();
        }

        // Ensure download
        requestEnsureDownload();
    }

    public void stopAllDownload() {
        // Stop all in wait list
        for (DownloadInfo info : mWaitList) {
            info.state = DownloadInfo.STATE_NONE;
            // Update in DB
            EhDB.putDownloadInfo(info);
        }
        mWaitList.clear();

        // Stop current
        stopCurrentDownloadInternal();

        // Notify mDownloadInfoListener
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onUpdateAll();
        }
    }

    public void deleteDownload(long gid) {
        stopDownloadInternal(gid);
        DownloadInfo info = mAllInfoMap.get(gid);
        if (info != null) {
            // Move to recycle bin instead of direct deletion
            moveToRecycleBin(info);

            // Remove from DB
            EhDB.removeDownloadInfo(info.gid);

            // Remove all list and map
            mAllInfoList.remove(info);
            mAllInfoMap.remove(info.gid);

            // Remove label list
            LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                int index = list.indexOf(info);
                if (index >= 0) {
                    list.remove(info);
                    // Update listener
                    notifyRemove(info, list, index);
                }
            }

            // Ensure download
            requestEnsureDownload();
        }
    }

    public void deleteRangeDownload(LongList gidList) {
        stopRangeDownloadInternal(gidList);

        for (int i = 0, n = gidList.size(); i < n; i++) {
            long gid = gidList.get(i);
            DownloadInfo info = mAllInfoMap.get(gid);
            if (null == info) {
                Log.d(TAG, "Can't get download info with gid: " + gid);
                continue;
            }

            // Move to recycle bin instead of direct deletion
            moveToRecycleBin(info);

            // Remove from DB
            EhDB.removeDownloadInfo(info.gid);

            // Remove from all info map
            mAllInfoList.remove(info);
            mAllInfoMap.remove(info.gid);

            // Remove from label list
            LinkedList<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                list.remove(info);
            }
        }

        // Update listener
        notifyReload();

        // Ensure download
        requestEnsureDownload();
    }

    @SuppressLint("StaticFieldLeak")
    public void resetAllReadingProgress() {
        LinkedList<DownloadInfo> list = new LinkedList<>(mAllInfoList);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                GalleryInfo galleryInfo = new GalleryInfo();
                for (DownloadInfo downloadInfo : list) {
                    galleryInfo.gid = downloadInfo.gid;
                    galleryInfo.token = downloadInfo.token;
                    galleryInfo.title = downloadInfo.title;
                    galleryInfo.thumb = downloadInfo.thumb;
                    galleryInfo.category = downloadInfo.category;
                    galleryInfo.posted = downloadInfo.posted;
                    galleryInfo.uploader = downloadInfo.uploader;
                    galleryInfo.rating = downloadInfo.rating;

                    UniFile downloadDir = SpiderDen.getGalleryDownloadDir(galleryInfo);
                    if (downloadDir == null) {
                        continue;
                    }
                    UniFile file = downloadDir.findFile(".ehviewer");
                    if (file == null) {
                        continue;
                    }
                    SpiderInfo spiderInfo = SpiderInfo.read(file);
                    if (spiderInfo == null) {
                        continue;
                    }
                    spiderInfo.startPage = 0;

                    try {
                        spiderInfo.write(file.openOutputStream());
                    } catch (IOException e) {
                        Log.e(TAG, "Can't write SpiderInfo", e);
                    }
                }
                return null;
            }
        }.executeOnExecutor(IoThreadPoolExecutor.Companion.getInstance());
    }

    // Update in DB
    // Update listener
    // No ensureDownload
    private DownloadInfo stopDownloadInternal(long gid) {
        // Check current task
        if (mCurrentTask != null && mCurrentTask.gid == gid) {
            // Stop current
            return stopCurrentDownloadInternal();
        }

        for (Iterator<DownloadInfo> iterator = mWaitList.iterator(); iterator.hasNext(); ) {
            DownloadInfo info = iterator.next();
            if (info.gid == gid) {
                // Remove from wait list
                iterator.remove();
                // Update state
                info.state = DownloadInfo.STATE_NONE;
                // Update in DB
                EhDB.putDownloadInfo(info);
                return info;
            }
        }
        return null;
    }

    // Update in DB
    // Update mDownloadListener
    private DownloadInfo stopCurrentDownloadInternal() {
        DownloadInfo info = mCurrentTask;
        SpiderQueen spider = mCurrentSpider;
        // Release spider
        if (spider != null) {
            spider.removeOnSpiderListener(DownloadManager.this);
            SpiderQueen.releaseSpiderQueen(spider, SpiderQueen.MODE_DOWNLOAD);
        }
        mCurrentTask = null;
        mCurrentSpider = null;
        // Stop speed reminder
        mSpeedReminder.stop();
        
        // 结束后台任务
        if (mCurrentDownloadTaskId != null) {
            mBackgroundTaskManager.getTaskStatusManager().markTaskCancelled(mCurrentDownloadTaskId);
            Log.d(TAG, "[STOP] 标记后台任务取消: " + mCurrentDownloadTaskId);
            mCurrentDownloadTaskId = null;
        }
        
        if (info == null) {
            return null;
        }

        // Update state
        info.state = DownloadInfo.STATE_NONE;
        // Update in DB
        EhDB.putDownloadInfo(info);
        // Listener
        if (mDownloadListener != null) {
            mDownloadListener.onCancel(info);
        }
        return info;
    }

    // Update in DB
    // Update mDownloadListener
    private void stopRangeDownloadInternal(LongList gidList) {
        // Two way
        if (gidList.size() < mWaitList.size()) {
            for (int i = 0, n = gidList.size(); i < n; i++) {
                stopDownloadInternal(gidList.get(i));
            }
        } else {
            // Check current task
            if (mCurrentTask != null && gidList.contains(mCurrentTask.gid)) {
                // Stop current
                stopCurrentDownloadInternal();
            }

            // Check all in wait list
            for (Iterator<DownloadInfo> iterator = mWaitList.iterator(); iterator.hasNext(); ) {
                DownloadInfo info = iterator.next();
                if (gidList.contains(info.gid)) {
                    // Remove from wait list
                    iterator.remove();
                    // Update state
                    info.state = DownloadInfo.STATE_NONE;
                    // Update in DB
                    EhDB.putDownloadInfo(info);
                }
            }
        }
    }

    /**
     * @param label Not allow new label
     */
    public void changeLabel(List<DownloadInfo> list, String label) {
        if (null != label && !containLabel(label)) {
            Log.e(TAG, "Not exits label: " + label);
            return;
        }

        List<DownloadInfo> dstList = getInfoListForLabel(label);
        if (dstList == null) {
            Log.e(TAG, "Can't find label with label: " + label);
            return;
        }

        for (DownloadInfo info : list) {
            if (ObjectUtils.equal(info.label, label)) {
                continue;
            }

            List<DownloadInfo> srcList = getInfoListForLabel(info.label);
            if (srcList == null) {
                Log.e(TAG, "Can't find label with label: " + info.label);
                continue;
            }

            srcList.remove(info);
            dstList.add(info);
            info.label = label;
            Collections.sort(dstList, DATE_DESC_COMPARATOR);

            // Save to DB
            EhDB.putDownloadInfo(info);
        }

        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onReload();
        }
    }

    public void addLabel(String label) {
        if (label == null || containLabel(label)) {
            return;
        }

        mLabelList.add(EhDB.addDownloadLabel(label));
        mMap.put(label, new LinkedList<>());

        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onUpdateLabels();
        }
    }

    public void addLabelInSyncThread(String label) {
        if (label == null || containLabel(label)) {
            return;
        }

        mLabelList.add(EhDB.addDownloadLabel(label));
        mMap.put(label, new LinkedList<>());
    }

    public void moveLabel(int fromPosition, int toPosition) {
        final DownloadLabel item = mLabelList.remove(fromPosition);
        mLabelList.add(toPosition, item);
        EhDB.moveDownloadLabel(fromPosition, toPosition);

        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onUpdateLabels();
        }
    }

    public void renameLabel(@NonNull String from, @NonNull String to) {
        // Find in label list
        boolean found = false;
        for (DownloadLabel raw : mLabelList) {
            if (from.equals(raw.getLabel())) {
                found = true;
                raw.setLabel(to);
                // Update in DB
                EhDB.updateDownloadLabel(raw);
                break;
            }
        }
        if (!found) {
            return;
        }

        LinkedList<DownloadInfo> list = mMap.remove(from);
        if (list == null) {
            return;
        }

        // Update info label
        for (DownloadInfo info : list) {
            info.label = to;
            // Update in DB
            EhDB.putDownloadInfo(info);
        }
        // Put list back with new label
        mMap.put(to, list);

        // Notify listener
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onRenameLabel(from, to);
        }
    }

    public void deleteLabel(@NonNull String label) {
        // Find in label list and remove
        boolean found = false;
        for (Iterator<DownloadLabel> iterator = mLabelList.iterator(); iterator.hasNext(); ) {
            DownloadLabel raw = iterator.next();
            if (label.equals(raw.getLabel())) {
                found = true;
                iterator.remove();
                EhDB.removeDownloadLabel(raw);
                break;
            }
        }
        if (!found) {
            return;
        }

        LinkedList<DownloadInfo> list = mMap.remove(label);
        if (list == null) {
            return;
        }

        // Update info label
        for (DownloadInfo info : list) {
            info.label = null;
            // Update in DB
            EhDB.putDownloadInfo(info);
            mDefaultInfoList.add(info);
        }

        // Sort
        Collections.sort(mDefaultInfoList, DATE_DESC_COMPARATOR);

        // Notify listener
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onChange();
        }
    }

    boolean isIdle() {
        return mCurrentTask == null && mWaitList.isEmpty() && mCopyList.isEmpty();
    }

    /**
     * 是否有正在进行的下载任务（包括当前任务或等待/复制队列）
     */
    public boolean hasActiveDownload() {
        return mCurrentTask != null || !mWaitList.isEmpty() || !mCopyList.isEmpty();
    }

    @Override
    public void onGetPages(int pages) {
        if (mCurrentTask != null) {
            String galleryTitle = EhUtils.getSuitableTitle(mCurrentTask);
            Log.d(TAG, "[SPIDER] 获取到页数: " + pages + " - " + galleryTitle);
        }
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnGetPagesData(pages);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onGet509(int index) {
        if (mCurrentTask != null) {
            String galleryTitle = EhUtils.getSuitableTitle(mCurrentTask);
            Log.w(TAG, "[SPIDER] 获取到509错误，页码: " + index + " - " + galleryTitle);
        }
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnGet509Data(index);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onPageDownload(int index, long contentLength, long receivedSize, int bytesRead) {
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnPageDownloadData(index, contentLength, receivedSize, bytesRead);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onPageSuccess(int index, int finished, int downloaded, int total) {
        if (mCurrentTask != null) {
            String galleryTitle = EhUtils.getSuitableTitle(mCurrentTask);
            Log.d(TAG, "[SPIDER] 页面下载成功: " + index + "/" + total + " (" + finished + " 完成) - " + galleryTitle);
            
            // 记录下载进度日志
            int speed = mSpeedReminder != null ? mSpeedReminder.getSpeed() : 0;
            mDownloadLogger.logDownloadProgress(String.valueOf(mCurrentTask.gid), galleryTitle, 
                finished, total, speed);
            
            // 更新后台任务进度
            if (mCurrentDownloadTaskId != null) {
                mBackgroundTaskManager.getTaskStatusManager().updateTaskProgress(
                    mCurrentDownloadTaskId, finished, total, 
                    "正在下载第 " + index + " 页"
                );
            }
        }
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnPageSuccessData(index, finished, downloaded, total);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onPageFailure(int index, String error, int finished, int downloaded, int total) {
        if (mCurrentTask != null) {
            String galleryTitle = EhUtils.getSuitableTitle(mCurrentTask);
            Log.d(TAG, "[SPIDER] 页面下载失败: " + index + "/" + total + " (" + finished + " 完成) - " + galleryTitle + " - 错误: " + error);
            
            // 记录下载错误日志
            mDownloadLogger.logDownloadError(String.valueOf(mCurrentTask.gid), galleryTitle, 
                "页面 " + index + " 下载失败: " + error, null);
        }
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnPageFailureDate(index, error, finished, downloaded, total);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onFinish(int finished, int downloaded, int total) {
        if (mCurrentTask != null) {
            String galleryTitle = EhUtils.getSuitableTitle(mCurrentTask);
            Log.i(TAG, "[SPIDER] 下载完成: " + finished + "/" + total + " (" + downloaded + " 已下载) - " + galleryTitle);
            
            // 记录下载完成日志
            long totalTime = mCurrentTask.time > 0 ? System.currentTimeMillis() - mCurrentTask.time : 0;
            mDownloadLogger.logDownloadComplete(String.valueOf(mCurrentTask.gid), galleryTitle, 
                totalTime, finished, total - finished);
            
            // 保存画廊缓存
            saveGalleryCache(mCurrentTask);
        }
        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask();
        }
        task.setOnFinishDate(finished, downloaded, total);
        SimpleHandler.getInstance().post(task);
    }

    @Override
    public void onGetImageSuccess(int index, Image image) {
        // Ignore
    }

    @Override
    public void onGetImageFailure(int index, String error) {
        // Ignore
    }

    private class NotifyTask implements Runnable {

        public static final int TYPE_ON_GET_PAGES = 0;
        public static final int TYPE_ON_GET_509 = 1;
        public static final int TYPE_ON_PAGE_DOWNLOAD = 2;
        public static final int TYPE_ON_PAGE_SUCCESS = 3;
        public static final int TYPE_ON_PAGE_FAILURE = 4;
        public static final int TYPE_ON_FINISH = 5;

        private int mType;
        private int mPages;
        private int mIndex;
        private long mContentLength;
        private long mReceivedSize;
        private int mBytesRead;
        @SuppressWarnings("unused")
        private String mError;
        private int mFinished;
        private int mDownloaded;
        private int mTotal;

        public void setOnGetPagesData(int pages) {
            mType = TYPE_ON_GET_PAGES;
            mPages = pages;
        }

        public void setOnGet509Data(int index) {
            mType = TYPE_ON_GET_509;
            mIndex = index;
        }

        public void setOnPageDownloadData(int index, long contentLength, long receivedSize, int bytesRead) {
            mType = TYPE_ON_PAGE_DOWNLOAD;
            mIndex = index;
            mContentLength = contentLength;
            mReceivedSize = receivedSize;
            mBytesRead = bytesRead;
        }

        public void setOnPageSuccessData(int index, int finished, int downloaded, int total) {
            mType = TYPE_ON_PAGE_SUCCESS;
            mIndex = index;
            mFinished = finished;
            mDownloaded = downloaded;
            mTotal = total;
        }

        public void setOnPageFailureDate(int index, String error, int finished, int downloaded, int total) {
            mType = TYPE_ON_PAGE_FAILURE;
            mIndex = index;
            mError = error;
            mFinished = finished;
            mDownloaded = downloaded;
            mTotal = total;
        }

        public void setOnFinishDate(int finished, int downloaded, int total) {
            mType = TYPE_ON_FINISH;
            mFinished = finished;
            mDownloaded = downloaded;
            mTotal = total;
        }

        @Override
        public void run() {
            switch (mType) {
                case TYPE_ON_GET_PAGES: {
                    DownloadInfo info = mCurrentTask;
                    if (info == null) {
                        Log.e(TAG, "Current task is null, but it should not be");
                    } else {
                        info.total = mPages;
                        List<DownloadInfo> list = getInfoListForLabel(info.label);
                        if (list != null) {
                            for (DownloadInfoListener l : mDownloadInfoListeners) {
                                l.onUpdate(info, list, mWaitList);
                            }
                        }
                    }
                    break;
                }
                case TYPE_ON_GET_509: {
                    if (mDownloadListener != null) {
                        mDownloadListener.onGet509();
                    }
                    break;
                }
                case TYPE_ON_PAGE_DOWNLOAD: {
                    mSpeedReminder.onDownload(mIndex, mContentLength, mReceivedSize, mBytesRead);
                    break;
                }
                case TYPE_ON_PAGE_SUCCESS: {
                    mSpeedReminder.onDone(mIndex);
                    DownloadInfo info = mCurrentTask;
                    if (info == null) {
                        Log.e(TAG, "Current task is null, but it should not be");
                    } else {
                        info.finished = mFinished;
                        info.downloaded = mDownloaded;
                        info.networkCount = mDownloaded;
                        info.total = mTotal;
                        if (mDownloadListener != null) {
                            mDownloadListener.onGetPage(info);
                        }
                        List<DownloadInfo> list = getInfoListForLabel(info.label);
                        if (list != null) {
                            for (DownloadInfoListener l : mDownloadInfoListeners) {
                                l.onUpdate(info, list, mWaitList);
                            }
                        }
                    }
                    break;
                }
                case TYPE_ON_PAGE_FAILURE: {
                    mSpeedReminder.onDone(mIndex);
                    DownloadInfo info = mCurrentTask;
                    if (info == null) {
                        Log.e(TAG, "Current task is null, but it should not be");
                    } else {
                        info.finished = mFinished;
                        info.downloaded = mDownloaded;
                        info.total = mTotal;
                        List<DownloadInfo> list = getInfoListForLabel(info.label);
                        if (list != null) {
                            for (DownloadInfoListener l : mDownloadInfoListeners) {
                                l.onUpdate(info, list, mWaitList);
                            }
                        }
                    }
                    break;
                }
                case TYPE_ON_FINISH: {
                    mSpeedReminder.onFinish();
                    // Download done
                    DownloadInfo info = mCurrentTask;
                    mCurrentTask = null;
                    SpiderQueen spider = mCurrentSpider;
                    mCurrentSpider = null;
                    
                    String galleryTitle = info != null ? EhUtils.getSuitableTitle(info) : "未知画廊";
                    
                    // Release spider
                    if (spider != null) {
                        spider.removeOnSpiderListener(DownloadManager.this);
                        SpiderQueen.releaseSpiderQueen(spider, SpiderQueen.MODE_DOWNLOAD);
                        Log.d(TAG, "[FINISH] 释放SpiderQueen: " + galleryTitle);
                    }
                    // Check null
                    if (info == null || spider == null) {
                        Log.e(TAG, "[FINISH] 当前任务为空，但不应该为空");
                        break;
                    }
                    // Stop speed count
                    mSpeedReminder.stop();
                    // Update state
                    info.finished = mFinished;
                    info.downloaded = mDownloaded;
                    info.networkCount = mDownloaded;
                    info.total = mTotal;
                    info.legacy = mTotal - mFinished;
                    if (info.legacy == 0) {
                        info.state = DownloadInfo.STATE_FINISH;
                        Log.i(TAG, "[FINISH] 下载完成: " + galleryTitle + " (" + mFinished + "/" + mTotal + ")");
                    } else {
                        info.state = DownloadInfo.STATE_FAILED;
                        Log.w(TAG, "[FINISH] 下载失败，有未完成页面: " + galleryTitle + " (剩余: " + info.legacy + ")");
                    }
                    // Update in DB
                    EhDB.putDownloadInfo(info);
                    Log.d(TAG, "[FINISH] 更新下载状态到数据库: " + galleryTitle);
                    // Notify
                    if (mDownloadListener != null) {
                        mDownloadListener.onFinish(info);
                        Log.d(TAG, "[FINISH] 通知下载监听器: " + galleryTitle);
                    }
                    List<DownloadInfo> list = getInfoListForLabel(info.label);
                    if (list != null) {
                        for (DownloadInfoListener l : mDownloadInfoListeners) {
                            l.onUpdate(info, list, mWaitList);
                        }
                    }
                    
                    // 结束后台任务
                    if (mCurrentDownloadTaskId != null) {
                        if (info.legacy == 0) {
                            mBackgroundTaskManager.getTaskStatusManager().markTaskCompleted(mCurrentDownloadTaskId);
                            Log.d(TAG, "[FINISH] 标记后台任务完成: " + mCurrentDownloadTaskId);
                        } else {
                            mBackgroundTaskManager.getTaskStatusManager().markTaskError(mCurrentDownloadTaskId, 
                                "下载失败，剩余 " + info.legacy + " 页");
                            Log.d(TAG, "[FINISH] 标记后台任务失败: " + mCurrentDownloadTaskId);
                        }
                        mCurrentDownloadTaskId = null;
                    }
                    
                    // Start next download
                    requestEnsureDownload();
                    Log.d(TAG, "[FINISH] 确保下一个下载开始: " + galleryTitle);
                    break;
                }
            }

            mNotifyTaskPool.push(this);
        }
    }


    class SpeedReminder implements Runnable {

        private boolean mStop = true;

        private long mBytesRead;
        private long oldSpeed = -1;

        private final SparseIJArray mContentLengthMap = new SparseIJArray();
        private final SparseIJArray mReceivedSizeMap = new SparseIJArray();

        public void start() {
            if (mStop) {
                mStop = false;
                SimpleHandler.getInstance().post(this);
            }
        }

        public void stop() {
            if (!mStop) {
                mStop = true;
                mBytesRead = 0;
                oldSpeed = -1;
                mContentLengthMap.clear();
                mReceivedSizeMap.clear();
                SimpleHandler.getInstance().removeCallbacks(this);
            }
        }

        public void onDownload(int index, long contentLength, long receivedSize, int bytesRead) {
            mContentLengthMap.put(index, contentLength);
            mReceivedSizeMap.put(index, receivedSize);
            mBytesRead += bytesRead;
        }

        public void onDone(int index) {
            mContentLengthMap.delete(index);
            mReceivedSizeMap.delete(index);
        }

        public void onFinish() {
            mContentLengthMap.clear();
            mReceivedSizeMap.clear();
        }
        
        public int getSpeed() {
            return oldSpeed > 0 ? (int) (oldSpeed / 1024) : 0; // 返回KB/s
        }

        @Override
        public void run() {
            DownloadInfo info = mCurrentTask;
            if (info != null) {
                long newSpeed = mBytesRead / 2;
                if (oldSpeed != -1) {
                    newSpeed = (long) MathUtils.lerp(oldSpeed, newSpeed, 0.75f);
                }
                oldSpeed = newSpeed;
                info.speed = newSpeed;

                // Calculate remaining
                if (info.total <= 0) {
                    info.remaining = -1;
                } else if (newSpeed == 0) {
                    info.remaining = 300L * 24L * 60L * 60L * 1000L; // 300 days
                } else {
                    int downloadingCount = 0;
                    long downloadingContentLengthSum = 0;
                    long totalSize = 0;
                    for (int i = 0, n = Math.max(mContentLengthMap.size(), mReceivedSizeMap.size()); i < n; i++) {
                        long contentLength = mContentLengthMap.valueAt(i);
                        long receivedSize = mReceivedSizeMap.valueAt(i);
                        downloadingCount++;
                        downloadingContentLengthSum += contentLength;
                        totalSize += contentLength - receivedSize;
                    }
                    if (downloadingCount != 0) {
                        totalSize += downloadingContentLengthSum * (info.total - info.downloaded - downloadingCount) / downloadingCount;
                        info.remaining = totalSize / newSpeed * 1000;
                    }
                }
                if (mDownloadListener != null) {
                    mDownloadListener.onDownload(info);
                }
                List<DownloadInfo> list = getInfoListForLabel(info.label);
                if (list != null) {
                    for (DownloadInfoListener l : mDownloadInfoListeners) {
                        l.onUpdate(info, list, mWaitList);
                    }
                }
            }

            mBytesRead = 0;

            if (!mStop) {
                SimpleHandler.getInstance().postDelayed(this, 500);
            }
        }
    }

    private static final Comparator<DownloadInfo> DATE_DESC_COMPARATOR = new Comparator<>() {
        @Override
        public int compare(DownloadInfo lhs, DownloadInfo rhs) {
            long dif = lhs.time - rhs.time;
            if (dif > 0) {
                return -1;
            } else if (dif < 0) {
                return 1;
            } else {
                return 0;
            }
//            return  > 0 ? -1 : 1;
        }
    };

    public interface DownloadListener {

        /**
         * Get 509 error
         */
        void onGet509();

        /**
         * Start download
         */
        void onStart(DownloadInfo info);

        /**
         * Update download speed
         */
        void onDownload(DownloadInfo info);

        /**
         * Update page downloaded
         */
        void onGetPage(DownloadInfo info);

        /**
         * Download done
         */
        void onFinish(DownloadInfo info);

        /**
         * Download done
         */
        void onCancel(DownloadInfo info);
    }
    
    public interface StartAllDownloadListener {
        void onStart();
        void onProgress(int current, int total, String title);
        void onComplete(int totalStarted);
        void onError(String error);
    }

    /**
     * 当应用进入前台时调用，可以恢复下载优先级
     * 提高用户体验
     */
    public void onAppForeground() {
        Log.d(TAG, "应用进入前台，可以优化下载性能");
        if (mCurrentSpider != null) {
            try {
                // 允许系统优化线程优先级
                Process.setThreadPriority(Process.getThreadPriority(Process.THREAD_PRIORITY_BACKGROUND), Process.THREAD_PRIORITY_DEFAULT);
                Log.d(TAG, "前台下载线程优先级已优化");
            } catch (Exception e) {
                Log.w(TAG, "无法优化前台下载优先级", e);
            }
        }
    }

    /**
     * 当应用进入后台时调用，维持下载优先级以保证下载速度
     * 这很重要，否则后台下载可能会变得非常慢
     */
    public void onAppBackground() {
        Log.d(TAG, "应用进入后台，维持下载优先级");
        if (mCurrentSpider != null && mCurrentTask != null) {
            Log.d(TAG, "正在下载: " + mCurrentTask.title + ", 保持优先级避免速度下降");
            // Ensure foreground notification stays alive while app is backgrounded
            if (mDownloadListener != null) {
                mDownloadListener.onDownload(mCurrentTask);
            }
        }
    }

    /**
     * 检查画廊文件的完整性
     * @param gid 画廊GID
     * @return 文件检查结果
     */
    public DownloadedFileManager.GalleryFileCheckResult checkGalleryFilesIntegrity(long gid) {
        Log.d(TAG, "[INTEGRITY] 开始检查画廊文件完整性: GID " + gid);
        
        DownloadedFileManager manager = DownloadedFileManager.getInstance();
        DownloadedFileManager.GalleryFileCheckResult result = manager.checkGalleryFilesExist(gid);
        
        // 记录详细的检查结果
        if (result.isComplete()) {
            Log.i(TAG, "[INTEGRITY] 画廊文件完整性检查通过: GID " + gid + 
                      " (" + result.validFiles + "/" + result.totalFiles + " 文件完整)");
        } else {
            Log.w(TAG, "[INTEGRITY] 画廊文件完整性检查发现问题: GID " + gid + 
                      " - 总计: " + result.totalFiles + 
                      ", 存在: " + result.existingFiles + 
                      ", 有效: " + result.validFiles + 
                      ", 缺失: " + result.missingFiles + 
                      ", 损坏: " + result.invalidFiles);
            
            // 记录缺失的文件
            if (result.hasMissingFiles()) {
                Log.w(TAG, "[INTEGRITY] 缺失文件列表:");
                for (DownloadedFile file : result.missingFileList) {
                    Log.w(TAG, "[INTEGRITY]   - " + file.getFilename() + " (路径: " + file.getPath() + ")");
                }
            }
            
            // 记录损坏的文件
            if (result.hasInvalidFiles()) {
                Log.w(TAG, "[INTEGRITY] 损坏文件列表:");
                for (DownloadedFile file : result.invalidFileList) {
                    Log.w(TAG, "[INTEGRITY]   - " + file.getFilename() + " (路径: " + file.getPath() + ")");
                }
            }
        }
        
        return result;
    }
    
    /**
     * 尝试修复画廊的缺失文件
     * @param gid 画廊GID
     * @return 修复的文件数量
     */
    public int repairMissingFiles(long gid) {
        Log.d(TAG, "[REPAIR] 开始修复画廊缺失文件: GID " + gid);
        
        DownloadedFileManager manager = DownloadedFileManager.getInstance();
        DownloadedFileManager.GalleryFileCheckResult checkResult = manager.checkGalleryFilesExist(gid);
        
        if (!checkResult.hasMissingFiles()) {
            Log.d(TAG, "[REPAIR] 没有缺失文件需要修复: GID " + gid);
            return 0;
        }
        
        int repairedCount = 0;
        DownloadInfo downloadInfo = getDownloadInfo(gid);
        
        if (downloadInfo != null) {
            for (DownloadedFile missingFile : checkResult.missingFileList) {
                // 尝试从其他位置查找文件
                if (attemptFileRepair(missingFile, downloadInfo)) {
                    repairedCount++;
                    Log.i(TAG, "[REPAIR] 成功修复文件: " + missingFile.getFilename());
                } else {
                    Log.w(TAG, "[REPAIR] 无法修复文件: " + missingFile.getFilename());
                }
            }
        }
        
        Log.i(TAG, "[REPAIR] 修复完成: GID " + gid + ", 修复了 " + repairedCount + " 个文件");
        return repairedCount;
    }
    
    /**
     * 获取下载统计信息
     */
    public DownloadStatistics getDownloadStatistics() {
        DownloadStatistics stats = new DownloadStatistics();
        
        // 统计所有下载任务
        for (DownloadInfo info : mAllInfoList) {
            stats.totalGalleries++;
            
            switch (info.state) {
                case DownloadInfo.STATE_NONE:
                    stats.noneCount++;
                    break;
                case DownloadInfo.STATE_WAIT:
                    stats.waitCount++;
                    break;
                case DownloadInfo.STATE_DOWNLOAD:
                    stats.downloadCount++;
                    break;
                case DownloadInfo.STATE_FINISH:
                    stats.finishCount++;
                    break;
                case DownloadInfo.STATE_FAILED:
                    stats.failedCount++;
                    break;
            }
            
            // 统计文件数量
            if (info.total > 0) {
                stats.totalImages += info.total;
                stats.finishedImages += info.finished;
                stats.downloadedImages += info.downloaded;
            }
        }
        
        // 统计已下载文件
        DownloadedFileManager fileManager = DownloadedFileManager.getInstance();
        stats.downloadedFilesCount = fileManager.getTotalFilesCount();
        
        Log.d(TAG, "[STATS] 下载统计 - 总画廊: " + stats.totalGalleries + 
                  ", 完成: " + stats.finishCount + 
                  ", 下载中: " + stats.downloadCount + 
                  ", 等待: " + stats.waitCount + 
                  ", 失败: " + stats.failedCount + 
                  ", 总图片: " + stats.totalImages + 
                  ", 已完成图片: " + stats.finishedImages);
        
        return stats;
    }
    
    /**
     * 下载统计信息
     */
    public static class DownloadStatistics {
        public int totalGalleries = 0;
        public int noneCount = 0;
        public int waitCount = 0;
        public int downloadCount = 0;
        public int finishCount = 0;
        public int failedCount = 0;
        
        public int totalImages = 0;
        public int finishedImages = 0;
        public int downloadedImages = 0;
        public int downloadedFilesCount = 0;
        
        public double getCompletionRate() {
            return totalImages > 0 ? (double) finishedImages / totalImages : 0.0;
        }
        
        public int getActiveDownloads() {
            return downloadCount + waitCount;
        }
    }

    /**
     * 尝试修复单个文件
     */
    private boolean attemptFileRepair(DownloadedFile missingFile, DownloadInfo downloadInfo) {
        try {
            // 检查文件是否在其他位置存在
            java.io.File file = new java.io.File(missingFile.getPath());
            if (file.exists()) {
                Log.d(TAG, "[REPAIR] 文件实际存在，可能是数据库记录问题: " + missingFile.getFilename());
                return true;
            }
            
            // 可以在这里添加更多的修复逻辑，比如：
            // 1. 从备份目录恢复
            // 2. 从临时目录恢复
            // 3. 重新下载标记
            
            return false;
        } catch (Exception e) {
            Log.e(TAG, "[REPAIR] 修复文件时发生错误: " + missingFile.getFilename(), e);
            return false;
        }
    }

    /**
     * 获取队列顺序文本描述
     */
    private String getQueueOrderText(int order) {
        switch (order) {
            case Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT:
                return "默认顺序";
            case Settings.DOWNLOAD_QUEUE_ORDER_FEWEST_FIRST:
                return "少图优先";
            case Settings.DOWNLOAD_QUEUE_ORDER_MOST_FIRST:
                return "多图优先";
            default:
                return "未知";
        }
    }

    /**
     * 安全获取下载队列顺序设置，处理ClassCastException异常
     */
    private int getDownloadQueueOrderSafely() {
        try {
            return Settings.getDownloadQueueOrder();
        } catch (ClassCastException e) {
            Log.e(TAG, "获取下载队列顺序设置时发生类型转换异常，使用默认值", e);
            // 重置设置为默认值
            Settings.setDownloadQueueOrder(Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT);
            return Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT;
        }
    }

    /**
     * 保存画廊缓存信息
     */
    private void saveGalleryCache(@NonNull DownloadInfo downloadInfo) {
        try {
            // 获取画廊详细信息
            GalleryDetail galleryDetail = getGalleryDetailFromSpiderQueen(downloadInfo);
            if (galleryDetail != null) {
                GalleryCacheManager cacheManager = GalleryCacheManager.getInstance(mContext);
                boolean success = cacheManager.saveGalleryCache(galleryDetail);
                if (success) {
                    Log.d(TAG, "[CACHE] 画廊缓存保存成功: " + EhUtils.getSuitableTitle(downloadInfo));
                } else {
                    Log.w(TAG, "[CACHE] 画廊缓存保存失败: " + EhUtils.getSuitableTitle(downloadInfo));
                }
            } else {
                Log.w(TAG, "[CACHE] 无法获取画廊详细信息: " + EhUtils.getSuitableTitle(downloadInfo));
            }
        } catch (Exception e) {
            Log.e(TAG, "[CACHE] 保存画廊缓存时发生错误: " + EhUtils.getSuitableTitle(downloadInfo), e);
        }
    }

    /**
     * 从SpiderQueen获取画廊详细信息
     */
    @Nullable
    private GalleryDetail getGalleryDetailFromSpiderQueen(@NonNull DownloadInfo downloadInfo) {
        try {
            // 创建一个基本的GalleryDetail对象
            GalleryDetail galleryDetail = new GalleryDetail();
            
            // 复制基本信息
            galleryDetail.gid = downloadInfo.gid;
            galleryDetail.token = downloadInfo.token;
            galleryDetail.title = downloadInfo.title;
            galleryDetail.titleJpn = downloadInfo.titleJpn;
            galleryDetail.thumb = downloadInfo.thumb;
            galleryDetail.category = downloadInfo.category;
            galleryDetail.posted = downloadInfo.posted;
            galleryDetail.uploader = downloadInfo.uploader;
            galleryDetail.rating = downloadInfo.rating;
            galleryDetail.simpleLanguage = downloadInfo.simpleLanguage;
            galleryDetail.pages = downloadInfo.pages;
            
            // 包含标签信息 - 将simpleTags转换为GalleryTagGroup格式
            if (downloadInfo.simpleTags != null && downloadInfo.simpleTags.length > 0) {
                // 创建一个默认的标签组来包含所有simpleTags
                GalleryTagGroup tagGroup = new GalleryTagGroup();
                tagGroup.groupName = "tags";
                for (String tag : downloadInfo.simpleTags) {
                    tagGroup.addTag(tag);
                }
                galleryDetail.tags = new GalleryTagGroup[]{tagGroup};
            }
            
            // 尝试从SpiderQueen获取更多信息
            if (mCurrentSpider != null) {
                // SpiderInfo可能通过其他方式获取，这里暂时跳过
                // 可以根据需要添加更多字段
                galleryDetail.size = "Unknown";
                galleryDetail.language = "Unknown";
            }
            
            return galleryDetail;
        } catch (Exception e) {
            Log.e(TAG, "[CACHE] 从SpiderQueen获取画廊详细信息失败", e);
            return null;
        }
    }

    /**
     * 修复已下载文件的画廊信息
     * @param gid 画廊ID
     * @return 是否修复成功
     */
    public boolean repairGalleryInfo(long gid) {
        DownloadInfo downloadInfo = getDownloadInfo(gid);
        if (downloadInfo == null) {
            Log.w(TAG, "[REPAIR] 未找到下载信息: GID " + gid);
            return false;
        }
        
        String galleryTitle = EhUtils.getSuitableTitle(downloadInfo);
        Log.i(TAG, "[REPAIR] 开始修复画廊信息: " + galleryTitle + " (GID: " + gid + ")");
        
        try {
            // 获取最新的画廊详细信息
            GalleryDetail galleryDetail = fetchGalleryDetailFromNetwork(downloadInfo);
            if (galleryDetail == null) {
                Log.w(TAG, "[REPAIR] 无法从网络获取画廊信息: " + galleryTitle);
                return false;
            }
            
            // 保存到缓存
            GalleryCacheManager cacheManager = GalleryCacheManager.getInstance(mContext);
            boolean cacheSuccess = cacheManager.saveGalleryCache(galleryDetail);
            if (cacheSuccess) {
                Log.d(TAG, "[REPAIR] 画廊缓存保存成功: " + galleryTitle);
            } else {
                Log.w(TAG, "[REPAIR] 画廊缓存保存失败: " + galleryTitle);
            }
            
            // 更新下载信息
            downloadInfo.updateInfo(galleryDetail);
            EhDB.putDownloadInfo(downloadInfo);
            Log.d(TAG, "[REPAIR] 下载信息更新成功: " + galleryTitle);
            
            // 通知监听器
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onUpdate(downloadInfo, getInfoListForLabel(downloadInfo.label), mWaitList);
            }
            
            Log.i(TAG, "[REPAIR] 画廊信息修复完成: " + galleryTitle);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "[REPAIR] 修复画廊信息时发生错误: " + galleryTitle, e);
            return false;
        }
    }

    /**
     * 从网络获取画廊详细信息
     */
    @Nullable
    private GalleryDetail fetchGalleryDetailFromNetwork(@NonNull DownloadInfo downloadInfo) {
        try {
            String url = EhUrl.getGalleryDetailUrl(downloadInfo.gid, downloadInfo.token);
            Log.d(TAG, "[REPAIR] 从网络获取画廊信息: " + url);
            
            // 使用EhEngine获取画廊详情
            Context context = mContext;
            OkHttpClient okHttpClient = EhApplication.getOkHttpClient(context);
            GalleryDetail galleryDetail = EhEngine.getGalleryDetail(null, okHttpClient, url);
            
            if (galleryDetail == null) {
                Log.w(TAG, "[REPAIR] 无法从网络获取画廊信息: " + downloadInfo.title);
                return null;
            }
            
            Log.d(TAG, "[REPAIR] 成功获取画廊信息: " + galleryDetail.title);
            return galleryDetail;
            
        } catch (Throwable e) {
            Log.e(TAG, "[REPAIR] 从网络获取画廊信息时发生错误: " + EhUtils.getSuitableTitle(downloadInfo), e);
            
            // 如果是画廊已删除的错误，仍然返回部分信息
            if (e.getMessage() != null && 
                (e.getMessage().contains("Gallery Not Found") || e.getMessage().contains("404"))) {
                Log.d(TAG, "[REPAIR] 画廊已被删除，但保留基本信息: " + EhUtils.getSuitableTitle(downloadInfo));
                
                // 创建一个基本的GalleryDetail，标记为已删除
                GalleryDetail deletedDetail = new GalleryDetail();
                deletedDetail.gid = downloadInfo.gid;
                deletedDetail.token = downloadInfo.token;
                deletedDetail.title = downloadInfo.title;
                deletedDetail.titleJpn = downloadInfo.titleJpn;
                deletedDetail.thumb = downloadInfo.thumb;
                deletedDetail.category = downloadInfo.category;
                deletedDetail.posted = downloadInfo.posted;
                deletedDetail.uploader = downloadInfo.uploader;
                deletedDetail.rating = downloadInfo.rating;
                deletedDetail.simpleLanguage = downloadInfo.simpleLanguage;
                deletedDetail.pages = downloadInfo.pages;
                deletedDetail.visible = "deleted"; // 标记为已删除
                
                return deletedDetail;
            }
            
            return null;
        }
    }

    /**
     * 批量修复所有下载画廊的信息
     */
    public void repairAllGalleryInfo() {
        Log.i(TAG, "[REPAIR] 开始批量修复所有画廊信息");
        
        int successCount = 0;
        int failCount = 0;
        
        for (DownloadInfo downloadInfo : mAllInfoList) {
            if (repairGalleryInfo(downloadInfo.gid)) {
                successCount++;
            } else {
                failCount++;
            }
        }
        
        Log.i(TAG, "[REPAIR] 批量修复完成: 成功 " + successCount + " 个，失败 " + failCount + " 个");
    }

    /**
     * 处理单个下载项的通用逻辑
     * @param info 下载信息
     * @param incrementalUpdateEnabled 是否启用增量更新
     * @param logPrefix 日志前缀
     * @return 是否更新了下载项
     */
    private boolean processRangeDownloadItem(DownloadInfo info, boolean incrementalUpdateEnabled, String logPrefix) {
        if (null == info) {
            Log.d(TAG, logPrefix + " Can't get download info");
            return false;
        }

        if (info.state == DownloadInfo.STATE_NONE ||
                info.state == DownloadInfo.STATE_FAILED ||
                info.state == DownloadInfo.STATE_FINISH) {

            if (incrementalUpdateEnabled && isLocalGalleryAvailable(info.gid)) {
                // 本地存在走复制队列（增量模式）
                Log.i(TAG, logPrefix + " 本地库命中，加入复制队列: " + EhUtils.getSuitableTitle(info) + " (GID: " + info.gid + ")");
                info.incremental = true;

                DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(info.gid);
                if (checkResult != null) {
                    info.copyCount = checkResult.validFiles;
                    info.networkCount = 0;
                    info.total = checkResult.totalFiles;
                    info.finished = checkResult.validFiles;
                    info.downloaded = checkResult.validFiles;
                    info.legacy = info.total - info.finished;
                }

                info.state = DownloadInfo.STATE_WAIT;
                EhDB.putDownloadInfo(info);
                enqueueCopyInfo(info);
                return true;
            }

            // 普通下载队列
            info.incremental = false;
            info.state = DownloadInfo.STATE_WAIT;
            mWaitList.add(info);
            EhDB.putDownloadInfo(info);
            return true;
        }
        return false;
    }

    /**
     * 处理下载列表的通用方法（支持正序和倒序）
     * @param allInfoList 所有下载信息列表
     * @param waitList 等待列表
     * @param forwardOrder 是否为正序（true=正序，false=倒序）
     * @param incrementalUpdateEnabled 是否启用增量更新
     * @param listener 监听器
     * @param totalCount 总数量
     * @param eligibleCount 符合条件的数量
     * @param logPrefix 日志前缀
     * @return 是否有更新
     */
    private boolean processDownloadListInOrder(LinkedList<DownloadInfo> allInfoList, 
            LinkedList<DownloadInfo> waitList, boolean forwardOrder, 
            boolean incrementalUpdateEnabled, StartAllDownloadListener listener,
            int totalCount, int eligibleCount, String logPrefix) {
        
        boolean update = false;
        int allProcessedCount = 0; // 用于跟踪所有扫描项目的进度
        int processedCount = 0; // 符合条件的处理数量
        String orderText = forwardOrder ? "正序" : "倒序";
        
        if (forwardOrder) {
            // 正序：从前往后处理
            for (DownloadInfo info : allInfoList) {
                if (info.state != DownloadInfo.STATE_FINISH) {
                    allProcessedCount++;
                }
                final int currentAll = allProcessedCount;
                final int totalAll = totalCount;
                
                if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                    processedCount++;
                    final int current = processedCount;
                    
                    if (processSingleDownloadItem(info, waitList, incrementalUpdateEnabled, listener, 
                            current, eligibleCount, currentAll, totalAll, logPrefix + orderText, true)) {
                        update = true;
                    }
                } else if (info.state != DownloadInfo.STATE_FINISH && listener != null) {
                    final String title = EhUtils.getSuitableTitle(info);
                    SimpleHandler.getInstance().post(() -> listener.onProgress(currentAll, totalAll, title));
                }
            }
        } else {
            // 倒序：从后往前处理
            for (int i = allInfoList.size() - 1; i >= 0; i--) {
                DownloadInfo info = allInfoList.get(i);
                if (info.state != DownloadInfo.STATE_FINISH) {
                    allProcessedCount++;
                }
                final int currentAll = allProcessedCount;
                final int totalAll = totalCount;
                
                if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                    processedCount++;
                    final int current = processedCount;
                    
                    if (processSingleDownloadItem(info, waitList, incrementalUpdateEnabled, listener, 
                            current, eligibleCount, currentAll, totalAll, logPrefix + orderText, false)) {
                        update = true;
                    }
                } else if (info.state != DownloadInfo.STATE_FINISH && listener != null) {
                    final String title = EhUtils.getSuitableTitle(info);
                    SimpleHandler.getInstance().post(() -> listener.onProgress(currentAll, totalAll, title));
                }
            }
        }
        
        return update;
    }

    /**
     * 处理单个下载项的完整逻辑
     * @param info 下载信息
     * @param waitList 等待列表
     * @param incrementalUpdateEnabled 是否启用增量更新
     * @param listener 监听器
     * @param current 当前处理数量
     * @param eligibleCount 符合条件的数量
     * @param currentAll 当前扫描总数
     * @param totalAll 总扫描数量
     * @param logPrefix 日志前缀
     * @param addToLast 是否添加到列表末尾（true=末尾，false=开头）
     * @return 是否处理成功
     */
    private boolean processSingleDownloadItem(DownloadInfo info, LinkedList<DownloadInfo> waitList,
            boolean incrementalUpdateEnabled, StartAllDownloadListener listener,
            int current, int eligibleCount, int currentAll, int totalAll, 
            String logPrefix, boolean addToLast) {
        
        String galleryTitle = EhUtils.getSuitableTitle(info);
        Log.d(TAG, "[START_ALL] [" + current + "/" + eligibleCount + "] " + logPrefix + "处理: " + galleryTitle + 
                  " (GID: " + info.gid + ", 页数: " + info.total + ")");
        
        final String finalGalleryTitle = galleryTitle;
        if (listener != null) {
            SimpleHandler.getInstance().post(() -> 
                listener.onProgress(currentAll, totalAll, finalGalleryTitle));
        }
        
        // 检查是否需要增量更新（本地记录）
        if (incrementalUpdateEnabled && isLocalGalleryAvailable(info.gid)) {
            Log.i(TAG, logPrefix + " 本地库命中，加入复制队列: " + galleryTitle + " (GID: " + info.gid + ")");
            info.incremental = true;

            DownloadedFileManager.GalleryFileCheckResult checkResult = DownloadedFileManager.getInstance().checkGalleryFilesExist(info.gid);
            if (checkResult != null) {
                info.copyCount = checkResult.validFiles;
                info.networkCount = 0;
                info.total = checkResult.totalFiles;
                info.finished = checkResult.validFiles;
                info.downloaded = checkResult.validFiles;
                info.legacy = info.total - info.finished;
            }

            info.state = DownloadInfo.STATE_WAIT;
            EhDB.putDownloadInfo(info);
            enqueueCopyInfo(info);
            return true;
        }

        info.incremental = false;
        info.state = DownloadInfo.STATE_WAIT;
        synchronized (waitList) {
            if (addToLast) {
                waitList.addLast(info); // 正序时添加到末尾，保持顺序
            } else {
                waitList.addFirst(info); // 倒序时添加到开头，实现倒序效果
            }
        }
        EhDB.putDownloadInfo(info);
        
        Log.d(TAG, "[START_ALL] [" + current + "/" + eligibleCount + "] 已添加到等待队列: " + galleryTitle + 
                  " (扫描进度: " + currentAll + "/" + totalAll + ")");
        
        return true;
    }

    /**
     * 根据设置对下载列表进行排序
     */
    private void sortDownloadList(List<DownloadInfo> list, int order) {
        if (list == null || list.isEmpty()) {
            return;
        }
        
        switch (order) {
            case Settings.DOWNLOAD_QUEUE_ORDER_FEWEST_FIRST:
                // 少图画廊优先（按总页数排序）
                Collections.sort(list, (o1, o2) -> {
                    int pages1 = Math.max(o1.total, 0);
                    int pages2 = Math.max(o2.total, 0);
                    return Integer.compare(pages1, pages2);
                });
                break;
            case Settings.DOWNLOAD_QUEUE_ORDER_MOST_FIRST:
                // 多图画廊优先（按总页数排序）
                Collections.sort(list, (o1, o2) -> {
                    int pages1 = Math.max(o1.total, 0);
                    int pages2 = Math.max(o2.total, 0);
                    return Integer.compare(pages2, pages1);
                });
                break;
            case Settings.DOWNLOAD_QUEUE_ORDER_DEFAULT:
            default:
                // 默认按添加顺序，不需要排序
                break;
        }
    }

    /**
     * 在后台线程中执行增量更新检测（已废弃，具体逻辑移至 ProcessSingleDownloadItem/addDownload）
     */
    private void performIncrementalUpdateCheck(DownloadInfo info, GalleryInfo galleryInfo) {
        // no-op
        // 触发下载队列，保持兼容性
        requestEnsureDownload();
    }
}

