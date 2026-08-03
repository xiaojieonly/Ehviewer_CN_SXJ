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
        favorites.remove(gid);
    }

    @Override
    public LocalFavoriteInfo loadLocalFavorite(long gid) {
        return favorites.get(gid);
    }

    @Override
    public void putLocalFavorite(GalleryInfo galleryInfo) {
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
        history.remove(gid);
    }

    @Override
    public void applySyncedHistory(HistoryInfo incoming) {
        HistoryInfo existing = history.get(incoming.gid);
        if (existing == null || incoming.time > existing.time) {
            history.put(incoming.gid, incoming);
        }
    }

    @Override
    public void removeDownloadInfo(long gid) {
        downloads.remove(gid);
    }

    @Override
    public void putDownloadInfo(DownloadInfo info) {
        downloads.put(info.gid, info);
    }

    @Override
    public void removeBookmarkByGid(long gid) {
        bookmarks.remove(gid);
    }

    @Override
    public void putBookmark(BookmarkInfo bookmark) {
        bookmarks.put(bookmark.gid, bookmark);
    }

    @Override
    public void deleteFilterByKey(int mode, String text) {
        filters.remove(filterKey(mode, text));
    }

    @Override
    public Filter findFilterByKey(int mode, String text) {
        return filters.get(filterKey(mode, text));
    }

    @Override
    public void addFilter(Filter filter) {
        filter.setId(nextId++);
        filters.put(filterKey(filter.mode, filter.text), filter);
    }

    @Override
    public void triggerFilter(Filter filter) {
        filter.setEnable(!filter.enable);
    }

    @Override
    public void deleteQuickSearch(QuickSearch search) {
        quickSearches.remove(search.name);
    }

    @Override
    public void insertQuickSearch(QuickSearch search) {
        search.id = nextId++;
        quickSearches.put(search.name, search);
    }

    @Override
    public void updateQuickSearch(QuickSearch search) {
        quickSearches.put(search.name, search);
    }

    @Override
    public void removeDownloadLabel(DownloadLabel label) {
        downloadLabels.remove(label.getLabel());
    }

    @Override
    public void addDownloadLabel(DownloadLabel label) {
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
}
