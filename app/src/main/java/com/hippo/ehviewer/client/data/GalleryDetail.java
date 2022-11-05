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


import java.util.Arrays;

/**
 * 画廊参数存储对象
 */
public class GalleryDetail extends GalleryInfo {

    public long apiUid = -1L;
    public String apiKey;
    public int torrentCount;
    public String torrentUrl;
    public String archiveUrl;
    public String parent;
    public String visible;
    public String language;
    public String size;
    public int pages;
    public int favoriteCount;
    public boolean isFavorited;
    public int ratingCount;
    public GalleryTagGroup[] tags;
    public GalleryCommentList comments;
    public int previewPages;
    public PreviewSet previewSet;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(this.torrentCount);
        dest.writeString(this.torrentUrl);
        dest.writeString(this.archiveUrl);
        dest.writeString(this.parent);
        dest.writeString(this.visible);
        dest.writeString(this.language);
        dest.writeString(this.size);
        dest.writeInt(this.pages);
        dest.writeInt(this.favoriteCount);
        dest.writeByte(isFavorited ? (byte) 1 : (byte) 0);
        dest.writeInt(this.ratingCount);
        dest.writeParcelableArray(this.tags, flags);
        dest.writeParcelable(this.comments, flags);
        dest.writeInt(this.previewPages);
        dest.writeParcelable(previewSet, flags);
    }

    public GalleryDetail() {
    }

    protected GalleryDetail(Parcel in) {
        super(in);
        this.torrentCount = in.readInt();
        this.torrentUrl = in.readString();
        this.archiveUrl = in.readString();
        this.parent = in.readString();
        this.visible = in.readString();
        this.language = in.readString();
        this.size = in.readString();
        this.pages = in.readInt();
        this.favoriteCount = in.readInt();
        this.isFavorited = in.readByte() != 0;
        this.ratingCount = in.readInt();
        Parcelable[] array = in.readParcelableArray(GalleryTagGroup.class.getClassLoader());
        if (array != null) {
            this.tags = Arrays.copyOf(array, array.length, GalleryTagGroup[].class);
        } else {
            this.tags = null;
        }
        this.comments = in.readParcelable(getClass().getClassLoader());
        this.previewPages = in.readInt();
        this.previewSet = in.readParcelable(PreviewSet.class.getClassLoader());
    }

    public static final Creator<GalleryDetail> CREATOR = new Creator<GalleryDetail>() {
        @Override
        public GalleryDetail createFromParcel(Parcel source) {
            return new GalleryDetail(source);
        }

        @Override
        public GalleryDetail[] newArray(int size) {
            return new GalleryDetail[size];
        }
    };
}
