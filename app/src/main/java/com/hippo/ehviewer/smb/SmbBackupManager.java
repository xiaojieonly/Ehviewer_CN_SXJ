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

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.unifile.SmbUri;
import com.hippo.unifile.SmbUriHandler;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

        // Only sync when download location is local (not SMB)
        UniFile downloadLoc = com.hippo.ehviewer.Settings.getDownloadLocation();
        if (downloadLoc == null || (downloadLoc.getUri() != null && "smb".equals(downloadLoc.getUri().getScheme()))) {
            return;
        }

        UniFile localDir = SpiderDen.getGalleryDownloadDir(galleryInfo);
        if (localDir == null || !localDir.isDirectory()) {
            return;
        }

        UniFile smbRoot = getSmbUniFile(config);
        if (smbRoot == null) {
            return;
        }

        String dirname = localDir.getName();
        if (dirname == null) {
            return;
        }

        try {
            UniFile smbGalleryDir = smbRoot.subFile(dirname);
            if (smbGalleryDir == null) {
                return;
            }
            smbGalleryDir.ensureDir();
            copyDirectory(localDir, smbGalleryDir);
        } catch (Exception e) {
            Log.e(TAG, "Failed to sync gallery " + dirname + " to SMB backup", e);
        }
    }

    private void syncAllInternal() {
        try {
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

            UniFile smbRoot = getSmbUniFile(config);
            if (smbRoot == null) {
                notifyError("SMB backup location is unavailable");
                return;
            }

            notifyStart();

            UniFile[] localDirs = localRoot.listFiles();
            if (localDirs == null || localDirs.length == 0) {
                notifyComplete(0, 0);
                return;
            }

            int total = localDirs.length;
            int successCount = 0;
            int failCount = 0;

            for (int i = 0; i < total; i++) {
                UniFile localDir = localDirs[i];
                if (!localDir.isDirectory()) {
                    continue;
                }

                String dirname = localDir.getName();
                if (dirname == null || dirname.startsWith(".")) {
                    continue;
                }

                notifyProgress(i + 1, total, dirname);

                try {
                    UniFile smbGalleryDir = smbRoot.subFile(dirname);
                    if (smbGalleryDir == null) {
                        failCount++;
                        continue;
                    }
                    smbGalleryDir.ensureDir();
                    copyDirectory(localDir, smbGalleryDir);
                    successCount++;
                } catch (Exception e) {
                    Log.e(TAG, "Failed to sync " + dirname + " to SMB backup", e);
                    failCount++;
                }
            }

            notifyComplete(successCount, failCount);
        } finally {
            mSyncingAll.set(false);
        }
    }

    private void copyDirectory(@NonNull UniFile src, @NonNull UniFile dst) throws IOException {
        UniFile[] files = src.listFiles();
        if (files == null) {
            return;
        }

        for (UniFile file : files) {
            String name = file.getName();
            if (name == null) {
                continue;
            }

            if (file.isDirectory()) {
                UniFile subDir = dst.subFile(name);
                if (subDir != null) {
                    subDir.ensureDir();
                    copyDirectory(file, subDir);
                }
            } else {
                copyFile(file, dst);
            }
        }
    }

    private void copyFile(@NonNull UniFile srcFile, @NonNull UniFile dstDir) throws IOException {
        String name = srcFile.getName();
        if (name == null) {
            return;
        }

        UniFile dstFile = dstDir.subFile(name);
        if (dstFile == null) {
            return;
        }

        if (dstFile.exists() && dstFile.length() == srcFile.length()) {
            return;
        }

        try (InputStream is = srcFile.openInputStream();
             OutputStream os = dstFile.openOutputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }

    @Nullable
    private UniFile getSmbUniFile(@NonNull SmbConfig config) {
        try {
            SmbUri smbUri = config.toUri();
            return new SmbUriHandler().fromUri(mContext, smbUri.toUri());
        } catch (Exception e) {
            Log.e(TAG, "Failed to create SMB UniFile", e);
            return null;
        }
    }

    @Nullable
    private UniFile getLocalDownloadDir() {
        try {
            return com.hippo.ehviewer.Settings.getDownloadLocation();
        } catch (Exception e) {
            Log.e(TAG, "Failed to get local download directory", e);
            return null;
        }
    }

    private void notifyStart() {
        SyncListener listener = mListener;
        if (listener != null) {
            listener.onSyncStart();
        }
    }

    private void notifyProgress(int current, int total, String name) {
        SyncListener listener = mListener;
        if (listener != null) {
            listener.onSyncProgress(current, total, name);
        }
    }

    private void notifyComplete(int successCount, int failCount) {
        SyncListener listener = mListener;
        if (listener != null) {
            listener.onSyncComplete(successCount, failCount);
        }
    }

    private void notifyError(String error) {
        SyncListener listener = mListener;
        if (listener != null) {
            listener.onSyncError(error);
        }
    }
}
