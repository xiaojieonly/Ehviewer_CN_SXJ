/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.lib.glview.glrenderer;

public class GLPaint {
    private float mLineWidth = 1f;
    private int mColor = 0;
    private int mBackgroundColor = 0;

    public void setColor(int color) {
        mColor = color;
    }

    public int getColor() {
        return mColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        mBackgroundColor = backgroundColor;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public void setLineWidth(float width) {
        if (width < 0) {
            throw new IllegalArgumentException("width < 0");
        }
        mLineWidth = width;
    }

    public float getLineWidth() {
        return mLineWidth;
    }
}
