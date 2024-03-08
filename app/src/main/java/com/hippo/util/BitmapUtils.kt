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
package com.hippo.util

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.hippo.streampipe.InputStreamPipe
import com.hippo.yorozuya.MathUtils
import java.io.IOException

@SuppressLint("StaticFieldLeak")
object BitmapUtils {
    var sContext: Context? = null
    @JvmStatic
    fun initialize(context: Context) {
        sContext = context.applicationContext
    }

    fun availableMemory(): Long {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val activityManager =
            sContext!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val total = (activityManager.memoryClass * 1024 * 1024).toLong()
        return total - used
    }

    /**
     * @return null or the bitmap
     */
    @JvmStatic
    @JvmOverloads
    fun decodeStream(
        isp: InputStreamPipe,
        maxWidth: Int,
        maxHeight: Int,
        pixels: Int = -1,
        checkMemory: Boolean = false,
        justCalc: Boolean = false,
        sampleSize: IntArray? = null
    ): Bitmap? {
        return try {
            isp.obtain()
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(isp.open(), null, options)
            isp.close()
            val width = options.outWidth
            val height = options.outHeight
            if (width <= 0 || height <= 0) {
                if (sampleSize != null && sampleSize.size >= 1) {
                    sampleSize[0] = -1
                }
                return null
            }
            var scaleW = 1
            var scaleH = 1
            var scaleP = 1
            var scaleM = 1
            if (maxWidth > 0 && width > maxWidth) {
                scaleW = MathUtils.ceilDivide(width, maxWidth)
            }
            if (maxHeight > 0 && height > maxHeight) {
                scaleH = MathUtils.ceilDivide(height, maxHeight)
            }
            if (pixels > 0 && width * height > pixels) {
                scaleP =
                    Math.ceil(Math.sqrt((width * height / pixels.toFloat()).toDouble())).toInt()
            }
            if (checkMemory) {
                val m = availableMemory() - 5 * 1024 * 1024 // Leave 5m
                if (m < 0) {
                    if (sampleSize != null && sampleSize.size >= 1) {
                        sampleSize[0] = -1
                    }
                    return null
                }
                if (width * height * 3 > m) {
                    scaleM =
                        Math.ceil(Math.sqrt((width * height * 3 / m.toFloat()).toDouble())).toInt()
                }
            }
            options.inSampleSize =
                MathUtils.nextPowerOf2(MathUtils.max(scaleW, scaleH, scaleP, scaleM, 1))
            if (sampleSize != null && sampleSize.size >= 1) {
                sampleSize[0] = options.inSampleSize
            }
            options.inJustDecodeBounds = false
            if (justCalc) {
                null
            } else {
                try {
                    BitmapFactory.decodeStream(isp.open(), null, options)
                } catch (e: OutOfMemoryError) {
                    if (sampleSize != null && sampleSize.size >= 1) {
                        sampleSize[0] = -1
                    }
                    null
                }
            }
        } catch (e: IOException) {
            if (sampleSize != null && sampleSize.size >= 1) {
                sampleSize[0] = -1
            }
            null
        } finally {
            isp.close()
            isp.release()
        }
    }
}