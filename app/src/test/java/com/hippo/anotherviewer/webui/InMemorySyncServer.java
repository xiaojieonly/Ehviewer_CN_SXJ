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
 * <p>Merge model — mirrors the REAL server merge (anotherviewer-web
 * {@code SyncService.kt}, contract v2 §1.4/§3.8), not a naive union:
 * <ul>
 *   <li>union entities (favorites, downloads, filters, quick searches,
 *       download labels): live-vs-live is arbitrated exactly like
 *       {@code mergeFavorite}'s same-state branch (A/C cross-platform →
 *       priority platform wins unconditionally; B / same-platform → LWW);
 *       live-vs-tombstone follows {@code softDeleteLiveWins} (B → any live
 *       resurrects; A/C → priority deletion is final, priority live beats a
 *       non-priority tomb, and when NEITHER side is priority the deletion
 *       propagates).</li>
 *   <li>hard-delete entities (history, bookmarks): deleted records are removed,
 *       but a tombstone marker is kept long enough for the pull side so other
 *       devices observe the deletion (the real server retains tombstones for
 *       the same reason). Resurrection is entity-LWW like v1 (§3.8 mirror):
 *       only a strictly newer live record revives the key.</li>
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
    /** When non-null, pull responses carry this policy (contract v2 §8). */
    public WebUiSyncModels.SyncPolicy policy = null;
    /** Records the policy carried by the most recent push (D2 assertions). */
    public WebUiSyncModels.SyncPolicy lastPushPolicy = null;
    /** Number of accepted push requests (B9: observe push traffic). */
    public int pushRequestCount = 0;
    /** Total entity records (live + tombstone) accepted across all pushes. */
    public long totalPushedEntities = 0;

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
        lastPushPolicy = request.policy;
        // Strictly monotonic server clock (independent of the pushed wall-clock
        // timestamp) so a record's serverModified is always greater than any
        // watermark a device previously pulled with.
        serverTimestamp++;
        WebUiSyncModels.EntityCollection e = request.entities;
        pushRequestCount++;
        totalPushedEntities += e.favorites.size() + e.history.size() + e.downloads.size()
                + e.bookmarks.size() + e.filters.size() + e.quickSearches.size()
                + e.downloadLabels.size();
        for (WebUiSyncModels.SyncFavorite fav : e.favorites) {
            mergeUnion(favorites, fav.gid, fav, fav.deleted);
        }
        for (WebUiSyncModels.SyncHistory hist : e.history) {
            mergeHard(history, hist.gid, hist, hist.lastModified, hist.deleted);
        }
        for (WebUiSyncModels.SyncDownload dl : e.downloads) {
            mergeUnion(downloads, dl.gid, dl, dl.deleted);
        }
        for (WebUiSyncModels.SyncBookmark bm : e.bookmarks) {
            mergeHard(bookmarks, bm.gid, bm, bm.lastModified, bm.deleted);
        }
        for (WebUiSyncModels.SyncFilter f : e.filters) {
            mergeUnion(filters, filterKey(f.mode, f.text), f, f.deleted);
        }
        for (WebUiSyncModels.SyncQuickSearch qs : e.quickSearches) {
            mergeUnion(quickSearches, qs.name, qs, qs.deleted);
        }
        for (WebUiSyncModels.SyncDownloadLabel dl : e.downloadLabels) {
            mergeUnion(downloadLabels, dl.label, dl, dl.deleted);
        }
        response.success = true;
        response.serverTimestamp = serverTimestamp;
        return response;
    }

    private WebUiSyncEngine.ConflictStrategy serverStrategy() {
        return policy != null
                ? WebUiSyncEngine.ConflictStrategy.parse(policy.conflictStrategy)
                : WebUiSyncEngine.ConflictStrategy.LWW;
    }

    private static String platformOfDto(Object dto) {
        if (dto instanceof WebUiSyncModels.GalleryBase) return WebUiSyncEngine.platformOf(((WebUiSyncModels.GalleryBase) dto).deviceId);
        if (dto instanceof WebUiSyncModels.SyncFilter) return WebUiSyncEngine.platformOf(((WebUiSyncModels.SyncFilter) dto).deviceId);
        if (dto instanceof WebUiSyncModels.SyncQuickSearch) return WebUiSyncEngine.platformOf(((WebUiSyncModels.SyncQuickSearch) dto).deviceId);
        return WebUiSyncEngine.platformOf(((WebUiSyncModels.SyncDownloadLabel) dto).deviceId);
    }

    private static long lastModifiedOf(Object dto) {
        if (dto instanceof WebUiSyncModels.GalleryBase) return ((WebUiSyncModels.GalleryBase) dto).lastModified;
        if (dto instanceof WebUiSyncModels.SyncFilter) return ((WebUiSyncModels.SyncFilter) dto).lastModified;
        if (dto instanceof WebUiSyncModels.SyncQuickSearch) return ((WebUiSyncModels.SyncQuickSearch) dto).lastModified;
        return ((WebUiSyncModels.SyncDownloadLabel) dto).lastModified;
    }

    /**
     * Incoming TOMBSTONE vs existing LIVE (mirror of SyncService
     * {@code softDeleteLiveWins}, inverted): B → the live record always
     * survives (v1 union); A/C → a priority-platform deletion propagates
     * unconditionally, a deletion never beats the priority platform's live
     * copy, and when neither side is priority the deletion propagates.
     */
    private boolean deletionWinsOverLive(Object tombDto, Record liveRecord) {
        WebUiSyncEngine.ConflictStrategy strategy = serverStrategy();
        String priority = strategy.priorityPlatform();
        if (priority == null) return false;
        String tombPlatform = platformOfDto(tombDto);
        String livePlatform = platformOfDto(liveRecord.dto);
        if (priority.equals(tombPlatform)) return true;
        if (priority.equals(livePlatform)) return false;
        return true;
    }

    /**
     * Incoming LIVE vs existing TOMBSTONE (mirror of {@code softDeleteLiveWins}):
     * B → any live push resurrects; A/C → a priority deletion is final (even
     * against a newer non-priority live), a priority live resurrects over a
     * non-priority tomb, and when neither side is priority the deletion
     * propagates (the tombstone survives).
     */
    private boolean liveWinsOverTombstone(Object liveDto, Record tombRecord) {
        WebUiSyncEngine.ConflictStrategy strategy = serverStrategy();
        String priority = strategy.priorityPlatform();
        if (priority == null) return true;
        String livePlatform = platformOfDto(liveDto);
        String tombPlatform = platformOfDto(tombRecord.dto);
        if (priority.equals(tombPlatform)) return false;
        if (priority.equals(livePlatform)) return true;
        return false;
    }

    /**
     * LIVE vs LIVE (mirror of the SyncService same-state branch): A/C
     * cross-platform → the priority platform wins unconditionally (§1.4);
     * B / same-platform → LWW fallback (§1.4 tie-break). The fake omits the
     * server's skew tolerance; tests use clearly distinct timestamps. On an
     * exact lastModified tie the incoming record REPLACES the existing one —
     * that rewrite bumps serverModified, which is what makes a re-pushed
     * record observable to other devices through incremental pulls.
     * F6 裁决（leader，2026-08-04）：B9 增量推送下 lww soft 删除粘性为预期语义；
     * 旧 §3.1「删端下轮被复活」系全量推送时代的偶然收敛机制（保留端全量重推
     * 回显），非设计承诺；复活须经保留端显式 re-add/重戳，作为新 lastModified
     * delta 即时传播。
     */
    private boolean incomingLiveWinsOverLive(Object incomingDto, Record existing) {
        WebUiSyncEngine.ConflictStrategy strategy = serverStrategy();
        String priority = strategy.priorityPlatform();
        String incomingPlatform = platformOfDto(incomingDto);
        String existingPlatform = platformOfDto(existing.dto);
        if (priority != null && !incomingPlatform.equals(existingPlatform)) {
            return priority.equals(incomingPlatform);
        }
        return lastModifiedOf(incomingDto) >= existing.dtoLastModified();
    }

    /** TOMB vs TOMB (mirror of the same-state branch): A/C cross-platform → priority wins; else LWW (tie → incoming). */
    private boolean incomingTombWinsOverTomb(Object incomingDto, Record existing) {
        WebUiSyncEngine.ConflictStrategy strategy = serverStrategy();
        String priority = strategy.priorityPlatform();
        String incomingPlatform = platformOfDto(incomingDto);
        String existingPlatform = platformOfDto(existing.dto);
        if (priority != null && !incomingPlatform.equals(existingPlatform)) {
            return priority.equals(incomingPlatform);
        }
        return lastModifiedOf(incomingDto) >= existing.dtoLastModified();
    }

    private void mergeUnion(Map<Long, Record> map, long key, Object dto, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            if (existing == null) {
                map.put(key, new Record(serverTimestamp, true, dto));
            } else if (existing.deleted) {
                if (incomingTombWinsOverTomb(dto, existing)) {
                    map.put(key, new Record(serverTimestamp, true, dto));
                }
            } else if (deletionWinsOverLive(dto, existing)) {
                // 优先端删除无条件传播（§4.1）— no timestamp gate, same as server.
                map.put(key, new Record(serverTimestamp, true, dto));
            }
            return;
        }
        if (existing == null) {
            map.put(key, new Record(serverTimestamp, false, dto));
        } else if (existing.deleted) {
            if (liveWinsOverTombstone(dto, existing)) {
                map.put(key, new Record(serverTimestamp, false, dto));
            }
        } else if (incomingLiveWinsOverLive(dto, existing)) {
            map.put(key, new Record(serverTimestamp, false, dto));
        }
    }

    private void mergeUnion(Map<String, Record> map, String key, Object dto, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            if (existing == null) {
                map.put(key, new Record(serverTimestamp, true, dto));
            } else if (existing.deleted) {
                if (incomingTombWinsOverTomb(dto, existing)) {
                    map.put(key, new Record(serverTimestamp, true, dto));
                }
            } else if (deletionWinsOverLive(dto, existing)) {
                // 优先端删除无条件传播（§4.1）— no timestamp gate, same as server.
                map.put(key, new Record(serverTimestamp, true, dto));
            }
            return;
        }
        if (existing == null) {
            map.put(key, new Record(serverTimestamp, false, dto));
        } else if (existing.deleted) {
            if (liveWinsOverTombstone(dto, existing)) {
                map.put(key, new Record(serverTimestamp, false, dto));
            }
        } else if (incomingLiveWinsOverLive(dto, existing)) {
            map.put(key, new Record(serverTimestamp, false, dto));
        }
    }

    /**
     * Hard-delete entities: deleted records are removed from the live state,
     * but a tombstone marker is kept (serverModified bumped) so the deletion
     * is observable through pull. Resurrection is entity-LWW like v1 (§3.8
     * mirror): only a STRICTLY newer live record revives the key. Live-vs-live
     * follows the same strategy arbitration as the union entities.
     */
    private void mergeHard(Map<Long, Record> map, long key, Object dto, long lastModified, boolean deleted) {
        Record existing = map.get(key);
        if (deleted) {
            map.put(key, new Record(serverTimestamp, true, dto));
            return;
        }
        if (existing == null) {
            map.put(key, new Record(serverTimestamp, false, dto));
        } else if (existing.deleted) {
            if (existing.dtoLastModified() >= lastModified) {
                // A newer (or equally new) tombstone than this live record: keep the deletion.
                return;
            }
            map.put(key, new Record(serverTimestamp, false, dto));
        } else if (incomingLiveWinsOverLive(dto, existing)) {
            map.put(key, new Record(serverTimestamp, false, dto));
        }
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
        response.policy = policy;
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
