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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.spider.SpiderDen;
import com.hippo.unifile.SmbUri;
import com.hippo.unifile.SmbUriHandler;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmbBackupManager {

    private static final String TAG = "SmbBackupManager";

    private final Context mContext;
    private final SmbBackupSettings mBackupSettings;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean mSyncingAll = new AtomicBoolean(false);

    @Nullable
    private SyncListener mListener;

    public interface SyncListener {
        void onSyncStart();
        void onSyncProgress(int current, int total, String galleryName);
        void onSyncComplete(int successCount, int failCount);
        void onSyncError(String error);
    }

    public SmbBackupManager(Context context) {
        mContext = context.getApplicationContext();
        mBackupSettings = new SmbBackupSettings(context);
    }

    public void setSyncListener(@Nullable SyncListener listener) {
        mListener = listener;
    }

    public boolean isBackupEnabled() {
        return mBackupSettings.isEnabled() && mBackupSettings.loadConfig() != null;
    }

    public boolean isSyncingAll() {
        return mSyncingAll.get();
    }

    public void syncGalleryToBackup(@NonNull GalleryInfo galleryInfo) {
        mExecutor.execute(() -> syncGalleryInternal(galleryInfo));
    }

    public void syncAllToBackup() {
        if (mSyncingAll.compareAndSet(false, true)) {
            mExecutor.execute(this::syncAllInternal);
        }
    }

    private void syncGalleryInternal(@NonNull GalleryInfo galleryInfo) {
        SmbConfig config = mBackupSettings.loadConfigIfEnabled();
        if (config == null) {
            return;
        }

        UniFile downloadLoc = com.hippo.anotherviewer.Settings.getDownloadLocation();
        if (downloadLoc == null || (downloadLoc.getUri() != null && "smb".equals(downloadLoc.getUri().getScheme()))) {
            return;
        }

        UniFile localDir = SpiderDen.getGalleryDownloadDir(galleryInfo);
        if (localDir == null || !localDir.isDirectory()) {
            return;
        }

        SmbConnection connection = new SmbConnection(config);
        try {
            connection.open();
            String dirname = localDir.getName();
            if (dirname == null) return;

            String basePath = config.getPath();
            String galleryPath = basePath.isEmpty() ? dirname : basePath + "/" + dirname;
            connection.ensureDirectory(galleryPath);

            UniFile[] files = localDir.listFiles();
            if (files != null) {
                for (UniFile file : files) {
                    String name = file.getName();
                    if (name == null) continue;
                    if (file.isDirectory()) {
                        connection.ensureDirectory(galleryPath + "/" + name);
                    } else {
                        String filePath = galleryPath + "/" + name;
                        if (connection.exists(filePath)) continue;
                        try (InputStream is = file.openInputStream()) {
                            connection.writeFile(filePath, is);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync gallery " + localDir.getName() + " to SMB backup", e);
        } finally {
            connection.close();
        }
    }

    private void syncAllInternal() {
        SmbConfig config = mBackupSettings.loadConfigIfEnabled();
        if (config == null) {
            notifyError("SMB backup is not configured");
            return;
        }

        UniFile localRoot = getLocalDownloadDir();
        if (localRoot == null) {
            notifyError("Local download directory is unavailable");
            return;
        }

        UniFile[] localDirs = localRoot.listFiles();
        if (localDirs == null || localDirs.length == 0) {
            notifyComplete(0, 0);
            return;
        }

        notifyStart();

        int total = localDirs.length;
        int successCount = 0;
        int failCount = 0;

        SmbConnection connection = new SmbConnection(config);
        try {
            connection.open();
            String basePath = config.getPath();

            for (int i = 0; i < total; i++) {
                UniFile localDir = localDirs[i];
                if (!localDir.isDirectory()) continue;
                String dirname = localDir.getName();
                if (dirname == null || dirname.startsWith(".")) continue;

                notifyProgress(i + 1, total, dirname);

                try {
                    String galleryPath = basePath.isEmpty() ? dirname : basePath + "/" + dirname;
                    connection.ensureDirectory(galleryPath);

                    UniFile[] files = localDir.listFiles();
                    if (files != null) {
                        for (int j = 0; j < files.length; j++) {
                            UniFile file = files[j];
                            String name = file.getName();
                            if (name == null) continue;
                            if (file.isDirectory()) {
                                connection.ensureDirectory(galleryPath + "/" + name);
                            } else {
                                String filePath = galleryPath + "/" + name;
                                if (connection.exists(filePath) && connection.length(filePath) == file.length()) continue;
                                try (InputStream is = file.openInputStream()) {
                                    connection.writeFile(filePath, is);
                                }
                            }
                        }
                    }
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync " + dirname + " to SMB backup", e);
                    failCount++;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to open SMB connection", e);
        } finally {
            connection.close();
        }

        notifyComplete(successCount, failCount);
        mSyncingAll.set(false);
    }

    @Nullable
    private UniFile getLocalDownloadDir() {
        try {
            return com.hippo.anotherviewer.Settings.getDownloadLocation();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local download directory", e);
            return null;
        }
    }

    private void notifyStart() {
        SyncListener listener = mListener;
        if (listener != null) listener.onSyncStart();
    }

    private void notifyProgress(int current, int total, String name) {
        SyncListener listener = mListener;
        if (listener != null) listener.onSyncProgress(current, total, name);
    }

    private void notifyComplete(int successCount, int failCount) {
        SyncListener listener = mListener;
        if (listener != null) listener.onSyncComplete(successCount, failCount);
    }

    private void notifyError(String error) {
        SyncListener listener = mListener;
        if (listener != null) listener.onSyncError(error);
    }
}
