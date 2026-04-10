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
import android.util.AttributeSet;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.hippo.drawerlayout.DrawerLayoutChild;
import com.hippo.ehviewer.ui.inset.WindowInsetHelper;
import com.hippo.widget.DrawerView;

public class EhDrawerView extends DrawerView implements DrawerLayoutChild {

    private int mWindowPaddingTop;
    private int mWindowPaddingBottom;

    public EhDrawerView(Context context) {
        super(context);
    }

    public EhDrawerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EhDrawerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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
        return 0;
    }

    public void applyDrawerInsets() {
        WindowInsetHelper.applyVerticalSystemBarsToPadding(this);
    }

    public void applyDrawerWindowPadding(WindowInsetsCompat insets) {
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        onGetWindowPadding(bars.top, bars.bottom);
        // Do NOT apply the top inset here — the inner Toolbar handles the status bar inset
        // itself, so its background color extends behind the transparent status bar. Applying
        // top padding on the drawer root would show the drawer's windowBackground behind the
        // status bar, producing a visible color seam above the Toolbar.
        setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), mWindowPaddingBottom);
    }
}
