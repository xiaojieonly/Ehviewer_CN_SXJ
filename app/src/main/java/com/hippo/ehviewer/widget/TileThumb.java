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

import com.hippo.widget.LoadImageView;
import com.hippo.yorozuya.MathUtils;

public class TileThumb extends LoadImageView {

    private static final float MIN_ASPECT = 0.33f;
    private static final float MAX_ASPECT = 1.5f;
    private static final float DEFAULT_ASPECT = 0.67f;

    public TileThumb(Context context) {
        super(context);
    }

    public TileThumb(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TileThumb(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setThumbSize(int thumbWidth, int thumbHeight) {
        float aspect;
        if (thumbWidth > 0 && thumbHeight > 0) {
            aspect = MathUtils.clamp(thumbWidth / (float) thumbHeight, MIN_ASPECT, MAX_ASPECT);
        } else {
            aspect = DEFAULT_ASPECT;
        }
        setAspect(aspect);
    }
}
