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

import android.graphics.Bitmap
import android.util.Log
import java.lang.ref.WeakReference

internal class BitmapPool {
    private val mReusableBitmapSet: MutableSet<WeakReference<Bitmap>> = LinkedHashSet()
    @Synchronized
    fun put(bitmap: Bitmap?) {
        if (bitmap != null) {
            mReusableBitmapSet.add(WeakReference(bitmap))
        }
    }

    @Synchronized
    operator fun get(width: Int, height: Int): Bitmap? {
        val iterator = mReusableBitmapSet.iterator()
        var item: Bitmap?
        while (iterator.hasNext()) {
            item = iterator.next().get()
            if (item != null) {
                if (item.width == width && item.height == height) {
                    // Remove from reusable set so it can't be used again.
                    iterator.remove()
                    return item
                }
            } else {
                // Remove from the set if the reference has been cleared or
                // it can't be used.
                iterator.remove()
            }
        }

        // Can not find reusable bitmap
        return try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "Out of memory")
            null
        }
    }

    companion object {
        private val TAG = BitmapPool::class.java.simpleName
    }
}