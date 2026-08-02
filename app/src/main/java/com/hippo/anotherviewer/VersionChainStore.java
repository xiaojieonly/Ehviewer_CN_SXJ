/*
 * Copyright 2016 Hippo Seven
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

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.anotherviewer.dao.DownloadInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 版本链存储：记录「旧画廊 -> 新画廊」的关联边，发现自画廊详情页的 #gnd 新版列表。
 * 边持久化在独立的 SharedPreferences 中；展示层据此把同一画廊的不同版本折叠，
 * 第一层只显示最新版本（组件内 gid 最大者），旧版本经展开菜单访问。
 */
public final class VersionChainStore {

    private static final String PREF_NAME = "version_chain";
    private static final String KEY_EDGES = "edges";
    private static final String EDGE_SEPARATOR = ":";
    private static final Pattern GID_PATTERN = Pattern.compile("/g/(\\d+)/");

    private static SharedPreferences sPrefs;

    private VersionChainStore() {
    }

    private static synchronized SharedPreferences getPrefs(@NonNull Context context) {
        if (sPrefs == null) {
            sPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        }
        return sPrefs;
    }

    /** 从 EXH 画廊 URL 中提取 gid；解析失败返回 -1。 */
    public static long parseGidFromVersionUrl(@Nullable String url) {
        if (url == null) {
            return -1L;
        }
        Matcher matcher = GID_PATTERN.matcher(url);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return -1L;
    }

    /** 记录一条「旧画廊 -> 新画廊」的关联边（幂等）。 */
    public static void recordEdge(@NonNull Context context, long oldGid, @Nullable String newVersionUrl) {
        if (oldGid <= 0L) {
            return;
        }
        long newGid = parseGidFromVersionUrl(newVersionUrl);
        if (newGid <= 0L || newGid == oldGid) {
            return;
        }
        Set<String> edges = loadEdges(context);
        if (edges.add(oldGid + EDGE_SEPARATOR + newGid)) {
            saveEdges(context, edges);
        }
    }

    /** 记录画廊详情页解析出的全部新版链接。 */
    public static void recordEdges(@NonNull Context context, long oldGid, @Nullable String[] newVersionUrls) {
        if (oldGid <= 0L || newVersionUrls == null) {
            return;
        }
        Set<String> edges = loadEdges(context);
        boolean changed = false;
        for (String url : newVersionUrls) {
            long newGid = parseGidFromVersionUrl(url);
            if (newGid <= 0L || newGid == oldGid) {
                continue;
            }
            if (edges.add(oldGid + EDGE_SEPARATOR + newGid)) {
                changed = true;
            }
        }
        if (changed) {
            saveEdges(context, edges);
        }
    }

    private static Set<String> loadEdges(@NonNull Context context) {
        return new HashSet<>(getPrefs(context).getStringSet(KEY_EDGES, Collections.emptySet()));
    }

    private static void saveEdges(@NonNull Context context, Set<String> edges) {
        getPrefs(context).edit().putStringSet(KEY_EDGES, edges).apply();
    }

    /**
     * 根据下载列表计算折叠结果：
     * - rootToOlders：组件根 gid -> 组件内其余版本（按 gid 降序）
     * - hiddenGids：应折叠（不进入第一层）的 gid
     * 组件只由「两端都在下载列表中的边」构成，孤立条目不进任何组件。
     */
    public static Collapse buildCollapse(@NonNull Context context, @Nullable List<DownloadInfo> list) {
        Collapse collapse = new Collapse();
        if (list == null || list.isEmpty()) {
            return collapse;
        }
        Map<Long, DownloadInfo> byGid = new HashMap<>();
        for (DownloadInfo info : list) {
            byGid.put(info.gid, info);
        }
        Set<String> edges = getPrefs(context).getStringSet(KEY_EDGES, Collections.emptySet());
        Map<Long, Long> parent = new HashMap<>();
        for (Long gid : byGid.keySet()) {
            parent.put(gid, gid);
        }
        for (String edge : edges) {
            int sep = edge.indexOf(EDGE_SEPARATOR);
            if (sep <= 0) {
                continue;
            }
            long oldGid;
            long newGid;
            try {
                oldGid = Long.parseLong(edge.substring(0, sep));
                newGid = Long.parseLong(edge.substring(sep + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            if (byGid.containsKey(oldGid) && byGid.containsKey(newGid)) {
                union(parent, oldGid, newGid);
            }
        }
        Map<Long, List<Long>> componentMembers = new HashMap<>();
        for (Long gid : byGid.keySet()) {
            long root = find(parent, gid);
            List<Long> members = componentMembers.get(root);
            if (members == null) {
                members = new ArrayList<>();
                componentMembers.put(root, members);
            }
            members.add(gid);
        }
        for (List<Long> members : componentMembers.values()) {
            if (members.size() < 2) {
                continue;
            }
            long rootGid = -1L;
            for (Long gid : members) {
                rootGid = Math.max(rootGid, gid);
            }
            List<DownloadInfo> olders = new ArrayList<>();
            for (Long gid : members) {
                if (gid != rootGid) {
                    olders.add(byGid.get(gid));
                    collapse.hiddenGids.add(gid);
                }
            }
            Collections.sort(olders, (a, b) -> Long.compare(b.gid, a.gid));
            collapse.rootToOlders.put(rootGid, olders);
        }
        return collapse;
    }

    private static long find(Map<Long, Long> parent, long gid) {
        long root = gid;
        while (parent.get(root) != root) {
            root = parent.get(root);
        }
        while (parent.get(gid) != gid) {
            long next = parent.get(gid);
            parent.put(gid, root);
            gid = next;
        }
        return root;
    }

    private static void union(Map<Long, Long> parent, long a, long b) {
        long ra = find(parent, a);
        long rb = find(parent, b);
        if (ra != rb) {
            parent.put(ra, rb);
        }
    }

    /** 折叠结果：根 -> 旧版本 与 被隐藏的 gid 集合。 */
    public static class Collapse {
        public final Map<Long, List<DownloadInfo>> rootToOlders = new HashMap<>();
        public final Set<Long> hiddenGids = new HashSet<>();
    }
}
