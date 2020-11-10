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

import com.hippo.widget.LoadImageView;
import com.hippo.yorozuya.collect.IntList;

import java.util.ArrayList;

public class NormalPreviewSet extends PreviewSet {

    private IntList mPositionList = new IntList();
    private ArrayList<String> mImageKeyList = new ArrayList<>();
    private ArrayList<String> mImageUrlList = new ArrayList<>();
    private IntList mOffsetXList = new IntList();
    private IntList mOffsetYList = new IntList();
    private IntList mClipWidthList = new IntList();
    private IntList mClipHeightList = new IntList();
    private ArrayList<String> mPageUrlList = new ArrayList<>();

    private String getImageKey(String imageUrl) {
        int index = imageUrl.indexOf('/');
        if (index >= 0) {
            return imageUrl.substring(index + 1);
        } else {
            return imageUrl;
        }
    }

    public void addItem(int position, String imageUrl, int xOffset, int yOffset, int width,
            int height, String pageUrl) {
        mPositionList.add(position);
        mImageKeyList.add(getImageKey(imageUrl));
        mImageUrlList.add(imageUrl);
        mOffsetXList.add(xOffset);
        mOffsetYList.add(yOffset);
        mClipWidthList.add(width);
        mClipHeightList.add(height);
        mPageUrlList.add(pageUrl);
    }

    @Override
    public int size() {
        return mPositionList.size();
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
        galleryPreview.imageKey = mImageKeyList.get(index);
        galleryPreview.imageUrl = mImageUrlList.get(index);
        galleryPreview.pageUrl = mPageUrlList.get(index);
        galleryPreview.offsetX = mOffsetXList.get(index);
        galleryPreview.offsetY = mOffsetYList.get(index);
        galleryPreview.clipWidth = mClipWidthList.get(index);
        galleryPreview.clipHeight = mClipHeightList.get(index);
        return galleryPreview;
    }

    @Override
    public void load(LoadImageView view, long gid, int index) {
        view.setClip(mOffsetXList.get(index), mOffsetYList.get(index),
                mClipWidthList.get(index), mClipHeightList.get(index));
        view.load(mImageKeyList.get(index), mImageUrlList.get(index));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.mPositionList, flags);
        dest.writeStringList(this.mImageKeyList);
        dest.writeStringList(this.mImageUrlList);
        dest.writeParcelable(this.mOffsetXList, flags);
        dest.writeParcelable(this.mOffsetYList, flags);
        dest.writeParcelable(this.mClipWidthList, flags);
        dest.writeParcelable(this.mClipHeightList, flags);
        dest.writeStringList(this.mPageUrlList);
    }

    public NormalPreviewSet() {
    }

    protected NormalPreviewSet(Parcel in) {
        this.mPositionList = in.readParcelable(IntList.class.getClassLoader());
        this.mImageKeyList = in.createStringArrayList();
        this.mImageUrlList = in.createStringArrayList();
        this.mOffsetXList = in.readParcelable(IntList.class.getClassLoader());
        this.mOffsetYList = in.readParcelable(IntList.class.getClassLoader());
        this.mClipWidthList = in.readParcelable(IntList.class.getClassLoader());
        this.mClipHeightList = in.readParcelable(IntList.class.getClassLoader());
        this.mPageUrlList = in.createStringArrayList();
    }

    public static final Creator<NormalPreviewSet> CREATOR = new Creator<NormalPreviewSet>() {
        @Override
        public NormalPreviewSet createFromParcel(Parcel source) {
            return new NormalPreviewSet(source);
        }

        @Override
        public NormalPreviewSet[] newArray(int size) {
            return new NormalPreviewSet[size];
        }
    };
}
