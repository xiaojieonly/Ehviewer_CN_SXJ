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

import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.LocalGalleryInfo;
import com.hippo.unifile.UniFile;
import com.hippo.yorozuya.FileUtils;
import com.hippo.yorozuya.IOUtils;
import com.hippo.yorozuya.StringUtils;

import android.text.TextUtils;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LocalGalleryManager {
    
    private static final String TAG = "LocalGalleryManager";
    private static final String RECYCLE_BIN_DIR_NAME = ".recycle_bin";
    private static LocalGalleryManager sInstance;
    
    private final Context mContext;
    private final Handler mMainHandler;
    private final List<LocalGalleryListener> mListeners;
    
    public interface LocalGalleryListener {
        void onScanStart();
        void onScanProgress(String current);
        void onScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries);
        void onGalleryDeleted(LocalGalleryInfo gallery, boolean success);
        void onGalleryRestored(LocalGalleryInfo gallery, boolean success);
    }
    
    private LocalGalleryManager(Context context) {
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        mListeners = new ArrayList<>();
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
        Log.d(TAG, "开始扫描本地画廊");
        new ScanTask().execute();
    }
    
    public void deleteGallery(LocalGalleryInfo gallery) {
        new DeleteTask(gallery).execute();
    }
    
    public void restoreGallery(LocalGalleryInfo gallery) {
        new RestoreTask(gallery).execute();
    }
    
    public void permanentlyDeleteGallery(LocalGalleryInfo gallery) {
        new PermanentDeleteTask(gallery).execute();
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
        
        // 简化版本：暂时返回false，即所有本地画廊都显示
        // TODO: 实现完整的下载列表检查功能
        return false;
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
    
    private void notifyScanComplete(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
        mMainHandler.post(() -> {
            for (LocalGalleryListener listener : mListeners) {
                listener.onScanComplete(localGalleries, recycleBinGalleries);
            }
        });
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
                        
                        if (!isInDownloadList(file.getAbsolutePath())) {
                            LocalGalleryInfo info = new LocalGalleryInfo(file.getAbsolutePath());
                            if (info.isValid()) {
                                info.type = LocalGalleryInfo.TYPE_LOCAL;
                                localGalleries.add(info);
                                Log.d(TAG, "添加本地画廊: " + file.getName() + ", 图片数量: " + info.pageCount);
                            } else {
                                Log.w(TAG, "无效的画廊目录: " + file.getAbsolutePath());
                            }
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
                return sourceDir.renameTo(targetDir);
                
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            notifyGalleryDeleted(mGallery, success);
        }
    }
    
    private class RestoreTask extends AsyncTask<Void, Void, Boolean> {
        private final LocalGalleryInfo mGallery;
        
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
        return sourceDir.renameTo(targetDir);
            } catch (Exception e) {
                return false;
            }
        }
        
        @Override
        protected void onPostExecute(Boolean success) {
            notifyGalleryRestored(mGallery, success);
        }
    }
    
    private class PermanentDeleteTask extends AsyncTask<Void, Void, Boolean> {
        private final LocalGalleryInfo mGallery;
        
        PermanentDeleteTask(LocalGalleryInfo gallery) {
            mGallery = gallery;
        }
        
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                File dir = new File(mGallery.path);
                if (!dir.exists()) {
                    return false;
                }
                
                return deleteDirectory(dir);
                
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
    }
    
    private static class ScanResult {
        final List<LocalGalleryInfo> localGalleries;
        final List<LocalGalleryInfo> recycleBinGalleries;
        
        ScanResult(List<LocalGalleryInfo> localGalleries, List<LocalGalleryInfo> recycleBinGalleries) {
            this.localGalleries = localGalleries;
            this.recycleBinGalleries = recycleBinGalleries;
        }
    }
}