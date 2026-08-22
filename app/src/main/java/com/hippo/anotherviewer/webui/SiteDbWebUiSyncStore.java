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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.hippo.anotherviewer.client.data.GalleryInfo;
import com.hippo.anotherviewer.dao.BookmarkInfo;
import com.hippo.anotherviewer.dao.DownloadInfo;
import com.hippo.anotherviewer.dao.DownloadLabel;
import com.hippo.anotherviewer.dao.Filter;
import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;
import com.hippo.anotherviewer.dao.QuickSearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Production {@link WebUiSyncStore} wrapping the static {@link com.hippo.anotherviewer.SiteDB}
 * accessors and the sync-state SharedPreferences. Behaviour is identical to
 * what the engine used to call directly; only the call site moved behind the
 * interface so the engine can run against an in-memory store in tests.
 */
public class SiteDbWebUiSyncStore implements WebUiSyncStore {

    /**
     * SharedPreferences file holding the per-server sync bookkeeping (the
     * snapshot/pending key sets and B9 push ledgers), keyed as
     * {@code serverKey + suffix} with every suffix starting with {@code "."}.
     * Lives apart from the connection prefs ({@code webui_settings}), so
     * clearing the connection alone cannot reach it — see
     * {@link #clearServerState}.
     */
    public static final String PREFS = "webui_sync_state";

    @Override
    public List<GalleryInfo> getAllLocalFavorites() {
        return com.hippo.anotherviewer.SiteDB.getAllLocalFavorites();
    }

    @Override
    public List<HistoryInfo> getAllHistoryForSync() {
        return com.hippo.anotherviewer.SiteDB.getAllHistoryForSync();
    }

    @Override
    public List<DownloadInfo> getAllDownloadInfo() {
        return com.hippo.anotherviewer.SiteDB.getAllDownloadInfo();
    }

    @Override
    public List<BookmarkInfo> getAllBookmark() {
        return com.hippo.anotherviewer.SiteDB.getAllBookmark();
    }

    @Override
    public List<Filter> getAllFilter() {
        return com.hippo.anotherviewer.SiteDB.getAllFilter();
    }

    @Override
    public List<QuickSearch> getAllQuickSearch() {
        return com.hippo.anotherviewer.SiteDB.getAllQuickSearch();
    }

    @Override
    public List<DownloadLabel> getAllDownloadLabelList() {
        return com.hippo.anotherviewer.SiteDB.getAllDownloadLabelList();
    }

    @Override
    public void removeLocalFavorites(long gid) {
        com.hippo.anotherviewer.SiteDB.removeLocalFavorites(gid);
    }

    @Override
    public LocalFavoriteInfo loadLocalFavorite(long gid) {
        return com.hippo.anotherviewer.SiteDB.loadLocalFavorite(gid);
    }

    @Override
    public void putLocalFavorite(GalleryInfo galleryInfo) {
        com.hippo.anotherviewer.SiteDB.putLocalFavorite(galleryInfo);
    }

    @Override
    public void updateLocalFavorite(LocalFavoriteInfo info) {
        com.hippo.anotherviewer.SiteDB.updateLocalFavorite(info);
    }

    @Override
    public void removeHistoryByKey(long gid) {
        com.hippo.anotherviewer.SiteDB.removeHistoryByKey(gid);
    }

    @Override
    public void applySyncedHistory(HistoryInfo incoming) {
        com.hippo.anotherviewer.SiteDB.applySyncedHistory(incoming);
    }

    @Override
    public void removeDownloadInfo(long gid) {
        com.hippo.anotherviewer.SiteDB.removeDownloadInfo(gid);
    }

    @Override
    public void putDownloadInfo(DownloadInfo info) {
        com.hippo.anotherviewer.SiteDB.putDownloadInfo(info);
    }

    @Override
    public void removeBookmarkByGid(long gid) {
        com.hippo.anotherviewer.SiteDB.removeBookmarkByGid(gid);
    }

    @Override
    public void putBookmark(BookmarkInfo bookmark) {
        com.hippo.anotherviewer.SiteDB.putBookmark(bookmark);
    }

    @Override
    public void deleteFilterByKey(int mode, String text) {
        com.hippo.anotherviewer.SiteDB.deleteFilterByKey(mode, text);
    }

    @Override
    public Filter findFilterByKey(int mode, String text) {
        return com.hippo.anotherviewer.SiteDB.findFilterByKey(mode, text);
    }

    @Override
    public void addFilter(Filter filter) {
        com.hippo.anotherviewer.SiteDB.addFilter(filter);
    }

    @Override
    public void triggerFilter(Filter filter) {
        com.hippo.anotherviewer.SiteDB.triggerFilter(filter);
    }

    @Override
    public void deleteQuickSearch(QuickSearch search) {
        com.hippo.anotherviewer.SiteDB.deleteQuickSearch(search);
    }

    @Override
    public void insertQuickSearch(QuickSearch search) {
        com.hippo.anotherviewer.SiteDB.insertQuickSearch(search);
    }

    @Override
    public void updateQuickSearch(QuickSearch search) {
        com.hippo.anotherviewer.SiteDB.updateQuickSearch(search);
    }

    @Override
    public void removeDownloadLabel(DownloadLabel label) {
        com.hippo.anotherviewer.SiteDB.removeDownloadLabel(label);
    }

