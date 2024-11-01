/*
 * Copyright 2015 Hippo Seven
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

package com.hippo.lib.glview.glrenderer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

public class XmlResourceTexture extends UploadedTexture {

    private final Context mContext;
    private final int mResId;
    private int mWidth;
    private int mHeight;

    public XmlResourceTexture(Context context, @DrawableRes int resId) {
        mContext = context;
        mResId = resId;
        setOpaque(false);
    }

    public void setBound(int width, int height) {
        mWidth = width;
        mHeight = height;
        invalidateContent();
    }

    @Override
    protected Bitmap onGetBitmap() {
        Drawable drawable = ContextCompat.getDrawable(mContext, mResId);

        int width = mWidth;
        int height = mHeight;
        if (width <= 0) {
            width = drawable.getIntrinsicWidth();
        }
        if (height <= 0) {
            height = drawable.getIntrinsicHeight();
        }
        if (width <= 0) {
            width = 1;
        }
        if (height <= 0) {
            height = 1;
        }

        drawable.setBounds(0, 0, width, height);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.draw(canvas);

        return bitmap;
    }

    @Override
    protected void onFreeBitmap(Bitmap bitmap) {
        if (!inFinalizer()) {
            bitmap.recycle();
        }
    }
}
