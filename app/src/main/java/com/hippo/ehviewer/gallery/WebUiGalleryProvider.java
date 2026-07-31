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

package com.hippo.ehviewer.gallery;

import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.ehviewer.client.data.GalleryInfo;
import com.hippo.ehviewer.spider.SpiderDen;
import com.hippo.ehviewer.webui.WebUiApiClient;
import com.hippo.ehviewer.webui.WebUiConfig;
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

import okhttp3.Response;

/**
 * Reads a gallery from the WebUI companion server instead of EH directly:
 * page metadata comes from {@code GET /api/v1/gallery/{gid}} and each page is
 * streamed from {@code GET /api/v1/image/{gid}/{page}}. Downloaded pages are
 * written into the shared {@link SpiderDen} cache so previously read pages are
 * served locally on the next open (the "SpiderDen read cache" of roadmap §2.4).
 *
 * <p>The reader stays identical to {@link EhGalleryProvider}: the provider
 * surface (size/request/decode/notify) is unchanged, only the byte source is
 * the LAN server. The phone never talks to EH when this provider is active.
 */
public class WebUiGalleryProvider extends GalleryProvider2 {

    private static final int WORKER_THREADS = 2;

    private final GalleryInfo mGalleryInfo;
    private final WebUiConfig mConfig;
    private final SpiderDen mSpiderDen;
    private final ExecutorService mExecutor;

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
        mExecutor.execute(this::loadPages);
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
        fetchPage(index, false);
    }

    @Override
    protected void onForceRequest(int index) {
        fetchPage(index, true);
    }

    @Override
    protected void onCancelRequest(int index) {
        // Tasks already submitted cannot be cancelled mid-flight; the provider
        // stops accepting new work in stop(). Same semantics as SpiderQueen.
    }

    private void fetchPage(int index, boolean force) {
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
    public boolean save(int index, @NonNull UniFile file) {
        // Remote reading keeps pages in the SpiderDen cache only; saving to a
        // picked location is out of scope for the remote provider.
        return false;
    }

    @Nullable
    @Override
    public UniFile save(int index, @NonNull UniFile dir, @NonNull String filename) {
        return null;
    }
}
