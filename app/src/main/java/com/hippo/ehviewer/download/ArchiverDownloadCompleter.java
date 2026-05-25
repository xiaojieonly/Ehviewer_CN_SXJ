package com.hippo.ehviewer.download;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.gallery.GalleryProvider2;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.ehviewer.util.GZIPUtils;
import com.hippo.lib.yorozuya.StringUtils;
import com.hippo.lib.yorozuya.Utilities;
import com.hippo.unifile.UniFile;
import com.hippo.util.FileUtils;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 归档系统 DownloadManager 任务的完成处理：Application 级广播、enqueue 后补查、轮询兜底、进程重启恢复。
 */
public class ArchiverDownloadCompleter {

    private static final String TAG = "ArchiverDownloadCompleter";

    private static final int MAX_ARCHIVER_BASENAME_UTF8_BYTES =
            255 - ".zip".getBytes(StandardCharsets.UTF_8).length;

    @Nullable
    private static ArchiverDownloadCompleter sInstance;

    private final Context appContext;
    private final Handler mainHandler;
    private final Set<Long> handlingIds = new HashSet<>();

    private boolean receiverRegistered;
    @Nullable
    private BroadcastReceiver downloadReceiver;

    private ArchiverDownloadCompleter(Context appContext) {
        this.appContext = appContext;
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static ArchiverDownloadCompleter getInstance(@Nullable Context context) {
        if (sInstance == null && context != null) {
            sInstance = new ArchiverDownloadCompleter(context.getApplicationContext());
        }
        return sInstance;
    }

    public static void resumePendingDownloads(Context context) {
        ArchiverDownloadCompleter completer = getInstance(context);
        if (completer == null) {
            return;
        }
        completer.ensureReceiverRegistered();
        for (long downloadId : Settings.getPendingArchiverDownloadIds()) {
            completer.checkAndHandleStatus(downloadId);
        }
    }

    public synchronized void ensureReceiverRegistered() {
        if (receiverRegistered) {
            return;
        }
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                    return;
                }
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (downloadId < 0) {
                    return;
                }
                checkAndHandleStatus(downloadId);
            }
        };
        ContextCompat.registerReceiver(
                appContext,
                downloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private synchronized void unregisterReceiverIfIdle() {
        if (!receiverRegistered || Settings.hasPendingArchiverDownloads()) {
            return;
        }
        if (downloadReceiver != null) {
            try {
                appContext.unregisterReceiver(downloadReceiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver already unregistered", e);
            }
            downloadReceiver = null;
        }
        receiverRegistered = false;
    }

    /**
     * enqueue 之后调用，捕获「注册前已完成」的竞态。
     */
    public void checkAndHandleStatus(long downloadId) {
        if (downloadId < 0 || Settings.getArchiverDownload(downloadId) == null) {
            return;
        }
        DownloadManager dm = (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) {
            return;
        }
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor c = dm.query(query)) {
            if (c == null || !c.moveToFirst()) {
                return;
            }
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            switch (status) {
                case DownloadManager.STATUS_SUCCESSFUL:
                    Log.i(TAG, "Download successful, id=" + downloadId);
                    handleSuccessfulDownload(c);
                    break;
                case DownloadManager.STATUS_FAILED:
                    Log.i(TAG, "Download failed, id=" + downloadId);
                    handleFailedDownload(downloadId);
                    break;
                default:
                    break;
            }
        }
    }

    private boolean tryBeginHandling(long downloadId) {
        synchronized (handlingIds) {
            if (handlingIds.contains(downloadId)) {
                return false;
            }
            handlingIds.add(downloadId);
            return true;
        }
    }

    private void endHandling(long downloadId) {
        synchronized (handlingIds) {
            handlingIds.remove(downloadId);
        }
    }

    private void handleFailedDownload(long downloadId) {
        GalleryInfo info = Settings.getArchiverDownload(downloadId);
        if (info == null) {
            return;
        }
        Settings.deleteArchiverDownloadId(info.gid);
        Settings.deleteArchiverDownload(downloadId);
        mainHandler.post(() ->
                Toast.makeText(appContext, R.string.download_state_failed, Toast.LENGTH_LONG).show());
        unregisterReceiverIfIdle();
    }

    private void handleSuccessfulDownload(Cursor cursor) {
        long downloadId = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
        if (!tryBeginHandling(downloadId)) {
            return;
        }
        GalleryInfo galleryInfo = Settings.getArchiverDownload(downloadId);
        if (galleryInfo == null) {
            endHandling(downloadId);
            return;
        }
        try {
            unzipAndImportFile(cursor, galleryInfo, downloadId);
        } catch (IllegalArgumentException | URISyntaxException e) {
            Log.e(TAG, e.getMessage(), e);
            endHandling(downloadId);
            mainHandler.post(() ->
                    Toast.makeText(appContext, R.string.download_state_failed, Toast.LENGTH_LONG).show());
        }
    }

    private void unzipAndImportFile(Cursor cursor, GalleryInfo galleryInfo, long downloadId)
            throws IllegalArgumentException, URISyntaxException {
        String path = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
        Uri uri = Uri.parse(path);
        File tempDir = AppConfig.getExternalTempDir();
        if (tempDir == null) {
            endHandling(downloadId);
            return;
        }
        String fileName = createFileName(galleryInfo.title, galleryInfo.gid);
        String tempFilePath = tempDir.getPath() + "/" + fileName;

        new Thread(() -> {
            File tempZipFile = null;
            try {
                String zipFilePath;
                if ("file".equals(uri.getScheme())) {
                    File zipFile = new File(uri.getPath());
                    zipFilePath = zipFile.getPath();
                } else {
                    tempZipFile = new File(tempDir, fileName + ".zip");
                    UniFile sourceFile = UniFile.fromUri(appContext, uri);
                    if (sourceFile == null) {
                        Log.e(TAG, "Cannot access source file: " + uri);
                        postImportFailed(downloadId);
                        return;
                    }
                    UniFile destFile = UniFile.fromFile(tempZipFile);
                    if (destFile == null || !FileUtils.copyFile(sourceFile, destFile, false)) {
                        Log.e(TAG, "Failed to copy zip file to temp location");
                        postImportFailed(downloadId);
                        return;
                    }
                    zipFilePath = tempZipFile.getPath();
                }

                if (!GZIPUtils.UnZipFolder(zipFilePath, tempFilePath)) {
                    postImportFailed(downloadId);
                    return;
                }
                importGallery(tempFilePath, galleryInfo, downloadId);
            } catch (Exception e) {
                Log.e(TAG, "Error in unzipAndImportFile", e);
                postImportFailed(downloadId);
            } finally {
                if (tempZipFile != null && tempZipFile.exists()) {
                    tempZipFile.delete();
                }
            }
        }).start();
    }

    private void postImportFailed(long downloadId) {
        endHandling(downloadId);
        mainHandler.post(() ->
                Toast.makeText(appContext, R.string.download_state_failed, Toast.LENGTH_LONG).show());
    }

    private void importGallery(String tempFilePath, GalleryInfo galleryInfo, long downloadId) {
        if (tempFilePath.isEmpty()) {
            return;
        }
        File tempFile = new File(tempFilePath);
        File importRoot = resolveImportRoot(tempFile);
        if (importRoot == null) {
            postImportFailed(downloadId);
            return;
        }
        List<File> tempPictures = collectImageFiles(importRoot);
        if (tempPictures.isEmpty()) {
            Log.e(TAG, "No image files found under: " + tempFilePath);
            postImportFailed(downloadId);
            return;
        }
        Collections.sort(tempPictures, (file1, file2) -> file1.getName().compareTo(file2.getName()));

        SpiderDen spiderDen = new SpiderDen(galleryInfo);
        spiderDen.setMode(SpiderQueen.MODE_DOWNLOAD);
        spiderDen.prepareDownloadStorage();
        UniFile downloadDir = spiderDen.getDownloadDir();
        if (downloadDir == null) {
            postImportFailed(downloadId);
            return;
        }
        int copiedCount = 0;
        try {
            for (int i = 0; i < tempPictures.size(); i++) {
                File picture = tempPictures.get(i);
                if (!picture.isFile() || !picture.exists()) {
                    Log.w(TAG, "Skip missing file: " + picture.getPath());
                    continue;
                }
                String extension = getImageExtension(picture.getName());
                String newName = SpiderDen.generateImageFilename(i, extension);

                UniFile destFile = downloadDir.findFile(newName);
                if (destFile != null && destFile.exists() && !destFile.delete()) {
                    continue;
                }
                destFile = downloadDir.createFile(newName);
                if (destFile == null) {
                    Log.e(TAG, "Failed to create file: " + newName);
                    continue;
                }
                UniFile sourceFile = UniFile.fromFile(picture);
                if (sourceFile == null) {
                    Log.e(TAG, "Cannot open source file: " + picture.getPath());
                    destFile.delete();
                    continue;
                }
                if (!FileUtils.copyFile(sourceFile, destFile, false)) {
                    Log.e(TAG, "Failed to copy file: " + picture.getName() + " to " + newName);
                    destFile.delete();
                    continue;
                }
                copiedCount++;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in importGallery", e);
            postImportFailed(downloadId);
            return;
        }
        if (copiedCount == 0) {
            Log.e(TAG, "No images were copied from: " + tempFilePath);
            postImportFailed(downloadId);
            return;
        }
        if (!com.hippo.lib.yorozuya.FileUtils.delete(tempFile)) {
            tempFile.deleteOnExit();
        }
        String finalFileName = tempFile.getName();
        mainHandler.post(() -> {
            String labelName = appContext.getString(R.string.download_label_archiver);
            com.hippo.ehviewer.download.DownloadManager manager =
                    EhApplication.getDownloadManager(appContext);
            manager.addLabel(labelName);
            manager.addDownload(galleryInfo, labelName, DownloadInfo.STATE_FINISH);
            Toast.makeText(appContext,
                    appContext.getString(R.string.stat_download_done_line_succeeded, finalFileName),
                    Toast.LENGTH_LONG).show();
            Settings.deleteArchiverDownloadId(galleryInfo.gid);
            Settings.deleteArchiverDownload(downloadId);
            endHandling(downloadId);
            unregisterReceiverIfIdle();
        });
    }

    @Nullable
    private static File resolveImportRoot(File dir) {
        if (!dir.isDirectory()) {
            return null;
        }
        File[] children = dir.listFiles();
        if (children == null) {
            return null;
        }
        boolean hasImageAtLevel = false;
        File onlySubdir = null;
        int subdirCount = 0;
        for (File child : children) {
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

    private static List<File> collectImageFiles(File root) {
        List<File> images = new ArrayList<>();
        collectImageFilesRecursive(root, images);
        return images;
    }

    private static void collectImageFilesRecursive(File dir, List<File> images) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectImageFilesRecursive(child, images);
            } else if (isImageFile(child)) {
                images.add(child);
            }
        }
    }

    private static boolean isImageFile(File file) {
        if (!file.isFile()) {
            return false;
        }
        String lower = file.getName().toLowerCase(Locale.ROOT);
        return StringUtils.endsWith(lower, GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS);
    }

    private static String getImageExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String extension = dot >= 0 ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
        if (Utilities.contain(GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS, extension)) {
            return extension;
        }
        return GalleryProvider2.SUPPORT_IMAGE_EXTENSIONS[0];
    }

    public static String createFileName(@Nullable String name, long gid) {
        String result = name == null ? "" : com.hippo.lib.yorozuya.FileUtils.sanitizeFilename(name);
        result = truncateUtf8ToMaxBytes(result, MAX_ARCHIVER_BASENAME_UTF8_BYTES);
        if (result.isEmpty()) {
            result = gid > 0 ? "archiver_" + gid : "archiver";
        }
        return result;
    }

    private static String truncateUtf8ToMaxBytes(String s, int maxBytes) {
        if (s == null || s.isEmpty() || maxBytes <= 0) {
            return s == null ? "" : s;
        }
        int byteCount = 0;
        int cutCharEnd = 0;
        for (int i = 0; i < s.length(); ) {
            char ch = s.charAt(i);
            int charUtf8Bytes;
            int charWidth = 1;
            if (ch <= 0x7F) {
                charUtf8Bytes = 1;
            } else if (ch <= 0x7FF) {
                charUtf8Bytes = 2;
            } else if (Character.isHighSurrogate(ch)) {
                charUtf8Bytes = 4;
                charWidth = 2;
                if (i + 1 >= s.length()) {
                    break;
                }
            } else {
                charUtf8Bytes = 3;
            }
            if (byteCount + charUtf8Bytes > maxBytes) {
                break;
            }
            byteCount += charUtf8Bytes;
            i += charWidth;
            cutCharEnd = i;
        }
        return cutCharEnd < s.length() ? s.substring(0, cutCharEnd) : s;
    }
}
