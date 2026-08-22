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

import com.hippo.anotherviewer.client.SiteCookieStore;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Drives the full {@link WebUiSyncEngine} push → pull → apply cycle against an
 * in-memory store and fake server: initial round trip, tombstone propagation,
 * snapshot/pending bookkeeping, failed-push retry, the B2 lastModified wire
 * value, and the B9 incremental push (new/changed/tombstone selection,
 * unchanged suppression, corrupt-ledger full-push fallback, re-add after
 * delete, pull-apply ledger adoption and §3.8 resurrection), and the R4-15
 * W1 regression (a favorite/history adopted from a pull and then deleted
 * locally must still emit its tombstone).
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

        // Snapshots persisted on both devices. R4-15 W1 (fixed): the snapshot
        // is re-collected from the local store at save time, so the keys B
        // adopted from the pull — favorites 1,2 and history 3, which the old
        // applyFavorites/applyHistory never added to their snapshots — are in
        // B's persisted snapshot too. A later local deletion of a pulled
        // record is therefore visible to detectDeletions (see the w1_* tests).
        assertEquals("1,2", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals("3", storeA.prefs.get(config.baseUrl() + ".snapshot.history"));
        assertEquals("4", storeA.prefs.get(config.baseUrl() + ".snapshot.downloads"));
        assertEquals("1,2", storeB.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals("3", storeB.prefs.get(config.baseUrl() + ".snapshot.history"));
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
        // B9 incremental: unchanged fav 1 stays home (its ledger entry
        // matches); only the tombstone for fav 2 goes out.
        assertEquals(1, del.pushedFavorites); // tombstone for fav 2 only
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
        // - history is hard-delete: the tombstone is pulled, so the deletion
        //   propagates to B.
        // - favorites are union merge: the tombstone never overwrites the
        //   live server record (§3.1/§3.8-B), so the deletion does not
        //   propagate to B. B9 incremental: B's ledger matches its unchanged
        //   local copies, so B no longer re-pushes fav 1/fav 2 every cycle —
        //   contract §6.1 sends only what changed. The union invariant still
        //   holds server-side: the record stays alive because the tombstone
        //   was discarded, not because B re-sent it.
        WebUiSyncEngine.Result delB = engineB.syncInternal(config, "devB", bWatermark);
        assertEquals(0, delB.pushedFavorites); // B9: unchanged, ledgered
        assertEquals(1, delB.pulledHistory);
        assertEquals(0, storeB.history.size());
        assertEquals(2, storeB.favorites.size());
        assertFalse(server.favorites.get(2L).deleted); // union: live survives
        assertTrue(server.history.get(3L).deleted);    // hard tombstone intact

        // B9 convergence note: under full-push, B's unconditional re-push of
        // fav 2 bumped its server timestamp and A re-adopted the favorite it
        // had just deleted. Incrementally, B has no change to send and the
        // server record is not re-stamped, so A's local deletion stands while
        // the server keeps the union alive for B (and any other device). The
        // §3.8 resurrection path — a retained live record forced back onto
        // the wire via ledger invalidation — is covered by
        // incrementalPush_priorityGuardInvalidatesLedgerForResurrection.
        WebUiSyncEngine.Result round = engineA.syncInternal(config, "devA", del.serverTimestamp);
        assertEquals(0, round.pulledFavorites);
        assertEquals(1, storeA.favorites.size());
        assertFalse(server.favorites.get(2L).deleted); // union kept on server
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
        // B9 incremental: fav 1 is unchanged (ledger intact — the failed
        // cycle saved nothing), so only the tombstone for fav 2 is retried.
        assertEquals(1, retry.pushedFavorites); // tombstone for fav 2 only
        // Union merge: the server retains the live record (soft tombstone does
        // not overwrite live, contract §3.1); the pending bookkeeping above is
        // what guarantees the tombstone rides along on every retry.
        assertFalse(server.favorites.get(2L).deleted);
        assertEquals("1", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
    }

    @Test
    public void pushRejection_messageCarriesBatchAndEntityContext() throws IOException {
        // A5: the rejection message must say which batch failed and what was
        // in it, instead of the bare "Server rejected push".
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putDownloadInfo(download(2, 2000, 2500, DownloadInfo.STATE_WAIT));
        server.rejectPushes = true;
        try {
            engineA.syncInternal(config, "devA", 0);
            fail("Expected IOException for rejected push");
        } catch (IOException expected) {
            assertTrue("message carries the failing batch position",
                    expected.getMessage().contains("batch 1/1"));
            assertTrue(expected.getMessage().contains("favorites=1"));
            assertTrue(expected.getMessage().contains("downloads=1"));
        }
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

    // ==================== B9 incremental push ====================
    // The push phase sends only new keys, keys whose effective lastModified
    // differs from the per-serverKey push ledger, and pending tombstones.
    // Filters/quick searches/download labels stay full-push (no trusted
    // change timestamp); see buildPushRequests.

    private static BookmarkInfo bookmark(long gid, long time, int page) {
        BookmarkInfo info = new BookmarkInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "bookmark " + gid;
        info.page = page;
        info.time = time;
        return info;
    }

    private static WebUiSyncModels.SyncPolicy policy(String strategy) {
        WebUiSyncModels.SyncPolicy p = new WebUiSyncModels.SyncPolicy();
        p.conflictStrategy = strategy;
        return p;
    }

    @Test
    public void incrementalPush_onlyNewAndChangedSent() throws IOException {
        // One record per ledgered entity.
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.applySyncedHistory(history(2, 2000));
        storeA.putBookmark(bookmark(3, 3000, 1));
        storeA.putDownloadInfo(download(4, 4000, 5000, DownloadInfo.STATE_WAIT));

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedFavorites);
        assertEquals(1, first.pushedHistory);
        assertEquals(1, first.pushedBookmarks);
        assertEquals(1, first.pushedDownloads);

        // Mutate: one new key per entity, plus a change on each existing one
        // (favorite add, history re-view, bookmark page turn, download state
        // transition stamped by DownloadManager).
        storeA.putLocalFavorite(favorite(10, 1100, "fav 10"));
        storeA.applySyncedHistory(history(2, 2500));
        storeA.putBookmark(bookmark(3, 3500, 2));
        storeA.putDownloadInfo(download(4, 4000, 5500, DownloadInfo.STATE_DOWNLOAD));
        storeA.putDownloadInfo(download(40, 4100, 5600, DownloadInfo.STATE_NONE));

        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        // New + changed only; nothing else crosses the wire.
        assertEquals(1, second.pushedFavorites);   // new fav 10
        assertEquals(1, second.pushedHistory);     // re-viewed hist 2
        assertEquals(1, second.pushedBookmarks);   // page turn on bm 3
        assertEquals(2, second.pushedDownloads);   // changed dl 4 + new dl 40

        // The wire carries the bumped lastModified values.
        assertEquals(2500, ((WebUiSyncModels.SyncHistory) server.history.get(2L).dto).lastModified);
        assertEquals(3500, ((WebUiSyncModels.SyncBookmark) server.bookmarks.get(3L).dto).lastModified);
        assertEquals(5500, ((WebUiSyncModels.SyncDownload) server.downloads.get(4L).dto).lastModified);
    }

    @Test
    public void incrementalPush_unchangedNotSent_smallEntitiesStayFull() throws IOException {
        // All seven entity types.
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.applySyncedHistory(history(2, 2000));
        storeA.putBookmark(bookmark(3, 3000, 1));
        storeA.putDownloadInfo(download(4, 4000, 5000, DownloadInfo.STATE_WAIT));
        Filter filter = new Filter();
        filter.mode = 1;
        filter.text = "text";
        filter.enable = true;
        storeA.addFilter(filter);
        QuickSearch qs = new QuickSearch();
        qs.name = "search";
        qs.keyword = "key";
        storeA.insertQuickSearch(qs);
        DownloadLabel label = new DownloadLabel();
        label.setLabel("lbl");
        label.setTime(6000);
        storeA.addDownloadLabel(label);

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(7, server.totalPushedEntities);

        // Nothing changes: the ledgered entities send zero records, while the
        // three timestamp-less small entities keep riding full-push (their
        // content edits do not bump a trusted timestamp, so a ledger could
        // silently miss changes; the sets are small enough that this is free).
        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(0, second.pushedFavorites);
        assertEquals(0, second.pushedHistory);
        assertEquals(0, second.pushedBookmarks);
        assertEquals(0, second.pushedDownloads);
        assertEquals(1, second.pushedFilters);
        assertEquals(1, second.pushedQuickSearches);
        assertEquals(1, second.pushedDownloadLabels);
        assertEquals(3, server.totalPushedEntities - 7); // only the small full-push sets

        // The empty-but-present push request still carries the device policy
        // (D2): a push happened even though no ledgered entity changed.
        assertTrue(server.pushRequestCount >= 2);
        assertNotNull(server.lastPushPolicy);
    }

    @Test
    public void incrementalPush_tombstonesStillSent_allEntities() throws IOException {
        // device_priority: the android tombstones win over the live server
        // records, so the deleted state is observable on the server.
        server.policy = policy("device_priority");
        String android = "android-00000000-0000-0000-0000-000000000001";
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.applySyncedHistory(history(2, 2000));
        storeA.putBookmark(bookmark(3, 3000, 1));
        storeA.putDownloadInfo(download(4, 4000, 5000, DownloadInfo.STATE_WAIT));

        WebUiSyncEngine.Result first = engineA.syncInternal(config, android, 0);

        // Delete everything locally between cycles.
        storeA.removeLocalFavorites(1);
        storeA.removeHistoryByKey(2);
        storeA.removeBookmarkByGid(3);
        storeA.removeDownloadInfo(4);

        WebUiSyncEngine.Result second = engineA.syncInternal(config, android, first.serverTimestamp);
        // Only the four tombstones go out — no live records remain to send.
        assertEquals(1, second.pushedFavorites);
        assertEquals(1, second.pushedHistory);
        assertEquals(1, second.pushedBookmarks);
        assertEquals(1, second.pushedDownloads);
        assertEquals(4, server.totalPushedEntities - 4); // 4 live in cycle 1, 4 tombs here
        assertTrue(server.favorites.get(1L).deleted);
        assertTrue(server.history.get(2L).deleted);
        assertTrue(server.bookmarks.get(3L).deleted);
        assertTrue(server.downloads.get(4L).deleted);

        // The tombstoned keys were dropped from the ledger: a later re-add
        // pushes as new again (covered in detail by
        // incrementalPush_reAddAfterDeleteUpdatesLedger).
    }

    @Test
    public void incrementalPush_corruptLedgerFallsBackToFullPush() throws IOException {
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putLocalFavorite(favorite(2, 2000, "fav 2"));
        storeA.putDownloadInfo(download(3, 3000, 3500, DownloadInfo.STATE_NONE));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(2, first.pushedFavorites);
        assertEquals(1, first.pushedDownloads);

        // Corrupt the persisted favorite ledger; leave the download ledger intact.
        storeA.prefs.put(config.baseUrl() + ".ledger.favorites", "{{{ not json");

        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        // Corrupt ledger -> safe default: every live favorite is re-sent...
        assertEquals(2, second.pushedFavorites);
        // ...while the intact download ledger still suppresses unchanged rows.
        assertEquals(0, second.pushedDownloads);

        // The cycle rewrote a clean ledger; the third cycle is incremental again.
        WebUiSyncEngine.Result third = engineA.syncInternal(config, "devA", second.serverTimestamp);
        assertEquals(0, third.pushedFavorites);
        assertEquals(0, third.pushedDownloads);
    }

    @Test
    public void incrementalPush_reAddAfterDeleteUpdatesLedger() throws IOException {
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedFavorites);

        // Delete and re-add between syncs: the re-add cancels the pending
        // tombstone and the live record is pushed at its new lastModified.
        storeA.removeLocalFavorites(1);
        storeA.putLocalFavorite(favorite(1, 9000, "fav 1 again"));

        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(1, second.pushedFavorites); // live re-add, no tombstone
        assertFalse(server.favorites.get(1L).deleted);
        WebUiSyncModels.SyncFavorite wire = (WebUiSyncModels.SyncFavorite) server.favorites.get(1L).dto;
        assertEquals("fav 1 again", wire.title);
        assertEquals(9000, wire.lastModified);
        // F6 evidence row: the re-add carries a NEW add time (re-stamped by the
        // retaining side), which is exactly what makes it a fresh lastModified
        // delta that propagates incrementally — resurrection is explicit, not
        // an implicit next-round echo.
        assertEquals(9000, wire.time);

        // The ledger now tracks the re-added record; unchanged -> not sent.
        WebUiSyncEngine.Result third = engineA.syncInternal(config, "devA", second.serverTimestamp);
        assertEquals(0, third.pushedFavorites);
    }

    @Test
    public void incrementalPush_pulledRecordsNotEchoed() throws IOException {
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putDownloadInfo(download(2, 2000, 2500, DownloadInfo.STATE_NONE));
        engineA.syncInternal(config, "devA", 0);

        // B pulls both records; the apply phase ledgers them at their local
        // effective values.
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, bFirst.pulledFavorites);
        assertEquals(1, bFirst.pulledDownloads);
        long pushedBefore = server.totalPushedEntities;

        // B's next cycle sends nothing back — no echo of pulled state.
        WebUiSyncEngine.Result bSecond = engineB.syncInternal(config, "devB", bFirst.serverTimestamp);
        assertEquals(0, bSecond.pushedFavorites);
        assertEquals(0, bSecond.pushedDownloads);
        assertEquals(0, server.totalPushedEntities - pushedBefore);
    }

    @Test
    public void applyDownloads_selfEchoKeepsWireStampAndStaysSilent() throws IOException {
        // A3: the same-cycle push → pull loop rewrites a device's own download
        // row from its self-push echo. The rewrite must keep the B2
        // lastModified stamp (previously wiped to 0) and stay silent next
        // cycle — the ledger matches the rewritten row's effective value.
        storeA.putDownloadInfo(download(9, 4000, 9000, DownloadInfo.STATE_WAIT));

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedDownloads);

        // The self-echo rewrote the row: the stamp survives verbatim.
        assertEquals(9000L, storeA.downloads.get(9L).lastModified);

        // No re-echo: the rewritten row's effective push value (9000) equals
        // its ledger entry.
        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(0, second.pushedDownloads);
    }

    @Test
    public void applyDownloads_pulledRowAdoptsWireStamp() throws IOException {
        // A3: a genuine pull adopts the wire stamp as the local one instead of
        // leaving 0 (effective value would degrade to time).
        storeA.putDownloadInfo(download(11, 5000, 7000, DownloadInfo.STATE_NONE));
        engineA.syncInternal(config, "devA", 0);

        engineB.syncInternal(config, "devB", 0);

        assertEquals(7000L, storeB.downloads.get(11L).lastModified);
    }

    @Test
    public void incrementalPush_priorityGuardInvalidatesLedgerForResurrection() throws IOException {
        // device_priority: a non-priority (web) tombstone must not remove the
        // android live copy; the surviving copy is re-pushed next cycle and
        // resurrects the record on the server (§3.8). Under full-push this
        // happened implicitly; incrementally the ledger entry must be dropped
        // explicitly when the tombstone is not honored.
        server.policy = policy("device_priority");
        String android = "android-00000000-0000-0000-0000-000000000001";
        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, android, 0);
        assertFalse(server.favorites.get(1L).deleted);

        // Web deletes the favorite after our last sync; the tombstone lands
        // above the watermark so it is pulled (same seeding pattern as the
        // §3.8 matrix tests).
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = 1;
        tomb.token = "tok1";
        tomb.lastModified = 5000;
        tomb.deviceId = "web-browser-1";
        tomb.deleted = true;
        server.favorites.put(1L, new InMemorySyncServer.Record(first.serverTimestamp + 1, true, tomb));

        // Cycle 2: the tombstone is not honored (priority platform holds a
        // live copy) — the local record survives, its ledger entry is dropped.
        WebUiSyncEngine.Result second = engineA.syncInternal(config, android, first.serverTimestamp);
        assertEquals(0, second.pushedFavorites); // ledger still matched at push time
        assertNotNull("priority live copy survives the web tombstone",
                storeA.loadLocalFavorite(1));
        assertTrue("server holds the web tombstone", server.favorites.get(1L).deleted);

        // Cycle 3: the dropped ledger entry forces the live record back onto
        // the wire; the android (priority) live record resurrects it.
        engineA.syncInternal(config, android, second.serverTimestamp);
        assertFalse("android live push resurrects the record (§3.8)",
                server.favorites.get(1L).deleted);
        assertNotNull(storeA.loadLocalFavorite(1));
    }

    // ==================== R1 P3: tombstone-entity edges ====================

    /**
     * Positive resurrection for the hard-delete (tombstone-class) entities
     * (§3.8 mirror, §3.2/§3.4): after a deletion propagates, an explicit
     * re-view / re-bookmark with a clearly-newer stamp (beyond the ±5 s skew
     * window) revives the key on the server — both for history and bookmarks.
     * Within the window the full SyncService tie-break applies: a same-view
     * live push ties and loses against the tombstone's preserved view time
     * (a soft-deleted row keeps its fields, §4.2), so the deletion stands
     * until a record that actually wins the tie-break arrives.
     */
    @Test
    public void tombstoneEntity_positiveResurrection_afterDeletion() throws IOException {
        storeA.applySyncedHistory(history(31, 1000));
        storeA.putBookmark(bookmark(32, 1000, 3));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertFalse(server.history.get(31L).deleted);
        assertFalse(server.bookmarks.get(32L).deleted);

        // Delete both between cycles; the tombstones land on the server and
        // the pull phase clears the local rows.
        storeA.removeHistoryByKey(31);
        storeA.removeBookmarkByGid(32);
        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertTrue(server.history.get(31L).deleted);
        assertTrue(server.bookmarks.get(32L).deleted);
        assertEquals(0, storeA.history.size());
        assertEquals(0, storeA.bookmarks.size());

        // Explicit re-add with clearly-newer stamps (beyond the skew window):
        // both tombstones are revived by the live pushes (§3.2/§3.4 mirror).
        long tombHistoryLm = server.history.get(31L).dtoLastModified();
        long tombBookmarkLm = server.bookmarks.get(32L).dtoLastModified();
        storeA.applySyncedHistory(history(31, tombHistoryLm + 10_000));
        storeA.putBookmark(bookmark(32, tombBookmarkLm + 10_000, 7));

        engineA.syncInternal(config, "devA", second.serverTimestamp);

        assertFalse("clearly-newer live history resurrects the tombstone (§3.2)",
                server.history.get(31L).deleted);
        assertFalse("clearly-newer live bookmark resurrects the tombstone (§3.4)",
                server.bookmarks.get(32L).deleted);
        assertEquals(7, ((WebUiSyncModels.SyncBookmark) server.bookmarks.get(32L).dto).page);
        assertNotNull(storeA.history.get(31L));
        assertNotNull(storeA.bookmarks.get(32L));

        // Within-skew live pushes only resurrect when they win the entity
        // tie-break (SyncService mergeHistory: later view time). The tombstone
        // preserves the resurrected row's view time (§4.2), so a same-view
        // re-push inside the 5 s window ties and loses (§5.1③ first-received
        // baseline) — the deletion stays until a clearly-newer stamp arrives.
        storeA.removeHistoryByKey(31);
        WebUiSyncEngine.Result third = engineA.syncInternal(config, "devA", second.serverTimestamp);
        assertTrue(server.history.get(31L).deleted);
        long tombLm2 = server.history.get(31L).dtoLastModified();
        storeA.applySyncedHistory(history(31, tombLm2)); // inside the 5 s window, same view
        engineA.syncInternal(config, "devA", third.serverTimestamp);
        assertTrue("in-skew same-view live push loses the tie-break, deletion stays",
                server.history.get(31L).deleted);
    }

    /**
     * Tomb-vs-tomb: both devices delete the same key (tombstone-class
     * entity). The two tombstones collide on the server — the deletion must
     * stay (a tombstone can never resurrect the key) and the later stamp
     * wins the tomb-vs-tomb LWW.
     */
    @Test
    public void tombstoneEntity_tombVsTomb_keepsDeletion() throws IOException {
        storeA.applySyncedHistory(history(41, 1000));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, storeB.history.size());

        // A deletes first; the tombstone lands on the server.
        storeA.removeHistoryByKey(41);
        engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertTrue(server.history.get(41L).deleted);
        long tombLmAfterA = server.history.get(41L).dtoLastModified();

        // B deletes too before seeing A's tombstone and pushes its own.
        storeB.removeHistoryByKey(41);
        engineB.syncInternal(config, "devB", bFirst.serverTimestamp);

        InMemorySyncServer.Record record = server.history.get(41L);
        assertTrue("tomb-vs-tomb keeps the deletion", record.deleted);
        assertTrue("the later tombstone stamp wins the tomb-vs-tomb LWW",
                record.dtoLastModified() >= tombLmAfterA);
        assertEquals("no resurrection: B's local row stays gone",
                0, storeB.history.size());
    }

    /**
     * R1 P3: per-serverKey isolation. The same local store syncs to two
     * different servers (different ports → different {@code baseUrl()}
     * serverKeys). Snapshots/pending/push ledgers are keyed by serverKey, so
     * bookkeeping for one server must never leak into the other: each server
     * independently receives the full state, suppresses unchanged records on
     * its own ledger, and only sees a change when its own cycle pushes it.
     */
    @Test
    public void dualServerKey_ledgerAndSnapshotIsolation() throws IOException {
        WebUiConfig otherConfig = new WebUiConfig("http", "127.0.0.1", 8081, "user", "token");
        InMemorySyncServer otherServer = new InMemorySyncServer();
        WebUiSyncEngine otherEngine = new WebUiSyncEngine(storeA, otherServer);

        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.applySyncedHistory(history(2, 2000));

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedFavorites);
        assertEquals(1, first.pushedHistory);

        // Same store, other server: everything counts as new again under the
        // other serverKey — the first server's ledger must not suppress it.
        WebUiSyncEngine.Result otherFirst = otherEngine.syncInternal(otherConfig, "devA", 0);
        assertEquals(1, otherFirst.pushedFavorites);
        assertEquals(1, otherFirst.pushedHistory);
        assertEquals(1, otherServer.favorites.size());
        assertEquals(1, otherServer.history.size());

        // Unchanged cycles are then suppressed independently per server.
        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(0, second.pushedFavorites);
        assertEquals(0, second.pushedHistory);
        WebUiSyncEngine.Result otherSecond =
                otherEngine.syncInternal(otherConfig, "devA", otherFirst.serverTimestamp);
        assertEquals(0, otherSecond.pushedFavorites);
        assertEquals(0, otherSecond.pushedHistory);

        // A new key pushed to server 1 does not leak into server 2's state
        // until that server's own cycle runs.
        storeA.putLocalFavorite(favorite(9, 3000, "fav 9"));
        engineA.syncInternal(config, "devA", second.serverTimestamp);
        assertNotNull("server 1 received the new key", server.favorites.get(9L));
        assertNull("server 2 untouched until its own cycle", otherServer.favorites.get(9L));
        otherEngine.syncInternal(otherConfig, "devA", otherSecond.serverTimestamp);
        assertNotNull("server 2 picks it up on its own cycle", otherServer.favorites.get(9L));
    }

    // ==================== R4-15 W1: deletion visibility of pulled records ====================
    // The persisted snapshot is re-collected from the local store at save
    // time (after apply). Keys adopted from a pull are therefore in it even
    // though applyFavorites/applyHistory never added them — deleting such a
    // key locally afterwards is visible to detectDeletions and its tombstone
    // reaches the server. Before the fix the key fell out of every detection
    // set after the adopting cycle: the tombstone was never generated and the
    // server row stayed alive forever.

    /**
     * W1 favorites: device B adopts favorite K from the server, deletes it
     * locally before its next cycle; the tombstone must be generated and win
     * on the server (device_priority: the priority platform's deletion is
     * final — SyncService softDeleteLiveWins, tombPlatform == priority).
     */
    @Test
    public void w1_pulledFavoriteAdoptedThenDeleted_tombstoneReachesServer() throws IOException {
        server.policy = policy("device_priority");
        String androidA = "android-00000000-0000-0000-0000-000000000001";
        String androidB = "android-00000000-0000-0000-0000-000000000002";

        // Device A seeds favorite K=7 on the server.
        storeA.putLocalFavorite(favorite(7, 1000, "fav 7"));
        engineA.syncInternal(config, androidA, 0);
        assertFalse(server.favorites.get(7L).deleted);

        // Device B (fresh watermark) pulls and adopts K. W1: K must land in
        // B's persisted snapshot even though applyFavorites never adds it.
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, androidB, 0);
        assertEquals(1, bFirst.pulledFavorites);
        assertNotNull(storeB.loadLocalFavorite(7));
        assertEquals("7", storeB.prefs.get(config.baseUrl() + ".snapshot.favorites"));

        // The user deletes K locally (long-press delete writes no pending).
        storeB.removeLocalFavorites(7);

        // Next cycle: detectDeletions sees K through the snapshot -> tombstone.
        WebUiSyncEngine.Result bSecond =
                engineB.syncInternal(config, androidB, bFirst.serverTimestamp);
        assertEquals(1, bSecond.pushedFavorites); // the tombstone for K only
        assertTrue("pulled-then-deleted favorite tombstone reaches the server",
                server.favorites.get(7L).deleted);

        // Converged: the deletion is not re-pushed forever and stays final.
        WebUiSyncEngine.Result bThird =
                engineB.syncInternal(config, androidB, bSecond.serverTimestamp);
        assertEquals(0, bThird.pushedFavorites);
        assertTrue(server.favorites.get(7L).deleted);
    }

    /** W1 history (same shape, hard-delete entity): the tombstone must reach the server. */
    @Test
    public void w1_pulledHistoryAdoptedThenDeleted_tombstoneReachesServer() throws IOException {
        String androidA = "android-00000000-0000-0000-0000-000000000001";
        String androidB = "android-00000000-0000-0000-0000-000000000002";

        // Device A seeds history K=11 on the server.
        storeA.applySyncedHistory(history(11, 1000));
        engineA.syncInternal(config, androidA, 0);
        assertFalse(server.history.get(11L).deleted);

        // Device B pulls and adopts K; the persisted snapshot must contain it.
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, androidB, 0);
        assertEquals(1, bFirst.pulledHistory);
        assertNotNull(storeB.history.get(11L));
        assertEquals("11", storeB.prefs.get(config.baseUrl() + ".snapshot.history"));

        // B clears the history row locally before its next cycle.
        storeB.removeHistoryByKey(11);

        WebUiSyncEngine.Result bSecond =
                engineB.syncInternal(config, androidB, bFirst.serverTimestamp);
        assertEquals(1, bSecond.pushedHistory); // hard-delete tombstone for K
        assertTrue("pulled-then-deleted history tombstone reaches the server",
                server.history.get(11L).deleted);
        assertEquals(0, storeB.history.size());

        // Converged: no repeated tombstone traffic, deletion stays.
        WebUiSyncEngine.Result bThird =
                engineB.syncInternal(config, androidB, bSecond.serverTimestamp);
        assertEquals(0, bThird.pushedHistory);
        assertTrue(server.history.get(11L).deleted);
    }

    // ==================== W5: write-through pending (R4-15 audit §2.1 hardening) ====================
    // Detected pending tombstones are persisted at detection time — before the
    // push — not only by the end-of-cycle save block. A process death between
    // detection and save (in particular after the push was acked) therefore
    // cannot lose the deletion: the next cycle re-delivers the tombstone from
    // the durable pending sets alone, without depending on the persisted
    // snapshot still containing the key.

    /**
     * Unit-level kill-window simulation: the cycle detects the tombstone,
     * pushes it (acked), then "dies" before the save block (pull throws).
     * The pending set must already have been durable at push time, survive
     * the kill, and drive the tombstone re-delivery on the recovery cycle —
     * while ack-before-clear (pending only cleared after a FULLY successful
     * cycle) and the B9 ledger semantics stay intact.
     */
    @Test
    public void w5_writeThroughPending_survivesKillAfterPushAck() throws IOException {
        server.policy = policy("device_priority");
        String android = "android-00000000-0000-0000-0000-000000000001";

        storeA.putLocalFavorite(favorite(1, 1000, "fav 1"));
        storeA.putLocalFavorite(favorite(2, 2000, "fav 2"));
        WebUiSyncEngine.Result first = engineA.syncInternal(config, android, 0);
        assertEquals("1,2", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".pending.favorites"));

        // The user deletes favorite 2 between cycles.
        storeA.removeLocalFavorites(2);

        // Kill-window transport: records what the store has persisted AT PUSH
        // TIME, accepts the push (the tombstone is acked by the real fake
        // server), then simulates process death before the save block by
        // throwing from pull.
        final String[] pendingAtPushTime = new String[1];
        WebUiSyncTransport killingTransport = new WebUiSyncTransport() {
            @Override
            public WebUiSyncModels.PushResponse push(WebUiConfig cfg,
                    WebUiSyncModels.PushRequest request) throws IOException {
                pendingAtPushTime[0] = storeA.prefs.get(cfg.baseUrl() + ".pending.favorites");
                return server.push(cfg, request);
            }

            @Override
            public WebUiSyncModels.PullResponse pull(WebUiConfig cfg, long since)
                    throws IOException {
                throw new IOException("simulated process death after push ack");
            }
        };
        WebUiSyncEngine killingEngine = new WebUiSyncEngine(storeA, killingTransport);
        try {
            killingEngine.syncInternal(config, android, first.serverTimestamp);
            fail("Expected IOException from the simulated kill");
        } catch (IOException expected) {
            // Expected: the cycle died after the push ack, before the save block.
        }

        // The tombstone was durable BEFORE the push went out...
        assertEquals("write-through persisted the pending tombstone before the push",
                "2", pendingAtPushTime[0]);
        // ...and survives the kill: pending stays, snapshot untouched (the
        // aborted cycle's save block never ran — zero persistence).
        assertEquals("2", storeA.prefs.get(config.baseUrl() + ".pending.favorites"));
        assertEquals("1,2", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));

        // Recovery on the healthy transport: the durable pending set drives
        // the tombstone re-delivery; B9 keeps unchanged fav 1 at home.
        WebUiSyncEngine.Result recovery =
                engineA.syncInternal(config, android, first.serverTimestamp);
        assertEquals(1, recovery.pushedFavorites); // the tombstone for fav 2 only
        assertTrue("tombstone delivered, priority deletion wins on the server",
                server.favorites.get(2L).deleted);
        // Only the fully successful cycle clears pending and advances the snapshot.
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".pending.favorites"));
        assertEquals("1", storeA.prefs.get(config.baseUrl() + ".snapshot.favorites"));

        // Converged: nothing more to push, deletion stays.
        WebUiSyncEngine.Result steady =
                engineA.syncInternal(config, android, recovery.serverTimestamp);
        assertEquals(0, steady.pushedFavorites);
        assertTrue(server.favorites.get(2L).deleted);
    }

    // ==================== ehSession (ADR-0004) ====================
    // The singleton EH login session: local cookies + settings are pushed as a
    // SyncEhSession (full cookie set, not just the identity pair), pulled
    // sessions write the cookie jar + Settings, a deleted tombstone signs the
    // device out, and the push ledger tracks a content fingerprint so a pulled
    // session is not echoed back while real changes propagate.

    /** In-memory EhSessionSource mirroring SiteEhSessionSource semantics. */
    static final class FakeEhSession implements WebUiSyncEngine.EhSessionSource {
        final Map<String, WebUiSyncModels.SyncEhCookie> cookies = new LinkedHashMap<>();
        String displayName;
        String avatar;
        Integer gallerySite;
        boolean needSignIn = true;

        void putCookie(String name, String value) {
            WebUiSyncModels.SyncEhCookie c = new WebUiSyncModels.SyncEhCookie();
            c.name = name;
            c.value = value;
            c.domain = "e-hentai.org";
            c.path = "/";
            c.expiresAt = Long.MAX_VALUE;
            c.persistent = true;
            cookies.put(name, c);
        }

        boolean signedIn() {
            return cookies.containsKey(SiteCookieStore.KEY_IPD_MEMBER_ID)
                    && cookies.containsKey(SiteCookieStore.KEY_IPD_PASS_HASH);
        }

        @Override
        public WebUiSyncModels.SyncEhSession loadLocal() {
            if (cookies.isEmpty()) {
                return null;
            }
            WebUiSyncModels.SyncEhSession s = new WebUiSyncModels.SyncEhSession();
            s.cookies = new ArrayList<>(cookies.values());
            s.displayName = displayName;
            s.avatar = avatar;
            s.gallerySite = gallerySite;
            return s;
        }

        @Override
        public void applyRemote(WebUiSyncModels.SyncEhSession session) {
            if (session.deleted) {
                cookies.clear();
                displayName = null;
                avatar = null;
                gallerySite = null;
                needSignIn = true;
                return;
            }
            cookies.clear();
            for (WebUiSyncModels.SyncEhCookie c : session.cookies) {
                cookies.put(c.name, c);
            }
            displayName = session.displayName;
            avatar = session.avatar;
            if (session.gallerySite != null) {
                gallerySite = session.gallerySite;
            }
        }
    }

    @Test
    public void ehSession_localSessionPushed() throws IOException {
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "12345");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        sessionA.putCookie(SiteCookieStore.KEY_IGNEOUS, "igneA");
        sessionA.displayName = "Alice";
        sessionA.avatar = "https://ehgt.org/alice.png";
        sessionA.gallerySite = 1;
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);

        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedEhSessions);
        assertNotNull(server.ehSessions.get("session"));
        WebUiSyncModels.SyncEhSession wire =
                (WebUiSyncModels.SyncEhSession) server.ehSessions.get("session").dto;
        assertFalse(wire.deleted);
        // The full cookie set rides along, not just the identity pair.
        assertEquals(3, wire.cookies.size());
        assertTrue(wire.lastModified > 0);
        assertEquals("devA", wire.deviceId);
        assertEquals("Alice", wire.displayName);
        assertEquals("https://ehgt.org/alice.png", wire.avatar);
        assertEquals(Integer.valueOf(1), wire.gallerySite);
    }

    @Test
    public void ehSession_remoteSessionAppliedWritesCookiesAndSettings() throws IOException {
        // Seed the server with a live session from device A.
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "111");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        sessionA.putCookie(SiteCookieStore.KEY_IGNEOUS, "igneA");
        sessionA.displayName = "Alice";
        sessionA.avatar = "https://ehgt.org/alice.png";
        sessionA.gallerySite = 1;
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);
        engineA.syncInternal(config, "devA", 0);

        // Fresh device B pulls and applies the session into its cookie jar and
        // settings.
        FakeEhSession sessionB = new FakeEhSession();
        WebUiSyncEngine engineB = new WebUiSyncEngine(storeB, server, sessionB);
        WebUiSyncEngine.Result pull = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, pull.pulledEhSessions);
        assertTrue(sessionB.signedIn());
        assertEquals("hashA", sessionB.cookies.get(SiteCookieStore.KEY_IPD_PASS_HASH).value);
        assertEquals("igneA", sessionB.cookies.get(SiteCookieStore.KEY_IGNEOUS).value);
        assertEquals("Alice", sessionB.displayName);
        assertEquals("https://ehgt.org/alice.png", sessionB.avatar);
        assertEquals(Integer.valueOf(1), sessionB.gallerySite);
    }

    @Test
    public void ehSession_logoutTombstoneSignsOutRemoteDevice() throws IOException {
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "111");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        sessionA.displayName = "Alice";
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedEhSessions);

        // Device B adopts the session.
        FakeEhSession sessionB = new FakeEhSession();
        sessionB.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "111");
        sessionB.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        sessionB.displayName = "Alice";
        WebUiSyncEngine engineB = new WebUiSyncEngine(storeB, server, sessionB);
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, bFirst.pulledEhSessions);
        assertTrue(sessionB.signedIn());

        // A logs out: the next cycle detects the missing session and pushes a
        // deleted tombstone.
        sessionA.cookies.clear();
        sessionA.displayName = null;
        sessionA.avatar = null;
        WebUiSyncEngine.Result del = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(1, del.pushedEhSessions);
        assertTrue(server.ehSessions.get("session").deleted);
        // Converged: the signed-out device pushes nothing more.
        assertEquals("", storeA.prefs.get(config.baseUrl() + ".snapshot.ehSession"));

        // B pulls the tombstone and signs out locally: cookies cleared plus
        // display_name/avatar cleared and need_sign_in=true.
        WebUiSyncEngine.Result bSecond = engineB.syncInternal(config, "devB", bFirst.serverTimestamp);
        assertEquals(1, bSecond.pulledEhSessions);
        assertFalse(sessionB.signedIn());
        assertNull(sessionB.displayName);
        assertNull(sessionB.avatar);
        assertTrue(sessionB.needSignIn);
    }

    @Test
    public void ehSession_twoDeviceLww_lastSyncerWins() throws IOException {
        // A and B hold different sessions. B (the later syncer) must win: A's
        // unchanged session is not re-pushed, so B's session is pulled and
        // adopted by A.
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "1");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);
        WebUiSyncEngine.Result aFirst = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, aFirst.pushedEhSessions);

        FakeEhSession sessionB = new FakeEhSession();
        sessionB.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "2");
        sessionB.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashB");
        WebUiSyncEngine engineB = new WebUiSyncEngine(storeB, server, sessionB);
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, bFirst.pushedEhSessions);
        // B's push carried a newer lastModified, so its session won the LWW on
        // the server.
        assertEquals("hashB", ((WebUiSyncModels.SyncEhSession)
                server.ehSessions.get("session").dto)
                .cookies.get(1).value);

        // A's second cycle sends nothing (fingerprint unchanged) and adopts B's
        // session from the pull — the later syncer wins, with no echo back.
        WebUiSyncEngine.Result aSecond = engineA.syncInternal(config, "devA", aFirst.serverTimestamp);
        assertEquals(0, aSecond.pushedEhSessions);
        assertEquals(1, aSecond.pulledEhSessions);
        assertEquals("hashB", sessionA.cookies.get(SiteCookieStore.KEY_IPD_PASS_HASH).value);
        assertTrue(sessionA.signedIn());

        // Converged: neither device re-pushes the adopted session.
        WebUiSyncEngine.Result bSecond = engineB.syncInternal(config, "devB", bFirst.serverTimestamp);
        assertEquals(0, bSecond.pushedEhSessions);
        assertEquals(0, bSecond.pulledEhSessions);
    }

    @Test
    public void ehSession_unchangedNotRepushed_cookieRotationIs() throws IOException {
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "1");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertEquals(1, first.pushedEhSessions);

        // Unchanged session: nothing crosses the wire.
        WebUiSyncEngine.Result second = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(0, second.pushedEhSessions);
        assertEquals(0, second.pulledEhSessions);

        // The server rotates igneous: the changed fingerprint pushes the
        // updated session.
        sessionA.putCookie(SiteCookieStore.KEY_IGNEOUS, "igneNew");
        WebUiSyncEngine.Result third = engineA.syncInternal(config, "devA", second.serverTimestamp);
        assertEquals(1, third.pushedEhSessions);
        assertEquals(3, ((WebUiSyncModels.SyncEhSession) server.ehSessions.get("session").dto)
                .cookies.size());
    }

    @Test
    public void ehSession_reloginAfterLogoutResurrects() throws IOException {
        FakeEhSession sessionA = new FakeEhSession();
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "1");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashA");
        WebUiSyncEngine engineA = new WebUiSyncEngine(storeA, server, sessionA);
        WebUiSyncEngine.Result first = engineA.syncInternal(config, "devA", 0);
        assertFalse(server.ehSessions.get("session").deleted);

        // Log out -> tombstone.
        sessionA.cookies.clear();
        sessionA.displayName = null;
        sessionA.avatar = null;
        WebUiSyncEngine.Result del = engineA.syncInternal(config, "devA", first.serverTimestamp);
        assertEquals(1, del.pushedEhSessions);
        assertTrue(server.ehSessions.get("session").deleted);

        // Log back in with fresh credentials: the newer live push resurrects
        // the server record.
        sessionA.putCookie(SiteCookieStore.KEY_IPD_MEMBER_ID, "3");
        sessionA.putCookie(SiteCookieStore.KEY_IPD_PASS_HASH, "hashC");
        sessionA.displayName = "Carol";
        WebUiSyncEngine.Result relogin = engineA.syncInternal(config, "devA", del.serverTimestamp);
        assertEquals(1, relogin.pushedEhSessions);
        WebUiSyncModels.SyncEhSession wire =
                (WebUiSyncModels.SyncEhSession) server.ehSessions.get("session").dto;
        assertFalse(wire.deleted);
        assertEquals("Carol", wire.displayName);

        // A fresh device adopts the resurrected session.
        FakeEhSession sessionB = new FakeEhSession();
        WebUiSyncEngine engineB = new WebUiSyncEngine(storeB, server, sessionB);
        WebUiSyncEngine.Result bFirst = engineB.syncInternal(config, "devB", 0);
        assertEquals(1, bFirst.pulledEhSessions);
        assertTrue(sessionB.signedIn());
        assertEquals("hashC", sessionB.cookies.get(SiteCookieStore.KEY_IPD_PASS_HASH).value);
    }
}
