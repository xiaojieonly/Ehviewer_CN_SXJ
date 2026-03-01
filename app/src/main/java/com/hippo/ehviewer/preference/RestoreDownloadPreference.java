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

package com.hippo.ehviewer.preference;

import android.app.Activity;
import android.content.Context;
import android.os.Parcel;
import androidx.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhEngine;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.ehviewer.task.BackgroundTask;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.ehviewer.util.UiThreadHelper;
import com.hippo.lib.yorozuya.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import okhttp3.OkHttpClient;

public class RestoreDownloadPreference extends Preference {

    private Future<?> mTask;
    private BackgroundTaskManager mTaskManager;

    public RestoreDownloadPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RestoreDownloadPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onClick() {
        super.onClick();
        if (mTask == null) {
            Context context = getContext();
            if (context != null) {
                mTaskManager = BackgroundTaskManager.getInstance();
                RestoreDownloadTask task = new RestoreDownloadTask(context);
                mTask = task.execute();
                
                // 显示任务开始的提示
                Toast.makeText(context, R.string.settings_download_restore_started, Toast.LENGTH_SHORT).show();
            }
        } else {
            // 任务正在运行
            Context context = getContext();
            if (context != null) {
                Toast.makeText(context, R.string.settings_download_restore_already_running, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 恢复下载项任务
     * 恢复有完整.ehviewer元数据文件的下载项
     */
    private static class RestoreDownloadTask {
        
        private final Context context;
        private final EhApplication application;
        private final DownloadManager downloadManager;
        private final OkHttpClient httpClient;
        private final BackgroundTaskManager taskManager;
        
        private volatile boolean isPaused = false;
        private volatile boolean isCancelled = false;
        private BackgroundTask.ProgressListener progressListener;
        private LogListener logListener;
        
        private int currentProgress = 0;
        private int totalProgress = 0;
        private int successCount = 0;
        private int foundCount = 0;

        public RestoreDownloadTask(@NonNull Context context) {
            this.context = context;
            this.application = (EhApplication) context.getApplicationContext();
            this.downloadManager = EhApplication.getDownloadManager(application);
            this.httpClient = EhApplication.getOkHttpClient(application);
            this.taskManager = BackgroundTaskManager.getInstance();
        }
        
        /**
         * 执行任务并返回Future对象
         */
        public Future<?> execute() {
            String taskName = application.getString(R.string.settings_download_restore_download_items);
            String taskDesc = application.getString(R.string.settings_download_restore_download_items_summary);
            
            // 添加到任务状态管理器
                String taskId = taskManager.getTaskStatusManager().addTask(
                    taskName,
                    taskDesc,
                    null,
                    BackgroundTask.TaskType.SCAN,
                    true
                );
                    if (taskId == null) {
                    UiThreadHelper.runOnUiThread(() ->
                        Toast.makeText(context, R.string.background_task_unique_running, Toast.LENGTH_SHORT).show());
                return taskManager.submitLongRunningTask(taskName, taskDesc, () -> {}, null,
                    BackgroundTask.TaskType.SCAN, true);
                }
            
            return taskManager.submitRestoreDownloadTask(new Runnable() {
                @Override
                public void run() {
                    try {
                        taskManager.getTaskStatusManager().appendTaskLog(taskId, "开始恢复下载项");
                        
                        UniFile downloadDir = Settings.getDownloadLocation();
                        if (downloadDir == null) {
                            taskManager.getTaskStatusManager().appendTaskLog(taskId, "下载目录无效");
                            taskManager.getTaskStatusManager().markTaskError(taskId, "下载目录无效");
                            return;
                        }
                        
                        UniFile[] files = downloadDir.listFiles();
                        if (files == null) {
                            taskManager.getTaskStatusManager().appendTaskLog(taskId, "无法列出下载目录文件");
                            taskManager.getTaskStatusManager().markTaskError(taskId, "无法列出下载目录文件");
                            return;
                        }
                        
                        totalProgress = files.length;
                        currentProgress = 0;
                        foundCount = 0;
                        successCount = 0;
                        
                        List<RestoreItem> restoreItemList = new ArrayList<>();
                        
                        // 扫描阶段
                        taskManager.getTaskStatusManager().appendTaskLog(taskId, 
                            "开始扫描下载目录，共 " + files.length + " 个文件夹");
                        
                        for (int i = 0; i < files.length; i++) {
                            if (isCancelled) {
                                taskManager.getTaskStatusManager().appendTaskLog(taskId, "任务已取消");
                                taskManager.getTaskStatusManager().markTaskError(taskId, "任务已取消");
                                return;
                            }
                            
                            // 检查暂停状态
                            while (isPaused && !isCancelled) {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    taskManager.getTaskStatusManager().appendTaskLog(taskId, "任务被中断");
                                    taskManager.getTaskStatusManager().markTaskError(taskId, "任务被中断");
                                    return;
                                }
                            }
                            
                            UniFile file = files[i];
                            RestoreItem restoreItem = getRestoreItem(file);
                            if (restoreItem != null) {
                                restoreItemList.add(restoreItem);
                                foundCount++;
                            }
                            
                            currentProgress = i + 1;
                            String progressDetail = "扫描中 " + currentProgress + "/" + totalProgress + "，找到 " + foundCount + " 个可恢复项";
                            taskManager.getTaskStatusManager().updateTaskProgress(taskId, currentProgress, totalProgress, progressDetail);
                        }
                        
                        if (restoreItemList.isEmpty()) {
                            taskManager.getTaskStatusManager().appendTaskLog(taskId, "未找到可恢复的下载项");
                            taskManager.getTaskStatusManager().markTaskCompleted(taskId);
                            
                            // 在主线程显示Toast
                            taskManager.runOnUiThread(() -> {
                                UiThreadHelper.showToastSafely(application, R.string.settings_download_restore_not_found, 
                                    Toast.LENGTH_SHORT);
                            });
                            return;
                        }
                        
                        // 第二阶段：获取画廊信息
                        taskManager.getTaskStatusManager().appendTaskLog(taskId, 
                            "找到 " + foundCount + " 个可恢复项，正在获取画廊信息...");
                        taskManager.getTaskStatusManager().updateTaskProgress(taskId, -1, -1, "获取画廊信息中...");
                        
                        List<GalleryInfo> galleryList;
                        try {
                            galleryList = EhEngine.fillGalleryListByApi(null, httpClient, 
                                new ArrayList<GalleryInfo>(restoreItemList), EhUrl.getReferer());
                        } catch (Throwable e) {
                            ExceptionUtils.throwIfFatal(e);
                            taskManager.getTaskStatusManager().appendTaskLog(taskId, "获取画廊信息失败: " + e.getMessage());
                            taskManager.getTaskStatusManager().markTaskError(taskId, e.getMessage());
                            
                            // 在主线程显示Toast
                            taskManager.runOnUiThread(() -> {
                                UiThreadHelper.showToastSafely(application, R.string.settings_download_restore_failed, 
                                    Toast.LENGTH_SHORT);
                            });
                            return;
                        }
                        
                        // 第三阶段：添加到下载管理器
                        if (galleryList != null) {
                            for (int i = 0; i < restoreItemList.size() && i < galleryList.size(); i++) {
                                RestoreItem item = restoreItemList.get(i);
                                GalleryInfo gallery = galleryList.get(i);
                                if (gallery != null && gallery.title != null) {
                                    // 复制画廊信息
                                    item.title = gallery.title;
                                    item.thumb = gallery.thumb;
                                    item.category = gallery.category;
                                    item.posted = gallery.posted;
                                    item.uploader = gallery.uploader;
                                    item.rating = gallery.rating;
                                    item.pages = gallery.pages;
                                    
                                    // 添加到下载管理器
                                    downloadManager.addDownload(item, null);
                                    EhDB.putDownloadDirname(item.gid, item.dirname);
                                    successCount++;
                                    
                                    taskManager.getTaskStatusManager().appendTaskLog(taskId, 
                                        "已恢复: " + item.title + " (GID: " + item.gid + ")");
                                }
                            }
                        }
                        
                        String finalResult = "恢复完成: 成功 " + successCount + "/" + foundCount + " 个";
                        taskManager.getTaskStatusManager().appendTaskLog(taskId, finalResult);
                        taskManager.getTaskStatusManager().markTaskCompleted(taskId);
                        
                        // 在主线程显示Toast
                        taskManager.runOnUiThread(() -> {
                            int failCount = foundCount - successCount;
                            String message;
                            if (successCount > 0) {
                                message = application.getString(R.string.settings_download_restore_successfully_with_details, 
                                    successCount, failCount);
                                if (failCount > 0) {
                                    message += "\n" + application.getString(R.string.settings_download_restore_check_logs);
                                }
                            } else {
                                message = application.getString(R.string.settings_download_restore_failed_all);
                                message += "\n" + application.getString(R.string.settings_download_restore_check_logs);
                            }
                            
                            UiThreadHelper.showToastSafely(message, Toast.LENGTH_LONG);

                            if (context instanceof Activity) {
                                ((Activity) context).setResult(Activity.RESULT_OK);
                            }
                        });
                        
                    } catch (Exception e) {
                        taskManager.getTaskStatusManager().appendTaskLog(taskId, "恢复任务出错: " + e.getMessage());
                        taskManager.getTaskStatusManager().markTaskError(taskId, e.getMessage());
                        
                        // 在主线程显示Toast
                        taskManager.runOnUiThread(() -> {
                            UiThreadHelper.showToastSafely(application, R.string.settings_download_restore_failed, 
                                Toast.LENGTH_SHORT);
                        });
                    }
                }
            });
        }
        
        private RestoreItem getRestoreItem(UniFile file) {
            if (file == null || !file.isDirectory()) {
                return null;
            }
            
            UniFile siFile = file.findFile(SpiderQueen.SPIDER_INFO_FILENAME);
            if (siFile == null) {
                return null;
            }
            
            InputStream inputStream = null;
            try {
                inputStream = siFile.openInputStream();
                SpiderInfo spiderInfo = SpiderInfo.read(inputStream);
                if (spiderInfo == null) {
                    return null;
                }
                
                long gid = spiderInfo.gid;
                if (downloadManager.containDownloadInfo(gid)) {
                    return null;
                }
                
                RestoreItem restoreItem = new RestoreItem();
                restoreItem.gid = gid;
                restoreItem.token = spiderInfo.token;
                restoreItem.dirname = file.getName();
                return restoreItem;
            } catch (IOException e) {
                return null;
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
        
        private void logStep(String message) {
            if (logListener != null) {
                logListener.onLog(message);
            }
        }
        
        /**
         * 获取恢复结果统计
         */
        public RestoreResult getRestoreResult() {
            return new RestoreResult(foundCount, successCount);
        }
        
        public static class RestoreResult {
            public final int foundCount;
            public final int successCount;
            
            public RestoreResult(int foundCount, int successCount) {
                this.foundCount = foundCount;
                this.successCount = successCount;
            }
        }
        
        public interface LogListener {
            void onLog(String message);
        }
    }

    private static class RestoreItem extends GalleryInfo {

        public String dirname;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(this.dirname);
        }

        public RestoreItem() {
        }

        protected RestoreItem(Parcel in) {
            super(in);
            this.dirname = in.readString();
        }

        public static final Creator<RestoreItem> CREATOR = new Creator<RestoreItem>() {
            @Override
            public RestoreItem createFromParcel(Parcel source) {
                return new RestoreItem(source);
            }

            @Override
            public RestoreItem[] newArray(int size) {
                return new RestoreItem[size];
            }
        };
    }
}
