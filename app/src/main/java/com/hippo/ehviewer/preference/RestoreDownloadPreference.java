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

import android.app.Activity;
import android.content.Context;
import android.os.Parcel;
import android.preference.Preference;
import android.util.AttributeSet;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.hippo.ehviewer.EhApplication;
import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.client.EhEngine;
import com.hippo.ehviewer.client.EhUrl;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.download.DownloadManager;
import com.hippo.ehviewer.spider.SpiderInfo;
import com.hippo.ehviewer.spider.SpiderQueen;
import com.hippo.unifile.UniFile;
import com.hippo.util.ExceptionUtils;
import com.hippo.yorozuya.IOUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import okhttp3.OkHttpClient;

public class RestoreDownloadPreference extends TaskPreference {

    public RestoreDownloadPreference(Context context) {
        super(context);
    }

    public RestoreDownloadPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RestoreDownloadPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @NonNull
    @Override
    protected Task onCreateTask() {
        return new RestoreTask(getContext());
    }

    private static class RestoreTask extends Task {

        private final EhApplication mApplication;
        private final DownloadManager mManager;
        private final OkHttpClient mHttpClient;

        public RestoreTask(@NonNull Context context) {
            super(context);
            mApplication = (EhApplication) context.getApplicationContext();
            mManager = EhApplication.getDownloadManager(mApplication);
            mHttpClient = EhApplication.getOkHttpClient(mApplication);
        }

        private RestoreItem getRestoreItem(UniFile file) {
            if (null == file || !file.isDirectory()) {
                return null;
            }
            UniFile siFile = file.findFile(SpiderQueen.SPIDER_INFO_FILENAME);
            if (null == siFile) {
                return null;
            }

            InputStream is = null;
            try {
                is = siFile.openInputStream();
                SpiderInfo spiderInfo = SpiderInfo.read(is);
                if (spiderInfo == null) {
                    return null;
                }
                long gid = spiderInfo.gid;
                if (mManager.containDownloadInfo(gid)) {
                    return null;
                }
                String token = spiderInfo.token;
                RestoreItem restoreItem = new RestoreItem();
                restoreItem.gid = gid;
                restoreItem.token = token;
                restoreItem.dirname = file.getName();
                return restoreItem;
            } catch (IOException e) {
                return null;
            } finally {
                IOUtils.closeQuietly(is);
            }
        }

        @Override
        protected Object doInBackground(Void... params) {
            UniFile dir = Settings.getDownloadLocation();
            if (null == dir) {
                return null;
            }

            List<RestoreItem> restoreItemList = new ArrayList<>();

            UniFile[] files = dir.listFiles();
            if (files == null) {
                return null;
            }

            for (UniFile file: files) {
                RestoreItem restoreItem = getRestoreItem(file);
                if (null != restoreItem) {
                    restoreItemList.add(restoreItem);
                }
            }

            if (0 == restoreItemList.size()) {
                return Collections.EMPTY_LIST;
            }

            try {
                return EhEngine.fillGalleryListByApi(null, mHttpClient, new ArrayList<GalleryInfo>(restoreItemList), EhUrl.getReferer());
            } catch (Throwable e) {
                ExceptionUtils.throwIfFatal(e);
                e.printStackTrace();
                return null;
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        protected void onPostExecute(Object o) {
            if (!(o instanceof List)) {
                Toast.makeText(mApplication, R.string.settings_download_restore_failed, Toast.LENGTH_SHORT).show();
            } else {
                List<RestoreItem> list = (List<RestoreItem>) o;
                if (list.isEmpty()) {
                    Toast.makeText(mApplication, R.string.settings_download_restore_not_found, Toast.LENGTH_SHORT).show();
                } else {
                    int count = 0;
                    for (int i = 0, n = list.size(); i < n; i++) {
                        RestoreItem item = list.get(i);
                        // Avoid failed gallery info
                        if (null != item.title) {
                            // Put to download
                            mManager.addDownload(item, null);
                            // Put download dir to DB
                            EhDB.putDownloadDirname(item.gid, item.dirname);
                            count++;
                        }
                    }
                    Toast.makeText(mApplication,
                            mApplication.getString(R.string.settings_download_restore_successfully, count),
                            Toast.LENGTH_SHORT).show();

                    Preference preference = getPreference();
                    if (null != preference) {
                        Context context = preference.getContext();
                        if (context instanceof Activity) {
                            ((Activity) context).setResult(Activity.RESULT_OK);
                        }
                    }
                }
            }
            super.onPostExecute(o);
        }
    }

    private static class RestoreItem extends GalleryInfo {

        public String dirname;

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeString(this.dirname);
        }

        public RestoreItem() {
        }

        protected RestoreItem(Parcel in) {
            super(in);
            this.dirname = in.readString();
        }

        public static final Creator<RestoreItem> CREATOR = new Creator<RestoreItem>() {
            @Override
            public RestoreItem createFromParcel(Parcel source) {
                return new RestoreItem(source);
            }

            @Override
            public RestoreItem[] newArray(int size) {
                return new RestoreItem[size];
            }
        };
    }
}
