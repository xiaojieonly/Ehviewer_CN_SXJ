package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
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

public class CleanRedundancyPreference2 extends Preference {

    public CleanRedundancyPreference2(Context context) {
        super(context);
        init();
    }

    public CleanRedundancyPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CleanRedundancyPreference2(Context context, AttributeSet attrs, int defStyleAttr) {
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
        BackgroundTaskManager taskManager = BackgroundTaskManager.getInstance();
        BackgroundTaskStatusManager statusManager = taskManager.getTaskStatusManager();
        
        // 添加到任务状态管理器
        String taskId = statusManager.addTask(
            context.getString(R.string.settings_download_clean_redundancy),
            context.getString(R.string.settings_download_clean_redundancy_summary),
            null
        );
        
        // 提交任务
        taskManager.submitLongRunningTask(
            context.getString(R.string.settings_download_clean_redundancy),
            context.getString(R.string.settings_download_clean_redundancy_summary),
            () -> {
                EhApplication mApplication = (EhApplication) context.getApplicationContext();
                DownloadManager mManager = EhApplication.getDownloadManager(mApplication);
                
                UniFile dir = Settings.getDownloadLocation();
                if (null == dir) {
                    statusManager.updateTaskProgress(taskId, 0, 0);
                    return;
                }
                UniFile[] files = dir.listFiles();
                if (null == files) {
                    statusManager.updateTaskProgress(taskId, 0, 0);
                    return;
                }

                int total = files.length;
                int count = 0;
                for (int i = 0; i < total; i++) {
                    UniFile f = files[i];
                    if (clearFile(f, mManager)) {
                        ++count;
                    }
                    // 更新进度
                    statusManager.updateTaskProgress(taskId, i + 1, total);
                }
                
                // 显示结果
                final int resultCount = count;
                new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                    Toast.makeText(mApplication, 0 == resultCount ?
                            mApplication.getString(R.string.settings_download_clean_redundancy_no_redundancy):
                            mApplication.getString(R.string.settings_download_clean_redundancy_done, resultCount), Toast.LENGTH_SHORT).show();
                });
            },
            taskId
        );
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