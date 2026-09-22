/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui.scene.download.part;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.lib.yorozuya.StringUtils;
import com.hippo.lib.yorozuya.Utilities;
import com.hippo.unifile.UniFile;
import com.hippo.util.FileUtils;
import com.hippo.util.NaturalComparator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 将本地图片文件夹复制为已完成画廊。
 */
public class DownloadAlbumImporter {

    private static final String TAG = DownloadAlbumImporter.class.getSimpleName();

    public static final String URI_PREFIX = "local-album:";
    private static final String LOCAL_TOKEN = "local";
    private static final String LOCAL_THUMB = "local";
    private static final String LOCAL_UPLOADER = "Local Album";

    public interface Host {
        @Nullable
        Context getEHContext();

        String getString(int resId);

        void runOnUiThread(Runnable runnable);

        void updateForLabel();

        void updateView();

        @Nullable
        DownloadManager getDownloadManager();

        @Nullable
        String getLabel();
    }

    @NonNull
    private final Host mHost;

    public DownloadAlbumImporter(@NonNull Host host) {
        mHost = host;
    }

    public static boolean isLocalAlbum(@Nullable DownloadInfo info) {
        return info != null && info.archiveUri != null && info.archiveUri.startsWith(URI_PREFIX);
    }

    public void importLocalAlbum(@NonNull ActivityResultLauncher<Intent> folderPickerLauncher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);

