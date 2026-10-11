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

package com.hippo.anotherviewer.client;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.anotherviewer.webui.WebUiSettings;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Server-cache fallback for gallery page fetches (metadata / detail pages):
 * when the direct Gallery Site fetch fails at the connection level — OkHttp's
 * "exhausted all routes", connect/read timeouts, unknown host, ... i.e. the
 * origin (or the route to it) is unreachable — the very same page URL is
 * fetched a second time through the paired WebUI server's tier-2 page cache,
 * {@code GET {base}/api/v1/tier2/page?url=<encoded page url>} with
 * {@code Authorization: Bearer <device token>}. The cached raw page body is
 * handed back wrapped in a synthesized 200 {@link Response}, so the existing
 * parsers in {@link SiteEngine} consume it unchanged. Direct fetching stays
 * the primary path: the fallback runs only after the direct call has already
 * failed, and only when a WebUI server is paired with a device token — with
 * no server configured the behaviour is bit-for-bit identical to before.
 *
 * <p>Failure mapping (per the tier-2 page contract): 404 → the server has no
 * cache and the origin is down for it too; 401/403 → the device token was
 * rejected; any other non-2xx → server error. In all cases the original
 * direct failure remains the cause, and the surfaced message names both the
 * direct outcome and the fallback outcome, so the error dialog tells the
 * whole story instead of hiding one of the two failures.
 *
 * <p>Only connection-level failures trigger the fallback. Parse errors, Sad
 * Panda, kokomade and HTTP status errors mean the site answered — the app's
 * normal error handling for those must not be bypassed with a stale copy.
 *
 * <p>The server call uses a dedicated bare {@link OkHttpClient} (same idiom
 * as {@code WebUiApiClient}): the shared client's EH DNS hosts, cookie jar and
 * proxy selector are for the Gallery Site and must not apply to a LAN WebUI
 * server. Its timeouts are more generous than the direct fetch's 10 s — on a
 * cache miss the server fetches the page from the origin itself, which can
 * legitimately take longer than a direct page load.
 */
public final class Tier2PageFetcher {

    private static final String TAG = Tier2PageFetcher.class.getSimpleName();

    private static final MediaType MEDIA_TYPE_HTML = MediaType.get("text/html; charset=utf-8");

    /** Contract paths, relative to the paired server base URL. */
    private static final String PATH_TIER2_PAGE = "api/v1/tier2/page";

    private static volatile OkHttpClient sClient;

    private Tier2PageFetcher() {
    }

    /**
     * Whether the fallback can be attempted at all: a WebUI server is paired
     * and a device token is available (auth-on servers reject token-less
     * requests, and a token-less pairing is treated as not usable here).
     */
    public static boolean isEnabled() {
        WebUiConfig config = loadConfig();
        return config != null && !TextUtils.isEmpty(config.getToken());
    }

    /**
     * Runs the direct page call; on a connection-level failure retries the
     * same page through the paired server's tier-2 page cache.
     *
     * @param pageUrl    the original Gallery Site page URL (list page or
     *                   gallery detail/preview page) that failed directly
     * @param directCall the already-built direct call, registered with the
     *                   running {@link SiteClient.Task} for cancellation
     * @return the response to parse: the direct response on success, or a
     *         synthesized 200 response carrying the server's cached page body
     * @throws IOException the direct failure if cancellation or no server is
     *                     configured (behaviour unchanged), otherwise a
     *                     failure naming both the direct and the fallback
     *                     outcome when the cache cannot satisfy the request
     */
    @NonNull
    public static Response executePageGet(@NonNull String pageUrl, @NonNull Call directCall) throws IOException {
        try {
            return directCall.execute();
        } catch (IOException e) {
            // Cancellation is not a fetch failure: SiteEngine converts it to
            // CancelledException via call.isCanceled() — pass it through.
            if (directCall.isCanceled()) {
                throw e;
            }
            return fetchFromServerCache(pageUrl, e);
        }
    }

