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

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory fake of the WebUI sync server for {@link WebUiSyncEngine} tests.
 *
 * <p>Merge model (mirrors the sync contract the engine was written against):
 * <ul>
 *   <li>union entities (favorites, downloads, filters, quick searches,
 *       download labels): live records and tombstones coexist; a live record
 *       replaces any tombstone for the same key; a tombstone is stored when no
 *       live record exists or the tombstone is newer.</li>
 *   <li>hard-delete entities (history, bookmarks): deleted records are removed,
 *       but a tombstone marker is kept long enough for the pull side so other
 *       devices observe the deletion (the real server retains tombstones for
 *       the same reason).</li>
 * </ul>
 * Every write bumps {@code serverModified}, and pull returns everything with
 * {@code serverModified > since} — the "changes since the watermark" contract.
 * The pushed {@code lastModified} values are preserved on the stored records.
 */
public class InMemorySyncServer implements WebUiSyncTransport {

    static final class Record {
        final long serverModified;
        final boolean deleted;
        final Object dto;

        Record(long serverModified, boolean deleted, Object dto) {
            this.serverModified = serverModified;
            this.deleted = deleted;
            this.dto = dto;
        }

        long dtoLastModified() {
            if (dto instanceof WebUiSyncModels.GalleryBase) {
                return ((WebUiSyncModels.GalleryBase) dto).lastModified;
            }
            if (dto instanceof WebUiSyncModels.SyncFilter) {
                return ((WebUiSyncModels.SyncFilter) dto).lastModified;
            }
            if (dto instanceof WebUiSyncModels.SyncQuickSearch) {
                return ((WebUiSyncModels.SyncQuickSearch) dto).lastModified;
            }
            return ((WebUiSyncModels.SyncDownloadLabel) dto).lastModified;
        }
    }

    public long serverTimestamp = 0;
    /** Set to {@code true} to make push fail with success=false. */
    public boolean rejectPushes = false;

    public final Map<Long, Record> favorites = new LinkedHashMap<>();
    public final Map<Long, Record> history = new LinkedHashMap<>();
    public final Map<Long, Record> downloads = new LinkedHashMap<>();
    public final Map<Long, Record> bookmarks = new LinkedHashMap<>();
    public final Map<String, Record> filters = new LinkedHashMap<>();
    public final Map<String, Record> quickSearches = new LinkedHashMap<>();
    public final Map<String, Record> downloadLabels = new LinkedHashMap<>();

    @NonNull
    @Override
    public WebUiSyncModels.PushResponse push(@NonNull WebUiConfig config,
            @NonNull WebUiSyncModels.PushRequest request) {
        WebUiSyncModels.PushResponse response = new WebUiSyncModels.PushResponse();
        if (rejectPushes) {
            response.success = false;
            return response;
        }
        // Strictly monotonic server clock (independent of the pushed wall-clock
        // timestamp) so a record's serverModified is always greater than any
        // watermark a device previously pulled with.
        serverTimestamp++;
        WebUiSyncModels.EntityCollection e = request.entities;
        for (WebUiSyncModels.SyncFavorite fav : e.favorites) {
            mergeUnion(favorites, fav.gid, fav, fav.lastModified, fav.deleted);
        }
        for (WebUiSyncModels.SyncHistory hist : e.history) {
            mergeHard(history, hist.gid, hist, hist.lastModified, hist.deleted);
        }
        for (WebUiSyncModels.SyncDownload dl : e.downloads) {
            mergeUnion(downloads, dl.gid, dl, dl.lastModified, dl.deleted);
        }
        for (WebUiSyncModels.SyncBookmark bm : e.bookmarks) {
            mergeHard(bookmarks, bm.gid, bm, bm.lastModified, bm.deleted);
        }
        for (WebUiSyncModels.SyncFilter f : e.filters) {
            mergeUnion(filters, filterKey(f.mode, f.text), f, f.lastModified, f.deleted);
        }
        for (WebUiSyncModels.SyncQuickSearch qs : e.quickSearches) {
            mergeUnion(quickSearches, qs.name, qs, qs.lastModified, qs.deleted);
        }
        for (WebUiSyncModels.SyncDownloadLabel dl : e.downloadLabels) {
            mergeUnion(downloadLabels, dl.label, dl, dl.lastModified, dl.deleted);
        }
        response.success = true;
        response.serverTimestamp = serverTimestamp;
        return response;
    }

