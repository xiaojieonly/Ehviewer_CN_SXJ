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

import java.io.IOException;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tier-2 browsing proxy (ADR-0003 D3, MASTER §4.1): when {@code clientTier >= 2}
 * and a WebUI server is paired, Gallery Site requests are routed through the
 * paired server — scheme/host/port rewritten to the server's
 * {@link WebUiConfig#baseUrl()}, path and query preserved, Referer/Origin
 * headers pointing at site hosts rewritten the same way. This mirrors the
 * debug-only {@code MOCK_EH_BASE_URL} interceptor in
 * {@link com.hippo.anotherviewer.SiteApplication}, per D3's "reuse the
 * MOCK_EH_BASE_URL interceptor pattern" directive.
 *
 * <p>Everything else passes through untouched: Tier-0/1 traffic (Tier-1
 * behavior is unchanged by contract), non-site hosts, and Tier-2/3 without a
 * configured server — browsing then degrades to direct instead of breaking.
 * The decision is read per request, so a tier or pairing change takes effect
 * immediately without rebuilding the client.
 */
public final class WebUiTier2ProxyInterceptor implements Interceptor {

    private final WebUiSettings settings;

    public WebUiTier2ProxyInterceptor(@NonNull WebUiSettings settings) {
        this.settings = settings;
    }

    /**
     * Whether site traffic is currently routed through the paired server.
     * Exposed so guards that compare request URLs against site-issued URLs
     * (e.g. the SpiderQueen anti-hijack check, which already exempts the
     * MOCK_EH_BASE_URL rewrite) can apply the same exemption for Tier-2.
     */
    public static boolean isRoutingActive(@NonNull WebUiSettings settings) {
        return settings.clientTier() >= 2 && settings.isConfigured();
    }

    /** Same host predicate as the mock-site interceptor in SiteApplication. */
    private static boolean isGallerySiteHost(@NonNull String host) {
        return host.equals("gallery.test") || host.endsWith(".gallery.test");
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!isGallerySiteHost(request.url().host()) || settings.clientTier() < 2) {
            return chain.proceed(request);
        }
        WebUiConfig config = settings.loadConfig();
        if (config == null) {
            return chain.proceed(request);
        }
        HttpUrl base = HttpUrl.parse(config.baseUrl());
        if (base == null) {
            return chain.proceed(request);
        }

        HttpUrl newUrl = request.url().newBuilder()
                .scheme(base.scheme())
                .host(base.host())
                .port(base.port())
                .build();
        Request.Builder builder = request.newBuilder().url(newUrl);
        String referer = request.header("Referer");
        HttpUrl refererUrl = referer != null ? HttpUrl.parse(referer) : null;
        if (refererUrl != null && isGallerySiteHost(refererUrl.host())) {
            builder.header("Referer", rewriteSiteUrl(refererUrl, base));
        }
        String origin = request.header("Origin");
        HttpUrl originUrl = origin != null ? HttpUrl.parse(origin) : null;
        if (originUrl != null && isGallerySiteHost(originUrl.host())) {
            builder.header("Origin", rewriteSiteUrl(originUrl, base));
        }
        return chain.proceed(builder.build());
    }

    private static String rewriteSiteUrl(@NonNull HttpUrl url, @NonNull HttpUrl base) {
        return url.newBuilder()
                .scheme(base.scheme())
                .host(base.host())
                .port(base.port())
                .build().toString();
    }
}
