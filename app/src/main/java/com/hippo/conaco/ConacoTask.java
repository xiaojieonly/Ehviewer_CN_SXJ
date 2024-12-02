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

package com.hippo.conaco;


import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.hippo.beerbelly.SimpleDiskCache;
import com.hippo.streampipe.InputStreamPipe;
import com.hippo.streampipe.OutputStreamPipe;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class ConacoTask<V> {

    private static final String TAG = ConacoTask.class.getSimpleName();

    private final int mId;
    private final WeakReference<Unikery<V>> mUnikeryWeakReference;
    private final String mKey;
    private final String mUrl;
    private final DataContainer mDataContainer;
    private final boolean mUseMemoryCache;
    private final boolean mUseDiskCache;
    private final boolean mUseNetwork;
    private final ValueHelper<V> mHelper;
    private final ValueCache<V> mCache;
    private final OkHttpClient mOkHttpClient;
    private final Executor mDiskExecutor;
    private final Executor mNetworkExecutor;
    private final Conaco<V> mConaco;

    private DiskLoadTask mDiskLoadTask;
    private NetworkLoadTask mNetworkLoadTask;
    private Call mCall;
    private boolean mStart;
    private volatile boolean mStop;

    private ConacoTask(Builder<V> builder) {
        mId = builder.mId;
        mUnikeryWeakReference = new WeakReference<>(builder.mUnikery);
        mKey = builder.mKey;
        mUrl = builder.mUrl;
        mDataContainer = builder.mDataContainer;
        mUseMemoryCache = builder.mUseMemoryCache;
        mUseDiskCache = builder.mUseDiskCache;
        mUseNetwork = builder.mUseNetwork;
        mHelper = builder.mHelper;
        mCache = builder.mCache;
        mOkHttpClient = builder.mOkHttpClient;
        mDiskExecutor = builder.mDiskExecutor;
        mNetworkExecutor = builder.mNetworkExecutor;
        mConaco = builder.mConaco;
    }

    int getId() {
        return mId;
    }

    String getKey() {
        return mKey;
    }

    boolean useMemoryCache() {
        return mUseMemoryCache;
    }

    @Nullable
    Unikery<V> getUnikery() {
        return mUnikeryWeakReference.get();
    }

    void clearUnikery() {
        mUnikeryWeakReference.clear();
    }

    private void onFinish() {
        if (!mStop) {
            mConaco.finishConacoTask(this);
        }/* else  {
            // It is done by Conaco
        }*/
    }

    @UiThread
    void start() {
        if (mStop || mStart) {
            return;
        }

        mStart = true;

        Unikery<V> unikery = mUnikeryWeakReference.get();
        if (unikery != null && unikery.getTaskId() == mId) {
            if (mUseDiskCache) {
                mDiskLoadTask = new DiskLoadTask();
                mDiskLoadTask.executeOnExecutor(mDiskExecutor);
                return;
            } else if (mUseNetwork) {
                unikery.onMiss(Conaco.SOURCE_DISK);
                unikery.onRequest();
                mNetworkLoadTask = new NetworkLoadTask();
                mNetworkLoadTask.executeOnExecutor(mNetworkExecutor);
                return;
            } else {
                unikery.onMiss(Conaco.SOURCE_DISK);
                unikery.onMiss(Conaco.SOURCE_NETWORK);
                unikery.onFailure();
            }
        }

        onFinish();
    }

    @UiThread
    void stop() {
        if (mStop) {
            return;
        }

        mStop = true;

        // Stop jobs
        if (mDiskLoadTask != null) { // Getting from disk
            mDiskLoadTask.cancel(false);
        } else if (mNetworkLoadTask != null) { // Getting from network
            mNetworkLoadTask.cancel(false);
            if (mCall != null) {
                mCall.cancel();
                mCall = null;
            }
        }

        Unikery<V> unikery = mUnikeryWeakReference.get();
        if (unikery != null) {
            unikery.onCancel();
        }

        // Conaco handle the clean up
    }

    private boolean isNotNecessary(AsyncTask asyncTask) {
        Unikery<V> unikery = mUnikeryWeakReference.get();
        return mStop || asyncTask.isCancelled() || unikery == null || unikery.getTaskId() != mId;
    }

    private static void putFromDiskCacheToDataContainer(String key, ValueCache cache, DataContainer container) {
        SimpleDiskCache diskCache = cache.getDiskCache();
        if (diskCache != null) {
            InputStreamPipe pipe = diskCache.getInputStreamPipe(key);
            if (pipe != null) {
                try {
                    pipe.obtain();
                    container.save(pipe.open(), -1L, null, null);
                } catch (IOException e) {
                    Log.d(TAG, "Can't save value from disk cache to data container");
                    e.printStackTrace();
                    container.remove();
                } finally {
                    pipe.close();
                    pipe.release();
                }
            }
        }
    }

    private static void putFromDataContainerToDiskCache(String key, ValueCache cache, DataContainer container) {
        InputStreamPipe pipe = container.get();
        if (pipe != null) {
            try {
                pipe.obtain();
                cache.putRawToDisk(key, pipe.open());
            } catch (IOException e) {
                Log.w(TAG, "Can't save value from data container to disk cache", e);
                cache.removeFromDisk(key);
            } finally {
                pipe.close();
                pipe.release();
            }
        }
    }

    private class DiskLoadTask extends AsyncTask<Void, Void, V> {

        @Override
        protected V doInBackground(Void... params) {
            if (isNotNecessary(this)) {
                return null;
            } else {
                V value = null;

                // First check data container
                if (mDataContainer != null && mDataContainer.isEnabled()) {
                    InputStreamPipe isp = mDataContainer.get();
                    if (isp != null) {
                        value = mHelper.decode(isp);
                    }
                }

                // Then check disk cache
                if (mKey != null) {
                    if (value == null && mUseDiskCache) {
                        value = mCache.getFromDisk(mKey);
                        // Put back to data container
                        if (value != null && mDataContainer != null && mDataContainer.isEnabled()) {
                            putFromDiskCacheToDataContainer(mKey, mCache, mDataContainer);
                        }
                    }

                    if (value != null && mUseMemoryCache && mHelper.useMemoryCache(mKey, value)) {
                        // Put it to memory
                        mCache.putToMemory(mKey, value);
                    }
                }

                return value;
            }
        }

        @Override
        protected void onPostExecute(V value) {
            mDiskLoadTask = null;

            if (isCancelled() || mStop) {
                onCancelled(value);
            } else {
                Unikery<V> unikery = mUnikeryWeakReference.get();
                if (unikery != null && unikery.getTaskId() == mId) {
                    boolean getValue = false;
                    if ((value == null || !(getValue = unikery.onGetValue(value, Conaco.SOURCE_DISK))) && mUseNetwork) {
                        unikery.onMiss(Conaco.SOURCE_DISK);
                        unikery.onRequest();
                        mNetworkLoadTask = new NetworkLoadTask();
                        mNetworkLoadTask.executeOnExecutor(mNetworkExecutor);
                        return;
                    } else if (!getValue) {
                        unikery.onMiss(Conaco.SOURCE_DISK);
                        unikery.onFailure();
                    }
                }
                onFinish();
            }
        }

        @Override
        protected void onCancelled(V holder) {
            onFinish();
        }
    }

    private class NetworkLoadTask extends AsyncTask<Void, Long, V> implements ProgressNotifier {

        @Override
        public void notifyProgress(long singleReceivedSize, long receivedSize, long totalSize) {
            if (!isNotNecessary(this)) {
                publishProgress(singleReceivedSize, receivedSize, totalSize);
            }
        }

        private boolean putToDiskCache(InputStream is, long length) {
            SimpleDiskCache diskCache = mCache.getDiskCache();
            if (diskCache == null) {
                return false;
            }

            OutputStreamPipe pipe = diskCache.getOutputStreamPipe(mKey);
            try {
                pipe.obtain();
                OutputStream os = pipe.open();

                final byte buffer[] = new byte[1024 * 4];
                long receivedSize = 0;
                int bytesRead;

                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                    receivedSize += bytesRead;
                    notifyProgress((long) bytesRead, receivedSize, length);
                }

                return true;
            } catch (IOException e) {
                return false;
            } finally {
                pipe.close();
                pipe.release();
            }
        }

        private boolean putToDataContainer(InputStream is, ResponseBody body) {
            // Get media type
            String mediaType;
            MediaType mt = body.contentType();
            if (mt != null) {
                mediaType = mt.type() + '/' + mt.subtype();
            } else {
                mediaType = null;
            }
            return mDataContainer.save(is, body.contentLength(), mediaType, this);
        }

        @Override
        protected V doInBackground(Void... params) {
            if (isNotNecessary(this)) {
                return null;
            }

            V value;
            InputStream is = null;
            try {
                // Load it from internet
                Request request = new Request.Builder().url(mUrl).build();
                mCall = mOkHttpClient.newCall(request);

                Response response = mCall.execute();
                ResponseBody body = response.body();
                is = body.byteStream();

                if (isNotNecessary(this)) {
                    return null;
                }

                if ((mDataContainer == null || !mDataContainer.isEnabled()) && mKey != null) {
                    if (putToDiskCache(is, body.contentLength())) {
                        // Get object from disk cache
                        value = mCache.getFromDisk(mKey);
                        if (value == null) {
                            // Maybe bad download, remove it from disk cache
                            mCache.removeFromDisk(mKey);
                        } else if (mUseMemoryCache && mHelper.useMemoryCache(mKey, value)) {
                            // Put it to memory
                            mCache.putToMemory(mKey, value);
                        }
                        return value;
                    } else {
                        // Maybe bad download, remove it from disk cache
                        mCache.removeFromDisk(mKey);
                        return null;
                    }
                } else if (mDataContainer != null && mDataContainer.isEnabled()) {
                    // Check url Moved
                    HttpUrl requestHttpUrl = request.url();
                    HttpUrl responseHttpUrl = response.request().url();
                    if (!responseHttpUrl.equals(requestHttpUrl)) {
                        mDataContainer.onUrlMoved(mUrl, responseHttpUrl.url().toString());
                    }

                    // Put to data container
                    if (!putToDataContainer(is, body)) {
                        return null;
                    }

                    // Get value from data container
                    InputStreamPipe isp = mDataContainer.get();
                    if (isp == null) {
                        return null;
                    }
                    value = mHelper.decode(isp);
                    if (value == null) {
                        mDataContainer.remove();
                    } else if (mKey != null) {
                        // Put to disk cache
                        putFromDataContainerToDiskCache(mKey, mCache, mDataContainer);

                        if (mUseMemoryCache && mHelper.useMemoryCache(mKey, value)) {
                            // Put it to memory
                            mCache.putToMemory(mKey, value);
                        }
                    }
                    return value;
                } else {
                    return null;
                }
            } catch (Exception e) {
                return null;
            } finally {
                mCall = null;
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        @Override
        protected void onPostExecute(V holder) {
            mNetworkLoadTask = null;

            if (isCancelled() || mStop) {
                onCancelled(holder);
            } else {
                Unikery<V> unikery = mUnikeryWeakReference.get();
                if (unikery != null && unikery.getTaskId() == mId) {
                    if (holder == null || !unikery.onGetValue(holder, Conaco.SOURCE_NETWORK)) {
                        unikery.onFailure();
                    }
                }
                onFinish();
            }
        }

        @Override
        protected void onCancelled(V value) {
            onFinish();
        }

        @Override
        protected void onProgressUpdate(Long... values) {
            Unikery unikery = mUnikeryWeakReference.get();
            if (!mStop && !isCancelled() && unikery != null && unikery.getTaskId() == mId) {
                unikery.onProgress(values[0], values[1], values[2]);
            }
        }
    }

    public static class Builder<T> {

        private int mId;
        private Unikery<T> mUnikery;
        private String mKey;
        private String mUrl;
        private DataContainer mDataContainer;
        private boolean mUseMemoryCache = true;
        private boolean mUseDiskCache = true;
        private boolean mUseNetwork = true;
        private ValueHelper<T> mHelper;
        private ValueCache<T> mCache;
        private OkHttpClient mOkHttpClient;
        private Executor mDiskExecutor;
        private Executor mNetworkExecutor;
        private Conaco<T> mConaco;

        public Builder<T> setId(int id) {
            mId = id;
            return this;
        }

        public Builder<T> setUnikery(Unikery<T> unikery) {
            mUnikery = unikery;
            return this;
        }

        public Unikery<T> getUnikery() {
            return mUnikery;
        }

        public Builder<T> setKey(String key) {
            mKey = key;
            return this;
        }

        public String getKey() {
            return mKey;
        }

        public Builder<T> setUrl(String url) {
            mUrl = url;
            return this;
        }

        public String getUrl() {
            return mUrl;
        }

        public Builder<T> setDataContainer(DataContainer dataContainer) {
            mDataContainer = dataContainer;
            return this;
        }

        public Builder<T> setUseMemoryCache(boolean useMemoryCache) {
            mUseMemoryCache = useMemoryCache;
            return this;
        }

        boolean isUseMemoryCache() {
            return mUseMemoryCache;
        }

        public Builder<T> setUseDiskCache(boolean useDiskCache) {
            mUseDiskCache = useDiskCache;
            return this;
        }

        boolean isUseDiskCache() {
            return mUseDiskCache;
        }

        public Builder<T> setUseNetwork(boolean useNetwork) {
            mUseNetwork = useNetwork;
            return this;
        }

        boolean isUseNetwork() {
            return mUseNetwork;
        }

        Builder<T> setHelper(ValueHelper<T> helper) {
            mHelper = helper;
            return this;
        }

        ValueHelper<T> getHelper() {
            return mHelper;
        }

        Builder<T> setCache(ValueCache<T> cache) {
            mCache = cache;
            return this;
        }

        Builder<T> setOkHttpClient(OkHttpClient okHttpClient) {
            mOkHttpClient = okHttpClient;
            return this;
        }

        Builder<T> setDiskExecutor(Executor diskExecutor) {
            mDiskExecutor = diskExecutor;
            return this;
        }

        Builder<T> setNetworkExecutor(Executor networkExecutor) {
            mNetworkExecutor = networkExecutor;
            return this;
        }

        Builder<T> setConaco(Conaco<T> conaco) {
            mConaco = conaco;
            return this;
        }

        public void isValid() {
            if (mUnikery == null) {
                throw new IllegalStateException("Must set unikery");
            }
            if (mKey == null && mUrl == null && mDataContainer == null) {
                throw new IllegalStateException("At least one of mKey and mUrl and mDataContainer have to not be null");
            }
        }

        public ConacoTask<T> build() {
            return new ConacoTask<>(this);
        }
    }
}
