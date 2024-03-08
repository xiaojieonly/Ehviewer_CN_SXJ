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
package com.hippo.drawable

import android.graphics.*
import android.graphics.drawable.Drawable

class RoundDrawable(color: Int) : Drawable() {
    private val mPaint: Paint
    private val mRectF: RectF

    init {
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        mPaint.color = color
        mRectF = RectF()
    }

    fun setColor(color: Int) {
        mPaint.color = color
        invalidateSelf()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        mRectF.set(bounds)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawOval(mRectF, mPaint)
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }
}