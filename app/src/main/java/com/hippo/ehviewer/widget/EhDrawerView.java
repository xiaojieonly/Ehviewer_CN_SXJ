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

import com.hippo.drawerlayout.DrawerLayoutChild;
import com.hippo.widget.DrawerView;

/**
 * 右侧抽屉:全高布局(背景延伸进状态栏/系统导航栏区域,沉浸式),
 * 内容由内部 padding 避让系统栏;库按 DrawerLayoutChild 的
 * additional*Margin 避让会破坏沉浸感,故全部返回 0
 */
public class EhDrawerView extends DrawerView implements DrawerLayoutChild {

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
        if (getPaddingTop() != top || getPaddingBottom() != bottom) {
            setPadding(getPaddingLeft(), top, getPaddingRight(), bottom);
        }
    }

    @Override
    public int getAdditionalTopMargin() {
        return 0;
    }

    public int getAdditionalBottomMargin() {
        return 0;
    }
}
