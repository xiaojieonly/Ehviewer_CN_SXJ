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

import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Client-side §3.8 behavior matrix (contract v2 §3.8 / MASTER §4.2), driven
 * through the full {@link WebUiSyncEngine} push → pull → apply cycle against
 * the §3.8-aware {@link InMemorySyncServer}. The device under test is always
 * the android app ({@link #ANDROID}); the "other" platform is web.
 *
 * <p>Coverage: priority-platform soft-delete propagation across all five soft
 * entities, tombstone-entity propagation under every strategy, double-alive
 * (§3.8 same-key edit) convergence incl. the unconditional-priority case,
 * disjoint adds (union), each-deletes-different for both entity classes, and
 * the same-platform LWW fallback (§1.4).
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncClientMatrixTest {

    /** The device under test — the android app running the sync engine. */
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

    private void setStrategy(String strategy) {
        server.policy = policy(strategy);
    }

    private static LocalFavoriteInfo favorite(long gid, long time, String title) {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = title;
        info.time = time;
        info.pages = 5;
        return info;
    }

    private static HistoryInfo history(long gid, long time) {
        HistoryInfo info = new HistoryInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "history " + gid;
        info.mode = 1;
        info.time = time;
        return info;
    }

    private void seedFavoriteTombstone(long gid, String deviceId) {
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = deviceId;
        tomb.deleted = true;
        server.favorites.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedDownloadTombstone(long gid, String deviceId) {
        WebUiSyncModels.SyncDownload tomb = new WebUiSyncModels.SyncDownload();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = deviceId;
        tomb.deleted = true;
        server.downloads.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedFilterTombstone(int mode, String text, String deviceId) {
        WebUiSyncModels.SyncFilter tomb = new WebUiSyncModels.SyncFilter();
        tomb.mode = mode;
        tomb.text = text;
        tomb.lastModified = 5000;
        tomb.deviceId = deviceId;
        tomb.deleted = true;
        server.filters.put(mode + "|" + text, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedQuickSearchTombstone(String name, String deviceId) {
        WebUiSyncModels.SyncQuickSearch tomb = new WebUiSyncModels.SyncQuickSearch();
        tomb.name = name;
        tomb.lastModified = 5000;
        tomb.deviceId = deviceId;
        tomb.deleted = true;
        server.quickSearches.put(name, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedDownloadLabelTombstone(String label, String deviceId) {
        WebUiSyncModels.SyncDownloadLabel tomb = new WebUiSyncModels.SyncDownloadLabel();
        tomb.label = label;
        tomb.lastModified = 5000;
        tomb.deviceId = deviceId;
        tomb.deleted = true;
        server.downloadLabels.put(label, new InMemorySyncServer.Record(1, true, tomb));
    }

    // ==================== aliveRecordWins unit matrix (§3.8 double-alive) ====================

    @Test
    public void aliveRecordWinsMatrix() {
        WebUiSyncEngine.ConflictStrategy A = WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY;
        WebUiSyncEngine.ConflictStrategy B = WebUiSyncEngine.ConflictStrategy.LWW;
        WebUiSyncEngine.ConflictStrategy C = WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY;

        // B: pure last-write-wins, platform-agnostic.
        assertTrue(WebUiSyncEngine.aliveRecordWins(B, WEB, ANDROID, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(B, WEB, ANDROID, false));
        assertTrue(WebUiSyncEngine.aliveRecordWins(B, ANDROID_OTHER, ANDROID, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(B, ANDROID_OTHER, ANDROID, false));

        // A (android priority), cross-platform: the android record wins
        // unconditionally — even when it is the older incoming record.
        assertTrue(WebUiSyncEngine.aliveRecordWins(A, ANDROID, WEB, false));
        assertTrue(WebUiSyncEngine.aliveRecordWins(A, ANDROID, WEB, true));
        // ...and a web incoming never displaces the locally-held android copy.
        assertFalse(WebUiSyncEngine.aliveRecordWins(A, WEB, ANDROID, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(A, WEB, ANDROID, false));

        // C (web priority), cross-platform: the web record wins
        // unconditionally — even when it is the older incoming record.
        assertTrue(WebUiSyncEngine.aliveRecordWins(C, WEB, ANDROID, false));
        assertTrue(WebUiSyncEngine.aliveRecordWins(C, WEB, ANDROID, true));
        // ...and an android incoming never displaces the locally-held web copy.
        assertFalse(WebUiSyncEngine.aliveRecordWins(C, ANDROID, WEB, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(C, ANDROID, WEB, false));

        // Same platform always falls back to LWW (§1.4), under A and C alike.
        assertTrue(WebUiSyncEngine.aliveRecordWins(A, ANDROID_OTHER, ANDROID, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(A, ANDROID_OTHER, ANDROID, false));
        assertTrue(WebUiSyncEngine.aliveRecordWins(C, ANDROID_OTHER, ANDROID, true));
        assertFalse(WebUiSyncEngine.aliveRecordWins(C, ANDROID_OTHER, ANDROID, false));
    }

    // ==================== priority-platform soft delete propagates (all 5 soft entities) ====================
    // Under A the priority platform is android (a second android device's delete
    // reaches this device); under C the priority platform is web. In both cases
    // the soft tombstone must override the locally-alive record.

    @Test
    public void devicePriority_androidDeletePropagates_allSoftEntities() throws IOException {
        setStrategy("device_priority");
        store.putLocalFavorite(favorite(11, 1000, "fav"));
        store.putDownloadInfo(download(12, 1000));
        store.addFilter(filter(1, "f"));
        store.insertQuickSearch(quickSearch("qs"));
        store.addDownloadLabel(label("lbl"));

        seedFavoriteTombstone(11, ANDROID_OTHER);
        seedDownloadTombstone(12, ANDROID_OTHER);
        seedFilterTombstone(1, "f", ANDROID_OTHER);
        seedQuickSearchTombstone("qs", ANDROID_OTHER);
        seedDownloadLabelTombstone("lbl", ANDROID_OTHER);

        engine.syncInternal(config, ANDROID, 0);

        assertNull("favorite: priority delete propagates", store.loadLocalFavorite(11));
        assertTrue("download: priority delete propagates", store.getAllDownloadInfo().isEmpty());
        assertNull("filter: priority delete propagates", store.findFilterByKey(1, "f"));
        assertTrue("quickSearch: priority delete propagates", store.getAllQuickSearch().isEmpty());
        assertTrue("downloadLabel: priority delete propagates", store.getAllDownloadLabelList().isEmpty());
    }

    @Test
    public void webPriority_webDeletePropagates_allSoftEntities() throws IOException {
        setStrategy("web_priority");
        store.putLocalFavorite(favorite(21, 1000, "fav"));
        store.putDownloadInfo(download(22, 1000));
        store.addFilter(filter(2, "f"));
        store.insertQuickSearch(quickSearch("qs"));
        store.addDownloadLabel(label("lbl"));

        seedFavoriteTombstone(21, WEB);
        seedDownloadTombstone(22, WEB);
        seedFilterTombstone(2, "f", WEB);
        seedQuickSearchTombstone("qs", WEB);
        seedDownloadLabelTombstone("lbl", WEB);

        engine.syncInternal(config, ANDROID, 0);

        assertNull("favorite: web (priority) delete propagates", store.loadLocalFavorite(21));
        assertTrue("download: web (priority) delete propagates", store.getAllDownloadInfo().isEmpty());
        assertNull("filter: web (priority) delete propagates", store.findFilterByKey(2, "f"));
        assertTrue("quickSearch: web (priority) delete propagates", store.getAllQuickSearch().isEmpty());
        assertTrue("downloadLabel: web (priority) delete propagates", store.getAllDownloadLabelList().isEmpty());
    }

    // ==================== tombstone entities propagate under every strategy ====================

    @Test
    public void tombstoneEntities_historyBookmarkPropagate_allStrategies() throws IOException {
        for (String strategy : new String[] {"device_priority", "lww", "web_priority"}) {
            InMemoryWebUiSyncStore localStore = new InMemoryWebUiSyncStore();
            InMemorySyncServer syncServer = new InMemorySyncServer();
            syncServer.policy = policy(strategy);
            WebUiSyncEngine syncEngine = new WebUiSyncEngine(localStore, syncServer);

            localStore.applySyncedHistory(history(31, 1000));
            BookmarkInfo bm = new BookmarkInfo();
            bm.gid = 32;
            bm.token = "tok32";
            bm.page = 3;
            bm.time = 1000;
            localStore.putBookmark(bm);

            // Even a web (non-priority under device_priority) deletion propagates
            // for tombstone entities — §3.8: history/bookmark deletes always propagate.
            WebUiSyncModels.SyncHistory histTomb = new WebUiSyncModels.SyncHistory();
            histTomb.gid = 31;
            histTomb.token = "tok31";
            histTomb.lastModified = 5000;
            histTomb.deviceId = WEB;
            histTomb.deleted = true;
            syncServer.history.put(31L, new InMemorySyncServer.Record(1, true, histTomb));

            WebUiSyncModels.SyncBookmark bmTomb = new WebUiSyncModels.SyncBookmark();
            bmTomb.gid = 32;
            bmTomb.token = "tok32";
            bmTomb.lastModified = 5000;
            bmTomb.deviceId = WEB;
            bmTomb.deleted = true;
            syncServer.bookmarks.put(32L, new InMemorySyncServer.Record(1, true, bmTomb));

            syncEngine.syncInternal(config, ANDROID, 0);

            assertTrue("[" + strategy + "] history tombstone propagates",
                    localStore.getAllHistoryForSync().isEmpty());
            assertTrue("[" + strategy + "] bookmark tombstone propagates",
                    localStore.getAllBookmark().isEmpty());
        }
    }

    // ==================== same-key edit (§3.8 double-alive) convergence ====================

    @Test
    public void sameKeyEdit_devicePriority_androidRecordWins() throws IOException {
        setStrategy("device_priority");
        // Local android copy is OLDER; the server holds a NEWER web copy.
        store.putLocalFavorite(favorite(41, 1000, "android-title"));
        WebUiSyncModels.SyncFavorite webCopy = new WebUiSyncModels.SyncFavorite();
        webCopy.gid = 41;
        webCopy.token = "tok41";
        webCopy.title = "web-title";
        webCopy.time = 50;
        webCopy.lastModified = 50;
        webCopy.deviceId = WEB;
        server.favorites.put(41L, new InMemorySyncServer.Record(1, false, webCopy));

        engine.syncInternal(config, ANDROID, 0);

        LocalFavoriteInfo result = store.loadLocalFavorite(41);
        assertNotNull(result);
        assertEquals("device_priority: android record wins unconditionally", "android-title", result.title);
    }

    @Test
    public void sameKeyEdit_webPriority_webRecordWinsEvenWhenOlder() throws IOException {
        setStrategy("web_priority");
        // Local android copy is NEWER; the server holds an OLDER web copy. Under
        // web_priority the web record must win even though it is older (§1.4:
        // timestamp does not arbitrate cross-platform).
        store.putLocalFavorite(favorite(51, 9000, "android-title"));
        WebUiSyncModels.SyncFavorite webCopy = new WebUiSyncModels.SyncFavorite();
        webCopy.gid = 51;
        webCopy.token = "tok51";
        webCopy.title = "web-title";
        webCopy.time = 50;
        webCopy.lastModified = 50;
        webCopy.deviceId = WEB;
        server.favorites.put(51L, new InMemorySyncServer.Record(1, false, webCopy));

        engine.syncInternal(config, ANDROID, 0);

        LocalFavoriteInfo result = store.loadLocalFavorite(51);
        assertNotNull(result);
        assertEquals("web_priority: web record wins even when older", "web-title", result.title);
    }

    @Test
    public void sameKeyEdit_lww_newerRecordWins() throws IOException {
        setStrategy("lww");
        // Local android copy is OLDER than the web copy -> the newer web copy wins.
        store.putLocalFavorite(favorite(61, 1000, "android-title"));
        WebUiSyncModels.SyncFavorite webCopy = new WebUiSyncModels.SyncFavorite();
        webCopy.gid = 61;
        webCopy.token = "tok61";
        webCopy.title = "web-title";
        webCopy.time = 50;
        webCopy.lastModified = 9000;
        webCopy.deviceId = WEB;
        server.favorites.put(61L, new InMemorySyncServer.Record(1, false, webCopy));

        engine.syncInternal(config, ANDROID, 0);

        LocalFavoriteInfo result = store.loadLocalFavorite(61);
        assertNotNull(result);
        assertEquals("lww: newer record wins", "web-title", result.title);
    }

    @Test
    public void sameKeyEdit_lww_localNewerIsKept() throws IOException {
        setStrategy("lww");
        store.putLocalFavorite(favorite(62, 9000, "android-title"));
        WebUiSyncModels.SyncFavorite webCopy = new WebUiSyncModels.SyncFavorite();
        webCopy.gid = 62;
        webCopy.token = "tok62";
        webCopy.title = "web-title";
        webCopy.time = 50;
        webCopy.lastModified = 1000;
        webCopy.deviceId = WEB;
        server.favorites.put(62L, new InMemorySyncServer.Record(1, false, webCopy));

        engine.syncInternal(config, ANDROID, 0);

        assertEquals("lww: newer local record is kept", "android-title", store.loadLocalFavorite(62).title);
    }

    @Test
    public void sameKeyEdit_bookmark_webPriorityWins() throws IOException {
        setStrategy("web_priority");
        BookmarkInfo local = new BookmarkInfo();
        local.gid = 71;
        local.token = "tok71";
        local.page = 90;
        local.time = 9000;
        store.putBookmark(local);

        WebUiSyncModels.SyncBookmark webCopy = new WebUiSyncModels.SyncBookmark();
        webCopy.gid = 71;
        webCopy.token = "tok71";
        webCopy.page = 5;
        webCopy.time = 50;
        webCopy.lastModified = 50;
        webCopy.deviceId = WEB;
        server.bookmarks.put(71L, new InMemorySyncServer.Record(1, false, webCopy));

        engine.syncInternal(config, ANDROID, 0);

        assertEquals("web_priority: web bookmark wins even when older", 5, store.getAllBookmark().get(0).page);
    }

    // ==================== disjoint adds (union) ====================

    @Test
    public void disjointAdds_union_allStrategies() throws IOException {
        for (String strategy : new String[] {"device_priority", "lww", "web_priority"}) {
            InMemoryWebUiSyncStore localStore = new InMemoryWebUiSyncStore();
            InMemorySyncServer syncServer = new InMemorySyncServer();
            syncServer.policy = policy(strategy);
            WebUiSyncEngine syncEngine = new WebUiSyncEngine(localStore, syncServer);

            // Client holds favorite 81; the server holds favorite 82 (from web).
            localStore.putLocalFavorite(favorite(81, 1000, "local"));
            WebUiSyncModels.SyncFavorite webFav = new WebUiSyncModels.SyncFavorite();
            webFav.gid = 82;
            webFav.token = "tok82";
            webFav.title = "web-fav";
            webFav.time = 1000;
            webFav.lastModified = 1000;
            webFav.deviceId = WEB;
            syncServer.favorites.put(82L, new InMemorySyncServer.Record(1, false, webFav));

            syncEngine.syncInternal(config, ANDROID, 0);

            assertEquals("[" + strategy + "] disjoint adds union", 2, localStore.favorites.size());
            assertNotNull(localStore.loadLocalFavorite(81));
            assertNotNull(localStore.loadLocalFavorite(82));
        }
    }

    // ==================== each deletes a different key ====================

    @Test
    public void eachDeletesDifferent_tombstoneEntities_bothPropagate() throws IOException {
        // Client deleted history 91 locally (pushed as a hard tombstone); the web
        // device deleted history 92. Both deletions must end up applied locally.
        setStrategy("device_priority");
        store.applySyncedHistory(history(91, 1000));
        store.applySyncedHistory(history(92, 1000));
        // First sync establishes both keys on the server and in the snapshot.
        long watermark = engine.syncInternal(config, ANDROID, 0).serverTimestamp;
        assertEquals(2, store.history.size());

        // Client deletes 91 locally.
        store.removeHistoryByKey(91);
        // Web deletes 92 on the server (hard tombstone).
        WebUiSyncModels.SyncHistory web92 = new WebUiSyncModels.SyncHistory();
        web92.gid = 92;
        web92.token = "tok92";
        web92.lastModified = 5000;
        web92.deviceId = WEB;
        web92.deleted = true;
        server.history.put(92L, new InMemorySyncServer.Record(watermark + 1, true, web92));

        engine.syncInternal(config, ANDROID, watermark);

        assertTrue("each-deletes-different tombstone: both deletions propagate",
                store.getAllHistoryForSync().isEmpty());
    }

    @Test
    public void eachDeletesDifferent_softEntities_devicePriority() throws IOException {
        // Under device_priority the android (priority) deletion of favorite 95
        // propagates, while a web (non-priority) deletion of favorite 96 that
        // this device still holds does NOT remove the local copy.
        setStrategy("device_priority");
        store.putLocalFavorite(favorite(95, 1000, "a"));
        store.putLocalFavorite(favorite(96, 1000, "b"));
        long watermark = engine.syncInternal(config, ANDROID, 0).serverTimestamp;
        assertEquals(2, store.favorites.size());

        // Client deletes 95 (android = priority delete -> propagates).
        store.removeLocalFavorites(95);
        // Web deletes 96 (non-priority; this device still holds it -> kept).
        // Seeded with serverModified above the watermark so it is pulled.
        WebUiSyncModels.SyncFavorite tomb96 = new WebUiSyncModels.SyncFavorite();
        tomb96.gid = 96;
        tomb96.token = "tok96";
        tomb96.lastModified = 5000;
        tomb96.deviceId = WEB;
        tomb96.deleted = true;
        server.favorites.put(96L, new InMemorySyncServer.Record(watermark + 1, true, tomb96));

        engine.syncInternal(config, ANDROID, watermark);

        assertNull("priority (android) deletion propagates", store.loadLocalFavorite(95));
        assertNotNull("non-priority (web) deletion does not remove the live local copy",
                store.loadLocalFavorite(96));
    }

    // ==================== same-platform falls back to LWW (§1.4) ====================

    @Test
    public void samePlatformConflict_fallsBackToLww() throws IOException {
        // Two android devices (same platform) under device_priority: no
        // unconditional winner — the newer record must win (LWW fallback).
        setStrategy("device_priority");
        store.putLocalFavorite(favorite(101, 1000, "android-old"));
        WebUiSyncModels.SyncFavorite otherAndroid = new WebUiSyncModels.SyncFavorite();
        otherAndroid.gid = 101;
        otherAndroid.token = "tok101";
        otherAndroid.title = "android-new";
        otherAndroid.time = 50;
        otherAndroid.lastModified = 9000;
        otherAndroid.deviceId = ANDROID_OTHER;
        server.favorites.put(101L, new InMemorySyncServer.Record(1, false, otherAndroid));

        engine.syncInternal(config, ANDROID, 0);

        assertEquals("same platform (android vs android) falls back to LWW",
                "android-new", store.loadLocalFavorite(101).title);
    }

    // ==================== helpers for non-favorite soft entities ====================

    private static DownloadInfo download(long gid, long time) {
        DownloadInfo info = new DownloadInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "download " + gid;
        info.state = DownloadInfo.STATE_WAIT;
        info.time = time;
        info.lastModified = time;
        return info;
    }

    private static Filter filter(int mode, String text) {
        Filter f = new Filter();
        f.mode = mode;
        f.text = text;
        f.enable = true;
        return f;
    }

    private static QuickSearch quickSearch(String name) {
        QuickSearch qs = new QuickSearch();
        qs.name = name;
        qs.keyword = "key";
        return qs;
    }

    private static DownloadLabel label(String name) {
        DownloadLabel l = new DownloadLabel();
        l.setLabel(name);
        l.setTime(1000);
        return l;
    }
}
