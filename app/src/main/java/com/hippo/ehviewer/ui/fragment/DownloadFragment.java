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

package com.hippo.ehviewer.ui.fragment;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.NotificationCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.client.EhUtils;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.download.DownloadLogger;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.DirPickerActivity;
import com.hippo.ehviewer.ui.progress.ProgressDialogManager;
import com.hippo.ehviewer.task.TaskExecutor;
import com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask;
import com.hippo.ehviewer.task.impl.RebuildDownloadRecordsTask;
import com.hippo.ehviewer.task.impl.ScanDownloadFilesTask;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.ExecutorManager;
import com.hippo.util.ReadableTime;
import android.util.Log;

import java.io.File;
import com.hippo.yorozuya.IOUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DownloadFragment extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener,
        Preference.OnPreferenceClickListener {

    private static final String TAG = "DownloadFragment";
    public static final int REQUEST_CODE_PICK_IMAGE_DIR = 0;
    public static final int REQUEST_CODE_PICK_IMAGE_DIR_L = 1;
    private static final int REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE = 2;

    // 通知栏相关常量
    private static final String CHANNEL_ID_DOWNLOAD = "download_channel";
    private static final int NOTIFICATION_ID_BASE = 1000;
    private static final int NOTIFICATION_ID_IMPORT = NOTIFICATION_ID_BASE + 1;
    private static final int NOTIFICATION_ID_CLEAN = NOTIFICATION_ID_BASE + 2;
    private static final int NOTIFICATION_ID_REPAIR = NOTIFICATION_ID_BASE + 3;
    private static final int NOTIFICATION_ID_REBUILD = NOTIFICATION_ID_BASE + 4;

    public static final String KEY_DOWNLOAD_LOCATION = "download_location";
    public static final String KEY_EXPORT_DOWNLOAD_ITEMS = "export_download_items";
    public static final String KEY_IMPORT_DOWNLOAD_ITEMS = "import_download_items";
    public static final String KEY_CLEAN_INVALID_DOWNLOAD = "clean_invalid_download";
    public static final String KEY_REPAIR_ALL_DOWNLOADED_GALLERY = "repair_all_downloaded_gallery";
    public static final String KEY_REPAIR_UNKNOWN_CATEGORY_GALLERY = "repair_unknown_category_gallery";
    public static final String KEY_REBUILD_DOWNLOAD_RECORDS = "rebuild_download_records";
    public static final String KEY_VIEW_DOWNLOAD_LOGS = "view_download_logs";
    public static final String KEY_CLEAN_DOWNLOAD_LOGS = "clean_download_logs";
    public static final String KEY_RESET_MEDIA_SCAN = "reset_media_scan";

    @Nullable
    private Preference mDownloadLocation;
    
    // 下载日志记录器
    private final DownloadLogger mDownloadLogger = DownloadLogger.getInstance();

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        addPreferencesFromResource(R.xml.download_settings);
        
        // 初始化通知栏
        initNotificationChannel();

        Preference mediaScan = findPreference(Settings.KEY_MEDIA_SCAN);
        Preference enableMinDownloadSpeed = findPreference(Settings.KEY_ENABLE_MIN_DOWNLOAD_SPEED);
        Preference minDownloadSpeed = findPreference(Settings.KEY_MIN_DOWNLOAD_SPEED);
        Preference downloadThread = findPreference("download_thread");
        Preference imageResolution = findPreference(Settings.KEY_IMAGE_RESOLUTION);
        Preference enableDownloadTimeout = findPreference(Settings.KEY_ENABLE_DOWNLOAD_TIMEOUT);
        Preference downloadTimeout = findPreference(Settings.KEY_DOWNLOAD_TIMEOUT);
        Preference downloadLoggingEnabled = findPreference(Settings.KEY_DOWNLOAD_LOGGING_ENABLED);
        mDownloadLocation = findPreference(KEY_DOWNLOAD_LOCATION);
        Preference exportDownloadItems = findPreference(KEY_EXPORT_DOWNLOAD_ITEMS);
        Preference importDownloadItems = findPreference(KEY_IMPORT_DOWNLOAD_ITEMS);
        Preference cleanInvalidDownload = findPreference(KEY_CLEAN_INVALID_DOWNLOAD);
        Preference repairAllDownloadedGallery = findPreference(KEY_REPAIR_ALL_DOWNLOADED_GALLERY);
        Preference repairUnknownCategoryGallery = findPreference(KEY_REPAIR_UNKNOWN_CATEGORY_GALLERY);
        Preference rebuildDownloadRecords = findPreference(KEY_REBUILD_DOWNLOAD_RECORDS);
        Preference mergeDuplicateGallery = findPreference("merge_duplicate_gallery");
        Preference scanDownloadFiles = findPreference("scan_download_files");
        Preference viewDownloadLogs = findPreference(KEY_VIEW_DOWNLOAD_LOGS);
        Preference cleanDownloadLogs = findPreference(KEY_CLEAN_DOWNLOAD_LOGS);
        Preference resetMediaScan = findPreference(KEY_RESET_MEDIA_SCAN);
        Preference preloadImage = findPreference("preload_image");
        Preference imageResolutionPref = findPreference(Settings.KEY_IMAGE_RESOLUTION);

        onUpdateDownloadLocation();

        // Initialize summaries with current settings
        if (downloadThread != null) {
            downloadThread.setSummary(getString(R.string.settings_download_multi_thread_download_summary, String.valueOf(Settings.getMultiThreadDownload())));
        }
        if (imageResolution != null) {
            imageResolution.setSummary(getString(R.string.settings_download_image_resolution_summary, Settings.getImageResolution()));
        }
        if (downloadTimeout != null) {
            String timeoutStr = Settings.getDownloadTimeout() == 0 ? getString(R.string.download_timeout_unlimited) : String.valueOf(Settings.getDownloadTimeout());
            downloadTimeout.setSummary(getString(R.string.settings_download_timeout_summary, timeoutStr));
        }
        if(preloadImage != null){
            preloadImage.setSummary(getString(R.string.settings_download_preload_image_summary, String.valueOf(Settings.getPreloadImage())));
        }
        if(imageResolutionPref != null){
            imageResolutionPref.setSummary(getString(R.string.settings_download_image_resolution_summary, Settings.getImageResolution()));
        }


        if (mediaScan != null) {
            mediaScan.setOnPreferenceChangeListener(this);
        }
        if (enableMinDownloadSpeed != null) {
            enableMinDownloadSpeed.setOnPreferenceChangeListener(this);
        }
        if (minDownloadSpeed != null) {
            minDownloadSpeed.setOnPreferenceChangeListener(this);
        }
        if (imageResolution != null) {
            imageResolution.setOnPreferenceChangeListener(this);
        }
        if (enableDownloadTimeout != null) {
            enableDownloadTimeout.setOnPreferenceChangeListener(this);
        }
        if (downloadTimeout != null) {
            downloadTimeout.setOnPreferenceChangeListener(this);
        }
        if (downloadLoggingEnabled != null) {
            downloadLoggingEnabled.setOnPreferenceChangeListener(this);
        }

        if (mDownloadLocation != null) {
            mDownloadLocation.setOnPreferenceClickListener(this);
        }
        if (exportDownloadItems != null) {
            exportDownloadItems.setOnPreferenceClickListener(this);
        }
        if (importDownloadItems != null) {
            importDownloadItems.setOnPreferenceClickListener(this);
        }
        if (cleanInvalidDownload != null) {
            cleanInvalidDownload.setOnPreferenceClickListener(this);
        }
        if (repairAllDownloadedGallery != null) {
            repairAllDownloadedGallery.setOnPreferenceClickListener(this);
        }
        if (repairUnknownCategoryGallery != null) {
            repairUnknownCategoryGallery.setOnPreferenceClickListener(this);
        }
        if (rebuildDownloadRecords != null) {
            rebuildDownloadRecords.setOnPreferenceClickListener(this);
        }
        if (mergeDuplicateGallery != null) {
            mergeDuplicateGallery.setOnPreferenceClickListener(this);
        }
        if (scanDownloadFiles != null) {
            scanDownloadFiles.setOnPreferenceClickListener(this);
        }
        if (resetMediaScan != null) {
            resetMediaScan.setOnPreferenceClickListener(this);
        }
        if (viewDownloadLogs != null) {
            viewDownloadLogs.setOnPreferenceClickListener(this);
        }
        if (cleanDownloadLogs != null) {
            cleanDownloadLogs.setOnPreferenceClickListener(this);
        }
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_DOWNLOAD,
                    "下载管理",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("下载管理后台任务通知");
            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createProgressNotification(String title, String content, int progress, int max) {
        Intent intent = new Intent(requireContext(), requireActivity().getClass());
        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID_DOWNLOAD)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        if (max > 0) {
            builder.setProgress(max, progress, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private void updateNotification(int notificationId, String title, String content, int progress, int max) {
        Notification notification = createProgressNotification(title, content, progress, max);
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification);
    }

    private void cancelNotification(int notificationId) {
        NotificationManager notificationManager = (NotificationManager) requireContext().getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(notificationId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mDownloadLocation = null;
    }

    public void onUpdateDownloadLocation() {
        UniFile file = Settings.getDownloadLocation();
        if (mDownloadLocation != null) {
            if (file != null) {
                mDownloadLocation.setSummary(file.getUri().toString());
            } else {
                mDownloadLocation.setSummary(R.string.settings_download_invalid_download_location);
            }
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_DOWNLOAD_LOCATION.equals(key)) {
            int sdk = Build.VERSION.SDK_INT;
            if (sdk < Build.VERSION_CODES.KITKAT) {
                openDirPicker();
            } else if (sdk < Build.VERSION_CODES.LOLLIPOP) {
                showDirPickerDialogKK();
            } else {
                showDirPickerDialogL();
            }
            return true;
        } else if (KEY_EXPORT_DOWNLOAD_ITEMS.equals(key)) {
            exportDownloadItems();
            return true;
        } else if (KEY_IMPORT_DOWNLOAD_ITEMS.equals(key)) {
            importDownloadItems();
            return true;
        } else if (KEY_CLEAN_INVALID_DOWNLOAD.equals(key)) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_clean_invalid_download)
                    .setMessage(R.string.settings_download_clean_invalid_download_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask task = new com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask(requireActivity());
                        TaskExecutor.executeTask(task);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.show_details, (dialog, which) -> {
                        showCleanInvalidDownloadDetails();
                    })
                    .show();
            return true;
        } else if (KEY_REPAIR_ALL_DOWNLOADED_GALLERY.equals(key)) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_repair_all_downloaded_gallery)
                    .setMessage(R.string.repair_all_downloaded_gallery_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String taskName = getString(R.string.settings_download_repair_all_downloaded_gallery);
                        String taskDesc = getString(R.string.settings_download_repair_all_downloaded_gallery_summary);

                        // 使用后台任务框架
                        com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask task = 
                            new com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask(requireContext());
                        
                        com.hippo.ehviewer.BackgroundTaskManager taskManager = 
                            com.hippo.ehviewer.BackgroundTaskManager.getInstance();
                        com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager statusManager = 
                            taskManager.getTaskStatusManager();

                        // 先注册任务，确保进度条不再显示不确定状态
                        String taskId = statusManager.addTask(taskName, taskDesc, null);

                        // 准备日志文件（存放在 logcat 目录）
                        java.io.File logFile = createRepairLogFile();
                        if (logFile != null) {
                            statusManager.updateTaskLogFile(taskId, logFile);
                        }

                        // 将任务进度和日志回写到任务状态管理器
                        task.setStatusListener(new com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask.StatusListener() {
                            @Override
                            public void onStatus(int current, int total, int success, int failed) {
                                String detail = "成功: " + success + " 失败: " + failed;
                                statusManager.updateTaskProgress(taskId, current, total, detail);
                            }
                        });

                        task.setLogListener(new com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask.LogListener() {
                            @Override
                            public void onLog(String message) {
                                statusManager.appendTaskLog(taskId, message);
                            }
                        });

                        taskManager.submitLongRunningTask(
                            taskName,
                            taskDesc,
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        statusManager.appendTaskLog(taskId, "开始修复任务");
                                        task.executeBlockingOrThrow();
                                        statusManager.appendTaskLog(taskId, "修复任务完成");
                                    } catch (Exception e) {
                                        statusManager.appendTaskLog(taskId, "修复任务失败: " + e.getMessage());
                                        Log.e(TAG, "修复任务执行失败", e);
                                        throw new RuntimeException(e.getMessage(), e);
                                    }
                                }
                            },
                            taskId
                        );
                        
                        Toast.makeText(requireActivity(), 
                            R.string.settings_download_rebuild_download_records_started, 
                            Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        } else if (KEY_REPAIR_UNKNOWN_CATEGORY_GALLERY.equals(key)) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_repair_unknown_category_gallery)
                    .setMessage(R.string.repair_unknown_category_gallery_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String taskName = getString(R.string.settings_download_repair_unknown_category_gallery);
                        String taskDesc = getString(R.string.settings_download_repair_unknown_category_gallery_summary);

                        // 使用后台任务框架
                        com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask task = 
                            new com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask(requireContext());
                        
                        com.hippo.ehviewer.BackgroundTaskManager taskManager = 
                            com.hippo.ehviewer.BackgroundTaskManager.getInstance();
                        com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager statusManager = 
                            taskManager.getTaskStatusManager();

                        // 先注册任务，确保进度条不再显示不确定状态
                        String taskId = statusManager.addTask(taskName, taskDesc, null);

                        // 准备日志文件（存放在 logcat 目录）
                        java.io.File logFile = createRepairLogFile();
                        if (logFile != null) {
                            statusManager.updateTaskLogFile(taskId, logFile);
                        }

                        // 将任务进度和日志回写到任务状态管理器
                        task.setStatusListener(new com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask.StatusListener() {
                            @Override
                            public void onStatus(int current, int total, int success, int failed) {
                                String detail = "成功: " + success + " 失败: " + failed;
                                statusManager.updateTaskProgress(taskId, current, total, detail);
                            }
                        });

                        task.setLogListener(new com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask.LogListener() {
                            @Override
                            public void onLog(String message) {
                                statusManager.appendTaskLog(taskId, message);
                            }
                        });

                        taskManager.submitLongRunningTask(
                            taskName,
                            taskDesc,
                            new Runnable() {
                                @Override
                                public void run() {
                                    try {
                                        statusManager.appendTaskLog(taskId, "开始修复分类未知画廊任务");
                                        task.executeBlockingOrThrow();
                                        statusManager.appendTaskLog(taskId, "修复分类未知画廊任务完成");
                                    } catch (Exception e) {
                                        statusManager.appendTaskLog(taskId, "修复分类未知画廊任务失败: " + e.getMessage());
                                        Log.e(TAG, "修复分类未知画廊任务执行失败", e);
                                        throw new RuntimeException(e.getMessage(), e);
                                    }
                                }
                            },
                            taskId
                        );
                        
                        Toast.makeText(requireActivity(), 
                            R.string.repair_unknown_category_gallery_processing, 
                            Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        } else if (KEY_REBUILD_DOWNLOAD_RECORDS.equals(key)) {
            // 显示一个"正在检查"的对话框，然后在后台线程执行预检查
            ProgressDialog checkingDialog = new ProgressDialog(requireActivity());
            checkingDialog.setTitle("正在检查下载目录");
            checkingDialog.setMessage("请稍候...");
            checkingDialog.setIndeterminate(true);
            checkingDialog.setCancelable(false);
            checkingDialog.show();
            
            // 在后台线程执行预检查
            ExecutorManager.getBackgroundExecutor().execute(() -> {
                try {
                    UniFile downloadDir = Settings.getDownloadLocation();
                    if (downloadDir == null || !downloadDir.isDirectory()) {
                        showCheckResultOnMainThread("下载目录不存在或无效，无法重建下载记录");
                        return;
                    }
                    
                    UniFile[] files = downloadDir.listFiles();
                    if (files == null || files.length == 0) {
                        showCheckResultOnMainThread("下载目录为空，没有找到任何画廊文件夹");
                        return;
                    }
                    
                    // 检查是否有.ehviewer文件
                    boolean hasEhViewerFiles = false;
                    for (UniFile file : files) {
                        if (file.isDirectory()) {
                            UniFile ehViewerFile = file.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
                            if (ehViewerFile != null) {
                                hasEhViewerFiles = true;
                                break;
                            }
                        }
                    }
                    
                    if (!hasEhViewerFiles) {
                        showCheckResultOnMainThread("下载目录中没有找到任何.ehviewer文件，无法重建下载记录");
                        return;
                    }
                    
                    // 预检查通过，显示确认对话框
                    showConfirmDialogOnMainThread();
                    
                } catch (Exception e) {
                    Log.e(TAG, "[REBUILD] 预检查时发生错误", e);
                    showCheckResultOnMainThread("检查下载目录时发生错误: " + e.getMessage());
                } finally {
                    // 关闭检查对话框
                    dismissCheckingDialogOnMainThread(checkingDialog);
                }
            });
            return true;
        } else if ("merge_duplicate_gallery".equals(key)) {
            new MergeDuplicateGalleryTask(this).execute();
            return true;
        } else if ("scan_download_files".equals(key)) {
            showScanDownloadFilesDialog();
            return true;
        } else if (KEY_RESET_MEDIA_SCAN.equals(key)) {
            showResetMediaScanDialog();
            return true;
        } else if (KEY_VIEW_DOWNLOAD_LOGS.equals(key)) {
            showViewDownloadLogsDialog();
            return true;
        } else if (KEY_CLEAN_DOWNLOAD_LOGS.equals(key)) {
            showCleanDownloadLogsDialog();
            return true;
        }
        return false;
    }

    private void showDirPickerDialogKK() {
        new AlertDialog.Builder(requireActivity()).setMessage(R.string.settings_download_pick_dir_kk)
                .setPositiveButton(R.string.settings_download_continue, (dialog, which) -> openDirPicker()).show();
    }

    private void showDirPickerDialogL() {
        DialogInterface.OnClickListener listener = (dialog, which) -> {
            switch (which) {
                case DialogInterface.BUTTON_POSITIVE:
                    openDirPicker();
                    break;
                case DialogInterface.BUTTON_NEUTRAL:
                    openDirPickerL();
                    break;
            }
        };

        new AlertDialog.Builder(requireActivity()).setMessage(R.string.settings_download_pick_dir_l)
                .setPositiveButton(R.string.settings_download_continue, listener)
                .setNeutralButton(R.string.settings_download_document, listener)
                .show();
    }

    private void showScanDownloadFilesDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_scan_download_files)
                .setMessage(R.string.settings_download_scan_download_files_summary)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    ScanDownloadFilesTask task = new ScanDownloadFilesTask(requireActivity());
                    TaskExecutor.executeTask(task);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showResetMediaScanDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_reset_media_scan)
                .setMessage(R.string.settings_download_reset_media_scan_summary)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    resetMediaScan();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetMediaScan() {
        try {
            // 获取下载目录
            UniFile downloadLocation = Settings.getDownloadLocation();
            if (downloadLocation == null) {
                Toast.makeText(requireActivity(), "下载目录未设置", Toast.LENGTH_SHORT).show();
                return;
            }

            // 发送媒体扫描广播
            Uri downloadUri = downloadLocation.getUri();
            if (downloadUri != null) {
                String downloadPath = downloadUri.getPath();
                if (downloadPath != null) {
                    File downloadDir = new File(downloadPath);
                    if (downloadDir.exists()) {
                        MediaScannerConnection.scanFile(
                            requireActivity(),
                            new String[]{downloadDir.getAbsolutePath()},
                            null,
                            (path, uri) -> {
                                // 扫描完成后的回调
                                requireActivity().runOnUiThread(() -> {
                                    Toast.makeText(requireActivity(), 
                                        "媒体扫描已重置，相册应用将更新", 
                                        Toast.LENGTH_SHORT).show();
                                });
                            }
                        );
                    } else {
                        Toast.makeText(requireActivity(), "下载目录不存在", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireActivity(), "无法获取下载目录路径", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(requireActivity(), "无法获取下载目录URI", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "重置媒体扫描失败", e);
            Toast.makeText(requireActivity(), "重置媒体扫描失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openDirPicker() {
        UniFile uniFile = Settings.getDownloadLocation();
        Intent intent = new Intent(getActivity(), DirPickerActivity.class);
        if (uniFile != null) {
            intent.putExtra(DirPickerActivity.KEY_FILE_URI, uniFile.getUri());
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR);
    }

    private void openDirPickerL() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_IMAGE_DIR_L);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(getActivity(), R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    private void exportDownloadItems() {
        List<GalleryInfo> list = EhApplication.getDownloadManager(requireActivity()).getDownloadInfoList();
        if (list.isEmpty()) {
            Toast.makeText(getActivity(), R.string.settings_download_export_no_items, Toast.LENGTH_SHORT).show();
            return;
        }

        UniFile dir = Settings.getDownloadLocation();
        if (dir == null) {
            Toast.makeText(getActivity(), R.string.settings_download_invalid_download_location, Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US);
        String fileName = "ehviewer-download-" + sdf.format(new Date()) + ".csv";

        UniFile file = dir.createFile(fileName);
        if (file == null) {
            Toast.makeText(getActivity(), R.string.settings_download_export_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        try (OutputStream os = file.openOutputStream()) {
            os.write(DownloadManager.DOWNLOAD_INFO_HEADER.getBytes(StandardCharsets.UTF_8));
            for (GalleryInfo gi : list) {
                os.write(gi.toCSV().getBytes(StandardCharsets.UTF_8));
            }
            Toast.makeText(getActivity(), getString(R.string.settings_download_export_succeed, file.getUri().toString()), Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(getActivity(), R.string.settings_download_export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void importDownloadItems() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE);
        } catch (Throwable e) {
            ExceptionUtils.throwIfFatal(e);
            Toast.makeText(getActivity(), R.string.error_cant_find_activity, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(data == null){
            super.onActivityResult(requestCode, resultCode, null);
            return;
        }
        switch (requestCode) {
            case REQUEST_CODE_PICK_IMAGE_DIR: {
                if (resultCode == Activity.RESULT_OK) {
                    UniFile uniFile = UniFile.fromUri(getActivity(), data.getData());
                    if (uniFile != null) {
                        Settings.putDownloadLocation(uniFile);
                        onUpdateDownloadLocation();
                    } else {
                        Toast.makeText(getActivity(), R.string.settings_download_cant_get_download_location,
                                Toast.LENGTH_SHORT).show();
                    }
                }
                break;
            }
            case REQUEST_CODE_PICK_IMAGE_DIR_L: {
                if (resultCode == Activity.RESULT_OK) {
                    Uri treeUri = data.getData();
                    if (treeUri != null) {
                        requireActivity().getContentResolver().takePersistableUriPermission(
                                treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        UniFile uniFile = UniFile.fromTreeUri(getActivity(), treeUri);
                        if (uniFile != null) {
                            Settings.putDownloadLocation(uniFile);
                            onUpdateDownloadLocation();
                        } else {
                            Toast.makeText(getActivity(), R.string.settings_download_cant_get_download_location,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                break;
            }
            case REQUEST_CODE_PICK_DOWNLOAD_IMPORT_FILE: {
                if (resultCode == Activity.RESULT_OK) {
                    new ImportDownloadTask(this, data.getData()).execute();
                }
                break;
            }
            default: {
                super.onActivityResult(requestCode, resultCode, data);
            }
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();
        Object oldValue = null;
        
        // 获取旧值用于日志记录
        if (Settings.KEY_MEDIA_SCAN.equals(key)) {
            oldValue = Settings.getMediaScan();
        } else if (Settings.KEY_IMAGE_RESOLUTION.equals(key)) {
            oldValue = Settings.getImageResolution();
        } else if (Settings.KEY_DOWNLOAD_TIMEOUT.equals(key)) {
            oldValue = Settings.getDownloadTimeout();
        } else if (Settings.KEY_DOWNLOAD_LOGGING_ENABLED.equals(key)) {
            oldValue = Settings.getDownloadLoggingEnabled();
        } else if (Settings.KEY_ENABLE_MIN_DOWNLOAD_SPEED.equals(key)) {
            oldValue = Settings.getEnableMinDownloadSpeed();
        }
        
        if (Settings.KEY_MEDIA_SCAN.equals(key)) {
            if (newValue instanceof Boolean) {
                UniFile downloadLocation = Settings.getDownloadLocation();
                if ((Boolean) newValue) {
                    CommonOperations.removeNoMediaFile(downloadLocation);
                } else {
                    CommonOperations.ensureNoMediaFile(downloadLocation);
                }
                // 记录设置变更日志
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
            }
            return true;
        } else if (Settings.KEY_ENABLE_MIN_DOWNLOAD_SPEED.equals(key)) {
            if (newValue instanceof Boolean) {
                Settings.putEnableMinDownloadSpeed((Boolean) newValue);
                // 记录设置变更日志
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
            }
            return true;
        } else if (Settings.KEY_IMAGE_RESOLUTION.equals(key)) {
            if (newValue instanceof String) {
                Settings.putImageResolution((String) newValue);
                // 记录设置变更日志
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
            }
            return true;
        }else if (Settings.KEY_DOWNLOAD_TIMEOUT.equals(key)) {
            if (newValue instanceof String) {
                Settings.setDownloadTimeout(toTimeoutTime(newValue));
                // 记录设置变更日志
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
            }
            return true;
        } else if (Settings.KEY_DOWNLOAD_LOGGING_ENABLED.equals(key)) {
            if (newValue instanceof Boolean) {
                // 更新日志设置
                mDownloadLogger.setLoggingEnabled((Boolean) newValue);
                // 记录设置变更日志（在禁用日志前记录）
                if ((Boolean) newValue) {
                    mDownloadLogger.logSettingsChange(key, oldValue, newValue);
                }
            }
            return true;
        }
        return false;
    }

    private int toTimeoutTime(Object newValue) {
        try{
            return Integer.parseInt(newValue.toString());
        }catch (NumberFormatException e){
            return 0;
        }
    }

    private static class ImportDownloadTask {
        
        private final WeakReference<DownloadFragment> mFragment;
        private final Uri mUri;
        private ProgressDialog mProgressDialog;
        private volatile boolean isCancelled = false;
        private boolean isDialogShown = true;
        private int mTotalCount = 0;
        private int mFailCount = 0;

        public ImportDownloadTask(DownloadFragment fragment, Uri uri) {
            mFragment = new WeakReference<>(fragment);
            mUri = uri;
        }

        public void execute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            
            onPreExecute();
            ExecutorManager.getIoExecutor().execute(() -> {
                Integer result = doInBackground();
                ExecutorManager.runOnMainThread(() -> onPostExecute(result));
            });
        }

        private void onPreExecute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_import_items);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", (dialog, which) -> {
                isDialogShown = false;
                dialog.dismiss();
            });
            mProgressDialog.show();
            
            // 显示通知栏
            fragment.updateNotification(NOTIFICATION_ID_IMPORT, 
                fragment.getString(R.string.settings_download_import_items), 
                "正在导入下载记录...", 0, 0);
        }

        private Integer doInBackground() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null || mUri == null || isCancelled) {
                return 0;
            }

            try (InputStream is = fragment.requireActivity().getContentResolver().openInputStream(mUri)) {
                if (is == null) {
                    return 0;
                }
                String content = IOUtils.readString(is, StandardCharsets.UTF_8.name());
                String[] lines = content.split("\n");
                List<GalleryInfo> galleryInfos = new ArrayList<>();
                for (String line : lines) {
                    if (line.startsWith(DownloadManager.DOWNLOAD_INFO_HEADER)) {
                        continue;
                    }
                    GalleryInfo gi = GalleryInfo.fromCSV(line);
                    if (gi != null) {
                        galleryInfos.add(gi);
                    }
                }

                DownloadManager downloadManager = EhApplication.getDownloadManager(fragment.requireActivity());
                int successCount = 0;
                int failCount = 0;
                int total = galleryInfos.size();
                mTotalCount = total;
                mFailCount = 0; // 重置失败计数
                publishProgress(0, total, 0, 0);

                for (int i = 0; i < total && !isCancelled; i++) {
                    GalleryInfo gi = galleryInfos.get(i);
                    try {
                        if (downloadManager.getDownloadInfo(gi.gid) == null) {
                            downloadManager.addDownload(gi, null);
                            successCount++;
                        } else {
                            failCount++; // 已存在，算作失败
                            mFailCount++;
                        }
                    } catch (Exception e) {
                        failCount++;
                        mFailCount++;
                        // 记录错误日志
                        Log.e("ImportDownloadTask", "Failed to import gallery: " + gi.title + " (GID: " + gi.gid + ")", e);
                    }
                    publishProgress(i + 1, total, successCount, failCount);
                }
                // 返回成功个数
                return successCount;
            } catch (IOException e) {
                Log.e("ImportDownloadTask", "Failed to read import file", e);
                return -1; // 使用-1表示读取文件失败
            }
        }

        private void publishProgress(int progress, int max, int successCount, int failCount) {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || !fragment.isAdded() || fragment.getActivity() == null) {
                return;
            }
            
            // 更新对话框进度
            ExecutorManager.runOnMainThread(() -> {
                if (mProgressDialog != null && isDialogShown) {
                    mProgressDialog.setMax(max);
                    mProgressDialog.setProgress(progress);
                }
            });
            
            // 更新通知栏进度
            String content = "正在导入下载记录... (" + progress + "/" + max + ") - 成功:" + successCount + " 失败:" + failCount;
            fragment.updateNotification(NOTIFICATION_ID_IMPORT, 
                fragment.getString(R.string.settings_download_import_items), 
                content, progress, max);
        }

        private void onPostExecute(Integer result) {
            DownloadFragment fragment = mFragment.get();
            
            // 关闭对话框
            if (mProgressDialog != null && isDialogShown) {
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    } catch (IllegalArgumentException e) {
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
                mProgressDialog = null;
            }
            
            // 取消通知栏
            if (fragment != null) {
                fragment.cancelNotification(NOTIFICATION_ID_IMPORT);
            }
            
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            
            if (result == -1) {
                // 文件读取失败
                Toast.makeText(fragment.getActivity(), 
                    fragment.getString(R.string.settings_download_import_read_failed), 
                    Toast.LENGTH_LONG).show();
            } else if (result >= 0) {
                // 显示详细的成功/失败信息
                String message;
                if (result > 0) {
                    message = fragment.getString(R.string.settings_download_import_succeed_with_details, 
                        result, mFailCount);
                    if (mFailCount > 0) {
                        message += "\n" + fragment.getString(R.string.settings_download_import_check_logs);
                    }
                } else {
                    message = fragment.getString(R.string.settings_download_import_failed_all);
                    message += "\n" + fragment.getString(R.string.settings_download_import_check_logs);
                }
                Toast.makeText(fragment.getActivity(), message, Toast.LENGTH_LONG).show();
            }
        }

        public void cancel() {
            isCancelled = true;
        }
    }

    private class CleanInvalidDownloadTaskWrapper {
        
        private final WeakReference<DownloadFragment> mFragment;
        private ProgressDialog mProgressDialog;
        private final List<String> mLogs = new ArrayList<>();
        private volatile boolean isCancelled = false;
        private boolean isDialogShown = true;

        public CleanInvalidDownloadTaskWrapper(DownloadFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        public void execute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            
            // 创建新的清理任务
            com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask task = new com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask(fragment.getActivity());
            
            // 使用统一的进度对话框管理器
            boolean isNewDialog = ProgressDialogManager.showOrUpdateDialog(fragment.getActivity(), task);
            
            if (isNewDialog) {
                // 如果是新对话框，启动任务
                TaskExecutor.executeTask(task);
            }
        }

        private void onPreExecute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_cleaning);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", (dialog, which) -> {
                isDialogShown = false;
                dialog.dismiss();
            });
            mProgressDialog.show();
            
            // 显示通知栏
            fragment.updateNotification(NOTIFICATION_ID_CLEAN, 
                fragment.getString(R.string.settings_download_cleaning), 
                "正在清理无效下载...", 0, 0);
        }

        private Integer doInBackground() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null || isCancelled) {
                return 0;
            }
            
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null || !downloadDir.isDirectory()) {
                return 0;
            }

            UniFile[] files = downloadDir.listFiles();
            if (files == null) {
                return 0;
            }

            int invalidCount = 0;
            int total = files.length;
            publishProgress(0, total);

            DownloadManager downloadManager = EhApplication.getDownloadManager(fragment.requireActivity());

            for (int i = 0; i < total && !isCancelled; i++) {
                UniFile dir = files[i];
                publishProgress(i + 1, total);

                if (!dir.isDirectory()) {
                    continue;
                }

                UniFile[] subFiles = dir.listFiles();
                if (subFiles == null || subFiles.length == 0) {
                    mLogs.add(getString(R.string.clean_invalid_download_empty_directory, dir.getName()));
                    invalidCount++;
                    dir.delete();
                    continue;
                }

                UniFile ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
                if (ehViewerFile == null) {
                    mLogs.add(getString(R.string.clean_invalid_download_missing_ehviewer_file, dir.getName()));
                    invalidCount++;
                    continue;
                }

                try {
                    String content = IOUtils.readString(ehViewerFile.openInputStream(), StandardCharsets.UTF_8.name());
                    String[] lines = content.split("\n");
                    if (lines.length < 8) {
                        mLogs.add(getString(R.string.clean_invalid_download_invalid_ehviewer_file, dir.getName()));
                        invalidCount++;
                        // Try to reset if possible
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0]);
                        } catch (NumberFormatException e) {
                            gid = -1;
                        }
                        if (gid != -1) {
                            com.hippo.ehviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.ehviewer.dao.DownloadInfo.STATE_NONE;
                                EhDB.putDownloadInfo(gi);
                            }
                        }
                        continue;
                    }
                    int pageCount = Integer.parseInt(lines[7]);
                    int imageFileCount = 0;
                    for (UniFile subFile : subFiles) {
                        String name = subFile.getName();
                        if (name != null && !name.startsWith(".")) {
                            imageFileCount++;
                        }
                    }

                    if (imageFileCount != pageCount) {
                        mLogs.add(getString(R.string.clean_invalid_download_inconsistent_file_count, dir.getName(), pageCount, imageFileCount));
                        invalidCount++;
                        for (UniFile subFile : subFiles) {
                            String name = subFile.getName();
                            if (name != null && !name.equals(DownloadManager.DOWNLOAD_INFO_FILENAME) && !name.startsWith(".")) {
                                subFile.delete();
                            }
                        }
                        // Reset to unfinished state
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0]);
                        } catch (NumberFormatException e) {
                            gid = -1;
                        }
                        if (gid != -1) {
                            com.hippo.ehviewer.dao.DownloadInfo gi = downloadManager.getDownloadInfo(gid);
                            if (gi != null) {
                                gi.state = com.hippo.ehviewer.dao.DownloadInfo.STATE_NONE;
                                EhDB.putDownloadInfo(gi);
                            }
                        }
                    }
                } catch (IOException | NumberFormatException e) {
                    mLogs.add(getString(R.string.clean_invalid_download_error_processing_directory, dir.getName(), e.getMessage()));
                    invalidCount++;
                }
            }

            if (!mLogs.isEmpty()) {
                saveLog();
            }

            return invalidCount;
        }

        private void publishProgress(int progress, int max) {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || !fragment.isAdded() || fragment.getActivity() == null) {
                return;
            }
            
            // 更新对话框进度
            ExecutorManager.runOnMainThread(() -> {
                if (mProgressDialog != null && isDialogShown) {
                    mProgressDialog.setMax(max);
                    mProgressDialog.setProgress(progress);
                }
            });
            
            // 更新通知栏进度
            String content = "正在清理无效下载... (" + progress + "/" + max + ")";
            fragment.updateNotification(NOTIFICATION_ID_CLEAN, 
                fragment.getString(R.string.settings_download_cleaning), 
                content, progress, max);
        }

        private void onPostExecute(Integer result) {
            DownloadFragment fragment = mFragment.get();
            
            // 关闭对话框
            if (mProgressDialog != null && isDialogShown) {
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    } catch (IllegalArgumentException e) {
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
                mProgressDialog = null;
            }
            
            // 取消通知栏
            if (fragment != null) {
                fragment.cancelNotification(NOTIFICATION_ID_CLEAN);
            }
            
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            if (result > 0) {
                Toast.makeText(fragment.getActivity(), fragment.getString(R.string.settings_download_clean_invalid_done, result), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.getActivity(), R.string.settings_download_clean_invalid_no_invalid, Toast.LENGTH_SHORT).show();
            }
        }

        private void saveLog() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String fileName = "delfile-" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadDir.createFile(fileName);
            if (logFile != null) {
                try (OutputStream os = logFile.openOutputStream()) {
                    for (String log : mLogs) {
                        os.write((log + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        public void cancel() {
            isCancelled = true;
        }
    }

private class RebuildDownloadRecordsTask {
        
        private final WeakReference<DownloadFragment> mFragment;
        private ProgressDialog mProgressDialog;
        private final List<String> mLogs = new ArrayList<>();
        private final List<GalleryInfo> mGalleryInfosToAdd = new ArrayList<>(); // 收集需要添加的GalleryInfo
        private volatile boolean isCancelled = false;
        private boolean isDialogShown = true;

        public RebuildDownloadRecordsTask(DownloadFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        public void execute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            
            onPreExecute();
            ExecutorManager.getBackgroundExecutor().execute(() -> {
                Integer result = doInBackground();
                ExecutorManager.runOnMainThread(() -> onPostExecute(result));
            });
        }

        private void onPreExecute() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            mProgressDialog = new ProgressDialog(fragment.getActivity());
            mProgressDialog.setTitle(R.string.settings_download_rebuilding);
            mProgressDialog.setIndeterminate(false);
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", (dialog, which) -> {
                isDialogShown = false;
                dialog.dismiss();
            });
            mProgressDialog.show();
            
            // 显示通知栏
            fragment.updateNotification(NOTIFICATION_ID_REBUILD, 
                fragment.getString(R.string.settings_download_rebuilding), 
                "正在重建下载记录...", 0, 0);
        }

        private Integer doInBackground() {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || fragment.getActivity() == null || isCancelled) {
                Log.d(TAG, "[REBUILD] 任务被取消或Fragment无效");
                return 0;
            }

            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null || !downloadDir.isDirectory()) {
                Log.d(TAG, "[REBUILD] 下载目录无效");
                return 0;
            }

            UniFile[] files = downloadDir.listFiles();
            if (files == null) {
                Log.d(TAG, "[REBUILD] 无法列出下载目录文件");
                return 0;
            }
            
            Log.d(TAG, "[REBUILD] 开始处理 " + files.length + " 个文件");

            int rebuildCount = 0;
            int total = files.length;
            publishProgress(0, total);

            DownloadManager downloadManager = EhApplication.getDownloadManager(fragment.requireActivity());

            for (int i = 0; i < total && !isCancelled; i++) {
                UniFile dir = files[i];
                publishProgress(i + 1, total);

                if (!dir.isDirectory()) {
                    continue;
                }

                UniFile ehViewerFile = dir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
                if (ehViewerFile == null) {
                    // 没有.ehviewer文件，尝试创建基本的假画廊信息
                    GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                    if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                        mGalleryInfosToAdd.add(fallbackInfo);
                        rebuildCount++;
                        mLogs.add("Created fallback gallery: " + fallbackInfo.title + " (GID: " + fallbackInfo.gid + ") [LOCAL]");
                    }
                    continue;
                }

                try {
                    String content = IOUtils.readString(ehViewerFile.openInputStream(), StandardCharsets.UTF_8.name());
                    String[] lines = content.split("\n");
                    
                    // 检查是否是VERSION2格式
                    if (lines.length > 0 && lines[0].trim().equals("VERSION2")) {
                        // VERSION2格式解析
                        if (lines.length < 4) {
                            mLogs.add("Invalid VERSION2 .ehviewer file in: " + dir.getName());
                            // 尝试创建fallback
                            GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                            if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                                mGalleryInfosToAdd.add(fallbackInfo);
                                rebuildCount++;
                                mLogs.add("Created fallback for invalid VERSION2: " + fallbackInfo.title + " [LOCAL]");
                            }
                            continue;
                        }
                        
                        // 解析gid和token
                        long gid;
                        String token;
                        try {
                            // 跳过版本号行，直接获取gid
                            gid = Long.parseLong(lines[2].trim());
                            token = lines[3].trim();
                        } catch (NumberFormatException e) {
                            mLogs.add("Invalid gid or token in VERSION2 .ehviewer file: " + dir.getName());
                            // 尝试创建fallback
                            GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                            if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                                mGalleryInfosToAdd.add(fallbackInfo);
                                rebuildCount++;
                                mLogs.add("Created fallback for invalid VERSION2: " + fallbackInfo.title + " [LOCAL]");
                            }
                            continue;
                        }
                        
                        // 检查是否已经存在
                        if (downloadManager.getDownloadInfo(gid) != null) {
                            continue;
                        }
                        
                        // 创建GalleryInfo对象
                        GalleryInfo galleryInfo = new GalleryInfo();
                        galleryInfo.gid = gid;
                        galleryInfo.token = token;
                        
                        // 使用目录名作为标题（如果无法从文件中解析）
                        String dirName = dir.getName();
                        if (dirName != null && dirName.contains("-")) {
                            // 尝试从目录名提取标题（去掉GID前缀）
                            String[] parts = dirName.split("-", 2);
                            if (parts.length > 1) {
                                galleryInfo.title = parts[1].trim();
                            } else {
                                galleryInfo.title = dirName;
                            }
                        } else {
                            galleryInfo.title = dirName != null ? dirName : "Unknown Title";
                        }
                        
                        // 设置首图作为缩略图
                        String firstImagePath = getFirstImagePath(dir);
                        if (firstImagePath != null) {
                            galleryInfo.thumb = "file://" + firstImagePath;
                        }
                        
                        // 统计图片文件数量
                        int imageCount = countImageFiles(dir);
                        galleryInfo.pages = imageCount;
                        galleryInfo.category = EhUtils.UNKNOWN; // 默认分类
                        galleryInfo.uploader = "[本地记录]"; // 标记为本地记录
                        
                        mGalleryInfosToAdd.add(galleryInfo);
                        rebuildCount++;
                        mLogs.add("Added VERSION2 gallery: " + galleryInfo.title + " (GID: " + galleryInfo.gid + "), Pages: " + imageCount);
                        
                    } else {
                        // 原有格式解析
                        if (lines.length < 8) {
                            mLogs.add("Invalid .ehviewer file in: " + dir.getName());
                            // 尝试创建fallback
                            GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                            if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                                mGalleryInfosToAdd.add(fallbackInfo);
                                rebuildCount++;
                                mLogs.add("Created fallback for invalid file: " + fallbackInfo.title + " [LOCAL]");
                            }
                            continue;
                        }

                        // Parse gid from first line
                        long gid;
                        try {
                            gid = Long.parseLong(lines[0].trim());
                        } catch (NumberFormatException e) {
                            mLogs.add("Invalid gid in .ehviewer file: " + dir.getName());
                            // 尝试创建fallback
                            GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                            if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                                mGalleryInfosToAdd.add(fallbackInfo);
                                rebuildCount++;
                                mLogs.add("Created fallback for invalid gid: " + fallbackInfo.title + " [LOCAL]");
                            }
                            continue;
                        }

                        // Check if already exists
                        if (downloadManager.getDownloadInfo(gid) != null) {
                            continue;
                        }

                        // Parse gallery info from .ehviewer file
                        GalleryInfo galleryInfo = parseGalleryInfoFromLines(lines);
                        if (galleryInfo != null) {
                            // 如果没有thumb，使用首图
                            if (galleryInfo.thumb == null || galleryInfo.thumb.isEmpty()) {
                                String firstImagePath = getFirstImagePath(dir);
                                if (firstImagePath != null) {
                                    galleryInfo.thumb = "file://" + firstImagePath;
                                }
                            }
                            mGalleryInfosToAdd.add(galleryInfo);
                            rebuildCount++;
                            mLogs.add("Added gallery: " + galleryInfo.title + " (GID: " + galleryInfo.gid + ")");
                        } else {
                            // 解析失败，创建fallback
                            GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir, gid);
                            if (fallbackInfo != null) {
                                mGalleryInfosToAdd.add(fallbackInfo);
                                rebuildCount++;
                                mLogs.add("Created fallback for parse failure: " + fallbackInfo.title + " [LOCAL]");
                            }
                        }
                    }

                } catch (IOException | NumberFormatException e) {
                    mLogs.add("Error processing directory " + dir.getName() + ": " + e.getMessage());
                    // 出错时尝试创建fallback
                    GalleryInfo fallbackInfo = createFallbackGalleryInfo(dir);
                    if (fallbackInfo != null && downloadManager.getDownloadInfo(fallbackInfo.gid) == null) {
                        mGalleryInfosToAdd.add(fallbackInfo);
                        rebuildCount++;
                        mLogs.add("Created fallback for exception: " + fallbackInfo.title + " [LOCAL]");
                    }
                }
            }

            if (!mLogs.isEmpty()) {
                saveRebuildLog();
            }

            return rebuildCount;
        }

        private GalleryInfo parseGalleryInfoFromLines(String[] lines) {
            try {
                GalleryInfo info = new GalleryInfo();
                info.gid = Long.parseLong(lines[0].trim());
                info.token = lines[1].trim();
                info.title = lines[2].trim();
                info.titleJpn = lines.length > 3 ? lines[3].trim() : "";
                info.thumb = lines.length > 4 ? lines[4].trim() : "";
                info.category = lines.length > 5 ? EhUtils.getCategory(lines[5].trim()) : EhUtils.UNKNOWN;
                info.posted = lines.length > 6 ? lines[6].trim() : "";
                info.uploader = lines.length > 7 ? lines[7].trim() : "";
                info.rating = lines.length > 8 ? Float.parseFloat(lines[8].trim()) : 0.0f;
                info.rated = lines.length > 9 ? Boolean.parseBoolean(lines[9].trim()) : false;
                info.simpleLanguage = lines.length > 10 ? lines[10].trim() : "";
                info.simpleTags = lines.length > 11 ? lines[11].trim().split(",") : new String[0];
                info.thumbWidth = lines.length > 12 ? Integer.parseInt(lines[12].trim()) : 0;
                info.thumbHeight = lines.length > 13 ? Integer.parseInt(lines[13].trim()) : 0;
                info.spanSize = lines.length > 14 ? Integer.parseInt(lines[14].trim()) : 0;
                info.spanIndex = lines.length > 15 ? Integer.parseInt(lines[15].trim()) : 0;
                info.spanGroupIndex = lines.length > 16 ? Integer.parseInt(lines[16].trim()) : 0;
                info.favoriteSlot = lines.length > 17 ? Integer.parseInt(lines[17].trim()) : -2;
                info.favoriteName = lines.length > 18 ? lines[18].trim() : null;
                info.pages = lines.length > 19 ? Integer.parseInt(lines[19].trim()) : 0;
                return info;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 创建fallback画廊信息（无GID版本）
         * 从文件夹名称和内容推断信息
         */
        private GalleryInfo createFallbackGalleryInfo(UniFile dir) {
            try {
                String dirName = dir.getName();
                if (dirName == null) {
                    return null;
                }
                
                // 尝试从目录名提取GID
                long gid = -1;
                String title = dirName;
                
                if (dirName.contains("-")) {
                    String[] parts = dirName.split("-", 2);
                    try {
                        gid = Long.parseLong(parts[0]);
                        if (parts.length > 1) {
                            title = parts[1].trim();
                        }
                    } catch (NumberFormatException e) {
                        // 无法解析GID，使用文件夹名的哈希值作为GID
                        gid = Math.abs(dirName.hashCode());
                    }
                } else {
                    // 使用文件夹名的哈希值作为GID
                    gid = Math.abs(dirName.hashCode());
                }
                
                return createFallbackGalleryInfo(dir, gid, title);
            } catch (Exception e) {
                return null;
            }
        }
        
        /**
         * 创建fallback画廊信息（有GID版本）
         */
        private GalleryInfo createFallbackGalleryInfo(UniFile dir, long gid) {
            String dirName = dir.getName();
            String title = dirName;
            
            if (dirName != null && dirName.contains("-")) {
                String[] parts = dirName.split("-", 2);
                if (parts.length > 1) {
                    title = parts[1].trim();
                }
            }
            
            return createFallbackGalleryInfo(dir, gid, title);
        }
        
        /**
         * 创建fallback画廊信息（完整版本）
         */
        private GalleryInfo createFallbackGalleryInfo(UniFile dir, long gid, String title) {
            try {
                GalleryInfo info = new GalleryInfo();
                info.gid = gid;
                info.token = "local-" + gid; // 生成本地token
                info.title = title;
                info.titleJpn = "";
                
                // 使用首图作为缩略图
                String firstImagePath = getFirstImagePath(dir);
                if (firstImagePath != null) {
                    info.thumb = "file://" + firstImagePath;
                } else {
                    info.thumb = "";
                }
                
                info.category = EhUtils.UNKNOWN;
                info.posted = "";
                info.uploader = "[本地画廊]"; // 标记为本地画廊
                info.rating = 0.0f;
                info.rated = false;
                info.simpleLanguage = "";
                info.simpleTags = new String[0];
                
                // 统计图片数量
                info.pages = countImageFiles(dir);
                
                return info;
            } catch (Exception e) {
                return null;
            }
        }
        
        /**
         * 获取文件夹中首张图片的路径
         */
        private String getFirstImagePath(UniFile dir) {
            try {
                UniFile[] files = dir.listFiles();
                if (files == null || files.length == 0) {
                    return null;
                }
                
                // 按文件名排序
                Arrays.sort(files, new Comparator<UniFile>() {
                    @Override
                    public int compare(UniFile o1, UniFile o2) {
                        String name1 = o1.getName();
                        String name2 = o2.getName();
                        if (name1 == null) return 1;
                        if (name2 == null) return -1;
                        return name1.compareTo(name2);
                    }
                });
                
                // 找到第一个图片文件
                for (UniFile file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        if (fileName != null && !fileName.startsWith(".") && isImageFile(fileName)) {
                            return file.getUri().getPath();
                        }
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
            return null;
        }
        
        /**
         * 统计文件夹中的图片数量
         */
        private int countImageFiles(UniFile dir) {
            try {
                UniFile[] files = dir.listFiles();
                if (files == null) {
                    return 0;
                }
                
                int count = 0;
                for (UniFile file : files) {
                    if (file.isFile()) {
                        String fileName = file.getName();
                        if (fileName != null && !fileName.startsWith(".") && isImageFile(fileName)) {
                            count++;
                        }
                    }
                }
                return count;
            } catch (Exception e) {
                return 0;
            }
        }
        
        /**
         * 判断是否为图片文件
         */
        private boolean isImageFile(String fileName) {
            String lowerName = fileName.toLowerCase();
            return lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                   lowerName.endsWith(".png") || lowerName.endsWith(".gif") ||
                   lowerName.endsWith(".webp") || lowerName.endsWith(".bmp");
        }

        private void saveRebuildLog() {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null) {
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String fileName = "rebuild-" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadDir.createFile(fileName);
            if (logFile != null) {
                try (OutputStream os = logFile.openOutputStream()) {
                    for (String log : mLogs) {
                        os.write((log + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        private void publishProgress(int progress, int max) {
            DownloadFragment fragment = mFragment.get();
            if (fragment == null || !fragment.isAdded() || fragment.getActivity() == null) {
                return;
            }
            
            // 更新对话框进度
            ExecutorManager.runOnMainThread(() -> {
                if (mProgressDialog != null && isDialogShown) {
                    mProgressDialog.setMax(max);
                    mProgressDialog.setProgress(progress);
                }
            });
            
            // 更新通知栏进度
            String content = "正在重建下载记录... (" + progress + "/" + max + ")";
            fragment.updateNotification(NOTIFICATION_ID_REBUILD, 
                fragment.getString(R.string.settings_download_rebuilding), 
                content, progress, max);
        }

        private void onPostExecute(Integer result) {
            DownloadFragment fragment = mFragment.get();
            
            // 在主线程中批量添加下载任务
            if (fragment != null && fragment.getActivity() != null && !mGalleryInfosToAdd.isEmpty()) {
                DownloadManager downloadManager = EhApplication.getDownloadManager(fragment.requireActivity());
                for (GalleryInfo galleryInfo : mGalleryInfosToAdd) {
                    downloadManager.addDownload(galleryInfo, null);
                }
            }
            
            // 关闭对话框
            if (mProgressDialog != null && isDialogShown) {
                if (fragment != null && fragment.isAdded() && fragment.getActivity() != null) {
                    try {
                        if (mProgressDialog.isShowing()) {
                            mProgressDialog.dismiss();
                        }
                    } catch (IllegalArgumentException e) {
                        ExceptionUtils.throwIfFatal(e);
                    }
                }
                mProgressDialog = null;
            }
            
            // 取消通知栏
            if (fragment != null) {
                fragment.cancelNotification(NOTIFICATION_ID_REBUILD);
            }
            
            if (fragment == null || fragment.getActivity() == null) {
                return;
            }
            if (result > 0) {
                Toast.makeText(fragment.getActivity(), fragment.getString(R.string.settings_download_rebuild_success, result), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(fragment.getActivity(), R.string.settings_download_rebuild_no_records, Toast.LENGTH_SHORT).show();
            }
        }

        public void cancel() {
            isCancelled = true;
        }
    }
    
    // 重建下载记录预检查的辅助方法
    private void showCheckResultOnMainThread(String message) {
        ExecutorManager.runOnMainThread(() -> {
            if (isAdded() && getActivity() != null) {
                new AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.settings_download_rebuild_download_records)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
    }
    
    private void dismissCheckingDialogOnMainThread(ProgressDialog dialog) {
        ExecutorManager.runOnMainThread(() -> {
            try {
                if (dialog != null && dialog.isShowing()) {
                    dialog.dismiss();
                }
            } catch (Exception e) {
                Log.e(TAG, "[REBUILD] 关闭检查对话框时发生错误", e);
            }
        });
    }
    
    private void showConfirmDialogOnMainThread() {
        ExecutorManager.runOnMainThread(() -> {
            if (isAdded() && getActivity() != null) {
                new AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.settings_download_rebuild_download_records)
                        .setMessage(R.string.settings_download_rebuild_confirm)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> new RebuildDownloadRecordsTask(this).execute())
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
    }
    
    private void showViewDownloadLogsDialog() {
        File[] logFiles = mDownloadLogger.getLogFiles();
        if (logFiles.length == 0) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_view_logs)
                    .setMessage("没有找到日志文件")
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        
        String[] logFileNames = new String[logFiles.length];
        for (int i = 0; i < logFiles.length; i++) {
            logFileNames[i] = logFiles[i].getName() + " (" + (logFiles[i].length() / 1024) + " KB)";
        }
        
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_view_logs)
                .setItems(logFileNames, (dialog, which) -> {
                    File selectedLogFile = logFiles[which];
                    showLogFileContent(selectedLogFile);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
    
    private void showLogFileContent(File logFile) {
        try {
            String content = new String(java.nio.file.Files.readAllBytes(logFile.toPath()));
            // 限制显示的字符数，避免界面卡顿
            if (content.length() > 10000) {
                content = content.substring(0, 10000) + "\n\n... (内容过长，仅显示前10000个字符)";
            }
            
            new AlertDialog.Builder(requireActivity())
                    .setTitle("日志文件: " + logFile.getName())
                    .setMessage(content)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        } catch (IOException e) {
            Toast.makeText(requireActivity(), "读取日志文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showCleanDownloadLogsDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_clean_logs)
                .setMessage("确定要删除7天前的旧日志文件吗？")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    mDownloadLogger.cleanOldLogs();
                    Toast.makeText(requireActivity(), "旧日志文件清理完成", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private File createRepairLogFile() {
        File dir = AppConfig.getExternalLogcatDir();
        if (dir == null) {
            Log.e(TAG, "无法获取logcat目录");
            return null;
        }
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "无法创建logcat目录: " + dir.getAbsolutePath());
            return null;
        }
        String name = "repair_gallery_" + ReadableTime.getFilenamableTime(System.currentTimeMillis()) + ".log";
        return new File(dir, name);
    }
    
    /**
     * 显示清空无效下载项的详细信息
     */
    private void showCleanInvalidDownloadDetails() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.clean_invalid_download_detail_title)
                .setMessage(R.string.clean_invalid_download_detail_message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}

