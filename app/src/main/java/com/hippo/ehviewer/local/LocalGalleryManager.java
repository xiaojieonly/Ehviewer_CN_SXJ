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

package com.hippo.ehviewer.local;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.cache.GalleryCacheManager;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.EhApplication;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.unifile.UniFile;

import android.text.TextUtils;
import android.util.Log;

import com.hippo.ehviewer.service.LocalGalleryScanService;
import com.hippo.ehviewer.service.RecycleBinScanService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalGalleryManager {
    
    private static final String TAG = "LocalGalleryManager";
    private static final String RECYCLE_BIN_DIR_NAME = ".recycle_bin";
    private static final String KEY_LOCAL_GALLERY_CACHE_JSON = "local_gallery_cache_json";
    private static final String KEY_RECYCLE_BIN_CACHE_JSON = "recycle_bin_cache_json";
    private static final String KEY_LOCAL_GALLERY_CACHE_TIME = "local_gallery_cache_time";
    private static final String KEY_RECYCLE_BIN_CACHE_TIME = "recycle_bin_cache_time";
    private static LocalGalleryManager sInstance;
    
    private final Context mContext;
    private final Handler mMainHandler;
    private final List<LocalGalleryListener> mListeners;
    private final List<LocalGalleryInfo> mCachedLocalGalleries;
    private final List<LocalGalleryInfo> mCachedRecycleBinGalleries;
    private boolean mCacheLoaded;
    private String mLastScanTrigger = "auto";
    
    public interface LocalGalleryListener {
        void onScanStart();
        void onScanProgress(String current);
        void onScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries);
        void onGalleryDeleted(LocalGalleryInfo gallery, boolean success);
        void onGalleryRestored(LocalGalleryInfo gallery, boolean success);
    }

    public interface DeleteProgressCallback {
        void onProgress(int current, int total, String detail);
    }

    public interface ScanProgressCallback {
        boolean onProgress(int current, int total, String detail);
    }
    
    private LocalGalleryManager(Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mListeners = new ArrayList<>();
        mCachedLocalGalleries = new ArrayList<>();
        mCachedRecycleBinGalleries = new ArrayList<>();
    }
    
    public static LocalGalleryManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new LocalGalleryManager(context);
        }
        return sInstance;
    }
    
    public void addListener(LocalGalleryListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    public void removeListener(LocalGalleryListener listener) {
        mListeners.remove(listener);
    }
    
    public void scanLocalGalleries() {
        scanLocalGalleries(false);
    }

    public void scanLocalGalleries(boolean forceRefresh) {
        mLastScanTrigger = forceRefresh ? "manual" : "auto";
        if (!forceRefresh && Settings.getLocalGalleryScanCacheEnabled()) {
            CacheSnapshot cacheSnapshot = loadCacheIfValid();
            if (cacheSnapshot != null) {
                applyCacheSnapshot(cacheSnapshot);
                return;
            }
        }

        Log.d(TAG, "开始扫描本地画廊");
        notifyScanStart();
        LocalGalleryScanService.start(mContext);
        RecycleBinScanService.start(mContext);
    }
    
    public void deleteGallery(LocalGalleryInfo gallery) {
        new DeleteTask(gallery).execute();
    }

    public void restoreGallery(LocalGalleryInfo gallery) {
        new RestoreTask(gallery).execute();
    }

    public void permanentlyDeleteGallery(LocalGalleryInfo gallery) {
        permanentlyDeleteGallery(gallery, null);
    }

    public void permanentlyDeleteGallery(LocalGalleryInfo gallery, DeleteProgressCallback callback) {
        new PermanentDeleteTask(gallery, callback).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public void emptyRecycleBin() {
        new EmptyRecycleBinTask().execute();
    }
    
    private File getRecycleBinDir() {
        File downloadDir = getDownloadDir();
        if (downloadDir == null) {
            return null;
        }
        File recycleBinDir = new File(downloadDir, RECYCLE_BIN_DIR_NAME);
        if (!recycleBinDir.exists()) {
            recycleBinDir.mkdirs();
        }
        return recycleBinDir;
    }
    
    private File getDownloadDir() {
        UniFile downloadLocation = Settings.getDownloadLocation();
        if (downloadLocation == null) {
            // 简化版本：使用默认路径
            return new File("/storage/emulated/0/Download/EhViewer");
        }
        return downloadLocation != null ? new File(downloadLocation.getUri().getPath()) : null;
    }

    private boolean isInDownloadList(String path) {
        if (TextUtils.isEmpty(path)) {
            return false;
        }

        Long gid = parseGidFromPath(path);
        if (gid == null) {
            return false;
        }

        DownloadManager downloadManager = EhApplication.getDownloadManager(mContext);
        DownloadInfo downloadInfo = downloadManager.getDownloadInfo(gid);
        if (downloadInfo == null) {
            return false;
        }

        return downloadInfo.state == DownloadInfo.STATE_WAIT || downloadInfo.state == DownloadInfo.STATE_DOWNLOAD;
    }

    private Long parseGidFromPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        String folderName = new File(path).getName();
        if (TextUtils.isEmpty(folderName)) {
            return null;
        }

        int idx = 0;
        while (idx < folderName.length() && Character.isDigit(folderName.charAt(idx))) {
            idx++;
        }
        if (idx == 0) {
            return null;
        }

        try {
            return Long.parseLong(folderName.substring(0, idx));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalGalleryInfo createLocalGalleryInfo(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return null;
        }

        LocalGalleryInfo info = new LocalGalleryInfo(directory.getAbsolutePath());

        // 从 .ehviewer.extra.json 读取缓存信息
        File extraFile = new File(directory, GalleryCacheManager.GALLERY_CACHE_FILENAME);
        if (extraFile.exists() && extraFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(extraFile)) {
                String content = IOUtils.readString(fis, "UTF-8");
                if (!TextUtils.isEmpty(content)) {
                    JSONObject jsonObject = JSON.parseObject(content);
                    if (jsonObject != null) {
                        long gid = jsonObject.getLongValue("gid");
                        if (gid > 0) {
                            info.gid = String.valueOf(gid);
                        }
                        String token = jsonObject.getString("token");
                        if (!TextUtils.isEmpty(token)) {
                            info.token = token;
                        }
                        String title = jsonObject.getString("title");
                        if (!TextUtils.isEmpty(title)) {
                            info.title = title;
                        }
                        String titleJpn = jsonObject.getString("titleJpn");
                        if (!TextUtils.isEmpty(titleJpn)) {
                            info.titleJpn = titleJpn;
                        }
                        String thumb = jsonObject.getString("thumb");
                        if (!TextUtils.isEmpty(thumb)) {
                            info.thumb = new File(directory, thumb).getAbsolutePath();
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "无法读取 .ehviewer.extra.json", e);
            }
        }

        // 兼容旧版本 .ehviewer 文件
        File ehviewerFile = new File(directory, DownloadManager.DOWNLOAD_INFO_FILENAME);
        if (ehviewerFile.exists() && ehviewerFile.isFile()) {
            try (FileInputStream fis = new FileInputStream(ehviewerFile)) {
                String content = IOUtils.readString(fis, "UTF-8");
                if (!TextUtils.isEmpty(content)) {
                    String[] lines = content.split("\\n");
                    if (lines.length > 0) {
                        try {
                            long parsedGid = Long.parseLong(lines[0].trim());
                            info.gid = String.valueOf(parsedGid);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (lines.length > 1) {
                        String token = lines[1].trim();
                        if (!TextUtils.isEmpty(token)) {
                            info.token = token;
                        }
                    }
                    if (lines.length > 2) {
                        String title = lines[2].trim();
                        if (!TextUtils.isEmpty(title)) {
                            info.title = title;
                        }
                    }
                    if (lines.length > 3) {
                        String titleJpn = lines[3].trim();
                        if (!TextUtils.isEmpty(titleJpn)) {
                            info.titleJpn = titleJpn;
                        }
                    }
                    if (lines.length > 7) {
                        try {
                            info.pageCount = Integer.parseInt(lines[7].trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "无法读取 .ehviewer 文件", e);
            }
        }

        return info;
    }

    private void notifyScanStart() {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onScanStart();
            }
        });
    }
    
    private void notifyScanProgress(String current) {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onScanProgress(current);
            }
        });
    }

    public void reportScanProgress(String detail) {
        notifyScanProgress(detail);
    }
    
    private void notifyScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onScanComplete(localGalleries, recycleBinGalleries);
            }
        });
    }

    public void reportLocalScanComplete(List<LocalGalleryInfo> localGalleries) {
        synchronized (mCachedLocalGalleries) {
            mCachedLocalGalleries.clear();
            mCachedLocalGalleries.addAll(localGalleries);
        }
        persistLocalGalleryCache();
        mCacheLoaded = true;
        updateLastScanRecord("local");
        List<LocalGalleryInfo> recycleSnapshot = new ArrayList<>();
        synchronized (mCachedRecycleBinGalleries) {
            recycleSnapshot.addAll(mCachedRecycleBinGalleries);
        }
        notifyScanComplete(new ArrayList<>(localGalleries), recycleSnapshot);
    }

    public void reportRecycleBinScanComplete(List<LocalGalleryInfo> recycleBinGalleries) {
        synchronized (mCachedRecycleBinGalleries) {
            mCachedRecycleBinGalleries.clear();
            mCachedRecycleBinGalleries.addAll(recycleBinGalleries);
        }
        persistRecycleBinCache();
        mCacheLoaded = true;
        updateLastScanRecord("recycle_bin");
        List<LocalGalleryInfo> localSnapshot = new ArrayList<>();
        synchronized (mCachedLocalGalleries) {
            localSnapshot.addAll(mCachedLocalGalleries);
        }
        notifyScanComplete(localSnapshot, new ArrayList<>(recycleBinGalleries));
    }

    private void updateLastScanRecord(String scanType) {
        JSONObject record = new JSONObject();
        record.put("trigger", mLastScanTrigger);
        record.put("type", scanType);
        record.put("localCount", mCachedLocalGalleries.size());
        record.put("recycleCount", mCachedRecycleBinGalleries.size());
        Settings.putLong(Settings.KEY_LOCAL_GALLERY_LAST_SCAN_TIME, System.currentTimeMillis());
        Settings.putString(Settings.KEY_LOCAL_GALLERY_LAST_SCAN_TYPE, scanType);
        Settings.putString(Settings.KEY_LOCAL_GALLERY_LAST_SCAN_RESULT, record.toJSONString());
    }

    public List<LocalGalleryInfo> getCachedLocalGalleries() {
        List<LocalGalleryInfo> snapshot = new ArrayList<>();
        synchronized (mCachedLocalGalleries) {
            snapshot.addAll(mCachedLocalGalleries);
        }
        return snapshot;
    }

    public List<LocalGalleryInfo> getCachedRecycleBinGalleries() {
        List<LocalGalleryInfo> snapshot = new ArrayList<>();
        synchronized (mCachedRecycleBinGalleries) {
            snapshot.addAll(mCachedRecycleBinGalleries);
        }
        return snapshot;
    }

    public List<LocalGalleryInfo> scanLocalGalleriesSync(@Nullable ScanProgressCallback callback) {
        List<LocalGalleryInfo> localGalleries = new ArrayList<>();
        File downloadDir = getDownloadDir();
        if (downloadDir == null || !downloadDir.exists()) {
            Log.w(TAG, "下载目录不存在: " + downloadDir);
            return localGalleries;
        }

        File[] files = downloadDir.listFiles();
        if (files == null) {
            Log.w(TAG, "无法列出下载目录的文件");
            return localGalleries;
        }

        List<File> galleryDirs = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory() && !file.getName().equals(RECYCLE_BIN_DIR_NAME)) {
                galleryDirs.add(file);
            }
        }

        int total = galleryDirs.size();
        if (total == 0) {
            return localGalleries;
        }

        AtomicInteger current = new AtomicInteger(0);
        AtomicBoolean canceled = new AtomicBoolean(false);
        int batchSize = Math.max(1, (int) Math.ceil(total * 0.1f));
        int batchCount = (total + batchSize - 1) / batchSize;
        int threadCount = Math.min(batchCount, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<LocalGalleryInfo>>> futures = new ArrayList<>();

        for (int start = 0; start < total; start += batchSize) {
            int from = start;
            int to = Math.min(total, start + batchSize);
            futures.add(executor.submit(() -> scanLocalBatch(galleryDirs, from, to, current, total, canceled, callback)));
        }

        for (Future<List<LocalGalleryInfo>> future : futures) {
            if (canceled.get()) {
                break;
            }
            try {
                localGalleries.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                Log.w(TAG, "Scan local gallery batch failed", e);
            }
        }

        if (canceled.get()) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }

        return localGalleries;
    }

    public List<LocalGalleryInfo> scanRecycleBinSync(@Nullable ScanProgressCallback callback) {
        List<LocalGalleryInfo> recycleBinGalleries = new ArrayList<>();
        File recycleBinDir = getRecycleBinDir();
        if (recycleBinDir == null || !recycleBinDir.exists()) {
            Log.d(TAG, "回收站目录不存在: " + recycleBinDir);
            return recycleBinGalleries;
        }

        File[] files = recycleBinDir.listFiles();
        if (files == null) {
            Log.w(TAG, "无法列出回收站目录的文件");
            return recycleBinGalleries;
        }

        List<File> galleryDirs = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory()) {
                galleryDirs.add(file);
            }
        }

        int total = galleryDirs.size();
        if (total == 0) {
            return recycleBinGalleries;
        }

        AtomicInteger current = new AtomicInteger(0);
        AtomicBoolean canceled = new AtomicBoolean(false);
        int batchSize = Math.max(1, (int) Math.ceil(total * 0.1f));
        int batchCount = (total + batchSize - 1) / batchSize;
        int threadCount = Math.min(batchCount, Math.max(1, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<List<LocalGalleryInfo>>> futures = new ArrayList<>();

        for (int start = 0; start < total; start += batchSize) {
            int from = start;
            int to = Math.min(total, start + batchSize);
            futures.add(executor.submit(() -> scanRecycleBatch(galleryDirs, from, to, current, total, canceled, callback)));
        }

        for (Future<List<LocalGalleryInfo>> future : futures) {
            if (canceled.get()) {
                break;
            }
            try {
                recycleBinGalleries.addAll(future.get());
            } catch (InterruptedException | ExecutionException e) {
                Log.w(TAG, "Scan recycle bin batch failed", e);
            }
        }

        if (canceled.get()) {
            executor.shutdownNow();
        } else {
            executor.shutdown();
        }

        return recycleBinGalleries;
    }

    private List<LocalGalleryInfo> scanLocalBatch(List<File> galleryDirs, int from, int to,
            AtomicInteger current, int total, AtomicBoolean canceled, @Nullable ScanProgressCallback callback) {
        List<LocalGalleryInfo> batch = new ArrayList<>();
        for (int index = from; index < to; index++) {
            if (canceled.get()) {
                break;
            }
            File file = galleryDirs.get(index);
            int progress = current.incrementAndGet();
            if (callback != null && !callback.onProgress(progress, total, file.getName())) {
                canceled.set(true);
                break;
            }

            if (isInDownloadList(file.getAbsolutePath())) {
                Log.d(TAG, "正在下载中，跳过本地画廊: " + file.getAbsolutePath());
                continue;
            }

            LocalGalleryInfo info = createLocalGalleryInfo(file);
            if (info != null && info.isValid()) {
                info.type = LocalGalleryInfo.TYPE_LOCAL;
                batch.add(info);
            }
        }
        return batch;
    }

    private List<LocalGalleryInfo> scanRecycleBatch(List<File> galleryDirs, int from, int to,
            AtomicInteger current, int total, AtomicBoolean canceled, @Nullable ScanProgressCallback callback) {
        List<LocalGalleryInfo> batch = new ArrayList<>();
        for (int index = from; index < to; index++) {
            if (canceled.get()) {
                break;
            }
            File file = galleryDirs.get(index);
            int progress = current.incrementAndGet();
            if (callback != null && !callback.onProgress(progress, total, file.getName())) {
                canceled.set(true);
                break;
            }

            LocalGalleryInfo info = new LocalGalleryInfo(file.getAbsolutePath());
            if (info.isValid()) {
                info.type = LocalGalleryInfo.TYPE_RECYCLE_BIN;
                batch.add(info);
            }
        }
        return batch;
    }
    
    private void notifyGalleryDeleted(LocalGalleryInfo gallery, boolean success) {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onGalleryDeleted(gallery, success);
            }
        });
    }
    
    private void notifyGalleryRestored(LocalGalleryInfo gallery, boolean success) {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onGalleryRestored(gallery, success);
            }
        });
    }
    
    private class ScanTask extends AsyncTask<Void, String, ScanResult> {
        
        @Override
        protected void onPreExecute() {
            Log.d(TAG, "ScanTask: 准备开始扫描");
            notifyScanStart();
        }
        
        @Override
        protected ScanResult doInBackground(Void... params) {
            List<LocalGalleryInfo> localGalleries = new ArrayList<>();
            List<LocalGalleryInfo> recycleBinGalleries = new ArrayList<>();
            
            File downloadDir = getDownloadDir();
            if (downloadDir == null || !downloadDir.exists()) {
                Log.w(TAG, "下载目录不存在: " + downloadDir);
                return new ScanResult(localGalleries, recycleBinGalleries);
            }
            
            Log.d(TAG, "扫描下载目录: " + downloadDir.getAbsolutePath());
            
            // 扫描下载目录中的画廊
            File[] files = downloadDir.listFiles();
            if (files != null) {
                Log.d(TAG, "找到 " + files.length + " 个文件/文件夹");
                for (File file : files) {
                    if (file.isDirectory() && !file.getName().equals(RECYCLE_BIN_DIR_NAME)) {
                        publishProgress(file.getName());
                        
                        if (isInDownloadList(file.getAbsolutePath())) {
                            Log.d(TAG, "正在下载中，跳过本地画廊: " + file.getAbsolutePath());
                            continue;
                        }

                        LocalGalleryInfo info = createLocalGalleryInfo(file);
                        if (info != null && info.isValid()) {
                            info.type = LocalGalleryInfo.TYPE_LOCAL;
                            localGalleries.add(info);
                            Log.d(TAG, "添加本地画廊: " + file.getName() + ", 图片数量: " + info.pageCount);
                        } else {
                            Log.w(TAG, "无效的画廊目录: " + file.getAbsolutePath());
                        }
                    }
                }
            } else {
                Log.w(TAG, "无法列出下载目录的文件");
            }
            
            // 扫描回收站
            File recycleBinDir = getRecycleBinDir();
            if (recycleBinDir != null && recycleBinDir.exists()) {
                Log.d(TAG, "扫描回收站目录: " + recycleBinDir.getAbsolutePath());
                File[] recycleFiles = recycleBinDir.listFiles();
                if (recycleFiles != null) {
                    Log.d(TAG, "回收站找到 " + recycleFiles.length + " 个文件/文件夹");
                    for (File file : recycleFiles) {
                        if (file.isDirectory()) {
                            publishProgress("Recycle: " + file.getName());
                            
                            LocalGalleryInfo info = new LocalGalleryInfo(file.getAbsolutePath());
                            if (info.isValid()) {
                                info.type = LocalGalleryInfo.TYPE_RECYCLE_BIN;
                                recycleBinGalleries.add(info);
                                Log.d(TAG, "添加回收站画廊: " + file.getName() + ", 图片数量: " + info.pageCount);
                            } else {
                                Log.w(TAG, "回收站中的无效画廊目录: " + file.getAbsolutePath());
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "无法列出回收站目录的文件");
                }
            } else {
                Log.d(TAG, "回收站目录不存在: " + recycleBinDir);
            }
            
            Log.d(TAG, "扫描完成 - 本地画廊: " + localGalleries.size() + ", 回收站: " + recycleBinGalleries.size());
            return new ScanResult(localGalleries, recycleBinGalleries);
        }
        
        @Override
        protected void onProgressUpdate(String... values) {
            if (values.length > 0) {
                Log.d(TAG, "扫描进度更新: " + values[0]);
                notifyScanProgress(values[0]);
            }
        }
        
        @Override
        protected void onPostExecute(ScanResult result) {
            Log.d(TAG, "ScanTask 完成，通知监听器");
            notifyScanComplete(result.localGalleries, result.recycleBinGalleries);
        }
    }
    
    private class DeleteTask extends AsyncTask<Void, Void, Boolean> {
        private final LocalGalleryInfo mGallery;
        private File mTargetDir;
        
        DeleteTask(LocalGalleryInfo gallery) {
            mGallery = gallery;
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File sourceDir = new File(mGallery.path);
                if (!sourceDir.exists()) {
                    return false;
                }
                
                File recycleBinDir = getRecycleBinDir();
                if (recycleBinDir == null) {
                    return false;
                }
                
                File targetDir = new File(recycleBinDir, sourceDir.getName());
                
                // 如果目标目录已存在，添加时间戳
                if (targetDir.exists()) {
                    String newName = sourceDir.getName() + "_" + System.currentTimeMillis();
                    targetDir = new File(recycleBinDir, newName);
                }
                
                // 移动文件夹到回收站
                boolean result = sourceDir.renameTo(targetDir);
                if (result) {
                    mTargetDir = targetDir;
                }
                return result;
                
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                updateCacheAfterDelete(mGallery, mTargetDir);
            }
            notifyGalleryDeleted(mGallery, success);
        }
    }
    
    private class RestoreTask extends AsyncTask<Void, Void, Boolean> {
        private final LocalGalleryInfo mGallery;
        private File mTargetDir;
        
        RestoreTask(LocalGalleryInfo gallery) {
            mGallery = gallery;
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File sourceDir = new File(mGallery.path);
                if (!sourceDir.exists()) {
                    return false;
                }
                
                File downloadDir = getDownloadDir();
                if (downloadDir == null) {
                    return false;
                }
                
                File targetDir = new File(downloadDir, sourceDir.getName());
                
                // 如果目标目录已存在，添加时间戳
                if (targetDir.exists()) {
                    String newName = sourceDir.getName() + "_restored_" + System.currentTimeMillis();
                    targetDir = new File(downloadDir, newName);
                }
                
                // 移动文件夹回下载目录
                boolean result = sourceDir.renameTo(targetDir);
                if (result) {
                    mTargetDir = targetDir;
                }
                return result;
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                updateCacheAfterRestore(mGallery, mTargetDir);
            }
            notifyGalleryRestored(mGallery, success);
        }
    }
    
    private class PermanentDeleteTask extends AsyncTask<Void, Integer, Boolean> {
        private final LocalGalleryInfo mGallery;
        private final DeleteProgressCallback mCallback;
        private int mTotalFiles;

        PermanentDeleteTask(LocalGalleryInfo gallery, DeleteProgressCallback callback) {
            mGallery = gallery;
            mCallback = callback;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File dir = new File(mGallery.path);
                if (!dir.exists()) {
                    return false;
                }

                mTotalFiles = countFiles(dir);
                if (mTotalFiles <= 0) {
                    return dir.delete();
                }

                AtomicInteger deletedCount = new AtomicInteger(0);
                boolean result = deleteDirectory(dir, deletedCount);
                publishProgress(deletedCount.get());
                return result;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (mCallback != null && values != null && values.length > 0) {
                int current = values[0];
                mCallback.onProgress(current, mTotalFiles, "");
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                updateCacheAfterPermanentDelete(mGallery);
            }
            if (mCallback != null) {
                mCallback.onProgress(mTotalFiles, mTotalFiles, success ? "done" : "failed");
            }
        }

        private int countFiles(File directory) {
            if (directory == null || !directory.exists()) {
                return 0;
            }
            int count = 1; // count this file/dir
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        count += countFiles(file);
                    } else {
                        count += 1;
                    }
                }
            }
            return count;
        }

        private boolean deleteDirectory(File directory, AtomicInteger deletedCount) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        if (!deleteDirectory(file, deletedCount)) {
                            return false;
                        }
                    } else {
                        if (file.delete()) {
                            deletedCount.incrementAndGet();
                            publishProgress(deletedCount.get());
                        }
                    }
                }
            }
            if (directory.delete()) {
                deletedCount.incrementAndGet();
                publishProgress(deletedCount.get());
                return true;
            }
            return false;
        }
    }
    
    private class EmptyRecycleBinTask extends AsyncTask<Void, Void, Boolean> {
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File recycleBinDir = getRecycleBinDir();
                if (recycleBinDir == null || !recycleBinDir.exists()) {
                    return true;
                }
                
                return deleteDirectory(recycleBinDir);
                
            } catch (Exception e) {
                return false;
            }
        }
        
        private boolean deleteDirectory(File directory) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            return directory.delete();
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                updateCacheAfterEmptyRecycleBin();
            }
        }
    }
    
    private static class ScanResult {
        final List<LocalGalleryInfo> localGalleries;
        final List<LocalGalleryInfo> recycleBinGalleries;
        
        ScanResult(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
            this.localGalleries = localGalleries;
            this.recycleBinGalleries = recycleBinGalleries;
        }
    }

    private static class CacheSnapshot {
        final List<LocalGalleryInfo> localGalleries;
        final List<LocalGalleryInfo> recycleBinGalleries;

        CacheSnapshot(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
            this.localGalleries = localGalleries;
            this.recycleBinGalleries = recycleBinGalleries;
        }
    }

    private CacheSnapshot loadCacheIfValid() {
        int expiryDays = Settings.getLocalGalleryCacheExpireDays();
        if (expiryDays <= 0) {
            return null;
        }

        long now = System.currentTimeMillis();
        long localTimestamp = Settings.getLong(KEY_LOCAL_GALLERY_CACHE_TIME, 0L);
        long recycleTimestamp = Settings.getLong(KEY_RECYCLE_BIN_CACHE_TIME, 0L);
        long maxAgeMillis = TimeUnit.DAYS.toMillis(expiryDays);

        if (localTimestamp <= 0 || recycleTimestamp <= 0) {
            return null;
        }
        if (now - localTimestamp > maxAgeMillis || now - recycleTimestamp > maxAgeMillis) {
            return null;
        }

        String localJson = Settings.getString(KEY_LOCAL_GALLERY_CACHE_JSON, null);
        String recycleJson = Settings.getString(KEY_RECYCLE_BIN_CACHE_JSON, null);
        if (TextUtils.isEmpty(localJson) || TextUtils.isEmpty(recycleJson)) {
            return null;
        }

        try {
            List<LocalGalleryInfo> localList = decodeGalleryList(localJson);
            List<LocalGalleryInfo> recycleList = decodeGalleryList(recycleJson);
            return new CacheSnapshot(localList, recycleList);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse local gallery cache", e);
            return null;
        }
    }

    private void applyCacheSnapshot(CacheSnapshot snapshot) {
        synchronized (mCachedLocalGalleries) {
            mCachedLocalGalleries.clear();
            mCachedLocalGalleries.addAll(snapshot.localGalleries);
        }
        synchronized (mCachedRecycleBinGalleries) {
            mCachedRecycleBinGalleries.clear();
            mCachedRecycleBinGalleries.addAll(snapshot.recycleBinGalleries);
        }
        mCacheLoaded = true;
        notifyScanComplete(new ArrayList<>(snapshot.localGalleries), new ArrayList<>(snapshot.recycleBinGalleries));
    }

    private void persistLocalGalleryCache() {
        List<LocalGalleryInfo> snapshot = new ArrayList<>();
        synchronized (mCachedLocalGalleries) {
            snapshot.addAll(mCachedLocalGalleries);
        }
        String json = encodeGalleryList(snapshot);
        Settings.putString(KEY_LOCAL_GALLERY_CACHE_JSON, json);
        Settings.putLong(KEY_LOCAL_GALLERY_CACHE_TIME, System.currentTimeMillis());
    }

    private void persistRecycleBinCache() {
        List<LocalGalleryInfo> snapshot = new ArrayList<>();
        synchronized (mCachedRecycleBinGalleries) {
            snapshot.addAll(mCachedRecycleBinGalleries);
        }
        String json = encodeGalleryList(snapshot);
        Settings.putString(KEY_RECYCLE_BIN_CACHE_JSON, json);
        Settings.putLong(KEY_RECYCLE_BIN_CACHE_TIME, System.currentTimeMillis());
    }

    private String encodeGalleryList(List<LocalGalleryInfo> galleries) {
        JSONArray array = new JSONArray();
        if (galleries != null) {
            for (LocalGalleryInfo info : galleries) {
                array.add(encodeGalleryInfo(info));
            }
        }
        return array.toJSONString();
    }

    private List<LocalGalleryInfo> decodeGalleryList(String json) {
        List<LocalGalleryInfo> galleries = new ArrayList<>();
        if (TextUtils.isEmpty(json)) {
            return galleries;
        }
        JSONArray array = JSON.parseArray(json);
        if (array == null) {
            return galleries;
        }
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj != null) {
                galleries.add(decodeGalleryInfo(obj));
            }
        }
        return galleries;
    }

    private JSONObject encodeGalleryInfo(LocalGalleryInfo info) {
        JSONObject obj = new JSONObject();
        if (info == null) {
            return obj;
        }
        obj.put("id", info.id);
        obj.put("title", info.title);
        obj.put("titleJpn", info.titleJpn);
        obj.put("category", info.category);
        obj.put("thumb", info.thumb);
        obj.put("path", info.path);
        obj.put("type", info.type);
        obj.put("timestamp", info.timestamp);
        obj.put("pageCount", info.pageCount);
        obj.put("size", info.size);
        obj.put("gid", info.gid);
        obj.put("token", info.token);
        return obj;
    }

    private LocalGalleryInfo decodeGalleryInfo(JSONObject obj) {
        LocalGalleryInfo info = new LocalGalleryInfo();
        info.id = obj.getLongValue("id");
        info.title = obj.getString("title");
        info.titleJpn = obj.getString("titleJpn");
        info.category = obj.getString("category");
        info.thumb = obj.getString("thumb");
        info.path = obj.getString("path");
        info.type = obj.getIntValue("type");
        info.timestamp = obj.getLongValue("timestamp");
        info.pageCount = obj.getIntValue("pageCount");
        info.size = obj.getLongValue("size");
        info.gid = obj.getString("gid");
        info.token = obj.getString("token");
        return info;
    }

    private void updateCacheAfterDelete(LocalGalleryInfo gallery, File targetDir) {
        if (gallery == null || targetDir == null) {
            return;
        }
        LocalGalleryInfo updated = new LocalGalleryInfo(targetDir.getAbsolutePath());
        updated.type = LocalGalleryInfo.TYPE_RECYCLE_BIN;

        synchronized (mCachedLocalGalleries) {
            removeByPath(mCachedLocalGalleries, gallery.path);
        }
        synchronized (mCachedRecycleBinGalleries) {
            mCachedRecycleBinGalleries.add(updated);
        }
        if (mCacheLoaded) {
            persistLocalGalleryCache();
            persistRecycleBinCache();
        }
    }

    private void updateCacheAfterRestore(LocalGalleryInfo gallery, File targetDir) {
        if (gallery == null || targetDir == null) {
            return;
        }
        LocalGalleryInfo updated = new LocalGalleryInfo(targetDir.getAbsolutePath());
        updated.type = LocalGalleryInfo.TYPE_LOCAL;

        synchronized (mCachedRecycleBinGalleries) {
            removeByPath(mCachedRecycleBinGalleries, gallery.path);
        }
        synchronized (mCachedLocalGalleries) {
            mCachedLocalGalleries.add(updated);
        }
        if (mCacheLoaded) {
            persistLocalGalleryCache();
            persistRecycleBinCache();
        }
    }

    private void updateCacheAfterPermanentDelete(LocalGalleryInfo gallery) {
        if (gallery == null) {
            return;
        }
        synchronized (mCachedRecycleBinGalleries) {
            removeByPath(mCachedRecycleBinGalleries, gallery.path);
        }
        if (mCacheLoaded) {
            persistRecycleBinCache();
        }
    }

    private void updateCacheAfterEmptyRecycleBin() {
        synchronized (mCachedRecycleBinGalleries) {
            mCachedRecycleBinGalleries.clear();
        }
        if (mCacheLoaded) {
            persistRecycleBinCache();
        }

        // Notify listeners to refresh UI immediately after emptying recycle bin
        List<LocalGalleryInfo> localSnapshot = getCachedLocalGalleries();
        List<LocalGalleryInfo> recycleSnapshot = getCachedRecycleBinGalleries();
        notifyScanComplete(localSnapshot, recycleSnapshot);
    }

    private void removeByPath(List<LocalGalleryInfo> list, String path) {
        if (list == null || TextUtils.isEmpty(path)) {
            return;
        }
        Iterator<LocalGalleryInfo> iterator = list.iterator();
        while (iterator.hasNext()) {
            LocalGalleryInfo info = iterator.next();
            if (TextUtils.equals(path, info.path)) {
                iterator.remove();
                return;
            }
        }
    }
}