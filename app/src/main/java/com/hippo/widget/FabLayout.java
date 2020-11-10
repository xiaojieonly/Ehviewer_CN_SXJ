/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.widget;

import android.animation.Animator;
import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.AssertUtils;
import com.hippo.yorozuya.SimpleAnimatorListener;

public class FabLayout extends ViewGroup implements View.OnClickListener {

    private static final long ANIMATE_TIME = 300L;

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_AUTO_CANCEL = "auto_cancel";
    private static final String STATE_KEY_EXPANDED = "expanded";

    private int mFabSize;
    private int mFabMiniSize;
    private int mIntervalPrimary;
    private int mIntervalSecondary;

    private boolean mExpanded = true;
    private boolean mAutoCancel = true;
    private boolean mHidePrimaryFab = false;
    private float mMainFabCenterY = -1f;

    private OnExpandListener mOnExpandListener;
    private OnClickFabListener mOnClickFabListener;

    public FabLayout(Context context) {
        super(context);
        init(context);
    }

    public FabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setSoundEffectsEnabled(false);
        setClipToPadding(false);
        mFabSize = context.getResources().getDimensionPixelOffset(R.dimen.fab_size);
        mFabMiniSize = context.getResources().getDimensionPixelOffset(R.dimen.fab_min_size);
        mIntervalPrimary = context.getResources().getDimensionPixelOffset(R.dimen.fab_layout_primary_margin);
        mIntervalSecondary = context.getResources().getDimensionPixelOffset(R.dimen.fab_layout_secondary_margin);
    }

    @Override
    public void addView(@NonNull View child, int index, ViewGroup.LayoutParams params) {
        if (!(child instanceof FloatingActionButton)) {
            throw new IllegalStateException("FloatingActionBarLayout should only " +
                    "contain FloatingActionButton, but try to add "+ child.getClass().getName());
        }
        super.addView(child, index, params);
    }

    public FloatingActionButton getPrimaryFab() {
        View v = getChildAt(getChildCount() - 1);
        if (v == null) {
            return null;
        } else {
            return (FloatingActionButton) v;
        }
    }

    public int getSecondaryFabCount() {
        return Math.max(0, getChildCount() - 1);
    }

    public FloatingActionButton getSecondaryFabAt(int index) {
        if (index < 0 || index >= getSecondaryFabCount()) {
            return null;
        }
        return (FloatingActionButton) getChildAt(index);
    }

    public void setSecondaryFabVisibilityAt(int index, boolean visible) {
        View fab = getSecondaryFabAt(index);
        if (fab != null) {
            if (visible && fab.getVisibility() == View.GONE) {
                fab.animate().cancel();
                fab.setVisibility(mExpanded ? View.VISIBLE : View.INVISIBLE);
            } else if (!visible && fab.getVisibility() != View.GONE) {
                fab.animate().cancel();
                fab.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        AssertUtils.assertEquals("Measure mode must be MeasureSpec.EXACTLY",
                MeasureSpec.getMode(widthMeasureSpec), MeasureSpec.EXACTLY);
        AssertUtils.assertEquals("Measure mode must be MeasureSpec.EXACTLY",
                MeasureSpec.getMode(heightMeasureSpec), MeasureSpec.EXACTLY);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);

        int childWidthMeasureSpec = MeasureSpec.makeMeasureSpec(
                width - getPaddingLeft() - getPaddingRight(), MeasureSpec.AT_MOST);
        int childHeightMeasureSpec = MeasureSpec.makeMeasureSpec(
                height - getPaddingTop() - getPaddingBottom(), MeasureSpec.AT_MOST);
        measureChildren(childWidthMeasureSpec, childHeightMeasureSpec);

        setMeasuredDimension(width, height);
    }

    // For pre-L, FloatActionButton use padding to show shadow, so its position looks wrong.
    // We use it default size to make it position right
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int centerX = 0;
        int bottom = getMeasuredHeight() - getPaddingBottom();
        int count = getChildCount();
        int i = count;
        while(--i >= 0) {
            View child = getChildAt(i);
            if (child.getVisibility() == View.GONE) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int layoutBottom;
            int layoutRight;
            if (i == count - 1) {
                layoutBottom = bottom + ((childHeight - mFabSize) / 2);
                layoutRight = getMeasuredWidth() - getPaddingRight() + ((childWidth - mFabSize) / 2);
                bottom -= mFabSize + mIntervalPrimary;
                centerX = layoutRight - (childWidth / 2);
                mMainFabCenterY = layoutBottom - (childHeight / 2f);
            } else {
                layoutBottom = bottom + ((childHeight - mFabMiniSize) / 2);
                layoutRight = centerX + (childWidth / 2);
                bottom -= mFabMiniSize + mIntervalSecondary;
            }
            child.layout(layoutRight - childWidth, layoutBottom - childHeight, layoutRight, layoutBottom);
        }
    }

    public void setOnExpandListener(OnExpandListener listener) {
        mOnExpandListener = listener;
    }

    public void setOnClickFabListener(OnClickFabListener listener) {
        mOnClickFabListener = listener;
        if (listener != null) {
            for (int i = 0, n = getChildCount(); i < n; i++) {
                getChildAt(i).setOnClickListener(this);
            }
        } else {
            for (int i = 0, n = getChildCount(); i < n; i++) {
                getChildAt(i).setClickable(false);
            }
        }
    }

    public void setHidePrimaryFab(boolean hidePrimaryFab) {
        if (mHidePrimaryFab != hidePrimaryFab) {
            mHidePrimaryFab = hidePrimaryFab;
            boolean expanded = mExpanded;
            int count = getChildCount();
            if (!expanded && count > 0) {
                getChildAt(count - 1).setVisibility(hidePrimaryFab ? INVISIBLE : VISIBLE);
            }
        }
    }

    public void setAutoCancel(boolean autoCancel) {
        if (mAutoCancel != autoCancel) {
            mAutoCancel = autoCancel;

            if (mExpanded) {
                if (autoCancel) {
                    setOnClickListener(this);
                } else {
                    setClickable(false);
                }
            }
        }
    }

    public void toggle() {
        setExpanded(!mExpanded);
    }

    public boolean isExpanded() {
        return mExpanded;
    }

    public void setExpanded(boolean expanded) {
        setExpanded(expanded, true);
    }

    public void setExpanded(boolean expanded, boolean animation) {
        if (mExpanded != expanded) {
            mExpanded = expanded;

            if (mAutoCancel) {
                if (expanded) {
                    setOnClickListener(this);
                } else {
                    setClickable(false);
                }
            }

            final int count = getChildCount();
            if (count > 0) {
                if (mMainFabCenterY == -1f || !animation) {
                    // It is before first onLayout
                    int checkCount = mHidePrimaryFab ? count : count - 1;
                    for (int i = 0; i < checkCount; i++) {
                        View child = getChildAt(i);
                        if (child.getVisibility() == GONE) {
                            continue;
                        }
                        child.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
                        if (expanded) {
                            child.setAlpha(1f);
                        }
                    }
                } else {
                    if (mHidePrimaryFab) {
                        setPrimaryFabAnimation(getChildAt(count - 1), expanded, !expanded);
                    }

                    for (int i = 0; i < count - 1; i++) {
                        View child = getChildAt(i);
                        if (child.getVisibility() == GONE) {
                            continue;
                        }
                        setSecondaryFabAnimation(child, expanded, expanded);
                    }
                }
            }

            if (mOnExpandListener != null) {
                mOnExpandListener.onExpand(expanded);
            }
        }
    }


    private void setPrimaryFabAnimation(final View child, final boolean expanded, boolean delay) {
        float startRotation;
        float endRotation;
        float startScale;
        float endScale;
        Interpolator interpolator;
        if (expanded) {
            startRotation = -45.0f;
            endRotation = 0.0f;
            startScale = 0.0f;
            endScale = 1.0f;
            interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR;
        } else {
            startRotation = 0.0f;
            endRotation = 0.0f;
            startScale  = 1.0f;
            endScale = 0.0f;
            interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR;
        }

        child.setScaleX(startScale);
        child.setScaleY(startScale);
        child.setRotation(startRotation);
        child.animate()
                .scaleX(endScale)
                .scaleY(endScale)
                .rotation(endRotation)
                .setStartDelay(delay ? ANIMATE_TIME : 0L)
                .setDuration(ANIMATE_TIME)
                .setInterpolator(interpolator)
                .setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (expanded) {
                            child.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!expanded) {
                            child.setVisibility(View.INVISIBLE);
                        }
                    }
                }).start();
    }

    private void setSecondaryFabAnimation(final View child, final boolean expanded, boolean delay) {
        float startTranslationY;
        float endTranslationY;
        float startAlpha;
        float endAlpha;
        Interpolator interpolator;
        if (expanded) {
            startTranslationY = mMainFabCenterY -
                    (child.getTop() + (child.getHeight() / 2));
            endTranslationY = 0f;
            startAlpha = 0f;
            endAlpha = 1f;
            interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR;
        } else {
            startTranslationY = 0f;
            endTranslationY = mMainFabCenterY -
                    (child.getTop() + (child.getHeight() / 2));
            startAlpha = 1f;
            endAlpha = 0f;
            interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR;
        }

        child.setAlpha(startAlpha);
        child.setTranslationY(startTranslationY);
        child.animate()
                .alpha(endAlpha)
                .translationY(endTranslationY)
                .setStartDelay(delay ? ANIMATE_TIME : 0L)
                .setDuration(ANIMATE_TIME)
                .setInterpolator(interpolator)
                .setListener(new SimpleAnimatorListener() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (expanded) {
                            child.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!expanded) {
                            child.setVisibility(View.INVISIBLE);
                        }
                    }
                }).start();
    }

    @Override
    public void onClick(View v) {
        if (this == v) {
            setExpanded(false);
        } else if (mOnClickFabListener != null) {
            int position = indexOfChild(v);
            if (position == getChildCount() - 1) {
                mOnClickFabListener.onClickPrimaryFab(this, (FloatingActionButton) v);
            } else if (position >= 0 && mExpanded) {
                mOnClickFabListener.onClickSecondaryFab(this, (FloatingActionButton) v, position);
            }
        }
    }

    @Override
    protected void dispatchSetPressed(boolean pressed) {
        // Don't dispatch it to children
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putBoolean(STATE_KEY_AUTO_CANCEL, mAutoCancel);
        state.putBoolean(STATE_KEY_EXPANDED, mExpanded);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setAutoCancel(savedState.getBoolean(STATE_KEY_AUTO_CANCEL));
            setExpanded(savedState.getBoolean(STATE_KEY_EXPANDED), false);
        }
    }

    public interface OnExpandListener {
        void onExpand(boolean expanded);
    }

    public interface OnClickFabListener {

        void onClickPrimaryFab(FabLayout view, FloatingActionButton fab);

        void onClickSecondaryFab(FabLayout view, FloatingActionButton fab, int position);
    }
}
