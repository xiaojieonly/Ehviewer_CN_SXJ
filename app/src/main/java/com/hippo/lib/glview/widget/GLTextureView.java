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

package com.hippo.lib.glview.widget;

import android.graphics.RectF;

import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.glview.glrenderer.Texture;
import com.hippo.lib.glview.view.GLView;

public class GLTextureView extends GLView {

    private final RectF mSrc = new RectF();
    private final RectF mDst = new RectF();

    private Texture mTexture;

    public void setTexture(Texture texture) {
        mTexture = texture;
        if (texture != null) {
            mSrc.set(0.0f, 0.0f, texture.getWidth(), texture.getHeight());
        } else {
            mSrc.setEmpty();
        }
        invalidate();
    }

    public Texture getTexture() {
        return mTexture;
    }

    @Override
    protected int getSuggestedMinimumWidth() {
        return mTexture == null ? super.getSuggestedMinimumWidth() : mTexture.getWidth() + mPaddings.width();
    }

    @Override
    protected int getSuggestedMinimumHeight() {
        return mTexture == null ? super.getSuggestedMinimumWidth() : mTexture.getHeight() + mPaddings.height();
    }

    @Override
    public void onRender(GLCanvas canvas) {
        if (mTexture == null || mSrc.isEmpty()) {
            return;
        }

        mDst.set(mPaddings.left, mPaddings.top, getWidth() - mPaddings.right, getHeight() - mPaddings.bottom);
        if (mDst.isEmpty()) {
            return;
        }

        mTexture.draw(canvas, mSrc, mDst);
    }
}
