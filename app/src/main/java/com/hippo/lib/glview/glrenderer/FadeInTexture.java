/*
 * Copyright (C) 2011 The Android Open Source Project
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

// FadeInTexture is a texture which begins with a color, then gradually animates
// into a given texture.
public class FadeInTexture extends FadeTexture implements Texture {

    private final int mColor;
    private final TiledTexture mTexture;

    public FadeInTexture(int color, TiledTexture texture) {
        super(texture.getWidth(), texture.getHeight(), texture.isOpaque());
        mColor = color;
        mTexture = texture;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        if (isAnimating()) {
            mTexture.drawMixed(canvas, mColor, getRatio(), x, y, w, h);
        } else {
            mTexture.draw(canvas, x, y, w, h);
        }
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF target) {
        if (isAnimating()) {
            mTexture.drawMixed(canvas, mColor, getRatio(), source, target);
        } else {
            mTexture.draw(canvas, source, target);
        }
    }
}
