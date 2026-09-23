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

package com.hippo.ehviewer.ui.scene.download.part;

import static com.hippo.util.FileUtils.getFileName;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Process;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.R;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.lib.yorozuya.SimpleHandler;
import com.hippo.lib.yorozuya.thread.PriorityThreadFactory;
import com.hippo.lib.yorozuya.thread.SerialThreadExecutor;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * 本地下载压缩包导入。
 */
public class DownloadArchiveImporter {

    private static final String TAG = DownloadArchiveImporter.class.getSimpleName();

    public interface Host {
        /** 返回当前导入目标标签。 */
        @Nullable
        String getArchiveImportLabel();

        /** 判断导入结果是否仍可在此页面显示。 */
        boolean canShowArchiveImportResult(DownloadManager manager);

        @Nullable
        Context getEHContext();

        String getString(int resId);

        void runOnUiThread(Runnable runnable);

        void updateForLabel();

        void updateView();

        @Nullable
        DownloadManager getDownloadManager();
    }

    @NonNull
    private final Host mHost;

    /** 绑定导入界面提供的上下文和状态接口。 */
    public DownloadArchiveImporter(@NonNull Host host) {
        mHost = host;
    }

    private static final int MAX_ARCHIVE_IMPORT_COUNT = 50;
    private static final Executor ARCHIVE_IMPORT_EXECUTOR = new SerialThreadExecutor(
            3000L, new LinkedList<>(),
            new PriorityThreadFactory("ArchiveImport", Process.THREAD_PRIORITY_BACKGROUND));
    private static long sLastLocalGalleryId;

