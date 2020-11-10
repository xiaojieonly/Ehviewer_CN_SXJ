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

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import com.hippo.ehviewer.R;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.ViewUtils;

/**
 * not scrollable
 *
 * @author Hippo
 *
 */
public class SimpleGridLayout extends ViewGroup {

    private static final int DEFAULT_COLUMN_COUNT = 3;

    private int mColumnCount;
    private int mItemMargin;

    private int[] mRowHeights;
    private int mItemWidth;

    public SimpleGridLayout(Context context) {
        super(context);
        init(context, null);
    }

    public SimpleGridLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SimpleGridLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SimpleGridLayout);
        mColumnCount = a.getInteger(R.styleable.SimpleGridLayout_columnCount, DEFAULT_COLUMN_COUNT);
        mItemMargin = a.getDimensionPixelOffset(R.styleable.SimpleGridLayout_itemMargin, 0);
        a.recycle();
    }

    public void setItemMargin(int itemMargin) {
        if (mItemMargin != itemMargin) {
            mItemMargin = itemMargin;
            requestLayout();
        }
    }

    public void setColumnCount(int columnCount) {
        if (columnCount <= 0) {
            throw new IllegalStateException("Column count can't be " + columnCount);
        }

        if (mColumnCount != columnCount) {
            mColumnCount = columnCount;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int maxRowCount = MathUtils.ceilDivide(getChildCount(), mColumnCount);
        if (mRowHeights == null || mRowHeights.length != maxRowCount) {
            mRowHeights = new int[maxRowCount];
        }

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int maxWidth = MeasureSpec.getSize(widthMeasureSpec);
        int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

        if (widthMode == MeasureSpec.UNSPECIFIED) {
            maxWidth = 300;
        }
        if (heightMode == MeasureSpec.UNSPECIFIED) {
            maxHeight = ViewUtils.MAX_SIZE;
        }

        // Get item width MeasureSpec
        mItemWidth = Math.max(
                (maxWidth - getPaddingLeft() - getPaddingRight() - ((mColumnCount - 1) * mItemMargin)) / mColumnCount, 1);
        int itemWidthMeasureSpec = MeasureSpec.makeMeasureSpec(mItemWidth, MeasureSpec.EXACTLY);
        int itemHeightMeasureSpec = MeasureSpec.UNSPECIFIED;

        int measuredWidth = maxWidth;
        int measuredHeight = 0;
        int rowHeight = 0;
        int row = 0;
        int count = getChildCount();
        for (int index = 0, indexInRow = 0; index < count; index++, indexInRow++) {
            final View child = getChildAt(index);
            if (child.getVisibility() == View.GONE) {
                indexInRow--;
                continue;
            }

            child.measure(itemWidthMeasureSpec, itemHeightMeasureSpec);

            if (indexInRow == mColumnCount) {
                // New row
                indexInRow = 0;
                rowHeight = 0;
                row++;
            }

            rowHeight = Math.max(rowHeight, child.getMeasuredHeight());

            if (indexInRow == mColumnCount - 1 || index == count - 1) {
                mRowHeights[row] = rowHeight;
                measuredHeight += rowHeight + mItemMargin;
            }
        }
        measuredHeight -= mItemMargin;
        measuredHeight = Math.max(0, Math.min(measuredHeight + getPaddingTop() + getPaddingBottom(), maxHeight));

        setMeasuredDimension(measuredWidth, measuredHeight);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int itemWidth = mItemWidth;
        int itemMargin = mItemMargin;
        int paddingLeft = getPaddingLeft();
        int left = paddingLeft;
        int top = getPaddingTop();
        int row = 0;
        int count = getChildCount();
        for (int index = 0, indexInRow = 0; index < count; index++, indexInRow++) {
            final View child = getChildAt(index);
            if (child.getVisibility() == View.GONE) {
                indexInRow--;
                continue;
            }

            if (indexInRow == mColumnCount) {
                // New row
                left = paddingLeft;
                top += mRowHeights[row] + itemMargin;

                indexInRow = 0;
                row++;
            }

            child.layout(left, top, left + child.getMeasuredWidth(), top + child.getMeasuredHeight());

            left += itemWidth + itemMargin;
        }
    }
}
