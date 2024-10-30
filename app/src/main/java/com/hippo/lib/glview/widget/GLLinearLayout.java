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

package com.hippo.lib.glview.widget;

import android.graphics.Rect;

import androidx.annotation.IntDef;

import com.hippo.lib.glview.view.GLView;
import com.hippo.lib.glview.view.Gravity;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

public class GLLinearLayout extends GLView {

    @IntDef({HORIZONTAL, VERTICAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface OrientationMode {}

    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    private int mInterval = 0;
    private int mOrientation = VERTICAL;

    private final List<GLView> mTempList = new ArrayList<>();

    public void setInterval(int interval) {
        if (mInterval != interval) {
            mInterval = interval;
            requestLayout();
        }
    }

    /**
     * Should the layout be a column or a row.
     * @param orientation Pass {@link #HORIZONTAL} or {@link #VERTICAL}. Default
     * value is {@link #HORIZONTAL}.
     */
    public void setOrientation(@OrientationMode int orientation) {
        if (mOrientation != orientation) {
            mOrientation = orientation;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int layoutWidthSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec) - mPaddings.left - mPaddings.right,
                MeasureSpec.getMode(widthSpec));
        int layoutHeightSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(heightSpec) - mPaddings.top - mPaddings.bottom,
                MeasureSpec.getMode(heightSpec));

        if (mOrientation == HORIZONTAL && MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY) {
            int width = MeasureSpec.getSize(layoutWidthSpec);
            float sumWeight = 0.0f;

            for (int i = 0, n = getComponentCount(); i < n; i++) {
                final GLView component = getComponent(i);
                if (component.getVisibility() == GONE) {
                    continue;
                }
                final LayoutParams lp = (LayoutParams) component.getLayoutParams();

                if (lp.weight > 0.0f) {
                    sumWeight += lp.weight;
                    mTempList.add(component);
                } else {
                    measureComponent(component, layoutWidthSpec, layoutHeightSpec);
                    width -= component.getMeasuredWidth();
                }
            }

            for (GLView component : mTempList) {
                final LayoutParams lp = (LayoutParams) component.getLayoutParams();
                final int componentWidthSpec = MeasureSpec.makeMeasureSpec(
                        (int) (width * lp.weight / sumWeight), MeasureSpec.EXACTLY);
                final int componentHeightSpec = getComponentSpec(layoutHeightSpec, lp.height);
                component.measure(componentWidthSpec, componentHeightSpec);
            }

            mTempList.clear();
        } else if (mOrientation == VERTICAL && MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY) {
            int height = MeasureSpec.getSize(layoutHeightSpec);
            float sumWeight = 0.0f;

            for (int i = 0, n = getComponentCount(); i < n; i++) {
                final GLView component = getComponent(i);
                if (component.getVisibility() == GONE) {
                    continue;
                }
                final LayoutParams lp = (LayoutParams) component.getLayoutParams();

                if (lp.weight > 0.0f) {
                    sumWeight += lp.weight;
                    mTempList.add(component);
                } else {
                    measureComponent(component, layoutWidthSpec, layoutHeightSpec);
                    height -= component.getMeasuredHeight();
                }
            }

            for (GLView component : mTempList) {
                final LayoutParams lp = (LayoutParams) component.getLayoutParams();
                final int componentWidthSpec = getComponentSpec(layoutWidthSpec, lp.width);
                final int componentHeightSpec = MeasureSpec.makeMeasureSpec(
                        (int) (height * lp.weight / sumWeight), MeasureSpec.EXACTLY);
                component.measure(componentWidthSpec, componentHeightSpec);
            }

            mTempList.clear();
        } else {
            measureAllComponents(this, layoutWidthSpec, layoutHeightSpec);
        }

        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0, n = getComponentCount(); i < n; i++) {
            GLView component = getComponent(i);
            if (component.getVisibility() == GONE) {
                continue;
            }

            if (mOrientation == VERTICAL) {
                maxWidth = Math.max(maxWidth, component.getMeasuredWidth());
                if (i != 0) {
                    maxHeight += mInterval;
                }
                maxHeight += component.getMeasuredHeight();
            } else if (mOrientation == HORIZONTAL) {
                maxHeight = Math.max(maxHeight, component.getMeasuredHeight());
                if (i != 0) {
                    maxWidth += mInterval;
                }
                maxWidth += component.getMeasuredWidth();
            }
        }

        Rect paddings = getPaddings();
        maxWidth = maxWidth + paddings.left + paddings.right;
        maxHeight = maxHeight + paddings.top + paddings.bottom;

        setMeasuredSize(getDefaultSize(maxWidth, widthSpec), getDefaultSize(maxHeight, heightSpec));
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
        int width = getWidth();
        int height = getHeight();
        Rect paddings = getPaddings();
        int componentLeft;
        int componentTop;

        if (mOrientation == VERTICAL) {
            componentTop = paddings.top;

            for (int i = 0, n = getComponentCount(); i < n; i++) {
                GLView component = getComponent(i);
                if (component.getVisibility() == GONE) {
                    continue;
                }
                int measureWidth = component.getMeasuredWidth();
                int measureHeight = component.getMeasuredHeight();

                LayoutParams lp = (LayoutParams) component.getLayoutParams();
                componentLeft = getDefaultBegin(width, measureWidth, paddings.left, paddings.right,
                        Gravity.getPosition(lp.gravity, Gravity.HORIZONTAL));
                component.layout(componentLeft, componentTop,
                        componentLeft + measureWidth, componentTop + measureHeight);

                componentTop += measureHeight + mInterval;
            }
        } else if (mOrientation == HORIZONTAL) {
            componentLeft = paddings.left;

            for (int i = 0, n = getComponentCount(); i < n; i++) {
                GLView component = getComponent(i);
                if (component.getVisibility() == GONE) {
                    continue;
                }
                int measureWidth = component.getMeasuredWidth();
                int measureHeight = component.getMeasuredHeight();

                LayoutParams lp = (LayoutParams) component.getLayoutParams();
                componentTop = getDefaultBegin(height, measureHeight, paddings.top, paddings.bottom,
                        Gravity.getPosition(lp.gravity, Gravity.VERTICAL));
                component.layout(componentLeft, componentTop,
                        componentLeft + measureWidth, componentTop + measureHeight);

                componentLeft += measureWidth + mInterval;
            }
        }
    }

    @Override
    protected boolean checkLayoutParams(GLView.LayoutParams p) {
        return p instanceof LayoutParams;
    }

    @Override
    protected GLView.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected GLView.LayoutParams generateLayoutParams(GLView.LayoutParams p) {
        return p == null ? generateDefaultLayoutParams() : new LayoutParams(p);
    }

    public static class LayoutParams extends GravityLayoutParams {

        public float weight = 0.0f;

        public LayoutParams(GLView.LayoutParams source) {
            super(source);

            if (source instanceof LayoutParams) {
                weight = ((LayoutParams) source).weight;
            }
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }
    }
}
