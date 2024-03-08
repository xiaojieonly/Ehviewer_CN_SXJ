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
package com.hippo.drawable

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

open class WrapDrawable : Drawable() {
    private var drawable: Drawable? = null

    open fun setDrawable(drawable: Drawable?) {
        this.drawable = drawable
    }

    open fun getDrawable(): Drawable? {
        return this.drawable
    }

    fun updateBounds() {
        setBounds(0, 0, intrinsicWidth, intrinsicHeight)
    }

    override fun draw(canvas: Canvas) {
        if (drawable != null) {
            drawable!!.draw(canvas)
        }
    }

    override fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        super.setBounds(left, top, right, bottom)
        if (drawable != null) {
            drawable!!.setBounds(left, top, right, bottom)
        }
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        if (drawable != null) {
            drawable!!.bounds = bounds
        }
    }

    override fun setChangingConfigurations(configs: Int) {
        super.setChangingConfigurations(configs)
        if (drawable != null) {
            drawable!!.changingConfigurations = configs
        }
    }

    override fun getChangingConfigurations(): Int {
        return if (drawable != null) {
            drawable!!.changingConfigurations
        } else {
            super.getChangingConfigurations()
        }
    }

    override fun setDither(dither: Boolean) {
        super.setDither(dither)
        if (drawable != null) {
            drawable!!.setDither(dither)
        }
    }

    override fun setFilterBitmap(filter: Boolean) {
        super.setFilterBitmap(filter)
        if (drawable != null) {
            drawable!!.isFilterBitmap = filter
        }
    }

    override fun setAlpha(alpha: Int) {
        if (drawable != null) {
            drawable!!.alpha = alpha
        }
    }

    override fun setColorFilter(cf: ColorFilter?) {
        if (drawable != null) {
            drawable!!.colorFilter = cf
        }
    }

    override fun getOpacity(): Int {
        return if (drawable != null) {
            drawable!!.opacity
        } else {
            PixelFormat.UNKNOWN
        }
    }

    override fun getIntrinsicWidth(): Int {
        return if (drawable != null) {
            drawable!!.intrinsicWidth
        } else {
            super.getIntrinsicWidth()
        }
    }

    override fun getIntrinsicHeight(): Int {
        return if (drawable != null) {
            drawable!!.intrinsicHeight
        } else {
            super.getIntrinsicHeight()
        }
    }
}