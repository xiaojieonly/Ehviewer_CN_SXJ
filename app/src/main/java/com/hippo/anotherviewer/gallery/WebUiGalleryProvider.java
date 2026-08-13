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

import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.SparseArray;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.spider.SpiderDen;
import com.hippo.anotherviewer.spider.SpiderInfo;
import com.hippo.anotherviewer.webui.WebUiApiClient;
import com.hippo.anotherviewer.webui.WebUiAutoSyncScheduler;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.lib.glview.view.GLRoot;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

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
 *
 * <p>Server-outage resilience (same semantics as
 * {@link com.hippo.anotherviewer.webui.WebUiTier2ProxyInterceptor}): when the
 * paired server is unreachable (connection failure) or cannot serve the
 * gallery/page (HTTP error, empty page count), reading hands over to an inner
 * {@link SiteGalleryProvider} and continues directly against EH — the shared
 * SpiderDen cache is consulted first in both modes, so pages already read stay
 * local. The hand-over lasts a short degrade window, then the server is tried
 * again; a successful response brings remote reading back and triggers a sync.
 */
public class WebUiGalleryProvider extends GalleryProvider2 {

    private static final int WORKER_THREADS = 2;

    /** How long to keep reading direct after the paired server was seen
     *  unreachable, before the server is tried again. Mirrors the Tier-2
     *  browsing proxy so both surfaces degrade in the same rhythm. */
    private static final long DEGRADE_WINDOW_MS = 60_000L;

    private final Context mContext;
    private final GalleryInfo mGalleryInfo;
    private final WebUiConfig mConfig;
    private final SpiderDen mSpiderDen;
    private volatile ExecutorService mExecutor;

    private volatile int mPages = STATE_WAIT;
    private volatile String mError;

    /** Server considered unreachable until this timestamp (ms since epoch). */
    private final AtomicLong mDegradedUntil = new AtomicLong(0L);

    /** Whether remote reading is currently degraded to direct EH reading. */
    private final AtomicBoolean mDegraded = new AtomicBoolean(false);

    /** The direct-reading provider taking over during a degrade window. */
    volatile SiteGalleryProvider mFallback;

    /** Page that failed against the server and must be retried via the fallback. */
    private volatile int mPendingRetry = -1;

    /** Listener/GLRoot forwarded to the fallback so its notifications reach the reader. */
    private volatile Listener mListener;
    private volatile GLRoot mGLRoot;

    private volatile boolean mStarted = false;

    public WebUiGalleryProvider(@NonNull Context context,
            @NonNull GalleryInfo galleryInfo, @NonNull WebUiConfig config) {
        mContext = context;
        mGalleryInfo = galleryInfo;
        mConfig = config;
        mSpiderDen = new SpiderDen(galleryInfo);
        mExecutor = Executors.newFixedThreadPool(WORKER_THREADS);
    }

    @Override
    public void setListener(@Nullable Listener listener) {
        super.setListener(listener);
        mListener = listener;
        SiteGalleryProvider fallback = mFallback;
        if (fallback != null) {
            fallback.setListener(listener);
        }
    }

    @Override
    public void setGLRoot(GLRoot glRoot) {
        super.setGLRoot(glRoot);
        mGLRoot = glRoot;
        SiteGalleryProvider fallback = mFallback;
        if (fallback != null) {
            fallback.setGLRoot(glRoot);
        }
    }

    @Override
    public void start() {
        super.start();
        mStarted = true;
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
        mStarted = false;
        // Interrupts in-flight downloads; their notifies are no-ops once the
        // listener is detached by GalleryActivity.
        mExecutor.shutdownNow();
        SiteGalleryProvider fallback = mFallback;
        mFallback = null;
        if (fallback != null) {
            fallback.stop();
        }
    }

    /** Fetches the page count from the server index; async so the UI shows WAIT first. */
    private void loadPages() {
        try {
            int pages = WebUiApiClient.getGalleryPages(mConfig, mGalleryInfo.gid);
            if (pages > 0) {
                mPages = pages;
                notifyDataChanged();
            } else {
                // The server is up but cannot serve this gallery (e.g. it never
                // saw it): hand over to direct EH reading instead of erroring.
                enterFallbackMode();
            }
        } catch (IOException e) {
            // Server unreachable: hand over to direct EH reading. The fallback
            // reports its own size/error when ready (notifyDataChanged driven).
            enterFallbackMode();
        }
    }

