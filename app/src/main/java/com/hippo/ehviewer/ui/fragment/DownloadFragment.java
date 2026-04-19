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
import com.hippo.ehviewer.task.RebuildDownloadRecordsTask;
import com.hippo.ehviewer.task.TaskExecutor;
import com.hippo.ehviewer.task.ScanDownloadTask;
import com.hippo.ehviewer.task.CleanRedundancyTask;
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
        Preference showFolderTimeOnCard = findPreference(Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_TIME);
        Preference showFolderSizeOnCard = findPreference(Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_SIZE);
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
        if (showFolderTimeOnCard != null) {
            showFolderTimeOnCard.setOnPreferenceChangeListener(this);
        }
        if (showFolderSizeOnCard != null) {
            showFolderSizeOnCard.setOnPreferenceChangeListener(this);
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
                        com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask task =
                                new com.hippo.ehviewer.task.impl.CleanInvalidDownloadTask(requireActivity());
                        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                        Toast.makeText(requireActivity(),
                                R.string.settings_download_clean_invalid_download,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.show_details, (dialog, which) -> showCleanInvalidDownloadDetails())
                    .show();
            return true;
        } else if (KEY_REPAIR_ALL_DOWNLOADED_GALLERY.equals(key)) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.settings_download_repair_all_downloaded_gallery)
                    .setMessage(R.string.repair_all_downloaded_gallery_confirm)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask task =
                                new com.hippo.ehviewer.task.RepairAllDownloadedGalleryTask(requireContext());
                        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                        Toast.makeText(requireActivity(),
                                R.string.settings_download_repair_all_downloaded_gallery,
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
                        com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask task =
                                new com.hippo.ehviewer.task.RepairUnknownCategoryGalleryTask(requireContext());
                        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                        Toast.makeText(requireActivity(),
                                R.string.settings_download_repair_unknown_category_gallery,
                                Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return true;
        } else if (KEY_REBUILD_DOWNLOAD_RECORDS.equals(key)) {
            // 显示Toast并开始预检查
            Toast.makeText(requireActivity(), R.string.settings_download_rebuilding, Toast.LENGTH_SHORT).show();
            
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
                }
            });
            return true;
        } else if ("merge_duplicate_gallery".equals(key)) {
            com.hippo.ehviewer.BackgroundTaskManager taskManager = com.hippo.ehviewer.BackgroundTaskManager.getInstance();
            if (taskManager.getTaskStatusManager().getActiveUniqueNonDownloadTask() != null) {
            Toast.makeText(requireActivity(), R.string.background_task_unique_running, Toast.LENGTH_SHORT).show();
            return true;
            }
            taskManager.submitBackgroundTask(new com.hippo.ehviewer.task.MergeDuplicateGalleryTask(requireContext()));
            Toast.makeText(requireActivity(), R.string.settings_download_merge_duplicate_gallery, Toast.LENGTH_SHORT).show();
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
                    ScanDownloadTask task = new ScanDownloadTask(requireContext());
                    com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showResetMediaScanDialog() {
        new AlertDialog.Builder(requireActivity())
                .setTitle(R.string.settings_download_reset_media_scan)
                .setMessage(R.string.settings_download_reset_media_scan_summary)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    com.hippo.ehviewer.task.ResetMediaScanTask task =
                            new com.hippo.ehviewer.task.ResetMediaScanTask(requireContext());
                    com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetMediaScan() {
        com.hippo.ehviewer.task.ResetMediaScanTask task =
                new com.hippo.ehviewer.task.ResetMediaScanTask(requireContext());
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        Toast.makeText(requireActivity(),
                R.string.settings_download_reset_media_scan, Toast.LENGTH_SHORT).show();
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

        // 使用统一的后台任务
        com.hippo.ehviewer.task.ExportDownloadItemsTask task = 
            new com.hippo.ehviewer.task.ExportDownloadItemsTask(requireActivity());
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        Toast.makeText(requireActivity(),
                R.string.settings_download_export_download_items, Toast.LENGTH_SHORT).show();
    }



    private void importDownloadItems() {
        // 打开文件选择器，选中文件后在onActivityResult中通过ImportDownloadItemsTask处理
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
                if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                    com.hippo.ehviewer.task.ImportDownloadItemsTask task =
                        new com.hippo.ehviewer.task.ImportDownloadItemsTask(requireActivity(), data.getData());
                    com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                    Toast.makeText(requireActivity(),
                            R.string.settings_download_import_items, Toast.LENGTH_SHORT).show();
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
        } else if (Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_TIME.equals(key)) {
            oldValue = Settings.getShowDownloadCardFolderTime();
        } else if (Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_SIZE.equals(key)) {
            oldValue = Settings.getShowDownloadCardFolderSize();
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
        } else if (Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_TIME.equals(key)) {
            if (newValue instanceof Boolean) {
                Settings.putShowDownloadCardFolderTime((Boolean) newValue);
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
            }
            return true;
        } else if (Settings.KEY_SHOW_DOWNLOAD_CARD_FOLDER_SIZE.equals(key)) {
            if (newValue instanceof Boolean) {
                Settings.putShowDownloadCardFolderSize((Boolean) newValue);
                mDownloadLogger.logSettingsChange(key, oldValue, newValue);
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
        private volatile boolean isCancelled = false;
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
            
            // 显示Toast提示
            Toast.makeText(fragment.getActivity(), 
                fragment.getString(R.string.settings_download_import_items), 
                Toast.LENGTH_SHORT).show();
            
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
            
            // 更新通知栏进度
            String content = "正在导入下载记录... (" + progress + "/" + max + ") - 成功:" + successCount + " 失败:" + failCount;
            fragment.updateNotification(NOTIFICATION_ID_IMPORT, 
                fragment.getString(R.string.settings_download_import_items), 
                content, progress, max);
        }

        private void onPostExecute(Integer result) {
            DownloadFragment fragment = mFragment.get();
            
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
    
    // 重建下载记录预检查的辅助方法
    private void showCheckResultOnMainThread(String message) {
        ExecutorManager.runOnMainThread(() -> {
            if (isAdded() && getActivity() != null) {
                Toast.makeText(getActivity(), message, Toast.LENGTH_LONG).show();
            }
        });
    }
    
    private void showConfirmDialogOnMainThread() {
        ExecutorManager.runOnMainThread(() -> {
            if (isAdded() && getActivity() != null) {
                new AlertDialog.Builder(requireActivity())
                        .setTitle(R.string.settings_download_rebuild_download_records)
                        .setMessage(R.string.settings_download_rebuild_confirm)
                        .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                            com.hippo.ehviewer.task.RebuildDownloadRecordsTask task =
                                    new com.hippo.ehviewer.task.RebuildDownloadRecordsTask(requireContext());
                            com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                        })
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
                .setMessage("确定要删除旧日志文件吗？")
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    com.hippo.ehviewer.task.CleanDownloadLogsTask task =
                            new com.hippo.ehviewer.task.CleanDownloadLogsTask(requireContext());
                    com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
                    Toast.makeText(requireActivity(),
                            R.string.settings_download_clean_logs, Toast.LENGTH_SHORT).show();
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

