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
import com.hippo.ehviewer.dao.DownloadInfo;
import com.hippo.ehviewer.download.DownloadLogger;
import com.hippo.ehviewer.task.BackgroundTask;
import com.hippo.ehviewer.task.BackgroundTaskRunner;
import com.hippo.ehviewer.task.MergeDuplicateGalleryTask;
import com.hippo.ehviewer.ui.task.BackgroundTaskInfo;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 鍚庡彴浠诲姟绠＄悊鍣?
 * 鎻愪緵澶氱嚎绋嬪鐞嗗拰鏁版嵁搴撳崟绾跨▼鎿嶄綔鐨勮兘鍔?
 */
public class BackgroundTaskManager {

    private static final String TAG = BackgroundTaskManager.class.getSimpleName();
    private static final String CHANNEL_ID = "eh_background_tasks";
    private static final int NOTIFICATION_ID = 1001;
    private static final int IO_TASK_QUEUE_CAPACITY = 500;

    private static BackgroundTaskManager sInstance;

    private final Context mContext;
    private final Handler mMainHandler;

    private final Map<String, BackgroundTaskFactory> mTaskFactoryMap = new ConcurrentHashMap<>();

    // CPU瀵嗛泦鍨嬩换鍔＄嚎绋嬫睜锛堢敤浜庢枃浠舵壂鎻忕瓑锛?
    private final ExecutorService mCpuExecutor;

    // 鏁版嵁搴撴搷浣滃崟绾跨▼鎵ц鍣紙纭繚鏁版嵁搴撴搷浣滀覆琛屽寲锛?
    private final ExecutorService mDbExecutor;

    // IO瀵嗛泦鍨嬩换鍔＄嚎绋嬫睜锛堢敤浜庣綉缁滄垨鏂囦欢IO锛?
    private final ThreadPoolExecutor mIoExecutor;
    private volatile int mBackgroundConcurrentTasks;

    // 缃戠粶绾跨▼姹狅紙涓撻棬鐢ㄤ簬涓嶦h浜や簰锛?
    private final ExecutorService mNetworkExecutor;

    private NotificationManager mNotificationManager;
    private boolean mForegroundServiceRunning = false;
    private final AtomicInteger mActiveTaskCount = new AtomicInteger(0);
    private final AtomicInteger mNotificationTaskIndex = new AtomicInteger(0);

    // 涓嬭浇鏃ュ織璁板綍鍣?
    private final DownloadLogger mDownloadLogger;

