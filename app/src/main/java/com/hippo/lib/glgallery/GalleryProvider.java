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

package com.hippo.lib.glgallery;

import android.util.LruCache;

import androidx.annotation.IntDef;
import androidx.annotation.UiThread;

import com.hippo.glview.glrenderer.GLCanvas;
import com.hippo.glview.image.ImageWrapper;
import com.hippo.glview.view.GLRoot;
import com.hippo.image.Image;
import com.hippo.yorozuya.ConcurrentPool;
import com.hippo.yorozuya.MathUtils;
import com.hippo.yorozuya.OSUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public abstract class GalleryProvider {

    public static final int STATE_WAIT = -1;
    public static final int STATE_ERROR = -2;

    private final ConcurrentPool<NotifyTask> mNotifyTaskPool = new ConcurrentPool<>(5);
    private volatile Listener mListener;
    private volatile GLRoot mGLRoot;

    private final ImageCache mImageCache = new ImageCache();

    private boolean mStarted = false;

    @UiThread
    public void start() {
        OSUtils.checkMainLoop();

        if (mStarted) {
            throw new IllegalStateException("Can't start it twice");
        }
        mStarted = true;
    }

    @UiThread
    public void stop() {
        OSUtils.checkMainLoop();
        mImageCache.evictAll();
    }

    public void setGLRoot(GLRoot glRoot) {
        mGLRoot = glRoot;
    }

    /**
     * @return {@link #STATE_WAIT} for wait,
     *          {@link #STATE_ERROR} for error, {@link #getError()} to get error message,
     *          0 for empty
     */
    public abstract int size();

    public final void request(int index) {
        ImageWrapper imageWrapper = mImageCache.get(index);
        if (imageWrapper != null) {
            notifyPageSucceed(index, imageWrapper);
        } else {
            onRequest(index);
        }
    }

    public final void forceRequest(int index) {
        onForceRequest(index);
    }

    public void removeCache(int index) {
        mImageCache.remove(index);
    }

    protected abstract void onRequest(int index);

    protected abstract void onForceRequest(int index);

    public final void cancelRequest(int index) {
        onCancelRequest(index);
    }

    protected abstract void onCancelRequest(int index);

    public abstract String getError();

    public void setListener(Listener listener) {
        mListener = listener;
    }

    public void notifyDataChanged() {
        notify(NotifyTask.TYPE_DATA_CHANGED, -1, 0.0f, null, null);
    }

    public void notifyDataChanged(int index) {
        notify(NotifyTask.TYPE_DATA_CHANGED, index, 0.0f, null, null);
    }

    public void notifyPageWait(int index) {
        notify(NotifyTask.TYPE_WAIT, index, 0.0f, null, null);
    }

    public void notifyPagePercent(int index, float percent) {
        notify(NotifyTask.TYPE_PERCENT, index, percent, null, null);
    }

    public void notifyPageSucceed(int index, Image image) {
        ImageWrapper imageWrapper = new ImageWrapper(image);
        mImageCache.add(index, imageWrapper);
        notifyPageSucceed(index, imageWrapper);
    }

    public void notifyPageSucceed(int index, ImageWrapper image) {
        notify(NotifyTask.TYPE_SUCCEED, index, 0.0f, image, null);
    }

    public void notifyPageFailed(int index, String error) {
        notify(NotifyTask.TYPE_FAILED, index, 0.0f, null, error);
    }

    private void notify(@NotifyTask.Type int type, int index, float percent, ImageWrapper image, String error) {
        Listener listener = mListener;
        if (listener == null) {
            return;
        }

        GLRoot glRoot = mGLRoot;
        if (glRoot == null) {
            return;
        }

        NotifyTask task = mNotifyTaskPool.pop();
        if (task == null) {
            task = new NotifyTask(listener, mNotifyTaskPool);
        }
        task.setData(type, index, percent, image, error);
        glRoot.addOnGLIdleListener(task);
    }

    private static class NotifyTask implements GLRoot.OnGLIdleListener {

        @IntDef({TYPE_DATA_CHANGED, NotifyTask.TYPE_WAIT, TYPE_PERCENT, TYPE_SUCCEED, TYPE_FAILED})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Type {}

        public static final int TYPE_DATA_CHANGED = 0;
        public static final int TYPE_WAIT = 1;
        public static final int TYPE_PERCENT = 2;
        public static final int TYPE_SUCCEED = 3;
        public static final int TYPE_FAILED = 4;

        private final Listener mListener;
        private final ConcurrentPool<NotifyTask> mPool;

        @Type
        private int mType;
        private int mIndex;
        private float mPercent;
        private ImageWrapper mImage;
        private String mError;

        public NotifyTask(Listener listener, ConcurrentPool<NotifyTask> pool) {
            mListener = listener;
            mPool = pool;
        }

        public void setData(@Type int type, int index, float percent, ImageWrapper image, String error) {
            mType = type;
            mIndex = index;
            mPercent = percent;
            mImage = image;
            mError = error;
        }

        @Override
        public boolean onGLIdle(GLCanvas canvas, boolean renderRequested) {
            switch (mType) {
                case TYPE_DATA_CHANGED:
                    if (mIndex < 0) {
                        mListener.onDataChanged();
                    } else {
                        mListener.onDataChanged(mIndex);
                    }
                    break;
                case TYPE_WAIT:
                    mListener.onPageWait(mIndex);
                    break;
                case TYPE_PERCENT:
                    mListener.onPagePercent(mIndex, mPercent);
                    break;
                case TYPE_SUCCEED:
                    mListener.onPageSucceed(mIndex, mImage);
                    break;
                case TYPE_FAILED:
                    mListener.onPageFailed(mIndex, mError);
                    break;
            }

            // Clean data
            mImage = null;
            mError = null;
            // Push back
            mPool.push(this);

            return false;
        }
    }

    private static class ImageCache extends LruCache<Integer, ImageWrapper> {

        private static final long MAX_CACHE_SIZE = 128 * 1024 * 1024;
        private static final long MIN_CACHE_SIZE = 32 * 1024 * 1024;

        public ImageCache() {
            super((int) MathUtils.clamp(OSUtils.getTotalMemory() / 16, MIN_CACHE_SIZE, MAX_CACHE_SIZE));
        }

        public void add(Integer key, ImageWrapper value) {
            if (value.getFormat() != Image.FORMAT_GIF && value.obtain()) {
                put(key, value);
            }
        }

        @Override
        protected int sizeOf(Integer key, ImageWrapper value) {
            int size = value.getWidth() * value.getHeight() * 4;
            if (value.getFormat() == Image.FORMAT_GIF) {
                size *= 5;
            }
            return size;
        }

        @Override
        protected void entryRemoved(boolean evicted, Integer key, ImageWrapper oldValue, ImageWrapper newValue) {
            if (oldValue != null) {
                oldValue.release();
            }
        }
    }

    public interface Listener {

        void onDataChanged();

        void onPageWait(int index);

        void onPagePercent(int index, float percent);

        void onPageSucceed(int index, ImageWrapper image);

        void onPageFailed(int index, String error);

        void onDataChanged(int index);
    }
}
