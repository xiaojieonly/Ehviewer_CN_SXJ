package com.hippo.ehviewer.preference;

import android.content.Context;
import com.hippo.ehviewer.ui.fragment.MergeDuplicateGalleryTask;
import com.hippo.ehviewer.ui.task.BackgroundTaskStatusManager;

public class MergeDuplicateGalleryRunner {
    
    public static void executeMergeTask(Context context, String taskId, BackgroundTaskStatusManager statusManager) {
        MergeDuplicateGalleryTask task = new MergeDuplicateGalleryTask(null);
        task.setTaskId(taskId);
        task.setStatusManager(statusManager);
        
        // 使用反射调用protected方法
        try {
            java.lang.reflect.Method method = MergeDuplicateGalleryTask.class.getDeclaredMethod("doInBackground", Void[].class);
            method.setAccessible(true);
            method.invoke(task, (Object) null);
        } catch (Exception e) {
            e.printStackTrace();
            statusManager.markTaskError(taskId, e.getMessage());
        }
    }
}