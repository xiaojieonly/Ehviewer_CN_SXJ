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

package com.hippo.widget.recyclerview;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import java.util.ArrayList;
import java.util.List;

public class AutoStaggeredGridLayoutManager extends StaggeredGridLayoutManager {

    public static final int STRATEGY_MIN_SIZE = 0;
    public static final int STRATEGY_SUITABLE_SIZE = 1;

    private int mColumnSize = -1;
    private boolean mColumnSizeChanged = true;
    private int mStrategy;

    private List<OnUpdateSpanCountListener> mListeners;

    public AutoStaggeredGridLayoutManager(int columnSize, int orientation) {
        super(1, orientation);
        setColumnSize(columnSize);
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
    public void onMeasure(RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec, int heightSpec) {
        if (mColumnSizeChanged && mColumnSize > 0) {
            int totalSpace;
            if (getOrientation() == VERTICAL) {
                if (View.MeasureSpec.EXACTLY != View.MeasureSpec.getMode(widthSpec)) {
                    throw new IllegalStateException("RecyclerView need a fixed width for AutoStaggeredGridLayoutManager");
                }
                totalSpace = View.MeasureSpec.getSize(widthSpec) - getPaddingRight() - getPaddingLeft();
            } else {
                if (View.MeasureSpec.EXACTLY != View.MeasureSpec.getMode(heightSpec)) {
                    throw new IllegalStateException("RecyclerView need a fixed height for AutoStaggeredGridLayoutManager");
                }
                totalSpace = View.MeasureSpec.getSize(heightSpec) - getPaddingTop() - getPaddingBottom();
            }

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
            setSpanCount(spanCount);
            mColumnSizeChanged = false;

            if (null != mListeners) {
                for (int i = 0, n = mListeners.size(); i < n; i++) {
                    mListeners.get(i).onUpdateSpanCount(spanCount);
                }
            }
        }
        super.onMeasure(recycler, state, widthSpec, heightSpec);
    }

    public void addOnUpdateSpanCountListener(OnUpdateSpanCountListener listener) {
        if (null == mListeners) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(listener);
    }

    public void removeOnUpdateSpanCountListener(OnUpdateSpanCountListener listener) {
        if (null != mListeners) {
            mListeners.remove(listener);
        }
    }

    public interface OnUpdateSpanCountListener {
        void onUpdateSpanCount(int spanCount);
    }
}
