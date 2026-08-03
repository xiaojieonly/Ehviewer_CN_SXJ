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

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;
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
    public void testPolicyDefaultsAndRoundTrip() {
        assertEquals("device_priority", settings.conflictStrategy());
        assertEquals(1, settings.clientTier());
        assertEquals(900, settings.autoSyncIntervalSec());

        settings.setConflictStrategy("web_priority");
        settings.setClientTier(2);
        settings.setAutoSyncIntervalSec(0);

        assertEquals("web_priority", settings.conflictStrategy());
        assertEquals(2, settings.clientTier());
        assertEquals(0, settings.autoSyncIntervalSec());
    }

    @Test
    public void testPolicyInvalidValuesFallBack() {
        settings.setConflictStrategy("bogus");
        assertEquals("device_priority", settings.conflictStrategy());

        settings.setClientTier(7);
        assertEquals(3, settings.clientTier());
        settings.setClientTier(-2);
        assertEquals(0, settings.clientTier());

        settings.setAutoSyncIntervalSec(-5);
        assertEquals(0, settings.autoSyncIntervalSec());
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
        settings.setClientTier(2);
        settings.setAutoSyncIntervalSec(60);
        settings.saveConfig(serverA);

        settings.clearConfig();

        assertEquals("device_priority", settings.conflictStrategy());
        assertEquals(1, settings.clientTier());
        assertEquals(900, settings.autoSyncIntervalSec());
    }
}
