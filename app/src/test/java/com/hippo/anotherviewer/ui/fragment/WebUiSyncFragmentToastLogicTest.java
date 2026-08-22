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

package com.hippo.anotherviewer.ui.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.hippo.anotherviewer.webui.WebUiSyncEngine;

import org.junit.Test;

import java.io.IOException;

/**
 * Pure-logic coverage for WebUiSyncFragment's toast helpers: the
 * ConnectTask anonymous-probe failure classification (auth required vs
 * network unreachable vs unclassified) and the sync-done toast's extra
 * counters appended from the write-only Result fields.
 */
public class WebUiSyncFragmentToastLogicTest {

    @Test
    public void testProbeHttp401And403ClassifyAsAuthRequired() {
        assertEquals(WebUiSyncFragment.PROBE_AUTH_REQUIRED,
                WebUiSyncFragment.classifyProbeFailure(new IOException("HTTP 401 Unauthorized")));
        assertEquals(WebUiSyncFragment.PROBE_AUTH_REQUIRED,
                WebUiSyncFragment.classifyProbeFailure(new IOException("HTTP 403 Forbidden")));
    }

    @Test
    public void testOtherHttpStatusIsNotAuthRequired() {
        // Server answered but with something else: reachable, not an auth gate.
        assertEquals(WebUiSyncFragment.PROBE_UNREACHABLE,
                WebUiSyncFragment.classifyProbeFailure(new IOException("HTTP 500 Internal Server Error")));
        assertEquals(WebUiSyncFragment.PROBE_UNREACHABLE,
                WebUiSyncFragment.classifyProbeFailure(new IOException("HTTP 404 Not Found")));
    }

    @Test
    public void testTransportFailuresClassifyAsUnreachable() {
        assertEquals(WebUiSyncFragment.PROBE_UNREACHABLE,
                WebUiSyncFragment.classifyProbeFailure(
                        new IOException("Failed to connect to localhost/127.0.0.1:8080")));
        assertEquals(WebUiSyncFragment.PROBE_UNREACHABLE,
                WebUiSyncFragment.classifyProbeFailure(new IOException("请求超时（服务器无响应）")));
    }

    @Test
    public void testNonIoThrowableStaysUnclassified() {
        assertSame(WebUiSyncFragment.PROBE_OTHER,
                WebUiSyncFragment.classifyProbeFailure(new RuntimeException("boom")));
        assertSame(WebUiSyncFragment.PROBE_OTHER,
                WebUiSyncFragment.classifyProbeFailure(null));
    }

    private static WebUiSyncEngine.Result result(int pushedBookmarks, int pulledBookmarks,
            int pushedQuickSearches, int pulledQuickSearches, int pushedDownloadLabels,
            int pulledDownloadLabels, int pushedEhSessions, int pulledEhSessions) {
        WebUiSyncEngine.Result result = new WebUiSyncEngine.Result();
        result.pushedBookmarks = pushedBookmarks;
        result.pulledBookmarks = pulledBookmarks;
        result.pushedQuickSearches = pushedQuickSearches;
        result.pulledQuickSearches = pulledQuickSearches;
        result.pushedDownloadLabels = pushedDownloadLabels;
        result.pulledDownloadLabels = pulledDownloadLabels;
        result.pushedEhSessions = pushedEhSessions;
        result.pulledEhSessions = pulledEhSessions;
        return result;
    }

    @Test
    public void testAllZeroExtrasAppendNothing() {
        String base = "同步完成：收藏 1↑/2↓、下载 0↑/0↓、过滤 0↑/0↓";
        String message = WebUiSyncFragment.appendExtraSyncCounts(base,
                result(0, 0, 0, 0, 0, 0, 0, 0));
        assertEquals(base, message);
    }

    @Test
    public void testNonZeroCountersAreAppendedWithSum() {
        String message = WebUiSyncFragment.appendExtraSyncCounts("base",
                result(2, 3, 1, 0, 0, 4, 5, 0));
        assertEquals("base、bookmarks×5、quickSearches×1、downloadLabels×4、ehSession×5", message);
    }

    @Test
    public void testOnlyNonZeroCountersAppear() {
        String message = WebUiSyncFragment.appendExtraSyncCounts("base",
                result(0, 0, 7, 0, 0, 0, 0, 0));
        assertEquals("base、quickSearches×7", message);
    }
}
