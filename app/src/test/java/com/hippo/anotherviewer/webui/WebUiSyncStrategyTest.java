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

import com.hippo.anotherviewer.dao.HistoryInfo;
import com.hippo.anotherviewer.dao.LocalFavoriteInfo;

import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Client-side behavior matrix for the Wave-2 conflict strategies
 * (contract v2 §3.8 / MASTER §4.2): soft-entity tombstone honoring per
 * strategy, tombstone-entity propagation, legacy (no-policy) fallback and the
 * D2 push-carried policy.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncStrategyTest {

    private static final String ANDROID = "android-00000000-0000-0000-0000-000000000001";
    private static final String WEB = "web-browser-1";
    private static final String ANDROID_OTHER = "android-00000000-0000-0000-0000-000000000002";

    private WebUiConfig config;
    private InMemoryWebUiSyncStore store;
    private InMemorySyncServer server;
    private WebUiSyncEngine engine;

    @Before
    public void setUp() {
        // 防御性复位：生产装配（SiteApplication.onCreate）的 settings-backed 策略源
        // 可能在 Robolectric 同 sandbox 内泄漏到本类（其默认已随 WebUiSettings 变为
        // lww/10），「未装配默认」用例必须看到真正的契约缺省，不被泄漏源污染。
        WebUiSyncEngine.setPolicySource(null);
        config = new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
        store = new InMemoryWebUiSyncStore();
        server = new InMemorySyncServer();
        engine = new WebUiSyncEngine(store, server);
    }

    @After
    public void tearDown() {
        WebUiSyncEngine.setPolicySource(null);
    }

    private static WebUiSyncModels.SyncPolicy policy(String strategy) {
        WebUiSyncModels.SyncPolicy p = new WebUiSyncModels.SyncPolicy();
        p.conflictStrategy = strategy;
        return p;
    }

    private static LocalFavoriteInfo favorite(long gid) {
        LocalFavoriteInfo info = new LocalFavoriteInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "fav " + gid;
        info.time = 1000;
        info.pages = 5;
        info.spanSize = 1;
        return info;
    }

    private void seedWebFavoriteTombstone(long gid) {
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.favorites.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    private void seedAndroidFavoriteTombstone(long gid) {
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = gid;
        tomb.token = "tok" + gid;
        tomb.lastModified = 5000;
        tomb.deviceId = ANDROID_OTHER;
        tomb.deleted = true;
        server.favorites.put(gid, new InMemorySyncServer.Record(1, true, tomb));
    }

    @Test
    public void honorSoftTombstoneMatrix() {
        // B: everything propagates.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.LWW, WEB, ANDROID, true));
        // A: priority (android) deletion propagates...
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, ANDROID_OTHER, ANDROID, true));
        // ...but a non-priority (web) deletion does not remove the priority live copy...
        assertFalse(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, WEB, ANDROID, true));
        // ...and does propagate when the priority platform holds nothing.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY, WEB, ANDROID, false));
        // C: web deletion propagates; android-vs-android (both non-priority) propagates.
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY, WEB, ANDROID, true));
        assertTrue(WebUiSyncEngine.honorSoftTombstone(
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY, ANDROID_OTHER, ANDROID, true));
    }

    /**
     * WP5-3：honorTombstone 新重载矩阵。policy 存在（v4 服务器）→ 恒 true，
     * 删除统一传播，优先端保留不再适用；policy 缺失（legacy）→ 与旧公式
     * {@link WebUiSyncEngine#honorSoftTombstone} 逐格一致。
     */
    @Test
    public void honorTombstoneMatrix_policyPresentAlwaysHonors_legacyFallsBack() {
        WebUiSyncEngine.ConflictStrategy[] strategies = {
                WebUiSyncEngine.ConflictStrategy.DEVICE_PRIORITY,
                WebUiSyncEngine.ConflictStrategy.LWW,
                WebUiSyncEngine.ConflictStrategy.WEB_PRIORITY,
        };
        String[] tombDevices = {ANDROID, ANDROID_OTHER, WEB, "ios-device-9", ""};
        String[] localDevices = {ANDROID, WEB};
        for (WebUiSyncEngine.ConflictStrategy strategy : strategies) {
            for (String tomb : tombDevices) {
                for (String local : localDevices) {
                    for (boolean alive : new boolean[] {true, false}) {
                        String ctx = "strategy=" + strategy + " tomb=" + tomb
                                + " local=" + local + " alive=" + alive;
                        // v4（pull 携带 policy）：恒遵守，与策略/平台/存活无关。
                        assertTrue(ctx, WebUiSyncEngine.honorTombstone(
                                strategy, tomb, local, alive, true));
                        // legacy（无 policy）：旧公式逐格一致。
                        assertEquals(ctx,
                                WebUiSyncEngine.honorSoftTombstone(strategy, tomb, local, alive),
                                WebUiSyncEngine.honorTombstone(strategy, tomb, local, alive, false));
                    }
                }
            }
        }
    }

    /**
     * WP5-3 engine 级：v4（policy 存在）下拉到的 web 墓碑无条件传播——本地
     * live 拷贝被删，不再有 §3.8 优先端保留。用例把墓碑种在首周期水位之上，
     * 且本地记录已在首周期入账（第二周期无新内容可推），确保 pull 真的能
     * 拿到墓碑（而不是被客户端的 live 重推先复活）。
     */
    @Test
    public void policyPresent_pulledWebTombstone_localFavoriteDeleted() throws IOException {
        server.policy = policy("device_priority");
        store.putLocalFavorite(favorite(12));
        WebUiSyncEngine.Result first = engine.syncInternal(config, ANDROID, 0);
        assertEquals(1, first.pushedFavorites);
        assertNotNull(store.loadLocalFavorite(12));

        // Web 在首周期之后删除：墓碑落在水位之上，必然被 pull 拉到。
        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = 12;
        tomb.token = "tok12";
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.favorites.put(12L, new InMemorySyncServer.Record(first.serverTimestamp + 1, true, tomb));

        engine.syncInternal(config, ANDROID, first.serverTimestamp);

        assertNull("v4 删除统一传播：优先端 live 拷贝也被遵守",
                store.loadLocalFavorite(12));
    }

    /**
     * legacy（policy=null）engine 级回退：pull 不携带策略 → 客户端按 LWW 旧
     * 公式（priority==null → 恒遵守）处理墓碑，删除同样传播，且不走任何
     * v4 分支（回归保护：老服务器行为不变）。
     */
    @Test
    public void legacyServer_nullPolicy_tombstoneStillHonoredAsLww() throws IOException {
        server.policy = null;
        store.putLocalFavorite(favorite(13));
        WebUiSyncEngine.Result first = engine.syncInternal(config, ANDROID, 0);
        assertEquals(1, first.pushedFavorites);

        WebUiSyncModels.SyncFavorite tomb = new WebUiSyncModels.SyncFavorite();
        tomb.gid = 13;
        tomb.token = "tok13";
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.favorites.put(13L, new InMemorySyncServer.Record(first.serverTimestamp + 1, true, tomb));

        engine.syncInternal(config, ANDROID, first.serverTimestamp);

        assertNull("legacy 回退：LWW 下墓碑传播（旧公式 priority==null 恒遵守）",
                store.loadLocalFavorite(13));
    }

    /**
     * WP5-3 之后的语义说明：v4（policy 存在）下服务器墓碑统一传播（见
     * {@link #policyPresent_pulledWebTombstone_localFavoriteDeleted}）。本用例
     * 里墓碑种在 since=0 的水位之上，但同步顺序是 push 先行——客户端自己的
     * live 记录先把服务端墓碑复活（device_priority 下 android live 胜 web
     * 墓碑），pull 拉回的是复活后的 live 行，所以本地保留。保留本用例作为
     * 「push 先行 + 优先端 live 复活」组合的回归保护。
     */
    @Test
    public void devicePriority_webTombstone_localFavoriteRetained() throws IOException {
        server.policy = policy("device_priority");
        store.putLocalFavorite(favorite(7));
        seedWebFavoriteTombstone(7);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull("priority platform retains its live copy", store.loadLocalFavorite(7));
    }

    @Test
    public void devicePriority_androidTombstone_localFavoriteDeleted() throws IOException {
        server.policy = policy("device_priority");
        store.putLocalFavorite(favorite(8));
        seedAndroidFavoriteTombstone(8);

        engine.syncInternal(config, ANDROID, 0);

        assertNull(store.loadLocalFavorite(8));
    }

    @Test
    public void lww_webTombstone_localFavoriteResurrects() throws IOException {
        // Matrix §4.2 B: union — the local live copy survives the pull (client
        // honoring). F6 裁决（leader，2026-08-04）：B9 增量推送下 lww soft 删除
        // 粘性为预期语义——「the deleting side is resurrected next round」系
        // 全量推送时代的偶然收敛机制（保留端全量重推回显），非设计承诺；复活须
        // 经保留端显式 re-add/重戳，作为新 lastModified delta 即时传播。本用例
        // 走 since=0 全量首推，故保留端的 live 记录随推送复活服务端墓碑。
        server.policy = policy("lww");
        store.putLocalFavorite(favorite(9));
        seedWebFavoriteTombstone(9);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(store.loadLocalFavorite(9));
    }

    @Test
    public void webPriority_webTombstone_localFavoriteDeleted() throws IOException {
        server.policy = policy("web_priority");
        store.putLocalFavorite(favorite(10));
        seedWebFavoriteTombstone(10);

        engine.syncInternal(config, ANDROID, 0);

        assertNull(store.loadLocalFavorite(10));
    }

    @Test
    public void noPolicy_legacyServer_unionResurrection() throws IOException {
        // Legacy servers (no policy) behave as v1 union: live resurrects.
        server.policy = null;
        store.putLocalFavorite(favorite(11));
        seedWebFavoriteTombstone(11);

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(store.loadLocalFavorite(11));
    }

    @Test
    public void tombstoneEntity_historyDeletionPropagatesUnderDevicePriority() throws IOException {
        server.policy = policy("device_priority");
        HistoryInfo local = new HistoryInfo();
        local.gid = 21;
        local.token = "tok21";
        local.time = 1000;
        store.applySyncedHistory(local);

        WebUiSyncModels.SyncHistory tomb = new WebUiSyncModels.SyncHistory();
        tomb.gid = 21;
        tomb.token = "tok21";
        // Realistic tombstone: a soft-deleted history row preserves its view
        // time, so the tombstone of the shared synced row carries the same
        // time as the local copy — a same-view live push ties the in-window
        // tie-break and can never beat its own tombstone.
        tomb.time = 1000;
        tomb.lastModified = 5000;
        tomb.deviceId = WEB;
        tomb.deleted = true;
        server.history.put(21L, new InMemorySyncServer.Record(1, true, tomb));

        engine.syncInternal(config, ANDROID, 0);

        assertTrue("history tombstones propagate under any strategy",
                store.getAllHistoryForSync().isEmpty());
    }

    @Test
    public void pushCarriesDevicePolicy() throws IOException {
        WebUiSyncModels.SyncPolicy mine = new WebUiSyncModels.SyncPolicy();
        mine.conflictStrategy = "web_priority";
        mine.clientTier = 2;
        mine.autoSyncIntervalSec = 300;
        WebUiSyncEngine.setPolicySource(() -> mine);
        // 种一条本地数据（写方法置脏）确保走完整周期：无脏 + pending 空时的
        // 快路径会整体跳过 push（策略也就无从携带）。
        store.putLocalFavorite(favorite(1));

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(server.lastPushPolicy);
        assertEquals("web_priority", server.lastPushPolicy.conflictStrategy);
        assertEquals(2, server.lastPushPolicy.clientTier);
        assertEquals(300, server.lastPushPolicy.autoSyncIntervalSec);
    }

    @Test
    public void pushCarriesContractDefaultsWithoutSource() throws IOException {
        store.putLocalFavorite(favorite(2));

        engine.syncInternal(config, ANDROID, 0);

        assertNotNull(server.lastPushPolicy);
        // v4 契约缺省（sync-conflict-rules §7）：lww / clientTier 1 / 10s 节拍。
        assertEquals("lww", server.lastPushPolicy.conflictStrategy);
        assertEquals(1, server.lastPushPolicy.clientTier);
        assertEquals(10, server.lastPushPolicy.autoSyncIntervalSec);
    }

    @Test
    public void autoSyncPeriodicDelay() {
        assertEquals(-1L, WebUiAutoSyncScheduler.periodicDelayMs(0));
        assertEquals(900_000L, WebUiAutoSyncScheduler.periodicDelayMs(900));
    }

    @Test
    public void platformParsing() {
        assertEquals("android", WebUiSyncEngine.platformOf(ANDROID));
        assertEquals("web", WebUiSyncEngine.platformOf(WEB));
        assertEquals("", WebUiSyncEngine.platformOf(null));
    }
}
