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
import static org.junit.Assert.fail;

import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Drives the full {@link WebUiSyncEngine} push → pull → apply cycle against an
 * in-memory store and fake server: initial round trip, tombstone propagation,
 * snapshot/pending bookkeeping, failed-push retry, and the B2 lastModified
 * wire value.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncEngineTest {

    private WebUiConfig config;
    private InMemoryWebUiSyncStore storeA;
    private InMemoryWebUiSyncStore storeB;
    private InMemorySyncServer server;
    private WebUiSyncEngine engineA;
    private WebUiSyncEngine engineB;

    @Before
    public void setUp() {
        config = new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
        storeA = new InMemoryWebUiSyncStore();
        storeB = new InMemoryWebUiSyncStore();
        server = new InMemorySyncServer();
        engineA = new WebUiSyncEngine(storeA, server);
        engineB = new WebUiSyncEngine(storeB, server);
    }

    private static LocalFavoriteInfo favorite(long gid, long time, String title) {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = title;
        info.time = time;
        info.rated = true;
        info.simpleTags = new String[] {"a", "b"};
        info.pages = 5;
        info.thumbWidth = 100;
        info.thumbHeight = 200;
        info.spanSize = 3;
        info.spanIndex = 1;
        info.spanGroupIndex = 2;
        info.favoriteSlot = 4;
        info.favoriteName = "favName";
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

    private static DownloadInfo download(long gid, long time, long lastModified, int state) {
        DownloadInfo info = new DownloadInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "download " + gid;
        info.state = state;
        info.legacy = 2;
        info.time = time;
        info.label = "default";
        info.total = 10;
        info.finished = 3;
        info.lastModified = lastModified;
        info.rated = true;
        info.simpleTags = new String[] {"x", "y"};
        info.pages = 10;
        return info;
    }

    @Test
    public void testFullRoundTripBetweenTwoDevices() throws IOException {
        // Device A local state: all seven entity types.
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putLocalFavorite(favorite(2, 2000, "fav 2"));
        storeA.applySyncedHistory(history(3, 3000));
        DownloadInfo dl = download(4, 4000, 9000, DownloadInfo.STATE_WAIT);
        storeA.putDownloadInfo(dl);
        BookmarkInfo bm = new BookmarkInfo();
        bm.gid = 5;
        bm.token = "tok5";
        bm.title = "bookmark 5";
        bm.page = 7;
        bm.time = 5000;
        storeA.putBookmark(bm);
        Filter filter = new Filter();
        filter.mode = 1;
        filter.text = "text";
        filter.enable = true;
        storeA.addFilter(filter);
        QuickSearch qs = new QuickSearch();
        qs.name = "search";
        qs.mode = 2;
        qs.keyword = "key";
        storeA.insertQuickSearch(qs);
        DownloadLabel label = new DownloadLabel();
        label.setLabel("lbl");
        label.setTime(6000);
        storeA.addDownloadLabel(label);

        // A pushes everything to the server.
        WebUiSyncEngine.Result push = engineA.syncInternal(config, "devA", 0);
        assertEquals(2, push.pushedFavorites);
        assertEquals(1, push.pushedHistory);
        assertEquals(1, push.pushedDownloads);
        assertEquals(1, push.pushedBookmarks);
        assertEquals(1, push.pushedFilters);
        assertEquals(1, push.pushedQuickSearches);
        assertEquals(1, push.pushedDownloadLabels);
        assertEquals(0, push.pulledFavorites);

        // B2: the wire lastModified comes from the new column, not `time`.
        InMemorySyncServer.Record serverDownload = server.downloads.get(4L);
        assertNotNull(serverDownload);
        assertEquals(9000, ((WebUiSyncModels.SyncDownload) serverDownload.dto).lastModified);

        // B (fresh device) pulls the whole server state.
        WebUiSyncEngine.Result pull = engineB.syncInternal(config, "devB", 0);
        assertEquals(2, pull.pulledFavorites);
        assertEquals(1, pull.pulledHistory);
        assertEquals(1, pull.pulledDownloads);
        assertEquals(1, pull.pulledBookmarks);
        assertEquals(1, pull.pulledFilters);
        assertEquals(1, pull.pulledQuickSearches);
        assertEquals(1, pull.pulledDownloadLabels);

        // B's local state mirrors A's, including the gallery-detail columns
        // (B4 round trip through the wire) and the real WAIT state (B3: no
        // more "Fix state" NONE-ification on the way out).
        assertEquals(2, storeB.favorites.size());
        LocalFavoriteInfo fav = storeB.favorites.get(1L);
        assertNotNull(fav);
        assertEquals("fav 1", fav.title);
        assertTrue(fav.rated);
        assertTrue(Arrays.equals(new String[] {"a", "b"}, fav.simpleTags));
        assertEquals(5, fav.pages);
        assertEquals(100, fav.thumbWidth);
        assertEquals(200, fav.thumbHeight);
        assertEquals(3, fav.spanSize);
        assertEquals(1, fav.spanIndex);
        assertEquals(2, fav.spanGroupIndex);
        assertEquals(4, fav.favoriteSlot);
        assertEquals("favName", fav.favoriteName);

        DownloadInfo pulledDownload = storeB.downloads.get(4L);
        assertNotNull(pulledDownload);
        assertEquals(DownloadInfo.STATE_WAIT, pulledDownload.state);
        assertEquals(10, pulledDownload.total);
        assertEquals(3, pulledDownload.finished);
        assertTrue(pulledDownload.rated);
        assertTrue(Arrays.equals(new String[] {"x", "y"}, pulledDownload.simpleTags));
        assertEquals(10, pulledDownload.pages);

        assertEquals(1, storeB.history.size());
        assertEquals(1, storeB.history.get(3L).mode);
        assertEquals(1, storeB.bookmarks.size());
        assertEquals(7, storeB.bookmarks.get(5L).page);
        assertEquals(1, storeB.filters.size());
        assertTrue(storeB.filters.get("1|text").enable);
        assertEquals(1, storeB.quickSearches.size());
        assertEquals(1, storeB.downloadLabels.size());

        // Snapshot persisted on A (keys pushed). Note: applyFavorites and
        // applyHistory do not add pulled keys to their snapshots (existing
        // engine semantics, out of scope this wave), so only the push side
        // (A) and the download snapshot (which applyDownloads fills) are
        // asserted here.
        assertEquals("1,2", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals("3", storeA.prefs.get(config.baseUrl() + ".snapshot.history"));
        assertEquals("4", storeA.prefs.get(config.baseUrl() + ".snapshot.downloads"));
        assertEquals("4", storeB.prefs.get(config.baseUrl() + ".snapshot.downloads"));
    }

    @Test
    public void testTombstonePropagationAndUnionResurrection() throws IOException {
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putLocalFavorite(favorite(2, 2000, "fav 2"));
        storeA.applySyncedHistory(history(3, 3000));

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        WebUiSyncEngine.Result firstB = engineB.syncInternal(config, "devB", 0);
        long bWatermark = firstB.serverTimestamp;
        assertEquals(2, storeB.favorites.size());
        assertEquals(1, storeB.history.size());

        // Device A deletes favorite 2 and history 3.
        storeA.removeLocalFavorites(2);
        storeA.removeHistoryByKey(3);
        WebUiSyncEngine.Result del = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(2, del.pushedFavorites); // live fav 1 + tombstone for fav 2
        assertEquals(1, del.pushedHistory);   // hard-delete tombstone

        // Server: union merge keeps the live favorite (a soft delete never
        // overwrites a live record, contract §3.1/§3.8-B); history 3 is
        // hard-deleted and its tombstone retained.
        assertFalse(server.favorites.get(2L).deleted);
        assertTrue(server.history.get(3L).deleted);

        // Pending was delivered and cleared; snapshot now reflects current keys.
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".pending.favorites"));
        assertEquals("1", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".pending.history"));
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".snapshot.history"));

        // Device B syncs with its stale copies of both records:
        // - history is hard-delete: the tombstone survives B's live push, so
        //   the deletion propagates to B.
        // - favorites are union merge: B's stale live record resurrects fav 2
        //   on the server (the tombstone only applies when no live record
        //   exists), so the deletion does not propagate to B.
        WebUiSyncEngine.Result delB = engineB.syncInternal(config, "devB", bWatermark);
        assertEquals(1, delB.pulledHistory);
        assertEquals(0, storeB.history.size());
        assertEquals(2, storeB.favorites.size());
        assertFalse(server.favorites.get(2L).deleted); // resurrected by B's push
        assertTrue(server.history.get(3L).deleted);    // hard tombstone intact

        // A's next sync observes B's resurrection and pulls fav 2 back, so
        // both devices converge on the union of their states.
        WebUiSyncEngine.Result round = engineA.syncInternal(config, "devA", del.serverTimestamp);
        assertEquals(1, round.pulledFavorites);
        assertEquals(2, storeA.favorites.size());
    }

    @Test
    public void testFailedPushLeavesSnapshotAndPendingIntact() throws IOException {
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putLocalFavorite(favorite(2, 2000, "fav 2"));
        engineA.syncInternal(config, "devA", 0);

        storeA.removeLocalFavorites(2);
        server.rejectPushes = true;
        try {
            engineA.syncInternal(config, "devA", 1);
            fail("Expected IOException for rejected push");
        } catch (IOException expected) {
            // Expected.
        }
        // The failed cycle never saved the new state: the snapshot still lists
        // both keys, so the deletion is re-detected on the next attempt.
        assertEquals("1,2", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals(2, server.favorites.size());

        server.rejectPushes = false;
        WebUiSyncEngine.Result retry = engineA.syncInternal(config, "devA", 1);
        assertEquals(2, retry.pushedFavorites); // live fav 1 + tombstone for fav 2
        // Union merge: the server retains the live record (soft tombstone does
        // not overwrite live, contract §3.1); the pending bookkeeping above is
        // what guarantees the tombstone rides along on every retry.
        assertFalse(server.favorites.get(2L).deleted);
        assertEquals("1", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
    }

    @Test
    public void testDownloadLastModifiedFallbackForLegacyData() throws IOException {
        // Pre-v8 record: lastModified column reads 0 -> fall back to `time`.
        storeA.putDownloadInfo(download(1, 1000, 0, DownloadInfo.STATE_NONE));
        // v8 record: DownloadManager stamped lastModified.
        storeA.putDownloadInfo(download(2, 1000, 5000, DownloadInfo.STATE_NONE));

        engineA.syncInternal(config, "devA", 0);

        assertEquals(1000, ((WebUiSyncModels.SyncDownload) server.downloads.get(1L).dto).lastModified);
        assertEquals(5000, ((WebUiSyncModels.SyncDownload) server.downloads.get(2L).dto).lastModified);
    }

    @Test
    public void testChunkedDownloadPush() throws IOException {
        // More downloads than a single push batch: every batch must be accepted
        // and the server must end up with all records.
        for (long gid = 1; gid <= 1200; gid++) {
            storeA.putDownloadInfo(download(gid, gid * 10, gid * 100, DownloadInfo.STATE_NONE));
        }
        WebUiSyncEngine.Result result = engineA.syncInternal(config, "devA", 0);
        assertEquals(1200, result.pushedDownloads);
        assertEquals(1200, server.downloads.size());

        WebUiSyncEngine.Result pull = engineB.syncInternal(config, "devB", 0);
        assertEquals(1200, pull.pulledDownloads);
        assertEquals(1200, storeB.downloads.size());
        DownloadInfo sixthHundred = storeB.downloads.get(600L);
        assertNotNull(sixthHundred);
        assertEquals(DownloadInfo.STATE_NONE, sixthHundred.state);
        assertEquals(10, sixthHundred.total);
        assertEquals(3, sixthHundred.finished);
    }
}
