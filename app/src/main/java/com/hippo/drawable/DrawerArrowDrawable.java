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

package com.hippo.drawable;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.ColorInt;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.MathUtils;

/**
 * A drawable that can draw a "Drawer hamburger" menu or an Arrow and animate between them.
 */
public class DrawerArrowDrawable extends Drawable {

    private final Paint mPaint = new Paint();

    // The angle in degrees that the arrow head is inclined at.
    private static final float ARROW_HEAD_ANGLE = (float) Math.toRadians(45);
    private final float mBarThickness;
    // The length of top and bottom bars when they merge into an arrow
    private final float mTopBottomArrowSize;
    // The length of middle bar
    private final float mBarSize;
    // The length of the middle bar when arrow is shaped
    private final float mMiddleArrowSize;
    // The space between bars when they are parallel
    private final float mBarGap;
    // Whether bars should spin or not during progress
    private final boolean mSpin;
    // Use Path instead of canvas operations so that if color has transparency, overlapping sections
    // wont look different
    private final Path mPath = new Path();
    // The reported intrinsic size of the drawable.
    private final int mSize;
    // Whether we should mirror animation when animation is reversed.
    private boolean mVerticalMirror = false;
    // The interpolated version of the original progress
    private float mProgress;
    // the amount that overlaps w/ bar size when rotation is max
    private final float mMaxCutForBarSize;

    /**
     * @param context used to get the configuration for the drawable from
     */
    public DrawerArrowDrawable(Context context, int color) {
        Resources resources = context.getResources();

        mPaint.setAntiAlias(true);
        mPaint.setColor(color);
        mSize = resources.getDimensionPixelSize(R.dimen.dad_drawable_size);
        // round this because having this floating may cause bad measurements
        mBarSize = Math.round(resources.getDimension(R.dimen.dad_bar_size));
        // round this because having this floating may cause bad measurements
        mTopBottomArrowSize = Math.round(resources.getDimension(R.dimen.dad_top_bottom_bar_arrow_size));
        mBarThickness = resources.getDimension(R.dimen.dad_thickness);
        // round this because having this floating may cause bad measurements
        mBarGap = Math.round(resources.getDimension(R.dimen.dad_gap_between_bars));
        mSpin = resources.getBoolean(R.bool.dad_spin_bars);
        mMiddleArrowSize = resources.getDimension(R.dimen.dad_middle_bar_arrow_size);

        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        mPaint.setStrokeWidth(mBarThickness);

        mMaxCutForBarSize = (float) (mBarThickness / 2 * Math.cos(ARROW_HEAD_ANGLE));
    }

    /**
     * If set, canvas is flipped when progress reached to end and going back to start.
     */
    protected void setVerticalMirror(boolean verticalMirror) {
        mVerticalMirror = verticalMirror;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        // Interpolated widths of arrow bars
        final float arrowSize = MathUtils.lerp(mBarSize, mTopBottomArrowSize, mProgress);
        final float middleBarSize = MathUtils.lerp(mBarSize, mMiddleArrowSize, mProgress);
        // Interpolated size of middle bar
        final float middleBarCut = Math.round(MathUtils.lerp(0, mMaxCutForBarSize, mProgress));
        // The rotation of the top and bottom bars (that make the arrow head)
        final float rotation = MathUtils.lerp(0, ARROW_HEAD_ANGLE, mProgress);

        // The whole canvas rotates as the transition happens
        final float canvasRotate = MathUtils.lerp(-180, 0, mProgress);
        final float arrowWidth = Math.round(arrowSize * Math.cos(rotation));
        final float arrowHeight = Math.round(arrowSize * Math.sin(rotation));

        mPath.rewind();
        final float topBottomBarOffset = MathUtils.lerp(mBarGap + mBarThickness, -mMaxCutForBarSize,
                mProgress);

        final float arrowEdge = -middleBarSize / 2;
        // draw middle bar
        mPath.moveTo(arrowEdge + middleBarCut, 0);
        mPath.rLineTo(middleBarSize - middleBarCut * 2, 0);

        // bottom bar
        mPath.moveTo(arrowEdge, topBottomBarOffset);
        mPath.rLineTo(arrowWidth, arrowHeight);

        // top bar
        mPath.moveTo(arrowEdge, -topBottomBarOffset);
        mPath.rLineTo(arrowWidth, -arrowHeight);

        mPath.close();

        canvas.save();
        // Rotate the whole canvas if spinning, if not, rotate it 180 to get
        // the arrow pointing the other way for RTL.
        canvas.translate(bounds.centerX(), bounds.centerY());
        if (mSpin) {
            canvas.rotate(canvasRotate * (mVerticalMirror ? -1 : 1));
        }
        canvas.drawPath(mPath, mPaint);

        canvas.restore();
    }

    public void setColor(@ColorInt int color) {
        mPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int i) {
        mPaint.setAlpha(i);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @SuppressWarnings("unused")
    public float getProgress() {
        return mProgress;
    }

    @SuppressWarnings("unused")
    public void setProgress(float progress) {
        if (progress == 1f) {
            setVerticalMirror(true);
        } else if (progress == 0f) {
            setVerticalMirror(false);
        }
        mProgress = progress;
        invalidateSelf();
    }

    public void setMenu(long duration) {
        setShape(false, duration);
    }

    public void setArrow(long duration) {
        setShape(true, duration);
    }

    public void setShape(boolean arrow, long duration) {
        if (!((!arrow && mProgress == 0f) || (arrow && mProgress == 1f))) {
            float endProgress = arrow ? 1f : 0f;
            if (duration <= 0) {
                setProgress(endProgress);
            } else {
                ObjectAnimator oa = ObjectAnimator.ofFloat(this, "progress", endProgress);
                oa.setDuration(duration);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    oa.setAutoCancel(true);
                }
                oa.start();
            }
        }
    }
}
