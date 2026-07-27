/*
 * Copyright 2026 Hippo Seven
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
import androidx.annotation.Nullable;

import com.hippo.lib.glview.anim.Animation;
import com.hippo.lib.glview.anim.FloatAnimation;
import com.hippo.lib.glview.view.GLView;
import com.hippo.lib.glview.widget.GLEdgeView;
import com.hippo.lib.glview.widget.GLProgressView;
import com.hippo.lib.glview.widget.GLTextureView;
import com.hippo.lib.yorozuya.AnimationUtils;
import com.hippo.lib.yorozuya.AssertUtils;
import com.hippo.lib.yorozuya.MathUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Lays out two pages side-by-side (a "spread") in landscape. Pages are paired
 * (0 alone when the cover option is on, then 1+2, 3+4, ...), advancing one
 * spread at a time. Modeled on {@link PagerLayoutManager}.
 */
class SpreadLayoutManager extends GalleryView.LayoutManager {

    private static final String TAG = SpreadLayoutManager.class.getSimpleName();

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

    // Pages of the previous / current / next spread. *1 is null for a single-page spread.
    private GalleryPageView mPrev0;
    private GalleryPageView mPrev1;
    private GalleryPageView mCenter0;
    private GalleryPageView mCenter1;
    private GalleryPageView mNext0;
    private GalleryPageView mNext1;

    @Mode
    private int mMode = MODE_RIGHT_TO_LEFT;
    private boolean mCoverEnabled = true;
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

    private final SmoothScroller mSmoothScroller;
    private final PageFling mPageFling;
    private final SmoothScaler mSmoothScaler;
    private final OverScroller mOverScroller;

    // First page index of the current (center) spread.
    private int mIndex;

    private final Rect mTempRect = new Rect();

