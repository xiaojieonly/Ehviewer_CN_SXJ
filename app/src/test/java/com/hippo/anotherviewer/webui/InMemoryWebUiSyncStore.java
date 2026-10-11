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

import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Map-backed {@link WebUiSyncStore} for tests. Mirrors the semantics of the
 * SiteDB accessors the engine used to call directly (LWW on history time,
 * union insert-only favorites, filter toggling, etc.).
 *
 * <p>WP5-5：全部写方法镜像生产语义置位 {@link SiteDB#SYNC_DIRTY}（生产侧
 * SiteDB 的同一批写方法都置位）。这是「无脏快路径」判据的测试基石：任何
 * 通过本 store 做出的本地变更都会让下一个周期走全量差分，等价于生产路径。
 * 置位在方法入口无条件进行（连 no-op 分支也置位）——生产侧同为无条件置位
 * （精确性不重要，多走一次全量差分的代价远小于漏推），也是多周期既有用例
 * 语义成立的原因：apply 阶段的写会把脏标志带到周期尾，后续周期仍走全量。
 * 静态标志跨 store 实例共享（与生产同一份）：测试若需精确控制快路径，
 * 用 {@code WebUiSyncEngine.setDirtyFlagForTest} 注入独立实例。
 */
public class InMemoryWebUiSyncStore implements WebUiSyncStore {

    public final Map<Long, LocalFavoriteInfo> favorites = new LinkedHashMap<>();
    public final Map<Long, HistoryInfo> history = new LinkedHashMap<>();
    public final Map<Long, DownloadInfo> downloads = new LinkedHashMap<>();
    public final Map<Long, BookmarkInfo> bookmarks = new LinkedHashMap<>();
    public final Map<String, Filter> filters = new LinkedHashMap<>();
    public final Map<String, QuickSearch> quickSearches = new LinkedHashMap<>();
    public final Map<String, DownloadLabel> downloadLabels = new LinkedHashMap<>();
    public final Map<String, String> prefs = new LinkedHashMap<>();

    private long nextId = 1;

    /** 镜像生产写路径的脏标志置位（见类注释）。 */
    private static void markDirty() {
        com.hippo.anotherviewer.SiteDB.markSyncDirty();
    }

    private static String filterKey(int mode, String text) {
        return mode + "|" + (text == null ? "" : text);
    }

    @Override
    public List<GalleryInfo> getAllLocalFavorites() {
        return new ArrayList<>(favorites.values());
    }

    @Override
    public List<HistoryInfo> getAllHistoryForSync() {
        return new ArrayList<>(history.values());
    }

    @Override
    public List<DownloadInfo> getAllDownloadInfo() {
        return new ArrayList<>(downloads.values());
    }

    @Override
    public List<BookmarkInfo> getAllBookmark() {
        return new ArrayList<>(bookmarks.values());
    }

    @Override
    public List<Filter> getAllFilter() {
        return new ArrayList<>(filters.values());
    }

    @Override
    public List<QuickSearch> getAllQuickSearch() {
        return new ArrayList<>(quickSearches.values());
    }

    @Override
    public List<DownloadLabel> getAllDownloadLabelList() {
        return new ArrayList<>(downloadLabels.values());
    }

    @Override
    public void removeLocalFavorites(long gid) {
        markDirty();
        favorites.remove(gid);
    }

    @Override
    public LocalFavoriteInfo loadLocalFavorite(long gid) {
        return favorites.get(gid);
    }

    @Override
    public void putLocalFavorite(GalleryInfo galleryInfo) {
        markDirty();
        if (favorites.containsKey(galleryInfo.gid)) {
            return;
        }
        LocalFavoriteInfo info;
        if (galleryInfo instanceof LocalFavoriteInfo) {
            info = (LocalFavoriteInfo) galleryInfo;
        } else {
            info = new LocalFavoriteInfo(galleryInfo);
            info.time = System.currentTimeMillis();
        }
        favorites.put(info.gid, info);
    }

    @Override
    public void updateLocalFavorite(LocalFavoriteInfo info) {
        markDirty();
        LocalFavoriteInfo existing = favorites.get(info.gid);
        if (existing == null) {
            favorites.put(info.gid, info);
            return;
        }
        info.time = existing.time;
        favorites.put(info.gid, info);
    }

    @Override
    public void removeHistoryByKey(long gid) {
        markDirty();
        history.remove(gid);
    }

    @Override
    public void applySyncedHistory(HistoryInfo incoming) {
        markDirty();
        HistoryInfo existing = history.get(incoming.gid);
        if (existing == null || incoming.time > existing.time) {
            history.put(incoming.gid, incoming);
        }
    }

    @Override
    public void removeDownloadInfo(long gid) {
        markDirty();
        downloads.remove(gid);
    }

    @Override
    public void putDownloadInfo(DownloadInfo info) {
        markDirty();
        downloads.put(info.gid, info);
    }

    @Override
    public void removeBookmarkByGid(long gid) {
        markDirty();
        bookmarks.remove(gid);
    }

    @Override
    public void putBookmark(BookmarkInfo bookmark) {
        markDirty();
        bookmarks.put(bookmark.gid, bookmark);
    }

    @Override
    public void deleteFilterByKey(int mode, String text) {
        markDirty();
        filters.remove(filterKey(mode, text));
    }

    @Override
    public Filter findFilterByKey(int mode, String text) {
        return filters.get(filterKey(mode, text));
    }

    @Override
    public void addFilter(Filter filter) {
        markDirty();
        filter.setId(nextId++);
        filters.put(filterKey(filter.mode, filter.text), filter);
    }

    @Override
    public void triggerFilter(Filter filter) {
        markDirty();
        filter.setEnable(!filter.enable);
    }

    @Override
    public void deleteQuickSearch(QuickSearch search) {
        markDirty();
        quickSearches.remove(search.name);
    }

    @Override
    public void insertQuickSearch(QuickSearch search) {
        markDirty();
        search.id = nextId++;
        quickSearches.put(search.name, search);
    }

    @Override
    public void updateQuickSearch(QuickSearch search) {
        markDirty();
        quickSearches.put(search.name, search);
    }

    @Override
    public void removeDownloadLabel(DownloadLabel label) {
        markDirty();
        downloadLabels.remove(label.getLabel());
    }

    @Override
    public void addDownloadLabel(DownloadLabel label) {
        markDirty();
        DownloadLabel existing = downloadLabels.get(label.getLabel());
        if (existing != null) {
            label.setId(existing.getId());
            return;
        }
        label.setId(nextId++);
        downloadLabels.put(label.getLabel(), label);
    }

    @Override
    public void updateDownloadLabel(DownloadLabel label) {
        markDirty();
        downloadLabels.put(label.getLabel(), label);
    }

    @Override
    public Set<Long> loadKeySet(String serverKey, String suffix) {
        Set<Long> keys = new LinkedHashSet<>();
        String raw = prefs.get(serverKey + suffix);
        if (raw == null || raw.isEmpty()) {
            return keys;
        }
        for (String part : raw.split(",")) {
            try {
                keys.add(Long.parseLong(part));
            } catch (NumberFormatException ignored) {
            }
        }
        return keys;
    }

    @Override
    public void saveKeySet(String serverKey, String suffix, Set<Long> keys) {
        StringBuilder sb = new StringBuilder();
        for (Long key : keys) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key);
        }
        prefs.put(serverKey + suffix, sb.toString());
    }

    @Override
    public Set<String> loadStringKeySet(String serverKey, String suffix) {
        Set<String> keys = new LinkedHashSet<>();
        String raw = prefs.get(serverKey + suffix);
        if (raw == null || raw.isEmpty()) {
            return keys;
        }
        for (String part : raw.split(",")) {
            if (!part.isEmpty()) {
                keys.add(part);
            }
        }
        return keys;
    }

    @Override
    public void saveStringKeySet(String serverKey, String suffix, Set<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (String key : keys) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(key);
        }
        prefs.put(serverKey + suffix, sb.toString());
    }

    /**
     * B9 push ledger, persisted in {@link #prefs} with the exact same JSON
     * codec as the production store — including its corrupt-value semantics:
     * a garbage value under the key makes {@code loadPushLedger} return
     * {@code null}, which the engine answers with a full-push fallback.
     */
    @Override
    public Map<String, Long> loadPushLedger(String serverKey, String suffix) {
        return SiteDbWebUiSyncStore.decodeLedger(prefs.get(serverKey + suffix));
    }

    @Override
    public void savePushLedger(String serverKey, String suffix, Map<String, Long> ledger) {
        prefs.put(serverKey + suffix, SiteDbWebUiSyncStore.encodeLedger(ledger));
    }
}
