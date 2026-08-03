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
 * Client-side behavior matrix for the Wave-2 conflict strategies — the app
 * mirror of anotherviewer-web {@code SyncStrategyMatrixTest} (contract v2
 * §1.4/§3.8, MASTER §4.2): 3 strategies (A device_priority / B lww /
 * C web_priority) × 7 entities × the six divergence rows, driven through a
 * full push→pull cycle of {@link WebUiSyncEngine} against the in-memory
 * seams, plus the client-only guard edges (re-add, tombstone non-re-emit,
 * legacy/unknown strategy fallback, §3.8 platform edges).
 *
 * <p>Rows (client view):
 * <ol>
 *   <li>same-key simultaneous edit — the client applies the server-arbitrated
 *       winner (A: android record; B/C: the newer web record);</li>
 *   <li>priority side deletes / non-priority keeps — a priority-platform
 *       tombstone deletes the local copy (A/C); B resurrects via the live
 *       push; tombstone-class entities propagate under every strategy;</li>
 *   <li>non-priority side deletes / priority keeps — the §3.8 guard retains
 *       the priority live copy and resurrects it server-side (A); B keeps via
 *       union; when neither side is priority the deletion propagates (C);</li>
 *   <li>each deletes a different key, tombstone entities — both directions
 *       propagate under every strategy;</li>
 *   <li>each deletes a different key, soft entities — own deletion stored for
 *       A/C, discarded for B (union); incoming tombstones honored;</li>
 *   <li>disjoint adds — union under every strategy.</li>
 * </ol>
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncClientMatrixTest {

    private static final String ANDROID = "android-00000000-0000-0000-0000-000000000001";
    private static final String ANDROID_OTHER = "android-00000000-0000-0000-0000-000000000002";
    private static final String WEB = "web-browser-1";

    private static final String[] STRATEGIES = {"device_priority", "lww", "web_priority"};

    private enum Kind {
        FAVORITE, DOWNLOAD, FILTER, QUICK_SEARCH, DOWNLOAD_LABEL, HISTORY, BOOKMARK
    }

    private static final Kind[] SOFT_KINDS = {
            Kind.FAVORITE, Kind.DOWNLOAD, Kind.FILTER, Kind.QUICK_SEARCH, Kind.DOWNLOAD_LABEL};
    private static final Kind[] TOMB_KINDS = {Kind.HISTORY, Kind.BOOKMARK};
    private static final Kind[] ALL_KINDS = {
            Kind.FAVORITE, Kind.DOWNLOAD, Kind.FILTER, Kind.QUICK_SEARCH,
            Kind.DOWNLOAD_LABEL, Kind.HISTORY, Kind.BOOKMARK};

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

    // ==================== seeding / observation helpers ====================

    private void setStrategy(String strategy) {
        WebUiSyncModels.SyncPolicy p = new WebUiSyncModels.SyncPolicy();
        p.conflictStrategy = strategy;
        server.policy = p;
    }

    private static String filterKey(int base) {
        return base + "|f" + base;
    }

    private void seedLocal(Kind kind, int base) {
        switch (kind) {
            case FAVORITE: {
                LocalFavoriteInfo info = new LocalFavoriteInfo();
                info.gid = base;
                info.token = "tok" + base;
                info.title = "local-" + base;
                info.time = 1000;
                info.pages = 5;
                info.spanSize = 1;
                store.putLocalFavorite(info);
                break;
            }
            case DOWNLOAD: {
                DownloadInfo info = new DownloadInfo();
                info.gid = base;
                info.token = "tok" + base;
                info.title = "local-" + base;
                info.time = 1000;
                info.state = 0;
                info.label = "";
                info.total = 10;
                info.lastModified = 1000;
                store.putDownloadInfo(info);
                break;
            }
            case FILTER: {
                Filter filter = new Filter();
                filter.mode = base;
                filter.text = "f" + base;
                filter.enable = true;
                store.addFilter(filter);
                break;
            }
            case QUICK_SEARCH: {
                QuickSearch qs = new QuickSearch();
                qs.name = "qs" + base;
                qs.mode = 0;
                qs.keyword = "local";
                qs.time = 1000;
                store.insertQuickSearch(qs);
                break;
            }
            case DOWNLOAD_LABEL: {
                DownloadLabel label = new DownloadLabel();
                label.setLabel("label" + base);
                label.setTime(1000);
                store.addDownloadLabel(label);
                break;
            }
            case HISTORY: {
                HistoryInfo info = new HistoryInfo();
                info.gid = base;
                info.token = "tok" + base;
                info.time = 1000;
                info.mode = 0;
                store.applySyncedHistory(info);
                break;
            }
            case BOOKMARK: {
                BookmarkInfo info = new BookmarkInfo();
                info.gid = base;
                info.token = "tok" + base;
                info.time = 1000;
                info.page = 1;
                store.putBookmark(info);
                break;
            }
        }
    }

    private void removeLocal(Kind kind, int base) {
        switch (kind) {
            case FAVORITE: store.removeLocalFavorites(base); break;
            case DOWNLOAD: store.removeDownloadInfo(base); break;
            case FILTER: store.deleteFilterByKey(base, "f" + base); break;
            case QUICK_SEARCH: store.deleteQuickSearch(store.quickSearches.get("qs" + base)); break;
            case DOWNLOAD_LABEL: store.removeDownloadLabel(store.downloadLabels.get("label" + base)); break;
            case HISTORY: store.removeHistoryByKey(base); break;
            case BOOKMARK: store.removeBookmarkByGid(base); break;
        }
    }

    private boolean localAlive(Kind kind, int base) {
        switch (kind) {
            case FAVORITE: return store.favorites.containsKey((long) base);
            case DOWNLOAD: return store.downloads.containsKey((long) base);
            case FILTER: return store.findFilterByKey(base, "f" + base) != null;
            case QUICK_SEARCH: return store.quickSearches.containsKey("qs" + base);
            case DOWNLOAD_LABEL: return store.downloadLabels.containsKey("label" + base);
            case HISTORY: return store.history.containsKey((long) base);
            case BOOKMARK: return store.bookmarks.containsKey((long) base);
        }
        throw new IllegalStateException(kind.toString());
    }

    /** True when the local copy carries the server-side marker value. */
    private boolean localHasServerMarker(Kind kind, int base) {
        switch (kind) {
            case FAVORITE: return store.favorites.get((long) base).title.startsWith("server-");
            case DOWNLOAD: return store.downloads.get((long) base).state == 7;
            case FILTER: {
                Filter f = store.findFilterByKey(base, "f" + base);
                return f.enable != null && !f.enable;
            }
            case QUICK_SEARCH: return store.quickSearches.get("qs" + base).mode == 3;
            case DOWNLOAD_LABEL: return store.downloadLabels.get("label" + base).getTime() == 777;
            case HISTORY: return store.history.get((long) base).mode == 3;
            case BOOKMARK: return store.bookmarks.get((long) base).page == 42;
        }
        throw new IllegalStateException(kind.toString());
    }

    private Object serverKey(Kind kind, int base) {
        switch (kind) {
            case FILTER: return filterKey(base);
            case QUICK_SEARCH: return "qs" + base;
            case DOWNLOAD_LABEL: return "label" + base;
            default: return (long) base;
        }
    }

    private InMemorySyncServer.Record serverRecord(Kind kind, int base) {
        Object key = serverKey(kind, base);
        switch (kind) {
            case FAVORITE: return server.favorites.get(key);
            case DOWNLOAD: return server.downloads.get(key);
            case FILTER: return server.filters.get(key);
            case QUICK_SEARCH: return server.quickSearches.get(key);
            case DOWNLOAD_LABEL: return server.downloadLabels.get(key);
            case HISTORY: return server.history.get(key);
            case BOOKMARK: return server.bookmarks.get(key);
        }
        throw new IllegalStateException(kind.toString());
    }

    private void seedServerLive(Kind kind, int base, String deviceId, long lastModified, long serverModified) {
        Object dto;
        switch (kind) {
            case FAVORITE: {
                WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
                fav.gid = base;
                fav.token = "tok" + base;
                fav.title = "server-" + base;
                fav.time = lastModified;
                fav.lastModified = lastModified;
                fav.deviceId = deviceId;
                fav.deleted = false;
                dto = fav;
                break;
            }
            case DOWNLOAD: {
                WebUiSyncModels.SyncDownload dl = new WebUiSyncModels.SyncDownload();
                dl.gid = base;
                dl.token = "tok" + base;
                dl.state = 7;
                dl.time = lastModified;
                dl.lastModified = lastModified;
                dl.deviceId = deviceId;
                dl.deleted = false;
                dto = dl;
                break;
            }
            case FILTER: {
                WebUiSyncModels.SyncFilter f = new WebUiSyncModels.SyncFilter();
                f.mode = base;
                f.text = "f" + base;
                f.enabled = false;
                f.lastModified = lastModified;
                f.deviceId = deviceId;
                f.deleted = false;
                dto = f;
                break;
            }
            case QUICK_SEARCH: {
                WebUiSyncModels.SyncQuickSearch qs = new WebUiSyncModels.SyncQuickSearch();
                qs.name = "qs" + base;
                qs.mode = 3;
                qs.keyword = "server";
                qs.time = lastModified;
                qs.lastModified = lastModified;
                qs.deviceId = deviceId;
                qs.deleted = false;
                dto = qs;
                break;
            }
            case DOWNLOAD_LABEL: {
                WebUiSyncModels.SyncDownloadLabel dl = new WebUiSyncModels.SyncDownloadLabel();
                dl.label = "label" + base;
                dl.time = 777;
                dl.lastModified = lastModified;
                dl.deviceId = deviceId;
                dl.deleted = false;
                dto = dl;
                break;
            }
            case HISTORY: {
                WebUiSyncModels.SyncHistory h = new WebUiSyncModels.SyncHistory();
                h.gid = base;
                h.token = "tok" + base;
                h.mode = 3;
                h.time = lastModified;
                h.lastModified = lastModified;
                h.deviceId = deviceId;
                h.deleted = false;
                dto = h;
                break;
            }
            case BOOKMARK: {
                WebUiSyncModels.SyncBookmark b = new WebUiSyncModels.SyncBookmark();
                b.gid = base;
                b.token = "tok" + base;
                b.page = 42;
                b.time = lastModified;
                b.lastModified = lastModified;
                b.deviceId = deviceId;
                b.deleted = false;
                dto = b;
                break;
            }
            default:
                throw new IllegalStateException(kind.toString());
        }
        putServerRecord(kind, base, new InMemorySyncServer.Record(serverModified, false, dto));
    }

    private void seedServerTombstone(Kind kind, int base, String deviceId, long lastModified, long serverModified) {
        Object dto;
        switch (kind) {
            case FAVORITE: {
                WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
                fav.gid = base;
                fav.token = "tok" + base;
                fav.lastModified = lastModified;
                fav.deviceId = deviceId;
                fav.deleted = true;
                dto = fav;
                break;
            }
            case DOWNLOAD: {
                WebUiSyncModels.SyncDownload dl = new WebUiSyncModels.SyncDownload();
                dl.gid = base;
                dl.lastModified = lastModified;
                dl.deviceId = deviceId;
                dl.deleted = true;
                dto = dl;
                break;
            }
            case FILTER: {
                WebUiSyncModels.SyncFilter f = new WebUiSyncModels.SyncFilter();
                f.mode = base;
                f.text = "f" + base;
                f.lastModified = lastModified;
                f.deviceId = deviceId;
                f.deleted = true;
                dto = f;
                break;
            }
            case QUICK_SEARCH: {
                WebUiSyncModels.SyncQuickSearch qs = new WebUiSyncModels.SyncQuickSearch();
                qs.name = "qs" + base;
                qs.lastModified = lastModified;
                qs.deviceId = deviceId;
                qs.deleted = true;
                dto = qs;
                break;
            }
            case DOWNLOAD_LABEL: {
                WebUiSyncModels.SyncDownloadLabel dl = new WebUiSyncModels.SyncDownloadLabel();
                dl.label = "label" + base;
                dl.lastModified = lastModified;
                dl.deviceId = deviceId;
                dl.deleted = true;
                dto = dl;
                break;
            }
            case HISTORY: {
                WebUiSyncModels.SyncHistory h = new WebUiSyncModels.SyncHistory();
                h.gid = base;
                h.token = "tok" + base;
                h.lastModified = lastModified;
                h.deviceId = deviceId;
                h.deleted = true;
                dto = h;
                break;
            }
            case BOOKMARK: {
                WebUiSyncModels.SyncBookmark b = new WebUiSyncModels.SyncBookmark();
                b.gid = base;
                b.token = "tok" + base;
                b.lastModified = lastModified;
                b.deviceId = deviceId;
                b.deleted = true;
                dto = b;
                break;
            }
            default:
                throw new IllegalStateException(kind.toString());
        }
        putServerRecord(kind, base, new InMemorySyncServer.Record(serverModified, true, dto));
    }

    private void putServerRecord(Kind kind, int base, InMemorySyncServer.Record record) {
        Object key = serverKey(kind, base);
        switch (kind) {
            case FAVORITE: server.favorites.put((Long) key, record); break;
            case DOWNLOAD: server.downloads.put((Long) key, record); break;
            case FILTER: server.filters.put((String) key, record); break;
            case QUICK_SEARCH: server.quickSearches.put((String) key, record); break;
            case DOWNLOAD_LABEL: server.downloadLabels.put((String) key, record); break;
            case HISTORY: server.history.put((Long) key, record); break;
            case BOOKMARK: server.bookmarks.put((Long) key, record); break;
        }
    }

    // ==================== row 1: same-key simultaneous edit ====================

    /**
     * The client applies the server-arbitrated winner: under A the android
     * record wins (§1.4), so the local copy stays; under B the newer web
     * record wins (LWW) and under C the web record wins (§1.4) — both refresh
     * the local copy. Exception: filters stamp their push with wall-clock
     * lastModified, so under B the pushed filter out-dates the seeded web one.
     */
    @Test
    public void row1_sameKeySimultaneousEdit() throws IOException {
        for (Kind kind : ALL_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int base = 100;
                seedLocal(kind, base);
                seedServerLive(kind, base, WEB, 5000, 1);

                engine.syncInternal(config, ANDROID, 0);

                boolean expectLocal = "device_priority".equals(strategy)
                        || (kind == Kind.FILTER && "lww".equals(strategy));
                String ctx = "kind=" + kind + " strategy=" + strategy;
                assertTrue(ctx + ": local copy must exist", localAlive(kind, base));
                assertEquals(ctx + ": winner marker", expectLocal, !localHasServerMarker(kind, base));
            }
        }
    }

    // ========== row 2: priority side deletes, non-priority keeps ==========

    /**
     * A/C: a priority-platform tombstone deletes the local soft copy
     * (§3.8 — the fake mirror keeps the tomb against the client's live push);
     * B: the client's live push resurrects the union record, local survives.
     */
    @Test
    public void row2_prioritySideDeletes_softEntities() throws IOException {
        for (Kind kind : SOFT_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int base = 110;
                String deleter = "web_priority".equals(strategy) ? WEB : ANDROID_OTHER;
                seedLocal(kind, base);
                seedServerTombstone(kind, base, deleter, 5000, 1);

                engine.syncInternal(config, ANDROID, 0);

                boolean expectDeleted = !"lww".equals(strategy);
                assertEquals("kind=" + kind + " strategy=" + strategy,
                        expectDeleted, !localAlive(kind, base));
            }
        }
    }

    /** Tombstone-class entities propagate under EVERY strategy. */
    @Test
    public void row2_prioritySideDeletes_tombstoneEntities() throws IOException {
        for (Kind kind : TOMB_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int base = 120;
                seedLocal(kind, base);
                seedServerTombstone(kind, base, WEB, 5000, 1);

                engine.syncInternal(config, ANDROID, 0);

                assertFalse("kind=" + kind + " strategy=" + strategy, localAlive(kind, base));
            }
        }
    }

    // ========== row 3: non-priority side deletes, priority keeps ==========

    /**
     * A: the §3.8 guard retains the priority platform's live copy AND the
     * client's live push resurrects the record server-side; B: union keeps;
     * C: neither platform is priority and web does not hold the key, so the
     * deletion propagates (§3.8 refinement) — local copy removed.
     */
    @Test
    public void row3_nonPrioritySideDeletes_softEntities() throws IOException {
        for (Kind kind : SOFT_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int base = 130;
                String deleter = "web_priority".equals(strategy) ? ANDROID_OTHER : WEB;
                seedLocal(kind, base);
                seedServerTombstone(kind, base, deleter, 5000, 1);

                engine.syncInternal(config, ANDROID, 0);

                String ctx = "kind=" + kind + " strategy=" + strategy;
                if ("web_priority".equals(strategy)) {
                    assertFalse(ctx + ": non-priority deletion propagates when priority holds nothing",
                            localAlive(kind, base));
                } else {
                    assertTrue(ctx + ": local live copy survives", localAlive(kind, base));
                    if ("device_priority".equals(strategy)) {
                        InMemorySyncServer.Record record = serverRecord(kind, base);
                        assertNotNull(ctx, record);
                        assertFalse(ctx + ": priority live push resurrects server-side", record.deleted);
                    }
                }
            }
        }
    }

    // ===== row 4: each deletes a different key — tombstone entities =====

    /** Both directions propagate under every strategy (two-cycle run). */
    @Test
    public void row4_eachDeletesDifferentKey_tombstoneEntities() throws IOException {
        for (Kind kind : TOMB_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int k1 = 141;
                int k2 = 142;
                seedLocal(kind, k1);
                seedLocal(kind, k2);
                long ts1 = engine.syncInternal(config, ANDROID, 0).serverTimestamp;

                removeLocal(kind, k1); // client deletes K1
                seedServerTombstone(kind, k2, WEB, 5000, ts1 + 1); // web deletes K2

                engine.syncInternal(config, ANDROID, ts1);

                String ctx = "kind=" + kind + " strategy=" + strategy;
                assertFalse(ctx + ": K1 locally deleted", localAlive(kind, k1));
                assertFalse(ctx + ": K2 deletion reaches the client", localAlive(kind, k2));
                InMemorySyncServer.Record r1 = serverRecord(kind, k1);
                assertNotNull(ctx + ": K1 tombstone delivered", r1);
                assertTrue(ctx + ": K1 tombstone stored", r1.deleted);
            }
        }
    }

    // ======= row 5: each deletes a different key — soft entities =======

    /**
     * Own deletion (K1): stored server-side for A/C (§3.8 — A priority
     * deletion; C non-priority deletion with the priority platform holding
     * nothing), discarded for B (v1 union). Incoming web tombstone (K2,
     * never held locally) is honored as a no-op under every strategy.
     */
    @Test
    public void row5_eachDeletesDifferentKey_softEntities() throws IOException {
        for (Kind kind : SOFT_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int k1 = 151;
                int k2 = 152;
                seedLocal(kind, k1);
                long ts1 = engine.syncInternal(config, ANDROID, 0).serverTimestamp;

                removeLocal(kind, k1); // client deletes its own K1
                seedServerTombstone(kind, k2, WEB, 5000, ts1 + 1); // web deleted K2

                engine.syncInternal(config, ANDROID, ts1);

                String ctx = "kind=" + kind + " strategy=" + strategy;
                assertFalse(ctx + ": K1 locally deleted", localAlive(kind, k1));
                assertFalse(ctx + ": K2 never held locally", localAlive(kind, k2));
                InMemorySyncServer.Record r1 = serverRecord(kind, k1);
                assertNotNull(ctx + ": K1 record exists", r1);
                assertEquals(ctx + ": K1 tombstone outcome",
                        !"lww".equals(strategy), r1.deleted);
            }
        }
    }

    // ==================== row 6: disjoint adds union ====================

    @Test
    public void row6_disjointAddsUnion() throws IOException {
        for (Kind kind : ALL_KINDS) {
            for (String strategy : STRATEGIES) {
                setUp();
                setStrategy(strategy);
                int k1 = 161;
                int k2 = 162;
                seedLocal(kind, k1);
                seedServerLive(kind, k2, WEB, 1100, 1);

                engine.syncInternal(config, ANDROID, 0);

                String ctx = "kind=" + kind + " strategy=" + strategy;
                assertTrue(ctx + ": own key kept", localAlive(kind, k1));
                assertTrue(ctx + ": remote key added", localAlive(kind, k2));
                InMemorySyncServer.Record r1 = serverRecord(kind, k1);
                assertNotNull(ctx, r1);
                assertFalse(ctx + ": own key alive server-side", r1.deleted);
            }
        }
    }

    // ==================== client-only guard edges ====================

    /** Re-adding a deleted key drops it from pending — it is pushed alive, not re-deleted (B). */
    @Test
    public void reAddDropsPendingDeletion() throws IOException {
        setStrategy("lww");
        int gid = 171;
        seedLocal(Kind.FAVORITE, gid);
        long ts1 = engine.syncInternal(config, ANDROID, 0).serverTimestamp;

        removeLocal(Kind.FAVORITE, gid);
        seedLocal(Kind.FAVORITE, gid); // re-add before the next cycle

        engine.syncInternal(config, ANDROID, ts1);

        assertTrue(localAlive(Kind.FAVORITE, gid));
        InMemorySyncServer.Record record = serverRecord(Kind.FAVORITE, gid);
        assertNotNull(record);
        assertFalse("re-added key must be pushed alive, not tombstoned", record.deleted);
    }

    /**
     * An honored tombstone is never re-emitted: after the local copy is
     * removed and dropped from the snapshot, the next cycle pushes nothing
     * for that key and the server record stays untouched (A).
     */
    @Test
    public void honoredTombstoneNotReEmitted() throws IOException {
        setStrategy("device_priority");
        int gid = 172;
        seedLocal(Kind.FAVORITE, gid);
        long ts1 = engine.syncInternal(config, ANDROID, 0).serverTimestamp;

        // Priority-platform deletion arrives; the local copy is honored away.
        seedServerTombstone(Kind.FAVORITE, gid, ANDROID_OTHER, 5000, ts1 + 1);
        long ts2 = engine.syncInternal(config, ANDROID, ts1).serverTimestamp;
        assertFalse(localAlive(Kind.FAVORITE, gid));

        long serverModifiedAfterHonored = serverRecord(Kind.FAVORITE, gid).serverModified;
        engine.syncInternal(config, ANDROID, ts2);

        InMemorySyncServer.Record record = serverRecord(Kind.FAVORITE, gid);
        assertTrue("tombstone persists", record.deleted);
        assertEquals("no tombstone re-emit next cycle", serverModifiedAfterHonored, record.serverModified);
        assertFalse(localAlive(Kind.FAVORITE, gid));
    }

    /** Unknown strategy strings fall back to lww (legacy compat, contract §4.2). */
    @Test
    public void unknownStrategyFallsBackToLww() throws IOException {
        setStrategy("bogus");
        int gid = 173;
        seedLocal(Kind.FAVORITE, gid);
        seedServerTombstone(Kind.FAVORITE, gid, WEB, 5000, 1);

        engine.syncInternal(config, ANDROID, 0);

        assertTrue("bogus strategy behaves as lww (union resurrection)", localAlive(Kind.FAVORITE, gid));
    }

    /** Same-platform same-key falls back to LWW under A (§1.4 tie-break). */
    @Test
    public void samePlatformSameKeyFallsBackToLwwUnderA() throws IOException {
        setStrategy("device_priority");
        // Newer same-platform remote record refreshes the local copy (LWW).
        int newer = 174;
        seedLocal(Kind.FAVORITE, newer);
        seedServerLive(Kind.FAVORITE, newer, ANDROID_OTHER, 5000, 1);
        engine.syncInternal(config, ANDROID, 0);
        assertTrue("newer same-platform record wins under A (LWW fallback)",
                localHasServerMarker(Kind.FAVORITE, newer));

        // Older same-platform remote record does NOT displace the local copy.
        setUp();
        setStrategy("device_priority");
        int older = 175;
        seedLocal(Kind.FAVORITE, older);
        seedServerLive(Kind.FAVORITE, older, ANDROID_OTHER, 500, 1);
        engine.syncInternal(config, ANDROID, 0);
        assertFalse("older same-platform record loses under A (LWW fallback)",
                localHasServerMarker(Kind.FAVORITE, older));
    }

    /** §3.8 guard edges: unknown-platform deletions never beat the priority live copy. */
    @Test
    public void honorSoftTombstonePlatformEdges() {
        // Unknown / empty platform = non-priority: the priority live copy is guarded.
        assertFalse(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, "ios-device-9", ANDROID, true));
        assertFalse(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, "", ANDROID, true));
        // Priority-platform deletion always propagates...
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY, WEB, ANDROID, true));
        // ...and B honors everything (union resurrection happens via the push).
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.LWW, WEB, ANDROID, true));
    }

    /** Strategy parsing: known values map, anything else (incl. null) falls back to lww. */
    @Test
    public void strategyParseFallback() {
        assertEquals(WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY,
                WebUiSyncEngine.ConflictStrategy.parse("device_priority"));
        assertEquals(WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY,
                WebUiSyncEngine.ConflictStrategy.parse("web_priority"));
        assertEquals(WebUiSyncEngine.ConflictStrategy.LWW,
                WebUiSyncEngine.ConflictStrategy.parse("lww"));
        assertEquals(WebUiSyncEngine.ConflictStrategy.LWW,
                WebUiSyncEngine.ConflictStrategy.parse("bogus"));
        assertEquals(WebUiSyncEngine.ConflictStrategy.LWW,
                WebUiSyncEngine.ConflictStrategy.parse(""));
        assertEquals(WebUiSyncEngine.ConflictStrategy.LWW,
                WebUiSyncEngine.ConflictStrategy.parse(null));
    }
}
