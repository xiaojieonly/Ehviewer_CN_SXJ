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
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.widget.LinearLayout;
import androidx.annotation.NonNull;
import com.hippo.drawerlayout.DrawerLayoutChild;

public class EhNavigationView extends LinearLayout implements DrawerLayoutChild {

    private static final int SCRIM_COLOR = 0x44000000;
    private static final boolean DRAW_SCRIM = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    private Paint mPaint;
    private int mWindowPaddingTop;
    private int mWindowPaddingBottom;

    public EhNavigationView(Context context) {
        super(context);
        init();
    }

    public EhNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EhNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        if (DRAW_SCRIM) {
            setWillNotDraw(false);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        super.draw(canvas);

        if (DRAW_SCRIM && mWindowPaddingTop > 0) {
            if (null == mPaint) {
                mPaint = new Paint();
                mPaint.setColor(SCRIM_COLOR);
            }
            canvas.drawRect(0, 0, getWidth(), mWindowPaddingTop, mPaint);
        }
    }

    @Override
    public void onGetWindowPadding(int top, int bottom) {
        mWindowPaddingTop = top;
        mWindowPaddingBottom = bottom;
    }

    @Override
    public int getAdditionalTopMargin() {
        return 0;
    }

    @Override
    public int getAdditionalBottomMargin() {
        return mWindowPaddingBottom;
    }
}
