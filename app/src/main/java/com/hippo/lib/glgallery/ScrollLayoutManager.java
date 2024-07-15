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

import androidx.annotation.NonNull;

import com.hippo.glview.anim.Animation;
import com.hippo.glview.anim.FloatAnimation;
import com.hippo.glview.view.GLView;
import com.hippo.glview.widget.GLEdgeView;
import com.hippo.glview.widget.GLProgressView;
import com.hippo.glview.widget.GLTextureView;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.MathUtils;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

class ScrollLayoutManager extends GalleryView.LayoutManager {

    private static final String TAG = ScrollLayoutManager.class.getSimpleName();

    private static final float RESERVATION = 1f;

    private static final float MAX_SCALE = 2.0f;
    private static final float MIN_SCALE = 1.0f;
    private static final float SCALE_ERROR = 0.01f;

    private static final int INVALID_TOP = Integer.MAX_VALUE;

    private GalleryView.Adapter mAdapter;

    private GLProgressView mProgress;
    private String mErrorStr;
    private GLTextureView mErrorView;
    private final LinkedList<GalleryPageView> mPages = new LinkedList<>();
    private final LinkedList<GalleryPageView> mTempPages = new LinkedList<>();

    private float mScale = 0.5f;
    private int mOffsetX;
    private int mOffsetY;
    private int mDeltaX;
    private int mDeltaY;
    private int mKeepTopPageIndex = GalleryPageView.INVALID_INDEX;
    private int mKeepTop = INVALID_TOP;
    private int mFirstShownPageIndex = GalleryPageView.INVALID_INDEX;
    private boolean mScrollUp;
    private boolean mFlingUp;
    private boolean mStopAnimationFinger;

    private int mInterval;

    private final PageFling mPageFling;
    private final SmoothScaler mSmoothScaler;
    private final OverScroller mOverScroller;

    // Current index
    private int mIndex;

    private int mBottomStateBottom;
    private boolean mBottomStateHasNext;

    public ScrollLayoutManager(Context context, @NonNull GalleryView galleryView, int interval) {
        super(galleryView);

        mInterval = interval;
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
        mScale = 1.0f;
        mOffsetX = 0;
        mOffsetY = 0;
        mDeltaX = 0;
        mDeltaY = 0;
        mKeepTopPageIndex = GalleryPageView.INVALID_INDEX;
        mKeepTop = INVALID_TOP;
        mFirstShownPageIndex = GalleryPageView.INVALID_INDEX;
        mScrollUp = false;
        mFlingUp = false;
        mStopAnimationFinger = false;
    }

    // Return true for animations are running
    private boolean cancelAllAnimations() {
        boolean running = mPageFling.isRunning() ||
                mSmoothScaler.isRunning() ||
                mOverScroller.isRunning();
        mPageFling.cancel();
        mSmoothScaler.cancel();
        mOverScroller.cancel();
        return running;
    }

    @Override
    public void onAttach(GalleryView.Adapter adapter) {
        AssertUtils.assertNull("The ScrollLayoutManager is attached", mAdapter);
        AssertUtils.assertNotNull("The iterator is null", adapter);
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
        for (GalleryPageView page : mPages) {
            removePage(page);
        }
        mPages.clear();
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
        GalleryView.Adapter iterator = mAdapter;
        mAdapter = null;

        return iterator;
    }

    private GalleryPageView obtainPage() {
        GalleryPageView page = mGalleryView.obtainPage();
        page.getImageView().setScaleOffset(ImageView.SCALE_FIT, ImageView.START_POSITION_TOP_RIGHT, 1.0f);
        return page;
    }

    private GalleryPageView getPageForIndex(List<GalleryPageView> pages, int index, boolean remove) {
        for (Iterator<GalleryPageView> iterator = pages.iterator(); iterator.hasNext();) {
            GalleryPageView page = iterator.next();
            if (index == page.getIndex()) {
                if (remove) {
                    iterator.remove();
                }
                return page;
            }
        }
        return null;
    }

