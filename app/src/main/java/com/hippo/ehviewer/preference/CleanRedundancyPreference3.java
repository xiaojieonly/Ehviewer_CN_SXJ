package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;
import com.hippo.unifile.UniFile;
import com.hippo.lib.yorozuya.NumberUtils;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CleanRedundancyPreference3 extends Preference {

    public CleanRedundancyPreference3(Context context) {
        super(context);
        init();
    }

    public CleanRedundancyPreference3(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CleanRedundancyPreference3(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setKey("clean_redundancy");
        setTitle(R.string.settings_download_clean_redundancy);
        setSummary(R.string.settings_download_clean_redundancy_summary);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        com.hippo.ehviewer.task.CleanRedundancyTask task =
                new com.hippo.ehviewer.task.CleanRedundancyTask(context);
        com.hippo.ehviewer.BackgroundTaskManager.getInstance().submitBackgroundTask(task);
        android.widget.Toast.makeText(context,
                R.string.settings_download_clean_redundancy_started,
                android.widget.Toast.LENGTH_SHORT).show();
    }
    
    private void runTaskAsync(com.hippo.ehviewer.task.CleanRedundancyTask task) {
        // 在线程中执行任务
        try {
            task.executeSync();
        } catch (Exception e) {
            Log.e("CleanRedundancyPreference3", "Task execution failed", e);
        }
    }

    private static class CleanTask implements Runnable {
        private final Context mContext;
        private final String mTaskId;
        private final BackgroundTaskStatusManager mStatusManager;
        private final AtomicInteger mCount = new AtomicInteger(0);
        private final AtomicInteger mProcessedCount = new AtomicInteger(0);

        public CleanTask(@NonNull Context context, String taskId, BackgroundTaskStatusManager statusManager) {
            mContext = context;
            mTaskId = taskId;
            mStatusManager = statusManager;
        }

        @Override
        public void run() {
            EhApplication mApplication = (EhApplication) mContext.getApplicationContext();
            DownloadManager mManager = EhApplication.getDownloadManager(mApplication);
            
            UniFile dir = Settings.getDownloadLocation();
            if (null == dir) {
                mStatusManager.updateTaskProgress(mTaskId, 0, 0);
                mStatusManager.markTaskCompleted(mTaskId);
                return;
            }
            UniFile[] files = dir.listFiles();
            if (null == files) {
                mStatusManager.updateTaskProgress(mTaskId, 0, 0);
                mStatusManager.markTaskCompleted(mTaskId);
                return;
            }

            int total = files.length;
            mStatusManager.updateTaskProgress(mTaskId, 0, total);
            
            // 创建专用的线程池用于清理任务
            int threadCount = 10; // 使用10个线程进行清理
            ExecutorService cleanExecutor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(total);

            // 提交所有文件处理任务到线程池
            for (UniFile f : files) {
                cleanExecutor.submit(() -> {
                    try {
                        if (clearFile(f, mManager)) {
                            mCount.incrementAndGet();
                        }
                    } finally {
                        int processed = mProcessedCount.incrementAndGet();
                        mStatusManager.updateTaskProgress(mTaskId, processed, total);
                        latch.countDown();
                    }
                });
            }

            try {
                // 等待所有任务完成
                latch.await(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                cleanExecutor.shutdown();
            }

            // 标记任务完成
            mStatusManager.markTaskCompleted(mTaskId);
            
            // 显示结果
            final int resultCount = mCount.get();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                Toast.makeText(mApplication, 0 == resultCount ?
                        mApplication.getString(R.string.settings_download_clean_redundancy_no_redundancy):
                        mApplication.getString(R.string.settings_download_clean_redundancy_done, resultCount), Toast.LENGTH_SHORT).show();
            });
        }
        
        // True for cleared
        private boolean clearFile(UniFile file, DownloadManager mManager) {
            String name = file.getName();
            if (name == null) {
                return false;
            }
            int index = name.indexOf('-');
            if (index >= 0) {
                name = name.substring(0, index);
            }
            long gid = NumberUtils.parseLongSafely(name, -1L);
            if (-1L == gid) {
                return false;
            }
            if (mManager.containDownloadInfo(gid)) {
                return false;
            }
            file.delete();
            return true;
        }
    }
}