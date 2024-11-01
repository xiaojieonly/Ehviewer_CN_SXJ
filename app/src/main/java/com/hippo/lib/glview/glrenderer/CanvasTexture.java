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
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;

// CanvasTexture is a texture whose content is the drawing on a Canvas.
// The subclasses should override onDraw() to draw on the bitmap.
// By default CanvasTexture is not opaque.
abstract class CanvasTexture extends UploadedTexture {
    private final Config mConfig;

    public CanvasTexture(int width, int height) {
        mConfig = Config.ARGB_8888;
        setSize(width, height);
        setOpaque(false);
    }

    @Override
    protected Bitmap onGetBitmap() {
        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, mConfig);
        Canvas canvas = new Canvas(bitmap);
        onDraw(canvas, bitmap);
        return bitmap;
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        if (!inFinalizer()) {
            bitmap.recycle();
        }
    }

    abstract protected void onDraw(Canvas canvas, Bitmap backing);
}
