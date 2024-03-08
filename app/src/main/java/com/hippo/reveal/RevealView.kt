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
package com.hippo.reveal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class RevealView : View, Reveal {
    private val mRevealPath = Path()
    private var mReveal = false
    private var mCenterX = 0
    private var mCenterY = 0
    private var mRadius = 0f

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
    }

    override fun setRevealEnable(enable: Boolean) {
        mReveal = enable
        invalidate()
    }

    override fun setReveal(centerX: Int, centerY: Int, radius: Float) {
        mCenterX = centerX
        mCenterY = centerY
        mRadius = radius
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        val reveal = mReveal
        var saveCount = 0
        if (reveal) {
            saveCount = canvas.save()
            val path = mRevealPath
            path.reset()
            path.addCircle(mCenterX.toFloat(), mCenterY.toFloat(), mRadius, Path.Direction.CW)
            canvas.clipPath(path)
        }
        super.draw(canvas)
        if (reveal) {
            canvas.restoreToCount(saveCount)
        }
    }
}