/*
 * Copyright 2015-2016 Hippo Seven
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

package com.hippo.beerbelly;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.streampipe.InputStreamPipe;
import com.hippo.streampipe.OutputStreamPipe;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SimpleDiskCache {

    private static final String TAG = SimpleDiskCache.class.getSimpleName();

    /**
     * Disk cache is free
     */
    private static final int STATE_DISK_CACHE_NONE = 0;

    /**
     * Disk cache is flushing or clearing
     */
    private static final int STATE_DISK_CACHE_IN_USE = 1;

    /**
     * Disk cache is reading or writing
     */
    private static final int STATE_DISK_CACHE_BUSY = 2;

    private final File mCacheDir;
    private final int mSize;
    @NonNull
    private final Map<String, CounterLock> mLockMap = new HashMap<>();
    @NonNull
    private final LockPool mLockPool = new LockPool();
    private int mDiskCacheState;
    @Nullable
    private DiskLruCache mDiskLruCache;

    public SimpleDiskCache(File cacheDir, int size) {
        mCacheDir = cacheDir;
        mSize = size;

        try {
            init();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable
     * for using as a disk filename.
     *
     * @param key The key used to store the file
     */
    private static String hashKeyForDisk(final String key) {
        String cacheKey;
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(key.getBytes());
            cacheKey = bytesToHexString(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    /**
     * http://stackoverflow.com/questions/332079
     *
     * @param bytes The bytes to convert.
     * @return A {@link String} converted from the bytes of a hashable key used
     * to store a filename on the disk, to hex digits.
     */
    private static String bytesToHexString(final byte[] bytes) {
        final StringBuilder builder = new StringBuilder();
        for (final byte b : bytes) {
            final String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    public synchronized boolean isValid() {
        return mDiskLruCache != null;
    }

    private synchronized void init() throws IOException {
        if (!isValid()) {
            mDiskLruCache = DiskLruCache.open(mCacheDir, 1, 1, mSize);
        }
    }

    public synchronized long size() {
        if (null != mDiskLruCache) {
            return mDiskLruCache.size();
        } else {
            return -1L;
        }
    }

    public synchronized void flush() {
        // Wait for cache available
        while (mDiskCacheState != STATE_DISK_CACHE_NONE) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mDiskCacheState = STATE_DISK_CACHE_BUSY;

        if (null != mDiskLruCache) {
            try {
                mDiskLruCache.flush();
            } catch (IOException e) {
                Log.e(TAG, "SimpleDiskCache flush", e);
            }
        }

        mDiskCacheState = STATE_DISK_CACHE_NONE;
        notifyAll();
    }

    public synchronized boolean clear() {
        boolean result = true;
        if (null == mDiskLruCache) {
            return false;
        }

        // Wait for cache available
        while (mDiskCacheState != STATE_DISK_CACHE_NONE) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        mDiskCacheState = STATE_DISK_CACHE_BUSY;

        try {
            mDiskLruCache.delete();
        } catch (IOException e) {
            Log.e(TAG, "SimpleDiskCache clearCache", e);
        }
        mDiskLruCache = null;
        try {
            init();
        } catch (IOException e) {
            Log.e(TAG, "SimpleDiskCache init", e);
            result = false;
        }

        mDiskCacheState = STATE_DISK_CACHE_NONE;
        notifyAll();
        return result;
    }

    private synchronized CounterLock obtainLock(String key) {
        // Wait for clear over
        while (mDiskCacheState == STATE_DISK_CACHE_BUSY) {
            try {
                wait();
            } catch (InterruptedException e) {
                // Empty
            }
        }
        mDiskCacheState = STATE_DISK_CACHE_IN_USE;

        // Get lock from key
        CounterLock lock;
        lock = mLockMap.get(key);
        if (lock == null) {
            lock = mLockPool.get();
            mLockMap.put(key, lock);
        }
        lock.obtain();

        return lock;
    }

    private synchronized void releaseLock(String key, CounterLock lock) {
        lock.release();
        if (lock.isFree()) {
            mLockMap.remove(key);
            mLockPool.push(lock);
        }

        if (mLockMap.isEmpty()) {
            mDiskCacheState = STATE_DISK_CACHE_NONE;
            notifyAll();
        }
    }

    public boolean contain(@NonNull String key) {
        String diskKey = hashKeyForDisk(key);
        CounterLock lock = obtainLock(diskKey);
        if (null == mDiskLruCache) {
            releaseLock(diskKey, lock);
            return false;
        }
        lock.readLock().lock();

        boolean result = mDiskLruCache.contain(diskKey);

        lock.readLock().unlock();
        releaseLock(diskKey, lock);

        return result;
    }

    public boolean remove(@NonNull String key) {
        String diskKey = hashKeyForDisk(key);
        CounterLock lock = obtainLock(diskKey);
        if (null == mDiskLruCache) {
            releaseLock(diskKey, lock);
            return false;
        }
        lock.writeLock().lock();

        boolean result;
        try {
            result = mDiskLruCache.remove(diskKey);
        } catch (IOException e) {
            e.printStackTrace();
            result = false;
        }

        lock.writeLock().unlock();
        releaseLock(diskKey, lock);

        return result;
    }

    public boolean put(@NonNull String key, @NonNull InputStream is) {
        String diskKey = hashKeyForDisk(key);
        CounterLock lock = obtainLock(diskKey);
        if (null == mDiskLruCache) {
            releaseLock(diskKey, lock);
            return false;
        }
        lock.writeLock().lock();

        boolean result = false;
        if (isValid()) {
            result = putToDisk(diskKey, is);
        }

        lock.writeLock().unlock();
        releaseLock(diskKey, lock);

        return result;
    }

    private boolean putToDisk(String key, InputStream is) {
        DiskLruCache.Editor editor = null;
        OutputStream os = null;
        boolean completeEdit = false;
        try {
            //noinspection ConstantConditions
            editor = mDiskLruCache.edit(key);
            if (editor == null) {
                // The editor is in progress
                return false;
            }

            os = editor.newOutputStream(0);
            if (os != null) {
                Util.copy(is, os);
                completeEdit = true;
                editor.commit();
                return true;
            } else {
                // Can't get OutputStream
                completeEdit = true;
                editor.abort();
                return false;
            }
        } catch (IOException e) {
            Util.closeQuietly(os);
            try {
                if (!completeEdit && editor != null) {
                    editor.abort();
                }
            } catch (IOException ignored) {
            }
            return false;
        }
    }

    /**
     * @param key the key of the target
     * @return the InputStreamPipe, <code>null</code> for missing
     */
    @Nullable
    public InputStreamPipe getInputStreamPipe(@NonNull String key) {
        if (contain(key)) {
            String diskKey = hashKeyForDisk(key);
            return new CacheInputStreamPipe(diskKey);
        } else {
            return null;
        }
    }

    @NonNull
    public OutputStreamPipe getOutputStreamPipe(@NonNull String key) {
        String diskKey = hashKeyForDisk(key);
        return new CacheOutputStreamPipe(diskKey);
    }

    private class CacheInputStreamPipe implements InputStreamPipe {

        private final String mKey;
        private CounterLock mLock;
        private DiskLruCache.Snapshot mCurrentSnapshot;

        private CacheInputStreamPipe(String key) {
            mKey = key;
        }

        @Override
        public void obtain() {
            if (mLock == null) {
                mLock = obtainLock(mKey);
                mLock.readLock().lock();
            }
        }

        @Override
        public void release() {
            if (mCurrentSnapshot != null) {
                throw new IllegalStateException("Please close it first");
            }

            if (mLock != null) {
                mLock.readLock().unlock();
                releaseLock(mKey, mLock);
                mLock = null;
            }
        }

        @Override
        public @NonNull
        InputStream open() throws IOException {
            if (mLock == null) {
                throw new IllegalStateException("Please obtain it first");
            }
            if (mCurrentSnapshot != null) {
                throw new IllegalStateException("Please close it before reopen");
            }
            if (null == mDiskLruCache) {
                throw new IOException("Can't find disk lru cache");
            }

            DiskLruCache.Snapshot snapshot;
            snapshot = mDiskLruCache.get(mKey);
            if (snapshot == null) {
                throw new IOException("Miss the key " + mKey);
            }
            mCurrentSnapshot = snapshot;
            return snapshot.getInputStream(0);
        }

        @Override
        public void close() {
            Util.closeQuietly(mCurrentSnapshot);
            mCurrentSnapshot = null;
        }
    }

    private class CacheOutputStreamPipe implements OutputStreamPipe {

        private final String mKey;
        private CounterLock mLock;
        private DiskLruCache.Editor mCurrentEditor;

        private CacheOutputStreamPipe(String key) {
            mKey = key;
        }

        @Override
        public void obtain() {
            if (mLock == null) {
                mLock = obtainLock(mKey);
                mLock.writeLock().lock();
            }
        }

        @Override
        public void release() {
            if (mCurrentEditor != null) {
                throw new IllegalStateException("Please close it first");
            }

            if (mLock != null) {
                mLock.writeLock().unlock();
                releaseLock(mKey, mLock);
                mLock = null;
            }
        }

        @Override
        public @NonNull
        OutputStream open() throws IOException {
            if (mLock == null) {
                throw new IllegalStateException("Please obtain it first");
            }
            if (mCurrentEditor != null) {
                throw new IllegalStateException("Please close it before reopen");
            }
            if (null == mDiskLruCache) {
                throw new IOException("Can't find disk lru cache");
            }

            DiskLruCache.Editor editor = mDiskLruCache.edit(mKey);
            if (editor == null) {
                throw new IOException("Miss the key " + mKey);
            }

            OutputStream os;
            try {
                os = editor.newOutputStream(0);
                mCurrentEditor = editor;
                return os;
            } catch (IOException e) {
                // Get trouble
                try {
                    editor.abort();
                } catch (IOException ex) {
                    // Ignore
                }
                throw e;
            }
        }

        @Override
        public void close() {
            if (mCurrentEditor != null) {
                try {
                    mCurrentEditor.commit();
                } catch (IOException e) {
                    // Empty
                }
                mCurrentEditor = null;
            }
        }
    }

    private class CounterLock extends ReentrantReadWriteLock {

        private int mCount;

        public boolean isFree() {
            return mCount == 0;
        }

        public void release() {
            if (--mCount < 0) {
                throw new IllegalStateException("Release time is more than occupy time");
            }
        }

        public void obtain() {
            ++mCount;
        }
    }

    private class LockPool {

        private static final int MAX_SIZE = 10;

        private final CounterLock[] mArray = new CounterLock[MAX_SIZE];
        private final int mMaxSize = MAX_SIZE;
        private int mSize;

        public void push(CounterLock lock) {
            if (lock != null && mSize < mMaxSize) {
                mArray[mSize++] = lock;
            }
        }

        public CounterLock get() {
            if (mSize > 0) {
                CounterLock lock = mArray[--mSize];
                mArray[mSize] = null;
                return lock;
            } else {
                return new CounterLock();
            }
        }
    }
}
