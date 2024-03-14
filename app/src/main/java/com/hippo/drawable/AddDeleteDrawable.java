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

import com.hippo.ehviewer.R;
import com.hippo.yorozuya.MathUtils;

public class AddDeleteDrawable extends Drawable {

    private final Paint mPaint = new Paint();
    private final Path mPath = new Path();

    private final int mSize;

    private float mProgress;

    private boolean mAutoUpdateMirror = false;
    private boolean mVerticalMirror = false;

    /**
     * @param context used to get the configuration for the drawable from
     */
    public AddDeleteDrawable(Context context, int color) {
        Resources resources = context.getResources();

        mSize = resources.getDimensionPixelSize(R.dimen.add_size);
        float barThickness = Math.round(resources.getDimension(R.dimen.add_thickness));

        mPaint.setColor(color);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeJoin(Paint.Join.MITER);
        mPaint.setStrokeCap(Paint.Cap.BUTT);
        mPaint.setStrokeWidth(barThickness);

        float halfSize = mSize / 2;
        mPath.moveTo(0f, -halfSize);
        mPath.lineTo(0, halfSize);
        mPath.moveTo(-halfSize, 0);
        mPath.lineTo(halfSize, 0);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        float canvasRotate;
        if (mVerticalMirror) {
            canvasRotate = MathUtils.lerp(270, 135f, mProgress);
        } else {
            canvasRotate = MathUtils.lerp(0f, 135f, mProgress);
        }

        canvas.save();
        canvas.translate(bounds.centerX(), bounds.centerY());
        canvas.rotate(canvasRotate);
        canvas.drawPath(mPath, mPaint);
        canvas.restore();
    }

    public void setColor(int color) {
        mPaint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getIntrinsicHeight() {
        return mSize * 6 / 5;
    }

    @Override
    public int getIntrinsicWidth() {
        return mSize * 6 / 5;
    }

    /**
     * If set, canvas is flipped when progress reached to end and going back to start.
     */
    protected void setVerticalMirror(boolean verticalMirror) {
        mVerticalMirror = verticalMirror;
    }

    public void setAutoUpdateMirror(boolean autoUpdateMirror) {
        mAutoUpdateMirror = autoUpdateMirror;
    }

    @SuppressWarnings("unused")
    public float getProgress() {
        return mProgress;
    }

    @SuppressWarnings("unused")
    public void setProgress(float progress) {
        if (mAutoUpdateMirror) {
            if (progress == 1f) {
                setVerticalMirror(true);
            } else if (progress == 0f) {
                setVerticalMirror(false);
            }
        }
        mProgress = progress;
        invalidateSelf();
    }

    public void setAdd(long duration) {
        setShape(false, duration);
    }

    public void setDelete(long duration) {
        setShape(true, duration);
    }

    public void setShape(boolean delete, long duration) {
        if (!((!delete && mProgress == 0f) || (delete && mProgress == 1f))) {
            float endProgress = delete ? 1f : 0f;
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

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
