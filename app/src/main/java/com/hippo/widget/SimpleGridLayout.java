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
import com.hippo.lib.yorozuya.MathUtils;
import com.hippo.lib.yorozuya.ViewUtils;

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
    
    // 缓存字段，用于性能优化
    private int mCachedItemWidth = -1;
    private int mCachedWidthMeasureSpec = -1;
    private int mCachedPaddingLeft = Integer.MIN_VALUE;
    private int mCachedPaddingRight = Integer.MIN_VALUE;
    private int mCachedColumnCount = -1;
    
    // 布局缓存，用于优化 onLayout 性能
    private int mCachedLayoutLeft = Integer.MIN_VALUE;
    private int mCachedLayoutTop = Integer.MIN_VALUE;
    private int mCachedLayoutRight = Integer.MIN_VALUE;
    private int mCachedLayoutBottom = Integer.MIN_VALUE;
    private int mCachedLayoutItemWidth = -1;
    private int mCachedLayoutItemMargin = -1;
    private int mCachedLayoutColumnCount = -1;

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
            // 清除缓存，确保下次 onMeasure 和 onLayout 时重新计算
            mCachedItemWidth = -1;
            mCachedLayoutItemMargin = -1;
            requestLayout();
        }
    }

    public void setColumnCount(int columnCount) {
        if (columnCount <= 0) {
            throw new IllegalStateException("Column count can't be " + columnCount);
        }

        if (mColumnCount != columnCount) {
            mColumnCount = columnCount;
            // 清除缓存，确保下次 onMeasure 和 onLayout 时重新计算
            mCachedItemWidth = -1;
            mCachedLayoutColumnCount = -1;
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

        // 检查是否需要重新计算 itemWidth
        int paddingLeft = getPaddingLeft();
        int paddingRight = getPaddingRight();
        boolean needRecalculateItemWidth = (mCachedItemWidth < 0 || 
                mCachedWidthMeasureSpec != widthMeasureSpec ||
                mCachedPaddingLeft != paddingLeft ||
                mCachedPaddingRight != paddingRight ||
                mCachedColumnCount != mColumnCount);

        // Get item width MeasureSpec
        if (needRecalculateItemWidth) {
            mItemWidth = Math.max(
                    (maxWidth - paddingLeft - paddingRight - ((mColumnCount - 1) * mItemMargin)) / mColumnCount, 1);
            mCachedItemWidth = mItemWidth;
            mCachedWidthMeasureSpec = widthMeasureSpec;
            mCachedPaddingLeft = paddingLeft;
            mCachedPaddingRight = paddingRight;
            mCachedColumnCount = mColumnCount;
        } else {
            mItemWidth = mCachedItemWidth;
        }
        
        int itemWidthMeasureSpec = MeasureSpec.makeMeasureSpec(mItemWidth, MeasureSpec.EXACTLY);
        int itemHeightMeasureSpec = MeasureSpec.UNSPECIFIED;

        int measuredWidth = maxWidth;
        int measuredHeight = 0;
        int rowHeight = 0;
        int row = 0;
        int count = getChildCount();
        
        // 优化：减少循环中的计算
        int lastIndexInRow = mColumnCount - 1;
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

            int childHeight = child.getMeasuredHeight();
            rowHeight = Math.max(rowHeight, childHeight);

            // 优化：使用提前计算的值，减少条件判断
            if (indexInRow == lastIndexInRow || index == count - 1) {
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
        // 检查布局参数是否变化，如果没变化且布局区域相同，可以跳过部分计算
        int itemWidth = mItemWidth;
        int itemMargin = mItemMargin;
        int paddingLeft = getPaddingLeft();
        boolean layoutParamsChanged = (mCachedLayoutItemWidth != itemWidth ||
                mCachedLayoutItemMargin != itemMargin ||
                mCachedLayoutColumnCount != mColumnCount ||
                mCachedLayoutLeft != l ||
                mCachedLayoutTop != t ||
                mCachedLayoutRight != r ||
                mCachedLayoutBottom != b);
        
        // 如果布局参数没变化且 changed 为 false，可以优化
        // 但为了安全起见，仍然执行布局，只是优化计算过程
        int left = paddingLeft;
        int top = getPaddingTop();
        int row = 0;
        int count = getChildCount();
        
        // 优化：提前计算常用值
        int itemWidthPlusMargin = itemWidth + itemMargin;
        int lastIndexInRow = mColumnCount - 1;
        
        for (int index = 0, indexInRow = 0; index < count; index++, indexInRow++) {
            final View child = getChildAt(index);
            if (child.getVisibility() == View.GONE) {
                indexInRow--;
                continue;
            }

            if (indexInRow == mColumnCount) {
                // New row
                left = paddingLeft;
                if (row < mRowHeights.length) {
                    top += mRowHeights[row] + itemMargin;
                }
                indexInRow = 0;
                row++;
            }

            // 优化：使用缓存的测量尺寸，避免重复获取
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            child.layout(left, top, left + childWidth, top + childHeight);

            left += itemWidthPlusMargin;
        }
        
        // 更新布局缓存
        if (layoutParamsChanged) {
            mCachedLayoutItemWidth = itemWidth;
            mCachedLayoutItemMargin = itemMargin;
            mCachedLayoutColumnCount = mColumnCount;
            mCachedLayoutLeft = l;
            mCachedLayoutTop = t;
            mCachedLayoutRight = r;
            mCachedLayoutBottom = b;
        }
    }
}
