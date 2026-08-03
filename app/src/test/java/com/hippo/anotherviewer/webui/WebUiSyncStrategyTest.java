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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Client-side behavior matrix for the Wave-2 conflict strategies
 * (contract v2 §3.8 / MASTER §4.2): soft-entity tombstone honoring per
 * strategy, tombstone-entity propagation, legacy (no-policy) fallback and the
 * D2 push-carried policy.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncStrategyTest {

    private static final String ANDROID = "android-00000000-0000-0000-0000-000000000001";
    private static final String WEB = "web-browser-1";
    private static final String ANDROID_OTHER = "android-00000000-0000-0000-0000-000000000002";

    private WebUiConfig config;
    private InMemoryWebUiSyncStore store;
    private InMemorySyncServer server;
    private WebUiSyncEngine engine;

    @Before
    public void setUp() {
        config = new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
        store = new InMemoryWebUiSyncStore();
        server = new InMemorySyncServer();
        engine = new WebUiSyncEngine(store, server);
    }

    @After
    public void tearDown() {
        WebUiSyncEngine.setPolicySource(null);
    }

    private static WebUiSyncModels.SyncPolicy policy(String strategy) {
        WebUiSyncModels.SyncPolicy p = new WebUiSyncModels.SyncPolicy();
        p.conflictStrategy = strategy;
        return p;
    }

    private static LocalFavoriteInfo favorite(long gid) {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "fav " + gid;
        info.time = 1000;
        info.pages = 5;
        info.spanSize = 1;
        return info;
    }

    private void seedWebFavoriteTombstone(long gid) {
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.favorites.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedAndroidFavoriteTombstone(long gid) {
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = ANDROID_OTHER;
        tomb.deleted = true;
        server.favorites.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    @Test
    public void honorSoftTombstoneMatrix() {
        // B: everything propagates.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.LWW, WEB, ANDROID, true));
        // A: priority (android) deletion propagates...
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, ANDROID_OTHER, ANDROID, true));
        // ...but a non-priority (web) deletion does not remove the priority live copy...
        assertFalse(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, WEB, ANDROID, true));
        // ...and does propagate when the priority platform holds nothing.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, WEB, ANDROID, false));
        // C: web deletion propagates; android-vs-android (both non-priority) propagates.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY, WEB, ANDROID, true));
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY, ANDROID_OTHER, ANDROID, true));
    }

    @Test
    public void devicePriority_webTombstone_localFavoriteRetained() throws IOException {
        server.policy = policy("device_priority");
        store.putLocalFavorite(favorite(7));
        seedWebFavoriteTombstone(7);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull("priority platform retains its live copy", store.loadLocalFavorite(7));
    }

    @Test
    public void devicePriority_androidTombstone_localFavoriteDeleted() throws IOException {
        server.policy = policy("device_priority");
        store.putLocalFavorite(favorite(8));
        seedAndroidFavoriteTombstone(8);

        engine.syncInternal(config, ANDROID, 0);

        assertNull(store.loadLocalFavorite(8));
    }

    @Test
    public void lww_webTombstone_localFavoriteResurrects() throws IOException {
        // Matrix §4.2 B: union — the deleting side is resurrected next round.
        server.policy = policy("lww");
        store.putLocalFavorite(favorite(9));
        seedWebFavoriteTombstone(9);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(store.loadLocalFavorite(9));
    }

    @Test
    public void webPriority_webTombstone_localFavoriteDeleted() throws IOException {
        server.policy = policy("web_priority");
        store.putLocalFavorite(favorite(10));
        seedWebFavoriteTombstone(10);

        engine.syncInternal(config, ANDROID, 0);

        assertNull(store.loadLocalFavorite(10));
    }

    @Test
    public void noPolicy_legacyServer_unionResurrection() throws IOException {
        // Legacy servers (no policy) behave as v1 union: live resurrects.
        server.policy = null;
        store.putLocalFavorite(favorite(11));
        seedWebFavoriteTombstone(11);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(store.loadLocalFavorite(11));
    }

    @Test
    public void tombstoneEntity_historyDeletionPropagatesUnderDevicePriority() throws IOException {
        server.policy = policy("device_priority");
        HistoryInfo local = new HistoryInfo();
        local.gid = 21;
        local.token = "tok21";
        local.time = 1000;
        store.applySyncedHistory(local);

        WebUiSyncModels.SyncHistory tomb = new WebUiSyncModels.SyncHistory();
        tomb.gid = 21;
        tomb.token = "tok21";
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.history.put(21L, new InMemorySyncServer.Record(1, true, tomb));

        engine.syncInternal(config, ANDROID, 0);

        assertTrue("history tombstones propagate under any strategy",
                store.getAllHistoryForSync().isEmpty());
    }

    @Test
    public void pushCarriesDevicePolicy() throws IOException {
        WebUiSyncModels.SyncPolicy mine = new WebUiSyncModels.SyncPolicy();
        mine.conflictStrategy = "web_priority";
        mine.clientTier = 2;
        mine.autoSyncIntervalSec = 300;
        WebUiSyncEngine.setPolicySource(() -> mine);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(server.lastPushPolicy);
        assertEquals("web_priority", server.lastPushPolicy.conflictStrategy);
        assertEquals(2, server.lastPushPolicy.clientTier);
        assertEquals(300, server.lastPushPolicy.autoSyncIntervalSec);
    }

    @Test
    public void pushCarriesContractDefaultsWithoutSource() throws IOException {
        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(server.lastPushPolicy);
        assertEquals("device_priority", server.lastPushPolicy.conflictStrategy);
        assertEquals(1, server.lastPushPolicy.clientTier);
        assertEquals(900, server.lastPushPolicy.autoSyncIntervalSec);
    }

    @Test
    public void autoSyncPeriodicDelay() {
        assertEquals(-1L, WebUiAutoSyncScheduler.periodicDelayMs(0));
        assertEquals(900_000L, WebUiAutoSyncScheduler.periodicDelayMs(900));
    }

    @Test
    public void platformParsing() {
        assertEquals("android", WebUiSyncEngine.platformOf(ANDROID));
        assertEquals("web", WebUiSyncEngine.platformOf(WEB));
        assertEquals("", WebUiSyncEngine.platformOf(null));
    }
}
