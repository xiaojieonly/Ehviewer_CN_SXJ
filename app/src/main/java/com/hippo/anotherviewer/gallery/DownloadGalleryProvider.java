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

package com.hippo.anotherviewer.gallery;

import android.content.Context;
import android.os.Process;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.GetText;
import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.download.DownloadManager;
import com.hippo.anotherviewer.spider.SpiderDen;
import com.hippo.anotherviewer.spider.SpiderInfo;
import com.hippo.lib.glgallery.GalleryPageView;
import com.hippo.lib.image.Image;
import com.hippo.lib.yorozuya.FileUtils;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.lib.yorozuya.thread.PriorityThread;
import com.hippo.unifile.UniFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reads a finished download straight from its folder on disk: page metadata
 * comes from the {@code .anotherviewer} spider info and each page image is
 * decoded from the numbered files inside the folder.
 */
public class DownloadGalleryProvider extends GalleryProvider2 implements Runnable {

    private static final String TAG = DownloadGalleryProvider.class.getSimpleName();
    private static final AtomicInteger sIdGenerator = new AtomicInteger();

    @Nullable
    private final Context mContext;
    private final GalleryInfo mGalleryInfo;
    private final UniFile mDownloadDir;
    private final Stack<Integer> mRequests = new Stack<>();
    private final AtomicInteger mDecodingIndex = new AtomicInteger(GalleryPageView.INVALID_INDEX);

    @Nullable
    private volatile SpiderInfo mSpiderInfo;
    @Nullable
    private Thread mBgThread;
    private volatile int mSize = STATE_WAIT;
    private String mError;

    public DownloadGalleryProvider(@NonNull Context context, @NonNull GalleryInfo galleryInfo,
            @NonNull UniFile downloadDir) {
        mContext = context;
        mGalleryInfo = galleryInfo;
        mDownloadDir = downloadDir;
    }

    @Override
    public void start() {
        super.start();

        mSpiderInfo = readSpiderInfo();
        if (mSpiderInfo == null) {
            mSize = STATE_ERROR;
            mError = GetText.getString(R.string.error_not_folder_path);
            notifyDataChanged();
            return;
        }

        mSize = mSpiderInfo.pages;
        notifyDataChanged();

        mBgThread = new PriorityThread(this, TAG + '-' + sIdGenerator.incrementAndGet(),
                Process.THREAD_PRIORITY_BACKGROUND);
        mBgThread.start();
    }

    @Override
    public void stop() {
        super.stop();

        if (mBgThread != null) {
            mBgThread.interrupt();
            mBgThread = null;
        }
    }

    @Override
    public int size() {
        int size = mSize;
        if (size != STATE_WAIT) {
            return size;
        }
        // start() has not resolved the spider info yet; fall back to the
        // info's own page count.
        if (mGalleryInfo.pages > 0) {
            return mGalleryInfo.pages;
        }
        return STATE_ERROR;
    }

    @Override
    public String getError() {
        return mError != null ? mError : "Download read error";
    }

    @Override
    public int getStartPage() {
        SpiderInfo spiderInfo = readSpiderInfo();
        return spiderInfo != null ? spiderInfo.startPage : 0;
    }

    @Override
    public void putStartPage(int page) {
        SpiderInfo spiderInfo = readSpiderInfo();
        if (spiderInfo == null) {
            // No info yet; only create one once the page count is known, so
            // the entry stays valid for SpiderInfo.read().
            if (mGalleryInfo.pages <= 0) {
                return;
            }
            spiderInfo = new SpiderInfo();
            spiderInfo.gid = mGalleryInfo.gid;
            spiderInfo.token = mGalleryInfo.token;
            spiderInfo.pages = mGalleryInfo.pages;
            spiderInfo.pTokenMap = new SparseArray<>(0);
        }
        spiderInfo.startPage = page;
        writeSpiderInfo(spiderInfo);
    }

    @NonNull
    @Override
    public String getImageFilename(int index) {
        return String.format(Locale.US, "%d-%s-%08d", mGalleryInfo.gid, mGalleryInfo.token, index + 1);
    }

