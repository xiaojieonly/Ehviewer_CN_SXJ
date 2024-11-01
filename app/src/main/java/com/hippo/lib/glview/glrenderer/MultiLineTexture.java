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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;


// MultiLineTexture is a texture shows the content of a specified String.
//
// To create a MultiLineTexture, use the newInstance() method and specify
// the String, the font size, and the color.
class MultiLineTexture extends CanvasTexture {
    private final Layout mLayout;

    private MultiLineTexture(Layout layout) {
        super(layout.getWidth(), layout.getHeight());
        mLayout = layout;
    }

    public static MultiLineTexture newInstance(
            String text, int maxWidth, float textSize, int color,
            Layout.Alignment alignment) {
        TextPaint paint = StringTexture.getDefaultPaint(textSize, color);
        Layout layout = new StaticLayout(text, 0, text.length(), paint,
                maxWidth, alignment, 1, 0, true, null, 0);

        return new MultiLineTexture(layout);
    }

    @Override
    protected void onDraw(Canvas canvas, Bitmap backing) {
        mLayout.draw(canvas);
    }
}
