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

package com.hippo.ehviewer.ui.scene.gallery.detail;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

import androidx.annotation.Nullable;

/**
 * Observes touch events without taking them away from vertical scrolling and child views.
 * A left swipe is reported only after the finger is lifted and the full distance threshold
 * has been reached.
 */
public class GalleryDetailScrollView extends ScrollView {

    private static final float MIN_DISTANCE_FRACTION = 0.25f;
    private static final float HORIZONTAL_BIAS = 1.5f;

    private final int mTouchSlop;

    @Nullable
    private OnSwipeLeftListener mOnSwipeLeftListener;
    @Nullable
    private View mSwipeExclusionView;
    private int mActivePointerId = MotionEvent.INVALID_POINTER_ID;
    private float mDownX;
    private float mDownY;
    private boolean mTrackingSwipe;
    private boolean mHadMultiplePointers;
    private boolean mSwipeReady;
    private boolean mSwipeReadyChanged;

    public GalleryDetailScrollView(Context context) {
        this(context, null);
    }

    public GalleryDetailScrollView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GalleryDetailScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public void setOnSwipeLeftListener(@Nullable OnSwipeLeftListener listener) {
        mOnSwipeLeftListener = listener;
    }

    public void setSwipeExclusionView(@Nullable View view) {
        mSwipeExclusionView = view;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean completedSwipe = trackSwipe(event);
        boolean swipeReadyChanged = mSwipeReadyChanged;
        boolean swipeReady = mSwipeReady;
        boolean handled = super.dispatchTouchEvent(event);
        if (mOnSwipeLeftListener != null) {
            if (swipeReadyChanged) {
                mOnSwipeLeftListener.onSwipeLeftReadyChanged(swipeReady);
            }
            if (completedSwipe) {
                mOnSwipeLeftListener.onSwipeLeft();
            }
        }
        return handled;
    }

    private boolean trackSwipe(MotionEvent event) {
        mSwipeReadyChanged = false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = event.getPointerId(0);
                mDownX = event.getX(0);
                mDownY = event.getY(0);
                mTrackingSwipe = !isPointInsideExclusionView(mDownX, mDownY);
                mHadMultiplePointers = false;
                setSwipeReady(false);
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                mHadMultiplePointers = true;
                setSwipeReady(false);
                break;
            case MotionEvent.ACTION_MOVE:
                updateSwipeReady(event);
                break;
            case MotionEvent.ACTION_UP:
                updateSwipeReady(event);
                boolean completed = mSwipeReady;
                if (completed) {
                    // Completion owns the indicator fade-out, so don't emit a separate retreat.
                    mSwipeReady = false;
                    mSwipeReadyChanged = false;
                } else {
                    setSwipeReady(false);
                }
                resetSwipeTracking();
                return completed;
            case MotionEvent.ACTION_CANCEL:
                setSwipeReady(false);
                resetSwipeTracking();
                break;
            default:
                break;
        }
        return false;
    }

    private void updateSwipeReady(MotionEvent event) {
        int pointerIndex = event.findPointerIndex(mActivePointerId);
        if (!mTrackingSwipe || mHadMultiplePointers || pointerIndex < 0) {
            setSwipeReady(false);
            return;
        }

        float distanceX = event.getX(pointerIndex) - mDownX;
        float distanceY = event.getY(pointerIndex) - mDownY;
        float requiredDistance = Math.max(
                getWidth() * MIN_DISTANCE_FRACTION, mTouchSlop * 8f);
        setSwipeReady(distanceX <= -requiredDistance
                && Math.abs(distanceX) > Math.abs(distanceY) * HORIZONTAL_BIAS);
    }

    private void setSwipeReady(boolean ready) {
        if (mSwipeReady != ready) {
            mSwipeReady = ready;
            mSwipeReadyChanged = true;
        }
    }

    private boolean isPointInsideExclusionView(float x, float y) {
        if (mSwipeExclusionView == null || !mSwipeExclusionView.isShown()) {
            return false;
        }

        int[] scrollLocation = new int[2];
        int[] exclusionLocation = new int[2];
        getLocationOnScreen(scrollLocation);
        mSwipeExclusionView.getLocationOnScreen(exclusionLocation);
        float screenX = scrollLocation[0] + x;
        float screenY = scrollLocation[1] + y;
        return screenX >= exclusionLocation[0]
                && screenX < exclusionLocation[0] + mSwipeExclusionView.getWidth()
                && screenY >= exclusionLocation[1]
                && screenY < exclusionLocation[1] + mSwipeExclusionView.getHeight();
    }

    private void resetSwipeTracking() {
        mActivePointerId = MotionEvent.INVALID_POINTER_ID;
        mTrackingSwipe = false;
        mHadMultiplePointers = false;
    }

    public interface OnSwipeLeftListener {
        void onSwipeLeftReadyChanged(boolean ready);

        void onSwipeLeft();
    }
}
