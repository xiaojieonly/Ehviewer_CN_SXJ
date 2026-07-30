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

import com.hippo.scene.StageLayout;

/**
 * 场景容器。沉浸式改造后不再是 DrawerLayout 的直接子 View
 * (外层套了 MainContentLayout),窗口 inset 由 MainContentLayout 分发,
 * 不再实现 DrawerLayoutChild。
 */
public class EhStageLayout extends StageLayout {

    public EhStageLayout(Context context) {
        super(context);
    }

    public EhStageLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EhStageLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }
}
