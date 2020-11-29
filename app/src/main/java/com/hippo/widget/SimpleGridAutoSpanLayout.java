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

package com.hippo.widget;

import android.content.Context;
import android.util.AttributeSet;

public class SimpleGridAutoSpanLayout extends SimpleGridLayout {

    public static final int STRATEGY_MIN_SIZE = 0;
    public static final int STRATEGY_SUITABLE_SIZE = 1;

    private int mColumnSize = -1;
    private boolean mColumnSizeChanged = true;
    private int mStrategy;

    public SimpleGridAutoSpanLayout(Context context) {
        super(context);
    }

    public SimpleGridAutoSpanLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SimpleGridAutoSpanLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setColumnSize(int columnSize) {
        if (columnSize == mColumnSize) {
            return;
        }
        mColumnSize = columnSize;
        mColumnSizeChanged = true;
    }

    public void setStrategy(int strategy) {
        if (strategy == mStrategy) {
            return;
        }
        mStrategy = strategy;
        mColumnSizeChanged = true;
    }

    public static int getSpanCountForSuitableSize(int total, int single) {
        int span = total / single;
        if (span <= 0) {
            return 1;
        }
        int span2 = span + 1;
        float deviation = Math.abs(1 - (total / span / (float) single));
        float deviation2 = Math.abs(1 - (total / span2 / (float) single));
        return deviation < deviation2 ? span : span2;
    }

    public static int getSpanCountForMinSize(int total, int single) {
        return Math.max(1, total / single);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (mColumnSizeChanged && mColumnSize > 0 && widthMode == MeasureSpec.EXACTLY) {
            int totalSpace = widthSize - getPaddingRight() - getPaddingLeft();
            int spanCount;
            switch (mStrategy) {
                default:
                case STRATEGY_MIN_SIZE:
                    spanCount = getSpanCountForMinSize(totalSpace, mColumnSize);
                    break;
                case STRATEGY_SUITABLE_SIZE:
                    spanCount = getSpanCountForSuitableSize(totalSpace, mColumnSize);
                    break;
            }
            setColumnCount(spanCount);
            mColumnSizeChanged = false;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
