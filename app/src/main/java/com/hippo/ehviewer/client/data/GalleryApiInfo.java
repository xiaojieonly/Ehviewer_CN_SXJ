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
import androidx.annotation.Nullable;

public class GalleryApiInfo implements Parcelable {

    public long gid;
    public String token;
    public String archiverKey;
    public String title;
    public String titleJpn;
    public int category;
    public String thumb;
    public String uploader;
    public long posted;
    public int filecount;
    public long filesize;
    public boolean expunged;
    public float rating;
    public int torrentcount;
    @Nullable
    public String[] tags;

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.gid);
        dest.writeString(this.token);
        dest.writeString(this.archiverKey);
        dest.writeString(this.title);
        dest.writeString(this.titleJpn);
        dest.writeInt(this.category);
        dest.writeString(this.thumb);
        dest.writeString(this.uploader);
        dest.writeLong(this.posted);
        dest.writeInt(this.filecount);
        dest.writeLong(this.filesize);
        dest.writeByte(expunged ? (byte) 1 : (byte) 0);
        dest.writeFloat(this.rating);
        dest.writeInt(this.torrentcount);
        dest.writeStringArray(this.tags);
    }

    public GalleryApiInfo() {
    }

    protected GalleryApiInfo(Parcel in) {
        this.gid = in.readLong();
        this.token = in.readString();
        this.archiverKey = in.readString();
        this.title = in.readString();
        this.titleJpn = in.readString();
        this.category = in.readInt();
        this.thumb = in.readString();
        this.uploader = in.readString();
        this.posted = in.readLong();
        this.filecount = in.readInt();
        this.filesize = in.readLong();
        this.expunged = in.readByte() != 0;
        this.rating = in.readFloat();
        this.torrentcount = in.readInt();
        this.tags = in.createStringArray();
    }

    public static final Parcelable.Creator<GalleryApiInfo> CREATOR = new Parcelable.Creator<GalleryApiInfo>() {
        @Override
        public GalleryApiInfo createFromParcel(Parcel source) {
            return new GalleryApiInfo(source);
        }

        @Override
        public GalleryApiInfo[] newArray(int size) {
            return new GalleryApiInfo[size];
        }
    };
}
