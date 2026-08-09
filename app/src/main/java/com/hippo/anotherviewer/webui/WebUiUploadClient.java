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

package com.hippo.anotherviewer.webui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.fastjson.JSON;
import com.hippo.unifile.UniFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;

/**
 * Thin REST client for the WebUI companion server's download-upload (push)
 * endpoints (see {@code contracts/openapi.yaml}, prefix
 * {@code /api/v1/download/upload/}).
 *
 * <p>Mirrors {@link WebUiApiClient} in style (dedicated OkHttp client, bearer
 * auth, fastjson) but lives in its own file so the sync client can evolve
 * independently. All calls are synchronous and must run off the main thread;
 * pages stream from the {@link UniFile} without being buffered into memory.
 */
public final class WebUiUploadClient {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final MediaType OCTET_MEDIA = MediaType.get("application/octet-stream");

    private static volatile OkHttpClient sClient;

    private WebUiUploadClient() {}

    private static OkHttpClient client() {
        OkHttpClient client = sClient;
        if (client == null) {
            synchronized (WebUiUploadClient.class) {
                client = sClient;
                if (client == null) {
                    client = new OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(120, TimeUnit.SECONDS)
                            .writeTimeout(60, TimeUnit.SECONDS)
                            .retryOnConnectionFailure(true)
                            .build();
                    sClient = client;
                }
            }
        }
        return client;
    }

    /**
     * PUT /api/v1/download/upload/{gid} — registers or updates the download
     * row and returns the pages the server already has (resume support).
     * The server answers a non-force gid conflict with 400 + an
     * InitResponse(success=false); the caller decides to skip that gallery.
     */
    @NonNull
    public static WebUiUploadModels.InitResponse uploadInit(@NonNull WebUiConfig config, long gid,
            @NonNull WebUiUploadModels.UploadInitRequest request) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(config.baseUrl() + "/api/v1/download/upload/" + gid)
                .put(RequestBody.create(JSON.toJSONString(request), JSON_MEDIA));
        addAuth(builder, config.getToken());
        try (Response response = client().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            String text = body != null ? body.string() : "";
            WebUiUploadModels.InitResponse parsed = JSON.parseObject(text, WebUiUploadModels.InitResponse.class);
            if (parsed == null) {
                throw new IOException("HTTP " + response.code() + " " + response.message());
            }
            return parsed;
        }
    }

    /**
     * POST /api/v1/download/upload/{gid}/page/{page} — uploads one page
     * (multipart, streamed from the {@link UniFile}). Content-type is inferred
     * from the file extension; the server keeps the original extension.
     */
    public static boolean uploadPage(@NonNull WebUiConfig config, long gid, int page,
            @NonNull UniFile file) throws IOException {
        String name = file.getName();
        MultipartBody body = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", name != null ? name : ("page" + page),
                        streamBody(file, mediaTypeOf(name)))
                .build();
        Request.Builder builder = new Request.Builder()
                .url(config.baseUrl() + "/api/v1/download/upload/" + gid + "/page/" + page)
                .post(body);
        addAuth(builder, config.getToken());
        try (Response response = client().newCall(builder.build()).execute()) {
            return response.isSuccessful();
        }
    }

    /** POST /api/v1/download/upload/{gid}/complete — finalizes the push. */
    public static boolean uploadComplete(@NonNull WebUiConfig config, long gid, int total)
            throws IOException {
        WebUiUploadModels.CompleteRequest request = new WebUiUploadModels.CompleteRequest();
        request.total = total;
        request.done = total;
        Request.Builder builder = new Request.Builder()
                .url(config.baseUrl() + "/api/v1/download/upload/" + gid + "/complete")
                .post(RequestBody.create(JSON.toJSONString(request), JSON_MEDIA));
        addAuth(builder, config.getToken());
        try (Response response = client().newCall(builder.build()).execute()) {
            return response.isSuccessful();
        }
    }

    private static void addAuth(Request.Builder builder, String token) {
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }

    /** Content-type by file extension; unknown extensions fall back to octet-stream. */
    private static MediaType mediaTypeOf(@Nullable String name) {
        if (name == null) {
            return OCTET_MEDIA;
        }
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.get("image/jpeg");
        }
        if (lower.endsWith(".png")) {
            return MediaType.get("image/png");
        }
        if (lower.endsWith(".gif")) {
            return MediaType.get("image/gif");
        }
        if (lower.endsWith(".webp")) {
            return MediaType.get("image/webp");
        }
        return OCTET_MEDIA;
    }

    /**
     * Streams a {@link UniFile} into the request body in 64 KiB chunks — a
     * SAF document never needs to be buffered into memory, however large.
     */
    private static RequestBody streamBody(@NonNull final UniFile file,
            @NonNull final MediaType mediaType) {
        return new RequestBody() {
            @Override
            public MediaType contentType() {
                return mediaType;
            }

            @Override
            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream in = file.openInputStream()) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        sink.write(buffer, 0, read);
                    }
                }
            }
        };
    }
}
