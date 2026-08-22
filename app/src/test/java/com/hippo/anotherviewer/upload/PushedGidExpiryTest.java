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

package com.hippo.anotherviewer.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * A2：pushed_gids 断点续传判定的过期语义。时间戳化条目只在 TTL 内授权
 * force 续传；过期、旧版裸 gid、损坏条目一律按「未推送」保守处理（跳过，
 * 绝不 force 覆盖服务器自己的下载行）。纯函数钉死，不启服务。
 */
public class PushedGidExpiryTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TTL = DownloadUploadService.PUSHED_GID_TTL_MS;

    @Test
    public void freshEntryAuthorizesForceResume() {
        Set<String> entries = new HashSet<>(Arrays.asList(
                DownloadUploadService.encodePushedEntry(42L, NOW - 1000L)));
        assertTrue(DownloadUploadService.isPushedEntryFresh(entries, 42L, NOW));
        // 未推送过的 gid 不授权。
        assertFalse(DownloadUploadService.isPushedEntryFresh(entries, 43L, NOW));
    }

    @Test
    public void expiredEntryFallsBackToConservativeSkip() {
        Set<String> entries = new HashSet<>(Arrays.asList(
                DownloadUploadService.encodePushedEntry(42L, NOW - TTL - 1L)));
        assertFalse("过期条目不得再授权 force 续传",
                DownloadUploadService.isPushedEntryFresh(entries, 42L, NOW));
    }

    @Test
    public void ttlBoundaryIsExclusive() {
        // 恰好满 TTL：视为过期（now - at == ttl 不满足 <）。
        Set<String> entries = new HashSet<>(Arrays.asList(
                DownloadUploadService.encodePushedEntry(7L, NOW - TTL)));
        assertFalse(DownloadUploadService.isPushedEntryFresh(entries, 7L, NOW));
        // 差一毫秒仍鲜活。
        Set<String> justInside = new HashSet<>(Arrays.asList(
                DownloadUploadService.encodePushedEntry(7L, NOW - TTL + 1L)));
        assertTrue(DownloadUploadService.isPushedEntryFresh(justInside, 7L, NOW));
    }

    @Test
    public void legacyBareGidEntriesAreTreatedAsExpired() {
        // 旧版本只写裸 gid 字符串，无时间戳——无法证明新鲜度，按未推送处理：
        // 宁可跳过也不冒然 force 覆盖可能是别人的服务器行。
        Set<String> legacy = new HashSet<>(Arrays.asList("42"));
        assertFalse(DownloadUploadService.isPushedEntryFresh(legacy, 42L, NOW));
        // null 集合同样按未推送。
        assertFalse(DownloadUploadService.isPushedEntryFresh(null, 42L, NOW));
    }

    @Test
    public void corruptEntriesNeverAuthorizeForce() {
        Set<String> corrupt = new HashSet<>(Arrays.asList(
                "42:notanumber", ":123", "abc:def", ""));
        assertFalse("时间戳损坏的条目不得授权 force 续传",
                DownloadUploadService.isPushedEntryFresh(corrupt, 42L, NOW));
    }

    @Test
    public void anyFreshDuplicateAuthorizes() {
        Set<String> entries = new HashSet<>(Arrays.asList(
                DownloadUploadService.encodePushedEntry(9L, NOW - TTL - 10L),
                DownloadUploadService.encodePushedEntry(9L, NOW - 5L)));
        assertTrue("同一 gid 任一条目鲜活即算推送过",
                DownloadUploadService.isPushedEntryFresh(entries, 9L, NOW));
    }

    @Test
    public void pruneKeepsOnlyFreshDecodableEntries() {
        String fresh = DownloadUploadService.encodePushedEntry(1L, NOW - 1000L);
        String stale = DownloadUploadService.encodePushedEntry(2L, NOW - TTL - 1000L);
        Set<String> pruned = DownloadUploadService.prunePushedEntries(
                new HashSet<>(Arrays.asList(fresh, stale, "3", "4:x", "")), NOW);

        assertEquals(new HashSet<>(Arrays.asList(fresh)), pruned);
        // 空输入安全。
        assertEquals(0, DownloadUploadService.prunePushedEntries(null, NOW).size());
        assertEquals(0, DownloadUploadService.prunePushedEntries(
                new HashSet<String>(), NOW).size());
    }
}
