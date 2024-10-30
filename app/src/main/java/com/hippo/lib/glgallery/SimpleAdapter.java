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
package com.hippo.lib.glgallery;

import androidx.annotation.NonNull;

import com.hippo.lib.glview.image.ImageTexture;
import com.hippo.lib.glview.image.ImageWrapper;
import com.hippo.lib.glview.view.GLRootView;

public class SimpleAdapter extends GalleryView.Adapter implements GalleryProvider.Listener {

    private final GalleryProvider mProvider;
    private final ImageTexture.Uploader mUploader;
    private boolean mShowIndex = true;

    public SimpleAdapter(@NonNull GLRootView glRootView, @NonNull GalleryProvider provider) {
        mProvider = provider;
        mUploader = new ImageTexture.Uploader(glRootView);
    }

    public void clearUploader() {
        mUploader.clear();
    }

    public void setShowIndex(boolean show) {
        mShowIndex = show;
    }

    @Override
    public void onBind(GalleryPageView view, int index) {
        mProvider.request(index);
        view.showInfo();
        view.setImage(null);
        if (mShowIndex) {
            view.setPage(index + 1);
        } else {
            view.hidePage();
        }
        view.setProgress(GalleryPageView.PROGRESS_INDETERMINATE);
        view.setError(null, null);
    }

    @Override
    public void onUnbind(GalleryPageView view, int index) {
        mProvider.cancelRequest(index);
        view.setImage(null);
        view.setError(null, null);
    }

    @Override
    public String getError() {
        return mProvider.getError();
    }

    @Override
    public int size() {
        return mProvider.size();
    }

    @Override
    public void onDataChanged() {
        mGalleryView.onDataChanged();
    }

    @Override
    public void onPageWait(int index) {
        GalleryPageView page = findPageByIndex(index);
        if (page != null) {
            page.showInfo();
            page.setImage(null);
            if (mShowIndex) {
                page.setPage(index + 1);
            } else {
                page.hidePage();
            }
            page.setProgress(GalleryPageView.PROGRESS_INDETERMINATE);
            page.setError(null, null);
        }
    }

    @Override
    public void onPagePercent(int index, float percent) {
        GalleryPageView page = findPageByIndex(index);
        if (page != null) {
            page.showInfo();
            page.setImage(null);
            if (mShowIndex) {
                page.setPage(index + 1);
            } else {
                page.hidePage();
            }
            page.setProgress(percent);
            page.setError(null, null);
        }
    }

    @Override
    public void onPageSucceed(int index, ImageWrapper image) {
        GalleryPageView page = findPageByIndex(index);
        if (page != null) {
            if (image.obtain()) {
                ImageTexture imageTexture = new ImageTexture(image);
                mUploader.addTexture(imageTexture);
                page.showImage();
                page.setImage(imageTexture);
                if (mShowIndex) {
                    page.setPage(index + 1);
                } else {
                    page.hidePage();
                }
                page.setProgress(GalleryPageView.PROGRESS_GONE);
                page.setError(null, null);
            } else {
                // The image is recycled, request again.
                // TODO request loop ?
                mProvider.request(index);
            }
        }
    }

    @Override
    public void onPageFailed(int index, String error) {
        GalleryPageView page = findPageByIndex(index);
        if (page != null) {
            page.showInfo();
            page.setImage(null);
            if (mShowIndex) {
                page.setPage(index + 1);
            } else {
                page.hidePage();
            }
            page.setProgress(GalleryPageView.PROGRESS_GONE);
            page.setError(error, mGalleryView);
        }
    }

    @Override
    public void onDataChanged(int index) {
        GalleryPageView page = findPageByIndex(index);
        if (page != null) {
            mProvider.request(index);
        }
    }

    private GalleryPageView findPageByIndex(int index) {
        return mGalleryView != null ? mGalleryView.findPageByIndex(index) : null;
    }
}
