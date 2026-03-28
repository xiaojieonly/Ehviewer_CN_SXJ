package com.hippo.ehviewer.preference;

import android.content.Context;
import com.hippo.ehviewer.ui.fragment.MergeDuplicateGalleryTask;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;

public class MergeDuplicateGalleryRunner {
    
    public static void executeMergeTask(Context context, String taskId, BackgroundTaskStatusManager statusManager) {
        MergeDuplicateGalleryTask task = new MergeDuplicateGalleryTask(context);
        task.setTaskId(taskId);
        task.setStatusManager(statusManager);
        
        try {
            // 直接调用非UI线程安全方法
            boolean success = task.runDirectly();
            if (!success) {
                statusManager.markTaskError(taskId, "合并重复画廊失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            statusManager.markTaskError(taskId, e.getMessage());
        }
    }
}