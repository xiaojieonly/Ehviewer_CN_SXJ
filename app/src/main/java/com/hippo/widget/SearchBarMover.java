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

package com.hippo.widget;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.SimpleAnimatorListener;
import com.hippo.lib.yorozuya.ViewUtils;

public class SearchBarMover extends RecyclerView.OnScrollListener {

    private static final long ANIMATE_TIME = 300L;
    /** M3 emphasized 曲线 */
    private static final PathInterpolator EMPHASIZED_DECELERATE =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    private static final PathInterpolator EMPHASIZED_ACCELERATE =
            new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private boolean mShow;
    private ValueAnimator mSearchBarMoveAnimator;
    private final Helper mHelper;
    private final View mSearchBar;
    @Nullable
    private OnBarVisibilityListener mVisibilityListener;
    private boolean mLastFullyHidden;

    public SearchBarMover(Helper helper, View searchBar,@Nullable RecyclerView... recyclerViews) {
        mHelper = helper;
        mSearchBar = searchBar;
        if (recyclerViews == null){
            return;
        }
        for (RecyclerView recyclerView: recyclerViews) {
            recyclerView.addOnScrollListener(this);
        }
    }

    public void cancelAnimation() {
        if (mSearchBarMoveAnimator != null) {
            mSearchBarMoveAnimator.cancel();
        }
    }

    /**
     * 栏「完全隐藏↔部分可见」状态翻转回调(底边越过父容器顶时触发),
     * 供状态栏样式等联动;吸附动画与拖拽滚动都会上报
     */
    public void setOnBarVisibilityListener(@Nullable OnBarVisibilityListener listener) {
        mVisibilityListener = listener;
    }

    private void dispatchVisibilityIfChanged() {
        if (mVisibilityListener == null) {
            return;
        }
        boolean fullyHidden = ViewUtils.getY2(mSearchBar) <= 0;
        if (fullyHidden != mLastFullyHidden) {
            mLastFullyHidden = fullyHidden;
            mVisibilityListener.onBarVisibilityChanged(fullyHidden);
        }
    }

    @Override
    public void onScrollStateChanged(RecyclerView recyclerView, int newState){
        if (newState == RecyclerView.SCROLL_STATE_IDLE && mHelper.isValidView(recyclerView)) {
            returnSearchBarPosition();
        }
    }

    @Override
    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (mHelper.isValidView(recyclerView)) {
            int oldBottom = (int) ViewUtils.getY2(mSearchBar);
            int offsetYStep = MathUtils.clamp(-dy, -oldBottom, -(int) mSearchBar.getTranslationY());
            if (offsetYStep != 0) {
                ViewUtils.translationYBy(mSearchBar, offsetYStep);
                dispatchVisibilityIfChanged();
            }
        }
    }

    public void returnSearchBarPosition() {
        returnSearchBarPosition(true);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    public void returnSearchBarPosition(boolean animation) {
        if (mSearchBar.getHeight() == 0) {
            // Layout not called
            return;
        }

        boolean show;
        if (mHelper.forceShowSearchBar()) {
            show = true;
        } else {
            RecyclerView recyclerView = mHelper.getValidRecyclerView();
            if (recyclerView == null) {
                return;
            }
            if (!recyclerView.isShown()) {
                show = true;
            } else if (recyclerView.computeVerticalScrollOffset() < mSearchBar.getBottom()){
                show = true;
            } else {
                show = (int) ViewUtils.getY2(mSearchBar) > (mSearchBar.getHeight()) / 2;
            }
        }

        int offset;
        if (show) {
            offset = -(int) mSearchBar.getTranslationY();
        } else {
            offset = -(int) ViewUtils.getY2(mSearchBar);
        }

        if (offset == 0) {
            // No need to scroll
            return;
        }

        if (animation) {
            if (mSearchBarMoveAnimator != null) {
                if (mShow == show) {
                    // The same target, no need to do animation
                    return;
                } else {
                    // Cancel it
                    mSearchBarMoveAnimator.cancel();
                    mSearchBarMoveAnimator = null;
                }
            }

            mShow = show;
            final ValueAnimator va = ValueAnimator.ofInt(0, offset);
            va.setDuration(ANIMATE_TIME);
            // M3 emphasized:展开减速、收起加速
            va.setInterpolator(show ? EMPHASIZED_DECELERATE : EMPHASIZED_ACCELERATE);
            va.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSearchBarMoveAnimator = null;
                }
            });
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                int lastValue;
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (Integer) animation.getAnimatedValue();
                    int offsetStep = value - lastValue;
                    lastValue = value;
                    ViewUtils.translationYBy(mSearchBar, offsetStep);
                    dispatchVisibilityIfChanged();
                }
            });
            mSearchBarMoveAnimator = va;
            va.start();
        } else {
            if (mSearchBarMoveAnimator != null) {
                mSearchBarMoveAnimator.cancel();
            }
            ViewUtils.translationYBy(mSearchBar, offset);
            dispatchVisibilityIfChanged();
        }
    }

    public void showSearchBar() {
        showSearchBar(true);
    }

    public void showSearchBar(boolean animation) {
        if (mSearchBar.getHeight() == 0) {
            // Layout not called
            return;
        }

        final int offset = -(int) mSearchBar.getTranslationY();

        if (offset == 0) {
            // No need to scroll
            return;
        }

        if (animation) {
            if (mSearchBarMoveAnimator != null) {
                if (mShow) {
                    // The same target, no need to do animation
                    return;
                } else {
                    // Cancel it
                    mSearchBarMoveAnimator.cancel();
                    mSearchBarMoveAnimator = null;
                }
            }

            mShow = true;
            final ValueAnimator va = ValueAnimator.ofInt(0, offset);
            va.setDuration(ANIMATE_TIME);
            // M3 emphasized:展开减速
            va.setInterpolator(EMPHASIZED_DECELERATE);
            va.addListener(new SimpleAnimatorListener() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mSearchBarMoveAnimator = null;
                }
            });
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                int lastValue;
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    int value = (Integer) animation.getAnimatedValue();
                    int offsetStep = value - lastValue;
                    lastValue = value;
                    ViewUtils.translationYBy(mSearchBar, offsetStep);
                    dispatchVisibilityIfChanged();
                }
            });
            mSearchBarMoveAnimator = va;
            va.start();
        } else {
            if (mSearchBarMoveAnimator != null) {
                mSearchBarMoveAnimator.cancel();
            }
            ViewUtils.translationYBy(mSearchBar, offset);
            dispatchVisibilityIfChanged();
        }
    }

    public interface Helper {

        boolean isValidView(RecyclerView recyclerView);

        @Nullable
        RecyclerView getValidRecyclerView();

        boolean forceShowSearchBar();
    }

    public interface OnBarVisibilityListener {

        /**
         * @param fullyHidden true = 栏底边已完全移出父容器顶(不可见);false = 重新部分可见
         */
        void onBarVisibilityChanged(boolean fullyHidden);
    }
}
