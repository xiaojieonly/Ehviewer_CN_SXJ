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

package com.hippo.reveal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

public class RevealView extends View implements Reveal {

    private final Path mRevealPath = new Path();
    private boolean mReveal;
    private int mCenterX;
    private int mCenterY;
    private float mRadius;

    public RevealView(Context context) {
        super(context);
    }

    public RevealView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public RevealView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setRevealEnable(boolean enable) {
        mReveal = enable;
        invalidate();
    }

    @Override
    public void setReveal(int centerX, int centerY, float radius) {
        mCenterX = centerX;
        mCenterY = centerY;
        mRadius = radius;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        boolean reveal = mReveal;
        int saveCount = 0;

        if (reveal) {
            saveCount = canvas.save();
            Path path = mRevealPath;
            path.reset();
            path.addCircle(mCenterX, mCenterY, mRadius, Path.Direction.CW);
            canvas.clipPath(path);
        }

        super.draw(canvas);

        if (reveal) {
            canvas.restoreToCount(saveCount);
        }
    }
}