    /** 打开支持多选的归档选择器并请求持久读权限。 */
    public void importLocalArchive(@NonNull ActivityResultLauncher<Intent> filePickerLauncher) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/vnd.rar",
                "application/x-rar",
                "application/rar",
                "application/x-cbz",
                "application/x-cbr"
        });
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // 保留持久读权限，保证应用重启后仍可访问归档。
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        try {
            filePickerLauncher.launch(Intent.createChooser(intent, mHost.getString(R.string.import_archive_title)));
        } catch (Exception e) {
            Context context = mHost.getEHContext();
            if (context != null) {
                Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** 获取选择结果的权限，捕获目标标签并提交串行后台校验。 */
    public void handleSelectedFiles(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) {
            return;
        }

        Intent data = result.getData();
        List<Uri> selectedUris = collectSelectedUris(data);
        if (selectedUris.isEmpty()) {
            return;
        }

        Context context = mHost.getEHContext();
        DownloadManager downloadManager = mHost.getDownloadManager();
        if (context == null || downloadManager == null) {
            return;
        }
        if (selectedUris.size() > MAX_ARCHIVE_IMPORT_COUNT) {
            Toast.makeText(context, context.getString(R.string.import_archive_too_many,
                    selectedUris.size(), MAX_ARCHIVE_IMPORT_COUNT), Toast.LENGTH_LONG).show();
            return;
        }

        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (takeFlags == 0) {
            takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        }

        List<Uri> permittedUris = new ArrayList<>(selectedUris.size());
        int permissionFailureCount = 0;
        for (Uri uri : selectedUris) {
            try {
                context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
                permittedUris.add(uri);
                Log.d(TAG, "Successfully obtained persistent URI permission for: " + uri);
            } catch (SecurityException e) {
                permissionFailureCount++;
                Log.e(TAG, "Failed to obtain persistent URI permission for: " + uri, e);
            } catch (Exception e) {
                permissionFailureCount++;
                Log.e(TAG, "Unexpected error when obtaining URI permission for: " + uri, e);
            }
        }

        if (permittedUris.isEmpty()) {
            Toast.makeText(context, R.string.archive_permission_lost, Toast.LENGTH_LONG).show();
            return;
        }

        Toast.makeText(context, R.string.import_archive_processing, Toast.LENGTH_LONG).show();

        Context applicationContext = context.getApplicationContext();
        String targetLabel = mHost.getArchiveImportLabel();
        int selectedCount = selectedUris.size();
        int finalPermissionFailureCount = permissionFailureCount;
        WeakReference<Host> sceneReference = new WeakReference<>(mHost);
        try {
            ARCHIVE_IMPORT_EXECUTOR.execute(() -> DownloadArchiveImporter.processArchiveFiles(applicationContext,
                    downloadManager, permittedUris, targetLabel, selectedCount,
                    finalPermissionFailureCount, sceneReference));
        } catch (RuntimeException e) {
            for (Uri uri : permittedUris) {
                downloadManager.releaseArchiveUriPermissionIfUnused(uri.toString());
            }
            Log.e(TAG, "Failed to schedule archive import", e);
            Toast.makeText(context, R.string.import_archive_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /** 合并单选和多选 URI，按选择顺序去重。 */
    private static List<Uri> collectSelectedUris(Intent data) {
        Set<Uri> selectedUris = new LinkedHashSet<>();
        Uri singleUri = data.getData();
        if (singleUri != null) {
            selectedUris.add(singleUri);
        }
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    selectedUris.add(uri);
                }
            }
        }
        return new ArrayList<>(selectedUris);
    }

    /** 后台检查文件访问权限和格式，不操作界面或共享下载集合。 */
    private static void processArchiveFiles(Context context, DownloadManager downloadManager,
                                            List<Uri> uris, @Nullable String targetLabel,
                                            int selectedCount, int permissionFailureCount,
                                            WeakReference<Host> sceneReference) {
        List<PendingArchiveImport> pendingImports = new ArrayList<>(uris.size());
        int invalidFormatCount = 0;
        int failedCount = permissionFailureCount;

        for (Uri uri : uris) {
            try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
                if (inputStream == null) {
                    failedCount++;
                    continue;
                }
            } catch (Exception e) {
                Log.e(TAG, "Cannot access file even with persistent permission", e);
                failedCount++;
                continue;
            }

            try {
                String fileName = getFileName(context, uri);
                if (!isValidArchiveFormat(fileName)) {
                    invalidFormatCount++;
                    continue;
                }

                pendingImports.add(new PendingArchiveImport(uri, fileName));
            } catch (Exception e) {
                failedCount++;
                Log.e(TAG, "Failed to process archive file: " + uri, e);
            }
        }

        postArchiveImportResult(context, downloadManager, sceneReference, selectedCount,
                uris, pendingImports, targetLabel, invalidFormatCount, failedCount);
    }

    /** 在主线程生成已导入 URI 快照，避免重复导入。 */
    private static Set<String> getImportedArchiveUriSnapshot(DownloadManager downloadManager) {
        Set<String> snapshot = new HashSet<>();
        for (DownloadInfo info : downloadManager.getAllDownloadInfoList()) {
            if (info.archiveUri != null) {
                snapshot.add(info.archiveUri);
            }
        }
        return snapshot;
    }

    /** 在主线程批量入库、回收未使用权限，并向仍可见的页面报告结果。 */
    private static void postArchiveImportResult(Context context,
                                                DownloadManager downloadManager,
                                                WeakReference<Host> sceneReference,
                                                int selectedCount,
                                                List<Uri> permissionUris,
                                                List<PendingArchiveImport> pendingImports,
                                                @Nullable String targetLabel,
                                                int invalidFormatCount, int failedCount) {
        SimpleHandler.getInstance().post(() -> {
            int alreadyImportedCount = 0;
            int committedFailedCount = failedCount;
            List<DownloadInfo> downloadList = new ArrayList<>(pendingImports.size());
            Set<String> importedArchiveUris;

            // 标签可能在后台校验期间被删除或改名，不能静默重建旧标签。
            if (targetLabel != null && !downloadManager.containLabel(targetLabel)) {
                committedFailedCount += pendingImports.size();
                importedArchiveUris = null;
                Log.w(TAG, "Archive import target label no longer exists: " + targetLabel);
            } else {
                try {
                    importedArchiveUris = getImportedArchiveUriSnapshot(downloadManager);
                } catch (RuntimeException e) {
                    committedFailedCount += pendingImports.size();
                    importedArchiveUris = null;
                    Log.e(TAG, "Failed to create imported archive URI snapshot", e);
                }
            }

            if (importedArchiveUris != null) {
                for (PendingArchiveImport pendingImport : pendingImports) {
                    String archiveUri = pendingImport.uri.toString();
                    // 以 URI 全局去重，跨标签也不能创建同一归档的重复记录。
                    if (!importedArchiveUris.add(archiveUri)) {
                        alreadyImportedCount++;
                        continue;
                    }
                    try {
                        downloadList.add(createArchiveDownloadInfo(downloadManager,
                                pendingImport.uri, pendingImport.fileName, targetLabel));
                    } catch (RuntimeException e) {
                        committedFailedCount++;
                        Log.e(TAG, "Failed to prepare imported archive: " + archiveUri, e);
                    }
                }

                if (!downloadList.isEmpty()) {
                    int preparedCount = downloadList.size();
                    try {
                        downloadList = downloadManager.addDownload(downloadList);
                        committedFailedCount += preparedCount - downloadList.size();
                    } catch (RuntimeException e) {
                        committedFailedCount += downloadList.size();
                        downloadList.clear();
                        Log.e(TAG, "Failed to commit imported archives", e);
                    }
                }
            }

            // 失败项释放授权；已有或本次成功记录仍引用的 URI 由管理器保留。
            for (Uri uri : permissionUris) {
                downloadManager.releaseArchiveUriPermissionIfUnused(uri.toString());
            }

            int importedCount = downloadList.size();
            // 后台入库可完成，但离开页面后不能再更新旧界面。
            Host scene = sceneReference.get();
            if (scene == null || !scene.canShowArchiveImportResult(downloadManager)) {
                return;
            }

            showArchiveImportResult(context, selectedCount, importedCount,
                    invalidFormatCount, alreadyImportedCount, committedFailedCount);
            scene.updateForLabel();
            scene.updateView();
        });
    }

    /** 按不受系统语言影响的扩展名判断支持的归档格式。 */
    private static boolean isValidArchiveFormat(@Nullable String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".zip") || lowerName.endsWith(".rar") ||
                lowerName.endsWith(".cbz") || lowerName.endsWith(".cbr");
    }

    /** 按成功、重复、格式错误和失败数量展示导入结果。 */
    private static void showArchiveImportResult(Context context, int selectedCount,
                                                int importedCount, int invalidFormatCount,
                                                int alreadyImportedCount, int failedCount) {
        if (importedCount == selectedCount) {
            Toast.makeText(context, R.string.import_archive_success, Toast.LENGTH_SHORT).show();
        } else if (importedCount == 0) {
            int message;
            if (invalidFormatCount == selectedCount) {
                message = R.string.import_archive_invalid_format;
            } else if (alreadyImportedCount == selectedCount) {
                message = R.string.import_archive_already_imported;
            } else {
                message = R.string.import_archive_failed;
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
        } else {
            int skippedCount = invalidFormatCount + alreadyImportedCount + failedCount;
            String message = context.getString(R.string.import_archive_success) + ": " +
                    importedCount + "/" + selectedCount + "\n" +
                    context.getString(R.string.import_archive_failed) + ": " +
                    skippedCount + "/" + selectedCount;
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
        }
    }

    /** 使用目标标签和唯一编号创建本地归档下载记录。 */
    private static DownloadInfo createArchiveDownloadInfo(DownloadManager downloadManager,
                                                          Uri uri, String fileName,
                                                          @Nullable String label) {
        DownloadInfo downloadInfo = new DownloadInfo();
        downloadInfo.gid = nextLocalGalleryId(downloadManager);
        downloadInfo.token = "";
        downloadInfo.title = fileName.replaceAll("\\.[^.]*$", "");
        downloadInfo.titleJpn = null;
        downloadInfo.thumb = null;
        downloadInfo.category = EhUtils.UNKNOWN;
        downloadInfo.posted = null;
        downloadInfo.uploader = "Local Archive";
        downloadInfo.rating = -1.0f;
        downloadInfo.state = DownloadInfo.STATE_FINISH;
        downloadInfo.legacy = 0;
        downloadInfo.time = System.currentTimeMillis();
        downloadInfo.label = label;
        downloadInfo.total = 0;
        downloadInfo.finished = 0;
        downloadInfo.archiveUri = uri.toString();
        return downloadInfo;
    }

    /** 生成不与已有下载记录冲突的递增本地编号。 */
    private static synchronized long nextLocalGalleryId(DownloadManager downloadManager) {
        long galleryId = Math.max(System.currentTimeMillis(), sLastLocalGalleryId + 1L);
        while (downloadManager.containDownloadInfo(galleryId)) {
            galleryId++;
        }
        sLastLocalGalleryId = galleryId;
        return galleryId;
    }

    private static final class PendingArchiveImport {
        private final Uri uri;
        private final String fileName;

        /** 保存后台校验通过但尚未入库的归档信息。 */
        private PendingArchiveImport(Uri uri, String fileName) {
            this.uri = uri;
            this.fileName = fileName;
        }
    }
}
