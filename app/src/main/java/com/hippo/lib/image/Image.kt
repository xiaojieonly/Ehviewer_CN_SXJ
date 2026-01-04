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
package com.hippo.lib.image

import android.content.Context
import android.graphics.Bitmap
import com.getkeepsafe.relinker.ReLinker
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * The `Image` is a image which stored pixel data in native heap
 */
class Image private constructor(
    private var mNativePtr: Long,
    /**
     * Return the format of the image
     */
    val format: Int,
    /**
     * Return the width of the image
     */
    val width: Int,
    /**
     * Return the height of the image
     */
    val height: Int
) {
    private var mRecycleTracker: Throwable? = null

    init {
        sImageCount.getAndIncrement()
    }

    val byteCount: Int
        /**
         * Return the minimum number of bytes that can be used to store this image's pixels.
         */
        get() {
            checkRecycled()
            return nativeGetByteCount(mNativePtr, this.format)
        }

    private fun checkRecycled() {
        if (mNativePtr == 0L) {
            if (mRecycleTracker != null) {
                throw IllegalStateException("The image is recycled.", mRecycleTracker)
            } else {
                throw IllegalStateException("The image is recycled.")
            }
        }
    }

    /**
     * Complete the image decoding
     */
    fun complete(): Boolean {
        checkRecycled()
        return nativeComplete(mNativePtr, this.format)
    }

    val isCompleted: Boolean
        /**
         * Is the image decoding completed
         */
        get() {
            try{
                checkRecycled()
                return nativeIsCompleted(mNativePtr, this.format)
            }catch (e:Exception){
                return false
            }
        }

    /**
     * Render the image to `Bitmap`
     */
    fun render(
        srcX: Int, srcY: Int, dst: Bitmap?, dstX: Int, dstY: Int,
        width: Int, height: Int, fillBlank: Boolean, defaultColor: Int
    ) {
        checkRecycled()
        nativeRender(
            mNativePtr,
            this.format, srcX, srcY, dst, dstX, dstY,
            width, height, fillBlank, defaultColor
        )
    }

    /**
     * Call `glTexImage2D` for init is true and
     * call `glTexSubImage2D` for init is false.
     * width * height must <= 512 * 512 or do nothing
     */
    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        checkRecycled()
        nativeTexImage(
            mNativePtr,
            this.format, init, offsetX, offsetY, width, height
        )
    }

    /**
     * Move to next frame. Do nothing for non-animation image
     */
    fun advance() {
        checkRecycled()
        nativeAdvance(mNativePtr, this.format)
    }

    val delay: Int
        /**
         * Return current frame delay. 0 for non-animation image
         */
        get() {
            checkRecycled()
            val delay: Int =
                nativeGetDelay(mNativePtr, this.format)
            return if (delay <= 10) 100 else delay
        }

    val frameCount: Int
        /**
         * Return frame count. 1 for non-animation image
         */
        get() {
            checkRecycled()
            return nativeFrameCount(mNativePtr, this.format)
        }

    val isOpaque: Boolean
        /**
         * Return is the image opaque
         */
        get() {
            checkRecycled()
            return nativeIsOpaque(mNativePtr, this.format)
        }

    /**
     * Return `true` if the image is gray.
     */
    fun isGray(error: Int): Boolean {
        checkRecycled()
        return nativeIsGray(mNativePtr, this.format, error)
    }

    /**
     * Improves contrast in this image with CLAHE.
     */
    fun clahe(toGray: Boolean) {
        checkRecycled()
        nativeClahe(mNativePtr, this.format, toGray)
    }

    /**
     * Free the native object associated with this image.
     * It must be called when the image will not be used.
     * The image can't be used after this method is called.
     */
    fun recycle() {
        if (mNativePtr != 0L) {
            nativeRecycle(mNativePtr, this.format)
            mNativePtr = 0

            sImageCount.getAndDecrement()

            mRecycleTracker = Throwable("It's a ImageRecycleTracker")
        }
    }

    val isRecycled: Boolean
        /**
         * Returns true if this image has been recycled.
         */
        get() = mNativePtr == 0L

    companion object {
        /**
         * Unknown image format
         */
        val FORMAT_UNKNOWN: Int = -1

        /**
         * Plain image format, for `Image` from [.create]
         */
        const val FORMAT_PLAIN: Int = 0

        /**
         * JPEG image format
         */
        const val FORMAT_JPEG: Int = 1

        /**
         * PNG image format
         */
        const val FORMAT_PNG: Int = 2

        /**
         * GIF image format
         */
        const val FORMAT_GIF: Int = 3

        /**
         * WEBP image format
         */
        const val FORMAT_WEBP: Int = 4

        private val sImageCount = AtomicInteger()

        /**
         * Decode image from `InputStream`
         */
        @JvmStatic
        fun decode(`is`: InputStream?, partially: Boolean): Image? {
            var `is` = `is`
            if (`is` !is BufferedInputStream) {
                `is` = BufferedInputStream(`is`)
            }
            return nativeDecode(`is`, partially)
        }

        /**
         * Create a plain image from Bitmap
         */
        @JvmStatic
        fun create(bitmap: Bitmap?): Image? {
            return nativeCreate(bitmap)
        }

        val imageCount: Int
            /**
             * Return all un-recycled `Image` instance count.
             * It is useful for debug.
             */
            get() = sImageCount.get()

        val supportedImageFormats: IntArray?
            /**
             * Return all supported image formats, exclude [.FORMAT_PLAIN]
             */
            get() = nativeGetSupportedImageFormats()

        /**
         * Return decoder description of the image format,
         * `null` for invalid image format.
         */
        fun getDecoderDescription(format: Int): String? {
            return nativeGetDecoderDescription(format)
        }

        @JvmStatic
        fun initialize(context: Context?) {
            ReLinker.loadLibrary(context, "image")
            //        System.loadLibrary("ehviewer");
        }
        @JvmStatic
        private external fun nativeDecode(`is`: InputStream?, partially: Boolean): Image?

        //    private static native Image nativeDecode3(InputStream is, boolean partially);
        @JvmStatic
        private external fun nativeCreate(bitmap: Bitmap?): Image?
        @JvmStatic
        private external fun nativeGetByteCount(nativePtr: Long, format: Int): Int
        @JvmStatic
        private external fun nativeComplete(nativePtr: Long, format: Int): Boolean
        @JvmStatic
        private external fun nativeIsCompleted(nativePtr: Long, format: Int): Boolean
        @JvmStatic
        private external fun nativeRender(
            nativePtr: Long, format: Int,
            srcX: Int, srcY: Int, dst: Bitmap?, dstX: Int, dstY: Int,
            width: Int, height: Int, fillBlank: Boolean, defaultColor: Int
        )
        @JvmStatic
        private external fun nativeTexImage(
            nativePtr: Long, format: Int,
            init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int
        )
        @JvmStatic
        private external fun nativeAdvance(nativePtr: Long, format: Int)
        @JvmStatic
        private external fun nativeGetDelay(nativePtr: Long, format: Int): Int
        @JvmStatic
        private external fun nativeFrameCount(nativePtr: Long, format: Int): Int
        @JvmStatic
        private external fun nativeIsOpaque(nativePtr: Long, format: Int): Boolean
        @JvmStatic
        private external fun nativeIsGray(nativePtr: Long, format: Int, error: Int): Boolean
        @JvmStatic
        private external fun nativeClahe(nativePtr: Long, format: Int, toGray: Boolean)
        @JvmStatic
        private external fun nativeRecycle(nativePtr: Long, format: Int)
        @JvmStatic
        private external fun nativeGetSupportedImageFormats(): IntArray?
        @JvmStatic
        private external fun nativeGetDecoderDescription(format: Int): String?
    }
}
