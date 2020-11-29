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

// TODO Add url field?
public class GalleryTagGroup implements Parcelable {

    public String groupName;
    private final ArrayList<String> mTagList;

    public void addTag(String tag) {
        mTagList.add(tag);
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
    }

    public GalleryTagGroup() {
        mTagList = new ArrayList<>();
    }

    protected GalleryTagGroup(Parcel in) {
        this.groupName = in.readString();
        this.mTagList = in.createStringArrayList();
    }

    public static final Parcelable.Creator<GalleryTagGroup> CREATOR = new Parcelable.Creator<GalleryTagGroup>() {
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