    // 浠诲姟鐘舵€佺鐞嗗櫒
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
        List<BackgroundTaskInfo> activeTasks = new ArrayList<>(mTaskStatusManager.getActiveTasks());
        for (BackgroundTaskInfo info : activeTasks) {
            if (info.isCompleted() || info.isCancelled() || info.getFuture() != null) {
                continue;
            }
            if (info.getTaskType() == BackgroundTask.TaskType.DOWNLOAD) {
                String persistData = info.getTaskPersistData();
                long gid = -1;
                if (persistData != null) {
                    try {
                        gid = Long.parseLong(persistData);
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (gid >= 0) {
                    DownloadInfo downloadInfo = EhDB.getDownloadInfo(gid);
                    if (downloadInfo != null && downloadInfo.state == DownloadInfo.STATE_FINISH) {
                        mTaskStatusManager.markTaskCompleted(info.getTaskId());
                    } else {
                        mTaskStatusManager.markTaskCancelled(info.getTaskId());
                    }
                } else {
                    mTaskStatusManager.markTaskCancelled(info.getTaskId());
                }
                continue;
            }
            String className = info.getTaskClassName();
            String persistData = info.getTaskPersistData();
            if (persistData == null || className == null) {
                mTaskStatusManager.markTaskError(info.getTaskId(), mContext.getString(R.string.background_task_not_recoverable));
                continue;
            }
            BackgroundTaskFactory factory = mTaskFactoryMap.get(className);
            if (factory == null) {
                mTaskStatusManager.markTaskError(info.getTaskId(), mContext.getString(R.string.background_task_not_recoverable));
                continue;
            }
            BackgroundTask task = factory.create(mContext, info.getTaskId(), persistData);
            if (task != null) {
                mTaskStatusManager.removeTask(info.getTaskId());
                submitBackgroundTask(task);
            } else {
                mTaskStatusManager.markTaskError(info.getTaskId(), mContext.getString(R.string.background_task_not_recoverable));
            }
        }
    }

    private BackgroundTaskManager(Context context) {
        mContext = context;
        mMainHandler = new Handler(Looper.getMainLooper());

        // 鍒濆鍖栦笅杞芥棩蹇楄褰曞櫒
        mDownloadLogger = DownloadLogger.getInstance();

        // 鍒濆鍖栦换鍔＄姸鎬佺鐞嗗櫒
        BackgroundTaskStatusManager.initialize(context);
        mTaskStatusManager = BackgroundTaskStatusManager.getInstance();

        // 娉ㄥ唽鍙仮澶嶄换鍔″伐鍘?
        registerTaskFactory(CompressSelectedGalleriesTask.class.getName(), (ctx, taskId, persistData) ->
                CompressSelectedGalleriesTask.restore(ctx, taskId, persistData));
        registerTaskFactory(MergeDuplicateGalleryTask.class.getName(), (ctx, taskId, persistData) ->
            MergeDuplicateGalleryTask.restore(ctx, taskId, persistData));

        // CPU绾跨▼姹狅細鏍稿績绾跨▼鏁?= CPU鏍稿績鏁帮紝鏈€澶х嚎绋嬫暟 = CPU鏍稿績鏁?* 2
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

        // 鏁版嵁搴撴墽琛屽櫒锛氬崟绾跨▼纭繚鏁版嵁搴撴搷浣滀覆琛屽寲
        mDbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "BackgroundTaskManager-DB");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });

        // IO绾跨▼姹狅細鐢ㄤ簬鏂囦欢IO銆佺綉缁滆姹傜瓑
        // 浣跨敤鍙厤缃苟鍙戝拰鏈夌晫闃熷垪锛岄伩鍏嶅悗鍙颁换鍔℃棤闄愬爢绉鑷磋祫婧愯€楀敖
        mBackgroundConcurrentTasks = Settings.getBackgroundConcurrentTasks();
        mIoExecutor = new ThreadPoolExecutor(
            mBackgroundConcurrentTasks,
            mBackgroundConcurrentTasks,
                60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(IO_TASK_QUEUE_CAPACITY),
                r -> {
                    Thread thread = new Thread(r, "BackgroundTaskManager-IO");
                    thread.setPriority(Thread.NORM_PRIORITY - 1); // 绋嶅井闄嶄綆浼樺厛绾?
                    return thread;
                }
        );

        // 缃戠粶绾跨▼姹狅細涓撻棬鐢ㄤ簬涓嶦h浜や簰
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

        // 鍒濆鍖栭€氱煡閫氶亾
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
     * 鎻愪氦CPU瀵嗛泦鍨嬩换鍔★紙濡傚浘鍍忓鐞嗐€佽绠楃瓑锛?
     */
    public <T> Future<T> submitCpuTask(Callable<T> task) {
        return mCpuExecutor.submit(task);
    }

    /**
     * 鎻愪氦CPU瀵嗛泦鍨嬩换鍔★紙鏃犺繑鍥炲€硷級
     */
    public Future<?> submitCpuTask(Runnable task) {
        return mCpuExecutor.submit(task);
    }

    /**
     * 鎻愪氦鏁版嵁搴撴搷浣滀换鍔★紙纭繚鍗曠嚎绋嬫墽琛岋級
     */
    public <T> Future<T> submitDbTask(Callable<T> task) {
        return mDbExecutor.submit(task);
    }

    /**
     * 鎻愪氦鏁版嵁搴撴搷浣滀换鍔★紙鏃犺繑鍥炲€硷級
     */
    public Future<?> submitDbTask(Runnable task) {
        return mDbExecutor.submit(task);
    }

    /**
     * 鎻愪氦IO瀵嗛泦鍨嬩换鍔★紙濡傛枃浠惰鍐欍€佺綉缁滆姹傜瓑锛?
     */
    public <T> Future<T> submitIoTask(Callable<T> task) {
        return mIoExecutor.submit(task);
    }

    /**
     * 鎻愪氦IO瀵嗛泦鍨嬩换鍔★紙鏃犺繑鍥炲€硷級
     */
    public Future<?> submitIoTask(Runnable task) {
        return mIoExecutor.submit(task);
    }

    private boolean canAcceptIoTask() {
        if (mIoExecutor.getActiveCount() < mBackgroundConcurrentTasks) {
            return true;
        }
        return mIoExecutor.getQueue().remainingCapacity() > 0;
    }

    public synchronized void applyBackgroundConcurrentTaskSetting() {
        int newConcurrent = Settings.getBackgroundConcurrentTasks();
        int oldConcurrent = mBackgroundConcurrentTasks;
        if (newConcurrent == oldConcurrent) {
            return;
        }

        // ThreadPoolExecutor 瑕佹眰 corePoolSize <= maximumPoolSize锛岃皟鏁撮『搴忚鏍规嵁澧炲噺鏂瑰悜澶勭悊銆?
        if (newConcurrent > oldConcurrent) {
            mIoExecutor.setMaximumPoolSize(newConcurrent);
            mIoExecutor.setCorePoolSize(newConcurrent);
        } else {
            mIoExecutor.setCorePoolSize(newConcurrent);
            mIoExecutor.setMaximumPoolSize(newConcurrent);
        }

        mBackgroundConcurrentTasks = newConcurrent;
        Log.i(TAG, "Apply background concurrent tasks: " + oldConcurrent + " -> " + newConcurrent);
    }

    /**
     * 鎻愪氦宸叉湁鐨凢utureTask鍒癐O绾跨▼姹?
     */
    public Future<?> submitIoFutureTask(java.util.concurrent.FutureTask<?> task) {
        mIoExecutor.execute(task);
        return task;
    }

    /**
     * 鎻愪氦缃戠粶浠诲姟锛堜笓闂ㄧ敤浜庝笌Eh浜や簰锛?
     */
    public <T> Future<T> submitNetworkTask(Callable<T> task) {
        return mNetworkExecutor.submit(task);
    }

    /**
     * 鎻愪氦缃戠粶浠诲姟锛堟棤杩斿洖鍊硷級
     */
    public Future<?> submitNetworkTask(Runnable task) {
        return mNetworkExecutor.submit(task);
    }

    /**
     * 鍚庡彴浠诲姟鎻愪氦缁撴灉
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
     * 鎻愪氦BackgroundTask骞舵帴鍏ヤ换鍔＄鐞嗕笌閫氱煡
     */
    @NonNull
    public TaskHandle submitBackgroundTask(@NonNull BackgroundTask task) {
        final String taskId = task.getTaskId();
        final String taskName = task.getTaskName();
        final String taskDescription = task.getTaskDescription();

        if (!canAcceptIoTask()) {
            String rejectedTaskId = mTaskStatusManager.addTask(taskId, taskName, taskDescription, null,
                    task.getTaskType(), task.isUniqueTask(), task.getTaskClassName(), task.getTaskPersistData());
            if (rejectedTaskId != null) {
                mTaskStatusManager.markTaskError(rejectedTaskId, mContext.getString(R.string.background_task_queue_full));
            }
            return new TaskHandle(taskId, createNoOpFuture());
        }

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
            public void onProgressChanged(int current, int total, @Nullable String detail) {
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
            mTaskStatusManager.markTaskRunning(taskId);
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
        mTaskStatusManager.markTaskQueued(registeredTaskId, null);
        try {
            submitIoFutureTask(futureTask);
        } catch (RejectedExecutionException e) {
            mTaskStatusManager.markTaskError(registeredTaskId, mContext.getString(R.string.background_task_queue_full));
            return new TaskHandle(taskId, createNoOpFuture());
        }

        return new TaskHandle(taskId, futureTask);
    }

    private Future<?> createNoOpFuture() {
        java.util.concurrent.FutureTask<?> futureTask = new java.util.concurrent.FutureTask<>(() -> null);
        futureTask.run();
        return futureTask;
    }

    /**
     * 寮€濮嬩竴涓墠鍙颁换鍔★紙鏄剧ず閫氱煡锛?
     */
    public void startForegroundTask(String taskName, @Nullable String taskDescription) {
        int activeCount = mActiveTaskCount.incrementAndGet();
        Log.d(TAG, "Start foreground task: " + taskName + ", active tasks: " + activeCount);

        if (activeCount == 1) {
            // 绗竴涓墠鍙颁换鍔★紝鏄剧ず閫氱煡
            showForegroundNotification(taskName, taskDescription);
        } else {
            // 鏇存柊鐜版湁閫氱煡
            updateForegroundNotification(taskName, taskDescription);
        }
    }

    /**
     * 缁撴潫涓€涓墠鍙颁换鍔?
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
     * 鏄剧ず鍓嶅彴閫氱煡
     */
    private void showForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        showForegroundNotification(taskName, taskDescription, -1, -1);
    }

    /**
     * 鏄剧ず鍓嶅彴閫氱煡锛堝甫杩涘害锛?
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

        Intent intent = new Intent(mContext, com.hippo.ehviewer.ui.task.BackgroundTaskActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
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

        // 娣诲姞杩涘害鏄剧ず
        if (displayCurrent >= 0 && displayTotal > 0) {
            builder.setProgress(displayTotal, displayCurrent, false);
            // 鏇存柊鏂囨湰鏄剧ず杩涘害
            String progressText = mContext.getString(R.string.task_progress_format,
                    displayCurrent, displayTotal, (displayCurrent * 100) / displayTotal);
            builder.setContentText(text + " - " + progressText);
        } else {
            builder.setProgress(0, 0, true); // 涓嶇‘瀹氳繘搴?
        }

        Notification notification = builder.build();

        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 鏇存柊鍓嶅彴閫氱煡
     */
    private void updateForegroundNotification(@Nullable String taskName, @Nullable String taskDescription) {
        updateForegroundNotification(taskName, taskDescription, -1, -1);
    }

    /**
     * 鏇存柊鍓嶅彴閫氱煡锛堝甫杩涘害锛?
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

        Intent intent = new Intent(mContext, com.hippo.ehviewer.ui.task.BackgroundTaskActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent);

        // 娣诲姞杩涘害鏄剧ず
        if (displayCurrent >= 0 && displayTotal > 0) {
            builder.setProgress(displayTotal, displayCurrent, false);
            // 鏇存柊鏂囨湰鏄剧ず杩涘害
            String progressText = mContext.getString(R.string.task_progress_format,
                    displayCurrent, displayTotal, (displayCurrent * 100) / displayTotal);
            builder.setContentText(text + " - " + progressText);
        } else {
            builder.setProgress(0, 0, true); // 涓嶇‘瀹氳繘搴?
        }

        Notification notification = builder.build();

        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * 鏄剧ず绌洪棽鐘舵€侀€氱煡锛堟病鏈夋鍦ㄦ墽琛岀殑浠诲姟锛?
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
     * 闅愯棌鍓嶅彴閫氱煡锛堝鏋滈渶瑕佸畬鍏ㄥ彇娑堝綋鍓嶉€氱煡锛?
     */
    private void hideForegroundNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.cancel(NOTIFICATION_ID);
        }
    }

    /**
     * 鏇存柊浠诲姟杩涘害閫氱煡
     * @param taskName 浠诲姟鍚嶇О
     * @param taskDescription 浠诲姟鎻忚堪
     * @param currentProgress 褰撳墠杩涘害
     * @param totalProgress 鎬昏繘搴?
     */
    public void updateTaskProgress(@Nullable String taskName, @Nullable String taskDescription,
                                  int currentProgress, int totalProgress) {
        if (mActiveTaskCount.get() > 0) {
            updateForegroundNotification(taskName, taskDescription, currentProgress, totalProgress);
        }
    }

    /**
     * 鍦║I绾跨▼鎵ц浠诲姟
     */
    public void runOnUiThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            mMainHandler.post(runnable);
        }
    }

    /**
     * 鑾峰彇浠诲姟鐘舵€佺鐞嗗櫒
     */
    public BackgroundTaskStatusManager getTaskStatusManager() {
        return mTaskStatusManager;
    }

    /**
     * 鏆傚仠鎸囧畾浠诲姟
     */
    public boolean pauseTask(@NonNull String taskId) {
        return mTaskStatusManager.pauseTask(taskId);
    }

    /**
     * 鎭㈠鎸囧畾浠诲姟
     */
    public boolean resumeTask(@NonNull String taskId) {
        return mTaskStatusManager.resumeTask(taskId);
    }

    /**
     * 鍙栨秷鎸囧畾浠诲姟
     */
    public boolean cancelTask(@NonNull String taskId) {
        return mTaskStatusManager.cancelTask(taskId);
    }

    /**
     * 娓呯┖宸插畬鎴愮殑浠诲姟
     */
    public void clearCompletedTasks() {
        mTaskStatusManager.clearCompletedTasks();
    }

    /**
     * 寮哄姏鍋滄骞舵竻绌烘墍鏈夊悗鍙颁换鍔?
     */
    public void forceStopAllTasks() {
        mTaskStatusManager.clearAllTasks();
        mActiveTaskCount.set(0);
        hideForegroundNotification();
    }

    /**
     * 绉婚櫎鎸囧畾浠诲姟锛堜粠娲昏穬鍜屽凡瀹屾垚鍒楄〃涓垹闄わ級
     */
    public void removeTask(@NonNull String taskId) {
        mTaskStatusManager.removeTask(taskId);
    }

    /**
     * 鎻愪氦鎵弿涓嬭浇鏂囦欢浠诲姟
     * @return Future鐢ㄤ簬绛夊緟浠诲姟瀹屾垚鎴栧彇娑堜换鍔?
     */
    public Future<?> submitScanDownloadTask(@Nullable final DownloadedFileManagerScanListener progressListener) {
        long startTime = System.currentTimeMillis();
        mDownloadLogger.logBackgroundTaskStart("ScanDownload", "鎵弿涓嬭浇鏂囦欢");

        String taskName = mContext.getString(R.string.settings_download_scan_download_files);
        String taskDescription = mContext.getString(R.string.settings_download_scan_download_files_summary);

        // 娣诲姞鍒颁换鍔＄姸鎬佺鐞嗗櫒
        String taskId = mTaskStatusManager.addTask(taskName, taskDescription, null,
            BackgroundTask.TaskType.SCAN, true);
        if (taskId == null) {
            Log.d(TAG, "Skip scan download task, unique task running");
            return createNoOpFuture();
        }

        java.util.concurrent.FutureTask<?> futureTask = new java.util.concurrent.FutureTask<>(() -> {
            mTaskStatusManager.markTaskRunning(taskId);
            startForegroundTask(taskName, taskDescription);

            try {
                DownloadedFileManager manager = DownloadedFileManager.getInstance();
                manager.scanDownloadDirectories(new DownloadedFileManagerScanListener() {
                    @Override
                    public void onProgress(final int current, final int total) {
                        mDownloadLogger.logBackgroundTaskProgress("ScanDownload", "鎵弿涓嬭浇鏂囦欢", current, total);

                        // 鏇存柊浠诲姟杩涘害
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
                        mDownloadLogger.logBackgroundTaskComplete("ScanDownload", "鎵弿涓嬭浇鏂囦欢", totalTime, true);

                        // 鏍囪浠诲姟瀹屾垚
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
                        mDownloadLogger.logBackgroundTaskComplete("ScanDownload", "鎵弿涓嬭浇鏂囦欢", totalTime, false);
                        mDownloadLogger.logDownloadError("ScanDownload", "鎵弿涓嬭浇鏂囦欢", e.getMessage(), e);

                        // 鏍囪浠诲姟鍑洪敊
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

                // 鏍囪浠诲姟鍑洪敊
                mTaskStatusManager.markTaskError(taskId, e.getMessage());

                endForegroundTask("scan_download");
                runOnUiThread(() -> {
                    if (progressListener != null) {
                        progressListener.onError(e);
                    }
                });
                throw e; // 閲嶆柊鎶涘嚭寮傚父锛屼互渚縁uture鑳藉鎹曡幏
            }
            return null;
        });

        mTaskStatusManager.markTaskQueued(taskId, null);
        try {
            submitIoFutureTask(futureTask);
        } catch (RejectedExecutionException e) {
            mTaskStatusManager.markTaskError(taskId, mContext.getString(R.string.background_task_queue_full));
            return createNoOpFuture();
        }
        return futureTask;
    }

    /**
     * 鎻愪氦閫氱敤鍚庡彴浠诲姟
     * @param taskName 浠诲姟鍚嶇О锛堟樉绀哄湪閫氱煡涓級
     * @param taskDescription 浠诲姟鎻忚堪锛堟樉绀哄湪閫氱煡涓級
     * @param task 瑕佹墽琛岀殑浠诲姟
     * @return Future鐢ㄤ簬绛夊緟浠诲姟瀹屾垚鎴栧彇娑堜换鍔?
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
        String candidateTaskId = existingTaskId != null ? existingTaskId : java.util.UUID.randomUUID().toString();
        java.util.concurrent.FutureTask<?> futureTask = new java.util.concurrent.FutureTask<>(() -> {
            mTaskStatusManager.markTaskRunning(candidateTaskId);
            startForegroundTask(taskName, taskDescription);
            try {
                task.run();
                // 鏍囪浠诲姟瀹屾垚
                mTaskStatusManager.markTaskCompleted(candidateTaskId);
            } catch (Exception e) {
                // 鏍囪浠诲姟鍑洪敊
                mTaskStatusManager.markTaskError(candidateTaskId, e.getMessage());
                throw e;
            } finally {
                endForegroundTask(taskName);
            }
            return null;
        });

        final String taskId = existingTaskId != null ? existingTaskId :
                mTaskStatusManager.addTask(candidateTaskId, taskName, taskDescription, futureTask,
                        taskType, uniqueTask, BackgroundTask.class.getName(), null);
        if (taskId == null) {
            Log.d(TAG, "Skip task, unique task running: " + taskName);
            return createNoOpFuture();
        }

        mTaskStatusManager.markTaskQueued(taskId, null);
        try {
            submitIoFutureTask(futureTask);
        } catch (RejectedExecutionException e) {
            mTaskStatusManager.markTaskError(taskId, mContext.getString(R.string.background_task_queue_full));
            return createNoOpFuture();
        }
        return futureTask;
    }

    /**
     * 鎻愪氦鎭㈠涓嬭浇椤逛换鍔?
     * @return Future鐢ㄤ簬绛夊緟浠诲姟瀹屾垚鎴栧彇娑堜换鍔?
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
     * 鍏抽棴鎵€鏈夌嚎绋嬫睜
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
