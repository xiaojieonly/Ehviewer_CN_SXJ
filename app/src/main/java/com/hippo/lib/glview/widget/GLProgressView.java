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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;

import androidx.core.view.animation.PathInterpolatorCompat;

import com.hippo.lib.glview.anim.Animation;
import com.hippo.lib.glview.anim.FloatAnimation;
import com.hippo.lib.glview.glrenderer.BitmapTexture;
import com.hippo.lib.glview.glrenderer.GLCanvas;
import com.hippo.lib.glview.glrenderer.GLPaint;
import com.hippo.lib.glview.view.AnimationTime;
import com.hippo.lib.glview.view.GLView;

import java.util.ArrayList;
import java.util.List;

public class GLProgressView extends GLView {

    private static final Interpolator TRIM_START_INTERPOLATOR;
    private static final Interpolator TRIM_END_INTERPOLATOR;
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    static {
        Path trimStartPath = new Path();
        trimStartPath.moveTo(0.0f, 0.0f);
        trimStartPath.lineTo(0.5f, 0.0f);
        trimStartPath.cubicTo(0.7f, 0.0f, 0.6f, 1f, 1f, 1f);
        TRIM_START_INTERPOLATOR = PathInterpolatorCompat.create(trimStartPath);

        Path trimEndPath = new Path();
        trimEndPath.moveTo(0.0f, 0.0f);
        trimEndPath.cubicTo(0.2f, 0.0f, 0.1f, 1f, 0.5f, 1f);
        trimEndPath.lineTo(1f, 1f);
        TRIM_END_INTERPOLATOR = PathInterpolatorCompat.create(trimEndPath);
    }

    private final GLPaint mGLPaint;

    private float mCx;
    private float mCy;
    private float mRadiusX;
    private float mRadiusY;

    private float mTrimStart = 0.0f;
    private float mTrimEnd = 0.0f;
    private float mTrimOffset = 0.0f;
    private float mTrimRotation = 0.0f;

    private boolean mIndeterminate = false;

    private List<Animation> mAnimations;

    // Inner text rendering fields
    private String mPageText = "";
    private String mPercentText = "";
    private String mSpeedText = "";
    private BitmapTexture mPageTexture = null;
    private BitmapTexture mPercentTexture = null;
    private BitmapTexture mSpeedTexture = null;

    // Text style constants as fraction of view size for proper scaling
    private static final float LARGE_TEXT_FRACTION = 0.22f;   // Percentage text ~22% of view
    private static final float SMALL_TEXT_FRACTION = 0.11f;   // Page number and speed ~11% of view
    
    // Computed pixel values from onLayout
    private float mLargeTextPx = 0f;
    private float mSmallTextPx = 0f;
    
    private int mTextColor = Color.WHITE;
    private boolean mShowDetailedProgress = false;

    public GLProgressView() {
        mGLPaint = new GLPaint();
        mGLPaint.setColor(Color.WHITE);
        mGLPaint.setBackgroundColor(Color.BLACK);
        mAnimations = new ArrayList<>();

        setupAnimations();
    }

    public void setupAnimations() {
        FloatAnimation trimStart = new FloatAnimation() {
            @Override
            protected void onCalculate(float progress) {
                super.onCalculate(progress);
                mTrimStart = get();
            }
        };
        trimStart.setRange(0.0f, 0.75f);
        trimStart.setDuration(1333L);
        trimStart.setInterpolator(TRIM_START_INTERPOLATOR);
        trimStart.setRepeatCount(Animation.INFINITE);

        FloatAnimation trimEnd = new FloatAnimation() {
            @Override
            protected void onCalculate(float progress) {
                super.onCalculate(progress);
                mTrimEnd = get();
            }
        };
        trimEnd.setRange(0.0f, 0.75f);
        trimEnd.setDuration(1333L);
        trimEnd.setInterpolator(TRIM_END_INTERPOLATOR);
        trimEnd.setRepeatCount(Animation.INFINITE);

        FloatAnimation trimOffset = new FloatAnimation() {
            @Override
            protected void onCalculate(float progress) {
                super.onCalculate(progress);
                mTrimOffset = get();
            }
        };
        trimOffset.setRange(0.0f, 0.25f);
        trimOffset.setDuration(1333L);
        trimOffset.setInterpolator(LINEAR_INTERPOLATOR);
        trimOffset.setRepeatCount(Animation.INFINITE);

        FloatAnimation trimRotation = new FloatAnimation() {
            @Override
            protected void onCalculate(float progress) {
                super.onCalculate(progress);
                mTrimRotation = get();
            }
        };
        trimRotation.setRange(0.0f, 720.0f);
        trimRotation.setDuration(6665L);
        trimRotation.setInterpolator(LINEAR_INTERPOLATOR);
        trimRotation.setRepeatCount(Animation.INFINITE);

        mAnimations.add(trimStart);
        mAnimations.add(trimEnd);
        mAnimations.add(trimOffset);
        mAnimations.add(trimRotation);
    }

