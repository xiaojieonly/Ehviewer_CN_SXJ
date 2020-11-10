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

package com.hippo.ehviewer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.hippo.conaco.ValueHelper;
import com.hippo.image.ImageBitmap;
import com.hippo.streampipe.InputStreamPipe;
import java.io.IOException;

public class ImageBitmapHelper implements ValueHelper<ImageBitmap> {

    private static final int MAX_CACHE_SIZE = 512 * 512;

    @Nullable
    @Override
    public ImageBitmap decode(@NonNull InputStreamPipe isPipe) {
        try {
            isPipe.obtain();
            return ImageBitmap.decode(isPipe.open());
        } catch (OutOfMemoryError e) {
            return null;
        } catch (IOException e) {
            return null;
        } finally {
            isPipe.close();
            isPipe.release();
        }
    }

    @Override
    public int sizeOf(@NonNull String key, @NonNull ImageBitmap value) {
        return value.getWidth() * value.getHeight() * 4 /* value.getByteCount() TODO Update Image */;
    }

    @Override
    public void onAddToMemoryCache(@NonNull ImageBitmap oldValue) {
        oldValue.obtain();
    }

    @Override
    public void onRemoveFromMemoryCache(@NonNull String key, @NonNull ImageBitmap oldValue) {
        oldValue.release();
    }

    @Override
    public boolean useMemoryCache(@NonNull String key, ImageBitmap value) {
        if (value != null) {
            return value.getWidth() * value.getHeight() <= MAX_CACHE_SIZE
                    /* value.getByteCount() <= MAX_CACHE_BYTE_COUNT TODO Update Image */;
        } else {
            return true;
        }
    }
}
