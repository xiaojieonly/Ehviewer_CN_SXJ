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
     * 启动时序第二路接线（第一路在 {@link WebUiSettings} 构造器）：任何一条
     * 路径先构造都会把白名单持久化接上；静态守卫保证只注册一次。
     */
    public SiteDbWebUiSyncStore() {
        wireCapPrunedPersistence();
    }

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

    // ----- WP4-1: 上限裁剪白名单（SiteDB.sCapPrunedHistoryGids）持久化 -----

    /**
     * 白名单在 {@link #PREFS} 中的键后缀（完整键 = {@code baseUrl + 后缀}）。
     * 键名带 {@code baseUrl.} 前缀使 {@link #clearServerState} 的既有前缀擦除
     * 天然覆盖它：换服务器/重新配对时白名单随该服务器的其它分片一起消失，
     * 而旧服务器的白名单对新服务器没有意义（新服务器没有该裁剪基线）。
     */
    static final String SUFFIX_CAP_PRUNED_HISTORY = ".cap_pruned_history";

    /** 幂等守卫：白名单持久化只注册一次（WebUiSettings / 本类构造器双路触发）。 */
    private static volatile boolean sCapPrunedWired = false;

    /**
     * 测试注入的 Context。Robolectric 下 {@code SiteApplication.getInstance()}
     * 为 null，生产代码永远走 SiteApplication；测试用它把 prefs / 配置解析
     * 指向 Robolectric application，从而覆盖真实的编解码与键名。
     */
    @androidx.annotation.Nullable
    private static volatile android.content.Context sContextForTest;

    /** 测试注入点（配 {@code setCapPrunedPersistence} 用）；生产不调用。 */
    static void setContextForTest(@androidx.annotation.Nullable android.content.Context context) {
        sContextForTest = context;
    }

    /** 当前进程可用的 Context：测试注入优先，否则 SiteApplication。 */
    @androidx.annotation.Nullable
    private static android.content.Context appContext() {
        android.content.Context override = sContextForTest;
        if (override != null) {
            return override;
        }
        return com.hippo.anotherviewer.SiteApplication.getInstance();
    }

    /**
     * 透出内部 {@link WebUiKeySetStore} 的 Long 逗号串 gid 集合编解码（键 →
     * 集合 / 集合 → 键，全量覆盖）。私有内部类保持私有；白名单持久化与
     * 单元测试共用同一编码，保证与快照/pending 分片完全一致。
     */
    static Set<Long> loadGidSet(String key) {
        return WebUiKeySetStore.loadKeySet(key);
    }

    /** 见 {@link #loadGidSet(String)}。 */
    static void saveGidSet(String key, Set<Long> gids) {
        WebUiKeySetStore.saveKeySet(key, gids);
    }

    /**
     * WP4-1 生产白名单持久化：写/读 {@code <baseUrl>.cap_pruned_history}。
     * 回调内实时解析当前 baseUrl（服务器可能被换掉），解析不出（未初始化
     * SiteApplication、未配置服务器、配置损坏）时 save/load 静默跳过——保持
     * 旧版纯内存行为，异常全静默：持久化通道故障只损失重启恢复能力，绝不
     * 影响裁剪或同步主流程。
     *
     * <p>包内可见（非 private）：测试可直接 new 出来注册，覆盖真实编解码。
     */
    static final class PrefsCapPrunedPersistence
            implements com.hippo.anotherviewer.SiteDB.CapPrunedPersistence {

        @Override
        public void save(Set<Long> gids) {
            String serverKey = currentServerKey();
            if (serverKey == null) {
                return;
            }
            try {
                saveGidSet(serverKey + SUFFIX_CAP_PRUNED_HISTORY,
                        gids != null ? gids : java.util.Collections.<Long>emptySet());
            } catch (Exception e) {
                // 静默：落盘失败只损失重启恢复能力，内存白名单仍然有效。
            }
        }

        @Override
        public Set<Long> load() {
            String serverKey = currentServerKey();
            if (serverKey == null) {
                return null;
            }
            try {
                return loadGidSet(serverKey + SUFFIX_CAP_PRUNED_HISTORY);
            } catch (Exception e) {
                // 静默：读失败按「无持久化」处理（等价旧纯内存行为）。
                return null;
            }
        }

        /**
         * 实时解析当前服务器的 baseUrl。不用 new WebUiSettings(...) 的
         * baseUrl()（不存在）而用 loadConfig()：语义等价且显式处理未配置。
         * 注意防类初始化循环——本方法可能经注册时的 load() 回调再进入
         * {@code WebUiSettings} 构造器，其末行的 wireCapPrunedPersistence()
         * 由静态守卫挡住，不会递归。
         */
        @androidx.annotation.Nullable
        private static String currentServerKey() {
            try {
                android.content.Context context = appContext();
                if (context == null) {
                    return null;
                }
                WebUiConfig config = new WebUiSettings(context).loadConfig();
                return config != null ? config.baseUrl() : null;
            } catch (Exception e) {
                // 静默：未配置/配置损坏/极早启动都按「无通道」处理。
                return null;
            }
        }
    }

    /**
     * 把 SiteDB 的上限裁剪白名单接到 {@link #PREFS} 持久化（WP4-1），静态
     * 幂等：只注册一次。启动时序双路覆盖（{@link WebUiSettings} 构造器与
     * 本类构造器都调用），无论哪条路径先到都保证进程内必被接线。
     *
     * <p>注销/清除配置（logout / clearConfig）只清持久化分片（经
     * clearServerState 的前缀擦除），<b>不</b>清 SiteDB 的内存白名单：误清会
     * 让被裁剪的 gid 在下个周期被删除检测当成用户删除、推墓碑给服务器——
     * 宁过度保留（多留一个 gid，最坏只漏推一次删除）不过度删除。内存白名单
     * 随进程存活，进程重启后由持久化分片恢复。
     *
     * <p>注意：本方法内不得 new SiteDbWebUiSyncStore 实例（构造器又会调用
     * 本方法）；回调也由此保持无实例状态。
     */
    public static void wireCapPrunedPersistence() {
        if (sCapPrunedWired) {
            return;
        }
        synchronized (SiteDbWebUiSyncStore.class) {
            if (sCapPrunedWired) {
                return;
            }
            // 守卫先置位再注册：注册会同步调用回调的 load()，它可能再次进入
            // 本类（WebUiSettings 构造器）——守卫已挡住，无递归。
            sCapPrunedWired = true;
            com.hippo.anotherviewer.SiteDB.setCapPrunedPersistence(new PrefsCapPrunedPersistence());
        }
    }

    /**
     * Removes every sync-bookkeeping entry scoped to {@code serverKey} from
     * the {@link #PREFS} file — the snapshot/pending key sets, push ledgers
     * and the WP4-1 cap-pruned whitelist shard the engine persists under
     * {@code serverKey + "." + suffix} keys. Called
     * from {@link WebUiSettings#clearConfig()} so a logout/re-pairing also
     * clears this cross-file shard; without it the stale snapshot and ledger
     * survive reconfiguration and the next sync against the same URL resumes
     * incrementally from stale state instead of a clean full sync. The
     * {@code "."}-suffixed prefix keeps the wipe exact: one baseUrl that is a
     * strict string prefix of another (e.g. {@code http://h:80} vs
     * {@code http://h:8080}) never collides, because every shard key carries
     * the dot separator. The in-memory whitelist is intentionally NOT cleared
     * here (see {@link #wireCapPrunedPersistence}).
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
            return appContext().getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE);
        }
    }
}
