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

package com.hippo.ehviewer.client;

import android.util.Log;

import com.hippo.ehviewer.EhDB;
import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.dao.Filter;

import java.util.ArrayList;
import java.util.List;

public final class EhFilter {

    private static final String TAG = EhFilter.class.getSimpleName();

    public static final int MODE_TITLE = 0;
    public static final int MODE_UPLOADER = 1;
    public static final int MODE_TAG = 2;
    public static final int MODE_TAG_NAMESPACE = 3;

    private final List<Filter> mTitleFilterList = new ArrayList<>();
    private final List<Filter> mUploaderFilterList = new ArrayList<>();
    private final List<Filter> mTagFilterList = new ArrayList<>();
    private final List<Filter> mTagNamespaceFilterList = new ArrayList<>();

    private static EhFilter sInstance;

    public static EhFilter getInstance() {
        if (sInstance == null) {
            sInstance = new EhFilter();
        }
        return sInstance;
    }

    private EhFilter() {
        List<Filter> list = EhDB.getAllFilter();
        for (int i = 0, n = list.size(); i < n; i++) {
            Filter filter = list.get(i);
            switch (filter.mode) {
                case MODE_TITLE:
                    filter.text = filter.text.toLowerCase();
                    mTitleFilterList.add(filter);
                    break;
                case MODE_UPLOADER:
                    mUploaderFilterList.add(filter);
                    break;
                case MODE_TAG:
                    filter.text = filter.text.toLowerCase();
                    mTagFilterList.add(filter);
                    break;
                case MODE_TAG_NAMESPACE:
                    filter.text = filter.text.toLowerCase();
                    mTagNamespaceFilterList.add(filter);
                    break;
                default:
                    Log.d(TAG, "Unknown mode: " + filter.mode);
                    break;
            }
        }
    }

    public List<Filter> getTitleFilterList() {
        return mTitleFilterList;
    }

    public List<Filter> getUploaderFilterList() {
        return mUploaderFilterList;
    }

    public List<Filter> getTagFilterList() {
        return mTagFilterList;
    }

    public List<Filter> getTagNamespaceFilterList() {
        return mTagNamespaceFilterList;
    }

    public synchronized void addFilter(Filter filter) {
        // enable filter by default before it is added to database
        filter.enable = true;
        EhDB.addFilter(filter);

        switch (filter.mode) {
            case MODE_TITLE:
                filter.text = filter.text.toLowerCase();
                mTitleFilterList.add(filter);
                break;
            case MODE_UPLOADER:
                mUploaderFilterList.add(filter);
                break;
            case MODE_TAG:
                filter.text = filter.text.toLowerCase();
                mTagFilterList.add(filter);
                break;
            case MODE_TAG_NAMESPACE:
                filter.text = filter.text.toLowerCase();
                mTagNamespaceFilterList.add(filter);
                break;
            default:
                Log.d(TAG, "Unknown mode: " + filter.mode);
                break;
        }
    }

    public synchronized void triggerFilter(Filter filter) {
        EhDB.triggerFilter(filter);
    }

    public synchronized void deleteFilter(Filter filter) {
        EhDB.deleteFilter(filter);

        switch (filter.mode) {
            case MODE_TITLE:
                mTitleFilterList.remove(filter);
                break;
            case MODE_UPLOADER:
                mUploaderFilterList.remove(filter);
                break;
            case MODE_TAG:
                mTagFilterList.remove(filter);
                break;
            case MODE_TAG_NAMESPACE:
                mTagNamespaceFilterList.remove(filter);
                break;
            default:
                Log.d(TAG, "Unknown mode: " + filter.mode);
                break;
        }
    }

    public synchronized boolean needTags() {
        return 0 != mTagFilterList.size() || 0 != mTagNamespaceFilterList.size();
    }

    public synchronized boolean filterTitle(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Title
        String title = info.title;
        List<Filter> filters = mTitleFilterList;
        if (null != title && filters.size() > 0) {
            for (int i = 0, n = filters.size(); i < n; i++) {
                if (filters.get(i).enable && title.toLowerCase().contains(filters.get(i).text)) {
                    return false;
                }
            }
        }

        return true;
    }

    public synchronized boolean filterUploader(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Uploader
        String uploader = info.uploader;
        List<Filter> filters = mUploaderFilterList;
        if (null != uploader && filters.size() > 0) {
            for (int i = 0, n = filters.size(); i < n; i++) {
                if (filters.get(i).enable && uploader.equals(filters.get(i).text)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean matchTag(String tag, String filter) {
        if (null == tag || null == filter) {
            return false;
        }

        String tagNamespace;
        String tagName;
        String filterNamespace;
        String filterName;
        int index = tag.indexOf(':');
        if (index < 0) {
            tagNamespace = null;
            tagName = tag;
        } else {
            tagNamespace = tag.substring(0, index);
            tagName = tag.substring(index + 1);
        }
        index = filter.indexOf(':');
        if (index < 0) {
            filterNamespace = null;
            filterName = filter;
        } else {
            filterNamespace = filter.substring(0, index);
            filterName = filter.substring(index + 1);
        }

        if (null != tagNamespace && null != filterNamespace &&
                !tagNamespace.equals(filterNamespace)) {
            return false;
        }
        if (!tagName.equals(filterName)) {
            return false;
        }

        return true;
    }

    public synchronized boolean filterTag(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        // Tag
        String[] tags = info.simpleTags;
        List<Filter> filters = mTagFilterList;
        if (null != tags && filters.size() > 0) {
            for (String tag: tags) {
                for (int i = 0, n = filters.size(); i < n; i++) {
                    if (filters.get(i).enable && matchTag(tag, filters.get(i).text)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private boolean matchTagNamespace(String tag, String filter) {
        if (null == tag || null == filter) {
            return false;
        }

        String tagNamespace;
        int index = tag.indexOf(':');
        if (index >= 0) {
            tagNamespace = tag.substring(0, index);
            return tagNamespace.equals(filter);
        } else {
            return false;
        }
    }

    public synchronized boolean filterTagNamespace(GalleryInfo info) {
        if (null == info) {
            return false;
        }

        String[] tags = info.simpleTags;
        List<Filter> filters = mTagNamespaceFilterList;
        if (null != tags && filters.size() > 0) {
            for (String tag: tags) {
                for (int i = 0, n = filters.size(); i < n; i++) {
                    if (filters.get(i).enable && matchTagNamespace(tag, filters.get(i).text)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }
}
