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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import com.hippo.ehviewer.R;

public class DividerView extends View {

    private Paint mPaint;
    private Rect mRect;
    private int mDividerWidth;
    private int mDividerHeight;

    public DividerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public DividerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.DividerView);
        int color = a.getColor(R.styleable.DividerView_dividerColor, Color.BLACK);
        mDividerWidth = a.getDimensionPixelOffset(R.styleable.DividerView_dividerWidth, 0);
        mDividerHeight = a.getDimensionPixelOffset(R.styleable.DividerView_dividerHeight, 0);
        a.recycle();

        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mPaint.setColor(color);
        mRect = new Rect();
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return Math.max((getPaddingLeft() + getPaddingRight() + mDividerWidth), super.getSuggestedMinimumWidth());
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return Math.max((getPaddingTop() + getPaddingBottom() + mDividerHeight), super.getSuggestedMinimumHeight());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Rect rect = mRect;
        rect.set(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        if (!rect.isEmpty()) {
            canvas.drawRect(rect, mPaint);
        }
    }
}
