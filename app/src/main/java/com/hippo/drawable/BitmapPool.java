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

package com.hippo.drawable;

import android.graphics.Bitmap;
import android.util.Log;
import androidx.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

class BitmapPool {

    private static final String TAG = BitmapPool.class.getSimpleName();

    private final Set<WeakReference<Bitmap>> mReusableBitmapSet = new LinkedHashSet<>();

    public synchronized void put(@Nullable Bitmap bitmap) {
        if (bitmap != null) {
            mReusableBitmapSet.add(new WeakReference<>(bitmap));
        }
    }

    @Nullable
    public synchronized Bitmap get(int width, int height) {
        final Iterator<WeakReference<Bitmap>> iterator = mReusableBitmapSet.iterator();
        Bitmap item;
        while (iterator.hasNext()) {
            item = iterator.next().get();
            if (item != null) {
                if (item.getWidth() == width && item.getHeight() == height) {
                    // Remove from reusable set so it can't be used again.
                    iterator.remove();
                    return item;
                }
            } else {
                // Remove from the set if the reference has been cleared or
                // it can't be used.
                iterator.remove();
            }
        }

        // Can not find reusable bitmap
        try {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "Out of memory");
            return null;
        }
    }
}
