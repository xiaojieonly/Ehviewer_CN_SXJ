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

package com.hippo.lib.glview.image;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.yorozuya.AssertUtils;

public class ImageSpriteTexture extends ImageTexture {

    private final int mCount;
    private final int[] mRects;

    private final RectF mTempSource = new RectF();
    private final RectF mTempTarget = new RectF();

    public ImageSpriteTexture(@NonNull ImageWrapper image, int count, int[] rects) {
        super(image);

        AssertUtils.assertEquals("rects.length must be count * 4", count * 4, rects.length);
        mCount = count;
        mRects = rects;
    }

    public int getCount() {
        return mCount;
    }

    public void drawSprite(GLCanvas canvas, int index, int x, int y) {
        int[] rects = mRects;
        int offset = index * 4;
        int sourceX = rects[offset];
        int sourceY = rects[offset + 1];
        int sourceWidth = rects[offset + 2];
        int sourceHeight = rects[offset + 3];
        mTempSource.set(sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight);
        mTempTarget.set(x, y, x + sourceWidth, y + sourceHeight);
        draw(canvas, mTempSource, mTempTarget);
    }

    public void drawSprite(GLCanvas canvas, int index, int x, int y, int width, int height) {
        int[] rects = mRects;
        int offset = index * 4;
        int sourceX = rects[offset];
        int sourceY = rects[offset + 1];
        mTempSource.set(sourceX, sourceY, sourceX + rects[offset + 2], sourceY + rects[offset + 3]);
        mTempTarget.set(x, y, x + width, y + height);
        draw(canvas, mTempSource, mTempTarget);
    }

    public void drawSpriteMixed(GLCanvas canvas, int index, int color, float ratio, int x, int y) {
        int[] rects = mRects;
        int offset = index * 4;
        int sourceX = rects[offset];
        int sourceY = rects[offset + 1];
        int sourceWidth = rects[offset + 2];
        int sourceHeight = rects[offset + 3];
        mTempSource.set(sourceX, sourceY, sourceX + sourceWidth, sourceY + sourceHeight);
        mTempTarget.set(x, y, x + sourceWidth, y + sourceHeight);
        drawMixed(canvas, color, ratio, mTempSource, mTempTarget);
    }

    public void drawSpriteMixed(GLCanvas canvas, int index, int color, float ratio,
                                int x, int y, int width, int height) {
        int[] rects = mRects;
        int offset = index * 4;
        int sourceX = rects[offset];
        int sourceY = rects[offset + 1];
        mTempSource.set(sourceX, sourceY, sourceX + rects[offset + 2],
                sourceY + rects[offset + 3]);
        mTempTarget.set(x, y, x + width, y + height);
        drawMixed(canvas, color, ratio, mTempSource, mTempTarget);
    }
}
