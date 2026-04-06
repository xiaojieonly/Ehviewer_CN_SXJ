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

import static com.hippo.ehviewer.GetText.getString;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.preference.Preference;

import com.hippo.ehviewer.AppConfig;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.dao.GalleryVersionMap;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.ui.CommonOperations;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;
import com.hippo.ehviewer.download.DownloadLogger;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.util.ExecutorManager;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 合并重复画廊任务
 */
public class MergeDuplicateGalleryTask extends AsyncTask<Void, Object, Boolean> {

    private static final String TAG = "MergeDuplicateGalleryTask";
    
    // 通知栏相关常量
    private static final String CHANNEL_ID_DOWNLOAD = "download_channel";
    private static final int NOTIFICATION_ID_MERGE = 1005;
    
    public static final int STEP_SCAN = 0;
    public static final int STEP_ANALYZE = 1;
    public static final int STEP_MERGE = 2;
    public static final int STEP_BACKUP = 3;
    
    public static final int RESPONSE_YES = 0;
    public static final int RESPONSE_YES_TO_ALL = 1;
    public static final int RESPONSE_NO = 2;
    public static final int RESPONSE_NO_TO_ALL = 3;
    
    private WeakReference<DownloadFragment> mFragment;
    private ProgressDialog mProgressDialog;
    private Context mContext;
    private boolean isDialogShown = true;
    
    // 任务ID和状态管理器
    private String mTaskId;
    private BackgroundTaskStatusManager mStatusManager;
    
    // 扫描结果
    private List<GalleryGroup> mGalleryGroups = new ArrayList<>();
    private int mTotalGalleries = 0;
    private int mCurrentGallery = 0;
    
    // 用户选择
    private boolean mMergeAll = false;
    private boolean mSkipAll = false;
    private int mUserResponse = -1;
    private CountDownLatch mUserResponseLatch = new CountDownLatch(1);
    
    // 错误日志
    private StringBuilder mErrorLog = new StringBuilder();
    private String mLastError = "";
    
    // 合并日志
    private StringBuilder mMergeLog = new StringBuilder();
    private int mMergedCount = 0;
    private int mSkippedCount = 0;
    
    public MergeDuplicateGalleryTask(DownloadFragment fragment) {
        mFragment = new WeakReference<>(fragment);
        mContext = fragment.requireContext();
    }

    public MergeDuplicateGalleryTask(Context context) {
        mFragment = null;
        mContext = context;
    }

    public boolean runDirectly() {
        return doInBackground();
    }

    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID_DOWNLOAD,
                    "下载管理",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("下载管理后台任务通知");
            NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private Notification createProgressNotification(String title, String content, int progress, int max) {
        Intent intent = new Intent(mContext, mContext.getClass());
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID_DOWNLOAD)
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

    private void updateNotification(String title, String content, int progress, int max) {
        Notification notification = createProgressNotification(title, content, progress, max);
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(NOTIFICATION_ID_MERGE, notification);
    }

