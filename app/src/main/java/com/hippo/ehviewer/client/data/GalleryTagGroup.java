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

package com.hippo.ehviewer.client.data;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GalleryTagGroup implements Parcelable {

    public String groupName;
    private final ArrayList<String> mTagList;
    private final Map<String, String> mTagUrlMap; // 存储标签名到URL的映射

    public void addTag(String tag) {
        mTagList.add(tag);
    }

    /**
     * 添加标签及其对应的URL
     * @param tag 标签名称
     * @param url 标签链接
     */
    public void addTagWithUrl(String tag, String url) {
        mTagList.add(tag);
        if (url != null && !url.isEmpty()) {
            mTagUrlMap.put(tag, url);
        }
    }

    /**
     * 获取标签的URL
     * @param tag 标签名称
     * @return 标签URL，如果没有则返回null
     */
    public String getTagUrl(String tag) {
        return mTagUrlMap.get(tag);
    }

    public int size() {
        return mTagList.size();
    }

    public String getTagAt(int position) {
        return mTagList.get(position);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.groupName);
        dest.writeStringList(this.mTagList);
        // 写入标签URL映射
        dest.writeInt(mTagUrlMap.size());
        for (Map.Entry<String, String> entry : mTagUrlMap.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }

    public GalleryTagGroup() {
        mTagList = new ArrayList<>();
        mTagUrlMap = new HashMap<>();
    }

    protected GalleryTagGroup(Parcel in) {
        this.groupName = in.readString();
        this.mTagList = in.createStringArrayList();
        // 读取标签URL映射
        this.mTagUrlMap = new HashMap<>();
        int mapSize = in.readInt();
        for (int i = 0; i < mapSize; i++) {
            String key = in.readString();
            String value = in.readString();
            mTagUrlMap.put(key, value);
        }
    }

    public static final Creator<GalleryTagGroup> CREATOR = new Creator<GalleryTagGroup>() {
        @Override
        public GalleryTagGroup createFromParcel(Parcel source) {
            return new GalleryTagGroup(source);
        }

        @Override
        public GalleryTagGroup[] newArray(int size) {
            return new GalleryTagGroup[size];
        }
    };
}
