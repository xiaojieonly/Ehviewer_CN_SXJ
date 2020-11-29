/*
 * Copyright 2015 Hippo Seven
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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.animation.PathInterpolatorCompat;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.ViewUtils;
import java.util.ArrayList;

// Base on android.graphics.drawable.MaterialProgressDrawable in L preview
public class ProgressView extends View {

    private static final Interpolator TRIM_START_INTERPOLATOR;
    private static final Interpolator TRIM_END_INTERPOLATOR;
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    static {
        Path trimStartPath = new Path();
        trimStartPath.moveTo(0.0f, 0.0f);
        trimStartPath.lineTo(0.5f, 0.0f);
        trimStartPath.cubicTo(0.7f, 0.0f, 0.6f, 1f, 1f, 1f);
        TRIM_START_INTERPOLATOR = PathInterpolatorCompat.create(trimStartPath);

        Path trimEndPath = new Path();
        trimEndPath.moveTo(0.0f, 0.0f);
        trimEndPath.cubicTo(0.2f, 0.0f, 0.1f, 1f, 0.5f, 1f);
        trimEndPath.lineTo(1f, 1f);
        TRIM_END_INTERPOLATOR = PathInterpolatorCompat.create(trimEndPath);
    }

    private final ArrayList<Animator> mAnimators = new ArrayList<>();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRectF = new RectF();

    private boolean mIndeterminate;

    private float mTrimStart = 0.0f;
    private float mTrimEnd = 0.0f;
    private float mTrimOffset = 0.0f;
    private float mTrimRotation = 0.0f;

    // It is a trick to avoid first sight stuck. Get it from ProgressBar
    private boolean mShouldStartAnimationDrawable = false;

    public ProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ProgressView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.ProgressView);
        int color = a.getColor(R.styleable.ProgressView_color, Color.BLACK);
        mPaint.setColor(color);
        mPaint.setStrokeCap(Paint.Cap.SQUARE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        mPaint.setStyle(Paint.Style.STROKE);
        mIndeterminate = a.getBoolean(R.styleable.ProgressView_indeterminate, true);
        setProgress(a.getFloat(R.styleable.ProgressView_progress, 0f));
        a.recycle();

        setupAnimators();
    }

    private void setupAnimators() {
        ObjectAnimator trimStart = ObjectAnimator.ofFloat(this, "trimStart", 0.0f, 0.75f);
        trimStart.setDuration(1333L);
        trimStart.setInterpolator(TRIM_START_INTERPOLATOR);
        trimStart.setRepeatCount(Animation.INFINITE);

        ObjectAnimator trimEnd = ObjectAnimator.ofFloat(this, "trimEnd", 0.0f, 0.75f);
        trimEnd.setDuration(1333L);
        trimEnd.setInterpolator(TRIM_END_INTERPOLATOR);
        trimEnd.setRepeatCount(Animation.INFINITE);

        ObjectAnimator trimOffset = ObjectAnimator.ofFloat(this, "trimOffset", 0.0f, 0.25f);
        trimOffset.setDuration(1333L);
        trimOffset.setInterpolator(LINEAR_INTERPOLATOR);
        trimOffset.setRepeatCount(Animation.INFINITE);

        ObjectAnimator trimRotation = ObjectAnimator.ofFloat(this, "trimRotation", 0.0f, 720.0f);
        trimRotation.setDuration(6665L);
        trimRotation.setInterpolator(LINEAR_INTERPOLATOR);
        trimRotation.setRepeatCount(Animation.INFINITE);

        mAnimators.add(trimStart);
        mAnimators.add(trimEnd);
        mAnimators.add(trimOffset);
        mAnimators.add(trimRotation);
    }

    private void startAnimation() {
        mShouldStartAnimationDrawable = true;
        postInvalidate();
    }

    private void startAnimationActually() {
        ArrayList<Animator> animators = mAnimators;
        int N = animators.size();
        for (int i = 0; i < N; i++) {
            Animator animator = animators.get(i);
            if (!animator.isRunning()) {
                animator.start();
            }
        }
    }

    private void stopAnimation() {
        mShouldStartAnimationDrawable = false;
        ArrayList<Animator> animators = mAnimators;
        int N = animators.size();
        for (int i = 0; i < N; i++) {
            animators.get(i).cancel();
        }
    }

    public boolean isRunning() {
        ArrayList<Animator> animators = mAnimators;
        int N = animators.size();
        for (int i = 0; i < N; i++) {
            Animator animator = animators.get(i);
            if (animator.isRunning()) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    public float getTrimStart() {
        return mTrimStart;
    }

    @SuppressWarnings("unused")
    public void setTrimStart(float trimStart) {
        mTrimStart = trimStart;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getTrimEnd() {
        return mTrimEnd;
    }

    @SuppressWarnings("unused")
    public void setTrimEnd(float trimEnd) {
        mTrimEnd = trimEnd;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getTrimOffset() {
        return mTrimOffset;
    }

    @SuppressWarnings("unused")
    public void setTrimOffset(float trimOffset) {
        mTrimOffset = trimOffset;
        invalidate();
    }

    @SuppressWarnings("unused")
    public float getTrimRotation() {
        return mTrimRotation;
    }

    @SuppressWarnings("unused")
    public void setTrimRotation(float trimRotation) {
        mTrimRotation = trimRotation;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mIndeterminate && getVisibility() == VISIBLE) {
            startAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mIndeterminate) {
            stopAnimation();
        }
        // This should come after stopAnimation(), otherwise an invalidate message remains in the
        // queue, which can prevent the entire view hierarchy from being GC'ed during a rotation
        super.onDetachedFromWindow();
    }


    @Override
    public void setVisibility(int v) {
        if (getVisibility() != v) {
            super.setVisibility(v);

            if (mIndeterminate) {
                if (v == GONE || v == INVISIBLE) {
                    stopAnimation();
                } else if (ViewCompat.isAttachedToWindow(this)) {
                    startAnimation();
                }
            }
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);

        if (mIndeterminate && ViewCompat.isAttachedToWindow(this)) {
            if (visibility == GONE || visibility == INVISIBLE) {
                stopAnimation();
            } else if (ViewCompat.isAttachedToWindow(this)) {
                startAnimation();
            }
        }
    }

    public void setColor(int color) {
        mPaint.setColor(color);
        invalidate();
    }

    public void setIndeterminate(boolean indeterminate) {
        if (mIndeterminate != indeterminate) {
            mIndeterminate = indeterminate;
            if (indeterminate) {
                if (isShown() && ViewCompat.isAttachedToWindow(this)) {
                    startAnimation();
                }
            } else {
                stopAnimation();
            }
        }
    }

    public boolean isIndeterminate() {
        return mIndeterminate;
    }

    public void setProgress(float progress) {
        if (!mIndeterminate) {
            mTrimStart = 0f;
            mTrimEnd = progress;
            mTrimOffset = 0f;
            mTrimRotation = 0f;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(ViewUtils.getSuitableSize(getSuggestedMinimumWidth(), widthMeasureSpec),
                ViewUtils.getSuitableSize(getSuggestedMinimumHeight(), heightMeasureSpec));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mPaint.setStrokeWidth(Math.min(w, h) / 12.0f);
        mRectF.set(0, 0, w, h);
        mRectF.inset(w / 48.0f * 5.0f, h / 48.0f * 5.0f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int saved = canvas.save();
        canvas.rotate(mTrimRotation, mRectF.centerX(), mRectF.centerY());

        float startAngle = (mTrimStart + mTrimOffset) * 360.0f - 90;
        float sweepAngle = (mTrimEnd - mTrimStart) * 360.0f;
        canvas.drawArc(mRectF, startAngle, sweepAngle, false, mPaint);

        canvas.restoreToCount(saved);

        if (mShouldStartAnimationDrawable) {
            mShouldStartAnimationDrawable = false;
            startAnimationActually();
        }
    }
}
