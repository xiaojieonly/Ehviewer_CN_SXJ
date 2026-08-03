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
 * Routing oracle for the Tier-2 browsing proxy (ADR-0003 D3, MASTER §4.1
 * acceptance ⑤ "Tier-2 走服务器"): site-host requests are rewritten to the
 * paired server only when {@code clientTier >= 2} AND a server is configured;
 * Tier-0/1 traffic and every non-site host pass through byte-identical.
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
                    .body(ResponseBody.create(MediaType.parse("text/html"), new byte[0]))
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

    // ------------------------------------------------------------------
    // Tier-2 rewriting
    // ------------------------------------------------------------------

    @Test
    public void testTierTwoRewritesSiteHostToPairedServer() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/g/1001/aaa/?p=1")
                .build());

        assertEquals("path and query survive the rewrite",
                "http://192.168.1.10:8080/g/1001/aaa/?p=1",
                proceeded.url().toString());
    }

    @Test
    public void testTierTwoRewritesSubdomainHosts() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://lofi.gallery.test/index.php")
                .build());

        assertEquals("http://192.168.1.10:8080/index.php", proceeded.url().toString());
    }

    @Test
    public void testTierThreeRoutesLikeTierTwo() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(3);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/api.php")
                .build());

        assertEquals("http://192.168.1.10:8080/api.php", proceeded.url().toString());
    }

    @Test
    public void testRewriteFollowsConfiguredProtocolAndPort() throws Exception {
        settings.saveConfig(new WebUiConfig("https", "10.0.0.5", 8443, "", ""));
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("http://gallery.test/tag/alpha")
                .build());

        assertEquals("https://10.0.0.5:8443/tag/alpha", proceeded.url().toString());
    }

    // ------------------------------------------------------------------
    // Referer / Origin header rewriting (mirrors the mock interceptor)
    // ------------------------------------------------------------------

    @Test
    public void testSiteRefererAndOriginAreRewritten() throws Exception {
        settings.saveConfig(SERVER);
        settings.setClientTier(2);

        Request proceeded = proceed(new Request.Builder()
                .url("https://gallery.test/g/1001/aaa/")
                .header("Referer", "https://gallery.test/?f_search=alpha")
                .header("Origin", "https://upld.gallery.test")
                .build());

        assertEquals("http://192.168.1.10:8080/?f_search=alpha",
                proceeded.header("Referer"));
        // HttpUrl normalization keeps the root path, hence the trailing slash.
        assertEquals("http://192.168.1.10:8080/", proceeded.header("Origin"));
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
        assertEquals("http://192.168.1.10:8080/", routed.url().toString());
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
