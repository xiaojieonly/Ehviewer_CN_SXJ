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

package com.hippo.anotherviewer.gallery;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.webui.WebUiConfig;
import com.hippo.lib.glgallery.GalleryProvider;
import com.hippo.lib.glview.view.GLRoot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowToast;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-outage resilience of the remote reader (same semantics as the Tier-2
 * browsing proxy): when the paired server cannot serve a gallery or page, the
 * provider hands over to a direct-EH {@link SiteGalleryProvider} for a 60s
 * degrade window, delegates size/error/start-page while degraded, dispatches
 * the failed page through the fallback, and clears back to remote reading
 * once a server request succeeds again.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiGalleryProviderFallbackTest {

    private static final GalleryInfo GALLERY = new GalleryInfo();

    static {
        GALLERY.gid = 1001;
        GALLERY.token = "tok123";
    }

    private static final WebUiConfig SERVER =
            new WebUiConfig("http", "192.168.1.10", 8080, "", "");

    /** Recording stand-in for the direct-EH provider; no SpiderQueen involved. */
    private static final class FakeFallback extends SiteGalleryProvider {
        boolean started;
        boolean stopped;
        int size = 5;
        String error = "EH unreachable";
        final List<Integer> requested = new ArrayList<>();
        final List<Integer> forceRequested = new ArrayList<>();
        Listener listener;
        GLRoot glRoot;
        int startPage = 3;

        FakeFallback(Context context, GalleryInfo galleryInfo) {
            super(context, galleryInfo);
        }

        @Override
        public void start() {
            started = true;
        }

        @Override
        public void stop() {
            stopped = true;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public String getError() {
            return error;
        }

        @Override
        public int getStartPage() {
            return startPage;
        }

        @Override
        public void putStartPage(int page) {
            startPage = page;
        }

        @Override
        public void setListener(Listener listener) {
            this.listener = listener;
        }

        @Override
        public void setGLRoot(GLRoot glRoot) {
            this.glRoot = glRoot;
        }

        @Override
        protected void onRequest(int index) {
            requested.add(index);
        }

        @Override
        protected void onForceRequest(int index) {
            forceRequested.add(index);
        }

        @Override
        protected void onCancelRequest(int index) {
        }
    }

    /** The provider under test, with the fallback factory swapped for a fake. */
    private static final class TestProvider extends WebUiGalleryProvider {
        final FakeFallback fake;

        TestProvider(Context context, GalleryInfo galleryInfo, WebUiConfig config,
                FakeFallback fake) {
            super(context, galleryInfo, config);
            this.fake = fake;
        }

        @Override
        SiteGalleryProvider createFallbackProvider() {
            return fake;
        }
    }

    private FakeFallback fake;
    private TestProvider provider;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
        fake = new FakeFallback(context, GALLERY);
        provider = new TestProvider(context, GALLERY, SERVER, fake);
        setStarted(provider, true);
    }

    /** Marks the provider started without touching the network (the real
     *  start() would launch a server fetch against the outside world). */
    private static void setStarted(WebUiGalleryProvider provider, boolean started) {
        try {
            Field field = WebUiGalleryProvider.class.getDeclaredField("mStarted");
            field.setAccessible(true);
            field.setBoolean(provider, started);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /** Moves the degraded-until timestamp into the past so the server is tried again. */
    private void expireDegradeWindow() {
        try {
            Field field = WebUiGalleryProvider.class.getDeclaredField("mDegradedUntil");
            field.setAccessible(true);
            ((AtomicLong) field.get(provider)).set(System.currentTimeMillis() - 1_000L);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private GLRoot stubGLRoot() {
        return (GLRoot) Proxy.newProxyInstance(
                GLRoot.class.getClassLoader(), new Class<?>[]{GLRoot.class},
                (proxy, method, args) -> null);
    }

    // ------------------------------------------------------------------
    // Fallback creation + wiring
    // ------------------------------------------------------------------

    @Test
    public void testRemoteFailureCreatesStartedFallbackWithReaderWiring() {
        GalleryProvider.Listener listener = new RecordingListener();
        GLRoot glRoot = stubGLRoot();
        provider.setListener(listener);
        provider.setGLRoot(glRoot);

        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        assertTrue("must be degraded after a server failure", provider.isDegraded());
        assertSame("the direct-EH fallback must take over", fake, provider.mFallback);
        assertTrue("fallback must be started", fake.started);
        assertSame("reader notifications must reach the fallback", listener, fake.listener);
        assertSame("fallback renders through the same GL root", glRoot, fake.glRoot);
    }

    @Test
    public void testFallbackCreatedOnlyOnce() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();
        assertNotNull(provider.mFallback);

        // A second failure inside the same outage must not create another one.
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();
        assertSame("one fallback per outage", fake, provider.mFallback);
    }

    @Test
    public void testListenerSetAfterDegradeIsForwarded() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        GalleryProvider.Listener listener = new RecordingListener();
        provider.setListener(listener);
        assertSame("late listener must still reach the fallback", listener, fake.listener);
    }

    // ------------------------------------------------------------------
    // Degrade window + page hand-over
    // ------------------------------------------------------------------

    @Test
    public void testFailedPageIsRetriedThroughTheFallback() {
        provider.fallbackPage(3);
        ShadowLooper.idleMainLooper();

        assertTrue(provider.isDegraded());
        assertEquals("the failed page must be re-requested via direct EH",
                List.of(3), fake.requested);
    }

    @Test
    public void testFailedPageRetriedWhenAlreadyDegraded() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        // A second page fails inside the window: re-armed, retried via the
        // existing fallback, no second toast.
        ShadowToast.reset();
        provider.fallbackPage(7);
        ShadowLooper.idleMainLooper();

        assertEquals(List.of(7), fake.requested);
        assertNull("no duplicate degrade toast for the same outage",
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testDegradeShowsToast() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        assertNotNull("degrade toast must be shown",
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testWindowExpiryStopsDegrading() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();
        assertTrue(provider.isDegraded());

        expireDegradeWindow();
        assertFalse("server must be tried again once the window expires",
                provider.isDegraded());
    }

    // ------------------------------------------------------------------
    // Size / error / start-page delegation while degraded
    // ------------------------------------------------------------------

    @Test
    public void testSizeAndErrorDelegateToFallbackWhileDegraded() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        assertEquals("page count must come from direct EH", 5, provider.size());
        assertEquals("errors must come from direct EH", "EH unreachable", provider.getError());
        assertEquals("start page must come from direct EH", 3, provider.getStartPage());

        provider.putStartPage(9);
        assertEquals(9, fake.startPage);
    }

    @Test
    public void testRequestsRouteToFallbackWhileDegraded() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        provider.request(0);
        assertEquals(List.of(0), fake.requested);

        provider.forceRequest(1);
        assertEquals(List.of(1), fake.forceRequested);

        provider.cancelRequest(0);
    }

    @Test
    public void testRequestsStayRemoteWhileHealthy() {
        // Not degraded: no fallback traffic at all.
        provider.request(2);
        ShadowLooper.idleMainLooper();
        assertTrue("healthy provider must not touch the fallback",
                fake.requested.isEmpty());
    }

    // ------------------------------------------------------------------
    // Recovery
    // ------------------------------------------------------------------

    @Test
    public void testClearDegradedReleasesFallbackAndShowsRecoveryToast() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();
        assertTrue(provider.isDegraded());

        ShadowToast.reset();
        provider.clearDegraded();
        ShadowLooper.idleMainLooper();

        assertFalse("a successful server fetch must leave degraded mode",
                provider.isDegraded());
        assertNull("the fallback must be released", provider.mFallback);
        assertTrue("the fallback must be stopped", fake.stopped);
        assertNotNull("recovery toast must be shown",
                ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testClearDegradedIsIdempotent() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();

        provider.clearDegraded();
        ShadowLooper.idleMainLooper();
        ShadowToast.reset();

        // A second successful fetch must not re-announce or re-stop anything.
        provider.clearDegraded();
        ShadowLooper.idleMainLooper();
        assertNull(ShadowToast.getTextOfLatestToast());
    }

    @Test
    public void testStopReleasesTheFallback() {
        provider.enterFallbackMode();
        ShadowLooper.idleMainLooper();
        assertNotNull(provider.mFallback);

        provider.stop();
        assertNull("stop() must release the fallback", provider.mFallback);
        assertTrue(fake.stopped);
    }

    /** Minimal no-op listener for wiring assertions. */
    private static final class RecordingListener implements GalleryProvider.Listener {
        @Override
        public void onDataChanged() {
        }

        @Override
        public void onPageWait(int index) {
        }

        @Override
        public void onPagePercent(int index, float percent) {
        }

        @Override
        public void onPageSucceed(int index,
                com.hippo.lib.glview.image.ImageWrapper image) {
        }

        @Override
        public void onPageFailed(int index, String error) {
        }

        @Override
        public void onDataChanged(int index) {
        }
    }
}