    @Override
    public void addDownloadLabel(DownloadLabel label) {
        com.hippo.anotherviewer.SiteDB.addDownloadLabel(label);
    }

    @Override
    public void updateDownloadLabel(DownloadLabel label) {
        com.hippo.anotherviewer.SiteDB.updateDownloadLabel(label);
    }

    @Override
    public Set<Long> loadKeySet(String serverKey, String suffix) {
        return WebUiKeySetStore.loadKeySet(serverKey + suffix);
    }

    @Override
    public void saveKeySet(String serverKey, String suffix, Set<Long> keys) {
        WebUiKeySetStore.saveKeySet(serverKey + suffix, keys);
    }

    @Override
    public Set<String> loadStringKeySet(String serverKey, String suffix) {
        return WebUiKeySetStore.loadStringKeySet(serverKey + suffix);
    }

    @Override
    public void saveStringKeySet(String serverKey, String suffix, Set<String> keys) {
        WebUiKeySetStore.saveStringKeySet(serverKey + suffix, keys);
    }

    @Override
    public Map<String, Long> loadPushLedger(String serverKey, String suffix) {
        return decodeLedger(WebUiKeySetStore.prefs().getString(serverKey + suffix, ""));
    }

    @Override
    public void savePushLedger(String serverKey, String suffix, Map<String, Long> ledger) {
        WebUiKeySetStore.prefs().edit().putString(serverKey + suffix, encodeLedger(ledger)).apply();
    }

    private static final TypeReference<LinkedHashMap<String, Long>> LEDGER_TYPE =
            new TypeReference<LinkedHashMap<String, Long>>() {};

    /**
     * B9: serializes a push ledger as a JSON object. JSON (not the
     * comma-separated format used for key sets) because ledger keys are
     * arbitrary strings and the values pair each key with a timestamp.
     * Shared with the in-memory test store so both persist identically.
     */
    static String encodeLedger(Map<String, Long> ledger) {
        return JSON.toJSONString(ledger);
    }

    /**
     * B9: parses a persisted push ledger. Empty/absent value → empty map
     * (first sync: every live record counts as new). Unreadable/corrupt
     * value → {@code null}, which the engine answers with a full push for
     * that entity (safe default) and then rewrites a clean ledger.
     */
    static Map<String, Long> decodeLedger(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return JSON.parseObject(raw, LEDGER_TYPE);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * Removes every sync-bookkeeping entry scoped to {@code serverKey} from
     * the {@link #PREFS} file — the snapshot/pending key sets and push ledgers
     * the engine persists under {@code serverKey + "." + suffix} keys. Called
     * from {@link WebUiSettings#clearConfig()} so a logout/re-pairing also
     * clears this cross-file shard; without it the stale snapshot and ledger
     * survive reconfiguration and the next sync against the same URL resumes
     * incrementally from stale state instead of a clean full sync. The
     * {@code "."}-suffixed prefix keeps the wipe exact: one baseUrl that is a
     * strict string prefix of another (e.g. {@code http://h:80} vs
     * {@code http://h:8080}) never collides, because every shard key carries
     * the dot separator.
     */
    public static void clearServerState(android.content.Context context, String serverKey) {
        android.content.SharedPreferences sp =
                context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        String prefix = serverKey + ".";
        java.util.List<String> doomed = new java.util.ArrayList<>();
        for (String key : sp.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                doomed.add(key);
            }
        }
        if (doomed.isEmpty()) {
            return;
        }
        android.content.SharedPreferences.Editor editor = sp.edit();
        for (String key : doomed) {
            editor.remove(key);
        }
        // commit() not apply(): clearConfig runs once per logout, and callers /
        // tests read the surviving shards immediately afterwards.
        editor.commit();
    }

    /** Static helpers for the comma-separated key-set persistence. */
    private static final class WebUiKeySetStore {
        private static final String SEPARATOR = ",";

        static Set<Long> loadKeySet(String key) {
            Set<Long> keys = new java.util.LinkedHashSet<>();
            String raw = prefs().getString(key, "");
            if (raw.isEmpty()) {
                return keys;
            }
            for (String part : raw.split(SEPARATOR)) {
                try {
                    keys.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {
                    // Skip malformed entries; the set is rebuilt on every save.
                }
            }
            return keys;
        }

        static void saveKeySet(String key, Set<Long> keys) {
            StringBuilder sb = new StringBuilder();
            for (Long k : keys) {
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append(k);
            }
            prefs().edit().putString(key, sb.toString()).apply();
        }

        static Set<String> loadStringKeySet(String key) {
            Set<String> keys = new java.util.LinkedHashSet<>();
            String raw = prefs().getString(key, "");
            if (raw.isEmpty()) {
                return keys;
            }
            for (String part : raw.split(SEPARATOR)) {
                if (!part.isEmpty()) {
                    keys.add(part);
                }
            }
            return keys;
        }

        static void saveStringKeySet(String key, Set<String> keys) {
            StringBuilder sb = new StringBuilder();
            for (String k : keys) {
                if (sb.length() > 0) {
                    sb.append(SEPARATOR);
                }
                sb.append(k);
            }
            prefs().edit().putString(key, sb.toString()).apply();
        }

        private static android.content.SharedPreferences prefs() {
            return com.hippo.anotherviewer.SiteApplication.getInstance()
                    .getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        }
    }
}
