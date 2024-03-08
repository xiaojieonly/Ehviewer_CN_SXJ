/*
 * Copyright 2018 Hippo Seven
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

import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.DrawableCompat

open class DrawableWrapper(var wrappedDrawable: Drawable?) : Drawable(), Drawable.Callback {
  
    override fun draw(canvas: Canvas) {
        wrappedDrawable!!.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        wrappedDrawable!!.bounds = bounds
    }

    override fun setChangingConfigurations(configs: Int) {
        wrappedDrawable!!.changingConfigurations = configs
    }

    override fun getChangingConfigurations(): Int {
        if (wrappedDrawable==null){
            return 0
        }
        return wrappedDrawable!!.changingConfigurations
    }

    override fun setDither(dither: Boolean) {
        wrappedDrawable!!.setDither(dither)
    }

    override fun setFilterBitmap(filter: Boolean) {
        wrappedDrawable!!.isFilterBitmap = filter
    }

    override fun setAlpha(alpha: Int) {
        wrappedDrawable!!.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        wrappedDrawable!!.colorFilter = cf
    }

    override fun isStateful(): Boolean {
        return wrappedDrawable!!.isStateful
    }

    override fun setState(stateSet: IntArray): Boolean {
        return wrappedDrawable!!.setState(stateSet)
    }

    override fun getState(): IntArray {
        return wrappedDrawable!!.state
    }

    override fun jumpToCurrentState() {
        wrappedDrawable!!.jumpToCurrentState()
    }

    override fun getCurrent(): Drawable {
        return wrappedDrawable!!.current
    }

    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        return super.setVisible(visible, restart) || wrappedDrawable!!.setVisible(visible, restart)
    }

    override fun getOpacity(): Int {
        return wrappedDrawable!!.opacity
    }

    override fun getTransparentRegion(): Region? {
        return wrappedDrawable!!.transparentRegion
    }

    override fun getIntrinsicWidth(): Int {
        if (wrappedDrawable==null){
            return -1
        }
        return wrappedDrawable!!.intrinsicWidth
    }

    override fun getIntrinsicHeight(): Int {
        if (wrappedDrawable==null){
            return -1
        }
        return wrappedDrawable!!.intrinsicHeight
    }

    override fun getMinimumWidth(): Int {
        return wrappedDrawable!!.minimumWidth
    }

    override fun getMinimumHeight(): Int {
        return wrappedDrawable!!.minimumHeight
    }

    override fun getPadding(padding: Rect): Boolean {
        return wrappedDrawable!!.getPadding(padding)
    }

    override fun invalidateDrawable(who: Drawable) {
        invalidateSelf()
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        scheduleSelf(what, `when`)
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    override fun onLevelChange(level: Int): Boolean {
        return wrappedDrawable!!.setLevel(level)
    }

    override fun setAutoMirrored(mirrored: Boolean) {
        DrawableCompat.setAutoMirrored(wrappedDrawable!!, mirrored)
    }

    override fun isAutoMirrored(): Boolean {
        return DrawableCompat.isAutoMirrored(wrappedDrawable!!)
    }

    override fun setTint(tint: Int) {
        DrawableCompat.setTint(wrappedDrawable!!, tint)
    }

    override fun setTintList(tint: ColorStateList?) {
        DrawableCompat.setTintList(wrappedDrawable!!, tint)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        if (tintMode != null) {
            DrawableCompat.setTintMode(wrappedDrawable!!, tintMode)
        }
    }

    override fun setHotspot(x: Float, y: Float) {
        DrawableCompat.setHotspot(wrappedDrawable!!, x, y)
    }

    override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
        DrawableCompat.setHotspotBounds(wrappedDrawable!!, left, top, right, bottom)
    }
}