    private void startAnimations() {
        List<Animation> animations = mAnimations;
        for (int i = 0, n = animations.size(); i < n; i++) {
            animations.get(i).reset();
        }
    }

    private void stopAnimations() {
        List<Animation> animations = mAnimations;
        for (int i = 0, n = animations.size(); i < n; i++) {
            animations.get(i).cancel();
        }
    }

    @Override
    protected void onLayout(boolean changeSize, int left, int top, int right,
            int bottom) {
        super.onLayout(changeSize, left, top, right, bottom);

        int width = right - left;
        int height = bottom - top;
        // Larger outer ring: ~45% of view size (was ~40%)
        mGLPaint.setLineWidth(Math.min(width, height) / 16.0f);
        mCx = width / 2;
        mCy = height / 2;
        mRadiusX = width / 48.0f * 22.0f;  // ~45.8% of width, larger outer ring
        mRadiusY = height / 48.0f * 22.0f; // ~45.8% of height
        
        // Compute text sizes proportional to view dimensions
        int minDim = Math.min(width, height);
        mLargeTextPx = minDim * LARGE_TEXT_FRACTION;
        mSmallTextPx = minDim * SMALL_TEXT_FRACTION;
        
        // Invalidate textures so they're recreated with new sizes
        if (mShowDetailedProgress) {
            recycleTextTextures();
            invalidate(); // Trigger re-render to recreate textures with correct sizes
        }
    }

    public void setColor(int color) {
        mGLPaint.setColor(color);
        invalidate();
    }

    public void setBgColor(int color) {
        mGLPaint.setBackgroundColor(color);
        invalidate();
    }

    public void setIndeterminate(boolean indeterminate) {
        if (mIndeterminate != indeterminate) {
            mIndeterminate = indeterminate;
            if (indeterminate) {
                startAnimations();
            } else {
                stopAnimations();
            }
            invalidate();
        }
    }

    public boolean isIndeterminate() {
        return mIndeterminate;
    }

    public void setProgress(float progress) {
        if (!mIndeterminate) {
            mTrimStart = 0.0f;
            mTrimEnd = progress;
            mTrimOffset = 0.0f;
            mTrimRotation = 0.0f;
            invalidate();
        }
    }

    /**
     * Set detailed progress text displayed inside the circle.
     * @param pageIndex Page index (1-based), displayed as "第X页"
     * @param percent Progress percentage text like "50%"
     * @param speed Transfer speed text like "1.5MB/s"
     */
    public void setDetailedProgress(int pageIndex, String percent, String speed) {
        mShowDetailedProgress = true;
        
        String newPageText = "第" + pageIndex + "页";
        String newPercentText = percent != null ? percent : "";
        if(!newPercentText.isEmpty() && !newPercentText.endsWith("%")) {
            newPercentText += "%";
        }
        String newSpeedText = speed != null ? speed : "";

        boolean changed = !newPageText.equals(mPageText)
                || !newPercentText.equals(mPercentText)
                || !newSpeedText.equals(mSpeedText);

        mPageText = newPageText;
        mPercentText = newPercentText;
        mSpeedText = newSpeedText;

        if (changed) {
            recycleTextTextures();
            invalidate();
        }
    }

    /**
     * Disable detailed progress mode (hide inner text)
     */
    public void hideDetailedProgress() {
        if (mShowDetailedProgress) {
            mShowDetailedProgress = false;
            recycleTextTextures();
            mPageText = "";
            mPercentText = "";
            mSpeedText = "";
            invalidate();
        }
    }

    public void setTextColor(int color) {
        if (mTextColor != color) {
            mTextColor = color;
            if (mShowDetailedProgress) {
                recycleTextTextures();
                invalidate();
            }
        }
    }

    private void recycleTextTextures() {
        if (mPageTexture != null) {
            mPageTexture.recycle();
            mPageTexture = null;
        }
        if (mPercentTexture != null) {
            mPercentTexture.recycle();
            mPercentTexture = null;
        }
        if (mSpeedTexture != null) {
            mSpeedTexture.recycle();
            mSpeedTexture = null;
        }
    }

    private BitmapTexture createTextTexture(String text, float textSize, boolean bold,int mAscent) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(textSize);
        paint.setColor(mTextColor);
        if (bold) {
            paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
        }

