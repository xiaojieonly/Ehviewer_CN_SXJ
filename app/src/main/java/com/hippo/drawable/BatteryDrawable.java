/*
 * Copyright (C) 2014 Hippo Seven
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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.hippo.yorozuya.MathUtils;

public class BatteryDrawable extends Drawable {

    @SuppressWarnings("unused")
    private static final String TAG = BatteryDrawable.class.getSimpleName();

    public static final int WARN_LIMIT = 15;

    private int mColor = Color.WHITE;
    private int mWarningColor = Color.RED;
    private int mElect = -1;
    private final Paint mPaint;

    private final Rect mTopRect;
    private final Rect mBottomRect;
    private final Rect mRightRect;
    private final Rect mHeadRect;
    private final Rect mElectRect;
    private int mStart;
    private int mStop;

    public BatteryDrawable() {
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setStyle(Paint.Style.FILL);

        mTopRect = new Rect();
        mBottomRect = new Rect();
        mRightRect = new Rect();
        mHeadRect = new Rect();
        mElectRect = new Rect();
        updatePaint();
    }

    @Override
    @SuppressWarnings("SuspiciousNameCombination")
    protected void onBoundsChange(Rect bounds) {
        int width = bounds.width();
        int height = bounds.height();
        int strokeWidth = (int) (Math.sqrt(width * width + height * height) * 0.06f);
        int turn1 = width * 6 / 7;
        int turn2 = height / 3;
        int secBottom = height - strokeWidth;
        mStart = strokeWidth;
        mStop = turn1 - strokeWidth;

        mTopRect.set(0, 0, turn1, strokeWidth);
        mBottomRect.set(0, secBottom, turn1, height);
        mRightRect.set(turn1 - strokeWidth, strokeWidth, turn1, secBottom);
        mHeadRect.set(turn1, turn2, width, height - turn2);
        mElectRect.set(0, strokeWidth, mStop, secBottom);
    }

    /**
     * How to draw:<br>
     * |------------------------------|<br>
     * |\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\|<br>
     * |------------------------------|---|<br>
     * |/////////////////|         |//|\\\|<br>
     * |/////////////////|         |//|\\\|<br>
     * |------------------------------|---|<br>
     * |\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\|<br>
     * |------------------------------|
     */
    @Override
    public void draw(Canvas canvas) {
        if (mElect == -1) {
            return;
        }

        mElectRect.right = MathUtils.lerp(mStart, mStop, mElect / 100.0f);

        canvas.drawRect(mTopRect, mPaint);
        canvas.drawRect(mBottomRect, mPaint);
        canvas.drawRect(mRightRect, mPaint);
        canvas.drawRect(mHeadRect, mPaint);
        canvas.drawRect(mElectRect, mPaint);
    }

    private boolean isWarn() {
        return mElect <= WARN_LIMIT;
    }

    public void setColor(int color) {
        if (mColor == color) {
            return;
        }

        mColor = color;
        if (!isWarn()) {
            mPaint.setColor(mColor);
            invalidateSelf();
        }
    }

    public void setWarningColor(int color) {
        if (mWarningColor == color) {
            return;
        }

        mWarningColor = color;
        if (isWarn()) {
            mPaint.setColor(mWarningColor);
            invalidateSelf();
        }
    }

    public void setElect(int elect) {
        if (mElect == elect) {
            return;
        }

        mElect = elect;
        updatePaint();
    }

    public void setElect(int elect, boolean warn) {
        if (mElect == elect) {
            return;
        }

        mElect = elect;
        updatePaint(warn);
    }

    private void updatePaint() {
        updatePaint(isWarn());
    }

    private void updatePaint(boolean warn) {
        if (warn) {
            mPaint.setColor(mWarningColor);
        } else {
            mPaint.setColor(mColor);
        }
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
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
