/*
 * Copyright 2024 Hippo Seven
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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * A semi-transparent bottom indicator bar similar to iOS design.
 * Used to indicate that the control panel is hidden and can be shown by tapping.
 */
public class BottomIndicatorView extends View {

    private static final float INDICATOR_WIDTH_RATIO = 0.35f;
    private static final float INDICATOR_HEIGHT_DP = 5f;
    private static final float INDICATOR_CORNER_RADIUS_DP = 2.5f;
    private static final int INDICATOR_COLOR = 0x80FFFFFF;

    private final Paint mPaint;
    private final RectF mRectF;
    private final float mIndicatorHeight;
    private final float mCornerRadius;

    public BottomIndicatorView(Context context) {
        this(context, null);
    }

    public BottomIndicatorView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BottomIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mPaint.setColor(INDICATOR_COLOR);
        mPaint.setStyle(Paint.Style.FILL);

        mRectF = new RectF();

        float density = context.getResources().getDisplayMetrics().density;
        mIndicatorHeight = INDICATOR_HEIGHT_DP * density;
        mCornerRadius = INDICATOR_CORNER_RADIUS_DP * density;
        
        setClickable(true);
        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int) (mIndicatorHeight * 3);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float indicatorWidth = width * INDICATOR_WIDTH_RATIO;
        float left = (width - indicatorWidth) / 2f;
        float top = (height - mIndicatorHeight) / 2f;
        float right = left + indicatorWidth;
        float bottom = top + mIndicatorHeight;

        mRectF.set(left, top, right, bottom);
        canvas.drawRoundRect(mRectF, mCornerRadius, mCornerRadius, mPaint);
    }
}
