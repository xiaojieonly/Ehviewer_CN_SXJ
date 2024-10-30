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

import android.content.Context;
import android.graphics.Rect;
import android.util.Log;
import android.view.animation.Interpolator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import com.hippo.lib.glview.anim.Animation;
import com.hippo.lib.glview.anim.FloatAnimation;
import com.hippo.lib.glview.view.GLView;
import com.hippo.lib.glview.widget.GLEdgeView;
import com.hippo.lib.glview.widget.GLProgressView;
import com.hippo.lib.glview.widget.GLTextureView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.MathUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

class PagerLayoutManager extends GalleryView.LayoutManager {

    private static final String TAG = PagerLayoutManager.class.getSimpleName();

    @IntDef({MODE_LEFT_TO_RIGHT, MODE_RIGHT_TO_LEFT})
    @Retention(RetentionPolicy.SOURCE)
    private @interface Mode {}

    public static final int MODE_LEFT_TO_RIGHT = 0;
    public static final int MODE_RIGHT_TO_LEFT = 1;

    private static final Interpolator SMOOTH_SCROLLER_INTERPOLATOR = new Interpolator() {
        @Override
        public float getInterpolation(float t) {
            t -= 1.0f;
            return t * t * t * t * t + 1.0f;
        }
    };

    private GalleryView.Adapter mAdapter;

    private GLProgressView mProgress;
    private String mErrorStr;
    private GLTextureView mErrorView;
    private GalleryPageView mPrevious;
    private GalleryPageView mCurrent;
    private GalleryPageView mNext;

    @Mode
    private int mMode = MODE_RIGHT_TO_LEFT;
    private int mScaleMode;
    private int mStartPosition;
    private float mScaleValue;

    private int mOffset;
    private int mDeltaX;
    private int mDeltaY;
    private boolean mCanScrollBetweenPages = false;
    private boolean mStopAnimationFinger;

    private int mInterval;

    private final int[] mScrollRemain = new int[2];
    private final float[] mScaleDefault = new float[4];

    private final SmoothScroller mSmoothScroller;
    private final PageFling mPageFling;
    private final SmoothScaler mSmoothScaler;
    private final OverScroller mOverScroller;

    // Current index
    private int mIndex;

    private final Rect mTempRect = new Rect();

    public PagerLayoutManager(Context context, @NonNull GalleryView galleryView,
            int scaleMode, int startPoint, float scaleValue, int interval) {
        super(galleryView);

        mScaleMode = scaleMode;
        mStartPosition = startPoint;
        mScaleValue = scaleValue;

        mInterval = interval;
        mSmoothScroller = new SmoothScroller();
        mPageFling = new PageFling(context);
        mSmoothScaler = new SmoothScaler();
        mOverScroller = new OverScroller();
    }

    public void setInterval(int interval) {
        if (mInterval == interval) {
            return;
        }

        if (mAdapter != null) {
            int index = getInternalCurrentIndex();
            GalleryView.Adapter adapter = onDetach();
            mInterval = interval;
            onAttach(adapter);
            setCurrentIndex(index);
            mGalleryView.requestFill();
        } else {
            mInterval = interval;
        }
    }

    private void resetParameters() {
        mOffset = 0;
        mDeltaX = 0;
        mDeltaY = 0;
        mCanScrollBetweenPages = false;
        mStopAnimationFinger = false;
    }

