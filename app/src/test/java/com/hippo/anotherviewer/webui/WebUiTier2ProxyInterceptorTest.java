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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Routing oracle for the Tier-2 browsing proxy (ADR-0003 D3, W3 R4-13):
 * site-host requests are rewritten to the paired server's transparent proxy
 * ({@code {paired}/api/v1/site/proxy?url=<encoded original>}) only when
 * {@code clientTier >= 2} AND a server is configured; Tier-0/1 traffic,
 * non-site hosts (including the server's own {@code /api/v1/*} structured
 * API) and unpaired Tier-2 pass through byte-identical.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiTier2ProxyInterceptorTest {

    private static final WebUiConfig SERVER =
            new WebUiConfig("http", "192.168.1.10", 8080, "", "");

    private WebUiSettings settings;
    private WebUiTier2ProxyInterceptor interceptor;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        settings = new WebUiSettings(context);
        interceptor = new WebUiTier2ProxyInterceptor(settings);
    }

    /** Minimal recording chain: remembers the request it was asked to proceed. */
    private static final class FakeChain implements Interceptor.Chain {
        private final Request original;
        private Request proceeded;
        final List<Request> proceededAll = new ArrayList<>();
        /** First N proceed() calls throw IOException (simulated server outage). */
        int failCount;
        /** HTTP status of the synthesized response. */
        int responseCode = 200;
        /** First N proceed() calls answer 502 BAD_GATEWAY, then responseCode. */
        int badGatewayFirst;

        FakeChain(Request original) {
            this.original = original;
        }

        @Override
        public Request request() {
            return original;
        }

        @Override
        public Response proceed(Request request) throws java.io.IOException {
            proceededAll.add(request);
            if (failCount > 0) {
                failCount--;
                throw new java.io.IOException("server unreachable");
            }
            int code = badGatewayFirst > 0 ? 502 : responseCode;
            if (badGatewayFirst > 0) {
                badGatewayFirst--;
            }
            proceeded = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(code == 200 ? "OK" : "Curl")
                    .body(ResponseBody.create(new byte[0], MediaType.get("text/html")))
                    .build();
        }

        @Override
        public Connection connection() {
            return null;
        }

        @Override
        public Call call() {
            return null;
        }

        @Override
        public int connectTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withConnectTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int readTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withReadTimeout(int timeout, TimeUnit unit) {
            return this;
        }

        @Override
        public int writeTimeoutMillis() {
            return 0;
        }

        @Override
        public Interceptor.Chain withWriteTimeout(int timeout, TimeUnit unit) {
            return this;
        }
    }

    private Request proceed(Request request) throws Exception {
        FakeChain chain = new FakeChain(request);
        Response response = interceptor.intercept(chain);
        response.close();
        return chain.proceeded;
    }

    // ------------------------------------------------------------------
    // Pass-through guarantees (Tier-1 behavior unchanged)
    // ------------------------------------------------------------------

    @Test
    public void testTierOnePassesSiteTrafficThroughUntouched() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(1);

        Request request = new Request.Builder()
                .url("https://e-hentai.org/?f_search=alpha&page=2")
                .header("Referer", "https://e-hentai.org/")
                .build();
        Request proceeded = proceed(request);

        assertSame("Tier-1 must forward the original request object", request, proceeded);
        assertEquals("https://e-hentai.org/?f_search=alpha&page=2",
                proceeded.url().toString());
    }

    @Test
    public void testTierZeroPassesSiteTrafficThroughUntouched() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(0);

        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        assertSame(request, proceed(request));
    }

    @Test
    public void testTierTwoWithoutConfiguredServerDegradesToDirect() throws Exception {
        settings.setClientTier(2);
        assertFalse(settings.isConfigured());

        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        assertSame("unpaired Tier-2 must not break browsing", request, proceed(request));
    }

    @Test
    public void testTierTwoLeavesNonSiteHostsAlone() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request request = new Request.Builder()
                .url("https://example.com/g/1001/aaa/")
                .build();
        assertSame(request, proceed(request));
    }

    @Test
    public void testStructuredApiRequestsAreNeverRewritten() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        // The app's own calls to the paired server (sync / reader structured
        // API) must not be wrapped into the site proxy.
        for (String url : new String[]{
                "http://192.168.1.10:8080/api/v1/sync/push",
                "http://192.168.1.10:8080/api/v1/image/1001/0",
                "http://192.168.1.10:8080/api/v1/site/proxy?url=anything"}) {
            Request request = new Request.Builder().url(url).build();
            assertSame(url, request, proceed(request));
        }
    }

    // ------------------------------------------------------------------
    // Tier-2 rewriting → server transparent proxy (W3 R4-13)
    // ------------------------------------------------------------------

    @Test
    public void testTierTwoRoutesThroughServerSiteProxy() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://exhentai.org/g/1001/aaa/?p=1")
                .build());

        assertEquals("http", proceeded.url().scheme());
        assertEquals("192.168.1.10", proceeded.url().host());
        assertEquals(8080, proceeded.url().port());
        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("the original URL travels as the url param, path/query intact",
                "https://exhentai.org/g/1001/aaa/?p=1",
                proceeded.url().queryParameter("url"));
    }

    @Test
    public void testUrlParamEncodingSurvivesReservedCharacters() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        // '&' and '=' in the original query must not leak into the proxy
        // request's own query structure: exactly one param, round-trip intact.
        String original = "https://e-hentai.org/?f_search=a+b&f_cats=2&advsearch=1";
        Request proceeded = proceed(new Request.Builder().url(original).build());

        assertEquals(1, proceeded.url().querySize());
        assertEquals(original, proceeded.url().queryParameter("url"));
        assertTrue(proceeded.url().toString().startsWith(
                "http://192.168.1.10:8080/api/v1/site/proxy?url="));
        assertFalse("raw '&' inside the value must stay percent-encoded",
                proceeded.url().toString().substring(
                        "http://192.168.1.10:8080/api/v1/site/proxy?url=".length())
                        .contains("&"));
    }

    @Test
    public void testSubdomainHostsRouteThroughTheProxyToo() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://lofi.e-hentai.org/index.php")
                .build());

        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("https://lofi.e-hentai.org/index.php",
                proceeded.url().queryParameter("url"));
    }

    @Test
    public void testTierThreeRoutesLikeTierTwo() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(3);

        Request proceeded = proceed(new Request.Builder()
                .url("https://e-hentai.org/api.php")
                .build());

        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("https://e-hentai.org/api.php", proceeded.url().queryParameter("url"));
    }

    @Test
    public void testProxyTargetFollowsConfiguredProtocolAndPort() throws Exception {
        settings.saveConfig(new WebUiConfig("https", "10.0.0.5", 8443, "", ""));
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("http://e-hentai.org/tag/alpha")
                .build());

        assertEquals("https", proceeded.url().scheme());
        assertEquals("10.0.0.5", proceeded.url().host());
        assertEquals(8443, proceeded.url().port());
        assertEquals("http://e-hentai.org/tag/alpha", proceeded.url().queryParameter("url"));
    }

    // ------------------------------------------------------------------
    // Bearer attachment (auth-on servers must accept the paired request)
    // ------------------------------------------------------------------

    @Test
    public void testBearerAttachment() {
        // Helper-level: the pairing token travels as a Bearer header.
        Request.Builder withToken = new Request.Builder().url("https://e-hentai.org/");
        WebUiTier2ProxyInterceptor.attachBearer(withToken, "tok123");
        assertEquals("Bearer tok123", withToken.build().header("Authorization"));

        // Empty token (auth-off pairing) adds nothing.
        Request.Builder withoutToken = new Request.Builder().url("https://e-hentai.org/");
        WebUiTier2ProxyInterceptor.attachBearer(withoutToken, "");
        assertNull(withoutToken.build().header("Authorization"));
    }

    @Test
    public void testEmptyTokenOmitsAuthorizationHeader() throws Exception {
        // End-to-end through intercept(): the configured empty token yields
        // no Authorization header. (A non-empty token cannot round-trip
        // WebUiSettings.saveConfig under Robolectric — the Android KeStore
        // is unavailable there — so attachment of real tokens is covered by
        // testBearerAttachment above.)
        settings.saveConfig(SERVER); // token = ""
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://e-hentai.org/g/1001/aaa/")
                .build());

        assertNull(proceeded.header("Authorization"));
    }

    // ------------------------------------------------------------------
    // Referer / Origin header semantics (site values wrapped the same way)
    // ------------------------------------------------------------------

    @Test
    public void testSiteRefererAndOriginAreRewrittenToProxyForm() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://e-hentai.org/g/1001/aaa/")
                .header("Referer", "https://e-hentai.org/?f_search=alpha")
                .header("Origin", "https://upld.e-hentai.org")
                .build());

        // Structural (encoding-agnostic) assertions: the rewritten headers are
        // proxy URLs whose url param round-trips to the original site value.
        okhttp3.HttpUrl referer = okhttp3.HttpUrl.parse(proceeded.header("Referer"));
        assertEquals("http", referer.scheme());
        assertEquals("192.168.1.10", referer.host());
        assertEquals(8080, referer.port());
        assertEquals("/api/v1/site/proxy", referer.encodedPath());
        assertEquals("https://e-hentai.org/?f_search=alpha", referer.queryParameter("url"));

        okhttp3.HttpUrl origin = okhttp3.HttpUrl.parse(proceeded.header("Origin"));
        assertEquals("192.168.1.10", origin.host());
        assertEquals("/api/v1/site/proxy", origin.encodedPath());
        assertEquals("https://upld.e-hentai.org/", origin.queryParameter("url"));
    }

    @Test
    public void testExternalRefererAndOriginStayUntouched() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://e-hentai.org/g/1001/aaa/")
                .header("Referer", "https://example.com/start")
                .header("Origin", "https://example.com")
                .build());

        assertEquals("https://example.com/start", proceeded.header("Referer"));
        assertEquals("https://example.com", proceeded.header("Origin"));
    }

    @Test
    public void testAbsentRefererStaysAbsent() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://e-hentai.org/g/1001/aaa/")
                .build());

        assertNull(proceeded.header("Referer"));
        assertNull(proceeded.header("Origin"));
    }

    // ------------------------------------------------------------------
    // Runtime reactivity + routing predicate
    // ------------------------------------------------------------------

    @Test
    public void testDecisionIsReadPerRequest() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(1);
        Request direct = proceed(new Request.Builder().url("https://e-hentai.org/").build());
        assertEquals("https://e-hentai.org/", direct.url().toString());

        // Flip to Tier-2 without touching the interceptor or the client.
        settings.setClientTier(2);
        Request routed = proceed(new Request.Builder().url("https://e-hentai.org/").build());
        assertEquals("/api/v1/site/proxy", routed.url().encodedPath());
        assertEquals("https://e-hentai.org/", routed.url().queryParameter("url"));
    }

    // ------------------------------------------------------------------
    // Real-client reactivity (R4-16): SiteApplication builds each client
    // once and registers the interceptor at construction; a tier or pairing
    // flip made in-process afterwards must change routing on that SAME
    // client — no rebuild, no cold restart.
    // ------------------------------------------------------------------

    /** Requests as the terminal interceptor saw them, in call order. */
    private final List<Request> terminalRequests = new ArrayList<>();

    /**
     * One client for the whole scenario: the tier interceptor under test plus
     * a terminal interceptor that records the request instead of opening a
     * socket, so the full OkHttp call path runs without any network.
     */
    private OkHttpClient prebuiltClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(interceptor)
                .addInterceptor(chain -> {
                    terminalRequests.add(chain.request());
                    return new Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .body(ResponseBody.create(new byte[0], MediaType.get("text/html")))
                            .build();
                })
                .build();
    }

    /** Runs one call through the prebuilt client; returns the terminal request. */
    private Request proceedThroughClient(OkHttpClient client, String url) throws Exception {
        int index = terminalRequests.size();
        try (Response response = client.newCall(
                new Request.Builder().url(url).build()).execute()) {
            // Body is drained/closed by try-with-resources.
        }
        return terminalRequests.get(index);
    }

    @Test
    public void testTierFlipTakesEffectOnPrebuiltClientWithoutRebuild() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(1);
        OkHttpClient client = prebuiltClient();

        // Tier-1 passes the site request through untouched.
        Request direct = proceedThroughClient(client, "https://e-hentai.org/?f_search=alpha");
        assertEquals("https://e-hentai.org/?f_search=alpha", direct.url().toString());

        // In-process flip to Tier-2: the SAME client instance rewrites
        // immediately — no rebuild, no restart (R4-16).
        settings.setClientTier(2);
        Request routed = proceedThroughClient(client, "https://e-hentai.org/?f_search=alpha");
        assertEquals("/api/v1/site/proxy", routed.url().encodedPath());
        assertEquals("https://e-hentai.org/?f_search=alpha", routed.url().queryParameter("url"));

        // Flipping back down is just as immediate on the same client.
        settings.setClientTier(1);
        Request directAgain = proceedThroughClient(client, "https://e-hentai.org/?f_search=alpha");
        assertEquals("https://e-hentai.org/?f_search=alpha", directAgain.url().toString());
    }

    @Test
    public void testPairingAfterClientConstructionTakesEffectWithoutRebuild() throws Exception {
        settings.setClientTier(2);
        OkHttpClient client = prebuiltClient();

        // Unpaired Tier-2 degrades to direct on this client ...
        Request direct = proceedThroughClient(client, "https://e-hentai.org/g/1001/aaa/");
        assertEquals("https://e-hentai.org/g/1001/aaa/", direct.url().toString());

        // ... and pairing that happens AFTER the client was built still takes
        // effect per request on that same client — no rebuild (R4-16).
        settings.saveConfig(SERVER);
        Request routed = proceedThroughClient(client, "https://e-hentai.org/g/1001/aaa/");
        assertEquals("/api/v1/site/proxy", routed.url().encodedPath());
        assertEquals("https://e-hentai.org/g/1001/aaa/", routed.url().queryParameter("url"));
    }

    @Test
    public void testIsRoutingActiveMatrix() {
        assertFalse(WebUiTier2ProxyInterceptor.isRoutingActive(settings));

        settings.setClientTier(2);
        assertFalse("Tier-2 without a paired server does not route",
                WebUiTier2ProxyInterceptor.isRoutingActive(settings));

        settings.saveConfig(SERVER);
        assertTrue(WebUiTier2ProxyInterceptor.isRoutingActive(settings));

        settings.setClientTier(1);
        assertFalse(WebUiTier2ProxyInterceptor.isRoutingActive(settings));

        settings.setClientTier(3);
        assertTrue(WebUiTier2ProxyInterceptor.isRoutingActive(settings));
    }

    // ------------------------------------------------------------------
    // Server-outage resilience: unreachable proxy falls back to direct
    // ------------------------------------------------------------------

    private FakeChain fakeChain(Request request) throws Exception {
        FakeChain chain = new FakeChain(request);
        try (Response response = interceptor.intercept(chain)) {
            // Response drained/closed here; assertion reads chain.proceededAll.
        }
        return chain;
    }

    @Test
    public void testUnreachableServerRetriesOnceDirectly() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        FakeChain chain = new FakeChain(request);
        chain.failCount = 1; // first (proxy) attempt throws, second succeeds

        try (Response response = interceptor.intercept(chain)) {
            assertEquals(200, response.code());
        }

        assertEquals("proxy attempt then direct retry",
                2, chain.proceededAll.size());
        assertEquals("/api/v1/site/proxy", chain.proceededAll.get(0).url().encodedPath());
        assertEquals("https://e-hentai.org/g/1001/aaa/",
                chain.proceededAll.get(1).url().toString());
    }

    @Test
    public void testDegradeWindowBypassesProxyUntilItExpires() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();

        // First request: proxy attempt fails (server down) → direct retry.
        FakeChain first = new FakeChain(request);
        first.failCount = 1;
        try (Response ignored = interceptor.intercept(first)) {
        }
        assertEquals(2, first.proceededAll.size());

        // Second request inside the degrade window: straight to direct, no
        // proxy attempt at all.
        FakeChain second = new FakeChain(request);
        try (Response response = interceptor.intercept(second)) {
            assertEquals(200, response.code());
        }
        assertEquals("direct only, no proxy attempt during the window",
                1, second.proceededAll.size());
        assertEquals("https://e-hentai.org/g/1001/aaa/",
                second.proceededAll.get(0).url().toString());
    }

    @Test
    public void testDegradeWindowExpiresAndRoutesThroughProxyAgain() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();

        // Trigger a degrade (server down once).
        FakeChain failing = new FakeChain(request);
        failing.failCount = 1;
        try (Response ignored = interceptor.intercept(failing)) {
        }

        // Expire the window by moving the degraded timestamp into the past.
        try {
            java.lang.reflect.Field field =
                    WebUiTier2ProxyInterceptor.class.getDeclaredField("mDegradedUntil");
            field.setAccessible(true);
            ((AtomicLong) field.get(interceptor))
                    .set(System.currentTimeMillis() - 1_000L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }

        // Proxy is retried again: a single proxied request, no direct fallback.
        FakeChain healthy = new FakeChain(request);
        try (Response response = interceptor.intercept(healthy)) {
            assertEquals(200, response.code());
        }
        assertEquals(1, healthy.proceededAll.size());
        assertEquals("/api/v1/site/proxy", healthy.proceededAll.get(0).url().encodedPath());
    }

    @Test
    public void testBadGatewayFromProxyFallsBackToDirect() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        FakeChain chain = new FakeChain(request);
        chain.badGatewayFirst = 1; // first (proxy) attempt answers 502, direct succeeds

        try (Response response = interceptor.intercept(chain)) {
            assertEquals("direct retry must win over the 502",
                    200, response.code());
        }

        assertEquals(2, chain.proceededAll.size());
        assertEquals("/api/v1/site/proxy", chain.proceededAll.get(0).url().encodedPath());
        assertEquals("https://e-hentai.org/g/1001/aaa/",
                chain.proceededAll.get(1).url().toString());
    }

    @Test
    public void testHealthyProxyPassesThroughWithoutFallback() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        FakeChain chain = new FakeChain(request);

        try (Response response = interceptor.intercept(chain)) {
            assertEquals(200, response.code());
        }

        assertEquals("single proxied request, no retry",
                1, chain.proceededAll.size());
        assertEquals("/api/v1/site/proxy", chain.proceededAll.get(0).url().encodedPath());
    }

    @Test
    public void testHttpErrorsOtherThan502PassThrough() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);
        Request request = new Request.Builder().url("https://e-hentai.org/g/1001/aaa/").build();
        FakeChain chain = new FakeChain(request);
        chain.responseCode = 404; // EH-side response, transparently passed back

        try (Response response = interceptor.intercept(chain)) {
            assertEquals(404, response.code());
        }

        assertEquals("site-side errors must not trigger the direct fallback",
                1, chain.proceededAll.size());
    }
}