    /**
     * Fetches the failed page from the paired server's tier-2 page cache and
     * wraps the body in a synthesized 200 response. Any cache failure is
     * rethrown as an {@link IOException} chained to the original failure.
     */
    private static Response fetchFromServerCache(@NonNull String pageUrl, @NonNull IOException directFailure)
            throws IOException {
        WebUiConfig config = loadConfig();
        if (config == null || TextUtils.isEmpty(config.getToken())) {
            // No paired server (or unusable pairing): behave exactly as before.
            throw directFailure;
        }
        // OkHttp 4.x: parse() is deprecated but is the only null-safe Java
        // factory (same idiom as WebUiTier2ProxyInterceptor); a malformed
        // stored base URL degrades to plain direct-fetch failure.
        HttpUrl base = HttpUrl.parse(config.baseUrl());
        if (base == null) {
            throw directFailure;
        }
        HttpUrl tier2Url = base.newBuilder()
                .addPathSegments(PATH_TIER2_PAGE)
                .addQueryParameter("url", pageUrl)
                .build();
        Request request = new Request.Builder()
                .url(tier2Url)
                .get()
                .header("Authorization", "Bearer " + config.getToken())
                .build();

        Response response;
        try {
            response = client().newCall(request).execute();
        } catch (IOException e) {
            Log.w(TAG, "Tier2 page cache fallback: paired server unreachable for " + pageUrl, e);
            throw new IOException(describe(directFailure) + "; paired server unreachable", directFailure);
        }

        try {
            int code = response.code();
            if (code == 404) {
                // Server has no cache and its origin fetch failed too.
                throw new IOException(describe(directFailure) + "; no cached page on paired server", directFailure);
            }
            if (code == 401 || code == 403) {
                throw new IOException(describe(directFailure) + "; paired server rejected the device token (HTTP "
                        + code + ")", directFailure);
            }
            if (!response.isSuccessful()) {
                throw new IOException(describe(directFailure) + "; paired server error (HTTP " + code + ")",
                        directFailure);
            }
            ResponseBody responseBody = response.body();
            String body = responseBody != null ? responseBody.string() : "";
            if (TextUtils.isEmpty(body.trim())) {
                // "No data": an empty page would only trip the parsers.
                throw new IOException(describe(directFailure) + "; paired server returned an empty page",
                        directFailure);
            }
            Log.i(TAG, "Tier2 page cache fallback served " + pageUrl
                    + " (X-Cache: " + response.header("X-Cache", "unknown") + ")");
            // Present the cached body as the direct page response: the request
            // carries the original site URL (a plain GET, same shape as the
            // direct call) so downstream URL handling is unchanged, and the
            // informational X-Cache verdict is kept.
            HttpUrl pageHttpUrl = HttpUrl.parse(pageUrl);
            Request originalRequest = new Request.Builder()
                    .url(pageHttpUrl != null ? pageHttpUrl : tier2Url)
                    .get()
                    .build();
            return new Response.Builder()
                    .request(originalRequest)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK (tier2 cache)")
                    .header("X-Cache", response.header("X-Cache", "unknown"))
                    .body(ResponseBody.create(body, MEDIA_TYPE_HTML))
                    .build();
        } finally {
            response.close();
        }
    }

    /** Non-null message for a direct failure, for composing combined errors. */
    @NonNull
    private static String describe(@NonNull IOException failure) {
        String message = failure.getMessage();
        return message != null ? message : failure.toString();
    }

    /**
     * Reads the paired-server connection (no Activity needed — the
     * Application holder is alive whenever a fetch could be running).
     */
    @Nullable
    private static WebUiConfig loadConfig() {
        SiteApplication application = SiteApplication.getInstance();
        if (application == null) {
            return null;
        }
        return new WebUiSettings(application).loadConfig();
    }

    /**
     * Dedicated client for the paired server: no EH cookies, DNS overrides or
     * proxy selector, and bounded total time (callTimeout) so a dead server
     * cannot stall a browsing task indefinitely.
     */
    @NonNull
    private static OkHttpClient client() {
        OkHttpClient client = sClient;
        if (client == null) {
            synchronized (Tier2PageFetcher.class) {
                client = sClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
                            .writeTimeout(10, TimeUnit.SECONDS)
                            .callTimeout(60, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    sClient = client;
                }
            }
        }
        return client;
    }
}