    private boolean isInScreen(GalleryPageView page) {
        int height = mGalleryView.getHeight();
        Rect bound = page.bounds();
        int pageTop = bound.top;
        int pageBottom = bound.bottom;
        return (pageTop >= 0 && pageTop < height) || (pageBottom > 0 && pageBottom <= height) ||
                (pageTop < 0 && pageBottom > height);
    }

    private float getReservation() {
        return Math.max(RESERVATION, (((1 + 2 * RESERVATION) * mScale) - 1) / 2);
    }

    private void fillPages(int startIndex, int startOffset) {
        final GalleryView.Adapter adapter = mAdapter;
        final GalleryView galleryView = mGalleryView;
        final LinkedList<GalleryPageView> pages = mPages;
        final LinkedList<GalleryPageView> tempPages = mTempPages;
        final int width = galleryView.getWidth();
        final int height = galleryView.getHeight();
        final int pageWidth = (int) (width * mScale);
        final int size = adapter.size();
        final int interval = mInterval;
        final float reservation = getReservation();
        final int minY = (int) (-height * reservation);
        final int maxY = (int) (height * (1 + reservation));
        final int widthSpec = GLView.MeasureSpec.makeMeasureSpec(pageWidth, GLView.MeasureSpec.EXACTLY);
        final int heightSpec = GLView.MeasureSpec.makeMeasureSpec(height, GLView.MeasureSpec.UNSPECIFIED);

        // Fix start index and start offset
        if (startIndex < 0) {
            startIndex = 0;
            startOffset = 0;
        } else if (startIndex >= size) {
            startIndex = size - 1;
            startOffset = 0;
        } else if (startOffset < minY) {
            while (true) {
                GalleryPageView page = getPageForIndex(pages, startIndex, false);
                if (null == page) {
                    startOffset = minY;
                    break;
                } else {
                    page.measure(widthSpec, heightSpec);
                    if (startOffset + page.getHeight() > minY) {
                        break;
                    } else if (size - 1 == startIndex) {
                        startOffset = 0;
                        break;
                    } else {
                        ++startIndex;
                        startOffset += page.getHeight() + interval;
                        if (startOffset >= minY) {
                            break;
                        }
                    }
                }
            }
        } else if (startOffset >= maxY) {
            if (0 == startIndex) {
                startOffset = 0;
            } else {
                --startIndex;
                int startBottomOffset = startOffset - interval;
                while (true) {
                    GalleryPageView page = getPageForIndex(pages, startIndex, false);
                    if (null == page) {
                        startOffset = maxY - 1;
                        break;
                    } else {
                        page.measure(widthSpec, heightSpec);
                        startOffset = startBottomOffset - page.getHeight();
                        if (startOffset < maxY) {
                            break;
                        } else if (0 == startIndex) {
                            startOffset = 0;
                            break;
                        } else {
                            --startIndex;
                            startBottomOffset = startOffset - interval;
                        }
                    }
                }
            }
        }

        // Put page to temp list
        tempPages.addAll(pages);
        pages.clear();

        // Sanitize offsetX
        int margin = pageWidth - width;
        if (margin >= 0) {
            mOffsetX = MathUtils.clamp(mOffsetX, -margin, 0);
        } else {
            mOffsetX = -margin / 2;
        }

        // Layout start page
        GalleryPageView page = getPageForIndex(tempPages, startIndex, true);
        if (null == page) {
            page = obtainPage();
            galleryView.addComponent(page);
            adapter.bind(page, startIndex);
        }
        pages.add(page);
        page.measure(widthSpec, heightSpec);
        page.layout(mOffsetX, startOffset, mOffsetX + pageWidth, startOffset + page.getMeasuredHeight());

        // Prepare for check up and down
        int bottomOffset = startOffset - interval;
        int topOffset = startOffset + page.getMeasuredHeight() + interval;

        // Check up
        int index = startIndex - 1;
        while (bottomOffset > minY && index >= 0) {
            page = getPageForIndex(tempPages, index, true);
            if (null == page) {
                page = obtainPage();
                galleryView.addComponent(page);
                adapter.bind(page, index);
            }
            pages.addFirst(page);
            page.measure(widthSpec, heightSpec);
            page.layout(mOffsetX, bottomOffset - page.getMeasuredHeight(), mOffsetX + pageWidth, bottomOffset);
            // Update
            bottomOffset -= page.getMeasuredHeight() + interval;
            index--;
        }

        // Avoid space in top
        page = pages.getFirst();
        if (0 == page.getIndex() && page.bounds().top > 0) {
            int offset = -page.bounds().top;
            for (GalleryPageView p: pages) {
                p.offsetTopAndBottom(offset);
            }
            topOffset += offset;
        }

        // Check down
        index = startIndex + 1;
        while (topOffset < maxY && index < size) {
            page = getPageForIndex(tempPages, index, true);
            if (null == page) {
                page = obtainPage();
                galleryView.addComponent(page);
                adapter.bind(page, index);
            }
            pages.addLast(page);
            page.measure(widthSpec, heightSpec);
            page.layout(mOffsetX, topOffset, mOffsetX + pageWidth, topOffset + page.getMeasuredHeight());
            // Update
            topOffset += page.getMeasuredHeight() + interval;
            index++;
        }

        // Avoid space in bottom
        if (size - 1 == pages.getLast().getIndex()) {
            while (true) {
                page = pages.getLast();
                int pagesBottom = page.bounds().bottom;
                if (pagesBottom >= height) {
                    break;
                }
                page = pages.getFirst();
                index = page.getIndex();
                if (0 == index) {
                    break;
                }
                --index;
                int pagesTop = page.bounds().top;

                page = getPageForIndex(tempPages, index, true);
                if (null == page) {
                    page = obtainPage();
                    galleryView.addComponent(page);
                    adapter.bind(page, index);
                }
                pages.addFirst(page);
                page.measure(widthSpec, heightSpec);

                int offset = Math.min(height - pagesBottom, page.getMeasuredHeight());
                for (GalleryPageView p: pages) {
                    p.offsetTopAndBottom(offset);
                }
                int bottom = pagesTop - interval + offset;
                page.layout(mOffsetX, bottom - page.getMeasuredHeight(), mOffsetX + pageWidth, bottom);
            }
        }

        // Remove remain page
        for (GalleryPageView p : tempPages) {
            removePage(p);
        }
        tempPages.clear();

        // Update state
        if (!pages.isEmpty()) {
            page = pages.getFirst();
            mIndex = page.getIndex();
            mOffsetY = page.bounds().top;
        }
    }

