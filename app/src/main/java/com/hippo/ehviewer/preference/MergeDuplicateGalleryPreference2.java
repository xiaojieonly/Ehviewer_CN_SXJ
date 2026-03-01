package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import androidx.preference.Preference;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;
import com.hippo.ehviewer.ui.fragment.MergeDuplicateGalleryTask;

public class MergeDuplicateGalleryPreference2 extends Preference {

    public MergeDuplicateGalleryPreference2(Context context) {
        super(context);
        init();
    }

    public MergeDuplicateGalleryPreference2(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MergeDuplicateGalleryPreference2(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setKey("merge_duplicate_gallery");
        setTitle(R.string.settings_download_merge_duplicate_gallery);
        setSummary(R.string.settings_download_merge_duplicate_gallery_summary);
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        BackgroundTaskManager taskManager = BackgroundTaskManager.getInstance();
        BackgroundTaskStatusManager statusManager = taskManager.getTaskStatusManager();
        
        // 添加到任务状态管理器
        String taskId = statusManager.addTask(
            context.getString(R.string.settings_download_merge_duplicate_gallery),
            context.getString(R.string.settings_download_merge_duplicate_gallery_summary),
            null,
            com.hippo.ehviewer.task.BackgroundTask.TaskType.MERGE,
            true
        );
        if (taskId == null) {
            android.widget.Toast.makeText(context, R.string.background_task_unique_running, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 提交任务
        taskManager.submitMergeDuplicateGalleryTask(() -> {
            MergeDuplicateGalleryRunner.executeMergeTask(context, taskId, statusManager);
        });
    }
}