    @Override
    public int size() {
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                return fallback.size();
            }
        }
        return mPages;
    }

    @Override
    public String getError() {
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                return fallback.getError();
            }
        }
        return mError != null ? mError : "WebUI read error";
    }

    @Override
    protected void onRequest(int index) {
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                fallback.request(index);
                return;
            }
            // Fallback not created yet (window expired race): fall through and
            // retry the server; its failure re-arms the degrade window.
        }
        if (!mExecutor.isShutdown()) {
            fetchPage(index, false);
        }
    }

    @Override
    protected void onForceRequest(int index) {
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                fallback.forceRequest(index);
                return;
            }
        }
        if (!mExecutor.isShutdown()) {
            fetchPage(index, true);
        }
    }

    @Override
    protected void onCancelRequest(int index) {
        // Tasks already submitted cannot be cancelled mid-flight; the provider
        // stops accepting new work in stop(). Same semantics as SpiderQueen.
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                fallback.cancelRequest(index);
            }
        }
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
                        fallbackPage(index);
                        return;
                    }
                    clearDegraded();
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
                    fallbackPage(index);
                }
            });
        } catch (RejectedExecutionException e) {
            // stop() raced with this request; drop it, the reader is detached.
        }
    }

    /**
     * A page fetch failed against the server: enter the degrade window and
     * retry the same page through the direct-EH fallback so the reader never
     * sees a failure tile, only a (slightly longer) wait.
     */
    void fallbackPage(int index) {
        mPendingRetry = index;
        enterFallbackMode();
    }

    /**
     * Marks the server unreachable until the degrade window expires, then
     * creates and starts the direct-reading fallback. The user is told exactly
     * once per outage (state-flip guarded).
     */
    void enterFallbackMode() {
        mDegradedUntil.set(System.currentTimeMillis() + DEGRADE_WINDOW_MS);
        if (mDegraded.compareAndSet(false, true)) {
            notifyTransition(R.string.webui_proxy_degraded);
        }

        SiteGalleryProvider fallback = mFallback;
        if (fallback != null) {
            // Already degraded: re-arm the window and dispatch the pending page.
            dispatchPendingRetry(fallback);
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            startFallbackNow();
        } else {
            // SiteGalleryProvider.start() requires the main thread.
            new Handler(Looper.getMainLooper()).post(this::startFallbackNow);
        }
    }

    /**
     * Whether the server is currently considered unreachable. Exposed so the
     * tests can drive the window without touching the network.
     */
    boolean isDegraded() {
        return mDegradedUntil.get() > System.currentTimeMillis();
    }

    /**
     * Creates and starts the direct-reading fallback (main thread only), wires
     * it to our listener/GLRoot so its notifications reach the reader, and
     * dispatches any page that failed against the server.
     */
    private void startFallbackNow() {
        if (!mStarted) {
            return;
        }
        SiteGalleryProvider fallback = mFallback;
        if (fallback != null) {
            return;
        }
        fallback = createFallbackProvider();
        mFallback = fallback;
        if (mListener != null) {
            fallback.setListener(mListener);
        }
        if (mGLRoot != null) {
            fallback.setGLRoot(mGLRoot);
        }
        fallback.start();
        dispatchPendingRetry(fallback);
    }

    /** Visible for testing: the concrete direct-reading provider to delegate to. */
    SiteGalleryProvider createFallbackProvider() {
        return new SiteGalleryProvider(mContext, mGalleryInfo);
    }

    private void dispatchPendingRetry(@NonNull SiteGalleryProvider fallback) {
        int index = mPendingRetry;
        mPendingRetry = -1;
        if (index >= 0) {
            final int retryIndex = index;
            new Handler(Looper.getMainLooper()).post(() -> fallback.request(retryIndex));
        }
    }

    /**
     * A remote fetch succeeded: leave degraded mode, tell the user, trigger a
     * sync (local changes accumulated while reading direct) and release the
     * fallback so future pages stream from the server again.
     */
    void clearDegraded() {
        if (mDegraded.compareAndSet(true, false)) {
            mDegradedUntil.set(0L);
            notifyTransition(R.string.webui_proxy_recovered);
            WebUiAutoSyncScheduler.triggerOnce(SiteApplication.getInstance());
        }
        SiteGalleryProvider fallback = mFallback;
        mFallback = null;
        if (fallback != null) {
            // fallback.stop() requires the main thread.
            new Handler(Looper.getMainLooper()).post(fallback::stop);
        }
    }

    /** Shows the transition toast on the main thread (fire-and-forget). */
    private void notifyTransition(final int resId) {
        final Context app = SiteApplication.getInstance();
        if (app == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, app.getString(resId), Toast.LENGTH_SHORT).show());
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
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                return fallback.getStartPage();
            }
        }
        SpiderInfo spiderInfo = readSpiderInfo();
        return spiderInfo != null ? spiderInfo.startPage : 0;
    }

    @Override
    public void putStartPage(int page) {
        if (isDegraded()) {
            SiteGalleryProvider fallback = mFallback;
            if (fallback != null) {
                fallback.putStartPage(page);
                return;
            }
        }
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
