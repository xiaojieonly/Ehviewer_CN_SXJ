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

import com.hippo.lib.glview.view.AnimationTime;
import com.hippo.lib.yorozuya.MathUtils;

// FadeTexture is a texture which fades the given texture along the time.
public abstract class FadeTexture implements Texture {

    // The duration of the fading animation in milliseconds
    public static final int DURATION = 180;

    private final long mStartTime;
    private final int mWidth;
    private final int mHeight;
    private final boolean mIsOpaque;
    private boolean mIsAnimating;

    public FadeTexture(int width, int height, boolean opaque) {
        mWidth = width;
        mHeight = height;
        mIsOpaque = opaque;
        mStartTime = now();
        mIsAnimating = true;
    }

    @Override
    public void draw(GLCanvas canvas, int x, int y) {
        draw(canvas, x, y, mWidth, mHeight);
    }

    @Override
    public boolean isOpaque() {
        return mIsOpaque;
    }

    @Override
    public int getWidth() {
        return mWidth;
    }

    @Override
    public int getHeight() {
        return mHeight;
    }

    public boolean isAnimating() {
        if (mIsAnimating) {
            if (now() - mStartTime >= DURATION) {
                mIsAnimating = false;
            }
        }
        return mIsAnimating;
    }

    protected float getRatio() {
        float r = (float)(now() - mStartTime) / DURATION;
        return MathUtils.clamp(1.0f - r, 0.0f, 1.0f);
    }

    private long now() {
        return AnimationTime.get();
    }
}
