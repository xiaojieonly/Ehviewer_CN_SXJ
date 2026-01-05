///*
// * Copyright 2016 Hippo Seven
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.hippo.lib.image
//
//import android.graphics.Canvas
//import android.graphics.ColorFilter
//import android.graphics.Paint
//import android.graphics.PixelFormat
//import android.graphics.drawable.Animatable
//import android.graphics.drawable.Drawable
//
///**
// * A drawable to draw [ImageBitmap]
// */
//class ImageDrawable(imageBitmap: ImageBitmap) : Drawable(), Animatable, ImageBitmap.Callback {
//    private val mImageBitmap: ImageBitmap
//    private val mPaint: Paint
//
//    init {
//        if (!imageBitmap.obtain()) {
//            throw RecycledException()
//        }
//
//        mImageBitmap = imageBitmap
//        mPaint = Paint(Paint.FILTER_BITMAP_FLAG)
//
//        // Add callback
//        imageBitmap.addCallback(this)
//    }
//
//    fun recycle() {
//        mImageBitmap.removeCallback(this)
//        mImageBitmap.release()
//    }
//
//    override fun draw(canvas: Canvas) {
//        mImageBitmap.draw(canvas, null, getBounds(), mPaint)
//    }
//
//    override fun setAlpha(alpha: Int) {
//        mPaint.setAlpha(alpha)
//    }
//
//    override fun setColorFilter(colorFilter: ColorFilter?) {
//        mPaint.setColorFilter(colorFilter)
//    }
//
//    override fun getOpacity(): Int {
//        return if (mImageBitmap.isOpaque) PixelFormat.OPAQUE else PixelFormat.UNKNOWN
//    }
//
//    override fun getIntrinsicWidth(): Int {
//        return mImageBitmap.width
//    }
//
//    override fun getIntrinsicHeight(): Int {
//        return mImageBitmap.height
//    }
//
//    override fun start() {
//        mImageBitmap.start()
//    }
//
//    override fun stop() {
//        mImageBitmap.stop()
//    }
//
//    override fun isRunning(): Boolean {
//        return mImageBitmap.isRunning()
//    }
//
//    override fun invalidateImage(who: ImageBitmap?) {
//        invalidateSelf()
//    }
//}