        try {
            folderPickerLauncher.launch(intent);
        } catch (Exception e) {
            Context context = mHost.getEHContext();
            if (context != null) {
                Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void handleSelectedFolder(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }

        Uri uri = result.getData().getData();
        if (uri == null) {
            return;
        }

        Context context = mHost.getEHContext();
        if (context == null) {
            return;
        }

        try {
            context.getContentResolver().takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d(TAG, "Successfully obtained persistent URI permission for: " + uri);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to obtain persistent URI permission for: " + uri, e);
            Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_LONG).show();
            return;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error when obtaining URI permission for: " + uri, e);
            Toast.makeText(context, R.string.import_album_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(context, R.string.import_album_processing, Toast.LENGTH_LONG).show();
        new Thread(() -> processAlbumFolder(uri)).start();
    }

    private void processAlbumFolder(Uri uri) {
        Context context = mHost.getEHContext();
        if (context == null) {
            return;
        }

        UniFile downloadDir = null;
        long gid = -1L;
        try {
            UniFile sourceDir = UniFile.fromTreeUri(context, uri);
            if (sourceDir == null || !sourceDir.isDirectory()) {
                showFailed();
                return;
            }

            String archiveUri = URI_PREFIX + uri.toString();
            DownloadManager downloadManager = mHost.getDownloadManager();
            if (downloadManager != null && isAlreadyImported(downloadManager, archiveUri)) {
                showToast(R.string.import_album_already_imported);
                return;
            }

            UniFile importRoot = resolveImportRoot(sourceDir);
            if (importRoot == null) {
                showFailed();
                return;
            }

            List<UniFile> images = collectImageFiles(importRoot);
            if (images.isEmpty()) {
                showToast(R.string.import_album_no_images);
                return;
            }

            Collections.sort(images, (left, right) ->
                    new NaturalComparator().compare(left.getName(), right.getName()));

            if (Settings.getDownloadLocation() == null) {
                showToast(R.string.settings_download_invalid_download_location);
                return;
            }

            gid = System.currentTimeMillis();
            DownloadInfo downloadInfo = createAlbumDownloadInfo(gid, sourceDir, archiveUri, images.size());
            SpiderDen spiderDen = new SpiderDen(downloadInfo);
            spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD);
            if (!spiderDen.prepareDownloadStorage()) {
                cleanupFailedImport(gid, null);
                showFailed();
                return;
            }

            downloadDir = spiderDen.getDownloadDir();
            if (downloadDir == null) {
                cleanupFailedImport(gid, null);
                showFailed();
                return;
            }

            int copiedCount = copyImages(images, downloadDir);
            if (copiedCount == 0) {
                cleanupFailedImport(gid, downloadDir);
                showFailed();
                return;
            }

            downloadInfo.total = copiedCount;
            downloadInfo.finished = copiedCount;
            writeSpiderInfo(context, spiderDen, gid, copiedCount);
            copyThumb(images.get(0), downloadDir);

            if (downloadManager == null) {
                cleanupFailedImport(gid, downloadDir);
                showFailed();
                return;
            }

            List<DownloadInfo> downloadList = new ArrayList<>();
            downloadList.add(downloadInfo);
            downloadManager.addDownload(downloadList);
            int importedCount = copiedCount;
            mHost.runOnUiThread(() -> {
                Toast.makeText(context,
                        context.getString(R.string.import_album_success, importedCount),
                        Toast.LENGTH_SHORT).show();
                mHost.updateForLabel();
                mHost.updateView();
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to process album folder", e);
            cleanupFailedImport(gid, downloadDir);
            showFailed();
        }
    }

    private DownloadInfo createAlbumDownloadInfo(long gid, UniFile sourceDir, String archiveUri, int imageCount) {
        DownloadInfo downloadInfo = new DownloadInfo();
        downloadInfo.gid = gid;
        downloadInfo.token = LOCAL_TOKEN;
        String folderName = sourceDir.getName();
        if (folderName == null || folderName.trim().isEmpty()) {
            folderName = "imported_album_" + gid;
        }
        downloadInfo.title = folderName;
        downloadInfo.titleJpn = null;
        downloadInfo.thumb = LOCAL_THUMB;
        downloadInfo.category = EhUtils.UNKNOWN;
        downloadInfo.posted = null;
        downloadInfo.uploader = LOCAL_UPLOADER;
        downloadInfo.rating = -1.0f;
        downloadInfo.state = DownloadInfo.STATE_FINISH;
        downloadInfo.legacy = 0;
        downloadInfo.time = gid;
        downloadInfo.label = mHost.getLabel();
        downloadInfo.total = imageCount;
        downloadInfo.finished = imageCount;
        downloadInfo.archiveUri = archiveUri;
        return downloadInfo;
    }

    private int copyImages(List<UniFile> images, UniFile downloadDir) {
        int copiedCount = 0;
        for (int i = 0; i < images.size(); i++) {
            UniFile picture = images.get(i);
            if (picture == null || !picture.isFile()) {
                continue;
            }
            String extension = getImageExtension(picture.getName());
            String newName = SpiderDen.generateImageFilename(copiedCount, extension);
            UniFile destFile = downloadDir.findFile(newName);
            if (destFile != null && destFile.exists() && !destFile.delete()) {
                continue;
            }
            destFile = downloadDir.createFile(newName);
            if (destFile == null) {
                Log.e(TAG, "Failed to create file: " + newName);
                continue;
            }
            if (!FileUtils.copyFile(picture, destFile, false)) {
                Log.e(TAG, "Failed to copy file: " + picture.getName() + " to " + newName);
                destFile.delete();
                continue;
            }
            copiedCount++;
        }
        return copiedCount;
    }

    private void writeSpiderInfo(Context context, SpiderDen spiderDen, long gid, int pages) {
        SpiderInfo spiderInfo = new SpiderInfo();
        spiderInfo.gid = gid;
        spiderInfo.token = LOCAL_TOKEN;
        spiderInfo.pages = pages;
        spiderInfo.previewPages = 0;
        spiderInfo.previewPerPage = 0;
        spiderInfo.startPage = 0;
        spiderInfo.pTokenMap = new SparseArray<>(pages);
        spiderInfo.writeNewSpiderInfoToLocal(spiderDen, context.getApplicationContext());
    }

    private void copyThumb(UniFile firstImage, UniFile downloadDir) {
        UniFile thumbFile = downloadDir.findFile(".thumb");
        if (thumbFile != null && thumbFile.exists()) {
            thumbFile.delete();
        }
        thumbFile = downloadDir.createFile(".thumb");
        if (thumbFile == null) {
            return;
        }
        if (!FileUtils.copyFile(firstImage, thumbFile, false)) {
            thumbFile.delete();
        }
    }

    @Nullable
    private UniFile resolveImportRoot(UniFile dir) {
        if (dir == null || !dir.isDirectory()) {
            return null;
        }
        UniFile[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        boolean hasImageAtLevel = false;
        UniFile onlySubdir = null;
        int subdirCount = 0;
        for (UniFile child : children) {
            if (child.isFile() && isImageFile(child)) {
                hasImageAtLevel = true;
            } else if (child.isDirectory()) {
                onlySubdir = child;
                subdirCount++;
            }
        }
        if (hasImageAtLevel) {
            return dir;
        }
        if (subdirCount == 1 && onlySubdir != null) {
            return resolveImportRoot(onlySubdir);
        }
        return dir;
    }

    private List<UniFile> collectImageFiles(UniFile root) {
        List<UniFile> images = new ArrayList<>();
        collectImageFilesRecursive(root, images);
        return images;
    }

    private void collectImageFilesRecursive(UniFile dir, List<UniFile> images) {
        UniFile[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (UniFile child : children) {
            if (child.isDirectory()) {
                collectImageFilesRecursive(child, images);
            } else if (isImageFile(child)) {
                images.add(child);
            }
        }
    }

    private boolean isImageFile(UniFile file) {
        if (file == null || !file.isFile()) {
            return false;
        }
        String name = file.getName();
        if (name == null) {
            return false;
        }
        return StringUtils.endsWith(name.toLowerCase(Locale.ROOT), GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS);
    }

    private String getImageExtension(String fileName) {
        if (fileName == null) {
            return GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0];
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
        if (Utilities.contain(GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS, extension)) {
            return extension;
        }
        return GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0];
    }

    private boolean isAlreadyImported(DownloadManager manager, String archiveUri) {
        List<DownloadInfo> all = manager.getAllDownloadInfoList();
        if (all == null) {
            return false;
        }
        for (DownloadInfo info : all) {
            if (archiveUri.equals(info.archiveUri)) {
                return true;
            }
        }
        return false;
    }

    private void cleanupFailedImport(long gid, @Nullable UniFile downloadDir) {
        if (downloadDir != null) {
            try {
                downloadDir.delete();
            } catch (Exception e) {
                Log.w(TAG, "Failed to delete incomplete album directory", e);
            }
        }
        if (gid > 0) {
            EhDB.removeDownloadDirname(gid);
        }
    }

    private void showFailed() {
        showToast(R.string.import_album_failed);
    }

    private void showToast(int resId) {
        Context context = mHost.getEHContext();
        if (context == null) {
            return;
        }
        mHost.runOnUiThread(() -> Toast.makeText(context, resId, Toast.LENGTH_SHORT).show());
    }
}
