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

import java.io.IOException;
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

    private static OkHttpClient client() {
        OkHttpClient client = sClient;
        if (client == null) {
            synchronized (WebUiApiClient.class) {
                client = sClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(30, TimeUnit.SECONDS)
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
        try (Response response = client().newCall(request).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return text;
        }
    }
}
