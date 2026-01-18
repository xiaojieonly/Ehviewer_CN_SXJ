package com.hippo.ehviewer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.download.DownloadLogger;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 后台任务管理器
 * 提供多线程处理和数据库单线程操作的能力
 */
public class BackgroundTaskManager {
    
    private static final String TAG = BackgroundTaskManager.class.getSimpleName();
    private static final String CHANNEL_ID = "eh_background_tasks";
    private static final int NOTIFICATION_ID = 1001;
    
    private static BackgroundTaskManager sInstance;
    
    private final Context mContext;
    private final Handler mMainHandler;
    
    // CPU密集型任务线程池（用于文件扫描等）
    private final ExecutorService mCpuExecutor;
    
    // 数据库操作单线程执行器（确保数据库操作串行化）
    private final ExecutorService mDbExecutor;
    
    // IO密集型任务线程池（用于网络或文件IO）
    private final ExecutorService mIoExecutor;
    
    // 网络线程池（专门用于与Eh交互）
    private final ExecutorService mNetworkExecutor;
    
    private NotificationManager mNotificationManager;
    private boolean mForegroundServiceRunning = false;
    private final AtomicInteger mActiveTaskCount = new AtomicInteger(0);
    
    // 下载日志记录器
    private final DownloadLogger mDownloadLogger;
    
    // 任务状态管理器
    private final BackgroundTaskStatusManager mTaskStatusManager;
    
