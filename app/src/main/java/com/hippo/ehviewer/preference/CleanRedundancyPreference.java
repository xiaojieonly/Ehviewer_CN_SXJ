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
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.unifile.UniFile;
import com.hippo.yorozuya.NumberUtils;

public class CleanRedundancyPreference extends TaskPreference {

    public CleanRedundancyPreference(Context context) {
        super(context);
    }

    public CleanRedundancyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CleanRedundancyPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    @Override
    protected Task onCreateTask() {
        return new ClearTask(getContext());
    }

    private static class ClearTask extends Task {

        private final EhApplication mApplication;
        private final DownloadManager mManager;

        public ClearTask(@NonNull Context context) {
            super(context);
            mApplication = (EhApplication) context.getApplicationContext();
            mManager = EhApplication.getDownloadManager(mApplication);
        }

        // True for cleared
        private boolean clearFile(UniFile file) {
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

        @Override
        protected Object doInBackground(Void... params) {
            UniFile dir = Settings.getDownloadLocation();
            if (null == dir) {
                return 0;
            }
            UniFile[] files = dir.listFiles();
            if (null == files) {
                return 0;
            }

            int count = 0;
            for (UniFile f: files) {
                if (clearFile(f)) {
                    ++count;
                }
            }

            return count;
        }

        @Override
        protected void onPostExecute(Object o) {
            int count;
            if (o instanceof Integer) {
                count = (Integer) o;
            } else {
                count = 0;
            }

            Toast.makeText(mApplication, 0 == count ?
                    mApplication.getString(R.string.settings_download_clean_redundancy_no_redundancy):
                    mApplication.getString(R.string.settings_download_clean_redundancy_done, count), Toast.LENGTH_SHORT).show();
            super.onPostExecute(o);
        }
    }
}
