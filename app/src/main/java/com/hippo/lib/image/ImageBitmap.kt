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
//import android.graphics.Bitmap
//import android.graphics.Canvas
//import android.graphics.Paint
//import android.graphics.Rect
//import android.graphics.RectF
//import android.graphics.drawable.Animatable
//import android.os.Handler
//import android.os.Looper
//import java.io.InputStream
//import java.lang.ref.WeakReference
//import kotlin.math.max
//
///**
// * A image with [Image1] for data and [Bitmap] for render.
// */
//class ImageBitmap : Animatable, Runnable {
//    private var mImage1: Image1? = null
//    private val mBitmap: Bitmap
//
//    /**
//     * Return the format of the image
//     */
//    val format: Int
//
//    /**
//     * Return image is opaque
//     */
//    val isOpaque: Boolean
//
//    /**
//     * Return byte count of image
//     */
//    val byteCount: Int
//
//    /**
//     * Return image frame count
//     */
//    val frameCount: Int
//    private var mReferences = 0
//    private var mAnimationReferences = 0
//    private var mRunning = false
//    private val mCallbackSet: MutableSet<WeakReference<Callback?>?> =
//        LinkedHashSet<WeakReference<Callback?>?>()
//
//    private constructor(image1: Image1) {
//        val width = image1.width
//        val height = image1.height
//        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        image1.render(0, 0, mBitmap, 0, 0, width, height, false, 0)
//        this.format = image1.format
//        this.isOpaque = image1.isOpaque
//        this.byteCount = image1.byteCount
//        this.frameCount = image1.frameCount
//
//        if (this.frameCount > 1) {
//            // For animated image, save image object
//            mImage1 = image1
//        } else {
//            // Free the image
//            image1.recycle()
//        }
//    }
//
//    private constructor(bitmap: Bitmap) {
//        this.format = Image1.FORMAT_PLAIN
//        mBitmap = bitmap
//        this.isOpaque = !bitmap.hasAlpha()
//        this.byteCount = bitmap.getRowBytes() * bitmap.getHeight()
//        this.frameCount = 1
//    }
//
//    /**
//     * Obtain the image bitmap
//     *
//     * @return false for the image is recycled and obtain failed
//     */
//    @Synchronized
//    fun obtain(): Boolean {
//        if (mBitmap.isRecycled()) {
//            return false
//        } else {
//            ++mReferences
//            return true
//        }
//    }
//
//    /**
//     * Release the image bitmap
//     */
//    @Synchronized
//    fun release() {
//        --mReferences
//        if (mReferences <= 0 && !mBitmap.isRecycled) {
//            mBitmap.recycle()
//            if (mImage1 != null) {
//                mImage1!!.recycle()
//            }
//        }
//    }
//
//    /**
//     * Add a callback for invalidating
//     */
//    fun addCallback(callback: Callback) {
//        val iterator = mCallbackSet.iterator()
//        var c: Callback?
//        while (iterator.hasNext()) {
//            c = iterator.next()!!.get()
//            if (c == null) {
//                // Remove from the set if the reference has been cleared or
//                // it can't be used.
//                iterator.remove()
//            } else if (c === callback) {
//                return
//            }
//        }
//
//        mCallbackSet.add(WeakReference<Callback?>(callback))
//    }
//
//    /**
//     * Remove a callback
//     */
//    fun removeCallback(callback: Callback) {
//        val iterator = mCallbackSet.iterator()
//        var c: Callback?
//        while (iterator.hasNext()) {
//            c = iterator.next()!!.get()
//            if (c == null) {
//                // Remove from the set if the reference has been cleared or
//                // it can't be used.
//                iterator.remove()
//            } else if (c === callback) {
//                iterator.remove()
//                return
//            }
//        }
//    }
//
//    val width: Int
//        /**
//         * Return image width
//         */
//        get() = mBitmap.getWidth()
//
//    val height: Int
//        /**
//         * Return image height
//         */
//        get() = mBitmap.getHeight()
//
//    val isAnimated: Boolean
//        /**
//         * Return image is animated
//         */
//        get() = mImage1 != null
//
//    /**
//     * Draw image to canvas
//     */
//    fun draw(canvas: Canvas, left: Float, top: Float, paint: Paint?) {
//        if (!mBitmap.isRecycled) {
//            canvas.drawBitmap(mBitmap, left, top, paint)
//        }
//    }
//
//    /**
//     * Draw image to canvas
//     */
//    fun draw(canvas: Canvas, src: Rect?, dst: Rect, paint: Paint?) {
//        if (!mBitmap.isRecycled) {
//            canvas.drawBitmap(mBitmap, src, dst, paint)
//        }
//    }
//
//    /**
//     * Draw image to canvas
//     */
//    fun draw(canvas: Canvas, src: Rect?, dst: RectF, paint: Paint?) {
//        if (!mBitmap.isRecycled) {
//            canvas.drawBitmap(mBitmap, src, dst, paint)
//        }
//    }
//
//    /**
//     * `start()` and `stop()` is a pair
//     */
//    override fun start() {
//        mAnimationReferences++
//        if (mBitmap.isRecycled || mImage1 == null || mRunning) {
//            return
//        }
//        mRunning = true
//        HANDLER.postDelayed(this, max(0, mImage1!!.delay).toLong())
//    }
//
//    /**
//     * `start()` and `stop()` is a pair
//     */
//    override fun stop() {
//        mAnimationReferences--
//        if (mAnimationReferences <= 0) {
//            mRunning = false
//            HANDLER.removeCallbacks(this)
//        }
//    }
//
//    override fun isRunning(): Boolean {
//        return mRunning
//    }
//
//    private fun notifyUpdate(): Boolean {
//        var hasCallback = false
//        val iterator = mCallbackSet.iterator()
//        var callback: Callback?
//        while (iterator.hasNext()) {
//            callback = iterator.next()!!.get()
//            if (callback != null) {
//                // Render bitmap int the first time
//                if (!hasCallback) {
//                    hasCallback = true
//                    mImage1!!.render(0, 0, mBitmap, 0, 0, mImage1!!.width, mImage1!!.height, false, 0)
//                }
//                callback.invalidateImage(this)
//            } else {
//                // Remove from the set if the reference has been cleared or
//                // it can't be used.
//                iterator.remove()
//            }
//        }
//        return hasCallback
//    }
//
//    override fun run() {
//        // Check recycled
//        if (mBitmap.isRecycled || mImage1 == null) {
//            mRunning = false
//            return
//        }
//
//        mImage1!!.advance()
//
//        if (notifyUpdate()) {
//            if (mRunning) {
//                HANDLER.postDelayed(this, max(0, mImage1!!.delay).toLong())
//            }
//        } else {
//            mRunning = false
//        }
//    }
//
//    interface Callback {
//        fun invalidateImage(who: ImageBitmap?)
//    }
//
//    companion object {
//        private val HANDLER = Handler(Looper.getMainLooper())
//
//        /**
//         * Decode `InputStream`, then create image.
//         */
//        fun decode(`is`: InputStream): ImageBitmap? {
//            val image1 = Image1.decode(`is`, false)
//            if (image1 != null) {
//                return create(image1)
//            } else {
//                return null
//            }
//        }
//
//        /**
//         * Decode `InputStream`, then create image.
//         */
//        @JvmStatic
//        fun decode(`is`: InputStream, hardware: Boolean): ImageBitmap? {
//            val image1 = Image1.decode(`is`, false)
//            if (image1 != null) {
//                return create(image1)
//            } else {
//                return null
//            }
//        }
//
//        /**
//         * Create `ImageBitmap` from `Image`.
//         * It is not recommended. Use [.decode] if you can.
//         *
//         * @param image1 the image should not be used before and
//         * it must not be recycled. And the image should
//         * not be used directly anymore.
//         */
//        fun create(image1: Image1): ImageBitmap? {
//            if (!image1.isRecycled) {
//                try {
//                    return ImageBitmap(image1)
//                } catch (e: OutOfMemoryError) {
//                    image1.recycle()
//                    return null
//                }
//            } else {
//                return null
//            }
//        }
//
//        /**
//         * Create `ImageBitmap` from `Bitmap`.
//         *
//         * @param bitmap the bitmap should not be recycled
//         */
//        fun create(bitmap: Bitmap): ImageBitmap? {
//            return if (!bitmap.isRecycled) {
//                ImageBitmap(bitmap)
//            } else {
//                null
//            }
//        }
//    }
//}
