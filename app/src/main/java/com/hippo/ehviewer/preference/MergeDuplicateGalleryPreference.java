package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.Preference;

import com.hippo.ehviewer.R;

public class MergeDuplicateGalleryPreference extends Preference {

    public MergeDuplicateGalleryPreference(Context context) {
        super(context);
        init();
    }

    public MergeDuplicateGalleryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MergeDuplicateGalleryPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setKey("merge_duplicate_gallery");
        setTitle(R.string.settings_download_merge_duplicate_gallery);
        setSummary(R.string.settings_download_merge_duplicate_gallery_summary);
    }
}