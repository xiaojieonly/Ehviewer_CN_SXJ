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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.hippo.ehviewer.R;
import com.hippo.yorozuya.AssertUtils;

public class MaxSizeContainer extends ViewGroup {

    private int mMaxWidth;
    private int mMaxHeight;

    public MaxSizeContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public MaxSizeContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.MaxSizeContainer);
        mMaxWidth = a.getDimensionPixelOffset(R.styleable.MaxSizeContainer_maxWidth, -1);
        mMaxHeight = a.getDimensionPixelOffset(R.styleable.MaxSizeContainer_maxHeight, -1);
        a.recycle();
    }

    private int getMeasureSpec(int measureSpec, int max) {
        if (max < 0) {
            return measureSpec;
        }

        int size = MeasureSpec.getSize(measureSpec);
        int mode = MeasureSpec.getMode(measureSpec);

        switch (mode) {
            case MeasureSpec.AT_MOST:
                size = Math.min(size, max);
                break;
            case MeasureSpec.UNSPECIFIED:
                size = max;
                mode = MeasureSpec.AT_MOST;
                break;
        }
        return MeasureSpec.makeMeasureSpec(size, mode);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        AssertUtils.assertEquals("getChildCount() must be 1", 1, getChildCount());

        View child = getChildAt(0);
        if (child.getVisibility() != GONE) {
            child.measure(getMeasureSpec(widthMeasureSpec, mMaxWidth),
                    getMeasureSpec(heightMeasureSpec, mMaxHeight));
            setMeasuredDimension(child.getMeasuredWidth(), child.getMeasuredHeight());
        } else {
            setMeasuredDimension(0, 0);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        AssertUtils.assertEquals("getChildCount() must be 1", 1, getChildCount());

        View child = getChildAt(0);
        if (child.getVisibility() != GONE) {
            child.layout(0, 0, r - l, b - t);
        }
    }
}
