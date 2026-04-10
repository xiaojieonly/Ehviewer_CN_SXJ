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
import android.widget.LinearLayout;

import androidx.core.view.WindowInsetsCompat;

import com.hippo.drawerlayout.DrawerLayoutChild;
import com.hippo.ehviewer.ui.inset.WindowInsetHelper;

public class EhNavigationView extends LinearLayout implements DrawerLayoutChild {

    private int mWindowPaddingTop;

    public EhNavigationView(Context context) {
        super(context);
    }

    public EhNavigationView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EhNavigationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void onGetWindowPadding(int top, int bottom) {
        mWindowPaddingTop = top;
    }

    @Override
    public int getAdditionalTopMargin() {
        return 0;
    }

    @Override
    public int getAdditionalBottomMargin() {
        return 0;
    }

    public void applyNavigationInsets() {
        WindowInsetHelper.applyTopSystemBarToPadding(this);
    }

    public void applyDrawerWindowPadding(WindowInsetsCompat insets) {
        onGetWindowPadding(insets.getInsets(WindowInsetsCompat.Type.systemBars()).top,
                insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom);
    }
}
