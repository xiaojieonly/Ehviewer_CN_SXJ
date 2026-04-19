package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.preference.Preference;

import com.hippo.ehviewer.BackgroundTaskManager;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.task.MergeDuplicateGalleryTask;

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
        if (taskManager.getTaskStatusManager().getActiveUniqueNonDownloadTask() != null) {
            Toast.makeText(context, R.string.background_task_unique_running, Toast.LENGTH_SHORT).show();
            return;
        }
        taskManager.submitBackgroundTask(new MergeDuplicateGalleryTask(context));
        Toast.makeText(context, R.string.settings_download_merge_duplicate_gallery, Toast.LENGTH_SHORT).show();
    }
}