    private boolean cancelAllAnimations() {
        boolean running = mSmoothScroller.isRunning() ||
                mPageFling.isRunning() ||
                mSmoothScaler.isRunning() ||
                mOverScroller.isRunning();
        mSmoothScroller.cancel();
        mPageFling.cancel();
        mSmoothScaler.cancel();
        mOverScroller.cancel();
        return running;
    }

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;
        if (mAdapter != null) {
            // It is attached, refill
            // Cancel all animations
            cancelAllAnimations();
            // Remove all view
            removeProgress();
            removeErrorView();
            removeAllPages();
            // Reset parameters
            resetParameters();
            // Request fill
            mGalleryView.requestFill();
        }
    }

    public void setScaleMode(int scaleMode) {
        if (mScaleMode == scaleMode) {
            return;
        }
        mScaleMode = scaleMode;

        if (mCurrent != null) {
            mCurrent.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPrevious != null) {
            mPrevious.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNext != null) {
            mNext.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    public void setStartPosition(int startPosition) {
        if (mStartPosition == startPosition) {
            return;
        }
        mStartPosition = startPosition;

        if (mCurrent != null) {
            mCurrent.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mPrevious != null) {
            mPrevious.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
        if (mNext != null) {
            mNext.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        AssertUtils.assertNull("The PagerLayoutManager is attached", mAdapter);
        AssertUtils.assertNotNull("The adapter is null", adapter);
        mAdapter = adapter;
        // Reset parameters
        resetParameters();
    }

    private void removeProgress() {
        if (mProgress != null) {
            mGalleryView.removeComponent(mProgress);
            mGalleryView.releaseProgress(mProgress);
            mProgress = null;
        }
    }

    private void removeErrorView() {
        if (mErrorView != null) {
            mGalleryView.removeComponent(mErrorView);
            mGalleryView.releaseErrorView(mErrorView);
            mErrorView = null;
            mErrorStr = null;
        }
    }

    private void removePage(@NonNull GalleryPageView page) {
        mGalleryView.removeComponent(page);
        mAdapter.unbind(page);
        mGalleryView.releasePage(page);
    }

    private void removeAllPages() {
        // Remove gallery view
        if (mPrevious != null) {
            removePage(mPrevious);
            mPrevious = null;
        }
        if (mCurrent != null) {
            removePage(mCurrent);
            mCurrent = null;
        }
        if (mNext != null) {
            removePage(mNext);
            mNext = null;
        }
    }

    @Override
    public GalleryView.Adapter onDetach() {
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        // Cancel all animations
        cancelAllAnimations();

        // Remove all view
        removeProgress();
        removeErrorView();
        removeAllPages();

        // Clear iterator
        GalleryView.Adapter adapter = mAdapter;
        mAdapter = null;

        return adapter;
    }

    private GalleryPageView getLeftPage() {
        switch (mMode) {
            case MODE_LEFT_TO_RIGHT:
                return mPrevious;
            default:
            case MODE_RIGHT_TO_LEFT:
                return mNext;
        }
    }

    private GalleryPageView getRightPage() {
        switch (mMode) {
            case MODE_LEFT_TO_RIGHT:
                return mNext;
            default:
            case MODE_RIGHT_TO_LEFT:
                return mPrevious;
        }
    }

    private GalleryPageView obtainPage() {
        GalleryPageView page = mGalleryView.obtainPage();
        page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        return page;
    }

    private void layoutPage(GalleryPageView page, int widthSpec, int heightSpec,
            int left, int top, int right, int bottom) {
        Rect rect = mTempRect;
        page.getValidRect(rect);
        boolean oldValid = !rect.isEmpty();
        page.measure(widthSpec, heightSpec);
        page.layout(left, top, right, bottom);
        page.getValidRect(rect);
        boolean newValid = !rect.isEmpty();
        if (!oldValid && newValid) {
            page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        }
    }

    @Override
    public void onFill() {
        GalleryView.Adapter adapter = mAdapter;
        GalleryView galleryView = mGalleryView;
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", adapter);

        int width = galleryView.getWidth();
        int height = galleryView.getHeight();
        int size = adapter.size();
        String errorStr = adapter.getError();

        if (size == GalleryProvider.STATE_WAIT) { // Wait here, show progress bar
            // Remove error view and all pages
            removeErrorView();
            removeAllPages();

            // Ensure progress
            if (mProgress == null) {
                mProgress = galleryView.obtainProgress();
                galleryView.addComponent(mProgress);
            }

            // Place progress center
            placeCenter(mProgress);
        } else if (size <= GalleryProvider.STATE_ERROR || size == 0) { // Get error or empty, show error text
            // Ensure error is not null
            if (0 == size) {
                errorStr = galleryView.getEmptyStr();
            } else if (null == errorStr) {
                errorStr = galleryView.getDefaultErrorStr();
            }

            // Remove progress and all pages
            removeProgress();
            removeAllPages();

            // Ensure error view
            if (mErrorView == null) {
                mErrorView = galleryView.obtainErrorView();
                galleryView.addComponent(mErrorView);
            }

            // Update error string
            if (!errorStr.equals(mErrorStr)) {
                mErrorStr = errorStr;
                galleryView.bindErrorView(mErrorView, errorStr);
            }

            // Place error view center
            placeCenter(mErrorView);
        } else {
            // Remove progress and error view
            removeProgress();
            removeErrorView();

            // Ensure index in range
            int index = mIndex;
            if (index < 0) {
                index = 0;
                mIndex = index;
                removeAllPages();
                Log.e(TAG, "index < 0, index = " + index);
            } else if (index >= size) {
                index = size - 1;
                mIndex = index;
                removeAllPages();
                Log.e(TAG, "index >= size, index = " + index + ", size = " + size);
            }

            // Ensure pages
            if (mCurrent == null) {
                mCurrent = obtainPage();
                galleryView.addComponent(mCurrent);
                adapter.bind(mCurrent, index);
            }
            if (mPrevious == null && index > 0) {
                mPrevious = obtainPage();
                galleryView.addComponent(mPrevious);
                adapter.bind(mPrevious, index - 1);
            } else if (mPrevious != null && index == 0) {
                removePage(mPrevious);
            }
            if (mNext == null && index < size - 1) {
                mNext = obtainPage();
                galleryView.addComponent(mNext);
                adapter.bind(mNext, index + 1);
            } else if (mNext != null && index == size - 1) {
                removePage(mNext);
            }

            GalleryPageView leftPage = getLeftPage();
            GalleryPageView rightPage = getRightPage();

            // Fix offset
            final int min = rightPage == null ? 0 : -width - mInterval + 1;
            final int max = leftPage == null ? 0 : width + mInterval - 1;
            mOffset = MathUtils.clamp(mOffset, min, max);

            // Measure and layout pages
            final int offset = mOffset;
            final int widthSpec = GLView.MeasureSpec.makeMeasureSpec(width, GLView.MeasureSpec.EXACTLY);
            final int heightSpec = GLView.MeasureSpec.makeMeasureSpec(height, GLView.MeasureSpec.EXACTLY);
            if (mCurrent != null) {
                layoutPage(mCurrent, widthSpec, heightSpec,
                        offset, 0, width + offset, height);
            }
            if (leftPage != null) {
                layoutPage(leftPage, widthSpec, heightSpec,
                        -mInterval - width + offset, 0, -mInterval + offset, height);
            }
            if (rightPage != null) {
                layoutPage(rightPage, widthSpec, heightSpec,
                        width + mInterval + offset, 0, width + mInterval + width + offset, height);
            }
        }
    }

    @Override
    public void onDown() {
        mDeltaX = 0;
        mDeltaY = 0;
        mStopAnimationFinger = cancelAllAnimations();
    }

    @Override
    public void onUp() {
        mGalleryView.getEdgeView().onRelease();

        if (mCurrent == null) {
            return;
        }

        // Scroll
        if (mOffset != 0) {
            int width = mGalleryView.getWidth();
            int dx;
            if (mOffset >= mInterval && getLeftPage() != null) {
                dx = mOffset - width - mInterval;
            } else if (mOffset <= -mInterval && getRightPage() != null) {
                dx = mOffset + width + mInterval;
            } else {
                dx = mOffset;
            }
            final float pageDelta = 7 * (float) Math.abs(mOffset) / (width + mInterval);
            int duration = (int) ((pageDelta + 1) * 100);
            mSmoothScroller.startSmoothScroll(dx, 0, duration);
        }
    }

    @Override
    public void onDoubleTapConfirmed(float x, float y) {
        if (mCurrent == null || !mCurrent.getImageView().isLoaded()) {
            return;
        }

        float[] scales = mScaleDefault;
        ImageView image = mCurrent.getImageView();
        image.getScaleDefault(scales);
        float scale = image.getScale();
        float endScale = scales[0];
        for (float value: scales) {
            if (scale < value - 0.01f) {
                endScale = value;
                break;
            }
        }

        mSmoothScaler.startSmoothScaler(x, y, scale, endScale, 300);
    }

    @Override
    public void onLongPress(float x, float y) {}

    private void pagePrevious() {
        if (mIndex <= 0) {
            return;
        }
        mIndex--;

        if (mNext != null) {
            removePage(mNext);
        }
        mNext = mCurrent;
        mCurrent = mPrevious;
        mPrevious = null;

        if (mIndex > 0) {
            mPrevious = obtainPage();
            mGalleryView.addComponent(mPrevious);
            mAdapter.bind(mPrevious, mIndex - 1);
        }
    }

    private void pageNext() {
        GalleryView.Adapter adapter = mAdapter;
        int size = adapter.size();
        if (mIndex >= size - 1) {
            return;
        }
        mIndex++;

        if (mPrevious != null) {
            removePage(mPrevious);
        }
        mPrevious = mCurrent;
        mCurrent = mNext;
        mNext = null;

        if (mIndex < size - 1) {
            mNext = obtainPage();
            mGalleryView.addComponent(mNext);
            adapter.bind(mNext, mIndex + 1);
        }
    }

    private void pageLeft() {
        switch (mMode) {
            case MODE_LEFT_TO_RIGHT:
                pagePrevious();
                break;
            default:
            case MODE_RIGHT_TO_LEFT:
                pageNext();
                break;
        }
    }

    private void pageRight() {
        switch (mMode) {
            case MODE_LEFT_TO_RIGHT:
                pageNext();
                break;
            default:
            case MODE_RIGHT_TO_LEFT:
                pagePrevious();
                break;
        }
    }

    private int scrollBetweenPages(int dx) {
        GalleryPageView leftPage = getLeftPage();
        GalleryPageView rightPage = getRightPage();
        int width = mGalleryView.getWidth();

        int remain;
        if (dx < 0) { // Try to show left
            int limit;
            if (leftPage == null) {
                limit = 0;
            } else {
                limit = width + mInterval;
            }

            if (dx > mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                // Go to left page if left page not null
                if (leftPage != null) {
                    pageLeft();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        } else { // Try to show right
            int limit;
            if (rightPage == null) {
                limit = 0;
            } else {
                limit = - width - mInterval;
            }

            if (dx < mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                // Go to right page if right page not null
                if (rightPage != null) {
                    pageRight();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        }

        return remain;
    }

    public void overScrollEdge(int dx, int dy, float x, float y) {
        GLEdgeView edgeView = mGalleryView.getEdgeView();

        mDeltaX += dx;
        mDeltaY += dy;

        if (mDeltaX < 0) {
            edgeView.onPull(-mDeltaX, y, GLEdgeView.LEFT);
            if (!edgeView.isFinished(GLEdgeView.RIGHT)) {
                edgeView.onRelease(GLEdgeView.RIGHT);
            }
        } else if (mDeltaX > 0) {
            edgeView.onPull(mDeltaX, y, GLEdgeView.RIGHT);
            if (!edgeView.isFinished(GLEdgeView.LEFT)) {
                edgeView.onRelease(GLEdgeView.LEFT);
            }
        }

        if (mCurrent.getImageView().canFlingVertically()) {
            if (mDeltaY < 0) {
                edgeView.onPull(-mDeltaY, x, GLEdgeView.TOP);
                if (!edgeView.isFinished(GLEdgeView.BOTTOM)) {
                    edgeView.onRelease(GLEdgeView.BOTTOM);
                }
            } else if (mDeltaY > 0) {
                edgeView.onPull(mDeltaY, x, GLEdgeView.BOTTOM);
                if (!edgeView.isFinished(GLEdgeView.TOP)) {
                    edgeView.onRelease(GLEdgeView.TOP);
                }
            }
        }
    }

    public void scrollInternal(float dx, float dy, float x, float y) {
        if (mCurrent == null) {
            return;
        }

        boolean needFill = false;
        boolean canImageScroll = true;
        int remainX = (int) dx;
        int remainY = (int) dy;

        if (mGalleryView.isFirstScroll()) {
            mCanScrollBetweenPages = Math.abs(dx) > Math.abs(dy) * 1.5;
        }

        while (remainX != 0 || remainY != 0) {
            if (mOffset == 0 && canImageScroll) {
                ImageView image = mCurrent.getImageView();
                image.scroll(remainX, remainY, mScrollRemain);
                remainX = mScrollRemain[0];
                remainY = mScrollRemain[1];
                canImageScroll = false;
                mDeltaX = 0;
                mDeltaY = 0;
            } else if (remainX == 0 ||
                    (getLeftPage() == null && mOffset == 0 && remainX < 0) ||
                    (getRightPage() == null && mOffset == 0 && remainX > 0)) {
                // On edge
                overScrollEdge(remainX, remainY, x, y);
                remainX = 0;
                remainY = 0;
            } else if (mCanScrollBetweenPages) {
                remainX = scrollBetweenPages(remainX);
                canImageScroll = true;
                needFill = true;
                mDeltaX = 0;
                mDeltaY = 0;
            } else {
                remainX = 0;
                remainY = 0;
                mDeltaX = 0;
                mDeltaY = 0;
            }
        }

        if (needFill) {
            mGalleryView.requestFill();
        }
    }

    @Override
    public void onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {
        scrollInternal(dx, dy, x, y);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (mCurrent == null || mOffset != 0 || !mCurrent.getImageView().isLoaded() ||
                !mCurrent.getImageView().canFling()) {
            return;
        }

        ImageView image = mCurrent.getImageView();
        mPageFling.startFling((int) velocityX, image.getMinDx(), image.getMaxDx(),
                (int) velocityY, image.getMinDy(), image.getMaxDy());
    }

    @Override
    public boolean canScale() {
        return mCurrent != null && mOffset == 0 && mCurrent.getImageView().isLoaded();
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {
        if (mCurrent == null || !mCurrent.getImageView().isLoaded()) {
            return;
        }

        mCurrent.getImageView().scale(focusX, focusY, scale);
        mScaleValue = mCurrent.getImageView().getScale();
    }

    @Override
    public boolean onUpdateAnimation(long time) {
        boolean invalidate = mSmoothScroller.calculate(time);
        invalidate |= mPageFling.calculate(time);
        invalidate |= mSmoothScaler.calculate(time);
        invalidate |= mOverScroller.calculate(time);
        return invalidate;
    }

    @Override
    public void onDataChanged() {
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", mAdapter);

        // Cancel all animations
        cancelAllAnimations();
        // Remove all views
        removeProgress();
        removeErrorView();
        removeAllPages();
        // Reset parameters
        resetParameters();
        mGalleryView.requestFill();
    }

    @Override
    public void onPageLeft() {
        int size = mAdapter.size();
        if (size <= 0 || mCurrent == null) {
            return;
        }

        if (mMode == MODE_LEFT_TO_RIGHT) {
            if (mIndex == 0) {
                mOverScroller.overScroll(GLEdgeView.LEFT);
            } else {
                setCurrentIndex(mIndex - 1);
            }
        } else {
            if (mIndex >= size - 1) {
                mOverScroller.overScroll(GLEdgeView.LEFT);
                mGalleryView.onTransferEnd();
            } else {
                setCurrentIndex(mIndex + 1);
            }
        }
    }

    @Override
    public void onPageRight() {
        int size = mAdapter.size();
        if (size <= 0 || mCurrent == null) {
            return;
        }

        if (mMode == MODE_LEFT_TO_RIGHT) {
            if (mIndex >= size - 1) {
                mOverScroller.overScroll(GLEdgeView.RIGHT);
                mGalleryView.onTransferEnd();
            } else {
                setCurrentIndex(mIndex + 1);
            }
        } else {
            if (mIndex == 0) {
                mOverScroller.overScroll(GLEdgeView.RIGHT);
            } else {
                setCurrentIndex(mIndex - 1);
            }
        }
    }

    @Override
    public boolean isTapOrPressEnable() {
        return !mStopAnimationFinger;
    }

    @Override
    public GalleryPageView findPageByIndex(int index) {
        if (mCurrent != null && mCurrent.getIndex() == index) {
            return mCurrent;
        }
        if (mPrevious != null && mPrevious.getIndex() == index) {
            return mPrevious;
        }
        if (mNext != null && mNext.getIndex() == index) {
            return mNext;
        }
        return null;
    }

    @Override
    public int getCurrentIndex() {
        if (mCurrent != null) {
            return mCurrent.getIndex();
        } else {
            return GalleryPageView.INVALID_INDEX;
        }
    }

    @Override
    public void setCurrentIndex(int index) {
        int size = mAdapter.size();
        if (size <= 0) {
            // Can't get size now, assume size is MAX
            size = Integer.MAX_VALUE;
        }
        if (index == mIndex || index < 0 || index >= size) {
            return;
        }
        if (mCurrent == null) {
            mIndex = index;
        } else if (index == mIndex - 1) {
            // Cancel all animations
            cancelAllAnimations();
            // Reset parameters
            resetParameters();
            // Go to previous
            pagePrevious();
            // Request fill
            mGalleryView.requestFill();
        } else if (index == mIndex + 1) {
            // Cancel all animations
            cancelAllAnimations();
            // Reset parameters
            resetParameters();
            // Go to next
            pageNext();
            // Request fill
            mGalleryView.requestFill();
        } else {
            mIndex = index;
            // It is attached, refill
            // Cancel all animations
            cancelAllAnimations();
            // Remove all view
            removeProgress();
            removeErrorView();
            removeAllPages();
            // Reset parameters
            resetParameters();
            // Request fill
            mGalleryView.requestFill();
        }
    }

    @Override
    public int getIndexUnder(float x, float y) {
        if (mCurrent == null) {
            return GalleryPageView.INVALID_INDEX;
        } else {
            int intX = (int) x;
            int intY = (int) y;
            if (mCurrent.bounds().contains(intX, intY)) {
                return mCurrent.getIndex();
            } else if (mPrevious != null && mPrevious.bounds().contains(intX, intY)) {
                return mPrevious.getIndex();
            } else if (mNext != null && mNext.bounds().contains(intX, intY)) {
                return mNext.getIndex();
            } else {
                return GalleryPageView.INVALID_INDEX;
            }
        }
    }

    @Override
    int getInternalCurrentIndex() {
        int currentIndex = getCurrentIndex();
        if (currentIndex == GalleryPageView.INVALID_INDEX) {
            currentIndex = mIndex;
        }
        return currentIndex;
    }

    private class SmoothScroller extends Animation {

        private int mDx;
        private int mDy;
        private int mLastX;
        private int mLastY;

        public SmoothScroller() {
            setInterpolator(SMOOTH_SCROLLER_INTERPOLATOR);
        }

        public void startSmoothScroll(int dx, int dy, int duration) {
            mDx = dx;
            mDy = dy;
            mLastX = 0;
            mLastY = 0;
            setDuration(duration);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            int x = (int) (mDx * progress);
            int y = (int) (mDy * progress);
            int offsetX = x - mLastX;
            while (offsetX != 0) {
                int oldOffsetX = offsetX;
                offsetX = scrollBetweenPages(offsetX);
                // Avoid loop infinitely
                if (offsetX == oldOffsetX) {
                    break;
                } else {
                    mGalleryView.requestFill();
                }
            }
            mLastX = x;
            mLastY = y;
        }
    }

    private class PageFling extends Fling {

        private int mVelocityX;
        private int mVelocityY;
        private int mDx;
        private int mDy;
        private int mLastX;
        private int mLastY;
        private final int[] mTemp = new int[2];

        public PageFling(Context context) {
            super(context);
        }

        public void startFling(int velocityX, int minX, int maxX,
                int velocityY, int minY, int maxY) {
            mVelocityX = velocityX;
            mVelocityY = velocityY;
            mDx = (int) (getSplineFlingDistance(velocityX) * Math.signum(velocityX));
            mDy = (int) (getSplineFlingDistance(velocityY) * Math.signum(velocityY));
            mLastX = 0;
            mLastY = 0;
            int durationX = getSplineFlingDuration(velocityX);
            int durationY = getSplineFlingDuration(velocityY);

            if (mDx < minX) {
                durationX = adjustDuration(0, mDx, minX, durationX);
                mDx = minX;
            }
            if (mDx > maxX) {
                durationX = adjustDuration(0, mDx, maxX, durationX);
                mDx = maxX;
            }
            if (mDy < minY) {
                durationY = adjustDuration(0, mDy, minY, durationY);
                mDy = minY;
            }
            if (mDy > maxY) {
                durationY = adjustDuration(0, mDy, maxY, durationY);
                mDy = maxY;
            }

            if (mDx == 0 && mDy == 0) {
                return;
            }

            setDuration(Math.max(durationX, durationY));
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            int x = (int) (mDx * progress);
            int y = (int) (mDy * progress);
            int offsetX = x - mLastX;
            int offsetY = y - mLastY;
            if (mCurrent != null && (offsetX != 0 || offsetY != 0)) {
                mCurrent.getImageView().scroll(-offsetX, -offsetY, mTemp);
            }
            mLastX = x;
            mLastY = y;
        }

        @Override
        protected void onFinish() {
            if (mCurrent == null) {
                return;
            }

            GLEdgeView edgeView = mGalleryView.getEdgeView();
            ImageView imageView = mCurrent.getImageView();
            if (imageView.canFlingHorizontally()) {
                if (mVelocityX > 0 && getLeftPage() == null && imageView.getMaxDx() == 0 &&
                        edgeView.isFinished(GLEdgeView.LEFT)) {
                    edgeView.onAbsorb(mVelocityX, GLEdgeView.LEFT);
                } else if (mVelocityX < 0 && getRightPage() == null && imageView.getMinDx() == 0 &&
                        edgeView.isFinished(GLEdgeView.RIGHT)) {
                    edgeView.onAbsorb(-mVelocityX, GLEdgeView.RIGHT);
                }
            }
            if (imageView.canFlingVertically()) {
                if (mVelocityY > 0 && imageView.getMaxDy() == 0 &&
                        edgeView.isFinished(GLEdgeView.TOP)) {
                    edgeView.onAbsorb(mVelocityY, GLEdgeView.TOP);
                } else if (mVelocityY < 0 && imageView.getMinDy() == 0 &&
                        edgeView.isFinished(GLEdgeView.BOTTOM)) {
                    edgeView.onAbsorb(-mVelocityY, GLEdgeView.BOTTOM);
                }
            }
        }
    }

    private class SmoothScaler extends Animation {

        private float mFocusX;
        private float mFocusY;
        private float mStartScale;
        private float mEndScale;
        private float mLastScale;

        public SmoothScaler() {
            setInterpolator(AnimationUtils.FAST_SLOW_INTERPOLATOR);
        }

        public void startSmoothScaler(float focusX, float focusY,
                float startScale, float endScale, int duration) {
            mFocusX = focusX;
            mFocusY = focusY;
            mStartScale = startScale;
            mEndScale = endScale;
            mLastScale = startScale;
            setDuration(duration);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            if (mCurrent == null) {
                return;
            }

            float scale = MathUtils.lerp(mStartScale, mEndScale, progress);
            mCurrent.getImageView().scale(mFocusX, mFocusY, scale / mLastScale);
            mLastScale = scale;
            mScaleValue = scale;
        }
    }

    private class OverScroller extends FloatAnimation {

        private int mDirection;
        private int mPosition;

        public OverScroller() {
            setDuration(300L);
        }

        public void overScroll(int direction) {
            mDirection = direction;
            int range;
            switch (mDirection) {
                case GLEdgeView.LEFT:
                case GLEdgeView.RIGHT:
                    range = mGalleryView.getWidth() / 7;
                    mPosition = mGalleryView.getHeight() / 2;
                    break;
                case GLEdgeView.TOP:
                case GLEdgeView.BOTTOM:
                    range = mGalleryView.getHeight() / 7;
                    mPosition = mGalleryView.getWidth() / 2;
                    break;
                default:
                    return;
            }
            setRange(0, range);
            start();
            mGalleryView.invalidate();
        }

        @Override
        protected void onCalculate(float progress) {
            super.onCalculate(progress);
            mGalleryView.getEdgeView().onPull(get(), mPosition, mDirection);
        }

        @Override
        protected void onFinish() {
            mGalleryView.getEdgeView().onRelease(mDirection);
        }
    }
}
