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
import com.hippo.yorozuya.MathUtils

/**
 * Show a part of the original drawable
 */
class PreciselyClipDrawable(
    drawable: Drawable,
    offsetX: Int,
    offsetY: Int,
    width: Int,
    height: Int
) : DrawableWrapper(drawable) {
    private var mClip = false
    private var mScale: RectF? = null
    private var mTemp: Rect? = null

    init {
        val originWidth = drawable.intrinsicWidth.toFloat()
        val originHeight = drawable.intrinsicHeight.toFloat()
        if (originWidth <= 0 || originHeight <= 0) {
            // Can not clip
            mClip = false
        } else {
            mClip = true
            mScale = RectF()
            mScale!!.set(
                MathUtils.clamp(offsetX / originWidth, 0.0f, 1.0f),
                MathUtils.clamp(offsetY / originHeight, 0.0f, 1.0f),
                MathUtils.clamp((offsetX + width) / originWidth, 0.0f, 1.0f),
                MathUtils.clamp((offsetY + height) / originHeight, 0.0f, 1.0f)
            )
            mTemp = Rect()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        if (mClip) {
            if (!mScale!!.isEmpty) {
                mTemp!!.left = ((mScale!!.left * bounds.right - mScale!!.right * bounds.left) /
                        (mScale!!.left * (1 - mScale!!.right) - mScale!!.right * (1 - mScale!!.left))).toInt()
                mTemp!!.right =
                    (((1 - mScale!!.right) * bounds.left - (1 - mScale!!.left) * bounds.right) /
                            (mScale!!.left * (1 - mScale!!.right) - mScale!!.right * (1 - mScale!!.left))).toInt()
                mTemp!!.top = ((mScale!!.top * bounds.bottom - mScale!!.bottom * bounds.top) /
                        (mScale!!.top * (1 - mScale!!.bottom) - mScale!!.bottom * (1 - mScale!!.top))).toInt()
                mTemp!!.bottom =
                    (((1 - mScale!!.bottom) * bounds.top - (1 - mScale!!.top) * bounds.bottom) /
                            (mScale!!.top * (1 - mScale!!.bottom) - mScale!!.bottom * (1 - mScale!!.top))).toInt()
                super.onBoundsChange(mTemp!!)
            }
        } else {
            super.onBoundsChange(bounds)
        }
    }

    override fun getIntrinsicWidth(): Int {
        return if (mClip) {
            (super.getIntrinsicWidth() * mScale!!.width()).toInt()
        } else {
            super.getIntrinsicWidth()
        }
    }

    override fun getIntrinsicHeight(): Int {
        return if (mClip) {
            (super.getIntrinsicHeight() * mScale!!.height()).toInt()
        } else {
            super.getIntrinsicHeight()
        }
    }

    override fun draw(canvas: Canvas) {
        if (mClip) {
            if (!mScale!!.isEmpty) {
                val saveCount = canvas.save()
                canvas.clipRect(bounds)
                super.draw(canvas)
                canvas.restoreToCount(saveCount)
            }
        } else {
            super.draw(canvas)
        }
    }
}