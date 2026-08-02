/*
 * Copyright 2026 Hippo Seven
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

import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.SparseArray;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.spider.SpiderDen;
import com.hippo.anotherviewer.spider.SpiderInfo;
import com.hippo.anotherviewer.webui.WebUiApiClient;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.lib.image.Image;
import com.hippo.lib.yorozuya.IOUtils;
import com.hippo.streampipe.InputStreamPipe;
import com.hippo.streampipe.OutputStreamPipe;
import com.hippo.unifile.UniFile;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import okhttp3.Response;

/**
 * Reads a gallery from the WebUI companion server instead of EH directly:
 * page metadata comes from {@code GET /api/v1/gallery/{gid}} and each page is
 * streamed from {@code GET /api/v1/image/{gid}/{page}}. Downloaded pages are
 * written into the shared {@link SpiderDen} cache so previously read pages are
 * served locally on the next open (the "SpiderDen read cache" of roadmap §2.4).
 *
 * <p>The reader stays identical to {@link SiteGalleryProvider}: the provider
 * surface (size/request/decode/notify) is unchanged, only the byte source is
 * the LAN server. The phone never talks to EH when this provider is active.
 */
public class WebUiGalleryProvider extends GalleryProvider2 {

    private static final int WORKER_THREADS = 2;

    private final GalleryInfo mGalleryInfo;
    private final WebUiConfig mConfig;
    private final SpiderDen mSpiderDen;
    private volatile ExecutorService mExecutor;

    private volatile int mPages = STATE_WAIT;
    private volatile String mError;

    public WebUiGalleryProvider(@NonNull GalleryInfo galleryInfo,
            @NonNull WebUiConfig config) {
        mGalleryInfo = galleryInfo;
        mConfig = config;
        mSpiderDen = new SpiderDen(galleryInfo);
        mExecutor = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    @Override
    public void start() {
        super.start();
        if (mExecutor.isShutdown()) {
            // A previous stop() shut the pool down; restart must get a fresh one.
            mExecutor = Executors.newFixedThreadPool(WORKER_THREADS);
        }
        try {
            mExecutor.execute(this::loadPages);
        } catch (RejectedExecutionException e) {
            // Raced with stop(); the reader is already detached.
        }
    }

    @Override
    public void stop() {
        super.stop();
        // Interrupts in-flight downloads; their notifies are no-ops once the
        // listener is detached by GalleryActivity.
        mExecutor.shutdownNow();
    }

    /** Fetches the page count from the server index; async so the UI shows WAIT first. */
    private void loadPages() {
        try {
            int pages = WebUiApiClient.getGalleryPages(mConfig, mGalleryInfo.gid);
            if (pages > 0) {
                mPages = pages;
            } else {
                mPages = STATE_ERROR;
                mError = "Server returned no page count";
            }
        } catch (IOException e) {
            mPages = STATE_ERROR;
            mError = e.getMessage() != null ? e.getMessage() : "Failed to load gallery from server";
        }
        notifyDataChanged();
    }

    @Override
    public int size() {
        return mPages;
    }

    @Override
    public String getError() {
        return mError != null ? mError : "WebUI read error";
    }

    @Override
    protected void onRequest(int index) {
        if (!mExecutor.isShutdown()) {
            fetchPage(index, false);
        }
    }

    @Override
    protected void onForceRequest(int index) {
        if (!mExecutor.isShutdown()) {
            fetchPage(index, true);
        }
    }

    @Override
    protected void onCancelRequest(int index) {
        // Tasks already submitted cannot be cancelled mid-flight; the provider
        // stops accepting new work in stop(). Same semantics as SpiderQueen.
    }

    private void fetchPage(int index, boolean force) {
        if (mExecutor.isShutdown()) {
            return;
        }
        try {
            mExecutor.execute(() -> {
                if (!force) {
                    InputStreamPipe pipe = mSpiderDen.openInputStreamPipe(index);
                    if (pipe != null) {
                        decodeAndNotify(index, pipe);
                        return;
                    }
                }

                // Cache miss (or forced): pull from the server, cache, then decode.
                try (Response response = WebUiApiClient.fetchImage(mConfig, mGalleryInfo.gid, index)) {
                    if (!response.isSuccessful()) {
                        notifyPageFailed(index, "HTTP " + response.code());
                        return;
                    }
                    String extension = extensionFromContentType(response.header("Content-Type"));
                    OutputStreamPipe out = mSpiderDen.openOutputStreamPipe(index, extension);
                    if (out == null) {
                        notifyPageFailed(index, "Local cache unavailable");
                        return;
                    }
                    try {
                        out.obtain();
                        OutputStream os = out.open();
                        try {
                            IOUtils.copy(response.body().byteStream(), os);
                        } finally {
                            out.close();
                        }
                    } finally {
                        out.release();
                    }

                    InputStreamPipe pipe = mSpiderDen.openInputStreamPipe(index);
                    if (pipe != null) {
                        decodeAndNotify(index, pipe);
                    } else {
                        notifyPageFailed(index, "Cache write failed");
                    }
                } catch (IOException e) {
                    notifyPageFailed(index, e.getMessage() != null ? e.getMessage() : "Network error");
                }
            });
        } catch (RejectedExecutionException e) {
            // stop() raced with this request; drop it, the reader is detached.
        }
    }

    private void decodeAndNotify(int index, InputStreamPipe pipe) {
        Image image = null;
        String error = null;
        try {
            pipe.obtain();
            image = Image.decode((FileInputStream) pipe.open(), false);
        } catch (IOException | OutOfMemoryError e) {
            error = "Decode failed";
        } finally {
            pipe.close();
            pipe.release();
        }
        if (image != null) {
            notifyPageSucceed(index, image);
        } else {
            notifyPageFailed(index, error != null ? error : "Decode failed");
        }
    }

    @Nullable
    private static String extensionFromContentType(@Nullable String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String mime = semicolon >= 0 ? contentType.substring(0, semicolon).trim() : contentType.trim();
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (extension != null) {
            return "." + extension;
        }
        return null;
    }

    @NonNull
    @Override
    public String getImageFilename(int index) {
        return String.format(Locale.US, "%d-%s-%08d", mGalleryInfo.gid, mGalleryInfo.token, index + 1);
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
            if (mPages <= 0) {
                return;
            }
            spiderInfo = new SpiderInfo();
            spiderInfo.gid = mGalleryInfo.gid;
            spiderInfo.token = mGalleryInfo.token;
            spiderInfo.pages = mPages;
            spiderInfo.pTokenMap = new SparseArray<>(0);
        }
        spiderInfo.startPage = page;
        writeSpiderInfo(spiderInfo);
    }

