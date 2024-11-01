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

import com.hippo.lib.glview.view.GLView;
import com.hippo.lib.glview.view.Gravity;

public class GLFrameLayout extends GLView {

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int maxWidth = 0;
        int maxHeight = 0;
        for (int i = 0, n = getComponentCount(); i < n; i++) {
            GLView component = getComponent(i);
            if (component.getVisibility() == GONE) {
                continue;
            }
            measureComponent(component, widthSpec, heightSpec);
            maxWidth = Math.max(maxWidth, component.getMeasuredWidth());
            maxHeight = Math.max(maxHeight, component.getMeasuredHeight());
        }

        // Consider min
        maxWidth = Math.max(maxWidth, getSuggestedMinimumWidth());
        maxHeight = Math.max(maxHeight, getSuggestedMinimumHeight());

        // The final
        maxWidth = getDefaultSize(maxWidth, widthSpec);
        maxHeight = getDefaultSize(maxHeight, heightSpec);

        setMeasuredSize(maxWidth, maxHeight);

        if (MeasureSpec.getSize(widthSpec) != MeasureSpec.EXACTLY ||
                MeasureSpec.getSize(heightSpec) != MeasureSpec.EXACTLY) {
            // Measure again
            measureAllComponents(this, MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right, int bottom) {
        int width = getWidth();
        int height = getHeight();

        for (int i = 0, n = getComponentCount(); i < n; i++) {
            GLView component = getComponent(i);
            if (component.getVisibility() == GONE) {
                continue;
            }
            int measureWidth = component.getMeasuredWidth();
            int measureHeight = component.getMeasuredHeight();
            int componentLeft;
            int componentTop;

            GravityLayoutParams lp = (GravityLayoutParams) component.getLayoutParams();
            int gravity = lp.gravity;
            if (Gravity.centerHorizontal(gravity)) {
                componentLeft = (width / 2) - (measureWidth / 2);
            } else if (Gravity.right(gravity)) {
                componentLeft = width - measureWidth;
            } else {
                componentLeft = 0;
            }
            if (Gravity.centerVertical(gravity)) {
                componentTop = (height / 2) - (measureHeight / 2);
            } else if (Gravity.bottom(gravity)) {
                componentTop = height - measureHeight;
            } else {
                componentTop = 0;
            }

            component.layout(componentLeft, componentTop,
                    componentLeft + measureWidth, componentTop + measureHeight);
        }
    }

    @Override
    protected boolean checkLayoutParams(GLView.LayoutParams p) {
        return p instanceof GravityLayoutParams;
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new GravityLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(GLView.LayoutParams p) {
        return p == null ? generateDefaultLayoutParams() : new GravityLayoutParams(p);
    }
}
