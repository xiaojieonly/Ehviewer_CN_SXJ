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

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Verifies the per-server scoping of the sync high-water mark (E2E-12):
 * timestamps are stored under a {@code baseUrl}-keyed preference, legacy
 * unscoped data never leaks into a new server's mark, and {@code clearConfig}
 * wipes the scoped keys so a reconfiguration starts from a full sync.
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSettingsTest {

    private WebUiSettings settings;
    private WebUiConfig serverA;
    private WebUiConfig serverB;
    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().clear().commit();
        settings = new WebUiSettings(context);
        serverA = new WebUiConfig("http", "192.168.1.10", 8080, "", "");
        serverB = new WebUiConfig("http", "192.168.1.20", 8080, "", "");
    }

    @Test
    public void testHighWaterMarkScopedPerServer() {
        assertEquals(0L, settings.lastSyncTimestamp(serverA.baseUrl()));
        assertEquals(0L, settings.lastSyncTimestamp(serverB.baseUrl()));

        settings.setLastSyncTimestamp(serverA.baseUrl(), 1000L);

        assertEquals(1000L, settings.lastSyncTimestamp(serverA.baseUrl()));
        assertEquals(0L, settings.lastSyncTimestamp(serverB.baseUrl()));

        settings.setLastSyncTimestamp(serverB.baseUrl(), 2000L);

        assertEquals(1000L, settings.lastSyncTimestamp(serverA.baseUrl()));
        assertEquals(2000L, settings.lastSyncTimestamp(serverB.baseUrl()));
    }

    @Test
    public void testSameServerKeyReadsBackSameValue() {
        settings.setLastSyncTimestamp(serverA.baseUrl(), 42L);
        assertEquals(42L, settings.lastSyncTimestamp("http://192.168.1.10:8080"));
    }

    @Test
    public void testLegacyUnscopedDataNeverLeaksIntoScopedRead() {
        // Data written before per-server scoping (or via the legacy accessors,
        // still used by the preferences pull) must not become the high-water
        // mark of any server: a fresh server starts from a full pull.
        settings.setLastSyncTimestamp(1234L);

        assertEquals(1234L, settings.lastSyncTimestamp());
        assertEquals(0L, settings.lastSyncTimestamp(serverA.baseUrl()));
        assertEquals(0L, settings.lastSyncTimestamp(serverB.baseUrl()));

        settings.setLastSyncTimestamp(serverA.baseUrl(), 777L);
        // The legacy key is untouched by scoped writes.
        assertEquals(1234L, settings.lastSyncTimestamp());
    }

    @Test
    public void testClearConfigWipesScopedHighWaterMark() {
        settings.setLastSyncTimestamp(serverA.baseUrl(), 1000L);
        settings.saveConfig(serverA);

        settings.clearConfig();

        assertEquals(0L, settings.lastSyncTimestamp(serverA.baseUrl()));
        assertEquals(0L, settings.lastSyncTimestamp(serverB.baseUrl()));
    }

    @Test
    public void testClearConfigWipesSyncStateShardsOnlyForClearedServer() {
        // A1: the engine's snapshot/pending/ledger bookkeeping lives in the
        // separate webui_sync_state file under "<baseUrl>.<suffix>" keys.
        // clearConfig must wipe exactly the cleared server's shard and leave
        // every other key (other servers, unprefixed entries) untouched.
        android.content.SharedPreferences syncState = RuntimeEnvironment.application
                .getSharedPreferences("webui_sync_state", Context.MODE_PRIVATE);
        syncState.edit()
                .putString(serverA.baseUrl() + ".snapshot.downloads", "4")
                .putString(serverA.baseUrl() + ".pending.favorites", "1")
                .putString(serverA.baseUrl() + ".ledger.downloads", "{\"4\":9000}")
                .putString(serverB.baseUrl() + ".snapshot.downloads", "5")
                .putString("unrelated-key", "keep")
                .commit();
        try {
            settings.saveConfig(serverA);

            settings.clearConfig();

            assertEquals(0, countKeysWithPrefix(syncState, serverA.baseUrl() + "."));
            assertEquals("5", syncState.getString(serverB.baseUrl() + ".snapshot.downloads", null));
            assertEquals("keep", syncState.getString("unrelated-key", null));
        } finally {
            syncState.edit().clear().commit();
        }
    }

    private static int countKeysWithPrefix(android.content.SharedPreferences prefs, String prefix) {
        int count = 0;
        for (String key : prefs.getAll().keySet()) {
            if (key.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    @Test
    public void testPolicyDefaultsAndRoundTrip() {
        // WP5-4 v4 默认值：conflictStrategy=lww（服务端裁决）、周期 10s。
        // clientTier 默认 2（Tier-2 默认，本次不改；断言与生产默认对齐）。
        assertEquals("lww", settings.conflictStrategy());
        assertEquals(2, settings.clientTier());
        assertEquals(10, settings.autoSyncIntervalSec());

        settings.setConflictStrategy("web_priority");
        settings.setClientTier(2);
        settings.setAutoSyncIntervalSec(0);

        assertEquals("web_priority", settings.conflictStrategy());
        assertEquals(2, settings.clientTier());
        assertEquals(0, settings.autoSyncIntervalSec());
    }

    @Test
    public void testLegacyDevicePriorityStillSelectable() {
        // A/C 旧版兼容策略保留可选（契约 v4 §1.4）：显式选择必须原样保存，
        // 不能被 lww 默认吞掉——设置 UI 仍提供「Android 优先（旧版兼容）」条目。
        settings.setConflictStrategy("device_priority");
        assertEquals("device_priority", settings.conflictStrategy());
    }

    @Test
    public void testPolicyInvalidValuesFallBack() {
        settings.setConflictStrategy("bogus");
        assertEquals("lww", settings.conflictStrategy());
        settings.setClientTier(7);
        assertEquals(3, settings.clientTier());
        settings.setClientTier(-2);
        assertEquals(0, settings.clientTier());

        settings.setAutoSyncIntervalSec(-5);
        assertEquals(0, settings.autoSyncIntervalSec());
    }

    /**
     * WP5-4：落库值非法/越界时的读取回退。autoSyncIntervalSec 的 setter 会
     * clamp，负值只能从旧版本数据或外部写坏的分片里出现——直接写裸 pref
     * 覆盖读取侧的 {@code value >= 0 ? value : 10} 回退分支。
     */
    @Test
    public void testAutoSyncIntervalNegativeStoredValueFallsBackToDefault() {
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().putInt("auto_sync_interval_sec", -30).commit();

        assertEquals(10, settings.autoSyncIntervalSec());
    }

    @Test
    public void testTierTwoImpliesRemoteRead() {
        settings.setRemoteReadEnabled(false);
        settings.setClientTier(1);
        assertEquals(false, settings.remoteReadEnabled());

        settings.setClientTier(2);
        assertEquals(true, settings.remoteReadEnabled());
    }

    @Test
    public void testTierThreeAlsoImpliesRemoteRead() {
        settings.setRemoteReadEnabled(false);
        settings.setClientTier(3);
        assertEquals(true, settings.remoteReadEnabled());
    }

    @Test
    public void testClearConfigRestoresPolicyDefaults() {
        settings.setConflictStrategy("web_priority");
        settings.setClientTier(3);
        settings.setAutoSyncIntervalSec(60);
        settings.saveConfig(serverA);

        settings.clearConfig();

        assertEquals("lww", settings.conflictStrategy());
        assertEquals("clientTier 默认 2（Tier-2 默认，本次不改）", 2, settings.clientTier());
        assertEquals(10, settings.autoSyncIntervalSec());
    }

    /**
     * clientTier 越界落库值的读取回退（setter clamp 不到的分片）：读取侧
     * {@code tier >= 0 && tier <= 3 ? tier : 1} 回退到 1。直接写裸 pref 覆盖。
     */
    @Test
    public void testClientTierOutOfRangeStoredValueFallsBack() {
        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().putInt("client_tier", 9).commit();
        assertEquals(1, settings.clientTier());

        context.getSharedPreferences("webui_settings", Context.MODE_PRIVATE)
                .edit().putInt("client_tier", -1).commit();
        assertEquals(1, settings.clientTier());
    }
}
