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

package com.hippo.view;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.view.View;

import com.hippo.yorozuya.SimpleAnimatorListener;

public class ViewTransition {

    private static final long ANIMATE_TIME = 300L;

    private final View[] mViews;

    private int mShownView = -1;

    private Animator mAnimator1;
    private Animator mAnimator2;

    private OnShowViewListener mOnShowViewListener;

    public ViewTransition(View... views) {
        if (views.length < 2) {
            throw new IllegalStateException("You must pass view to ViewTransition");
        }
        for (View v : views) {
            if (v == null) {
                throw new IllegalStateException("Any View pass to ViewTransition must not be null");
            }
        }

        mViews = views;
        showView(0, false);
    }

    public void setOnShowViewListener(OnShowViewListener listener) {
        mOnShowViewListener = listener;
    }

    public int getShownViewIndex() {
        return mShownView;
    }

    public boolean showView(int shownView) {
        return showView(shownView, true);
    }

    public boolean showView(int shownView, boolean animation) {
        View[] views = mViews;
        int length = views.length;
        if (shownView >= length || shownView < 0) {
            throw new IndexOutOfBoundsException("Only " + length + " view(s) in " +
                    "the ViewTransition, but attempt to show " + shownView);
        }

        if (mShownView != shownView) {
            int oldShownView = mShownView;
            mShownView = shownView;

            // Cancel animation
            if (mAnimator1 != null) {
                mAnimator1.cancel();
            }
            if (mAnimator2 != null) {
                mAnimator2.cancel();
            }

            if (animation) {
                for (int i = 0; i < length; i++) {
                    if (i != oldShownView && i != shownView) {
                        View v = views[i];
                        v.setAlpha(0f);
                        v.setVisibility(View.GONE);
                    }
                }
                startAnimations(views[oldShownView], views[shownView]);
            } else {
                for (int i = 0; i < length; i++) {
                    View v = views[i];
                    if (i == shownView) {
                        v.setAlpha(1f);
                        v.setVisibility(View.VISIBLE);
                    } else {
                        v.setAlpha(0f);
                        v.setVisibility(View.GONE);
                    }
                }
            }

            if (null != mOnShowViewListener) {
                mOnShowViewListener.onShowView(views[oldShownView], views[shownView]);
            }

            return true;
        } else {
            return false;
        }
    }

    private void startAnimations(final View hiddenView, final View shownView) {
        ObjectAnimator oa1 = ObjectAnimator.ofFloat(hiddenView, "alpha", 0f);
        oa1.setDuration(ANIMATE_TIME);
        oa1.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                hiddenView.setVisibility(View.GONE);
                mAnimator1 = null;
            }
        });
        oa1.start();
        mAnimator1 = oa1;

        shownView.setVisibility(View.VISIBLE);
        ObjectAnimator oa2 = ObjectAnimator.ofFloat(shownView, "alpha", 1f);
        oa2.setDuration(ANIMATE_TIME);
        oa2.addListener(new SimpleAnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimator2 = null;
            }
        });
        oa2.start();
        mAnimator2 = oa2;
    }

    public interface OnShowViewListener {
        void onShowView(View hiddenView, View shownView);
    }
}