    @Override
    public boolean save(int index, @NonNull UniFile file) {
        UniFile src = SpiderDen.findImageFile(mDownloadDir, index);
        if (src == null) {
            return false;
        }

        InputStream is = null;
        OutputStream os = null;
        try {
            is = src.openInputStream();
            os = file.openOutputStream();
            IOUtils.copy(is, os);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    @Nullable
    @Override
    public UniFile save(int index, @NonNull UniFile dir, @NonNull String filename) {
        UniFile src = SpiderDen.findImageFile(mDownloadDir, index);
        if (src == null) {
            return null;
        }

        String extension = FileUtils.getExtensionFromFilename(src.getName());
        UniFile dst = dir.subFile(null != extension ? filename + "." + extension : filename);
        if (null == dst) {
            return null;
        }

        InputStream is = null;
        OutputStream os = null;
        try {
            is = src.openInputStream();
            os = dst.openOutputStream();
            IOUtils.copy(is, os);
            return dst;
        } catch (IOException e) {
            return null;
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    @Override
    protected void onRequest(int index) {
        synchronized (mRequests) {
            if (!mRequests.contains(index) && index != mDecodingIndex.get()) {
                mRequests.add(index);
                mRequests.notify();
            }
        }
        notifyPageWait(index);
    }

    @Override
    protected void onForceRequest(int index) {
        onRequest(index);
    }

    @Override
    public void onCancelRequest(int index) {
        synchronized (mRequests) {
            mRequests.remove(Integer.valueOf(index));
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            int index;
            synchronized (mRequests) {
                if (mRequests.isEmpty()) {
                    try {
                        mRequests.wait();
                    } catch (InterruptedException e) {
                        // Interrupted
                        break;
                    }
                    continue;
                }
                index = mRequests.pop();
                mDecodingIndex.lazySet(index);
            }

            // Check index valid
            if (index < 0 || index >= mSize) {
                mDecodingIndex.lazySet(GalleryPageView.INVALID_INDEX);
                notifyPageFailed(index, GetText.getString(R.string.error_out_of_range));
                continue;
            }

            UniFile file = SpiderDen.findImageFile(mDownloadDir, index);
            if (file == null) {
                mDecodingIndex.lazySet(GalleryPageView.INVALID_INDEX);
                notifyPageFailed(index, GetText.getString(R.string.error_out_of_range));
                continue;
            }

            InputStream is = null;
            try {
                is = file.openInputStream();
                Image image = Image.decode((FileInputStream) is, false);
                mDecodingIndex.lazySet(GalleryPageView.INVALID_INDEX);
                if (image != null) {
                    notifyPageSucceed(index, image);
                } else {
                    notifyPageFailed(index, GetText.getString(R.string.error_decoding_failed));
                }
            } catch (IOException e) {
                mDecodingIndex.lazySet(GalleryPageView.INVALID_INDEX);
                notifyPageFailed(index, GetText.getString(R.string.error_reading_failed));
            } finally {
                IOUtils.closeQuietly(is);
            }
            mDecodingIndex.lazySet(GalleryPageView.INVALID_INDEX);
        }

        Log.i(TAG, "DownloadGalleryProvider end");
    }

    @Nullable
    private SpiderInfo readSpiderInfo() {
        UniFile file = mDownloadDir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
        SpiderInfo spiderInfo = SpiderInfo.read(file);
        if (spiderInfo == null || spiderInfo.gid != mGalleryInfo.gid
                || !TextUtils.equals(spiderInfo.token, mGalleryInfo.token)) {
            return null;
        }
        return spiderInfo;
    }

    private void writeSpiderInfo(@NonNull SpiderInfo spiderInfo) {
        UniFile file = mDownloadDir.findFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
        if (file == null) {
            file = mDownloadDir.createFile(DownloadManager.DOWNLOAD_INFO_FILENAME);
        }
        if (file == null) {
            return;
        }
        try {
            spiderInfo.write(file.openOutputStream());
        } catch (IOException e) {
            // Ignore
        }
    }
}
