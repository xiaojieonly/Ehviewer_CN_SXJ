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

package com.hippo.lib.glview.image;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hippo.lib.image.Image;

/**
 * A wrapper for {@link Image}. It is useful for multi-usage.
 * It handles image recycle automatically.
 */
public class ImageWrapper {

    private static final String LOG_TAG = "ImageWrapper";

    private final Image mImage;
    private final Rect mCut;
    private int mReferences;

    /**
     * Create ImageWrapper
     *
     * @param image the image should not be obtained or recycled.
     */
    public ImageWrapper(@NonNull Image image) {
        mImage = image;
        mCut = new Rect(0, 0, image.getWidth(), image.getHeight());
    }

    /**
     * Cuts this image to a specified region.
     * If the region is out of the image size, clamp the region.
     */
    public void setCutRect(int left, int top, int right, int bottom) {
        mCut.left = Math.max(0, left);
        mCut.top = Math.max(0, top);
        mCut.right = Math.min(mImage.getWidth(), right);
        mCut.bottom = Math.min(mImage.getHeight(), bottom);

        if (mCut.isEmpty()) {
            // Empty mCut has unspecified behavior
            Log.e(LOG_TAG, "Cut rect is empty");
            mCut.set(0, 0, mImage.getWidth(), mImage.getHeight());
        }
    }

    /**
     * Cuts this image to a specified region.
     * The region is described in percent, {@code [0.0f, 1.0f]}.
     * If the region is out of the image size, clamp the region.
     */
    public void setCutPercent(float left, float top, float right, float bottom) {
        setCutRect((int) (getWidth() * left), (int) (getHeight() * top),
                (int) (getWidth() * right), (int) (getHeight() * bottom));
    }

    /**
     * Obtain the image
     *
     * @return false for the image is recycled and obtain failed
     */
    public synchronized boolean obtain() {
        if (mImage.isRecycled()) {
            return false;
        } else {
            ++mReferences;
            return true;
        }
    }

    /**
     * Release the image
     */
    public synchronized void release() {
        --mReferences;
        if (mReferences <= 0 && !mImage.isRecycled()) {
            mImage.recycle();
        }
    }

    public boolean isImageRecycled() {
        return mImage.isRecycled();
    }

    /**
     * @see Image#getAnimated()
     */
    public Boolean getAnimated() {
        return mImage.getAnimated();
    }

    /**
     * @see Image#getAnimated()
     */
    public int getWidth() {
        return mCut.width();
    }

    /**
     * @see Image#getHeight()
     */
    public int getHeight() {
        return mCut.height();
    }

//    /**
//     * @see Image#render(int, int, Bitmap, int, int, int, int)
//     */
//    public void render(int srcX, int srcY, Bitmap dst, int dstX, int dstY,
//                       int width, int height, boolean fillBlank, int defaultColor) {
//        mImage.render(srcX + mCut.left, srcY + mCut.top, dst, dstX, dstY,
//                width, height);
//    }

    /**
     * @see Image#texImage(boolean, int, int, int, int)
     */
    public void texImage(boolean init, int offsetX, int offsetY, int width, int height) {
        mImage.texImage(init, offsetX + mCut.left, offsetY + mCut.top, width, height);
    }

    /**
     * @see Image#start()
     */
    public void start() {
        mImage.start();
    }

    /**
     * @see Image#getDelay()
     */
    public int getDelay() {
        return mImage.getDelay();
    }

    /**
     * @see Image#isOpaque()
     */
    public boolean isOpaque() {
        return mImage.isOpaque();
    }

    /**
     * @see Image#isRecycled()
     */
    public boolean isRecycled() {
        return mImage.isRecycled();
    }
}