    /**
     * Union-merge entities: a live record always wins over a tombstone for the
     * same key; a tombstone is stored only when no live record exists or the
     * tombstone is newer than the live record.
     */
    private void mergeUnion(Map<Long, Record> map, long key, Object dto, long lastModified, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            if (existing == null || existing.deleted || lastModified >= existing.dtoLastModified()) {
                map.put(key, new Record(serverTimestamp, true, dto));
            }
            return;
        }
        map.put(key, new Record(serverTimestamp, false, dto));
    }

    private void mergeUnion(Map<String, Record> map, String key, Object dto, long lastModified, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            if (existing == null || existing.deleted || lastModified >= existing.dtoLastModified()) {
                map.put(key, new Record(serverTimestamp, true, dto));
            }
            return;
        }
        map.put(key, new Record(serverTimestamp, false, dto));
    }

    /**
     * Hard-delete entities: deleted records are removed from the live state,
     * but a tombstone marker is kept (serverModified bumped) so the deletion
     * is observable through pull.
     */
    private void mergeHard(Map<Long, Record> map, long key, Object dto, long lastModified, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            map.put(key, new Record(serverTimestamp, true, dto));
            return;
        }
        if (existing != null && existing.deleted && existing.dtoLastModified() > lastModified) {
            // A newer tombstone than this live record: keep the deletion.
            return;
        }
        map.put(key, new Record(serverTimestamp, false, dto));
    }

    @NonNull
    @Override
    public WebUiSyncModels.PullResponse pull(@NonNull WebUiConfig config, long since) {
        WebUiSyncModels.PullResponse response = new WebUiSyncModels.PullResponse();
        fillFavoritePull(response.entities.favorites, favorites, since);
        fillHistoryPull(response.entities.history, history, since);
        fillDownloadPull(response.entities.downloads, downloads, since);
        fillBookmarkPull(response.entities.bookmarks, bookmarks, since);
        fillFilterPull(response.entities.filters, filters, since);
        fillQuickSearchPull(response.entities.quickSearches, quickSearches, since);
        fillDownloadLabelPull(response.entities.downloadLabels, downloadLabels, since);
        response.serverTimestamp = serverTimestamp;
        return response;
    }

    private static void fillFavoritePull(java.util.List<WebUiSyncModels.SyncFavorite> out,
            Map<Long, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncFavorite) record.dto);
            }
        }
    }

    private static void fillHistoryPull(java.util.List<WebUiSyncModels.SyncHistory> out,
            Map<Long, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncHistory) record.dto);
            }
        }
    }

    private static void fillDownloadPull(java.util.List<WebUiSyncModels.SyncDownload> out,
            Map<Long, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncDownload) record.dto);
            }
        }
    }

    private static void fillBookmarkPull(java.util.List<WebUiSyncModels.SyncBookmark> out,
            Map<Long, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncBookmark) record.dto);
            }
        }
    }

    private static void fillFilterPull(java.util.List<WebUiSyncModels.SyncFilter> out,
            Map<String, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncFilter) record.dto);
            }
        }
    }

    private static void fillQuickSearchPull(java.util.List<WebUiSyncModels.SyncQuickSearch> out,
            Map<String, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncQuickSearch) record.dto);
            }
        }
    }

    private static void fillDownloadLabelPull(java.util.List<WebUiSyncModels.SyncDownloadLabel> out,
            Map<String, Record> map, long since) {
        for (Record record : map.values()) {
            if (record.serverModified > since) {
                out.add((WebUiSyncModels.SyncDownloadLabel) record.dto);
            }
        }
    }

    private static String filterKey(int mode, String text) {
        return mode + "|" + (text == null ? "" : text);
    }
}