    public static synchronized void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new BackgroundTaskManager(context.getApplicationContext());
        }
    }
    
    public static BackgroundTaskManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("BackgroundTaskManager not initialized");
        }
        return sInstance;
    }
    
    private BackgroundTaskManager(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化下载日志记录器
        mDownloadLogger = DownloadLogger.getInstance();
        
        // 初始化任务状态管理器
        mTaskStatusManager = BackgroundTaskStatusManager.getInstance();
        
        // CPU线程池：核心线程数 = CPU核心数，最大线程数 = CPU核心数 * 2
        int cpuCount = Runtime.getRuntime().availableProcessors();
        int corePoolSize = Math.max(2, cpuCount);
        int maxPoolSize = cpuCount * 2;
        mCpuExecutor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory()
        );
        
        // 数据库执行器：单线程确保数据库操作串行化
        mDbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "BackgroundTaskManager-DB");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        
        // IO线程池：用于文件IO、网络请求等
        // 对于重建下载记录等IO密集型任务，使用更多线程
        int ioCorePoolSize = Math.max(4, cpuCount);
        int ioMaxPoolSize = cpuCount * 3;
        mIoExecutor = new ThreadPoolExecutor(
                ioCorePoolSize,
                ioMaxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread thread = new Thread(r, "BackgroundTaskManager-IO");
                    thread.setPriority(Thread.NORM_PRIORITY - 1); // 稍微降低优先级
                    return thread;
                }
        );
        
        // 网络线程池：专门用于与Eh交互
        int networkCorePoolSize = Math.max(2, cpuCount / 2);
        int networkMaxPoolSize = cpuCount;
        mNetworkExecutor = new ThreadPoolExecutor(
                networkCorePoolSize,
                networkMaxPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                r -> {
                    Thread thread = new Thread(r, "BackgroundTaskManager-Network");
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                }
        );
        
        // 初始化通知通道
        initNotificationChannel();
    }
    
    private void initNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mNotificationManager = mContext.getSystemService(NotificationManager.class);
            if (mNotificationManager != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        mContext.getString(R.string.background_tasks_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(mContext.getString(R.string.background_tasks_channel_description));
                channel.setShowBadge(false);
                mNotificationManager.createNotificationChannel(channel);
            }
        } else {
            mNotificationManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        }
    }
    
    /**
     * 提交CPU密集型任务（如图像处理、计算等）
     */
    public <T> Future<T> submitCpuTask(Callable<T> task) {
        return mCpuExecutor.submit(task);
    }
    
    /**
     * 提交CPU密集型任务（无返回值）
     */
    public Future<?> submitCpuTask(Runnable task) {
        return mCpuExecutor.submit(task);
    }
    
    /**
     * 提交数据库操作任务（确保单线程执行）
     */
    public <T> Future<T> submitDbTask(Callable<T> task) {
        return mDbExecutor.submit(task);
    }
    
    /**
     * 提交数据库操作任务（无返回值）
     */
    public Future<?> submitDbTask(Runnable task) {
        return mDbExecutor.submit(task);
    }
    
    /**
     * 提交IO密集型任务（如文件读写、网络请求等）
     */
    public <T> Future<T> submitIoTask(Callable<T> task) {
        return mIoExecutor.submit(task);
    }
    
    /**
     * 提交IO密集型任务（无返回值）
     */
    public Future<?> submitIoTask(Runnable task) {
        return mIoExecutor.submit(task);
    }
    
    /**
     * 提交网络任务（专门用于与Eh交互）
     */
    public <T> Future<T> submitNetworkTask(Callable<T> task) {
        return mNetworkExecutor.submit(task);
    }
    
    /**
     * 提交网络任务（无返回值）
     */
    public Future<?> submitNetworkTask(Runnable task) {
        return mNetworkExecutor.submit(task);
    }
    
    /**
     * 开始一个前台任务（显示通知）
     */
    public void startForegroundTask(String taskName, @Nullable String taskDescription) {
        int activeCount = mActiveTaskCount.incrementAndGet();
        Log.d(TAG, "Start foreground task: " + taskName + ", active tasks: " + activeCount);
        
        if (activeCount == 1) {
            // 第一个前台任务，显示通知
            showForegroundNotification(taskName, taskDescription);
        } else {
            // 更新现有通知
            updateForegroundNotification(taskName, taskDescription);
        }
    }
    
    /**
     * 结束一个前台任务
     */
    public void endForegroundTask(String taskName) {
        int activeCount = mActiveTaskCount.decrementAndGet();
        Log.d(TAG, "End foreground task: " + taskName + ", active tasks: " + activeCount);
        
        if (activeCount <= 0) {
            mActiveTaskCount.set(0);
            hideForegroundNotification();
        } else {
            updateForegroundNotification(null, null);
        }
    }
    
    /**
     * 显示前台通知
     */
    private void showForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        String title = taskName != null ? taskName : mContext.getString(R.string.background_task_running);
        String text = taskDescription != null ? taskDescription : mContext.getString(R.string.background_task_running_description);
        
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();
        
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 更新前台通知
     */
    private void updateForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        int activeTasks = mActiveTaskCount.get();
        if (activeTasks <= 0) {
            hideForegroundNotification();
            return;
        }
        
        String title = taskName != null ? taskName : mContext.getString(R.string.background_task_running);
        String text = taskDescription != null ? taskDescription : 
                mContext.getString(R.string.background_task_running_description) + " (" + activeTasks + ")";
        
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build();
        
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 隐藏前台通知
     */
    private void hideForegroundNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }
    
    /**
     * 在UI线程执行任务
     */
    public void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mMainHandler.post(runnable);
        }
    }
    
    /**
     * 提交扫描下载文件任务
     * @return Future用于等待任务完成或取消任务
     */
    public Future<?> submitScanDownloadTask(@Nullable final DownloadedFileManager.ScanProgressListener progressListener) {
        long startTime = System.currentTimeMillis();
        mDownloadLogger.logBackgroundTaskStart("ScanDownload", "扫描下载文件");
        
        String taskName = mContext.getString(R.string.settings_download_scan_download_files);
        String taskDescription = mContext.getString(R.string.settings_download_scan_download_files_summary);
        
        // 添加到任务状态管理器
        String taskId = mTaskStatusManager.addTask(taskName, taskDescription, null);
        
        return submitIoTask(() -> {
            startForegroundTask(taskName, taskDescription);
            
            try {
                DownloadedFileManager manager = DownloadedFileManager.getInstance();
                manager.scanDownloadDirectories(new DownloadedFileManager.ScanProgressListener() {
                    @Override
                    public void onProgress(final int current, final int total) {
                        mDownloadLogger.logBackgroundTaskProgress("ScanDownload", "扫描下载文件", current, total);
                        
                        // 更新任务进度
                        mTaskStatusManager.updateTaskProgress(taskId, current, total);
                        
                        runOnUiThread(() -> {
                            if (progressListener != null) {
                                progressListener.onProgress(current, total);
                            }
                        });
                    }

                    @Override
                    public void onCompleted() {
                        long totalTime = System.currentTimeMillis() - startTime;
                        mDownloadLogger.logBackgroundTaskComplete("ScanDownload", "扫描下载文件", totalTime, true);
                        
                        // 标记任务完成
                        mTaskStatusManager.markTaskCompleted(taskId);
                        
                        runOnUiThread(() -> {
                            if (progressListener != null) {
                                progressListener.onCompleted();
                            }
                        });
                        endForegroundTask("scan_download");
                    }

                    @Override
                    public void onError(final Exception e) {
                        long totalTime = System.currentTimeMillis() - startTime;
                        mDownloadLogger.logBackgroundTaskComplete("ScanDownload", "扫描下载文件", totalTime, false);
                        mDownloadLogger.logDownloadError("ScanDownload", "扫描下载文件", e.getMessage(), e);
                        
                        // 标记任务出错
                        mTaskStatusManager.markTaskError(taskId, e.getMessage());
                        
                        runOnUiThread(() -> {
                            if (progressListener != null) {
                                progressListener.onError(e);
                            }
                        });
                        endForegroundTask("scan_download");
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error in scan download task", e);
                
                // 标记任务出错
                mTaskStatusManager.markTaskError(taskId, e.getMessage());
                
                endForegroundTask("scan_download");
                runOnUiThread(() -> {
                    if (progressListener != null) {
                        progressListener.onError(e);
                    }
                });
                throw e; // 重新抛出异常，以便Future能够捕获
            }
        });
    }
    
    /**
     * 提交通用后台任务
     * @param taskName 任务名称（显示在通知中）
     * @param taskDescription 任务描述（显示在通知中）
     * @param task 要执行的任务
     * @return Future用于等待任务完成或取消任务
     */
    public Future<?> submitLongRunningTask(String taskName, String taskDescription, Runnable task) {
        return submitLongRunningTask(taskName, taskDescription, task, null);
    }

    public Future<?> submitLongRunningTask(String taskName, String taskDescription, Runnable task, @Nullable String existingTaskId) {
        // 添加到任务状态管理器（如果未提供现有任务ID）
        final String taskId = existingTaskId != null ? existingTaskId : mTaskStatusManager.addTask(taskName, taskDescription, null);
        
        return submitIoTask(() -> {
            startForegroundTask(taskName, taskDescription);
            try {
                task.run();
                // 标记任务完成
                mTaskStatusManager.markTaskCompleted(taskId);
            } catch (Exception e) {
                // 标记任务出错
                mTaskStatusManager.markTaskError(taskId, e.getMessage());
                throw e;
            } finally {
                endForegroundTask(taskName);
            }
        });
    }
    
    /**
     * 提交恢复下载项任务
     * @return Future用于等待任务完成或取消任务
     */
    public Future<?> submitRestoreDownloadTask(Runnable task) {
        return submitLongRunningTask(
                mContext.getString(R.string.settings_download_restore_download_items),
                mContext.getString(R.string.settings_download_restore_download_items_summary),
                task
        );
    }
    
    /**
     * 提交合并重复画廊任务
     * @param task 合并任务
     * @return Future用于等待任务完成或取消任务
     */
    public Future<?> submitMergeDuplicateGalleryTask(Runnable task) {
        return submitLongRunningTask(
                mContext.getString(R.string.settings_download_merge_duplicate_gallery),
                mContext.getString(R.string.settings_download_merge_duplicate_gallery_summary),
                task
        );
    }
    
    /**
     * 获取任务状态管理器
     */
    public BackgroundTaskStatusManager getTaskStatusManager() {
        return mTaskStatusManager;
    }
    
    /**
     * 关闭所有线程池
     */
    public void shutdown() {
        mCpuExecutor.shutdown();
        mDbExecutor.shutdown();
        mIoExecutor.shutdown();
        mNetworkExecutor.shutdown();
        
        try {
            if (!mCpuExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mCpuExecutor.shutdownNow();
            }
            if (!mDbExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mDbExecutor.shutdownNow();
            }
            if (!mIoExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mIoExecutor.shutdownNow();
            }
            if (!mNetworkExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                mNetworkExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            mCpuExecutor.shutdownNow();
            mDbExecutor.shutdownNow();
            mIoExecutor.shutdownNow();
            mNetworkExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        hideForegroundNotification();
    }
}