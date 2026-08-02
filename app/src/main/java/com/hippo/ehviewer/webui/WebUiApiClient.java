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

package com.hippo.ehviewer.webui;

import androidx.annotation.NonNull;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.parser.ParserConfig;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin REST client for the WebUI companion server's sync + auth endpoints
 * (see {@code contracts/openapi.yaml}, prefix {@code /api/v1/}). Uses the app's
 * existing OkHttp library and fastjson for (de)serialization.
 *
 * <p>A dedicated {@link OkHttpClient} is used instead of
 * {@link com.hippo.ehviewer.EhApplication#getOkHttpClient}: that client is tuned
 * for the EH site (custom DNS hosts, EH cookie jar) which must not be applied to
 * a LAN sync server. All calls are synchronous and must run off the main thread.
 */
public final class WebUiApiClient {

    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    private static volatile OkHttpClient sClient;

    private WebUiApiClient() {}

    static {
        // Hard-disable fastjson autotype for good: every parse below goes against
        // a known DTO shape, so @type would only serve gadget deserialization if a
        // sync server (or MITM) ever sent a hostile payload. Safe mode (1.2.68+)
        // makes @type permanently un-honorable even if autotype is re-enabled later.
        ParserConfig.getGlobalInstance().setSafeMode(true);
    }

    private static OkHttpClient client() {
        OkHttpClient client = sClient;
        if (client == null) {
            synchronized (WebUiApiClient.class) {
                client = sClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(30, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    sClient = client;
                }
            }
        }
        return client;
    }

    /** POST /api/v1/auth/login — obtains a bearer token. permitAll on the server. */
    @NonNull
    public static WebUiSyncModels.AuthResponse login(@NonNull WebUiConfig config,
            @NonNull String username, @NonNull String password) throws IOException {
        WebUiSyncModels.LoginRequest body = new WebUiSyncModels.LoginRequest();
        body.username = username;
        body.password = password;
        String json = postJson(config.baseUrl() + "/api/v1/auth/login", null, JSON.toJSONString(body));
        WebUiSyncModels.AuthResponse response = JSON.parseObject(json, WebUiSyncModels.AuthResponse.class);
        if (response == null) {
            throw new IOException("Empty login response");
        }
        return response;
    }

    /** GET /api/v1/sync/status — verifies the token and reports server state. */
    @NonNull
    public static WebUiSyncModels.StatusResponse status(@NonNull WebUiConfig config) throws IOException {
        String json = get(config.baseUrl() + "/api/v1/sync/status", config.getToken());
        WebUiSyncModels.StatusResponse response = JSON.parseObject(json, WebUiSyncModels.StatusResponse.class);
        if (response == null) {
            throw new IOException("Empty status response");
        }
        return response;
    }

    /**
     * POST /api/v1/auth/pair/complete — exchanges a pairing code for a device
     * token. permitAll on the server; no Authorization header is sent.
     */
    @NonNull
    public static WebUiSyncModels.PairCompleteResponse pairComplete(@NonNull WebUiConfig config,
            @NonNull String code, @NonNull String deviceId, @NonNull String deviceName,
            @NonNull String platform) throws IOException {
        WebUiSyncModels.PairCompleteRequest body = new WebUiSyncModels.PairCompleteRequest();
        body.code = code;
        body.deviceId = deviceId;
        body.deviceName = deviceName;
        body.platform = platform;
        String json = postJson(config.baseUrl() + "/api/v1/auth/pair/complete", null, JSON.toJSONString(body));
        WebUiSyncModels.PairCompleteResponse response = JSON.parseObject(json, WebUiSyncModels.PairCompleteResponse.class);
        if (response == null) {
            throw new IOException("Empty pair response");
        }
        return response;
    }

    /** POST /api/v1/sync/push — uploads local changes. */
    @NonNull
    public static WebUiSyncModels.PushResponse push(@NonNull WebUiConfig config,
            @NonNull WebUiSyncModels.PushRequest request) throws IOException {
        String json = postJson(config.baseUrl() + "/api/v1/sync/push", config.getToken(), JSON.toJSONString(request));
        WebUiSyncModels.PushResponse response = JSON.parseObject(json, WebUiSyncModels.PushResponse.class);
        if (response == null) {
            throw new IOException("Empty push response");
        }
        return response;
    }

    /** GET /api/v1/sync/pull?since={timestamp} — downloads server changes. */
    @NonNull
    public static WebUiSyncModels.PullResponse pull(@NonNull WebUiConfig config, long since) throws IOException {
        String json = get(config.baseUrl() + "/api/v1/sync/pull?since=" + since, config.getToken());
        WebUiSyncModels.PullResponse response = JSON.parseObject(json, WebUiSyncModels.PullResponse.class);
        if (response == null) {
            throw new IOException("Empty pull response");
        }
        return response;
    }

    // ---- Remote reading (I5 §2.4) ----

    /**
     * GET /api/v1/gallery/{gid} — returns the page count of a gallery from the
     * server's index. Used by {@link com.hippo.ehviewer.gallery.WebUiGalleryProvider}
     * to size the reader before any image is fetched.
     *
     * @return page count, or {@code -1} if the server payload has no {@code pages}
     *         field or the count is not positive (callers treat {@code < 1} as an error)
     */
    public static int getGalleryPages(@NonNull WebUiConfig config, long gid) throws IOException {
        String json = get(config.baseUrl() + "/api/v1/gallery/" + gid, config.getToken());
        GalleryDetail detail = JSON.parseObject(json, GalleryDetail.class);
        if (detail == null) {
            throw new IOException("Empty gallery detail response");
        }
        return detail.pages > 0 ? detail.pages : -1;
    }

    /**
     * GET /api/v1/image/{galleryId}/{page} — streams the raw bytes of one page.
     * The caller owns the returned {@link Response} and must close it.
     * The server fetches from EH on cache miss, so the phone never talks to EH.
     */
    @NonNull
    public static Response fetchImage(@NonNull WebUiConfig config, long gid, int page) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(config.baseUrl() + "/api/v1/image/" + gid + "/" + page)
                .get();
        addAuth(builder, config.getToken());
        return client().newCall(builder.build()).execute();
    }

    // ---- Delegated download (I5 §2.5) ----

    /**
     * POST /api/v1/download/add — registers a download task on the server.
     * Idempotent by gid: returns {@code false} when the task already exists.
     */
    public static boolean addDownload(@NonNull WebUiConfig config,
            @NonNull WebUiDownloadModels.DownloadAddRequest request) throws IOException {
        String json = postJson(config.baseUrl() + "/api/v1/download/add", config.getToken(),
                JSON.toJSONString(request));
        return Boolean.parseBoolean(json.trim());
    }

    /**
     * GET /api/v1/download/list — lists server-side download tasks. The server
     * assigns each task a numeric {@code id} on add, which the client needs to
     * start/pause it; we resolve it by gid from this list.
     */
    @NonNull
    public static List<WebUiDownloadModels.DownloadListItem> listDownloads(@NonNull WebUiConfig config)
            throws IOException {
        String json = get(config.baseUrl() + "/api/v1/download/list", config.getToken());
        DownloadListResponse root = JSON.parseObject(json, DownloadListResponse.class);
        if (root == null) {
            return new ArrayList<>();
        }
        return root.downloads;
    }

    /** POST /api/v1/download/start/{id} — starts or resumes a server-side task. */
    public static boolean startDownload(@NonNull WebUiConfig config, long id) throws IOException {
        String json = postJson(config.baseUrl() + "/api/v1/download/start/" + id, config.getToken(), "");
        return Boolean.parseBoolean(json.trim());
    }

    private static String get(String url, String token) throws IOException {
        Request.Builder builder = new Request.Builder().url(url).get();
        addAuth(builder, token);
        return execute(builder.build());
    }

    private static String postJson(String url, String token, String json) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(RequestBody.create(JSON_MEDIA, json));
        addAuth(builder, token);
        return execute(builder.build());
    }

    private static void addAuth(Request.Builder builder, String token) {
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    private static String execute(Request request) throws IOException {
        try {
            return executeInternal(request);
        } catch (SocketTimeoutException e) {
            throw new IOException("请求超时（服务器无响应）", e);
        }
    }

    private static String executeInternal(Request request) throws IOException {
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return text;
        }
    }

    /** Wire shape of GET /api/v1/gallery/{gid} (GalleryDetailDto); client consumes only {@code pages}. */
    private static final class GalleryDetail {
        public int pages;
    }

    /** Wire shape of GET /api/v1/download/list (DownloadListResponse). */
    private static final class DownloadListResponse {
        public List<WebUiDownloadModels.DownloadListItem> downloads = new ArrayList<>();
    }
}