    public SpreadLayoutManager(Context context, @NonNull GalleryView galleryView,
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

    public void setMode(@Mode int mode) {
        if (mMode == mode) {
            return;
        }
        mMode = mode;
        if (mAdapter != null) {
            cancelAllAnimations();
            removeProgress();
            removeErrorView();
            removeAllPages();
            resetParameters();
            mGalleryView.requestFill();
        }
    }

    public void setCoverEnabled(boolean coverEnabled) {
        if (mCoverEnabled == coverEnabled) {
            return;
        }
        mCoverEnabled = coverEnabled;
        if (mAdapter != null) {
            cancelAllAnimations();
            removeProgress();
            removeErrorView();
            removeAllPages();
            resetParameters();
            mGalleryView.requestFill();
        }
    }

    public void setScaleMode(int scaleMode) {
        if (mScaleMode == scaleMode) {
            return;
        }
        mScaleMode = scaleMode;
        applyScaleToAllPages();
    }

    public void setStartPosition(int startPosition) {
        if (mStartPosition == startPosition) {
            return;
        }
        mStartPosition = startPosition;
        applyScaleToAllPages();
    }

    private void applyScaleToAllPages() {
        GalleryPageView[] pages = {mPrev0, mPrev1, mCenter0, mCenter1, mNext0, mNext1};
        for (GalleryPageView page : pages) {
            if (page != null) {
                page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
            }
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

    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        AssertUtils.assertNull("The SpreadLayoutManager is attached", mAdapter);
        AssertUtils.assertNotNull("The adapter is null", adapter);
        mAdapter = adapter;
        resetParameters();
    }

    @Override
    public GalleryView.Adapter onDetach() {
        AssertUtils.assertNotNull("The SpreadLayoutManager is not attached", mAdapter);
        cancelAllAnimations();
        removeProgress();
        removeErrorView();
        removeAllPages();
        GalleryView.Adapter adapter = mAdapter;
        mAdapter = null;
        return adapter;
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

    private void removePage(@Nullable GalleryPageView page) {
        if (page == null) {
            return;
        }
        mGalleryView.removeComponent(page);
        mAdapter.unbind(page);
        mGalleryView.releasePage(page);
    }

    private void removeAllPages() {
        removePage(mPrev0);
        removePage(mPrev1);
        removePage(mCenter0);
        removePage(mCenter1);
        removePage(mNext0);
        removePage(mNext1);
        mPrev0 = mPrev1 = mCenter0 = mCenter1 = mNext0 = mNext1 = null;
    }

    // ---- Spread computation ----

    private int size() {
        return mAdapter.size();
    }

    /** Number of pages (1 or 2) in the spread that starts at {@code start}. */
    private int spreadSize(int start) {
        int size = size();
        if (start < 0 || start >= size) {
            return 0;
        }
        if (mCoverEnabled && start == 0) {
            return 1;
        }
        return (start + 1 < size) ? 2 : 1;
    }

    /** First page index of the spread containing {@code page}. */
    private int spreadStartOf(int page) {
        if (page < 0) {
            return -1;
        }
        if (mCoverEnabled) {
            if (page == 0) {
                return 0;
            }
            return 1 + 2 * ((page - 1) / 2);
        } else {
            return 2 * (page / 2);
        }
    }

    private int prevSpreadStart(int start) {
        if (start <= 0) {
            return -1;
        }
        if (mCoverEnabled && start == 1) {
            return 0;
        }
        return start - 2;
    }

    private int nextSpreadStart(int start) {
        int next = start + spreadSize(start);
        return next < size() ? next : -1;
    }

    // ---- Page binding ----

    private GalleryPageView obtainPage() {
        GalleryPageView page = mGalleryView.obtainPage();
        page.getImageView().setScaleOffset(mScaleMode, mStartPosition, mScaleValue);
        return page;
    }

    private GalleryPageView bindPage(int index) {
        GalleryPageView page = obtainPage();
        mGalleryView.addComponent(page);
        mAdapter.bind(page, index);
        return page;
    }

    /** Ensures the center spread pages exist for {@code mIndex}. */
    private void ensureCenterSpread() {
        int s = spreadSize(mIndex);
        if (mCenter0 == null) {
            mCenter0 = bindPage(mIndex);
        }
        if (s >= 2) {
            if (mCenter1 == null) {
                mCenter1 = bindPage(mIndex + 1);
            }
        } else if (mCenter1 != null) {
            removePage(mCenter1);
            mCenter1 = null;
        }
    }

    private void ensurePrevSpread() {
        int prevStart = prevSpreadStart(mIndex);
        if (prevStart < 0) {
            removePage(mPrev0);
            removePage(mPrev1);
            mPrev0 = mPrev1 = null;
            return;
        }
        int s = spreadSize(prevStart);
        if (mPrev0 == null) {
            mPrev0 = bindPage(prevStart);
        }
        if (s >= 2) {
            if (mPrev1 == null) {
                mPrev1 = bindPage(prevStart + 1);
            }
        } else if (mPrev1 != null) {
            removePage(mPrev1);
            mPrev1 = null;
        }
    }

    private void ensureNextSpread() {
        int nextStart = nextSpreadStart(mIndex);
        if (nextStart < 0) {
            removePage(mNext0);
            removePage(mNext1);
            mNext0 = mNext1 = null;
            return;
        }
        int s = spreadSize(nextStart);
        if (mNext0 == null) {
            mNext0 = bindPage(nextStart);
        }
        if (s >= 2) {
            if (mNext1 == null) {
                mNext1 = bindPage(nextStart + 1);
            }
        } else if (mNext1 != null) {
            removePage(mNext1);
            mNext1 = null;
        }
    }

    // ---- Layout ----

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

    /**
     * Lays out one spread whose left edge is at {@code left}. Returns the spread width used.
     * {@code p0}/{@code p1} are the first/second page in reading order.
     */
    private void layoutSpread(GalleryPageView p0, GalleryPageView p1, int left, int width,
            int height, int widthSpec, int heightSpec, boolean readingLeftToRight) {
        int seam = width / 2;
        if (p1 == null) {
            // Single page (cover or trailing): center a half-width slot.
            int slotLeft = left + (width - seam) / 2;
            layoutPage(p0, widthSpec, heightSpec, slotLeft, 0, slotLeft + seam, height);
        } else if (readingLeftToRight) {
            layoutPage(p0, widthSpec, heightSpec, left, 0, left + seam, height);
            layoutPage(p1, widthSpec, heightSpec, left + seam, 0, left + width, height);
        } else {
            // RTL: first page on the right half.
            layoutPage(p1, widthSpec, heightSpec, left, 0, left + seam, height);
            layoutPage(p0, widthSpec, heightSpec, left + seam, 0, left + width, height);
        }
    }

    @Override
    public void onFill() {
        GalleryView.Adapter adapter = mAdapter;
        GalleryView galleryView = mGalleryView;
        AssertUtils.assertNotNull("The SpreadLayoutManager is not attached", adapter);

        int width = galleryView.getWidth();
        int height = galleryView.getHeight();
        int size = adapter.size();
        String errorStr = adapter.getError();

        if (size == GalleryProvider.STATE_WAIT) {
            removeErrorView();
            removeAllPages();
            if (mProgress == null) {
                mProgress = galleryView.obtainProgress();
                galleryView.addComponent(mProgress);
            }
            placeCenter(mProgress);
        } else if (size <= GalleryProvider.STATE_ERROR || size == 0) {
            if (0 == size) {
                errorStr = galleryView.getEmptyStr();
            } else if (null == errorStr) {
                errorStr = galleryView.getDefaultErrorStr();
            }
            removeProgress();
            removeAllPages();
            if (mErrorView == null) {
                mErrorView = galleryView.obtainErrorView();
                galleryView.addComponent(mErrorView);
            }
            if (!errorStr.equals(mErrorStr)) {
                mErrorStr = errorStr;
                galleryView.bindErrorView(mErrorView, errorStr);
            }
            placeCenter(mErrorView);
        } else {
            removeProgress();
            removeErrorView();

            int index = mIndex;
            if (index < 0) {
                index = 0;
            } else if (index >= size) {
                index = size - 1;
            }
            // Snap index to a spread start.
            index = spreadStartOf(index);
            if (index != mIndex) {
                mIndex = index;
                removeAllPages();
            }

            ensureCenterSpread();
            ensurePrevSpread();
            ensureNextSpread();

            boolean ltr = mMode == MODE_LEFT_TO_RIGHT;
            // Physical left spread = previous for LTR, next for RTL.
            GalleryPageView leftA = ltr ? mPrev0 : mNext0;
            GalleryPageView leftB = ltr ? mPrev1 : mNext1;
            GalleryPageView rightA = ltr ? mNext0 : mPrev0;
            GalleryPageView rightB = ltr ? mNext1 : mPrev1;

            // Clamp offset.
            final int min = rightA == null ? 0 : -width - mInterval + 1;
            final int max = leftA == null ? 0 : width + mInterval - 1;
            mOffset = MathUtils.clamp(mOffset, min, max);

            final int offset = mOffset;
            final int widthSpec = GLView.MeasureSpec.makeMeasureSpec(width, GLView.MeasureSpec.EXACTLY);
            final int heightSpec = GLView.MeasureSpec.makeMeasureSpec(height, GLView.MeasureSpec.EXACTLY);

            // Center spread.
            layoutSpread(mCenter0, mCenter1, offset, width, height, widthSpec, heightSpec, ltr);
            // Left spread.
            if (leftA != null) {
                layoutSpread(leftA, leftB, -mInterval - width + offset, width, height,
                        widthSpec, heightSpec, ltr);
            }
            // Right spread.
            if (rightA != null) {
                layoutSpread(rightA, rightB, width + mInterval + offset, width, height,
                        widthSpec, heightSpec, ltr);
            }
        }
    }

    // ---- Spread advancement ----

    private void spreadPrevious() {
        int prevStart = prevSpreadStart(mIndex);
        if (prevStart < 0) {
            return;
        }
        mIndex = prevStart;

        removePage(mNext0);
        removePage(mNext1);
        mNext0 = mCenter0;
        mNext1 = mCenter1;
        mCenter0 = mPrev0;
        mCenter1 = mPrev1;
        mPrev0 = mPrev1 = null;

        ensurePrevSpread();
    }

    private void spreadNext() {
        int nextStart = nextSpreadStart(mIndex);
        if (nextStart < 0) {
            return;
        }
        mIndex = nextStart;

        removePage(mPrev0);
        removePage(mPrev1);
        mPrev0 = mCenter0;
        mPrev1 = mCenter1;
        mCenter0 = mNext0;
        mCenter1 = mNext1;
        mNext0 = mNext1 = null;

        ensureNextSpread();
    }

    private void spreadLeft() {
        if (mMode == MODE_LEFT_TO_RIGHT) {
            spreadPrevious();
        } else {
            spreadNext();
        }
    }

    private void spreadRight() {
        if (mMode == MODE_LEFT_TO_RIGHT) {
            spreadNext();
        } else {
            spreadPrevious();
        }
    }

    private boolean hasLeftSpread() {
        return mMode == MODE_LEFT_TO_RIGHT ? prevSpreadStart(mIndex) >= 0 : nextSpreadStart(mIndex) >= 0;
    }

    private boolean hasRightSpread() {
        return mMode == MODE_LEFT_TO_RIGHT ? nextSpreadStart(mIndex) >= 0 : prevSpreadStart(mIndex) >= 0;
    }

    private int scrollBetweenSpreads(int dx) {
        int width = mGalleryView.getWidth();
        boolean leftExists = hasLeftSpread();
        boolean rightExists = hasRightSpread();

        int remain;
        if (dx < 0) { // reveal left
            int limit = leftExists ? width + mInterval : 0;
            if (dx > mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                if (leftExists) {
                    spreadLeft();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        } else { // reveal right
            int limit = rightExists ? -width - mInterval : 0;
            if (dx < mOffset - limit) {
                remain = 0;
                mOffset -= dx;
            } else {
                if (rightExists) {
                    spreadRight();
                }
                remain = dx + limit - mOffset;
                mOffset = 0;
            }
        }
        return remain;
    }

    // ---- Gestures ----

    @Override
    public void onDown() {
        mDeltaX = 0;
        mDeltaY = 0;
        mStopAnimationFinger = cancelAllAnimations();
    }

    @Override
    public void onUp() {
        mGalleryView.getEdgeView().onRelease();

        if (mCenter0 == null) {
            return;
        }

        if (mOffset != 0) {
            int width = mGalleryView.getWidth();
            int dx;
            if (mOffset >= mInterval && hasLeftSpread()) {
                dx = mOffset - width - mInterval;
            } else if (mOffset <= -mInterval && hasRightSpread()) {
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
        if (mCenter0 == null || !mCenter0.getImageView().isLoaded()) {
            return;
        }
        ImageView image = mCenter0.getImageView();
        float[] scales = new float[4];
        image.getScaleDefault(scales);
        float scale = image.getScale();
        float endScale = scales[0];
        for (float value : scales) {
            if (scale < value - 0.01f) {
                endScale = value;
                break;
            }
        }
        mSmoothScaler.startSmoothScaler(x, y, scale, endScale, 300);
    }

    @Override
    public void onLongPress(float x, float y) {}

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

        if (mCenter0 != null && mCenter0.getImageView().canFlingVertically()) {
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
        if (mCenter0 == null) {
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
                ImageView image = mCenter0.getImageView();
                image.scroll(remainX, remainY, mScrollRemain);
                remainX = mScrollRemain[0];
                remainY = mScrollRemain[1];
                canImageScroll = false;
                mDeltaX = 0;
                mDeltaY = 0;
            } else if (remainX == 0 ||
                    (!hasLeftSpread() && mOffset == 0 && remainX < 0) ||
                    (!hasRightSpread() && mOffset == 0 && remainX > 0)) {
                overScrollEdge(remainX, remainY, x, y);
                remainX = 0;
                remainY = 0;
            } else if (mCanScrollBetweenPages) {
                remainX = scrollBetweenSpreads(remainX);
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
        if (mCenter0 == null || mOffset != 0 || !mCenter0.getImageView().isLoaded() ||
                !mCenter0.getImageView().canFling()) {
            return;
        }
        ImageView image = mCenter0.getImageView();
        mPageFling.startFling((int) velocityX, image.getMinDx(), image.getMaxDx(),
                (int) velocityY, image.getMinDy(), image.getMaxDy());
    }

    @Override
    public boolean canScale() {
        return mCenter0 != null && mOffset == 0 && mCenter0.getImageView().isLoaded();
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {
        if (mCenter0 == null || !mCenter0.getImageView().isLoaded()) {
            return;
        }
        // Zoom the whole spread: scale both center pages by the same factor.
        mCenter0.getImageView().scale(focusX, focusY, scale);
        if (mCenter1 != null && mCenter1.getImageView().isLoaded()) {
            mCenter1.getImageView().scale(focusX, focusY, scale);
        }
        mScaleValue = mCenter0.getImageView().getScale();
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
        AssertUtils.assertNotNull("The SpreadLayoutManager is not attached", mAdapter);
        cancelAllAnimations();
        removeProgress();
        removeErrorView();
        removeAllPages();
        resetParameters();
        mGalleryView.requestFill();
    }

    @Override
    public void onPageLeft() {
        int size = size();
        if (size <= 0 || mCenter0 == null) {
            return;
        }
        if (mMode == MODE_LEFT_TO_RIGHT) {
            if (prevSpreadStart(mIndex) < 0) {
                mOverScroller.overScroll(GLEdgeView.LEFT);
            } else {
                setCurrentIndex(prevSpreadStart(mIndex));
            }
        } else {
            if (nextSpreadStart(mIndex) < 0) {
                mOverScroller.overScroll(GLEdgeView.LEFT);
                mGalleryView.onTransferEnd();
            } else {
                setCurrentIndex(nextSpreadStart(mIndex));
            }
        }
    }

    @Override
    public void onPageRight() {
        int size = size();
        if (size <= 0 || mCenter0 == null) {
            return;
        }
        if (mMode == MODE_LEFT_TO_RIGHT) {
            if (nextSpreadStart(mIndex) < 0) {
                mOverScroller.overScroll(GLEdgeView.RIGHT);
                mGalleryView.onTransferEnd();
            } else {
                setCurrentIndex(nextSpreadStart(mIndex));
            }
        } else {
            if (prevSpreadStart(mIndex) < 0) {
                mOverScroller.overScroll(GLEdgeView.RIGHT);
            } else {
                setCurrentIndex(prevSpreadStart(mIndex));
            }
        }
    }

    @Override
    public boolean isTapOrPressEnable() {
        return !mStopAnimationFinger;
    }

    @Override
    public GalleryPageView findPageByIndex(int index) {
        GalleryPageView[] pages = {mPrev0, mPrev1, mCenter0, mCenter1, mNext0, mNext1};
        for (GalleryPageView page : pages) {
            if (page != null && page.getIndex() == index) {
                return page;
            }
        }
        return null;
    }

    @Override
    public int getCurrentIndex() {
        if (mCenter0 != null) {
            return mCenter0.getIndex();
        } else {
            return GalleryPageView.INVALID_INDEX;
        }
    }

    @Override
    public void setCurrentIndex(int index) {
        int size = size();
        if (size <= 0) {
            size = Integer.MAX_VALUE;
        }
        index = MathUtils.clamp(index, 0, size - 1);
        int start = spreadStartOf(index);

        if (mCenter0 == null) {
            mIndex = start;
            return;
        }

        if (start == nextSpreadStart(mIndex)) {
            cancelAllAnimations();
            resetParameters();
            spreadNext();
            mGalleryView.requestFill();
        } else if (start == prevSpreadStart(mIndex)) {
            cancelAllAnimations();
            resetParameters();
            spreadPrevious();
            mGalleryView.requestFill();
        } else if (start != mIndex) {
            mIndex = start;
            cancelAllAnimations();
            removeProgress();
            removeErrorView();
            removeAllPages();
            resetParameters();
            mGalleryView.requestFill();
        }
    }

    @Override
    public int getIndexUnder(float x, float y) {
        if (mCenter0 == null) {
            return GalleryPageView.INVALID_INDEX;
        }
        int intX = (int) x;
        int intY = (int) y;
        GalleryPageView[] pages = {mCenter0, mCenter1, mPrev0, mPrev1, mNext0, mNext1};
        for (GalleryPageView page : pages) {
            if (page != null && page.bounds().contains(intX, intY)) {
                return page.getIndex();
            }
        }
        return GalleryPageView.INVALID_INDEX;
    }

    @Override
    int getInternalCurrentIndex() {
        int currentIndex = getCurrentIndex();
        if (currentIndex == GalleryPageView.INVALID_INDEX) {
            currentIndex = mIndex;
        }
        return currentIndex;
    }

    // ---- Animations (modeled on PagerLayoutManager) ----

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
                offsetX = scrollBetweenSpreads(offsetX);
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
            if (mCenter0 != null && (offsetX != 0 || offsetY != 0)) {
                mCenter0.getImageView().scroll(-offsetX, -offsetY, mTemp);
            }
            mLastX = x;
            mLastY = y;
        }

        @Override
        protected void onFinish() {
            if (mCenter0 == null) {
                return;
            }
            GLEdgeView edgeView = mGalleryView.getEdgeView();
            ImageView imageView = mCenter0.getImageView();
            if (imageView.canFlingHorizontally()) {
                if (mVelocityX > 0 && !hasLeftSpread() && imageView.getMaxDx() == 0 &&
                        edgeView.isFinished(GLEdgeView.LEFT)) {
                    edgeView.onAbsorb(mVelocityX, GLEdgeView.LEFT);
                } else if (mVelocityX < 0 && !hasRightSpread() && imageView.getMinDx() == 0 &&
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
            if (mCenter0 == null) {
                return;
            }
            float scale = MathUtils.lerp(mStartScale, mEndScale, progress);
            float factor = scale / mLastScale;
            mCenter0.getImageView().scale(mFocusX, mFocusY, factor);
            if (mCenter1 != null && mCenter1.getImageView().isLoaded()) {
                mCenter1.getImageView().scale(mFocusX, mFocusY, factor);
            }
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
