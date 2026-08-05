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
 * Tier-2 browsing proxy (ADR-0003 D3, MASTER §4.1; W3 R4-13): when
 * {@code clientTier >= 2} and a WebUI server is paired, Gallery Site requests
 * are routed through the server's transparent site proxy — rewritten to
 * {@code {paired}/api/v1/site/proxy?url=<encoded original URL>}, which the
 * server fetches with its shared site session and passes back verbatim.
 * The paired request carries {@code Authorization: Bearer <pairing token>}
 * so auth-on servers accept it. Referer/Origin headers pointing at site
 * hosts are rewritten the same way, so from the app's point of view every
 * site resource comes from the server.
 *
 * <p>Everything else passes through untouched: Tier-0/1 traffic (Tier-1
 * behavior is unchanged by contract), non-site hosts — including the paired
 * server's own structured API ({@code /api/v1/*}) requests — and Tier-2/3
 * without a configured server, where browsing degrades to direct instead of
 * breaking. The decision is read per request, so a tier or pairing change
 * takes effect immediately without rebuilding the client.
 */
public final class WebUiTier2ProxyInterceptor implements Interceptor {

    private final WebUiSettings settings;

    public WebUiTier2ProxyInterceptor(@NonNull WebUiSettings settings) {
        this.settings = settings;
    }

    /**
     * Whether site traffic is currently routed through the paired server.
     * Exposed so guards that compare request URLs against site-issued URLs
     * (e.g. the SpiderQueen anti-hijack check) can apply the same exemption
     * for Tier-2.
     */
    public static boolean isRoutingActive(@NonNull WebUiSettings settings) {
        return settings.clientTier() >= 2 && settings.isConfigured();
    }

    /**
     * Host predicate for Gallery Site traffic: the site root domains and any
     * subdomain of them (e.g. s.exhentai.org, lofi.e-hentai.org, ehgt.org).
     */
    private static boolean isGallerySiteHost(@NonNull String host) {
        return host.equals("exhentai.org") || host.endsWith(".exhentai.org")
                || host.equals("e-hentai.org") || host.endsWith(".e-hentai.org")
                || host.equals("lofi.e-hentai.org") || host.endsWith(".lofi.e-hentai.org")
                || host.equals("ehgt.org") || host.endsWith(".ehgt.org");
    }

    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request request = chain.request();
        if (!isGallerySiteHost(request.url().host())
                || settings.clientTier() < 2) {
            return chain.proceed(request);
        }
        WebUiConfig config = settings.loadConfig();
        if (config == null) {
            return chain.proceed(request);
        }
        // OkHttp 4.x: parse() is deprecated but remains the only null-safe Java
        // factory (HttpUrl.get throws on malformed input); null checks below rely
        // on that to degrade Tier-2 routing to direct instead of breaking.
        HttpUrl base = HttpUrl.parse(config.baseUrl());
        if (base == null) {
            return chain.proceed(request);
        }

        HttpUrl proxied = proxyUrl(base, request.url());
        Request.Builder builder = request.newBuilder().url(proxied);
        attachBearer(builder, config.getToken());
        String referer = request.header("Referer");
        HttpUrl refererUrl = referer != null ? HttpUrl.parse(referer) : null;
        if (refererUrl != null && isGallerySiteHost(refererUrl.host())) {
            builder.header("Referer", proxyUrl(base, refererUrl).toString());
        }
        String origin = request.header("Origin");
        HttpUrl originUrl = origin != null ? HttpUrl.parse(origin) : null;
        if (originUrl != null && isGallerySiteHost(originUrl.host())) {
            builder.header("Origin", proxyUrl(base, originUrl).toString());
        }
        return chain.proceed(builder.build());
    }

    /**
     * {@code {base}/api/v1/site/proxy?url=<percent-encoded site url>} — the
     * W3 R4-13 transparent proxy endpoint on the paired WebUI server. The
     * original URL (path and query intact) travels as the {@code url} param.
     */
    private static HttpUrl proxyUrl(@NonNull HttpUrl base, @NonNull HttpUrl siteUrl) {
        return base.newBuilder()
                .addPathSegments("api/v1/site/proxy")
                .addQueryParameter("url", siteUrl.toString())
                .build();
    }

    /**
     * Visible for testing: attaches the pairing token as a Bearer header so
     * auth-on servers accept the proxied request; empty token adds nothing.
     */
    static void attachBearer(@NonNull Request.Builder builder, @NonNull String token) {
        if (!token.isEmpty()) {
            builder.header("Authorization", "Bearer " + token);
        }
    }
}
