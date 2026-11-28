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
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.hippo.anotherviewer.R;
import com.hippo.anotherviewer.SiteApplication;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
 * so auth-on servers accept it. Site Referer/Origin headers are dropped
 * rather than forwarded: the server-side proxy rebuilds both from its own
 * site config, and a forwarded Origin would make the POST a cross-origin
 * request to the server's CORS filter (instant 403 before the proxy runs).
 * From the app's point of view every site resource comes from the server.
 *
 * <p>Everything else passes through untouched: Tier-0/1 traffic (Tier-1
 * behavior is unchanged by contract), non-site hosts — including the paired
 * server's own structured API ({@code /api/v1/*}) requests — and Tier-2/3
 * without a configured server, where browsing degrades to direct instead of
 * breaking. The decision is read per request, so a tier or pairing change
 * takes effect immediately without rebuilding the client.
 *
 * <p>Server-outage resilience (ADR-0007 D7, 滞回版): a single failure no
 * longer flips browsing into direct mode. A connection-level failure still
 * degrades — but only for a short window ({@link #DEGRADE_WINDOW_MS}) — and a
 * direct attempt that fails too shrinks the window to
 * {@link #FAILED_FALLBACK_RETRY_MS}: direct is no better than the proxy then,
 * so the server is retried almost immediately (the user-reported 「偶发转直连
 * 导致不可用」 case). A 502 from the transparent proxy means the server is
 * <em>alive</em> (only its upstream fetch failed): it is retried inline once,
 * and only {@link #BAD_GATEWAY_DEGRADE_THRESHOLD} consecutive persistent-502
 * requests degrade at all.
 */
public final class WebUiTier2ProxyInterceptor implements Interceptor {

    /**
     * How long to keep browsing direct after the paired server was seen
     * unreachable, before the proxy is retried. Bounds the blast radius of a
     * dead server without hammering it with per-request probes. ADR-0007 D7：
     * 60s → 15s —— 坏链路上的单次抖动不值得把服务器关一整个分钟；直连兜底
     * 也失败时会进一步把窗口缩到 {@link #FAILED_FALLBACK_RETRY_MS}。
     */
    private static final long DEGRADE_WINDOW_MS = 15_000L;

    /** 窗口内直连兜底也失败 → 窗口缩到最短（直连无用，尽快回服务器重试）。 */
    private static final long FAILED_FALLBACK_RETRY_MS = 3_000L;

    /** 连续 N 次「内联重试后仍 502」才按服务器级故障进入降级窗口（ADR-0007 D7）。 */
    private static final int BAD_GATEWAY_DEGRADE_THRESHOLD = 3;

    private final WebUiSettings settings;

    /** Server proxy considered unreachable until this timestamp (ms since epoch). */
    private final AtomicLong mDegradedUntil = new AtomicLong(0L);

    /** Whether browsing is currently in degraded (direct) mode. */
    private final AtomicBoolean mDegraded = new AtomicBoolean(false);

    /** 连续「重试后仍 502」的请求数；任何代理成功即清零（ADR-0007 D7）。 */
    private final AtomicInteger mConsecutiveBadGateways = new AtomicInteger();

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

        if (System.currentTimeMillis() < mDegradedUntil.get()) {
            // The server was recently unreachable: fall straight back to
            // direct Gallery Site access instead of failing against the
            // proxy again (recovery retries after the window expires).
            return proceedDirect(chain, request);
        }

        HttpUrl proxied = isImageCacheCandidate(request)
                ? imageProxyUrl(base, request.url())
                : proxyUrl(base, request.url());
        Request.Builder builder = request.newBuilder().url(proxied);
        attachBearer(builder, config.getToken());
        // Drop site Referer/Origin instead of rewriting them: the server-side
        // proxy rebuilds both from its own site config (SiteProxyController),
        // and a forwarded Origin makes the POST a cross-origin request in the
        // server's CORS filter, which answers 403 before the proxy runs.
        builder.removeHeader("Referer").removeHeader("Origin");
        Response response;
        try {
            response = chain.proceed(builder.build());
        } catch (IOException e) {
            // Connection-level failure: the server is unreachable. Retry the
            // request once against the site directly and stay degraded for
            // the (short) window; a direct failure shrinks the window further
            // (see proceedDirect).
            degrade();
            return proceedDirect(chain, request);
        }
        if (response.code() == HTTP_BAD_GATEWAY) {
            // 服务器活着（它答复了 502，只是上游抓取失败）——这不是「服务器
            // 不可达」。上游抖动大多瞬态：先原样重试一次代理；仍 502 才计入
            // 连续计数，达到阈值才进入短降级窗口（ADR-0007 D7：单次 502 不再
            // 把整个浏览切到直连——「偶发转直连导致不可用」的实测痛点）。
            response.close();
            Response retry = chain.proceed(builder.build());
            if (retry.code() != HTTP_BAD_GATEWAY) {
                mConsecutiveBadGateways.set(0);
                announceRecoveredIfNeeded();
                return retry;
            }
            retry.close();
            if (mConsecutiveBadGateways.incrementAndGet() >= BAD_GATEWAY_DEGRADE_THRESHOLD) {
                degrade();
            }
            // 本次请求兜底直连一次（改变的只是本次请求，不是全局模式）。
            return proceedDirect(chain, request);
        }
        mConsecutiveBadGateways.set(0);
        announceRecoveredIfNeeded();
        return response;
    }

    /**
     * 直连兜底（降级窗口内与各失败路径共用）：成功即按原节拍等窗口到期；
     * 失败则把窗口缩到最短——直连并不比服务器好用，下一个请求尽快回到
     * 服务器重试。直连异常原样向上抛（调用方看到的就是直连失败）。
     */
    private Response proceedDirect(@NonNull Chain chain, @NonNull Request request) throws IOException {
        try {
            return chain.proceed(request);
        } catch (IOException e) {
            shrinkDegradeWindow();
            throw e;
        }
    }

    /**
     * 代理答以成功：离开降级态、报恢复并补一轮同步（状态翻转守卫——一次
     * 中断只在恢复时提示一次）。
     */
    private void announceRecoveredIfNeeded() {
        if (mDegraded.compareAndSet(true, false)) {
            notifyTransition(R.string.webui_proxy_recovered);
            WebUiAutoSyncScheduler.triggerOnce(SiteApplication.getInstance());
        }
    }

    /**
     * 直连兜底失败：直连并不比服务器更好用——把降级窗口缩到最短，让下一个
     * 请求尽快回到服务器（ADR-0007 D7）。只缩短、不建立：{@code 0}（未降级）
     * 经 min 后仍为 0，已过期的窗口保持过期。
     */
    private void shrinkDegradeWindow() {
        long target = System.currentTimeMillis() + FAILED_FALLBACK_RETRY_MS;
        mDegradedUntil.accumulateAndGet(target, Math::min);
    }

    /**
     * Marks the server proxy unreachable until the degrade window expires.
     * The user is told exactly once per outage (state-flip guarded).
     */
    private void degrade() {
        mDegradedUntil.set(System.currentTimeMillis() + DEGRADE_WINDOW_MS);
        if (mDegraded.compareAndSet(false, true)) {
            notifyTransition(R.string.webui_proxy_degraded);
        }
    }

    /** Whether browsing is currently degraded to direct site access. */
    boolean isDegraded() {
        return mDegraded.get();
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

    /**
     * 缩略图/图片资源走服务器 image proxy，与 Web 前端的 rewriteSiteAssetUrl
     * 共用同一 URL-keyed 磁盘缓存，实现 WebUI ↔ App 双向可见：任一端先取到的
     * ehgt 缩略图都会落入服务器缓存，另一端再取时命中缓存。仅对 GET + ehgt
     * 主机生效，避免把 HTML/API POST 误送进只读图片端点。
     */
    private static boolean isImageCacheCandidate(@NonNull Request request) {
        if (!"GET".equals(request.method())) {
            return false;
        }
        String host = request.url().host();
        return host.equals("ehgt.org") || host.endsWith(".ehgt.org");
    }

    /**
     * {@code {base}/api/v1/image/proxy?url=<percent-encoded site url>} — the
     * shared image cache endpoint used by the web frontend and, for thumbnail
     * hosts, by Tier-2 app traffic.
     */
    private static HttpUrl imageProxyUrl(@NonNull HttpUrl base, @NonNull HttpUrl siteUrl) {
        return base.newBuilder()
                .addPathSegments("api/v1/image/proxy")
                .addQueryParameter("url", siteUrl.toString())
                .build();
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

    /** BAD_GATEWAY from the transparent proxy: server up, upstream fetch failed. */
    private static final int HTTP_BAD_GATEWAY = 502;

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
