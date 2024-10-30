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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.image.Image;

import java.util.Arrays;

public class ImageMovableTextTexture extends ImageSpriteTexture {

    private final char[] mCharacters;
    private final float[] mWidths;
    private final float mHeight;
    private final float mMaxWidth;

    public ImageMovableTextTexture(@NonNull ImageWrapper image, int count, int[] rects,
                                   char[] characters, float[] widths, float height, float maxWidth) {
        super(image, count, rects);

        mCharacters = characters;
        mWidths = widths;
        mHeight = height;
        mMaxWidth = maxWidth;
    }

    /**
     * Create a TextTexture to draw text
     *
     * @param typeface   the typeface
     * @param size       text size
     * @param characters all Characters
     * @return the TextTexture
     */
    @Nullable
    public static ImageMovableTextTexture create(Typeface typeface, int size, int color, char[] characters) {
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setTextSize(size);
        paint.setColor(color);
        paint.setTypeface(typeface);

        Paint.FontMetricsInt fmi = paint.getFontMetricsInt();
        int fixed = fmi.bottom;
        int height = fmi.bottom - fmi.top;

        int length = characters.length;
        float[] widths = new float[length];
        paint.getTextWidths(characters, 0, length, widths);

        // Calculate bitmap size
        float maxWidth = 0.0f;
        for (float f : widths) {
            maxWidth = Math.max(maxWidth, f);
        }
        int hCount = (int) Math.ceil(Math.sqrt(height / maxWidth * length));
        int vCount = (int) Math.ceil(Math.sqrt(maxWidth / height * length));
        if (hCount * (vCount - 1) > length) {
            vCount--;
        }
        if ((hCount - 1) * vCount > length) {
            hCount--;
        }

        Bitmap bitmap = Bitmap.createBitmap((int) Math.ceil(hCount * maxWidth),
                (int) Math.ceil(vCount * height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, height - fixed);

        // Draw
        int[] rects = new int[length * 4];
        int x = 0;
        int y = 0;
        for (int i = 0; i < length; i++) {
            int offset = i * 4;
            rects[offset] = x;
            rects[offset + 1] = y;
            rects[offset + 2] = (int) widths[i];
            rects[offset + 3] = height;

            canvas.drawText(characters, i, 1, x, y, paint);

            if (i % hCount == hCount - 1) {
                // The end of row
                x = 0;
                y += height;
            } else {
                x += maxWidth;
            }
        }

        Image image = Image.create(bitmap);
        if (image == null) {
            return null;
        }
        ImageWrapper imageWrapper = new ImageWrapper(image);
        if (imageWrapper.obtain()) {
            return new ImageMovableTextTexture(imageWrapper, length, rects, characters, widths, height, maxWidth);
        } else {
            return null;
        }
    }

    public int[] getTextIndexes(String text) {
        char[] characters = mCharacters;

        int length = text.length();
        int[] indexes = new int[length];
        for (int i = 0; i < length; i++) {
            char ch = text.charAt(i);
            indexes[i] = Arrays.binarySearch(characters, ch);
        }

        return indexes;
    }

    public float getTextWidth(String text) {
        char[] characters = mCharacters;
        float[] widths = mWidths;
        float width = 0.0f;

        for (int i = 0, n = text.length(); i < n; i++) {
            char ch = text.charAt(i);
            int index = Arrays.binarySearch(characters, ch);
            if (index >= 0) {
                width += widths[index];
            } else {
                width += mMaxWidth;
            }
        }

        return width;
    }

    public float getTextWidth(int[] indexes) {
        float[] widths = mWidths;
        float width = 0.0f;
        int length = indexes.length;
        for (int i = 0; i < length; i++) {
            int index = indexes[i];
            if (index >= 0) {
                width += widths[index];
            } else {
                width += mMaxWidth;
            }
        }

        return width;
    }

    public float getMaxWidth() {
        return mMaxWidth;
    }

    public float getTextHeight() {
        return mHeight;
    }

    public void drawText(GLCanvas canvas, String text, int x, int y) {
        char[] characters = mCharacters;
        float[] widths = mWidths;

        for (int i = 0, n = text.length(); i < n; i++) {
            char ch = text.charAt(i);
            int index = Arrays.binarySearch(characters, ch);
            if (index >= 0) {
                drawSprite(canvas, index, x, y);
                x += widths[index];
            } else {
                x += mMaxWidth;
            }
        }
    }

    public void drawText(GLCanvas canvas, int[] indexes, int x, int y) {
        float[] widths = mWidths;

        for (int i = 0, n = indexes.length; i < n; i++) {
            int index = indexes[i];
            if (index >= 0) {
                drawSprite(canvas, index, x, y);
                x += widths[index];
            } else {
                x += mMaxWidth;
            }
        }
    }
}
