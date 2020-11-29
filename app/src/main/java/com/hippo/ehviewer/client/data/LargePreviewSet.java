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

import com.hippo.ehviewer.client.EhCacheKeyFactory;
import com.hippo.widget.LoadImageView;
import com.hippo.yorozuya.collect.IntList;

import java.util.ArrayList;

public class LargePreviewSet extends PreviewSet {

    private final IntList mPositionList;
    private final ArrayList<String> mImageUrlList;
    private final ArrayList<String> mPageUrlList;

    public void addItem(int index, String imageUrl, String pageUrl) {
        mPositionList.add(index);
        mImageUrlList.add(imageUrl);
        mPageUrlList.add(pageUrl);
    }

    @Override
    public int size() {
        return mImageUrlList.size();
    }

    @Override
    public int getPosition(int index) {
        return mPositionList.get(index);
    }

    @Override
    public String getPageUrlAt(int index) {
        return mPageUrlList.get(index);
    }

    @Override
    public GalleryPreview getGalleryPreview(long gid, int index) {
        GalleryPreview galleryPreview = new GalleryPreview();
        galleryPreview.position = mPositionList.get(index);
        galleryPreview.imageKey = EhCacheKeyFactory.getLargePreviewKey(gid, galleryPreview.position);
        galleryPreview.imageUrl = mImageUrlList.get(index);
        galleryPreview.pageUrl = mPageUrlList.get(index);
        return galleryPreview;
    }

    @Override
    public void load(LoadImageView view, long gid, int index) {
        view.resetClip();
        view.load(EhCacheKeyFactory.getLargePreviewKey(gid, mPositionList.get(index)),
                mImageUrlList.get(index));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mPositionList, flags);
        dest.writeStringList(this.mImageUrlList);
        dest.writeStringList(this.mPageUrlList);
    }

    public LargePreviewSet() {
        mPositionList = new IntList();
        mImageUrlList = new ArrayList<>();
        mPageUrlList = new ArrayList<>();
    }

    protected LargePreviewSet(Parcel in) {
        this.mPositionList = in.readParcelable(IntList.class.getClassLoader());
        this.mImageUrlList = in.createStringArrayList();
        this.mPageUrlList = in.createStringArrayList();
    }

    public static final Parcelable.Creator<LargePreviewSet> CREATOR = new Parcelable.Creator<LargePreviewSet>() {
        @Override
        public LargePreviewSet createFromParcel(Parcel source) {
            return new LargePreviewSet(source);
        }

        @Override
        public LargePreviewSet[] newArray(int size) {
            return new LargePreviewSet[size];
        }
    };
}
