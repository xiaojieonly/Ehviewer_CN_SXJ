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

class RoundSideRectDrawable(color: Int) : Drawable() {
    private val mPaint: Paint
    private val mPath: Path
    private val mTempRectF: RectF

    init {
        mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        mPaint.color = color
        mPath = Path()
        mTempRectF = RectF()
    }

    override fun onBoundsChange(bounds: Rect) {
        val width = bounds.width()
        val height = bounds.height()
        val rectF = mTempRectF
        val path = mPath
        path.reset()
        if (width > height) {
            val radius = height / 2
            path.moveTo((bounds.right - radius).toFloat(), bounds.top.toFloat())
            rectF[(bounds.right - height).toFloat(), bounds.top.toFloat(), bounds.right.toFloat()] =
                bounds.bottom.toFloat()
            path.arcTo(rectF, -90.0f, 180.0f, false)
            path.lineTo((bounds.left + radius).toFloat(), bounds.bottom.toFloat())
            rectF[bounds.left.toFloat(), bounds.top.toFloat(), (bounds.left + height).toFloat()] =
                bounds.bottom.toFloat()
            path.arcTo(rectF, 90.0f, 180.0f, false)
            path.lineTo((bounds.right - radius).toFloat(), bounds.top.toFloat())
        } else if (width == height) {
            path.addCircle(
                bounds.centerX().toFloat(),
                bounds.centerY().toFloat(),
                (width / 2).toFloat(),
                Path.Direction.CW
            )
        } else {
            // TODO
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(mPath, mPaint)
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