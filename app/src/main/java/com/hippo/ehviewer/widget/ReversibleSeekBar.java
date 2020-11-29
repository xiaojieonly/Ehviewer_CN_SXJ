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

package com.hippo.ehviewer.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import androidx.appcompat.widget.AppCompatSeekBar;

public class ReversibleSeekBar extends AppCompatSeekBar {

    private boolean mReverse;

    public ReversibleSeekBar(Context context) {
        super(context);
    }

    public ReversibleSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ReversibleSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setReverse(boolean reverse) {
        mReverse = reverse;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        boolean reverse = mReverse;
        int saveCount = 0;
        if (reverse) {
            saveCount = canvas.save();
            float px = this.getWidth() / 2.0f;
            float py = this.getHeight() / 2.0f;
            canvas.scale(-1, 1, px, py);
        }
        super.draw(canvas);
        if (reverse) {
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean reverse = mReverse;
        float x = 0.0f, y = 0.0f;
        if (reverse) {
            x = event.getX();
            y = event.getY();
            event.setLocation(getWidth() - x, y);
        }
        boolean result = super.onTouchEvent(event);
        if (reverse) {
            event.setLocation(x, y);
        }
        return result;
    }
}
