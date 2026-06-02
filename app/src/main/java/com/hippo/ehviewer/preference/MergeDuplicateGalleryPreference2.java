package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;

import com.hippo.ehviewer.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class MergeDuplicateGalleryPreference2 extends Preference {

    private static final String TAG = "MergeDupGalleryPref2";

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
        try {
            Class<?> taskClass = Class.forName("com.hippo.ehviewer.task.MergeDuplicateGalleryTask");
            Constructor<?> constructor = taskClass.getConstructor(Context.class);
            Object task = constructor.newInstance(context);

            Class<?> managerClass = Class.forName("com.hippo.ehviewer.BackgroundTaskManager");
            Method getInstance = managerClass.getMethod("getInstance");
            Object taskManager = getInstance.invoke(null);
            if (taskManager == null) {
                Toast.makeText(context, R.string.settings_download_merge_duplicate_gallery_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }

            for (Method method : managerClass.getMethods()) {
                if ("submitBackgroundTask".equals(method.getName()) && method.getParameterTypes().length == 1) {
                    method.invoke(taskManager, task);
                    Toast.makeText(context, R.string.settings_download_merge_duplicate_gallery, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            Toast.makeText(context, R.string.settings_download_merge_duplicate_gallery_unavailable, Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Log.w(TAG, "Unable to start merge task", t);
            Toast.makeText(context, R.string.settings_download_merge_duplicate_gallery_unavailable, Toast.LENGTH_SHORT).show();
        }
    }
}