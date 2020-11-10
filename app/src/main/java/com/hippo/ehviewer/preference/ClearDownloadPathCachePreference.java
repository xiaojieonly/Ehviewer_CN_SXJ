/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.preference;

import android.content.Context;
import android.util.AttributeSet;
import androidx.appcompat.app.AlertDialog;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.preference.MessagePreference;

public class ClearDownloadPathCachePreference extends MessagePreference {

    public ClearDownloadPathCachePreference(Context context) {
        super(context);
        init(context);
    }

    public ClearDownloadPathCachePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ClearDownloadPathCachePreference(Context context,
            AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public void init(Context context) {
        setDialogMessage(context.getString(R.string.settings_advanced_clear_download_path_cache_message));
    }

    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);
        builder.setTitle(null);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            EhDB.clearDownloadDirname();
        }
    }
}
