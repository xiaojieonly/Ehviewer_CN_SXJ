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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Interpolator;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import com.hippo.ehviewer.R;
import com.hippo.hotspot.Hotspot;
import com.hippo.hotspot.Hotspotable;
import com.hippo.yorozuya.AnimationUtils;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.SimpleAnimatorListener;

public class CheckTextView extends AppCompatTextView implements OnClickListener, Hotspotable {

    private static final String STATE_KEY_SUPER = "super";
    private static final String STATE_KEY_CHECKED = "checked";

    private static final long ANIMATION_DURATION = 200;

    private int mMaskColor;

    private boolean mChecked = false;
    private boolean mPrepareAnimator = false;

    private Paint mPaint;
    private float mRadius = 0f;
    private float mX;
    private float mY;

    Animator mAnimator;

    private float mMaxRadius;

    public CheckTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public CheckTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CheckTextView);
        mMaskColor = a.getColor(R.styleable.CheckTextView_maskColor, Color.WHITE);
        a.recycle();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setColor(mMaskColor);

        setOnClickListener(this);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Hotspot.addHotspotable(this, this);
        }
    }

    @Override
    public void setHotspot(float x, float y) {
        mX = x;
        mY = y;
        mMaxRadius = MathUtils.coverageRadius(getWidth(), getHeight(), x, y);
    }

    @Override
    public void drawableHotspotChanged(float x, float y) {
        super.drawableHotspotChanged(x, y);

        mX = x;
        mY = y;
        mMaxRadius = MathUtils.coverageRadius(getWidth(), getHeight(), x, y);
    }

    public void setRadius(float radius) {
        float bigger = Math.max(mRadius, radius);
        mRadius = radius;
        invalidate((int) (mX - bigger), (int) (mY - bigger), (int) (mX + bigger), (int) (mY + bigger));
    }

    public float getRadius() {
        return mRadius;
    }

    private final Animator.AnimatorListener mAnimatorListener = new SimpleAnimatorListener() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mAnimator = null;
        }
    };

    public void prepareAnimations() {
        mPrepareAnimator = true;
    }

    private void createAnimations() {
        float startRadius;
        float endRadius;
        Interpolator interpolator;
        if (mChecked) {
            startRadius = 0;
            endRadius = mMaxRadius;
            interpolator = AnimationUtils.FAST_SLOW_INTERPOLATOR;
        } else {
            startRadius = mMaxRadius;
            endRadius = 0;
            interpolator = AnimationUtils.SLOW_FAST_INTERPOLATOR;
        }
        mRadius = startRadius;

        final ObjectAnimator radiusAnim = ObjectAnimator.ofFloat(this, "radius", startRadius, endRadius);
        radiusAnim.setDuration(ANIMATION_DURATION);
        radiusAnim.setInterpolator(interpolator);
        radiusAnim.addListener(mAnimatorListener);
        radiusAnim.start();
        mAnimator = radiusAnim;
    }

    private void cancelAnimations() {
        if (mAnimator != null) {
            mAnimator.cancel();
        }
    }

    @Override
    public void onClick(View v) {
        setChecked(!mChecked);
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        if (mPrepareAnimator) {
            mPrepareAnimator = false;
            cancelAnimations();
            createAnimations();
        }

        if (mAnimator != null) {
            canvas.drawCircle(mX, mY, mRadius, mPaint);
        } else {
            if (mChecked) {
                canvas.drawColor(mMaskColor);
            }
        }
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    /**
     * Changes the checked state of this CheckTextView.
     *
     * @param checked checked true to check the CheckTextView, false to uncheck it
     * @param animation true for show animation
     */
    public void setChecked(boolean checked, boolean animation) {
        if (mChecked != checked) {
            mChecked = checked;

            if (animation) {
                prepareAnimations();
            }
            invalidate();
        }
    }

    /**
     * Get the checked state of it.
     *
     * @return true is it is checked
     */
    public boolean isChecked() {
        return mChecked;
    }

    @Override
    public Parcelable onSaveInstanceState() {
        final Bundle state = new Bundle();
        state.putParcelable(STATE_KEY_SUPER, super.onSaveInstanceState());
        state.putBoolean(STATE_KEY_CHECKED, mChecked);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle savedState = (Bundle) state;
            super.onRestoreInstanceState(savedState.getParcelable(STATE_KEY_SUPER));
            setChecked(savedState.getBoolean(STATE_KEY_CHECKED), false);
        }
    }
}