    @Override
    public void onFill() {
        GalleryView.Adapter adapter = mAdapter;
        GalleryView galleryView = mGalleryView;
        AssertUtils.assertNotNull("The PagerLayoutManager is not attached", adapter);

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
            LinkedList<GalleryPageView> pages = mPages;

            // Remove progress and error view
            removeProgress();
            removeErrorView();

            // Ensure index in range
            int index = mIndex;
            if (index < 0) {
                Log.e(TAG, "index < 0, index = " + index);
                index = 0;
                mIndex = index;
                removeAllPages();
            } else if (index >= size) {
                Log.e(TAG, "index >= size, index = " + index + ", size = " + size);
                index = size - 1;
                mIndex = index;
                removeAllPages();
            }

            // Find keep index and keep top
            int keepTop = INVALID_TOP;
            int keepTopIndex;
            if (GalleryPageView.INVALID_INDEX != mKeepTopPageIndex) {
                keepTopIndex = mKeepTopPageIndex;
                keepTop = mKeepTop;
            } else if (GalleryPageView.INVALID_INDEX != mFirstShownPageIndex) {
                keepTopIndex = mFirstShownPageIndex;
            } else {
                keepTopIndex = GalleryPageView.INVALID_INDEX;
            }
            if (GalleryPageView.INVALID_INDEX != keepTopIndex && INVALID_TOP == keepTop) {
                keepTop = mOffsetY;
                for (GalleryPageView page : pages) {
                    // Check keep page
                    if (keepTopIndex == page.getIndex()) {
                        break;
                    }
                    keepTop += page.getHeight() + mInterval;
                }
            }

            int startIndex;
            int startOffset;
            if (GalleryPageView.INVALID_INDEX != keepTopIndex) {
                startIndex = keepTopIndex;
                startOffset = keepTop;
            } else {
                startIndex = mIndex;
                startOffset = mOffsetY;
            }
            fillPages(startIndex, startOffset);

            // Get first shown image
            mFirstShownPageIndex = GalleryPageView.INVALID_INDEX;
            for (GalleryPageView page : mPages) {
                // Check first shown loaded page
                if ((mScrollUp || mFlingUp) && !page.isLoaded()) {
                    continue;
                }

                if (isInScreen(page)) {
                    mFirstShownPageIndex = page.getIndex();
                    mKeepTopPageIndex = page.getIndex();
                    break;
                }
            }
        }
    }

    @Override
    public void onDown() {
        mDeltaX = 0;
        mDeltaY = 0;
        mScrollUp = false;
        mStopAnimationFinger = cancelAllAnimations();
    }

    @Override
    public void onUp() {
        mScrollUp = false;
        mGalleryView.getEdgeView().onRelease();
    }

    @Override
    public void onDoubleTapConfirmed(float x, float y) {
        if (mPages.size() <= 0) {
            return;
        }

        float startScale = mScale;
        float endScale;
        if (startScale < MAX_SCALE - SCALE_ERROR) {
            endScale = MAX_SCALE;
        } else {
            endScale = MIN_SCALE;
        }

        mSmoothScaler.startSmoothScaler(x, y, startScale, endScale, 300);
    }

    @Override
    public void onLongPress(float x, float y) {

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

    private void getBottomState() {
        List<GalleryPageView> pages = mPages;
        int bottom = mOffsetY;
        int i = 0;
        for (GalleryPageView page : pages) {
            if (i != 0) {
                bottom += mInterval;
            }
            bottom += page.getHeight();
            i++;
        }
        boolean hasNext = mIndex + pages.size() < mAdapter.size();

        mBottomStateBottom = bottom;
        mBottomStateHasNext = hasNext;
    }

    // True for get top or bottom
    private boolean scrollInternal(float dx, float dy, boolean fling, float x, float y) {
        if (mPages.size() <= 0) {
            return false;
        }

        GalleryView galleryView = mGalleryView;
        int width = galleryView.getWidth();
        int height = galleryView.getHeight();
        int pageWidth = (int) (width * mScale);
        final float reservation = getReservation();
        boolean requestFill = false;
        boolean result = false;

        int margin = pageWidth - width;
        int dxInt = (int) dx;
        if (margin > 0 && 0 != dxInt) {
            int oldOffsetX = mOffsetX;
            int exceptOffsetX = oldOffsetX - dxInt;
            mOffsetX = MathUtils.clamp(exceptOffsetX, -margin, 0);
            if (mOffsetX != oldOffsetX) {
                requestFill = true;
            }
            // Do not show over scroll effect for left and right
            /*
            int extraOffsetX = mOffsetX - exceptOffsetX;
            if (0 != extraOffsetX) {
                overScrollEdge(extraOffsetX, 0, x, y);
            }
            */
        }

        int remainY = (int) dy;
        while (remainY != 0) {
            if (remainY < 0) { // Try to show top
                int limit;
                if (mIndex > 0) {
                    limit = (int) (-height * reservation) + mInterval;
                } else {
                    limit = 0;
                }

                if (mOffsetY - remainY <= limit) {
                    mOffsetY -= remainY;
                    remainY = 0;
                    requestFill = true;
                    mDeltaX = 0;
                    mDeltaY = 0;
                } else {
                    if (mIndex > 0) {
                        mOffsetY = limit;
                        remainY = remainY + limit - mOffsetY;
                        // Offset one pixel to avoid infinite loop
                        ++mOffsetY;
                        ++remainY;
                        galleryView.forceFill();
                        requestFill = false;
                        mDeltaX = 0;
                        mDeltaY = 0;
                    } else {
                        if (mOffsetY != limit) {
                            mOffsetY = limit;
                            requestFill = true;
                        }
                        if (!fling) {
                            overScrollEdge(0, remainY + limit - mOffsetY, x, y);
                        }
                        remainY = 0;
                        result = true;
                    }
                }
            } else { // Try to show bottom
                getBottomState();
                int bottom = mBottomStateBottom;
                boolean hasNext = mBottomStateHasNext;

                int limit;
                if (hasNext) {
                    limit = (int) (height * (1 + reservation)) - mInterval;
                } else {
                    limit = height;
                }
                // Fix limit for page not fill screen
                limit = Math.min(bottom, limit);

                if (bottom - remainY >= limit) {
                    mOffsetY -= remainY;
                    remainY = 0;
                    requestFill = true;
                    mDeltaX = 0;
                    mDeltaY = 0;
                } else {
                    if (hasNext) {
                        mOffsetY -= bottom - limit;
                        remainY = remainY + limit - bottom;
                        // Offset one pixel to avoid infinite loop
                        --mOffsetY;
                        --remainY;
                        galleryView.forceFill();
                        requestFill = false;
                        mDeltaX = 0;
                        mDeltaY = 0;
                    } else {
                        if (mOffsetY != limit) {
                            mOffsetY -= bottom - limit;
                            requestFill = true;
                        }
                        if (!fling) {
                            overScrollEdge(0, remainY + limit - bottom, x, y);
                        }
                        remainY = 0;
                        result = true;
                    }
                }
            }
        }

        if (requestFill) {
            mGalleryView.requestFill();
        }

        return result;
    }

    @Override
    public void onScroll(float dx, float dy, float totalX, float totalY, float x, float y) {
        mKeepTopPageIndex = GalleryPageView.INVALID_INDEX;
        mKeepTop = INVALID_TOP;
        mScrollUp = dy < 0;
        scrollInternal(dx, dy, false, x, y);
    }

    @Override
    public void onFling(float velocityX, float velocityY) {
        if (mPages.size() <= 0) {
            return;
        }

        mKeepTopPageIndex = GalleryPageView.INVALID_INDEX;
        mKeepTop = INVALID_TOP;
        mFlingUp = velocityY > 0;

        int maxX;
        int minX;
        int width = mGalleryView.getWidth();
        int pageWidth = (int) (width * mScale);
        int margin = pageWidth - width;
        if (margin > 0) {
            maxX = -mOffsetX;
            minX = -margin + mOffsetX;
        } else {
            maxX = 0;
            minX = 0;
        }

        int maxY;
        if (mIndex > 0) {
            maxY = Integer.MAX_VALUE;
        } else {
            maxY = -mOffsetY;
        }

        getBottomState();
        int bottom = mBottomStateBottom;
        boolean hasNext = mBottomStateHasNext;
        int minY;
        if (hasNext) {
            minY = Integer.MIN_VALUE;
        } else {
            minY = mGalleryView.getHeight() - bottom;
        }

        mPageFling.startFling((int) velocityX, minX, maxX,
                (int) velocityY, minY, maxY);
    }

    @Override
    public boolean canScale() {
        return mPages.size() > 0;
    }

    @Override
    public void onScale(float focusX, float focusY, float scale) {
        if (mPages.isEmpty()) {
            return;
        }

        float oldScale = mScale;
        mScale = MathUtils.clamp(oldScale * scale, MIN_SCALE, MAX_SCALE);
        scale = mScale / oldScale;

        if (oldScale != mScale) {
            GalleryPageView page = null;
            // Keep scale page origin position
            for (GalleryPageView p : mPages) {
                if (p.bounds().top < focusY) {
                    page = p;
                } else {
                    break;
                }
            }

            if (null != page) {
                mKeepTopPageIndex = page.getIndex();
                mKeepTop = page.bounds().top;

                mGalleryView.forceFill();
                int oldKeepTop = mKeepTop;
                mKeepTop = INVALID_TOP;

                // Apply scroll
                int newOffsetX = (int) (focusX - ((focusX - mOffsetX) * scale));
                int newKeepTop;
                if (page.isLoaded()) {
                    newKeepTop = (int) (focusY - ((focusY - oldKeepTop) * scale));
                } else {
                    newKeepTop = oldKeepTop;
                }
                scrollInternal(mOffsetX - newOffsetX, oldKeepTop - newKeepTop, false, focusX, focusY);
            } else {
                Log.e(TAG, "Can't find target page");
                mKeepTopPageIndex = GalleryPageView.INVALID_INDEX;
                mKeepTop = INVALID_TOP;
                mGalleryView.forceFill();
            }
        }
    }

    @Override
    public boolean onUpdateAnimation(long time) {
        boolean invalidate = mPageFling.calculate(time);
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
        if (size <= 0 || mPages.isEmpty()) {
            return;
        }

        ///////
        // UP
        ///////
        GalleryView galleryView = mGalleryView;
        if (mIndex == 0 && mOffsetY >= 0) {
            mOverScroller.overScroll(GLEdgeView.TOP);
            mGalleryView.onTransferEnd();
        } else {
            // Cancel all animations
            cancelAllAnimations();

            // Get first shown page
            GalleryPageView previousPage = null;
            GalleryPageView firstShownPage = null;
            for (GalleryPageView p: mPages) {
                if (isInScreen(p)) {
                    firstShownPage = p;
                    break;
                }
                previousPage = p;
            }

            int height = galleryView.getHeight();
            int maxOffset = height - mInterval;
            if (null == firstShownPage) {
                Log.e(TAG, "Can't find first shown page when paging left");
                mOffsetY += height / 2;
            } else {
                int firstShownTop = firstShownPage.bounds().top;
                if (firstShownTop >= 0) {
                    if (null == previousPage) {
                        Log.e(TAG, "Can't find previous page when paging left and offsetY == 0");
                        mOffsetY += height / 2;
                    } else {
                        mOffsetY += Math.min(maxOffset, -previousPage.bounds().top);
                    }
                } else {
                    mOffsetY += Math.min(maxOffset, -firstShownTop);
                }
            }

            // Request fill
            mGalleryView.requestFill();
        }
    }

    @Override
    public void onPageRight() {
        int size = mAdapter.size();
        if (size <= 0 || mPages.isEmpty()) {
            return;
        }

        /////////
        // DOWN
        /////////
        GalleryView galleryView = mGalleryView;
        getBottomState();
        int bottom = mBottomStateBottom;
        boolean hasNext = mBottomStateHasNext;
        if (!hasNext && bottom <= galleryView.getHeight()) {
            mOverScroller.overScroll(GLEdgeView.BOTTOM);
            mGalleryView.onTransferEnd();
        } else {
            // Cancel all animations
            cancelAllAnimations();

            // Get first shown page
            GalleryPageView lastShownPage = null;
            GalleryPageView nextPage = null;
            for (GalleryPageView p: mPages) {
                if (isInScreen(p)) {
                    lastShownPage = p;
                } else if (null != lastShownPage) {
                    nextPage = p;
                    break;
                }
            }

            int height = galleryView.getHeight();
            int maxOffset = height - mInterval;
            if (null == lastShownPage) {
                Log.e(TAG, "Can't find last shown page when paging left");
                mOffsetY -= height / 2;
            } else {
                int lastShownBottom = lastShownPage.bounds().bottom;
                if (lastShownBottom <= height) {
                    if (null == nextPage) {
                        Log.e(TAG, "Can't find previous page when paging left and offsetY == 0");
                        mOffsetY -= height / 2;
                    } else {
                        mOffsetY -= Math.min(maxOffset, nextPage.bounds().bottom - height);
                    }
                } else {
                    mOffsetY -= Math.min(maxOffset, lastShownBottom - height);
                }
            }

            // Request fill
            mGalleryView.requestFill();
        }
    }

    @Override
    public boolean isTapOrPressEnable() {
        return !mStopAnimationFinger;
    }

    @Override
    public GalleryPageView findPageByIndex(int index) {
        for (GalleryPageView page : mPages) {
            if (page.getIndex() == index) {
                return page;
            }
        }
        return null;
    }

    @Override
    public int getCurrentIndex() {
        for (GalleryPageView page : mPages) {
            if (isInScreen(page)) {
                return page.getIndex();
            }
        }
        return GalleryPageView.INVALID_INDEX;
    }

    @Override
    public void setCurrentIndex(int index) {
        int size = mAdapter.size();
        if (size <= 0) {
            // Can't get size now, assume size is MAX
            size = Integer.MAX_VALUE;
        }
        if (index < 0 || index >= size) {
            return;
        }

        mKeepTopPageIndex = index;
        mKeepTop = INVALID_TOP;

        if (mPages.isEmpty()) {
            mIndex = index;
        } else {
            // Fix the index page
            GalleryPageView targetPage = null;
            for (GalleryPageView page : mPages) {
                if (page.getIndex() == index) {
                    targetPage =page;
                    break;
                }
            }

            if (targetPage != null) {
                // Cancel all animations
                cancelAllAnimations();
                mOffsetY -= targetPage.bounds().top;
                // Request fill
                mGalleryView.requestFill();
            } else {
                mIndex = index;
                mOffsetY = 0;
                // Cancel all animations
                cancelAllAnimations();
                // Remove all view
                removeProgress();
                removeErrorView();
                removeAllPages();
                // Request fill
                mGalleryView.requestFill();
            }
        }
    }

    @Override
    public int getIndexUnder(float x, float y) {
        if (mPages.isEmpty()) {
            return GalleryPageView.INVALID_INDEX;
        } else {
            int intX = (int) x;
            int intY = (int) y;
            for (GalleryPageView page : mPages) {
                if (page.bounds().contains(intX, intY)) {
                    return page.getIndex();
                }
            }
            return GalleryPageView.INVALID_INDEX;
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

    private class PageFling extends Fling {

        private int mVelocityX;
        private int mVelocityY;
        private int mDx;
        private int mDy;
        private int mLastX;
        private int mLastY;

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
            if (scrollInternal(-offsetX, -offsetY, true, 0, 0)) {
                cancel();
                onFinish();
            }
            mLastX = x;
            mLastY = y;
        }

        @Override
        protected void onFinish() {
            mFlingUp = false;

            int index = mIndex;

            boolean topEdge = index <= 0 && mOffsetY >= 0;

            getBottomState();
            int bottom = mBottomStateBottom;
            boolean hasNext = mBottomStateHasNext;
            boolean bottomEdge = !hasNext && bottom <= mGalleryView.getHeight();

            if (topEdge && bottomEdge) {
                return;
            }

            GLEdgeView edgeView = mGalleryView.getEdgeView();
            if (topEdge && edgeView.isFinished(GLEdgeView.TOP)) {
                edgeView.onAbsorb(mVelocityY, GLEdgeView.TOP);
            } else if (bottomEdge && edgeView.isFinished(GLEdgeView.BOTTOM)) {
                edgeView.onAbsorb(-mVelocityY, GLEdgeView.BOTTOM);
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
            if (mPages.size() <= 0) {
                return;
            }

            float scale = MathUtils.lerp(mStartScale, mEndScale, progress);
            onScale(mFocusX, mFocusY, scale / mLastScale);
            mLastScale = scale;
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
