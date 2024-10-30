/*
 * Copyright (C) 2015-2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.beerbelly;

import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.streampipe.InputStreamPipe;
import com.hippo.streampipe.OutputStreamPipe;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public abstract class BeerBelly<V> {

    private static final String TAG = BeerBelly.class.getSimpleName();
    private final boolean mHasMemoryCache;
    private final boolean mHasDiskCache;
    @Nullable
    private MemoryCache<V> mMemoryCache;
    @Nullable
    private DiskCache<V> mDiskCache;

    public BeerBelly(BeerBellyParams params) {
        params.isValid();
        mHasMemoryCache = params.hasMemoryCache;
        mHasDiskCache = params.hasDiskCache;

        if (mHasMemoryCache) {
            initMemoryCache(params.memoryCacheMaxSize);
        }

        if (mHasDiskCache) {
            initDiskCache(params.diskCacheDir, params.diskCacheMaxSize);
        }
    }

    private void initMemoryCache(int maxSize) {
        mMemoryCache = new MemoryCache<>(maxSize, this);
    }

    private void initDiskCache(File cacheDir, int maxSize) {
        // Set up disk cache
        try {
            mDiskCache = new DiskCache<>(cacheDir, maxSize, this);
        } catch (IOException e) {
            Log.e(TAG, "Can't create disk cache", e);
        }
    }

    protected abstract int sizeOf(String key, V value);

    protected abstract void memoryEntryAdded(V value);

    protected abstract void memoryEntryRemoved(boolean evicted, String key, V oldValue, V newValue);

    protected abstract V read(@NonNull InputStreamPipe isPipe);

    protected abstract boolean write(OutputStream os, V value);

    @Nullable
    public LruCache<String, V> getMemoryCache() {
        return mMemoryCache;
    }

    @Nullable
    public SimpleDiskCache getDiskCache() {
        if (mDiskCache != null) {
            return mDiskCache.mDiskCache;
        } else {
            return null;
        }
    }

    /**
     * Check if have memory cache
     *
     * @return true if have memory cache
     */
    public boolean hasMemoryCache() {
        return mHasMemoryCache;
    }

    /**
     * Check if have disk cache
     *
     * @return true if have disk cache
     */
    public boolean hasDiskCache() {
        return mHasDiskCache;
    }

    /**
     * Get memory cache usage size
     *
     * @return {@code -1} for no memory cache
     */
    public int memorySize() {
        if (mHasMemoryCache && mMemoryCache != null) {
            return mMemoryCache.size();
        } else {
            return -1;
        }
    }

    /**
     * Get memory cache max size
     *
     * @return {@code -1} for no memory cache
     */
    public int memoryMaxSize() {
        if (mHasMemoryCache && mMemoryCache != null) {
            return mMemoryCache.maxSize();
        } else {
            return -1;
        }
    }

    /**
     * Get disk cache usage size
     *
     * @return {@code -1L} for no memory cache
     */
    public long diskSize() {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.size();
        } else {
            return -1L;
        }
    }

    /**
     * Get disk cache max size
     *
     * @return {@code -1L} for no memory cache
     */
    public long diskMaxSize() {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.getMaxSize();
        } else {
            return -1L;
        }
    }

    /**
     * Get value from memory cache
     *
     * @param key the key to get value
     * @return the value you get, null for miss or no memory cache
     */
    public V getFromMemory(@NonNull String key) {
        if (mHasMemoryCache && mMemoryCache != null) {
            return mMemoryCache.get(key);
        } else {
            return null;
        }
    }

    /**
     * Get value from disk cache. Override {@link #read(InputStreamPipe)} to do it
     *
     * @param key the key to get value
     * @return the value you get, null for miss or no memory cache or get error
     */
    public V getFromDisk(@NonNull String key) {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.get(key);
        } else {
            return null;
        }
    }

    /**
     * Get value from memory cache and disk cache. If miss in memory cache and
     * get in disk cache, it will put value from disk cache to memory cache.
     *
     * @param key the key to get value
     * @return the value you get
     */
    public V get(@NonNull String key) {
        V value = getFromMemory(key);

        if (value != null) {
            // Get it in memory cache
            return value;
        }

        value = getFromDisk(key);

        if (value != null) {
            // Get it in disk cache
            putToMemory(key, value);
            return value;
        }

        return null;
    }

    /**
     * Put value to memory cache
     *
     * @param key   the key
     * @param value the value
     * @return false if no memory cache
     */
    public boolean putToMemory(@NonNull String key, @NonNull V value) {
        if (mHasMemoryCache && mMemoryCache != null) {
            memoryEntryAdded(value);
            mMemoryCache.put(key, value);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Put value to disk cache
     *
     * @param key   the key
     * @param value the value
     * @return false if no disk cache or get error
     */
    public boolean putToDisk(@NonNull String key, @NonNull V value) {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.put(key, value);
        } else {
            return false;
        }
    }

    /**
     * Put value to memory cache and disk cache
     *
     * @param key   the key
     * @param value the value
     */
    public void put(@NonNull String key, @NonNull V value) {
        putToMemory(key, value);
        putToDisk(key, value);
    }

    public void removeFromMemory(@NonNull String key) {
        if (mHasMemoryCache && mMemoryCache != null) {
            mMemoryCache.remove(key);
        }
    }

    public void removeFromDisk(@NonNull String key) {
        if (mHasDiskCache && mDiskCache != null) {
            mDiskCache.remove(key);
        }
    }

    public void remove(@NonNull String key) {
        removeFromMemory(key);
        removeFromDisk(key);
    }

    public boolean pullFromDiskCache(@NonNull String key, @NonNull OutputStream os) {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.pull(key, os);
        } else {
            return false;
        }
    }

    /**
     * @param key the key
     * @param is  the input stream to store
     * @return false if no disk cache or get error
     */
    public boolean putRawToDisk(@NonNull String key, @NonNull InputStream is) {
        if (mHasDiskCache && mDiskCache != null) {
            return mDiskCache.putRaw(key, is);
        } else {
            return false;
        }
    }

    /**
     * Evicts all of the items from the memory cache
     */
    public void clearMemory() {
        if (mHasMemoryCache && mMemoryCache != null) {
            mMemoryCache.evictAll();
        }
    }

    /**
     * Clear disk cache
     */
    public void clearDisk() {
        if (mHasDiskCache && mDiskCache != null) {
            mDiskCache.clear();
        }
    }

    public void flush() {
        if (mHasDiskCache && mDiskCache != null) {
            mDiskCache.flush();
        }
    }

    /**
     * Evicts all of the items from the memory cache and lets the system know
     * now would be a good time to garbage collect
     */
    public void clear() {
        clearMemory();
        clearDisk();
    }

    /**
     * A holder class that contains cache parameters.
     */
    public static class BeerBellyParams {

        /**
         * is memory cache available
         */
        public boolean hasMemoryCache = false;
        /**
         * the maximum number of bytes the memory cache should use to store
         */
        public int memoryCacheMaxSize = 0;
        /**
         * is disk cache available
         */
        public boolean hasDiskCache = false;
        /**
         * the dir to store disk cache
         */
        public File diskCacheDir = null;
        /**
         * the maximum number of bytes the disk cache should use to store
         */
        public int diskCacheMaxSize = 0;

        /**
         * Check BeerBellyParams is valid
         */
        public void isValid() throws IllegalStateException {
            if (!hasMemoryCache && !hasDiskCache) {
                throw new IllegalStateException("No memory cache and no disk cache. What can I do for you?");
            }

            if (hasMemoryCache && memoryCacheMaxSize <= 0) {
                throw new IllegalStateException("Memory cache max size must be bigger than 0.");
            }

            if (hasDiskCache) {
                if (diskCacheDir == null) {
                    throw new IllegalStateException("Disk cache dir can't be null.");
                }
                if (diskCacheMaxSize <= 0) {
                    throw new IllegalStateException("Disk cache max size must be bigger than 0.");
                }
            }
        }
    }

    private static class DiskCache<E> {

        private static final int IO_BUFFER_SIZE = 8 * 1024;

        private final SimpleDiskCache mDiskCache;
        private final BeerBelly<E> mParent;

        private final File mCacheDir;
        private final int mMaxSize;

        public DiskCache(File cacheDir, int size, BeerBelly<E> parent) throws IOException {
            mDiskCache = new SimpleDiskCache(cacheDir, size);
            mParent = parent;

            mCacheDir = cacheDir;
            mMaxSize = size;
        }

        public File getCacheDir() {
            return mCacheDir;
        }

        public int getMaxSize() {
            return mMaxSize;
        }

        public long size() {
            return mDiskCache.size();
        }

        public void flush() {
            mDiskCache.flush();
        }

        public void clear() {
            mDiskCache.clear();
        }

        public E get(String key) {
            InputStreamPipe isPipe = mDiskCache.getInputStreamPipe(key);
            if (isPipe == null) {
                return null;
            } else {
                return mParent.read(isPipe);
            }
        }

        public boolean put(String key, E value) {
            OutputStreamPipe osPipe = mDiskCache.getOutputStreamPipe(key);
            BufferedOutputStream buffOut = null;
            try {
                osPipe.obtain();
                OutputStream os = osPipe.open();
                buffOut = new BufferedOutputStream(os, IO_BUFFER_SIZE);
                return mParent.write(os, value);
            } catch (IOException e) {
                return false;
            } finally {
                Util.closeQuietly(buffOut);
                osPipe.close();
                osPipe.release();
            }
        }

        public boolean putRaw(String key, InputStream is) {
            return mDiskCache.put(key, is);
        }

        public void remove(String key) {
            mDiskCache.remove(key);
        }

        public boolean pull(@NonNull String key, @NonNull OutputStream os) {
            InputStreamPipe isPipe = mDiskCache.getInputStreamPipe(key);
            if (isPipe == null) {
                return false;
            } else {
                try {
                    isPipe.obtain();
                    InputStream is = isPipe.open();
                    Util.copy(is, os);
                    return true;
                } catch (IOException e) {
                    return false;
                } finally {
                    isPipe.close();
                    isPipe.release();
                }
            }
        }
    }

    private class MemoryCache<E> extends LruCache<String, E> {

        public BeerBelly<E> mParent;

        public MemoryCache(int maxSize, BeerBelly<E> parent) {
            super(maxSize);
            mParent = parent;
        }

        @Override
        protected int sizeOf(String key, E value) {
            return mParent.sizeOf(key, value);
        }

        @Override
        protected void entryRemoved(boolean evicted, String key, E oldValue, E newValue) {
            mParent.memoryEntryRemoved(evicted, key, oldValue, newValue);
        }
    }
}
