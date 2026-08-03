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

import java.util.List;
import java.util.Set;

/**
 * Data-access seam for {@link WebUiSyncEngine}. The engine used to call
 * {@link com.hippo.anotherviewer.SiteDB} and SharedPreferences directly (all
 * static, Android-bound), which made the push → pull → apply cycle untestable
 * on the JVM. This interface captures every read/write the engine performs;
 * {@link SiteDbWebUiSyncStore} is the production implementation wrapping
 * SiteDB + SharedPreferences, and tests supply an in-memory Map-backed
 * implementation.
 *
 * <p>The engine additionally depends on {@link WebUiSyncTransport} for the
 * network calls (push/pull), so a full sync cycle can be driven entirely
 * in-memory.
 */
public interface WebUiSyncStore {

    // --- Snapshot reads ---

    List<GalleryInfo> getAllLocalFavorites();

    List<HistoryInfo> getAllHistoryForSync();

    List<DownloadInfo> getAllDownloadInfo();

    List<BookmarkInfo> getAllBookmark();

    List<Filter> getAllFilter();

    List<QuickSearch> getAllQuickSearch();

    List<DownloadLabel> getAllDownloadLabelList();

    // --- Favorites (union merge) ---

    void removeLocalFavorites(long gid);

    LocalFavoriteInfo loadLocalFavorite(long gid);

    void putLocalFavorite(GalleryInfo galleryInfo);

    void updateLocalFavorite(LocalFavoriteInfo info);

    // --- History (last-write-wins, hard delete) ---

    void removeHistoryByKey(long gid);

    void applySyncedHistory(HistoryInfo incoming);

    // --- Downloads (union merge + status sync) ---

    void removeDownloadInfo(long gid);

    void putDownloadInfo(DownloadInfo info);

    // --- Bookmarks (last-write-wins, hard delete) ---

    void removeBookmarkByGid(long gid);

    void putBookmark(BookmarkInfo bookmark);

    // --- Filters (union merge, soft delete) ---

    void deleteFilterByKey(int mode, String text);

    Filter findFilterByKey(int mode, String text);

    void addFilter(Filter filter);

    void triggerFilter(Filter filter);

    // --- Quick searches (union merge, soft delete) ---

    void deleteQuickSearch(QuickSearch search);

    void insertQuickSearch(QuickSearch search);

    void updateQuickSearch(QuickSearch search);

    // --- Download labels (union merge, soft delete) ---

    void removeDownloadLabel(DownloadLabel label);

    void addDownloadLabel(DownloadLabel label);

    void updateDownloadLabel(DownloadLabel label);

    // --- Snapshot / pending key-set persistence ---
    // Per-server-URL sets of keys pushed by the last successful sync
    // (snapshot) and deletions not yet delivered (pending). Stored in
    // SharedPreferences by the production implementation; in-memory in tests.

    Set<Long> loadKeySet(String serverKey, String suffix);

    void saveKeySet(String serverKey, String suffix, Set<Long> keys);

    Set<String> loadStringKeySet(String serverKey, String suffix);

    void saveStringKeySet(String serverKey, String suffix, Set<String> keys);
}
