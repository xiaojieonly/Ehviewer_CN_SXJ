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
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.hippo.android.resource.AttrResources;
import com.hippo.ehviewer.R;
import com.hippo.lib.yorozuya.AnimationUtils;
import com.hippo.lib.yorozuya.AssertUtils;
import com.hippo.lib.yorozuya.SimpleAnimatorListener;
import java.util.LinkedList;
import java.util.List;

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

    private static final String TAG_FAB_LABEL = "FabFunctionNameLabel";

    private boolean mShowFabFunctionName = false;
    private int mFabFunctionNamePaddingHorizontal;
    private int mFabFunctionNamePaddingVertical;
    private int mFabFunctionNameMargin;
    private float mFabFunctionNameCornerRadius;
    private final List<TextView> mFabFunctionNameViews = new LinkedList<>();

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
        mFabFunctionNamePaddingHorizontal = context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_horizontal);
        mFabFunctionNamePaddingVertical = context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_padding_vertical);
        mFabFunctionNameMargin = context.getResources().getDimensionPixelOffset(R.dimen.fab_function_name_margin);
        mFabFunctionNameCornerRadius = context.getResources().getDimension(R.dimen.fab_function_name_corner_radius);
    }

    @Override
    public void addView(@NonNull View child, int index, LayoutParams params) {
        if (child instanceof FloatingActionButton) {
            super.addView(child, index, params);
            return;
        }
        if (child instanceof TextView && TAG_FAB_LABEL.equals(child.getTag())) {
            super.addView(child, index, params);
            return;
        }
        throw new IllegalStateException("FloatingActionBarLayout should only " +
                "contain FloatingActionButton or internal label view, but try to add " + child.getClass().getName());
    }

    private boolean isFabLabel(@NonNull View child) {
        return TAG_FAB_LABEL.equals(child.getTag());
    }

    private int getFabChildCount() {
        int count = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (child instanceof FloatingActionButton) {
                count++;
            }
        }
        return count;
    }

    private int getFabIndex(@NonNull View target) {
        int index = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (!(child instanceof FloatingActionButton)) {
                continue;
            }
            if (child == target) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public FloatingActionButton getPrimaryFab() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof FloatingActionButton) {
                return (FloatingActionButton) child;
            }
        }
        return null;
    }

    public int getSecondaryFabCount() {
        return Math.max(0, getFabChildCount() - 1);
    }

    public FloatingActionButton getSecondaryFabAt(int index) {
        if (index < 0 || index >= getSecondaryFabCount()) {
            return null;
        }
        int fabIndex = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (!(child instanceof FloatingActionButton)) {
                continue;
            }
            if (fabIndex == index) {
                return (FloatingActionButton) child;
            }
            fabIndex++;
        }
        return null;
    }

    private FloatingActionButton getFabChildAt(int index) {
        if (index < 0 || index >= getFabChildCount()) {
            return null;
        }
        int fabIndex = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (!(child instanceof FloatingActionButton)) {
                continue;
            }
            if (fabIndex == index) {
                return (FloatingActionButton) child;
            }
            fabIndex++;
        }
        return null;
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
            if (child.getVisibility() == View.GONE || isFabLabel(child)) {
                continue;
            }

            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            int layoutBottom;
            int layoutRight;
            if (getFabIndex(child) == getFabChildCount() - 1) {
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
        layoutFabFunctionNameViews();
    }

    public void setOnExpandListener(OnExpandListener listener) {
        mOnExpandListener = listener;
    }

    public void setOnClickFabListener(OnClickFabListener listener) {
        mOnClickFabListener = listener;
        if (listener != null) {
            for (int i = 0, n = getChildCount(); i < n; i++) {
                View child = getChildAt(i);
                if (child instanceof FloatingActionButton) {
                    child.setOnClickListener(this);
                }
            }
        } else {
            for (int i = 0, n = getChildCount(); i < n; i++) {
                View child = getChildAt(i);
                if (child instanceof FloatingActionButton) {
                    child.setClickable(false);
                }
            }
        }
    }

    public void setShowFabFunctionName(boolean show) {
        if (mShowFabFunctionName != show) {
            mShowFabFunctionName = show;
            updateFabFunctionNameViews();
            requestLayout();
            invalidate();
        }
    }

    public boolean isShowFabFunctionName() {
        return mShowFabFunctionName;
    }

    private TextView getFabFunctionNameView(int index) {
        return index >= 0 && index < mFabFunctionNameViews.size() ? mFabFunctionNameViews.get(index) : null;
    }

    private TextView ensureFabFunctionNameView(int index) {
        while (mFabFunctionNameViews.size() <= index) {
            TextView label = createFabFunctionNameView(getContext());
            label.setTag(TAG_FAB_LABEL);
            mFabFunctionNameViews.add(label);
            super.addView(label, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        }
        return mFabFunctionNameViews.get(index);
    }

    private TextView createFabFunctionNameView(Context context) {
        TextView label = new TextView(context);
        label.setTag(TAG_FAB_LABEL);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setPadding(mFabFunctionNamePaddingHorizontal,
                mFabFunctionNamePaddingVertical,
                mFabFunctionNamePaddingHorizontal,
                mFabFunctionNamePaddingVertical);
        label.setClickable(false);
        label.setFocusable(false);
        return label;
    }

    private void updateFabFunctionNameViews() {
        int fabIndex = 0;
        int totalFabCount = getFabChildCount();
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (!(child instanceof FloatingActionButton)) {
                continue;
            }
            FloatingActionButton fab = (FloatingActionButton) child;
            TextView label = ensureFabFunctionNameView(fabIndex);
            boolean isPrimaryFab = fabIndex == totalFabCount - 1 && totalFabCount > 1;
            if (!mShowFabFunctionName || isPrimaryFab || fab.getVisibility() != View.VISIBLE || TextUtils.isEmpty(fab.getContentDescription())) {
                label.setVisibility(View.GONE);
            } else {
                label.setText(fab.getContentDescription());
                updateFabFunctionNameAppearance(label);
                label.setVisibility(View.VISIBLE);
            }
            fabIndex++;
        }
        for (int i = fabIndex; i < mFabFunctionNameViews.size(); i++) {
            mFabFunctionNameViews.get(i).setVisibility(View.GONE);
        }
    }

    private void layoutFabFunctionNameViews() {
        if (!mShowFabFunctionName) {
            for (TextView label : mFabFunctionNameViews) {
                label.setVisibility(View.GONE);
            }
            return;
        }

        int fabIndex = 0;
        for (int i = 0, n = getChildCount(); i < n; i++) {
            View child = getChildAt(i);
            if (!(child instanceof FloatingActionButton)) {
                continue;
            }
            FloatingActionButton fab = (FloatingActionButton) child;
            TextView label = ensureFabFunctionNameView(fabIndex);
            if (label.getVisibility() != View.VISIBLE) {
                fabIndex++;
                continue;
            }
            int labelWidth = label.getMeasuredWidth();
            int labelHeight = label.getMeasuredHeight();
            int left = fab.getLeft() - mFabFunctionNameMargin - labelWidth;
            int top = fab.getTop() + (fab.getMeasuredHeight() - labelHeight) / 2;
            label.layout(left, top, left + labelWidth, top + labelHeight);
            fabIndex++;
        }
    }

    private void updateFabFunctionNameAppearance(TextView label) {
        if (label == null) {
            return;
        }
        boolean isLightTheme = AttrResources.getAttrBoolean(getContext(), androidx.appcompat.R.attr.isLightTheme);
        int backgroundColor = isLightTheme ? 0xCCFFFFFF : 0xCC000000;
        int textColor = isLightTheme ? 0xFF000000 : 0xFFFFFFFF;
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(mFabFunctionNameCornerRadius);
        background.setColor(backgroundColor);
        label.setBackground(background);
        label.setTextColor(textColor);
    }

    public void setHidePrimaryFab(boolean hidePrimaryFab) {
        if (mHidePrimaryFab != hidePrimaryFab) {
            mHidePrimaryFab = hidePrimaryFab;
            boolean expanded = mExpanded;
            FloatingActionButton primaryFab = getPrimaryFab();
            if (!expanded && primaryFab != null) {
                primaryFab.setVisibility(hidePrimaryFab ? INVISIBLE : VISIBLE);
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

            final int fabCount = getFabChildCount();
            if (fabCount > 0) {
                if (mMainFabCenterY == -1f || !animation) {
                    // It is before first onLayout
                    int checkCount = mHidePrimaryFab ? fabCount : fabCount - 1;
                    for (int i = 0; i < checkCount; i++) {
                        FloatingActionButton child = getFabChildAt(i);
                        if (child == null || child.getVisibility() == GONE) {
                            continue;
                        }
                        child.setVisibility(expanded ? View.VISIBLE : View.INVISIBLE);
                        if (expanded) {
                            child.setAlpha(1f);
                        }
                    }
                } else {
                    if (mHidePrimaryFab) {
                        FloatingActionButton primaryFab = getPrimaryFab();
                        if (primaryFab != null) {
                            setPrimaryFabAnimation(primaryFab, expanded, !expanded);
                        }
                    }

                    int secondaryCount = fabCount - 1;
                    for (int i = 0; i < secondaryCount; i++) {
                        FloatingActionButton child = getFabChildAt(i);
                        if (child == null || child.getVisibility() == GONE) {
                            continue;
                        }
                        setSecondaryFabAnimation(child, expanded, expanded);
                    }
                }
            }

            updateFabFunctionNameViews();
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

        if (expanded) {
            child.setVisibility(View.VISIBLE);
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
                        // visibility already handled before animation starts
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (!expanded) {
                            child.setVisibility(View.INVISIBLE);
                            updateFabFunctionNameViews();
                        }
                    }
                }).start();
    }

    @Override
    public void onClick(View v) {
        if (this == v) {
            setExpanded(false);
        } else if (mOnClickFabListener != null) {
            int position = getFabIndex(v);
            if (position == getFabChildCount() - 1) {
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