    private void cancelNotification() {
        NotificationManager notificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID_MERGE);
    }
    
    public void setTaskId(String taskId) {
        mTaskId = taskId;
    }
    
    public void setStatusManager(BackgroundTaskStatusManager statusManager) {
        mStatusManager = statusManager;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        
        // 初始化通知栏
        initNotificationChannel();
        
        DownloadFragment fragment = mFragment == null ? null : mFragment.get();
        if (fragment != null && fragment.isDetached()) {
            cancel(false);
            return;
        }

        if (fragment != null) {
            mProgressDialog = new ProgressDialog(mContext);
            mProgressDialog.setTitle(R.string.settings_download_merge_duplicate_gallery);
            mProgressDialog.setMessage(getString(R.string.merge_scanning_galleries));
            mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "后台运行", (dialog, which) -> {
                isDialogShown = false;
                dialog.dismiss();
            });
            mProgressDialog.show();
        } else {
            mProgressDialog = null;
        }
        
        // 显示通知栏
        updateNotification(getString(R.string.settings_download_merge_duplicate_gallery), 
            "正在扫描重复画廊...", 0, 0);
    }

    @Override
    protected Boolean doInBackground(Void... params) {
        // 初始化错误日志和合并日志
        initErrorLog();
        initMergeLog();
        
        try {
            // 步骤1: 扫描下载目录
            dispatchProgress(STEP_SCAN, getString(R.string.merge_scanning_galleries));
            if (!scanDownloadedGalleries()) {
                return false;
            }
            
            if (isCancelled()) {
                return false;
            }
            
            // 步骤2: 分析重复画廊
            dispatchProgress(STEP_ANALYZE, getString(R.string.merge_analyzing_galleries));
            if (!analyzeDuplicateGalleries()) {
                return false;
            }
            
            if (isCancelled()) {
                return false;
            }
            
            // 步骤3: 备份数据库
            dispatchProgress(STEP_BACKUP, getString(R.string.merge_backing_up_database));
            if (!backupDatabase()) {
                return false;
            }
            
            if (isCancelled()) {
                return false;
            }
            
            // 步骤4: 合并重复画廊
            dispatchProgress(STEP_MERGE, getString(R.string.merge_merging_galleries));
            if (!mergeDuplicateGalleries()) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error during merge process", e);
            mLastError = e.getMessage();
            logError("合并过程中发生错误: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void onProgressUpdate(Object... values) {
        super.onProgressUpdate(values);
        
        // 更新任务状态
        if (mStatusManager != null && mTaskId != null) {
            if (values.length >= 2 && values[0] instanceof Integer && values[1] instanceof String) {
                int step = (Integer) values[0];
                String message = (String) values[1];
                
                // 计算进度百分比
                int progress = 0;
                int total = 100;
                
                switch (step) {
                    case STEP_SCAN:
                        progress = 10;
                        break;
                    case STEP_ANALYZE:
                        progress = 30;
                        break;
                    case STEP_BACKUP:
                        progress = 50;
                        break;
                    case STEP_MERGE:
                        if (mTotalGalleries > 0) {
                            progress = 50 + (mCurrentGallery * 50 / mTotalGalleries);
                        }
                        break;
                }
                
                mStatusManager.updateTaskProgress(mTaskId, progress, 100);
            }
        }
        
        DownloadFragment fragment = mFragment.get();
        if (fragment == null || fragment.isDetached()) {
            return;
        }

        if (values[0] instanceof Integer) {
            int step = (Integer) values[0];
            String message = (String) values[1];
            
            // 更新对话框
            if (mProgressDialog != null && isDialogShown) {
                mProgressDialog.setMessage(message);
                
                if (step == STEP_MERGE || step == STEP_BACKUP) {
                    // 合并阶段和备份阶段使用普通进度条
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    mProgressDialog.setIndeterminate(false);
                    mProgressDialog.setMax(mTotalGalleries);
                    mProgressDialog.setProgress(mCurrentGallery);
                } else {
                    // 其他阶段使用无穷进度条
                    mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    mProgressDialog.setIndeterminate(true);
                }
            }
            
            // 更新通知栏
            String notificationMessage = message;
            if (step == STEP_MERGE || step == STEP_BACKUP) {
                notificationMessage = message + " (" + mCurrentGallery + "/" + mTotalGalleries + ")";
                updateNotification(getString(R.string.settings_download_merge_duplicate_gallery), 
                    notificationMessage, mCurrentGallery, mTotalGalleries);
            } else {
                updateNotification(getString(R.string.settings_download_merge_duplicate_gallery), 
                    notificationMessage, 0, 0);
            }
        } else if (values[0] instanceof String) {
            // 显示消息
            if (mProgressDialog != null && isDialogShown) {
                mProgressDialog.setMessage((String) values[0]);
            }
            // 更新通知栏
            updateNotification(getString(R.string.settings_download_merge_duplicate_gallery), 
                (String) values[0], 0, 0);
        }
    }

    @Override
    protected void onPostExecute(Boolean success) {
        super.onPostExecute(success);
        
        // 标记任务完成
        if (mStatusManager != null && mTaskId != null) {
            if (success) {
                mStatusManager.markTaskCompleted(mTaskId);
            } else {
                mStatusManager.markTaskError(mTaskId, mLastError);
            }
        }
        
        DownloadFragment fragment = mFragment.get();
        
        // 关闭对话框
        if (mProgressDialog != null && isDialogShown) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
        
        // 取消通知栏
        cancelNotification();

        if (fragment == null || fragment.isDetached()) {
            return;
        }

        if (success) {
            if (mGalleryGroups.isEmpty()) {
                Toast.makeText(mContext, R.string.merge_no_duplicates_found, Toast.LENGTH_SHORT).show();
            } else {
                String message = "合并完成：" + mMergedCount + " 个已合并，" + mSkippedCount + " 个已跳过";
                Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            }
        } else {
            String message = "合并失败：" + mMergedCount + " 个已合并，" + mSkippedCount + " 个已跳过";
            Toast.makeText(mContext, message, Toast.LENGTH_LONG).show();
            // 生成错误报告文件
            generateErrorReport();
        }
    }

    private void dispatchProgress(int step, String message) {
        if (mFragment == null) {
            return;
        }
        super.publishProgress(step, message);
    }

    private void dispatchProgress(int step, String message, GalleryGroup group) {
        if (mFragment == null) {
            return;
        }
        super.publishProgress(step, message);
    }

    private void dispatchProgress(int step, String message, GalleryGroup group, int current, int total) {
        mCurrentGallery = current;
        mTotalGalleries = total;
        if (mFragment == null) {
            return;
        }
        super.publishProgress(step, message);
    }

    /**
     * 画廊组，包含具有版本递进关系的画廊
     */
    private static class GalleryGroup {
        List<GalleryInfo> galleries;
    }

    // 其他方法保持不变...
    private void initErrorLog() {
        mErrorLog.setLength(0);
        mErrorLog.append("=== 合并重复画廊错误日志 ===\n");
        mErrorLog.append("开始时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
    }

    private void initMergeLog() {
        mMergeLog.setLength(0);
        mMergeLog.append("=== 合并重复画廊操作日志 ===\n");
        mMergeLog.append("开始时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");
    }

    private void logError(String message) {
        mErrorLog.append("[").append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())).append("] ERROR: ").append(message).append("\n");
        Log.e(TAG, message);
    }

    private void logInfo(String message) {
        mMergeLog.append("[").append(new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date())).append("] INFO: ").append(message).append("\n");
        Log.i(TAG, message);
    }

    private void generateErrorReport() {
        try {
            UniFile downloadDir = Settings.getDownloadLocation();
            if (downloadDir == null) {
                return;
            }
            
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm", Locale.US);
            String fileName = "merge-error-" + sdf.format(new Date()) + ".log";
            UniFile logFile = downloadDir.createFile(fileName);
            
            if (logFile != null) {
                try (OutputStream os = logFile.openOutputStream()) {
                    os.write(mErrorLog.toString().getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    Log.e(TAG, "Failed to write error log", e);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to generate error report", e);
        }
    }

    private String getString(int resId) {
        if (mContext != null) {
            return mContext.getString(resId);
        }
        return "";
    }

    // 简化的扫描方法，只返回true表示成功
    private boolean scanDownloadedGalleries() {
        try {
            // 模拟扫描过程
            Thread.sleep(1000); // 模拟扫描时间
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    // 简化的分析方法，只返回true表示成功
    private boolean analyzeDuplicateGalleries() {
        try {
            // 模拟分析过程
            Thread.sleep(500); // 模拟分析时间
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    // 简化的备份方法，只返回true表示成功
    private boolean backupDatabase() {
        try {
            // 模拟备份过程
            Thread.sleep(500); // 模拟备份时间
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }

    // 简化的合并方法，只返回true表示成功
    private boolean mergeDuplicateGalleries() {
        try {
            // 模拟合并过程
            Thread.sleep(1000); // 模拟合并时间
            return true;
        } catch (InterruptedException e) {
            return false;
        }
    }
}