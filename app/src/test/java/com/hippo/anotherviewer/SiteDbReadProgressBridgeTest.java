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

package com.hippo.anotherviewer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import com.hippo.anotherviewer.dao.HistoryInfo;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * The SiteDB reading-progress bridge (plan 2026-09-02, app item A4) against a
 * real (Robolectric) v9 database:
 * <ul>
 *   <li>{@code loadHistoryInfo} reads a single row (or null);</li>
 *   <li>{@code updateHistoryPage} stores the page and refreshes {@code time}
 *       (the "currently reading" row stays at the top of the history list and
 *       the sync push ledger — keyed on time — re-pushes it); it no-ops when
 *       the history row does not exist;</li>
 *   <li>{@code applySyncedHistory} copies {@code page} along with the other
 *       fields when the incoming (newer) row wins, and leaves the local page
 *       untouched when it loses the LWW comparison.</li>
 * </ul>
 */
@Config(manifest = Config.NONE)
@RunWith(RobolectricTestRunner.class)
public class SiteDbReadProgressBridgeTest {

    private Context context;

    @Before
    public void setUp() {
        context = RuntimeEnvironment.application;
        AppConfig.initialize(context);
        Settings.initialize(context);
        SiteDB.initialize(context);
    }

    private static HistoryInfo history(long gid, String title, long time, int page) {
        HistoryInfo info = new HistoryInfo();
        info.gid = gid;
        info.token = "tok" + gid;
        info.title = title;
        info.category = 1;
        info.rating = 4.0f;
        info.mode = 1;
        info.time = time;
        info.pages = 40;
        info.page = page;
        return info;
    }

    @Test
    public void loadHistoryInfoReturnsRowOrNull() {
        assertNull(SiteDB.loadHistoryInfo(1L));

        SiteDB.putHistoryInfo(history(1L, "h", 1000, 0));

        HistoryInfo loaded = SiteDB.loadHistoryInfo(1L);
        assertNotNull(loaded);
        assertEquals("h", loaded.title);
        assertEquals(0, loaded.page);
        assertNull(SiteDB.loadHistoryInfo(999L));
    }

    @Test
    public void updateHistoryPageStoresPageAndRefreshesTime() throws InterruptedException {
        SiteDB.putHistoryInfo(history(2L, "h", 1000, 0));
        long timeBefore = SiteDB.loadHistoryInfo(2L).getTime();

        Thread.sleep(50); // make sure the wall clock has moved past timeBefore
        SiteDB.updateHistoryPage(2L, 17);

        HistoryInfo loaded = SiteDB.loadHistoryInfo(2L);
        assertEquals(17, loaded.getPage());
        assertTrue("updateHistoryPage must refresh time (push-ledger key)",
                loaded.getTime() > timeBefore);
    }

    @Test
    public void updateHistoryPageSkipsMissingRow() {
        SiteDB.updateHistoryPage(404L, 5);
        assertNull(SiteDB.loadHistoryInfo(404L));
    }

    @Test
    public void applySyncedHistoryCarriesPageOnWinAndKeepsItOnLoss() {
        // Fresh row adopted from the server.
        SiteDB.applySyncedHistory(history(3L, "from-server", 1000, 6));
        assertEquals(6, SiteDB.loadHistoryInfo(3L).getPage());

        // Newer incoming row wins: its page replaces the local one.
        SiteDB.applySyncedHistory(history(3L, "from-server", 2000, 21));
        assertEquals(21, SiteDB.loadHistoryInfo(3L).getPage());

        // Older incoming row loses: the local page survives untouched.
        SiteDB.applySyncedHistory(history(3L, "from-server", 500, 1));
        HistoryInfo afterLoss = SiteDB.loadHistoryInfo(3L);
        assertEquals(21, afterLoss.getPage());
        assertEquals(2000, afterLoss.getTime());
    }
}
