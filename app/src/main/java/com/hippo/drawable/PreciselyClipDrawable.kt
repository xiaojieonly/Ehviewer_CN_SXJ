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

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import androidx.core.graphics.withClip

/**
 * Show a part of the original drawable
 */
class PreciselyClipDrawable(
    drawable: Drawable,
    offsetX: Int,
    offsetY: Int,
    width: Int,
    height: Int,
) : DrawableWrapper(drawable) {
    private val mScale: RectF
    private val mTemp = Rect()

    init {
        val originWidth = drawable.intrinsicWidth.toFloat()
        val originHeight = drawable.intrinsicHeight.toFloat()
        mScale = RectF(
            (offsetX / originWidth).coerceIn(0.0f, 1.0f),
            (offsetY / originHeight).coerceIn(0.0f, 1.0f),
            ((offsetX + width) / originWidth).coerceIn(0.0f, 1.0f),
            ((offsetY + height) / originHeight).coerceIn(0.0f, 1.0f),
        )
    }

    override fun onBoundsChange(bounds: Rect) {
        mTemp.left = ((mScale.left * bounds.right - mScale.right * bounds.left) / (mScale.left * (1 - mScale.right) - mScale.right * (1 - mScale.left))).toInt()
        mTemp.right = (((1 - mScale.right) * bounds.left - (1 - mScale.left) * bounds.right) / (mScale.left * (1 - mScale.right) - mScale.right * (1 - mScale.left))).toInt()
        mTemp.top = ((mScale.top * bounds.bottom - mScale.bottom * bounds.top) / (mScale.top * (1 - mScale.bottom) - mScale.bottom * (1 - mScale.top))).toInt()
        mTemp.bottom = (((1 - mScale.bottom) * bounds.top - (1 - mScale.top) * bounds.bottom) / (mScale.top * (1 - mScale.bottom) - mScale.bottom * (1 - mScale.top))).toInt()
        super.onBoundsChange(mTemp)
    }

    override fun getIntrinsicWidth(): Int = (super.getIntrinsicWidth() * mScale.width()).toInt()

    override fun getIntrinsicHeight(): Int = (super.getIntrinsicHeight() * mScale.height()).toInt()

    override fun draw(canvas: Canvas) {
        canvas.withClip(bounds) { super.draw(canvas) }
    }
}