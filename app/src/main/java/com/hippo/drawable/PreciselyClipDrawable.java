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

package com.hippo.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.hippo.yorozuya.MathUtils;

/**
 * Show a part of the original drawable
 */
public class PreciselyClipDrawable extends DrawableWrapper {

    private final boolean mClip;
    private RectF mScale;
    private Rect mTemp;

    public PreciselyClipDrawable(Drawable drawable, int offsetX, int offsetY, int width, int height) {
        super(drawable);
        float originWidth = drawable.getIntrinsicWidth();
        float originHeight = drawable.getIntrinsicHeight();

        if (originWidth <= 0 || originHeight <= 0) {
            // Can not clip
            mClip = false;
        } else {
            mClip = true;
            mScale = new RectF();
            mScale.set(MathUtils.clamp(offsetX / originWidth, 0.0f, 1.0f),
                    MathUtils.clamp(offsetY / originHeight, 0.0f, 1.0f),
                    MathUtils.clamp((offsetX + width) / originWidth, 0.0f, 1.0f),
                    MathUtils.clamp((offsetY + height) / originHeight, 0.0f, 1.0f));
            mTemp = new Rect();
        }
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (mClip) {
            if (!mScale.isEmpty()) {
                mTemp.left = (int) ((mScale.left * bounds.right - mScale.right * bounds.left) /
                        (mScale.left * (1 - mScale.right) - mScale.right * (1 - mScale.left)));
                mTemp.right = (int) (((1 - mScale.right) * bounds.left - (1 - mScale.left) * bounds.right) /
                        (mScale.left * (1 - mScale.right) - mScale.right * (1 - mScale.left)));
                mTemp.top = (int) ((mScale.top * bounds.bottom - mScale.bottom * bounds.top) /
                        (mScale.top * (1 - mScale.bottom) - mScale.bottom * (1 - mScale.top)));
                mTemp.bottom = (int) (((1 - mScale.bottom) * bounds.top - (1 - mScale.top) * bounds.bottom) /
                        (mScale.top * (1 - mScale.bottom) - mScale.bottom * (1 - mScale.top)));
                super.onBoundsChange(mTemp);
            }
        } else {
            super.onBoundsChange(bounds);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        if (mClip) {
            return (int) (super.getIntrinsicWidth() * mScale.width());
        } else {
            return super.getIntrinsicWidth();
        }
    }

    @Override
    public int getIntrinsicHeight() {
        if (mClip) {
            return (int) (super.getIntrinsicHeight() * mScale.height());
        } else {
            return super.getIntrinsicHeight();
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (mClip) {
            if (!mScale.isEmpty()) {
                int saveCount = canvas.save();
                canvas.clipRect(getBounds());
                super.draw(canvas);
                canvas.restoreToCount(saveCount);
            }
        } else {
            super.draw(canvas);
        }
    }
}
