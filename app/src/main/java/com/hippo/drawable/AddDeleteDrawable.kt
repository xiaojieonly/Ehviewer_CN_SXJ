/*
 * Copyright (C) 2015 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.drawable

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.Build
import com.hippo.ehviewer.R
import com.hippo.yorozuya.MathUtils

class AddDeleteDrawable(context: Context, color: Int) : Drawable() {
    private val mPaint = Paint()
    private val mPath = Path()
    private val mSize: Int
    private var mProgress = 0f
    private var mAutoUpdateMirror = false
    private var mVerticalMirror = false

    /**
     * @param context used to get the configuration for the drawable from
     */
    init {
        val resources = context.resources
        mSize = resources.getDimensionPixelSize(R.dimen.add_size)
        val barThickness = Math.round(resources.getDimension(R.dimen.add_thickness)).toFloat()
        mPaint.color = color
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeJoin = Paint.Join.MITER
        mPaint.strokeCap = Paint.Cap.BUTT
        mPaint.strokeWidth = barThickness
        val halfSize = (mSize / 2).toFloat()
        mPath.moveTo(0f, -halfSize)
        mPath.lineTo(0f, halfSize)
        mPath.moveTo(-halfSize, 0f)
        mPath.lineTo(halfSize, 0f)
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val canvasRotate: Float
        canvasRotate = if (mVerticalMirror) {
            MathUtils.lerp(270f, 135f, mProgress)
        } else {
            MathUtils.lerp(0f, 135f, mProgress)
        }
        canvas.save()
        canvas.translate(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        canvas.rotate(canvasRotate)
        canvas.drawPath(mPath, mPaint)
        canvas.restore()
    }

    fun setColor(color: Int) {
        mPaint.color = color
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        mPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        mPaint.colorFilter = cf
    }

    override fun getIntrinsicHeight(): Int {
        return mSize * 6 / 5
    }

    override fun getIntrinsicWidth(): Int {
        return mSize * 6 / 5
    }

    /**
     * If set, canvas is flipped when progress reached to end and going back to start.
     */
    protected fun setVerticalMirror(verticalMirror: Boolean) {
        mVerticalMirror = verticalMirror
    }

    fun setAutoUpdateMirror(autoUpdateMirror: Boolean) {
        mAutoUpdateMirror = autoUpdateMirror
    }

    var progress: Float
        get() = mProgress
        set(progress) {
            if (mAutoUpdateMirror) {
                if (progress == 1f) {
                    setVerticalMirror(true)
                } else if (progress == 0f) {
                    setVerticalMirror(false)
                }
            }
            mProgress = progress
            invalidateSelf()
        }

    fun setAdd(duration: Long) {
        setShape(false, duration)
    }

    fun setDelete(duration: Long) {
        setShape(true, duration)
    }

    fun setShape(delete: Boolean, duration: Long) {
//        if (((!delete && mProgress == 0f || delete) && mProgress) != 1f) {
        if (!((!delete && mProgress == 0f) || (delete && mProgress == 1f))) {
            val endProgress = if (delete) 1f else 0f
            if (duration <= 0) {
                progress = endProgress
            } else {
                val oa = ObjectAnimator.ofFloat(this, "progress", endProgress)
                oa.duration = duration
                oa.setAutoCancel(true)
                oa.start()
            }
        }
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}