        Paint.FontMetricsInt fm = paint.getFontMetricsInt();
        int textWidth = (int) Math.ceil(paint.measureText(text));
        int textHeight = fm.bottom - fm.top;
        if (textWidth <= 0) textWidth = 1;
        if (textHeight <= 0) textHeight = 1;

        Bitmap bitmap = Bitmap.createBitmap(textWidth, textHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.translate(0, -fm.ascent+mAscent);
        canvas.drawText(text, 0, 0, paint);

        BitmapTexture texture = new BitmapTexture(bitmap);
        return texture;
    }

    private void ensureTextTextures() {
        if (!mShowDetailedProgress) return;
        // Skip if layout hasn't happened yet (sizes would be 0)
        if (mLargeTextPx <= 0f || mSmallTextPx <= 0f) return;

        // Recreate textures if text content changed
        if (mPercentTexture == null && !mPercentText.isEmpty()) {
            mPercentTexture = createTextTexture(mPercentText, mLargeTextPx, true,0);
        }
        if (mPageTexture == null && !mPageText.isEmpty()) {
            mPageTexture = createTextTexture(mPageText, mSmallTextPx, false,(int)(mSmallTextPx-mLargeTextPx));
        }
        if (mSpeedTexture == null && !mSpeedText.isEmpty()) {
            mSpeedTexture = createTextTexture(mSpeedText, mSmallTextPx/2, false,(int)(mSmallTextPx-mLargeTextPx));
        }
    }

    private void drawTextInside(GLCanvas canvas, BitmapTexture texture, int centerX, int centerY, int offsetY) {
        if (texture == null) return;
        int w = texture.getWidth();
        int h = texture.getHeight();
        canvas.save();
        canvas.translate(centerX - w / 2, centerY + offsetY - h / 2);
        canvas.drawTexture(texture, 0, 0, w, h);
        canvas.restore();
    }

    @Override
    public void onRender(GLCanvas canvas) {
        update();

        int width = getWidth();
        int height = getHeight();
        int cx = width / 2;
        int cy = height / 2;

        // Draw outer circle background (full ring)
        canvas.save();
        canvas.translate(cx, cy);
        canvas.drawArc(mCx, mCy, mRadiusX, mRadiusY, 360f, mGLPaint);
        canvas.restore();

        // Draw progress arc
        float startAngle = (mTrimStart + mTrimOffset) * 360.0f - 90;
        float sweepAngle = Math.max(12.0f, (mTrimEnd - mTrimStart) * 360.0f);
        float rotation = mTrimRotation + startAngle;

        canvas.save();
        canvas.translate(cx, cy);
        canvas.rotate(rotation, 0, 0, 1);
        if (rotation % 180 != 0) {
            canvas.translate(-cy, -cx);
        } else {
            canvas.translate(-cx, -cy);
        }
        canvas.drawArc(mCx, mCy, mRadiusX, mRadiusY, sweepAngle, mGLPaint);
        canvas.restore();

        // Draw inner text if detailed progress is enabled
        if (mShowDetailedProgress && !mIndeterminate) {
            ensureTextTextures();
            // Calculate vertical spacing for 3 lines inside the circle
            int pageH = mPageTexture != null ? mPageTexture.getHeight() : 0;
            int percentH = mPercentTexture != null ? mPercentTexture.getHeight() : 0;
            int speedH = mSpeedTexture != null ? mSpeedTexture.getHeight() : 0;
            int totalH = pageH + percentH + speedH;
            int gap = 6; // pixel gap between lines

            // Position: page at top, percent in middle (large), speed at bottom
            int yOffset = -(totalH + gap * 2) / 2;
            
            if (mPageTexture != null) {
                drawTextInside(canvas, mPageTexture, cx, cy, yOffset);
                yOffset += pageH + gap;
            }
            if (mPercentTexture != null) {
                drawTextInside(canvas, mPercentTexture, cx, cy, yOffset);
                yOffset += percentH + gap;
            }
            if (mSpeedTexture != null) {
                drawTextInside(canvas, mSpeedTexture, cx, cy, yOffset);
            }
        }
    }

    private void update() {
        boolean invalidate = false;

        if (mIndeterminate) {
            long currentTime = AnimationTime.get();
            List<Animation> animations = mAnimations;
            for (int i = 0, n = animations.size(); i < n; i++) {
                invalidate |= animations.get(i).calculate(currentTime);
            }
        }

        if (invalidate) {
            invalidate();
        }
    }

    protected void onDetached() {
        recycleTextTextures();
    }
}
