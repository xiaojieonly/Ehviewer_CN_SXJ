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

import com.hippo.ehviewer.task.impl.CompressSelectedGalleriesTask;
import com.hippo.ehviewer.ui.MainActivity;
import com.hippo.ehviewer.download.DownloadLogger;
import com.hippo.ehviewer.task.BackgroundTask;
import com.hippo.ehviewer.task.BackgroundTaskRunner;
import com.hippo.ehviewer.ui.task.BackgroundTaskInfo;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<String, BackgroundTaskFactory> mTaskFactoryMap = new ConcurrentHashMap<>();
    
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
    private final AtomicInteger mNotificationTaskIndex = new AtomicInteger(0);
    
    // 下载日志记录器
    private final DownloadLogger mDownloadLogger;
    
    // 任务状态管理器
    private final BackgroundTaskStatusManager mTaskStatusManager;

    public interface BackgroundTaskFactory {
        @Nullable
        BackgroundTask create(@NonNull Context context, @NonNull String taskId, @Nullable String persistData);
    }
    
    public static synchronized void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new BackgroundTaskManager(context.getApplicationContext());
            sInstance.recoverPersistedTasks();
        }
    }
    
    public static BackgroundTaskManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("BackgroundTaskManager not initialized");
        }
        return sInstance;
    }

    public void registerTaskFactory(@NonNull String taskClassName, @NonNull BackgroundTaskFactory factory) {
        mTaskFactoryMap.put(taskClassName, factory);
    }

    private void recoverPersistedTasks() {
        for (BackgroundTaskInfo info : mTaskStatusManager.getActiveTasks()) {
            if (info.isCompleted() || info.isCancelled() || info.getFuture() != null) {
                continue;
            }
            String className = info.getTaskClassName();
            String persistData = info.getTaskPersistData();
            if (persistData == null || className == null) {
                continue;
            }
            BackgroundTaskFactory factory = mTaskFactoryMap.get(className);
            if (factory == null) {
                continue;
            }
            BackgroundTask task = factory.create(mContext, info.getTaskId(), persistData);
            if (task != null) {
                mTaskStatusManager.removeTask(info.getTaskId());
                submitBackgroundTask(task);
            }
        }
    }

    private BackgroundTaskManager(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());
        
        // 初始化下载日志记录器
        mDownloadLogger = DownloadLogger.getInstance();
        
        // 初始化任务状态管理器
        BackgroundTaskStatusManager.initialize(context);
        mTaskStatusManager = BackgroundTaskStatusManager.getInstance();

        // 注册可恢复任务工厂
        registerTaskFactory(CompressSelectedGalleriesTask.class.getName(), (ctx, taskId, persistData) ->
                CompressSelectedGalleriesTask.restore(ctx, taskId, persistData));
        
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

    @Nullable
    private BackgroundTaskInfo getNextActiveTaskInfo() {
        List<BackgroundTaskInfo> activeTasks = mTaskStatusManager.getActiveTasks();
        if (activeTasks.isEmpty()) {
            return null;
        }
        int index = Math.abs(mNotificationTaskIndex.getAndIncrement());
        return activeTasks.get(index % activeTasks.size());
    }

    @NonNull
    private String getRunningTasksTitle(int count) {
        if (count <= 0) {
            return mContext.getString(R.string.background_task_running);
        }
        return mContext.getResources().getQuantityString(R.plurals.background_tasks_running, count, count);
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
     * 提交已有的FutureTask到IO线程池
     */
    public Future<?> submitIoFutureTask(java.util.concurrent.FutureTask<?> task) {
        mIoExecutor.execute(task);
        return task;
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
     * 后台任务提交结果
     */
    public static class TaskHandle {
        public final String taskId;
        public final Future<?> future;

        public TaskHandle(@NonNull String taskId, @NonNull Future<?> future) {
            this.taskId = taskId;
            this.future = future;
        }
    }

    /**
     * 提交BackgroundTask并接入任务管理与通知
     */
    @NonNull
    public TaskHandle submitBackgroundTask(@NonNull BackgroundTask task) {
        final String taskId = task.getTaskId();
        final String taskName = task.getTaskName();
        final String taskDescription = task.getTaskDescription();

        if (task.isUniqueTask() && task.getTaskType() != BackgroundTask.TaskType.DOWNLOAD) {
            BackgroundTaskInfo activeUnique = mTaskStatusManager.getActiveUniqueNonDownloadTask();
            if (activeUnique != null) {
                Log.d(TAG, "Skip unique task, active task running: " + activeUnique.getTaskId());
                return new TaskHandle(activeUnique.getTaskId(), createNoOpFuture());
            }
        }

        task.setProgressListener(new BackgroundTask.ProgressListener() {
            @Override
            public void onProgressChanged(int progress, @Nullable String detail) {
                int total = progress >= 0 ? 100 : -1;
                int current = progress >= 0 ? progress : -1;
                mTaskStatusManager.updateTaskProgress(taskId, current, total, detail);
            }

            @Override
            public void onCompleted() {
                mTaskStatusManager.updateTaskProgress(taskId, 100, 100, taskDescription);
            }

            @Override
            public void onError(@NonNull Throwable error) {
                mTaskStatusManager.markTaskError(taskId, error.getMessage());
            }
        });

        java.util.concurrent.FutureTask<?> futureTask = new java.util.concurrent.FutureTask<>(() -> {
            startForegroundTask(taskName, taskDescription);
            try {
                Throwable error = BackgroundTaskRunner.runBlockingExecute(task);
                if (error == null) {
                    mTaskStatusManager.markTaskCompleted(taskId);
                } else {
                    mTaskStatusManager.markTaskError(taskId, error.getMessage());
                }
            } finally {
                endForegroundTask(taskName);
            }
            return null;
        });

        String registeredTaskId = mTaskStatusManager.addTask(taskId, taskName, taskDescription, futureTask,
                task.getTaskType(), task.isUniqueTask(), task.getTaskClassName(), task.getTaskPersistData());
        if (registeredTaskId == null) {
            return new TaskHandle(taskId, createNoOpFuture());
        }
        submitIoFutureTask(futureTask);

        return new TaskHandle(taskId, futureTask);
    }

    private Future<?> createNoOpFuture() {
        java.util.concurrent.FutureTask<?> futureTask = new java.util.concurrent.FutureTask<>(() -> null);
        futureTask.run();
        return futureTask;
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
            showIdleNotification();
        } else {
            updateForegroundNotification(null, null);
        }
    }
    
    /**
     * 显示前台通知
     */
    private void showForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        showForegroundNotification(taskName, taskDescription, -1, -1);
    }
    
    /**
     * 显示前台通知（带进度）
     */
    private void showForegroundNotification(@Nullable String taskName, @Nullable String taskDescription, 
                                          int currentProgress, int totalProgress) {
        int activeTasks = Math.max(mTaskStatusManager.getActiveTaskCount(), mActiveTaskCount.get());
        BackgroundTaskInfo activeInfo = getNextActiveTaskInfo();

        String title = getRunningTasksTitle(activeTasks);
        String line = activeInfo != null ? activeInfo.getTaskName()
            : (taskName != null ? taskName : mContext.getString(R.string.background_task_running));
        String detail = activeInfo != null ? activeInfo.getProgressDetail() : taskDescription;
        int displayCurrent = activeInfo != null ? activeInfo.getCurrentProgress() : currentProgress;
        int displayTotal = activeInfo != null ? activeInfo.getTotalProgress() : totalProgress;

        String text = detail != null ? line + " - " + detail : line;
        
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);
        
        // 添加进度显示
        if (displayCurrent >= 0 && displayTotal > 0) {
            builder.setProgress(displayTotal, displayCurrent, false);
            // 更新文本显示进度
            String progressText = mContext.getString(R.string.task_progress_format, 
                    displayCurrent, displayTotal, (displayCurrent * 100) / displayTotal);
            builder.setContentText(text + " - " + progressText);
        } else {
            builder.setProgress(0, 0, true); // 不确定进度
        }
        
        Notification notification = builder.build();
        
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 更新前台通知
     */
    private void updateForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        updateForegroundNotification(taskName, taskDescription, -1, -1);
    }
    
    /**
     * 更新前台通知（带进度）
     */
    private void updateForegroundNotification(@Nullable String taskName, @Nullable String taskDescription,
                                            int currentProgress, int totalProgress) {
        int activeTasks = mActiveTaskCount.get();
        if (activeTasks <= 0) {
            showIdleNotification();
            return;
        }

        int runningCount = Math.max(mTaskStatusManager.getActiveTaskCount(), activeTasks);
        BackgroundTaskInfo activeInfo = getNextActiveTaskInfo();

        String title = getRunningTasksTitle(runningCount);
        String line = activeInfo != null ? activeInfo.getTaskName()
            : (taskName != null ? taskName : mContext.getString(R.string.background_task_running));
        String detail = activeInfo != null ? activeInfo.getProgressDetail() : taskDescription;
        int displayCurrent = activeInfo != null ? activeInfo.getCurrentProgress() : currentProgress;
        int displayTotal = activeInfo != null ? activeInfo.getTotalProgress() : totalProgress;

        String text = detail != null ? line + " - " + detail : line;
        
        Intent intent = new Intent(mContext, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);
        
        // 添加进度显示
        if (displayCurrent >= 0 && displayTotal > 0) {
            builder.setProgress(displayTotal, displayCurrent, false);
            // 更新文本显示进度
            String progressText = mContext.getString(R.string.task_progress_format, 
                    displayCurrent, displayTotal, (displayCurrent * 100) / displayTotal);
            builder.setContentText(text + " - " + progressText);
        } else {
            builder.setProgress(0, 0, true); // 不确定进度
        }
        
        Notification notification = builder.build();
        
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    /**
     * 显示空闲状态通知（没有正在执行的任务）
     */
    private void showIdleNotification() {
        if (mNotificationManager != null) {
            String title = mContext.getString(R.string.background_task_running);
            String content = mContext.getString(R.string.no_background_tasks);
            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setOngoing(false)
                    .setOnlyAlertOnce(true);

            Notification notification = builder.build();
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 隐藏前台通知（如果需要完全取消当前通知）
     */
    private void hideForegroundNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }
    
    /**
     * 更新任务进度通知
     * @param taskName 任务名称
     * @param taskDescription 任务描述
     * @param currentProgress 当前进度
     * @param totalProgress 总进度
     */
    public void updateTaskProgress(@Nullable String taskName, @Nullable String taskDescription,
                                  int currentProgress, int totalProgress) {
        if (mActiveTaskCount.get() > 0) {
            updateForegroundNotification(taskName, taskDescription, currentProgress, totalProgress);
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
     * 获取任务状态管理器
     */
    public BackgroundTaskStatusManager getTaskStatusManager() {
        return mTaskStatusManager;
    }
    
    /**
     * 提交扫描下载文件任务
     * @return Future用于等待任务完成或取消任务
     */
    public Future<?> submitScanDownloadTask(@Nullable final DownloadedFileManagerScanListener progressListener) {
        long startTime = System.currentTimeMillis();
        mDownloadLogger.logBackgroundTaskStart("ScanDownload", "扫描下载文件");
        
        String taskName = mContext.getString(R.string.settings_download_scan_download_files);
        String taskDescription = mContext.getString(R.string.settings_download_scan_download_files_summary);
        
        // 添加到任务状态管理器
        String taskId = mTaskStatusManager.addTask(taskName, taskDescription, null,
            BackgroundTask.TaskType.SCAN, true);
        if (taskId == null) {
            Log.d(TAG, "Skip scan download task, unique task running");
            return createNoOpFuture();
        }
        
        return submitIoTask(() -> {
            startForegroundTask(taskName, taskDescription);
            
            try {
                DownloadedFileManager manager = DownloadedFileManager.getInstance();
                manager.scanDownloadDirectories(new DownloadedFileManagerScanListener() {
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
        return submitLongRunningTask(taskName, taskDescription, task, null, BackgroundTask.TaskType.OTHER, true);
    }

    public Future<?> submitLongRunningTask(String taskName, String taskDescription, Runnable task, @Nullable String existingTaskId) {
        return submitLongRunningTask(taskName, taskDescription, task, existingTaskId, BackgroundTask.TaskType.OTHER, true);
    }

    public Future<?> submitLongRunningTask(String taskName, String taskDescription, Runnable task,
                                           @Nullable String existingTaskId, @NonNull BackgroundTask.TaskType taskType,
                                           boolean uniqueTask) {
        // 添加到任务状态管理器（如果未提供现有任务ID）
        final String taskId = existingTaskId != null ? existingTaskId :
                mTaskStatusManager.addTask(taskName, taskDescription, null, taskType, uniqueTask);
        if (taskId == null) {
            Log.d(TAG, "Skip task, unique task running: " + taskName);
            return createNoOpFuture();
        }
        
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
                task,
                null,
                BackgroundTask.TaskType.SCAN,
                true
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
                task,
                null,
                BackgroundTask.TaskType.MERGE,
                true
        );
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