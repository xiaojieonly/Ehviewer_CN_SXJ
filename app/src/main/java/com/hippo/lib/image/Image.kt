/*
 * Copyright 2022 Tarsin Norbin
 *
 * This file is part of EhViewer
 *
 * EhViewer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * EhViewer is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with EhViewer.
 * If not, see <https://www.gnu.org/licenses/>.
 */
package com.hippo.lib.image

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.ImageDecoder.ALLOCATOR_DEFAULT
import android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
import android.graphics.ImageDecoder.DecodeException
import android.graphics.ImageDecoder.ImageInfo
import android.graphics.ImageDecoder.Source
import android.graphics.PixelFormat
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toDrawable
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.hippo.ehviewer.EhApplication
import java.io.FileInputStream
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

class Image private constructor(
    source: FileInputStream?,
    drawable: Drawable? = null,
    val hardware: Boolean = false,
    val release: () -> Unit? = {}
) {
    private var mObtainedDrawable: Drawable?
    private var mBitmap: Bitmap? = null
    private var mReferences = 0

    init {
        mObtainedDrawable = null
        source?.let {
            var simpleSize: Int? = null
            if (source.available() > 10485760) {
                simpleSize = source.available() / 10485760 + 1
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val src = ImageDecoder.createSource(
                    source.channel.map(
                        FileChannel.MapMode.READ_ONLY, 0,
                        source.available().toLong()
                    )
                )
                try {
                    mObtainedDrawable =
                        ImageDecoder.decodeDrawable(src) { decoder: ImageDecoder, info: ImageInfo, _: Source ->
                            decoder.allocator =
                                if (hardware) ALLOCATOR_DEFAULT else ALLOCATOR_SOFTWARE
                            // Sadly we must use software memory since we need copy it to tile buffer, fuck glgallery
                            // Idk it will cause how much performance regression
                            val screenSize = min(
                                info.size.width / (2 * screenWidth),
                                info.size.height / (2 * screenHeight)
                            ).coerceAtLeast(1)
                            decoder.setTargetSampleSize(
                                max(screenSize, simpleSize ?: 1)
                            )
                            // Don't
                        }
                } catch (e: DecodeException) {
                    throw Exception("Android 9 解码失败", e)
                }
                // Should we lazy decode it?
            } else {
                if (simpleSize != null) {
                    val option = BitmapFactory.Options().apply {
                        inSampleSize = simpleSize
                    }
                    val bitmap = BitmapFactory.decodeStream(source, null, option)
                    mObtainedDrawable =
                        BitmapDrawable(EhApplication.getInstance().resources, bitmap)
                } else {
                    mObtainedDrawable = BitmapDrawable.createFromStream(source, null)
                }
            }
        }
        if (mObtainedDrawable == null) {
            mObtainedDrawable = drawable!!
//            throw IllegalArgumentException("数据解码出错")
        }
    }

    val animated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        mObtainedDrawable is AnimatedImageDrawable
    } else {
        mObtainedDrawable is AnimationDrawable
    }
    val width =
        (mObtainedDrawable as? BitmapDrawable)?.bitmap?.width ?: mObtainedDrawable!!.intrinsicWidth
    val height = (mObtainedDrawable as? BitmapDrawable)?.bitmap?.height
        ?: mObtainedDrawable!!.intrinsicHeight
    val isRecycled = mObtainedDrawable == null

    var started = false

    @Synchronized
    fun recycle() {
        if (mObtainedDrawable == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (mObtainedDrawable is AnimatedImageDrawable) {
                (mObtainedDrawable as AnimatedImageDrawable?)?.stop()
            }
        }
        if (mObtainedDrawable is BitmapDrawable) {
            (mObtainedDrawable as BitmapDrawable?)?.bitmap?.recycle()
        }
        mObtainedDrawable?.callback = null
        mObtainedDrawable = null
        mBitmap?.recycle()
        mBitmap = null
        release()
    }

    private fun prepareBitmap() {
        if (mBitmap != null) return
        mBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    private fun updateBitmap() {
        prepareBitmap()
        mObtainedDrawable!!.draw(Canvas(mBitmap!!))
    }

    @Synchronized
    fun obtain(): Boolean {
        return if (isRecycled) {
            false
        } else {
            ++mReferences
            true
        }
    }

    @Synchronized
    fun release() {
        --mReferences
        if (mReferences <= 0 && isRecycled) {
            recycle()
        }
    }

    fun getDrawable(): Drawable {
        check(obtain()) { "Recycled!" }
        return mObtainedDrawable as Drawable
    }

//    fun render(
//        srcX: Int, srcY: Int, dst: Bitmap, dstX: Int, dstY: Int,
//        width: Int, height: Int
//    ) {
//        check(!hardware) { "Hardware buffer cannot be used in glgallery" }
//        val bitmap: Bitmap = if (animated) {
//            updateBitmap()
//            mBitmap!!
//        } else {
//            (mObtainedDrawable as BitmapDrawable).bitmap
//        }
//        nativeRender(
//            bitmap,
//            srcX,
//            srcY,
//            dst,
//            dstX,
//            dstY,
//            width,
//            height
//        )
//    }

    fun texImage(init: Boolean, offsetX: Int, offsetY: Int, width: Int, height: Int) {
        check(!hardware) { "Hardware buffer cannot be used in glgallery" }
        try {
            val bitmap: Bitmap = if (animated) {
                updateBitmap()
                mBitmap!!
            } else {
                if (mObtainedDrawable == null) {
                    return
                }
                if (mObtainedDrawable is BitmapDrawable){
                    (mObtainedDrawable as BitmapDrawable).bitmap
                }else{
                    val stickerBitmap = Bitmap.createBitmap(mObtainedDrawable!!.intrinsicWidth, mObtainedDrawable!!.intrinsicHeight, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(stickerBitmap)
                    mObtainedDrawable!!.setBounds(0, 0, stickerBitmap.width, stickerBitmap.height)
                    mObtainedDrawable!!.draw(canvas)
                    stickerBitmap
                }
            }
            nativeTexImage(
                bitmap,
                init,
                offsetX,
                offsetY,
                width,
                height
            )
        }catch (e:ClassCastException){
            FirebaseCrashlytics.getInstance().recordException(e)
            return
        }
    }

    fun start() {
        if (!started) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                (mObtainedDrawable as AnimatedImageDrawable?)?.start()
            }
        }
    }

    val delay: Int
        get() {
            if (animated)
                return 50
            return 0
        }

    @get:SuppressWarnings("deprecation")
    val isOpaque: Boolean
        get() {
            return mObtainedDrawable?.opacity == PixelFormat.OPAQUE
        }

    companion object {
        var screenWidth: Int = 0
        var screenHeight: Int = 0

        @JvmStatic
        fun initialize(ehApplication: EhApplication) {
            screenWidth = ehApplication.resources.displayMetrics.widthPixels
            screenHeight = ehApplication.resources.displayMetrics.heightPixels
        }

        @JvmStatic
        fun decode(stream: FileInputStream, hardware: Boolean = true): Image? {
            try {
                return Image(stream, hardware = hardware)
            } catch (e: Exception) {
                e.printStackTrace()
                FirebaseCrashlytics.getInstance().recordException(e)
                return null
            }
        }

        @JvmStatic
        fun decode(drawable: Drawable?, hardware: Boolean = true): Image? {
            try {
                return Image(null, drawable, hardware = hardware)
            } catch (e: Exception) {
                e.printStackTrace()
                FirebaseCrashlytics.getInstance().recordException(e)
                return null
            }
        }

//        @JvmStatic
//        fun decode(buffer: ByteBuffer, hardware: Boolean = true, release: () -> Unit? = {}): Image {
//            val src = ImageDecoder.createSource(buffer)
//            return Image(src, hardware = hardware) {
//                release()
//            }
//        }

        @JvmStatic
        fun create(bitmap: Bitmap): Image? {
            try {
                return Image(null, bitmap.toDrawable(Resources.getSystem()), false)
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        @JvmStatic
        private external fun nativeRender(
            bitmap: Bitmap,
            srcX: Int, srcY: Int, dst: Bitmap, dstX: Int, dstY: Int,
            width: Int, height: Int
        )

        @JvmStatic
        private external fun nativeTexImage(
            bitmap: Bitmap,
            init: Boolean,
            offsetX: Int,
            offsetY: Int,
            width: Int,
            height: Int
        )
    }
}