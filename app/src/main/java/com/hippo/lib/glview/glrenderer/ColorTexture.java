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

import android.graphics.RectF;

import com.hippo.lib.yorozuya.ColorUtils;

// ColorTexture is a texture which fills the rectangle with the specified color.
public class ColorTexture implements Texture {

    private final int mColor;
    private int mWidth = 0;
    private int mHeight = 0;

    public ColorTexture(int color) {
        mColor = color;
    }

    private boolean isValidSize() {
        return mWidth > 0 && mHeight > 0;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y) {
        if (isValidSize()) {
            draw(canvas, x, y, mWidth, mHeight);
        }
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        canvas.fillRect(x, y, w, h, mColor);
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF target) {
        canvas.fillRect(target.left, target.top, target.width(), target.height(), mColor);
    }

    @Override
    public boolean isOpaque() {
        return ColorUtils.isOpaque(mColor);
    }

    public void setSize(int width, int height) {
        mWidth = width;
        mHeight = height;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }
}
