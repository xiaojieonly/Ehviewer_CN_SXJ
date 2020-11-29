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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.ScrollView;
import androidx.annotation.NonNull;
import com.hippo.ehviewer.R;

public class IndicatingScrollView extends ScrollView {

    private int mIndicatorHeight;

    private final Paint mPaint = new Paint();
    private final Rect mTemp = new Rect();

    public IndicatingScrollView(Context context) {
        super(context);
        init(context, null);
    }

    public IndicatingScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public IndicatingScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.Indicating);
        mIndicatorHeight = a.getDimensionPixelOffset(R.styleable.Indicating_indicatorHeight, 1);
        mPaint.setColor(a.getColor(R.styleable.Indicating_indicatorColor, Color.BLACK));
        mPaint.setStyle(Paint.Style.FILL);
        a.recycle();
    }

    private void fillTopIndicatorDrawRect() {
        mTemp.set(0, 0, getWidth(), mIndicatorHeight);
    }

    private void fillBottomIndicatorDrawRect() {
        mTemp.set(0, getHeight() - mIndicatorHeight, getWidth(), getHeight());
    }

    private boolean needShowTopIndicator() {
        return canScrollVertically(-1);
    }

    private boolean needShowBottomIndicator() {
        return canScrollVertically(1);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        final int restoreCount = canvas.save();
        canvas.translate(getScrollX(), getScrollY());

        // Draw top indicator
        if (needShowTopIndicator()) {
            fillTopIndicatorDrawRect();
            canvas.drawRect(mTemp, mPaint);
        }
        // Draw bottom indicator
        if (needShowBottomIndicator()) {
            fillBottomIndicatorDrawRect();
            canvas.drawRect(mTemp, mPaint);
        }

        canvas.restoreToCount(restoreCount);
    }
}
