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
import com.hippo.ehviewer.DownloadedFileManager;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.DownloadLabel;
import com.hippo.ehviewer.dao.DownloadedFile;
import com.hippo.ehviewer.dao.GalleryVersionMap;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.lib.image.Image;
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
    // Store download info wait to start
    private final LinkedList<DownloadInfo> mWaitList;

    private final SpeedReminder mSpeedReminder;

    @Nullable
    private DownloadListener mDownloadListener;
    private final List<DownloadInfoListener> mDownloadInfoListeners;

    @Nullable
    private DownloadInfo mCurrentTask;
    @Nullable
    private SpiderQueen mCurrentSpider;

    private final ConcurrentPool<NotifyTask> mNotifyTaskPool = new ConcurrentPool<>(5);

    public DownloadManager(Context context) {
        mContext = context;

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

    private void ensureDownload() {
        if (mCurrentTask != null) {
            // Only one download
            Log.d(TAG, "[ENSURE] 已有下载任务在运行: " + mCurrentTask.title);
            return;
        }

        // Get download from wait list
        if (!mWaitList.isEmpty()) {
            DownloadInfo info = mWaitList.removeFirst();
            String galleryTitle = EhUtils.getSuitableTitle(info);
            Log.d(TAG, "[ENSURE] 从等待列表获取任务: " + galleryTitle + " (等待列表剩余: " + mWaitList.size() + ")");
            
            SpiderQueen spider = SpiderQueen.obtainSpiderQueen(mContext, info, SpiderQueen.MODE_DOWNLOAD);
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
            
            Log.d(TAG, "[ENSURE] 初始化下载状态: " + galleryTitle);
            
            // Update in DB
            EhDB.putDownloadInfo(info);
            Log.d(TAG, "[ENSURE] 更新下载状态到数据库: " + galleryTitle);
            
            // Start speed count
            mSpeedReminder.start();
            Log.d(TAG, "[ENSURE] 开始速度计数: " + galleryTitle);
            
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
        }
    }

    void startDownload(GalleryInfo galleryInfo, @Nullable String label) {
        String galleryTitle = EhUtils.getSuitableTitle(galleryInfo);
        Log.d(TAG, "[START] 开始启动下载: " + galleryTitle + " (GID: " + galleryInfo.gid + ", 标签: " + label + ")");
        
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
            
            // 检查是否需要增量更新
            if (Settings.getIncrementalDownloadUpdate()) {
                Log.d(TAG, "[START] 增量下载更新功能已启用，检查是否存在同名画廊");
                Long oldGid = findSameNameGallery(galleryInfo);
                if (oldGid != null && oldGid != galleryInfo.gid) {
                    Log.i(TAG, "[START] 发现同名画廊，准备进行增量更新: " + galleryTitle + 
                              " (旧GID: " + oldGid + ", 新GID: " + galleryInfo.gid + ")");
                    
                    // 显示增量更新提示
                    SimpleHandler.getInstance().post(() -> {
                        Toast.makeText(mContext, "检测到画廊更新，将保留已下载的进度", Toast.LENGTH_LONG).show();
                    });
                    
                    // 处理增量下载更新
                    handleIncrementalUpdate(galleryInfo, oldGid);
                    
                    // 更新当前下载信息，保留进度
                    DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                    if (oldDownloadInfo != null) {
                        // 保留旧画廊的进度信息
                        info.finished = oldDownloadInfo.finished;
                        info.downloaded = oldDownloadInfo.downloaded;
                        info.total = oldDownloadInfo.total;
                        info.legacy = oldDownloadInfo.legacy;
                        
                        // 在标题前添加增量更新标识
                        String originalTitle = EhUtils.getSuitableTitle(info);
                        info.title = "🔄 " + originalTitle;
                        
                        Log.d(TAG, "[START] 增量更新 - 保留的进度信息: 完成=" + info.finished + 
                                  ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                        
                        // 更新数据库
                        EhDB.putDownloadInfo(info);
                        Log.d(TAG, "[START] 更新增量更新信息到数据库: " + galleryTitle);
                        
                        // 通知界面更新
                        List<DownloadInfo> list = getInfoListForLabel(info.label);
                        if (list != null) {
                            for (DownloadInfoListener l : mDownloadInfoListeners) {
                                l.onUpdate(info, list, mWaitList);
                            }
                            Log.d(TAG, "[START] 已通知界面更新增量下载信息: " + galleryTitle);
                        }
                    }
                } else {
                    Log.d(TAG, "[START] 未发现需要更新的同名画廊: " + galleryTitle);
                }
            }
            
            if (info.state != DownloadInfo.STATE_WAIT) {
                // Set state DownloadInfo.STATE_WAIT
                info.state = DownloadInfo.STATE_WAIT;
                Log.d(TAG, "[START] 设置状态为等待: " + galleryTitle);
                // Add to wait list
                mWaitList.add(info);
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
                // Make sure download is running
                ensureDownload();
                Log.d(TAG, "[START] 确保下载正在运行: " + galleryTitle);
            } else {
                Log.d(TAG, "[START] 任务已在等待队列中: " + galleryTitle);
            }
        } else {
            Log.d(TAG, "[START] 创建新的下载任务: " + galleryTitle);
            
            // 检查是否需要增量更新
            DownloadInfo oldDownloadInfo = null;
            boolean isIncrementalUpdate = false;
            
            if (Settings.getIncrementalDownloadUpdate()) {
                Log.d(TAG, "[START] 增量下载更新功能已启用，检查是否存在同名画廊");
                Long oldGid = findSameNameGallery(galleryInfo);
                if (oldGid != null && oldGid != galleryInfo.gid) {
                    isIncrementalUpdate = true;
                    oldDownloadInfo = mAllInfoMap.get(oldGid);
                    
                    // 如果在下载管理器中找不到旧画廊信息，尝试从文件系统读取
                    if (oldDownloadInfo == null) {
                        Log.w(TAG, "[START] 在下载管理器中未找到旧画廊信息，尝试从文件系统读取: GID " + oldGid);
                        oldDownloadInfo = readDownloadInfoFromFileSystem(oldGid, galleryTitle);
                        if (oldDownloadInfo != null) {
                            Log.d(TAG, "[START] 从文件系统成功读取旧画廊信息: " + oldDownloadInfo.title);
                        } else {
                            Log.w(TAG, "[START] 无法从文件系统读取旧画廊信息");
                        }
                    }
                    
                    Log.i(TAG, "[START] 发现同名画廊，准备进行增量更新: " + galleryTitle + 
                              " (旧GID: " + oldGid + ", 新GID: " + galleryInfo.gid + ")");
                    
                    // 显示增量更新提示
                    SimpleHandler.getInstance().post(() -> {
                        Toast.makeText(mContext, "检测到画廊更新，将保留已下载的进度", Toast.LENGTH_LONG).show();
                    });
                    
                    // 处理增量下载更新
                    handleIncrementalUpdate(galleryInfo, oldGid);
                } else {
                    Log.d(TAG, "[START] 未发现需要更新的同名画廊: " + galleryTitle);
                }
            }
            
            // It is new download info
            info = new DownloadInfo(galleryInfo);
            info.label = label;
            info.state = DownloadInfo.STATE_WAIT;
            info.time = System.currentTimeMillis();
            
            // 如果是增量更新，保留旧画廊的进度信息
            if (isIncrementalUpdate && oldDownloadInfo != null) {
                Log.d(TAG, "[START] 增量更新 - 保留旧画廊进度信息");
                info.finished = oldDownloadInfo.finished;
                info.downloaded = oldDownloadInfo.downloaded;
                info.total = oldDownloadInfo.total;
                info.legacy = oldDownloadInfo.legacy;
                
                // 在标题前添加增量更新标识
                String originalTitle = EhUtils.getSuitableTitle(info);
                info.title = "🔄 " + originalTitle;
                
                Log.d(TAG, "[START] 增量更新 - 保留的进度信息: 完成=" + info.finished + 
                          ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
            }

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

            // Save to
            EhDB.putDownloadInfo(info);
            Log.d(TAG, "[START] 保存新下载任务到数据库: " + galleryTitle);

            // Notify
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onAdd(info, list, list.size() - 1);
            }
            // Make sure download is running
            ensureDownload();
            Log.d(TAG, "[START] 确保新任务下载正在运行: " + galleryTitle);

            // Add it to history
            EhDB.putHistoryInfo(info);
            Log.d(TAG, "[START] 添加到历史记录: " + galleryTitle);
        }
        
        Log.i(TAG, "[START] 下载启动完成: " + galleryTitle);
    }

    void startRangeDownload(LongList gidList) {
        boolean update = false;
        boolean downloadOrder = Settings.getDownloadOrder();
        
        // 检查是否启用增量下载更新
        boolean incrementalUpdateEnabled = Settings.getIncrementalDownloadUpdate();
        
        if (downloadOrder) {
            for (int i = 0, n = gidList.size(); i < n; i++) {
                long gid = gidList.get(i);
                DownloadInfo info = mAllInfoMap.get(gid);
                if (null == info) {
                    Log.d(TAG, "[RANGE] Can't get download info with gid: " + gid);
                    continue;
                }

                if (info.state == DownloadInfo.STATE_NONE ||
                        info.state == DownloadInfo.STATE_FAILED ||
                        info.state == DownloadInfo.STATE_FINISH) {
                    
                    // 检查是否需要增量更新
                    if (incrementalUpdateEnabled) {
                        GalleryInfo galleryInfo = new GalleryInfo();
                        galleryInfo.gid = gid;
                        galleryInfo.title = info.title;
                        galleryInfo.thumb = info.thumb;
                        galleryInfo.uploader = info.uploader;
                        galleryInfo.category = info.category;
                        galleryInfo.rating = info.rating;
                        
                        Long oldGid = findSameNameGallery(galleryInfo);
                        if (oldGid != null && oldGid != gid) {
                            Log.i(TAG, "[RANGE] 批量下载发现增量更新: " + info.title + 
                                      " (旧GID: " + oldGid + ", 新GID: " + gid + ")");
                            
                            // 处理增量下载更新
                            handleIncrementalUpdate(galleryInfo, oldGid);
                            
                            // 保留旧画廊的进度信息
                            DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                            if (oldDownloadInfo != null) {
                                info.finished = oldDownloadInfo.finished;
                                info.downloaded = oldDownloadInfo.downloaded;
                                info.total = oldDownloadInfo.total;
                                info.legacy = oldDownloadInfo.legacy;
                                
                                // 在标题前添加增量更新标识
                                String originalTitle = EhUtils.getSuitableTitle(info);
                                info.title = "🔄 " + originalTitle;
                                
                                Log.d(TAG, "[RANGE] 批量增量更新 - 保留的进度信息: 完成=" + info.finished + 
                                          ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                            }
                        }
                    }
                    
                    update = true;
                    // Set state DownloadInfo.STATE_WAIT
                    info.state = DownloadInfo.STATE_WAIT;
                    // Add to wait list
                    mWaitList.add(info);
                    // Update in DB
                    EhDB.putDownloadInfo(info);
                }
            }
        } else {
            for (int i = gidList.size(), n = 0; i > n; i--) {
                long gid = gidList.get(i - 1);
                DownloadInfo info = mAllInfoMap.get(gid);
                if (null == info) {
                    Log.d(TAG, "[RANGE] Can't get download info with gid: " + gid);
                    continue;
                }

                if (info.state == DownloadInfo.STATE_NONE ||
                        info.state == DownloadInfo.STATE_FAILED ||
                        info.state == DownloadInfo.STATE_FINISH) {
                    
                    // 检查是否需要增量更新
                    if (incrementalUpdateEnabled) {
                        GalleryInfo galleryInfo = new GalleryInfo();
                        galleryInfo.gid = gid;
                        galleryInfo.title = info.title;
                        galleryInfo.thumb = info.thumb;
                        galleryInfo.uploader = info.uploader;
                        galleryInfo.category = info.category;
                        galleryInfo.rating = info.rating;
                        
                        Long oldGid = findSameNameGallery(galleryInfo);
                        if (oldGid != null && oldGid != gid) {
                            Log.i(TAG, "[RANGE] 批量下载发现增量更新: " + info.title + 
                                      " (旧GID: " + oldGid + ", 新GID: " + gid + ")");
                            
                            // 处理增量下载更新
                            handleIncrementalUpdate(galleryInfo, oldGid);
                            
                            // 保留旧画廊的进度信息
                            DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                            if (oldDownloadInfo != null) {
                                info.finished = oldDownloadInfo.finished;
                                info.downloaded = oldDownloadInfo.downloaded;
                                info.total = oldDownloadInfo.total;
                                info.legacy = oldDownloadInfo.legacy;
                                
                                // 在标题前添加增量更新标识
                                String originalTitle = EhUtils.getSuitableTitle(info);
                                info.title = "🔄 " + originalTitle;
                                
                                Log.d(TAG, "[RANGE] 批量增量更新 - 保留的进度信息: 完成=" + info.finished + 
                                          ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                            }
                        }
                    }
                    
                    update = true;
                    // Set state DownloadInfo.STATE_WAIT
                    info.state = DownloadInfo.STATE_WAIT;
                    // Add to wait list
                    mWaitList.add(info);
                    // Update in DB
                    EhDB.putDownloadInfo(info);
                }
            }
        }


        if (update) {
            // Notify Listener
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onUpdateAll();
            }
            // Ensure download
            ensureDownload();
        }
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
                boolean downloadOrder = Settings.getDownloadOrder();
                
                // 检查是否启用增量下载更新
                boolean incrementalUpdateEnabled = Settings.getIncrementalDownloadUpdate();
                
                int totalCount;
                int processedCount = 0;
                
                // 预计算总数
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    totalCount = (int) allInfoList.stream().filter(info -> info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED).count();
                } else {
                    totalCount = 0;
                }

                if (downloadOrder) {
                    for (DownloadInfo info : allInfoList) {
                        if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                            processedCount++;
                            final int current = processedCount;
                            final int total = totalCount;
                            
                            if (listener != null) {
                                SimpleHandler.getInstance().post(() -> 
                                    listener.onProgress(current, total, EhUtils.getSuitableTitle(info)));
                            }
                            
                            // 检查是否需要增量更新
                            if (incrementalUpdateEnabled) {
                                GalleryInfo galleryInfo = new GalleryInfo();
                                galleryInfo.gid = info.gid;
                                galleryInfo.title = info.title;
                                galleryInfo.thumb = info.thumb;
                                galleryInfo.uploader = info.uploader;
                                galleryInfo.category = info.category;
                                galleryInfo.rating = info.rating;
                                
                                Long oldGid = findSameNameGallery(galleryInfo);
                                if (oldGid != null && oldGid != info.gid) {
                                    Log.i(TAG, "[ALL] 全部下载发现增量更新: " + info.title + 
                                              " (旧GID: " + oldGid + ", 新GID: " + info.gid + ")");
                                    
                                    // 处理增量下载更新
                                    handleIncrementalUpdate(galleryInfo, oldGid);
                                    
                                    // 保留旧画廊的进度信息
                                    DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                                    if (oldDownloadInfo != null) {
                                        info.finished = oldDownloadInfo.finished;
                                        info.downloaded = oldDownloadInfo.downloaded;
                                        info.total = oldDownloadInfo.total;
                                        info.legacy = oldDownloadInfo.legacy;
                                        
                                        // 在标题前添加增量更新标识
                                        String originalTitle = EhUtils.getSuitableTitle(info);
                                        info.title = "🔄 " + originalTitle;
                                        
                                        Log.d(TAG, "[ALL] 全部增量更新 - 保留的进度信息: 完成=" + info.finished + 
                                                  ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                                    }
                                }
                            }
                            
                            update = true;
                            // Set state DownloadInfo.STATE_WAIT
                            info.state = DownloadInfo.STATE_WAIT;
                            // Add to wait list
                            synchronized (waitList) {
                                waitList.addFirst(info);
                            }
                            // Update in DB
                            EhDB.putDownloadInfo(info);
                        }
                    }
                } else {
                    for (DownloadInfo info : allInfoList) {
                        if (info.state == DownloadInfo.STATE_NONE || info.state == DownloadInfo.STATE_FAILED) {
                            processedCount++;
                            final int current = processedCount;
                            final int total = totalCount;
                            
                            if (listener != null) {
                                SimpleHandler.getInstance().post(() -> 
                                    listener.onProgress(current, total, EhUtils.getSuitableTitle(info)));
                            }
                            
                            // 检查是否需要增量更新
                            if (incrementalUpdateEnabled) {
                                GalleryInfo galleryInfo = new GalleryInfo();
                                galleryInfo.gid = info.gid;
                                galleryInfo.title = info.title;
                                galleryInfo.thumb = info.thumb;
                                galleryInfo.uploader = info.uploader;
                                galleryInfo.category = info.category;
                                galleryInfo.rating = info.rating;
                                
                                Long oldGid = findSameNameGallery(galleryInfo);
                                if (oldGid != null && oldGid != info.gid) {
                                    Log.i(TAG, "[ALL] 全部下载发现增量更新: " + info.title + 
                                              " (旧GID: " + oldGid + ", 新GID: " + info.gid + ")");
                                    
                                    // 处理增量下载更新
                                    handleIncrementalUpdate(galleryInfo, oldGid);
                                    
                                    // 保留旧画廊的进度信息
                                    DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                                    if (oldDownloadInfo != null) {
                                        info.finished = oldDownloadInfo.finished;
                                        info.downloaded = oldDownloadInfo.downloaded;
                                        info.total = oldDownloadInfo.total;
                                        info.legacy = oldDownloadInfo.legacy;
                                        
                                        // 在标题前添加增量更新标识
                                        String originalTitle = EhUtils.getSuitableTitle(info);
                                        info.title = "🔄 " + originalTitle;
                                        
                                        Log.d(TAG, "[ALL] 全部增量更新 - 保留的进度信息: 完成=" + info.finished + 
                                                  ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
                                    }
                                }
                            }
                            
                            update = true;
                            // Set state DownloadInfo.STATE_WAIT
                            info.state = DownloadInfo.STATE_WAIT;
                            // Add to wait list
                            synchronized (waitList) {
                                waitList.addFirst(info);
                            }
                            // Update in DB
                            EhDB.putDownloadInfo(info);
                        }
                    }
                }

                final boolean finalUpdate = update;
                SimpleHandler.getInstance().post(() -> {
                    if (finalUpdate) {
                        // Notify Listener
                        for (DownloadInfoListener l : mDownloadInfoListeners) {
                            l.onUpdateAll();
                        }
                        // Ensure download
                        ensureDownload();
                    }
                    
                    if (listener != null) {
                        listener.onComplete(totalCount);
                    }
                });
                
                Log.i(TAG, "[START_ALL] 批量启动完成，共处理 " + totalCount + " 个任务");
            } catch (Exception e) {
                Log.e(TAG, "[START_ALL] 批量启动出错", e);
                if (listener != null) {
                    SimpleHandler.getInstance().post(() -> listener.onError(e.getMessage()));
                }
            }
        });
    }

    public void addDownload(List<DownloadInfo> downloadInfoList) {
        for (DownloadInfo info : downloadInfoList) {
            if (containDownloadInfo(info.gid)) {
                // Contain
                continue;
            }

            // Ensure download state
            if (DownloadInfo.STATE_WAIT == info.state ||
                    DownloadInfo.STATE_DOWNLOAD == info.state) {
                info.state = DownloadInfo.STATE_NONE;
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

        // Notify
        new Handler(Looper.getMainLooper()).post(() -> {
            for (DownloadInfoListener l : mDownloadInfoListeners) {
                l.onReload();
            }
        });
    }

    /**
     * 检测是否存在同名但不同ID的画廊文件夹
     * @param galleryInfo 要检测的画廊信息
     * @return 如果存在同名画廊返回旧画廊的GID，否则返回null
     */
    @Nullable
    private Long findSameNameGallery(@NonNull GalleryInfo galleryInfo) {
        String galleryTitle = EhUtils.getSuitableTitle(galleryInfo);
        Log.d(TAG, "[FIND_SAME] 开始查找同名画廊: " + galleryTitle);
        
        if (!Settings.getIncrementalDownloadUpdate()) {
            Log.d(TAG, "[FIND_SAME] 增量下载更新功能未启用");
            return null;
        }
        Log.d(TAG, "[FIND_SAME] 增量下载更新功能已启用");

        UniFile downloadLocation = Settings.getDownloadLocation();
        if (downloadLocation == null) {
            Log.e(TAG, "[FIND_SAME] 无法获取下载位置");
            return null;
        }
        Log.d(TAG, "[FIND_SAME] 下载位置: " + downloadLocation.getUri());

        String sanitizedTitle = FileUtils.sanitizeFilename(galleryTitle);
        Log.d(TAG, "[FIND_SAME] 清理后的标题: " + sanitizedTitle);

        try {
            UniFile[] files = downloadLocation.listFiles();
            if (files == null) {
                Log.w(TAG, "[FIND_SAME] 无法列出下载目录中的文件");
                return null;
            }
            Log.d(TAG, "[FIND_SAME] 下载目录中共有 " + files.length + " 个文件/文件夹");

            int matchCount = 0;
            for (UniFile file : files) {
                if (!file.isDirectory()) {
                    continue;
                }

                String dirName = file.getName();
                if (dirName == null) {
                    continue;
                }

                // 解析文件夹名称，格式为 {gid}-{title}
                int dashIndex = dirName.indexOf('-');
                if (dashIndex <= 0 || dashIndex >= dirName.length() - 1) {
                    continue;
                }

                String dirTitle = dirName.substring(dashIndex + 1);
                
                // 处理带🔄标识的文件夹名
                String normalizedDirTitle = dirTitle;
                if (dirTitle.startsWith("🔄 ")) {
                    normalizedDirTitle = dirTitle.substring(3); // 移除"🔄 "前缀
                }
                
                if (!normalizedDirTitle.equals(sanitizedTitle)) {
                    continue;
                }

                try {
                    long dirGid = Long.parseLong(dirName.substring(0, dashIndex));
                    if (dirGid != galleryInfo.gid) {
                        // 找到同名但不同ID的画廊
                        matchCount++;
                        Log.i(TAG, "[FIND_SAME] 找到匹配的画廊: " + dirName + " (GID: " + dirGid + ")");
                        
                        // 检查是否有.ehviewer文件
                        UniFile ehviewerFile = file.findFile(".ehviewer");
                        if (ehviewerFile != null) {
                            Log.d(TAG, "[FIND_SAME] 画廊目录包含.ehviewer文件: " + dirName);
                            
                            // 尝试读取.ehviewer文件获取下载进度信息
                            try {
                                InputStream is = ehviewerFile.openInputStream();
                                byte[] buffer = new byte[is.available()];
                                is.read(buffer);
                                is.close();
                                String ehviewerContent = new String(buffer, StandardCharsets.UTF_8);
                                Log.d(TAG, "[FIND_SAME] .ehviewer文件内容: " + ehviewerContent);
                            } catch (Exception e) {
                                Log.w(TAG, "[FIND_SAME] 读取.ehviewer文件失败", e);
                            }
                        } else {
                            Log.w(TAG, "[FIND_SAME] 画廊目录不包含.ehviewer文件: " + dirName);
                        }
                        
                        // 检查下载管理器中是否有这个旧画廊的信息
                        DownloadInfo oldDownloadInfo = mAllInfoMap.get(dirGid);
                        if (oldDownloadInfo != null) {
                            Log.d(TAG, "[FIND_SAME] 在下载管理器中找到旧画廊信息: " + oldDownloadInfo.title);
                        } else {
                            Log.w(TAG, "[FIND_SAME] 在下载管理器中未找到旧画廊信息: GID " + dirGid);
                        }
                        
                        return dirGid;
                    }
                } catch (NumberFormatException e) {
                    // 忽略无法解析的文件夹名
                    Log.d(TAG, "[FIND_SAME] 跳过无法解析的文件夹名: " + dirName);
                }
            }
            
            if (matchCount == 0) {
                Log.d(TAG, "[FIND_SAME] 未找到同名的画廊: " + galleryTitle);
            }
        } catch (Exception e) {
            Log.e(TAG, "[FIND_SAME] 查找同名画廊时发生异常", e);
        }

        return null;
    }

    /**
     * 处理增量下载更新
     * @param newGalleryInfo 新画廊信息
     * @param oldGid 旧画廊的GID
     */
    private void handleIncrementalUpdate(@NonNull GalleryInfo newGalleryInfo, long oldGid) {
        String newTitle = EhUtils.getSuitableTitle(newGalleryInfo);
        Log.i(TAG, "[INCREMENTAL] 开始处理增量更新: " + newTitle + " (旧GID: " + oldGid + ", 新GID: " + newGalleryInfo.gid + ")");
        
        // 获取旧画廊信息
        DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
        if (oldDownloadInfo == null) {
            Log.w(TAG, "[INCREMENTAL] 未找到旧画廊的下载信息: GID " + oldGid + ", 尝试从文件系统读取");
            
            // 尝试从文件系统读取旧画廊的下载信息
            oldDownloadInfo = readDownloadInfoFromFileSystem(oldGid, newTitle);
            if (oldDownloadInfo != null) {
                Log.d(TAG, "[INCREMENTAL] 从文件系统成功读取旧画廊信息: " + oldDownloadInfo.title);
            } else {
                Log.w(TAG, "[INCREMENTAL] 无法从文件系统读取旧画廊信息");
            }
        } else {
            Log.d(TAG, "[INCREMENTAL] 找到旧画廊信息: " + oldDownloadInfo.title + " (状态: " + oldDownloadInfo.state + ")");
        }

        // 获取旧画廊的下载目录
        GalleryInfo tempGalleryInfo = new GalleryInfo();
        tempGalleryInfo.gid = oldGid;
        tempGalleryInfo.title = newTitle;
        UniFile oldDownloadDir = SpiderDen.getGalleryDownloadDir(tempGalleryInfo);
        if (oldDownloadDir == null || !oldDownloadDir.exists()) {
            Log.e(TAG, "[INCREMENTAL] 旧画廊下载目录不存在: " + oldDownloadDir);
            return;
        }
        Log.d(TAG, "[INCREMENTAL] 旧画廊下载目录: " + oldDownloadDir.getUri());

        // 检测旧画廊的下载状态和进度
        if (oldDownloadInfo != null) {
            Log.d(TAG, "[INCREMENTAL] 旧画廊状态检查:");
            Log.d(TAG, "[INCREMENTAL] - 状态: " + getStateString(oldDownloadInfo.state));
            Log.d(TAG, "[INCREMENTAL] - 已完成: " + oldDownloadInfo.finished);
            Log.d(TAG, "[INCREMENTAL] - 已下载: " + oldDownloadInfo.downloaded);
            Log.d(TAG, "[INCREMENTAL] - 总页数: " + oldDownloadInfo.total);
            Log.d(TAG, "[INCREMENTAL] - 剩余: " + oldDownloadInfo.legacy);
        }

        try {
            // 创建.updateGallery文件
            Log.d(TAG, "[INCREMENTAL] 创建.updateGallery文件");
            UniFile updateFile = oldDownloadDir.createFile(".updateGallery");
            if (updateFile != null) {
                String updateContent = "newGid=" + newGalleryInfo.gid + "\n" +
                        "oldGid=" + oldGid + "\n" +
                        "title=" + newGalleryInfo.title + "\n" +
                        "updateTime=" + System.currentTimeMillis() + "\n";
                OutputStream os = updateFile.openOutputStream();
                os.write(updateContent.getBytes("UTF-8"));
                os.close();
                Log.d(TAG, "[INCREMENTAL] .updateGallery文件创建成功");
            } else {
                Log.e(TAG, "[INCREMENTAL] 无法创建.updateGallery文件");
            }

            // 复制旧版本的.ehviewer文件为.ehviewer.[原始ID]
            Log.d(TAG, "[INCREMENTAL] 备份.ehviewer文件");
            UniFile oldEhviewerFile = oldDownloadDir.findFile(".ehviewer");
            if (oldEhviewerFile != null) {
                InputStream is = oldEhviewerFile.openInputStream();
                byte[] buffer = new byte[is.available()];
                is.read(buffer);
                is.close();
                String oldEhviewerContent = new String(buffer, StandardCharsets.UTF_8);
                String backupFileName = ".ehviewer." + oldGid;
                UniFile backupFile = oldDownloadDir.createFile(backupFileName);
                if (backupFile != null) {
                    OutputStream os = backupFile.openOutputStream();
                    os.write(oldEhviewerContent.getBytes(StandardCharsets.UTF_8));
                    os.close();
                    Log.d(TAG, "[INCREMENTAL] .ehviewer文件备份成功: " + backupFileName);
                } else {
                    Log.e(TAG, "[INCREMENTAL] 无法创建.ehviewer备份文件");
                }
            } else {
                Log.w(TAG, "[INCREMENTAL] 未找到.ehviewer文件");
            }

            // 在数据库中添加映射关系
            Log.d(TAG, "[INCREMENTAL] 添加版本映射关系到数据库");
            // 检查是否已有原始记录
            GalleryVersionMap existingMap = EhDB.getGalleryVersionMap(oldGid);
            long originalGid;
            if (existingMap != null) {
                originalGid = existingMap.getOriginalGid();
                Log.d(TAG, "[INCREMENTAL] 找到已存在的版本映射，原始GID: " + originalGid);
            } else {
                originalGid = oldGid;
                Log.d(TAG, "[INCREMENTAL] 未找到已存在的版本映射，使用当前GID作为原始GID: " + originalGid);
            }
            
            // 添加新的映射关系
            EhDB.addGalleryVersionMap(newGalleryInfo.gid, originalGid, newGalleryInfo.title);
            Log.i(TAG, "[INCREMENTAL] 版本映射关系添加成功: " + originalGid + " -> " + newGalleryInfo.gid);

        } catch (Exception e) {
            Log.e(TAG, "[INCREMENTAL] 处理增量更新时发生异常", e);
        }
        
        Log.i(TAG, "[INCREMENTAL] 增量更新处理完成: " + newTitle);
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
            Log.d(TAG, "[DOWNLOAD] 下载任务已存在，检查是否需要增量更新: " + galleryTitle);
            
            // 即使已存在，也检查是否需要增量更新
            if (Settings.getIncrementalDownloadUpdate()) {
                Log.d(TAG, "[DOWNLOAD] 增量下载更新功能已启用，检查是否存在同名画廊");
                Long oldGid = findSameNameGallery(galleryInfo);
                if (oldGid != null && oldGid != galleryInfo.gid) {
                    Log.i(TAG, "[DOWNLOAD] 发现同名画廊，准备进行增量更新: " + galleryTitle + 
                              " (旧GID: " + oldGid + ", 新GID: " + galleryInfo.gid + ")");
                    
                    // 显示增量更新提示
                    SimpleHandler.getInstance().post(() -> {
                        Toast.makeText(mContext, "检测到画廊更新，将保留已下载的进度", Toast.LENGTH_LONG).show();
                    });
                    
                    // 获取已存在的下载信息
                    DownloadInfo existingInfo = mAllInfoMap.get(galleryInfo.gid);
                    if (existingInfo != null) {
                        // 处理增量下载更新
                        handleIncrementalUpdate(galleryInfo, oldGid);
                        
                        // 获取旧画廊信息以保留进度
                        DownloadInfo oldDownloadInfo = mAllInfoMap.get(oldGid);
                        if (oldDownloadInfo != null) {
                            // 更新已存在的下载信息，保留进度
                            existingInfo.finished = oldDownloadInfo.finished;
                            existingInfo.downloaded = oldDownloadInfo.downloaded;
                            existingInfo.total = oldDownloadInfo.total;
                            existingInfo.legacy = oldDownloadInfo.legacy;
                            
                            // 更新状态
                            existingInfo.state = state;
                            existingInfo.time = System.currentTimeMillis();
                            
                            // 更新数据库
                            EhDB.putDownloadInfo(existingInfo);
                            
                            Log.d(TAG, "[DOWNLOAD] 增量更新 - 保留的进度信息: 完成=" + existingInfo.finished + 
                                      ", 下载=" + existingInfo.downloaded + ", 总计=" + existingInfo.total + ", 剩余=" + existingInfo.legacy);
                            
                            // 通知更新
                            List<DownloadInfo> list = getInfoListForLabel(existingInfo.label);
                            if (list != null) {
                                for (DownloadInfoListener l : mDownloadInfoListeners) {
                                    l.onUpdate(existingInfo, list, mWaitList);
                                }
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "[DOWNLOAD] 未发现需要更新的同名画廊: " + galleryTitle);
                }
            }
            
            Log.d(TAG, "[DOWNLOAD] 下载任务已存在，跳过: " + galleryTitle);
            return;
        }

        // 检测是否存在同名但不同ID的画廊
        Log.d(TAG, "[DOWNLOAD] 检查是否存在同名画廊: " + galleryTitle);
        Long oldGid = findSameNameGallery(galleryInfo);
        DownloadInfo oldDownloadInfo = null;
        boolean isIncrementalUpdate = false;
        
        if (oldGid != null) {
            isIncrementalUpdate = true;
            oldDownloadInfo = mAllInfoMap.get(oldGid);
            
            // 如果在下载管理器中找不到旧画廊信息，尝试从文件系统读取
            if (oldDownloadInfo == null) {
                Log.w(TAG, "[DOWNLOAD] 在下载管理器中未找到旧画廊信息，尝试从文件系统读取: GID " + oldGid);
                oldDownloadInfo = readDownloadInfoFromFileSystem(oldGid, galleryTitle);
                if (oldDownloadInfo != null) {
                    Log.d(TAG, "[DOWNLOAD] 从文件系统成功读取旧画廊信息: " + oldDownloadInfo.title);
                } else {
                    Log.w(TAG, "[DOWNLOAD] 无法从文件系统读取旧画廊信息");
                }
            }
            
            Log.i(TAG, "[DOWNLOAD] 发现同名画廊，准备进行增量更新: " + galleryTitle + 
                      " (旧GID: " + oldGid + ", 新GID: " + galleryInfo.gid + ")");
            
            // 显示增量更新提示
            SimpleHandler.getInstance().post(() -> {
                Toast.makeText(mContext, "检测到画廊更新，将保留已下载的进度", Toast.LENGTH_LONG).show();
            });
            
            // 处理增量下载更新
            handleIncrementalUpdate(galleryInfo, oldGid);
        } else {
            Log.d(TAG, "[DOWNLOAD] 未发现同名画廊，作为新下载处理: " + galleryTitle);
        }

        // It is new download info
        DownloadInfo info = new DownloadInfo(galleryInfo);
        info.label = label;
        info.state = state;
        info.time = System.currentTimeMillis();
        
        // 如果是增量更新，保留一些旧信息
        if (isIncrementalUpdate && oldDownloadInfo != null) {
            Log.d(TAG, "[DOWNLOAD] 增量更新 - 保留旧画廊信息");
            // 保留下载进度信息
            info.finished = oldDownloadInfo.finished;
            info.downloaded = oldDownloadInfo.downloaded;
            info.total = oldDownloadInfo.total;
            info.legacy = oldDownloadInfo.legacy;
            
            Log.d(TAG, "[DOWNLOAD] 增量更新 - 保留的进度信息: 完成=" + info.finished + 
                      ", 下载=" + info.downloaded + ", 总计=" + info.total + ", 剩余=" + info.legacy);
        }

        // Add to label download list
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

        // Notify
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onAdd(info, list, list.size() - 1);
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
            // Update listener
            List<DownloadInfo> list = getInfoListForLabel(info.label);
            if (list != null) {
                for (DownloadInfoListener l : mDownloadInfoListeners) {
                    l.onUpdate(info, list, mWaitList);
                }
            }
            // Ensure download
            ensureDownload();
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
            ensureDownload();
        }
    }

    public void stopRangeDownload(LongList gidList) {
        stopRangeDownloadInternal(gidList);

        // Update listener
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onUpdateAll();
        }

        // Ensure download
        ensureDownload();
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
                    for (DownloadInfoListener l : mDownloadInfoListeners) {
                        l.onRemove(info, list, index);
                    }
                }
            }

            // Ensure download
            ensureDownload();
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
        for (DownloadInfoListener l : mDownloadInfoListeners) {
            l.onReload();
        }

        // Ensure download
        ensureDownload();
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
        }.executeOnExecutor(IoThreadPoolExecutor.getInstance());
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
        return mCurrentTask == null && mWaitList.isEmpty();
    }

    /**
     * 是否有正在进行的下载任务（包括当前任务或等待队列）
     */
    public boolean hasActiveDownload() {
        return mCurrentTask != null || !mWaitList.isEmpty();
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
                    // Start next download
                    ensureDownload();
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

    public interface DownloadInfoListener {

        /**
         * Add the special info to the special position
         */
        void onAdd(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position);

        /**
         * delete Old replace new
         */
        void onReplace(@NonNull DownloadInfo newInfo, @NonNull DownloadInfo oldInfo);

        /**
         * The special info is changed
         */
        void onUpdate(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, LinkedList<DownloadInfo> mWaitList);

        /**
         * Maybe all data is changed, but size is the same
         */
        void onUpdateAll();

        /**
         * Maybe all data is changed, maybe list is changed
         */
        void onReload();

        /**
         * The list is gone, use default list please
         */
        void onChange();

        /**
         * Rename label
         */
        void onRenameLabel(String from, String to);

        /**
         * Remove the special info from the special position
         */
        void onRemove(@NonNull DownloadInfo info, @NonNull List<DownloadInfo> list, int position);

        void onUpdateLabels();
    }

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

}
