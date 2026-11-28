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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.SiteApplication;
import com.hippo.anotherviewer.SiteDB;
import com.hippo.anotherviewer.client.SiteCacheKeyFactory;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.beerbelly.BeerBelly;
import com.hippo.conaco.Conaco;
import com.hippo.lib.image.Image;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Best-effort thumbnail prefetch after sync: fetches gallery thumbs through
 * the paired server image proxy and writes bytes into Conaco disk cache so
 * both WebUI-cached and App-cached thumbnails are available offline.
 */
final class WebUiThumbPrefetcher {

    private static final int MAX_THUMBS_PER_RUN = 200;
    private static final long MAX_BYTES = 2L * 1024L * 1024L;

    private WebUiThumbPrefetcher() {}

    static void prefetch(@NonNull Context context, @NonNull WebUiConfig config) {
        try {
            List<ThumbTarget> targets = collectTargets();
            if (targets.isEmpty()) {
                return;
            }
            Conaco<Image> conaco = SiteApplication.getConaco(context);
            BeerBelly<Image> belly = conaco.getBeerBelly();
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(20, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build();

            int count = 0;
            for (ThumbTarget target : targets) {
                if (count >= MAX_THUMBS_PER_RUN) {
                    break;
                }
                if (target.url == null || target.url.isEmpty()) {
                    continue;
                }
                String key = SiteCacheKeyFactory.getThumbKey(target.gid);
                if (hasDisk(belly, key)) {
                    continue;
                }
                if (fetchAndStore(client, config, target, key, belly)) {
                    count++;
                }
            }
            if (count > 0) {
                belly.flush();
            }
        } catch (Throwable ignored) {
            // Best-effort: sync must not fail because of thumbnail prefetch.
        }
    }

    private static boolean hasDisk(BeerBelly<Image> belly, String key) {
        try {
            // pullFromDiskCache writes to OutputStream; use a sink that discards bytes.
            return belly.pullFromDiskCache(key, new OutputStreamSink());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean fetchAndStore(OkHttpClient client, WebUiConfig config,
            ThumbTarget target, String key, BeerBelly<Image> belly) {
        String requestUrl = buildRequestUrl(config, target.url);
        Request.Builder builder = new Request.Builder().url(requestUrl).get();
        String token = config.getToken();
        if (token != null && !token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
        try (Response response = client.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                return false;
            }
            ResponseBody body = response.body();
            if (body == null) {
                return false;
            }
            byte[] bytes = readBounded(body.byteStream(), MAX_BYTES);
            if (bytes == null || bytes.length == 0) {
                return false;
            }
            return belly.putRawToDisk(key, new ByteArrayInputStream(bytes));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String buildRequestUrl(WebUiConfig config, String originalUrl) {
        if (isSiteAssetUrl(originalUrl)) {
            return config.baseUrl() + "/api/v1/image/proxy?url=" + urlEncode(originalUrl);
        }
        return originalUrl;
    }

    private static boolean isSiteAssetUrl(String url) {
        String u = url.toLowerCase();
        return u.startsWith("https://e-hentai.org/")
                || u.startsWith("http://e-hentai.org/")
                || u.startsWith("https://exhentai.org/")
                || u.startsWith("http://exhentai.org/")
                || u.startsWith("https://ehgt.org/")
                || u.startsWith("http://ehgt.org/")
                || u.contains(".e-hentai.org/")
                || u.contains(".exhentai.org/")
                || u.contains(".ehgt.org/");
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    @Nullable
    private static byte[] readBounded(InputStream is, long maxBytes) throws IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = is.read(buf)) != -1) {
            total += n;
            if (total > maxBytes) {
                return null;
            }
            baos.write(buf, 0, n);
        }
        return baos.toByteArray();
    }

    private static List<ThumbTarget> collectTargets() {
        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<ThumbTarget> targets = new ArrayList<>();
        try {
            List<DownloadInfo> downloads = SiteDB.getAllDownloadInfo();
            if (downloads != null) {
                for (DownloadInfo info : downloads) {
                    addTarget(targets, seen, info.gid, info.thumb);
                }
            }
        } catch (Throwable ignored) {}
        try {
            List<GalleryInfo> favorites = SiteDB.getAllLocalFavorites();
            if (favorites != null) {
                for (GalleryInfo gi : favorites) {
                    addTarget(targets, seen, gi.gid, gi.thumb);
                }
            }
        } catch (Throwable ignored) {}
        try {
            List<HistoryInfo> history = SiteDB.getAllHistoryForSync();
            if (history != null) {
                for (HistoryInfo hi : history) {
                    addTarget(targets, seen, hi.gid, hi.thumb);
                }
            }
        } catch (Throwable ignored) {}
        return targets;
    }

    private static void addTarget(List<ThumbTarget> targets, Set<Long> seen, long gid, String thumb) {
        if (gid <= 0 || thumb == null || thumb.isEmpty() || !seen.add(gid)) {
            return;
        }
        targets.add(new ThumbTarget(gid, thumb));
    }

    private static final class ThumbTarget {
        final long gid;
        final String url;
        ThumbTarget(long gid, String url) {
            this.gid = gid;
            this.url = url;
        }
    }

    private static final class OutputStreamSink extends java.io.OutputStream {
        @Override
        public void write(int b) {}
        @Override
        public void write(@NonNull byte[] b, int off, int len) {}
    }
}
