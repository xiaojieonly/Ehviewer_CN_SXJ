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

package com.hippo.drawable;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class RoundSideRectDrawable extends Drawable {

    private final Paint mPaint;
    private final Path mPath;
    private final RectF mTempRectF;

    public RoundSideRectDrawable(int color) {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setColor(color);
        mPath = new Path();
        mTempRectF = new RectF();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        RectF rectF = mTempRectF;
        Path path = mPath;

        path.reset();
        if (width > height) {
            int radius = height / 2;
            path.moveTo(bounds.right - radius, bounds.top);
            rectF.set(bounds.right - height, bounds.top, bounds.right, bounds.bottom);
            path.arcTo(rectF, -90.0f, 180.0f, false);
            path.lineTo(bounds.left + radius, bounds.bottom);
            rectF.set(bounds.left, bounds.top, bounds.left + height, bounds.bottom);
            path.arcTo(rectF, 90.0f, 180.0f, false);
            path.lineTo(bounds.right - radius, bounds.top);
        } else if (width == height) {
            path.addCircle(bounds.centerX(), bounds.centerY(), width / 2, Path.Direction.CW);
        } else {
            // TODO
        }
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(mPath, mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
