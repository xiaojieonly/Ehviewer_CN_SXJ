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
import static org.junit.Assert.assertTrue;

import com.alibaba.fastjson.JSON;
import com.hippo.anotherviewer.dao.HistoryInfo;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;

/**
 * Reading-progress (page) over the WebUI sync protocol (plan 2026-09-02 D1/D2,
 * app items A6/A7): the pushed SyncHistory carries the local page, a pulled
 * SyncHistory writes the page into the adopted local row, progress survives a
 * full App A → server → App B → server → App A round trip without regressing,
 * and the wire JSON stays backward compatible (old payloads without the field
 * and unknown extra fields both parse).
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class WebUiSyncHistoryPageTest {

    private static final String ANDROID = "android-00000000-0000-0000-0000-000000000001";
    private static final String ANDROID_OTHER = "android-00000000-0000-0000-0000-000000000002";

    private WebUiConfig config;
    private InMemoryWebUiSyncStore store;
    private InMemorySyncServer server;
    private WebUiSyncEngine engine;

    @Before
    public void setUp() {
        config = new WebUiConfig("http", "127.0.0.1", 8080, "user", "token");
        store = new InMemoryWebUiSyncStore();
        server = new InMemorySyncServer();
        engine = new WebUiSyncEngine(store, server);
    }

    @After
    public void tearDown() {
        WebUiSyncEngine.setPolicySource(null);
    }

    private static HistoryInfo history(long gid, long time, int page) {
        HistoryInfo info = new HistoryInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = "h " + gid;
        info.mode = 1;
        info.time = time;
        info.page = page;
        info.pages = 40;
        info.spanSize = 1;
        return info;
    }

    private static WebUiSyncModels.SyncHistory serverHistory(long gid, long time, int page) {
        WebUiSyncModels.SyncHistory dto = new WebUiSyncModels.SyncHistory();
        dto.gid = gid;
        dto.token = "tok" + gid;
        dto.title = "h " + gid;
        dto.mode = 1;
        dto.time = time;
        dto.lastModified = time;
        dto.deviceId = ANDROID_OTHER;
        dto.page = page;
        return dto;
    }

    @Test
    public void pushCarriesPage() throws IOException {
        store.history.put(1L, history(1, 1000, 12));

        engine.syncInternal(config, ANDROID, 0);

        InMemorySyncServer.Record record = server.history.get(1L);
        assertNotNull("history row must be pushed", record);
        assertFalse(record.deleted);
        WebUiSyncModels.SyncHistory pushed = (WebUiSyncModels.SyncHistory) record.dto;
        assertEquals(12, pushed.page);
        assertEquals(1000, pushed.time);
    }

    @Test
    public void pullAppliesPage() throws IOException {
        server.history.put(2L, new InMemorySyncServer.Record(1, false, serverHistory(2, 2000, 9)));

        engine.syncInternal(config, ANDROID, 0);

        HistoryInfo local = store.history.get(2L);
        assertNotNull("pulled history row must be adopted", local);
        assertEquals(9, local.page);
        assertEquals(2000, local.time);
    }

    @Test
    public void pageRoundTripBetweenTwoDevices() throws IOException {
        // Device A reads to page 12 and pushes.
        store.history.put(3L, history(3, 1000, 12));
        engine.syncInternal(config, ANDROID, 0);

        // Device B pulls: the adopted row carries page 12.
        InMemoryWebUiSyncStore storeB = new InMemoryWebUiSyncStore();
        WebUiSyncEngine engineB = new WebUiSyncEngine(storeB, server);
        engineB.syncInternal(config, ANDROID_OTHER, 0);
        assertEquals(12, storeB.history.get(3L).page);

        // Device B reads further to page 30 (newer view time wins the LWW merge).
        storeB.history.put(3L, history(3, 5000, 30));
        engineB.syncInternal(config, ANDROID_OTHER, 0);

        // Device A pulls again: progress does not regress.
        engine.syncInternal(config, ANDROID, 0);
        assertEquals(30, store.history.get(3L).page);
    }

    @Test
    public void syncHistoryWireJsonIsBackwardCompatible() {
        // Serializing with page puts the field on the wire (server DTO reads it).
        String json = JSON.toJSONString(serverHistory(6, 1000, 17));
        assertTrue(json.contains("\"page\":17"));

        // An old payload without the page field parses with the default 0
        // (compat matrix row: old App ← new server, and old server ← new App).
        WebUiSyncModels.SyncHistory legacy = JSON.parseObject(
                "{\"gid\":6,\"mode\":1,\"time\":1000,\"lastModified\":1000,\"deviceId\":\"d\"}",
                WebUiSyncModels.SyncHistory.class);
        assertEquals(0, legacy.page);
        assertEquals(6, legacy.gid);
        assertEquals(1000, legacy.time);
        assertEquals(1, legacy.mode);

        // Unknown extra fields are silently ignored (compat matrix row:
        // new server → old-style clients, and vice versa).
        WebUiSyncModels.SyncHistory unknown = JSON.parseObject(
                "{\"gid\":7,\"mode\":2,\"time\":2000,\"page\":4,\"brandNewField\":\"zzz\"}",
                WebUiSyncModels.SyncHistory.class);
        assertEquals(4, unknown.page);
        assertEquals(2, unknown.mode);
        assertEquals(2000, unknown.time);
    }
}
