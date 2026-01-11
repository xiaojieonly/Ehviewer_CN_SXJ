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

import java.lang.reflect.Field;

public class SimpleGridAutoSpanLayout extends SimpleGridLayout {

    public static final int STRATEGY_MIN_SIZE = 0;
    public static final int STRATEGY_SUITABLE_SIZE = 1;

    private int mColumnSize = -1;
    private boolean mColumnSizeChanged = true;
    private int mStrategy;

    // 缓存字段，用于性能优化
    private int mLastWidthSize = -1;
    private int mLastTotalSpace = -1;
    private int mCachedPaddingLeft = Integer.MIN_VALUE;
    private int mCachedPaddingRight = Integer.MIN_VALUE;

    // 用于反射访问父类的 mColumnCount 字段
    private static Field sColumnCountField;
    private static boolean sColumnCountFieldInitialized = false;

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
        // 清除缓存，确保下次 onMeasure 时重新计算
        mLastWidthSize = -1;
        mLastTotalSpace = -1;
    }

    public void setStrategy(int strategy) {
        if (strategy == mStrategy) {
            return;
        }
        mStrategy = strategy;
        mColumnSizeChanged = true;
        // 清除缓存，确保下次 onMeasure 时重新计算
        mLastWidthSize = -1;
        mLastTotalSpace = -1;
    }

    /**
     * 直接设置列数而不触发 requestLayout()，用于在 onMeasure 中优化性能
     */
    private void setColumnCountDirectly(int columnCount) {
        if (columnCount <= 0) {
            throw new IllegalStateException("Column count can't be " + columnCount);
        }

        // 使用反射访问父类的 mColumnCount 字段，避免触发 requestLayout()
        try {
            if (!sColumnCountFieldInitialized) {
                sColumnCountField = SimpleGridLayout.class.getDeclaredField("mColumnCount");
                sColumnCountField.setAccessible(true);
                sColumnCountFieldInitialized = true;
            }
            int currentCount = sColumnCountField.getInt(this);
            if (currentCount != columnCount) {
                sColumnCountField.setInt(this, columnCount);
            }
        } catch (Exception e) {
            // 如果反射失败，回退到使用公共方法（会触发 requestLayout）
            setColumnCount(columnCount);
        }
    }

    public static int getSpanCountForSuitableSize(int total, int single) {
        int span = total / single;
        if (span <= 0) {
            return 1;
        }
        int span2 = span + 1;
        // 使用整数计算代替浮点数，避免浮点运算开销
        // 比较 |total - span * single| * span2 和 |total - span2 * single| * span
        // 等价于比较偏差的绝对值
        long diff1 = Math.abs((long) total - (long) span * single);
        long diff2 = Math.abs((long) total - (long) span2 * single);
        // 比较 diff1 * span2 和 diff2 * span，避免除法
        return (diff1 * span2 < diff2 * span) ? span : span2;
    }

    public static int getSpanCountForMinSize(int total, int single) {
        return Math.max(1, total / single);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        // 检查是否需要重新计算列数
        boolean needRecalculate = false;
        if (mColumnSize > 0 && widthMode == MeasureSpec.EXACTLY) {
            // 获取并缓存 padding 值
            int paddingLeft = getPaddingLeft();
            int paddingRight = getPaddingRight();
            
            // 检查 padding 是否变化
            boolean paddingChanged = (paddingLeft != mCachedPaddingLeft) || 
                                     (paddingRight != mCachedPaddingRight);
            
            // 计算 totalSpace
            int totalSpace = widthSize - paddingRight - paddingLeft;
            
            // 检查是否需要重新计算：列大小变化、策略变化、宽度变化或 padding 变化
            if (mColumnSizeChanged || widthSize != mLastWidthSize || 
                totalSpace != mLastTotalSpace || paddingChanged) {
                needRecalculate = true;
                
                // 更新缓存
                mCachedPaddingLeft = paddingLeft;
                mCachedPaddingRight = paddingRight;
                mLastWidthSize = widthSize;
                mLastTotalSpace = totalSpace;
            }
            
            if (needRecalculate) {
                int spanCount;
                // 使用 if-else 代替 switch，优化性能
                if (mStrategy == STRATEGY_SUITABLE_SIZE) {
                    spanCount = getSpanCountForSuitableSize(totalSpace, mColumnSize);
                } else {
                    // STRATEGY_MIN_SIZE 或默认情况
                    spanCount = getSpanCountForMinSize(totalSpace, mColumnSize);
                }
                // 直接设置列数，避免触发 requestLayout()
                setColumnCountDirectly(spanCount);
                mColumnSizeChanged = false;
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
