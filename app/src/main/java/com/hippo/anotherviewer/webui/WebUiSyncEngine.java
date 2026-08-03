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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Timestamp-incremental sync against the WebUI server for all seven entities:
 * favorites (union merge), history (last-write-wins), downloads (union merge +
 * status sync), bookmarks (last-write-wins, hard delete), filters (union merge,
 * soft delete), quick searches (union merge, soft delete) and download labels
 * (union merge, soft delete). Implements the push → pull → apply cycle from
 * sync-conflict-rules.md §6, reusing the existing GreenDAO storage via
 * SiteDB (behind the {@link WebUiSyncStore} seam).
 *
 * <p>Deletion propagation: local removals are detected by diffing the current
 * local key sets against the keys pushed in the last successful sync (persisted
 * per server URL in SharedPreferences). Removed keys are pushed as
 * {@code deleted: true} tombstones — soft for favorites, downloads, filters,
 * quick searches and download labels (the server keeps the record alive so
 * other devices can resurrect it, per union semantics) and hard for history and
 * bookmarks (the server deletes the record). Incoming server tombstones are
 * honored locally without re-adding. Re-adding a locally-deleted key drops it
 * from the pending deletions so it is pushed alive again instead of being
 * re-deleted.
 *
 * <p>The push phase is chunked: downloads — the only realistically huge entity
 * — are split into batches of {@link #PUSH_BATCH_SIZE} records (live records
 * and tombstones alike); all other entities ride along on the first batch.
 * Every batch must be accepted for the sync to proceed.
 *
 * <p>All methods are synchronous and must run off the main thread.
 *
 * <p>The engine talks to two seams so the full cycle is JVM-testable:
 * {@link WebUiSyncStore} for data (production: {@link SiteDbWebUiSyncStore}
 * wrapping SiteDB + SharedPreferences; tests: an in-memory Map store) and
 * {@link WebUiSyncTransport} for the network (production:
 * {@link WebUiApiSyncTransport} wrapping WebUiApiClient; tests: an in-memory
 * fake server). The static {@link #sync} facade keeps the production call
 * sites unchanged; the instance entry point {@link #syncInternal} is what the
 * tests drive.
 */
public final class WebUiSyncEngine {

    private static final String SUFFIX_SNAPSHOT_FAVORITES = ".snapshot.favorites";
    private static final String SUFFIX_SNAPSHOT_HISTORY = ".snapshot.history";
    private static final String SUFFIX_PENDING_FAVORITES = ".pending.favorites";
    private static final String SUFFIX_PENDING_HISTORY = ".pending.history";
    private static final String SUFFIX_SNAPSHOT_DOWNLOADS = ".snapshot.downloads";
    private static final String SUFFIX_PENDING_DOWNLOADS = ".pending.downloads";
    private static final String SUFFIX_SNAPSHOT_BOOKMARKS = ".snapshot.bookmarks";
    private static final String SUFFIX_PENDING_BOOKMARKS = ".pending.bookmarks";
    private static final String SUFFIX_SNAPSHOT_FILTERS = ".snapshot.filters";
    private static final String SUFFIX_PENDING_FILTERS = ".pending.filters";
    private static final String SUFFIX_SNAPSHOT_QUICK_SEARCHES = ".snapshot.quickSearches";
    private static final String SUFFIX_PENDING_QUICK_SEARCHES = ".pending.quickSearches";
    private static final String SUFFIX_SNAPSHOT_DOWNLOAD_LABELS = ".snapshot.downloadLabels";
    private static final String SUFFIX_PENDING_DOWNLOAD_LABELS = ".pending.downloadLabels";
    private static final String KEY_SEPARATOR = "|";
    private static final int PUSH_BATCH_SIZE = 500;

    private static volatile WebUiSyncEngine sInstance;

    private final WebUiSyncStore mStore;
    private final WebUiSyncTransport mTransport;

    private WebUiSyncEngine() {
        this(new SiteDbWebUiSyncStore(), new WebUiApiSyncTransport());
    }

    /**
     * Test-facing constructor: supplies the data and network seams directly so
     * a sync cycle can run without Android or the real server.
     */
    WebUiSyncEngine(WebUiSyncStore store, WebUiSyncTransport transport) {
        mStore = store;
        mTransport = transport;
    }

    private static WebUiSyncEngine instance() {
        WebUiSyncEngine engine = sInstance;
        if (engine == null) {
            synchronized (WebUiSyncEngine.class) {
                engine = sInstance;
                if (engine == null) {
                    engine = new WebUiSyncEngine();
                    sInstance = engine;
                }
            }
        }
        return engine;
    }

    public static final class Result {
        public int pushedFavorites;
        public int pushedHistory;
        public int pulledFavorites;
        public int pulledHistory;
        public int pushedDownloads, pulledDownloads, pushedBookmarks, pulledBookmarks;
        public int pushedFilters, pulledFilters, pushedQuickSearches, pulledQuickSearches;
        public int pushedDownloadLabels, pulledDownloadLabels;
        public long serverTimestamp;
    }

    /**
     * Runs a full sync cycle. {@code since} is the persisted high-water mark
     * (0 on first sync); the returned {@link Result#serverTimestamp} should be
     * persisted by the caller for the next run.
     */
    @NonNull
    public static Result sync(@NonNull WebUiConfig config, @NonNull String deviceId, long since) throws IOException {
        return instance().syncInternal(config, deviceId, since);
    }

    /**
     * Instance sync entry point: the static facade delegates here. Tests drive
     * this directly against an in-memory store/transport.
     */
    @NonNull
    Result syncInternal(@NonNull WebUiConfig config, @NonNull String deviceId, long since) throws IOException {
        Result result = new Result();
        String serverKey = config.baseUrl();

        // Keys pushed by the last successful sync, plus deletions detected but
        // not yet delivered. Diffed against the current local keys to find keys
        // removed locally since the last push; keys re-added locally (present in
        // both the pending set and the local set) are dropped automatically.
        Set<Long> snapshotFavorites = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES);
        Set<Long> snapshotHistory = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY);
        Set<Long> snapshotDownloads = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOADS);
        Set<Long> snapshotBookmarks = mStore.loadKeySet(serverKey, SUFFIX_SNAPSHOT_BOOKMARKS);
        Set<String> snapshotFilters = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_FILTERS);
        Set<String> snapshotQuickSearches = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_QUICK_SEARCHES);
        Set<String> snapshotDownloadLabels = mStore.loadStringKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOAD_LABELS);

        Set<Long> pendingFavorites = mStore.loadKeySet(serverKey, SUFFIX_PENDING_FAVORITES);
        Set<Long> pendingHistory = mStore.loadKeySet(serverKey, SUFFIX_PENDING_HISTORY);
        Set<Long> pendingDownloads = mStore.loadKeySet(serverKey, SUFFIX_PENDING_DOWNLOADS);
        Set<Long> pendingBookmarks = mStore.loadKeySet(serverKey, SUFFIX_PENDING_BOOKMARKS);
        Set<String> pendingFilters = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_FILTERS);
        Set<String> pendingQuickSearches = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_QUICK_SEARCHES);
        Set<String> pendingDownloadLabels = mStore.loadStringKeySet(serverKey, SUFFIX_PENDING_DOWNLOAD_LABELS);

        Set<Long> currentFavorites = collectFavoriteKeys();
        Set<Long> currentHistory = collectHistoryKeys();
        Set<Long> currentDownloads = collectDownloadKeys();
        Set<Long> currentBookmarks = collectBookmarkKeys();
        Set<String> currentFilters = collectFilterKeys();
        Set<String> currentQuickSearches = collectQuickSearchKeys();
        Set<String> currentDownloadLabels = collectDownloadLabelKeys();

        pendingFavorites = detectDeletions(snapshotFavorites, pendingFavorites, currentFavorites);
        pendingHistory = detectDeletions(snapshotHistory, pendingHistory, currentHistory);
        pendingDownloads = detectDeletions(snapshotDownloads, pendingDownloads, currentDownloads);
        pendingBookmarks = detectDeletions(snapshotBookmarks, pendingBookmarks, currentBookmarks);
        pendingFilters = detectDeletions(snapshotFilters, pendingFilters, currentFilters);
        pendingQuickSearches = detectDeletions(snapshotQuickSearches, pendingQuickSearches, currentQuickSearches);
        pendingDownloadLabels = detectDeletions(snapshotDownloadLabels, pendingDownloadLabels, currentDownloadLabels);

        // 1. Push local state, including tombstones for pending deletions.
        // Downloads are chunked so no request carries more than
        // PUSH_BATCH_SIZE of them; the push succeeds only if every batch is
        // accepted, otherwise the pending sets are left intact for a retry.
        List<WebUiSyncModels.PushRequest> requests = buildPushRequests(deviceId,
                pendingFavorites, pendingHistory, pendingDownloads, pendingBookmarks,
                pendingFilters, pendingQuickSearches, pendingDownloadLabels);
        for (WebUiSyncModels.PushRequest push : requests) {
            WebUiSyncModels.PushResponse pushResponse = mTransport.push(config, push);
            if (!pushResponse.success) {
                throw new IOException("Server rejected push");
            }
            WebUiSyncModels.EntityCollection entities = push.entities;
            result.pushedFavorites += entities.favorites.size();
            result.pushedHistory += entities.history.size();
            result.pushedDownloads += entities.downloads.size();
            result.pushedBookmarks += entities.bookmarks.size();
            result.pushedFilters += entities.filters.size();
            result.pushedQuickSearches += entities.quickSearches.size();
            result.pushedDownloadLabels += entities.downloadLabels.size();
        }

        // The push is the new baseline; the pending deletions were delivered.
        // The snapshot is finalized after the pull so keys removed locally by
        // incoming server tombstones are not re-emitted next cycle.
        snapshotFavorites = currentFavorites;
        snapshotHistory = currentHistory;
        snapshotDownloads = currentDownloads;
        snapshotBookmarks = currentBookmarks;
        snapshotFilters = currentFilters;
        snapshotQuickSearches = currentQuickSearches;
        snapshotDownloadLabels = currentDownloadLabels;

        // 2. Pull server changes since the high-water mark.
        WebUiSyncModels.PullResponse pull = mTransport.pull(config, since);

        // 3. Apply pulled changes locally.
        applyFavorites(pull.entities.favorites, result, snapshotFavorites);
        applyHistory(pull.entities.history, result, snapshotHistory);
        applyDownloads(pull.entities.downloads, result, snapshotDownloads);
        applyBookmarks(pull.entities.bookmarks, result, snapshotBookmarks);
        applyFilters(pull.entities.filters, result, snapshotFilters);
        applyQuickSearches(pull.entities.quickSearches, result, snapshotQuickSearches);
        applyDownloadLabels(pull.entities.downloadLabels, result, snapshotDownloadLabels);

        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_FAVORITES, snapshotFavorites);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_HISTORY, snapshotHistory);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOADS, snapshotDownloads);
        mStore.saveKeySet(serverKey, SUFFIX_SNAPSHOT_BOOKMARKS, snapshotBookmarks);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_FILTERS, snapshotFilters);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_QUICK_SEARCHES, snapshotQuickSearches);
        mStore.saveStringKeySet(serverKey, SUFFIX_SNAPSHOT_DOWNLOAD_LABELS, snapshotDownloadLabels);
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_FAVORITES, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_HISTORY, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_DOWNLOADS, Collections.emptySet());
        mStore.saveKeySet(serverKey, SUFFIX_PENDING_BOOKMARKS, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_FILTERS, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_QUICK_SEARCHES, Collections.emptySet());
        mStore.saveStringKeySet(serverKey, SUFFIX_PENDING_DOWNLOAD_LABELS, Collections.emptySet());

        result.serverTimestamp = pull.serverTimestamp;
        return result;
    }

    private List<WebUiSyncModels.PushRequest> buildPushRequests(String deviceId,
            Set<Long> pendingFavorites, Set<Long> pendingHistory,
            Set<Long> pendingDownloads, Set<Long> pendingBookmarks,
            Set<String> pendingFilters, Set<String> pendingQuickSearches,
            Set<String> pendingDownloadLabels) {
        List<DownloadInfo> downloads = mStore.getAllDownloadInfo();
        List<Long> downloadTombstones = new ArrayList<>(pendingDownloads);
        long now = System.currentTimeMillis();

        // Downloads are the only realistically huge entity; chunk both live
        // records and tombstones into batches of PUSH_BATCH_SIZE. All other
        // entities are small and ride along on the first batch.
        int liveBatches = (downloads.size() + PUSH_BATCH_SIZE - 1) / PUSH_BATCH_SIZE;
        int tombBatches = (downloadTombstones.size() + PUSH_BATCH_SIZE - 1) / PUSH_BATCH_SIZE;
        int batchCount = Math.max(1, Math.max(liveBatches, tombBatches));

        List<WebUiSyncModels.PushRequest> requests = new ArrayList<>(batchCount);
        for (int i = 0; i < batchCount; i++) {
            WebUiSyncModels.PushRequest request = new WebUiSyncModels.PushRequest();
            request.deviceId = deviceId;
            request.timestamp = now;

            if (i == 0) {
                fillFavorites(request.entities, deviceId, now, pendingFavorites);
                fillHistory(request.entities, deviceId, now, pendingHistory);
                fillBookmarks(request.entities, deviceId, now, pendingBookmarks);
                fillFilters(request.entities, deviceId, now, pendingFilters);
                fillQuickSearches(request.entities, deviceId, now, pendingQuickSearches);
                fillDownloadLabels(request.entities, deviceId, now, pendingDownloadLabels);
            }

            int from = i * PUSH_BATCH_SIZE;
            int to = Math.min(from + PUSH_BATCH_SIZE, downloads.size());
            for (int j = from; j < to; j++) {
                fillDownload(request.entities, deviceId, now, downloads.get(j));
            }
            int tFrom = i * PUSH_BATCH_SIZE;
            int tTo = Math.min(tFrom + PUSH_BATCH_SIZE, downloadTombstones.size());
            for (int j = tFrom; j < tTo; j++) {
                // Soft tombstone: the server stores it only when no live record
                // exists (union merge, contract §3.3).
                WebUiSyncModels.SyncDownload dto = new WebUiSyncModels.SyncDownload();
                dto.gid = downloadTombstones.get(j);
                dto.lastModified = now;
                dto.deviceId = deviceId;
                dto.deleted = true;
                request.entities.downloads.add(dto);
            }
            requests.add(request);
        }
        return requests;
    }

    private void fillFavorites(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<Long> pendingFavorites) {
        for (GalleryInfo gi : mStore.getAllLocalFavorites()) {
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            copyGalleryToDto(gi, fav);
            long time = gi instanceof LocalFavoriteInfo ? ((LocalFavoriteInfo) gi).time : now;
            fav.time = time;
            fav.lastModified = time;
            fav.deviceId = deviceId;
            fav.deleted = false;
            entities.favorites.add(fav);
        }
        for (long gid : pendingFavorites) {
            // Soft tombstone: the server stores it only when no live record
            // exists (union merge, contract §3.1/§4.1).
            WebUiSyncModels.SyncFavorite fav = new WebUiSyncModels.SyncFavorite();
            fav.gid = gid;
            fav.lastModified = now;
            fav.deviceId = deviceId;
            fav.deleted = true;
            entities.favorites.add(fav);
        }
    }

    private void fillHistory(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<Long> pendingHistory) {
        for (HistoryInfo hi : mStore.getAllHistoryForSync()) {
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            copyGalleryToDto(hi, hist);
            hist.mode = hi.mode;
            hist.time = hi.time;
            hist.lastModified = hi.time;
            hist.deviceId = deviceId;
            hist.deleted = false;
            entities.history.add(hist);
        }
        for (long gid : pendingHistory) {
            // Hard-delete tombstone: the server removes the record entirely
            // (mergeHistory deletes on deleted=true, contract §3.2/§4.2).
            WebUiSyncModels.SyncHistory hist = new WebUiSyncModels.SyncHistory();
            hist.gid = gid;
            hist.lastModified = now;
            hist.deviceId = deviceId;
            hist.deleted = true;
            entities.history.add(hist);
        }
    }

    private void fillDownload(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, DownloadInfo info) {
        WebUiSyncModels.SyncDownload dto = new WebUiSyncModels.SyncDownload();
        copyGalleryToDto(info, dto);
        dto.state = info.state;
        dto.legacy = info.legacy;
        dto.time = info.time;
        dto.label = info.label;
        dto.total = info.total;
        dto.finished = info.finished;
        dto.downloaded = info.downloaded;
        // B2: lastModified is stamped by DownloadManager on every state change;
        // fall back to the record's time for pre-v8 data where the column is 0.
        dto.lastModified = info.lastModified > 0 ? info.lastModified : info.time;
        dto.deviceId = deviceId;
        dto.deleted = false;
        entities.downloads.add(dto);
    }

    private void fillBookmarks(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<Long> pendingBookmarks) {
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            WebUiSyncModels.SyncBookmark dto = new WebUiSyncModels.SyncBookmark();
            copyGalleryToDto(bi, dto);
            dto.page = bi.page;
            dto.time = bi.time;
            dto.lastModified = bi.time;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.bookmarks.add(dto);
        }
        for (long gid : pendingBookmarks) {
            // Hard-delete tombstone: the server removes the row entirely
            // (mergeBookmark deletes on deleted=true, contract §3.4/§4.4).
            WebUiSyncModels.SyncBookmark dto = new WebUiSyncModels.SyncBookmark();
            dto.gid = gid;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.bookmarks.add(dto);
        }
    }

    private void fillFilters(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingFilters) {
        for (Filter f : mStore.getAllFilter()) {
            WebUiSyncModels.SyncFilter dto = new WebUiSyncModels.SyncFilter();
            dto.mode = f.mode;
            dto.text = f.text;
            dto.enabled = f.enable != null && f.enable;
            // Local filters carry no timestamp; the push side is always newest.
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.filters.add(dto);
        }
        for (String key : pendingFilters) {
            // Composite key "mode|text" (filter text never contains '|').
            WebUiSyncModels.SyncFilter dto = new WebUiSyncModels.SyncFilter();
            int sep = key.indexOf(KEY_SEPARATOR);
            try {
                dto.mode = Integer.parseInt(key.substring(0, sep));
            } catch (NumberFormatException | IndexOutOfBoundsException ignored) {
                // Skip malformed tombstone keys; the set is rebuilt on save.
                continue;
            }
            dto.text = key.substring(sep + 1);
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.filters.add(dto);
        }
    }

    private void fillQuickSearches(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingQuickSearches) {
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            WebUiSyncModels.SyncQuickSearch dto = new WebUiSyncModels.SyncQuickSearch();
            dto.name = qs.name;
            dto.mode = qs.mode;
            dto.category = qs.category;
            dto.keyword = qs.keyword;
            dto.advanceSearch = qs.advanceSearch;
            dto.minRating = qs.minRating;
            dto.pageFrom = qs.pageFrom;
            dto.pageTo = qs.pageTo;
            dto.time = qs.time;
            dto.lastModified = qs.time;
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.quickSearches.add(dto);
        }
        for (String name : pendingQuickSearches) {
            // Soft tombstone (union merge, contract §3.6).
            WebUiSyncModels.SyncQuickSearch dto = new WebUiSyncModels.SyncQuickSearch();
            dto.name = name;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.quickSearches.add(dto);
        }
    }

    private void fillDownloadLabels(WebUiSyncModels.EntityCollection entities,
            String deviceId, long now, Set<String> pendingDownloadLabels) {
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            WebUiSyncModels.SyncDownloadLabel dto = new WebUiSyncModels.SyncDownloadLabel();
            dto.label = dl.getLabel();
            dto.time = dl.getTime();
            dto.lastModified = dl.getTime();
            dto.deviceId = deviceId;
            dto.deleted = false;
            entities.downloadLabels.add(dto);
        }
        for (String label : pendingDownloadLabels) {
            // Soft tombstone (union merge, contract §3.7).
            WebUiSyncModels.SyncDownloadLabel dto = new WebUiSyncModels.SyncDownloadLabel();
            dto.label = label;
            dto.lastModified = now;
            dto.deviceId = deviceId;
            dto.deleted = true;
            entities.downloadLabels.add(dto);
        }
    }

    /**
     * Favorites use union merge. Incoming tombstones remove the local copy
     * without re-adding; alive records are inserted when absent; existing
     * favorites get their metadata refreshed when the server copy is newer
     * (last-write-wins on lastModified, local add time is preserved).
     */
    private void applyFavorites(List<WebUiSyncModels.SyncFavorite> favorites,
            Result result, Set<Long> snapshotFavorites) {
        for (WebUiSyncModels.SyncFavorite fav : favorites) {
            if (fav.deleted) {
                // Tombstone: this device has deleted the favorite (or another
                // device did) — honor it locally and never re-add it.
                mStore.removeLocalFavorites(fav.gid);
                snapshotFavorites.remove(fav.gid);
                result.pulledFavorites++;
                continue;
            }
            LocalFavoriteInfo local = mStore.loadLocalFavorite(fav.gid);
            if (local == null) {
                LocalFavoriteInfo info = new LocalFavoriteInfo();
                copyDtoToGallery(fav, info);
                info.time = fav.time;
                mStore.putLocalFavorite(info);
                result.pulledFavorites++;
            } else if (fav.lastModified > local.time) {
                // Server copy is newer — refresh metadata (the local favorite's
                // push lastModified is its add time, so time is the comparison).
                copyDtoToGallery(fav, local);
                mStore.updateLocalFavorite(local);
                result.pulledFavorites++;
            }
        }
    }

    /** History uses last-write-wins with hard-delete. */
    private void applyHistory(List<WebUiSyncModels.SyncHistory> history,
            Result result, Set<Long> snapshotHistory) {
        for (WebUiSyncModels.SyncHistory hist : history) {
            if (hist.deleted) {
                mStore.removeHistoryByKey(hist.gid);
                snapshotHistory.remove(hist.gid);
                result.pulledHistory++;
                continue;
            }
            HistoryInfo info = new HistoryInfo();
            copyDtoToGallery(hist, info);
            info.mode = hist.mode;
            info.time = hist.time;
            mStore.applySyncedHistory(info);
            result.pulledHistory++;
        }
    }

    /**
     * Downloads follow the server unconditionally: incoming tombstones remove
     * the local record, alive records overwrite every synced field via
     * {@code putDownloadInfo} (the server already resolved union/LWW conflicts).
     */
    private void applyDownloads(List<WebUiSyncModels.SyncDownload> downloads,
            Result result, Set<Long> snapshotDownloads) {
        Map<Long, DownloadInfo> locals = new HashMap<>();
        for (DownloadInfo info : mStore.getAllDownloadInfo()) {
            locals.put(info.gid, info);
        }
        for (WebUiSyncModels.SyncDownload dto : downloads) {
            if (dto.deleted) {
                // Soft tombstone delivered: the server no longer tracks it.
                mStore.removeDownloadInfo(dto.gid);
                snapshotDownloads.remove(dto.gid);
                result.pulledDownloads++;
                continue;
            }
            DownloadInfo info = new DownloadInfo();
            copyDtoToDownload(dto, info);
            DownloadInfo existing = locals.get(dto.gid);
            if (existing != null) {
                // Keep the local archive URI (not part of the wire model).
                info.archiveUri = existing.archiveUri;
            }
            mStore.putDownloadInfo(info);
            locals.put(dto.gid, info);
            snapshotDownloads.add(dto.gid);
            result.pulledDownloads++;
        }
    }

    /** Bookmarks use last-write-wins on lastModified (local time) with hard-delete. */
    private void applyBookmarks(List<WebUiSyncModels.SyncBookmark> bookmarks,
            Result result, Set<Long> snapshotBookmarks) {
        Map<Long, BookmarkInfo> locals = new HashMap<>();
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            locals.put(bi.gid, bi);
        }
        for (WebUiSyncModels.SyncBookmark dto : bookmarks) {
            if (dto.deleted) {
                // Hard-delete tombstone: clearing a reading position propagates.
                mStore.removeBookmarkByGid(dto.gid);
                snapshotBookmarks.remove(dto.gid);
                result.pulledBookmarks++;
                continue;
            }
            BookmarkInfo local = locals.get(dto.gid);
            if (local == null) {
                BookmarkInfo info = new BookmarkInfo();
                copyDtoToGallery(dto, info);
                info.page = dto.page;
                info.time = dto.time;
                mStore.putBookmark(info);
                locals.put(dto.gid, info);
                snapshotBookmarks.add(dto.gid);
                result.pulledBookmarks++;
            } else if (dto.lastModified > local.time) {
                copyDtoToGallery(dto, local);
                local.page = dto.page;
                local.time = dto.time;
                mStore.putBookmark(local);
                snapshotBookmarks.add(dto.gid);
                result.pulledBookmarks++;
            }
        }
    }

    /**
     * Filters follow the server unconditionally (the local model has no
     * timestamp, so no LWW comparison is possible): the enable flag is applied
     * as-is to the existing (mode, text) row, or the row is created.
     */
    private void applyFilters(List<WebUiSyncModels.SyncFilter> filters,
            Result result, Set<String> snapshotFilters) {
        for (WebUiSyncModels.SyncFilter dto : filters) {
            String key = filterKey(dto.mode, dto.text);
            if (dto.deleted) {
                // Soft tombstone: remove the local row without re-adding it.
                mStore.deleteFilterByKey(dto.mode, dto.text);
                snapshotFilters.remove(key);
                result.pulledFilters++;
                continue;
            }
            Filter existing = mStore.findFilterByKey(dto.mode, dto.text);
            if (existing == null) {
                Filter filter = new Filter();
                filter.mode = dto.mode;
                filter.text = dto.text;
                filter.enable = dto.enabled;
                mStore.addFilter(filter);
            } else {
                setFilterEnabled(existing, dto.enabled);
            }
            snapshotFilters.add(key);
            result.pulledFilters++;
        }
    }

    /**
     * Applies the server's {@code enabled} flag to an existing filter row.
     * {@code triggerFilter} toggles, so it is only used when the current value
     * is known and differs; a null enable (meaning disabled) is replaced by
     * re-inserting the row when the server enables it, since toggling null
     * would throw.
     */
    private void setFilterEnabled(Filter filter, boolean enabled) {
        if (filter.enable == null) {
            if (enabled) {
                mStore.deleteFilterByKey(filter.mode, filter.text);
                Filter replacement = new Filter();
                replacement.mode = filter.mode;
                replacement.text = filter.text;
                replacement.enable = true;
                mStore.addFilter(replacement);
            }
        } else if (filter.enable != enabled) {
            mStore.triggerFilter(filter);
        }
    }

    /**
     * Quick searches follow the server unconditionally, keyed by name: deleted
     * tombstones remove the local row, alive records are inserted or updated
     * (keeping the local row id).
     */
    private void applyQuickSearches(List<WebUiSyncModels.SyncQuickSearch> searches,
            Result result, Set<String> snapshotQuickSearches) {
        Map<String, QuickSearch> locals = new HashMap<>();
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            locals.put(qs.name, qs);
        }
        for (WebUiSyncModels.SyncQuickSearch dto : searches) {
            if (dto.deleted) {
                QuickSearch existing = locals.remove(dto.name);
                if (existing != null) {
                    mStore.deleteQuickSearch(existing);
                }
                snapshotQuickSearches.remove(dto.name);
                result.pulledQuickSearches++;
                continue;
            }
            QuickSearch local = locals.get(dto.name);
            if (local == null) {
                QuickSearch qs = new QuickSearch();
                copyDtoToQuickSearch(dto, qs);
                mStore.insertQuickSearch(qs);
                locals.put(dto.name, qs);
            } else {
                copyDtoToQuickSearch(dto, local);
                mStore.updateQuickSearch(local);
            }
            snapshotQuickSearches.add(dto.name);
            result.pulledQuickSearches++;
        }
    }

    /**
     * Download labels follow the server unconditionally, keyed by label name:
     * deleted tombstones remove the local row, alive records are inserted with
     * the server's time or updated in place.
     */
    private void applyDownloadLabels(List<WebUiSyncModels.SyncDownloadLabel> labels,
            Result result, Set<String> snapshotDownloadLabels) {
        Map<String, DownloadLabel> locals = new HashMap<>();
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            locals.put(dl.getLabel(), dl);
        }
        for (WebUiSyncModels.SyncDownloadLabel dto : labels) {
            if (dto.deleted) {
                DownloadLabel existing = locals.remove(dto.label);
                if (existing != null) {
                    mStore.removeDownloadLabel(existing);
                }
                snapshotDownloadLabels.remove(dto.label);
                result.pulledDownloadLabels++;
                continue;
            }
            DownloadLabel local = locals.get(dto.label);
            if (local == null) {
                DownloadLabel dl = new DownloadLabel();
                dl.setLabel(dto.label);
                dl.setTime(dto.time);
                mStore.addDownloadLabel(dl);
                locals.put(dto.label, dl);
            } else {
                local.setLabel(dto.label);
                local.setTime(dto.time);
                mStore.updateDownloadLabel(local);
            }
            snapshotDownloadLabels.add(dto.label);
            result.pulledDownloadLabels++;
        }
    }

    /**
     * Keys pushed before (or already pending) that no longer exist locally are
     * deletions to propagate. Keys present locally again (re-added) are dropped
     * so the next push resurrects them instead of re-deleting them.
     */
    private <T> Set<T> detectDeletions(Set<T> snapshot, Set<T> pending, Set<T> current) {
        Set<T> deletions = new LinkedHashSet<>(snapshot);
        deletions.addAll(pending);
        deletions.removeAll(current);
        return deletions;
    }

    private Set<Long> collectFavoriteKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (GalleryInfo gi : mStore.getAllLocalFavorites()) {
            keys.add(gi.gid);
        }
        return keys;
    }

    private Set<Long> collectHistoryKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (HistoryInfo hi : mStore.getAllHistoryForSync()) {
            keys.add(hi.gid);
        }
        return keys;
    }

    private Set<Long> collectDownloadKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (DownloadInfo info : mStore.getAllDownloadInfo()) {
            keys.add(info.gid);
        }
        return keys;
    }

    private Set<Long> collectBookmarkKeys() {
        Set<Long> keys = new LinkedHashSet<>();
        for (BookmarkInfo bi : mStore.getAllBookmark()) {
            keys.add(bi.gid);
        }
        return keys;
    }

    private Set<String> collectFilterKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Filter f : mStore.getAllFilter()) {
            keys.add(filterKey(f.mode, f.text));
        }
        return keys;
    }

    private Set<String> collectQuickSearchKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (QuickSearch qs : mStore.getAllQuickSearch()) {
            keys.add(qs.name);
        }
        return keys;
    }

    private Set<String> collectDownloadLabelKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (DownloadLabel dl : mStore.getAllDownloadLabelList()) {
            keys.add(dl.getLabel());
        }
        return keys;
    }

    private String filterKey(int mode, String text) {
        return mode + KEY_SEPARATOR + (text == null ? "" : text);
    }

    private void copyGalleryToDto(GalleryInfo gi, WebUiSyncModels.GalleryBase dto) {
        dto.gid = gi.gid;
        dto.token = gi.token;
        dto.title = gi.title;
        dto.titleJpn = gi.titleJpn;
        dto.thumb = gi.thumb;
        dto.category = gi.category;
        dto.posted = gi.posted;
        dto.uploader = gi.uploader;
        dto.rating = gi.rating;
        dto.rated = gi.rated;
        dto.simpleLanguage = gi.simpleLanguage;
        dto.simpleTags = joinTags(gi.simpleTags);
        dto.thumbWidth = gi.thumbWidth;
        dto.thumbHeight = gi.thumbHeight;
        dto.spanSize = gi.spanSize;
        dto.spanIndex = gi.spanIndex;
        dto.spanGroupIndex = gi.spanGroupIndex;
        dto.favoriteSlot = gi.favoriteSlot;
        dto.favoriteName = gi.favoriteName;
        dto.pages = gi.pages;
    }

    private void copyDtoToGallery(WebUiSyncModels.GalleryBase dto, GalleryInfo gi) {
        gi.gid = dto.gid;
        gi.token = dto.token;
        gi.title = dto.title;
        gi.titleJpn = dto.titleJpn;
        gi.thumb = dto.thumb;
        gi.category = dto.category;
        gi.posted = dto.posted;
        gi.uploader = dto.uploader;
        gi.rating = dto.rating;
        gi.rated = dto.rated;
        gi.simpleLanguage = dto.simpleLanguage;
        gi.simpleTags = splitTags(dto.simpleTags);
        gi.thumbWidth = dto.thumbWidth;
        gi.thumbHeight = dto.thumbHeight;
        gi.spanSize = dto.spanSize;
        gi.spanIndex = dto.spanIndex;
        gi.spanGroupIndex = dto.spanGroupIndex;
        gi.favoriteSlot = dto.favoriteSlot;
        gi.favoriteName = dto.favoriteName;
        gi.pages = dto.pages;
    }

    private void copyDtoToDownload(WebUiSyncModels.SyncDownload dto, DownloadInfo info) {
        copyDtoToGallery(dto, info);
        info.state = dto.state;
        info.legacy = dto.legacy;
        info.time = dto.time;
        info.label = dto.label;
        info.total = dto.total;
        info.finished = dto.finished;
        info.downloaded = dto.downloaded;
    }

    private void copyDtoToQuickSearch(WebUiSyncModels.SyncQuickSearch dto, QuickSearch qs) {
        qs.name = dto.name;
        qs.mode = dto.mode;
        qs.category = dto.category;
        qs.keyword = dto.keyword;
        qs.advanceSearch = dto.advanceSearch;
        qs.minRating = dto.minRating;
        qs.pageFrom = dto.pageFrom;
        qs.pageTo = dto.pageTo;
        qs.time = dto.time;
    }

    private String joinTags(String[] tags) {
        if (tags == null || tags.length == 0) {
            return null;
        }
        return TextUtils.join(";", tags);
    }

    private String[] splitTags(String tags) {
        if (TextUtils.isEmpty(tags)) {
            return null;
        }
        return tags.split(";");
    }
}
