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

// FadeOutTexture is a texture which begins with a given texture, then gradually animates
// into fading out totally.
public class FadeOutTexture extends FadeTexture {

    private final BasicTexture mTexture;

    public FadeOutTexture(BasicTexture texture) {
        super(texture.getWidth(), texture.getHeight(), texture.isOpaque());
        mTexture = texture;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y, int w, int h) {
        if (isAnimating()) {
            canvas.save(GLCanvas.SAVE_FLAG_ALPHA);
            canvas.setAlpha(getRatio());
            mTexture.draw(canvas, x, y, w, h);
            canvas.restore();
        }
    }

    @Override
    public void draw(GLCanvas canvas, RectF source, RectF target) {
        if (isAnimating()) {
            canvas.save(GLCanvas.SAVE_FLAG_ALPHA);
            canvas.setAlpha(getRatio());
            mTexture.draw(canvas, source, target);
            canvas.restore();
        }
    }
}