    @Nullable
    private SpiderInfo readSpiderInfo() {
        InputStreamPipe pipe = SiteApplication.getSpiderInfoCache(SiteApplication.getInstance())
                .getInputStreamPipe(Long.toString(mGalleryInfo.gid));
        if (pipe == null) {
            return null;
        }
        try {
            pipe.obtain();
            SpiderInfo spiderInfo = SpiderInfo.read(pipe.open());
            if (spiderInfo == null
                    || spiderInfo.gid != mGalleryInfo.gid
                    || !TextUtils.equals(spiderInfo.token, mGalleryInfo.token)) {
                return null;
            }
            return spiderInfo;
        } catch (IOException e) {
            return null;
        } finally {
            pipe.close();
            pipe.release();
        }
    }

    private void writeSpiderInfo(@NonNull SpiderInfo spiderInfo) {
        OutputStreamPipe pipe = SiteApplication.getSpiderInfoCache(SiteApplication.getInstance())
                .getOutputStreamPipe(Long.toString(mGalleryInfo.gid));
        try {
            pipe.obtain();
            spiderInfo.write(pipe.open());
        } catch (IOException e) {
            // Ignore
        } finally {
            pipe.close();
            pipe.release();
        }
    }

    @Override
    public boolean save(int index, @NonNull UniFile file) {
        InputStreamPipe pipe = mSpiderDen.openInputStreamPipe(index);
        if (pipe == null) {
            // Remote pages exist locally only after being read; nothing to save yet.
            return false;
        }
        OutputStream os = null;
        try {
            pipe.obtain();
            os = file.openOutputStream();
            IOUtils.copy(pipe.open(), os);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            pipe.close();
            pipe.release();
            IOUtils.closeQuietly(os);
        }
    }

    @Nullable
    @Override
    public UniFile save(int index, @NonNull UniFile dir, @NonNull String filename) {
        InputStreamPipe pipe = mSpiderDen.openInputStreamPipe(index);
        if (pipe == null) {
            return null;
        }
        OutputStream os = null;
        try {
            pipe.obtain();

            // Get dst file
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(pipe.open(), null, options);
            pipe.close();
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(options.outMimeType);
            UniFile dst = dir.subFile(null != extension ? filename + "." + extension : filename);
            if (null == dst) {
                return null;
            }

            // Copy
            os = dst.openOutputStream();
            IOUtils.copy(pipe.open(), os);
            return dst;
        } catch (IOException e) {
            return null;
        } finally {
            pipe.close();
            pipe.release();
            IOUtils.closeQuietly(os);
        }
    }
}
