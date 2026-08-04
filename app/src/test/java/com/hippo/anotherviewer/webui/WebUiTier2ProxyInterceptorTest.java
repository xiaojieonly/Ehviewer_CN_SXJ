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

import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.Interceptor;
import okhttp3.MediaType;
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
        Request proceeded;

        FakeChain(Request original) {
            this.original = original;
        }

        @Override
        public Request request() {
            return original;
        }

        @Override
        public Response proceed(Request request) {
            proceeded = request;
            return new Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
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
                .url("https://gallery.test/?f_search=alpha&page=2")
                .header("Referer", "https://gallery.test/")
                .build();
        Request proceeded = proceed(request);

        assertSame("Tier-1 must forward the original request object", request, proceeded);
        assertEquals("https://gallery.test/?f_search=alpha&page=2",
                proceeded.url().toString());
    }

    @Test
    public void testTierZeroPassesSiteTrafficThroughUntouched() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(0);

        Request request = new Request.Builder().url("https://gallery.test/g/1001/aaa/").build();
        assertSame(request, proceed(request));
    }

    @Test
    public void testTierTwoWithoutConfiguredServerDegradesToDirect() throws Exception {
        settings.setClientTier(2);
        assertFalse(settings.isConfigured());

        Request request = new Request.Builder().url("https://gallery.test/g/1001/aaa/").build();
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
                .url("https://gallery.test/g/1001/aaa/?p=1")
                .build());

        assertEquals("http", proceeded.url().scheme());
        assertEquals("192.168.1.10", proceeded.url().host());
        assertEquals(8080, proceeded.url().port());
        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("the original URL travels as the url param, path/query intact",
                "https://gallery.test/g/1001/aaa/?p=1",
                proceeded.url().queryParameter("url"));
    }

    @Test
    public void testUrlParamEncodingSurvivesReservedCharacters() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        // '&' and '=' in the original query must not leak into the proxy
        // request's own query structure: exactly one param, round-trip intact.
        String original = "https://gallery.test/?f_search=a+b&f_cats=2&advsearch=1";
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
                .url("https://lofi.gallery.test/index.php")
                .build());

        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("https://lofi.gallery.test/index.php",
                proceeded.url().queryParameter("url"));
    }

    @Test
    public void testTierThreeRoutesLikeTierTwo() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(3);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/api.php")
                .build());

        assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        assertEquals("https://gallery.test/api.php", proceeded.url().queryParameter("url"));
    }

    @Test
    public void testProxyTargetFollowsConfiguredProtocolAndPort() throws Exception {
        settings.saveConfig(new WebUiConfig("https", "10.0.0.5", 8443, "", ""));
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("http://gallery.test/tag/alpha")
                .build());

        assertEquals("https", proceeded.url().scheme());
        assertEquals("10.0.0.5", proceeded.url().host());
        assertEquals(8443, proceeded.url().port());
        assertEquals("http://gallery.test/tag/alpha", proceeded.url().queryParameter("url"));
    }

    // ------------------------------------------------------------------
    // Bearer attachment (auth-on servers must accept the paired request)
    // ------------------------------------------------------------------

    @Test
    public void testBearerAttachment() {
        // Helper-level: the pairing token travels as a Bearer header.
        Request.Builder withToken = new Request.Builder().url("https://gallery.test/");
        WebUiTier2ProxyInterceptor.attachBearer(withToken, "tok123");
        assertEquals("Bearer tok123", withToken.build().header("Authorization"));

        // Empty token (auth-off pairing) adds nothing.
        Request.Builder withoutToken = new Request.Builder().url("https://gallery.test/");
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
                .url("https://gallery.test/g/1001/aaa/")
                .build());

        assertNull(proceeded.header("Authorization"));
    }

    // ------------------------------------------------------------------
    // Mock-debug yield gate (explicit, not just interceptor ordering)
    // ------------------------------------------------------------------

    @Test
    public void testMockDebugYieldGate() throws Exception {
        // In the unit-test variant MOCK_EH_BASE_URL is empty, so the explicit
        // yield gate is OPEN and routing behaves normally. When the debug
        // mock base is set (debug builds), intercept() yields before any
        // routing decision — verified here by the invariant that the gate
        // condition and the routing outcome stay consistent.
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/")
                .build());

        if (com.hippo.anotherviewer.BuildConfig.MOCK_EH_BASE_URL.isEmpty()) {
            assertEquals("/api/v1/site/proxy", proceeded.url().encodedPath());
        } else {
            assertEquals("mock debug active must yield to the mock interceptor",
                    "https://gallery.test/", proceeded.url().toString());
        }
    }

    // ------------------------------------------------------------------
    // Referer / Origin header semantics (site values wrapped the same way)
    // ------------------------------------------------------------------

    @Test
    public void testSiteRefererAndOriginAreRewrittenToProxyForm() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/g/1001/aaa/")
                .header("Referer", "https://gallery.test/?f_search=alpha")
                .header("Origin", "https://upld.gallery.test")
                .build());

        // Structural (encoding-agnostic) assertions: the rewritten headers are
        // proxy URLs whose url param round-trips to the original site value.
        okhttp3.HttpUrl referer = okhttp3.HttpUrl.parse(proceeded.header("Referer"));
        assertEquals("http", referer.scheme());
        assertEquals("192.168.1.10", referer.host());
        assertEquals(8080, referer.port());
        assertEquals("/api/v1/site/proxy", referer.encodedPath());
        assertEquals("https://gallery.test/?f_search=alpha", referer.queryParameter("url"));

        okhttp3.HttpUrl origin = okhttp3.HttpUrl.parse(proceeded.header("Origin"));
        assertEquals("192.168.1.10", origin.host());
        assertEquals("/api/v1/site/proxy", origin.encodedPath());
        assertEquals("https://upld.gallery.test/", origin.queryParameter("url"));
    }

    @Test
    public void testExternalRefererAndOriginStayUntouched() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/g/1001/aaa/")
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
                .url("https://gallery.test/g/1001/aaa/")
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
        Request direct = proceed(new Request.Builder().url("https://gallery.test/").build());
        assertEquals("https://gallery.test/", direct.url().toString());

        // Flip to Tier-2 without touching the interceptor or the client.
        settings.setClientTier(2);
        Request routed = proceed(new Request.Builder().url("https://gallery.test/").build());
        assertEquals("/api/v1/site/proxy", routed.url().encodedPath());
        assertEquals("https://gallery.test/", routed.url().queryParameter("url"));
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
}
