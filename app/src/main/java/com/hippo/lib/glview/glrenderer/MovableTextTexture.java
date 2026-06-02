package com.hippo.lib.glview.glrenderer;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.util.Arrays;

/**
 * Works like movable type.<br>
 * <br>
 * Supports multiline text with '\n' as line separator
 */
public final class MovableTextTexture extends SpriteTexture {

    private final char[] mCharacters;
    private final float[] mWidths;
    private final float mHeight;
    private final float mMaxWidth;
    private final float mLineSpacing; // 行间距

    private MovableTextTexture(Bitmap bitmap, int count, int[] rects, char[] characters,
            float[] widths, float height, float maxWidth) {
        this(bitmap, count, rects, characters, widths, height, maxWidth, 0f);
    }

    private MovableTextTexture(Bitmap bitmap, int count, int[] rects, char[] characters,
            float[] widths, float height, float maxWidth, float lineSpacing) {
        super(bitmap, false, count, rects);
        mCharacters = characters;
        mWidths = widths;
        mHeight = height;
        mMaxWidth = maxWidth;
        mLineSpacing = lineSpacing;
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

        int startX = x;
        for (int i = 0, n = text.length(); i < n; i++) {
            char ch = text.charAt(i);
            if (ch == '\n') {
                // 换行
                x = startX;
                y += mHeight + mLineSpacing;
                continue;
            }
            
            int index = Arrays.binarySearch(characters, ch);
            if (index >= 0) {
                drawSprite(canvas, index, x, y);
                x += widths[index];
            } else {
                x += mMaxWidth;
            }
        }
    }

    /**
     * 绘制多行文本，自动计算总高度
     * @param canvas GL画布
     * @param text 要绘制的文本（可包含\n换行符）
     * @param x 起始x坐标
     * @param y 起始y坐标
     * @return 文本的总高度
     */
    public float drawMultilineText(GLCanvas canvas, String text, int x, int y) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String[] lines = text.split("\n", -1); // -1保留末尾的空字符串
        float totalHeight = 0;
        
        for (int i = 0; i < lines.length; i++) {
            drawText(canvas, lines[i], x, y);
            totalHeight += mHeight;
            if (i < lines.length - 1) {
                totalHeight += mLineSpacing;
            }
            y += mHeight + mLineSpacing;
        }
        
        return totalHeight;
    }

    /**
     * 计算多行文本的总高度
     * @param text 文本内容
     * @return 总高度
     */
    public float calculateMultilineHeight(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int lineCount = 1;
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                lineCount++;
            }
        }
        
        return lineCount * mHeight + (lineCount - 1) * mLineSpacing;
    }

    /**
     * 计算多行文本的最大宽度
     * @param text 文本内容
     * @return 最大宽度
     */
    public float calculateMultilineWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        String[] lines = text.split("\n", -1);
        float maxWidth = 0;
        
        for (String line : lines) {
            float lineWidth = getTextWidth(line);
            if (lineWidth > maxWidth) {
                maxWidth = lineWidth;
            }
        }
        
        return maxWidth;
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

    /**
     * Create a TextTexture to draw text
     *
     * @param typeface the typeface
     * @param size text size
     * @param characters all Characters
     * @return the TextTexture
     */
    public static MovableTextTexture create(Typeface typeface, int size, int color, char[] characters) {
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
            rects[offset + 0] = x;
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

        return new MovableTextTexture(bitmap, length, rects, characters, widths, height, maxWidth);
    }
}
